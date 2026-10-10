"""Тексты заклинаний стихии «Призыв (духи, призрачное оружие)» (summon). Формат — в tools/spelldata/__init__.py.
"""

SPELLS = {
    # --- Ранг 1 ---
    "bound_blade": {
        "rank": 1,
        "ru": "Призрачный Клинок",
        "ru_desc": "Призрачный меч во вторую руку на 2 минуты: Острота III (V при силе ×1,2), не ломается и не кладётся "
                   "в сундуки; копия мультикаста достаётся союзнику в 6 блоках.",
        "en": "Bound Blade",
        "en_desc": "A spectral sword in your off hand for 2 minutes: Sharpness III (V at power ×1.2), unbreakable, cannot be "
                   "stored; a multicast copy goes to an ally within 6 blocks.",
        "chant_ru": "",
        "chant_en": "",
        "ingredients": ["iron_sword", "amethyst_shard", "glowstone_dust"],
    },
    "familiar": {
        "rank": 1,
        "ru": "Фамильяр",
        "ru_desc": "Из дымки у ног выпрыгивает волк-фамильяр (30 здоровья, 6 урона, +20% скорости; два при силе ×1,2) "
                   "и служит 90 с; не больше трёх сразу.",
        "en": "Familiar",
        "en_desc": "A familiar wolf leaps out of the mist at your feet (30 health, 6 damage, +20% speed; two at power ×1.2) "
                   "and serves for 90 s; three at most.",
        "chant_ru": "",
        "chant_en": "",
        "ingredients": ["bone", "lead", "amethyst_shard"],
    },

    # --- Ранг 2 ---
    "bound_tool": {
        "rank": 2,
        "ru": "Призрачный Инструмент",
        "ru_desc": "Призрачная кирка, топор или лопата — под блок в прицеле (до 6 блоков) — на 3 минуты, Эффективность III "
                   "(V при силе ×1,2); копии мультикаста дают остальные два инструмента.",
        "en": "Bound Tool",
        "en_desc": "A spectral pickaxe, axe or shovel matching the block in your sights (up to 6 blocks) for 3 minutes, "
                   "Efficiency III (V at power ×1.2); multicast copies add the other two tools.",
        "chant_ru": "Свет, обрети форму — стань орудием в моей руке!",
        "chant_en": "Light, take shape — become a tool in my hand!",
        "ingredients": ["iron_pickaxe", "amethyst_shard", "redstone"],
    },
    "spectral_steed": {
        "rank": 2,
        "ru": "Призрачный Скакун",
        "ru_desc": "Конь из тумана (скорость 0,32, прыжок 1,0, 30 здоровья) на 5 минут — вы сразу в седле; не разбивается "
                   "при падении, до 2 с скачет по воде и исчезает, если 10 с остаётся без всадника.",
        "en": "Spectral Steed",
        "en_desc": "A steed of mist (speed 0.32, jump 1.0, 30 health) for 5 minutes — you mount it at once; it takes no fall "
                   "damage, gallops over water for up to 2 s and fades after 10 s without a rider.",
        "chant_ru": "Конь из тумана, неси меня туда, где кончается дорога!",
        "chant_en": "Steed of mist, carry me to where the road ends!",
        "ingredients": ["saddle", "golden_carrot", "phantom_membrane"],
    },

    # --- Ранг 3 ---
    "spirit_blades": {
        "rank": 3,
        "ru": "Проекция: Парящие Клинки",
        "ru_desc": "За спиной по одному проступают 5 мечей на 15 с: раз в секунду меч летит во врага под прицелом "
                   "(до 20 блоков) — 7 урона, пробивает одного — и возвращается через 3 с; ваш удар вблизи добавляет удар "
                   "меча на 5; мультикаст — ещё 3 меча за копию.",
        "en": "Projection: Hovering Blades",
        "en_desc": "Five swords form one by one behind you for 15 s: once a second a sword flies at the enemy in your sights "
                   "(up to 20 blocks) — 7 damage, pierces one — and reforms after 3 s; your melee hits add a 5-damage sword "
                   "strike; multicast adds 3 swords per copy.",
        "chant_ru": "Сотворение начато — структура постигнута,|клинки из памяти, обретите плоть!",
        "chant_en": "Projection begins — the structure is understood,|blades of memory, take on flesh!",
        "ingredients": ["iron_sword", "iron_sword", "amethyst_shard", "ender_pearl"],
    },
    "raise_dead": {
        "rank": 3,
        "ru": "Теневые Солдаты",
        "ru_desc": "Из тени у прицела (до 24 блоков) встают 2 солдата с железными мечами (3 при силе ×1,2, +20% здоровья) "
                   "и сражаются за вас 60 с; не больше шести.",
        "en": "Shadow Soldiers",
        "en_desc": "Two soldiers with iron swords rise from the shadow at your sights (up to 24 blocks; three at power ×1.2, "
                   "+20% health) and fight for you for 60 s; six at most.",
        "chant_ru": "Павшие, ваша служба ещё не окончена —|встаньте из тени и вновь поднимите мечи!",
        "chant_en": "Fallen ones, your service is not yet over —|rise from the shadow and raise your swords once more!",
        "ingredients": ["rotten_flesh", "bone", "soul_sand", "ink_sac"],
    },

    # --- Ранг 4 ---
    "death_knight": {
        "rank": 4,
        "ru": "Рыцарь Могильного Покоя",
        "ru_desc": "У прицела (до 32 блоков) из земли встаёт рыцарь-нежить (80 здоровья, 12 брони, 10 урона, щит) на 60 с: "
                   "каждые 2 с враги в 10 блоках бросаются на него, а первый смертельный удар оставляет его с 1 здоровья.",
        "en": "Gravebound Knight",
        "en_desc": "An undead knight rises from the ground at your sights (up to 32 blocks; 80 health, 12 armour, 10 damage, "
                   "shield) for 60 s: every 2 s enemies within 10 blocks charge at it, and it survives the first lethal "
                   "blow with 1 health.",
        "chant_ru": "Из праха павших, из ржавчины мечей|восстань, рыцарь, не знающий смерти,|"
                    "стань щитом, о который разобьётся их ярость!",
        "chant_en": "From the dust of the fallen, from the rust of swords,|arise, knight who knows not death,|"
                    "become the shield upon which their fury breaks!",
        "ingredients": ["wither_skeleton_skull", "netherite_scrap", "shield", "soul_soil"],
    },

    # --- Ранг 5 ---
    "treasury_gate": {
        "rank": 5,
        "ru": "Врата Тысячи Сокровищ",
        "ru_desc": "Пока держите (до 5 с; 60 маны сразу и 16 в секунду), за спиной парами открываются 12 золотых врат и "
                   "раз в секунду каждые мечут оружие в прицел (до 96 блоков): 4 урона, каждое шестое — 6 и взрыв "
                   "(воронка 1,5); не больше 8 урона в секунду по одной цели.",
        "en": "Gate of a Thousand Treasures",
        "en_desc": "While held (up to 5 s; 60 mana up front and 16 per second), 12 golden gates open in pairs behind you and "
                   "each hurls a weapon at your sights once a second (up to 96 blocks): 4 damage, every sixth one deals 6 "
                   "and bursts (crater 1.5); at most 8 damage per second to one target.",
        "chant_ru": "Сокровищница древнего царя, отвори свои двери,|всё оружие этого мира некогда было моим,|"
                    "сверкайте, клинки и копья, из золотой ряби,|пусть враг узрит богатство, что его погубит!",
        "chant_en": "Treasury of the ancient king, open your doors,|every weapon of this world was once mine,|"
                    "shine, blades and spears, from the golden ripples,|let my foe behold the wealth that will slay him!",
        "ingredients": None,
    },

    # --- Ранг 6 ---
    "endless_blade_graveyard": {
        "rank": 6,
        "ru": "Бесконечное Кладбище Клинков",
        "ru_desc": "Мир радиусом 20 на 15 с становится холмом мечей: 4 меча в секунду летят во врагов внутри (7 урона, не "
                   "больше 14 в секунду), враги замедлены и не могут уйти, павшие встают вашими тенями (до 6); присядьте — "
                   "залп из 20 мечей на прицел (по 5 урона).",
        "en": "Endless Graveyard of Blades",
        "en_desc": "For 15 s the world within 20 blocks becomes a hill of swords: 4 swords a second fly at enemies inside "
                   "(7 damage, at most 14 per second), enemies are slowed and cannot leave, the fallen rise as your shadows "
                   "(up to 6); crouch for a volley of 20 swords onto your sights (5 damage each).",
        "chant_ru": "Память о каждом клинке — моё наследство,|я ковал их в одиночестве у пепельного горна,|"
                    "ни славы, ни имени мне не осталось —|лишь холм, усеянный мечами, под ржавым небом.|"
                    "Так пусть же мир станет моей кузницей!|Бесконечное Кладбище Клинков!",
        "chant_en": "The memory of every blade is my inheritance,|I forged them alone at an ashen hearth,|"
                    "no glory, no name remains to me —|only a hill strewn with swords beneath a rusted sky.|"
                    "So let the world become my forge!|Endless Graveyard of Blades!",
        "ingredients": None,
    },

    # --- Ранг 7 ---
    "thousand_hand_bodhisattva": {
        "rank": 7,
        "ru": "Тысячерукий Бодхисаттва",
        "ru_desc": "За спиной встаёт золотое божество: 24 удара ладонями по прицелу (до 96 блоков) — по 14 урона в радиусе "
                   "3, затем «Нулевая длань» — луч на 60 урона в радиусе 5 и воронка радиусом 7.",
        "en": "Thousand-Armed Bodhisattva",
        "en_desc": "A golden deity rises behind you: 24 palm strikes at your sights (up to 96 blocks) — 14 damage each within "
                   "3 blocks, then the Zero Hand — a beam dealing 60 damage within 5 blocks and leaving a crater of radius 7.",
        "chant_ru": "Благодарю за этот бой.|Сто тысяч поклонов, сто тысяч ударов —|моя молитва обогнала звук.|"
                    "Встань за моей спиной, тысячерукое божество.|Сложи ладони.|Нулевая длань.",
        "chant_en": "Thank you for this battle.|A hundred thousand bows, a hundred thousand strikes —|"
                    "my prayer has outrun sound itself.|Rise behind me, thousand-armed deity.|Join your palms.|Zero Hand.",
        "ingredients": None,
    },
}
