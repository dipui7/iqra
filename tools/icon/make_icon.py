#!/usr/bin/env python3
"""
Draw the AlifUp icon and write every form the builds need.

The icon: the first four letters, ا ب ت ث, climbing from right to left (Arabic order), each
bigger and higher than the last and standing on a small platform, like levels in a game. The
Alif and the letters' dots are amber (the dots are what tell ب ت ث apart); the letters go from
light to deep green. Two sparkles at the top of the climb. The letters are the real glyphs of
the Amiri Quran font (SIL OFL) used in the app.

Writes:
  composeApp/src/androidMain/res/drawable/ic_launcher_foreground.xml   Android adaptive icon (foreground)
  composeApp/src/androidMain/res/values/ic_launcher_background.xml     its background colour
  composeApp/src/commonMain/composeResources/drawable/app_icon.xml     in-app / desktop window icon
  composeApp/icon.ico, composeApp/icon.png                             Windows installer / Linux

Needs Pillow and fontTools (pip install pillow fonttools). Edit the settings below and re-run.
"""
import math
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FONT = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/font/amiri_quran.ttf")

IVORY = "#F8F4EC"
PLATFORM = "#DCEFE2"
AMBER = "#E39A3B"
LETTERS = "ابتث"
COLOURS = [AMBER, "#6FB28C", "#4A8F69", "#2F6B4C"]   # Alif amber, then light to deep green
SCALES = [0.46, 0.68, 0.92, 1.22]                     # each letter bigger than the last
GAP, RISE = 70, 300                                   # font units between letters / per step up

# Android shows only the middle of the 108 x 108 adaptive canvas, so the drawing is laid out in a
# smaller square there; standalone icons (with their own ivory tile) scale it up to fill the tile.
STANDALONE_SCALE = 1.6
CANVAS = 108
LAYOUT = CANVAS / STANDALONE_SCALE   # side of the square the drawing is laid out in


def glyph_commands(ch):
    """The glyph's outline in font units (y up), as M/L/C/Z commands; quadratics become cubics."""
    from fontTools.pens.basePen import BasePen
    from fontTools.ttLib import TTFont

    font = TTFont(FONT)
    glyphs = font.getGlyphSet()
    contours, cur = [], []

    class Pen(BasePen):
        def _moveTo(self, p): cur.clear(); cur.append(("M", p))
        def _lineTo(self, p): cur.append(("L", p))
        def _curveToOne(self, a, b, c): cur.append(("C", a, b, c))
        def _qCurveToOne(self, q, p):
            p0 = self._getCurrentPoint()
            cur.append(("C", (p0[0] + 2 / 3 * (q[0] - p0[0]), p0[1] + 2 / 3 * (q[1] - p0[1])),
                        (p[0] + 2 / 3 * (q[0] - p[0]), p[1] + 2 / 3 * (q[1] - p[1])), p))
        def _closePath(self): cur.append(("Z",)); contours.append(list(cur))

    glyphs[font.getBestCmap()[ord(ch)]].draw(Pen(glyphs))
    return contours


def points(cmds):
    return [p for c in cmds for p in c[1:]]


def box(cmds):
    pts = points(cmds)
    return min(p[0] for p in pts), min(p[1] for p in pts), max(p[0] for p in pts), max(p[1] for p in pts)


def rounded_rect(x0, y0, x1, y1, r):
    k = r * 0.45
    return [("M", (x0 + r, y0)), ("L", (x1 - r, y0)), ("C", (x1 - k, y0), (x1, y0 + k), (x1, y0 + r)),
            ("L", (x1, y1 - r)), ("C", (x1, y1 - k), (x1 - k, y1), (x1 - r, y1)), ("L", (x0 + r, y1)),
            ("C", (x0 + k, y1), (x0, y1 - k), (x0, y1 - r)), ("L", (x0, y0 + r)),
            ("C", (x0, y0 + k), (x0 + k, y0), (x0 + r, y0)), ("Z",)]


def sparkle(cx, cy, r):
    cmds = []
    for i in range(8):
        a = math.pi / 2 + i * math.pi / 4
        rr = r if i % 2 == 0 else r * 0.32
        cmds.append(("M" if i == 0 else "L", (cx + rr * math.cos(a), cy - rr * math.sin(a))))
    return cmds + [("Z",)]


def shapes():
    """Every filled shape as (colour, commands), in canvas coordinates (108 x 108, y down)."""
    s = LAYOUT
    off = (CANVAS - LAYOUT) / 2
    letters = []
    for ch, k, col in zip(LETTERS, SCALES, COLOURS):
        contours = [[(c[0], *[(x * k, y * k) for x, y in c[1:]]) for c in cont] for cont in glyph_commands(ch)]
        areas = []
        for cont in contours:
            x0, y0, x1, y1 = box(cont)
            areas.append((x1 - x0) * (y1 - y0))
        x0, _, x1, _ = box([c for cont in contours for c in cont])
        letters.append(dict(contours=contours, areas=areas, x0=x0, x1=x1, col=col))
    letters = letters[::-1]  # Arabic order: Tha on the left, Alif on the right
    n = len(letters)
    lifts = [(n - 1 - i) * RISE for i in range(n)]
    width = sum(l["x1"] - l["x0"] for l in letters) + GAP * (n - 1)
    ys = [p[1] + lifts[i] for i, l in enumerate(letters) for cont in l["contours"] for p in points(cont)]
    unit = min(s * 0.76 / width, s * 0.58 / (max(ys) - min(ys) + 120))
    x = off + (s - width * unit) / 2
    base = off + s / 2 + (max(ys) + min(ys)) / 2 * unit + s * 0.03

    out = []
    for i, l in enumerate(letters):
        w = (l["x1"] - l["x0"]) * unit
        cx = x + w / 2
        by = base - lifts[i] * unit  # this letter's baseline
        pw = max(w / 2 + s * 0.015, s * 0.05)
        out.append((PLATFORM, rounded_rect(cx - pw, by + s * 0.012, cx + pw, by + s * 0.045, s * 0.016)))
        biggest = max(l["areas"])
        for cont, a in zip(l["contours"], l["areas"]):
            colour = AMBER if a < biggest * 0.15 else l["col"]  # small contours are the dots
            place = lambda p: (round(cx + (p[0] - (l["x0"] + l["x1"]) / 2) * unit, 2), round(by - p[1] * unit, 2))
            out.append((colour, [(c[0], *map(place, c[1:])) for c in cont]))
        x += w + GAP * unit
    out.append((AMBER, sparkle(off + s * 0.80, off + s * 0.24, s * 0.055)))
    out.append(("#9ACFB0", sparkle(off + s * 0.88, off + s * 0.36, s * 0.028)))
    # Centre the whole drawing (letters, platforms and sparkles together) on the canvas.
    pts = [p for _, cmds in out for p in points(cmds)]
    dx = CANVAS / 2 - (min(p[0] for p in pts) + max(p[0] for p in pts)) / 2
    dy = CANVAS / 2 - (min(p[1] for p in pts) + max(p[1] for p in pts)) / 2
    move = lambda p: (round(p[0] + dx, 2), round(p[1] + dy, 2))
    return [(colour, [(c[0], *map(move, c[1:])) for c in cmds]) for colour, cmds in out]


def path_data(cmds):
    return " ".join(c[0] + " ".join(f"{x:g},{y:g}" for x, y in c[1:]) for c in cmds)


def vector(items, background):
    bg = (f'    <path android:fillColor="{IVORY}" android:pathData="M24,0 H84 A24,24 0 0 1 108,24 V84 '
          f'A24,24 0 0 1 84,108 H24 A24,24 0 0 1 0,84 V24 A24,24 0 0 1 24,0 Z" />\n') if background else ""
    group_open = (f'    <group android:scaleX="{STANDALONE_SCALE}" android:scaleY="{STANDALONE_SCALE}" '
                  f'android:pivotX="{CANVAS / 2:g}" android:pivotY="{CANVAS / 2:g}">\n') if background else ""
    group_close = "    </group>\n" if background else ""
    paths = "".join(f'    <path android:fillColor="{colour}" android:pathData="{path_data(cmds)}" />\n'
                    for colour, cmds in items)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generated by tools/icon/make_icon.py -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n'
            f'{bg}{group_open}{paths}{group_close}</vector>\n')


def polygon(cmds, scale):
    """Flatten commands to points, scaled up as in the standalone icon."""
    pts, cur = [], None
    for c in cmds:
        if c[0] in "ML":
            cur = c[1]
            pts.append(cur)
        elif c[0] == "C":
            p0, (p1, p2, p3) = cur, c[1:]
            for i in range(1, 17):
                t = i / 16
                pts.append(tuple((1-t)**3*p0[k] + 3*(1-t)**2*t*p1[k] + 3*(1-t)*t*t*p2[k] + t**3*p3[k] for k in (0, 1)))
            cur = p3
    c0, k = CANVAS / 2, STANDALONE_SCALE
    return [((c0 + (x - c0) * k) * scale, (c0 + (y - c0) * k) * scale) for x, y in pts]


def raster(items, size):
    from PIL import Image, ImageDraw
    big = size * 4  # draw large, then downsample for smooth edges
    s = big / CANVAS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, big - 1, big - 1), radius=24 * s, fill=IVORY)
    for colour, cmds in items:
        d.polygon(polygon(cmds, s), fill=colour)
    return img.resize((size, size), Image.LANCZOS)


def write(rel, text):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(text)
    print("wrote", rel)


if __name__ == "__main__":
    items = shapes()
    write("composeApp/src/androidMain/res/drawable/ic_launcher_foreground.xml", vector(items, background=False))
    write("composeApp/src/androidMain/res/values/ic_launcher_background.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
          f'    <color name="ic_launcher_background">{IVORY}</color>\n</resources>\n')
    write("composeApp/src/commonMain/composeResources/drawable/app_icon.xml", vector(items, background=True))
    img = raster(items, 512)
    img.save(os.path.join(ROOT, "composeApp/icon.png"))
    img.save(os.path.join(ROOT, "composeApp/icon.ico"), sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print("wrote composeApp/icon.png, composeApp/icon.ico")
