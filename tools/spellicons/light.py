"""Значки заклинаний стихии «Свет» (light). Как добавить значок — в tools/spellicons/__init__.py."""

import math  # noqa: F401

from iconlib import (almond, arrow, bolt, burst, crescent, figure, heart, leaf, move, orbit, polygon,  # noqa: F401
                     register, rot, spell, spiral, star, sword, tapered, teardrop, wave)
from spellicons import classic  # noqa: F401

# Старые рисунки: свеча, кара, вспышка, луч, благословение, святилище, суд.
register("candlelight", "light", classic.candlelight)
register("smite", "light", classic.smite)
register("flash", "light", classic.flash)
register("sunbeam", "light", classic.sunbeam)
register("blessing", "light", classic.blessing)
register("sanctuary", "light", classic.sanctuary)
register("judgement", "light", classic.judgement)


@spell("light")
def ward(s):
    # Шестигранный покров: голубая решётка сот вокруг семи золотых ячеек, сердцевина — белый свет.
    cells = [(0, 0)] + [(5.4 * math.cos(math.radians(30 + 60 * k)), 5.4 * math.sin(math.radians(30 + 60 * k)))
                        for k in range(6)]
    lattice = s("ice")
    polygon(lattice, 0, 0, 10.2, 6, rot_deg=0)
    face = s("m")
    for x, y in cells:
        polygon(face, x, y, 2.35, 6, rot_deg=0)
    s("h").circle(0, 0, 1.3)


@spell("light", accent="gold")
def promised_dawnblade(s):
    # Меч рассвета остриём вверх над восходящим солнцем; от клинка — лучи.
    burst(s("a"), 0, 4.0, 10.2, 5.0, n=12, rot_deg=-90)
    s("m").circle(0, 4.0, 5.0)
    s("h").poly([(0, -10.2), (1.7, -7.4), (1.5, 4.0), (-1.5, 4.0), (-1.7, -7.4)])
    s("a").rect(-4.6, 4.0, 4.6, 5.6)
    s("a").rect(-1.0, 5.6, 1.0, 9.0)
    s("h", bevel=False).line([(0, -8.0), (0, 2.6)], 0.7)


@spell("light", accent="moon")
def fallen_down(s):
    # Падение небес: столп сине-белого света бьёт в раскалённую землю, его опоясывают три ореола.
    s("a").poly([(-3.0, -10.2), (3.0, -10.2), (4.4, 7.0), (-4.4, 7.0)])
    s("h").poly([(-1.1, -10.2), (1.1, -10.2), (1.7, 7.0), (-1.7, 7.0)])
    s("gold").ellipse(0, 8.0, 9.6, 2.2)
    s("h", bevel=False).ellipse(0, 7.8, 5.0, 1.0)
    halos = s("gold")
    for y, rx in [(-7.6, 7.8), (-3.0, 6.6)]:
        orbit(halos, 0, y, rx, 1.3, 0.9)
