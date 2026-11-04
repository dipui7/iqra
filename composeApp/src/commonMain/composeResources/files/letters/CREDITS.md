# Letter recordings

`<letter>.mp3` (the 28 letter names) come from
https://github.com/razunatmohammed88-cyber/arabic-alphabet-audio
(commit 6848aac, "Add complete Arabic alphabet audio set", 2025), MIT License,
Copyright (c) 2025 razunatmohammed88-cyber — full text in `licenses/letter-audio-MIT.txt`.

Converted with `tools/letter-audio/make_clips.py one` (silence trimmed, loudness
normalised, mono 64 kbps MP3). The source files `taa`/`taa'`, `thaa`/`thaa'` and
`haa`/`haa'` were told apart by their sound (emphatic letters lower the vowel's second
formant; ح has a longer breathy onset than ه): taa → ت, taa' → ط, thaa → ث,
thaa' → ظ, haa → ح, haa' → ه.

The syllables (`<letter>_a/_i/_u.mp3`: ba, bi, bu …) are not recorded yet.
