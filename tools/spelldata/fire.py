"""Тексты заклинаний стихии «Огонь» (fire). Формат — в tools/spelldata/__init__.py.
Числа в описаниях — как в коде (magic/spells/FireSpells.java) при силе ×1.
"""

SPELLS = {
    # ---------------------------------------------------------------- Ранг 1 — Начальный
    "firebolt": {
        "rank": 1,
        "ru": "Огненная Стрела",
        "ru_desc": "Быстрая огненная стрела на 64 блока: 6 урона огнём и поджог на 3 с; накопленная почти "
                   "до предела посоха — перегретая — пробивает одну цель насквозь.",
        "en": "Flame Arrow",
        "en_desc": "A swift fire arrow flying 64 blocks: 6 fire damage and burning for 3 s; charged almost to the "
                   "focus limit it overheats and pierces one target.",
        "chant_ru": "",
        "chant_en": "",
        "ingredients": ["blaze_powder", "flint"],
    },
    "flames": {
        "rank": 1,
        "ru": "Поток Пламени",
        "ru_desc": "Пока держите, из посоха бьёт струя огня на 7 блоков: 6 урона в секунду и поджог на 2 с; "
                   "снег и лёд на её конце тают.",
        "en": "Flamestream",
        "en_desc": "While held, a jet of fire bursts 7 blocks from the staff: 6 damage per second and burning "
                   "for 2 s; snow and ice at its tip melt.",
        "chant_ru": "",
        "chant_en": "",
        "ingredients": ["blaze_powder", "coal", "coal"],
    },

    # ---------------------------------------------------------------- Ранг 2 — Средний
    "flash_fire_hand": {
        "rank": 2,
        "ru": "Огненная Длань: Вспышка",
        "ru_desc": "Метит до 4 врагов в конусе на 28 блоков; через 0,6 с каждая метка взрывается: 9 урона огнём "
                   "цели, 4 — всем в 2 блоках и поджог на 4 с.",
        "en": "Flash Fire Hand",
        "en_desc": "Marks up to 4 foes in a 28-block cone; 0.6 s later each mark detonates: 9 fire damage to the "
                   "target, 4 to everyone within 2 blocks, and burning for 4 s.",
        "chant_ru": "Багряный цветок раскрывается на кончиках пальцев — шаг, и весь сад в огне!",
        "chant_en": "A crimson flower opens at my fingertips — one step, and the whole garden burns!",
        "ingredients": ["blaze_powder", "fire_charge", "gunpowder", "red_dye"],
    },

    # ---------------------------------------------------------------- Ранг 3 — Продвинутый
    "fireball": {
        "rank": 3,
        "ru": "Огненный Цветок",
        "ru_desc": "Огненный шар летит на 64 блока и взрывается в радиусе 3,5: от 16 урона огнём в центре до 8 "
                   "на краю, поджог на 4 с и небольшая воронка.",
        "en": "Blossoming Fireball",
        "en_desc": "A fireball flies up to 64 blocks and bursts in a 3.5-block radius: 16 fire damage at the "
                   "centre down to 8 at the edge, burning for 4 s and a small crater.",
        "chant_ru": "Пламя, сожмись в моей ладони в малое солнце,|сорвись и расцвети огненным цветком!",
        "chant_en": "Flame, compress in my palm into a lesser sun,|break loose and bloom into a flower of fire!",
        "ingredients": ["fire_charge", "fire_charge", "magma_cream", "blaze_rod", "gunpowder"],
    },

    # ---------------------------------------------------------------- Ранг 4 — Святой
    "pillar_of_fire": {
        "rank": 4,
        "ru": "Огненный Столп",
        "ru_desc": "Под прицелом (до 96 блоков) вырывается столп пламени высотой 25 блоков: 22 урона огнём, "
                   "подброс и поджог на 6 с; ещё 2 с внутри — по 6 урона в секунду; земля выжжена.",
        "en": "Pillar of Fire",
        "en_desc": "A 25-block pillar of flame erupts at the crosshair (up to 96 blocks): 22 fire damage, a launch "
                   "and burning for 6 s; for 2 more seconds it deals 6 damage per second inside; the ground is scorched.",
        "chant_ru": "Пламя, что спит в сердце земли,|услышь моё имя и поднимись к облакам —"
                    "|стань колонной, что держит небо!",
        "chant_en": "Flame that sleeps in the heart of the earth,|hear my name and rise to the clouds —"
                    "|become the column that holds up the sky!",
        "ingredients": ["blaze_rod", "blaze_rod", "magma_block", "fire_charge", "gold_ingot", "diamond"],
    },

    # ---------------------------------------------------------------- Ранг 5 — Королевский
    "meteor_shower": {
        "rank": 5,
        "ru": "Звездопад Возмездия",
        "ru_desc": "Из небесного круга в 40 блоках над целью (до 128 блоков) за 3,5 с падают 10 метеоров — по 18 "
                   "урона в радиусе 3, — затем великий метеор: 36 урона в радиусе 5 и воронки.",
        "en": "Starfall of Retribution",
        "en_desc": "A sky circle 40 blocks above the target (up to 128 blocks) rains 10 meteors over 3.5 s — 18 "
                   "damage in a 3-block radius each — then a great meteor: 36 damage in a 5-block radius, and craters.",
        "chant_ru": "Звёзды, что дремлют за пределом небосвода,|услышьте зов того, кто отмечен пламенем,"
                    "|сорвитесь с орбит, раскалитесь добела|и падите дождём возмездия!",
        "chant_en": "Stars slumbering beyond the firmament,|hear the call of one marked by flame,"
                    "|tear from your orbits, burn white-hot,|and fall as a rain of retribution!",
        "ingredients": None,
    },
    "sun_wheel": {
        "rank": 5,
        "ru": "Солнечное Колесо",
        "ru_desc": "Огненное колесо радиусом 3 катится на 48 блоков, взбираясь на уступы и прожигая стены: 28 "
                   "урона огнём и поджог на 8 с, в конце — взрыв радиусом 4 на 14 урона.",
        "en": "Sun Wheel",
        "en_desc": "A 3-block fire wheel rolls 48 blocks, climbing ledges and burning through walls: 28 fire damage "
                   "and burning for 8 s, ending in a 4-block explosion for 14 damage.",
        "chant_ru": "Седьмая форма солнечного огня.|Круг без начала, жар без конца,"
                    "|прокатись по земле, что посмела встать на пути,|и оставь за собой лишь пепел — Солнечное Колесо!",
        "chant_en": "Seventh form of the solar flame.|A circle without beginning, heat without end,"
                    "|roll across the land that dared to stand in the way,|and leave nothing but ash — Sun Wheel!",
        "ingredients": None,
    },

    # ---------------------------------------------------------------- Ранг 6 — Императорский
    "heliogenesis": {
        "rank": 6,
        "ru": "Гелиогенезис: Рождение Солнца",
        "ru_desc": "В 22 блоках над целью рождается малое солнце: 2 с жжёт всё в радиусе 14 (3 урона в секунду), "
                   "затем падает — до 55 урона в радиусе 10, огненная грива до 16 блоков на 8 урона, воронка радиусом 8.",
        "en": "Heliogenesis: Birth of a Sun",
        "en_desc": "A small sun is born 22 blocks above the target: for 2 s it burns everything within 14 blocks "
                   "(3 damage per second), then falls — up to 55 damage in a 10-block radius, a flame mane out to "
                   "16 blocks for 8, and a crater of radius 8.",
        "chant_ru": "Свет, рождённый в сердце неба,|жар, что старше самого мира,|сожмись в моих ладонях —"
                    "|я дарую тебе рождение!|Вспыхни, малое солнце, и сожги всё, на что падёт твой взгляд!",
        "chant_en": "Light born in the heart of the sky,|heat older than the world itself,|condense within my palms —"
                    "|I grant you birth!|Blaze, little sun, and burn all that falls beneath your gaze!",
        "ingredients": None,
    },

    # ---------------------------------------------------------------- Ранг 7 — Божественный
    "crimson_explosion": {
        "rank": 7,
        "ru": "Багровая Эксплозия",
        "ru_desc": "Сжигает всю ману (минимум 250): семь кругов ствола к цели, взрыв радиусом 16 — от 160 урона "
                   "в центре до 40 на краю, — ударная волна до 48 блоков и огромная воронка; потом маг 10 с без сил.",
        "en": "Crimson Explosion",
        "en_desc": "Burns all your mana (at least 250): seven barrel circles toward the target, a 16-block blast — "
                   "160 damage at the centre down to 40 at the edge — a shockwave out to 48 blocks and a huge crater; "
                   "then the caster is drained for 10 s.",
        "chant_ru": "Тьма чернее самой ночи, багрянец алее крови,|я взываю к тебе, дремлющий огонь мироздания,"
                    "|пробудись в глубине моего сердца,|сожги расчёт, сожги страх, сожги меня до последней капли,"
                    "|пусть всё сущее содрогнётся перед единственной вспышкой —|ВЗОРВИСЬ!",
        "chant_en": "Darkness blacker than night itself, crimson redder than blood,|I call to you, slumbering fire of "
                    "creation,|awaken in the depths of my heart,|burn reason, burn fear, burn me to the very last drop,"
                    "|let all that exists tremble before a single flash —|BURST!",
        "ingredients": None,
    },
}
