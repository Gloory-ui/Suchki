"""Тексты заклинаний стихии «Ветер» (wind). Формат — в tools/spelldata/__init__.py.
Числа в описаниях — как в коде (magic/spells/WindSpells.java) при силе ×1.
"""

SPELLS = {
    # ---------------------------------------------------------------- Ранг 1 — Начальный
    "featherfall": {
        "rank": 1,
        "ru": "Восходящий Зефир",
        "ru_desc": "Порыв ветра подбрасывает на ~8 блоков (с Shift — рывок вперёд) и отталкивает врагов у ног в "
                   "2,5 блока; 60 с медленного падения и прыгучести; каждая копия мультикаста добавляет ещё порыв.",
        "en": "Rising Zephyr",
        "en_desc": "A gust tosses you ~8 blocks up (sneak to dash forward instead) and pushes foes within 2.5 blocks "
                   "away; 60 s of slow falling and jump boost; every multicast copy adds another gust.",
        "chant_ru": "",
        "chant_en": "",
        "ingredients": ["feather", "feather", "string"],
    },
    "gale_palm": {
        "rank": 1,
        "ru": "Ладонь Ветра",
        "ru_desc": "Сжатый удар воздуха в конусе 8 блоков: 5 урона ветром, мощное отбрасывание, гасит огонь на "
                   "врагах и разворачивает летящие стрелы и снаряды обратно.",
        "en": "Gale Palm",
        "en_desc": "A compressed air strike in an 8-block cone: 5 wind damage and heavy knockback, puts out burning "
                   "foes and turns incoming arrows and projectiles back.",
        "chant_ru": "",
        "chant_en": "",
        "ingredients": ["feather", "paper", "gunpowder"],
    },
    # ---------------------------------------------------------------- Ранг 2 — Средний
    "zephyr_sickles": {
        "rank": 2,
        "ru": "Незримые Серпы",
        "ru_desc": "Три почти невидимых серпа ветра веером летят на 40 блоков и режут насквозь: по 5 урона ветром и "
                   "кровотечение на 2 с; каждый срезает по пути до 24 блоков листвы, травы, цветов и паутины.",
        "en": "Unseen Sickles",
        "en_desc": "Three nearly invisible wind sickles fan out 40 blocks and cut through everything: 5 wind damage "
                   "each and 2 s of bleeding; each one shears up to 24 leaves, grass, flowers and cobwebs on its way.",
        "chant_ru": "Ветер, обнажи свои серпы — рассеки всё на пути!",
        "chant_en": "Wind, bare your sickles — cleave everything in your path!",
        "ingredients": ["wind_charge", "feather", "flint", "iron_ingot"],
    },
    # ---------------------------------------------------------------- Ранг 3 — Продвинутый
    "fly": {
        "rank": 3,
        "ru": "Тропа Ветров",
        "ru_desc": "Управляемый полёт на 30 с (1 мана в секунду, пока в воздухе); удар сильнее 6 сбивает с ветра на "
                   "2 с; в конце — 5 с плавного падения; копии мультикаста дарят крылья союзникам в 6 блоках.",
        "en": "Windwalk Flight",
        "en_desc": "Controlled flight for 30 s (1 mana per second while airborne); a hit above 6 knocks you off the "
                   "wind for 2 s; ends with 5 s of slow falling; multicast copies give wings to allies within 6 blocks.",
        "chant_ru": "Ветер, что не знает оков, одолжи мне крылья,|и пусть земля отпустит меня в небо!",
        "chant_en": "Wind that knows no shackles, lend me your wings,|and let the earth release me to the sky!",
        "ingredients": ["phantom_membrane", "phantom_membrane", "feather", "feather", "wind_charge", "gold_ingot"],
    },
    "spiral_sphere": {
        "rank": 3,
        "ru": "Вихревая Сфера",
        "ru_desc": "Рывок на 6 блоков с шаром сжатого вихря: 0,5 с перемалывает цель (5 ударов по 3 и ещё 6 — "
                   "21 урона), отшвыривает её на ~12 блоков и оставляет воронку радиусом 1,5; копии мультикаста — "
                   "самонаводящиеся шары силой ×0,7.",
        "en": "Spiral Sphere",
        "en_desc": "Dash 6 blocks with a ball of compressed whirlwind: it grinds the target for 0.5 s (5 hits of 3 "
                   "plus 6 — 21 damage), hurls it ~12 blocks and leaves a radius-1.5 crater; multicast copies are "
                   "homing spheres at ×0.7 power.",
        "chant_ru": "Ветер, свернись в кулаке и не знай покоя.|Вращайся, пока сам мир не треснет!",
        "chant_en": "Wind, coil in my fist and know no rest.|Spin until the world itself cracks!",
        "ingredients": ["breeze_rod", "wind_charge", "wind_charge", "ender_pearl", "gold_ingot"],
    },
    # ---------------------------------------------------------------- Ранг 4 — Святой
    "howling_cyclone": {
        "rank": 4,
        "ru": "Воющий Смерч",
        "ru_desc": "У прицела (до 96 блоков) встаёт смерч высотой 16 блоков на 8 с и бредёт за взглядом: затягивает "
                   "врагов в 8 блоках, поднимает и кружит их (6 урона в секунду), в конце швыряет прочь с 6 уроном; "
                   "срывает листву, траву, стекло и шерсть.",
        "en": "Howling Cyclone",
        "en_desc": "A 16-block-tall tornado rises at the crosshair (up to 96 blocks) for 8 s and follows your gaze: "
                   "it drags in foes within 8 blocks, lifts and spins them (6 damage per second) and finally flings "
                   "them away for 6 damage; tears up leaves, grass, glass and wool.",
        "chant_ru": "Ветра всех четырёх сторон — сойдитесь в одной точке,|завывая, скрутитесь в столп до небес|"
                    "и сорвите прочь всё, что не пустило корней!",
        "chant_en": "Winds of the four quarters — converge on a single point,|howl and twist into a pillar that reaches "
                    "the sky,|tear away all that has not taken root!",
        "ingredients": ["breeze_rod", "breeze_rod", "wind_charge", "wind_charge", "phantom_membrane", "diamond"],
    },
    # ---------------------------------------------------------------- Ранг 5 — Королевский
    "spiral_shuriken": {
        "rank": 5,
        "ru": "Вихревой Сюрикэн",
        "ru_desc": "Сюрикэн из ветра летит до 64 блоков и раскрывается сферой микро-лезвий радиусом 6 на 1,5 с: "
                   "10 ударов по 1,6 и финальные 12 (28 урона), 5 с кровотечения, цели держит на месте; оставляет "
                   "кратер радиусом 4.",
        "en": "Spiral Shuriken",
        "en_desc": "A shuriken of wind flies up to 64 blocks and bursts into a radius-6 sphere of micro-blades for "
                   "1.5 s: 10 hits of 1.6 and a final 12 (28 damage), 5 s of bleeding, targets held in place; leaves "
                   "a radius-4 crater.",
        "chant_ru": "Ветер, свернись в сферу, сфера — в звезду.|Пусть каждая клетка будет рассечена.|"
                    "Разорви и не оставь ничего целого!|Вихревой Сюрикэн!",
        "chant_en": "Wind, coil into a sphere, the sphere into a star.|Let every cell be severed.|"
                    "Tear apart and leave nothing whole!|Spiral Shuriken!",
        "ingredients": None,
    },
    # ---------------------------------------------------------------- Ранг 6 — Императорский
    "sky_maelstrom": {
        "rank": 6,
        "ru": "Небесные Жернова",
        "ru_desc": "Из облачного круга в 24 блоках над прицелом (до 128 блоков) спускается торнадо на 10 с и бредёт "
                   "за взглядом: тянет врагов в 12 блоках, поднимает до 20 блоков, 8 урона в секунду; в финале "
                   "швыряет и вбивает в землю — 30 урона; выдирает землю, песок и листву и разбрасывает обломки.",
        "en": "Millstones of Heaven",
        "en_desc": "A tornado descends for 10 s from a cloud circle 24 blocks above the crosshair (up to 128 blocks) "
                   "and follows your gaze: it drags in foes within 12 blocks, lifts them up to 20 blocks, 8 damage per "
                   "second; at the end it flings them and slams them into the ground for 30 damage; rips up dirt, "
                   "sand and leaves and scatters the debris.",
        "chant_ru": "Ветра четырёх сторон света, внемлите зову того, кто держит небо.|"
                    "Два жернова неба, верхний и нижний, вращайтесь навстречу друг другу,|"
                    "сплетитесь в одну спираль,|спуститесь столпом гнева|и смелите упрямцев в пыль!",
        "chant_en": "Winds of the four quarters, heed the one who holds the sky.|"
                    "Two millstones of heaven, upper and lower, turn against each other,|"
                    "braid yourselves into one spiral,|descend as a pillar of wrath|and mill the stubborn into dust!",
        "ingredients": None,
    },
    # ---------------------------------------------------------------- Ранг 7 — Божественный
    "storm_dragon_breath": {
        "rank": 7,
        "ru": "Дыхание Штормового Дракона",
        "ru_desc": "За спиной собирается голова дракона из ветра и выдыхает луч бури на 3 с: цилиндр радиусом 4 и "
                   "длиной 128 блоков, 12 урона ветром каждые 0,25 с (до 144) и тоннель радиусом 3 на 96 блоков "
                   "сквозь всё, кроме обсидиана и бедрока.",
        "en": "Storm Dragon's Breath",
        "en_desc": "A dragon head of wind forms behind you and exhales a storm beam for 3 s: a cylinder 4 blocks in "
                   "radius and 128 long, 12 wind damage every 0.25 s (up to 144) and a radius-3 tunnel 96 blocks long "
                   "through everything but obsidian and bedrock.",
        "chant_ru": "Владыка бурь, спящий в запечатанной пещере,|одолжи мне свой вдох на одно мгновение,|"
                    "ветер, что сдирает горы до самых костей,|гром, что раскалывает небесный свод,|"
                    "сожмитесь в моей груди в единое дыхание|и вырвитесь наружу рёвом дракона!",
        "chant_en": "Lord of storms who sleeps in a sealed cavern,|lend me your breath for a single instant,|"
                    "wind that strips mountains to the bone,|thunder that splits the vault of heaven,|"
                    "compress within my chest into a single breath,|and burst forth as a dragon's roar!",
        "ingredients": None,
    },
}
