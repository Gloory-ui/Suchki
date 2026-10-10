"""Значки заклинаний стихии «Тьма (смерть, кровь)» (dark). Как добавить значок — в tools/spellicons/__init__.py."""

import math  # noqa: F401

from iconlib import (almond, arrow, bolt, burst, crescent, figure, heart, leaf, move, orbit, polygon,  # noqa: F401
                     register, rot, spell, spiral, star, sword, tapered, teardrop, wave)
from spellicons import classic  # noqa: F401

# Старые рисунки, которые по-прежнему точно передают суть заклинания.
register("drain_life", "dark", classic.drain_life)
register("curse", "dark", classic.curse)
register("shadow_step", "dark", classic.shadow_step)
register("darkness", "dark", classic.darkness)
register("soul_harvest", "dark", classic.soul_harvest)


@spell("dark")
def shadow_bolt(s):
    # Круг у посоха и белый пронзающий луч из него — «обыденная смерть».
    tapered(s("a"), (-3.0, 3.0), (10.0, -9.2), 2.4, 1.2)
    s("h", bevel=False).line([(-2.6, 2.6), (9.6, -8.8)], 1.2, caps=False)
    s("m").ring(-5.0, 5.0, 4.0, 1.6)
    s("d").circle(-5.0, 5.0, 2.6)
    star(s("h", bevel=False), -5.0, 5.0, 2.4, 0.9, n=3)


@spell("dark", accent="blood")
def grasp_heart(s):
    # Костлявая ладонь сжимает алое сердце: пальцы обхватывают его слева, большой палец — справа.
    heart(s("a"), 0.6, -1.6, 3.0)
    s("h", bevel=False).circle(-1.4, -3.2, 0.9)
    bone = s("bone")
    bone.ellipse(0.0, 7.0, 6.0, 2.6)
    bone.line([(-4.8, 6.0), (-8.6, 1.0), (-7.6, -5.2), (-4.6, -8.2)], 1.6)
    bone.line([(-2.0, 5.6), (-5.6, 1.8), (-5.0, -2.4)], 1.4)
    bone.line([(4.6, 6.0), (8.0, 2.6), (7.4, -1.2), (5.2, -2.6)], 1.6)
    s("blood_d", bevel=False).line([(-0.6, -1.6), (1.2, 0.4), (0.0, 2.2)], 0.7)


@spell("dark")
def black_coffin(s):
    # Чёрный гроб и копья тьмы, пронзающие его.
    lid = [(0, -10.0), (4.4, -6.4), (3.4, 9.6), (-3.4, 9.6), (-4.4, -6.4)]
    s("d").poly(lid)
    s("a", bevel=False).poly(lid).cut().poly([(0, -8.4), (3.0, -5.8), (2.2, 8.2), (-2.2, 8.2), (-3.0, -5.8)])
    spears = s("m")
    for y, side in ((-3.4, 1), (1.6, -1), (6.0, 1)):
        tapered(spears, (10.0 * side, y + 1.2 * side), (2.4 * side, y), 1.1, 0.15)
    s("red", bevel=False).line([(0, -6.0), (0, 6.0)], 0.9).line([(-2.2, -2.0), (2.2, -2.0)], 0.9)


@spell("dark", accent="blood")
def black_moonfang(s):
    # Огромный чёрный полумесяц с алой кромкой.
    edge = s("a")
    crescent(edge, 0.2, 0.0, 9.6, 4.8, -1.2, 8.2)
    body = s("d")
    crescent(body, 0.0, 0.0, 8.4, 4.2, -1.0, 7.8)
    s("h", bevel=False).arc(0.2, 0.0, 9.0, 128, 158, 0.8)
    s("red", bevel=False).arc(0.0, 0.0, 6.4, 120, 235, 0.8)


@spell("dark", accent="blood")
def malevolent_shrine(s):
    # Святилище: костяные колонны, алая крыша, белые разрезы поперёк.
    roof = s("a")
    roof.poly([(-10.0, -5.0), (0, -9.4), (10.0, -5.0), (8.4, -3.2), (-8.4, -3.2)])
    bone = s("bone")
    bone.rect(-6.6, -3.2, -4.4, 8.8).rect(4.4, -3.2, 6.6, 8.8)
    bone.rect(-7.6, 8.0, 7.6, 9.6)
    s("d", bevel=False).rect(-4.4, -1.8, 4.4, -0.6)
    cuts = s("h", bevel=False)
    cuts.line([(-9.0, 6.4), (8.6, -6.2)], 0.8)
    cuts.line([(-8.4, -1.4), (9.2, 7.0)], 0.7)
    cuts.line([(-3.0, 9.6), (4.6, 0.6)], 0.6)
