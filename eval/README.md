# Speech evaluation

Measures Whisper's accuracy on Kenyan clinic speech (Phase 4): the scripts and reference
transcripts are here; the results table and known weak spots will be added by #29 and #30.

## The scripts

`scripts/` holds 20 short dialogues between two people, A and B: six at reception (`s01` to
`s06`) and fourteen in the consultation room (`s07` to `s20`). They mix Swahili and English and
include medicine names, doses, numbers, durations and negatives ("sina homa", "no allergies",
"hapana"), because a misheard negative or dose is the dangerous kind of mistake. Every person
in them is made up.

Each file is both what the speakers read and the reference transcript: the runner drops the
`A:` and `B:` labels and compares the rest with what Whisper heard.

The Swahili has not yet been checked by a native speaker; do that before recording.

## Recording

Each script is recorded twice, once by each pair of speakers, so there are 40 recordings:

- Pair 1 records every script; odd numbers in a quiet room, even numbers with background noise
  (a radio, a corridor, a busy waiting area).
- Pair 2 records every script the other way round: odd numbers noisy, even numbers quiet.
- Read naturally, at clinic speed, with the phone or laptop about an arm's length away.
- Use real voices only, never a patient's, and keep each recording to one script.

Name each file `sNN-pP-quiet` or `sNN-pP-noisy` with its own extension, for example
`s07-p1-quiet.webm` or `s08-p2-quiet.m4a`. WebM, OGG, MP3, M4A and WAV all work.

If a speaker says something different from the script (a skipped word, a different number),
write exactly what was said in `references/sNN-pP.txt`, in the same `A:`/`B:` form. The runner
uses that file instead of the script for that recording.

## Where the audio lives

Audio is never committed. Keep the recordings in `eval/recordings/` (ignored by Git) on the
computer that runs the evaluation, with a copy in private storage you control, such as a
private folder in your own cloud drive. Write down here where that copy is once it exists.
