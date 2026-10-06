"""Тексты заклинаний: по модулю на стихию (имя модуля = School.id: fire, water, wind, earth, lightning, light, dark,
space, life, mind, summon, fusion). tools/gen_magic.py читает их все и пишет переводы и рецепты книг.

Формат модуля:

    SPELLS = {
        "fire_arrow": {                       # id заклинания — как в Spell.of("fire_arrow", ...) в Java
            "rank": 1,                        # ранг 1…7 — как Spell.tier
            "ru": "Огненная стрела",          # название
            "ru_desc": "Снаряд: 6 урона огнём и поджог на 3 с.",   # числа — как в коде заклинания
            "en": "Fire Arrow",
            "en_desc": "Projectile: 6 fire damage, sets the target on fire for 3 s.",
            "chant_ru": "Пламя, стань моей стрелой!",   # заклинательная фраза: строки через «|»
            "chant_en": "Flame, become my arrow!",
            "ingredients": ["blaze_powder", "flint"],   # рецепт книги: ванильные предметы без «minecraft:»
        },
    }

Что пишется:
- переводы spell.suchki.<id>, spell.suchki.<id>.desc и spell.suchki.<id>.chant (ru_ru и en_us);
  у фразы строки разделены «|» (Spell.chantKey); пустая фраза — ключ .chant не пишется;
- рецепт data/suchki/recipe/tome_<id>.json: книга + ingredients → suchki:tome_<стихия> с компонентом
  suchki:spell = <id>. Ранги 1–4 могут иметь рецепт (до 8 предметов), ранги 5–7 — только находки: ingredients = None.

Правила (gen_magic.py проверяет и пишет предупреждения):
- id уникален во всех модулях; ранг 1…7; ru/en/ru_desc/en_desc не пустые;
- у рангов 2+ есть фраза; строк: ранг 2 — 1, 3 — 2, 4 — 3, 5 — 4, 6–7 — 5–6; в ru и en строк поровну;
- ингредиенты — существующие ванильные предметы (проверяется по клиенту Minecraft из кэша Gradle, если он есть);
- каждое заклинание из Java (Spell.of в magic/spells/*.java) должно иметь тексты, и наоборот.
"""
