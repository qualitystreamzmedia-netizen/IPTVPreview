package com.example.iptvpreview.data.parser

import com.example.iptvpreview.data.model.EpgProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2
import java.io.InputStream
import java.io.BufferedInputStream
import java.util.zip.GZIPInputStream
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.SAXParserFactory

class EpgParser {
    /** Bounded bridge from synchronous SAX callbacks to a suspending database consumer. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun programs(inputStream: InputStream): kotlinx.coroutines.flow.Flow<EpgProgram> = kotlinx.coroutines.flow.channelFlow {
        parseInto(inputStream) { program ->
            trySendBlocking(program).getOrThrow()
        }
    }.buffer(64)
    /**
     * Streams XMLTV on Dispatchers.IO and closes the supplied stream, even on failure.
     * XML is parsed incrementally; the returned program list still resides in memory.
     * Malformed XML fails the parse; incomplete or invalid program entries are skipped.
     */
    suspend fun parse(inputStream: InputStream): List<EpgProgram> {
        val programs = mutableListOf<EpgProgram>()
        parseInto(inputStream) { programs.add(it) }
        return programs
    }

    /** Retains only the preferred live/upcoming program per channel, not the full guide. */
    suspend fun parseCurrentOrNext(inputStream: InputStream, now: Long = System.currentTimeMillis()): Map<String, EpgProgram> {
        val selected = mutableMapOf<String, EpgProgram>()
        parseInto(inputStream) { candidate ->
            if (candidate.endTime > now) {
                val existing = selected[candidate.channelId]
                val candidateLive = candidate.startTime <= now
                val existingLive = existing != null && existing.startTime <= now
                val preferred = existing == null ||
                    (candidateLive && !existingLive) ||
                    (candidateLive && existingLive && candidate.startTime > existing.startTime) ||
                    (!candidateLive && !existingLive && candidate.startTime < existing.startTime)
                if (preferred) selected[candidate.channelId] = candidate
            }
        }
        return selected.toMap()
    }

    private suspend fun parseInto(inputStream: InputStream, onProgram: (EpgProgram) -> Unit) = inputStream.use { stream ->
        withContext(Dispatchers.IO) {
            val context = coroutineContext
            val handler = ProgramHandler(onProgram) { context.ensureActive() }
            val reader = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
                .newSAXParser().xmlReader
            reader.contentHandler = handler
            reader.errorHandler = handler
            reader.entityResolver = handler
            // Reject DTDs before their declarations or entities can be processed.
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
            context.ensureActive()
            val buffered = BufferedInputStream(stream)
            buffered.mark(2)
            val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
            buffered.reset()
            val xmlStream = if (gzip) GZIPInputStream(buffered) else buffered
            xmlStream.use { reader.parse(InputSource(it)) }
        }
    }

    private class ProgramHandler(private val onProgram: (EpgProgram) -> Unit, private val checkActive: () -> Unit) : DefaultHandler2() {
        private var depth = 0
        private var programDepth = -1
        private var fieldDepth = -1
        private var field: String? = null
        private val text = StringBuilder()
        private var channel = ""
        private var start: Long? = null
        private var stop: Long? = null
        private var title: String? = null
        private var description: String? = null
        private var category: String? = null
        private val formats = listOf("yyyyMMddHHmmss", "yyyyMMddHHmm", "yyyyMMddHH", "yyyyMMdd", "yyyyMM", "yyyy")
            .associateBy({ it.length }, { pattern ->
                SimpleDateFormat("$pattern Z", Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            })

        override fun startDTD(name: String?, publicId: String?, systemId: String?) {
            throw SAXException("XMLTV documents containing a DTD are not supported")
        }

        override fun resolveEntity(publicId: String?, systemId: String?): InputSource {
            throw SAXException("External XML entities are not supported")
        }

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            checkActive()
            depth++
            val name = localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty()
            if (depth == 1 && name != "tv") throw SAXException("Expected an XMLTV tv document")
            if (name == "programme" && programDepth == -1) {
                programDepth = depth
                channel = attributes.getValue("channel")?.trim().orEmpty()
                start = parseTime(attributes.getValue("start"))
                stop = parseTime(attributes.getValue("stop"))
                title = null
                description = null
                category = null
            } else if (programDepth != -1 && depth == programDepth + 1 && name in setOf("title", "desc", "category")) {
                field = name
                fieldDepth = depth
                text.setLength(0)
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            checkActive()
            if (field != null) text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            checkActive()
            if (depth == fieldDepth) {
                val value = text.toString().trim().takeIf { it.isNotEmpty() }
                when (field) {
                    "title" -> if (title == null) title = value
                    "desc" -> if (description == null) description = value
                    "category" -> if (category == null) category = value
                }
                field = null
                fieldDepth = -1
            }
            if (depth == programDepth) {
                val begin = start
                val end = stop
                val programTitle = title
                if (channel.isNotBlank() && programTitle != null && begin != null && end != null && end > begin) {
                    onProgram(EpgProgram(channel, programTitle, description, begin, end, category))
                }
                programDepth = -1
            }
            depth--
        }

        private fun parseTime(value: String?): Long? {
            // XMLTV allows truncated dates. An omitted timezone means UTC.
            val match = TIME.matchEntire(value?.trim().orEmpty()) ?: return null
            val digits = match.groupValues[1]
            val format = formats[digits.length] ?: return null
            val zone = match.groupValues[2].ifEmpty { "+0000" }
            val timestamp = "$digits $zone"
            val position = ParsePosition(0)
            val date = format.parse(timestamp, position) ?: return null
            return date.time.takeIf { position.index == timestamp.length }
        }
    }

    private companion object {
        val TIME = Regex("(\\d{4}(?:\\d{2}){0,5})(?:\\s+([+-]\\d{4}))?")
    }
}
