#!/usr/bin/env python3
"""
Make the printable AlifUp workbook (A4 PDF) from the app's own lesson data.

    python3 tools/workbook/make_workbook.py            -> docs/AlifUp-Workbook.pdf

Letters (names, sounds, hints, which ones join) are read from
composeApp/src/commonMain/kotlin/app/iqra/data/Letters.kt, so the workbook always matches the
app. Practice syllables and words are put together from letters and marks the way a Qāʿida
primer does; there is no Quranic text in the workbook.

Needs Pillow built with libraqm (for Arabic shaping): pip install pillow.
"""
import os
import random
import re
import textwrap

from PIL import Image, ImageDraw, ImageFont, features

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ARABIC_FONT = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/font/amiri_quran.ttf")
LATIN_FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
LATIN_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
OUT = os.path.join(ROOT, "docs", "AlifUp-Workbook.pdf")

DPI = 200
W, H = int(8.27 * DPI), int(11.69 * DPI)  # A4
M = int(0.6 * DPI)  # margin

INK = (31, 42, 36)
SOFT = (107, 117, 112)
GREEN = (46, 139, 98)
LIGHT_GREEN = (228, 242, 231)
PAPER = (255, 255, 255)
GUIDE = (140, 160, 150)
LINE = (220, 224, 216)

ZWJ = "‍"
FATHA, KASRA, DAMMA = "َ", "ِ", "ُ"
SUKUN, SHADDA = "ْ", "ّ"
FATHATAN, DAMMATAN, KASRATAN = "ً", "ٌ", "ٍ"
HARAKAT = [("Fatḥa", FATHA, "a"), ("Kasra", KASRA, "i"), ("Ḍamma", DAMMA, "u")]


# --- Data from the app ---------------------------------------------------------------------

def load_letters():
    src = open(os.path.join(ROOT, "composeApp/src/commonMain/kotlin/app/iqra/data/Letters.kt"), encoding="utf-8").read()
    rx = re.compile(r'Letter\("(\w+)", "(.)", "([^"]+)", "([^"]*)", "((?:[^"\\]|\\.)*)"(.*?)\)\n')
    letters = []
    for m in rx.finditer(src):
        key, char, name, sound, hint, rest = m.groups()
        letters.append(dict(key=key, char=char, name=name, sound=sound,
                            hint=hint.replace('\\"', '"'), joins="joins = false" not in rest))
    assert len(letters) == 28, f"found {len(letters)} letters in Letters.kt"
    return letters


LETTERS = load_letters()
BY_KEY = {l["key"]: l for l in LETTERS}
CONSONANTS = [l for l in LETTERS if l["key"] != "alif"]


# --- Drawing helpers -----------------------------------------------------------------------

_fonts = {}


def font(path, size):
    k = (path, size)
    if k not in _fonts:
        _fonts[k] = ImageFont.truetype(path, size, layout_engine=ImageFont.Layout.RAQM)
    return _fonts[k]


def arabic(d, text, center, size, fill=INK, hollow=False):
    """Draw Arabic text with its ink centred on [center] (the font's line box is very tall)."""
    f = font(ARABIC_FONT, size)
    l, t, r, b = d.textbbox((0, 0), text, font=f, direction="rtl")
    x = center[0] - (l + r) / 2
    y = center[1] - (t + b) / 2
    if hollow:
        d.text((x, y), text, font=f, fill=PAPER, stroke_width=max(3, size // 32), stroke_fill=GUIDE, direction="rtl")
    else:
        d.text((x, y), text, font=f, fill=fill, direction="rtl")


def latin(d, text, xy, size, fill=INK, bold=False, anchor="la"):
    d.text(xy, text, font=font(LATIN_BOLD if bold else LATIN_FONT, size), fill=fill, anchor=anchor)


def paragraph(d, text, x, y, size, width_chars, fill=INK, gap=1.45):
    for line in textwrap.wrap(text, width_chars):
        latin(d, line, (x, y), size, fill)
        y += int(size * gap)
    return y


def box(d, x0, y0, x1, y1, fill=None, outline=LINE, r=24, w=3):
    d.rounded_rectangle((x0, y0, x1, y1), radius=r, fill=fill, outline=outline, width=w)


class Book:
    def __init__(self):
        self.pages = []

    def page(self, title=None, subtitle=None):
        im = Image.new("RGB", (W, H), PAPER)
        d = ImageDraw.Draw(im)
        self.pages.append(im)
        n = len(self.pages)
        if n > 1:
            latin(d, f"AlifUp workbook · {n}", (W // 2, H - M // 2), 26, SOFT, anchor="mm")
        y = M
        if title:
            latin(d, title, (M, y), 64, INK, bold=True)
            y += 90
        if subtitle:
            y = paragraph(d, subtitle, M, y, 32, 80, SOFT) + 10
        return d, y

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.pages[0].save(path, save_all=True, append_images=self.pages[1:], resolution=DPI)


# --- Practice words (same rules as the app's LessonBuilder) --------------------------------

def open_(l, h):
    return l["char"] + h[1], l["sound"] + h[2]


def closed(l):
    return l["char"] + SUKUN, l["sound"]


def doubled(l, h):
    return l["char"] + SHADDA + h[1], l["sound"] * 2 + h[2]


def tanwin(l, h):
    if h[2] == "a":
        return l["char"] + FATHATAN + "ا", l["sound"] + "an"
    return (l["char"] + (KASRATAN if h[2] == "i" else DAMMATAN), l["sound"] + ("in" if h[2] == "i" else "un"))


def long_(l, h):
    tail = {"a": ("ا", "ā"), "i": ("ي", "ī"), "u": ("و", "ū")}[h[2]]
    return l["char"] + h[1] + tail[0], l["sound"] + tail[1]


def join(pieces):
    ar = "".join(p[0] for p in pieces)
    lat = ""
    for p in pieces:
        if lat and lat[-1] in "sktdgz" and p[1].startswith("h"):
            lat += "'"
        lat += p[1]
    return ar, lat


def practice_word(rule, rnd):
    used = set()

    def pick(pool):
        choice = rnd.choice([l for l in pool if l["key"] not in used and l["key"] != "ayn"])
        used.add(choice["key"])
        return choice

    closable = [l for l in CONSONANTS if l["key"] not in ("waw", "ya")]
    single = [l for l in CONSONANTS if len(l["sound"]) == 1 and l["sound"] != "ʿ"]
    h = lambda: rnd.choice(HARAKAT)
    if rule == "sukun":
        return join([open_(pick(CONSONANTS), h()), closed(pick(closable))])
    if rule == "shadda":
        return join([open_(pick(CONSONANTS), h()), doubled(pick(single), h())])
    if rule == "tanwin":
        return join([open_(pick(CONSONANTS), h()), tanwin(pick(CONSONANTS), h())])
    if rnd.random() < 0.5:
        return join([long_(pick(CONSONANTS), h()), closed(pick(closable))])
    return join([open_(pick(CONSONANTS), h()), long_(pick(CONSONANTS), h())])


RULES = [
    ("sukun", "Sukūn — no vowel",
     "A small circle above a letter means it has no vowel of its own. It closes the syllable "
     "before it: m-a-k is read \"mak\".", lambda: [join([open_(BY_KEY["mim"], HARAKAT[0]), closed(BY_KEY["kaf"])]),
                                                  join([open_(BY_KEY["qaf"], HARAKAT[2]), closed(BY_KEY["lam"])]),
                                                  join([open_(BY_KEY["mim"], HARAKAT[1]), closed(BY_KEY["nun"])])]),
    ("shadda", "Shadda — a doubled letter",
     "A small \"w\" above a letter means: say it twice — hold it, then add its vowel. "
     "r-a-bb-a is read \"rabba\".", lambda: [join([open_(BY_KEY["ra"], HARAKAT[0]), doubled(BY_KEY["ba"], HARAKAT[0])]),
                                             join([open_(BY_KEY["ta"], HARAKAT[2]), doubled(BY_KEY["mim"], HARAKAT[0])]),
                                             join([open_(BY_KEY["kaf"], HARAKAT[2]), doubled(BY_KEY["lam"], HARAKAT[2])])]),
    ("tanwin", "Tanwīn — the \"n\" endings",
     "A doubled vowel mark adds an \"n\": \"an\", \"in\", \"un\". It comes at the end of a word; "
     "the \"an\" form is usually followed by an alif, which is not read.",
     lambda: [tanwin(BY_KEY["ba"], h) for h in HARAKAT]),
    ("long", "Long vowels",
     "Alif after fatḥa, yā after kasra and wāw after ḍamma stretch the vowel to two beats: "
     "\"bā\", \"bī\", \"bū\".", lambda: [long_(BY_KEY["ba"], h) for h in HARAKAT]),
]


# --- Pages ---------------------------------------------------------------------------------

def cover(book):
    d, _ = book.page()
    # The first letters climbing like steps.
    steps = ["ا", "ب", "ت", "ث"]
    for i, ch in enumerate(steps):
        cx = W - M - 220 - i * 300
        cy = int(H * 0.36) - i * 110
        box(d, cx - 130, cy - 130, cx + 130, cy + 130, fill=GREEN if i == len(steps) - 1 else LIGHT_GREEN, outline=None, r=40)
        arabic(d, ch, (cx, cy), 190, fill=PAPER if i == len(steps) - 1 else GREEN)
    latin(d, "AlifUp", (M, int(H * 0.55)), 150, INK, bold=True)
    latin(d, "Workbook", (M, int(H * 0.55) + 175), 90, GREEN, bold=True)
    paragraph(d, "Learn to read the Quran, letter by letter. The 28 letters, their shapes, the vowels "
                 "and the reading rules — with tracing, reading practice and an answer key.",
              M, int(H * 0.55) + 330, 38, 60, SOFT)
    latin(d, "For learners of every age · use alongside the AlifUp app or on its own",
          (M, H - M - 40), 30, SOFT)


def how_to(book):
    d, y = book.page("How to use this workbook")
    tips = [
        ("Arabic is read from right to left.", "Every row of Arabic in this book starts on the right."),
        ("One letter a day is plenty.", "Read the letter's page, say its name and sound aloud, then trace and write it."),
        ("Trace slowly.", "Follow the grey outline from right to left, then write the letter on your own in the empty boxes."),
        ("Read aloud.", "Cover the Latin letters under the syllables and read the Arabic, then check yourself."),
        ("Ask a teacher.", "Pronunciation is best learned by listening. Hear every letter in the AlifUp app, "
                          "and if you can, read to a teacher who can correct you."),
        ("Practice pages have answers.", "The answer key is at the end of the book."),
    ]
    y += 30
    for i, (head, body) in enumerate(tips, 1):
        d.ellipse((M, y, M + 70, y + 70), fill=GREEN)
        latin(d, str(i), (M + 35, y + 35), 36, PAPER, bold=True, anchor="mm")
        latin(d, head, (M + 100, y + 4), 40, INK, bold=True)
        y = paragraph(d, body, M + 100, y + 62, 32, 66, SOFT) + 40
    box(d, M, y + 20, W - M, y + 330, fill=LIGHT_GREEN, outline=None)
    latin(d, "Contents", (M + 40, y + 50), 40, INK, bold=True)
    paragraph(d, "The alphabet · 28 letter pages · Short vowels · Sukūn · Shadda · Tanwīn · Long vowels · "
                 "Answer key · Certificate", M + 40, y + 120, 32, 70, INK)


def alphabet(book):
    d, y = book.page("The alphabet", "28 letters, read from right to left: start at the top right.")
    cols, rows = 4, 7
    cw = (W - 2 * M) / cols
    ch = (H - y - M - 80) / rows
    for i, l in enumerate(LETTERS):
        r, c = divmod(i, cols)
        x0 = W - M - (c + 1) * cw + 10
        y0 = y + r * ch + 10
        box(d, x0, y0, x0 + cw - 20, y0 + ch - 20, fill=LIGHT_GREEN if l["key"] == "alif" else None)
        arabic(d, l["char"], (x0 + (cw - 20) / 2, y0 + (ch - 20) * 0.42), 130)
        latin(d, l["name"], (x0 + (cw - 20) / 2, y0 + ch - 70), 32, INK, bold=True, anchor="mm")


def letter_page(book, i, l):
    d, _ = book.page()
    y = M
    latin(d, f"LETTER {i} OF 28", (M, y), 28, SOFT, bold=True)
    box(d, W - M - 420, y, W - M, y + 420, fill=LIGHT_GREEN, outline=None, r=40)
    arabic(d, l["char"], (W - M - 210, y + 210), 300)
    latin(d, l["name"], (M, y + 70), 110, INK, bold=True)
    paragraph(d, l["hint"], M, y + 230, 36, 34, SOFT)
    y += 480

    latin(d, "Its shapes in a word" if l["joins"] else "Its shapes — it never joins to the next letter", (M, y), 36, INK, bold=True)
    y += 70
    forms = ([("Alone", l["char"]), ("Start", l["char"] + ZWJ), ("Middle", ZWJ + l["char"] + ZWJ), ("End", ZWJ + l["char"])]
             if l["joins"] else [("Alone", l["char"]), ("End", ZWJ + l["char"])])
    n = len(forms)
    bw = (W - 2 * M - (n - 1) * 30) / n
    for k, (label, text) in enumerate(forms):
        x0 = W - M - (k + 1) * bw - k * 30  # right to left, as in the app
        box(d, x0, y, x0 + bw, y + 280)
        arabic(d, text, (x0 + bw / 2, y + 125), 150)
        latin(d, label, (x0 + bw / 2, y + 245), 28, SOFT, anchor="mm")
    y += 340

    latin(d, "Trace, from right to left", (M, y), 36, INK, bold=True)
    y += 70
    cell = (W - 2 * M) / 5
    for k in range(5):
        x0 = W - M - (k + 1) * cell
        box(d, x0 + 8, y, x0 + cell - 8, y + 250)
        d.line((x0 + 20, y + 175, x0 + cell - 20, y + 175), fill=LINE, width=2)  # baseline
        arabic(d, l["char"], (x0 + cell / 2, y + 125), 170, hollow=True)
    y += 300

    latin(d, "Now write it yourself", (M, y), 36, INK, bold=True)
    y += 70
    for k in range(5):
        x0 = W - M - (k + 1) * cell
        box(d, x0 + 8, y, x0 + cell - 8, y + 250)
        d.line((x0 + 20, y + 175, x0 + cell - 20, y + 175), fill=LINE, width=2)
    y += 300

    if l["key"] == "alif":
        paragraph(d, "Alif carries no vowel of its own: it lengthens a fatḥa (bā) or carries a hamza (ء), "
                     "which then takes the vowel: أَ a, إِ i, أُ u.", M, y + 10, 32, 70, SOFT)
    else:
        latin(d, "Read it with each vowel", (M, y), 36, INK, bold=True)
        y += 70
        cw3 = (W - 2 * M) / 3
        for k, h in enumerate(HARAKAT):
            x0 = W - M - (k + 1) * cw3
            box(d, x0 + 10, y, x0 + cw3 - 10, y + 270, fill=LIGHT_GREEN, outline=None)
            arabic(d, l["char"] + h[1], (x0 + cw3 / 2, y + 105), 120)
            latin(d, l["sound"] + h[2], (x0 + cw3 / 2, y + 235), 36, GREEN, bold=True, anchor="mm")


def vowel_pages(book):
    for name, mark, snd in HARAKAT:
        hint = {"a": "A small stroke above the letter: a short \"a\".",
                "i": "A small stroke below the letter: a short \"i\".",
                "u": "A small wāw above the letter: a short \"u\"."}[snd]
        d, y = book.page(f"Short vowels: {name}", hint + " Read every box aloud, right to left. Answers at the back.")
        cols = 6
        cw = (W - 2 * M) / cols
        chh = 210
        for i, l in enumerate(CONSONANTS):
            r, c = divmod(i, cols)
            x0 = W - M - (c + 1) * cw
            y0 = y + 20 + r * chh
            box(d, x0 + 8, y0, x0 + cw - 8, y0 + chh - 20)
            arabic(d, l["char"] + mark, (x0 + cw / 2, y0 + (chh - 20) / 2), 120)


def rule_pages(book, rnd, answers):
    for key, title, explain, examples in RULES:
        d, y = book.page(title)
        y = paragraph(d, explain, M, y, 36, 62, INK) + 30
        latin(d, "Examples", (M, y), 36, INK, bold=True)
        y += 70
        ex = examples()
        cw = (W - 2 * M) / len(ex)
        for k, (ar, lat) in enumerate(ex):
            x0 = W - M - (k + 1) * cw
            box(d, x0 + 10, y, x0 + cw - 10, y + 290, fill=LIGHT_GREEN, outline=None)
            arabic(d, ar, (x0 + cw / 2, y + 115), 120)
            latin(d, lat, (x0 + cw / 2, y + 250), 38, GREEN, bold=True, anchor="mm")
        y += 350
        latin(d, "Practice: read each word aloud", (M, y), 36, INK, bold=True)
        y += 70
        words = []
        while len(words) < 12:
            w = practice_word(key, rnd)
            if w not in words:
                words.append(w)
        answers.append((title, words))
        cols = 3
        cw = (W - 2 * M) / cols
        for i, (ar, _) in enumerate(words):
            r, c = divmod(i, cols)
            x0 = W - M - (c + 1) * cw
            y0 = y + r * 250
            box(d, x0 + 10, y0, x0 + cw - 10, y0 + 220)
            latin(d, str(i + 1), (x0 + cw - 40, y0 + 30), 26, SOFT, anchor="mm")
            arabic(d, ar, (x0 + cw / 2, y0 + 115), 120)


def answer_key(book, answers):
    d, y = book.page("Answer key")
    latin(d, "Short vowels", (M, y), 40, INK, bold=True)
    y += 70
    for name, mark, snd in HARAKAT:
        line = " · ".join(l["sound"] + snd for l in CONSONANTS)
        latin(d, name, (M, y), 32, GREEN, bold=True)
        y = paragraph(d, line, M + 220, y, 30, 62, INK) + 20
    y += 20
    for title, words in answers:
        latin(d, title, (M, y), 34, INK, bold=True)
        y += 56
        line = "   ".join(f"{i + 1}. {lat}" for i, (_, lat) in enumerate(words))
        y = paragraph(d, line, M, y, 30, 75, INK) + 30


def certificate(book):
    d, _ = book.page()
    box(d, M, M, W - M, H - M, outline=GREEN, w=10, r=50)
    box(d, M + 40, M + 40, W - M - 40, H - M - 40, outline=LIGHT_GREEN, w=6, r=40)
    arabic(d, "ا ب ت ث", (W // 2, int(H * 0.2)), 200, fill=GREEN)
    latin(d, "Certificate", (W // 2, int(H * 0.33)), 120, INK, bold=True, anchor="mm")
    latin(d, "This certifies that", (W // 2, int(H * 0.43)), 44, SOFT, anchor="mm")
    d.line((M + 250, int(H * 0.52), W - M - 250, int(H * 0.52)), fill=INK, width=3)
    latin(d, "has learned to read the 28 Arabic letters,", (W // 2, int(H * 0.58)), 44, INK, anchor="mm")
    latin(d, "the vowels and the reading rules.", (W // 2, int(H * 0.62)), 44, INK, anchor="mm")
    for k, label in enumerate(["Date", "Teacher"]):
        x0 = M + 200 + k * ((W - 2 * M - 400) // 2 + 60)
        d.line((x0, int(H * 0.78), x0 + 420, int(H * 0.78)), fill=INK, width=3)
        latin(d, label, (x0 + 210, int(H * 0.78) + 45), 32, SOFT, anchor="mm")


def main():
    assert features.check("raqm"), "Pillow needs libraqm for Arabic shaping"
    rnd = random.Random(2026)  # fixed, so the workbook is the same every time
    book = Book()
    cover(book)
    how_to(book)
    alphabet(book)
    for i, l in enumerate(LETTERS, 1):
        letter_page(book, i, l)
    vowel_pages(book)
    answers = []
    rule_pages(book, rnd, answers)
    answer_key(book, answers)
    certificate(book)
    book.save(OUT)
    print(f"wrote {os.path.relpath(OUT, ROOT)} ({len(book.pages)} pages)")


if __name__ == "__main__":
    main()
