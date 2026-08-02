package com.example.midigameengine

import android.net.Uri
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.roundToInt
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import core.visualization.KeyboardProfile
import core.visualization.KeyboardZoom
import core.visualization.PitchRange
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var controller: TeachingSessionController
    private lateinit var visualizerView: TeachingVisualizerView
    private lateinit var timelineSeekBar: SeekBar
    private lateinit var timeLabel: TextView
    private lateinit var playPauseButton: Button
    private lateinit var speedButton: Button
    private lateinit var trimButton: Button
    private lateinit var optionsPanel: View
    private lateinit var optionsToggleButton: Button
    private var userScrubbing = false
    private var isPlaying = false
    private var optionsExpanded = true
    private var latestState: TeachingUiState? = null
    private var activityActive = false

    private val importMidiLauncher =
        registerForActivityResult(object : ActivityResultContract<Array<String>, Uri?>() {
            override fun createIntent(context: android.content.Context, input: Array<String>): Intent {
                return Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_MIME_TYPES, input)
                    .apply {
                        controller.lastPickerUri()?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
                    }
            }

            override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
                return if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
            }
        }) { uri: Uri? ->
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                controller.importMidi(uri, resolveDisplayName(uri))
            }
        }

    private val exportLogsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                contentResolver.openOutputStream(uri)?.use { output: OutputStream ->
                    output.write(AppDebugLogger.exportText(latestState).toByteArray(Charsets.UTF_8))
                } ?: error("Unable to open export destination")
                AppDebugLogger.log("Diagnostic export completed: $uri")
            }.onFailure { error ->
                AppDebugLogger.log("Diagnostic export failed", error)
                android.widget.Toast.makeText(this, "Could not export logs", android.widget.Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        super.onCreate(savedInstanceState)
        AppDebugLogger.initialize(this)
        AppDebugLogger.log("onCreate orientation=${resources.configuration.orientation} saved=${savedInstanceState != null}")
        optionsExpanded = savedInstanceState?.getBoolean("optionsExpanded", true) ?: true

        visualizerView = TeachingVisualizerView(this)
        controller = TeachingSessionController(
            context = this,
            onStateChanged = { state ->
                if (!activityActive) return@TeachingSessionController
                latestState = state
                AppDebugLogger.logState(state)
                visualizerView.submitState(state)
                if (::timelineSeekBar.isInitialized && !userScrubbing) {
                    val duration = state.playbackEndUs - state.playbackStartUs
                    timelineSeekBar.progress = if (duration <= 0L) 0 else {
                        (((state.playbackTimeUs - state.playbackStartUs).toDouble() / duration) * timelineSeekBar.max)
                            .toInt()
                            .coerceIn(0, timelineSeekBar.max)
                    }
                    timeLabel.text = "${formatTime(state.playbackTimeUs - state.playbackStartUs)} / ${formatTime(duration)}"
                }
                if (::speedButton.isInitialized) speedButton.text = "${"%.2f".format(state.speed)}x"
                if (::trimButton.isInitialized) {
                    trimButton.text = if (state.autoTrimEnabled) {
                        "Trim: ${state.trimPaddingMs}ms"
                    } else {
                        "Trim: Off"
                    }
                }
                isPlaying = state.isPlaying
                if (::playPauseButton.isInitialized) playPauseButton.text = if (state.isPlaying) "Pause" else "Play"
            },
            onTrackSelectionRequired = { choices ->
                showTrackSelectionDialog(choices)
            }
        )

        val importButton = Button(this).apply {
            text = "Import MIDI"
            contentDescription = "Import a MIDI file"
            setOnClickListener {
                importMidiLauncher.launch(
                    arrayOf(
                        "audio/midi",
                        "audio/x-midi",
                        "application/midi",
                        "audio/mid",
                        "application/octet-stream"
                    )
                )
            }
        }

        val trackButton = Button(this).apply {
            text = "Track"
            contentDescription = "Choose MIDI tracks"
            setOnClickListener { controller.openTrackSelection() }
        }
        val libraryButton = Button(this).apply {
            text = "Library"
            contentDescription = "Open MIDI library"
            setOnClickListener { showLibraryDialog() }
        }
        val layoutButton = Button(this).apply {
            text = "Layout"
            contentDescription = "Configure keyboard layout"
            setOnClickListener { showLayoutDialog() }
        }

        playPauseButton = Button(this).apply {
            text = "Play"
            contentDescription = "Play or pause MIDI playback"
            setOnClickListener {
                if (isPlaying) controller.pause() else controller.play()
            }
        }
        val restartButton = Button(this).apply {
            text = "Restart"
            contentDescription = "Restart playback"
            setOnClickListener { controller.restart() }
        }
        speedButton = Button(this).apply {
            text = "Speed"
            contentDescription = "Change playback speed"
            setOnClickListener { showSpeedDialog() }
        }
        trimButton = Button(this).apply {
            text = "Auto Trim"
            contentDescription = "Configure automatic silence trimming"
            setOnClickListener { showTrimDialog() }
        }

        listOf(
            importButton,
            trackButton,
            libraryButton,
            layoutButton,
            playPauseButton,
            restartButton,
            speedButton,
            trimButton
        ).forEach(::styleButton)

        val exportLogsButton = Button(this).apply {
            text = "Export Logs"
            contentDescription = "Export diagnostic logs and current state"
            setOnClickListener {
                AppDebugLogger.log("Diagnostic export requested")
                exportLogsLauncher.launch("midi-game-engine-debug.txt")
            }
        }

        val quickPlayPauseButton = Button(this).apply {
            text = "Play"
            contentDescription = "Play or pause MIDI playback"
            setOnClickListener {
                if (isPlaying) controller.pause() else controller.play()
            }
        }

        optionsToggleButton = Button(this).apply {
            contentDescription = "Expand or collapse MIDI controls"
            setOnClickListener { setOptionsExpanded(!optionsExpanded, quickPlayPauseButton) }
        }

        val toolbarRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(optionsToggleButton, buttonParams())
            addView(quickPlayPauseButton, buttonParams())
            addView(exportLogsButton, buttonParams())
        }
        listOf(optionsToggleButton, quickPlayPauseButton, exportLogsButton).forEach(::styleButton)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(importButton, buttonParams())
            addView(trackButton, buttonParams())
            addView(libraryButton, buttonParams())
            addView(layoutButton, buttonParams())
        }

        val transportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(playPauseButton, buttonParams())
            addView(restartButton, buttonParams())
            addView(speedButton, buttonParams())
            addView(trimButton, buttonParams())
        }

        timeLabel = TextView(this).apply {
            text = "0:00 / 0:00"
            setPadding(dp(12), 0, dp(12), 0)
            contentDescription = "Playback position"
        }
        timelineSeekBar = SeekBar(this).apply {
            max = 1000
            contentDescription = "Scrub playback timeline"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    userScrubbing = true
                    controller.beginScrub()
                }

                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) controller.scrubToFraction(progress / seekBar.max.toFloat())
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    userScrubbing = false
                    controller.endScrub()
                }
            })
        }

        val timelineRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(timelineSeekBar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(timeLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(themeColor(android.R.attr.colorBackground))
            addView(
                toolbarRow,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                buttonRow,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                transportRow,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                timelineRow,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                visualizerView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        }

        setContentView(root)
        optionsPanel = buttonRow
        setOptionsExpanded(optionsExpanded, quickPlayPauseButton)
        controller.restoreLast()
    }

    override fun onResume() {
        super.onResume()
        activityActive = true
        AppDebugLogger.log("onResume")
        controller.start()
    }

    override fun onPause() {
        activityActive = false
        AppDebugLogger.log("onPause")
        controller.stop()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("optionsExpanded", optionsExpanded)
        AppDebugLogger.log("onSaveInstanceState optionsExpanded=$optionsExpanded")
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        AppDebugLogger.log("onDestroy changingConfigurations=$isChangingConfigurations")
        controller.release()
        super.onDestroy()
    }

    private fun resolveDisplayName(uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex) ?: "Imported MIDI"
            }
        }
        return "Imported MIDI"
    }

    private fun showTrackSelectionDialog(choices: List<TeachingSessionController.TrackChoice>) {
        val labels = choices.map { "${it.label} (${it.noteCount} notes)" }.toTypedArray()
        val checked = BooleanArray(labels.size).apply {
            choices.forEachIndexed { index, choice -> this[index] = choice.selected }
            if (isNotEmpty() && none { it }) this[0] = true
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Choose MIDI tracks\nSelect one or more")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Use selected") { _, _ ->
                val selected = checked.indices.filter { checked[it] }
                if (selected.isEmpty()) {
                    android.widget.Toast.makeText(
                        this,
                        "Select at least one track",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    controller.selectTracks(selected)
                }
            }
            .show()
    }

    private fun showLibraryDialog() {
        val entries = controller.libraryEntries()
        if (entries.isEmpty()) {
            android.widget.Toast.makeText(this, "No imported MIDI files", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val labels = entries.map { it.displayName }.toTypedArray()
        var selectedIndex = 0
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("MIDI Library")
            .setSingleChoiceItems(labels, 0) { _, which -> selectedIndex = which }
            .setNegativeButton("Close", null)
            .setNeutralButton("Remove") { _, _ -> controller.removeLibraryEntry(entries[selectedIndex]) }
            .setPositiveButton("Load") { _, _ -> controller.loadLibraryEntry(entries[selectedIndex]) }
            .show()
    }

    private fun showLayoutDialog() {
        val options = arrayOf(
            "Auto detect",
            "25-key keyboard",
            "49-key keyboard",
            "61-key keyboard",
            "76-key keyboard",
            "88-key keyboard",
            "Full visible range",
            "Visible range: selected tracks",
            "Custom visible range",
            "Keyboard zoom: Compact",
            "Keyboard zoom: Standard",
            "Keyboard zoom: Large"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Keyboard Layout")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> controller.setAutoKeyboardProfile()
                    1 -> controller.setManualKeyboardProfile(KeyboardProfile.KEYS_25)
                    2 -> controller.setManualKeyboardProfile(KeyboardProfile.KEYS_49)
                    3 -> controller.setManualKeyboardProfile(KeyboardProfile.KEYS_61)
                    4 -> controller.setManualKeyboardProfile(KeyboardProfile.KEYS_76)
                    5 -> controller.setManualKeyboardProfile(KeyboardProfile.KEYS_88)
                    6 -> controller.setVisibleRange(PitchRange(21, 108))
                    7 -> controller.setVisibleRangeToSelectedTracks()
                    8 -> showCustomRangeDialog()
                    9 -> controller.setKeyboardZoom(KeyboardZoom.COMPACT)
                    10 -> controller.setKeyboardZoom(KeyboardZoom.STANDARD)
                    11 -> controller.setKeyboardZoom(KeyboardZoom.LARGE)
                }
            }
            .show()
    }

    private fun showCustomRangeDialog() {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(24), 0, dp(24), 0)
        }
        val first = EditText(this).apply {
            hint = "First MIDI pitch"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val last = EditText(this).apply {
            hint = "Last MIDI pitch"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        fields.addView(first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        fields.addView(last, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Visible MIDI range")
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply") { _, _ ->
                val firstPitch = first.text.toString().toIntOrNull()
                val lastPitch = last.text.toString().toIntOrNull()
                if (firstPitch != null && lastPitch != null && firstPitch in 0..127 && lastPitch in firstPitch..127) {
                    controller.setVisibleRange(PitchRange(firstPitch, lastPitch))
                } else {
                    android.widget.Toast.makeText(this, "Use MIDI pitches from 0 to 127", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showSpeedDialog() {
        val speeds = (5..40).map { it * 0.05 }.toTypedArray()
        val labels = speeds.map { "${"%.2f".format(it)}x" }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Playback speed")
            .setItems(labels) { _, which -> controller.setSpeed(speeds[which]) }
            .show()
    }

    private fun showTrimDialog() {
        val paddings = intArrayOf(0, 25, 50, 100, 250, 500)
        val labels = paddings.map { if (it == 0) "No padding" else "${it}ms before and after notes" }.toTypedArray()
        val current = latestState?.trimPaddingMs ?: 50
        val selected = paddings.indexOfFirst { it == current }.coerceAtLeast(0)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (latestState?.autoTrimEnabled == true) "Auto trim: On" else "Auto trim: Off")
            .setSingleChoiceItems(labels, selected) { _, which ->
                controller.setTrimPaddingMs(paddings[which])
            }
            .setNeutralButton(if (latestState?.autoTrimEnabled == true) "Disable" else "Enable") { _, _ ->
                controller.toggleAutoTrim()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun formatTime(timeUs: Long): String {
        val totalSeconds = (timeUs.coerceAtLeast(0L) / 1_000_000L).toInt()
        return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun buttonParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }

    private fun styleButton(button: Button) {
        button.setAllCaps(false)
        button.textSize = 14f
        button.maxLines = 1
        button.ellipsize = android.text.TextUtils.TruncateAt.END
        button.gravity = Gravity.CENTER
        button.minHeight = dp(44)
        button.minimumHeight = dp(44)
        button.minWidth = 0
        button.minimumWidth = 0
        button.setPadding(dp(6), 0, dp(6), 0)
        val darkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        button.setTextColor(themeColor(android.R.attr.textColorPrimary))
        button.background = GradientDrawable().apply {
            setColor(if (darkMode) Color.rgb(42, 47, 58) else Color.rgb(235, 238, 243))
            cornerRadius = dp(9).toFloat()
            setStroke(dp(1), if (darkMode) Color.rgb(73, 82, 98) else Color.rgb(210, 214, 221))
        }
        button.elevation = dp(2).toFloat()
    }

    private fun themeColor(attribute: Int): Int {
        val value = TypedValue()
        check(theme.resolveAttribute(attribute, value, true)) {
            "Theme attribute $attribute is not defined"
        }
        return if (value.resourceId != 0) {
            androidx.core.content.ContextCompat.getColor(this, value.resourceId)
        } else {
            value.data
        }
    }

    private fun setOptionsExpanded(expanded: Boolean, quickPlayPauseButton: Button) {
        optionsExpanded = expanded
        optionsPanel.visibility = if (expanded) View.VISIBLE else View.GONE
        val root = optionsPanel.parent as? ViewGroup
        root?.getChildAt(2)?.visibility = if (expanded) View.VISIBLE else View.GONE
        optionsToggleButton.text = if (expanded) "Hide Controls" else "Show Controls"
        quickPlayPauseButton.visibility = if (expanded) View.GONE else View.VISIBLE
        quickPlayPauseButton.text = if (isPlaying) "Pause" else "Play"
    }
}
