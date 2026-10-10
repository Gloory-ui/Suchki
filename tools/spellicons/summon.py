"""Значки заклинаний стихии «Призыв (духи, призрачное оружие)» (summon). Как добавить значок — в tools/spellicons/__init__.py."""

import math  # noqa: F401

from iconlib import (almond, arrow, bolt, burst, crescent, figure, heart, leaf, move, orbit, polygon,  # noqa: F401
                     register, rot, spell, spiral, star, sword, tapered, teardrop, wave)
from spellicons import classic  # noqa: F401

# Старые рисунки: призрачный клинок, фамильяр, инструмент, скакун; солдаты — тенью.
register("bound_blade", "summon", classic.bound_blade)
register("familiar", "summon", classic.familiar)
register("bound_tool", "summon", classic.bound_tool)
register("spectral_steed", "summon", classic.spectral_steed)
register("raise_dead", "summon", classic.raise_dead, accent="shadow")


@spell("summon", accent="spirit")
def spirit_blades(s):
    # Веер из пяти проецируемых мечей остриями вверх, рукояти сходятся к кругу-источнику.
    s("a", bevel=False).arc(0, 6.0, 4.2, 180, 360, 1.0)
    for ang in (-150, -120, -90, -60, -30):
        a = math.radians(ang)
        cx, cy = 6.0 * math.cos(a) * 0.95, 6.0 + 6.0 * math.sin(a) * 1.05
        blade = [(4.6, 0), (2.6, -1.0), (-2.2, -1.0), (-2.2, 1.0), (2.6, 1.0)]
        s("m").poly(move(rot(blade, ang), cx, cy))
        s("a").poly(move(rot([(-2.2, -2.2), (-1.3, -2.2), (-1.3, 2.2), (-2.2, 2.2)], ang), cx, cy))
        s("h", bevel=False).line(move(rot([(-1.0, 0), (3.4, 0)], ang), cx, cy), 0.5, caps=False)
    s("h").circle(0, 6.0, 1.6)


@spell("summon", accent="poison")
def death_knight(s):
    # Шлем рыцаря-нежити с узкой прорезью, из которой горит могильный огонь; за ним — меч остриём вниз.
    s("steel_d").poly([(0, 10.2), (1.3, 7.6), (1.3, -3.0), (-1.3, -3.0), (-1.3, 7.6)])
    helm = [(-6.6, 4.2), (-6.8, -2.6), (-5.0, -7.0), (-1.6, -9.2), (1.6, -9.2), (5.0, -7.0), (6.8, -2.6), (6.6, 4.2),
            (3.2, 6.2), (-3.2, 6.2)]
    s("steel").poly(helm)
    s("steel_h", bevel=False).line([(-4.6, -5.6), (-2.0, -7.6)], 0.9)
    s("black", bevel=False).rect(-5.0, -2.2, 5.0, -0.6).rect(-0.8, -0.6, 0.8, 4.8)
    s("a", bevel=False).ellipse(-2.8, -1.4, 1.5, 0.6).ellipse(2.8, -1.4, 1.5, 0.6)
    s("steel_d").rect(-8.4, -3.4, -6.4, -1.8).rect(6.4, -3.4, 8.4, -1.8)


@spell("summon", accent="gold")
def treasury_gate(s):
    # Три золотые ряби-врат, из каждой высовывается остриё оружия, нацеленное вперёд-вниз.
    for cx, cy, r, ang in [(-5.0, -4.2, 3.6, 40), (4.6, -5.4, 3.2, 130), (0.0, 3.4, 3.8, 90)]:
        s("a", bevel=False).ring(cx, cy, r, 1.0)
        s("m", bevel=False).ring(cx, cy, r * 0.55, 0.6)
        tip = [(r + 3.6, 0), (r + 1.2, -1.2), (-0.4, -1.0), (-0.4, 1.0), (r + 1.2, 1.2)]
        s("h").poly(move(rot(tip, ang), cx, cy))


@spell("summon", accent="sun")
def endless_blade_graveyard(s):
    # Холм, утыканный мечами, под огромной ржавой шестернёй закатного неба.
    gear = s("a")
    burst(gear, 0, -3.0, 9.4, 7.6, n=12, rot_deg=-90)
    gear.cut().circle(0, -3.0, 4.6)
    s("m", bevel=False).ring(0, -3.0, 6.2, 0.6)
    s("earth_d").poly([(-10.2, 10.2), (-10.0, 4.2), (-4.0, 2.2), (4.0, 2.2), (10.0, 4.6), (10.2, 10.2)])
    for x, y, ang, ln in [(-6.4, 5.0, 100, 9.0), (-2.2, 3.0, 84, 10.4), (2.6, 3.2, 96, 9.6), (6.6, 5.2, 76, 8.6)]:
        blade = [(ln * 0.5, 0), (ln * 0.5 - 1.6, -0.9), (-ln * 0.5, -0.9), (-ln * 0.5, 0.9), (ln * 0.5 - 1.6, 0.9)]
        cx, cy = x - math.cos(math.radians(ang)) * ln * 0.5, y - math.sin(math.radians(ang)) * ln * 0.5
        s("steel").poly(move(rot(blade, ang), cx, cy))
        hx, hy = x - math.cos(math.radians(ang)) * ln, y - math.sin(math.radians(ang)) * ln
        s("steel_d").poly(move(rot([(-0.5, -2.0), (0.5, -2.0), (0.5, 2.0), (-0.5, 2.0)], ang), hx, hy))


@spell("summon", accent="gold")
def thousand_hand_bodhisattva(s):
    # Ореол из раскрытых ладоней вокруг сложенных в молитве рук.
    s("a", bevel=False).ring(0, 0, 8.6, 0.9)
    for k in range(10):
        ang = -90 + 36 * k
        a = math.radians(ang)
        cx, cy = 7.6 * math.cos(a), 7.6 * math.sin(a)
        palm = [(-1.3, 1.4), (-1.4, -0.6), (-0.8, -2.3), (0.8, -2.3), (1.4, -0.6), (1.3, 1.4)]
        s("m").poly(move(rot(palm, ang + 90), cx, cy))
    hands = s("h")
    hands.poly([(0, -6.2), (1.2, -4.8), (2.8, 1.6), (3.4, 5.6), (0.6, 5.6), (0, 1.2)])
    hands.poly([(0, -6.2), (-1.2, -4.8), (-2.8, 1.6), (-3.4, 5.6), (-0.6, 5.6), (0, 1.2)])
    s("a", bevel=False).line([(0, -5.6), (0, 5.4)], 0.5, caps=False)
