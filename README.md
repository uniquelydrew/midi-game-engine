# MIDI Game Engine

An Android MIDI practice and drum-training app with three experiences:

- **Piano Practice** — follow imported Standard MIDI files on a synchronized note highway with keyboard input and synthesized playback.
- **Whack-a-MIDI** — a reactive drum-pad target game for a calibrated physical kit.
- **Drum Sequence Training** — practice imported MIDI drum sequences with timing feedback, scoring, count-in, and looped repetition.

## Navigation

The app opens on **Home**. Choose **Piano Practice**, or choose **Drums** and then select **Whack-a-MIDI** or **Sequence Training**. **Home** returns to this selection without discarding each experience's saved setup.

## Piano Practice

1. Use **Import MIDI**, select tracks when prompted, and configure **Layout** as desired.
2. Play, pause, scrub, restart, adjust speed, or change trim from the Piano controls.
3. Use **Loop** to set A and B at the playhead, enter exact `m:ss` values, or drag the highlighted range handles.
4. Choose forever, 4/8/16 passes, or 5/10/15/30 minutes. Paused time is excluded; duration sessions stop at B.

Piano judgments are restricted to note onsets inside `[A, B)`. Each completed loop pass starts a fresh score; the last completed pass summary remains visible. Piano track choices and loop preferences are saved per source MIDI file.

## Drums

### Whack-a-MIDI

Connect a MIDI drum module, configure its pads through **Drum Kit** (or use General MIDI defaults), choose a difficulty, and start the game. Whack remains a reactive target game: its hit, miss, wrong-pad, combo, and reaction-time feedback are independent from sequence-training judgments.

### Sequence Training

1. Open **Drums → Sequence Training** and import a MIDI file.
2. Select the drum tracks independently from Piano Practice.
3. Configure the physical kit with **Drum Kit**. This maps physical MIDI notes to logical pads.
4. Use **Source Map** to map each used source note to a logical drum target or explicitly ignore it. It starts with General MIDI defaults and is saved separately from kit calibration.
5. Start practice. An optional four-beat visual/audible count-in occurs before starting and before loop restarts.

Only NoteOn timing is scored: Perfect (≤50 ms) earns 100 points and Good (≤120 ms) earns 50. Misses, wrong-pad strikes, and extra strikes earn zero and do not consume a valid target. Score, counts, and timing error accumulate for the full loop session.

## Architecture

```text
Standard MIDI file ─┬─> Piano: PlayableChart → Teaching judgment/session
                    └─> Drums: DrumSequence → timing judgment/session

Physical MIDI input ─┬─> Piano keyboard input
                     ├─> calibrated Whack-a-MIDI target input
                     └─> calibrated Drum Sequence strikes

Shared: MIDI parsing, monotonic transport, A/B PracticeLoop, local preferences
```

- `core`: parsing, charts, transport, loop state, piano and drum judgments, drum models, and visualization geometry.
- `app`: Android UI, MIDI integration, persistence, synthesis, controllers, diagnostics, and custom views.

## Building and testing

From the project root on Windows:

```powershell
.\gradlew.bat :core:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Documentation

- [UX concept spec](docs/UX_CONCEPT_SPEC.md)
- [Debugging](docs/DEBUGGING.md)
- [Play Store listing](docs/PLAY_STORE_LISTING.md)
- [Play Store release](docs/PLAY_STORE_RELEASE.md)
- [Privacy policy](docs/PRIVACY_POLICY.md)

## Project status

This is an actively developed local-first MVP. Validate MIDI hardware behavior across keyboards and drum modules before release.
