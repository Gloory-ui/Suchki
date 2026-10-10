"""Значки заклинаний стихии «Вода (и лёд)» (water). Как добавить значок — в tools/spellicons/__init__.py."""

import math  # noqa: F401

from iconlib import (almond, arrow, bolt, burst, crescent, figure, heart, leaf, move, orbit, polygon,  # noqa: F401
                     register, rot, spell, spiral, star, sword, tapered, teardrop, wave)
from spellicons import classic  # noqa: F401

# Ранг 1: старые рисунки — ледяной шип и пузырьки дыхания.
register("ice_shard", "water", classic.ice_shard, accent="ice")
register("deep_breath", "water", classic.deep_breath)


def _lance(s, x0, y0, x1, y1, w, body="a", hi="m"):
    """Ледяное копьё от (x0, y0) к острию (x1, y1): ромбовидное древко с бликом по оси."""
    dx, dy = x1 - x0, y1 - y0
    ln = math.hypot(dx, dy)
    ux, uy = dx / ln, dy / ln
    nx, ny = -uy, ux
    mx, my = x0 + dx * 0.62, y0 + dy * 0.62
    s(body).poly([(x0, y0), (mx + nx * w, my + ny * w), (x1, y1), (mx - nx * w, my - ny * w)])
    s(hi).poly([(x0 + dx * 0.15, y0 + dy * 0.15), (mx + nx * w * 0.45, my + ny * w * 0.45), (x1, y1), (mx, my)])


def _snowflake(lay, x, y, r, w=1.1, branch=True):
    for k in range(6):
        a = math.radians(-90 + 60 * k)
        ca, sa = math.cos(a), math.sin(a)
        lay.line([(x, y), (x + r * ca, y + r * sa)], w)
        if branch:
            bx, by = x + r * 0.58 * ca, y + r * 0.58 * sa
            for side in (-1, 1):
                b = a + side * math.radians(50)
                lay.line([(bx, by), (bx + r * 0.36 * math.cos(b), by + r * 0.36 * math.sin(b))], w * 0.85)


@spell("water", accent="ice")
def frost_beak(s):
    # Три ледяных копья веером снизу-слева в правый верхний угол.
    _lance(s, -8.5, 6.5, 2.0, -9.6, 2.0)
    _lance(s, -7.0, 8.2, 9.2, -3.0, 2.0)
    _lance(s, -9.6, 3.0, -4.0, -9.4, 1.6)
    s("h", bevel=False).circle(-8.2, 6.6, 1.3)


@spell("water", accent="frost")
def frost_nova(s):
    # «Белая луна»: серп за снежинкой.
    crescent(s("moon"), -1.6, 0.0, 9.4, 4.4, -1.6, 8.0)
    flake = s("ice")
    _snowflake(flake, 3.6, 0.6, 5.6, 1.3)
    s("h", bevel=False).circle(3.6, 0.6, 1.3)


@spell("water", accent="ice")
def frost_waltz(s):
    # Наклонная орбита и три копья, летящие по ней друг за другом; в центре — снежинка.
    orbit(s("m"), 0, 0, 9.4, 4.4, 1.3, rot_deg=-24)
    for t in (200, 320, 80):
        a, b = math.radians(t), math.radians(t + 34)
        def pt(u):
            ex, ey = 9.4 * math.cos(u), 4.4 * math.sin(u)
            r = math.radians(-24)
            return ex * math.cos(r) - ey * math.sin(r), ex * math.sin(r) + ey * math.cos(r)
        (x0, y0), (x1, y1) = pt(a), pt(b)
        _lance(s, x0, y0, x1, y1, 2.0)
    _snowflake(s("h", bevel=False), 0, 0, 3.4, 1.0, branch=False)


@spell("water", accent="ice")
def glacier_ridge(s):
    # Ледяная гряда: три зубца, средний выше, с бликами граней.
    s("a").poly([(-10.2, 5.8), (-6.4, -1.6), (-4.0, 1.0), (0.2, -9.6), (3.6, -1.8), (5.8, -4.6), (10.2, 5.8)])
    s("m").poly([(-6.4, -1.6), (-5.6, 5.8), (-8.2, 5.8)])
    s("m").poly([(0.2, -9.6), (1.4, 5.8), (-2.6, 5.8), (-1.2, -1.0)])
    s("m").poly([(5.8, -4.6), (6.8, 5.8), (4.6, 5.8)])
    s("frost").rect(-10.0, 6.0, 10.0, 8.2)
    s("h", bevel=False).line([(0.2, -9.0), (-0.6, -2.0)], 0.9)


@spell("water", accent="crystal")
def cursed_crystal_prison(s):
    # Хрустальная призма-темница, внутри — тёмный силуэт пленника.
    s("a").poly([(0, -10.4), (7.0, -5.8), (7.0, 5.8), (0, 10.4), (-7.0, 5.8), (-7.0, -5.8)])
    s("m").poly([(0, -10.4), (7.0, -5.8), (7.0, 5.8), (0, 10.4), (2.4, 5.0), (2.4, -5.4)])
    figure(s("void_d"), -0.4, 0.4, 0.78)
    s("h", bevel=False).line([(-5.6, -5.0), (-5.6, 4.6)], 0.9).circle(5.0, -5.4, 0.8)


@spell("water")
def abyssal_wyrm_roar(s):
    # Водяной змей: изогнутое тело из воды и голова с раскрытой пастью.
    orbit(s("a"), -1.6, 3.2, 7.6, 5.0, 3.0, rot_deg=0, a0=60, a1=250)
    s("m").poly([(-5.2, -4.6), (0.4, -9.4), (5.4, -8.6), (10.2, -6.2), (4.8, -4.6), (9.6, -2.0), (3.4, -1.2),
                 (-1.8, 0.4)])
    s("d").poly([(4.8, -4.6), (9.0, -2.4), (3.6, -2.6)])
    s("h", bevel=False).circle(2.2, -6.6, 1.1)
    wave(s("ice"), -9.8, 1.0, 9.4, 0.7, 1.5, 1.2)


@spell("water", accent="ice")
def hail_hammer(s):
    # Падающая ледяная глыба (куб), следы падения над ней и трещины земли под ней.
    cx, cy, r = 1.4, -0.4, 6.6
    top = [(cx, cy - r), (cx + r * 0.87, cy - r * 0.5), (cx, cy), (cx - r * 0.87, cy - r * 0.5)]
    left = [(cx - r * 0.87, cy - r * 0.5), (cx, cy), (cx, cy + r), (cx - r * 0.87, cy + r * 0.5)]
    right = [(cx + r * 0.87, cy - r * 0.5), (cx + r * 0.87, cy + r * 0.5), (cx, cy + r), (cx, cy)]
    s("frost").poly(top)
    s("a").poly(left)
    s("ice_d").poly(right)
    s("m", bevel=False).line([(-9.6, -6.0), (-6.8, -9.0)], 1.1).line([(-8.6, -1.4), (-4.4, -6.2)], 1.1)
    s("h", bevel=False).line([(-9.0, 9.2), (-5.4, 7.6), (-6.0, 9.6)], 0.9).line([(9.0, 9.0), (6.4, 7.4), (7.4, 9.8)], 0.9)


@spell("water")
def tidal_leviathan(s):
    # Цунами: огромный загнутый гребень над водой, пена на гребне.
    s("a").poly([(-10.4, 8.2), (-9.4, 1.0), (-6.6, -5.6), (-1.6, -9.6), (4.2, -9.8), (8.6, -6.8), (9.8, -2.6),
                 (7.2, -4.8), (3.6, -5.2), (1.0, -3.0), (0.4, 1.4), (2.8, 5.0), (10.2, 8.2)])
    s("m").poly([(-6.6, -5.6), (-1.6, -9.6), (4.2, -9.8), (8.6, -6.8), (9.8, -2.6), (7.2, -4.8), (3.6, -5.2),
                 (-0.6, -4.8), (-3.6, -2.4)])
    wave(s("ice"), -10.4, 10.4, 8.6, 0.9, 2.5, 1.4)
    s("h", bevel=False).circle(9.2, -3.4, 1.1).circle(6.8, -6.8, 0.9).circle(-2.8, -6.6, 0.8)
