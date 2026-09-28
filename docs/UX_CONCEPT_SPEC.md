# MIDI Game Engine UX Concept Spec

**Status:** Current MVP behavior

## Product model

MIDI Game Engine is a local-first Android music-practice app. Home routes to **Piano Practice** or a **Drums** hub. The Drums hub separates two intentionally different experiences:

- **Whack-a-MIDI:** a reactive target game that accepts calibrated drum-pad input and emphasizes fast, clear target feedback.
- **Drum Sequence Training:** a timed imported-MIDI practice system that judges proximity to expected drum events.

Piano Practice and Drum Sequence Training share A/B practice looping. Whack-a-MIDI does not use the timed-sequence judgment system.

## Primary journeys

### Piano Practice

1. From Home, choose **Piano Practice** and import a Standard MIDI file.
2. Select one or more tracks when required; the original `SongModel` remains unchanged and a `PlayableChart` is derived from the selection.
3. Use Layout, speed, trim, timeline scrubbing, and synthesized playback as needed.
4. Optionally enable a practice loop by setting A/B at the playhead, entering exact `m:ss` values, or dragging the two timeline handles.
5. Choose forever, 4/8/16 passes, or 5/10/15/30 minutes. Paused time does not count toward duration. The session stops at the next B boundary.
6. During a loop, only note onsets in `[A, B)` are judged. At B, transport and synth return to A, runtime key/judgment state clears, and the next pass starts with a fresh Piano score.

### Whack-a-MIDI

1. From Home, choose **Drums → Whack-a-MIDI**.
2. Connect a class-compliant drum module and configure its physical pads in **Drum Kit**, or load General MIDI defaults.
3. Choose a difficulty and start the reaction game.
4. Strike the highlighted target before it expires. Hits, misses, wrong-pad strikes, score, combo, velocity, and reaction time belong solely to the Whack session.

### Drum Sequence Training

1. From Home, choose **Drums → Sequence Training** and import a Standard MIDI file.
2. Choose drum tracks independently of the Piano track selection saved for that file.
3. Configure the physical drum kit in **Drum Kit**. This maps physical MIDI NoteOn input to logical `DrumTarget`s.
4. Use **Source Map** to map every used source MIDI pitch to a logical target or explicitly ignore it. The initial map uses General MIDI and is independent of device calibration.
5. Start training. An optional one-bar, four-beat visual and audible count-in plays before the first start and loop restarts.
6. The judgment engine matches the nearest pending logical target in the timing window. Perfect (≤50 ms) earns 100 points; Good (≤120 ms) earns 50; misses, wrong-pad strikes, and extra strikes earn zero.
7. Score, Perfect/Good/Miss/Wrong counts, and average absolute timing error accumulate for the active session, including across loop passes. Feedback identifies early or late strikes.

## Screen and control model

- **Home:** Piano Practice and Drums choices.
- **Drums hub:** Whack-a-MIDI and Sequence Training choices.
- **Shared toolbar:** options expansion, primary play/pause, diagnostic export, and Home.
- **Piano setup:** Import MIDI, Track, Library, Layout; timeline and visualizer remain visible.
- **Sequence setup:** Import MIDI, Track, Library, Source Map, Drum Kit; the trainer surface presents source/device status, score, count-in, loop status, and feedback.
- **Loop control:** enable/edit A/B, choose the stop rule, clear the loop, and configure count-in for Sequence Training.

## State and lifecycle

Only the active Piano, Whack, or Sequence controller consumes MIDI input. Moving to Home or a different experience pauses and stops the prior controller. Importing or changing tracks creates a new derived session without changing the source MIDI document.

`PracticeLoopSession` tracks active wall-clock practice time, completed passes, and terminal state. It excludes paused time and evaluates pass-count or duration completion at B boundaries.

## Persistence

All preferences are local to the device:

- MIDI library source URIs and display names.
- Independent Piano and Drum Sequence selected track IDs per library entry.
- Piano and Drum Sequence loop ranges and stop rules per source and mode.
- Drum Sequence count-in preference with its loop setting.
- Drum source-note mapping per source MIDI file.
- Physical drum-kit mappings per detected device, with a default fallback.
- Piano layout, playback, trim, and keyboard preferences.
- Whack-a-MIDI difficulty.

## Canonical terms

- `PracticeLoop`: validated, non-empty A/B time range.
- `LoopStopRule`: forever, finite pass count, or active practice duration.
- `DrumKitProfile`: physical MIDI NoteOn to logical drum-target calibration.
- `DrumSequenceProfile`: source MIDI pitch to logical drum-target or Ignore mapping.
- `DrumSequence`: selected source chart represented as timed logical drum events.
- `DrumSequenceJudgmentEngine`: timed-event matcher; distinct from Whack's reactive strike engine.

## Current boundaries

- Standard MIDI import only; no in-app drum-pattern editor.
- Loop selection uses timecode rather than bar snapping because time-signature metadata is not retained by the current model.
- No cloud sync, social functionality, or destructive MIDI editing.
