#!/usr/bin/env python3
"""Таблицы заклинаний для docs/magic.md: из выгрузки игры (magic_spells.json — команда /suchki_spells_dump
на сервере с -Dsuchki.audit=true) и текстов tools/spelldata/*.py. Пишет между метками <!-- spells:begin/end -->.

Запуск из корня мода:
    python tools/gen_docs.py путь/к/magic_spells.json
"""

import importlib.util
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOC = ROOT / "docs" / "magic.md"

SCHOOLS = [
    ("fire", "Огонь"), ("water", "Вода"), ("wind", "Ветер"), ("earth", "Земля"), ("lightning", "Молния"),
    ("light", "Свет"), ("dark", "Тьма"), ("space", "Пространство"), ("life", "Жизнь"), ("mind", "Разум"),
    ("summon", "Призыв"), ("fusion", "Слияние"),
]
RANKS = {1: "I", 2: "II", 3: "III", 4: "IV", 5: "V", 6: "VI", 7: "VII"}
STYLES = {"BOLT": "удар", "CHANNEL": "поток", "SELF": "на себя", "RITUAL": "ритуал", "SUMMON": "призыв"}
ANCHORS = {"HAND": "у посоха", "FEET": "под ногами", "TARGET": "у цели", "SKY": "в небе", "LINE": "линией",
           "BACK": "за спиной", "ORBIT": "по орбите"}


def texts():
    out = {}
    for path in sorted((ROOT / "tools" / "spelldata").glob("*.py")):
        if path.name.startswith("_"):
            continue
        spec = importlib.util.spec_from_file_location(f"spelldata_{path.stem}", path)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        out.update(getattr(mod, "SPELLS", {}))
    return out


def table(spells, data):
    names = dict(SCHOOLS)
    lines = []
    for sid, ru in SCHOOLS:
        group = sorted((s for s in spells if s["school"] == sid), key=lambda s: (s["tier"], s["id"]))
        if not group:
            continue
        lines.append(f"### {ru}\n")
        lines.append("| Заклинание | Ранг | Тип | Круг | Мана | Что делает |")
        lines.append("|---|---|---|---|---|---|")
        for s in group:
            t = data.get(s["id"], {})
            name = t.get("ru", s["id"])
            if s["combo"]:
                name += " (" + " + ".join(names.get(c, c) for c in s["combo"]) + ")"
            mana = f"{s['mana']}/с" if s["style"] == "CHANNEL" else str(s["mana"])
            circle = ANCHORS.get(s["anchor"], s["anchor"])
            if s["layers"] > 1:
                circle += f", ×{s['layers']}"
            desc = t.get("ru_desc", "").replace("|", "/")
            if s["destroys"]:
                desc += " **Ломает блоки.**"
            lines.append(f"| {name} | {RANKS[s['tier']]} | {STYLES.get(s['style'], s['style'])} | {circle} | {mana} | {desc} |")
        lines.append("")
    return "\n".join(lines)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    spells = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    body = table(spells, texts())
    doc = DOC.read_text(encoding="utf-8")
    begin, end = "<!-- spells:begin -->", "<!-- spells:end -->"
    i, j = doc.index(begin) + len(begin), doc.index(end)
    total = f"\nВсего заклинаний: **{len(spells)}**, из них ломают блоки — {sum(1 for s in spells if s['destroys'])}.\n\n"
    DOC.write_text(doc[:i] + total + body + "\n" + doc[j:], encoding="utf-8")
    print(f"docs/magic.md: {len(spells)} заклинаний")


if __name__ == "__main__":
    main()
