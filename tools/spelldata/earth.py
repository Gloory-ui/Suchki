"""Тексты заклинаний стихии «Земля» (earth). Формат — в tools/spelldata/__init__.py.
Числа в описаниях — как в коде (magic/spells/EarthSpells.java) при силе ×1.
"""

SPELLS = {
    # ---------------------------------------------------------------- Ранг 1 — Начальный
    "stoneflesh": {
        "rank": 1,
        "ru": "Каменная Кожа",
        "ru_desc": "Кожа твердеет камнем на 90 с: +4 брони, +2 прочности брони и +0,3 к устойчивости к отбрасыванию; "
                   "первый удар сильнее 6 раз в 5 с ослаблен на 2; копии мультикаста покрывают союзников в 6 блоках.",
        "en": "Stoneflesh",
        "en_desc": "Your skin hardens into stone for 90 s: +4 armor, +2 armor toughness and +0.3 knockback resistance; "
                   "the first hit above 6 every 5 s is reduced by 2; multicast copies coat allies within 6 blocks.",
        "chant_ru": "",
        "chant_en": "",
        "ingredients": ["cobblestone", "cobblestone", "clay_ball"],
    },
    # ---------------------------------------------------------------- Ранг 2 — Средний
    "stone_cannon": {
        "rank": 2,
        "ru": "Нарезное Каменное Ядро",
        "ru_desc": "Раскрученное каменное ядро на 96 блоков: 8–16 урона землёй по мере зарядки и сильный отброс; "
                   "на полной зарядке пробивает цель насквозь и не замечает 30% брони; разбивает по пути до 4 "
                   "хрупких блоков (стекло, лёд, листва).",
        "en": "Rifled Stone Round",
        "en_desc": "A spinning stone round flying 96 blocks: 8–16 earth damage depending on charge and heavy "
                   "knockback; at full charge it pierces a target and ignores 30% of armor; shatters up to 4 fragile "
                   "blocks (glass, ice, leaves) on its way.",
        "chant_ru": "Камень, вращайся и пронзи — ядро земли, лети!",
        "chant_en": "Stone, spin and pierce — core of the earth, fly!",
        "ingredients": ["cobblestone", "cobblestone", "flint", "gunpowder"],
    },
    "earth_wall": {
        "rank": 2,
        "ru": "Земляной Бастион",
        "ru_desc": "У прицела (до 48 блоков) поднимается каменная стена 7×4 на 15 с и выталкивает существ из кладки; "
                   "с Shift — кольцо-крепость 5×5 высотой 3; над водой, лавой или пропастью — мост шириной 3 до 48 "
                   "блоков на 60 с; каждая копия мультикаста — +7 к ширине стены.",
        "en": "Earthen Bulwark",
        "en_desc": "A 7×4 stone wall rises at the crosshair (up to 48 blocks) for 15 s, pushing creatures out of it; "
                   "sneak to raise a 5×5 ring fortress 3 high instead; aimed over water, lava or a chasm it builds a "
                   "3-wide bridge up to 48 blocks long for 60 s; each multicast copy adds 7 to the wall's width.",
        "chant_ru": "Земля, поднимись щитом пред моим взором!",
        "chant_en": "Earth, rise as a shield before my gaze!",
        "ingredients": ["stone_bricks", "stone_bricks", "cobblestone", "clay_ball", "brick"],
    },
    # ---------------------------------------------------------------- Ранг 3 — Продвинутый
    "quagmire": {
        "rank": 3,
        "ru": "Бездонная Топь",
        "ru_desc": "Земля радиусом 5 у прицела (до 64 блоков) становится трясиной на 12 с: враги намертво вязнут на "
                   "3 с, потом — Замедление IV, мелкие тонут (1 урон в секунду); летящих и боссов — Замедление II; "
                   "копии мультикаста — топи радиусом 4 у других целей.",
        "en": "Bottomless Mire",
        "en_desc": "The ground in a radius of 5 at the crosshair (up to 64 blocks) turns into a mire for 12 s: foes "
                   "are rooted for 3 s, then get Slowness IV, small ones sink (1 damage per second); flyers and "
                   "bosses get Slowness II; multicast copies open radius-4 mires at other targets.",
        "chant_ru": "Твердь, забудь свою крепость и стань трясиной,|пусть каждый шаг врага тонет без дна!",
        "chant_en": "Bedrock, forget your firmness and become a mire,|let every step of the foe sink without a bottom!",
        "ingredients": ["mud", "mud", "slime_ball", "slime_ball", "clay_ball", "mangrove_roots"],
    },
    # ---------------------------------------------------------------- Ранг 4 — Святой
    "earthquake": {
        "rank": 4,
        "ru": "Рокот Священной Горы",
        "ru_desc": "От мага расходятся три ударные волны (радиус 6, 11 и 16, через 0,4 с): каждая — 10 урона землёй "
                   "всем врагам на земле, подброс и Замедление II на 3 с; земля раскалывается 4–6 трещинами до 16 "
                   "блоков, стекло и листва рядом бьются (без разрушения — каменные шипы на 6 с); копия "
                   "мультикаста — ещё волна (+5 к радиусу) и +15% урона.",
        "en": "Rumble of the Sacred Mountain",
        "en_desc": "Three shockwaves spread from the caster (radius 6, 11 and 16, 0.4 s apart): each deals 10 earth "
                   "damage to every grounded foe, launches it and applies Slowness II for 3 s; the ground splits "
                   "into 4–6 fissures up to 16 blocks long and nearby glass and leaves shatter (without block damage, "
                   "stone spikes for 6 s instead); each multicast copy adds a wave (+5 radius) and +15% damage.",
        "chant_ru": "Корни гор, спящие под нами, — услышьте меня,|поднимитесь, разломите кожу земли,|"
                    "и пусть дрогнет всё, что стоит на ней!",
        "chant_en": "Roots of the mountains sleeping beneath us — hear me,|rise and split the skin of the earth,|"
                    "let all that stands upon it tremble!",
        "ingredients": ["cracked_stone_bricks", "cracked_stone_bricks", "tuff", "tnt", "obsidian", "diamond"],
    },
    # ---------------------------------------------------------------- Ранг 5 — Королевский
    "iron_guardian": {
        "rank": 5,
        "ru": "Каменный Колосс",
        "ru_desc": "У прицела (до 48 блоков) из каменного столпа встаёт колосс ×1,8 на 90 с: 150 здоровья, удар "
                   "×1,3, и каждый его удар сотрясает землю — 6 урона землёй врагам в 2,5 блока от цели; колосс "
                   "один (два при силе ×1,2), каждая копия мультикаста — ещё малый колосс ×1,3.",
        "en": "Stone Colossus",
        "en_desc": "A ×1.8 colossus rises from a stone pillar at the crosshair (up to 48 blocks) for 90 s: 150 "
                   "health, ×1.3 attack, and every blow shakes the ground for 6 earth damage to foes within 2.5 "
                   "blocks of its target; one colossus at a time (two at ×1.2 power), each multicast copy adds a "
                   "smaller ×1.3 one.",
        "chant_ru": "Камень, что помнит рождение гор,|обрети руки и ноги,|встань рядом со мной|"
                    "и стой, пока не рухнет небо!",
        "chant_en": "Stone that remembers the birth of mountains,|take on arms and legs,|stand at my side|"
                    "and stand until the sky falls!",
        "ingredients": None,
    },
    "fissure": {
        "rank": 5,
        "ru": "Пасть Разлома",
        "ru_desc": "От мага к прицелу (до 64 блоков) земля раскалывается трещиной 3 в ширину и 8 в глубину; враги "
                   "проваливаются и вязнут, а через 4 с разлом смыкается: 34 урона землёй и выброс наверх (боссам — "
                   "20 урона сразу); земля возвращается, при разрушении края остаются щебнем; копии мультикаста — "
                   "параллельные трещины в 4 блоках.",
        "en": "Maw of the Rift",
        "en_desc": "The ground splits from the caster to the crosshair (up to 64 blocks) in a rift 3 wide and 8 deep; "
                   "foes fall in and are stuck, and after 4 s the rift slams shut: 34 earth damage and an ejection "
                   "upwards (bosses take 20 at once); the ground returns, with block damage its edges stay as rubble; "
                   "multicast copies open parallel rifts 4 blocks apart.",
        "chant_ru": "Кости мира, разойдитесь по моему слову,|раскройся, пасть земли, глубже корней гор,|"
                    "прими незваных в свою темноту|и сомкни челюсти навсегда!",
        "chant_en": "Bones of the world, part at my word,|open, maw of earth, deeper than mountain roots,|"
                    "take the uninvited into your darkness,|and close your jaws forever!",
        "ingredients": None,
    },
    # ---------------------------------------------------------------- Ранг 6 — Императорский
    "titan_upheaval": {
        "rank": 6,
        "ru": "Восстание Титана",
        "ru_desc": "У прицела (до 128 блоков) встают три кольца каменных копий (радиус 4, 8 и 12, через 0,5 с): "
                   "12 урона землёй и подброс; через 2,5 с центр обрушивается — 16 урона в радиусе 7 и Замедление III "
                   "(всего до 28 урона); при разрушении — воронка радиусом 7 и щебень; копии мультикаста — малые "
                   "эпицентры у других целей.",
        "en": "Titan's Upheaval",
        "en_desc": "Three rings of stone spears rise at the crosshair (up to 128 blocks; radius 4, 8 and 12, 0.5 s "
                   "apart): 12 earth damage and a launch; after 2.5 s the centre collapses for 16 damage within 7 "
                   "blocks and Slowness III (up to 28 damage in total); with block damage it leaves a radius-7 crater "
                   "and rubble; multicast copies raise smaller epicentres at other targets.",
        "chant_ru": "Кости земли, древние и немые,|проснитесь по моему слову.|Пусть твердь станет копьём,|"
                    "пусть горы встанут из праха,|и пусть мир провалится под ногами врагов!",
        "chant_en": "Bones of the earth, ancient and mute,|awaken at my word.|Let the ground become a spear,|"
                    "let mountains rise from the dust,|and let the world give way beneath my foes!",
        "ingredients": None,
    },
    # ---------------------------------------------------------------- Ранг 7 — Божественный
    "mountain_awakening": {
        "rank": 7,
        "ru": "Пробуждение Великой Горы",
        "ru_desc": "Под прицелом (до 128 блоков) за 1,5 с вырастает гора высотой 30 и радиусом 12, поднимая врагов "
                   "(40 урона землёй, дальних до 20 блоков отбрасывает), а через 3 с рушится лавиной валунов: до 90 "
                   "урона (к краю радиуса 20 — 40%) и Замедление IV — всего до 130; при разрушении остаётся воронка "
                   "радиусом 12 и глубиной 8 в кольце каменных холмов.",
        "en": "Awakening of the Great Mountain",
        "en_desc": "A mountain 30 high and 12 in radius grows under the crosshair (up to 128 blocks) in 1.5 s, lifting "
                   "foes (40 earth damage, those out to 20 blocks are thrown back), and after 3 s it collapses in an "
                   "avalanche of boulders: up to 90 damage (40% at the radius-20 edge) and Slowness IV — up to 130 in "
                   "total; with block damage it leaves a crater 12 in radius and 8 deep ringed by rubble hills.",
        "chant_ru": "Великая гора, спящая под кожей мира,|я называю твоё имя, забытое людьми,|"
                    "поднимись из глубин, неси врагов на своих плечах,|пусть они увидят небо в последний раз,|"
                    "а затем — обрушься всей тяжестью земли!|Пробудись, Великая Гора!",
        "chant_en": "Great mountain sleeping beneath the skin of the world,|I speak your name, forgotten by men,|"
                    "rise from the depths, carry my foes upon your shoulders,|let them see the sky one last time,|"
                    "and then — come down with all the weight of the earth!|Awaken, Great Mountain!",
        "ingredients": None,
    },
}
