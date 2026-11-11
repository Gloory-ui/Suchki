#!/usr/bin/env python3
"""Проверяет каталог магии и показывает расстояние до цели: 12 школ и 204 заклинания.

По умолчанию цель — отчёт, а ошибки целостности (дубли, переводы, иконки, рецепты) завершают проверку с кодом 1.
Для релизного гейта используйте ``--strict-target``: тогда отсутствие любой из 12 целевых школ или менее 200
зарегистрированных заклинаний тоже считается ошибкой. Число 204 — это 17 заклинаний в каждой из 12 школ.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path


TARGET_SCHOOLS = (
    "fire", "water", "wind", "earth", "lightning", "light",
    "dark", "space", "life", "mind", "summon", "fusion",
)
TARGET_SPELLS = 204
TARGET_PER_SCHOOL = 17
CRAFTABLE_MAX_RANK = 4

SCHOOL_PATTERN = re.compile(r'^\s*([A-Z][A-Z0-9_]*)\("([a-z0-9_]+)"\s*,', re.MULTILINE)
SPELL_PATTERN = re.compile(
    r'Spell\.of\(\s*"([a-z0-9_]+)"\s*,\s*(?:School\.)?([A-Z][A-Z0-9_]*)\s*,\s*'
    r'(\d+)\s*,\s*(?:CastStyle\.)?([A-Z][A-Z0-9_]*)',
    re.MULTILINE,
)


@dataclass(frozen=True)
class SpellRow:
    spell_id: str
    school_const: str
    school_id: str
    rank: int
    style: str
    source: Path


def load_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"не удалось прочитать {path}: {exc}") from exc


def discover(root: Path) -> tuple[dict[str, str], list[SpellRow]]:
    school_file = root / "src/main/java/com/suchki/magic/School.java"
    school_text = school_file.read_text(encoding="utf-8")
    schools = dict(SCHOOL_PATTERN.findall(school_text))
    rows: list[SpellRow] = []
    spell_dir = root / "src/main/java/com/suchki/magic/spells"
    for path in sorted(spell_dir.glob("*Spells.java")):
        text = path.read_text(encoding="utf-8")
        for spell_id, school_const, rank, style in SPELL_PATTERN.findall(text):
            rows.append(SpellRow(spell_id, school_const, schools.get(school_const, ""), int(rank), style, path))
    return schools, rows


def recipe_spell_id(recipe: dict) -> str | None:
    result = recipe.get("result")
    if not isinstance(result, dict):
        return None
    components = result.get("components")
    if not isinstance(components, dict):
        return None
    value = components.get("suchki:spell")
    return value if isinstance(value, str) else None


def validate(root: Path, strict_target: bool) -> int:
    errors: list[str] = []
    warnings: list[str] = []
    schools, spells = discover(root)
    school_ids = set(schools.values())

    ids = [row.spell_id for row in spells]
    duplicates = sorted(spell_id for spell_id, count in Counter(ids).items() if count > 1)
    if duplicates:
        errors.append("повторяющиеся id: " + ", ".join(duplicates))

    for row in spells:
        if not row.school_id:
            errors.append(f"{row.spell_id}: неизвестная константа школы {row.school_const} ({row.source.name})")
        if not 1 <= row.rank <= 7:
            errors.append(f"{row.spell_id}: ранг {row.rank}, допустимо 1…7")

    lang_dir = root / "src/main/resources/assets/suchki/lang"
    ru = load_json(lang_dir / "ru_ru.json")
    en = load_json(lang_dir / "en_us.json")
    icon_dir = root / "src/main/resources/assets/suchki/textures/gui/spell"
    recipe_dir = root / "src/main/resources/data/suchki/recipe"

    for row in spells:
        for suffix in ("", ".desc"):
            key = f"spell.suchki.{row.spell_id}{suffix}"
            if not isinstance(ru.get(key), str) or not ru[key].strip():
                errors.append(f"{row.spell_id}: нет ru_ru ключа {key}")
            if not isinstance(en.get(key), str) or not en[key].strip():
                errors.append(f"{row.spell_id}: нет en_us ключа {key}")

        icon = icon_dir / f"{row.spell_id}.png"
        if not icon.is_file():
            errors.append(f"{row.spell_id}: нет иконки {icon.relative_to(root)}")

        recipe_path = recipe_dir / f"tome_{row.spell_id}.json"
        if row.rank <= CRAFTABLE_MAX_RANK:
            if not recipe_path.is_file():
                errors.append(f"{row.spell_id}: для ранга {row.rank} нет рецепта книги")
            else:
                try:
                    linked_id = recipe_spell_id(load_json(recipe_path))
                except ValueError as exc:
                    errors.append(str(exc))
                else:
                    if linked_id != row.spell_id:
                        errors.append(f"{row.spell_id}: рецепт ссылается на {linked_id!r}")
        elif recipe_path.is_file():
            warnings.append(f"{row.spell_id}: книга ранга {row.rank} имеет рецепт; проверьте редкость")

    # Сироты обычно означают опечатку или удалённое из Java заклинание, оставшееся в ресурсах.
    spell_set = set(ids)
    orphan_icons = sorted(path.stem for path in icon_dir.glob("*.png") if path.stem not in spell_set)
    orphan_recipes = sorted(
        path.stem.removeprefix("tome_") for path in recipe_dir.glob("tome_*.json")
        if path.stem.removeprefix("tome_") not in spell_set
    )
    if orphan_icons:
        warnings.append("иконки без регистрации: " + ", ".join(orphan_icons))
    if orphan_recipes:
        warnings.append("рецепты без регистрации: " + ", ".join(orphan_recipes))

    by_school: dict[str, list[SpellRow]] = defaultdict(list)
    for row in spells:
        by_school[row.school_id or f"?{row.school_const}"].append(row)

    print("| школа | заклинаний | ранги |")
    print("|---|---:|---|")
    ordered = list(dict.fromkeys((*TARGET_SCHOOLS, *sorted(school_ids - set(TARGET_SCHOOLS)))))
    for school in ordered:
        rows = by_school.get(school, [])
        ranks = Counter(row.rank for row in rows)
        rank_text = ", ".join(f"{rank}:{ranks[rank]}" for rank in sorted(ranks)) or "—"
        marker = "" if school in school_ids else " (нет в School)"
        print(f"| {school}{marker} | {len(rows)} | {rank_text} |")

    missing_target_schools = sorted(set(TARGET_SCHOOLS) - school_ids)
    underfilled_schools = [
        f"{school} ({len(by_school.get(school, []))}/{TARGET_PER_SCHOOL})"
        for school in TARGET_SCHOOLS
        if len(by_school.get(school, [])) < TARGET_PER_SCHOOL
    ]
    print(f"\nшкол в коде: {len(school_ids)}; целевых школ: {len(TARGET_SCHOOLS)}")
    print(f"заклинаний в коде: {len(spells)}; цель: {TARGET_SPELLS}+; осталось: {max(0, TARGET_SPELLS - len(spells))}")
    if missing_target_schools:
        message = "до целевой модели не хватает школ: " + ", ".join(missing_target_schools)
        (errors if strict_target else warnings).append(message)
    if len(spells) < TARGET_SPELLS:
        message = f"до цели {TARGET_SPELLS} не хватает {TARGET_SPELLS - len(spells)} заклинаний"
        (errors if strict_target else warnings).append(message)
    if underfilled_schools:
        message = "до комплектации 17 на школу не хватает: " + ", ".join(underfilled_schools)
        (errors if strict_target else warnings).append(message)

    if warnings:
        print("\nПредупреждения:")
        for warning in warnings:
            print(f"- {warning}")
    if errors:
        print("\nОшибки:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
    else:
        print("\nЦелостность каталога: OK")
    return 1 if errors else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent,
                        help="корень проверяемой рабочей копии")
    parser.add_argument("--strict-target", action="store_true",
                        help=f"требовать именно 12 целевых школ и не менее {TARGET_SPELLS} заклинаний")
    args = parser.parse_args()
    try:
        return validate(args.root.resolve(), args.strict_target)
    except (OSError, ValueError) as exc:
        print(f"Ошибка каталога: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
