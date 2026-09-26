# Debugging Guide

## Exporting diagnostics

The app records a rotating diagnostic file in its private app storage. It includes:

- Activity lifecycle transitions and orientation changes.
- Controller start, stop, release, playback, import, and restore events.
- Throttled playback state snapshots.
- Import and audio errors.
- Uncaught exceptions with stack traces.
- The current Piano, Whack-a-MIDI, and Drum Sequence state snapshots plus Android device information at export time.

To export diagnostics:

1. Reproduce the problem.
2. Reopen the app if Android closed it.
3. Expand the toolbar if needed.
4. Tap **Export Logs**.
5. Save the generated `midi-game-engine-debug.txt` file and attach it to the bug report.

The internal log is capped and rotated so repeated testing does not grow without bound.

## Rotation testing

For orientation issues, test these cases separately:

- Portrait to landscape while paused.
- Landscape to portrait while paused.
- Portrait to landscape while playing synthesized audio.
- Rotation while scrubbing.
- Rotation while a multi-track selector or file picker is open.

Record whether the failure happens before or after the Activity visibly recreates. The exported log should contain `onPause`, `onSaveInstanceState`, `onDestroy`, `onCreate`, and `onResume` entries around the failure.

## MIDI restore behavior

The application stores:

- The last MIDI URI.
- Per-library-entry selected track IDs.
- Independent Drum Sequence track IDs for the same library entry.
- A separate last-session selected-track set.
- The last picker URI.
- Playback speed and auto-trim preference.
- Adjustable trim padding and keyboard zoom preference.
- Per-source Piano and Drum Sequence loop settings, plus Drum Sequence source-note mappings.

On launch, a multi-track MIDI only opens the track chooser when no saved track IDs match the parsed document. If the file changed and the saved IDs are no longer valid, prompting is intentional and prevents silently teaching the wrong tracks.

## Drum Sequence troubleshooting

- Confirm the imported source notes are mapped in **Source Map**; each used pitch must map to a logical target or be explicitly ignored.
- Confirm **Drum Kit** maps the physical pad to the same logical target. Source mapping and physical-kit calibration are intentionally separate.
- When reviewing a loop, capture the selected A/B range, stop rule, count-in state, pass count, and timing feedback from the exported `DRUM_SEQUENCE_STATE` entry.

## Useful adb commands

```powershell
adb logcat -c
adb logcat '*:E'
adb shell dumpsys activity activities
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
adb shell settings put system user_rotation 0
```

For a local debug build:

```powershell
.\gradlew.bat :core:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Known diagnostic limitations

- State snapshots are throttled to approximately once per second to keep the exported file useful.
- The log contains URI metadata and filenames because they identify the active MIDI source; it does not export the MIDI file itself.
- A crash before the logger initializes will only be available through Android's system log.
