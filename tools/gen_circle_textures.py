#!/usr/bin/env python3
"""Текстуры для процедурных магических кругов и эффектов в мире Suchki (рисует их client.magic.CircleRenderer
и WorldFxRenderer). Все текстуры белые (цвет задаёт код), форма — в альфе.

Запуск из корня мода:
    python tools/gen_circle_textures.py                 — записать текстуры
    python tools/gen_circle_textures.py --preview DIR   — только увеличенный лист-превью в DIR
Нужны Pillow и numpy. Генерация детерминирована: повторный запуск даёт побайтно те же файлы.

Профили линий (circle_line, circle_glow, fx_wave) — полоски: U поперёк штриха, V вдоль (постоянно).
Шумы (fx_noise, fx_streak) и соты (fx_hex) бесшовные — их можно прокручивать и повторять.
"""

import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/suchki/textures/particle"


def smooth(alpha):
    out = np.zeros(alpha.shape + (4,))
    out[..., :3] = 1
    out[..., 3] = np.clip(alpha, 0, 1)
    return Image.fromarray(np.round(out * 255).astype(np.uint8), "RGBA")


def across(w, h):
    """Координата поперёк полоски: −1…1 по ширине (центры пикселей)."""
    u = (np.arange(w) + 0.5) / w * 2 - 1
    return np.tile(u, (h, 1))


# ---------------------------------------------------------------- штрихи круга

def circle_line():
    # Чёткая яркая сердцевина и узкий мягкий край: линия читается и вблизи, и издалека.
    u = across(32, 4)
    win = np.clip(1 - u * u, 0, 1)
    a = np.exp(-(u / 0.34) ** 2) * 0.95 + 0.3 * np.exp(-(u / 0.75) ** 2)
    return smooth(a * win ** 0.6)


def circle_glow():
    # Широкое мягкое свечение (второй проход под линией).
    u = across(32, 4)
    a = np.exp(-(u / 0.48) ** 2) * np.clip(1 - u * u, 0, 1) ** 1.5
    return smooth(a)


def circle_flare():
    # Блик «острия пера» и вспышек: горячая точка, четыре длинных луча и четыре коротких.
    n = 64
    y, x = (np.mgrid[0:n, 0:n] + 0.5) / n * 2 - 1
    r = np.hypot(x, y)
    core = np.exp(-(r / 0.07) ** 2) + 0.55 * np.exp(-(r / 0.22) ** 2)
    halo = 0.22 * np.clip(1 - r, 0, 1) ** 2
    rays = np.maximum(np.exp(-(y / 0.022) ** 2) * np.clip(1 - np.abs(x), 0, 1) ** 2.2,
                      np.exp(-(x / 0.022) ** 2) * np.clip(1 - np.abs(y), 0, 1) ** 2.2)
    d1, d2 = (x + y) / math.sqrt(2), (x - y) / math.sqrt(2)
    diag = 0.55 * np.maximum(np.exp(-(d1 / 0.02) ** 2) * np.clip(1 - np.abs(d2) / 0.55, 0, 1) ** 2,
                             np.exp(-(d2 / 0.02) ** 2) * np.clip(1 - np.abs(d1) / 0.55, 0, 1) ** 2)
    a = core + halo + rays + diag
    return smooth(a * np.clip(1 - r * r, 0, 1) ** 0.5)


def circle_disc():
    # Мягкое пятно света под кругом (подсветка земли), чуть ярче у края.
    n = 64
    y, x = (np.mgrid[0:n, 0:n] + 0.5) / n * 2 - 1
    r = np.hypot(x, y)
    a = 0.55 * np.clip(1 - r, 0, 1) ** 1.4 + 0.45 * np.exp(-((r - 0.86) / 0.1) ** 2)
    return smooth(a * (r < 1))


def circle_dot():
    # Ровный круглый «узел» (вершины звёзд, точки-звёзды) — мягкий диск без лучей.
    n = 32
    y, x = (np.mgrid[0:n, 0:n] + 0.5) / n * 2 - 1
    r = np.hypot(x, y)
    a = np.clip((1 - r) / 0.35, 0, 1) ** 1.2 * 0.9 + 0.35 * np.exp(-(r / 0.25) ** 2)
    return smooth(a)


# ---------------------------------------------------------------- эффекты в мире

def fx_wave():
    # Ударная волна: яркая передняя кромка (U=1 — снаружи) и мягкий хвост внутрь.
    u = (across(32, 4) + 1) / 2
    front = np.exp(-((u - 0.86) / 0.055) ** 2)
    tail = 0.28 * np.clip(u / 0.86, 0, 1) ** 3 * (u < 0.86) + 0.28 * (u >= 0.86) * np.exp(-((u - 0.86) / 0.05) ** 2)
    return smooth((front + tail) * np.clip((1 - u) / 0.06, 0, 1))


def periodic_noise(w, h, seed, sigmas, weights, aniso=(1.0, 1.0)):
    """Бесшовный шум: белый шум, размытый в частотной области (свёртка по кругу) на нескольких масштабах."""
    rng = np.random.default_rng(seed)
    base = rng.standard_normal((h, w))
    f = np.fft.fft2(base)
    ky = np.fft.fftfreq(h)[:, None]
    kx = np.fft.fftfreq(w)[None, :]
    out = np.zeros((h, w))
    for s, wt in zip(sigmas, weights):
        g = np.exp(-2 * (math.pi * s) ** 2 * ((kx * aniso[0]) ** 2 + (ky * aniso[1]) ** 2))
        layer = np.real(np.fft.ifft2(f * g))
        layer /= layer.std() + 1e-9
        out += wt * layer
    out -= out.min()
    out /= out.max()
    return out


def fx_noise():
    n = periodic_noise(128, 128, 7, [2.0, 5.0, 12.0], [0.25, 0.4, 0.5])
    return smooth(np.clip((n - 0.18) / 0.72, 0, 1) ** 1.3)


def fx_streak():
    # Полосы энергии вдоль V (лучи, столпы, смерч): шум, вытянутый по вертикали.
    # Края по U мягко гаснут, чтобы лента с этой текстурой не имела жёсткой кромки.
    n = periodic_noise(64, 128, 11, [1.2, 3.0], [0.5, 0.6], aniso=(0.7, 6.0))
    win = np.clip(1 - across(64, 128) ** 2, 0, 1) ** 0.9
    return smooth((0.3 + 0.7 * np.clip((n - 0.2) / 0.65, 0, 1) ** 1.4) * win)


def fx_hex():
    # Соты барьера: тонкие светящиеся рёбра шестиугольников, бесшовно (2 периода по X, 4 по Y).
    w, h = 128, 148
    y, x = np.mgrid[0:h, 0:w] + 0.5
    px = x / w * 2 * 3.0          # период по X — 3 единицы (два столбца сот)
    py = y / h * 4 * math.sqrt(3)  # период по Y — √3
    best = np.full((h, w), 1e9)
    second = np.full((h, w), 1e9)
    for cx0 in range(-2, 9):
        for cy0 in range(-2, 10):
            cx = cx0 * 1.5
            cy = cy0 * math.sqrt(3) + (math.sqrt(3) / 2 if cx0 % 2 else 0)
            d = np.hypot(px - cx, py - cy)
            second = np.where(d < best, best, np.minimum(second, d))
            best = np.minimum(best, d)
    edge = second - best          # 0 на ребре между двумя центрами
    a = np.exp(-(edge / 0.07) ** 2) + 0.35 * np.exp(-(edge / 0.25) ** 2) + 0.06
    return smooth(a)


TEXTURES = {
    "circle_line": circle_line,
    "circle_glow": circle_glow,
    "circle_flare": circle_flare,
    "circle_disc": circle_disc,
    "circle_dot": circle_dot,
    "fx_wave": fx_wave,
    "fx_noise": fx_noise,
    "fx_streak": fx_streak,
    "fx_hex": fx_hex,
}


def sheet(images, scale=4):
    pad = 8
    w = sum(im.width * scale + pad for im in images) + pad
    h = max(im.height * scale for im in images) + 2 * pad
    out = Image.new("RGBA", (w, h), (12, 10, 20, 255))
    x = pad
    for im in images:
        big = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
        out.alpha_composite(big, (x, pad))
        x += big.width + pad
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--preview", type=Path, help="только превью в эту папку (текстуры в src не трогаются)")
    args = ap.parse_args()
    images = {name: fn() for name, fn in TEXTURES.items()}
    if args.preview:
        args.preview.mkdir(parents=True, exist_ok=True)
        sheet(list(images.values())).save(args.preview / "circle_textures.png")
        print(f"превью в {args.preview}")
        return
    OUT.mkdir(parents=True, exist_ok=True)
    for name, im in images.items():
        im.save(OUT / f"{name}.png", format="PNG")
    print(f"записано {len(images)} текстур в {OUT}")


if __name__ == "__main__":
    main()
