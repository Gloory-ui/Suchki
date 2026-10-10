"""Значки заклинаний стихии «Ветер» (wind). Как добавить значок — в tools/spellicons/__init__.py."""

import math  # noqa: F401

from iconlib import (almond, arrow, bolt, burst, crescent, figure, heart, leaf, move, orbit, polygon,  # noqa: F401
                     register, rot, spell, spiral, star, sword, tapered, teardrop, wave)
from spellicons import classic  # noqa: F401

# Ранг 1: старое перо — подъём на ветре.
register("featherfall", "wind", classic.featherfall)


def _gust(lay, x0, x1, y, curl, w):
    """Порыв: линия ветра, загнутая на конце в завиток радиусом {@code curl}."""
    pts = [(x0 + (x1 - x0) * k / 12, y) for k in range(13)]
    for k in range(1, 15):
        a = math.radians(90 - 22 * k)
        pts.append((x1 + curl * math.cos(a), y - curl + curl * math.sin(a)))
    lay.line(pts, w)


@spell("wind")
def gale_palm(s):
    # Раскрытая ладонь толкает воздух: за ней расходятся три дуги сжатого ветра.
    rings = s("h", bevel=False)
    for r, w in ((5.4, 1.6), (8.0, 1.4), (10.2, 1.2)):
        rings.arc(-1.0, 0.0, r, -50, 50, w)
    hand = s("m")
    hand.poly([(-9.0, -3.2), (-4.2, -3.6), (-3.0, -2.0), (-3.0, 4.0), (-4.4, 5.6), (-9.0, 5.0)])
    for y in (-2.6, -0.4, 1.8, 3.8):
        hand.line([(-4.0, y), (0.6 - abs(y - 0.6) * 0.35, y)], 1.7)
    hand.line([(-6.6, -3.4), (-5.4, -6.8)], 1.8)
    s("h", bevel=False).line([(-8.2, -2.0), (-8.2, 3.6)], 0.8, caps=False)


@spell("wind")
def zephyr_sickles(s):
    # Три тонких серпа ветра веером летят вправо, за каждым — бледный след.
    trail = s("m", bevel=False)
    for dy in (-6.4, 0.0, 6.4):
        trail.line([(-9.6, dy * 1.1), (-3.6, dy * 0.95)], 0.8)
    blades = s("h")
    for dx, dy in ((0.0, -6.4), (3.0, 0.0), (0.0, 6.4)):
        blades.poly([(dx - 3.2, dy - 4.2), (dx + 1.2, dy - 3.0), (dx + 3.6, dy), (dx + 1.2, dy + 3.0), (dx - 3.2, dy + 4.2),
                     (dx + 0.6, dy + 1.4), (dx + 1.4, dy), (dx + 0.6, dy - 1.4)])


@spell("wind")
def fly(s):
    # Маг на ветру: два широких крыла-пера за спиной, под ногами — завиток.
    wings = s("m")
    leaf(wings, (-1.4, -2.4), (-10.4, -8.0), 3.2)
    leaf(wings, (-1.4, -0.6), (-10.2, -2.6), 2.4)
    leaf(wings, (1.4, -2.4), (10.4, -8.0), 3.2)
    leaf(wings, (1.4, -0.6), (10.2, -2.6), 2.4)
    figure(s("h"), 0.0, -0.6, 0.78)
    wind = s("h", bevel=False)
    orbit(wind, 0.0, 8.6, 5.4, 1.4, 1.0, a0=200, a1=520)


@spell("wind")
def spiral_sphere(s):
    # Шар сжатого вихря: светлая сфера, по ней — закрученные спиральные рукава.
    s("m").circle(0.0, 0.0, 8.4)
    arms = s("a", bevel=False)
    for k in range(3):
        spiral(arms, 0.0, 0.0, 1.0, 8.0, 0.55, 1.4, rot_deg=120 * k)
    s("h", bevel=False).circle(0.0, 0.0, 1.6).arc(0.0, 0.0, 6.6, 200, 250, 1.0)


@spell("wind")
def howling_cyclone(s):
    # Смерч: воронка из наклонённых витков, широкая вверху и узкая у земли, вокруг — сорванные листья.
    funnel = s("m", bevel=False)
    for k in range(6):
        t = k / 5
        y = -8.0 + 14.4 * t
        rx = 8.8 - 6.8 * t
        orbit(funnel, 1.4 * math.sin(t * 3.0), y, rx, 1.3 - 0.5 * t, 1.5 - 0.3 * t)
    swirl = s("s", bevel=False)
    for k in range(5):
        t = (k + 0.5) / 5
        swirl.line([(-(8.0 - 6.4 * t), -7.4 + 14.4 * t), (8.0 - 6.4 * t, -6.6 + 14.4 * t)], 0.7, caps=False)
    tip = s("m")
    tip.poly([(-1.4, 6.0), (1.4, 6.0), (0.4, 9.6), (-0.4, 9.6)])
    debris = s("h")
    leaf(debris, (-10.2, 1.0), (-7.4, -1.6), 1.1)
    leaf(debris, (7.4, 3.6), (10.0, 1.2), 1.0)
    leaf(debris, (5.6, -10.0), (8.6, -9.6), 0.9)


@spell("wind")
def spiral_shuriken(s):
    # Сюрикэн из ветра: четыре изогнутых лезвия вокруг белой сферы, кольцо вращения.
    s("a", bevel=False).ring(0.0, 0.0, 9.6, 0.9)
    blades = s("m")
    for k in range(4):
        base = [(0.0, -2.2), (2.6, -3.2), (6.0, -6.0), (10.0, -6.2), (6.4, -3.0), (3.0, 0.4), (1.4, 1.0)]
        blades.poly(rot(base, 90 * k))
    s("h").circle(0.0, 0.0, 3.2)
    s("a", bevel=False).ring(0.0, 0.0, 2.0, 0.8)


@spell("wind")
def sky_maelstrom(s):
    # Небесные Жернова: грозовая туча сверху, из неё бьёт воронка к нижнему кругу на земле.
    s("a", bevel=False).ellipse(0.0, 8.0, 8.4, 1.8).cut().ellipse(0.0, 8.0, 6.0, 0.9)
    funnel = s("m")
    funnel.poly([(-7.2, -3.6), (7.2, -3.6), (3.0, 1.6), (1.4, 5.2), (0.6, 8.2), (-0.6, 8.2), (-1.4, 5.2), (-3.0, 1.6)])
    swirl = s("s", bevel=False)
    for y, rx in ((-1.4, 5.0), (2.2, 2.6), (5.4, 1.4)):
        swirl.line([(-rx, y - 0.6), (rx, y + 0.4)], 0.8, caps=False)
    cloud = s("stone")
    for x, y, r in ((-6.0, -6.6, 3.4), (-1.6, -8.0, 3.8), (3.4, -7.4, 3.6), (7.0, -5.8, 2.8), (0.6, -5.0, 3.6)):
        cloud.circle(x, y, r)
    bolt(s("storm"), [(5.6, -4.8), (3.8, -1.6), (5.6, -1.0), (3.6, 2.2)], w_glow=1.3)
    s("h", bevel=False).arc(-1.6, -8.0, 2.8, 200, 280, 0.9)


@spell("wind")
def storm_dragon_breath(s):
    # Голова дракона слева раскрывает пасть, из неё вправо бьёт расширяющийся луч бури с молнией.
    beam = s("m")
    beam.poly([(-2.0, -2.0), (10.4, -5.0), (10.4, 5.0), (-2.0, 2.0)])
    s("h", bevel=False).poly([(-1.0, -0.8), (10.4, -2.0), (10.4, 2.0), (-1.0, 0.8)])
    head = s("steel")
    head.poly([(-10.4, -5.0), (-7.6, -7.6), (-3.6, -7.0), (0.0, -4.6), (0.6, -2.8), (-3.4, -2.2), (-7.0, -1.6), (-10.4, -1.6)])
    head.poly([(-10.4, 1.2), (-6.8, 1.6), (-3.2, 2.6), (0.2, 4.4), (-3.4, 6.6), (-7.8, 6.6), (-10.4, 4.6)])
    head.poly([(-8.4, -6.8), (-10.6, -10.0), (-5.6, -7.4)])
    s("black", bevel=False).circle(-5.0, -4.6, 0.9)
    teeth = s("white", bevel=False)
    for x in (-5.6, -2.8):
        teeth.poly([(x - 0.8, -2.0), (x + 0.6, -2.2), (x - 0.2, -0.8)])
        teeth.poly([(x - 0.8, 2.0), (x + 0.6, 2.2), (x - 0.2, 0.8)])
    bolt(s("storm"), [(1.6, -1.6), (4.4, 1.4), (6.6, -1.4), (9.8, 1.2)], w_glow=1.2)
