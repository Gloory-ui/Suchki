"""Значки заклинаний стихии «Огонь» (fire). Как добавить значок — в tools/spellicons/__init__.py."""

import math  # noqa: F401

from iconlib import (almond, arrow, bolt, burst, crescent, figure, heart, leaf, move, orbit, polygon,  # noqa: F401
                     register, rot, spell, spiral, star, sword, tapered, teardrop, wave)
from spellicons import classic  # noqa: F401

# Ранги 1, 3, 5: старые рисунки — стрела, струя, шар и звездопад.
register("firebolt", "fire", classic.firebolt)
register("flames", "fire", classic.flames)
register("fireball", "fire", classic.fireball)
register("meteor_shower", "fire", classic.meteor_shower)


@spell("fire")
def flash_fire_hand(s):
    # Багряный цветок: восемь лепестков-языков вокруг раскалённой сердцевины.
    petals = s("red")
    for k in range(8):
        teardrop(petals, 0, 0, 3.0, 10.2, angle=-90 + 45 * k)
    inner = s("a")
    for k in range(8):
        teardrop(inner, 0, 0, 1.9, 7.0, angle=-67.5 + 45 * k)
    s("m").circle(0, 0, 3.6)
    s("h", bevel=False).circle(-0.6, -0.6, 1.7)


@spell("fire")
def pillar_of_fire(s):
    # Столп пламени из круга на земле: широкий у основания, вверху расходится языками.
    s("red").ellipse(0, 7.4, 9.2, 2.6)
    s("a").ring(0, 7.4, 6.8, 1.0)
    col = s("a")
    col.poly([(-4.2, 7.2), (-3.4, -3.0), (-5.6, -6.4), (-2.6, -5.0), (-1.4, -10.2), (0.6, -6.0), (2.8, -9.0),
              (3.0, -4.4), (5.4, -6.8), (3.6, -1.6), (4.2, 7.2)])
    s("m").poly([(-2.2, 7.0), (-1.8, -2.6), (-0.6, -6.8), (0.8, -3.6), (2.2, -5.4), (2.0, 7.0)])
    s("h", bevel=False).line([(0.1, 6.6), (0.1, -2.4)], 1.3)


@spell("fire")
def sun_wheel(s):
    # Огненное колесо: обод, восемь спиц, ступица; снаружи языки пламени, закрученные по ходу вращения.
    tongues = s("red")
    for k in range(6):
        a = -90 + 60 * k
        r = math.radians(a)
        teardrop(tongues, 7.4 * math.cos(r), 7.4 * math.sin(r), 1.6, 3.2, angle=a - 60)
    s("gold").ring(0, 0, 6.2, 2.4)
    spokes = s("m")
    for k in range(8):
        r = math.radians(45 * k + 22.5)
        spokes.line([(0, 0), (5.0 * math.cos(r), 5.0 * math.sin(r))], 1.3)
    s("h").circle(0, 0, 2.0)


@spell("fire")
def heliogenesis(s):
    # Рождение солнца: малое солнце с короной висит над кругом на земле, к кругу стекает свет.
    s("red").ellipse(0, 7.0, 9.4, 2.6)
    circ = s("gold")
    circ.ellipse(0, 7.0, 7.6, 1.8)
    circ.cut().ellipse(0, 7.0, 6.2, 1.1)
    s("m").poly([(-1.0, 0.0), (1.0, 0.0), (0.4, 6.8), (-0.4, 6.8)])
    burst(s("red"), 0, -3.0, 7.6, 4.4, n=12)
    burst(s("a"), 0, -3.0, 6.0, 3.8, n=12, rot_deg=-75)
    s("gold").circle(0, -3.0, 3.8)
    s("m").circle(0, -3.0, 2.8)
    s("h", bevel=False).circle(-0.8, -3.8, 1.2)


@spell("fire")
def crimson_explosion(s):
    # Багровый взрыв: огненный гриб — шапка, столб и вспышка у земли.
    burst(s("red"), 0, 6.6, 8.4, 3.4, n=10, rot_deg=-90)
    s("red").ellipse(0, -4.6, 9.6, 5.0)
    s("a").poly([(-2.4, 7.0), (-1.6, -1.0), (-3.0, -2.0), (3.0, -2.0), (1.6, -1.0), (2.4, 7.0)])
    s("a").ellipse(0, -5.0, 7.4, 3.4)
    s("m").ellipse(0, -5.6, 4.6, 2.0)
    s("m").poly([(-1.0, 6.4), (-0.7, -1.4), (0.7, -1.4), (1.0, 6.4)])
    s("h", bevel=False).ellipse(-1.2, -6.2, 2.0, 0.9)
    s("h", bevel=False).circle(0, 6.6, 1.6)
