#!/usr/bin/env python3
"""
Make the app's letter recordings from a few long takes.

The app plays a short MP3 for each letter's name and for each letter read with a short
vowel (ba, bi, bu). Recording 109 separate files is tedious, so instead record four takes,
each one reading a list in order with a clear pause (about a second) between items:

    names   the 28 letter names:           alif, bā', tā', thā', jīm, ...
    a       27 letters with fatḥa:         ba, ta, tha, ja, ...   (no alif)
    i       27 letters with kasra:         bi, ti, thi, ji, ...
    u       27 letters with ḍamma:         bu, tu, thu, ju, ...

Print the lists to read from:

    python3 tools/letter-audio/make_clips.py script

Split a take into clips (any format ffmpeg reads: m4a from a phone, wav, mp3 ...):

    python3 tools/letter-audio/make_clips.py split names  my-names.m4a
    python3 tools/letter-audio/make_clips.py split a      my-fatha.m4a

The clips are written to composeApp/src/commonMain/composeResources/files/letters/ as mono
MP3s, trimmed and loudness-normalised. If the take does not split into exactly the expected
number of items, nothing is written and the found segments are listed so you can adjust
--silence-db / --min-silence, or re-record. To replace one clip, record it on its own:

    python3 tools/letter-audio/make_clips.py one ba_a  ba.m4a

Needs ffmpeg on PATH. The app picks up whatever clips exist; missing ones are simply not offered.
"""

import argparse
import os
import re
import subprocess
import sys
import tempfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.environ.get("IQRA_LETTERS_OUT") or os.path.join(
    ROOT, "composeApp", "src", "commonMain", "composeResources", "files", "letters")

# Same order and keys as Letters.all in composeApp/.../data/Letters.kt.
# (key, letter, name to say, Latin sound used for syllables)
LETTERS = [
    ("alif", "ا", "alif", None),
    ("ba", "ب", "bā'", "b"),
    ("ta", "ت", "tā'", "t"),
    ("tha", "ث", "thā'", "th"),
    ("jim", "ج", "jīm", "j"),
    ("haa", "ح", "ḥā'", "ḥ"),
    ("kha", "خ", "khā'", "kh"),
    ("dal", "د", "dāl", "d"),
    ("dhal", "ذ", "dhāl", "dh"),
    ("ra", "ر", "rā'", "r"),
    ("zay", "ز", "zāy", "z"),
    ("sin", "س", "sīn", "s"),
    ("shin", "ش", "shīn", "sh"),
    ("sad", "ص", "ṣād", "ṣ"),
    ("dad", "ض", "ḍād", "ḍ"),
    ("taa", "ط", "ṭā'", "ṭ"),
    ("zaa", "ظ", "ẓā'", "ẓ"),
    ("ayn", "ع", "ʿayn", "ʿ"),
    ("ghayn", "غ", "ghayn", "gh"),
    ("fa", "ف", "fā'", "f"),
    ("qaf", "ق", "qāf", "q"),
    ("kaf", "ك", "kāf", "k"),
    ("lam", "ل", "lām", "l"),
    ("mim", "م", "mīm", "m"),
    ("nun", "ن", "nūn", "n"),
    ("ha", "ه", "hā'", "h"),
    ("waw", "و", "wāw", "w"),
    ("ya", "ي", "yā'", "y"),
]

MARKS = {"a": "َ", "i": "ِ", "u": "ُ"}


def items(take):
    """(file key, Arabic, what to say) for each item of a take, in reading order."""
    if take == "names":
        return [(key, ch, name) for key, ch, name, _ in LETTERS]
    return [(f"{key}_{take}", ch + MARKS[take], snd + take) for key, ch, _, snd in LETTERS if snd]


def all_keys():
    return {k for take in ("names", "a", "i", "u") for k, _, _ in items(take)}


def ffmpeg(*args):
    return subprocess.run(["ffmpeg", "-hide_banner", "-nostdin", "-y", *args],
                          capture_output=True, text=True)


def speech_segments(path, silence_db, min_silence):
    """Return (start, end) seconds of each stretch of sound between silences."""
    r = ffmpeg("-i", path, "-af", f"silencedetect=noise={silence_db}dB:d={min_silence}", "-f", "null", "-")
    if r.returncode != 0:
        sys.exit(f"ffmpeg could not read {path}:\n{r.stderr[-800:]}")
    duration = None
    m = re.search(r"Duration: (\d+):(\d+):([\d.]+)", r.stderr)
    if m:
        duration = int(m[1]) * 3600 + int(m[2]) * 60 + float(m[3])
    starts = [float(x) for x in re.findall(r"silence_start: ([\d.]+)", r.stderr)]
    ends = [float(x) for x in re.findall(r"silence_end: ([\d.]+)", r.stderr)]
    # Sound runs from the end of one silence to the start of the next.
    bounds = [0.0] + ends
    cuts = starts + ([duration] if duration and len(starts) == len(ends) else [])
    segments = []
    for b, c in zip(bounds, cuts):
        if c - b > 0.08:  # ignore clicks
            segments.append((b, c))
    return segments


def write_clip(src, start, end, key):
    os.makedirs(OUT, exist_ok=True)
    dest = os.path.join(OUT, key + ".mp3")
    pad = 0.06
    r = ffmpeg(
        "-ss", f"{max(0.0, start - pad):.3f}", "-to", f"{end + pad:.3f}", "-i", src,
        "-af", "loudnorm=I=-16:TP=-1.5:LRA=11,afade=t=in:d=0.02",
        "-ac", "1", "-ar", "44100", "-codec:a", "libmp3lame", "-b:a", "64k",
        "-map_metadata", "-1", "-id3v2_version", "0", "-write_id3v1", "0", "-write_xing", "0",
        dest,
    )
    if r.returncode != 0:
        sys.exit(f"ffmpeg failed writing {dest}:\n{r.stderr[-800:]}")
    return dest


def write_index():
    """files/letters/index.txt: the clips that exist, which the app reads instead of probing for each."""
    names = sorted(f for f in os.listdir(OUT) if f.endswith(".mp3")) if os.path.isdir(OUT) else []
    with open(os.path.join(OUT, "index.txt"), "w") as f:
        f.write("".join(n + "\n" for n in names))
    print(f"Listed {len(names)} clips in index.txt")


def cmd_script(_):
    for take, title in (("names", "Take 1 - letter names"), ("a", "Take 2 - with fatha"),
                        ("i", "Take 3 - with kasra"), ("u", "Take 4 - with damma")):
        print(f"\n{title} ({len(items(take))} items, pause about a second between them)\n")
        for n, (key, ar, say) in enumerate(items(take), 1):
            print(f"  {n:2}. {ar:<3}  {say}")


def cmd_split(a):
    expected = items(a.take)
    segs = speech_segments(a.file, a.silence_db, a.min_silence)
    if len(segs) != len(expected):
        print(f"Found {len(segs)} spoken items, expected {len(expected)}. Nothing written.")
        for n, (s, e) in enumerate(segs, 1):
            print(f"  {n:2}. {s:7.2f}s - {e:7.2f}s  ({e - s:.2f}s)")
        print("Try --silence-db -35 (quieter rooms: -45) or --min-silence 0.4, or re-record with longer pauses.")
        sys.exit(1)
    for (key, _, say), (s, e) in zip(expected, segs):
        write_clip(a.file, s, e, key)
        print(f"  {key:10} {say:8} {s:7.2f}s - {e:7.2f}s")
    print(f"Wrote {len(expected)} clips to {os.path.relpath(OUT, ROOT)}")
    write_index()


def cmd_one(a):
    if a.key not in all_keys():
        sys.exit(f"Unknown clip '{a.key}'. Known: {', '.join(sorted(all_keys()))}")
    segs = speech_segments(a.file, a.silence_db, a.min_silence)
    if not segs:
        sys.exit("No sound found in the file.")
    print("Wrote", os.path.relpath(write_clip(a.file, segs[0][0], segs[-1][1], a.key), ROOT))
    write_index()


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("script", help="print the lists to read")
    sub.add_parser("index", help="rewrite files/letters/index.txt from the clips present")
    for name, fn in (("split", cmd_split), ("one", cmd_one)):
        s = sub.add_parser(name)
        if name == "split":
            s.add_argument("take", choices=["names", "a", "i", "u"])
        else:
            s.add_argument("key", help="clip name, e.g. ba or ba_a")
        s.add_argument("file")
        s.add_argument("--silence-db", type=float, default=-40)
        s.add_argument("--min-silence", type=float, default=0.5)
        s.set_defaults(fn=fn)
    a = p.parse_args()
    if a.cmd == "script":
        cmd_script(a)
    elif a.cmd == "index":
        write_index()
    else:
        a.fn(a)


if __name__ == "__main__":
    main()
