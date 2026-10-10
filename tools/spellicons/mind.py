"""Значки заклинаний стихии «Разум (иллюзии)» (mind). Как добавить значок — в tools/spellicons/__init__.py."""

import math  # noqa: F401

from iconlib import (almond, arrow, bolt, burst, crescent, figure, heart, leaf, move, orbit, polygon,  # noqa: F401
                     register, rot, spell, spiral, star, sword, tapered, teardrop, wave)
from spellicons import classic  # noqa: F401

# Старые рисунки школы иллюзий: глаз мудреца, страх, колыбельная, пустое место, безумие, двойники, истерия.
register("night_eye", "mind", classic.night_eye)
register("fear", "mind", classic.fear)
register("calm", "mind", classic.calm)
register("invisibility", "mind", classic.invisibility)
register("frenzy", "mind", classic.frenzy, accent="blood")
register("mirror_image", "mind", classic.mirror_image)
register("mass_hysteria", "mind", classic.mass_hysteria)


@spell("mind", accent="gold")
def dominate(s):
    # Глас Владыки: золотая корона над всевидящим оком, от ока вниз — звено цепи подчинения.
    crown = [(-7.4, -2.6), (-8.0, -8.6), (-4.0, -5.2), (0, -9.4), (4.0, -5.2), (8.0, -8.6), (7.4, -2.6)]
    s("a").poly(crown)
    gems = s("blood", bevel=False)
    for x, y in [(-4.2, -3.9), (0, -4.2), (4.2, -3.9)]:
        gems.circle(x, y, 0.9)
    almond(s("m"), 9.0, 4.4, 0, 3.8)
    s("blood").circle(0, 3.8, 2.9)
    s("d").ellipse(0, 3.8, 0.9, 2.3)
    s("h", bevel=False).circle(-1.1, 2.8, 0.7)
    s("a", bevel=False).ring(0, 9.4, 1.0, 0.7)


@spell("mind", accent="blood")
def moonlit_nightmare(s):
    # Лунный Кошмар: алая луна на чёрном небе, в ней — око с тремя запятыми.
    s("black").circle(0, 0, 10.2)
    s("a").circle(0, 0, 8.4)
    s("h", bevel=False).arc(0, 0, 7.4, 200, 250, 0.7)
    eye = s("black", bevel=False)
    eye.ring(0, 0, 4.5, 0.7)
    eye.circle(0, 0, 1.9)
    for k in range(3):
        a = math.radians(-90 + 120 * k)
        teardrop(eye, 4.5 * math.cos(a), 4.5 * math.sin(a), 1.25, 2.4, angle=math.degrees(a) + 100)


@spell("mind", accent="crystal")
def perfect_hypnosis(s):
    # Зеркало Цветов: овальное зеркало в золотой оправе, в тёмном стекле — полумесяц и цветок; по стеклу — трещина.
    s("gold").ellipse(0, 0, 7.8, 10.2)
    s("shadow").ellipse(0, 0, 6.0, 8.4)
    moon = s("h")
    moon.circle(1.2, -3.4, 3.0)
    moon.cut().circle(2.6, -4.3, 2.6)
    petals = s("m")
    for k in range(5):
        a = math.radians(-90 + 72 * k)
        petals.circle(-1.2 + 1.8 * math.cos(a), 3.4 + 1.8 * math.sin(a), 1.3)
    s("gold").circle(-1.2, 3.4, 0.9)
    s("a", bevel=False).line([(4.4, -7.0), (2.4, -3.0), (3.8, -0.8), (1.6, 2.6), (3.0, 5.6)], 0.6, caps=False)
