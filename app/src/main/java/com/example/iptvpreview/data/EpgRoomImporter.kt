package com.example.iptvpreview.data

import androidx.room.withTransaction
import com.example.iptvpreview.data.database.EpgProgramEntity
import com.example.iptvpreview.data.database.IptvDatabase
import com.example.iptvpreview.data.model.EpgProgram
import com.example.iptvpreview.data.parser.EpgParser
import java.io.InputStream
import kotlinx.coroutines.flow.collect

/** The app has one configured guide. Failed parsing rolls back its replacement. */
internal suspend fun importEpgStream(db: IptvDatabase, stream: InputStream,
    now: Long = System.currentTimeMillis()): Map<String, EpgProgram> = stream.use {
    db.withTransaction {
        val dao = db.epgDao()
        val batch = ArrayList<EpgProgramEntity>(500)
        val snapshot = mutableMapOf<String, EpgProgram>()
        val cutoff = now - 24L * 60 * 60 * 1000
        dao.deleteAll()
        EpgParser().programs(stream).collect { program ->
            if (program.endTime >= cutoff) {
                batch.add(EpgProgramEntity(channelId = program.channelId, title = program.title,
                    description = program.description, startTime = program.startTime, endTime = program.endTime))
                if (batch.size == 500) { dao.insertAll(batch); batch.clear() }
            }
            if (program.endTime > now) {
                val old = snapshot[program.channelId]
                val live = program.startTime <= now
                val oldLive = old != null && old.startTime <= now
                if (old == null || (live && !oldLive) ||
                    (live && oldLive && program.startTime > old.startTime) ||
                    (!live && !oldLive && program.startTime < old.startTime)) snapshot[program.channelId] = program
            }
        }
        if (batch.isNotEmpty()) dao.insertAll(batch)
        dao.deleteOldPrograms(cutoff)
        snapshot.toMap()
    }
}
