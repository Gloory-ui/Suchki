"""Тексты заклинаний стихии «Пространство (гравитация, время)» (space). Формат — в tools/spelldata/__init__.py.
Числа в описаниях — как в коде (magic/spells/SpaceSpells.java) при силе ×1.
"""

SPELLS = {
    # ---------------------------------------------------------------- Ранг 1 — Начальный
    "telekinesis": {
        "rank": 1,
        "ru": "Длань Владыки",
        "ru_desc": "Пока держите, невидимая рука тянет к вам предметы и опыт в 3 блоках от взгляда (до 32 блоков), "
                   "а без них хватает мелкое существо (до 30 здоровья) и держит в 3 блоках перед собой; "
                   "отпустите — бросок, 4 урона пустотой при ударе.",
        "en": "Ruler's Hand",
        "en_desc": "While held, an unseen hand drags items and XP within 3 blocks of your gaze (up to 32 blocks) to "
                   "you; with none around it grabs a small creature (up to 30 health) and holds it 3 blocks ahead; "
                   "let go to hurl it for 4 void damage on impact.",
        "chant_ru": "",
        "chant_en": "",
        "ingredients": ["lead", "amethyst_shard", "feather"],
    },

    # ---------------------------------------------------------------- Ранг 2 — Средний
    "blink": {
        "rank": 2,
        "ru": "Скачок Пространства",
        "ru_desc": "Мгновенный перенос к прицелу на 16 блоков (32 при силе ×2) с половиной разгона и 0,5 с без "
                   "урона от падения; каждая копия мультикаста оставляет на месте ухода двойника, отвлекающего "
                   "врагов на 3 с.",
        "en": "Space Skip",
        "en_desc": "Instantly moves you to the crosshair up to 16 blocks away (32 at ×2 power), keeping half your "
                   "momentum, with 0.5 s of fall immunity; each multicast copy leaves a decoy afterimage at the "
                   "departure point that draws foes for 3 s.",
        "chant_ru": "Сложись, пространство, — шаг за горизонт!",
        "chant_en": "Fold, o space — one step past the horizon!",
        "ingredients": ["ender_pearl", "chorus_fruit", "amethyst_shard"],
    },

    # ---------------------------------------------------------------- Ранг 3 — Продвинутый
    "recall": {
        "rank": 3,
        "ru": "Метка Возвращения",
        "ru_desc": "После долгого ритуала переносит вас к кровати или якорю возрождения (без них — к точке "
                   "появления мира); с Shift ставит переносную метку, и в её мире вы вернётесь к ней; каждая "
                   "копия мультикаста забирает с собой игрока в 4 блоках.",
        "en": "Homeward Mark",
        "en_desc": "After a long ritual, carries you to your bed or respawn anchor (or the world spawn); sneak-cast "
                   "to set a portable mark instead, which takes priority in its dimension; each multicast copy "
                   "brings along a player within 4 blocks.",
        "chant_ru": "Метка у моего порога зовёт меня —|расстояние, сложись как бумага: я иду домой.",
        "chant_en": "The mark at my threshold calls to me —|distance, fold like paper: I am going home.",
        "ingredients": ["compass", "ender_eye", "white_bed", "chorus_fruit"],
    },
    "door_crossing": {
        "rank": 3,
        "ru": "Переход Дверей",
        "ru_desc": "Две связанные двери 2×3 — в 2 блоках перед вами и у прицела (до 128 блоков) — на 15 с: всё "
                   "живое, предметы и снаряды проходят насквозь, сохраняя скорость; повтор закрывает их. С Shift — "
                   "«Охотничьи угодья»: дверь под врагом (до 48 блоков) и вторая в 25 блоках над ним на 3 с.",
        "en": "Door Crossing",
        "en_desc": "Two linked 2×3 doors — 2 blocks ahead of you and at the crosshair (up to 128 blocks) — for 15 s: "
                   "creatures, items and projectiles pass through keeping their speed; recast closes them. Sneak-cast "
                   "'Hunting Ground': a door under a foe (up to 48 blocks) and its twin 25 blocks above it for 3 s.",
        "chant_ru": "Порог к порогу, дверь к двери —|расстояние, преклонись предо мной.",
        "chant_en": "Threshold to threshold, door to door —|distance, kneel before me.",
        "ingredients": ["oak_door", "ender_eye", "ender_eye", "obsidian", "amethyst_shard"],
    },

    # ---------------------------------------------------------------- Ранг 4 — Святой
    "lapse_blue": {
        "rank": 4,
        "ru": "Лазурь",
        "ru_desc": "Точка притяжения у прицела (до 96 блоков) на 4 с стягивает врагов в 10 блоках и добычу в 16; "
                   "у самого ядра — 6 урона пустотой в секунду и Замедление IV, в конце схлопывание: 12 урона в "
                   "3 блоках и воронка радиусом 2,5.",
        "en": "Lapse: Azure",
        "en_desc": "A point of attraction at the crosshair (up to 96 blocks) drags foes within 10 blocks and loot "
                   "within 16 for 4 s; at the core 6 void damage per second and Slowness IV, then it collapses for "
                   "12 damage within 3 blocks, gouging a 2.5-block pit.",
        "chant_ru": "Отрицательная бесконечность, сожмись в точку.|Всё, что имеет вес, — ко мне.|Лазурь!",
        "chant_en": "Negative infinity, collapse into a point.|All that has weight — come to me.|Azure!",
        "ingredients": ["heart_of_the_sea", "echo_shard", "ender_eye", "lapis_block"],
    },

    # ---------------------------------------------------------------- Ранг 5 — Королевский
    "reversal_red": {
        "rank": 5,
        "ru": "Обращение: Багрянец",
        "ru_desc": "Красная сфера отталкивания летит на 64 блока: 30 урона пустотой каждому на пути (каждому "
                   "следующему на 20% меньше) и отшвыривает их, прошивает до 24 блоков камня и взрывается — до 20 "
                   "урона в 5 блоках и кратер радиусом 4.",
        "en": "Reversal: Crimson",
        "en_desc": "A red sphere of repulsion flies 64 blocks: 30 void damage to everyone in its path (20% less for "
                   "each next one), hurling them aside, bores through up to 24 blocks of rock and bursts for up to 20 "
                   "damage within 5 blocks, leaving a radius-4 crater.",
        "chant_ru": "Положительная бесконечность, развернись.|Всё, что стоит на пути, — прочь от меня.|"
                    "Расширение без предела, отказ без пощады.|Багрянец!",
        "chant_en": "Positive infinity, unfold.|All that stands in the way — away from me.|"
                    "Expansion without limit, rejection without mercy.|Crimson!",
        "ingredients": None,
    },
    "kamui": {
        "rank": 5,
        "ru": "Вихрь Измерений",
        "ru_desc": "Спираль искажения держит цель у прицела (до 96 блоков) 1,5 с — 26 урона пустотой, снаряды "
                   "рядом исчезают; мобов с запасом здоровья не больше 30% (или до 24 всего) изгоняет навсегда, и их "
                   "добыча падает к вашим ногам, остальных роняет с высоты 8 блоков.",
        "en": "Dimension Swirl",
        "en_desc": "A swirl of warped space holds the target at the crosshair (up to 96 blocks) for 1.5 s — 26 void "
                   "damage, nearby projectiles vanish; mobs at 30% health or less (or with 24 max health or less) "
                   "are banished for good, their loot dropping at your feet; others fall from 8 blocks up.",
        "chant_ru": "Глаз, видящий иной мир, — откройся.|Пространство, свернись спиралью вокруг него.|"
                    "Здесь для тебя нет места.|Исчезни в моём измерении!",
        "chant_en": "Eye that sees another world — open.|Space, curl into a spiral around him.|"
                    "There is no place for you here.|Vanish into my dimension!",
        "ingredients": None,
    },

    # ---------------------------------------------------------------- Ранг 6 — Императорский
    "time_stop": {
        "rank": 6,
        "ru": "Мгновение, Застынь",
        "ru_desc": "Время в радиусе 48 блоков встаёт на 6 с (боссы — на 2 с): мобы, снаряды, предметы и динамит "
                   "замирают, а весь нанесённый им урон обрушивается разом по окончании; чужие игроки вязнут на 3 с; "
                   "каждая копия мультикаста добавляет 1,5 с (не больше 10 с).",
        "en": "Stillness of the Instant",
        "en_desc": "Time halts within 48 blocks for 6 s (bosses 2 s): mobs, projectiles, items and TNT freeze, and "
                   "all damage dealt to them lands at once when time resumes; other players are bogged down for 3 s; "
                   "each multicast copy adds 1.5 s (up to 10 s).",
        "chant_ru": "Стрелки вечных часов, остановитесь,|песок, повисни между колбами,|сердца, замрите между ударами,|"
                    "пусть мгновение растянется в вечность —|и в этой вечности двигаюсь лишь я!",
        "chant_en": "Hands of the eternal clock, be still,|sand, hang between the bulbs,|hearts, freeze between beats,|"
                    "let the instant stretch into eternity —|and in that eternity, I alone move!",
        "ingredients": None,
    },
    "reality_slash": {
        "rank": 6,
        "ru": "Рассечение Сущего",
        "ru_desc": "Разрез пространства длиной 64 блока и высотой 8 (с Shift — горизонтальный): 64 урона пустотой "
                   "сквозь любую броню (боссам 40), а блоки в плоскости разреза исчезают; копии мультикаста режут "
                   "крест-накрест под 45°.",
        "en": "Severance of Reality",
        "en_desc": "A cut through space 64 blocks long and 8 high (horizontal when sneaking): 64 void damage through "
                   "any armor (40 to bosses), and the blocks in its plane vanish; multicast copies slash crosswise "
                   "at 45°.",
        "chant_ru": "Мир — это холст, натянутый на пустоту,|а всякий холст можно разрезать.|"
                    "Броня — нить, щит — нить, плоть — лишь нить;|я провожу линию — и она становится истиной.|"
                    "Рассеки сущее!",
        "chant_en": "The world is a canvas stretched over the void,|and every canvas can be cut.|"
                    "Armor is thread, shield is thread, flesh is but thread;|I draw a line — and it becomes truth.|"
                    "Sever what is!",
        "ingredients": None,
    },

    # ---------------------------------------------------------------- Ранг 7 — Божественный
    "planetary_devastation": {
        "rank": 7,
        "ru": "Опустошение Планеты",
        "ru_desc": "Чёрное ядро взмывает на 24 блока над прицелом (до 128 блоков) и 6 с тянет к себе врагов и "
                   "добычу в 28 блоках, выгрызая из земли кратер радиусом 10; рядом с ядром — 5 урона каждые 0,5 с, "
                   "в конце сжатие на 60 урона в 10 блоках, а каменная луна радиусом 7 висит ещё 30 с.",
        "en": "Planetary Devastation",
        "en_desc": "A black core rises 24 blocks above the crosshair (up to 128 blocks) and for 6 s drags foes and "
                   "loot within 28 blocks into itself, tearing a radius-10 crater from the ground; near the core 5 "
                   "damage every 0.5 s, then a crushing 60 damage within 10 blocks, and a stone moon of radius 7 hangs "
                   "for 30 more seconds.",
        "chant_ru": "Земля, оторвись от земли.|Небо, раскрой свою бездонную пасть.|Всё, что ходит, ползает и летает, —|"
                    "станьте единым камнем.|Пусть родится новая луна —|Опустошение Планеты!",
        "chant_en": "Earth, tear yourself from the earth.|Heaven, open your bottomless maw.|All that walks, crawls and "
                    "flies —|become a single stone.|Let a new moon be born —|Planetary Devastation!",
        "ingredients": None,
    },
}
