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
import core.visualization.KeyboardProfile
import core.visualization.KeyboardZoom
import core.visualization.PitchRange
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var controller: TeachingSessionController
    private lateinit var whackController: WhackGameController
    private lateinit var visualizerView: TeachingVisualizerView
    private lateinit var whackGameView: WhackGameView
    private lateinit var preferences: AppPreferencesStore
    private lateinit var timelineSeekBar: SeekBar
    private lateinit var timelineRow: LinearLayout
    private lateinit var timeLabel: TextView
    private lateinit var playPauseButton: Button
    private lateinit var restartButton: Button
    private lateinit var quickPlayPauseButton: Button
    private lateinit var speedButton: Button
    private lateinit var trimButton: Button
    private lateinit var optionsPanel: View
    private lateinit var optionsToggleButton: Button
    private lateinit var gameModeButton: Button
    private lateinit var drumKitButton: Button
    private lateinit var difficultyButton: Button
    private var teachingSetupButtons: List<Button> = emptyList()
    private var userScrubbing = false
    private var currentMode = GameMode.TEACHING
    private var isPlaying = false
    private var optionsExpanded = true
    private var latestState: TeachingUiState? = null
    private var latestWhackState: WhackUiState? = null
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
                controller.importMidi(uri, resolveDisplayName(uri))
            }
        }

    private val exportLogsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                contentResolver.openOutputStream(uri)?.use { output: OutputStream ->
                    output.write(
                        AppDebugLogger.exportText(latestState, latestWhackState)
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
        currentMode = preferences.gameMode()
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
                if (::gameModeButton.isInitialized) gameModeButton.text = currentMode.label
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
                if (::gameModeButton.isInitialized) gameModeButton.text = currentMode.label
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

        gameModeButton = Button(this).apply {
            text = "Teaching"
            contentDescription = "Choose game mode"
            setOnClickListener { showGameModeDialog() }
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

        listOf(
            importButton,
            trackButton,
            libraryButton,
            layoutButton,
            playPauseButton,
            restartButton,
            speedButton,
            trimButton,
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
            addView(gameModeButton, buttonParams())
        }
        listOf(optionsToggleButton, quickPlayPauseButton, exportLogsButton, gameModeButton).forEach(::styleButton)

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
            addView(
                whackGameView,
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
            GameMode.TEACHING -> controller.start()
            GameMode.GAME -> whackController.start()
        }
    }

    override fun onPause() {
        activityActive = false
        AppDebugLogger.log("onPause")
        when (currentMode) {
            GameMode.TEACHING -> controller.stop()
            GameMode.GAME -> whackController.stop()
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("optionsExpanded", optionsExpanded)
        AppDebugLogger.log("onSaveInstanceState optionsExpanded=$optionsExpanded")
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        AppDebugLogger.log("onDestroy changingConfigurations=$isChangingConfigurations")
        drumLearnDialog?.dismiss()
        controller.release()
        whackController.release()
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

    private fun showGameModeDialog() {
        val modes = GameMode.values()
        val selected = modes.indexOf(currentMode).coerceAtLeast(0)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Game mode")
            .setSingleChoiceItems(
                modes.map { "${it.label}\n${it.description}" }.toTypedArray(),
                selected
            ) { dialog, which ->
                switchMode(modes[which])
                dialog.dismiss()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showDrumKitDialog() {
        val targets = DrumTarget.values().toList()
        val profile = whackController.currentProfile()
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
                whackController.useGeneralMidiMapping()
            }
        })
        content.addView(Button(this).apply {
            text = "Clear / Reset Mapping"
            contentDescription = "Clear every drum pad mapping"
            setOnClickListener { whackController.clearMappings() }
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
        whackController.beginLearn(target)
        drumLearnDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Map ${target.label}")
            .setMessage("Hit the ${target.label} on the connected MIDI drum kit.")
            .setNegativeButton("Cancel") { _, _ -> whackController.cancelLearn() }
            .create()
            .also { dialog ->
                dialog.setOnCancelListener { whackController.cancelLearn() }
                dialog.show()
            }
    }

    private fun switchMode(mode: GameMode) {
        if (mode == currentMode) return

        when (currentMode) {
            GameMode.TEACHING -> {
                controller.pause()
                if (activityActive) controller.stop()
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
                GameMode.TEACHING -> controller.start()
                GameMode.GAME -> whackController.start()
            }
        }
    }

    private fun togglePlayPause() {
        when (currentMode) {
            GameMode.TEACHING -> if (isPlaying) controller.pause() else controller.play()
            GameMode.GAME -> when (latestWhackState?.readiness) {
                WhackReadiness.NEEDS_CONFIGURATION -> showDrumKitDialog()
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
            GameMode.TEACHING -> controller.restart()
            GameMode.GAME -> whackController.restart()
        }
    }

    private fun applyModeVisibility(mode: GameMode) {
        val game = mode == GameMode.GAME
        visualizerView.visibility = if (game) View.GONE else View.VISIBLE
        whackGameView.visibility = if (game) View.VISIBLE else View.GONE
        timelineRow.visibility = if (game) View.GONE else View.VISIBLE
        teachingSetupButtons.forEach { it.visibility = if (game) View.GONE else View.VISIBLE }
        drumKitButton.visibility = if (game) View.VISIBLE else View.GONE
        difficultyButton.visibility = if (game) View.VISIBLE else View.GONE
        difficultyButton.text = whackController.currentDifficulty().label
        speedButton.visibility = if (game) View.GONE else View.VISIBLE
        trimButton.visibility = if (game) View.GONE else View.VISIBLE
        gameModeButton.text = mode.label
        if (::playPauseButton.isInitialized) {
            if (game) {
                latestWhackState?.let(::renderWhackControls)
                    ?: renderWhackControls(WhackUiState.empty())
            } else {
                playPauseButton.text = if (isPlaying) "Pause" else "Play"
                restartButton.text = "Restart"
                quickPlayPauseButton.text = if (isPlaying) "Pause" else "Play"
            }
        }
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
