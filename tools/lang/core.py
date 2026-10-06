"""Основные тексты магии: стихии, ранги, предметы, подсказки, сообщения, смерть от заклинаний, субтитры звуков.

Формат как у всех модулей tools/lang/: LANG = {"ключ": ("ru", "en")}. gen_magic.py сливает все модули
(этот — первым, остальные по алфавиту могут переопределить его ключи) и пишет assets/suchki/lang/{ru_ru,en_us}.json.
Субтитры звуков строятся по списку событий из MagicSounds.java и таблицам SUBTITLES / ELEMENT_SUBTITLES ниже.
"""

# Стихии в порядке School.java: id → (ru, en, ru в родительном падеже — «магия огня», en прилагательное).
ELEMENTS = {
    "fire": ("Огонь", "Fire", "огня", "Fire"),
    "water": ("Вода", "Water", "воды", "Water"),
    "wind": ("Ветер", "Wind", "ветра", "Wind"),
    "earth": ("Земля", "Earth", "земли", "Earth"),
    "lightning": ("Молния", "Lightning", "молнии", "Lightning"),
    "light": ("Свет", "Light", "света", "Light"),
    "dark": ("Тьма", "Darkness", "тьмы", "Dark"),
    "space": ("Пространство", "Space", "пространства", "Space"),
    "life": ("Жизнь", "Life", "жизни", "Life"),
    "mind": ("Разум", "Mind", "разума", "Mind"),
    "summon": ("Призыв", "Summoning", "призыва", "Summoning"),
    "fusion": ("Слияние", "Fusion", "слияния", "Fusion"),
}

# Ранги (как в «Реинкарнации безработного»): Spell.tier 1…7.
TIERS = {
    1: ("I — Начальный", "I — Beginner"),
    2: ("II — Средний", "II — Intermediate"),
    3: ("III — Продвинутый", "III — Advanced"),
    4: ("IV — Святой", "IV — Saint"),
    5: ("V — Королевский", "V — King"),
    6: ("VI — Императорский", "VI — Emperor"),
    7: ("VII — Божественный", "VII — God"),
}

# Ключи — CastStyle.name().toLowerCase().
STYLES = {
    "bolt": ("Снаряд или удар по прицелу", "Projectile or aimed strike"),
    "channel": ("Поток: держите, пока нужно", "Channel: hold as long as needed"),
    "self": ("На себя или вокруг себя", "On yourself or around you"),
    "ritual": ("Ритуал: долгая зарядка, огромная сила", "Ritual: long charge, enormous power"),
    "summon": ("Призыв", "Summon"),
}

# Смерть от заклинаний: тип урона → (ru, ru от игрока/моба, en, en от игрока/моба).
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
    "spell_earth": ("%1$s раздавлен землёй", "%1$s раздавлен магией земли %2$s",
                    "%1$s was crushed by the earth", "%1$s was crushed by %2$s's earth magic"),
    "spell_wind": ("%1$s разорван ветром", "%1$s разорван ветром %2$s",
                   "%1$s was torn apart by the wind", "%1$s was torn apart by %2$s's wind"),
    "spell_water": ("%1$s утонул в магической воде", "%1$s утоплен магией %2$s",
                    "%1$s drowned in magic water", "%1$s was drowned by %2$s's magic"),
    "spell_void": ("%1$s стёрт пустотой", "%1$s стёрт пустотой %2$s",
                   "%1$s was erased by the void", "%1$s was erased from existence by %2$s"),
    "spell_blood": ("%1$s истёк кровью", "%1$s обескровлен магией %2$s",
                    "%1$s bled out", "%1$s was bled dry by %2$s"),
    "spell_psychic": ("%1$s сошёл с ума", "%1$s сломлен разумом %2$s",
                      "%1$s lost their mind", "%1$s's mind was broken by %2$s"),
}

# Субтитры звуков: самый длинный подходящий префикс события. Неизвестное семейство — общий текст и предупреждение.
SUBTITLES = {
    "magic": ("Магия", "Magic"),
    "magic.ready": ("Заклинание готово", "Spell ready"),
    "magic.stage": ("Сила растёт", "Power grows"),
    "magic.peak": ("Пик силы", "Peak power"),
    "magic.fizzle": ("Заклинание рассеялось", "Spell fizzles"),
    "magic.learn": ("Изучено заклинание", "Spell learned"),
    "magic.select": ("Выбрано заклинание", "Spell selected"),
    "magic.multicast": ("Мультикаст переключён", "Multicast switched"),
    "magic.circle": ("Магический круг", "Magic circle"),
    "magic.circle.draw": ("Чертится магический круг", "Magic circle being drawn"),
    "magic.circle.complete": ("Магический круг замкнулся", "Magic circle completes"),
    "magic.circle.big": ("Возникает огромный круг", "Huge magic circle appears"),
    "magic.riser": ("Нарастает сила", "Power surges"),
    "magic.riser.long": ("Нарастает огромная сила", "Immense power surges"),
    "magic.release": ("Выпуск заклинания", "Spell released"),
    "magic.release.heavy": ("Мощный выпуск заклинания", "Heavy spell released"),
    "magic.release.ultimate": ("Высшее заклинание высвобождено", "Ultimate spell unleashed"),
    "magic.glitch": ("Искажение магии", "Magic glitches"),
    "magic.explosion": ("Магический взрыв", "Magic explosion"),
    "magic.explosion.huge": ("Колоссальный взрыв", "Colossal explosion"),
    "magic.meteor_fall": ("Падает метеор", "Meteor falls"),
    "magic.pillar": ("Столп света", "Pillar of light"),
    "magic.shockwave": ("Ударная волна", "Shockwave"),
    "magic.thunder": ("Гремит гром", "Thunder roars"),
    "magic.rumble": ("Гудит земля", "Ground rumbles"),
    "magic.freeze": ("Сковывает лёд", "Ice freezes"),
    "magic.shatter": ("Раскалывается лёд", "Ice shatters"),
    "magic.gust": ("Порыв ветра", "Wind gusts"),
    "magic.beam_blast": ("Залп луча", "Beam blasts"),
    "magic.timestop": ("Время остановилось", "Time stops"),
    "magic.domain": ("Раскрывается домен", "Domain expands"),
    "magic.teleport": ("Телепортация", "Teleport"),
    "magic.summon": ("Призыв", "Summoning"),
    "magic.vanish": ("Призрак исчез", "Spectral fades"),
    "magic.ward_hit": ("Оберег гасит удар", "Ward blocks"),
    "magic.loop": ("Поток магии", "Magic flows"),
    "magic.loop.flames": ("Ревёт пламя", "Flames roar"),
    "magic.loop.beam": ("Гудит луч", "Beam hums"),
    "magic.loop.drain": ("Утекает жизнь", "Life drains"),
    "magic.loop.heal": ("Струится исцеление", "Healing flows"),
    "magic.loop.ward": ("Гудит оберег", "Ward hums"),
    "magic.loop.wind": ("Свистит ветер", "Wind whistles"),
    "magic.loop.lightning": ("Трещит молния", "Lightning crackles"),
    "magic.loop.water": ("Бурлит вода", "Water surges"),
    "magic.loop.void": ("Гудит пустота", "Void hums"),
    "magic.loop.storm": ("Бушует буря", "Storm rages"),
    "magic.loop.chant": ("Звучит заклинание", "Incantation chanted"),
}

# Семейства по стихиям: событие «<префикс>.<стихия>» → текст с названием стихии (ru — родительный падеж).
ELEMENT_SUBTITLES = {
    "magic.charge": ("Копится магия {ru}", "{en} magic charges"),
    "magic.cast": ("Заклинание {ru}", "{en} spell cast"),
    "magic.impact": ("Удар магии {ru}", "{en} magic hits"),
}

LANG = {
    # --- Предметы, блоки, эффекты ---
    "itemGroup.suchki.magic": ("Suchki: Магия", "Suchki: Magic"),
    "item.suchki.apprentice_staff": ("Посох ученика", "Apprentice Staff"),
    "item.suchki.grimoire": ("Гримуар", "Grimoire"),
    "item.suchki.archmage_staff": ("Посох архимага", "Archmage Staff"),
    "item.suchki.primordial_staff": ("Посох первоначал", "Primordial Staff"),
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
    "key.suchki.multicast": ("Мультикаст: ×1 → ×4", "Multicast: ×1 → ×4"),

    # --- Подсказки фокусов и книг ---
    "suchki.focus.tier": ("Выдерживает заклинания до ранга %s", "Handles spells up to rank %s"),
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
    "suchki.spell.line": ("%s · ранг %s", "%s · rank %s"),
    "suchki.spell.fusion": ("Слияние: %s", "Fusion: %s"),
    "suchki.spell.destroys": ("Разрушает блоки", "Destroys blocks"),
    "suchki.spell.multicast": ("Мультикаст: до ×%s", "Multicast: up to ×%s"),
    "suchki.spell.mana": ("Мана: %s", "Mana: %s"),
    "suchki.spell.mana_per_second": ("Мана: %s в секунду", "Mana: %s per second"),
    "suchki.spell.cooldown_left": ("Перезарядка: %s с", "Cooldown: %s s"),

    # --- Сообщения ---
    "suchki.magic.not_mage": ("Колдовать может только Маг", "Only a Mage can cast spells"),
    "suchki.magic.no_spell": ("Заклинание не выбрано — зажмите R", "No spell selected — hold R"),
    "suchki.magic.focus_too_weak": ("Этот фокус не выдерживает ранг %s", "This focus can't handle rank %s"),
    "suchki.magic.cooldown": ("Заклинание восстанавливается: %s с", "The spell is recovering: %s s"),
    "suchki.magic.no_mana": ("Не хватает маны", "Not enough mana"),
    "suchki.magic.mana_dry": ("Мана иссякла — сила больше не растёт, отпустите", "Out of mana — power won't grow, release"),
    "suchki.magic.no_target": ("Нет цели — заклинание рассеялось", "No target — the spell dissipated"),
    "suchki.magic.starter": ("Вы стали Магом! Получен Посох ученика и три заклинания. Зажмите R, чтобы выбрать заклинание, "
                             "и ПКМ посохом, чтобы колдовать. Чем дольше держите — тем сильнее.",
                             "You are a Mage now! You got an Apprentice Staff and three spells. Hold R to choose a spell "
                             "and right click with the staff to cast. The longer you hold, the stronger it gets."),
    "suchki.magic.learned": ("Изучено заклинание «%s»", "Learned the spell \"%s\""),
    "suchki.magic.learn_not_mage": ("Изучать заклинания может только Маг", "Only a Mage can learn spells"),
    "suchki.magic.learn_level": ("Нужен Маг %s уровня", "Requires Mage level %s"),
    "suchki.magic.learn_known": ("Вы уже знаете это заклинание", "You already know this spell"),
    "suchki.magic.learn_elements": ("Чтобы изучить слияние, нужно владеть стихиями: %s (заклинание ранга не ниже %s)",
                                    "To learn a fusion you must master the elements: %s (a spell of rank %s or higher)"),
    "suchki.magic.cmd.learned": ("Изучены все заклинания: %s", "Learned all spells: %s"),
    "suchki.magic.cmd.mana": ("Мана восполнена", "Mana restored"),
    "suchki.magic.cmd.reset": ("Перезарядки сброшены, мана восполнена", "Cooldowns reset, mana restored"),
    "suchki.magic.cmd.foci": ("Выданы все посохи и гримуар: %s", "Given all staffs and the grimoire: %s"),

    # --- Колесо и HUD ---
    "suchki.wheel.title": ("Заклинания", "Spells"),
    "suchki.wheel.empty": ("Вы ещё не знаете заклинаний этой стихии", "You don't know any spells of this element yet"),
    "suchki.wheel.none": ("Вы ещё не знаете ни одного заклинания. Изучайте книги заклинаний!",
                          "You don't know any spells yet. Study spell tomes!"),
    "suchki.wheel.hint": ("Отпустите R, чтобы выбрать", "Release R to choose"),
    "suchki.hud.mana": ("%s / %s", "%s / %s"),
    "suchki.power": ("Сила ×%s", "Power ×%s"),
    # Мультикаст (клавиша G): подсказка над хотбаром. tools/lang/hud.py может переопределить.
    "suchki.multicast.toast": ("Мультикаст ×%s", "Multicast ×%s"),
    "suchki.multicast.locked": ("Откроется на %s уровне Мага", "Unlocks at Mage level %s"),
    "suchki.multicast.spell_limit": ("«%s» — не больше ×%s", "\"%s\" allows at most ×%s"),
}

for _sid, (_ru, _en, _gen, _adj) in ELEMENTS.items():
    LANG[f"suchki.school.{_sid}"] = (_ru, _en)
    LANG[f"item.suchki.tome_{_sid}"] = (f"Книга заклинаний: {_ru}", f"Spell Tome: {_en}")
for _t, _names in TIERS.items():
    LANG[f"suchki.tier.{_t}"] = _names
for _s, _names in STYLES.items():
    LANG[f"suchki.style.{_s}"] = _names
for _did, (_r0, _r1, _e0, _e1) in DEATH.items():
    LANG[f"death.attack.suchki.{_did}"] = (_r0, _e0)
    LANG[f"death.attack.suchki.{_did}.player"] = (_r1, _e1)
