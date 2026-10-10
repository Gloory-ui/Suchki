"""Тексты заклинаний стихии «Вода (и лёд)» (water). Формат — в tools/spelldata/__init__.py.
Числа в описаниях — как в коде (magic/spells/WaterSpells.java) при силе ×1.
"""

SPELLS = {
    # ---------------------------------------------------------------- Ранг 1 — Начальный
    "ice_shard": {
        "rank": 1,
        "ru": "Ледяной Шип",
        "ru_desc": "Ледяной шип на 48 блоков: 6 урона холодом, пробивает одну цель, Замедление II на 3 с; "
                   "водная гладь на его пути схватывается льдом 3×3 на 10 с.",
        "en": "Frost Spike",
        "en_desc": "An ice spike flying 48 blocks: 6 frost damage, pierces one target, Slowness II for 3 s; "
                   "water in its path freezes into a 3×3 crossing for 10 s.",
        "chant_ru": "",
        "chant_en": "",
        "ingredients": ["ice", "snowball", "snowball"],
    },
    "deep_breath": {
        "rank": 1,
        "ru": "Дыхание Глубин",
        "ru_desc": "На 3 минуты — дыхание под водой, грация дельфина и сила проводника (при силе ×1,2 и ночное "
                   "зрение); гасит огонь; копии мультикаста дарят то же союзникам в 6 блоках.",
        "en": "Breath of the Deep",
        "en_desc": "For 3 minutes: water breathing, dolphin's grace and conduit power (plus night vision at ×1.2 "
                   "power); puts out fire; multicast copies bless allies within 6 blocks.",
        "chant_ru": "",
        "chant_en": "",
        "ingredients": ["pufferfish", "kelp"],
    },

    # ---------------------------------------------------------------- Ранг 2 — Средний
    "frost_beak": {
        "rank": 2,
        "ru": "Ледяной Клюв",
        "ru_desc": "За спиной вырастают 3 ледяных копья и по очереди летят в цель (до 48 блоков): по 7 урона "
                   "холодом и Замедление на 3 с, а если все три попали в одну цель — она лопается ещё на 4 урона.",
        "en": "Frost Beak Volley",
        "en_desc": "Three ice spears grow behind you and fly at the target one by one (up to 48 blocks): 7 frost "
                   "damage and Slowness for 3 s each; if all three strike one foe it shatters for 4 more.",
        "chant_ru": "Клюв зимы, пронзи тёплую плоть!",
        "chant_en": "Beak of winter, pierce the warm flesh!",
        "ingredients": ["packed_ice", "prismarine_shard", "feather"],
    },

    # ---------------------------------------------------------------- Ранг 3 — Продвинутый
    "frost_nova": {
        "rank": 3,
        "ru": "Белая Луна",
        "ru_desc": "Круг льда радиусом 6 вокруг вас: 9 урона холодом, Замедление III на 4 с, ноги врагов "
                   "вмерзают в лёд на 3 с, вода в 8 блоках замерзает на 10 с.",
        "en": "White Moon Nova",
        "en_desc": "A ring of ice with radius 6 around you: 9 frost damage, Slowness III for 4 s, foes' feet "
                   "freeze in place for 3 s, water within 8 blocks freezes for 10 s.",
        "chant_ru": "Луна, что спит в снегу, — открой глаза.|Первый танец: всё вокруг станет белым!",
        "chant_en": "Moon that sleeps in the snow — open your eyes.|First dance: let everything turn white!",
        "ingredients": ["blue_ice", "powder_snow_bucket", "diamond"],
    },
    "frost_waltz": {
        "rank": 3,
        "ru": "Вальс Ледяных Копий",
        "ru_desc": "Шесть ледяных копий 20 с кружат вокруг вас: раз в полсекунды одно бьёт ближайшего врага "
                   "в 14 блоках (7 урона холодом), копьё разбивает вражеский снаряд, а остаток в конце "
                   "разлетается осколками (3 урона в радиусе 3).",
        "en": "Waltz of Ice Lances",
        "en_desc": "Six ice lances circle you for 20 s: every half second one strikes the nearest foe within "
                   "14 blocks (7 frost damage), a lance shatters an enemy projectile, and the rest burst into "
                   "shards at the end (3 damage within 3 blocks).",
        "chant_ru": "Шесть ледяных копий, закружитесь в танце вокруг меня,|"
                    "и каждого, кто войдёт в круг, пронзите зимой!",
        "chant_en": "Six lances of ice, whirl in a dance around me,|and pierce with winter all who enter the ring!",
        "ingredients": ["blue_ice", "amethyst_shard", "diamond", "packed_ice"],
    },

    # ---------------------------------------------------------------- Ранг 4 — Святой
    "glacier_ridge": {
        "rank": 4,
        "ru": "Ледниковая Гряда",
        "ru_desc": "Вдоль линии на 40 блоков вырастает ледник шириной 5 и высотой до 6 (тает через 10 с): "
                   "враги в 3 блоках от него получают 14 урона холодом и вмерзают в лёд на 4 с.",
        "en": "Glacier Ridge",
        "en_desc": "A glacier 5 wide and up to 6 high surges along a 40-block line (melts after 10 s): foes "
                   "within 3 blocks of it take 14 frost damage and are frozen in place for 4 s.",
        "chant_ru": "Половина меня — вечный холод.|Пусть северный ледник встанет стеной|"
                    "и запрёт всё живое во льду!",
        "chant_en": "Half of me is endless cold.|Let a northern glacier rise like a wall|"
                    "and lock all living things in ice!",
        "ingredients": ["blue_ice", "blue_ice", "prismarine_crystals", "diamond"],
    },

    # ---------------------------------------------------------------- Ранг 5 — Королевский
    "cursed_crystal_prison": {
        "rank": 5,
        "ru": "Склеп Проклятого Хрусталя",
        "ru_desc": "Враги в радиусе 6 у цели (до 96 блоков) получают 20 урона холодом и вмерзают в хрустальные "
                   "темницы на 8 с (4 урона в секунду); темница лопается по сроку или от удара в 10+ урона — "
                   "18 урона в радиусе 2,5 и соседние следом.",
        "en": "Crypt of Cursed Crystal",
        "en_desc": "Foes within 6 blocks of the target (up to 96 blocks away) take 20 frost damage and are sealed "
                   "in crystal prisons for 8 s (4 damage per second); a prison bursts when time runs out or it "
                   "takes a 10+ damage hit — 18 damage within 2.5 blocks, chaining to its neighbours.",
        "chant_ru": "Холод, что старше звёзд, приди из бездны забвения,|облеки моих врагов в хрусталь безмолвия,|"
                    "пусть их сердца станут гранями проклятого камня,|"
                    "а ты, темница, рассыпься, когда я разожму ладонь!",
        "chant_en": "Cold older than the stars, come from the abyss of oblivion,|clothe my foes in crystal of "
                    "silence,|let their hearts become facets of cursed stone,|and shatter, prison, when I open "
                    "my hand!",
        "ingredients": None,
    },
    "abyssal_wyrm_roar": {
        "rank": 5,
        "ru": "Рёв Владыки Пучины",
        "ru_desc": "Водяной дракон проносится на 56 блоков полосой шириной 4: 22 урона водой (вдвое больше "
                   "огненным тварям и эндерменам), уносит врагов потоком и замедляет на 4 с, гасит огонь, "
                   "лаву превращает в обсидиан; с разрушением сносит листву, стекло и траву.",
        "en": "Roar of the Abyssal Wyrm",
        "en_desc": "A water dragon tears 56 blocks ahead in a 4-wide lane: 22 water damage (double to fiery "
                   "creatures and endermen), sweeps foes away and slows them for 4 s, puts out fire and turns "
                   "lava to obsidian; with block damage on it rips through leaves, glass and grass.",
        "chant_ru": "Владыка глубин, спящий во тьме океана,|пробудись и разверзни свою пасть,|"
                    "пусть рёв твой станет потопом,|поглоти тех, кто стоит предо мной!",
        "chant_en": "Lord of the depths, sleeping in the ocean's dark,|awaken and open wide your jaws,|"
                    "let your roar become a deluge,|devour those who stand before me!",
        "ingredients": None,
    },

    # ---------------------------------------------------------------- Ранг 6 — Императорский
    "hail_hammer": {
        "rank": 6,
        "ru": "Градовый Молот Ста Цветов",
        "ru_desc": "Из небесного круга 2,5 с идёт снег (враги в радиусе 10 замедлены, на них расцветают ледяные "
                   "цветы по 2 урона), затем падает ледяная глыба: 40 урона холодом в центре и 16 на краю "
                   "радиуса 8, +2 за каждый цветок, ледяные темницы на 4 с и воронка радиуса 5 в ледяных шипах.",
        "en": "Hundred-Flower Hail Hammer",
        "en_desc": "Snow falls from a sky circle for 2.5 s (foes within 10 blocks are slowed and sprout ice "
                   "flowers, 2 damage each), then a glacier drops: 40 frost damage at the centre and 16 at the "
                   "edge of radius 8, +2 per flower, ice prisons for 4 s and a radius-5 crater bristling with "
                   "ice spikes.",
        "chant_ru": "Небо, остуди своё сердце.|Пусть первая снежинка коснётся их плеч, а сотая — сердца.|"
                    "Зима, не знающая жалости, выкуй молот из вечного льда,|"
                    "пусть небо станет наковальней, а земля — тем, что расколется.|Падай, ледяной приговор!",
        "chant_en": "Heaven, cool your heart.|Let the first snowflake touch their shoulders, and the hundredth "
                    "their heart.|Merciless winter, forge a hammer of eternal ice,|let the sky be the anvil and "
                    "the earth the thing that cracks.|Fall, frozen verdict!",
        "ingredients": None,
    },

    # ---------------------------------------------------------------- Ранг 7 — Божественный
    "tidal_leviathan": {
        "rank": 7,
        "ru": "Потоп Левиафана",
        "ru_desc": "Цунами шириной 32 и высотой 10 проносится на 80 блоков: 60 урона водой и унос волной, "
                   "в конце обрушение — 40 урона в радиусе 12, затем вся затопленная полоса мгновенно замерзает: "
                   "враги вмерзают на 5 с и при оттаивании получают ещё 25 урона холодом.",
        "en": "Deluge of the Leviathan",
        "en_desc": "A tsunami 32 wide and 10 high rolls 80 blocks: 60 water damage and foes swept along, a "
                   "crash of 40 damage within 12 blocks at the end, then the whole flooded strip flash-freezes: "
                   "foes are locked in ice for 5 s and take 25 more frost damage as it thaws.",
        "chant_ru": "Море, что старше берегов, услышь меня из глубины,|"
                    "Левиафан, спящий под тысячей волн, — открой глаза,|подними океан на свою спину|"
                    "и обрушь его на сушу, как на собственную добычу,|"
                    "а когда схлынет вода — пусть застынет даже эхо.|Потоп Левиафана!",
        "chant_en": "Sea older than the shores, hear me from the depths,|Leviathan sleeping beneath a thousand "
                    "waves — open your eyes,|lift the ocean upon your back|and bring it down upon the land as "
                    "upon your prey,|and when the water recedes — let even the echo freeze.|Deluge of the "
                    "Leviathan!",
        "ingredients": None,
    },
}
