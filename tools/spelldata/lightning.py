"""Тексты заклинаний стихии «Молния» (lightning). Формат — в tools/spelldata/__init__.py.
"""

SPELLS = {
    # --- Ранг 1 ---
    "flicked_coin": {
        "rank": 1,
        "ru": "Щелчок Монеты",
        "ru_desc": "Монета, разогнанная током, мгновенно летит на 32 блока: 7 урона молнией первой цели и 4 — второй, "
                   "мокрым на 30% больше, иногда замедляет; при мультикасте — монета из каждого круга в своего врага.",
        "en": "Coin Flick",
        "en_desc": "A coin driven by current flies 32 blocks in an instant: 7 shock damage to the first target and 4 to the "
                   "second, 30% more to wet ones, sometimes slows; multicast flicks a coin from every circle at its own foe.",
        "chant_ru": "",
        "chant_en": "",
        "ingredients": ["gold_nugget", "copper_ingot", "redstone"],
    },

    # --- Ранг 2 ---
    "paralyze": {
        "rank": 2,
        "ru": "Статические Оковы",
        "ru_desc": "Шесть разрядов вонзаются в цель у прицела (до 64 блоков): 4 урона и паралич на 4 с (мокрым — 6 с, "
                   "боссам — 1 с, игрокам — 1,5 с); без цели заклинание срывается.",
        "en": "Static Shackles",
        "en_desc": "Six discharges pierce the target at the crosshair (up to 64 blocks): 4 damage and paralysis for 4 s "
                   "(6 s if wet, 1 s for bosses, 1.5 s for players); with no target the spell fizzles.",
        "chant_ru": "Шесть разрядов, шесть засовов — ни шагу больше!",
        "chant_en": "Six sparks, six bolts — not one more step!",
        "ingredients": ["lightning_rod", "redstone", "string", "copper_ingot"],
    },
    "thunderclap_flash": {
        "rank": 2,
        "ru": "Громовой Росчерк",
        "ru_desc": "Молниеносный рывок на 14 блоков: 9 урона и паралич на 0,5 с всем на пути, после рывка — Сопротивление II; "
                   "при мультикасте — рывки следом, по 10 блоков к следующим врагам.",
        "en": "Thunderclap Stroke",
        "en_desc": "A lightning-fast dash of 14 blocks: 9 damage and 0.5 s paralysis to everything in the way, Resistance II "
                   "after landing; multicast chains follow-up dashes of 10 blocks toward the next foes.",
        "chant_ru": "Один удар — один раскат грома!",
        "chant_en": "One strike, one peal of thunder!",
        "ingredients": ["lightning_rod", "feather", "redstone", "sugar"],
    },

    # --- Ранг 3 ---
    "chain_lightning": {
        "rank": 3,
        "ru": "Цепная Молния",
        "ru_desc": "Разряд в цель у прицела (до 40 блоков): 12 урона и паралич 0,5 с, затем 3 прыжка (до 5 с силой, мокрым "
                   "+2) к врагам в 8 блоках, каждый слабее на 20%.",
        "en": "Chain Lightning",
        "en_desc": "A discharge into the target at the crosshair (up to 40 blocks): 12 damage and 0.5 s paralysis, then 3 hops "
                   "(up to 5 with power, +2 when wet) to foes within 8 blocks, each 20% weaker.",
        "chant_ru": "Ток бежит по нитям мира —|прыгай от сердца к сердцу!",
        "chant_en": "Current runs through the threads of the world —|leap from heart to heart!",
        "ingredients": ["lightning_rod", "copper_ingot", "copper_ingot", "redstone", "prismarine_crystals"],
    },
    "lightning_strike": {
        "rank": 3,
        "ru": "Удар Небесного Грома",
        "ru_desc": "Над прицелом (до 96 блоков) рисуется круг, и молния бьёт в точку: 18 урона в радиусе 2,5 и 9 до 4 блоков, "
                   "паралич 1 с, мокрым +30%, потом разветвления ещё в двух врагов по 7; плавит песок в стекло.",
        "en": "Heaven's Thunderstrike",
        "en_desc": "A circle forms above the crosshair (up to 96 blocks) and lightning strikes the spot: 18 damage within 2.5 "
                   "and 9 up to 4 blocks, 1 s paralysis, +30% to wet foes, then forks into two more foes for 7; fuses sand "
                   "into glass.",
        "chant_ru": "Небеса, что копят гнев за пеленой облаков,|обрушьте копьё грома на землю, что я назову!",
        "chant_en": "Heavens that hoard wrath behind the veil of clouds,|hurl your spear of thunder upon the ground I name!",
        "ingredients": ["lightning_rod", "lightning_rod", "glowstone_dust", "copper_block", "ghast_tear"],
    },

    # --- Ранг 4 ---
    "railgun": {
        "rank": 4,
        "ru": "Рельсотрон",
        "ru_desc": "Пять кругов-рельс разгоняют монету: луч на 128 блоков, 26 урона первому на линии и на 15% меньше каждому "
                   "следующему, отброс и поджог на 2 с; пробивает тоннель в скалах на 48 блоков (кроме обсидиана).",
        "en": "Railgun",
        "en_desc": "Five rail circles accelerate a coin: a 128-block beam, 26 damage to the first in line and 15% less to each "
                   "next one, knockback and 2 s of burning; bores a 48-block tunnel through rock (except obsidian).",
        "chant_ru": "Подброшенная монета падает в ладонь судьбы.|Магнитные рельсы, выстройтесь в ряд!|Лети быстрее грома!",
        "chant_en": "A flipped coin falls into the palm of fate.|Magnetic rails, fall into line!|Fly faster than thunder!",
        "ingredients": ["gold_block", "copper_block", "redstone_block", "lightning_rod", "lightning_rod", "diamond"],
    },

    # --- Ранг 5 ---
    "thunder_dragon": {
        "rank": 5,
        "ru": "Громовой Дракон Цепей",
        "ru_desc": "Дракон из молний гонится за целью (до 64 блоков): 30 урона и паралич 2 с, обвивает жертву и перескакивает "
                   "ещё на 6 врагов в 12 блоках, теряя 15% за прыжок; мокрым +40%.",
        "en": "Chained Thunder Dragon",
        "en_desc": "A dragon of lightning chases the target (up to 64 blocks): 30 damage and 2 s paralysis, coils around the "
                   "victim and leaps to 6 more foes within 12 blocks, losing 15% per leap; +40% to wet foes.",
        "chant_ru": "Дракон, рождённый в разряде грозы,|извивайся меж туч и стали,|перескакивай с жертвы на жертву,|"
                    "пока небо не устанет греметь!",
        "chant_en": "Dragon born in the storm's discharge,|coil between clouds and steel,|leap from prey to prey,|"
                    "until the sky grows tired of roaring!",
        "ingredients": None,
    },

    # --- Ранг 6 ---
    "heavenly_qilin": {
        "rank": 6,
        "ru": "Небесный Цилинь",
        "ru_desc": "Над прицелом (до 128 блоков) 2 с бушует гроза — молнии по 8 урона в ближайшего врага, — затем зверь-молния "
                   "падает с неба: 55 урона в центре и половина на краю радиуса 8, паралич 2 с, подброс и кратер.",
        "en": "Heavenly Qilin",
        "en_desc": "A storm rages above the crosshair (up to 128 blocks) for 2 s, its bolts dealing 8 to the nearest foe, then "
                   "a lightning beast falls from the sky: 55 damage at the centre and half at the edge of radius 8, 2 s "
                   "paralysis, a launch and a crater.",
        "chant_ru": "Гром, рождённый не мной, а небом, —|я лишь укажу тебе путь.|Тучи, соберитесь в стаю,|"
                    "молнии, сплетитесь в гриву.|Спустись, Небесный Зверь!",
        "chant_en": "Thunder born not of me but of heaven —|I merely show you the way.|Clouds, gather into a herd,|"
                    "lightning, braid into a mane.|Descend, Heavenly Beast!",
        "ingredients": None,
    },

    # --- Ранг 7 ---
    "indra_thousand_thunders": {
        "rank": 7,
        "ru": "Тысяча Громов Индры",
        "ru_desc": "Небо над полем радиусом 32 становится грозовым сводом: 6 с ливня молний (по 10 урона и паралич), затем "
                   "копьё Индры — 120 урона в центре, 40% на краю радиуса 12, оплавленный кратер и кольцо из 12 молний.",
        "en": "Indra's Thousand Thunders",
        "en_desc": "The sky over a field of radius 32 becomes a storm vault: 6 s of raining lightning (10 damage and paralysis "
                   "each), then Indra's Spear — 120 damage at the centre, 40% at the edge of radius 12, a fused crater and a "
                   "ring of 12 bolts.",
        "chant_ru": "Небесный владыка, восседающий на облаках,|я поднимаю к тебе свой посох, как громоотвод,|"
                    "тысяча громов — тысяча клятв,|пролейтесь на землю ливнем белого огня,|"
                    "а последним — копьё, что раскалывает мир надвое.|Тысяча Громов Индры!",
        "chant_en": "Lord of heaven enthroned upon the clouds,|I raise my staff to you like a lightning rod,|"
                    "a thousand thunders — a thousand oaths,|pour down upon the earth as a rain of white fire,|"
                    "and last of all — the spear that splits the world in two.|Indra's Thousand Thunders!",
        "ingredients": None,
    },
}
