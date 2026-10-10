"""Тексты заклинаний стихии «Разум (иллюзии)» (mind). Формат — в tools/spelldata/__init__.py.
Числа в описаниях — как в коде (magic/spells/MindSpells.java) при силе ×1.
"""

SPELLS = {
    # ---------------------------------------------------------------- Ранг 1 — Начальный
    "night_eye": {
        "rank": 1,
        "ru": "Взор Мудреца",
        "ru_desc": "Ночное зрение на 4 минуты. Волна обзора на 32 блока (+16 за копию мультикаста, до 96) на 30 с "
                   "подсвечивает существ и проявляет невидимых, каждые 2 с отмечая новых; у цели в прицеле (до 64 "
                   "блоков) 10 с видны здоровье, броня и атака.",
        "en": "Sage's Eye",
        "en_desc": "Night vision for 4 minutes. A 32-block scan wave (+16 per multicast copy, up to 96) outlines "
                   "creatures for 30 s and reveals the invisible, re-marking newcomers every 2 s; the target under the "
                   "crosshair (up to 64 blocks) shows its health, armor and attack for 10 s.",
        "chant_ru": "",
        "chant_en": "",
        "ingredients": ["golden_carrot", "spider_eye"],
    },
    "fear": {
        "rank": 1,
        "ru": "Жажда Убийства",
        "ru_desc": "Волна жажды убийства у прицела (до 32 блоков): мобы в радиусе 4 в ужасе бегут от вас 8 с и не "
                   "нападают; боссы замедлены и ослаблены на 3 с.",
        "en": "Killing Intent",
        "en_desc": "A wave of killing intent at the crosshair (up to 32 blocks): mobs within 4 blocks flee from you in "
                   "terror for 8 s and cannot attack; bosses are slowed and weakened for 3 s.",
        "chant_ru": "",
        "chant_en": "",
        "ingredients": ["bone", "spider_eye"],
    },
    # ---------------------------------------------------------------- Ранг 2 — Средний
    "calm": {
        "rank": 2,
        "ru": "Колыбельная Счастливых Дней",
        "ru_desc": "Розовый туман у прицела (до 40 блоков): мобы в радиусе 5 на 20 с теряют интерес к бою, а слабые "
                   "(до 30 здоровья) засыпают на 8 с — первый удар по спящему добавляет половину урона разумом (до 8) "
                   "и будит его. Хранитель и эндермены не поддаются.",
        "en": "Lullaby of Happier Days",
        "en_desc": "A pink haze at the crosshair (up to 40 blocks): mobs within 5 blocks lose interest in fighting for "
                   "20 s, and weak ones (up to 30 health) fall asleep for 8 s — the first hit on a sleeper deals half "
                   "as much again as psychic damage (up to 8) and wakes it. Wardens and endermen are immune.",
        "chant_ru": "Тише, тише — эта битва тебе лишь приснилась.",
        "chant_en": "Hush, hush — you only dreamed this battle.",
        "ingredients": ["pink_petals", "honey_bottle", "amethyst_shard"],
    },
    "invisibility": {
        "rank": 2,
        "ru": "Пустое Место",
        "ru_desc": "Невидимость на 30 с; мобы в 32 блоках, что гнались за вами, теряют цель. Удар или зарядка "
                   "заклинания 3-го ранга и выше выдаёт вас на 2 с. Каждая копия мультикаста прячет союзника в 4 блоках.",
        "en": "Empty Space",
        "en_desc": "Invisibility for 30 s; mobs within 32 blocks that were chasing you lose track of you. Attacking or "
                   "charging a rank 3+ spell reveals you for 2 s. Each multicast copy also hides an ally within 4 blocks.",
        "chant_ru": "Глаза смотрят, но не видят — я лишь пустое место.",
        "chant_en": "Eyes look but do not see — I am only empty space.",
        "ingredients": ["fermented_spider_eye", "glass_pane", "amethyst_shard"],
    },
    # ---------------------------------------------------------------- Ранг 3 — Продвинутый
    "frenzy": {
        "rank": 3,
        "ru": "Безумие",
        "ru_desc": "Мобы в радиусе 5 у прицела (до 48 блоков) 15 с дерутся друг с другом и бьют на 20% сильнее; "
                   "боссы 3 с бредут вслепую в тошноте.",
        "en": "Frenzy",
        "en_desc": "Mobs within 5 blocks of the crosshair (up to 48 blocks) fight each other for 15 s and deal 20% more "
                   "damage; bosses stagger around in nausea for 3 s.",
        "chant_ru": "Враг твой стоит рядом с тобой —|убей его, пока он не убил тебя!",
        "chant_en": "Your enemy stands beside you —|kill him before he kills you!",
        "ingredients": ["blaze_powder", "fermented_spider_eye", "ender_pearl", "red_dye"],
    },
    # ---------------------------------------------------------------- Ранг 4 — Святой
    "dominate": {
        "rank": 4,
        "ru": "Глас Владыки Душ",
        "ru_desc": "Моб под прицелом (до 48 блоков) и до 2 рядом с ним становятся вашими слугами на 60 с (90 при полной "
                   "силе): бьют ваших врагов на 20% сильнее и следуют за вами; слуг не больше 3 (с 40 уровня — 5). "
                   "Мобы, у которых больше 60 + 2×уровень здоровья, получают 10 урона разуму и 6 с блуждают; боссы — "
                   "замедление IV и Слабость II на 3 с. Ваш удар по слуге снимает чары.",
        "en": "Voice of the Soul Sovereign",
        "en_desc": "The mob under the crosshair (up to 48 blocks) and up to 2 next to it become your servants for 60 s "
                   "(90 at full power): they strike your enemies 20% harder and follow you; at most 3 servants (5 from "
                   "level 40). Mobs with more than 60 + 2×level health take 10 psychic damage and wander for 6 s; "
                   "bosses get Slowness IV and Weakness II for 3 s. Hitting a servant yourself breaks the charm.",
        "chant_ru": "Твой разум — свеча на ветру моей воли;|отныне мой приказ — единственное биение твоего сердца.|"
                    "Преклони колени — и сражайся за меня!",
        "chant_en": "Your mind is a candle in the wind of my will;|from now on my command is your only heartbeat.|"
                    "Kneel — and fight for me!",
        "ingredients": ["golden_helmet", "lead", "ender_eye", "amethyst_shard", "diamond"],
    },
    "mirror_image": {
        "rank": 4,
        "ru": "Зеркальная Луна",
        "ru_desc": "Два двойника с вашим обликом на 15 с (по 20 здоровья): враги в 16 блоках атакуют их, а вы 3 с "
                   "невидимы. Разбитый двойник разлетается осколками — слепота 2 с и 4 урона разуму в радиусе 3. "
                   "Каждая копия мультикаста — ещё один двойник.",
        "en": "Mirror Moon",
        "en_desc": "Two decoys with your appearance for 15 s (20 health each): enemies within 16 blocks attack them, "
                   "and you turn invisible for 3 s. A broken decoy bursts into shards — 2 s of blindness and 4 psychic "
                   "damage within 3 blocks. Each multicast copy adds one more decoy.",
        "chant_ru": "Луна в воде — не луна.|То, что ты видишь, — лишь моё отражение.|Разбей его, если сможешь!",
        "chant_en": "The moon in the water is not the moon.|What you see is only my reflection.|Shatter it, if you can!",
        "ingredients": ["glass", "amethyst_shard", "ender_pearl", "phantom_membrane", "gold_ingot"],
    },
    # ---------------------------------------------------------------- Ранг 5 — Королевский
    "mass_hysteria": {
        "rank": 5,
        "ru": "Массовая Истерия",
        "ru_desc": "Волна ужаса расходится на 16 блоков (+6 за копию): 12 урона разуму, затем бегство и Слабость на "
                   "12 с; около трети врагов вместо бегства сходит с ума и бьёт своих. Боссы — Слабость II и "
                   "замедление II на 6 с.",
        "en": "Mass Hysteria",
        "en_desc": "A wave of terror spreads 16 blocks (+6 per copy): 12 psychic damage, then fleeing and Weakness for "
                   "12 s; about a third of the enemies go mad instead and attack their own. Bosses get Weakness II and "
                   "Slowness II for 6 s.",
        "chant_ru": "Страх, что живёт в каждом сердце,|я открываю тебе дверь.|Пусть они услышат шаги за спиной —|"
                    "и бегут, бегут, бегут!",
        "chant_en": "Fear that lives in every heart,|I open the door for you.|Let them hear footsteps behind them —|"
                    "and run, run, run!",
        "ingredients": None,
    },
    # ---------------------------------------------------------------- Ранг 6 — Императорский
    "moonlit_nightmare": {
        "rank": 6,
        "ru": "Лунный Кошмар",
        "ru_desc": "Цель в прицеле (до 96 блоков) заперта в кошмаре под алой луной: 6 с оцепенения, 40 урона разуму "
                   "(по 4 каждые 0,6 с), затем бегство и Слабость II на 10 с. До 4 врагов в 6 блоках от неё — 3 с "
                   "оцепенения и 15 урона. Боссы цепенеют лишь на 1 с.",
        "en": "Moonlit Nightmare",
        "en_desc": "The target under the crosshair (up to 96 blocks) is locked in a nightmare beneath a crimson moon: "
                   "6 s of paralysis, 40 psychic damage (4 every 0.6 s), then fleeing and Weakness II for 10 s. Up to 4 "
                   "enemies within 6 blocks of it are paralyzed for 3 s and take 15 damage. Bosses freeze for only 1 s.",
        "chant_ru": "Луна окрасилась кровью.|В этом мире время подчиняется мне.|Три дня станут одним мгновением,|"
                    "одно мгновение — вечностью боли.|Смотри мне в глаза!",
        "chant_en": "The moon is stained with blood.|In this world, time obeys me.|Three days become a single instant,|"
                    "a single instant an eternity of pain.|Look into my eyes!",
        "ingredients": None,
    },
    # ---------------------------------------------------------------- Ранг 7 — Божественный
    "perfect_hypnosis": {
        "rank": 7,
        "ru": "Совершенный Гипноз: Зеркало Цветов",
        "ru_desc": "Зеркальный купол на 40 блоков: враги внутри 10 с сражаются друг с другом (на 50% сильнее) и с "
                   "шестью возрождающимися двойниками, получая 8 урона разуму в секунду; в финале зеркало трескается — "
                   "30 урона и замедление III на 3 с.",
        "en": "Perfect Hypnosis: Mirror of Flowers",
        "en_desc": "A mirrored dome 40 blocks wide: enemies inside fight each other (50% harder) and six respawning "
                   "decoys of you for 10 s, taking 8 psychic damage per second; at the end the mirror cracks — 30 "
                   "damage and Slowness III for 3 s.",
        "chant_ru": "Цветок в зеркале, луна в воде —|с той минуты, как вы увидели меня, вы уже спите.|"
                    "Тот, кого вы зовёте врагом, — ваш брат,|тот, кого вы зовёте мной, — лишь отражение,|"
                    "а когда зеркало треснет, проснётся только боль.|Совершенный Гипноз!",
        "chant_en": "A flower in the mirror, the moon in the water —|from the moment you saw me, you have been asleep.|"
                    "The one you call your enemy is your brother,|the one you call me is only a reflection,|"
                    "and when the mirror cracks, only pain will wake.|Perfect Hypnosis!",
        "ingredients": None,
    },
}
