"""Тексты заклинаний стихии «Свет» (light). Формат — в tools/spelldata/__init__.py.
Числа в описаниях — как в коде (magic/spells/LightSpells.java) при силе ×1.
"""

SPELLS = {
    # ---------------------------------------------------------------- Ранг 1 — Начальный
    "candlelight": {
        "rank": 1,
        "ru": "Свеча Странника",
        "ru_desc": "Парящий огонёк 3 минуты следует за вами и освещает путь; нежить в 3 блоках от него получает "
                   "1 урона каждые 2 с. С Shift — неподвижный огонёк на 5 минут там, куда смотрите (до 24 блоков, "
                   "не больше 8 сразу).",
        "en": "Wanderer's Candle",
        "en_desc": "A floating light follows you for 3 minutes and lights the way; undead within 3 blocks of it take "
                   "1 damage every 2 s. With Shift — a fixed light for 5 minutes where you look (up to 24 blocks, "
                   "at most 8 at once).",
        "chant_ru": "",
        "chant_en": "",
        "ingredients": ["torch", "glowstone_dust"],
    },
    "smite": {
        "rank": 1,
        "ru": "Кара",
        "ru_desc": "Сгусток света с лёгким наведением летит на 40 блоков: 5 урона святым светом, нежить получает 12 "
                   "и горит 3 с.",
        "en": "Smite",
        "en_desc": "A lightly homing bolt of light flies 40 blocks: 5 holy damage; undead take 12 and burn for 3 s.",
        "chant_ru": "",
        "chant_en": "",
        "ingredients": ["glowstone_dust", "arrow"],
    },

    # ---------------------------------------------------------------- Ранг 2 — Средний
    "ward": {
        "rank": 2,
        "ru": "Шестигранный Покров",
        "ru_desc": "Пока держите, выгнутый щит из 14 шестигранников спереди гасит 70% урона и сжигает стрелы "
                   "(запас 30); с Shift — купол вокруг вас (запас 20). Разбитый щит не поднять 3 с.",
        "en": "Hexweave Veil",
        "en_desc": "While held, a curved shield of 14 hexagons in front absorbs 70% of damage and burns arrows "
                   "(pool 30); with Shift — a dome around you (pool 20). A shattered veil can't be raised for 3 s.",
        "chant_ru": "Сплетись, шестигранный покров, — ни стрела, ни проклятье не пройдут!",
        "chant_en": "Weave yourself, hexagonal veil — let neither arrow nor curse pass!",
        "ingredients": ["shield", "glass_pane", "gold_ingot"],
    },
    "flash": {
        "rank": 2,
        "ru": "Солнечная Вспышка",
        "ru_desc": "Ослепительная вспышка: враги в радиусе 12, что видят вас, слепнут и теряют цель на 5 с и "
                   "замедлены на 3 с; нежить получает 5 урона.",
        "en": "Solar Flash",
        "en_desc": "A blinding flash: foes within 12 blocks that can see you are blinded and lose their target for "
                   "5 s and slowed for 3 s; undead take 5 damage.",
        "chant_ru": "Солнце, ослепи их глаза!",
        "chant_en": "Sun, blind their eyes!",
        "ingredients": ["glowstone_dust", "glow_ink_sac", "gold_ingot"],
    },

    # ---------------------------------------------------------------- Ранг 3 — Продвинутый
    "blessing": {
        "rank": 3,
        "ru": "Благословение Рассвета",
        "ru_desc": "Вы и союзники в радиусе 8 получаете Силу I и Сопротивление I на 45 с; при накоплении от ×1,2 — "
                   "ещё и Удачу.",
        "en": "Blessing of Dawn",
        "en_desc": "You and allies within 8 blocks gain Strength I and Resistance I for 45 s; charged to ×1.2 or "
                   "more — Luck as well.",
        "chant_ru": "Свет, ляг на их плечи доспехом,|пусть клинки их не знают усталости.",
        "chant_en": "Light, rest upon their shoulders as armour,|let their blades know no weariness.",
        "ingredients": ["golden_apple", "glowstone", "feather"],
    },
    "sunbeam": {
        "rank": 3,
        "ru": "Солнечный Луч",
        "ru_desc": "Пока держите, луч солнца бьёт на 48 блоков: 3 урона каждые 0,5 с (нежить ×2,5 и горит); "
                   "через 2 с луч разгорается шире и бьёт по 4; на его конце плавятся хрупкие блоки.",
        "en": "Sunbeam",
        "en_desc": "While held, a ray of sunlight strikes up to 48 blocks: 3 damage every 0.5 s (undead ×2.5 and "
                   "set ablaze); after 2 s it flares wider and deals 4; fragile blocks melt at its end.",
        "chant_ru": "Солнце, протяни мне свою руку,|пусть каждый луч станет копьём!",
        "chant_en": "Sun, reach out your hand to me,|let every ray become a spear!",
        "ingredients": ["sunflower", "amethyst_shard", "glowstone", "gold_ingot"],
    },

    # ---------------------------------------------------------------- Ранг 4 — Святой
    "sanctuary": {
        "rank": 4,
        "ru": "Святилище",
        "ru_desc": "Священный круг радиусом 6 на 15 с: союзники регенерируют, нежить получает 2 урона каждые 0,5 с "
                   "и горит, враги выталкиваются наружу, чужие снаряды сгорают.",
        "en": "Sanctuary",
        "en_desc": "A holy circle of radius 6 for 15 s: allies regenerate, undead take 2 damage every 0.5 s and "
                   "burn, foes are pushed out, hostile projectiles burn away.",
        "chant_ru": "Здесь кончается тьма.|Здесь ни одно зло не сделает шага.|Свет, обнеси это место стеной!",
        "chant_en": "Here the darkness ends.|Here no evil may take a step.|Light, wall this place around!",
        "ingredients": ["gold_block", "diamond", "glowstone"],
    },

    # ---------------------------------------------------------------- Ранг 5 — Королевский
    "judgement": {
        "rank": 5,
        "ru": "Небесный Суд",
        "ru_desc": "Через 0,75 с столп небесного света радиусом 3,5 бьёт в прицел (до 128 блоков): 28 урона "
                   "(нежить ×1,5) и слепота; затем 30 стрел света по 3 урона в радиусе 8; оплавленная воронка.",
        "en": "Heavenly Judgement",
        "en_desc": "After 0.75 s a pillar of heavenly light of radius 3.5 strikes the target (up to 128 blocks): "
                   "28 damage (undead ×1.5) and blindness; then 30 arrows of light rain within 8 blocks for 3 "
                   "each; leaves a glassy crater.",
        "chant_ru": "Небо видит всё и помнит всё.|Весы склонились, приговор вынесен.|"
                    "Пусть свет падёт копьём на виновного,|и да не останется даже тени!",
        "chant_en": "Heaven sees all and remembers all.|The scales have tipped, the verdict is given.|"
                    "Let light fall as a spear upon the guilty,|and let not even a shadow remain!",
        "ingredients": None,
    },

    # ---------------------------------------------------------------- Ранг 6 — Императорский
    "promised_dawnblade": {
        "rank": 6,
        "ru": "Клинок Обетованного Рассвета",
        "ru_desc": "Золотая волна шириной 6 и высотой 8 проходит 96 блоков за 0,8 с: 48 урона святым светом "
                   "(нежить ×1,5, дальше 64 блоков — ×0,75) и траншея на 64 блока; союзников не задевает.",
        "en": "Blade of the Promised Dawn",
        "en_desc": "A golden wave 6 wide and 8 high sweeps 96 blocks in 0.8 s: 48 holy damage (undead ×1.5, "
                   "×0.75 beyond 64 blocks) and a 64-block trench; allies are spared.",
        "chant_ru": "Свет надежды, поклявшийся хранить этот мир,|звёзды и ветра, отдайте мне своё сияние,|"
                    "меч, выкованный из обещания, — пробудись,|рассеки тьму и ложь до самого горизонта —|"
                    "Клинок Обетованного Рассвета!",
        "chant_en": "Light of hope, sworn to guard this world,|stars and winds, lend me your radiance,|"
                    "sword forged from a promise — awaken,|cleave darkness and lies to the very horizon —|"
                    "Blade of the Promised Dawn!",
        "ingredients": None,
    },

    # ---------------------------------------------------------------- Ранг 7 — Божественный
    "fallen_down": {
        "rank": 7,
        "ru": "Падение Небес",
        "ru_desc": "Пятислойный круг в 60 блоках над прицелом обрушивает столп света радиусом 12 на 4 с: только "
                   "врагам 20 урона каждые 0,5 с (до 160, нежить ×1,5), свои внутри лечатся; в конце кольцо света "
                   "на 40 блоков (6 урона) и шахта глубиной 16.",
        "en": "Heavenfall",
        "en_desc": "A five-layer circle 60 blocks above the target brings down a pillar of light of radius 12 for "
                   "4 s: only foes take 20 damage every 0.5 s (up to 160, undead ×1.5), allies inside are healed; "
                   "it ends in a 40-block ring of light (6 damage) and a shaft 16 deep.",
        "chant_ru": "Небосвод, я отнимаю у тебя опору,|синий огонь за пределом звёзд, спустись ко мне,|"
                    "пусть свет обретёт тяжесть, а тяжесть станет судом,|отличи друга от врага, кольцо за кольцом,|"
                    "пади, небо, на головы недостойных|и не оставь от них даже тени!",
        "chant_en": "Firmament, I take away your pillars,|blue fire beyond the stars, descend to me,|"
                    "let light gain weight, and let weight become judgment,|tell friend from foe, ring after ring,|"
                    "fall, o heaven, upon the unworthy|and leave of them not even a shadow!",
        "ingredients": None,
    },
}
