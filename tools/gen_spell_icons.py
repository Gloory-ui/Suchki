#!/usr/bin/env python3
"""Значки заклинаний для колеса выбора и HUD: медальон 32×32 в цвете стихии и символ заклинания.

Рисунки — в tools/spellicons/<стихия>.py (функция на заклинание, имя = id, декоратор @spell из tools/iconlib.py;
там же весь API рисования). Символ строится из простых фигур с 8-кратным суперсэмплингом и сводится к чётким
пикселям; объём, тёмный контур и свечение добавляет iconlib. Медальон — как у значков стихий (gen_textures.py).
Запуск из корня мода:
    python tools/gen_spell_icons.py                 — записать textures/gui/spell/<id>.png (лишние PNG удаляются)
    python tools/gen_spell_icons.py --preview DIR   — только превью: по стихиям (×8, с подписями) и все сразу (×3)
    python tools/gen_spell_icons.py --demo DIR      — превью старых рисунков (spellicons/classic.py) в цветах стихий
Заклинания из tools/spelldata/ и Java (Spell.of) без своего значка получают заглушку — значок стихии — и попадают
в список предупреждений. Генерация детерминирована.
"""

import argparse
import importlib
import sys
import traceback
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS))
sys.dont_write_bytecode = True

import numpy as np  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

import gen_magic  # noqa: E402
import iconlib  # noqa: E402
from gen_textures import SCHOOL_HEX, TEX, check_school_hex, save_png, sheet  # noqa: E402

OUT = TEX / "gui" / "spell"


def load_icon_modules():
    """Импортировать все tools/spellicons/<модуль>.py (кроме classic и служебных); ошибки — списком, не падая."""
    errors = []
    for p in sorted((TOOLS / "spellicons").glob("*.py")):
        if p.stem.startswith("_") or p.stem == "classic":
            continue
        try:
            importlib.import_module(f"spellicons.{p.stem}")
        except Exception:
            errors.append(f"spellicons/{p.name} не загрузился:\n{traceback.format_exc(limit=3)}")
    return errors


def known_spells():
    """id → (стихия, откуда): тексты из tools/spelldata и заклинания из Java."""
    out = {}
    elements = gen_magic.java_elements()
    for el in elements:
        path = TOOLS / "spelldata" / f"{el}.py"
        if not path.exists():
            continue
        try:
            spells = getattr(gen_magic.load_module(path, f"spelldata_{el}"), "SPELLS", {}) or {}
        except Exception as e:
            print(f"  ! spelldata/{el}.py не загрузился: {e}")
            continue
        for sid in spells:
            out[sid] = (el, f"spelldata/{el}.py")
    for sid, (el, _, file) in gen_magic.java_spells(elements).items():
        out.setdefault(sid, (el, file))
    return out


def labeled_sheet(items, scale, cols, pad=10):
    """Превью с подписями id под значками (для проверки своих рисунков)."""
    font = ImageFont.load_default()
    cell = 32 * scale
    cw, ch = cell + pad, cell + pad + 14
    rows = max(1, (len(items) + cols - 1) // cols)
    out = Image.new("RGBA", (cw * cols + pad, ch * rows + pad), (34, 34, 40, 255))
    d = ImageDraw.Draw(out)
    for i, (name, im, mark) in enumerate(items):
        x, y = pad + (i % cols) * cw, pad + (i // cols) * ch
        out.alpha_composite(im.resize((cell, cell), Image.NEAREST), (x, y))
        label = name if len(name) * 6 <= cell else name[:cell // 6 - 1] + "…"
        d.text((x, y + cell + 1), label, fill=(255, 170, 120, 255) if mark else (215, 215, 225, 255), font=font)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--preview", type=Path, help="только превью в эту папку (значки в src не трогаются)")
    ap.add_argument("--demo", type=Path, help="превью старых рисунков в цветах новых стихий")
    args = ap.parse_args()
    check_school_hex()
    if args.demo:
        demo(args.demo)
        return
    errors = load_icon_modules()
    spells = known_spells()

    icons, broken = {}, []
    for sid, icon in iconlib.REGISTRY.items():
        try:
            icons[sid] = iconlib.render(sid)
        except Exception:
            broken.append(f"значок {sid} ({icon.origin}) не нарисовался:\n{traceback.format_exc(limit=2)}")
    warnings = []
    for sid, icon in sorted(iconlib.REGISTRY.items()):
        if sid in spells and spells[sid][0] != icon.element:
            warnings.append(f"{sid}: значок стихии {icon.element}, а заклинание — {spells[sid][0]} ({spells[sid][1]})")
        if spells and sid not in spells:
            warnings.append(f"{sid}: значок есть ({icon.origin}), а заклинания с таким id нет")
    missing = sorted(sid for sid in spells if sid not in icons)
    placeholders = {sid: iconlib.placeholder(spells[sid][0]) for sid in missing}
    if missing:
        warnings.append(f"без своего значка ({len(missing)}, нарисована заглушка-стихия): " + ", ".join(missing))

    for e in errors + broken:
        print("  !! " + e.rstrip())
    for w in warnings:
        print("  ! " + w)

    out = {**icons, **placeholders}
    if args.preview:
        args.preview.mkdir(parents=True, exist_ok=True)
        el_of = {sid: (iconlib.REGISTRY[sid].element if sid in iconlib.REGISTRY else spells[sid][0]) for sid in out}
        for el in SCHOOL_HEX:
            items = [(sid, out[sid], sid in placeholders) for sid in out if el_of[sid] == el]
            if items:
                labeled_sheet(items, 8, 6).save(args.preview / f"spells_{el}.png")
        if out:
            sheet(sorted(out.items(), key=lambda kv: (list(SCHOOL_HEX).index(el_of[kv[0]]), kv[0])), 3, 12,
                  dark=True).save(args.preview / "spells_all.png")
        print(f"превью {len(icons)} значков (+{len(placeholders)} заглушек) в {args.preview}")
        return
    for sid, im in out.items():
        save_png(im, OUT / f"{sid}.png")
    stale = [f for f in sorted(OUT.glob("*.png")) if f.stem not in out]
    if errors or broken:
        print(f"  !! есть ошибки — устаревшие значки ({len(stale)}) не удаляю")
    else:
        for f in stale:
            f.unlink()
    print(f"записано {len(icons)} значков и {len(placeholders)} заглушек в {OUT}"
          + (f", удалено устаревших: {len(stale)}" if stale and not (errors or broken) else ""))
    if errors or broken:
        sys.exit(1)


# Слияния для демонстрации секторного медальона: старый рисунок → стихии.
DEMO_FUSION = {
    "fireball": ("fire", "dark"), "frost_nova": ("water", "wind"), "chain_lightning": ("lightning", "water"),
    "judgement": ("light", "fire"), "mass_hysteria": ("space", "dark"), "healing_circle": ("life", "light"),
    "meteor_shower": ("fire", "earth"), "spirit_blades": ("summon", "wind", "lightning"),
}


def demo(path):
    from spellicons import classic
    path.mkdir(parents=True, exist_ok=True)
    items = []
    for name, (_, element) in classic.STYLE.items():
        accent = classic.ACCENT.get(name)
        if accent == element:
            accent = None
        items.append((name, iconlib.draw(element, getattr(classic, name), accent), False))
    items.sort(key=lambda t: (list(SCHOOL_HEX).index(classic.STYLE[t[0]][1]), t[0]))
    labeled_sheet(items, 6, 10).save(path / "classic_by_element.png")
    fusion = [(f"{n}:{'+'.join(c)}", iconlib.draw("fusion", getattr(classic, n), None, c), False)
              for n, c in DEMO_FUSION.items()]
    labeled_sheet(fusion, 8, 4).save(path / "classic_fusion.png")
    print(f"демо {len(items)} + {len(fusion)} значков в {path}")


if __name__ == "__main__":
    main()
