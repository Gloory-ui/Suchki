"""Классические рисунки — 49 значков прежних заклинаний (до «Магии 2.0»). Функции НЕ зарегистрированы: это
библиотека готовых символов. Чтобы взять рисунок для заклинания стихии, в tools/spellicons/<стихия>.py:

    from iconlib import register
    from spellicons import classic

    register("firebolt", "fire", classic.firebolt)                 # тот же рисунок, медальон огня
    register("frost_spear", "water", classic.ice_shard, accent="ice")

или вызвать внутри своей функции и дорисовать поверх:

    @spell("light")
    def holy_smite(s):
        classic.smite(s)
        s("h", bevel=False).circle(0, -6, 1.2)

ACCENT — какой акцент был у рисунка раньше (огонь/лёд/молния), STYLE — прежняя школа и стихия, к которой рисунок
ближе всего в новой системе (для превью gen_spell_icons.py --demo).
"""

import math

import numpy as np

from iconlib import almond, figure, heart, leaf, move, rot, star, sword, tapered, teardrop

# Разрушение

def firebolt(s):
    # Стрела вверх-вправо: пламя хвостом, древко, белый наконечник с выемкой.
    trail = [(0.5, -1.7), (-3.0, -3.4), (-4.6, -2.0), (-8.4, -4.6), (-7.2, -1.2), (-10.4, 0), (-7.2, 1.2),
             (-8.4, 4.6), (-4.6, 2.0), (-3.0, 3.4), (0.5, 1.7)]
    s("a").poly(rot(trail, -45))
    s("m").poly(rot([(0.5, -0.9), (-3.0, -1.8), (-6.6, -0.4), (-6.6, 0.4), (-3.0, 1.8), (0.5, 0.9)], -45))
    s("h").line(rot([(-3.0, 0), (5.0, 0)], -45), 1.5)
    s("h").poly(rot([(10.4, 0), (3.8, -4.0), (5.4, 0), (3.8, 4.0)], -45))


def flames(s):
    # Струя пламени: узко у руки (внизу слева), расходится веером языков вверх-вправо.
    outer = [(-9.8, 0), (-4, -2.2), (1, -4.4), (3.8, -6.8), (5.2, -4.6), (8.2, -6.6), (7.8, -3.0), (10.2, -1.4),
             (8.2, 0.8), (10.2, 3.0), (7.0, 3.8), (7.6, 6.8), (4.0, 4.8), (1, 4.4), (-4, 2.2)]
    s("a").poly(rot(outer, -45))
    s("m").poly(rot([(-8.2, 0), (-2, -1.8), (3, -3.4), (6.2, -2.4), (7.6, -0.2), (6.2, 2.0), (3, 3.2), (-2, 1.8)], -45))
    s("h").poly(rot([(-7.0, 0), (0, -0.9), (4.4, 0), (0, 0.9)], -45))


def ice_shard(s):
    shard = [(9.6, 0), (3, -3.3), (-8.6, -1.6), (-9.6, 0.4), (-6, 2.5), (3, 3.1)]
    s("a").poly(rot(shard, -45))
    s("m").poly(rot([(9.6, 0), (3, -3.3), (-8.6, -1.6), (-7.6, -0.2), (3, 0)], -45))
    s("h", bevel=False).line(rot([(-6.5, -0.2), (8.2, 0)], -45), 0.9, caps=False)
    small = [(3.4, 5.2), (-0.8, 3.7), (-5.4, 5.5), (-0.8, 7.0)]
    s("a").poly(rot(small, -45))
    s("m").poly(rot([(3.4, 5.2), (-0.8, 3.7), (-5.4, 5.5), (-0.8, 5.4)], -45))


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


def fireball(s):
    # Шар летит вниз-влево, за ним три языка пламени.
    tongues = s("a")
    for (y0, y1, w, ln) in [(-3.0, -5.2, 1.7, 8.6), (0.0, 0.0, 2.3, 10.4), (3.0, 5.2, 1.7, 8.6)]:
        tapered(tongues, *rot([(-2.0, y0), (ln, y1)], -45), w, 0.2)
    ball = rot([(-3.6, 0)], -45)[0]
    s("a").circle(ball[0], ball[1], 5.4)
    s("m").circle(ball[0] - 0.4, ball[1] + 0.4, 4.0)
    s("h").circle(ball[0] - 1.0, ball[1] + 1.0, 2.1)


def meteor_shower(s):
    for (x, y, r, L) in [(-4.4, 5.0, 3.0, 7.0), (4.2, 2.6, 2.4, 6.0), (-1.8, -4.6, 2.0, 5.0)]:
        tail = [(0, -r), (L, -0.3), (L, 0.3), (0, r)]
        s("a").poly(move(rot(tail, -45), x, y)).circle(x, y, r)
        s("m").circle(x - 0.3, y + 0.3, r * 0.68)
        s("h", bevel=False).circle(x - 0.6, y + 0.6, r * 0.34)


# Восстановление

def healing(s):
    heart(s("m"), 0, 1.4, 3.1)
    s("h", bevel=False).circle(-3.3, -0.8, 1.0)
    sp = s("h", bevel=False)
    star(sp, 6.6, -6.4, 2.8, 0.7)
    star(sp, -6.8, -5.8, 2.0, 0.55)
    star(sp, 7.4, 3.2, 1.6, 0.5)


def ward(s):
    shield = [(-6.6, -7.6), (6.6, -7.6), (6.6, -0.6), (5.0, 3.6), (0, 8.6), (-5.0, 3.6), (-6.6, -0.6)]
    s("m").poly(shield)
    inner = [(x * 0.68, (y + 0.4) * 0.68 - 0.4) for x, y in shield]
    s("a").poly(inner)
    s("h").rect(-0.95, -4.6, 0.95, 3.0).rect(-3.4, -1.9, 3.4, -0.1)


def heal_other(s):
    heart(s("m"), -3.0, 2.6, 2.6)
    arrow = s("h")
    arrow.line([(-0.6, -0.6), (3.6, -4.4)], 1.8)
    arrow.poly([(7.0, -7.0), (1.4, -6.0), (6.0, -1.4)])


def cure(s):
    glass = s("m")
    glass.circle(0, 3.0, 5.4).rect(-1.9, -6.4, 1.9, -1.2).rect(-2.9, -8.0, 2.9, -6.2)
    liquid = s("a")
    liquid.circle(0, 3.0, 4.2)
    liquid.cut().rect(-6, -3, 6, 1.4)
    s("h").rect(-0.9, 1.9, 0.9, 6.9).rect(-2.5, 3.5, 2.5, 5.3)
    s("h", bevel=False).arc(0, 3.0, 3.4, 190, 245, 1.0)


def rejuvenation(s):
    for k in range(3):
        a0 = math.radians(-90 + 120 * k)
        a1 = a0 + math.radians(52)
        p0 = (1.8 * math.cos(a0), 1.8 * math.sin(a0))
        p1 = (9.2 * math.cos(a1), 9.2 * math.sin(a1))
        leaf(s("a"), p0, p1, 2.7)
        s("m", bevel=False).line([p0, ((p0[0] + p1[0] * 3) / 4, (p0[1] + p1[1] * 3) / 4)], 0.9, caps=False)
    s("h").circle(0, 0, 1.6)


def healing_circle(s):
    s("a").ring(0, 0, 8.0, 1.8)
    s("m").rect(-1.7, -5.6, 1.7, 5.6).rect(-5.6, -1.7, 5.6, 1.7)
    dots = s("h", bevel=False)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        dots.circle(8.0 * math.cos(a), 8.0 * math.sin(a), 1.3)


def undying(s):
    wings = s("h")
    left = [(-2.6, -1.4), (-6.4, -4.8), (-9.6, -5.6), (-8.6, -3.4), (-10.0, -2.2), (-8.4, -0.6), (-9.2, 1.0),
            (-6.8, 1.4), (-6.8, 3.0), (-3.6, 2.6)]
    wings.poly(left).poly([(-x, y) for x, y in left])
    heart(s("gold"), 0, 1.0, 2.7)


# Свет

def candlelight(s):
    teardrop(s("a"), 0, 3.0, 4.6, 12.0)
    teardrop(s("m"), 0, 3.8, 3.0, 8.2)
    teardrop(s("h"), 0, 4.6, 1.6, 4.4)
    sp = s("h", bevel=False)
    star(sp, -6.4, -5.0, 2.0, 0.55)
    star(sp, 6.6, -2.6, 1.6, 0.5)


def smite(s):
    s("m").poly([(0, -9.8), (2.4, -2.0), (0, 6.2), (-2.4, -2.0)])
    s("a").rect(-4.2, -3.6, 4.2, -2.2)
    s("h", bevel=False).line([(0, -8.0), (0, 4.0)], 0.9, caps=False)
    burst = s("a", bevel=False)
    burst.line([(-6.0, 8.2), (6.0, 8.2)], 1.3)
    burst.line([(-2.6, 7.0), (-5.4, 4.6)], 1.1).line([(2.6, 7.0), (5.4, 4.6)], 1.1)


def flash(s):
    pts = []
    for k in range(16):
        a = math.radians(-90 + 22.5 * k)
        r = (9.6 if k % 4 == 0 else 6.2) if k % 2 == 0 else 1.9
        pts.append((r * math.cos(a), r * math.sin(a)))
    s("a").poly(pts)
    s("h").poly([(x * 0.5, y * 0.5) for x, y in pts]).circle(0, 0, 1.9)


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


def blessing(s):
    person = s("m")
    person.circle(0, -0.8, 3.4).ellipse(0, 9.6, 7.8, 6.2)
    s("a").ellipse(0, -6.6, 6.6, 2.5).cut().ellipse(0, -6.6, 4.6, 1.0)
    rays = s("h", bevel=False)
    rays.line([(-8.4, -8.6), (-7.0, -7.4)], 1.0).line([(8.4, -8.6), (7.0, -7.4)], 1.0).line([(0, -10.4), (0, -9.4)], 1.0)


def sanctuary(s):
    s("m").poly([(-8.4, -2.6), (0, -8.6), (8.4, -2.6)])
    s("h", bevel=False).circle(0, -4.8, 1.1)
    s("a").rect(-7.8, -2.6, 7.8, -1.0)
    cols = s("m")
    for x in (-5.6, -1.9, 1.9, 5.6):
        cols.rect(x - 0.8, -1.0, x + 0.8, 5.4)
    s("a").rect(-8.4, 5.4, 8.4, 7.4)


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

def shadow_bolt(s):
    tr = s("a")
    tapered(tr, (-1.0, 1.0), (-8.6, 6.0), 2.6, 0.4)
    tapered(tr, (0.6, 2.6), (-6.4, 8.6), 1.8, 0.3)
    tapered(tr, (-1.6, -0.6), (-8.4, 1.6), 1.6, 0.3)
    s("m").circle(2.6, -2.6, 5.4)
    s("d").circle(2.6, -2.6, 4.0)
    s("a", bevel=False).arc(2.6, -2.6, 2.2, 200, 330, 1.0)


def drain_life(s):
    beam = s("a", bevel=False)
    pts = [(-8.6 + 11 * t / 20, 7.6 - 9.4 * t / 20 + 1.2 * math.sin(t * 0.9)) for t in range(21)]
    beam.line(pts, 1.6)
    teardrop(s("red"), 3.6, -1.0, 3.6, 8.4)
    s("h", bevel=False).circle(2.4, -1.8, 1.0)


def curse(s):
    drips = s("a")
    for x, ln in ((-3.4, 3.0), (0, 4.4), (3.4, 3.0)):
        drips.line([(x, 3.0), (x, 3.0 + ln)], 1.4)
    almond(s("m"), 8.6, 4.6)
    s("a").circle(0, 0, 3.4)
    s("d").ellipse(0, 0, 0.9, 2.9)
    s("h", bevel=False).circle(-1.4, -1.4, 0.75)


def shadow_step(s):
    trail = s("s", bevel=False)
    for y in (-3.0, 0.4, 3.8):
        trail.line([(-9.6, y), (-6.6, y)], 1.1)
    figure(s("s"), -2.6, 0.4)
    figure(s("m"), 3.0, 0.0)


def raise_dead(s):
    bone = s("bone")
    bone.circle(0, -2.4, 6.0).rect(-3.8, 1.0, 3.8, 6.4)
    holes = s("d", bevel=False)
    holes.circle(-2.5, -1.6, 1.8).circle(2.5, -1.6, 1.8)
    holes.poly([(-1.0, 1.8), (1.0, 1.8), (0, 0.2)])
    for x in (-1.6, 0.4, 2.4):
        holes.rect(x - 0.5, 4.0, x + 0.5, 6.4)
    s("a", bevel=False).circle(-2.5, -1.6, 0.7).circle(2.5, -1.6, 0.7)


def darkness(s):
    rim = s("m")
    rim.circle(-4.2, 0.0, 3.8).circle(0, -2.8, 4.6).circle(4.4, 0.0, 3.6).rect(-4.2, 0.0, 4.4, 3.6)
    body = s("d")
    body.circle(-3.6, 0.8, 3.4).circle(0.6, -1.8, 4.2).circle(4.8, 0.8, 3.2).rect(-3.6, 0.8, 4.8, 4.0)
    for (x, y) in [(-3.6, 7.4), (0.6, 8.6), (4.8, 7.0)]:
        teardrop(s("a"), x, y, 1.2, 3.0)


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

def stoneflesh(s):
    shield = [(-6.8, -7.6), (6.8, -7.6), (6.8, -0.6), (5.2, 3.6), (0, 8.8), (-5.2, 3.6), (-6.8, -0.6)]
    s("stone").poly(shield)
    cracks = s("stone_d", bevel=False)
    cracks.line([(-6.4, -3.0), (6.4, -3.0)], 1.0, caps=False).line([(-6.0, 1.8), (6.0, 1.8)], 1.0, caps=False)
    for x0, y0, y1 in [(-1.8, -7.4, -3.0), (2.8, -3.0, 1.8), (-3.2, -3.0, 1.8), (0.2, 1.8, 6.6)]:
        cracks.line([(x0, y0), (x0, y1)], 1.0, caps=False)


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


def featherfall(s):
    vane = s("m")
    leaf(vane, (-6.6, 7.6), (7.4, -8.4), 3.9)
    cut = vane.cut()
    for t, side in ((0.35, 1), (0.55, -1), (0.7, 1), (0.42, -1)):
        x, y = -6.6 + 14.0 * t, 7.6 - 16.0 * t
        nx, ny = 0.75 * side, 0.66 * side
        cut.line([(x + nx * 1.6, y + ny * 1.6), (x + nx * 4.6 + 1.2, y + ny * 4.6 + 1.2)], 0.7, caps=False)
    s("a", bevel=False).line([(-8.8, 9.8), (6.2, -7.0)], 1.2)


def blink(s):
    s("m").rect(-2.6, -1.7, 3.4, 1.7).poly([(2.6, -6.0), (9.4, 0), (2.6, 6.0)])
    speed = s("a", bevel=False)
    speed.line([(-9.0, -4.0), (-3.6, -4.0)], 1.3).line([(-8.0, 0), (-4.4, 0)], 1.3).line([(-9.0, 4.0), (-3.6, 4.0)], 1.3)


def deep_breath(s):
    b = s("m")
    for x, y, r in [(-2.0, 3.2, 4.2), (4.2, -3.0, 2.8), (-3.2, -6.4, 2.0), (5.4, 5.6, 1.7)]:
        b.ring(x, y, r - 0.7, 1.4)
    hi = s("h", bevel=False)
    for x, y, r in [(-2.0, 3.2, 4.2), (4.2, -3.0, 2.8)]:
        hi.circle(x - r * 0.4, y - r * 0.4, 0.8)


def paralyze(s):
    def link(lay, x, y, rx, ry, ang, w):
        outer = [(x + rx * math.cos(t), y + ry * math.sin(t)) for t in np.linspace(0, 2 * math.pi, 40)]
        inner = [(x + (rx - w) * math.cos(t), y + (ry - w) * math.sin(t)) for t in np.linspace(0, 2 * math.pi, 40)]
        lay.poly(rot(outer, ang, (x, y)))
        lay.cut().poly(rot(inner, ang, (x, y)))
    s("a").poly(rot([(-3.0, -1.0), (3.0, -1.0), (3.0, 1.0), (-3.0, 1.0)], -45))
    link(s("m"), -4.4, 4.4, 4.6, 2.9, -45, 1.6)
    link(s("m"), 4.4, -4.4, 4.6, 2.9, -45, 1.6)


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

def night_eye(s):
    almond(s("m"), 8.2, 4.4, 0, 2.0)
    s("a").circle(0, 2.0, 3.4)
    s("d").circle(0, 2.0, 1.8)
    s("h", bevel=False).circle(-1.2, 0.8, 0.8)
    moon = s("h")
    moon.circle(5.6, -6.6, 2.8)
    moon.cut().circle(6.9, -7.4, 2.3)
    star(s("h", bevel=False), -5.6, -6.8, 1.6, 0.45)


def fear(s):
    s("m").ellipse(0, 0.4, 6.4, 8.4)
    f = s("d", bevel=False)
    f.ellipse(-2.6, -0.6, 1.3, 1.9).ellipse(2.6, -0.6, 1.3, 1.9)
    f.line([(-4.4, -4.0), (-1.4, -5.6)], 1.0, caps=False).line([(4.4, -4.0), (1.4, -5.6)], 1.0, caps=False)
    f.ellipse(0, 4.6, 1.5, 2.2)
    teardrop(s("a"), 7.4, -3.4, 1.3, 3.2)


def calm(s):
    for k in range(5):
        a = math.radians(-90 + 72 * k)
        leaf(s("m"), (1.4 * math.cos(a), 1.4 * math.sin(a)), (9.2 * math.cos(a), 9.2 * math.sin(a)), 3.0)
    s("gold").circle(0, 0, 2.4)


def invisibility(s):
    figure(s("s", bevel=False), 0, 0.2, 1.08)
    sil = s("h", bevel=False)
    sil.outline_only = True
    sil.dashed = True
    figure(sil, 0, 0.2, 1.08)


def frenzy(s):
    sword(s, -45, 18.0)
    sword(s, -135, 18.0)


def mirror_image(s):
    figure(s("a"), 3.8, 0.4, 0.92)
    figure(s("m"), -3.6, 0.0, 0.92)


def mass_hysteria(s):
    pts = []
    for k in range(240):
        th = k / 240 * 2.4 * 2 * math.pi
        r = 0.8 + th * 0.56
        pts.append((r * math.cos(th), r * math.sin(th)))
    s("m", bevel=False).line(pts, 1.6)
    s("a").circle(0, 0, 1.2)


# Призыв

def bound_blade(s):
    s("m").poly([(0, -10.0), (1.9, -7.6), (1.9, 3.0), (-1.9, 3.0), (-1.9, -7.6)])
    s("h", bevel=False).line([(0, -8.4), (0, 1.6)], 0.9, caps=False)
    s("a").rect(-5.2, 3.0, 5.2, 4.6).rect(-0.9, 4.6, 0.9, 7.6)
    s("h").circle(0, 8.6, 1.4)


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


def bound_tool(s):
    s("a").line([(-7.6, 8.6), (3.4, -2.4)], 1.8)
    s("m").arc(-2.2, 2.2, 9.0, -110, 20, 2.4)
    tips = s("m")
    tips.poly(rot([(0, -1.2), (2.4, 0), (0, 1.2)], -110 - 90, (0, 0)))
    s("h", bevel=False).arc(-2.2, 2.2, 9.6, -80, -10, 0.8)


def spirit_blades(s):
    swirl = s("a", bevel=False)
    for k in range(3):
        swirl.arc(0, 0, 6.4, -70 + 120 * k, -20 + 120 * k, 1.0)
    for k in range(3):
        ang = -90 + 120 * k
        s("m").poly(rot([(2.4, 0), (4.4, -1.7), (10.0, 0), (4.4, 1.7)], ang))
        s("h", bevel=False).line(rot([(4.0, 0), (8.4, 0)], ang), 0.8, caps=False)
    s("a").circle(0, 0, 1.9)


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


def recall(s):
    s("m").rect(-4.8, -0.4, 4.8, 6.6)
    s("a").poly([(-6.8, 0.2), (0, -6.0), (6.8, 0.2)])
    s("d", bevel=False).rect(-1.3, 2.4, 1.3, 6.6)
    arrow = s("h", bevel=False)
    arrow.arc(0, 0.6, 9.2, 200, 330, 1.4)
    a = math.radians(330)
    tx, ty = 9.2 * math.cos(a), 0.6 + 9.2 * math.sin(a)
    arrow.poly(move(rot([(2.0, 0), (-1.4, -2.0), (-1.4, 2.0)], 330 + 90), tx, ty))


def iron_guardian(s):
    s("steel").rect(-5.8, -7.4, 5.8, 6.2)
    s("steel_d", bevel=False).rect(-5.8, -3.4, 5.8, -1.8)
    s("red", bevel=False).rect(-3.8, -1.8, -1.6, -0.4).rect(1.6, -1.8, 3.8, -0.4)
    s("steel_h").rect(-1.3, -1.8, 1.3, 5.6)
    s("steel_d", bevel=False).rect(1.3, -1.0, 1.9, 5.6)
    s("steel_d", bevel=False).rect(-4.0, 4.0, -2.0, 4.8).rect(2.4, 4.0, 4.4, 4.8)
    s("a", bevel=False).line([(-5.0, -7.0), (-4.2, -3.0), (-5.2, 1.0), (-4.4, 5.6)], 1.1, caps=False)


ACCENT = {
    "firebolt": "fire",
    "flames": "fire",
    "ice_shard": "ice",
    "chain_lightning": "lightning",
    "frost_nova": "ice",
    "fireball": "fire",
    "meteor_shower": "fire",
}
STYLE = {
    "firebolt": ("destruction", "fire"),
    "flames": ("destruction", "fire"),
    "ice_shard": ("destruction", "water"),
    "chain_lightning": ("destruction", "lightning"),
    "frost_nova": ("destruction", "water"),
    "fireball": ("destruction", "fire"),
    "meteor_shower": ("destruction", "fire"),
    "healing": ("restoration", "life"),
    "ward": ("restoration", "life"),
    "heal_other": ("restoration", "life"),
    "cure": ("restoration", "life"),
    "rejuvenation": ("restoration", "life"),
    "healing_circle": ("restoration", "life"),
    "undying": ("restoration", "life"),
    "candlelight": ("light", "light"),
    "smite": ("light", "light"),
    "flash": ("light", "light"),
    "sunbeam": ("light", "light"),
    "blessing": ("light", "light"),
    "sanctuary": ("light", "light"),
    "judgement": ("light", "light"),
    "shadow_bolt": ("dark", "dark"),
    "drain_life": ("dark", "dark"),
    "curse": ("dark", "dark"),
    "shadow_step": ("dark", "dark"),
    "raise_dead": ("dark", "dark"),
    "darkness": ("dark", "dark"),
    "soul_harvest": ("dark", "dark"),
    "stoneflesh": ("alteration", "earth"),
    "telekinesis": ("alteration", "space"),
    "featherfall": ("alteration", "wind"),
    "blink": ("alteration", "space"),
    "deep_breath": ("alteration", "water"),
    "paralyze": ("alteration", "lightning"),
    "earthquake": ("alteration", "earth"),
    "night_eye": ("illusion", "mind"),
    "fear": ("illusion", "mind"),
    "calm": ("illusion", "mind"),
    "invisibility": ("illusion", "mind"),
    "frenzy": ("illusion", "mind"),
    "mirror_image": ("illusion", "mind"),
    "mass_hysteria": ("illusion", "mind"),
    "bound_blade": ("conjuration", "summon"),
    "familiar": ("conjuration", "summon"),
    "bound_tool": ("conjuration", "summon"),
    "spirit_blades": ("conjuration", "summon"),
    "spectral_steed": ("conjuration", "summon"),
    "recall": ("conjuration", "space"),
    "iron_guardian": ("conjuration", "summon"),
}
