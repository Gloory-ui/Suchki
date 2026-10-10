"""Тексты заклинаний стихии «Жизнь (исцеление, природа)» (life). Формат — в tools/spelldata/__init__.py.
Числа в описаниях — как в коде (magic/spells/LifeSpells.java) при силе ×1.
"""

SPELLS = {
    # ---------------------------------------------------------------- Ранг 1 — Начальный
    "healing": {
        "rank": 1,
        "ru": "Мистическая Ладонь",
        "ru_desc": "Пока держите, зелёный свет в ладони лечит вас на 2 здоровья в секунду, а союзника перед вами "
                   "(до 6 блоков) — на 2,4; каждые 3 с на одном пациенте вытягивают из него один дебафф.",
        "en": "Mystic Palm",
        "en_desc": "While held, green light in your palm heals you for 2 health per second, or the ally in front of "
                   "you (up to 6 blocks) for 2.4; every 3 s on one patient draws out one harmful effect.",
        "chant_ru": "",
        "chant_en": "",
        "ingredients": ["glistering_melon_slice", "sweet_berries"],
    },
    "flower_field": {
        "rank": 1,
        "ru": "Луг Цветущих Воспоминаний",
        "ru_desc": "Вокруг расцветает луг радиусом 6: цветы на траве, своим — Регенерация I на 8 с и ослабление яда "
                   "и иссушения, звери 10 с тянутся к вам, нежить замедляется на 3 с.",
        "en": "Meadow of Blooming Memories",
        "en_desc": "A meadow blooms around you in a 6-block radius: flowers on the grass, Regeneration I for 8 s and "
                   "weaker poison and wither for allies, animals follow you for 10 s, undead are slowed for 3 s.",
        "chant_ru": "",
        "chant_en": "",
        "ingredients": ["bone_meal", "poppy", "dandelion"],
    },

    # ---------------------------------------------------------------- Ранг 2 — Средний
    "heal_other": {
        "rank": 2,
        "ru": "Исцеление Союзника",
        "ru_desc": "Лечит союзника у прицела (до 48 блоков) на 9 здоровья, снимает с него один дебафф и гасит огонь; "
                   "нежить под прицелом получает 7 урона святостью.",
        "en": "Heal Other",
        "en_desc": "Heals the ally under your crosshair (up to 48 blocks) for 9 health, removes one harmful effect "
                   "and puts out fire; undead under the crosshair take 7 holy damage instead.",
        "chant_ru": "Зелёный свет, затяни чужие раны.",
        "chant_en": "Green light, close another's wounds.",
        "ingredients": ["golden_carrot", "glistering_melon_slice", "lapis_lazuli"],
    },
    "cure": {
        "rank": 2,
        "ru": "Очищение",
        "ru_desc": "Снимает все негативные эффекты, огонь и холод, лечит 4 здоровья и на 5 с защищает от яда, "
                   "иссушения и голода.",
        "en": "Purification",
        "en_desc": "Removes all harmful effects, fire and freezing, heals 4 health and wards off poison, wither and "
                   "hunger for 5 s.",
        "chant_ru": "Яд и проклятие, покиньте это тело!",
        "chant_en": "Poison and curse, leave this body!",
        "ingredients": ["milk_bucket", "honey_bottle", "glow_berries"],
    },

    # ---------------------------------------------------------------- Ранг 3 — Продвинутый
    "rejuvenation": {
        "rank": 3,
        "ru": "Обновление",
        "ru_desc": "Вы и союзники в радиусе 8 (питомцы и призванные тоже) получаете Регенерацию II на 10 с "
                   "и 3 здоровья.",
        "en": "Rejuvenation",
        "en_desc": "You and allies within 8 blocks (pets and summons included) gain Regeneration II for 10 s and "
                   "3 health.",
        "chant_ru": "Весна, приди раньше срока,|пусть каждое сердце забьётся сильнее.",
        "chant_en": "Spring, come before your time,|let every heart beat stronger.",
        "ingredients": ["golden_apple", "moss_block", "glistering_melon_slice", "emerald"],
    },

    # ---------------------------------------------------------------- Ранг 4 — Святой
    "healing_circle": {
        "rank": 4,
        "ru": "Священный Родник",
        "ru_desc": "Родник радиусом 6 у прицела (сидя — у ног) на 12 с: союзники получают 6 здоровья при входе, "
                   "потом по 2 в секунду и очищение, нежить горит по 4 в секунду, вражеские снаряды сгорают.",
        "en": "Sacred Wellspring",
        "en_desc": "A 6-block wellspring at your crosshair (at your feet while sneaking) for 12 s: allies get "
                   "6 health on entry, then 2 per second and cleansing; undead burn for 4 per second, enemy "
                   "projectiles burn away.",
        "chant_ru": "Чистая вода, что течёт из сердца богини,|омой раны, изгони яд и проклятье,|"
                    "и пусть из камня прорастут цветы!",
        "chant_en": "Pure water flowing from the goddess's heart,|wash the wounds, banish poison and curse,|"
                    "and let flowers sprout from stone!",
        "ingredients": ["ghast_tear", "golden_apple", "glow_berries", "diamond", "flowering_azalea"],
    },

    # ---------------------------------------------------------------- Ранг 5 — Королевский
    "undying": {
        "rank": 5,
        "ru": "Печать Бессмертия",
        "ru_desc": "На 60 с смерть отступает от вас (или от союзника под прицелом в 16 блоках): смертельный удар "
                   "оставляет половину здоровья, Регенерацию II и Поглощение II, а золотой взрыв отбрасывает "
                   "врагов в 4 блоках.",
        "en": "Seal of Undying",
        "en_desc": "For 60 s death steps back from you (or from the ally under your crosshair within 16 blocks): "
                   "a lethal blow leaves half health, Regeneration II and Absorption II, and a golden burst throws "
                   "back foes within 4 blocks.",
        "chant_ru": "Печать, что копила силу сто дней, —|раскройся во мне.|Пусть смерть постучит —|"
                    "я не открою ей дверь!",
        "chant_en": "Seal that gathered strength for a hundred days —|open within me.|Let death come knocking —|"
                    "I will not open the door!",
        "ingredients": None,
    },

    # ---------------------------------------------------------------- Ранг 6 — Императорский
    "deep_forest_bloom": {
        "rank": 6,
        "ru": "Явление Дремучего Леса",
        "ru_desc": "У прицела (до 96 блоков) за секунду вырастает лес из 18 деревьев радиусом 12: корни держат "
                   "врагов 5 с, стволы бьют на 12 и через 3 с давят ещё на 24, союзники получают 4 здоровья "
                   "и Регенерацию II на 10 с.",
        "en": "Deep Forest Emergence",
        "en_desc": "A forest of 18 trees bursts from the ground at your crosshair (up to 96 blocks) in a 12-block "
                   "radius: roots hold foes for 5 s, trunks strike for 12 and crush for 24 more after 3 s, allies "
                   "gain 4 health and Regeneration II for 10 s.",
        "chant_ru": "Семя, что спало тысячу лет, — проснись.|Корни, станьте цепями.|Ветви, станьте стенами.|"
                    "Лес, рождённый в одно мгновение,|прими моих врагов в свои объятия!",
        "chant_en": "Seed that slept a thousand years — awaken.|Roots, become chains.|Branches, become walls.|"
                    "Forest born in a single instant,|take my enemies into your embrace!",
        "ingredients": None,
    },

    # ---------------------------------------------------------------- Ранг 7 — Божественный
    "yggdrasil_bloom": {
        "rank": 7,
        "ru": "Цветение Иггдрасиля",
        "ru_desc": "У прицела (до 128 блоков) на минуту вырастает Мировое Древо: союзники в 32 блоках полностью "
                   "исцеляются, получают Регенерацию III, Поглощение IV, Печать бессмертия и 30% маны, павшие "
                   "питомцы возвращаются, а корни 7 раз бьют врагов по 14 урона.",
        "en": "Bloom of Yggdrasil",
        "en_desc": "The World Tree rises at your crosshair (up to 128 blocks) for a minute: allies within 32 blocks "
                   "are fully healed and gain Regeneration III, Absorption IV, the Seal of Undying and 30% mana, "
                   "fallen pets return, and roots strike foes 7 times for 14 damage.",
        "chant_ru": "Корни, что держат девять миров,|ветви, что касаются звёзд,|"
                    "услышьте зов того, кто бережёт жизнь,|пусть каждая рана закроется, как бутон на рассвете,|"
                    "а каждый враг станет землёй для новых корней.|Расцвети, Иггдрасиль!",
        "chant_en": "Roots that hold the nine worlds,|branches that touch the stars,|"
                    "hear the call of one who guards life,|let every wound close like a bud at dawn,|"
                    "and every foe become soil for new roots.|Bloom, Yggdrasil!",
        "ingredients": None,
    },
}
