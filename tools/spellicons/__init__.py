"""Значки заклинаний для колеса и HUD (textures/gui/spell/<id>.png, 32×32). По модулю на стихию:
fire, water, wind, earth, lightning, light, dark, space, life, mind, summon, fusion (имя = School.id).

Как добавить значок — в модуль своей стихии дописать функцию с именем, равным id заклинания:

    import math

    from iconlib import spell, star, teardrop

    @spell("water", accent="ice")          # стихия; accent — другой цвет символа (лёд), combo — для слияний
    def frost_spear(s):
        s("a").poly([(9.6, 0), (3, -3.3), (-8.6, -1.6), (-9.6, 0.4), (-6, 2.5), (3, 3.1)])   # копьё, цвет акцента
        s("h", bevel=False).line([(-6.5, -0.2), (8.2, 0)], 0.9, caps=False)                    # блик

Слияния: @spell("fusion", combo=("fire", "dark")) — диск медальона из секторов стихий-составляющих.
Готовые старые рисунки: spellicons/classic.py (register("firebolt", "fire", classic.firebolt)).
Весь API (цвета слоёв, фигуры) — в docstring tools/iconlib.py.

Проверка: python tools/gen_spell_icons.py --preview DIR — превью по стихиям (×8) и всех сразу; посмотрите на свои
значки и поправьте нечитаемые. Без --preview — запись в ресурсы. Заклинания из tools/spelldata/ без значка получают
значок-заглушку (значок стихии) и попадают в список предупреждений.
"""
