# MIDI Game Engine

An Android MIDI practice and game application with two active modes:

- **Teaching** — practice Standard MIDI files against a synchronized note highway, on-screen keyboard, synthesized playback, and physical MIDI input.
- **Whack-a-MIDI** — connect a MIDI drum kit and strike the highlighted drum target as quickly and accurately as possible.

## Documentation

- [UX concept spec](docs/UX_CONCEPT_SPEC.md) - product and interaction model.
- [Debugging](docs/DEBUGGING.md) - diagnostic workflow and log export.
- [Play Store release](docs/PLAY_STORE_RELEASE.md) - release build and signing process.

## Teaching Workflow

1. Import a Standard MIDI file with **Import MIDI**.
2. Select one or more tracks when a file contains multiple tracks.
3. Use **Track** to change the teaching selection after import.
4. Use **Layout** to choose the physical keyboard profile and visible MIDI range.
5. Press **Play**, scrub the timeline, adjust speed, and practice against the visualizer.
6. Reopen imported files from **Library**. The source MIDI, selected tracks, layout, trim preference, and playback speed are persisted locally.

The app keeps the complete parsed MIDI document as its source of truth. Track selection creates a derived playable chart without replacing the original MIDI data.

## Whack-a-MIDI Workflow

1. Switch the mode selector to **Whack-a-MIDI**.
2. Connect a class-compliant MIDI drum module.
3. Open **Drum Kit** and either map each pad by striking it or load the General MIDI defaults.
4. Choose a **Difficulty** preset: Relaxed, Standard, Fast, or Expert.
5. Press **Play**.
6. Strike the highlighted drum target before it expires. Hits, misses, wrong-pad strikes, velocity, reaction times, score, and combo are tracked independently of the Teaching judgment engine.

Drum mappings are persisted per detected MIDI device. Target generation is constrained to mapped pads so the game does not request unavailable kit pieces.

## Architecture

```text
                           +----------------------+
Standard MIDI file ------>| Teaching pipeline    |
                           | SongModel             |
Physical MIDI input ------>| PlayableChart         |
            |              | JudgmentEngine        |
            |              +----------------------+
            |
            +------------->+----------------------+
                           | Whack-a-MIDI pipeline |
                           | DrumKitProfile        |
                           | DrumInputMapper       |
                           | WhackGameSession      |
                           | StrikeJudgmentEngine  |
                           +----------------------+

Shared:
- AndroidMidiInputReal
- MidiEvent
- monotonic Transport/Clock
- local preferences
```

### Modules

- `core`: MIDI parsing, chart models, timing, teaching judgment, drum mapping, strike judgment, game runtime, and visualization geometry.
- `app`: Android UI, MIDI device integration, persistence, playback, diagnostics, Teaching visualization, and Whack-a-MIDI visualization/controller.

### MIDI Input Behavior

- Android MIDI device discovery is instrument-neutral; USB hardware is preferred without keyboard- or brand-specific scoring.
- `NoteOn`, `NoteOff`, and `ControlChange` are normalized into shared core events.
- Android-provided monotonic MIDI timestamps are preserved and converted into transport-relative time when available.
- A drum-kit calibration maps physical MIDI notes to logical targets such as kick, snare, hi-hat, toms, crash, and ride.
- The Whack-a-MIDI surface includes a live MIDI monitor showing the latest note, channel, and velocity for hardware diagnostics.
- Difficulty changes target lifetime and the delay between targets. Scoring rewards a correct hit, faster reactions, and sustained combo; strike velocity is recorded but does not increase score.

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

For Play Store signing, privacy, listing, and release verification, see [docs/PLAY_STORE_RELEASE.md](docs/PLAY_STORE_RELEASE.md), [docs/PLAY_STORE_LISTING.md](docs/PLAY_STORE_LISTING.md), and [docs/PRIVACY_POLICY.md](docs/PRIVACY_POLICY.md).

Install it on a connected device or emulator with:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Android Verification Checklist

Teaching:

- Import a single-track `.mid` file.
- Import a multi-track file and select tracks.
- Change tracks after import and confirm the chart changes without replacing the source file.
- Rotate portrait to landscape and back while paused and while playing.
- Scrub, restart, change playback speed, and verify audio and visuals remain synchronized.
- Reopen the file from **Library** and confirm the selected tracks are restored.
- Connect a MIDI keyboard and verify physical notes, expected notes, and judgment feedback use the same pitch mapping.

Whack-a-MIDI:

- Connect a MIDI drum module and confirm it is detected without keyboard-specific assumptions.
- Map at least two pads with **Drum Kit** and confirm the mappings persist after reopening the app.
- Start Whack-a-MIDI and confirm only mapped pads are selected as targets.
- Confirm correct hits record reaction time and velocity and increase score/combo.
- Confirm wrong-pad strikes are counted without consuming the current target and reset combo.
- Change difficulty and confirm target lifetime/gap timing changes and persists across restart.
- Confirm the live MIDI monitor updates for NoteOn input, including unmapped notes.
- Confirm expired targets are recorded as misses.
- Verify pause/resume and restart preserve or reset the game state as intended.

Use **Export Logs** after any crash or unexpected behavior.

## Project Status

This is an actively developed MVP. Teaching mode is functional, and Whack-a-MIDI now has an independent drum-input game loop and Android surface. Hardware validation across multiple drum modules and further gameplay tuning remain in progress.
