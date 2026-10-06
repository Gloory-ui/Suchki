#!/usr/bin/env python3
"""Значки заклинаний для колеса выбора: 49 медальонов 32×32 — диск в цвете школы и символ заклинания.

Свои рисунки: символ строится из простых фигур (многоугольники, круги, линии) с 8-кратным
суперсэмплингом и сводится к чётким пикселям; объём (светлый край сверху-слева, тень снизу-справа),
тёмный контур символа и лёгкое свечение вокруг. Медальон — тот же, что у значков школ (gen_textures.py).
Запуск из корня мода:
    python tools/gen_spell_icons.py                 — записать значки в textures/gui/spell/<id>.png
    python tools/gen_spell_icons.py --preview DIR   — только превью (×8 по школам и ×3 все сразу)
Генерация детерминирована.
"""

import argparse
import colorsys
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from gen_textures import TEX, dilate, medallion, rgb, save_png, sheet, shift

SS = 8                      # суперсэмплинг
C = 16.5                    # центр медальона в пикселях (центр пикселя 16); координаты символов — от него
SYMBOL_R = 10.4             # символ не выходит за тёмное внутреннее кольцо диска

SCHOOL_HEX = {
    "destruction": "#FF7A2E", "restoration": "#52E07F", "light": "#FFD84A", "dark": "#9A4DFF",
    "alteration": "#4DA6FF", "illusion": "#E76BFF", "conjuration": "#3FE0D0",
}
ELEMENT_HEX = {"fire": "#FF7A2E", "ice": "#8FD8FF", "lightning": "#B9A8FF"}

WHITE = np.ones(3)
BLACK = np.zeros(3)


def mix(a, b, k):
    return np.asarray(a) * (1 - k) + np.asarray(b) * k


def luminance(c):
    lin = np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    return float(lin @ np.array([0.2126, 0.7152, 0.0722]))


def tone_ramp(hexc):
    """6 тонов диска из цвета школы. Яркость подбирается по восприятию (жёлтый не светлее синего),
    тёмные тона уходят оттенком к фиолетово-синему, как в пиксель-арте."""
    h, _, s = colorsys.rgb_to_hls(*rgb(hexc))
    out = []
    for k, grey in enumerate([0.10, 0.17, 0.25, 0.34, 0.44, 0.58]):
        dark = 1 - k / 5
        delta = ((0.72 - h + 0.5) % 1) - 0.5
        hh = (h + delta * 0.14 * dark) % 1
        ss = min(1.0, s * (0.85 + 0.15 * (1 - dark)))
        target = luminance(np.full(3, grey))
        lo, hi = 0.0, 1.0
        for _ in range(30):
            mid = (lo + hi) / 2
            if luminance(np.array(colorsys.hls_to_rgb(hh, mid, ss))) < target:
                lo = mid
            else:
                hi = mid
        out.append(colorsys.hls_to_rgb(hh, lo, ss))
    return np.array(out)


FIRE = {"a": rgb("#FF7A2E"), "m": rgb("#FFC447"), "h": rgb("#FFF5CF")}


def colors(school, element=None):
    pal = tone_ramp(SCHOOL_HEX[school])
    base = rgb(ELEMENT_HEX[element] if element else SCHOOL_HEX[school])
    fire = element == "fire"
    return pal, {
        "m": FIRE["m"] if fire else mix(base, WHITE, 0.58),   # основной (светлый); у огня — жёлтое пламя
        "a": base,                                            # акцент — цвет школы/стихии
        "h": FIRE["h"] if fire else mix(base, WHITE, 0.87),   # почти белый
        "d": mix(pal[0], BLACK, 0.25),         # тёмные детали
        "s": mix(base, pal[0], 0.55),          # тень (силуэт-тень, тёмный двойник)
        "gold": rgb("#ffd04a"), "red": rgb("#e8364c"), "bone": rgb("#efe8dc"),
        "stone": rgb("#b6b6be"), "stone_d": rgb("#5c5c68"), "earth": rgb("#b4895a"), "earth_d": rgb("#6a4a2c"),
        "steel": rgb("#d2d8e0"), "steel_d": rgb("#68727f"), "steel_h": rgb("#f4f7fa"), "wood": rgb("#a8743f"),
    }


# ---------------------------------------------------------------- векторные слои

def rot(pts, deg, about=(0.0, 0.0)):
    """Поворот точек на deg градусов (при y вниз: минус — против часовой, т. е. «вверх»)."""
    a = math.radians(deg)
    ca, sa = math.cos(a), math.sin(a)
    ox, oy = about
    return [(ox + (x - ox) * ca - (y - oy) * sa, oy + (x - ox) * sa + (y - oy) * ca) for x, y in pts]


def move(pts, dx, dy):
    return [(x + dx, y + dy) for x, y in pts]


class Layer:
    def __init__(self, color, thr=0.45, bevel=True):
        self.im = Image.new("L", (32 * SS, 32 * SS), 0)
        self.d = ImageDraw.Draw(self.im)
        self.color, self.thr, self.bevel = color, thr, bevel
        self.outline_only = False
        self.dashed = False

    @staticmethod
    def p(x, y):
        return ((C + x) * SS, (C + y) * SS)

    def poly(self, pts, fill=255):
        self.d.polygon([self.p(*q) for q in pts], fill=fill)
        return self

    def ellipse(self, x, y, rx, ry, fill=255):
        self.d.ellipse([(C + x - rx) * SS, (C + y - ry) * SS, (C + x + rx) * SS, (C + y + ry) * SS], fill=fill)
        return self

    def circle(self, x, y, r, fill=255):
        return self.ellipse(x, y, r, r, fill)

    def ring(self, x, y, r, w, fill=255):
        R = r + w / 2
        self.d.ellipse([(C + x - R) * SS, (C + y - R) * SS, (C + x + R) * SS, (C + y + R) * SS],
                       outline=fill, width=max(1, round(w * SS)))
        return self

    def arc(self, x, y, r, a0, a1, w, fill=255):
        """Дуга: углы в градусах по часовой от направления вправо (−90 — вверх)."""
        R = r + w / 2
        self.d.arc([(C + x - R) * SS, (C + y - R) * SS, (C + x + R) * SS, (C + y + R) * SS], a0, a1,
                   fill=fill, width=max(1, round(w * SS)))
        return self

    def line(self, pts, w, fill=255, caps=True):
        self.d.line([self.p(*q) for q in pts], fill=fill, width=max(1, round(w * SS)), joint="curve")
        if caps:
            for q in (pts[0], pts[-1]):
                self.circle(q[0], q[1], w / 2, fill)
        return self

    def rect(self, x0, y0, x1, y1, fill=255):
        self.d.rectangle([self.p(x0, y0), self.p(x1, y1)], fill=fill)
        return self

    def cut(self):
        """Тот же слой, но рисует «дырку» (fill=0) — пример: layer.cut().circle(...)."""
        return _Cut(self)

    def mask(self):
        a = np.asarray(self.im.resize((32, 32), Image.BOX), dtype=float) / 255
        m = a >= self.thr
        if self.outline_only:
            inner = m & shift(m, 1, 0) & shift(m, -1, 0) & shift(m, 0, 1) & shift(m, 0, -1)
            m = m & ~inner
        if self.dashed:
            y, x = np.mgrid[0:32, 0:32]
            m = m & ((x + y) % 3 != 0)
        return m


class _Cut:
    def __init__(self, layer):
        self.layer = layer

    def __getattr__(self, name):
        fn = getattr(self.layer, name)
        return lambda *a, **k: fn(*a, **{**k, "fill": 0})


class Sym:
    def __init__(self, c):
        self.c = c
        self.layers = []

    def __call__(self, color, **kw):
        lay = Layer(self.c[color] if isinstance(color, str) else color, **kw)
        self.layers.append(lay)
        return lay


# ---------------------------------------------------------------- готовые фигуры

def star(lay, x, y, R, r, n=4, rot_deg=-90.0):
    pts = []
    for k in range(2 * n):
        a = math.radians(rot_deg + 180.0 * k / n)
        rr = R if k % 2 == 0 else r
        pts.append((x + rr * math.cos(a), y + rr * math.sin(a)))
    return lay.poly(pts)


def heart(lay, x, y, s):
    """Сердце: центр (x, y), полуширина ≈ 2s."""
    lay.circle(x - s, y - 0.4 * s, s * 1.05).circle(x + s, y - 0.4 * s, s * 1.05)
    return lay.poly([(x - 2.0 * s, y), (x + 2.0 * s, y), (x, y + 2.15 * s)])


def teardrop(lay, x, y, r, tip, angle=-90.0):
    """Капля: круг радиуса r в (x, y), остриё на расстоянии tip от центра в направлении angle."""
    a = math.radians(angle)
    tx, ty = x + tip * math.cos(a), y + tip * math.sin(a)
    th = math.acos(min(1.0, r / tip))
    p1 = (x + r * math.cos(a + th), y + r * math.sin(a + th))
    p2 = (x + r * math.cos(a - th), y + r * math.sin(a - th))
    lay.circle(x, y, r)
    return lay.poly([p1, (tx, ty), p2, (x, y)])


def leaf(lay, p0, p1, w, n=16):
    (x0, y0), (x1, y1) = p0, p1
    dx, dy = x1 - x0, y1 - y0
    ln = math.hypot(dx, dy)
    nx, ny = -dy / ln, dx / ln
    side1, side2 = [], []
    for i in range(n + 1):
        t = i / n
        hw = w * math.sin(math.pi * t) ** 0.8
        cx, cy = x0 + dx * t, y0 + dy * t
        side1.append((cx + nx * hw, cy + ny * hw))
        side2.append((cx - nx * hw, cy - ny * hw))
    return lay.poly(side1 + side2[::-1])


def tapered(lay, p0, p1, w0, w1):
    (x0, y0), (x1, y1) = p0, p1
    ln = math.hypot(x1 - x0, y1 - y0)
    nx, ny = -(y1 - y0) / ln, (x1 - x0) / ln
    return lay.poly([(x0 + nx * w0, y0 + ny * w0), (x1 + nx * w1, y1 + ny * w1),
                     (x1 - nx * w1, y1 - ny * w1), (x0 - nx * w0, y0 - ny * w0)])


def figure(lay, x, y=0.0, k=1.0):
    """Силуэт человека ростом ≈16k, ступни на y+8.5k; между головой и плечами — пиксель зазора."""
    lay.circle(x, y - 6.0 * k, 2.3 * k)
    return lay.poly([(x - 3.3 * k, y - 2.4 * k), (x + 3.3 * k, y - 2.4 * k), (x + 2.6 * k, y + 3.6 * k),
                     (x + 3.0 * k, y + 8.5 * k), (x + 0.9 * k, y + 8.5 * k), (x, y + 4.4 * k),
                     (x - 0.9 * k, y + 8.5 * k), (x - 3.0 * k, y + 8.5 * k), (x - 2.6 * k, y + 3.6 * k)])


def almond(lay, w, h, x=0.0, y=0.0, n=20):
    top = [(x - w + 2 * w * i / n, y - h * math.sin(math.pi * i / n)) for i in range(n + 1)]
    bot = [(x + w - 2 * w * i / n, y + h * math.sin(math.pi * i / n)) for i in range(n + 1)]
    return lay.poly(top + bot)


def sword(s, angle, length=17.0, blade="m", hilt="a"):
    """Меч вдоль направления angle (через центр): остриё вперёд."""
    L = length / 2
    bl = [(L, 0), (L - 2.4, -1.25), (-L + 5.2, -1.25), (-L + 5.2, 1.25), (L - 2.4, 1.25)]
    s(blade).poly(rot(bl, angle))
    s(hilt).poly(rot([(-L + 5.2, -3.6), (-L + 6.6, -3.6), (-L + 6.6, 3.6), (-L + 5.2, 3.6)], angle))
    s(hilt).poly(rot([(-L, -0.9), (-L + 5.2, -0.9), (-L + 5.2, 0.9), (-L, 0.9)], angle))


# ---------------------------------------------------------------- 49 символов

SPELLS = {}


def spell(school, element=None):
    def reg(fn):
        SPELLS[fn.__name__] = (school, element, fn)
        return fn
    return reg


# Разрушение

@spell("destruction", "fire")
def firebolt(s):
    # Стрела вверх-вправо: пламя хвостом, древко, белый наконечник с выемкой.
    trail = [(0.5, -1.7), (-3.0, -3.4), (-4.6, -2.0), (-8.4, -4.6), (-7.2, -1.2), (-10.4, 0), (-7.2, 1.2),
             (-8.4, 4.6), (-4.6, 2.0), (-3.0, 3.4), (0.5, 1.7)]
    s("a").poly(rot(trail, -45))
    s("m").poly(rot([(0.5, -0.9), (-3.0, -1.8), (-6.6, -0.4), (-6.6, 0.4), (-3.0, 1.8), (0.5, 0.9)], -45))
    s("h").line(rot([(-3.0, 0), (5.0, 0)], -45), 1.5)
    s("h").poly(rot([(10.4, 0), (3.8, -4.0), (5.4, 0), (3.8, 4.0)], -45))


@spell("destruction", "fire")
def flames(s):
    # Струя пламени: узко у руки (внизу слева), расходится веером языков вверх-вправо.
    outer = [(-9.8, 0), (-4, -2.2), (1, -4.4), (3.8, -6.8), (5.2, -4.6), (8.2, -6.6), (7.8, -3.0), (10.2, -1.4),
             (8.2, 0.8), (10.2, 3.0), (7.0, 3.8), (7.6, 6.8), (4.0, 4.8), (1, 4.4), (-4, 2.2)]
    s("a").poly(rot(outer, -45))
    s("m").poly(rot([(-8.2, 0), (-2, -1.8), (3, -3.4), (6.2, -2.4), (7.6, -0.2), (6.2, 2.0), (3, 3.2), (-2, 1.8)], -45))
    s("h").poly(rot([(-7.0, 0), (0, -0.9), (4.4, 0), (0, 0.9)], -45))


@spell("destruction", "ice")
def ice_shard(s):
    shard = [(9.6, 0), (3, -3.3), (-8.6, -1.6), (-9.6, 0.4), (-6, 2.5), (3, 3.1)]
    s("a").poly(rot(shard, -45))
    s("m").poly(rot([(9.6, 0), (3, -3.3), (-8.6, -1.6), (-7.6, -0.2), (3, 0)], -45))
    s("h", bevel=False).line(rot([(-6.5, -0.2), (8.2, 0)], -45), 0.9, caps=False)
    small = [(3.4, 5.2), (-0.8, 3.7), (-5.4, 5.5), (-0.8, 7.0)]
    s("a").poly(rot(small, -45))
    s("m").poly(rot([(3.4, 5.2), (-0.8, 3.7), (-5.4, 5.5), (-0.8, 5.4)], -45))


@spell("destruction", "lightning")
def chain_lightning(s):
    main = [(-1, -9.6), (3, -4.2), (-1.2, -2.6), (4.2, 3.4), (0.8, 4.6), (3.2, 9.4)]
    branch = [(-1.2, -2.6), (-5.2, 1.2), (-3.4, 2.4), (-7.8, 7.0)]
    branch2 = [(4.2, 3.4), (8.2, 4.4)]
    glow = s("a", bevel=False)
    for pts in (main, branch, branch2):
        glow.line(pts, 3.4)
    core = s("h", bevel=False)
    for pts in (main, branch, branch2):
        core.line(pts, 1.4)


@spell("destruction", "ice")
def frost_nova(s):
    arms = s("a")
    for k in range(6):
        a = math.radians(-90 + 60 * k)
        ca, sa = math.cos(a), math.sin(a)
        arms.line([(0, 0), (8.6 * ca, 8.6 * sa)], 1.7)
        bx, by = 5.0 * ca, 5.0 * sa
        for side in (-1, 1):
            b = a + side * math.radians(48)
            arms.line([(bx, by), (bx + 2.9 * math.cos(b), by + 2.9 * math.sin(b))], 1.4)
    hexa = [(2.7 * math.cos(math.radians(30 + 60 * k)), 2.7 * math.sin(math.radians(30 + 60 * k))) for k in range(6)]
    s("h").poly(hexa)


@spell("destruction", "fire")
def fireball(s):
    # Шар летит вниз-влево, за ним три языка пламени.
    tongues = s("a")
    for (y0, y1, w, ln) in [(-3.0, -5.2, 1.7, 8.6), (0.0, 0.0, 2.3, 10.4), (3.0, 5.2, 1.7, 8.6)]:
        tapered(tongues, *rot([(-2.0, y0), (ln, y1)], -45), w, 0.2)
    ball = rot([(-3.6, 0)], -45)[0]
    s("a").circle(ball[0], ball[1], 5.4)
    s("m").circle(ball[0] - 0.4, ball[1] + 0.4, 4.0)
    s("h").circle(ball[0] - 1.0, ball[1] + 1.0, 2.1)


@spell("destruction", "fire")
def meteor_shower(s):
    for (x, y, r, L) in [(-4.4, 5.0, 3.0, 7.0), (4.2, 2.6, 2.4, 6.0), (-1.8, -4.6, 2.0, 5.0)]:
        tail = [(0, -r), (L, -0.3), (L, 0.3), (0, r)]
        s("a").poly(move(rot(tail, -45), x, y)).circle(x, y, r)
        s("m").circle(x - 0.3, y + 0.3, r * 0.68)
        s("h", bevel=False).circle(x - 0.6, y + 0.6, r * 0.34)


# Восстановление

@spell("restoration")
def healing(s):
    heart(s("m"), 0, 1.4, 3.1)
    s("h", bevel=False).circle(-3.3, -0.8, 1.0)
    sp = s("h", bevel=False)
    star(sp, 6.6, -6.4, 2.8, 0.7)
    star(sp, -6.8, -5.8, 2.0, 0.55)
    star(sp, 7.4, 3.2, 1.6, 0.5)


@spell("restoration")
def ward(s):
    shield = [(-6.6, -7.6), (6.6, -7.6), (6.6, -0.6), (5.0, 3.6), (0, 8.6), (-5.0, 3.6), (-6.6, -0.6)]
    s("m").poly(shield)
    inner = [(x * 0.68, (y + 0.4) * 0.68 - 0.4) for x, y in shield]
    s("a").poly(inner)
    s("h").rect(-0.95, -4.6, 0.95, 3.0).rect(-3.4, -1.9, 3.4, -0.1)


@spell("restoration")
def heal_other(s):
    heart(s("m"), -3.0, 2.6, 2.6)
    arrow = s("h")
    arrow.line([(-0.6, -0.6), (3.6, -4.4)], 1.8)
    arrow.poly([(7.0, -7.0), (1.4, -6.0), (6.0, -1.4)])


@spell("restoration")
def cure(s):
    glass = s("m")
    glass.circle(0, 3.0, 5.4).rect(-1.9, -6.4, 1.9, -1.2).rect(-2.9, -8.0, 2.9, -6.2)
    liquid = s("a")
    liquid.circle(0, 3.0, 4.2)
    liquid.cut().rect(-6, -3, 6, 1.4)
    s("h").rect(-0.9, 1.9, 0.9, 6.9).rect(-2.5, 3.5, 2.5, 5.3)
    s("h", bevel=False).arc(0, 3.0, 3.4, 190, 245, 1.0)


@spell("restoration")
def rejuvenation(s):
    for k in range(3):
        a0 = math.radians(-90 + 120 * k)
        a1 = a0 + math.radians(52)
        p0 = (1.8 * math.cos(a0), 1.8 * math.sin(a0))
        p1 = (9.2 * math.cos(a1), 9.2 * math.sin(a1))
        leaf(s("a"), p0, p1, 2.7)
        s("m", bevel=False).line([p0, ((p0[0] + p1[0] * 3) / 4, (p0[1] + p1[1] * 3) / 4)], 0.9, caps=False)
    s("h").circle(0, 0, 1.6)


@spell("restoration")
def healing_circle(s):
    s("a").ring(0, 0, 8.0, 1.8)
    s("m").rect(-1.7, -5.6, 1.7, 5.6).rect(-5.6, -1.7, 5.6, 1.7)
    dots = s("h", bevel=False)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        dots.circle(8.0 * math.cos(a), 8.0 * math.sin(a), 1.3)


@spell("restoration")
def undying(s):
    wings = s("h")
    left = [(-2.6, -1.4), (-6.4, -4.8), (-9.6, -5.6), (-8.6, -3.4), (-10.0, -2.2), (-8.4, -0.6), (-9.2, 1.0),
            (-6.8, 1.4), (-6.8, 3.0), (-3.6, 2.6)]
    wings.poly(left).poly([(-x, y) for x, y in left])
    heart(s("gold"), 0, 1.0, 2.7)


# Свет

@spell("light")
def candlelight(s):
    teardrop(s("a"), 0, 3.0, 4.6, 12.0)
    teardrop(s("m"), 0, 3.8, 3.0, 8.2)
    teardrop(s("h"), 0, 4.6, 1.6, 4.4)
    sp = s("h", bevel=False)
    star(sp, -6.4, -5.0, 2.0, 0.55)
    star(sp, 6.6, -2.6, 1.6, 0.5)


@spell("light")
def smite(s):
    s("m").poly([(0, -9.8), (2.4, -2.0), (0, 6.2), (-2.4, -2.0)])
    s("a").rect(-4.2, -3.6, 4.2, -2.2)
    s("h", bevel=False).line([(0, -8.0), (0, 4.0)], 0.9, caps=False)
    burst = s("a", bevel=False)
    burst.line([(-6.0, 8.2), (6.0, 8.2)], 1.3)
    burst.line([(-2.6, 7.0), (-5.4, 4.6)], 1.1).line([(2.6, 7.0), (5.4, 4.6)], 1.1)


@spell("light")
def flash(s):
    pts = []
    for k in range(16):
        a = math.radians(-90 + 22.5 * k)
        r = (9.6 if k % 4 == 0 else 6.2) if k % 2 == 0 else 1.9
        pts.append((r * math.cos(a), r * math.sin(a)))
    s("a").poly(pts)
    s("h").poly([(x * 0.5, y * 0.5) for x, y in pts]).circle(0, 0, 1.9)


@spell("light")
def sunbeam(s):
    s("m").poly([(-3.8, -1.0), (-1.0, -3.8), (9.4, 2.2), (2.2, 9.4)])
    s("h", bevel=False).line([(-1.6, -1.6), (6.0, 6.0)], 1.2, caps=False)
    sun = s("a")
    sun.circle(-4.4, -4.4, 3.4)
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        if k == 0:
            continue
        sun.line([(-4.4 + 4.6 * math.cos(a), -4.4 + 4.6 * math.sin(a)),
                  (-4.4 + 6.2 * math.cos(a), -4.4 + 6.2 * math.sin(a))], 1.3)
    s("h").circle(-5.0, -5.0, 1.5)


@spell("light")
def blessing(s):
    person = s("m")
    person.circle(0, -0.8, 3.4).ellipse(0, 9.6, 7.8, 6.2)
    s("a").ellipse(0, -6.6, 6.6, 2.5).cut().ellipse(0, -6.6, 4.6, 1.0)
    rays = s("h", bevel=False)
    rays.line([(-8.4, -8.6), (-7.0, -7.4)], 1.0).line([(8.4, -8.6), (7.0, -7.4)], 1.0).line([(0, -10.4), (0, -9.4)], 1.0)


@spell("light")
def sanctuary(s):
    s("m").poly([(-8.4, -2.6), (0, -8.6), (8.4, -2.6)])
    s("h", bevel=False).circle(0, -4.8, 1.1)
    s("a").rect(-7.8, -2.6, 7.8, -1.0)
    cols = s("m")
    for x in (-5.6, -1.9, 1.9, 5.6):
        cols.rect(x - 0.8, -1.0, x + 0.8, 5.4)
    s("a").rect(-8.4, 5.4, 8.4, 7.4)


@spell("light")
def judgement(s):
    # Столп света бьёт с неба в землю: луч от верхнего края, по бокам облачка, внизу вспышка.
    clouds = s("m")
    clouds.ellipse(-6.0, -6.2, 2.8, 1.5).ellipse(-4.4, -7.4, 1.8, 1.4)
    clouds.ellipse(6.0, -6.2, 2.8, 1.5).ellipse(4.4, -7.4, 1.8, 1.4)
    s("a").poly([(-2.0, -11.0), (2.0, -11.0), (3.0, 5.4), (-3.0, 5.4)])
    s("h").poly([(-0.7, -11.0), (0.7, -11.0), (1.1, 5.4), (-1.1, 5.4)])
    impact = s("h", bevel=False)
    impact.ellipse(0, 6.6, 5.6, 1.5)
    impact.line([(-4.2, 4.4), (-7.0, 2.2)], 1.1).line([(4.2, 4.4), (7.0, 2.2)], 1.1)
    impact.line([(-6.2, 8.6), (-8.2, 8.2)], 1.0).line([(6.2, 8.6), (8.2, 8.2)], 1.0)


# Тьма

@spell("dark")
def shadow_bolt(s):
    tr = s("a")
    tapered(tr, (-1.0, 1.0), (-8.6, 6.0), 2.6, 0.4)
    tapered(tr, (0.6, 2.6), (-6.4, 8.6), 1.8, 0.3)
    tapered(tr, (-1.6, -0.6), (-8.4, 1.6), 1.6, 0.3)
    s("m").circle(2.6, -2.6, 5.4)
    s("d").circle(2.6, -2.6, 4.0)
    s("a", bevel=False).arc(2.6, -2.6, 2.2, 200, 330, 1.0)


@spell("dark")
def drain_life(s):
    beam = s("a", bevel=False)
    pts = [(-8.6 + 11 * t / 20, 7.6 - 9.4 * t / 20 + 1.2 * math.sin(t * 0.9)) for t in range(21)]
    beam.line(pts, 1.6)
    teardrop(s("red"), 3.6, -1.0, 3.6, 8.4)
    s("h", bevel=False).circle(2.4, -1.8, 1.0)


@spell("dark")
def curse(s):
    drips = s("a")
    for x, ln in ((-3.4, 3.0), (0, 4.4), (3.4, 3.0)):
        drips.line([(x, 3.0), (x, 3.0 + ln)], 1.4)
    almond(s("m"), 8.6, 4.6)
    s("a").circle(0, 0, 3.4)
    s("d").ellipse(0, 0, 0.9, 2.9)
    s("h", bevel=False).circle(-1.4, -1.4, 0.75)


@spell("dark")
def shadow_step(s):
    trail = s("s", bevel=False)
    for y in (-3.0, 0.4, 3.8):
        trail.line([(-9.6, y), (-6.6, y)], 1.1)
    figure(s("s"), -2.6, 0.4)
    figure(s("m"), 3.0, 0.0)


@spell("dark")
def raise_dead(s):
    bone = s("bone")
    bone.circle(0, -2.4, 6.0).rect(-3.8, 1.0, 3.8, 6.4)
    holes = s("d", bevel=False)
    holes.circle(-2.5, -1.6, 1.8).circle(2.5, -1.6, 1.8)
    holes.poly([(-1.0, 1.8), (1.0, 1.8), (0, 0.2)])
    for x in (-1.6, 0.4, 2.4):
        holes.rect(x - 0.5, 4.0, x + 0.5, 6.4)
    s("a", bevel=False).circle(-2.5, -1.6, 0.7).circle(2.5, -1.6, 0.7)


@spell("dark")
def darkness(s):
    rim = s("m")
    rim.circle(-4.2, 0.0, 3.8).circle(0, -2.8, 4.6).circle(4.4, 0.0, 3.6).rect(-4.2, 0.0, 4.4, 3.6)
    body = s("d")
    body.circle(-3.6, 0.8, 3.4).circle(0.6, -1.8, 4.2).circle(4.8, 0.8, 3.2).rect(-3.6, 0.8, 4.8, 4.0)
    for (x, y) in [(-3.6, 7.4), (0.6, 8.6), (4.8, 7.0)]:
        teardrop(s("a"), x, y, 1.2, 3.0)


@spell("dark")
def soul_harvest(s):
    for (x, y) in [(5.0, 0.6), (6.8, 5.4)]:
        teardrop(s("a"), x, y, 1.5, 4.0, angle=-115)
        s("h", bevel=False).circle(x - 0.4, y - 0.2, 0.55)
    s("wood").line([(-5.6, 9.2), (2.6, -8.4)], 1.7)
    blade = [(2.8, -8.6), (0.0, -9.6), (-3.4, -9.4), (-6.4, -8.0), (-8.6, -5.6), (-9.8, -2.6),
             (-7.6, -4.6), (-5.4, -6.0), (-2.6, -6.8), (0.4, -6.8), (2.2, -6.4)]
    s("m").poly(blade)
    s("h", bevel=False).line([(-0.4, -9.0), (-4.0, -8.6), (-7.0, -6.8)], 0.9, caps=False)


# Изменение

@spell("alteration")
def stoneflesh(s):
    shield = [(-6.8, -7.6), (6.8, -7.6), (6.8, -0.6), (5.2, 3.6), (0, 8.8), (-5.2, 3.6), (-6.8, -0.6)]
    s("stone").poly(shield)
    cracks = s("stone_d", bevel=False)
    cracks.line([(-6.4, -3.0), (6.4, -3.0)], 1.0, caps=False).line([(-6.0, 1.8), (6.0, 1.8)], 1.0, caps=False)
    for x0, y0, y1 in [(-1.8, -7.4, -3.0), (2.8, -3.0, 1.8), (-3.2, -3.0, 1.8), (0.2, 1.8, 6.6)]:
        cracks.line([(x0, y0), (x0, y1)], 1.0, caps=False)


@spell("alteration")
def telekinesis(s):
    hand = s("m")
    hand.rect(-7.6, 0.4, -0.4, 6.8)
    for x, top in ((-7.0, -3.6), (-5.0, -4.8), (-3.0, -4.6), (-1.0, -3.2)):
        hand.rect(x - 0.6, top, x + 0.6, 0.8)
    hand.line([(0.0, 3.2), (2.6, 0.2)], 1.4)
    waves = s("a", bevel=False)
    waves.arc(-3.6, -1.0, 6.0, -75, -20, 1.0).arc(-3.6, -1.0, 8.2, -70, -25, 1.0)
    s("h").poly([(5.0, -9.2), (8.6, -7.2), (5.0, -5.2), (1.4, -7.2)])
    s("m").poly([(1.4, -7.2), (5.0, -5.2), (5.0, -1.2), (1.4, -3.2)])
    s("a").poly([(5.0, -5.2), (8.6, -7.2), (8.6, -3.2), (5.0, -1.2)])


@spell("alteration")
def featherfall(s):
    vane = s("m")
    leaf(vane, (-6.6, 7.6), (7.4, -8.4), 3.9)
    cut = vane.cut()
    for t, side in ((0.35, 1), (0.55, -1), (0.7, 1), (0.42, -1)):
        x, y = -6.6 + 14.0 * t, 7.6 - 16.0 * t
        nx, ny = 0.75 * side, 0.66 * side
        cut.line([(x + nx * 1.6, y + ny * 1.6), (x + nx * 4.6 + 1.2, y + ny * 4.6 + 1.2)], 0.7, caps=False)
    s("a", bevel=False).line([(-8.8, 9.8), (6.2, -7.0)], 1.2)


@spell("alteration")
def blink(s):
    s("m").rect(-2.6, -1.7, 3.4, 1.7).poly([(2.6, -6.0), (9.4, 0), (2.6, 6.0)])
    speed = s("a", bevel=False)
    speed.line([(-9.0, -4.0), (-3.6, -4.0)], 1.3).line([(-8.0, 0), (-4.4, 0)], 1.3).line([(-9.0, 4.0), (-3.6, 4.0)], 1.3)


@spell("alteration")
def deep_breath(s):
    b = s("m")
    for x, y, r in [(-2.0, 3.2, 4.2), (4.2, -3.0, 2.8), (-3.2, -6.4, 2.0), (5.4, 5.6, 1.7)]:
        b.ring(x, y, r - 0.7, 1.4)
    hi = s("h", bevel=False)
    for x, y, r in [(-2.0, 3.2, 4.2), (4.2, -3.0, 2.8)]:
        hi.circle(x - r * 0.4, y - r * 0.4, 0.8)


@spell("alteration")
def paralyze(s):
    def link(lay, x, y, rx, ry, ang, w):
        outer = [(x + rx * math.cos(t), y + ry * math.sin(t)) for t in np.linspace(0, 2 * math.pi, 40)]
        inner = [(x + (rx - w) * math.cos(t), y + (ry - w) * math.sin(t)) for t in np.linspace(0, 2 * math.pi, 40)]
        lay.poly(rot(outer, ang, (x, y)))
        lay.cut().poly(rot(inner, ang, (x, y)))
    s("a").poly(rot([(-3.0, -1.0), (3.0, -1.0), (3.0, 1.0), (-3.0, 1.0)], -45))
    link(s("m"), -4.4, 4.4, 4.6, 2.9, -45, 1.6)
    link(s("m"), 4.4, -4.4, 4.6, 2.9, -45, 1.6)


@spell("alteration")
def earthquake(s):
    s("earth").poly([(-10.6, 1.0), (-5.0, -0.6), (-1.0, 1.0), (0.6, -0.4), (4.0, 1.4), (10.6, 0.0),
                     (10.6, 11.0), (-10.6, 11.0)])
    s("earth_d", bevel=False).line([(0.6, -0.2), (1.8, 3.0), (-0.6, 5.6), (1.0, 8.6), (0.0, 11.0)], 1.6)
    s("earth_d", bevel=False).line([(-0.6, 5.6), (-4.0, 7.0)], 1.1)
    rocks = s("earth")
    rocks.poly(rot([(-1.2, -1.2), (1.2, -1.2), (1.2, 1.2), (-1.2, 1.2)], 20, (0, 0)))
    rocks.poly(move(rot([(-1.3, -1.3), (1.3, -1.3), (1.3, 1.3), (-1.3, 1.3)], 30), -5.0, -4.6))
    rocks.poly(move(rot([(-1.0, -1.0), (1.0, -1.0), (1.0, 1.0), (-1.0, 1.0)], 10), 4.6, -6.0))
    rocks.poly(move(rot([(-0.8, -0.8), (0.8, -0.8), (0.8, 0.8), (-0.8, 0.8)], 40), -0.6, -8.4))
    s("a", bevel=False).line([(-7.6, -7.6), (-6.6, -6.2)], 1.0).line([(7.4, -3.6), (6.8, -2.2)], 1.0)


# Иллюзия

@spell("illusion")
def night_eye(s):
    almond(s("m"), 8.2, 4.4, 0, 2.0)
    s("a").circle(0, 2.0, 3.4)
    s("d").circle(0, 2.0, 1.8)
    s("h", bevel=False).circle(-1.2, 0.8, 0.8)
    moon = s("h")
    moon.circle(5.6, -6.6, 2.8)
    moon.cut().circle(6.9, -7.4, 2.3)
    star(s("h", bevel=False), -5.6, -6.8, 1.6, 0.45)


@spell("illusion")
def fear(s):
    s("m").ellipse(0, 0.4, 6.4, 8.4)
    f = s("d", bevel=False)
    f.ellipse(-2.6, -0.6, 1.3, 1.9).ellipse(2.6, -0.6, 1.3, 1.9)
    f.line([(-4.4, -4.0), (-1.4, -5.6)], 1.0, caps=False).line([(4.4, -4.0), (1.4, -5.6)], 1.0, caps=False)
    f.ellipse(0, 4.6, 1.5, 2.2)
    teardrop(s("a"), 7.4, -3.4, 1.3, 3.2)


@spell("illusion")
def calm(s):
    for k in range(5):
        a = math.radians(-90 + 72 * k)
        leaf(s("m"), (1.4 * math.cos(a), 1.4 * math.sin(a)), (9.2 * math.cos(a), 9.2 * math.sin(a)), 3.0)
    s("gold").circle(0, 0, 2.4)


@spell("illusion")
def invisibility(s):
    figure(s("s", bevel=False), 0, 0.2, 1.08)
    sil = s("h", bevel=False)
    sil.outline_only = True
    sil.dashed = True
    figure(sil, 0, 0.2, 1.08)


@spell("illusion")
def frenzy(s):
    sword(s, -45, 18.0)
    sword(s, -135, 18.0)


@spell("illusion")
def mirror_image(s):
    figure(s("a"), 3.8, 0.4, 0.92)
    figure(s("m"), -3.6, 0.0, 0.92)


@spell("illusion")
def mass_hysteria(s):
    pts = []
    for k in range(240):
        th = k / 240 * 2.4 * 2 * math.pi
        r = 0.8 + th * 0.56
        pts.append((r * math.cos(th), r * math.sin(th)))
    s("m", bevel=False).line(pts, 1.6)
    s("a").circle(0, 0, 1.2)


# Призыв

@spell("conjuration")
def bound_blade(s):
    s("m").poly([(0, -10.0), (1.9, -7.6), (1.9, 3.0), (-1.9, 3.0), (-1.9, -7.6)])
    s("h", bevel=False).line([(0, -8.4), (0, 1.6)], 0.9, caps=False)
    s("a").rect(-5.2, 3.0, 5.2, 4.6).rect(-0.9, 4.6, 0.9, 7.6)
    s("h").circle(0, 8.6, 1.4)


@spell("conjuration")
def familiar(s):
    head = [(-7.2, -3.4), (-3.0, -5.6), (3.0, -5.6), (7.2, -3.4), (6.6, 1.2), (2.6, 4.2), (1.8, 7.8),
            (-1.8, 7.8), (-2.6, 4.2), (-6.6, 1.2)]
    ears = s("m")
    ears.poly([(-7.0, -9.0), (-2.4, -4.6), (-7.4, -2.4)]).poly([(7.0, -9.0), (2.4, -4.6), (7.4, -2.4)])
    s("m").poly(head)
    s("a").poly([(-6.0, -6.8), (-3.6, -4.8), (-6.2, -3.8)]).poly([(6.0, -6.8), (3.6, -4.8), (6.2, -3.8)])
    s("h").poly([(-2.6, 0.6), (2.6, 0.6), (1.6, 6.6), (-1.6, 6.6)])
    eyes = s("a", bevel=False)
    eyes.poly([(-4.6, -1.8), (-1.8, -1.2), (-2.4, 0.0), (-4.4, -0.6)])
    eyes.poly([(4.6, -1.8), (1.8, -1.2), (2.4, 0.0), (4.4, -0.6)])
    s("d", bevel=False).ellipse(0, 6.6, 1.5, 1.0)


@spell("conjuration")
def bound_tool(s):
    s("a").line([(-7.6, 8.6), (3.4, -2.4)], 1.8)
    s("m").arc(-2.2, 2.2, 9.0, -110, 20, 2.4)
    tips = s("m")
    tips.poly(rot([(0, -1.2), (2.4, 0), (0, 1.2)], -110 - 90, (0, 0)))
    s("h", bevel=False).arc(-2.2, 2.2, 9.6, -80, -10, 0.8)


@spell("conjuration")
def spirit_blades(s):
    swirl = s("a", bevel=False)
    for k in range(3):
        swirl.arc(0, 0, 6.4, -70 + 120 * k, -20 + 120 * k, 1.0)
    for k in range(3):
        ang = -90 + 120 * k
        s("m").poly(rot([(2.4, 0), (4.4, -1.7), (10.0, 0), (4.4, 1.7)], ang))
        s("h", bevel=False).line(rot([(4.0, 0), (8.4, 0)], ang), 0.8, caps=False)
    s("a").circle(0, 0, 1.9)


@spell("conjuration")
def spectral_steed(s):
    # Голова коня в профиль (морда влево): ухо, лоб, длинная морда, челюсть, шея; грива по спине шеи.
    head = [(1.5, -9.8), (3.2, -7.0), (5.6, -5.0), (7.4, -1.0), (8.2, 3.5), (8.0, 9.2), (0.8, 9.2), (0.4, 5.0),
            (-1.2, 2.8), (-6.0, 3.2), (-8.4, 2.2), (-9.4, 0.2), (-8.8, -1.6), (-5.0, -3.2), (-2.4, -5.8), (-0.6, -7.6)]
    s("m").poly(head)
    s("a").poly([(1.5, -9.8), (3.2, -7.0), (5.6, -5.0), (7.4, -1.0), (8.2, 3.5), (8.1, 7.0), (6.4, 5.2),
                 (6.4, 2.4), (5.0, 1.4), (5.2, -1.2), (3.8, -2.6), (3.4, -4.8), (1.8, -6.0)])
    face = s("d", bevel=False)
    face.circle(-2.6, -3.0, 0.85).circle(-7.8, -0.2, 0.6)
    face.line([(-8.6, 1.6), (-6.4, 1.8)], 0.8, caps=False)


@spell("conjuration")
def recall(s):
    s("m").rect(-4.8, -0.4, 4.8, 6.6)
    s("a").poly([(-6.8, 0.2), (0, -6.0), (6.8, 0.2)])
    s("d", bevel=False).rect(-1.3, 2.4, 1.3, 6.6)
    arrow = s("h", bevel=False)
    arrow.arc(0, 0.6, 9.2, 200, 330, 1.4)
    a = math.radians(330)
    tx, ty = 9.2 * math.cos(a), 0.6 + 9.2 * math.sin(a)
    arrow.poly(move(rot([(2.0, 0), (-1.4, -2.0), (-1.4, 2.0)], 330 + 90), tx, ty))


@spell("conjuration")
def iron_guardian(s):
    s("steel").rect(-5.8, -7.4, 5.8, 6.2)
    s("steel_d", bevel=False).rect(-5.8, -3.4, 5.8, -1.8)
    s("red", bevel=False).rect(-3.8, -1.8, -1.6, -0.4).rect(1.6, -1.8, 3.8, -0.4)
    s("steel_h").rect(-1.3, -1.8, 1.3, 5.6)
    s("steel_d", bevel=False).rect(1.3, -1.0, 1.9, 5.6)
    s("steel_d", bevel=False).rect(-4.0, 4.0, -2.0, 4.8).rect(2.4, 4.0, 4.4, 4.8)
    s("a", bevel=False).line([(-5.0, -7.0), (-4.2, -3.0), (-5.2, 1.0), (-4.4, 5.6)], 1.1, caps=False)


# ---------------------------------------------------------------- сборка значка

def icon(name):
    school, element, fn = SPELLS[name]
    pal, c = colors(school, element)
    cv, rim, disc = medallion(pal)
    s = Sym(c)
    fn(s)
    inside = np.hypot(cv.x - C, cv.y - C) <= SYMBOL_R
    union = np.zeros((32, 32), dtype=bool)
    for lay in s.layers:
        m = lay.mask() & inside
        if not m.any():
            continue
        col = np.asarray(lay.color)
        cv.rgb[m] = col
        if lay.bevel:
            ul, dr = shift(m, 1, 1), shift(m, -1, -1)
            cv.rgb[m & ~ul & dr] = mix(col, WHITE, 0.38)
            cv.rgb[m & ~dr & ul] = col * 0.74
        union |= m
    ring1 = dilate(union, 1) & ~union & disc
    cv.rgb[ring1] = mix(pal[0], BLACK, 0.3)
    ring2 = dilate(union, 2) & ~dilate(union, 1) & disc
    cv.tint(ring2, c["a"], 0.24)
    cv.glow(rim | disc, mix(c["a"], WHITE, 0.4), [(1, 0.3), (2, 0.12)])
    return cv.image()


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--preview", type=Path, help="только превью в эту папку (значки в src не трогаются)")
    args = ap.parse_args()
    icons = {name: icon(name) for name in SPELLS}
    if not args.preview:
        for name, im in icons.items():
            save_png(im, TEX / "gui" / "spell" / f"{name}.png")
        print(f"записано {len(icons)} значков в {TEX / 'gui' / 'spell'}")
        return
    args.preview.mkdir(parents=True, exist_ok=True)
    for school in SCHOOL_HEX:
        items = [(n, im) for n, im in icons.items() if SPELLS[n][0] == school]
        sheet(items, 8, 4, dark=True).save(args.preview / f"spells_{school}.png")
    sheet(list(icons.items()), 3, 7, dark=True).save(args.preview / "spells_all.png")
    print(f"превью {len(icons)} значков в {args.preview}")


if __name__ == "__main__":
    main()
