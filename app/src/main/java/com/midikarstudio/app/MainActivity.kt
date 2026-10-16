package com.midikarstudio.app

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var status: TextView

    enum class Tab { PROJECT, LYRICS, PIANO_ROLL, TRACKS }

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
            text = "Milestone 1 • Project foundation"
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
                addText("Your workspace for MIDI and karaoke files.", 16f, 0xFFD6DCE5.toInt())
                addAction("Open MIDI / KAR file") {
                    status.text = "File import is the next implementation step."
                }
                addAction("Create new project") {
                    status.text = "New-project data model is being prepared."
                }
                addText("First milestone", 18f, 0xFF7EDCC2.toInt())
                addText(
                    "1. Import and inspect real MIDI/KAR files\n2. Play with a built-in SoundFont\n3. Export a valid copy\n4. Add lyric timing and piano-roll editing",
                    15f,
                    0xFFD6DCE5.toInt()
                )
                addText(
                    "This starter build validates the app shell. MIDI parsing, synthesis and export are not implemented yet.",
                    14f,
                    0xFFFFD28A.toInt()
                )
            }
            Tab.LYRICS -> {
                addText("Lyric editing and timing will live here.", 16f, 0xFFD6DCE5.toInt())
                addText(
                    "Planned: lyric-event list, text entry, playback-follow, timing adjustment and KAR export.",
                    14f,
                    0xFFADB7C4.toInt()
                )
            }
            Tab.PIANO_ROLL -> {
                addText("Touch-friendly note editor planned for this tab.", 16f, 0xFFD6DCE5.toInt())
                addText(
                    "Planned: piano keyboard, beat grid, add/move/resize/delete notes, quantization and velocity.",
                    14f,
                    0xFFADB7C4.toInt()
                )
            }
            Tab.TRACKS -> {
                addText("Track and event inspection planned for this tab.", 16f, 0xFFD6DCE5.toInt())
                addText(
                    "Planned: track names, channels, instruments, tempo map, note counts and lyric events.",
                    14f,
                    0xFFADB7C4.toInt()
                )
            }
        }
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
