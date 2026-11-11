#!/usr/bin/env python3
"""Fast, dependency-free validation for Suchki data and generated resources.

The validator deliberately does not launch Minecraft. It catches errors that should fail before the
expensive Gradle/game-test jobs: malformed or duplicate JSON keys, translation drift, broken sound and
texture references, invalid Ogg/Vorbis channel metadata, and disagreements between the generated spell
catalog, Java registrations, icons and recipes.

Usage:
    python tools/validate_resources.py
    python tools/validate_resources.py --expect-spells 200
    python tools/validate_resources.py --report build/reports/resources.json
"""

from __future__ import annotations

import argparse
import ast
import json
import re
import struct
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "src/main/resources"
ASSETS = RESOURCES / "assets"
SUCHKI_ASSETS = ASSETS / "suchki"
DATA = RESOURCES / "data"


class DuplicateKey(ValueError):
    pass


class Validation:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.stats: dict[str, int] = {}

    def error(self, message: str) -> None:
        self.errors.append(message)

    def warning(self, message: str) -> None:
        self.warnings.append(message)

    def count(self, name: str, value: int) -> None:
        self.stats[name] = value


def unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateKey(f"duplicate key {key!r}")
        result[key] = value
    return result


def read_json(path: Path, validation: Validation) -> Any | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_object)
    except (OSError, UnicodeError, json.JSONDecodeError, DuplicateKey) as exc:
        validation.error(f"{path.relative_to(ROOT)}: {exc}")
        return None


def validate_all_json(validation: Validation) -> None:
    paths = sorted(RESOURCES.rglob("*.json"))
    for path in paths:
        read_json(path, validation)
    validation.count("json_files", len(paths))


FORMAT = re.compile(r"%(?:\d+\$)?[a-zA-Z]|\{\d+\}")


def validate_languages(validation: Validation) -> tuple[dict[str, str], dict[str, str]]:
    ru_path = SUCHKI_ASSETS / "lang/ru_ru.json"
    en_path = SUCHKI_ASSETS / "lang/en_us.json"
    ru = read_json(ru_path, validation)
    en = read_json(en_path, validation)
    if not isinstance(ru, dict) or not isinstance(en, dict):
        return {}, {}

    ru_keys, en_keys = set(ru), set(en)
    for key in sorted(ru_keys - en_keys):
        validation.error(f"translation {key!r} exists in ru_ru but not en_us")
    for key in sorted(en_keys - ru_keys):
        validation.error(f"translation {key!r} exists in en_us but not ru_ru")
    for key in sorted(ru_keys & en_keys):
        if not isinstance(ru[key], str) or not ru[key].strip():
            validation.error(f"ru_ru translation {key!r} is empty or not a string")
            continue
        if not isinstance(en[key], str) or not en[key].strip():
            validation.error(f"en_us translation {key!r} is empty or not a string")
            continue
        ru_format = sorted(FORMAT.findall(ru[key]))
        en_format = sorted(FORMAT.findall(en[key]))
        if ru_format != en_format:
            validation.error(f"translation placeholders differ for {key!r}: ru={ru_format}, en={en_format}")
    validation.count("translations", len(ru_keys & en_keys))
    return ru, en


def resource_path(identifier: str, folder: str, suffix: str) -> Path | None:
    if ":" not in identifier:
        return None
    namespace, name = identifier.split(":", 1)
    if not namespace or not name or ".." in Path(name).parts:
        return None
    return ASSETS / namespace / folder / f"{name}{suffix}"


def ogg_metadata(path: Path) -> tuple[int, int] | None:
    """Read channels and sample rate from the first Vorbis identification packet."""
    try:
        data = path.read_bytes()[:65536]
    except OSError:
        return None
    if not data.startswith(b"OggS"):
        return None
    marker = data.find(b"\x01vorbis")
    if marker < 0 or len(data) < marker + 16:
        return None
    channels = data[marker + 11]
    sample_rate = struct.unpack_from("<I", data, marker + 12)[0]
    return channels, sample_rate


def validate_sounds(validation: Validation, ru: dict[str, str], en: dict[str, str]) -> None:
    path = SUCHKI_ASSETS / "sounds.json"
    sounds = read_json(path, validation)
    if not isinstance(sounds, dict):
        return

    referenced: set[Path] = set()
    for event, definition in sounds.items():
        if not isinstance(definition, dict):
            validation.error(f"sounds.json event {event!r} must be an object")
            continue
        subtitle = definition.get("subtitle")
        if subtitle is not None and (subtitle not in ru or subtitle not in en):
            validation.error(f"sounds.json event {event!r} uses missing subtitle {subtitle!r}")
        entries = definition.get("sounds")
        if not isinstance(entries, list) or not entries:
            validation.error(f"sounds.json event {event!r} has no sound entries")
            continue
        for entry in entries:
            if isinstance(entry, str):
                name, kind = entry, "sound"
            elif isinstance(entry, dict):
                name, kind = entry.get("name"), entry.get("type", "sound")
            else:
                validation.error(f"sounds.json event {event!r} contains invalid entry {entry!r}")
                continue
            if not isinstance(name, str) or ":" not in name:
                validation.error(f"sounds.json event {event!r} contains invalid name {name!r}")
                continue
            if kind == "event":
                continue
            local = resource_path(name, "sounds", ".ogg")
            if local is None:
                validation.error(f"sounds.json event {event!r} contains unsafe resource id {name!r}")
            elif name.startswith("suchki:"):
                referenced.add(local)
                if not local.is_file():
                    validation.error(f"sounds.json event {event!r} references missing {local.relative_to(ROOT)}")

    local_files = set(SUCHKI_ASSETS.glob("sounds/**/*.ogg"))
    for unused in sorted(local_files - referenced):
        validation.error(f"unreferenced sound file: {unused.relative_to(ROOT)}")
    for ogg in sorted(local_files):
        if ogg.stat().st_size < 128:
            validation.error(f"sound file is suspiciously small: {ogg.relative_to(ROOT)}")
            continue
        metadata = ogg_metadata(ogg)
        if metadata is None:
            validation.error(f"not a readable Ogg/Vorbis file: {ogg.relative_to(ROOT)}")
            continue
        channels, sample_rate = metadata
        if channels != 1:
            validation.error(f"{ogg.relative_to(ROOT)}: expected mono, got {channels} channels")
        if sample_rate != 44100:
            validation.error(f"{ogg.relative_to(ROOT)}: expected 44100 Hz, got {sample_rate} Hz")
    validation.count("sound_events", len(sounds))
    validation.count("sound_files", len(local_files))


def validate_particles(validation: Validation) -> None:
    paths = sorted((SUCHKI_ASSETS / "particles").glob("*.json"))
    for path in paths:
        particle = read_json(path, validation)
        if not isinstance(particle, dict):
            continue
        textures = particle.get("textures")
        if not isinstance(textures, list) or not textures:
            validation.error(f"{path.relative_to(ROOT)}: particle has no textures")
            continue
        for texture in textures:
            if not isinstance(texture, str):
                validation.error(f"{path.relative_to(ROOT)}: invalid texture {texture!r}")
                continue
            target = resource_path(texture, "textures/particle", ".png")
            if target is None or not target.is_file():
                validation.error(f"{path.relative_to(ROOT)}: missing particle texture {texture!r}")
    validation.count("particle_definitions", len(paths))


def validate_models(validation: Validation) -> None:
    item_paths = sorted((SUCHKI_ASSETS / "items").glob("*.json"))
    for path in item_paths:
        descriptor = read_json(path, validation)
        try:
            model_id = descriptor["model"]["model"]
        except (KeyError, TypeError):
            validation.error(f"{path.relative_to(ROOT)}: missing model.model")
            continue
        target = resource_path(model_id, "models", ".json")
        if target is None or not target.is_file():
            validation.error(f"{path.relative_to(ROOT)}: missing model {model_id!r}")

    model_paths = sorted((SUCHKI_ASSETS / "models").rglob("*.json"))
    for path in model_paths:
        model = read_json(path, validation)
        if not isinstance(model, dict):
            continue
        parent = model.get("parent")
        if isinstance(parent, str) and parent.startswith("suchki:"):
            target = resource_path(parent, "models", ".json")
            if target is None or not target.is_file():
                validation.error(f"{path.relative_to(ROOT)}: missing parent model {parent!r}")
        textures = model.get("textures", {})
        if isinstance(textures, dict):
            for texture in textures.values():
                if isinstance(texture, str) and texture.startswith("suchki:") and not texture.startswith("#"):
                    target = resource_path(texture, "textures", ".png")
                    if target is None or not target.is_file():
                        validation.error(f"{path.relative_to(ROOT)}: missing texture {texture!r}")
    validation.count("item_descriptors", len(item_paths))
    validation.count("models", len(model_paths))


def python_literal(path: Path, name: str, validation: Validation) -> Any | None:
    try:
        tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    except (OSError, UnicodeError, SyntaxError) as exc:
        validation.error(f"{path.relative_to(ROOT)}: {exc}")
        return None
    for node in tree.body:
        if isinstance(node, (ast.Assign, ast.AnnAssign)):
            targets = node.targets if isinstance(node, ast.Assign) else [node.target]
            if any(isinstance(target, ast.Name) and target.id == name for target in targets):
                try:
                    return ast.literal_eval(node.value)
                except (ValueError, TypeError) as exc:
                    validation.error(f"{path.relative_to(ROOT)}: {name} is not a literal: {exc}")
                    return None
    validation.error(f"{path.relative_to(ROOT)}: missing literal {name}")
    return None


def java_spell_ids() -> set[str]:
    result: set[str] = set()
    pattern = re.compile(r'Spell\.of\(\s*"([a-z0-9_]+)"')
    for path in (ROOT / "src/main/java/com/suchki/magic/spells").glob("*.java"):
        result.update(pattern.findall(path.read_text(encoding="utf-8")))
    return result


def validate_spells(validation: Validation, ru: dict[str, str], en: dict[str, str], expected: int | None) -> None:
    generator = ROOT / "tools/gen_magic.py"
    schools = python_literal(generator, "SCHOOLS", validation)
    spells = python_literal(generator, "SPELLS", validation)
    if not isinstance(schools, dict) or not isinstance(spells, dict):
        return
    if expected is not None and len(spells) < expected:
        validation.error(f"spell catalog contains {len(spells)} spells; expected at least {expected}")

    for spell_id, data in spells.items():
        if not re.fullmatch(r"[a-z0-9_]+", spell_id):
            validation.error(f"invalid spell id {spell_id!r}")
            continue
        if not isinstance(data, tuple) or len(data) != 7:
            validation.error(f"spell {spell_id!r} has invalid generator record")
            continue
        school, tier, *_texts, ingredients = data
        if school not in schools:
            validation.error(f"spell {spell_id!r} uses unknown school {school!r}")
        if not isinstance(tier, int) or not 1 <= tier <= 5:
            validation.error(f"spell {spell_id!r} has invalid tier {tier!r}")
        for lang_name, lang in (("ru_ru", ru), ("en_us", en)):
            for key in (f"spell.suchki.{spell_id}", f"spell.suchki.{spell_id}.desc"):
                if key not in lang:
                    validation.error(f"spell {spell_id!r} is missing {lang_name} key {key!r}")
        icon = SUCHKI_ASSETS / f"textures/gui/spell/{spell_id}.png"
        if not icon.is_file():
            validation.error(f"spell {spell_id!r} is missing icon {icon.relative_to(ROOT)}")
        if ingredients is not None:
            recipe = DATA / f"suchki/recipe/tome_{spell_id}.json"
            if not recipe.is_file():
                validation.error(f"spell {spell_id!r} is missing recipe {recipe.relative_to(ROOT)}")

    catalog_ids = set(spells)
    code_ids = java_spell_ids()
    for spell_id in sorted(catalog_ids - code_ids):
        validation.error(f"spell {spell_id!r} exists in generator but is not registered in Java")
    for spell_id in sorted(code_ids - catalog_ids):
        validation.error(f"spell {spell_id!r} is registered in Java but missing from generator")
    icon_ids = {path.stem for path in (SUCHKI_ASSETS / "textures/gui/spell").glob("*.png")}
    for spell_id in sorted(icon_ids - catalog_ids):
        validation.error(f"orphan spell icon {spell_id!r}")

    validation.count("schools", len(schools))
    validation.count("spells", len(spells))


def validate_skills(validation: Validation) -> None:
    path = RESOURCES / "suchki/skills.json"
    document = read_json(path, validation)
    if not isinstance(document, dict) or not isinstance(document.get("roles"), list):
        validation.error(f"{path.relative_to(ROOT)}: missing roles array")
        return
    roles = document["roles"]
    role_ids: set[str] = set()
    node_ids: set[str] = set()
    for role in roles:
        if not isinstance(role, dict) or not isinstance(role.get("id"), str):
            validation.error(f"{path.relative_to(ROOT)}: invalid role record")
            continue
        role_id = role["id"]
        if role_id in role_ids:
            validation.error(f"duplicate role id {role_id!r}")
        role_ids.add(role_id)
        nodes = role.get("nodes")
        if not isinstance(nodes, list) or len(nodes) != 15:
            validation.error(f"role {role_id!r} must have 15 nodes, got {len(nodes) if isinstance(nodes, list) else 'invalid'}")
            continue
        positions: set[tuple[int, int]] = set()
        for node in nodes:
            try:
                node_id, branch, tier = node["id"], node["branch"], node["tier"]
            except (KeyError, TypeError):
                validation.error(f"role {role_id!r} contains an invalid node")
                continue
            if node_id in node_ids:
                validation.error(f"duplicate skill node id {node_id!r}")
            node_ids.add(node_id)
            if branch not in range(3) or tier not in range(1, 6):
                validation.error(f"skill node {node_id!r} has invalid branch/tier {branch!r}/{tier!r}")
            if (branch, tier) in positions:
                validation.error(f"role {role_id!r} duplicates branch {branch}, tier {tier}")
            positions.add((branch, tier))
    validation.count("roles", len(role_ids))
    validation.count("skill_nodes", len(node_ids))


def write_report(path: Path, validation: Validation) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({
        "ok": not validation.errors,
        "stats": validation.stats,
        "warnings": validation.warnings,
        "errors": validation.errors,
    }, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--expect-spells", type=int, help="fail if the generated spell catalog is smaller")
    parser.add_argument("--report", type=Path, default=ROOT / "build/reports/resource-validation.json")
    args = parser.parse_args()

    validation = Validation()
    validate_all_json(validation)
    ru, en = validate_languages(validation)
    validate_sounds(validation, ru, en)
    validate_particles(validation)
    validate_models(validation)
    validate_spells(validation, ru, en, args.expect_spells)
    validate_skills(validation)
    write_report(args.report, validation)

    print("Suchki resource validation")
    for name, value in sorted(validation.stats.items()):
        print(f"  {name}: {value}")
    for warning in validation.warnings:
        print(f"WARNING: {warning}", file=sys.stderr)
    for error in validation.errors:
        print(f"ERROR: {error}", file=sys.stderr)
    print(f"result: {'OK' if not validation.errors else f'{len(validation.errors)} error(s)'}")
    print(f"report: {args.report.relative_to(ROOT) if args.report.is_relative_to(ROOT) else args.report}")
    return 0 if not validation.errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
