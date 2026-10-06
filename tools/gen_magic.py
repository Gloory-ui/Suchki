#!/usr/bin/env python3
"""Генератор ресурсов магии Suchki.

Запуск из корня мода (лучше питоном с numpy и soundfile — иначе громкость звуков не выравнивается):
    python tools/gen_magic.py           — записать ресурсы
    python tools/gen_magic.py --check   — только проверить данные и показать предупреждения (ничего не пишет)

Источники:
- Java (источник правды): стихии — magic/School.java, звуковые события — magic/MagicSounds.java, виды урона —
  magic/MagicDamage.java, заклинания — Spell.of(...) в magic/spells/*.java (только для сверки);
- тексты заклинаний — tools/spelldata/<стихия>.py (формат — в tools/spelldata/__init__.py);
- тексты интерфейса — tools/lang/*.py: LANG = {"ключ": ("ru", "en")}; core.py — основа, остальные модули
  (по алфавиту) дописывают и могут переопределять его ключи;
- звуки — файлы assets/suchki/sounds/magic/*.ogg (их пишет tools/gen_sounds.py): событие «magic.cast.fire» ↔
  cast_fire.ogg или cast_fire_1…N.ogg; нет файла — ванильная замена из FALLBACK и предупреждение.

Пишет: переводы ru_ru/en_us (ключи магии обновляет, устаревшие убирает, остальные ключи мода не трогает; порядок
и формат файла сохраняются), рецепты книг и фокусов, модели и описания предметов, блок света, частицы, sounds.json,
типы урона и теги. Устаревшие файлы (рецепты книг, модели старых школ, типы урона) удаляет. Всё детерминировано.
"""
import argparse
import importlib.util
import json
import re
import sys
import zipfile
from pathlib import Path

sys.dont_write_bytecode = True          # модули данных грузятся из tools/ — без __pycache__ в репозитории

TOOLS = Path(__file__).resolve().parent
REPO = TOOLS.parent
RES = REPO / "src" / "main" / "resources"
ASSETS = RES / "assets" / "suchki"
DATA = RES / "data"
JAVA = REPO / "src" / "main" / "java" / "com" / "suchki"
MAGIC_JAVA = JAVA / "magic"
SOUND_DIR = ASSETS / "sounds" / "magic"
LANG_DIR = ASSETS / "lang"

MAX_RANK = 7
CRAFT_MAX_RANK = 4          # книги рангов 1–4 могут крафтиться, 5–7 — только находки
WARNINGS = []


def warn(msg):
    WARNINGS.append(msg)


class DataError(Exception):
    pass


# ---------------------------------------------------------------- чтение Java

def read(path):
    return path.read_text(encoding="utf-8")


def strip_comments(src):
    """Убрать комментарии Java (строки не трогаем: в них нет «//» и «/*» у нас)."""
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    return re.sub(r"//[^\n]*", "", src)


def java_elements():
    """Стихии из School.java: id → (цвет, светлый цвет) в порядке перечисления."""
    out = {}
    for m in re.finditer(r'^\s*[A-Z_]+\("([a-z_]+)",\s*0x([0-9A-Fa-f]{6}),\s*0x([0-9A-Fa-f]{6})\)',
                         strip_comments(read(MAGIC_JAVA / "School.java")), re.M):
        out[m.group(1)] = ("#" + m.group(2).upper(), "#" + m.group(3).upper())
    if not out:
        raise DataError("не нашёл стихий в School.java")
    return out


def java_styles():
    src = strip_comments(read(MAGIC_JAVA / "CastStyle.java"))
    body = src[src.index("{") + 1:]
    body = body[:body.index(";")]
    return [s.strip().lower() for s in body.split(",") if s.strip()]


def java_sound_events(elements):
    """События из MagicSounds.java: register("magic.x") и register("magic.charge." + s.id) — для каждой стихии."""
    src = strip_comments(read(MAGIC_JAVA / "MagicSounds.java"))
    events = []
    for m in re.finditer(r'register\(\s*"([a-z0-9_.]+)"\s*(\+\s*[A-Za-z_][\w.]*\s*)?\)', src):
        names = [m.group(1) + e for e in elements] if m.group(2) else [m.group(1)]
        for n in names:
            if n not in events:
                events.append(n)
    if not events:
        raise DataError("не нашёл звуковых событий в MagicSounds.java")
    return events


def java_damage_types():
    src = strip_comments(read(MAGIC_JAVA / "MagicDamage.java"))
    return [m.group(1) for m in re.finditer(r'^\s*[A-Z_]+\("(spell_[a-z_]+)"\)', src, re.M)]


def java_spells(elements):
    """Заклинания из magic/spells/*.java: id → (стихия, ранг). Только для сверки с текстами."""
    out = {}
    up = {e.upper(): e for e in elements}
    for f in sorted((MAGIC_JAVA / "spells").glob("*.java")):
        src = strip_comments(read(f))
        for m in re.finditer(r'\bof\(\s*"([a-z0-9_]+)"\s*,\s*(?:School\.)?([A-Z_]+)\s*,\s*(\d+)\s*,', src):
            if m.group(2) in up:
                out[m.group(1)] = (up[m.group(2)], int(m.group(3)), f.name)
    return out


def java_registered(file, kind):
    """Имена из register("name", ...) в файле магии (предметы, эффекты, блоки)."""
    path = MAGIC_JAVA / file
    if not path.exists():
        return []
    return [f"{kind}.suchki.{m.group(1)}"
            for m in re.finditer(r'\bregister\(\s*"([a-z0-9_]+)"\s*,', strip_comments(read(path)))]


def first_arg(src, i):
    """Первый аргумент вызова, начиная с позиции после «(»: до запятой или «)» на нулевой глубине."""
    depth, j = 0, i
    while j < len(src):
        ch = src[j]
        if ch == '"':
            j += 1
            while j < len(src) and src[j] != '"':
                j += 2 if src[j] == "\\" else 1
        elif ch in "([{":
            depth += 1
        elif ch in ")]}":
            if depth == 0:
                break
            depth -= 1
        elif ch == "," and depth == 0:
            break
        j += 1
    return src[i:j].strip()


def java_lang_refs():
    """Ключи переводов из кода: точные (translatable("x"), тернарники внутри) и префиксы (translatable("x." + …))."""
    keys, prefixes = {}, {}
    for f in sorted(JAVA.rglob("*.java")):
        src = strip_comments(read(f))
        rel = f.relative_to(JAVA).as_posix()
        for m in re.finditer(r'translatable(?:WithFallback)?\(', src):
            arg = first_arg(src, m.end())
            lits = re.findall(r'"((?:[^"\\]|\\.)*)"', arg)
            if not lits:
                continue
            if re.fullmatch(r'"(?:[^"\\]|\\.)*"\s*\+.*', arg, re.S):
                prefixes.setdefault(lits[0], rel)          # "spell.suchki." + id + ".desc" → префикс
            elif "+" not in arg:
                for lit in lits:                              # "x" или cond ? "x" : "y"
                    keys.setdefault(lit, rel)
        for m in re.finditer(r'"(key\.suchki\.[a-z0-9_.]+)"', src):
            keys.setdefault(m.group(1), rel)
    return keys, prefixes


# ---------------------------------------------------------------- ванильные справочники (для проверки)

def mc_version():
    for line in read(REPO / "gradle.properties").splitlines():
        if line.startswith("minecraft_version="):
            return line.split("=", 1)[1].strip()
    return None


def vanilla_items():
    """Ванильные предметы — из клиента Minecraft в кэше Gradle (assets/minecraft/items/*.json); None — нет клиента."""
    ver = mc_version()
    for jar in sorted(Path.home().glob(f".gradle/caches/fabric-loom/{ver}/minecraft-client.jar")):
        with zipfile.ZipFile(jar) as z:
            names = {n[len("assets/minecraft/items/"):-5] for n in z.namelist()
                     if n.startswith("assets/minecraft/items/") and n.endswith(".json")}
        if names:
            return names
    return None


def vanilla_sounds():
    """Ванильные звуковые файлы (minecraft/sounds/<путь>.ogg) — из индекса ассетов в кэше Gradle; None — нет индекса."""
    ver = mc_version()
    for idx in sorted(Path.home().glob(f".gradle/caches/fabric-loom/assets/indexes/{ver}*.json")):
        objs = json.loads(read(idx)).get("objects", {})
        return {k[len("minecraft/sounds/"):-4] for k in objs if k.startswith("minecraft/sounds/") and k.endswith(".ogg")}
    return None


# ---------------------------------------------------------------- модули данных

def load_module(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


SPELL_FIELDS = {"rank", "ru", "ru_desc", "en", "en_desc", "chant_ru", "chant_en", "ingredients"}


def chant_lines(text):
    return [s for s in (text or "").split("|") if s.strip()]


def load_spells(elements, items):
    """Тексты из tools/spelldata/<стихия>.py → [(id, стихия, данные)] по порядку стихий, внутри — по рангу."""
    out, seen = [], {}
    for el in elements:
        path = TOOLS / "spelldata" / f"{el}.py"
        if not path.exists():
            warn(f"нет модуля tools/spelldata/{el}.py")
            continue
        spells = getattr(load_module(path, f"spelldata_{el}"), "SPELLS", None)
        if not isinstance(spells, dict):
            raise DataError(f"{path.name}: нет словаря SPELLS")
        rows = []
        for sid, d in spells.items():
            where = f"spelldata/{el}.py: {sid}"
            if not re.fullmatch(r"[a-z0-9_]+", sid):
                raise DataError(f"{where}: id только из a-z, 0-9 и _")
            if sid in seen:
                raise DataError(f"{where}: такой id уже есть в {seen[sid]}.py")
            seen[sid] = el
            if not isinstance(d, dict):
                raise DataError(f"{where}: ожидался словарь")
            extra = set(d) - SPELL_FIELDS
            if extra:
                warn(f"{where}: лишние поля {sorted(extra)} (опечатка?)")
            rank = d.get("rank")
            if not isinstance(rank, int) or not 1 <= rank <= MAX_RANK:
                raise DataError(f"{where}: rank должен быть 1…{MAX_RANK}, а не {rank!r}")
            for f in ("ru", "ru_desc", "en", "en_desc"):
                if not isinstance(d.get(f), str) or not d[f].strip():
                    raise DataError(f"{where}: пустое поле {f}")
            ing = d.get("ingredients")
            if ing is not None:
                if rank > CRAFT_MAX_RANK:
                    raise DataError(f"{where}: ранг {rank} — только находки, ingredients должно быть None")
                if not isinstance(ing, (list, tuple)) or not 1 <= len(ing) <= 8:
                    raise DataError(f"{where}: ingredients — список из 1…8 предметов или None")
                ing = [i.removeprefix("minecraft:") for i in ing]
                if items is not None:
                    bad = [i for i in ing if i not in items]
                    if bad:
                        raise DataError(f"{where}: нет таких ванильных предметов: {bad}")
            elif rank <= 3:
                warn(f"{where}: ранг {rank} без рецепта книги (самопроверка ждёт рецепт у рангов 1–3)")
            cru, cen = chant_lines(d.get("chant_ru")), chant_lines(d.get("chant_en"))
            if rank >= 2 and (not cru or not cen):
                warn(f"{where}: ранг {rank} без фразы (chant_ru/chant_en)")
            elif len(cru) != len(cen):
                warn(f"{where}: в фразе ru {len(cru)} строк, en — {len(cen)}")
            if rank >= 3 and cru and len(cru) < min(4, rank - 1):
                warn(f"{where}: фраза ранга {rank} короче {min(4, rank - 1)} строк")
            rows.append((sid, el, {**d, "ingredients": ing}))
        rows.sort(key=lambda r: r[2]["rank"])           # как Spells.all(): стихия, потом ранг (устойчиво)
        out.extend(rows)
    return out


def load_lang():
    """Все модули tools/lang/*.py: core первым, остальные по алфавиту (могут переопределять core)."""
    paths = sorted((TOOLS / "lang").glob("*.py"), key=lambda p: (p.stem != "core", p.name))
    paths = [p for p in paths if not p.name.startswith("_")]
    merged, owner = {}, {}
    core = None
    for p in paths:
        mod = load_module(p, f"lang_{p.stem}")
        if p.stem == "core":
            core = mod
        lang = getattr(mod, "LANG", None)
        if not isinstance(lang, dict):
            warn(f"tools/lang/{p.name}: нет словаря LANG — пропущен")
            continue
        for k, v in lang.items():
            if not (isinstance(v, tuple) and len(v) == 2 and all(isinstance(s, str) for s in v)):
                raise DataError(f"tools/lang/{p.name}: {k} — ожидалась пара (ru, en)")
            if k in merged and merged[k] != v and owner[k] != "core":
                warn(f"ключ {k}: tools/lang/{p.name} переопределяет {owner[k]}.py")
            merged[k], owner[k] = v, p.stem
    if core is None:
        raise DataError("нет tools/lang/core.py")
    return merged, core


# ---------------------------------------------------------------- таблицы

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
    # Конец игры: посох архимага по диагонали (как посох лежит в сетке), навершие — звезда Незера, рядом осколки
    # эха и драконье дыхание, внизу — тяжёлое ядро (испытания). Трофеи визера, дракона, древнего города и испытаний.
    "primordial_staff": {"type": "minecraft:crafting_shaped", "category": "equipment",
                         "pattern": [" EN", "DAE", "HD "],
                         "key": {"N": "minecraft:nether_star", "E": "minecraft:echo_shard", "D": "minecraft:dragon_breath",
                                 "A": "suchki:archmage_staff", "H": "minecraft:heavy_core"},
                         "result": {"id": "suchki:primordial_staff", "count": 1}},
}
STAFFS = ("apprentice_staff", "archmage_staff", "primordial_staff")

PARTICLES = {
    "spark": ["suchki:spark"],
    "mote": ["suchki:mote"],
    "rune": ["suchki:rune_circle", "suchki:rune_circle_inner"],
    "ring": ["suchki:ring"],
    "glyph": [f"suchki:glyph_{i}" for i in range(8)],
    "pillar": ["suchki:beam"],
}

# Особые поля типов урона (остальное общее). Огонь горит, холод замораживает, вода — звук утопления.
DAMAGE_EXTRA = {
    "spell_fire": {"effects": "burning"},
    "spell_frost": {"effects": "freezing"},
    "spell_water": {"effects": "drowning"},
}

# Ванильные звуки на случай, если своего файла нет: событие → [(путь, громкость, высота)].
FALLBACK = {
    "magic.ready": [("block/amethyst/resonate1", 0.9, 1.6), ("block/amethyst/resonate2", 0.9, 1.6)],
    "magic.stage": [("note/icechime", 0.6, 1.0)],
    "magic.peak": [("block/beacon/power1", 0.9, 1.5), ("block/beacon/power2", 0.9, 1.5), ("block/beacon/power3", 0.9, 1.5)],
    "magic.fizzle": [("random/fizz", 0.5, 1.4), ("block/candle/extinguish1", 0.8, 0.9), ("block/candle/extinguish2", 0.8, 0.9)],
    "magic.learn": [("block/enchantment_table/enchant1", 1.0, 1.0), ("block/enchantment_table/enchant2", 1.0, 1.0),
                    ("block/enchantment_table/enchant3", 1.0, 1.0)],
    "magic.select": [("block/amethyst/shimmer", 0.8, 1.4)],
    "magic.multicast": [("block/respawn_anchor/charge1", 0.6, 1.7), ("block/respawn_anchor/charge2", 0.6, 1.7)],
    "magic.circle.draw": [("block/amethyst_cluster/place1", 0.5, 1.8), ("block/amethyst_cluster/place2", 0.5, 1.8),
                          ("block/amethyst_cluster/place3", 0.5, 1.8), ("block/amethyst_cluster/place4", 0.5, 1.8)],
    "magic.circle.complete": [("block/beacon/activate", 0.8, 1.6)],
    "magic.circle.big": [("block/conduit/activate", 1.0, 0.8)],
    "magic.riser.short": [("portal/trigger", 0.7, 1.7)],
    "magic.riser.long": [("portal/trigger", 0.9, 1.0)],
    "magic.release.heavy": [("mob/warden/sonic_boom1", 0.7, 1.4), ("mob/warden/sonic_boom2", 0.7, 1.4)],
    "magic.release.ultimate": [("mob/warden/sonic_boom3", 1.0, 0.8), ("mob/warden/sonic_boom4", 1.0, 0.8)],
    "magic.glitch": [("block/sculk_sensor/sculk_clicking1", 0.7, 1.8), ("block/sculk_sensor/sculk_clicking2", 0.7, 1.8)],
    "magic.explosion": [("random/explode1", 1.0, 1.0), ("random/explode2", 1.0, 1.0), ("random/explode3", 1.0, 1.0),
                        ("random/explode4", 1.0, 1.0)],
    "magic.explosion.huge": [("random/explode1", 1.0, 0.6), ("random/explode2", 1.0, 0.6), ("random/explode3", 1.0, 0.6)],
    "magic.meteor_fall": [("mob/ghast/fireball4", 1.0, 0.5)],
    "magic.pillar": [("block/beacon/activate", 1.0, 0.8), ("block/conduit/activate", 1.0, 1.2)],
    "magic.shockwave": [("item/mace/smash_ground_heavy", 1.0, 0.8)],
    "magic.thunder": [("ambient/weather/thunder1", 1.0, 1.0), ("ambient/weather/thunder2", 1.0, 1.0),
                      ("ambient/weather/thunder3", 1.0, 1.0)],
    "magic.rumble": [("ambient/weather/thunder2", 0.7, 0.5), ("ambient/weather/thunder3", 0.7, 0.5)],
    "magic.freeze": [("block/powder_snow/break1", 1.0, 0.8), ("random/glass1", 0.6, 1.4)],
    "magic.shatter": [("random/glass1", 1.0, 0.8), ("random/glass2", 1.0, 0.8), ("random/glass3", 1.0, 0.8)],
    "magic.gust": [("entity/wind_charge/wind_burst1", 1.0, 1.0), ("entity/wind_charge/wind_burst2", 1.0, 1.0),
                   ("entity/wind_charge/wind_burst3", 1.0, 1.0)],
    "magic.beam_blast": [("mob/warden/sonic_boom1", 1.0, 0.9), ("mob/warden/sonic_boom2", 1.0, 0.9)],
    "magic.timestop": [("block/bell/resonate", 1.0, 0.5), ("block/respawn_anchor/deplete1", 1.0, 0.6)],
    "magic.domain": [("block/end_portal/endportal", 1.0, 0.8)],
    "magic.teleport": [("mob/endermen/portal", 0.8, 1.0), ("mob/endermen/portal2", 0.8, 1.0)],
    "magic.summon": [("mob/evocation_illager/prepare_summon", 0.9, 1.1), ("block/trial_spawner/spawn1", 0.8, 1.0)],
    "magic.vanish": [("mob/illusion_illager/mirror_move1", 0.7, 1.4), ("block/candle/extinguish3", 1.0, 0.8)],
    "magic.ward_hit": [("block/amethyst/break1", 1.0, 0.8), ("block/amethyst/break2", 1.0, 0.8)],
    "magic.loop.flames": [("mob/blaze/breathe1", 0.8, 1.0), ("mob/blaze/breathe2", 0.8, 1.0), ("mob/blaze/breathe3", 0.8, 1.0)],
    "magic.loop.beam": [("block/beacon/ambient", 0.9, 2.0)],
    "magic.loop.drain": [("ambient/nether/soulsand_valley/whisper1", 1.0, 0.8),
                         ("ambient/nether/soulsand_valley/whisper2", 1.0, 0.8)],
    "magic.loop.heal": [("block/conduit/ambient", 0.8, 1.8)],
    "magic.loop.ward": [("block/beacon/ambient", 0.8, 0.7)],
    "magic.loop.wind": [("mob/breeze/whirl", 0.7, 1.2)],
    "magic.loop.lightning": [("block/beacon/ambient", 0.9, 2.0)],
    "magic.loop.water": [("liquid/water", 1.0, 1.0)],
    "magic.loop.void": [("portal/portal", 0.4, 0.5)],
    "magic.loop.storm": [("ambient/weather/rain1", 0.8, 0.8), ("ambient/weather/rain2", 0.8, 0.8)],
    "magic.loop.chant": [("ambient/nether/soulsand_valley/voices1", 0.6, 1.3)],
}
# Стихии: гул зарядки, выпуск, попадание.
ELEMENT_FALLBACK = {
    "fire": ([("fire/fire", 0.7, 1.0)], [("mob/ghast/fireball4", 0.8, 1.0)],
             [("random/explode1", 0.3, 1.6), ("fire/ignite", 0.9, 1.0)]),
    "water": ([("liquid/water", 0.8, 0.8)], [("liquid/splash", 0.8, 1.2), ("liquid/splash2", 0.8, 1.2)],
              [("random/splash", 0.8, 1.3), ("random/glass1", 0.5, 1.6)]),
    "wind": ([("item/elytra/elytra_loop", 0.5, 1.3)], [("mob/breeze/wind_burst1", 0.9, 1.1), ("mob/breeze/wind_burst2", 0.9, 1.1)],
             [("entity/wind_charge/wind_burst1", 0.8, 1.2), ("entity/wind_charge/wind_burst2", 0.8, 1.2)]),
    "earth": ([("block/respawn_anchor/ambient1", 0.8, 0.7), ("block/respawn_anchor/ambient2", 0.8, 0.7)],
              [("random/explode1", 0.6, 0.5), ("dig/stone1", 1.0, 0.6)],
              [("dig/stone2", 1.0, 0.7), ("dig/stone3", 1.0, 0.7), ("item/mace/smash_ground1", 0.8, 0.8)]),
    "lightning": ([("block/beacon/ambient", 0.8, 2.0)], [("item/trident/thunder1", 0.5, 1.6), ("item/trident/thunder2", 0.5, 1.6)],
                  [("item/trident/thunder1", 0.35, 1.9), ("item/trident/thunder2", 0.35, 1.9)]),
    "light": ([("block/beacon/ambient", 0.8, 1.6)], [("block/bell/resonate", 0.7, 1.4), ("block/beacon/activate", 0.8, 1.4)],
              [("block/bell/bell_use01", 0.6, 1.6), ("block/bell/bell_use02", 0.6, 1.6)]),
    "dark": ([("portal/portal", 0.35, 0.6)], [("mob/wither/shoot", 0.5, 1.2), ("mob/evocation_illager/cast1", 0.8, 0.8)],
             [("block/sculk/charge1", 1.0, 0.8), ("block/sculk/charge2", 1.0, 0.8), ("block/sculk/charge3", 1.0, 0.8)]),
    "space": ([("block/conduit/ambient", 0.8, 0.6)], [("mob/endermen/portal", 0.8, 0.8), ("mob/endermen/portal2", 0.8, 0.8)],
              [("block/respawn_anchor/deplete1", 0.8, 1.4), ("block/respawn_anchor/deplete2", 0.8, 1.4)]),
    "life": ([("block/conduit/ambient", 0.8, 1.4)], [("block/amethyst/resonate1", 1.0, 1.1), ("block/beacon/power1", 0.6, 1.8)],
             [("block/amethyst/resonate2", 0.9, 1.6), ("block/amethyst/resonate3", 0.9, 1.6)]),
    "mind": ([("block/conduit/ambient", 0.8, 0.8)],
             [("mob/illusion_illager/mirror_move1", 0.9, 1.0), ("mob/illusion_illager/mirror_move2", 0.9, 1.0)],
             [("mob/illusion_illager/prepare_blind", 0.7, 1.3)]),
    "summon": ([("block/vault/ambient1", 0.9, 1.0), ("block/vault/ambient2", 0.9, 1.0)],
               [("mob/evocation_illager/prepare_summon", 0.9, 1.1), ("block/trial_spawner/spawn1", 0.8, 1.0)],
               [("block/amethyst/break1", 1.0, 1.2), ("block/amethyst/break2", 1.0, 1.2)]),
    "fusion": ([("block/beacon/ambient", 0.8, 1.0)],
               [("block/respawn_anchor/charge1", 0.8, 1.3), ("block/respawn_anchor/charge2", 0.8, 1.3)],
               [("block/amethyst/break3", 1.0, 1.0), ("block/amethyst/break4", 1.0, 1.0)]),
}
for _el, (_charge, _cast, _impact) in ELEMENT_FALLBACK.items():
    FALLBACK[f"magic.charge.{_el}"] = _charge
    FALLBACK[f"magic.cast.{_el}"] = _cast
    FALLBACK[f"magic.impact.{_el}"] = _impact

# Большие звуки слышны издалека: attenuation_distance в sounds.json (по умолчанию 16). На клиенте дальность =
# max(громкость, 1) × attenuation_distance, сервер же рассылает звук на 16 × громкость блоков (MagicFx.soundFar).
FAR = {
    "magic.explosion.huge": 128, "magic.release.ultimate": 128, "magic.riser.long": 128, "magic.domain": 128,
    "magic.timestop": 128,
    "magic.explosion": 96, "magic.beam_blast": 96, "magic.meteor_fall": 96, "magic.thunder": 96, "magic.pillar": 96,
    "magic.rumble": 96, "magic.circle.big": 96, "magic.shockwave": 96,
}
# Сведение: громкость своих звуков выравнивается по самому громкому месту (RMS в окне 0,4 с) к уровню семейства, дБ.
# Ориентиры ванили так же: взрыв ≈ −8,5, выстрел гаста ≈ −13, заклинание вызывателя ≈ −15.
MIX_TARGET = {"default": -13.0, "big": -9.0, "cue": -18.0}
CUES = ("magic.select", "magic.multicast", "magic.circle.draw", "magic.stage", "magic.glitch")
LOUD = ("magic.release.heavy",)          # громкие, но слышные только рядом


def longest_prefix(event, table):
    best = None
    for k in table:
        if (event == k or event.startswith(k + ".")) and (best is None or len(k) > len(best)):
            best = k
    return best


def own_sounds(event, sf_ok):
    """Свои файлы для события: [(имя файла без .ogg, громкость)]; [] — своих нет."""
    base = event.removeprefix("magic.").replace(".", "_")
    files = []
    single = SOUND_DIR / f"{base}.ogg"
    if single.exists():
        files.append((0, single))
    for f in SOUND_DIR.glob(f"{base}_*.ogg"):
        m = re.fullmatch(re.escape(base) + r"_(\d+)", f.stem)
        if m:
            files.append((int(m.group(1)), f))
    files.sort()
    group = "big" if event in FAR or event in LOUD else "cue" if event in CUES else "default"
    out = []
    for _, f in files:
        vol = 1.0
        if sf_ok:
            vol = normalized_volume(f, MIX_TARGET[group])
        out.append((f.stem, vol))
    return out


def normalized_volume(path, target_db):
    import numpy as np
    import soundfile as sf
    data, sr = sf.read(str(path), dtype="float32", always_2d=True)
    x = data.mean(axis=1)
    win, hop = max(1, int(0.4 * sr)), max(1, int(0.1 * sr))
    if len(x) <= win:
        loud = float(np.sqrt(np.mean(np.square(x))))
    else:
        sq = np.concatenate([[0.0], np.cumsum(np.square(x, dtype=np.float64))])
        starts = np.arange(0, len(x) - win + 1, hop)
        loud = float(np.sqrt(np.max((sq[starts + win] - sq[starts]) / win)))
    db = 20 * np.log10(max(1e-9, loud))
    return round(float(min(1.0, max(0.35, 10 ** ((target_db - db) / 20)))), 2)


def subtitle(event, core, elements):
    """Текст субтитра (ru, en): семейство стихии, иначе самый длинный префикс; неизвестное — общий и предупреждение."""
    for fam, (ru, en) in core.ELEMENT_SUBTITLES.items():
        el = event[len(fam) + 1:] if event.startswith(fam + ".") else None
        if el in elements:
            _, _, gen, adj = core.ELEMENTS[el]
            return ru.format(ru=gen), en.format(en=adj)
    key = longest_prefix(event, core.SUBTITLES)
    if key is None or key == "magic":
        warn(f"звук {event}: нет субтитра для семейства — общий «{core.SUBTITLES['magic'][0]}» (добавьте в tools/lang/core.py)")
        return core.SUBTITLES["magic"]
    return core.SUBTITLES[key]


# ---------------------------------------------------------------- запись

def write(path, obj, written):
    """JSON с отступом 2 и CRLF — как остальные файлы ресурсов мода."""
    text = json.dumps(obj, indent=2, ensure_ascii=False) + "\n"
    written[path] = text


def flush(written, removed):
    for path, text in written.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        data = text.replace("\n", "\r\n").encode("utf-8")
        if not path.exists() or path.read_bytes() != data:
            path.write_bytes(data)
    for path in removed:
        if path.exists():
            path.unlink()


# Ключи магии в файлах перевода: такие ключи генератор ведёт сам (обновляет, а лишние — убирает).
MANAGED = ("spell.suchki.", "suchki.school.", "item.suchki.tome_", "suchki.tier.", "subtitles.suchki.magic.",
           "death.attack.suchki.spell_", "suchki.style.", "suchki.spell.", "suchki.magic.", "suchki.focus.",
           "suchki.wheel.", "suchki.hud.", "suchki.tome.", "suchki.power", "suchki.spectral.", "suchki.chant.",
           "suchki.multicast.", "suchki.circle.", "suchki.cast.")


def merge_lang(path, gen, java_keys, lang_idx):
    """Слить ключи в файл перевода: старые ключи на своих местах, новые — в конец, устаревшие ключи магии убрать."""
    old = json.loads(read(path)) if path.exists() else {}
    out, removed = {}, []
    for k, v in old.items():
        if k in gen:
            out[k] = gen[k][lang_idx]
        elif k.startswith(MANAGED) and k not in java_keys:
            removed.append(k)
        else:
            out[k] = v
    added = [k for k in gen if k not in out]
    for k in added:
        out[k] = gen[k][lang_idx]
    return out, removed, added


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--check", action="store_true", help="только проверить, ничего не писать")
    args = ap.parse_args()

    elements = java_elements()
    styles = java_styles()
    events = java_sound_events(elements)
    damage = java_damage_types()
    jspells = java_spells(elements)
    items = vanilla_items()
    if items is None:
        warn("нет клиента Minecraft в кэше Gradle — ингредиенты рецептов не проверены")
    vsounds = vanilla_sounds()
    if vsounds is None:
        warn("нет индекса ассетов Minecraft в кэше Gradle — ванильные звуки-замены не проверены")
    try:
        import numpy  # noqa: F401
        import soundfile  # noqa: F401
        sf_ok = True
    except ImportError:
        sf_ok = False
        warn("нет numpy/soundfile — громкость своих звуков не выровнена (1.0); запускайте питоном из venv")

    spells = load_spells(elements, items)
    lang, core = load_lang()
    if list(core.ELEMENTS) != list(elements):
        raise DataError(f"стихии в tools/lang/core.py {list(core.ELEMENTS)} не совпадают с School.java {list(elements)}")
    if set(core.STYLES) != set(styles):
        raise DataError(f"стили в tools/lang/core.py {sorted(core.STYLES)} не совпадают с CastStyle.java {sorted(styles)}")
    if set(core.DEATH) != set(damage):
        warn(f"смерть от урона: в core.py {sorted(set(core.DEATH) ^ set(damage))} не совпадает с MagicDamage.java")
    gen = dict(lang)

    # Сверка с Java.
    texts = {sid: (el, d["rank"]) for sid, el, d in spells}
    for sid, (el, rank, file) in sorted(jspells.items()):
        if sid not in texts:
            warn(f"заклинание {sid} ({file}) без текстов в tools/spelldata/{el}.py")
        elif texts[sid] != (el, rank):
            warn(f"заклинание {sid}: в Java {el}/ранг {rank}, в spelldata {texts[sid][0]}/ранг {texts[sid][1]}")
    for sid, (el, _) in texts.items():
        if jspells and sid not in jspells:
            warn(f"тексты {sid} (spelldata/{el}.py) есть, а заклинания в Java нет")

    # Тексты заклинаний.
    for sid, el, d in spells:
        gen[f"spell.suchki.{sid}"] = (d["ru"], d["en"])
        gen[f"spell.suchki.{sid}.desc"] = (d["ru_desc"], d["en_desc"])
        cru, cen = chant_lines(d.get("chant_ru")), chant_lines(d.get("chant_en"))
        if cru and cen:
            gen[f"spell.suchki.{sid}.chant"] = ("|".join(s.strip() for s in cru), "|".join(s.strip() for s in cen))

    # Звуки.
    sounds, fallback_used, missing_fallback = {}, [], []
    for event in events:
        ru, en = subtitle(event, core, elements)
        gen[f"subtitles.suchki.{event}"] = (ru, en)
        att = FAR.get(event)
        own = own_sounds(event, sf_ok)
        if own:
            entries = [{"name": f"suchki:magic/{name}", "volume": vol} for name, vol in own]
        else:
            fb = FALLBACK.get(event)
            if fb is None:
                fam = longest_prefix(event, FALLBACK)
                fb = FALLBACK.get(fam) if fam else None
            if fb is None:
                fb = [("block/amethyst/resonate1", 0.8, 1.0)]
                missing_fallback.append(event)
            fallback_used.append(event)
            if vsounds is not None:
                for n, _, _ in fb:
                    if n not in vsounds:
                        warn(f"звук {event}: ванильного minecraft:{n} нет")
            entries = [{"name": f"minecraft:{n}", "volume": v, "pitch": p} for n, v, p in fb]
        if att:
            for e in entries:
                e["attenuation_distance"] = att
        sounds[event] = {"subtitle": f"subtitles.suchki.{event}", "sounds": entries}
    if fallback_used:
        warn(f"нет своих .ogg для {len(fallback_used)} звуков — ванильная замена: {', '.join(fallback_used)}")
    if missing_fallback:
        warn(f"нет и ванильной замены (общий звон): {', '.join(missing_fallback)} — добавьте в FALLBACK")
    used_files = {f"{e['name'].split('/', 1)[1]}.ogg" for v in sounds.values() for e in v["sounds"]
                  if e["name"].startswith("suchki:")}
    unused = sorted(f.name for f in SOUND_DIR.glob("*.ogg") if f.name not in used_files)
    if unused:
        warn(f"файлы звуков без события ({len(unused)}): {', '.join(unused)}")

    # Ключи, на которые ссылается код.
    java_keys, java_prefixes = java_lang_refs()
    for k in java_registered("MagicItems.java", "item") + java_registered("MagicEffects.java", "effect") \
            + java_registered("MagicBlocks.java", "block"):
        java_keys.setdefault(k, "magic")
    java_keys.setdefault("entity.suchki.spell_projectile", "magic/MagicEntities.java")
    expand = {"suchki.tier.": [str(t) for t in range(1, MAX_RANK + 1)], "suchki.school.": list(elements),
              "suchki.style.": styles, "item.suchki.tome_": list(elements),
              "spell.suchki.": [s for s in jspells] or [sid for sid, _, _ in spells]}
    for pre, src in java_prefixes.items():
        if pre in expand:
            for x in expand[pre]:
                java_keys.setdefault(pre + x, src)
    for sid in jspells:
        java_keys.setdefault(f"spell.suchki.{sid}.desc", "magic/spells")

    # Файлы перевода.
    out = {}
    ru, removed_ru, added = merge_lang(LANG_DIR / "ru_ru.json", gen, java_keys, 0)
    en, removed_en, _ = merge_lang(LANG_DIR / "en_us.json", gen, java_keys, 1)
    unresolved = sorted(k for k in java_keys if "suchki" in k and (k not in ru or k not in en))
    for k in unresolved:
        warn(f"ключ перевода {k} ({java_keys[k]}) нигде не задан")
    for pre, src in sorted(java_prefixes.items()):
        if "suchki" in pre and pre not in expand and not any(k.startswith(pre) for k in ru):
            warn(f"динамический ключ «{pre}…» ({src}): нет ни одного ключа с таким началом")
    if set(ru) != set(en):
        warn(f"ru_ru и en_us различаются ключами: {sorted(set(ru) ^ set(en))[:12]}")
    write(LANG_DIR / "ru_ru.json", ru, out)
    write(LANG_DIR / "en_us.json", en, out)

    # Рецепты книг и фокусов.
    removed = []
    recipe_dir = DATA / "suchki" / "recipe"
    craft = {}
    for sid, el, d in spells:
        if d["ingredients"]:
            craft[f"tome_{sid}"] = {
                "type": "minecraft:crafting_shapeless",
                "category": "misc",
                "group": f"suchki_tome_{el}",
                "ingredients": ["minecraft:book"] + [f"minecraft:{i}" for i in d["ingredients"]],
                "result": {"id": f"suchki:tome_{el}", "count": 1, "components": {"suchki:spell": sid}},
            }
    for name, recipe in craft.items():
        write(recipe_dir / f"{name}.json", recipe, out)
    removed += [p for p in recipe_dir.glob("tome_*.json") if p.stem not in craft]
    for name, recipe in FOCUS_RECIPES.items():
        write(recipe_dir / f"{name}.json", recipe, out)

    # Модели и описания предметов.
    staff_display = {
        "thirdperson_righthand": {"rotation": [0, -90, 40], "translation": [0, 6.5, 0.5], "scale": [1.15, 1.15, 1.15]},
        "thirdperson_lefthand": {"rotation": [0, 90, -40], "translation": [0, 6.5, 0.5], "scale": [1.15, 1.15, 1.15]},
        "firstperson_righthand": {"rotation": [0, -90, 20], "translation": [1.13, 4.2, 1.13], "scale": [0.85, 0.85, 0.85]},
        "firstperson_lefthand": {"rotation": [0, 90, -20], "translation": [1.13, 4.2, 1.13], "scale": [0.85, 0.85, 0.85]},
    }
    models = ASSETS / "models" / "item"
    write(models / "staff.json", {"parent": "minecraft:item/handheld", "display": staff_display}, out)
    write(models / "book_focus.json", {"parent": "minecraft:item/generated", "display": {
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 2.6, 1.13], "scale": [0.48, 0.48, 0.48]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 2.6, 1.13], "scale": [0.48, 0.48, 0.48]},
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.6, 0.6, 0.6]},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.6, 0.6, 0.6]}}}, out)
    item_models = {s: "suchki:item/staff" for s in STAFFS}
    item_models["grimoire"] = "suchki:item/book_focus"
    item_models.update({f"tome_{e}": "minecraft:item/generated" for e in elements})
    item_models.update({f"spectral_{t}": "minecraft:item/handheld" for t in ("blade", "pickaxe", "axe", "shovel")})
    for name, parent in item_models.items():
        write(models / f"{name}.json", {"parent": parent, "textures": {"layer0": f"suchki:item/{name}"}}, out)
        write(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"suchki:item/{name}"}}, out)
    for folder in (models, ASSETS / "items"):
        removed += [p for p in folder.glob("tome_*.json") if p.stem not in item_models]

    # Блок магического света (невидимый) и частицы.
    write(ASSETS / "blockstates" / "magic_light.json", {"variants": {"": {"model": "suchki:block/magic_light"}}}, out)
    write(ASSETS / "models" / "block" / "magic_light.json", {"textures": {"particle": "minecraft:block/glowstone"}}, out)
    for name, textures in PARTICLES.items():
        write(ASSETS / "particles" / f"{name}.json", {"textures": textures}, out)
    write(ASSETS / "sounds.json", sounds, out)

    # Типы урона и теги (как у ванильной магии: животные паникуют, шипы стража не отвечают, чешуйницы просыпаются).
    for name in damage:
        write(DATA / "suchki" / "damage_type" / f"{name}.json",
              {"exhaustion": 0.1, "message_id": f"suchki.{name}", "scaling": "when_caused_by_living_non_player",
               **DAMAGE_EXTRA.get(name, {})}, out)
        for k in (f"death.attack.suchki.{name}", f"death.attack.suchki.{name}.player"):
            if k not in ru or k not in en:
                warn(f"тип урона {name}: нет перевода {k}")
    removed += [p for p in (DATA / "suchki" / "damage_type").glob("spell_*.json") if p.stem not in damage]
    all_types = [f"suchki:{n}" for n in damage]
    tags = DATA / "minecraft" / "tags" / "damage_type"
    write(DATA / "suchki" / "tags" / "damage_type" / "spell.json", {"values": all_types}, out)
    write(tags / "is_fire.json", {"replace": False, "values": ["suchki:spell_fire"]}, out)
    write(tags / "burns_armor_stands.json", {"replace": False, "values": ["suchki:spell_fire"]}, out)
    write(tags / "is_freezing.json", {"replace": False, "values": ["suchki:spell_frost"]}, out)
    for tag in ("no_knockback", "panic_causes", "avoids_guardian_thorns", "always_triggers_silverfish"):
        write(tags / f"{tag}.json", {"replace": False, "values": all_types}, out)

    print(f"Магия: {len(elements)} стихий, {len(spells)} заклинаний с текстами (в Java — {len(jspells)}), "
          f"{len(craft)} рецептов книг, {len(sounds)} звуков ({len(sounds) - len(fallback_used)} своих), "
          f"{len(damage)} видов урона")
    print(f"Переводы: {len(ru)} ключей; добавлено {len(added)}, убрано {len(removed_ru)} (ru) / {len(removed_en)} (en)")
    if removed_ru:
        groups = {}
        for k in removed_ru:
            g = ".".join(k.split(".")[:3 if k.startswith(("spell.", "subtitles.", "death.")) else 2])
            groups[g] = groups.get(g, 0) + 1
        print("  убраны: " + ", ".join(f"{g}.* ×{n}" if n > 1 else g for g, n in sorted(groups.items())))
    stale = [p for p in removed if p.exists()]
    if stale:
        print(f"Удаляются устаревшие файлы ({len(stale)}): " + ", ".join(p.relative_to(RES).as_posix() for p in stale))
    for w in WARNINGS:
        print("  ! " + w)
    if args.check:
        print("--check: ничего не записано")
        return
    flush(out, removed)
    print(f"записано {len(out)} файлов")


if __name__ == "__main__":
    try:
        main()
    except DataError as e:
        sys.exit(f"Ошибка данных: {e}")
