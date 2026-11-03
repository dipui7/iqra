#!/usr/bin/env python3
"""
Draw the AlifUp icon (an Alif standing on rising steps, on ivory) and write every form the builds need:

  composeApp/src/androidMain/res/drawable/ic_launcher_foreground.xml   Android adaptive icon (foreground)
  composeApp/src/androidMain/res/values/ic_launcher_background.xml     its background colour
  composeApp/src/commonMain/composeResources/drawable/app_icon.xml     in-app / desktop window icon
  composeApp/icon.ico, composeApp/icon.png                             Windows installer / Linux

Needs Pillow (pip install pillow). Edit the shapes below and re-run to change the icon.
"""
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
IVORY = "#F8F4EC"
STEP = "#B3DCC3"      # light sage, as the app's filled shapes
ALIF = "#2F6B4C"      # deep sage
SPARK = "#E39A3B"     # amber, as the streak and coins
# Android masks adaptive icons to the middle of the canvas; standalone icons can fill more of it.
STANDALONE_SCALE = 1.3
CENTER = (54, 54)

# Shapes on a 108 x 108 canvas (Android adaptive icon size; the middle 66 x 66 is always visible).
# Each shape: (colour, commands); commands are ("M"|"L", point), ("C", c1, c2, end) or ("Z",).
def steps(left, right, bottom, tops, r=1.6):
    """Rising steps from [left] to [right], each [tops] high, with softly rounded outer corners."""
    w = (right - left) / len(tops)
    cmds = [("M", (left, bottom)), ("L", (left, tops[0] + r))]
    for i, top in enumerate(tops):
        x = left + i * w
        if i > 0:
            cmds.append(("L", (x, top + r)))
        cmds += [("C", (x, top + r * 0.45), (x + r * 0.45, top), (x + r, top)), ("L", (x + w - (r if i == len(tops) - 1 else 0), top))]
    cmds += [("C", (right - r * 0.45, tops[-1]), (right, tops[-1] + r * 0.45), (right, tops[-1] + r)),
             ("L", (right, bottom - r)), ("C", (right, bottom - r * 0.45), (right - r * 0.45, bottom), (right - r, bottom)),
             ("Z",)]
    return cmds


STEPS = steps(left=27, right=81, bottom=84, tops=[75, 66, 57])
FONT = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/font/amiri_quran.ttf")


def alif_outline(center_x, bottom, height, widen=1.0):
    """
    The Alif (ا) as drawn by the Amiri Quran font (SIL OFL), scaled to stand on the top step.
    [widen] thickens the stroke sideways so it still reads at small icon sizes.
    """
    from fontTools.pens.basePen import BasePen
    from fontTools.ttLib import TTFont

    font = TTFont(FONT)
    glyphs = font.getGlyphSet()
    name = font.getBestCmap()[ord("ا")]
    cmds = []

    class Pen(BasePen):
        def _moveTo(self, p): cmds.append(("M", p))
        def _lineTo(self, p): cmds.append(("L", p))
        def _curveToOne(self, a, b, c): cmds.append(("C", a, b, c))
        def _qCurveToOne(self, q, p):
            p0 = self._getCurrentPoint()
            cmds.append(("C", (p0[0] + 2 / 3 * (q[0] - p0[0]), p0[1] + 2 / 3 * (q[1] - p0[1])),
                         (p[0] + 2 / 3 * (q[0] - p[0]), p[1] + 2 / 3 * (q[1] - p[1])), p))
        def _closePath(self): cmds.append(("Z",))

    glyphs[name].draw(Pen(glyphs))
    pts = [p for c in cmds for p in c[1:]]
    x0, x1 = min(p[0] for p in pts), max(p[0] for p in pts)
    y0, y1 = min(p[1] for p in pts), max(p[1] for p in pts)
    k = height / (y1 - y0)
    # Font units point up; the canvas points down.
    place = lambda p: (round(center_x + (p[0] - (x0 + x1) / 2) * k * widen, 2), round(bottom - (p[1] - y0) * k, 2))
    return [(c[0], *map(place, c[1:])) for c in cmds]


# The Alif stands on the top step.
ALIF_STROKE = alif_outline(center_x=72, bottom=56.6, height=35, widen=1.9)
# A four-point spark, up and to the left of the Alif.
def spark(cx, cy, r):
    k = r * 0.12
    return [("M", (cx, cy - r)), ("C", (cx + k, cy - k * 2.5), (cx + k * 2.5, cy - k), (cx + r, cy)),
            ("C", (cx + k * 2.5, cy + k), (cx + k, cy + k * 2.5), (cx, cy + r)),
            ("C", (cx - k, cy + k * 2.5), (cx - k * 2.5, cy + k), (cx - r, cy)),
            ("C", (cx - k * 2.5, cy - k), (cx - k, cy - k * 2.5), (cx, cy - r)), ("Z",)]


SPARK_STAR = spark(cx=50, cy=36, r=6)
SHAPES = [(STEP, STEPS), (ALIF, ALIF_STROKE), (SPARK, SPARK_STAR)]


def path_data(cmds):
    return " ".join(c[0] + " ".join(f"{x:g},{y:g}" for x, y in c[1:]) for c in cmds)


def vector(background):
    bg = (f'    <path android:fillColor="{IVORY}" android:pathData="M24,0 H84 A24,24 0 0 1 108,24 V84 '
          f'A24,24 0 0 1 84,108 H24 A24,24 0 0 1 0,84 V24 A24,24 0 0 1 24,0 Z" />\n') if background else ""
    group_open = (f'    <group android:scaleX="{STANDALONE_SCALE}" android:scaleY="{STANDALONE_SCALE}" '
                  f'android:pivotX="{CENTER[0]}" android:pivotY="{CENTER[1]}">\n') if background else ""
    group_close = "    </group>\n" if background else ""
    paths = "".join(f'    <path android:fillColor="{colour}" android:pathData="{path_data(cmds)}" />\n'
                    for colour, cmds in SHAPES)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generated by tools/icon/make_icon.py -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n'
            f'{bg}{group_open}{paths}{group_close}</vector>\n')


def polyline(cmds, scale):
    pts, cur = [], None
    for c in cmds:
        if c[0] in "ML":
            cur = c[1]
            pts.append(cur)
        elif c[0] == "C":
            p0, (p1, p2, p3) = cur, c[1:]
            for i in range(1, 25):
                t = i / 24
                x = (1-t)**3*p0[0] + 3*(1-t)**2*t*p1[0] + 3*(1-t)*t*t*p2[0] + t**3*p3[0]
                y = (1-t)**3*p0[1] + 3*(1-t)**2*t*p1[1] + 3*(1-t)*t*t*p2[1] + t**3*p3[1]
                pts.append((x, y))
            cur = p3
    k, (cx, cy) = STANDALONE_SCALE, CENTER
    return [((cx + (x - cx) * k) * scale, (cy + (y - cy) * k) * scale) for x, y in pts]


def raster(size):
    from PIL import Image, ImageDraw
    big = size * 4  # draw large, then downsample for smooth edges
    s = big / 108
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, big - 1, big - 1), radius=24 * s, fill=IVORY)
    for colour, cmds in SHAPES:
        d.polygon(polyline(cmds, s), fill=colour)
    return img.resize((size, size), Image.LANCZOS)


def write(rel, text):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(text)
    print("wrote", rel)


if __name__ == "__main__":
    write("composeApp/src/androidMain/res/drawable/ic_launcher_foreground.xml", vector(background=False))
    write("composeApp/src/androidMain/res/values/ic_launcher_background.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
          f'    <color name="ic_launcher_background">{IVORY}</color>\n</resources>\n')
    write("composeApp/src/commonMain/composeResources/drawable/app_icon.xml", vector(background=True))
    img = raster(512)
    img.save(os.path.join(ROOT, "composeApp/icon.png"))
    img.save(os.path.join(ROOT, "composeApp/icon.ico"), sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print("wrote composeApp/icon.png, composeApp/icon.ico")
