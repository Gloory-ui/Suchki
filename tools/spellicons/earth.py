"""Значки заклинаний стихии «Земля» (earth). Как добавить значок — в tools/spellicons/__init__.py."""

import math  # noqa: F401

from iconlib import (almond, arrow, bolt, burst, crescent, figure, heart, leaf, move, orbit, polygon,  # noqa: F401
                     register, rot, spell, spiral, star, sword, tapered, teardrop, wave)
from spellicons import classic  # noqa: F401

# Ранг 1 и 4: старые рисунки — каменный щит кожи и раскол земли.
register("stoneflesh", "earth", classic.stoneflesh)
register("earthquake", "earth", classic.earthquake)


def _block(lay, x, y, r, deg):
    """Повёрнутый квадратный камешек."""
    lay.poly(move(rot([(-r, -r), (r, -r), (r, r), (-r, r)], deg), x, y))


def _spike(lay, x, base, top, w):
    """Каменное копьё из земли: от основания {@code base} до острия {@code top}."""
    lay.poly([(x - w, base), (x - w * 0.35, base - (base - top) * 0.55), (x, top), (x + w * 0.4, base - (base - top) * 0.6),
              (x + w, base)])


@spell("earth")
def stone_cannon(s):
    # Раскрученное ядро летит в правый верхний угол: шар с нарезными бороздами и пыльный след.
    trail = s("a", bevel=False)
    trail.line([(-8.6, 5.8), (-2.6, -0.2)], 1.6).line([(-8.2, 1.4), (-4.4, -2.4)], 1.1).line([(-4.2, 8.6), (-0.4, 4.8)], 1.1)
    s("stone").circle(2.6, -2.6, 6.4)
    groove = s("stone_d", bevel=False)
    for k in range(3):
        a0 = -60 + 120 * k
        groove.arc(2.6, -2.6, 4.2, a0, a0 + 70, 1.1)
    s("stone_d", bevel=False).circle(2.6, -2.6, 1.3)
    s("h", bevel=False).arc(2.6, -2.6, 5.2, 200, 250, 1.0)


@spell("earth")
def earth_wall(s):
    # Бастион: каменная башня с зубцами и тёмными воротами вырастает из земли, по бокам летят камешки.
    s("earth").poly([(-10.6, 6.4), (10.6, 6.4), (10.6, 10.6), (-10.6, 10.6)])
    s("stone").poly([(-8.0, 7.0), (-8.0, -9.0), (-4.8, -9.0), (-4.8, -6.6), (-1.6, -6.6), (-1.6, -9.0), (1.6, -9.0),
                     (1.6, -6.6), (4.8, -6.6), (4.8, -9.0), (8.0, -9.0), (8.0, 7.0)])
    mortar = s("stone_d", bevel=False)
    for y in (-3.2, 1.6):
        mortar.line([(-8.0, y), (8.0, y)], 0.9, caps=False)
    for x, y0, y1 in [(-4.0, -6.6, -3.2), (4.0, -6.6, -3.2), (-5.6, -3.2, 1.6), (5.6, -3.2, 1.6)]:
        mortar.line([(x, y0), (x, y1)], 0.9, caps=False)
    gate = s("earth_d")
    gate.poly([(-2.6, 7.0), (-2.6, 0.8), (-1.6, -0.8), (0.0, -1.4), (1.6, -0.8), (2.6, 0.8), (2.6, 7.0)])
    s("h", bevel=False).line([(-7.2, -8.2), (-5.6, -8.2)], 0.8, caps=False)
    dust = s("a")
    _block(dust, -9.8, -1.6, 0.9, 25)
    _block(dust, 9.8, -3.4, 1.0, 40)


@spell("earth")
def quagmire(s):
    # Топь: тёмная лужа грязи, расходящиеся круги и пузыри; в центре — спираль водоворота.
    s("earth_d").ellipse(0, 3.0, 10.2, 6.4)
    s("earth", bevel=False).ellipse(0, 3.0, 7.6, 4.8).cut().ellipse(0, 3.0, 6.6, 3.8)
    spiral(s("a", bevel=False), 0, 3.0, 0.6, 4.6, 1.6, 1.2)
    bubbles = s("earth")
    bubbles.circle(-4.6, -4.4, 1.5).circle(3.8, -5.0, 1.2).circle(0.0, -7.6, 0.9)
    s("h", bevel=False).circle(-5.1, -4.9, 0.5).circle(3.4, -5.4, 0.4)


@spell("earth")
def iron_guardian(s):
    # Каменный колосс: квадратная голова на массивных плечах, янтарные глаза, трещины по камню.
    s("stone_d").poly([(-10.4, 10.2), (-9.6, 2.4), (-5.4, 0.6), (5.4, 0.6), (9.6, 2.4), (10.4, 10.2)])
    s("stone").rect(-5.4, -9.0, 5.4, 3.8)
    s("stone_d", bevel=False).rect(-5.4, -4.6, 5.4, -3.4)
    s("sun", bevel=False).rect(-3.8, -3.2, -1.2, -1.6).rect(1.2, -3.2, 3.8, -1.6)
    s("h", bevel=False).circle(-2.5, -2.4, 0.5).circle(2.5, -2.4, 0.5)
    s("stone_d", bevel=False).line([(-2.6, 0.6), (2.6, 0.6)], 1.0, caps=False)
    s("a", bevel=False).line([(4.4, -8.6), (3.2, -6.4), (4.2, -5.0)], 0.9, caps=False) \
        .line([(-8.0, 4.0), (-6.6, 6.6), (-7.6, 8.6)], 0.9, caps=False)


@spell("earth", accent="lava")
def fissure(s):
    # Пасть разлома: две плиты земли расходятся зубчатой трещиной, из неё бьёт свечение.
    edge_l = [(-1.4, -10.0), (0.6, -6.6), (-1.6, -3.4), (0.8, 0.0), (-1.2, 3.4), (1.0, 6.8), (-0.8, 10.2)]
    edge_r = [(x + 2.6, y) for x, y in edge_l]
    s("a").poly(edge_l + list(reversed(edge_r)))
    s("earth").poly([(-10.6, -6.0)] + edge_l + [(-10.6, 7.0)])
    s("earth").poly([(10.6, -6.0)] + edge_r + [(10.6, 7.0)])
    dust = s("stone")
    _block(dust, -6.0, -8.0, 1.0, 20)
    _block(dust, 6.6, -8.6, 1.2, 35)
    _block(dust, 4.6, 9.0, 0.8, 15)


@spell("earth")
def titan_upheaval(s):
    # Три каменных копья встают из земли в кольце преобразования.
    s("a", bevel=False).ring(0, 2.0, 9.4, 1.0)
    s("stone_d").poly([(-10.6, 5.6), (10.6, 5.6), (10.6, 10.6), (-10.6, 10.6)])
    _spike(s("stone_d"), -5.6, 6.0, -3.6, 2.6)
    _spike(s("stone_d"), 5.8, 6.0, -2.4, 2.6)
    _spike(s("stone"), 0.0, 6.4, -9.8, 3.4)
    s("h", bevel=False).line([(-0.6, 4.0), (-0.2, -7.4)], 0.8)
    sparks = s("red", bevel=False)
    sparks.circle(-8.4, -4.4, 0.8).circle(8.6, -6.4, 0.7).circle(-3.0, -8.6, 0.6)


@spell("earth")
def mountain_awakening(s):
    # Великая гора со снежной шапкой поднимается из круга; по склонам — светящиеся трещины.
    s("a", bevel=False).ring(0, -0.4, 9.8, 1.1)
    s("stone").poly([(-10.6, 8.0), (-4.6, -1.4), (-2.6, -0.2), (0.6, -9.4), (5.0, -2.6), (6.6, -3.8), (10.6, 8.0)])
    s("bone").poly([(-1.5, -5.6), (0.6, -9.4), (2.9, -5.8), (1.4, -4.6), (0.4, -5.6), (-0.6, -4.4)])
    s("earth").poly([(-10.6, 6.2), (10.6, 6.2), (10.6, 10.6), (-10.6, 10.6)])
    s("lava", bevel=False).line([(0.4, -4.4), (-0.8, -0.6), (0.8, 2.4), (-0.4, 6.0)], 1.0) \
        .line([(-0.8, -0.6), (-3.6, 2.2)], 0.8).line([(0.8, 2.4), (3.8, 4.4)], 0.8)
