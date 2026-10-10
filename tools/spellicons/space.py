"""Значки заклинаний стихии «Пространство (гравитация, время)» (space). Как добавить значок — в tools/spellicons/__init__.py."""

import math  # noqa: F401

from iconlib import (almond, arrow, bolt, burst, crescent, figure, heart, leaf, move, orbit, polygon,  # noqa: F401
                     register, rot, spell, spiral, star, sword, tapered, teardrop, wave)
from spellicons import classic  # noqa: F401

# Ранги 1–3: старые рисунки — ладонь с кубиком, рывок, домик со стрелкой возврата.
register("telekinesis", "space", classic.telekinesis, accent="summon")
register("blink", "space", classic.blink)
register("recall", "space", classic.recall, accent="summon")


def _door(s, x, y, rx, ry):
    """Овальная дверь: светлый обод, звёздная тьма внутри."""
    s("m").ellipse(x, y, rx, ry)
    s("d", bevel=False).ellipse(x, y, rx - 1.3, ry - 1.3)
    stars = s("h", bevel=False)
    stars.circle(x - rx * 0.3, y - ry * 0.35, 0.6).circle(x + rx * 0.25, y + ry * 0.1, 0.5).circle(x - rx * 0.1, y + ry * 0.45, 0.45)


@spell("space", accent="crystal")
def door_crossing(s):
    # Две двери-овала, связанные дугой со стрелкой: вошёл в одну — вышел из другой.
    _door(s, -5.0, 2.2, 3.4, 6.4)
    _door(s, 5.0, -2.2, 3.4, 6.4)
    link = s("a", bevel=False)
    link.arc(0, 0.6, 6.6, 196, 300, 1.3)
    a = math.radians(300)
    link.poly(move(rot([(2.2, 0), (-1.4, -2.0), (-1.4, 2.0)], 300 + 90), 6.6 * math.cos(a), 0.6 + 6.6 * math.sin(a)))


@spell("space", accent="water")
def lapse_blue(s):
    # Чёрное ядро в синем ореоле; четыре клина сходятся к центру — притяжение.
    s("a").ring(0, 0, 6.2, 1.6)
    s("m", bevel=False).arc(0, 0, 6.2, 200, 290, 0.8)
    wedges = s("m")
    for k in range(4):
        deg = 45 + 90 * k
        wedges.poly(rot([(10.2, -2.8), (10.2, 2.8), (6.6, 0)], deg))
    s("d").circle(0, 0, 3.6)
    s("h", bevel=False).ring(0, 0, 3.6, 0.7)


@spell("space", accent="blood")
def reversal_red(s):
    # Алая сфера, от которой расходятся кольца и стрелы — отталкивание.
    rays = s("a", bevel=False)
    for k in range(8):
        deg = -90 + 45 * k
        rays.poly(rot([(9.9, 0), (6.8, -1.8), (6.8, 1.8)], deg))
    s("a").ring(0, 0, 5.4, 0.9)
    s("m").circle(0, 0, 3.8)
    s("h", bevel=False).circle(-1.2, -1.3, 1.3)


@spell("space")
def kamui(s):
    # Три рукава спирали (томоэ) вокруг зрачка иного измерения, тонкое алое кольцо снаружи.
    s("blood", bevel=False).ring(0, 0, 8.6, 0.8)
    arms = s("m")
    for k in range(3):
        spiral(arms, 0, 0, 1.6, 8.0, 0.55, 1.7, rot_deg=120 * k)
    s("d").circle(0, 0, 2.4)
    s("h", bevel=False).circle(-0.7, -0.7, 0.7)


@spell("space", accent="time")
def time_stop(s):
    # Циферблат: двенадцать меток, стрелки застыли.
    s("m").ring(0, 0, 8.4, 1.6)
    s("d", bevel=False).circle(0, 0, 7.4)
    marks = s("a", bevel=False)
    for h in range(12):
        a = math.radians(-90 + 30 * h)
        r0 = 5.0 if h % 3 == 0 else 6.0
        marks.line([(r0 * math.cos(a), r0 * math.sin(a)), (6.9 * math.cos(a), 6.9 * math.sin(a))], 1.1 if h % 3 == 0 else 0.8)
    hands = s("h")
    hands.line([(0, 0), (0, -5.6)], 1.2).line([(0, 0), (3.6, 1.6)], 1.4)
    s("gold").circle(0, 0, 1.2)


def _half_plane(n, lo, hi):
    """Полоса {p: lo ≤ p·n ≤ hi} шириной 40 вдоль разреза (для вырезания половин диска)."""
    u = (-n[1], n[0])
    return [(n[0] * lo + u[0] * 20, n[1] * lo + u[1] * 20), (n[0] * lo - u[0] * 20, n[1] * lo - u[1] * 20),
            (n[0] * hi - u[0] * 20, n[1] * hi - u[1] * 20), (n[0] * hi + u[0] * 20, n[1] * hi + u[1] * 20)]


@spell("space")
def reality_slash(s):
    # Диск мира, рассечённый тонкой белой линией: половины разошлись, в щели — пустота с красной и голубой кромкой.
    ln = math.hypot(10.4, 8.0)
    u = (10.4 / ln, -8.0 / ln)
    n = (-u[1], u[0])
    g = 0.9
    top = s("m")
    top.circle(-n[0] * 1.1, -n[1] * 1.1, 7.6)
    top.cut().poly(_half_plane(n, -g, 30))
    bottom = s("m")
    bottom.circle(n[0] * 1.1, n[1] * 1.1, 7.6)
    bottom.cut().poly(_half_plane(n, -30, g))
    ends = [(-u[0] * 9.6, -u[1] * 9.6), (u[0] * 9.6, u[1] * 9.6)]
    s("black", bevel=False).line(ends, 2 * g)
    s("blood", bevel=False).line(move(ends, -n[0] * g, -n[1] * g), 0.7)
    s("lightning", bevel=False).line(move(ends, n[0] * g, n[1] * g), 0.7)
    s("h", bevel=False).line(ends, 0.6)


@spell("space", accent="void")
def planetary_devastation(s):
    # Каменная луна с кратерами, вокруг — спираль обломков, втягиваемых в ядро.
    debris = s("a", bevel=False)
    spiral(debris, 0, 0, 6.4, 10.2, 0.85, 1.2, rot_deg=40)
    rocks = s("stone")
    for x, y, r in ((-8.6, -3.4, 1.2), (7.4, 5.8, 1.0), (4.6, -8.6, 0.9)):
        rocks.circle(x, y, r)
    s("stone").circle(0, 0, 6.0)
    pits = s("stone_d", bevel=False)
    pits.circle(-2.2, -1.6, 1.5).circle(2.4, 1.4, 1.1).circle(-0.6, 3.2, 0.8).circle(2.0, -3.0, 0.7)
    s("black", bevel=False).arc(0, 0, 6.0, 20, 160, 1.6)
