"""Тексты заклинаний стихии «Тьма (смерть, кровь)» (dark). Формат — в tools/spelldata/__init__.py.
"""

SPELLS = {
    # --- Ранг 1 ---
    "shadow_bolt": {
        "rank": 1,
        "ru": "Луч Обыденной Смерти",
        "ru_desc": "Мгновенный пронзающий луч тьмы на 40 блоков: 5 урона и Иссушение на 3 с, задевает и вторую цель (3 урона); "
                   "при мультикасте — залп лучей по разным врагам.",
        "en": "Ray of Ordinary Death",
        "en_desc": "An instant piercing ray of darkness up to 40 blocks: 5 damage and Wither for 3 s, also hits a second target "
                   "(3 damage); multicast fires a volley at different foes.",
        "chant_ru": "",
        "chant_en": "",
        "ingredients": ["ink_sac", "amethyst_shard"],
    },

    # --- Ранг 2 ---
    "drain_life": {
        "rank": 2,
        "ru": "Похищение Жизни",
        "ru_desc": "Пока держите, багровые нити (до 20 блоков, до 3 целей) тянут из врагов по 4 здоровья в секунду и лечат вас "
                   "на 60% отнятого; излишек — поглощение до 6; через 3 с нить крадёт у цели полезный эффект.",
        "en": "Drain Life",
        "en_desc": "While held, crimson threads (up to 20 blocks, up to 3 targets) draw 4 health per second from foes and heal "
                   "you for 60% of it; overflow becomes up to 6 absorption; after 3 s a thread steals a positive effect.",
        "chant_ru": "Кровь, вернись к своему истинному хозяину.",
        "chant_en": "Blood, return to your true master.",
        "ingredients": ["fermented_spider_eye", "redstone", "glass_bottle"],
    },
    "curse": {
        "rank": 2,
        "ru": "Проклятая Печать",
        "ru_desc": "Проклинает цель (до 48 блоков) на 15 с: +25% получаемого урона, Слабость и Замедление; если проклятый умрёт, "
                   "печать перескакивает на врага в 8 блоках.",
        "en": "Cursed Seal",
        "en_desc": "Curses a target (up to 48 blocks) for 15 s: +25% damage taken, Weakness and Slowness; if the cursed one "
                   "dies, the seal jumps to a foe within 8 blocks.",
        "chant_ru": "Пусть каждый твой шаг станет тяжелее камня.",
        "chant_en": "Let every step you take weigh more than stone.",
        "ingredients": ["spider_eye", "ink_sac", "soul_sand"],
    },

    # --- Ранг 3 ---
    "shadow_step": {
        "rank": 3,
        "ru": "Теневой Обмен",
        "ru_desc": "Шаг сквозь тень: за спину врага (до 64 блоков) — следующий удар за 2 с наносит ещё 6 урона тьмой — или обмен "
                   "местами со своим призывом (до 128); потом 2 с невидимости.",
        "en": "Shadow Exchange",
        "en_desc": "Step through the shadows: behind a foe (up to 64 blocks) — your next melee hit within 2 s deals 6 more dark "
                   "damage — or swap places with your own summon (up to 128); then 2 s of invisibility.",
        "chant_ru": "Тень к тени, шаг к шагу —|там, где ты стоишь, уже я.",
        "chant_en": "Shadow to shadow, step to step —|where you stand, there I already am.",
        "ingredients": ["ender_pearl", "ink_sac", "phantom_membrane"],
    },
    "darkness": {
        "rank": 3,
        "ru": "Безлунная Мгла",
        "ru_desc": "Облако тьмы радиусом 5 у прицела (до 64 блоков) на 8 с: враги слепнут, иссыхают, теряют цель и не могут "
                   "колдовать или стрелять; вышедшие бродят в растерянности ещё 2 с.",
        "en": "Moonless Haze",
        "en_desc": "A cloud of darkness of radius 5 at the crosshair (up to 64 blocks) for 8 s: foes go blind, wither, lose "
                   "their target and cannot cast or shoot; those who leave wander confused for 2 more s.",
        "chant_ru": "Ночь, спустись раньше срока,|укрой их глаза и укради их чувства.",
        "chant_en": "Night, fall before your hour,|cover their eyes and steal their senses.",
        "ingredients": ["sculk", "ink_sac", "black_dye", "phantom_membrane"],
    },

    # --- Ранг 4 ---
    "soul_harvest": {
        "rank": 4,
        "ru": "Жатва Душ",
        "ru_desc": "Вырывает души врагов в радиусе 12: 18 урона и Иссушение II на 6 с; каждая душа лечит вас на 2 (до 10) "
                   "и даёт поглощение, убитые оставляют в полтора раза больше опыта.",
        "en": "Soul Harvest",
        "en_desc": "Tears out the souls of foes within 12 blocks: 18 damage and Wither II for 6 s; each soul heals you for 2 "
                   "(up to 10) and grants absorption, the slain drop half again as much experience.",
        "chant_ru": "Жнец не спрашивает имени, коса уже занесена над полем.|Души, отделитесь от плоти|и вернитесь ко мне!",
        "chant_en": "The reaper asks no names, the scythe is raised over the field.|Souls, part from your flesh|"
                    "and return to me!",
        "ingredients": ["wither_rose", "soul_soil", "echo_shard"],
    },

    # --- Ранг 5 ---
    "grasp_heart": {
        "rank": 5,
        "ru": "Длань, Сжимающая Сердце",
        "ru_desc": "Сжимает сердце цели (до 48 блоков): моб с 45 здоровья или меньше умирает сразу, иначе 38 урона сквозь "
                   "броню и оцепенение на 2 с; боссам — 30 урона, игрокам — 14 и тошнота.",
        "en": "Heartcrusher's Grip",
        "en_desc": "Crushes the target's heart (up to 48 blocks): a mob with 45 health or less dies at once, otherwise 38 "
                   "damage through armor and a 2 s stupor; bosses take 30, players 14 and nausea.",
        "chant_ru": "Я слышу, как бьётся твоё сердце, смертный,|тук — и мои пальцы ложатся на него,|"
                    "тук — и ты понимаешь, кто держит твою жизнь,|тук… и тишина!",
        "chant_en": "I hear your heart beating, mortal,|thump — and my fingers rest upon it,|"
                    "thump — and you understand who holds your life,|thump... and silence!",
        "ingredients": None,
    },
    "black_coffin": {
        "rank": 5,
        "ru": "Чёрный Гроб",
        "ru_desc": "Гроб из чёрной гравитации 5×5×5 (до 96 блоков) сковывает врагов и через 1,5 с пронзает их копьями тьмы: "
                   "34 урона, Иссушение II и Замедление IV; земля внутри раздавлена.",
        "en": "Black Coffin",
        "en_desc": "A 5×5×5 coffin of black gravity (up to 96 blocks) binds foes and after 1.5 s pierces them with spears of "
                   "darkness: 34 damage, Wither II and Slowness IV; the ground inside is crushed.",
        "chant_ru": "Тёмная печать гордыни, сосуд без дна.|Каждый вздох — оковы, каждый шаг — прах.|"
                    "Четыре стены без дверей встают из земли.|Утони в чёрной тишине без надежды!",
        "chant_en": "Dark seal of pride, a vessel without a bottom.|Every breath a shackle, every step dust.|"
                    "Four doorless walls rise from the earth.|Drown in black silence without hope!",
        "ingredients": None,
    },

    # --- Ранг 6 ---
    "black_moonfang": {
        "rank": 6,
        "ru": "Чёрный Лунный Клык",
        "ru_desc": "Гигантский чёрный полумесяц с алой кромкой (шириной 12) летит сквозь всё на 96 блоков: 48 урона и "
                   "кровотечение на 5 с; за ним остаётся траншея длиной 64 блока.",
        "en": "Black Moonfang",
        "en_desc": "A giant black crescent with a crimson edge (12 wide) flies through everything for 96 blocks: 48 damage "
                   "and bleeding for 5 s; it leaves a trench 64 blocks long.",
        "chant_ru": "Моя душа — клинок, мой клинок — душа.|Тьма, что я сжимал в ладони, — освободись.|"
                    "Пусть луна разрубит ночь надвое.|Чёрное лезвие, алая кромка —|Лунный Клык!",
        "chant_en": "My soul is a blade, my blade a soul.|Darkness I clenched in my palm — be free.|"
                    "Let the moon split the night in two.|Black blade, crimson edge —|Moonfang!",
        "ingredients": None,
    },

    # --- Ранг 7 ---
    "malevolent_shrine": {
        "rank": 7,
        "ru": "Расширение Территории: Святилище Злобы",
        "ru_desc": "Открытая территория радиусом 24 на 8 с: бесконечные невидимые разрезы по всем врагам (около 80 урона и "
                   "кровотечение), в конце — крест-разрез; местность нарезана на куски.",
        "en": "Domain Expansion: Shrine of Malice",
        "en_desc": "An open domain of radius 24 for 8 s: endless invisible slashes cut every foe (about 80 damage and "
                   "bleeding), ending in a giant cross slash; the terrain is diced to pieces.",
        "chant_ru": "Палец за пальцем, проклятие за проклятием.|Под небом нет места, что не было бы моим.|"
                    "Кости — колонны, черепа — черепица,|пусть святилище злобы отворит врата.|"
                    "Резать. Рассекать. Без конца.|Расширение Территории!",
        "chant_en": "Finger by finger, curse by curse.|Beneath the sky there is no place that is not mine.|"
                    "Bones for pillars, skulls for tiles,|let the shrine of malice open its gates.|"
                    "Dismantle. Cleave. Without end.|Domain Expansion!",
        "ingredients": None,
    },
}
