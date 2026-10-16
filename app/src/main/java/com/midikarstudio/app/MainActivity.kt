package com.midikarstudio.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import dev.kotlinds.fluidsynthkmp.AudioConfig
import dev.kotlinds.fluidsynthkmp.MidiFilePlayer

class MainActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var selectedFileInfo: MidiFileInfo? = null
    private var selectedMidiPath: String? = null
    private var selectedSoundFontPath: String? = null
    private var selectedSoundFontName: String? = null
    private var midiPlayer: MidiFilePlayer? = null
    private var playbackProgress: TextView? = null
    private var playbackSeekBar: SeekBar? = null
    private var isUserSeeking = false
    private var currentLyricView: TextView? = null
    private val playbackHandler = Handler(Looper.getMainLooper())
    private val playbackTicker = object : Runnable {
        override fun run() {
            val player = midiPlayer ?: return
            val tick = try { player.currentTick } catch (_: Exception) { 0L }
            val total = try { player.durationTicks } catch (_: Exception) { 0L }
            playbackProgress?.text = if (total > 0L) {
                "Position: $tick / $total ticks  (${(tick * 100 / total).coerceIn(0L, 100L)}%)"
            } else "Position: $tick ticks"
            if (!isUserSeeking && total > 0L) playbackSeekBar?.progress = ((tick.coerceIn(0L, total) * 1000L) / total).toInt()
            val lyric = selectedFileInfo?.lyricEvents?.lastOrNull { it.tick <= tick }?.text
            currentLyricView?.text = lyric?.takeIf { it.isNotBlank() } ?: "No lyric event at this position"
            if (player.isPlaying) playbackHandler.postDelayed(this, 100L)
        }
    }

    enum class Tab { PROJECT, LYRICS, PIANO_ROLL, TRACKS }

    data class TrackInfo(
        val index: Int,
        val name: String,
        val events: Int,
        val notes: Int,
        val channels: String,
        val instruments: String,
        val lyrics: List<String>
    )

    data class MidiFileInfo(
        val name: String,
        val sizeBytes: Long,
        val format: Int?,
        val trackCount: Int?,
        val division: Int?,
        val headerValid: Boolean,
        val message: String,
        val tracks: List<TrackInfo> = emptyList(),
        val tempoUs: Int? = null,
        val totalTicks: Long = 0,
        val parseError: String? = null,
        val metadata: Map<String, List<String>> = emptyMap(),
        val lyricEvents: List<LyricEvent> = emptyList()
    )

    data class LyricEvent(val tick: Long, val text: String)

    companion object { private const val REQUEST_OPEN_MIDI = 4101; private const val REQUEST_OPEN_SOUNDFONT = 4102 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showShell()
        renderTab(Tab.PROJECT)
    }

    private fun showShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            setBackgroundColor(0xFF101318.toInt())
        }
        root.addView(TextView(this).apply {
            text = "MIDI/KAR Studio"; textSize = 25f; setTextColor(0xFFF5F7FA.toInt())
        })
        root.addView(TextView(this).apply {
            text = "Create • Edit • Play • Export"; textSize = 14f
            setTextColor(0xFFADB7C4.toInt()); setPadding(0, dp(4), 0, dp(14))
        })
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Project" to Tab.PROJECT, "Lyrics" to Tab.LYRICS, "Piano Roll" to Tab.PIANO_ROLL, "Tracks" to Tab.TRACKS).forEach { (label, tab) ->
            tabs.addView(Button(this).apply {
                text = label; isAllCaps = false; setOnClickListener { renderTab(tab) }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        root.addView(tabs)
        status = TextView(this).apply {
            text = "Milestone 2 • MIDI/KAR event inspection"; textSize = 12f
            setTextColor(0xFF7EDCC2.toInt()); setPadding(0, dp(10), 0, dp(8))
        }
        root.addView(status)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, dp(8)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun renderTab(tab: Tab) {
        content.removeAllViews()
        addText(when(tab) { Tab.PROJECT -> "Project Home"; Tab.LYRICS -> "Lyrics & Karaoke Text"; Tab.PIANO_ROLL -> "Piano Roll"; Tab.TRACKS -> "Tracks & Events" }, 22f, 0xFFF5F7FA.toInt())
        val info = selectedFileInfo
        when (tab) {
            Tab.PROJECT -> {
                addText("Open a MIDI/KAR file to inspect its musical events.", 16f, 0xFFD6DCE5.toInt())
                addAction("Open MIDI / KAR file") { openMidiFile() }
                addAction("Choose SoundFont (.sf2)") { openSoundFont() }
                addText("SoundFont: ${selectedSoundFontName ?: "Not selected"}", 15f, 0xFF7EDCC2.toInt())
                addAction("Play MIDI") { startPlayback() }
                addAction("Pause") { controlPlayback("pause") }
                addAction("Resume") { controlPlayback("resume") }
                addAction("Stop") { controlPlayback("stop") }
                playbackProgress = TextView(this).apply {
                    text = "Position: —"; textSize = 14f
                    setTextColor(0xFFD6DCE5.toInt()); setPadding(0, dp(6), 0, dp(6))
                }
                content.addView(playbackProgress)
                playbackSeekBar = SeekBar(this).apply {
                    max = 1000
                    contentDescription = "MIDI playback position"
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onStartTrackingTouch(seekBar: SeekBar) { isUserSeeking = true }
                        override fun onStopTrackingTouch(seekBar: SeekBar) {
                            isUserSeeking = false
                            seekPlaybackToProgress(seekBar.progress)
                        }
                        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                            if (fromUser) {
                                val total = try { midiPlayer?.durationTicks ?: selectedFileInfo?.totalTicks ?: 0L } catch (_: Exception) { 0L }
                                if (total > 0L) playbackProgress?.text = "Seek to: ${total * progress / 1000L} / $total ticks"
                            }
                        }
                    })
                }
                content.addView(playbackSeekBar, LinearLayout.LayoutParams(-1, dp(44)))
                addText("Drag the bar to seek through the MIDI.", 13f, 0xFFADB7C4.toInt())
                addText("Current lyric event", 17f, 0xFF7EDCC2.toInt())
                currentLyricView = TextView(this).apply {
                    text = "Load a MIDI/KAR file to preview its timed lyrics."
                    textSize = 22f; setTextColor(0xFFF5F7FA.toInt())
                    setPadding(0, dp(8), 0, dp(14))
                }
                content.addView(currentLyricView)
                addAction("Create new project") {
                    status.text = "New-project editing is not implemented yet."
                    Toast.makeText(this, "New-project editing is a future milestone.", Toast.LENGTH_SHORT).show()
                }
                if (info != null) showSummary(info)
                addText("Next milestones: playback with SoundFont, editable lyrics, piano roll and export.", 14f, 0xFFFFD28A.toInt())
            }
            Tab.TRACKS -> {
                if (info == null) addText("Open a MIDI/KAR file first.", 16f, 0xFFD6DCE5.toInt())
                else {
                    showSummary(info)
                    info.tracks.forEach { track ->
                        addText("Track ${track.index + 1}: ${track.name}", 17f, 0xFF7EDCC2.toInt())
                        addText("Events: ${track.events} • Note-on events: ${track.notes}\nChannels: ${track.channels}\nPrograms: ${track.instruments}", 14f, 0xFFD6DCE5.toInt())
                    }
                    if (info.parseError != null) addText("Parser note: ${info.parseError}", 14f, 0xFFFFD28A.toInt())
                }
                addAction("Open another file") { openMidiFile() }
            }
            Tab.LYRICS -> {
                if (info == null) addText("Open a MIDI/KAR file first. This view currently extracts embedded lyric/text events; editing and timing are not implemented yet.", 15f, 0xFFD6DCE5.toInt())
                else {
                    addText("Embedded lyric/text events", 18f, 0xFF7EDCC2.toInt())
                    val lines = info.tracks.flatMap { t -> t.lyrics.map { "Track ${t.index + 1}: $it" } }
                    if (lines.isEmpty()) addText("No lyric/text meta-events were found in this file.", 15f, 0xFFD6DCE5.toInt())
                    else lines.take(300).forEach { addText(it, 14f, 0xFFF5F7FA.toInt()) }
                    if (lines.size > 300) addText("Showing first 300 of ${lines.size} lyric/text events.", 13f, 0xFFADB7C4.toInt())
                }
                addAction("Open MIDI / KAR file") { openMidiFile() }
            }
            Tab.PIANO_ROLL -> {
                addText("Note events are being counted in this milestone; the editable piano-roll canvas is not implemented yet.", 15f, 0xFFD6DCE5.toInt())
                if (info != null) info.tracks.forEach { addText("${it.name}: ${it.notes} note-on events", 14f, 0xFF7EDCC2.toInt()) }
                addAction("Open MIDI / KAR file") { openMidiFile() }
            }
        }
    }

    private fun showSummary(info: MidiFileInfo) {
        addText("Selected: ${info.name}", 17f, 0xFFF5F7FA.toInt())
        addText("Size ${formatBytes(info.sizeBytes)} • Format ${info.format ?: "?"} • ${info.trackCount ?: "?"} track(s)", 14f, 0xFFD6DCE5.toInt())
        val division = info.division ?: 0
        addText(if ((division and 0x8000) == 0) "Timing division: $division ticks/quarter note" else "Timing division: SMPTE ($division)", 14f, 0xFFD6DCE5.toInt())
        info.tempoUs?.let { addText("First tempo event: ${"%.2f".format(60000000.0 / it)} BPM", 14f, 0xFFD6DCE5.toInt()) }
        addText("Parsed tracks: ${info.tracks.size} • Total ticks: ${info.totalTicks}", 14f, 0xFFD6DCE5.toInt())
        addText("Embedded MIDI metadata", 18f, 0xFF7EDCC2.toInt())
        val metadataLabels = linkedMapOf(
            "copyright" to "Copyright",
            "trackName" to "Track names",
            "instrumentName" to "Instrument names",
            "text" to "Embedded text",
            "marker" to "Markers",
            "cue" to "Cue points"
        )
        var shownMetadata = false
        metadataLabels.forEach { (key, label) ->
            val values = info.metadata[key].orEmpty().distinct().filter { it.isNotBlank() }
            if (values.isNotEmpty()) {
                shownMetadata = true
                addText("$label: ${values.take(8).joinToString(" • ")}", 14f, 0xFFD6DCE5.toInt())
            }
        }
        addText("Song title / artist: not identified from this file's metadata. The selected filename is shown above; track names are not treated as the song title.", 14f, 0xFFFFD28A.toInt())
        if (!shownMetadata) addText("No common copyright/text metadata was found. Track names and karaoke lyrics, if present, appear in their respective tabs.", 14f, 0xFFFFD28A.toInt())
        if (!info.headerValid) addText("This file did not pass the basic MIDI header check.", 14f, 0xFFFFD28A.toInt())
    }

    private fun openSoundFont() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "audio/x-soundfont"))
        }
        try { startActivityForResult(intent, REQUEST_OPEN_SOUNDFONT) }
        catch (e: Exception) { status.text = "Could not open SoundFont picker: ${e.localizedMessage ?: "unknown error"}" }
    }

    private fun startPlayback() {
        val midiPath = selectedMidiPath
        val soundFontPath = selectedSoundFontPath
        if (midiPath == null) {
            Toast.makeText(this, "Open a MIDI/KAR file first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (soundFontPath == null) {
            Toast.makeText(this, "Choose a .sf2 SoundFont first.", Toast.LENGTH_LONG).show()
            openSoundFont()
            return
        }
        try {
            midiPlayer?.close()
            midiPlayer = MidiFilePlayer(soundFontPath, midiPath, AudioConfig())
            midiPlayer?.play()
            status.text = "Playing ${selectedFileInfo?.name ?: "MIDI"}"
            playbackHandler.removeCallbacks(playbackTicker)
            playbackHandler.post(playbackTicker)
        } catch (e: Exception) {
            midiPlayer = null
            status.text = "Playback failed: ${e.localizedMessage ?: "engine error"}"
            Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
        }
    }

    private fun seekPlaybackToProgress(progress: Int) {
        val player = midiPlayer
        if (player == null) {
            Toast.makeText(this, "Play a MIDI before seeking.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val total = player.durationTicks
            if (total > 0L) {
                val targetTick = (total * progress.coerceIn(0, 1000) / 1000L).coerceIn(0L, total)
                player.seekTo(targetTick)
                playbackTicker.run()
            }
        } catch (e: Exception) {
            status.text = "Could not seek: ${e.localizedMessage ?: "unknown error"}"
            Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
        }
    }

    private fun controlPlayback(action: String) {
        val player = midiPlayer
        if (player == null) {
            Toast.makeText(this, "Press Play MIDI first.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            when (action) {
                "pause" -> { player.pause(); status.text = "Playback paused"; playbackHandler.removeCallbacks(playbackTicker); playbackTicker.run() }
                "resume" -> { player.play(); status.text = "Playback resumed"; playbackHandler.removeCallbacks(playbackTicker); playbackHandler.post(playbackTicker) }
                "stop" -> { player.stop(); status.text = "Playback stopped"; playbackHandler.removeCallbacks(playbackTicker); playbackTicker.run() }
            }
        } catch (e: Exception) {
            status.text = "Playback control failed: ${e.localizedMessage ?: "unknown error"}"
        }
    }

    private fun openMidiFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/midi", "audio/x-midi", "application/octet-stream", "application/x-midi"))
        }
        try { startActivityForResult(intent, REQUEST_OPEN_MIDI) }
        catch (e: Exception) { status.text = "Could not open file picker: ${e.localizedMessage ?: "unknown error"}" }
    }

    @Deprecated("Uses compatible activity-result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        if (requestCode == REQUEST_OPEN_SOUNDFONT) {
            try {
                val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "SoundFont.sf2"
                if (!name.lowercase().let { it.endsWith(".sf2") || it.endsWith(".sf3") }) {
                    status.text = "Choose a .sf2 or .sf3 SoundFont file."
                    return
                }
                val target = File(filesDir, "selected-soundfont-${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
                contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                    ?: run { status.text = "Could not read SoundFont."; return }
                selectedSoundFontPath = target.absolutePath
                selectedSoundFontName = name
                status.text = "SoundFont ready: $name"
                renderTab(Tab.PROJECT)
            } catch (e: Exception) {
                status.text = "Could not load SoundFont: ${e.localizedMessage ?: "unknown error"}"
            }
            return
        }
        if (requestCode != REQUEST_OPEN_MIDI) return
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "Selected file"
                if (!name.lowercase().let { it.endsWith(".mid") || it.endsWith(".midi") || it.endsWith(".kar") }) {
                    status.text = "Choose a .mid, .midi, or .kar file."; return
                }
                val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val midiCopy = File(cacheDir, "current-$safeName")
                midiCopy.writeBytes(bytes)
                selectedMidiPath = midiCopy.absolutePath
                playbackHandler.removeCallbacks(playbackTicker)
                midiPlayer?.close()
                midiPlayer = null
                selectedFileInfo = parseMidi(name, bytes)
                status.text = if (selectedFileInfo?.headerValid == true) "File parsed • check Tracks and Lyrics tabs" else "File opened • parser reported a header issue"
                renderTab(Tab.PROJECT)
            } ?: run { status.text = "Could not read selected file." }
        } catch (e: Exception) {
            status.text = "Could not parse file: ${e.localizedMessage ?: "unknown error"}"
            Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
        }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Exception) { null }

    private fun parseMidi(name: String, bytes: ByteArray): MidiFileInfo {
        fun u8(i: Int) = bytes[i].toInt() and 0xFF
        fun be32(i: Int): Long = ((u8(i).toLong() shl 24) or (u8(i+1).toLong() shl 16) or (u8(i+2).toLong() shl 8) or u8(i+3).toLong())
        if (bytes.size < 14 || String(bytes, 0, 4, Charsets.US_ASCII) != "MThd") {
            return MidiFileInfo(name, bytes.size.toLong(), null, null, null, false, "Invalid or truncated MIDI header.")
        }
        val headerLen = be32(4).toInt()
        if (headerLen < 6 || 8L + headerLen > bytes.size) return MidiFileInfo(name, bytes.size.toLong(), null, null, null, false, "Invalid MIDI header length.")
        val format = (u8(8) shl 8) or u8(9)
        val declaredTracks = (u8(10) shl 8) or u8(11)
        val division = (u8(12) shl 8) or u8(13)
        var pos = 8 + headerLen
        val tracks = mutableListOf<TrackInfo>()
        var firstTempo: Int? = null
        var maxTicks = 0L
        val allLyricEvents = mutableListOf<LyricEvent>()
        var error: String? = null
        val metadata = linkedMapOf<String, MutableList<String>>()
        repeat(declaredTracks) { trackIndex ->
            if (pos + 8 > bytes.size) { error = "File ended before all declared tracks were found."; return@repeat }
            if (String(bytes, pos, 4, Charsets.US_ASCII) != "MTrk") { error = "Expected MTrk at byte $pos."; return@repeat }
            val lenLong = be32(pos + 4)
            if (lenLong > Int.MAX_VALUE || pos.toLong() + 8L + lenLong > bytes.size) { error = "Track ${trackIndex + 1} has an invalid length."; return@repeat }
            val start = pos + 8
            val end = start + lenLong.toInt()
            pos = end
            var p = start
            var tick = 0L
            var runningStatus = 0
            var eventCount = 0
            var noteCount = 0
            val channels = linkedSetOf<Int>()
            val programs = linkedSetOf<Int>()
            val lyrics = mutableListOf<String>()
            var trackName = "Track ${trackIndex + 1}"
            fun readVlq(): Long {
                var value = 0L
                var count = 0
                while (p < end && count < 4) {
                    val b = u8(p++); value = (value shl 7) or (b and 0x7F).toLong(); count++
                    if ((b and 0x80) == 0) return value
                }
                throw IllegalArgumentException("Invalid variable-length quantity")
            }
            try {
                while (p < end) {
                    tick += readVlq()
                    if (p >= end) break
                    var statusByte = u8(p)
                    if (statusByte < 0x80) {
                        if (runningStatus == 0) throw IllegalArgumentException("Missing running status")
                        statusByte = runningStatus
                    } else p++
                    if (statusByte in 0x80..0xEF) {
                        runningStatus = statusByte
                        val command = statusByte and 0xF0
                        val channel = statusByte and 0x0F
                        channels.add(channel + 1)
                        val data1 = if (p < end && u8(p) < 0x80) u8(p++) else throw IllegalArgumentException("Truncated channel event")
                        val data2Needed = command != 0xC0 && command != 0xD0
                        val data2 = if (data2Needed) { if (p < end && u8(p) < 0x80) u8(p++) else throw IllegalArgumentException("Truncated channel event") } else 0
                        if (command == 0x90 && data2 > 0) noteCount++
                        if (command == 0xC0) programs.add(data1 + 1)
                        eventCount++
                    } else if (statusByte == 0xFF) {
                        runningStatus = 0
                        if (p >= end) break
                        val type = u8(p++)
                        val length = readVlq().toInt()
                        if (length < 0 || p + length > end) throw IllegalArgumentException("Truncated meta event")
                        val payload = bytes.copyOfRange(p, p + length); p += length
                        val textValue = payload.toString(Charsets.UTF_8).trim().replace("\u0000", "")
                        when (type) {
                            0x01 -> if (textValue.isNotBlank()) { lyrics.add(textValue); allLyricEvents.add(LyricEvent(tick, textValue)); metadata.getOrPut("text") { mutableListOf() }.add(textValue) }
                            0x02 -> if (textValue.isNotBlank()) metadata.getOrPut("copyright") { mutableListOf() }.add(textValue)
                            0x03 -> if (textValue.isNotBlank()) { trackName = textValue; metadata.getOrPut("trackName") { mutableListOf() }.add("Track ${trackIndex + 1}: $textValue") }
                            0x04 -> if (textValue.isNotBlank()) metadata.getOrPut("instrumentName") { mutableListOf() }.add("Track ${trackIndex + 1}: $textValue")
                            0x05 -> if (textValue.isNotBlank()) { lyrics.add(textValue); allLyricEvents.add(LyricEvent(tick, textValue)) }
                            0x06 -> if (textValue.isNotBlank()) metadata.getOrPut("marker") { mutableListOf() }.add(textValue)
                            0x07 -> if (textValue.isNotBlank()) metadata.getOrPut("cue") { mutableListOf() }.add(textValue)
                            0x51 -> if (payload.size == 3 && firstTempo == null) firstTempo = (u8(p-length) shl 16) or (u8(p-length+1) shl 8) or u8(p-length+2)
                        }
                        eventCount++
                        if (type == 0x2F) break
                    } else if (statusByte == 0xF0 || statusByte == 0xF7) {
                        runningStatus = 0
                        val length = readVlq().toInt()
                        if (length < 0 || p + length > end) throw IllegalArgumentException("Truncated SysEx event")
                        p += length; eventCount++
                    } else {
                        throw IllegalArgumentException("Unsupported system status 0x${statusByte.toString(16)}")
                    }
                }
            } catch (e: Exception) { error = "Track ${trackIndex + 1}: ${e.message ?: "parse error"}" }
            maxTicks = maxOf(maxTicks, tick)
            tracks.add(TrackInfo(trackIndex, trackName, eventCount, noteCount, if (channels.isEmpty()) "—" else channels.joinToString(), if (programs.isEmpty()) "—" else programs.joinToString(), lyrics))
        }
        val valid = format in 0..2 && declaredTracks > 0 && division != 0
        return MidiFileInfo(name, bytes.size.toLong(), format, declaredTracks, division, valid,
            "Format $format • $declaredTracks track(s) • division $division", tracks, firstTempo, maxTicks, error,
            metadata.mapValues { it.value.distinct() }, allLyricEvents.sortedBy { it.tick })
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    }

    private fun addText(value: String, size: Float, color: Int) {
        content.addView(TextView(this).apply { text = value; textSize = size; setTextColor(color); setPadding(0, dp(8), 0, dp(8)) })
    }

    private fun addAction(label: String, action: () -> Unit) {
        content.addView(Button(this).apply { text = label; isAllCaps = false; gravity = Gravity.CENTER; setOnClickListener { action() } },
            LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
    }

    override fun onDestroy() {
        playbackHandler.removeCallbacks(playbackTicker)
        try { midiPlayer?.stop(); midiPlayer?.close() } catch (_: Exception) { }
        midiPlayer = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
