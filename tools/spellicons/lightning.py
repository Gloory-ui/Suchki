"""Значки заклинаний стихии «Молния» (lightning). Как добавить значок — в tools/spellicons/__init__.py."""

import math  # noqa: F401

from iconlib import (almond, arrow, bolt, burst, crescent, figure, heart, leaf, move, orbit, polygon,  # noqa: F401
                     register, rot, spell, spiral, star, sword, tapered, teardrop, wave)
from spellicons import classic  # noqa: F401

# Старый рисунок цепной молнии по-прежнему точно передаёт суть.
register("chain_lightning", "lightning", classic.chain_lightning)


@spell("lightning")
def flicked_coin(s):
    # Золотая монета внизу слева и тонкий зигзаг-луч, срывающийся с неё вверх вправо.
    bolt(s("a", bevel=False), [(-3.0, 3.0), (1.0, 0.4), (0.2, -1.6), (5.0, -4.6), (4.2, -6.4), (9.4, -9.0)], 3.0,
         s("h", bevel=False), 1.2)
    s("gold").circle(-5.2, 5.2, 4.4)
    s("d", bevel=False).ring(-5.2, 5.2, 3.0, 0.7)
    s("h", bevel=False).arc(-5.2, 5.2, 3.6, 200, 260, 0.9)


@spell("lightning")
def paralyze(s):
    # Тёмная фигура, в которую с шести сторон вонзаются светлые стержни-разряды.
    figure(s("storm"), 0.0, -0.6, 1.0)
    rods = s("m", bevel=False)
    tips = s("h", bevel=False)
    for k in range(6):
        a = math.radians(-60 + 60 * k)
        x0, y0 = 10.0 * math.cos(a), 10.0 * math.sin(a)
        x1, y1 = 4.6 * math.cos(a), 4.6 * math.sin(a)
        rods.line([(x0, y0), (x1, y1)], 1.6)
        tips.circle(x1, y1, 1.0)


@spell("lightning")
def thunderclap_flash(s):
    # Рывок: резкий зигзаг молнии по диагонали и полосы скорости позади.
    trail = s("m", bevel=False)
    for x0, y0, ln in ((-9.8, 2.0, 4.6), (-8.6, 6.2, 6.0), (-5.0, 9.0, 4.0)):
        trail.line(rot([(x0, y0), (x0 + ln, y0)], -40, (x0, y0)), 1.0)
    bolt(s("a", bevel=False), [(-5.6, 7.4), (-2.4, 0.6), (-0.4, 3.0), (2.0, -3.6), (4.2, -1.4), (8.6, -8.6)], 3.6,
         s("h", bevel=False), 1.6)
    star(s("h", bevel=False), 8.2, -8.2, 2.8, 0.7, n=4)


@spell("lightning")
def lightning_strike(s):
    # Круг в небе и ломаная молния из него в землю, на земле — сноп искр.
    sky = s("m", bevel=False)
    sky.ellipse(0.0, -7.4, 7.6, 2.4).cut().ellipse(0.0, -7.4, 6.2, 1.3)
    burst(s("m"), 0.0, 7.4, 5.6, 1.4, n=8)
    bolt(s("a", bevel=False), [(0.0, -7.0), (3.6, -3.2), (-2.4, -1.0), (2.6, 3.2), (-0.8, 4.2), (0.0, 7.4)], 3.4,
         s("h", bevel=False), 1.5)
    s("h", bevel=False).circle(0.0, 7.4, 1.6)


@spell("lightning", accent="sun")
def railgun(s):
    # Горизонтальный луч сквозь три кольца-рельса, монета у первого.
    s("a", bevel=False).rect(-9.0, -1.4, 10.4, 1.4)
    s("h", bevel=False).rect(-9.0, -0.5, 10.4, 0.5)
    rails = s("m", bevel=False)
    for x, ry in ((-2.0, 6.8), (3.4, 5.4), (8.0, 3.6)):
        rails.ellipse(x, 0.0, 1.3, ry).cut().ellipse(x, 0.0, 0.5, ry - 0.9)
    s("gold").circle(-7.0, 0.0, 3.0)
    s("d", bevel=False).ring(-7.0, 0.0, 1.9, 0.6)


@spell("lightning", accent="storm")
def thunder_dragon(s):
    # Змей из молний: волнистое тело снизу и голова с раскрытой пастью наверху справа.
    pts = [(-9.6 + 0.85 * k, 6.4 - 0.62 * k + 2.4 * math.sin(k * 0.7)) for k in range(13)]
    s("a", bevel=False).line(pts, 3.4)
    s("h", bevel=False).line(pts, 1.2)
    head = s("m")
    head.poly([(0.4, -0.6), (2.2, -6.2), (8.4, -6.0), (5.2, -3.6), (8.8, -1.0), (3.6, 0.6)])
    s("d", bevel=False).circle(4.0, -4.6, 1.0)
    s("h", bevel=False).line([(3.0, -5.8), (1.8, -9.0)], 0.9).line([(5.0, -6.0), (5.4, -9.0)], 0.9)
    # Разряд, срывающийся с пасти.
    bolt(s("h", bevel=False), [(8.4, -3.0), (9.4, 0.8), (7.4, 2.0), (8.2, 5.6)], 1.0)


@spell("lightning", accent="storm")
def heavenly_qilin(s):
    # Грозовая туча и зверь-молния с гривой ветвящихся разрядов, бьющий в землю.
    cloud = s("d")
    cloud.ellipse(-4.2, -6.6, 4.4, 2.8).ellipse(1.6, -7.8, 5.0, 3.0).ellipse(5.8, -5.8, 3.6, 2.4)
    main = [(0.4, -5.0), (-2.6, -1.2), (2.0, 0.4), (-1.8, 4.6), (1.0, 5.6), (-0.4, 9.0)]
    mane1 = [(-2.6, -1.2), (-6.4, -0.2), (-5.0, 2.4), (-8.8, 4.4)]
    mane2 = [(2.0, 0.4), (6.0, 0.8), (4.6, 3.6), (8.2, 5.2)]
    glow = s("a", bevel=False)
    core = s("h", bevel=False)
    for pts, w in ((main, 3.4), (mane1, 2.0), (mane2, 2.0)):
        glow.line(pts, w)
        core.line(pts, w * 0.4)


@spell("lightning")
def indra_thousand_thunders(s):
    # Грозовой свод из дуг, ливень тонких молний и копьё Индры в центре.
    s("m", bevel=False).arc(0.0, -1.0, 9.8, 210, 330, 1.2)
    rain = s("a", bevel=False)
    core = s("h", bevel=False)
    for x in (-7.4, -4.4, 4.4, 7.4):
        top = -4.8 if abs(x) > 6 else -6.8
        pts = [(x, top), (x + 1.0, top + 3.0), (x - 0.6, top + 4.4), (x + 0.6, top + 8.0)]
        rain.line(pts, 2.0)
        core.line(pts, 0.7)
    spear = s("m")
    spear.poly([(0.0, 10.2), (-2.6, 4.4), (-1.1, 4.4), (-1.1, -7.6), (1.1, -7.6), (1.1, 4.4), (2.6, 4.4)])
    core.line([(0.0, -7.2), (0.0, 7.6)], 0.8)
    star(s("h", bevel=False), 0.0, -8.0, 3.2, 0.8, n=4)
