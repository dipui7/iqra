#!/usr/bin/env python3
"""
Make the teacher review sheet: every piece of teaching content in the app, on paper, with a
column for the teacher to tick or correct. Run it again whenever lessons change.

    python3 tools/review/make_review_sheet.py      -> docs/teacher-review.html
    node tools/review/to_pdf.js                     -> docs/teacher-review.pdf (needs Playwright)

Everything is read from the app's source, so the sheet always shows what learners see.
"""
import html
import os
import re

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "composeApp/src/commonMain/kotlin/app/iqra")
OUT = os.path.join(ROOT, "docs", "teacher-review.html")
ZWJ = "‍"


def read(rel):
    return open(os.path.join(SRC, rel), encoding="utf-8").read()


def unq(s):
    return s.replace('\\"', '"')


def letters():
    rx = re.compile(r'val (\w+) = Letter\("(\w+)", "(.)", "([^"]+)", "([^"]*)", "((?:[^"\\]|\\.)*)"(.*?)\)\n')
    out = []
    for m in rx.finditer(read("data/Letters.kt")):
        _, key, char, name, sound, hint, rest = m.groups()
        out.append(dict(key=key, char=char, name=name, sound=sound, hint=unq(hint), joins="joins = false" not in rest))
    return out


def harakat():
    rx = re.compile(r'Haraka\("(.)", "([^"]+)", "(\w)", "((?:[^"\\]|\\.)*)"\)')
    return [dict(mark=m[1], name=m[2], sound=m[3], hint=unq(m[4])) for m in rx.finditer(read("data/Letters.kt"))]


def rules():
    src = read("data/Reading.kt")
    rx = re.compile(r'Rule\(\s*"(\w+)", "([^"]+)",\s*"((?:[^"\\]|\\.)*)"', re.S)
    return [dict(key=m[1], name=m[2], hint=unq(m[3])) for m in rx.finditer(src)]


def tajweed():
    rx = re.compile(r'(\w+)\("([^"]+)", "((?:[^"\\]|\\.)*)"\)')
    body = read("data/Tajweed.kt").split("enum class TajweedGroup", 1)[1].split("}", 1)[0]
    return [dict(name=m[2], hint=unq(m[3])) for m in rx.finditer(body)]


def openings():
    src = read("data/Letters.kt")
    return re.findall(r'AyahRef\((\d+), (\d+)\) to "([^"]+)"', src)


def recording_notes():
    p = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/files/letters/CREDITS.md")
    return open(p, encoding="utf-8").read() if os.path.exists(p) else ""


def voice_disclaimer():
    m = re.search(r'"(Your voice is compared[^"]*)" \+\s*"([^"]*)"', read("ui/VoiceQuestion.kt"))
    return (m[1] + m[2]) if m else "(not found)"


def ar(text, size=None):
    style = f' style="font-size:{size}px"' if size else ""
    return f'<span class="ar"{style}>{html.escape(text)}</span>'


def check():
    return '<td class="check"><span class="box"></span> OK<br><span class="box"></span> Fix:</td>'


def main():
    L = letters()
    assert len(L) == 28
    font = os.path.relpath(os.path.join(ROOT, "composeApp/src/commonMain/composeResources/font/amiri_quran.ttf"), os.path.dirname(OUT))
    rows = []
    for i, l in enumerate(L, 1):
        c = l["char"]
        forms = [c, c + ZWJ, ZWJ + c + ZWJ, ZWJ + c] if l["joins"] else [c, ZWJ + c]
        shapes = " &nbsp; ".join(ar(f, 26) for f in forms)
        rec = f"files/letters/{l['key']}.mp3"
        rows.append(f"<tr><td>{i}</td><td>{ar(l['char'], 44)}</td><td>{shapes}</td><td><b>{html.escape(l['name'])}</b></td>"
                    f"<td>{html.escape(l['sound'] or '—')}</td><td>{html.escape(l['hint'])}</td><td class='small'>{rec}</td>{check()}</tr>")

    H = harakat()
    vrows = "".join(
        f"<tr><td><b>{html.escape(h['name'])}</b></td><td>{ar('ب' + h['mark'], 36)} = b{h['sound']}</td><td>{html.escape(h['hint'])}</td>{check()}</tr>"
        for h in H)
    rrows = "".join(f"<tr><td><b>{html.escape(r['name'])}</b></td><td>{html.escape(r['hint'])}</td>{check()}</tr>" for r in rules())
    trows = "".join(f"<tr><td><b>{html.escape(t['name'])}</b></td><td>{html.escape(t['hint'])}</td>{check()}</tr>" for t in tajweed())
    orows = "".join(f"<tr><td>{s}:{a}</td><td>{html.escape(t)}</td>{check()}</tr>" for s, a, t in openings())

    doc = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>AlifUp — teacher review sheet</title>
<style>
@font-face {{ font-family: AmiriQuran; src: url("{font}"); }}
@page {{ size: A4; margin: 14mm; }}
body {{ font-family: "DejaVu Sans", Arial, sans-serif; font-size: 11px; color: #1F2A24; }}
h1 {{ font-size: 22px; margin: 0 0 4px; }} h2 {{ font-size: 15px; margin: 18px 0 6px; color: #2E8B62; page-break-after: avoid; }}
p {{ margin: 4px 0 8px; line-height: 1.45; }}
table {{ width: 100%; border-collapse: collapse; margin-bottom: 8px; }}
th, td {{ border: 1px solid #D7DDD6; padding: 5px 6px; vertical-align: middle; text-align: left; }}
th {{ background: #E4F2E7; font-size: 10px; }}
tr {{ page-break-inside: avoid; }}
.ar {{ font-family: AmiriQuran, serif; direction: rtl; unicode-bidi: isolate; line-height: 1.6; }}
.small {{ font-size: 9px; color: #6B7570; }}
.check {{ width: 120px; font-size: 10px; color: #6B7570; }}
.box {{ display: inline-block; width: 9px; height: 9px; border: 1px solid #6B7570; margin-right: 3px; }}
.note {{ background: #F7F5EE; border-left: 3px solid #2E8B62; padding: 6px 10px; }}
.sign td {{ height: 34px; }}
pre {{ white-space: pre-wrap; font-size: 9.5px; background: #F7F5EE; padding: 8px; }}
</style></head><body>
<h1>AlifUp — teacher review sheet</h1>
<p>Thank you for checking this. AlifUp is a free app that teaches adult beginners to read the Quran.
Below is <b>every piece of teaching content</b> in the app — letter names, sounds, explanations,
rules and recordings — exactly as learners see it. Please tick <b>OK</b> or write a correction.
The Quran text itself (Tanzil Uthmani, unchanged) and the recitation (Mishary Alafasy) are not
in question here.</p>
<p class="note">How sounds are written: one Latin letter (or pair) per Arabic letter — <b>ḥ ṣ ḍ ṭ ẓ</b> for the
heavy letters, <b>ʿ</b> for ʿayn, <b>th dh kh gh sh</b> as in the names; long vowels <b>ā ī ū</b>.
The hints are deliberately short and in plain English for adult beginners.</p>

<h2>1. The letters</h2>
<p>Name and sound as shown; the hint appears on the letter's card and in feedback. The recording
says the letter's <b>name</b> — please listen to the files (or in the app: tap the letter).
Special attention: the recordings of <b>ت / ط</b>, <b>ث / ظ</b> and <b>ح / ه</b> were matched to letters by
analysing the sound, not by ear — please confirm each is the right letter.</p>
<table><tr><th>#</th><th>Letter</th><th>Shapes (alone · start · middle · end)</th><th>Name</th><th>Sound</th><th>Hint shown to learners</th><th>Recording</th><th>Check</th></tr>
{''.join(rows)}</table>

<h2>2. Short vowels</h2>
<table><tr><th>Name</th><th>Example</th><th>Explanation</th><th>Check</th></tr>{vrows}</table>

<h2>3. Reading rules</h2>
<p>Practice syllables are put together from letters and marks, as in a Qāʿida (e.g. مَكْ "mak",
رَبَّ "rabba", بًا "ban", بَا "bā"). Wāw and yā are never given sukūn after a matching vowel (that
would be a long vowel), and ʿayn is left out of two-syllable practice words.</p>
<table><tr><th>Rule</th><th>Explanation shown to learners</th><th>Check</th></tr>{rrows}</table>

<h2>4. Tajweed colours in the reader</h2>
<p>Colours come from an open dataset (quran-tajweed, CC BY 4.0). Please check the one-line
explanations, and spot-check a few surahs in the app for wrongly coloured letters.</p>
<table><tr><th>Group</th><th>Explanation shown to learners</th><th>Check</th></tr>{trows}</table>

<h2>5. Opening letters (ḥurūf muqaṭṭaʿāt) — what is heard</h2>
<table><tr><th>Ayah</th><th>Description shown</th><th>Check</th></tr>{orows}</table>

<h2>6. Voice questions in exams</h2>
<p>The learner records themselves and the app compares the recording with the reciter's, on the
device. It is told:</p>
<p class="note">{html.escape(voice_disclaimer())}</p>
<table><tr><th>Is this wording clear enough that nobody takes it as approval of their recitation?</th>{check()}</tr></table>

<h2>7. Translation</h2>
<p>The reader can show the Saheeh International English translation (from Tanzil). It is shown
as is, ayah by ayah, and is not part of the lessons.</p>

<h2>8. Anything else</h2>
<p>Missing, misleading, or worth adding — anything is welcome.</p>
<table class="sign"><tr><td></td></tr><tr><td></td></tr><tr><td></td></tr><tr><td></td></tr></table>
<table class="sign"><tr><th style="width:50%">Reviewed by (name)</th><th>Date</th></tr><tr><td></td><td></td></tr></table>

<h2>Appendix: recording notes</h2>
<pre>{html.escape(recording_notes())}</pre>
</body></html>
"""
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "w", encoding="utf-8").write(doc)
    print("wrote", os.path.relpath(OUT, ROOT), f"({len(L)} letters, {len(H)} vowels)")


if __name__ == "__main__":
    main()
