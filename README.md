# Iqra — learn to read the Quran

A Duolingo-style app that teaches adult beginners to read Arabic letters, using
real Quran text and recitation. One Kotlin codebase builds both an **Android app**
and a **Windows desktop app** (Compose Multiplatform).

"Iqra" (اقرأ, *read*) is a working name.

## Decisions (made on 2026-11-03)

| Question | Decision |
|---|---|
| Platform | Android **and** desktop, from one codebase: Kotlin + Compose Multiplatform |
| Audience | Adult beginners who don't read Arabic yet; English explanations |
| First goal | Reading Arabic letters (letters → shapes → short vowels → letters in the Quran) |
| Quran text | Tanzil Uthmani text v1.1, bundled **unaltered** |
| Recitation | Mishary Alafasy per-ayah MP3s, **streamed** from EveryAyah at play time, never bundled (kept in a local cache once heard) |
| Translations | None yet (not needed for letters; their licences differ — check before adding) |
| Design | Minimal, Apple-like grouped cards; warm off-white and light green (dark mode: green-charcoal) |

## Content sources and licences

- **Quran text — [Tanzil Project](https://tanzil.net)**, Creative Commons Attribution 3.0.
  Terms: copy verbatim only, *changing it is not allowed*; credit Tanzil and link to tanzil.net;
  keep the copyright header. The file is
  `composeApp/src/commonMain/composeResources/files/quran_uthmani.xml`, exactly as downloaded,
  header included. A test pins its SHA-256 so any edit fails the build's tests.
  The app only shows whole ayat, or words split at the spaces already in the text.
- **Recitation — [EveryAyah](https://everyayah.com)** (Mishary Rashid Alafasy, 128 kbps).
  EveryAyah publishes no licence, so the app streams from their URLs instead of redistributing
  files. Before a public release, get permission or switch to a source with explicit terms.
- **Letter names — [arabic-alphabet-audio](https://github.com/razunatmohammed88-cyber/arabic-alphabet-audio)**,
  MIT License (`licenses/letter-audio-MIT.txt`).
- **Font — [Amiri Quran](https://github.com/aliftype/amiri)**, SIL Open Font License 1.1
  (`licenses/AmiriQuran-OFL.txt`).
- **Lesson text** (letter names, hints, transliterations) is written for this app and is not
  Quranic text. No Quranic text is generated or altered by AI or code.

All of this is also shown in the app (ⓘ on the home screen).

## What's in the first milestone

14 units, 40 lessons:

1–7. **The 28 letters** in groups of 3–5 (similar shapes together). Each unit has:
   - *Meet the letters* — a card per letter (name, sound, its 4 joined shapes, a button to
     hear its name), then recognition and listening questions.
   - *Practice* — name ↔ letter, sound → letter, start-of-word shape → letter, match pairs.
   - *Find them in the Quran* — "which word contains Ba?" and "does this word contain Ta?" using
     real words from Al-Fātiḥah and Juz ʿAmma, with the ayah's audio.
8. **Short vowels** — fatḥa, kasra, ḍamma; read syllables (بَ = ba).
9. **Letters in the Quran** — listen to the opening letters (الٓمٓ, يسٓ, طه …) recited and pick
   the text, or name the letters that make them up.

10–13. **Reading rules** — sukūn, shadda, tanwīn, long vowels. Each unit: *Meet* (the mark,
   examples, reading questions), *Read with it* (practice words, matching, earlier rules mixed
   in) and *Find it in the Quran* ("Which word has a shadda?" with real Juz ʿAmma words).
   Practice words are built from letters and marks the way a Qāʿida primer does; they are
   lesson text, not Quranic text.
14. **Reading words** — mixed practice words, every mark in real Quran words, final review.

**Memorise** (second tab on the home screen) — 21 short surahs: Al-Fātiḥah and most of the
end of Juz ʿAmma, in the usual memorisation order. Every surah is open from the start, with two
lessons:
- *Listen and learn* — each ayah is played and shown, with "recite it aloud" guidance; after
  each new ayah, "what comes next?" links it to the one before; then put the ayat in order.
- *Recite from memory* — what comes next, which word comes next (the start of an ayah, cut at
  one of its own spaces), listen and pick the ayah, put the ayat in order.

Questions only ever have one right answer: an ayah that occurs twice in its surah (Al-Kāfirūn 3
and 5) is never used as a prompt, and identical ayat may be ordered either way round.

Game mechanics: XP (10 per lesson, +5 if no mistakes), daily streak, daily XP goal (10/20/30/50,
set in Settings), 5 hearts (lose one per mistake, refill one every 20 minutes), wrong answers
come back at the end of the lesson, lessons unlock in order, completed lessons can be reviewed.

**Review system.** Every question is tagged with what it practises (a letter, a vowel, a rule).
Each of these has a Leitner-style schedule: a right answer moves it up a box and pushes its
next review out (1, 2, 4, 8, 16 days), a wrong one sends it back to today. The *Practice* card
on the home screen builds a session from what is due, weakest first. Practice costs no hearts
and gives one back, so it is also the way to keep going when hearts run out. Progress is saved locally:
`%APPDATA%\Iqra\progress.json` on Windows, app storage on Android.

**Exams and coins.** Each stage ends with an exam: after the 28 letters, after the vowels and
opening letters, after the reading rules, and after every 5 memorised surahs. An exam has 10
questions: half ordinary quiz questions, a fifth harder ones (look-alike letters in joined
forms, "which one is read …", next word), and the rest voice questions (always at least one).
Exams use no hearts and give no second tries; 80% passes. Passing an exam for the first time
earns 1 coin (retakes earn none). 3 coins unlock unlimited hearts, which last until the streak
breaks (a whole day missed); then they cost 3 coins again.

**Voice questions** (`engine/VoiceMatch.kt`): say a letter's name, recite an opening, read a
short ayah aloud, or recite a surah's first ayah from memory. The recording is compared on the
device (never uploaded) with the right recording and three decoys, using MFCC features and
dynamic time warping; it passes if it sounds closest to the right one, with 3 tries. This checks
how alike the recordings sound — it is not a pronunciation or tajweed check, and the app says
so. It has only been tested with the reference recordings themselves (including slowed,
quieter, noisy copies), not yet with real learners' voices; thresholds may need tuning.

**Help others.** A page (heart-in-hand icon on the home screen) lists well-known organisations
helping people in Gaza, Sudan and DR Congo, linking to their own websites. The app handles no
money and is not affiliated with them. Review the list before each release, and check the app
stores' rules on linking to donations.

**Developer switches** — both must be set back before release:
- `UNLOCK_ALL = true` in `data/Course.kt`: every lesson is open.
- `DEV_UNLIMITED_HEARTS = true` in `engine/Progress.kt`: hearts never run out (release: 5 hearts).

## Letter sounds

Each letter has its own short recordings, bundled with the app in
`composeApp/src/commonMain/composeResources/files/letters/`:

| File | What is said |
|---|---|
| `ba.mp3` | the letter's name, "bā'" |
| `ba_a.mp3`, `ba_i.mp3`, `ba_u.mp3` | the letter with fatḥa / kasra / ḍamma: "ba", "bi", "bu" |

The app uses whatever is there: letter cards get "Its name" / "Its sound" buttons, and lessons
add listening questions ("Listen. Which letter makes this sound?", "Which did you hear?").
Missing clips are simply not offered — the app never plays a whole ayah as if it were a letter.

**Letter names: bundled** — 28 recordings published under the MIT License by
[razunatmohammed88-cyber](https://github.com/razunatmohammed88-cyber/arabic-alphabet-audio)
(licence in `licenses/letter-audio-MIT.txt`, details in `files/letters/CREDITS.md`).

**Syllables (ba, bi, bu …): not recorded yet**, so "Its sound" buttons and sound-listening
questions don't appear. To make them, record three takes (fatḥa, kasra, ḍamma — a pause
between items) with a teacher or a clear reciter, and split them:

```bash
python3 tools/letter-audio/make_clips.py script
```

```bash
python3 tools/letter-audio/make_clips.py split a fatha.m4a
```

See `tools/letter-audio/make_clips.py` for details. Credit the reciter in
`files/letters/CREDITS.md` and in `AboutScreen.kt`.

## Run it

Needs JDK 21 (installed). Android SDK is at `D:\Android\sdk` (set in `local.properties`).

Desktop app:

```bash
./gradlew :composeApp:run
```

Android APK (install on a phone with USB debugging, or copy the file over):

```bash
./gradlew :composeApp:assembleDebug
```

The APK lands in `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.

Tests (lesson generation, untouched Tanzil text, audio decoding, and screenshots of each screen
into `composeApp/build/screens/`):

```bash
./gradlew :composeApp:desktopTest
```

## Code map

```
composeApp/src/
  commonMain/kotlin/app/iqra/
    data/Letters.kt        28 letters, their shapes, sounds; vowel marks
    data/Quran.kt          loads the Tanzil XML; ayah/word lookup; EveryAyah URLs
    data/Course.kt         units, lessons, unlock rules, opening-letter ayat
    data/Reading.kt        reading rules (sukūn, shadda, tanwīn, long vowels), practice syllables
    data/Hifz.kt           the surahs of the memorisation track
    data/Sound.kt          Sound (streamed ayah or bundled clip); which letter clips exist
    engine/Exercise.kt     exercise types (intro cards, multiple choice, match pairs)
    engine/LessonBuilder.kt  turns a lesson into a shuffled list of exercises
    engine/HifzBuilder.kt  memorisation exercises (next ayah, next word, ordering)
    engine/LessonSession.kt  one lesson in progress: answers, hearts, re-asking mistakes
    engine/Progress.kt     XP, streak, hearts, daily goal, review schedule, saving
    engine/Skills.kt       names of what questions practise (letter:ba, rule:shadda …)
    ui/                    Home, Lesson, Result, Settings, About screens; theme
    Platform.kt            expect: storage, audio, date, back button
  androidMain/             MediaPlayer audio, files dir storage, MainActivity
  desktopMain/             JLayer MP3 audio, %APPDATA% storage, window
  desktopTest/             tests + screenshot renderer
```

## What comes next

Roughly in order of value:

1. **Syllable audio.** Letter names are recorded; ba/bi/bu syllables still need recording
   (see *Letter sounds*).
2. **More reading:** ~~sukūn, shadda, tanwīn, long vowels~~ (done); next: hamza forms, the
   silent letters of the Uthmani script, reading short ayat with word-by-word highlighting.
3. **Hifz (memorisation) track** — ~~ordering, next word, listen-and-continue~~ (done for 21
   surahs); next: the rest of Juz ʿAmma (longer surahs split into parts), recite-along with
   speech recognition, a revision schedule per memorised surah.
4. **Tajweed basics** (qalqala, ghunna, madd) with colour-coded text — Tanzil offers tajweed data.
5. **Meaning** — word-by-word meanings once a translation with clear terms is chosen.
6. **Review system** — ~~spaced repetition, practice to earn hearts, daily goal~~ (done);
   next: reminders (notifications).
7. **Release prep** — ~~app icon, offline audio caching~~ (done: `tools/icon/make_icon.py`;
   heard ayat are cached, up to ~300 MB); next: signed release APK / Play Store listing,
   Windows installer (`./gradlew packageMsi` — icon and upgrade id are set), audio permission
   from EveryAyah or another source.
