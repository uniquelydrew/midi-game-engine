# MIDI Game Engine UX Concept Spec

**Status:** Draft
**Audience:** Product, design, engineering, and concept-ingestion tooling
**Focus:** User experience and interaction model first, implementation details second
**Scope:** Android MIDI teaching plus Whack-a-MIDI drum gameplay

## 1. Purpose

This document describes the intended user experience of MIDI Game Engine in a way that is readable by people and stable enough for machines to ingest.

The app supports two MIDI-driven experiences. Teaching mode lets a player practice a MIDI performance against a synchronized note highway, keyboard visualization, synthesized playback, and physical MIDI input. Whack-a-MIDI turns a connected MIDI drum kit into a reaction game driven by calibrated drum-pad mappings. Both modes emphasize immediate feedback and low-friction session recovery.

## 2. Product Summary

MIDI Game Engine is an Android MIDI practice and game app. Teaching mode focuses on MIDI-song practice; Whack-a-MIDI is an independent drum-input game loop.

The user can:

- Import a Standard MIDI file.
- Select one or more tracks from multi-track files.
- Adjust the visible keyboard range and physical keyboard profile.
- Play, pause, restart, scrub, and slow down or speed up playback.
- Review practice feedback through the note highway and on-screen keyboard.
- Reopen previously imported files from a local library.
- Switch to Whack-a-MIDI without loading a Standard MIDI file.
- Calibrate drum pads by striking the physical kit, or load General MIDI defaults.
- Play reaction targets using only the pads mapped for the current MIDI device.

Teaching mode keeps the original parsed MIDI file as the source of truth and derives playable practice charts from the selected tracks. Whack-a-MIDI instead uses a persisted DrumKitProfile and a strike-target session.

## 3. Core UX Principles

- Teach first: the default experience should help the user understand what to play next.
- Preserve the source: importing and track selection should not destroy the original MIDI data.
- Keep feedback immediate: expected notes, active notes, and played notes should all be visible at once.
- Make recovery easy: the user should be able to restart, scrub, or reload a session without re-importing.
- Remember preferences locally: layout, track selection, and playback settings should persist across sessions.

## 4. Primary User Journey

### 4.1 First Launch

1. The app opens with no MIDI loaded.
2. The user sees a prompt to import a MIDI file.
3. If a previously used file exists, the app restores it automatically.

### 4.2 Import and Setup

1. The user taps `Import MIDI`.
2. The system file picker opens.
3. If the file has multiple tracks, the app asks the user to choose one or more tracks.
4. The app builds a practice chart from the selected tracks.
5. The app updates the visualizer and playback window.

### 4.3 Practice Loop

1. The user presses `Play`.
2. Notes cascade down the note highway.
3. The keyboard highlights expected notes and currently active notes.
4. Physical MIDI input is shown separately from the expected chart notes.
5. The user pauses, scrubs, restarts, or changes speed as needed.

### 4.4 Return Visits

1. The app restores the last selected mode.
2. Teaching mode restores the last imported MIDI file or library entries and associated track/layout preferences.
3. Whack-a-MIDI restores the drum mapping for the detected MIDI device when available.

### 4.5 Whack-a-MIDI Loop

1. The user switches the mode selector to `Whack-a-MIDI`.
2. The user connects a MIDI drum module.
3. The user opens `Drum Kit` and maps pads by striking them, or loads General MIDI defaults.
4. The user presses `Play`.
5. A mapped drum target is highlighted.
6. A matching strike records a hit and reaction time; a different mapped pad records a wrong-pad strike without consuming the target.
7. An expired target records a miss and the next target is scheduled.

## 5. Screen Model

The current interface is a single-screen workspace whose primary surface and setup controls change with the selected mode.

### 5.1 Toolbar Row

Contains the most frequently used session controls:

- Show or hide advanced controls.
- Play or pause.
- Export logs.
- Switch between Teaching and Game mode.

### 5.2 Library and Setup Row

Contains file and session setup controls:

- Import MIDI.
- Track selection.
- Library access.
- Keyboard layout configuration in Teaching mode.
- Drum-kit mapping and General MIDI defaults in Whack-a-MIDI mode.

### 5.3 Transport Row

Contains playback controls:

- Play or pause.
- Restart.
- Playback speed.
- Auto trim.

### 5.4 Timeline and Visualizer

Contains the progress bar, playback time label, and the large visual practice surface.

The visualizer is the main teaching surface. It shows:

- A scrolling note highway.
- An on-screen keyboard.
- Currently expected notes.
- Currently active notes.
- Notes already matched by input.
- Physical MIDI input feedback.

## 6. Interaction Model

### 6.1 Importing

- The app opens a document picker for MIDI files.
- It persists read access to the chosen file when allowed by the system.
- Multi-track files trigger a track selection dialog.

### 6.2 Track Selection

- The user can select one or more tracks.
- A track must be selected before a practice chart can be generated.
- Track selection is reversible after import through the `Track` control.

### 6.3 Playback Controls

- `Play` starts or resumes playback.
- `Pause` stops playback without losing the loaded chart.
- `Restart` returns the session to the start of the active playback window.
- Speed changes are applied immediately.
- Scrubbing resets judgment state and resynchronizes audio and visuals.

### 6.4 Visualizer Gestures

- Tap the visualizer to toggle play and pause.
- Vertical drag scrubs through the chart.
- Horizontal drag is ignored.

### 6.5 Timeline Scrubbing

- Dragging the seek bar begins a scrub session in Teaching mode.
- The app pauses playback while scrubbing.
- Releasing the seek bar ends the scrub session.

### 6.6 Drum Mapping

- The Drum Kit dialog shows each logical drum target and its current MIDI note mapping.
- Selecting a target arms a one-hit learn operation.
- The next NoteOn maps its note/channel to that target.
- A single physical note is not retained for multiple logical targets.
- Mappings are stored per detected MIDI device, with a default fallback profile.
- General MIDI defaults can be loaded explicitly.

### 6.7 Whack-a-MIDI Gameplay

- Play starts or resumes the monotonic game transport.
- Restart clears the current strike session and begins at time zero.
- Targets are generated only from currently mapped pads.
- NoteOff events do not judge drum strikes; strike gameplay resolves from NoteOn events.

## 7. Feedback Semantics

The visualizer uses consistent feedback channels so the user can understand timing and correctness at a glance.

### 7.1 Note Highway

- Future notes appear as blue bars.
- The next expected notes are emphasized in yellow.
- Active notes are highlighted in cyan.
- Matched notes are shown in green.

### 7.2 On-Screen Keyboard

- Expected keys are highlighted.
- Currently active keys are emphasized more strongly.
- Physical MIDI key presses are shown in cyan.
- Correct or incorrect feedback briefly rings the last played key.

### 7.3 Session Feedback

Teaching mode communicates:

- Current source file.
- MIDI device status.
- Current session headline.
- Track summary.
- Physical keyboard profile.
- Visible range.
- Combo.
- Progress.

### 7.4 Whack-a-MIDI Feedback

The drum surface communicates:

- Connected MIDI-device status.
- Current drum-profile name.
- Mapped and unmapped kit pieces.
- The currently active target.
- Remaining target time.
- Hits, misses, and wrong-pad strikes.
- Average and best reaction time.
- Recent hit/wrong/miss feedback.

## 8. State Model

The app is driven by a session state snapshot that is pushed into the UI on a regular cadence and whenever MIDI input arrives.

### 8.1 Important States

- No MIDI loaded.
- Importing.
- Track selection required.
- Loaded.
- Playing.
- Paused.
- Scrubbing.
- Complete.
- Saved MIDI unavailable.
- Import failed.
- Drum kit not configured.
- Waiting for MIDI drum device.
- Learning a drum mapping.
- Whack-a-MIDI ready.
- Whack-a-MIDI playing.
- Whack-a-MIDI paused.

### 8.2 State Transition Rules

- Loading a new song resets the current session.
- Seeking resets judgment state.
- Changing track selection generates a new playable chart from the original song data.
- Reaching the end of the playback window stops playback and marks the session complete.

## 9. Persistence Model

Preferences are stored locally on the device.

### 9.1 Saved Session Data

- Library entries with file URI, display name, and selected track IDs.
- Last imported file URI.
- Last selected track IDs.
- Last picker URI for faster file re-entry.
- Playback speed.
- Auto-trim enabled state.
- Trim padding.
- Keyboard zoom.
- Game mode.
- Drum-kit mappings keyed by detected MIDI device.

### 9.2 Layout Preferences

Layout preferences can vary by detected device description.

Saved layout data includes:

- Keyboard profile mode.
- Manual keyboard profile.
- Visible pitch range.
- Visible range mode.

## 10. Canonical Terms

These terms are used consistently throughout the product and in this spec.

- `SongModel`: Parsed MIDI source data containing tracks, tempo changes, and timing information.
- `PlayableChart`: Derived list of expected inputs used by the teaching engine.
- `TeachingSessionController`: Session orchestration layer for loading, playback, selection, and persistence.
- `TeachingUiState`: Snapshot of what the user currently sees.
- `TeachingVisualizerView`: Custom view that renders the note highway and keyboard.
- `PlaybackWindow`: The active playback span, optionally auto-trimmed around note activity.
- `Track`: User-facing action for choosing which MIDI tracks are included in the practice chart.
- `Teaching mode`: MIDI-file practice using the note highway, keyboard, playback, and Teaching judgment engine.
- `Whack-a-MIDI`: Drum-input reaction game using DrumKitProfile, WhackGameSession, and StrikeJudgmentEngine.
- `DrumKitProfile`: Persisted mapping from physical MIDI NoteOn messages to logical drum targets.
- `StrikeTarget`: A logical drum target with an appearance and expiration time.

## 11. Machine-Friendly Summary

```yaml
product: MIDI Game Engine
platform: Android
primary_goals:
  - teach MIDI performance through synchronized visual feedback
  - provide MIDI-drum reaction gameplay
teaching_source_of_truth: Parsed Standard MIDI file
teaching_derived_artifact: PlayableChart
whack_source_of_truth: DrumKitProfile
teaching_loop:
  - import MIDI
  - select tracks
  - configure layout
  - play and practice
  - scrub or restart
whack_loop:
  - connect MIDI drum device
  - map drum pads
  - play timed targets
  - record hit miss wrong-pad and reaction metrics
core_surfaces:
  - toolbar controls
  - library and setup controls
  - transport controls
  - timeline
  - teaching visualizer
  - Whack-a-MIDI drum surface
persisted_preferences:
  - library entries
  - track selection
  - playback speed
  - auto trim
  - trim padding
  - keyboard profile
  - visible pitch range
  - keyboard zoom
  - game mode
  - drum kit profiles
gesture_rules:
  tap_visualizer: toggle play/pause
  drag_vertical: scrub
  drag_horizontal: ignore
state_outputs:
  - expected notes
  - active notes
  - physical MIDI input
  - combo and progress
non_goal_now:
  - cloud sync
  - destructive MIDI editing
  - advanced cymbal articulation and hi-hat pedal interpretation
```

## 12. Current Product Boundaries

- The app is still an MVP.
- Whack-a-MIDI now has its own controller, view, drum-mapping model, target session, and strike judgment engine; hardware validation and gameplay tuning are still in progress.
- The app is focused on local practice, not remote collaboration or cloud-backed libraries.
- The source MIDI is retained rather than rewritten during normal practice flows.

## 13. Implementation References

This spec is grounded in the current codebase:

- `README.md`
- `app/src/main/java/com/example/midigameengine/MainActivity.kt`
- `app/src/main/java/com/example/midigameengine/TeachingSessionController.kt`
- `app/src/main/java/com/example/midigameengine/TeachingUiState.kt`
- `app/src/main/java/com/example/midigameengine/TeachingVisualizerView.kt`
- `app/src/main/java/com/example/midigameengine/WhackGameController.kt`
- `app/src/main/java/com/example/midigameengine/WhackGameView.kt`
- `app/src/main/java/com/example/midigameengine/AppPreferencesStore.kt`
- `core/src/main/kotlin/core/chart/ChartGenerator.kt`
- `core/src/main/kotlin/core/chart/PlaybackWindow.kt`
- `core/src/main/kotlin/core/drums/DrumKitProfile.kt`
- `core/src/main/kotlin/core/runtime/WhackGameSession.kt`
- `core/src/main/kotlin/core/strike/StrikeJudgmentEngine.kt`

## 14. Suggested Next Additions

- A more detailed interaction spec for each control.
- A glossary of MIDI terms for non-technical readers.
- A machine-readable schema for session state snapshots.
- Example user stories and acceptance criteria.
