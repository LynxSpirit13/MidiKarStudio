package com.midikarstudio.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException

class MainActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var selectedFileInfo: MidiFileInfo? = null

    enum class Tab { PROJECT, LYRICS, PIANO_ROLL, TRACKS }

    data class MidiFileInfo(
        val name: String,
        val sizeBytes: Long,
        val format: Int?,
        val trackCount: Int?,
        val division: Int?,
        val headerValid: Boolean,
        val message: String
    )

    companion object {
        private const val REQUEST_OPEN_MIDI = 4101
    }

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
        val title = TextView(this).apply {
            text = "MIDI/KAR Studio"
            textSize = 25f
            setTextColor(0xFFF5F7FA.toInt())
        }
        val subtitle = TextView(this).apply {
            text = "Create • Edit • Play • Export"
            textSize = 14f
            setTextColor(0xFFADB7C4.toInt())
            setPadding(0, dp(4), 0, dp(14))
        }
        root.addView(title)
        root.addView(subtitle)

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            "Project" to Tab.PROJECT,
            "Lyrics" to Tab.LYRICS,
            "Piano Roll" to Tab.PIANO_ROLL,
            "Tracks" to Tab.TRACKS
        ).forEach { (label, tab) ->
            tabs.addView(Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { renderTab(tab) }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        root.addView(tabs)

        status = TextView(this).apply {
            text = "Milestone 1 • MIDI/KAR file inspection"
            textSize = 12f
            setTextColor(0xFF7EDCC2.toInt())
            setPadding(0, dp(10), 0, dp(8))
        }
        root.addView(status)

        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(8))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun renderTab(tab: Tab) {
        content.removeAllViews()
        val heading = when (tab) {
            Tab.PROJECT -> "Project Home"
            Tab.LYRICS -> "Lyrics Editor"
            Tab.PIANO_ROLL -> "Piano Roll"
            Tab.TRACKS -> "Tracks & Events"
        }
        addText(heading, 22f, 0xFFF5F7FA.toInt())
        when (tab) {
            Tab.PROJECT -> {
                addText("Open a MIDI or karaoke file to inspect its basic structure.", 16f, 0xFFD6DCE5.toInt())
                addAction("Open MIDI / KAR file") { openMidiFile() }
                addAction("Create new project") {
                    Toast.makeText(this, "New-project editing is not implemented yet.", Toast.LENGTH_SHORT).show()
                    status.text = "New-project editing is a future milestone."
                }
                selectedFileInfo?.let { showFileInfo(it) }
                addText("Current milestone", 18f, 0xFF7EDCC2.toInt())
                addText(
                    "Working now: file picker and basic Standard MIDI File header inspection.\nNext: parse tracks, notes, tempo and karaoke lyric events.",
                    15f,
                    0xFFD6DCE5.toInt()
                )
                addText(
                    "Playback, SoundFont synthesis, lyric editing, piano-roll editing and export are not implemented yet.",
                    14f,
                    0xFFFFD28A.toInt()
                )
            }
            Tab.LYRICS -> {
                addText("Lyric editing will be enabled after MIDI/KAR event parsing is implemented.", 16f, 0xFFD6DCE5.toInt())
                selectedFileInfo?.let { addText("Selected file: ${it.name}", 14f, 0xFF7EDCC2.toInt()) }
            }
            Tab.PIANO_ROLL -> {
                addText("The touch-friendly note editor is not implemented yet.", 16f, 0xFFD6DCE5.toInt())
                selectedFileInfo?.let { addText("Selected file: ${it.name}", 14f, 0xFF7EDCC2.toInt()) }
            }
            Tab.TRACKS -> {
                addText("Basic file-header inspection is available below.", 16f, 0xFFD6DCE5.toInt())
                selectedFileInfo?.let { showFileInfo(it) }
            }
        }
    }

    private fun openMidiFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "audio/midi",
                "audio/x-midi",
                "application/octet-stream",
                "application/x-midi"
            ))
        }
        try {
            startActivityForResult(intent, REQUEST_OPEN_MIDI)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open the file picker.", Toast.LENGTH_LONG).show()
            status.text = "File picker could not be opened: ${e.localizedMessage ?: "unknown error"}"
        }
    }

    @Deprecated("Uses the compatible activity-result callback for this starter app")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_OPEN_MIDI || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                val name = queryDisplayName(uri) ?: (uri.lastPathSegment ?: "Selected file")
                val lower = name.lowercase()
                if (!(lower.endsWith(".mid") || lower.endsWith(".midi") || lower.endsWith(".kar"))) {
                    status.text = "Unsupported extension. Choose a .mid, .midi, or .kar file."
                    Toast.makeText(this, "Please choose a .mid, .midi, or .kar file.", Toast.LENGTH_LONG).show()
                    return
                }
                val info = inspectMidiHeader(name, bytes)
                selectedFileInfo = info
                status.text = if (info.headerValid) "File opened • header looks valid" else "File opened • header needs checking"
                renderTab(Tab.PROJECT)
            } ?: run {
                status.text = "Could not read the selected file."
            }
        } catch (e: Exception) {
            status.text = "Could not read file: ${e.localizedMessage ?: "unknown error"}"
            Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun inspectMidiHeader(name: String, bytes: ByteArray): MidiFileInfo {
        if (bytes.size < 14) {
            return MidiFileInfo(name, bytes.size.toLong(), null, null, null, false, "File is too short to contain a standard MIDI header.")
        }
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val magic = ByteArray(4)
        input.readFully(magic)
        if (String(magic, Charsets.US_ASCII) != "MThd") {
            return MidiFileInfo(name, bytes.size.toLong(), null, null, null, false, "Header signature MThd was not found. This may not be a Standard MIDI File.")
        }
        val headerLength = input.readInt()
        if (headerLength < 6 || bytes.size < 14) {
            return MidiFileInfo(name, bytes.size.toLong(), null, null, null, false, "MIDI header length is invalid.")
        }
        val format = input.readUnsignedShort()
        val tracks = input.readUnsignedShort()
        val division = input.readUnsignedShort()
        val divisionText = if ((division and 0x8000) == 0) {
            "${division} ticks per quarter note"
        } else {
            "SMPTE timing (${division})"
        }
        return MidiFileInfo(
            name = name,
            sizeBytes = bytes.size.toLong(),
            format = format,
            trackCount = tracks,
            division = division,
            headerValid = format in 0..2 && tracks > 0 && division != 0,
            message = "Format ${format} • ${tracks} track(s) • ${divisionText}"
        )
    }

    private fun showFileInfo(info: MidiFileInfo) {
        addText("Selected file", 18f, 0xFF7EDCC2.toInt())
        addText(info.name, 16f, 0xFFF5F7FA.toInt())
        addText("Size: ${formatBytes(info.sizeBytes)}", 14f, 0xFFD6DCE5.toInt())
        addText(info.message, 14f, 0xFFD6DCE5.toInt())
        addText(
            if (info.headerValid) "Basic header check passed. Note and lyric events have not been parsed yet."
            else "Header check did not pass. The file may be damaged, unsupported, or not a standard MIDI file.",
            14f,
            if (info.headerValid) 0xFF7EDCC2.toInt() else 0xFFFFD28A.toInt()
        )
        addAction("Choose another file") { openMidiFile() }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    }

    private fun addText(value: String, size: Float, color: Int) {
        content.addView(TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            setPadding(0, dp(8), 0, dp(8))
        })
    }

    private fun addAction(label: String, action: () -> Unit) {
        content.addView(Button(this).apply {
            text = label
            isAllCaps = false
            gravity = Gravity.CENTER
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply {
            bottomMargin = dp(8)
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
