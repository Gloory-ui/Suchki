"""Значки заклинаний стихии «Жизнь (исцеление, природа)» (life). Как добавить значок — в tools/spellicons/__init__.py."""

import math  # noqa: F401

from iconlib import (almond, arrow, bolt, burst, crescent, figure, heart, leaf, move, orbit, polygon,  # noqa: F401
                     register, rgb, rot, spell, spiral, star, sword, tapered, teardrop, wave)
from spellicons import classic  # noqa: F401

# Старые рисунки, которые по-прежнему точно передают суть заклинания.
register("healing", "life", classic.healing)
register("heal_other", "life", classic.heal_other)
register("cure", "life", classic.cure)
register("rejuvenation", "life", classic.rejuvenation)
register("healing_circle", "life", classic.healing_circle)
register("undying", "life", classic.undying, accent="gold")


def _flower(s, x, y, r, petal, core="gold"):
    """Цветок из пяти раздельных лепестков с серединкой."""
    p = s(petal)
    for k in range(5):
        a = math.radians(-90 + 72 * k)
        p.circle(x + math.cos(a) * r * 0.66, y + math.sin(a) * r * 0.66, r * 0.4)
    s(core).circle(x, y, r * 0.34)


@spell("life")
def flower_field(s):
    # Розовый цветок на стебле над травой и два белых по бокам.
    grass = s("a")
    for x0, x1, top in ((-9.0, -7.6, 4.2), (-4.6, -5.4, 4.6), (-2.2, -0.8, 5.0), (2.4, 3.4, 4.4), (5.0, 4.4, 5.2),
                        (8.6, 9.2, 4.0)):
        tapered(grass, (x0, 9.4), (x1, top), 1.5, 0.2)
    grass.line([(0.0, 9.4), (0.0, -1.0)], 1.2)
    leaf(grass, (0.0, 5.6), (4.4, 2.6), 2.0)
    leaf(grass, (0.0, 4.0), (-4.0, 1.4), 1.8)
    _flower(s, 0.0, -4.6, 5.6, rgb("#FF9CCB"))
    _flower(s, -6.6, 1.2, 3.0, "h")
    _flower(s, 6.8, 0.6, 2.8, "h")


def _pine(lay, x, base, h, w):
    """Ель: три яруса треугольников от основания base вверх на h, ширина нижнего яруса w."""
    for k in range(3):
        y0 = base - h * k / 3.4
        hw = w * (1.0 - 0.25 * k)
        lay.poly([(x - hw, y0), (x + hw, y0), (x, y0 - h * 0.5)])


@spell("life")
def deep_forest_bloom(s):
    # Дремучий лес: две ели позади, высокая ель впереди, корни расходятся по земле.
    wood = s("wood")
    wood.line([(-5.8, 7.6), (-5.8, 5.0)], 1.4).line([(5.8, 7.6), (5.8, 5.0)], 1.4).line([(0.0, 8.6), (0.0, 5.0)], 1.8)
    wood.line([(-9.0, 8.8), (9.0, 8.8)], 1.2)
    wood.line([(0.0, 8.0), (-3.6, 9.8)], 1.0).line([(0.0, 8.0), (3.6, 9.8)], 1.0)
    back = s("a")
    _pine(back, -5.8, 5.4, 11.0, 4.0)
    _pine(back, 5.8, 5.4, 11.0, 4.0)
    front = s("m")
    _pine(front, 0.0, 6.2, 15.4, 5.0)
    s("h", bevel=False).line([(-0.6, -7.8), (-2.4, -4.6)], 0.8)


@spell("life", accent="gold")
def yggdrasil_bloom(s):
    # Мировое Древо: широкая крона с золотыми плодами, мощный ствол и корни.
    crown = s("life")
    crown.ellipse(0.0, -4.2, 9.6, 5.6)
    crown.circle(-5.6, -1.8, 3.4).circle(5.6, -1.8, 3.4).circle(0.0, -8.0, 3.6)
    s("life_m", bevel=False).ellipse(-1.6, -6.4, 4.4, 1.8)
    trunk = s("wood")
    trunk.poly([(-1.8, -1.0), (1.8, -1.0), (2.4, 6.4), (-2.4, 6.4)])
    trunk.line([(-1.6, 6.0), (-7.4, 9.2)], 1.5).line([(1.6, 6.0), (7.4, 9.2)], 1.5).line([(0.0, 6.0), (0.0, 9.6)], 1.5)
    trunk.line([(-1.0, -1.2), (-4.4, -4.0)], 1.1).line([(1.0, -1.2), (4.4, -4.0)], 1.1)
    fruit = s("a")
    for x, y in ((-6.4, -3.0), (6.2, -2.6), (-2.6, -7.6), (3.0, -6.8), (0.0, -3.6)):
        fruit.circle(x, y, 1.15)
    star(s("h", bevel=False), 0.0, -10.0, 2.2, 0.6)
