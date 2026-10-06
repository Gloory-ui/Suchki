"""Генератор ресурсов магии Suchki. Запуск: python tools/gen_magic.py

Пишет: переводы (дописывает ключи магии в ru_ru/en_us), рецепты книг и фокусов, модели предметов,
описания частиц, sounds.json, блок света, типы урона и теги. Числа в описаниях должны совпадать с кодом
в com.suchki.magic.spells.* (там — источник правды для механики).
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / "suchki"
DATA = ROOT / "data"

SCHOOLS = {
    "destruction": ("Разрушение", "Destruction"),
    "restoration": ("Восстановление", "Restoration"),
    "light": ("Свет", "Light"),
    "dark": ("Тьма", "Darkness"),
    "alteration": ("Изменение", "Alteration"),
    "illusion": ("Иллюзия", "Illusion"),
    "conjuration": ("Колдовство", "Conjuration"),
}

# id: (школа, круг, ru имя, ru описание, en имя, en описание, ингредиенты рецепта или None — только находки)
SPELLS = {
    # --- Разрушение ---
    "firebolt": ("destruction", 1, "Огненная стрела", "Снаряд: 6 урона огнём и поджог на 3 с.",
                 "Firebolt", "Projectile: 6 fire damage, sets the target on fire for 3 s.",
                 ["blaze_powder", "flint"]),
    "flames": ("destruction", 1, "Пламя", "Поток: конус огня на 5 блоков, ~6 урона в секунду и поджог.",
               "Flames", "Channel: a 5-block cone of fire, ~6 damage per second, ignites.",
               ["blaze_powder", "coal"]),
    "ice_shard": ("destruction", 2, "Ледяной осколок", "Снаряд: 7 урона холодом, замедление II на 3 с и обледенение.",
                  "Ice Shard", "Projectile: 7 frost damage, Slowness II for 3 s and freezing.",
                  ["packed_ice", "lapis_lazuli", "lapis_lazuli"]),
    "chain_lightning": ("destruction", 3, "Цепная молния", "Бьёт цель под прицелом на 8 урона и перескакивает ещё на 3 врагов (каждый раз ×0,8). Накопление добавляет перескоки.",
                        "Chain Lightning", "Strikes the target for 8 damage and jumps to 3 more enemies (×0.8 each). Overcharge adds jumps.",
                        ["lightning_rod", "copper_ingot", "gold_ingot", "lapis_lazuli", "lapis_lazuli"]),
    "frost_nova": ("destruction", 3, "Ледяная волна", "Волна холода вокруг мага (радиус 6): 6 урона, замедление III на 4 с.",
                   "Frost Nova", "A wave of cold around you (radius 6): 6 damage, Slowness III for 4 s.",
                   ["blue_ice", "snow_block", "gold_ingot", "lapis_lazuli", "lapis_lazuli"]),
    "fireball": ("destruction", 4, "Огненный шар", "Снаряд взрывается (радиус 3,5): до 14 урона, поджог, отбрасывание. Блоки не ломает.",
                 "Fireball", "Explodes on impact (radius 3.5): up to 14 damage, fire, knockback. Does not break blocks.",
                 ["fire_charge", "magma_block", "blaze_rod", "diamond", "lapis_lazuli", "lapis_lazuli", "lapis_lazuli"]),
    "meteor_shower": ("destruction", 5, "Метеоритный дождь", "Ритуал: за 4 с в точку прицела падают 8 метеоров (накопление — больше), каждый 9 урона в радиусе 2,5.",
                      "Meteor Shower", "Ritual: 8 meteors (more when overcharged) fall on the target area over 4 s, 9 damage each in radius 2.5.",
                      None),
    # --- Восстановление ---
    "healing": ("restoration", 1, "Исцеление", "Поток: лечит мага ~2 ед. здоровья в секунду.",
                "Healing", "Channel: heals you for ~2 health per second.",
                ["glistering_melon_slice", "apple"]),
    "ward": ("restoration", 1, "Оберег", "Поток: урон спереди снижается на 70%. Каждая погашенная единица урона стоит 2 маны.",
             "Ward", "Channel: damage from the front is reduced by 70%. Each blocked point costs 2 mana.",
             ["iron_ingot", "glistering_melon_slice"]),
    "heal_other": ("restoration", 2, "Лечение ближнего", "Лечит существо под прицелом на 8 ед. Нежить вместо этого получает 6 урона.",
                   "Heal Other", "Heals the creature you aim at for 8 health. Undead take 6 damage instead.",
                   ["golden_carrot", "lapis_lazuli", "lapis_lazuli"]),
    "cure": ("restoration", 2, "Очищение", "Снимает все вредные эффекты, тушит огонь и немного лечит.",
             "Cure", "Removes all harmful effects, puts out fire and heals a little.",
             ["milk_bucket", "honey_bottle", "lapis_lazuli", "lapis_lazuli"]),
    "rejuvenation": ("restoration", 3, "Восстановление сил", "Регенерация II на 10 с магу и союзникам в радиусе 8.",
                     "Rejuvenation", "Regeneration II for 10 s for you and allies within 8 blocks.",
                     ["golden_apple", "sweet_berries", "lapis_lazuli", "lapis_lazuli"]),
    "healing_circle": ("restoration", 4, "Круг исцеления", "Ритуал: круг радиуса 4 на 12 с. Союзникам +1 ед. здоровья в секунду, нежити −1.",
                       "Healing Circle", "Ritual: a circle of radius 4 for 12 s. Allies heal 1 health per second, undead take 1 damage.",
                       ["glistering_melon_slice", "glistering_melon_slice", "ghast_tear", "diamond", "lapis_lazuli", "lapis_lazuli", "lapis_lazuli"]),
    "undying": ("restoration", 5, "Неугасимая жизнь", "На 60 с: смертельный удар отменяется один раз — половина здоровья, регенерация и поглощение.",
                "Undying", "For 60 s: a fatal blow is cancelled once — half health, regeneration and absorption.",
                None),
    # --- Свет ---
    "candlelight": ("light", 1, "Светоч", "На 3 мин за магом летит свет — тёмные пещеры не страшны.",
                    "Candlelight", "For 3 min a light follows you — no more dark caves.",
                    ["torch", "glowstone_dust"]),
    "smite": ("light", 1, "Кара", "Снаряд света: 5 урона, нежити ×2,5 и поджог.",
              "Smite", "A bolt of light: 5 damage, ×2.5 against undead and sets them on fire.",
              ["glowstone_dust", "gold_nugget"]),
    "flash": ("light", 2, "Вспышка", "Слепящая вспышка: враги в радиусе 7 слепнут на 5 с и теряют цель.",
              "Flash", "A blinding flash: enemies within 7 blocks are blinded for 5 s and lose their target.",
              ["glow_ink_sac", "glowstone_dust", "lapis_lazuli", "lapis_lazuli"]),
    "sunbeam": ("light", 3, "Солнечный луч", "Поток: луч на 24 блока, ~6 урона в секунду; нежити ×2,5 и поджог.",
                "Sunbeam", "Channel: a 24-block beam, ~6 damage per second; ×2.5 against undead and ignites them.",
                ["sunflower", "glowstone", "gold_ingot", "lapis_lazuli", "lapis_lazuli"]),
    "blessing": ("light", 3, "Благословение", "Сила I и сопротивление I на 45 с магу и союзникам в радиусе 8.",
                 "Blessing", "Strength I and Resistance I for 45 s for you and allies within 8 blocks.",
                 ["golden_apple", "glowstone", "lapis_lazuli", "lapis_lazuli"]),
    "sanctuary": ("light", 4, "Святая земля", "Ритуал: зона радиуса 6 на 15 с. Нежить горит, враги выталкиваются, союзники регенерируют.",
                  "Sanctuary", "Ritual: a zone of radius 6 for 15 s. Undead burn, enemies are pushed out, allies regenerate.",
                  ["glowstone", "gold_block", "diamond", "lapis_lazuli", "lapis_lazuli", "lapis_lazuli"]),
    "judgement": ("light", 5, "Небесный суд", "Ритуал: столп света бьёт в точку прицела — 18 урона в радиусе 3 (нежити ×1,5), ослепление.",
                  "Judgement", "Ritual: a pillar of light strikes the target point — 18 damage in radius 3 (×1.5 vs undead), blindness.",
                  None),
    # --- Тьма ---
    "shadow_bolt": ("dark", 1, "Теневая стрела", "Снаряд тени: 5 урона и иссушение на 4 с.",
                    "Shadow Bolt", "A shadow projectile: 5 damage and Wither for 4 s.",
                    ["ink_sac", "bone"]),
    "drain_life": ("dark", 2, "Похищение жизни", "Поток: луч на 16 блоков, ~4 урона в секунду; 60% урона лечит мага.",
                   "Drain Life", "Channel: a 16-block beam, ~4 damage per second; 60% of it heals you.",
                   ["fermented_spider_eye", "redstone", "lapis_lazuli", "lapis_lazuli"]),
    "curse": ("dark", 2, "Проклятие", "Цель под прицелом на 15 с получает на 25% больше урона, слабеет и замедляется.",
              "Curse", "For 15 s the target takes 25% more damage, is weakened and slowed.",
              ["spider_eye", "ink_sac", "lapis_lazuli", "lapis_lazuli"]),
    "shadow_step": ("dark", 3, "Теневой шаг", "Телепорт в точку прицела (до 20 блоков) или за спину врага, 2 с невидимости.",
                    "Shadow Step", "Teleports you to the aimed spot (up to 20 blocks) or behind an enemy, 2 s of invisibility.",
                    ["ender_pearl", "ink_sac", "gold_ingot", "lapis_lazuli", "lapis_lazuli"]),
    "raise_dead": ("dark", 3, "Поднять мертвецов", "Призыв: 2 мертвеца-союзника на 60 с (при сильном накоплении — 3).",
                   "Raise Dead", "Summon: 2 undead allies for 60 s (3 when strongly overcharged).",
                   ["rotten_flesh", "rotten_flesh", "bone", "gold_ingot", "lapis_lazuli", "lapis_lazuli"]),
    "darkness": ("dark", 4, "Покров тьмы", "Облако тьмы радиуса 4 на 8 с в точке прицела: врагам слепота, тьма, иссушение и замедление.",
                 "Shroud of Darkness", "A cloud of darkness (radius 4) for 8 s at the aimed spot: enemies get blindness, darkness, wither and slowness.",
                 ["sculk", "ink_sac", "diamond", "lapis_lazuli", "lapis_lazuli", "lapis_lazuli"]),
    "soul_harvest": ("dark", 5, "Жатва душ", "Ритуал: враги в радиусе 12 получают 14 урона и иссушение II, маг лечится за каждого.",
                     "Soul Harvest", "Ritual: enemies within 12 blocks take 14 damage and Wither II, you heal for each.",
                     None),
    # --- Изменение ---
    "stoneflesh": ("alteration", 1, "Каменная кожа", "+4 брони и +2 прочности брони на 90 с (при сильном накоплении — вдвое).",
                   "Stoneflesh", "+4 armor and +2 armor toughness for 90 s (doubled when strongly overcharged).",
                   ["cobblestone", "iron_nugget"]),
    "telekinesis": ("alteration", 1, "Телекинез", "Поток: притягивает предметы и опыт по взгляду (до 24 блоков) и мелких существ.",
                    "Telekinesis", "Channel: pulls items and experience along your gaze (up to 24 blocks) and small creatures.",
                    ["string", "amethyst_shard"]),
    "featherfall": ("alteration", 2, "Пёрышко", "Плавное падение и прыгучесть на 60 с.",
                    "Featherfall", "Slow Falling and Jump Boost for 60 s.",
                    ["feather", "feather", "lapis_lazuli", "lapis_lazuli"]),
    "blink": ("alteration", 2, "Скачок", "Мгновенный рывок-телепорт на 10 блоков вперёд (не сквозь стены).",
              "Blink", "Instantly teleports you 10 blocks forward (not through walls).",
              ["ender_pearl", "sugar", "lapis_lazuli", "lapis_lazuli"]),
    "deep_breath": ("alteration", 3, "Дыхание глубин", "Подводное дыхание, грация дельфина и сила источника на 3 мин.",
                    "Deep Breath", "Water Breathing, Dolphin's Grace and Conduit Power for 3 min.",
                    ["pufferfish", "kelp", "gold_ingot", "lapis_lazuli", "lapis_lazuli"]),
    "paralyze": ("alteration", 4, "Паралич", "Снаряд: цель 5 с не двигается и не наносит урона (кроме боссов).",
                 "Paralyze", "Projectile: the target can't move or deal damage for 5 s (except bosses).",
                 ["cobweb", "slime_ball", "diamond", "lapis_lazuli", "lapis_lazuli", "lapis_lazuli"]),
    "earthquake": ("alteration", 5, "Землетрясение", "Ритуал: 3 волны в радиусе 8 — по 7 урона стоящим на земле врагам, подбрасывает и замедляет.",
                   "Earthquake", "Ritual: 3 waves in radius 8 — 7 damage each to grounded enemies, knocks them up and slows.",
                   None),
    # --- Иллюзия ---
    "night_eye": ("illusion", 1, "Ночной глаз", "Ночное зрение на 4 мин.",
                  "Night Eye", "Night Vision for 4 min.",
                  ["carrot", "glowstone_dust"]),
    "fear": ("illusion", 1, "Страх", "Враги в радиусе 4 от точки прицела 8 с бегут прочь и не нападают.",
             "Fear", "Enemies within 4 blocks of the aimed spot flee for 8 s and don't attack.",
             ["bone", "gunpowder"]),
    "calm": ("illusion", 2, "Умиротворение", "Мобы в радиусе 4 от точки прицела 20 с не нападают на игроков, пока их не ударить.",
             "Calm", "Mobs within 4 blocks of the aimed spot won't attack players for 20 s unless hit.",
             ["poppy", "wheat", "lapis_lazuli", "lapis_lazuli"]),
    "invisibility": ("illusion", 3, "Невидимость", "Невидимость на 30 с; мобы, гнавшиеся за магом, теряют его.",
                     "Invisibility", "Invisibility for 30 s; mobs chasing you lose track of you.",
                     ["fermented_spider_eye", "glass", "gold_ingot", "lapis_lazuli", "lapis_lazuli"]),
    "frenzy": ("illusion", 3, "Бешенство", "Мобы в радиусе 5 от точки прицела 15 с дерутся друг с другом.",
               "Frenzy", "Mobs within 5 blocks of the aimed spot fight each other for 15 s.",
               ["redstone", "rotten_flesh", "gold_ingot", "lapis_lazuli", "lapis_lazuli"]),
    "mirror_image": ("illusion", 4, "Двойник", "Создаёт двойника мага на 12 с — все враги рядом бросаются на него. Маг ненадолго невидим.",
                     "Mirror Image", "Creates a double of you for 12 s — nearby enemies attack it. You turn invisible briefly.",
                     ["glass", "armor_stand", "diamond", "lapis_lazuli", "lapis_lazuli", "lapis_lazuli"]),
    "mass_hysteria": ("illusion", 5, "Массовая паника", "Ритуал: все враги в радиусе 16 бегут 12 с и слабеют.",
                      "Mass Hysteria", "Ritual: all enemies within 16 blocks flee for 12 s and are weakened.",
                      None),
    # --- Колдовство ---
    "bound_blade": ("conjuration", 1, "Призрачный клинок", "Призрачный меч (как незеритовый) на 2 мин. При сильном накоплении — с Остротой V.",
                    "Bound Blade", "A spectral sword (like netherite) for 2 min. Sharpness V when strongly overcharged.",
                    ["iron_ingot", "amethyst_shard"]),
    "familiar": ("conjuration", 1, "Призрачный волк", "Призыв: волк-союзник на 90 с (при сильном накоплении — два).",
                 "Familiar", "Summon: a wolf ally for 90 s (two when strongly overcharged).",
                 ["bone", "beef"]),
    "bound_tool": ("conjuration", 2, "Призрачный инструмент", "Кирка, топор или лопата — по блоку под прицелом — с Эффективностью III на 3 мин.",
                   "Bound Tool", "A pickaxe, axe or shovel — depending on the block you look at — with Efficiency III for 3 min.",
                   ["iron_ingot", "stick", "lapis_lazuli", "lapis_lazuli"]),
    "spirit_blades": ("conjuration", 3, "Духи-клинки", "Призыв: 3 летающих духа с клинками на 30 с.",
                      "Spirit Blades", "Summon: 3 flying spirits with blades for 30 s.",
                      ["iron_sword", "amethyst_shard", "gold_ingot", "lapis_lazuli", "lapis_lazuli"]),
    "spectral_steed": ("conjuration", 3, "Призрачный конь", "Призыв: осёдланный быстрый конь на 5 мин; исчезает, если на нём никто не едет.",
                       "Spectral Steed", "Summon: a fast saddled horse for 5 min; vanishes when nobody rides it.",
                       ["leather", "hay_block", "gold_ingot", "lapis_lazuli", "lapis_lazuli"]),
    "recall": ("conjuration", 4, "Возврат", "Ритуал (3 с): перенос к точке возрождения — кровати или якорю, иначе к точке появления мира.",
               "Recall", "Ritual (3 s): teleports you to your respawn point — bed or anchor, otherwise world spawn.",
               ["compass", "ender_pearl", "diamond", "lapis_lazuli", "lapis_lazuli", "lapis_lazuli"]),
    "iron_guardian": ("conjuration", 5, "Железный страж", "Призыв: железный голем-союзник на 60 с (при сильном накоплении — два).",
                      "Iron Guardian", "Summon: an iron golem ally for 60 s (two when strongly overcharged).",
                      None),
}

TIERS = {1: ("I — Новичок", "I — Novice"), 2: ("II — Ученик", "II — Apprentice"), 3: ("III — Адепт", "III — Adept"),
         4: ("IV — Эксперт", "IV — Expert"), 5: ("V — Мастер", "V — Master")}

STYLES = {
    "bolt": ("Снаряд или удар по прицелу", "Projectile or aimed strike"),
    "channel": ("Поток: держите, пока нужно", "Channel: hold as long as needed"),
    "self": ("На себя или вокруг себя", "On yourself or around you"),
    "ritual": ("Ритуал: долгая зарядка, большая сила", "Ritual: long charge, great power"),
    "summon": ("Призыв", "Summon"),
}

LANG = {
    # (ru, en)
    "itemGroup.suchki.magic": ("Suchki: Магия", "Suchki: Magic"),
    "item.suchki.apprentice_staff": ("Посох ученика", "Apprentice Staff"),
    "item.suchki.grimoire": ("Гримуар", "Grimoire"),
    "item.suchki.archmage_staff": ("Посох архимага", "Archmage Staff"),
    "item.suchki.spectral_blade": ("Призрачный клинок", "Spectral Blade"),
    "item.suchki.spectral_pickaxe": ("Призрачная кирка", "Spectral Pickaxe"),
    "item.suchki.spectral_axe": ("Призрачный топор", "Spectral Axe"),
    "item.suchki.spectral_shovel": ("Призрачная лопата", "Spectral Shovel"),
    "item.suchki.tome.named": ("Книга заклинаний: %s", "Spell Tome: %s"),
    "block.suchki.magic_light": ("Магический свет", "Magic Light"),
    "entity.suchki.spell_projectile": ("Заклинание", "Spell"),
    "effect.suchki.stoneskin": ("Каменная кожа", "Stoneskin"),
    "effect.suchki.paralysis": ("Паралич", "Paralysis"),
    "effect.suchki.hex": ("Проклятие", "Hex"),
    "effect.suchki.undying": ("Неугасимая жизнь", "Undying"),
    "key.suchki.spell_wheel": ("Колесо заклинаний (зажать)", "Spell wheel (hold)"),
    "suchki.focus.tier": ("Выдерживает заклинания до круга %s", "Handles spells up to circle %s"),
    "suchki.focus.speed": ("Скорость каста: %s", "Casting speed: %s"),
    "suchki.focus.cap": ("Предел накопления: ×%s", "Overcharge limit: ×%s"),
    "suchki.focus.cost": ("Цена маны: %s", "Mana cost: %s"),
    "suchki.focus.staff_hint": ("Посох держат двумя руками. ПКМ — колдовать, R — выбрать заклинание.",
                                "Held in both hands. Right click — cast, R — choose a spell."),
    "suchki.focus.book_hint": ("Книга в руке, колдует свободная рука. ПКМ — колдовать, R — выбрать заклинание.",
                               "The book stays in hand, the free hand casts. Right click — cast, R — choose a spell."),
    "suchki.tome.hint": ("ПКМ — изучить (нужен Маг %s уровня)", "Right click to learn (requires Mage level %s)"),
    "suchki.spectral.hint": ("Призрачный: исчезнет со временем, его не положить в сундук",
                             "Spectral: fades with time, can't be stored in chests"),
    "suchki.spell.line": ("%s · круг %s", "%s · circle %s"),
    "suchki.spell.mana": ("Мана: %s", "Mana: %s"),
    "suchki.spell.mana_per_second": ("Мана: %s в секунду", "Mana: %s per second"),
    "suchki.spell.cooldown_left": ("Перезарядка: %s с", "Cooldown: %s s"),
    "suchki.magic.not_mage": ("Колдовать может только Маг", "Only a Mage can cast spells"),
    "suchki.magic.no_spell": ("Заклинание не выбрано — зажмите R", "No spell selected — hold R"),
    "suchki.magic.focus_too_weak": ("Этот фокус не выдерживает заклинания круга %s", "This focus can't handle circle %s spells"),
    "suchki.magic.cooldown": ("Заклинание восстанавливается: %s с", "The spell is recovering: %s s"),
    "suchki.magic.no_mana": ("Не хватает маны", "Not enough mana"),
    "suchki.magic.mana_dry": ("Мана иссякла — сила больше не растёт, отпустите", "Out of mana — power won't grow, release"),
    "suchki.magic.no_target": ("Нет цели — заклинание рассеялось", "No target — the spell dissipated"),
    "suchki.magic.starter": ("Вы стали Магом! Получен Посох ученика и три заклинания. Зажмите R, чтобы выбрать заклинание, и ПКМ посохом, чтобы колдовать. Чем дольше держите — тем сильнее.",
                             "You are a Mage now! You got an Apprentice Staff and three spells. Hold R to choose a spell and right click with the staff to cast. The longer you hold, the stronger it gets."),
    "suchki.magic.learned": ("Изучено заклинание «%s»", "Learned the spell \"%s\""),
    "suchki.magic.learn_not_mage": ("Изучать заклинания может только Маг", "Only a Mage can learn spells"),
    "suchki.magic.learn_level": ("Нужен Маг %s уровня", "Requires Mage level %s"),
    "suchki.magic.learn_known": ("Вы уже знаете это заклинание", "You already know this spell"),
    "suchki.wheel.title": ("Заклинания", "Spells"),
    "suchki.wheel.empty": ("Вы ещё не знаете заклинаний этой школы", "You don't know any spells of this school yet"),
    "suchki.wheel.none": ("Вы ещё не знаете ни одного заклинания. Изучайте книги заклинаний!", "You don't know any spells yet. Study spell tomes!"),
    "suchki.wheel.hint": ("Отпустите R, чтобы выбрать", "Release R to choose"),
    "suchki.hud.mana": ("%s / %s", "%s / %s"),
    "suchki.power": ("Сила ×%s", "Power ×%s"),
    "suchki.magic.cmd.learned": ("Изучены все заклинания: %s", "Learned all spells: %s"),
    "suchki.magic.cmd.mana": ("Мана восполнена", "Mana restored"),
    "suchki.magic.cmd.reset": ("Перезарядки сброшены, мана восполнена", "Cooldowns reset, mana restored"),
}

DEATH = {
    "spell_fire": ("%1$s сгорел в магическом огне", "%1$s сгорел в магическом огне от %2$s",
                   "%1$s was burnt by magic fire", "%1$s was burnt by %2$s's magic fire"),
    "spell_frost": ("%1$s замёрз от магии", "%1$s заморожен магией %2$s",
                    "%1$s was frozen by magic", "%1$s was frozen by %2$s's magic"),
    "spell_shock": ("%1$s поражён молнией", "%1$s поражён молнией %2$s",
                    "%1$s was struck by lightning magic", "%1$s was struck by %2$s's lightning"),
    "spell_holy": ("%1$s испепелён светом", "%1$s испепелён светом %2$s",
                   "%1$s was smitten by light", "%1$s was smitten by %2$s's light"),
    "spell_dark": ("%1$s поглощён тьмой", "%1$s поглощён тьмой %2$s",
                   "%1$s was consumed by darkness", "%1$s was consumed by %2$s's darkness"),
    "spell_arcane": ("%1$s погиб от чар", "%1$s погиб от чар %2$s",
                     "%1$s was killed by sorcery", "%1$s was killed by %2$s's sorcery"),
    "spell_earth": ("%1$s раздавлен землёй", "%1$s раздавлен землетрясением %2$s",
                    "%1$s was crushed by the earth", "%1$s was crushed by %2$s's earthquake"),
}

# Звуки: событие → список (ванильный звук, громкость, высота). Пересведение под магию.
SOUNDS = {
    "magic.charge.destruction": [("fire/fire", 0.7, 1.0)],
    "magic.charge.restoration": [("block/conduit/ambient", 0.8, 1.4)],
    "magic.charge.light": [("block/beacon/ambient", 0.8, 1.6)],
    "magic.charge.dark": [("portal/portal", 0.35, 0.6)],
    "magic.charge.alteration": [("block/respawn_anchor/ambient1", 0.8, 1.0), ("block/respawn_anchor/ambient2", 0.8, 1.0),
                                ("block/respawn_anchor/ambient3", 0.8, 1.0)],
    "magic.charge.illusion": [("block/conduit/ambient", 0.8, 0.8)],
    "magic.charge.conjuration": [("block/vault/ambient1", 0.9, 1.0), ("block/vault/ambient2", 0.9, 1.0), ("block/vault/ambient3", 0.9, 1.0)],
    "magic.ready": [("block/amethyst/resonate1", 0.9, 1.6), ("block/amethyst/resonate2", 0.9, 1.6),
                    ("block/amethyst/resonate3", 0.9, 1.6), ("block/amethyst/resonate4", 0.9, 1.6)],
    "magic.stage": [("note/icechime", 0.6, 1.0)],
    "magic.peak": [("block/beacon/power1", 0.9, 1.5), ("block/beacon/power2", 0.9, 1.5), ("block/beacon/power3", 0.9, 1.5)],
    "magic.fizzle": [("random/fizz", 0.5, 1.4), ("block/candle/extinguish1", 0.8, 0.9), ("block/candle/extinguish2", 0.8, 0.9)],
    "magic.learn": [("block/enchantment_table/enchant1", 1.0, 1.0), ("block/enchantment_table/enchant2", 1.0, 1.0),
                    ("block/enchantment_table/enchant3", 1.0, 1.0)],
    "magic.cast.fire": [("mob/ghast/fireball4", 0.8, 1.0)],
    "magic.cast.frost": [("block/powder_snow/break1", 1.0, 1.2), ("block/powder_snow/break2", 1.0, 1.2), ("random/glass1", 0.5, 1.6),
                         ("random/glass2", 0.5, 1.6)],
    "magic.cast.shock": [("item/trident/thunder1", 0.5, 1.6), ("item/trident/thunder2", 0.5, 1.6)],
    "magic.cast.holy": [("block/bell/resonate", 0.7, 1.4), ("block/beacon/activate", 0.8, 1.4)],
    "magic.cast.dark": [("mob/wither/shoot", 0.5, 1.2), ("mob/evocation_illager/cast1", 0.8, 0.8), ("mob/evocation_illager/cast2", 0.8, 0.8)],
    "magic.cast.arcane": [("block/respawn_anchor/charge1", 0.8, 1.3), ("block/respawn_anchor/charge2", 0.8, 1.3),
                          ("block/respawn_anchor/charge3", 0.8, 1.3)],
    "magic.cast.heal": [("block/amethyst/resonate1", 1.0, 1.1), ("block/amethyst/resonate2", 1.0, 1.1), ("block/beacon/power1", 0.6, 1.8)],
    "magic.cast.illusion": [("mob/illusion_illager/mirror_move1", 0.9, 1.0), ("mob/illusion_illager/mirror_move2", 0.9, 1.0),
                            ("mob/illusion_illager/prepare_blind", 0.7, 1.2)],
    "magic.cast.summon": [("mob/evocation_illager/prepare_summon", 0.9, 1.1), ("block/trial_spawner/spawn1", 0.8, 1.0),
                          ("block/trial_spawner/spawn2", 0.8, 1.0)],
    "magic.cast.teleport": [("mob/endermen/portal", 0.8, 1.0), ("mob/endermen/portal2", 0.8, 1.0)],
    "magic.cast.earth": [("random/explode1", 0.6, 0.5), ("random/explode2", 0.6, 0.5), ("dig/stone1", 1.0, 0.6)],
    "magic.impact.fire": [("random/explode1", 0.3, 1.6), ("fire/ignite", 0.9, 1.0)],
    "magic.impact.frost": [("random/glass1", 0.8, 1.3), ("random/glass2", 0.8, 1.3), ("random/glass3", 0.8, 1.3)],
    "magic.impact.shock": [("item/trident/thunder1", 0.35, 1.9), ("item/trident/thunder2", 0.35, 1.9)],
    "magic.impact.holy": [("block/bell/bell_use01", 0.6, 1.6), ("block/bell/bell_use02", 0.6, 1.6)],
    "magic.impact.dark": [("block/sculk/charge1", 1.0, 0.8), ("block/sculk/charge2", 1.0, 0.8), ("block/sculk/charge3", 1.0, 0.8)],
    "magic.impact.arcane": [("block/amethyst/break1", 1.0, 1.2), ("block/amethyst/break2", 1.0, 1.2), ("block/amethyst/break3", 1.0, 1.2)],
    "magic.explosion": [("random/explode1", 1.0, 1.0), ("random/explode2", 1.0, 1.0), ("random/explode3", 1.0, 1.0),
                        ("random/explode4", 1.0, 1.0)],
    "magic.meteor_fall": [("mob/ghast/fireball4", 1.0, 0.5)],
    "magic.pillar": [("block/beacon/activate", 1.0, 0.8), ("block/conduit/activate", 1.0, 1.2)],
    "magic.loop.flames": [("mob/blaze/breathe1", 0.8, 1.0), ("mob/blaze/breathe2", 0.8, 1.0), ("mob/blaze/breathe3", 0.8, 1.0),
                          ("mob/blaze/breathe4", 0.8, 1.0)],
    "magic.loop.beam": [("block/beacon/ambient", 0.9, 2.0)],
    "magic.loop.drain": [("ambient/nether/soulsand_valley/whisper1", 1.0, 0.8), ("ambient/nether/soulsand_valley/whisper2", 1.0, 0.8),
                         ("ambient/nether/soulsand_valley/whisper3", 1.0, 0.8)],
    "magic.loop.heal": [("block/conduit/ambient", 0.8, 1.8)],
    "magic.loop.ward": [("block/beacon/ambient", 0.8, 0.7)],
    "magic.loop.wind": [("mob/breeze/whirl", 0.7, 1.2)],
    "magic.vanish": [("mob/illusion_illager/mirror_move1", 0.7, 1.4), ("block/candle/extinguish3", 1.0, 0.8)],
    "magic.ward_hit": [("block/amethyst/break1", 1.0, 0.8), ("block/amethyst/break2", 1.0, 0.8)],
}

FOCUS_RECIPES = {
    "apprentice_staff": {"type": "minecraft:crafting_shaped", "category": "equipment",
                         "pattern": [" LA", " SL", "S  "],
                         "key": {"A": "minecraft:amethyst_shard", "L": "minecraft:lapis_lazuli", "S": "minecraft:stick"},
                         "result": {"id": "suchki:apprentice_staff", "count": 1}},
    "grimoire": {"type": "minecraft:crafting_shapeless", "category": "equipment",
                 "ingredients": ["minecraft:book", "minecraft:amethyst_shard", "minecraft:amethyst_shard", "minecraft:lapis_lazuli",
                                 "minecraft:lapis_lazuli", "minecraft:gold_ingot", "minecraft:glow_ink_sac"],
                 "result": {"id": "suchki:grimoire", "count": 1}},
    "archmage_staff": {"type": "minecraft:crafting_shaped", "category": "equipment",
                       "pattern": [" DE", " BD", "B  "],
                       "key": {"E": "minecraft:echo_shard", "D": "minecraft:diamond", "B": "minecraft:blaze_rod"},
                       "result": {"id": "suchki:archmage_staff", "count": 1}},
}

PARTICLES = {
    "spark": ["suchki:spark"],
    "mote": ["suchki:mote"],
    "rune": ["suchki:rune_circle", "suchki:rune_circle_inner"],
    "ring": ["suchki:ring"],
    "glyph": [f"suchki:glyph_{i}" for i in range(8)],
    "pillar": ["suchki:beam"],
}

DAMAGE_TYPES = {
    "spell_fire": {"effects": "burning"},
    "spell_frost": {"effects": "freezing"},
    "spell_shock": {},
    "spell_holy": {},
    "spell_dark": {},
    "spell_arcane": {},
    "spell_earth": {},
}


SOUND_DIR = ASSETS / "sounds" / "magic"
# Громкость выравниваем по RMS к этому уровню (dBFS): громкие звуки тише, тихие — как есть.
TARGET_RMS = -15.0


def own_sounds(event):
    """Свои синтезированные звуки (tools/gen_sounds.py) для события: [(имя, громкость)], или [] — тогда ванильные."""
    base = event.removeprefix("magic.").replace(".", "_")
    files = sorted(SOUND_DIR.glob(f"{base}.ogg")) + sorted(SOUND_DIR.glob(f"{base}_[0-9].ogg"))
    out = []
    for f in files:
        vol = 1.0
        try:
            import numpy as np
            import soundfile as sf
            data, _ = sf.read(str(f), dtype="float32")
            rms = 20 * np.log10(max(1e-9, float(np.sqrt(np.mean(np.square(data))))))
            vol = round(float(min(1.0, max(0.45, 10 ** ((TARGET_RMS - rms) / 20)))), 2)
        except ImportError:
            pass
        out.append((f.stem, vol))
    return out


def write(path: Path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main():
    ru_path = ASSETS / "lang" / "ru_ru.json"
    en_path = ASSETS / "lang" / "en_us.json"
    ru = json.loads(ru_path.read_text(encoding="utf-8"))
    en = json.loads(en_path.read_text(encoding="utf-8"))
    for key, (r, e) in LANG.items():
        ru[key], en[key] = r, e
    for sid, (r, e) in SCHOOLS.items():
        ru[f"suchki.school.{sid}"], en[f"suchki.school.{sid}"] = r, e
        ru[f"item.suchki.tome_{sid}"] = f"Книга заклинаний: {r}"
        en[f"item.suchki.tome_{sid}"] = f"Spell Tome: {e}"
    for t, (r, e) in TIERS.items():
        ru[f"suchki.tier.{t}"], en[f"suchki.tier.{t}"] = r, e
    for s, (r, e) in STYLES.items():
        ru[f"suchki.style.{s}"], en[f"suchki.style.{s}"] = r, e
    for did, (r0, r1, e0, e1) in DEATH.items():
        ru[f"death.attack.suchki.{did}"], ru[f"death.attack.suchki.{did}.player"] = r0, r1
        en[f"death.attack.suchki.{did}"], en[f"death.attack.suchki.{did}.player"] = e0, e1
    for sid, (school, tier, rn, rd, en_n, en_d, _) in SPELLS.items():
        ru[f"spell.suchki.{sid}"], ru[f"spell.suchki.{sid}.desc"] = rn, rd
        en[f"spell.suchki.{sid}"], en[f"spell.suchki.{sid}.desc"] = en_n, en_d
    write(ru_path, ru)
    write(en_path, en)

    # Рецепты книг (I–IV круг): книга + материалы.
    recipe_dir = DATA / "suchki" / "recipe"
    for sid, (school, tier, *_rest, ingredients) in SPELLS.items():
        path = recipe_dir / f"tome_{sid}.json"
        if ingredients is None:
            if path.exists():
                path.unlink()
            continue
        write(path, {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "group": f"suchki_tome_{school}",
            "ingredients": ["minecraft:book"] + [f"minecraft:{i}" for i in ingredients],
            "result": {"id": f"suchki:tome_{school}", "count": 1, "components": {"suchki:spell": sid}},
        })
    for name, recipe in FOCUS_RECIPES.items():
        write(recipe_dir / f"{name}.json", recipe)

    # Модели предметов.
    staff_display = {
        "thirdperson_righthand": {"rotation": [0, -90, 40], "translation": [0, 6.5, 0.5], "scale": [1.15, 1.15, 1.15]},
        "thirdperson_lefthand": {"rotation": [0, 90, -40], "translation": [0, 6.5, 0.5], "scale": [1.15, 1.15, 1.15]},
        "firstperson_righthand": {"rotation": [0, -90, 20], "translation": [1.13, 4.2, 1.13], "scale": [0.85, 0.85, 0.85]},
        "firstperson_lefthand": {"rotation": [0, 90, -20], "translation": [1.13, 4.2, 1.13], "scale": [0.85, 0.85, 0.85]},
    }
    write(ASSETS / "models" / "item" / "staff.json", {"parent": "minecraft:item/handheld", "display": staff_display})
    write(ASSETS / "models" / "item" / "book_focus.json", {"parent": "minecraft:item/generated", "display": {
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 2.6, 1.13], "scale": [0.48, 0.48, 0.48]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 2.6, 1.13], "scale": [0.48, 0.48, 0.48]},
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.6, 0.6, 0.6]},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.6, 0.6, 0.6]}}})
    items = {"apprentice_staff": "suchki:item/staff", "archmage_staff": "suchki:item/staff", "grimoire": "suchki:item/book_focus"}
    items.update({f"tome_{s}": "minecraft:item/generated" for s in SCHOOLS})
    items.update({f"spectral_{t}": "minecraft:item/handheld" for t in ("blade", "pickaxe", "axe", "shovel")})
    for name, parent in items.items():
        write(ASSETS / "models" / "item" / f"{name}.json", {"parent": parent, "textures": {"layer0": f"suchki:item/{name}"}})
        write(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"suchki:item/{name}"}})

    # Блок магического света: невидимый.
    write(ASSETS / "blockstates" / "magic_light.json", {"variants": {"": {"model": "suchki:block/magic_light"}}})
    write(ASSETS / "models" / "block" / "magic_light.json", {"textures": {"particle": "minecraft:block/glowstone"}})

    # Частицы и звуки.
    for name, textures in PARTICLES.items():
        write(ASSETS / "particles" / f"{name}.json", {"textures": textures})
    sounds = {}
    for event, entries in SOUNDS.items():
        own = own_sounds(event)
        if own:
            items = [{"name": f"suchki:magic/{name}", "volume": vol} for name, vol in own]
        else:
            items = [{"name": f"minecraft:{n}", "volume": v, "pitch": p} for n, v, p in entries]
        sounds[event] = {"subtitle": f"subtitles.suchki.{event}", "sounds": items}
    write(ASSETS / "sounds.json", sounds)
    subtitles = {
        "magic.charge": ("Магия копится", "Magic charges"), "magic.ready": ("Заклинание готово", "Spell ready"),
        "magic.stage": ("Сила растёт", "Power grows"), "magic.peak": ("Пик силы", "Peak power"),
        "magic.fizzle": ("Заклинание рассеялось", "Spell fizzles"), "magic.learn": ("Изучено заклинание", "Spell learned"),
        "magic.cast": ("Заклинание", "Spell cast"), "magic.impact": ("Удар магии", "Magic impact"),
        "magic.explosion": ("Магический взрыв", "Magic explosion"), "magic.meteor_fall": ("Падает метеор", "Meteor falls"),
        "magic.pillar": ("Столп света", "Pillar of light"), "magic.loop": ("Поток магии", "Magic flows"),
        "magic.vanish": ("Призрак исчез", "Spectral fades"), "magic.ward_hit": ("Оберег гасит удар", "Ward blocks"),
    }
    for event in SOUNDS:
        key = next(k for k in sorted(subtitles, key=len, reverse=True) if event.startswith(k))
        ru[f"subtitles.suchki.{event}"], en[f"subtitles.suchki.{event}"] = subtitles[key]
    write(ru_path, ru)
    write(en_path, en)

    # Типы урона и теги.
    for name, extra in DAMAGE_TYPES.items():
        write(DATA / "suchki" / "damage_type" / f"{name}.json",
              {"exhaustion": 0.1, "message_id": f"suchki.{name}", "scaling": "when_caused_by_living_non_player", **extra})
    all_types = [f"suchki:{n}" for n in DAMAGE_TYPES]
    write(DATA / "suchki" / "tags" / "damage_type" / "spell.json", {"values": all_types})
    write(DATA / "minecraft" / "tags" / "damage_type" / "is_fire.json", {"replace": False, "values": ["suchki:spell_fire"]})
    write(DATA / "minecraft" / "tags" / "damage_type" / "is_freezing.json", {"replace": False, "values": ["suchki:spell_frost"]})
    write(DATA / "minecraft" / "tags" / "damage_type" / "no_knockback.json", {"replace": False, "values": all_types})
    print(f"Магия: {len(SPELLS)} заклинаний, {sum(1 for s in SPELLS.values() if s[6])} рецептов книг, {len(SOUNDS)} звуков")


if __name__ == "__main__":
    main()
