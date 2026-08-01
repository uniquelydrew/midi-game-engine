# MIDI Game Engine

An Android teaching application for practicing MIDI performances against a synchronized note highway, an on-screen keyboard, and a connected physical MIDI keyboard.

## Current Workflow

1. Import a Standard MIDI file with **Import MIDI**, or load the bundled demo.
2. Select one or more tracks when a file contains multiple tracks.
3. Use **Tracks** to change the teaching selection after import.
4. Use **Layout** to choose the physical keyboard profile and visible MIDI range.
5. Press **Play**, scrub the timeline, adjust speed, and practice against the visualizer.
6. Reopen imported files from **Library**. The source MIDI, selected tracks, layout, trim preference, and playback speed are persisted locally.

The app keeps the complete parsed MIDI document as its source of truth. Track selection creates a derived playable chart without replacing the original MIDI data.

## Architecture

```text
Standard MIDI file
        |
        v
SongModel (all tracks, names, tempo changes)
        |
        +--> selected track IDs --> PlayableChart
        |                              |
        |                              +--> Judgment engine
        |                              +--> Note highway and keyboard
        |                              +--> Android audio synthesizer
        |
        +--> AppPreferencesStore (library and session settings)
        +--> Transport (play, pause, seek, rate)
```

### Modules

- `core`: MIDI parsing, song/chart models, timing, judgment, transport, and visualization geometry.
- `app`: Android UI, MIDI device integration, persistence, audio playback, diagnostics, and the custom visualizer.

### Playback behavior

- Auto-trim removes leading and trailing silence non-destructively using a 50 ms note boundary pad.
- Auto-trim padding can be set to `0`, `25`, `50`, `100`, `250`, or `500 ms` from the Trim control.
- Playback speed ranges from `0.25x` to `2.0x` in `0.05x` increments.
- Keyboard zoom is available as Compact, Standard, or Large; it changes the keyboard strip size without changing pitch mapping.
- Seeking resets judgment state and synchronizes the visualizer and audio event cursor.
- Physical MIDI input remains independent from synthesized Android playback.

## Building and Testing

From the project root on Windows:

```powershell
.\gradlew.bat :core:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device or emulator with:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Android Verification Checklist

- Import a single-track `.mid` file.
- Import a multi-track file and select tracks.
- Change tracks after import and confirm the chart changes without replacing the source file.
- Rotate portrait to landscape and back while paused and while playing.
- Scrub, restart, change playback speed, and verify audio and visuals remain synchronized.
- Reopen the file from **Library** and confirm the selected tracks are restored without reopening the selector.
- Connect a MIDI keyboard and verify physical notes, expected notes, and judgment feedback use the same pitch mapping.
- Use **Export Logs** after any crash or unexpected behavior.

## Project Status

This is an actively developed MVP. The current implementation prioritizes the teaching loop, complete MIDI retention, track selection, responsive keyboard visualization, synchronized local audio, and diagnostic capture.
