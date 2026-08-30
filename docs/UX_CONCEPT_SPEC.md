# MIDI Game Engine UX Concept Spec

**Status:** Draft
**Audience:** Product, design, engineering, and concept-ingestion tooling
**Focus:** User experience and interaction model first, implementation details second
**Scope:** Android MIDI teaching and practice flow

## 1. Purpose

This document describes the intended user experience of MIDI Game Engine in a way that is readable by people and stable enough for machines to ingest.

The app helps a player practice a MIDI performance against a synchronized note highway, a keyboard visualization, and connected physical MIDI input. The experience is centered on learning, feedback, and low-friction session recovery.

## 2. Product Summary

MIDI Game Engine is a teaching-focused Android app for practicing MIDI songs.

The user can:

- Import a Standard MIDI file.
- Select one or more tracks from multi-track files.
- Adjust the visible keyboard range and physical keyboard profile.
- Play, pause, restart, scrub, and slow down or speed up playback.
- Review practice feedback through the note highway and on-screen keyboard.
- Reopen previously imported files from a local library.

The app keeps the original parsed MIDI file as the source of truth and derives playable practice charts from the selected tracks.

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

1. The app shows the last imported MIDI file or library entries.
2. The app restores track selection and layout preferences when possible.
3. The user can reopen a saved session without re-importing the source file.

## 5. Screen Model

The current interface is a single-screen teaching workspace with four major zones.

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
- Keyboard layout configuration.

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

- Dragging the seek bar begins a scrub session.
- The app pauses playback while scrubbing.
- Releasing the seek bar ends the scrub session.

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

The header line communicates:

- Current source file.
- MIDI device status.
- Current session headline.
- Track summary.
- Physical keyboard profile.
- Visible range.
- Combo.
- Progress.

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
- `Teaching mode`: The current intended experience.
- `Game mode`: A placeholder mode that currently uses the teaching engine.

## 11. Machine-Friendly Summary

```yaml
product: MIDI Game Engine
platform: Android
primary_goal: Teach MIDI performance through synchronized visual feedback
source_of_truth: Parsed Standard MIDI file
derived_artifact: PlayableChart
primary_loop:
  - import MIDI
  - select tracks
  - configure layout
  - play and practice
  - scrub or restart
  - persist session
core_surfaces:
  - toolbar controls
  - library and setup controls
  - transport controls
  - timeline
  - teaching visualizer
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
  - final game mode rules
  - cloud sync
  - destructive MIDI editing
```

## 12. Current Product Boundaries

- The app is still an MVP.
- Game mode is present as a label and state branch, but it does not yet replace the teaching engine.
- The app is focused on local practice, not remote collaboration or cloud-backed libraries.
- The source MIDI is retained rather than rewritten during normal practice flows.

## 13. Implementation References

This spec is grounded in the current codebase:

- `README.md`
- `app/src/main/java/com/example/midigameengine/MainActivity.kt`
- `app/src/main/java/com/example/midigameengine/TeachingSessionController.kt`
- `app/src/main/java/com/example/midigameengine/TeachingUiState.kt`
- `app/src/main/java/com/example/midigameengine/TeachingVisualizerView.kt`
- `app/src/main/java/com/example/midigameengine/AppPreferencesStore.kt`
- `core/src/main/kotlin/core/chart/ChartGenerator.kt`
- `core/src/main/kotlin/core/chart/PlaybackWindow.kt`

## 14. Suggested Next Additions

- A more detailed interaction spec for each control.
- A glossary of MIDI terms for non-technical readers.
- A machine-readable schema for session state snapshots.
- Example user stories and acceptance criteria.
