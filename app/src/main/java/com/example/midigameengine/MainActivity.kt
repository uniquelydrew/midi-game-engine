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
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import core.drums.DrumTarget
import core.runtime.WhackDifficulty
import core.runtime.LoopStopRule
import core.visualization.KeyboardProfile
import core.visualization.KeyboardZoom
import core.visualization.PitchRange
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var controller: TeachingSessionController
    private lateinit var whackController: WhackGameController
    private lateinit var drumSequenceController: DrumSequenceController
    private lateinit var drumKitConfigController: DrumKitConfigController
    private lateinit var visualizerView: TeachingVisualizerView
    private lateinit var whackGameView: WhackGameView
    private lateinit var drumSequenceView: TextView
    private lateinit var preferences: AppPreferencesStore
    private lateinit var timelineSeekBar: SeekBar
    private lateinit var timelineRow: LinearLayout
    private lateinit var timeLabel: TextView
    private lateinit var playPauseButton: Button
    private lateinit var restartButton: Button
    private lateinit var quickPlayPauseButton: Button
    private lateinit var speedButton: Button
    private lateinit var trimButton: Button
    private lateinit var loopButton: Button
    private lateinit var loopRangeView: LoopRangeView
    private lateinit var optionsPanel: View
    private lateinit var optionsToggleButton: Button
    private lateinit var homeButton: Button
    private lateinit var drumKitButton: Button
    private lateinit var difficultyButton: Button
    private lateinit var homeView: View
    private lateinit var drumsHubView: View
    private lateinit var drumKitConfigView: View
    private lateinit var drumKitLayoutView: DrumKitLayoutView
    private lateinit var drumKitConfigStatus: TextView
    private lateinit var drumKitConfigProfile: TextView
    private lateinit var playScreen: View
    private var teachingSetupButtons: List<Button> = emptyList()
    private var userScrubbing = false
    private var currentMode = GameMode.HOME
    private var isPlaying = false
    private var optionsExpanded = true
    private var latestState: TeachingUiState? = null
    private var latestWhackState: WhackUiState? = null
    private var latestDrumSequenceState: DrumSequenceUiState? = null
    private var latestDrumKitConfigState: DrumKitConfigState? = null
    private var drumLearnDialog: androidx.appcompat.app.AlertDialog? = null
    private var activityActive = false
    private var visualizationDownX = 0f
    private var visualizationDownY = 0f
    private var visualizationLastY = 0f
    private var visualizationMoved = false
    private var visualizationScrubbing = false

    private val importMidiLauncher =
        registerForActivityResult(object : ActivityResultContract<Array<String>, Uri?>() {
            override fun createIntent(context: android.content.Context, input: Array<String>): Intent {
                return Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    // Some document providers report .mid as application/octet-stream
                    // and gray it out when EXTRA_MIME_TYPES is also supplied.
                    .setType("*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
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
                if (currentMode == GameMode.DRUM_SEQUENCE) {
                    drumSequenceController.importMidi(uri, resolveDisplayName(uri))
                } else {
                    controller.importMidi(uri, resolveDisplayName(uri))
                }
            }
        }

    private val exportLogsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                contentResolver.openOutputStream(uri)?.use { output: OutputStream ->
                    output.write(
                        AppDebugLogger.exportText(latestState, latestWhackState, latestDrumSequenceState)
                            .toByteArray(Charsets.UTF_8)
                    )
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
        preferences = AppPreferencesStore(this)
        currentMode = savedInstanceState?.getString("currentMode")
            ?.let { savedMode -> runCatching { GameMode.valueOf(savedMode) }.getOrNull() }
            ?: GameMode.HOME
        optionsExpanded = savedInstanceState?.getBoolean("optionsExpanded", true) ?: true

        visualizerView = TeachingVisualizerView(this).apply {
            isClickable = true
            contentDescription = "Tap the visualization to play or pause MIDI playback"
            setOnClickListener {
                if (isPlaying) controller.pause() else controller.play()
            }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        visualizationDownX = event.x
                        visualizationDownY = event.y
                        visualizationLastY = event.y
                        visualizationMoved = false
                        visualizationScrubbing = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - visualizationDownX
                        val dy = event.y - visualizationDownY
                        val slop = ViewConfiguration.get(this@MainActivity).scaledTouchSlop
                        if (!visualizationMoved &&
                            maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) > slop
                        ) {
                            visualizationMoved = true
                        }
                        if (!visualizationScrubbing &&
                            kotlin.math.abs(dy) > slop &&
                            kotlin.math.abs(dy) > kotlin.math.abs(dx)
                        ) {
                            visualizationScrubbing = true
                            controller.beginScrub()
                        }
                        if (visualizationScrubbing) {
                            val durationUs = latestState?.chartLengthUs ?: 0L
                            val viewHeight = view.height.coerceAtLeast(1)
                            val deltaUs = if (durationUs > 0L) {
                                (event.y - visualizationLastY) / viewHeight.toFloat() * durationUs * SEEK_SENSITIVITY
                            } else {
                                (event.y - visualizationLastY) * 20_000f * SEEK_SENSITIVITY
                            }
                            if (deltaUs.toLong() != 0L) {
                                controller.seekRelative(deltaUs.toLong())
                            }
                        }
                        visualizationLastY = event.y
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (visualizationScrubbing) {
                            controller.endScrub()
                        } else if (!visualizationMoved) {
                            view.performClick()
                        } else {
                            // Ignore horizontal drags; only vertical motion scrubs playback.
                        }
                        visualizationMoved = false
                        visualizationScrubbing = false
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        if (visualizationScrubbing) controller.endScrub()
                        visualizationMoved = false
                        visualizationScrubbing = false
                        true
                    }
                    else -> false
                }
            }
        }
        whackGameView = WhackGameView(this).apply {
            visibility = View.GONE
        }

        controller = TeachingSessionController(
            context = this,
            onStateChanged = { state ->
                if (!activityActive || currentMode != GameMode.TEACHING) return@TeachingSessionController
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
                if (::loopButton.isInitialized) {
                    loopButton.text = if (state.loopEnabled) "Loop: ${state.loopRuleLabel}" else "Loop"
                    loopRangeView.submit(
                        state.playbackStartUs,
                        state.playbackEndUs,
                        state.loopStartUs,
                        state.loopEndUs
                    )
                }
                isPlaying = state.isPlaying
                if (::playPauseButton.isInitialized) playPauseButton.text = if (state.isPlaying) "Pause" else "Play"
            },
            onTrackSelectionRequired = { choices ->
                showTrackSelectionDialog(choices)
            }
        )

        whackController = WhackGameController(
            context = this,
            onStateChanged = { state ->
                if (!activityActive || currentMode != GameMode.GAME) return@WhackGameController
                latestWhackState = state
                AppDebugLogger.logState(state)
                whackGameView.submitState(state)
                isPlaying = state.isPlaying
                if (::playPauseButton.isInitialized) renderWhackControls(state)
            },
            onMappingLearned = { target, note ->
                runOnUiThread {
                    drumLearnDialog?.dismiss()
                    drumLearnDialog = null
                    android.widget.Toast.makeText(
                        this,
                        "${target.label} mapped to MIDI $note",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )

        drumSequenceController = DrumSequenceController(
            context = this,
            onStateChanged = { state ->
                if (!activityActive || currentMode != GameMode.DRUM_SEQUENCE) return@DrumSequenceController
                latestDrumSequenceState = state
                AppDebugLogger.logState(state)
                renderDrumSequenceState(state)
                isPlaying = state.isPlaying || state.isCountingIn
                if (::playPauseButton.isInitialized) {
                    playPauseButton.text = when {
                        state.isCountingIn -> "Count-in ${state.countInBeat ?: 1}/4"
                        state.isPlaying -> "Pause"
                        else -> "Start Training"
                    }
                    quickPlayPauseButton.text = playPauseButton.text
                }
                if (::loopButton.isInitialized) loopButton.text = if (state.loopStartUs == null) "Loop" else "Loop: ${state.loopRuleLabel}"
                if (::loopRangeView.isInitialized) loopRangeView.submit(0L, state.playbackEndUs, state.loopStartUs, state.loopEndUs)
            },
            onTrackSelectionRequired = { choices -> showDrumTrackSelectionDialog(choices) }
        )
        drumKitConfigController = DrumKitConfigController(this) { state ->
            latestDrumKitConfigState = state
            if (activityActive && currentMode == GameMode.DRUM_KIT_CONFIG) renderDrumKitConfig(state)
        }

        val importButton = Button(this).apply {
            text = "Import MIDI"
            contentDescription = "Import a MIDI file"
            setOnClickListener {
                importMidiLauncher.launch(
                    emptyArray()
                )
            }
        }

        val trackButton = Button(this).apply {
            text = "Track"
            contentDescription = "Choose MIDI tracks"
            setOnClickListener {
                if (currentMode == GameMode.DRUM_SEQUENCE) drumSequenceController.openTrackSelection()
                else controller.openTrackSelection()
            }
        }
        val libraryButton = Button(this).apply {
            text = "Library"
            contentDescription = "Open MIDI library"
            setOnClickListener { showLibraryDialog() }
        }
        val layoutButton = Button(this).apply {
            text = "Layout"
            contentDescription = "Configure keyboard layout"
            setOnClickListener {
                if (currentMode == GameMode.DRUM_SEQUENCE) showSourceMappingDialog()
                else showLayoutDialog()
            }
        }
        drumKitButton = Button(this).apply {
            text = "Drum Kit"
            contentDescription = "Configure drum MIDI mappings"
            setOnClickListener { showDrumKitDialog() }
        }
        difficultyButton = Button(this).apply {
            text = whackController.currentDifficulty().label
            contentDescription = "Choose Whack-a-MIDI difficulty"
            setOnClickListener { showWhackDifficultyDialog() }
        }
        teachingSetupButtons = listOf(importButton, trackButton, libraryButton, layoutButton)

        homeButton = Button(this).apply {
            text = "Home"
            contentDescription = "Return to instrument selection"
            setOnClickListener { switchMode(GameMode.HOME) }
        }

        playPauseButton = Button(this).apply {
            text = "Play"
            contentDescription = "Play or pause the active mode"
            setOnClickListener { togglePlayPause() }
        }
        restartButton = Button(this).apply {
            text = "Restart"
            contentDescription = "Restart current session"
            setOnClickListener { restartActiveSession() }
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
        loopButton = Button(this).apply {
            text = "Loop"
            contentDescription = "Configure practice loop"
            setOnClickListener { showPracticeLoopDialog() }
        }

        listOf(
            importButton,
            trackButton,
            libraryButton,
            layoutButton,
            playPauseButton,
            restartButton,
            speedButton,
            trimButton,
            loopButton,
            drumKitButton,
            difficultyButton
        ).forEach(::styleButton)

        val exportLogsButton = Button(this).apply {
            text = "Export Logs"
            contentDescription = "Export diagnostic logs and current state"
            setOnClickListener {
                AppDebugLogger.log("Diagnostic export requested")
                exportLogsLauncher.launch("midi-game-engine-debug.txt")
            }
        }

        quickPlayPauseButton = Button(this).apply {
            text = "Play"
            contentDescription = "Play or pause the active mode"
            setOnClickListener { togglePlayPause() }
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
            addView(homeButton, buttonParams())
        }
        listOf(optionsToggleButton, quickPlayPauseButton, exportLogsButton, homeButton).forEach(::styleButton)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(importButton, buttonParams())
            addView(trackButton, buttonParams())
            addView(libraryButton, buttonParams())
            addView(layoutButton, buttonParams())
            addView(drumKitButton, buttonParams())
            addView(difficultyButton, buttonParams())
        }

        val transportRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(playPauseButton, buttonParams())
            addView(restartButton, buttonParams())
            addView(speedButton, buttonParams())
            addView(trimButton, buttonParams())
            addView(loopButton, buttonParams())
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

        timelineRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(timelineSeekBar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(timeLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        loopRangeView = LoopRangeView(this).apply {
            onRangeCommitted = { startUs, endUs ->
                if (currentMode == GameMode.DRUM_SEQUENCE) drumSequenceController.setPracticeLoop(startUs, endUs)
                else controller.setPracticeLoop(startUs, endUs)
            }
        }

        drumSequenceView = TextView(this).apply {
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            text = "Import a MIDI drum sequence to begin training."
        }

        homeView = createInstrumentHome()
        drumsHubView = createDrumsHub()
        drumKitConfigView = createDrumKitConfigView()
        playScreen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
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
                loopRangeView,
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
            addView(
                whackGameView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            addView(
                drumSequenceView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(themeColor(android.R.attr.colorBackground))
            addView(
                homeView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            addView(
                drumsHubView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            addView(
                drumKitConfigView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            addView(
                playScreen,
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
        applyModeVisibility(currentMode)
        controller.restoreLast()
    }

    override fun onResume() {
        super.onResume()
        activityActive = true
        AppDebugLogger.log("onResume")
        when (currentMode) {
            GameMode.HOME -> Unit
            GameMode.DRUMS_HUB -> Unit
            GameMode.DRUM_KIT_CONFIG -> drumKitConfigController.start()
            GameMode.TEACHING -> controller.start()
            GameMode.DRUM_SEQUENCE -> drumSequenceController.start()
            GameMode.GAME -> whackController.start()
        }
    }

    override fun onPause() {
        activityActive = false
        AppDebugLogger.log("onPause")
        when (currentMode) {
            GameMode.HOME -> Unit
            GameMode.DRUMS_HUB -> Unit
            GameMode.DRUM_KIT_CONFIG -> drumKitConfigController.stop()
            GameMode.TEACHING -> controller.stop()
            GameMode.DRUM_SEQUENCE -> drumSequenceController.stop()
            GameMode.GAME -> whackController.stop()
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("optionsExpanded", optionsExpanded)
        outState.putString("currentMode", currentMode.name)
        AppDebugLogger.log("onSaveInstanceState optionsExpanded=$optionsExpanded")
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        AppDebugLogger.log("onDestroy changingConfigurations=$isChangingConfigurations")
        drumLearnDialog?.dismiss()
        controller.release()
        whackController.release()
        drumSequenceController.release()
        drumKitConfigController.release()
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

    private fun showDrumTrackSelectionDialog(choices: List<TeachingSessionController.TrackChoice>) {
        val labels = choices.map { "${it.label} (${it.noteCount} notes)" }.toTypedArray()
        val checked = BooleanArray(labels.size) { index -> choices[index].selected }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Choose drum MIDI tracks\nSelect one or more")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Use selected") { _, _ ->
                val selected = checked.indices.filter { checked[it] }
                if (selected.isEmpty()) {
                    android.widget.Toast.makeText(this, "Select at least one track", android.widget.Toast.LENGTH_SHORT).show()
                } else drumSequenceController.selectTracks(selected)
            }
            .show()
    }

    private fun showLibraryDialog() {
        val sequenceMode = currentMode == GameMode.DRUM_SEQUENCE
        val entries = if (sequenceMode) drumSequenceController.libraryEntries() else controller.libraryEntries()
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
            .setPositiveButton("Load") { _, _ ->
                if (sequenceMode) drumSequenceController.loadLibraryEntry(entries[selectedIndex])
                else controller.loadLibraryEntry(entries[selectedIndex])
            }
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
                    6 -> controller.setFullVisibleRange()
                    7 -> controller.setVisibleRangeToSelectedTracks()
                    8 -> showCustomRangeDialog()
                    9 -> controller.setKeyboardZoom(KeyboardZoom.COMPACT)
                    10 -> controller.setKeyboardZoom(KeyboardZoom.STANDARD)
                    11 -> controller.setKeyboardZoom(KeyboardZoom.LARGE)
                }
            }
            .show()
    }

    private fun showDrumKitDialog() {
        val targets = DrumTarget.values().toList()
        val sequenceMode = currentMode == GameMode.DRUM_SEQUENCE
        val profile = if (sequenceMode) drumSequenceController.currentProfile() else whackController.currentProfile()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
        }
        content.addView(TextView(this).apply {
            text = "Individual pad calibration"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        })
        content.addView(TextView(this).apply {
            text = "Tap a target, then hit that pad once on your drum kit."
            setPadding(0, dp(4), 0, dp(8))
        })
        targets.forEach { target ->
            val trigger = profile?.triggers?.firstOrNull { it.target == target }
            val mapping = trigger?.let {
                "MIDI ${it.midiNote} / ${it.channel?.let { channel -> "Ch ${channel + 1}" } ?: "Any channel"}"
            } ?: "Not mapped"
            content.addView(Button(this).apply {
                text = "${target.label}     $mapping"
                contentDescription = "${target.label}: $mapping. Tap to calibrate"
            setOnClickListener {
                showDrumLearnDialog(target)
                }
            })
        }
        content.addView(TextView(this).apply {
            text = "Quick setup"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, dp(12), 0, dp(4))
        })
        content.addView(Button(this).apply {
            text = "Use General MIDI Defaults"
            contentDescription = "Use General MIDI Defaults for a standard drum kit"
            setOnClickListener {
                if (sequenceMode) drumSequenceController.useGeneralMidiKit() else whackController.useGeneralMidiMapping()
            }
        })
        content.addView(Button(this).apply {
            text = "Clear / Reset Mapping"
            contentDescription = "Clear every drum pad mapping"
            setOnClickListener { if (sequenceMode) drumSequenceController.clearMappings() else whackController.clearMappings() }
        })
        val scroll = ScrollView(this).apply { addView(content) }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(profile?.name ?: "Configure Drum Kit")
            .setMessage("${profile?.availableTargets()?.size ?: 0} pads mapped. Partial kits are playable; targets use only mapped pads.")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showWhackDifficultyDialog() {
        val difficulties = WhackDifficulty.values()
        val current = whackController.currentDifficulty()
        val selected = difficulties.indexOf(current).coerceAtLeast(0)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Whack-a-MIDI difficulty")
            .setSingleChoiceItems(
                difficulties.map { difficulty ->
                    "${difficulty.label} — ${difficulty.targetDurationUs / 1_000L}ms target"
                }.toTypedArray(),
                selected
            ) { dialog, which ->
                val difficulty = difficulties[which]
                whackController.setDifficulty(difficulty)
                difficultyButton.text = difficulty.label
                dialog.dismiss()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showDrumLearnDialog(target: DrumTarget) {
        drumLearnDialog?.dismiss()
        val sequenceMode = currentMode == GameMode.DRUM_SEQUENCE
        if (sequenceMode) drumSequenceController.beginLearn(target) else whackController.beginLearn(target)
        drumLearnDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Map ${target.label}")
            .setMessage("Hit the ${target.label} on the connected MIDI drum kit.")
            .setNegativeButton("Cancel") { _, _ -> if (sequenceMode) drumSequenceController.cancelLearn() else whackController.cancelLearn() }
            .create()
            .also { dialog ->
                dialog.setOnCancelListener { if (sequenceMode) drumSequenceController.cancelLearn() else whackController.cancelLearn() }
                dialog.show()
            }
    }

    private fun switchMode(mode: GameMode) {
        if (mode == currentMode) return

        when (currentMode) {
            GameMode.HOME -> Unit
            GameMode.DRUMS_HUB -> Unit
            GameMode.DRUM_KIT_CONFIG -> drumKitConfigController.stop()
            GameMode.TEACHING -> {
                controller.pause()
                if (activityActive) controller.stop()
            }
            GameMode.DRUM_SEQUENCE -> {
                drumSequenceController.pause()
                if (activityActive) drumSequenceController.stop()
            }
            GameMode.GAME -> {
                drumLearnDialog?.dismiss()
                drumLearnDialog = null
                whackController.cancelLearn()
                whackController.pause()
                if (activityActive) whackController.stop()
            }
        }

        currentMode = mode
        preferences.setGameMode(mode)
        controller.setGameMode(mode)
        isPlaying = false
        applyModeVisibility(mode)

        if (activityActive) {
            when (mode) {
                GameMode.HOME -> Unit
                GameMode.DRUMS_HUB -> Unit
                GameMode.DRUM_KIT_CONFIG -> drumKitConfigController.start()
                GameMode.TEACHING -> controller.start()
                GameMode.DRUM_SEQUENCE -> drumSequenceController.start()
                GameMode.GAME -> whackController.start()
            }
        }
    }

    private fun togglePlayPause() {
        when (currentMode) {
            GameMode.HOME -> Unit
            GameMode.DRUMS_HUB -> Unit
            GameMode.DRUM_KIT_CONFIG -> Unit
            GameMode.TEACHING -> if (isPlaying) controller.pause() else controller.play()
            GameMode.DRUM_SEQUENCE -> if (isPlaying) {
                drumSequenceController.pause()
            } else if (latestDrumSequenceState?.mappedTargets.isNullOrEmpty()) {
                switchMode(GameMode.DRUM_KIT_CONFIG)
            } else {
                drumSequenceController.play()
            }
            GameMode.GAME -> when (latestWhackState?.readiness) {
                WhackReadiness.NEEDS_CONFIGURATION -> switchMode(GameMode.DRUM_KIT_CONFIG)
                WhackReadiness.NO_DEVICE -> android.widget.Toast.makeText(
                    this, "Connect a MIDI drum kit to configure and start a game", android.widget.Toast.LENGTH_LONG
                ).show()
                WhackReadiness.PLAYING -> whackController.pause()
                else -> whackController.play()
            }
        }
    }

    private fun restartActiveSession() {
        when (currentMode) {
            GameMode.HOME -> Unit
            GameMode.DRUMS_HUB -> Unit
            GameMode.DRUM_KIT_CONFIG -> Unit
            GameMode.TEACHING -> controller.restart()
            GameMode.DRUM_SEQUENCE -> drumSequenceController.restart()
            GameMode.GAME -> whackController.restart()
        }
    }

    private fun applyModeVisibility(mode: GameMode) {
        if (mode == GameMode.HOME || mode == GameMode.DRUMS_HUB || mode == GameMode.DRUM_KIT_CONFIG) {
            homeView.visibility = if (mode == GameMode.HOME) View.VISIBLE else View.GONE
            drumsHubView.visibility = if (mode == GameMode.DRUMS_HUB) View.VISIBLE else View.GONE
            drumKitConfigView.visibility = if (mode == GameMode.DRUM_KIT_CONFIG) View.VISIBLE else View.GONE
            playScreen.visibility = View.GONE
            if (mode == GameMode.DRUM_KIT_CONFIG) latestDrumKitConfigState?.let(::renderDrumKitConfig)
            return
        }

        homeView.visibility = View.GONE
        drumsHubView.visibility = View.GONE
        drumKitConfigView.visibility = View.GONE
        playScreen.visibility = View.VISIBLE
        val game = mode == GameMode.GAME
        val sequenceTraining = mode == GameMode.DRUM_SEQUENCE
        if (sequenceTraining && teachingSetupButtons.size >= 4) {
            teachingSetupButtons[3].text = "Source Map"
            drumKitButton.text = "Drum Kit"
        } else if (teachingSetupButtons.size >= 4) {
            teachingSetupButtons[3].text = "Layout"
        }
        visualizerView.visibility = if (game || sequenceTraining) View.GONE else View.VISIBLE
        whackGameView.visibility = if (game) View.VISIBLE else View.GONE
        drumSequenceView.visibility = if (sequenceTraining) View.VISIBLE else View.GONE
        timelineRow.visibility = if (game || sequenceTraining) View.GONE else View.VISIBLE
        loopRangeView.visibility = if (game) View.GONE else View.VISIBLE
        teachingSetupButtons.forEach { it.visibility = if (game) View.GONE else View.VISIBLE }
        drumKitButton.visibility = View.GONE
        difficultyButton.visibility = if (game) View.VISIBLE else View.GONE
        difficultyButton.text = whackController.currentDifficulty().label
        speedButton.visibility = if (game || sequenceTraining) View.GONE else View.VISIBLE
        trimButton.visibility = if (game || sequenceTraining) View.GONE else View.VISIBLE
        loopButton.visibility = if (game) View.GONE else View.VISIBLE
        if (::playPauseButton.isInitialized) {
            if (game) {
                latestWhackState?.let(::renderWhackControls)
                    ?: renderWhackControls(WhackUiState.empty())
            } else if (sequenceTraining) {
                latestDrumSequenceState?.let(::renderDrumSequenceState)
                    ?: renderDrumSequenceState(DrumSequenceUiState.empty())
            } else {
                playPauseButton.text = if (isPlaying) "Pause" else "Play"
                restartButton.text = "Restart"
                quickPlayPauseButton.text = if (isPlaying) "Pause" else "Play"
            }
        }
    }

    private fun createInstrumentHome(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(28), dp(16), dp(28))
            contentDescription = "Choose an instrument"

            addView(TextView(this@MainActivity).apply {
                text = "MIDI Game Engine"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(TextView(this@MainActivity).apply {
                text = "Choose how you want to play"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(28))
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(instrumentChoiceButton(
                title = "Piano Practice",
                detail = "Import a MIDI song, follow the note highway, and practice with your piano or keyboard.",
                onClick = { switchMode(GameMode.TEACHING) }
            ), homeChoiceParams())
            addView(instrumentChoiceButton(
                title = "Drums",
                detail = "Choose reactive Whack-a-MIDI or timed sequence training with an imported MIDI file.",
                onClick = { switchMode(GameMode.DRUMS_HUB) }
            ), homeChoiceParams())
        }
    }

    private fun createDrumsHub(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(16), dp(28), dp(16), dp(28))
        addView(TextView(this@MainActivity).apply {
            text = "Drums"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        addView(TextView(this@MainActivity).apply {
            text = "Choose a drum experience"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(28))
        })
        addView(instrumentChoiceButton(
            "Whack-a-MIDI",
            "A reactive target game: strike the highlighted pad as quickly as you can.",
            onClick = { switchMode(GameMode.GAME) }
        ), homeChoiceParams())
        addView(instrumentChoiceButton(
            "Sequence Training",
            "Import a MIDI drum pattern and build a timing score while it loops.",
            onClick = { switchMode(GameMode.DRUM_SEQUENCE) }
        ), homeChoiceParams())
        addView(instrumentChoiceButton(
            "Configure Drum Kit",
            "Arrange photo-real kit pieces and map the connected physical pads.",
            onClick = { switchMode(GameMode.DRUM_KIT_CONFIG) }
        ), homeChoiceParams())
        addView(Button(this@MainActivity).apply {
            text = "Back to Home"
            setOnClickListener { switchMode(GameMode.HOME) }
        }, homeChoiceParams())
    }

    private fun createDrumKitConfigView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        drumKitConfigProfile = TextView(this@MainActivity).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f) }
        drumKitConfigStatus = TextView(this@MainActivity).apply { setPadding(0, dp(4), 0, dp(8)) }
        addView(drumKitConfigProfile)
        addView(drumKitConfigStatus)
        drumKitLayoutView = DrumKitLayoutView(this@MainActivity).apply {
            editable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            onSelect = { target -> drumKitConfigController.learn(target) }
            onMove = { target, x, y -> drumKitConfigController.move(target, x, y) }
        }
        addView(drumKitLayoutView)
        val actions = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
        fun action(label: String, click: () -> Unit) = Button(this@MainActivity).apply { text = label; setOnClickListener { click() } }
        actions.addView(action("Profiles") { showDrumProfileDialog() }, buttonParams())
        actions.addView(action("Add / Remove") { showDrumPieceDialog() }, buttonParams())
        actions.addView(action("General MIDI") { drumKitConfigController.useGeneralMidi() }, buttonParams())
        actions.addView(action("Drums") { switchMode(GameMode.DRUMS_HUB) }, buttonParams())
        addView(actions)
    }

    private fun renderDrumKitConfig(state: DrumKitConfigState) {
        if (!::drumKitLayoutView.isInitialized) return
        val profile = state.active
        drumKitConfigProfile.text = "Configure Drum Kit — ${profile.name}"
        drumKitConfigStatus.text = "${state.deviceStatus} • ${profile.availableTargets().size} pads mapped" +
            (state.learningTarget?.let { " • Hit ${it.label}" } ?: "")
        drumKitLayoutView.selectedTarget = state.learningTarget
        drumKitLayoutView.submit(profile.layout, profile.availableTargets().toSet())
    }

    private fun showDrumPieceDialog() {
        val state = latestDrumKitConfigState ?: drumKitConfigController.state()
        val profile = state.active
        val labels = profile.layout.map { piece -> "${if (piece.visible) "Remove" else "Add"} ${piece.target.label}" }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Kit pieces").setItems(labels) { _, which ->
            val piece = profile.layout[which]; drumKitConfigController.setVisible(piece.target, !piece.visible)
        }.show()
    }

    private fun showDrumProfileDialog() {
        val state = latestDrumKitConfigState ?: drumKitConfigController.state()
        val names = state.library.profiles.map { it.name }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Kit profiles")
            .setSingleChoiceItems(names, names.indexOf(state.library.activeName)) { dialog, which -> drumKitConfigController.select(names[which]); dialog.dismiss() }
            .setNeutralButton("Manage") { _, _ -> showDrumProfileManageDialog() }
            .setNegativeButton("Close", null).show()
    }

    private fun showDrumProfileManageDialog() {
        val state = latestDrumKitConfigState ?: drumKitConfigController.state()
        val options = arrayOf("Create", "Duplicate", "Rename", "Delete")
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Manage ${state.library.activeName}").setItems(options) { _, which ->
            if (which == 3) drumKitConfigController.deleteActive() else showDrumProfileNameDialog(which)
        }.show()
    }

    private fun showDrumProfileNameDialog(action: Int) {
        val field = EditText(this).apply { hint = "Profile name" }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Kit profile").setView(field).setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ -> when (action) {
                0 -> drumKitConfigController.create(field.text.toString().trim())
                1 -> drumKitConfigController.duplicate(field.text.toString().trim())
                2 -> drumKitConfigController.rename(field.text.toString().trim())
            } }.show()
    }

    private fun instrumentChoiceButton(
        title: String,
        detail: String,
        onClick: () -> Unit
    ): Button = Button(this).apply {
        text = "$title\n$detail"
        contentDescription = "$title. $detail"
        setAllCaps(false)
        textSize = 18f
        gravity = Gravity.CENTER
        minHeight = dp(116)
        minimumHeight = dp(116)
        setPadding(dp(18), dp(12), dp(18), dp(12))
        setOnClickListener { onClick() }
    }

    private fun homeChoiceParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, dp(8), 0, dp(8))
        }

    private fun renderWhackControls(state: WhackUiState) {
        val primaryLabel = when (state.readiness) {
            WhackReadiness.NO_DEVICE -> "Connect Drum Kit"
            WhackReadiness.NEEDS_CONFIGURATION -> "Configure Drum Kit"
            WhackReadiness.READY -> "Start Game"
            WhackReadiness.PLAYING -> "Pause"
            WhackReadiness.PAUSED -> "Resume"
            WhackReadiness.LEARNING_MAPPING -> "Mapping Pad"
        }
        playPauseButton.text = primaryLabel
        playPauseButton.isEnabled = state.readiness != WhackReadiness.LEARNING_MAPPING
        quickPlayPauseButton.text = primaryLabel
        quickPlayPauseButton.isEnabled = playPauseButton.isEnabled
        restartButton.text = "Restart Game"
        restartButton.contentDescription = "Restart Whack-a-MIDI game"
        drumKitButton.text = if (state.readiness == WhackReadiness.NEEDS_CONFIGURATION) {
            "Configure Drum Kit"
        } else {
            "Drum Kit"
        }
        drumKitButton.contentDescription = if (state.mappedTargetCount == 0) {
            "Configure Drum Kit: required before starting"
        } else {
            "Configure Drum Kit: ${state.mappedTargetCount} pads mapped"
        }
    }

    private fun renderDrumSequenceState(state: DrumSequenceUiState) {
        if (!::drumSequenceView.isInitialized) return
        val score = state.score
        val feedback = state.lastFeedback?.let { item ->
            val offset = item.timingDeltaUs?.let { delta ->
                " ${if (delta < 0) "Early" else "Late"} ${kotlin.math.abs(delta) / 1_000L}ms"
            }.orEmpty()
            "Last: ${item.outcome.name.replace('_', ' ')}$offset"
        } ?: "Last: —"
        val loop = if (state.loopStartUs == null) "Loop: off" else "Loop: ${formatTime(state.loopStartUs)}–${formatTime(state.loopEndUs ?: 0L)} (${state.loopRuleLabel})"
        val unmapped = state.unclassifiedSourcePitches.takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "\nSource notes need mapping: ") ?: ""
        drumSequenceView.text = buildString {
            append("Drum Sequence Training\n\n")
            append(state.sourceLabel).append('\n')
            append(state.deviceStatus).append(" • ").append(state.mappedTargets.size).append(" pads calibrated\n")
            append(if (state.isCountingIn) "Count-in: ${state.countInBeat ?: 1}/4\n" else "")
            append(loop).append(" • Passes: ${state.completedPasses}\n")
            append("Score ${score.scorePoints}   Perfect ${score.perfectCount}   Good ${score.goodCount}\n")
            append("Miss ${score.missCount}   Wrong ${score.wrongStrikeCount}   Extra ${score.extraStrikeCount}\n")
            append("Average error: ${score.averageAbsoluteTimingUs?.div(1_000L)?.let { "${it}ms" } ?: "—"}\n")
            append(feedback).append(unmapped)
        }
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

    private fun showSourceMappingDialog() {
        val unclassified = latestDrumSequenceState?.unclassifiedSourcePitches.orEmpty().sorted()
        if (unclassified.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Drum source mapping")
                .setMessage("Every used source note is already mapped or ignored. Reset to General MIDI defaults if you need to start over.")
                .setPositiveButton("Use General MIDI") { _, _ -> drumSequenceController.useGeneralMidiSourceMapping() }
                .setNegativeButton("Close", null)
                .show()
            return
        }
        val pitch = unclassified.first()
        val choices = DrumTarget.values().map { it.label }.plus("Ignore this source note").toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Map source MIDI note $pitch")
            .setMessage("Map this note from the imported MIDI sequence. This does not change your physical drum-kit calibration.")
            .setItems(choices) { _, which ->
                drumSequenceController.assignSourcePitch(pitch, DrumTarget.values().getOrNull(which))
                if (unclassified.size > 1) showSourceMappingDialog()
            }
            .setNeutralButton("Use General MIDI") { _, _ -> drumSequenceController.useGeneralMidiSourceMapping() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showPracticeLoopDialog() {
        if (currentMode == GameMode.DRUM_SEQUENCE) {
            showDrumPracticeLoopDialog()
            return
        }
        val loop = controller.currentLoop()
        val actions = arrayOf(
            "Set A at playhead",
            "Set B at playhead",
            "Edit start and end times",
            "Repeat forever",
            "Repeat 4 passes",
            "Repeat 8 passes",
            "Repeat 16 passes",
            "Practice 5 minutes",
            "Practice 10 minutes",
            "Practice 15 minutes",
            "Practice 30 minutes",
            "Clear loop"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (loop == null) "Practice loop" else "Loop ${formatTime(loop.startUs)} – ${formatTime(loop.endUs)}")
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> controller.setLoopStartAtPlayhead()
                    1 -> controller.setLoopEndAtPlayhead()
                    2 -> showLoopTimeDialog(loop)
                    3 -> controller.setLoopStopRule(LoopStopRule.Forever)
                    4 -> controller.setLoopStopRule(LoopStopRule.PassCount(4))
                    5 -> controller.setLoopStopRule(LoopStopRule.PassCount(8))
                    6 -> controller.setLoopStopRule(LoopStopRule.PassCount(16))
                    7 -> controller.setLoopStopRule(LoopStopRule.Duration(5 * 60_000L))
                    8 -> controller.setLoopStopRule(LoopStopRule.Duration(10 * 60_000L))
                    9 -> controller.setLoopStopRule(LoopStopRule.Duration(15 * 60_000L))
                    10 -> controller.setLoopStopRule(LoopStopRule.Duration(30 * 60_000L))
                    11 -> controller.clearPracticeLoop()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showDrumPracticeLoopDialog() {
        val loop = drumSequenceController.currentLoop()
        val actions = arrayOf(
            "Set A at playhead", "Set B at playhead", "Edit start and end times",
            "Repeat forever", "Repeat 4 passes", "Repeat 8 passes", "Repeat 16 passes",
            "Practice 5 minutes", "Practice 10 minutes", "Practice 15 minutes", "Practice 30 minutes",
            "Toggle count-in", "Clear loop"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (loop == null) "Drum practice loop" else "Loop ${formatTime(loop.startUs)} – ${formatTime(loop.endUs)}")
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> drumSequenceController.setLoopStartAtPlayhead()
                    1 -> drumSequenceController.setLoopEndAtPlayhead()
                    2 -> showDrumLoopTimeDialog(loop)
                    3 -> drumSequenceController.setLoopStopRule(LoopStopRule.Forever)
                    4 -> drumSequenceController.setLoopStopRule(LoopStopRule.PassCount(4))
                    5 -> drumSequenceController.setLoopStopRule(LoopStopRule.PassCount(8))
                    6 -> drumSequenceController.setLoopStopRule(LoopStopRule.PassCount(16))
                    7 -> drumSequenceController.setLoopStopRule(LoopStopRule.Duration(5 * 60_000L))
                    8 -> drumSequenceController.setLoopStopRule(LoopStopRule.Duration(10 * 60_000L))
                    9 -> drumSequenceController.setLoopStopRule(LoopStopRule.Duration(15 * 60_000L))
                    10 -> drumSequenceController.setLoopStopRule(LoopStopRule.Duration(30 * 60_000L))
                    11 -> drumSequenceController.setCountInEnabled(!(latestDrumSequenceState?.countInEnabled ?: true))
                    12 -> drumSequenceController.clearPracticeLoop()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showDrumLoopTimeDialog(loop: core.runtime.PracticeLoop?) {
        val fields = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(24), 0, dp(24), 0) }
        val start = EditText(this).apply { hint = "Start m:ss"; setText(formatTime(loop?.startUs ?: 0L)) }
        val end = EditText(this).apply { hint = "End m:ss"; setText(formatTime(loop?.endUs ?: latestDrumSequenceState?.playbackEndUs ?: 0L)) }
        fields.addView(start, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        fields.addView(end, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Loop range")
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply") { _, _ ->
                val a = parseTimeUs(start.text.toString())
                val b = parseTimeUs(end.text.toString())
                if (a != null && b != null && b > a) drumSequenceController.setPracticeLoop(a, b)
                else android.widget.Toast.makeText(this, "End must be after start", android.widget.Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showLoopTimeDialog(loop: core.runtime.PracticeLoop?) {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(24), 0, dp(24), 0)
        }
        val start = EditText(this).apply {
            hint = "Start m:ss"
            setText(formatTime(loop?.startUs ?: latestState?.playbackStartUs ?: 0L))
        }
        val end = EditText(this).apply {
            hint = "End m:ss"
            setText(formatTime(loop?.endUs ?: latestState?.playbackEndUs ?: 0L))
        }
        fields.addView(start, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        fields.addView(end, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Loop range")
            .setMessage("Use m:ss. The end must be after the start.")
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply") { _, _ ->
                val startUs = parseTimeUs(start.text.toString())
                val endUs = parseTimeUs(end.text.toString())
                if (startUs != null && endUs != null && endUs > startUs) {
                    controller.setPracticeLoop(startUs, endUs)
                } else {
                    android.widget.Toast.makeText(this, "Enter a valid loop range", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun formatTime(timeUs: Long): String {
        val totalSeconds = (timeUs.coerceAtLeast(0L) / 1_000_000L).toInt()
        return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
    }

    private fun parseTimeUs(value: String): Long? {
        val pieces = value.trim().split(":")
        if (pieces.size != 2) return null
        val minutes = pieces[0].toLongOrNull() ?: return null
        val seconds = pieces[1].toLongOrNull() ?: return null
        if (minutes < 0 || seconds !in 0..59) return null
        return (minutes * 60L + seconds) * 1_000_000L
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val SEEK_SENSITIVITY = 0.25f
    }

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
        button.setTextColor(themeColorStateList(android.R.attr.textColorPrimary))
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
            androidx.core.content.ContextCompat.getColorStateList(this, value.resourceId)?.defaultColor
                ?: value.data
        } else {
            value.data
        }
    }

    private fun themeColorStateList(attribute: Int): android.content.res.ColorStateList {
        val value = TypedValue()
        check(theme.resolveAttribute(attribute, value, true)) {
            "Theme attribute $attribute is not defined"
        }
        return if (value.resourceId != 0) {
            androidx.core.content.ContextCompat.getColorStateList(this, value.resourceId)
                ?: android.content.res.ColorStateList.valueOf(value.data)
        } else {
            android.content.res.ColorStateList.valueOf(value.data)
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
