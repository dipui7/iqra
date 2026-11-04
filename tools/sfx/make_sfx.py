#!/usr/bin/env python3
"""
Make the app's feedback sounds. They are deliberately not musical — no melodies, no instrument
tones — since many Muslims avoid music, above all around the Quran. Each is a short natural
sound: a water drop, a soft tap, a breath of air.

    python3 tools/sfx/make_sfx.py   -> composeApp/src/commonMain/composeResources/files/sfx/*.mp3

To use a recording instead (e.g. a voice saying "mā shāʾ Allāh"), put an MP3 with the same
name in that folder. Needs numpy and ffmpeg.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/files/sfx")
SR = 44100
rng = np.random.default_rng(7)


def t(seconds):
    return np.arange(int(SR * seconds)) / SR


def drop(f0=900, f1=1700, dur=0.16, decay=26):
    """A water drop: a quick upward pitch glide with a fast fade (one event, not a note)."""
    x = t(dur)
    freq = f0 + (f1 - f0) * (1 - np.exp(-x * 40))
    phase = 2 * np.pi * np.cumsum(freq) / SR
    env = np.exp(-x * decay) * (1 - np.exp(-x * 900))
    return np.sin(phase) * env


def tap(f0=230, f1=120, dur=0.22):
    """A soft low tap, like a fingertip on wood: falling pitch, a little noise, quick fade."""
    x = t(dur)
    freq = f1 + (f0 - f1) * np.exp(-x * 30)
    phase = 2 * np.pi * np.cumsum(freq) / SR
    env = np.exp(-x * 18) * (1 - np.exp(-x * 600))
    noise = rng.standard_normal(len(x)) * np.exp(-x * 90) * 0.15
    return (np.sin(phase) + noise) * env


def breath(dur=0.55, lo=500, hi=3500):
    """Air: band-limited noise that swells and fades."""
    n = int(SR * dur)
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    spec[(f < lo) | (f > hi)] = 0
    x = np.fft.irfft(spec, n)
    x /= np.abs(x).max()
    env = np.sin(np.pi * np.linspace(0, 1, n)) ** 2
    return x * env * 0.35


def mix(*parts):
    """parts: (signal, start_seconds, gain)."""
    end = max(int(s * SR) + len(sig) for sig, s, _ in parts)
    out = np.zeros(end)
    for sig, s, g in parts:
        i = int(s * SR)
        out[i:i + len(sig)] += sig * g
    return out


def write(name, x, peak=0.7):
    x = x / np.abs(x).max() * peak
    pad = np.zeros(int(SR * 0.02))
    x = np.concatenate([x, pad])
    os.makedirs(OUT, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        path = tmp.name
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((x * 32767).astype(np.int16).tobytes())
    dest = os.path.join(OUT, name + ".mp3")
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", path, "-ac", "1", "-ar", "44100",
                    "-codec:a", "libmp3lame", "-b:a", "96k", "-map_metadata", "-1", "-id3v2_version", "0",
                    "-write_id3v1", "0", "-write_xing", "0", dest], check=True)
    os.remove(path)
    print("wrote", os.path.relpath(dest, ROOT))


if __name__ == "__main__":
    write("correct", mix((drop(), 0, 1.0), (drop(1100, 2000, 0.12, 34), 0.07, 0.45)))
    write("wrong", tap())
    write("complete", mix((breath(0.7), 0, 1.0), (drop(800, 1500), 0.18, 0.8), (drop(1000, 1900), 0.34, 0.6), (drop(1200, 2200), 0.5, 0.45)))
    write("badge", mix((drop(1000, 1900), 0, 0.9), (drop(1300, 2300), 0.12, 0.7), (breath(0.45, 1500, 6000), 0.05, 0.6)))
    write("heart", tap(170, 90, 0.3))
