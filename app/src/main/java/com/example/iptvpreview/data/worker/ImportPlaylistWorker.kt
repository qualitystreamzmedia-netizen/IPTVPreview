package com.example.iptvpreview.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.room.withTransaction
import androidx.work.*
import com.example.iptvpreview.data.database.ChannelEntity
import com.example.iptvpreview.data.database.IptvDatabase
import com.example.iptvpreview.data.parseM3u
import com.example.iptvpreview.data.stableId
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ImportPlaylistWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(URL)?.toHttpUrlOrNull()
            ?: return@withContext Result.failure(workDataOf(ERROR to "Invalid playlist URL"))
        val playlistId = inputData.getString(PLAYLIST_ID)?.takeIf { it.isNotBlank() }
            ?: stableId("M3U|$url")
        var temp: File? = null
        try {
            report("Downloading playlist", -1)
            val staged = File.createTempFile("playlist-", ".m3u", applicationContext.cacheDir)
            temp = staged
            val call = client.newCall(Request.Builder().url(url).build())
            // Cancel the socket promptly if WorkManager stops this coroutine.
            val watcher = launch { try { awaitCancellation() } finally { call.cancel() } }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 408 || response.code == 429 || response.code >= 500) throw IOException("Temporary HTTP failure")
                        return@withContext Result.failure(workDataOf(ERROR to "HTTP ${response.code}"))
                    }
                    val body = response.body ?: throw IOException("Empty response")
                    val total = body.contentLength()
                    require(total <= MAX_BYTES) { "Playlist exceeds 100 MB" }
                    body.byteStream().use { input -> staged.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        var last = -1
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            copied += read
                            require(copied <= MAX_BYTES) { "Playlist exceeds 100 MB" }
                            output.write(buffer, 0, read)
                            val progress = if (total > 0) (copied * 70 / total).toInt().coerceIn(0, 70) else -1
                            if (progress != last) { report("Downloading playlist", progress); last = progress }
                        }
                    } }
                }
            } finally { watcher.cancelAndJoin() }
            report("Importing channels", 70)
            val count = importM3uFile(staged, url.toString(), playlistId, IptvDatabase.getInstance(applicationContext)) { rows ->
                report("Importing channels: $rows", 70, rows)
            }
            report("Playlist imported", 100, count)
            Result.success(workDataOf(PROGRESS_KEY to 100, ROWS to count, PLAYLIST_ID to playlistId))
        } catch (e: CancellationException) { throw e
        } catch (e: IOException) {
            if (runAttemptCount < 2) Result.retry() else Result.failure(workDataOf(ERROR to "Download failed after 3 attempts"))
        } catch (e: Exception) {
            Result.failure(workDataOf(ERROR to "Playlist import failed"))
        } finally { temp?.delete() }
    }

    private suspend fun report(message: String, percent: Int, rows: Int = 0) {
        setProgress(workDataOf(PROGRESS_KEY to percent, ROWS to rows))
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Playlist imports", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("IPTV Preview")
            .setContentText(message).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .addAction(android.R.drawable.ic_delete, "Cancel", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        val notificationId = id.hashCode() and Int.MAX_VALUE
        setForeground(if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(notificationId, notification))
    }

    companion object {
        const val URL = "url"
        const val PLAYLIST_ID = "playlist_id"
        const val PROGRESS_KEY = "progress"
        const val ROWS = "rows"
        const val ERROR = "error"
        private const val CHANNEL = "playlist_imports"
        private const val MAX_BYTES = 100L * 1024 * 1024
        private val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.MINUTES).readTimeout(30, TimeUnit.SECONDS).build()

        fun enqueue(context: Context, url: String, playlistId: String = stableId("M3U|$url")): java.util.UUID {
            require(url.toHttpUrlOrNull() != null && playlistId.isNotBlank())
            val request = OneTimeWorkRequestBuilder<ImportPlaylistWorker>()
                .setInputData(workDataOf(URL to url, PLAYLIST_ID to playlistId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("import-${stableId(playlistId)}", ExistingWorkPolicy.REPLACE, request)
            return request.id
        }
    }
}

/** Parse with bounded row memory; the outer transaction makes replacement atomic. */
internal suspend fun importM3uFile(file: File, url: String, playlistId: String, db: IptvDatabase,
    onBatch: suspend (Int) -> Unit = {}): Int = db.withTransaction {
    val dao = db.channelDao()
    val favorites = dao.favoriteIds(playlistId).toHashSet()
    dao.deletePlaylistChannels(playlistId)
    val batch = ArrayList<ChannelEntity>(1000)
    var metadata = ""
    var count = 0
    var valid = false
    file.bufferedReader().use { reader ->
        while (true) {
            currentCoroutineContext().ensureActive()
            val line = reader.readLine()?.trim()?.removePrefix("\uFEFF") ?: break
            when {
                line.startsWith("#EXTM3U") -> valid = true
                line.startsWith("#EXTINF:") -> { valid = true; metadata = line }
                line.startsWith("#EXTGRP:") -> metadata += "\n$line"
                line.isNotBlank() && !line.startsWith('#') -> {
                    require(valid) { "Not an M3U playlist" }
                    val channel = parseM3u("#EXTM3U\n$metadata\n$line", url, playlistId).firstOrNull()
                    metadata = ""
                    if (channel != null) {
                        batch.add(ChannelEntity(channel.id, playlistId, channel.name, channel.url, channel.group, count++, channel.id in favorites, channel.logoUrl, channel.epgId))
                        if (batch.size == 1000) { dao.insertAll(batch); batch.clear(); onBatch(count) }
                    }
                }
            }
        }
    }
    require(valid && count > 0) { "No playable channels" }
    if (batch.isNotEmpty()) { dao.insertAll(batch); onBatch(count) }
    count
}
