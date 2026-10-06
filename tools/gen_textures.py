#!/usr/bin/env python3
"""Генератор текстур магии Suchki: предметы 32×32, частицы, значки эффектов, интерфейс.

Всё рисуется кодом — свои формы и палитры, ничего не берётся со стороны.
Запуск из корня мода:
    python tools/gen_textures.py                 — перезаписать текстуры
    python tools/gen_textures.py --preview DIR   — только увеличенные превью в DIR (src не трогается)
Нужны Pillow и numpy. Генерация детерминирована: повторный запуск даёт побайтно те же файлы.

Предметы — пиксель-арт: свет сверху-слева, выборочный контур (сверху-слева светлее, снизу-справа темнее),
кристаллы с гранями и лёгким ореолом. Частицы — белые (цвет задаёт код), мягкое свечение со сглаживанием.
"""

import argparse
import math
import zlib
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / "src/main/resources/assets/suchki/textures"
SQ2 = math.sqrt(2)


# ---------------------------------------------------------------- палитры (от тёмного к светлому)

def rgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=float) / 255


def ramp(*hexes):
    return np.array([rgb(h) for h in hexes])


WOOD = ramp("#2a170c", "#4a2c16", "#6e4424", "#916034", "#b07c46", "#cfa064")
WRAP = ramp("#240d0a", "#451a14", "#68291e", "#8c3e2c", "#ad5a40")
AMETHYST = ramp("#2a1248", "#45207a", "#6634ab", "#8b55d6", "#ae82ee", "#d4b8ff", "#f6eeff")
EBONY = ramp("#07040b", "#140b1d", "#21142f", "#2f1f42", "#412a58", "#55376e")
GOLD = ramp("#3d2206", "#6e4110", "#a06a18", "#d39b2a", "#f0c84a", "#ffe98e", "#fffbe0")
AQUA = ramp("#05303c", "#0a5363", "#0f7f8e", "#18adb8", "#36d8d8", "#8ef6ee", "#eafffd")
SPECTRAL = ramp("#0b3150", "#0f4f78", "#1677a6", "#21a2cc", "#3fcbe6", "#8eeaf6", "#e6feff")
VIOLET = ramp("#170821", "#2a0f3b", "#3f1856", "#562273", "#6f2f90", "#8a42ab")
PAGES = ramp("#4e3f2a", "#7d6a4b", "#ab9670", "#d2c196", "#ece0bb", "#fbf4dc")
STONE = ramp("#1e1e22", "#3a3a40", "#5a5a62", "#7c7c86", "#a2a2ac", "#c8c8d0")
STEEL = ramp("#141c26", "#28344a", "#43536c", "#66788f", "#93a3b6", "#c4d0dc")
BOLT = ramp("#4a3000", "#8a5c00", "#c99200", "#f0c000", "#ffe050", "#fff6b8")
BONE = ramp("#1f1229", "#3e2c52", "#6c5a84", "#a090b6", "#cec2de", "#f0eaf7")

# Школы магии: обложка тома/круг значка и знак.
SCHOOLS = {
    "destruction": (ramp("#2e0806", "#5a1008", "#8c1e0c", "#bc3412", "#e05a1c", "#f4882e"), "flame"),
    "restoration": (ramp("#0a2410", "#123c1a", "#1d5a26", "#2a7a32", "#3e9c40", "#62bc58"), "leaf"),
    "alteration": (ramp("#0a1238", "#13205e", "#1e3388", "#2c4cb0", "#4068d0", "#628ae6"), "rhombus"),
    "illusion": (ramp("#2a0a2a", "#4a1248", "#701e6a", "#962e8c", "#bc48ac", "#dc6ec8"), "eye"),
    "conjuration": (ramp("#062a2a", "#0c4644", "#136662", "#1c8880", "#2aaaa0", "#4ccabe"), "spiral"),
    "light": (ramp("#6a5636", "#9a8456", "#c4b080", "#e2d4a8", "#f4ecd0", "#fffcf0"), "sun"),
    "dark": (ramp("#07040c", "#110a1a", "#1c112a", "#28183a", "#36204c", "#462a60"), "crescent"),
}


# ---------------------------------------------------------------- пиксельный холст

def shift(m, dx, dy):
    """Сдвиг маски: out[y, x] = m[y - dy, x - dx], за краем — ложь."""
    out = np.zeros_like(m)
    h, w = m.shape
    out[max(dy, 0):h + min(dy, 0), max(dx, 0):w + min(dx, 0)] = \
        m[max(-dy, 0):h + min(-dy, 0), max(-dx, 0):w + min(-dx, 0)]
    return out


def dilate(mask, r):
    out = mask.copy()
    rr = int(math.ceil(r))
    for dy in range(-rr, rr + 1):
        for dx in range(-rr, rr + 1):
            if dx * dx + dy * dy <= r * r + 1e-6:
                out |= shift(mask, dx, dy)
    return out


class Canvas:
    """Пиксель-арт: части с палитрой, выборочный контур, ореолы. Координаты центров пикселей — x+0.5, y+0.5."""

    def __init__(self, w, h=None):
        h = h or w
        self.w, self.h = w, h
        self.rgb = np.zeros((h, w, 3))
        self.a = np.zeros((h, w))
        self.owner = np.full((h, w), -1)
        self.parts = []
        y, x = np.mgrid[0:h, 0:w].astype(float)
        self.x, self.y = x + 0.5, y + 0.5

    def put(self, mask, pal, idx, alpha=1.0, outline=(1, 0), outline_alpha=None):
        """Закрасить mask цветами pal[idx]. outline=(индекс для освещённой стороны, для теневой) или None."""
        pid = len(self.parts)
        self.parts.append(dict(pal=pal, outline=outline, outline_alpha=outline_alpha))
        idx = np.clip(np.broadcast_to(np.asarray(idx), mask.shape).astype(int), 0, len(pal) - 1)
        alpha = np.broadcast_to(np.asarray(alpha, dtype=float), mask.shape)
        self.rgb[mask] = pal[idx][mask]
        self.a[mask] = alpha[mask]
        self.owner[mask] = pid
        return pid

    def outline(self):
        """Крайние пиксели каждой части: сверху/слева — светлее, снизу/справа — темнее."""
        empty = self.owner < 0
        pad = np.pad(empty, 1, constant_values=True)
        up, down, left, right = pad[:-2, 1:-1], pad[2:, 1:-1], pad[1:-1, :-2], pad[1:-1, 2:]
        lit = up.astype(int) + left
        dark = down.astype(int) + right
        border = ~empty & (up | down | left | right)
        self.border = border
        for pid, p in enumerate(self.parts):
            if p["outline"] is None:
                continue
            m = border & (self.owner == pid)
            li, di = p["outline"]
            self.rgb[m & (lit > dark)] = p["pal"][li]
            self.rgb[m & (lit <= dark)] = p["pal"][di]
            if p["outline_alpha"] is not None:
                self.a[m] = p["outline_alpha"]

    def rim(self, pid, lit, dark, alpha=None):
        """Кромка — второй ряд пикселей внутри контура части (после outline): сверху/слева lit, иначе dark."""
        part = self.owner == pid
        b = self.border & part
        pad = np.pad(b, 1)
        up, down, left, right = pad[:-2, 1:-1], pad[2:, 1:-1], pad[1:-1, :-2], pad[1:-1, 2:]
        m = part & ~b & (up | down | left | right)
        pal = self.parts[pid]["pal"]
        is_lit = (up.astype(int) + left) > (down.astype(int) + right)
        self.rgb[m & is_lit] = pal[lit]
        self.rgb[m & ~is_lit] = pal[dark]
        if alpha is not None:
            self.a[m] = alpha

    def glow(self, mask, color, levels):
        """Ореол вокруг mask на пустых пикселях: levels = [(радиус, альфа), ...] по возрастанию радиуса."""
        done = mask.copy()
        for r, al in levels:
            d = dilate(mask, r)
            m = d & ~done & (self.a == 0)
            self.rgb[m] = color
            self.a[m] = al
            done |= d

    def tint(self, mask, color, k):
        """Подмешать цвет к уже нарисованным пикселям (свечение на поверхности)."""
        m = mask & (self.a > 0)
        self.rgb[m] = self.rgb[m] * (1 - k) + np.asarray(color) * k

    def px(self, x, y, color, alpha=1.0):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.rgb[y, x] = color
            self.a[y, x] = alpha

    def image(self):
        out = np.zeros((self.h, self.w, 4))
        out[..., :3] = self.rgb * (self.a[..., None] > 0)
        out[..., 3] = self.a
        return Image.fromarray(np.round(np.clip(out, 0, 1) * 255).astype(np.uint8), "RGBA")


def diag(cv, c=32.5):
    """Координаты вдоль диагонали «снизу-слева → вверх-вправо»: u — вдоль (0 в левом нижнем углу), v — поперёк
    (плюс — вниз-вправо, в тень). Линия v=0: x+y=c; при полуцелом c древко в 4 пикселя ровной лесенкой."""
    u = (cv.x - cv.y + cv.h) / SQ2
    v = (cv.x + cv.y - c) / SQ2
    return u, v


def hash01(*keys):
    """Детерминированный «шум» 0..1 для узоров (сучки, крупинки) — одинаков при каждом запуске."""
    h = np.zeros(np.broadcast(*keys).shape, dtype=np.int64)
    for i, k in enumerate(keys):
        h = h * 73856093 ^ (np.floor(np.asarray(k)).astype(np.int64) * (19349663 + 83492791 * i))
    h = (h ^ (h >> 13)) * 1274126177
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


# ---------------------------------------------------------------- знаки школ 13×13 («#» основной, «+» блик, «-» тень)

FLAME = """
......#......
.....##......
.....###.....
....####.....
....#####..#.
...######.##.
..####+#####.
..####++####.
.####+++####.
.###++++####.
.###+++++##..
..###+++###..
...#######...
"""

EYE = """
.............
.............
.....###.....
...##...##...
..#..###..#..
.#..#++##..#.
#...#+-##...#
.#..#####..#.
..#..###..#..
...##...##...
.....###.....
.............
.............
"""

SUN = """
......#......
......#......
..#.......#..
...#.###.#...
....#+++#....
...#++###....
##.#+####.##.
...######....
....#####....
...#.###.#...
..#.......#..
......#......
......#......
"""


def parse(art):
    rows = [r for r in art.strip("\n").split("\n")]
    return np.array([list(r.ljust(13, ".")) for r in rows])


def sym_leaf():
    g = np.full((13, 13), ".")
    y, x = np.mgrid[0:13, 0:13] + 0.5
    s = ((x - 6.5) - (y - 6.5)) / SQ2          # вдоль листа (вверх-вправо)
    t = ((x - 6.5) + (y - 6.5)) / SQ2          # поперёк
    s0 = s + 0.7
    body = (np.abs(s0) <= 6.0) & (np.abs(t) <= 3.4 * (1 - (s0 / 6.0) ** 2) + 0.25)
    g[body] = "#"
    g[body & (t < -1.1)] = "+"
    g[body & (np.abs(t) < 0.5) & (s0 > -5.2) & (s0 < 4.6)] = "-"
    stem = (np.abs(t) < 0.5) & (s0 < -4.8) & (s0 > -8.2)
    g[stem] = "#"
    return g


def sym_rhombus():
    g = np.full((13, 13), ".")
    for j in range(13):
        for i in range(13):
            dx, dy = i - 6, j - 6
            d = abs(dx) + abs(dy)
            if d in (5, 6):
                g[j, i] = "#"
            elif d <= 3:
                if dx <= 0 and dy <= 0:
                    g[j, i] = "+"
                elif dx > 0 and dy > 0:
                    g[j, i] = "-"
                else:
                    g[j, i] = "#"
    return g


def sym_spiral():
    g = np.full((13, 13), ".")
    for k in range(2000):
        th = k / 2000 * 4.3 * math.pi
        r = 0.6 + th * 0.43
        x, y = 6.5 + r * math.cos(th), 6.5 - r * math.sin(th)
        i, j = int(math.floor(x)), int(math.floor(y))
        if 0 <= i < 13 and 0 <= j < 13:
            g[j, i] = "#"
    g[6, 6] = "+"
    return g


def sym_crescent():
    g = np.full((13, 13), ".")
    y, x = np.mgrid[0:13, 0:13] + 0.5
    outer = (x - 6.2) ** 2 + (y - 6.5) ** 2 <= 6.0 ** 2
    inner = (x - 8.6) ** 2 + (y - 5.4) ** 2 <= 5.0 ** 2
    moon = outer & ~inner
    g[moon] = "#"
    g[moon & (x < 3.5)] = "+"
    for (i, j) in [(10, 8), (9, 9), (10, 9), (11, 9), (10, 10)]:
        g[j, i] = "+"
    return g


SYMBOLS = {
    "flame": lambda: parse(FLAME),
    "leaf": sym_leaf,
    "rhombus": sym_rhombus,
    "eye": lambda: parse(EYE),
    "spiral": sym_spiral,
    "sun": lambda: parse(SUN),
    "crescent": sym_crescent,
}


def stamp(cv, sym, ox, oy, colors, shadow=None):
    """Нанести знак: colors = {"#": цвет, "+": цвет, "-": цвет}; shadow — тень на 1 px вниз-вправо (рельеф)."""
    mask = sym != "."
    if shadow is not None:
        sh = shift(mask, 1, 1) & ~mask
        for j, i in zip(*np.nonzero(sh)):
            cv.px(ox + i, oy + j, shadow)
    for j, i in zip(*np.nonzero(mask)):
        cv.px(ox + i, oy + j, colors[sym[j, i]])


# ---------------------------------------------------------------- предметы

def claws(u, v, u0, u1, inner, outer, bend_from, bend):
    """Два зубца-держателя по бокам кристалла (|v| между inner и outer), к концу загибаются внутрь."""
    shift_in = np.clip(u - bend_from, 0, None) * bend
    av = np.abs(v) + shift_in
    return (u >= u0) & (u <= u1) & (av >= inner) & (av <= outer)


def apprentice_staff():
    cv = Canvas(32)
    u, v = diag(cv)
    grain = hash01(np.round((u - v) * 0.7), 3) < 0.18
    shaft = (u >= 4.5) & (u <= 30.0) & (np.abs(v) <= 1.1)
    cv.put(shaft, WOOD, np.where(v < 0, 4, 3) - grain - (u < 6.0), outline=(1, 0))
    wrap = shaft & (u >= 10.0) & (u <= 17.0)
    stripes = np.floor((u - v) * 0.71) % 2 == 0
    cv.put(wrap, WRAP, np.where(stripes, 1, np.where(v < 0, 4, 3)), outline=(1, 0))
    # Гнездо и шестигранный кристалл аметиста, его держат два деревянных зубца.
    socket = (u >= 27.0) & (u <= 31.0) & (np.abs(v) <= 2.5)
    cv.put(socket, WOOD, np.where(v < -0.5, 4, np.where(v > 0.5, 2, 3)), outline=(1, 0))
    du = u - 30.6
    width = np.where(du < 7.0, 2.6, 2.6 * (10.6 - du) / 3.6)
    crystal = (du > 0) & (du <= 10.6) & (np.abs(v) <= width)
    facet = np.where(v < -0.9, 4, np.where(v > 0.9, 2, 5)) + (du > 7.0)
    cv.put(crystal, AMETHYST, facet, outline=(2, 1))
    prong = claws(u, v, 28.5, 35.5, 2.7, 3.95, 33.2, 0.55) & ~crystal
    cv.put(prong, WOOD, np.where(v < 0, 5, 3), outline=(3, 1))
    cv.outline()
    spec = crystal & (np.abs(u - 34.6) < 0.4) & (np.abs(v + 1.06) < 0.1)
    cv.rgb[spec] = AMETHYST[6]
    cv.glow(crystal, AMETHYST[5], [(1, 0.45), (2, 0.18)])
    for (x, y, al) in [(30, 0, 0.9), (23, 1, 0.6), (31, 9, 0.6)]:
        cv.px(x, y, AMETHYST[6], al)
    return cv.image()


def archmage_staff():
    cv = Canvas(32)
    u, v = diag(cv)
    shaft = (u >= 5.5) & (u <= 29.0) & (np.abs(v) <= 1.1)
    sheen = hash01(np.round((u - v) * 0.7), 7) < 0.12
    cv.put(shaft, EBONY, np.where(v < 0, 4, 2) + sheen, outline=(1, 0))
    ferrule = (u >= 3.4) & (u <= 6.4) & (np.abs(v) <= np.where(u < 4.5, 0.4, 1.1))
    cv.put(ferrule, GOLD, np.where(v < 0, 5, 3), outline=(2, 0))
    for c in (12.0, 19.5):
        ring = (np.abs(u - c) <= 0.75) & (np.abs(v) <= 1.8)
        cv.put(ring, GOLD, np.where(v < 0, 5, 3), outline=(2, 0))
    collar = (u >= 26.0) & (u <= 29.6) & (np.abs(v) <= 2.2)
    cv.put(collar, GOLD, np.where(v < -0.5, 5, np.where(v > 0.5, 3, 4)), outline=(2, 0))
    # Бирюзовый кристалл-ромб в золотой оправе.
    uc = 35.0
    gem = (np.abs(u - uc) / 5.7 + np.abs(v) / 3.4) <= 1.0
    gem_idx = np.where(u > uc, np.where(v < 0, 5, 3), np.where(v < 0, 4, 2))
    gem_idx = np.where((np.abs(v) < 0.4) & (u > uc - 3), 5, gem_idx)
    cv.put(gem, AQUA, gem_idx, outline=(2, 1))
    claw = claws(u, v, 28.0, 36.0, 3.0, 4.3, 32.5, 0.62) & ~gem
    cv.put(claw, GOLD, np.where(v < 0, 5, 3), outline=(3, 1))
    cv.outline()
    spec = gem & (np.abs(u - (uc + 1.4)) < 0.75) & (np.abs(v + 0.9) < 0.5)
    cv.rgb[spec] = AQUA[6]
    cv.glow(gem, AQUA[5], [(1, 0.5), (2, 0.24), (3, 0.09)])
    for (x, y, al) in [(31, 0, 0.85), (24, 1, 0.55), (31, 8, 0.55), (21, 4, 0.35)]:
        cv.px(x, y, AQUA[6], al)
    return cv.image()


def book(cv, pal, *, x0, y0, x1, y1, thick, spine, corners, clasp=False, gold_frame=False, seed=1):
    """Книга в лёгком ракурсе: обложка спереди, справа и снизу видны страницы (на thick пикселей)."""
    x, y = cv.x - 0.5, cv.y - 0.5

    def rect(dx, dy, ax0=x0, ay0=y0, ax1=x1, ay1=y1):
        return (x >= ax0 + dx) & (x <= ax1 + dx) & (y >= ay0 + dy) & (y <= ay1 + dy)

    cv.put(rect(thick, thick), pal, 1, outline=(0, 0))
    pages = np.zeros_like(cv.a, dtype=bool)
    kmap = np.zeros(cv.a.shape, dtype=int)
    for k in range(thick - 1, 0, -1):
        m = rect(k, k)
        pages |= m
        kmap[m] = k
    page_idx = np.where(kmap % 2 == 1, 4, 3)
    page_idx = np.where((x > x1) & (y > y1), 2, page_idx)
    cv.put(pages & ~rect(0, 0), PAGES, page_idx, outline=(2, 1))
    cover = rect(0, 0)
    grain = (hash01(x, y, seed) < 0.07)
    idx = np.full(cv.a.shape, 3) - grain
    idx = np.where(y == y0 + 1, 4, idx)
    idx = np.where(x >= x1 - 1, 2, idx)
    idx = np.where(y >= y1 - 1, 2, idx)
    sp = x < x0 + spine
    idx = np.where(sp, np.where(x == x0 + 1, 4, np.where(x == x0 + spine - 1, 1, 2)), idx)
    cv.put(cover, pal, idx, outline=(1, 0))
    cv.outline()
    # Рамка-тиснение на лицевой стороне.
    fx0, fy0, fx1, fy1 = x0 + spine + 1, y0 + 2, x1 - 2, y1 - 2
    frame = (((x == fx0) | (x == fx1)) & (y >= fy0) & (y <= fy1)) | (((y == fy0) | (y == fy1)) & (x >= fx0) & (x <= fx1))
    inner = ((((x == fx0 + 1) | (x == fx1 + 1)) & (y > fy0) & (y <= fy1 + 1))
             | (((y == fy0 + 1) | (y == fy1 + 1)) & (x > fx0) & (x <= fx1 + 1))) & ~frame
    if gold_frame:
        cv.rgb[frame] = GOLD[4]
        cv.rgb[frame & ((x == fx1) | (y == fy1))] = GOLD[2]
        cv.rgb[inner & cover] = pal[1]
        for (px_, py_) in [(fx0, fy0), (fx1, fy0), (fx0, fy1), (fx1, fy1)]:
            cv.px(int(px_), int(py_), GOLD[6])
    else:
        cv.rgb[frame] = pal[1]
        cv.rgb[inner & cover] = pal[4]
    # Золотые полосы на корешке.
    for by in (y0 + 3, y1 - 3):
        band = sp & (y == by) & (x > x0)
        cv.rgb[band] = GOLD[4]
        cv.rgb[sp & (y == by + 1) & (x > x0)] = GOLD[2]
    # Золотые уголки.
    for (cx, cy, sx, sy) in corners:
        for j in range(4):
            for i in range(4 - j):
                xx, yy = cx + sx * i, cy + sy * j
                c = GOLD[5] if (i == 0 or j == 0) and (sx < 0 or sy < 0) and i + j < 3 else GOLD[3]
                if i + j == 3:
                    c = GOLD[1]
                cv.px(xx, yy, c)
    if clasp:
        my = (y0 + y1) // 2
        strap = (y >= my - 1) & (y <= my + 1) & (x >= x1 - 3) & (x <= x1 + thick)
        cv.rgb[strap] = pal[1]
        cv.rgb[strap & (y == my - 1)] = pal[2]
        cv.a[strap] = 1
        for (i, j, c) in [(0, -2, 1), (1, -2, 4), (2, -2, 4), (3, -2, 2), (0, 2, 1), (1, 2, 2), (2, 2, 2), (3, 2, 1),
                          (0, -1, 4), (0, 0, 5), (0, 1, 3), (3, -1, 3), (3, 0, 2), (3, 1, 1)]:
            cv.px(x1 - 4 + i, my + j, GOLD[c])
        cv.px(x1 - 3, my, pal[0])
        cv.px(x1 - 2, my, pal[0])
    return (fx0 + fx1) // 2, (fy0 + fy1) // 2


def tome(school):
    pal, sym = SCHOOLS[school]
    cv = Canvas(32)
    # Зерно — от имени школы через crc32: hash() строк в Python случаен при каждом запуске.
    cx, cy = book(cv, pal, x0=4, y0=2, x1=25, y1=26, thick=3, spine=4,
                  corners=[(25, 2, -1, 1), (25, 26, -1, -1)], seed=zlib.crc32(school.encode()) % 97)
    g = SYMBOLS[sym]()
    if school == "light":
        colors = {"#": GOLD[3], "+": GOLD[5], "-": GOLD[1]}
        shadow = pal[1]
    elif school == "dark":
        colors = {"#": AMETHYST[4], "+": AMETHYST[6], "-": AMETHYST[2]}
        shadow = pal[0]
    else:
        colors = {"#": GOLD[4], "+": GOLD[6], "-": GOLD[2]}
        shadow = pal[0]
    stamp(cv, g, cx - 6 + 1, cy - 6, colors, shadow)
    if school == "dark":
        m = np.zeros_like(cv.a, dtype=bool)
        m[cy - 6:cy + 7, cx - 5:cx + 8] = g != "."
        cv.tint(dilate(m, 1.5) & ~m, AMETHYST[3], 0.3)
    return cv.image()


RUNE = """
....#####....
..##.....##..
.#....#....#.
.#...#.#...#.
#...#...#...#
#..#..+..#..#
#.#.......#.#
#.#########.#
#...........#
.#.........#.
.#.........#.
..##.....##..
....#####....
"""


def grimoire():
    cv = Canvas(32)
    cx, cy = book(cv, VIOLET, x0=2, y0=1, x1=25, y1=26, thick=5, spine=4,
                  corners=[(25, 1, -1, 1), (25, 26, -1, -1)], clasp=True, gold_frame=True, seed=5)
    g = parse(RUNE)
    m = np.zeros_like(cv.a, dtype=bool)
    ox, oy = cx - 6, cy - 6
    m[oy:oy + 13, ox:ox + 13] = g != "."
    cv.tint(dilate(m, 1) & ~m, AQUA[4], 0.45)
    cv.tint(dilate(m, 2) & ~dilate(m, 1), AQUA[3], 0.22)
    stamp(cv, g, ox, oy, {"#": AQUA[5], "+": AQUA[6], "-": AQUA[3]})
    return cv.image()


def spectral(kind):
    """Призрачные инструменты: бирюзово-голубые, полупрозрачные (тело ~62–72%), тёмный контур и светлая кромка."""
    cv = Canvas(32)
    u, v = diag(cv)
    handle = (u >= 4.5) & (u <= 34.0) & (np.abs(v) <= 1.1)
    fitting = np.zeros_like(handle)
    if kind == "blade":
        handle = (u >= 6.2) & (u <= 13.0) & (np.abs(v) <= 1.1)
        pommel = np.hypot(u - 5.2, v) <= 1.9
        fitting = pommel | ((np.abs(u - 13.8) <= 1.1) & (np.abs(v) <= 4.7))
        tip = 41.2
        half = np.where(u < tip - 6.0, 2.6, 2.6 * (tip - u) / 6.0)
        head = (u > 14.8) & (u <= tip) & (np.abs(v) <= half)
    elif kind == "pickaxe":
        r = np.hypot(u - 24.5, v)
        ang = np.abs(np.arctan2(v, u - 24.5))
        th = 0.75 + 1.25 * np.clip(1 - ang / 1.2, 0, 1)
        head = (np.abs(r - 10.4) <= th) & (ang <= 1.2)
    elif kind == "axe":
        blade = inside_poly(u, v, [(26.0, -0.5), (25.5, -3.0), (22.0, -6.0), (20.0, -8.6), (22.5, -10.3),
                                   (28.0, -10.7), (33.5, -9.9), (35.6, -7.8), (33.0, -5.0), (31.5, -3.0), (31.0, -0.5)])
        poll = inside_poly(u, v, [(26.5, 0.4), (30.5, 0.4), (29.8, 2.9), (27.2, 2.9)])
        head = blade | poll
        handle = handle & (u <= 32.0)
    else:  # shovel
        du = u - 26.0
        w = np.where(du < 3.0, 1.4 + du * 0.8, np.where(du < 9.0, 3.8, 3.8 * np.sqrt(np.clip(1 - ((du - 9.0) / 4.6) ** 2, 0, 1))))
        head = (du >= 0) & (du <= 13.6) & (np.abs(v) <= w)
        handle = handle & (u <= 27.0)
        fitting = (u >= 24.0) & (u <= 26.6) & (np.abs(v) <= 1.8)
    sheen_u = {"blade": 30.0, "pickaxe": 33.0, "axe": 27.5, "shovel": 33.5}[kind]
    sheen = np.abs((u + v * 0.6) - sheen_u) < 0.75
    h = cv.put(handle & ~head & ~fitting, SPECTRAL, np.where(v < 0, 3, 2), alpha=0.72, outline=(1, 0), outline_alpha=0.9)
    f = cv.put(fitting & ~head, SPECTRAL, np.where(v < 0, 5, 3), alpha=0.82, outline=(1, 0), outline_alpha=0.92)
    hd = cv.put(head, SPECTRAL, np.where(sheen, 6, np.where(v < 0, 4, 3)), alpha=np.where(sheen, 0.8, 0.62),
                outline=(1, 0), outline_alpha=0.9)
    cv.outline()
    cv.rim(hd, 6, 5, 0.86)
    cv.rim(f, 6, 4, 0.88)
    cv.rim(h, 5, 3, 0.8)
    if kind == "blade":
        fuller = (u > 16.5) & (u < 34.5) & (np.abs(v) < 0.4)
        cv.rgb[fuller] = SPECTRAL[5]
        cv.a[fuller] = 0.74
    sparks = {"blade": [(26, 1), (20, 9), (31, 8)], "pickaxe": [(11, 3), (30, 19), (24, 2)],
              "axe": [(8, 6), (21, 0), (28, 10)], "shovel": [(31, 1), (22, 6), (31, 11)]}[kind]
    for (x, y) in sparks:
        if cv.a[y, x] == 0:
            cv.px(x, y, SPECTRAL[6], 0.8)
    return cv.image()


# ---------------------------------------------------------------- значки эффектов 18×18

SKULL = """
....######....
..##++++####..
.#++++++#####.
.#+++++######.
#+++++########
#++oo####oo###
#++oo####oo###
#+++###-######
.####-#-#####.
..##########..
...#-#-#-#-#..
...#########..
....#######...
"""


def effect_hex():
    cv = Canvas(18)
    g = np.array([list(r.ljust(14, ".")) for r in SKULL.strip("\n").split("\n")])
    ox, oy = 2, 2
    m = np.zeros((18, 18), dtype=bool)
    m[oy:oy + g.shape[0], ox:ox + g.shape[1]] = g != "."
    gg = np.full((18, 18), ".")
    gg[oy:oy + g.shape[0], ox:ox + g.shape[1]] = g
    idx = np.select([gg == "+", gg == "-", gg == "o"], [4, 1, 0], 3)
    cv.put(m, BONE, idx, outline=(1, 0))
    cv.outline()
    eyes = gg == "o"
    cv.rgb[eyes] = rgb("#c23cff")
    for (x, y) in [(5, 7), (12, 7)]:
        cv.px(x, y, rgb("#f4c8ff"))
    cv.glow(m, rgb("#9a3ad8"), [(1, 0.35)])
    return cv.image()


def effect_undying():
    cv = Canvas(18)
    x, y = cv.x, cv.y
    loop = np.abs(np.hypot((x - 9) / 2.9, (y - 5.6) / 3.3) - 1.0) <= 0.4
    bar = (np.abs(y - 10.0) <= 1.0) & (np.abs(x - 9) <= 5.6)
    stem = (y >= 9.0) & (y <= 16.8) & (np.abs(x - 9) <= 1.0 + (y - 10.0).clip(0) * 0.15)
    m = loop | bar | stem
    idx = np.where((x - 9) + (y - 9) * 0.3 < -0.6, 5, np.where((x - 9) > 0.8, 3, 4))
    cv.put(m, GOLD, idx, outline=(2, 0))
    cv.outline()
    cv.glow(m, GOLD[5], [(1, 0.4), (2, 0.14)])
    cv.px(6, 3, GOLD[6])
    cv.px(4, 9, GOLD[6])
    return cv.image()


def effect_paralysis():
    cv = Canvas(18)
    x, y = cv.x, cv.y
    ring = np.abs(np.hypot(x - 9, y - 9) - 7.0) <= 0.85
    links = (np.floor(np.arctan2(y - 9, x - 9) / (math.pi / 6)) % 2 == 0)
    cv.put(ring, STEEL, np.where(links, 4, 2), outline=(1, 0))
    poly = [(11.5, 0.8), (4.6, 9.6), (8.6, 9.6), (6.2, 17.2), (13.6, 7.4), (9.6, 7.4), (12.8, 0.8)]
    bolt = inside_poly(x, y, poly)
    cv.put(bolt, BOLT, np.where(x + y * 0.35 < 11.2, 5, 3), outline=(2, 0))
    cv.outline()
    cv.glow(bolt, BOLT[5], [(1, 0.3)])
    return cv.image()


def inside_poly(x, y, pts):
    inside = np.zeros(x.shape, dtype=bool)
    n = len(pts)
    for i in range(n):
        x0, y0 = pts[i]
        x1, y1 = pts[(i + 1) % n]
        cond = (y0 > y) != (y1 > y)
        xi = (x1 - x0) * (y - y0) / ((y1 - y0) + 1e-12) + x0
        inside ^= cond & (x < xi)
    return inside


def effect_stoneskin():
    cv = Canvas(18)
    x, y = cv.x, cv.y
    shield = inside_poly(x, y, [(2.2, 1.6), (15.8, 1.6), (15.8, 9.0), (9.0, 17.0), (2.2, 9.0)])
    shield &= ~((y > 8.6) & (np.hypot(x - 9, y - 8.6) > 7.2))
    seeds = [(5, 4), (10, 3.5), (14, 5), (4.5, 9), (9, 8), (13.5, 10), (7, 13), (11, 14)]
    d = np.stack([np.hypot(x - sx, y - sy) for sx, sy in seeds])
    order = np.sort(d, axis=0)
    cell = np.argmin(d, axis=0)
    mortar = (order[1] - order[0]) < 0.9
    shade = np.array([4, 3, 2, 3, 4, 2, 3, 2])[cell]
    idx = np.where(mortar, 1, shade)
    cv.put(shield, STONE, idx, outline=(1, 0))
    cv.outline()
    for sx, sy in seeds:
        xx, yy = int(sx - 1), int(sy - 1)
        if shield[yy, xx] and not mortar[yy, xx]:
            cv.px(xx, yy, STONE[5])
    return cv.image()


# ---------------------------------------------------------------- интерфейс

def mana_bar(full):
    w, h = 182, 5
    img = np.zeros((h, w, 4))
    border = rgb("#0a1630") if full else rgb("#080c18")
    img[..., :3] = border
    img[..., 3] = 1
    if full:
        t = np.linspace(0, 1, w - 2)
        base = (1 - t)[:, None] * rgb("#2a5cff") + t[:, None] * rgb("#2fd2ff")
        hi = (1 - t)[:, None] * rgb("#8cb8ff") + t[:, None] * rgb("#b8f6ff")
        low = (1 - t)[:, None] * rgb("#1a2fa8") + t[:, None] * rgb("#1689c4")
        img[1, 1:-1, :3] = hi
        img[2, 1:-1, :3] = base
        img[3, 1:-1, :3] = low
        sparkle = hash01(np.arange(w - 2), 11) < 0.04
        img[2, 1:-1, :3][sparkle] = base[sparkle] * 0.65 + 0.35
    else:
        img[1, 1:-1, :3] = rgb("#1c2546")
        img[2, 1:-1, :3] = rgb("#151d38")
        img[3, 1:-1, :3] = rgb("#10162c")
        for k in range(1, 10):
            xx = round(k * (w - 1) / 10)
            img[1:4, xx, :3] = rgb("#0a0f1e")
    return Image.fromarray(np.round(img * 255).astype(np.uint8), "RGBA")


def medallion(pal):
    """Круглый медальон 32×32: золотой обод, диск в цвете pal (6 тонов) с бликом. Центр — пиксель 16."""
    cv = Canvas(32)
    x, y = cv.x, cv.y
    cx = cy = 16.5
    r = np.hypot(x - cx, y - cy)
    light = -((x - cx) + (y - cy)) / (SQ2 * 13)
    rim = (r <= 13.8) & (r > 11.5)
    disc = r <= 11.5
    cv.put(rim, GOLD, np.where(light > 0.25, 5, np.where(light > -0.3, 4, 3)) - (r < 12.4), outline=(2, 0))
    cv.put(disc, pal, np.clip(np.round(2.6 + light * 2.2), 1, 4).astype(int), outline=None)
    cv.outline()
    cv.rgb[disc & (r > 10.9)] = pal[0]
    gloss = disc & (np.abs(r - 9.4) < 0.6) & (np.arctan2(y - cy, x - cx) < -1.75) & (np.arctan2(y - cy, x - cx) > -2.9)
    cv.rgb[gloss] = pal[5]
    return cv, rim, disc


def school_icon(school):
    pal, sym = SCHOOLS[school]
    cv, rim, disc = medallion(pal)
    g = SYMBOLS[sym]()
    light_main = pal[5] * 0.45 + 0.55
    colors = {"#": light_main, "+": np.ones(3), "-": pal[2]}
    if school == "light":
        colors = {"#": GOLD[4], "+": GOLD[6], "-": GOLD[2]}
    stamp(cv, g, 10, 10, colors, shadow=pal[0])
    cv.glow(rim | disc, pal[5] * 0.6 + 0.4, [(1, 0.3), (2, 0.12)])
    return cv.image()


# ---------------------------------------------------------------- частицы (гладкие, белые)

def smooth(alpha):
    out = np.zeros(alpha.shape + (4,))
    out[..., :3] = 1
    out[..., 3] = np.clip(alpha, 0, 1)
    return Image.fromarray(np.round(out * 255).astype(np.uint8), "RGBA")


def spark():
    n = 32
    y, x = np.mgrid[0:n, 0:n] + 0.5
    r = np.hypot(x - 16, y - 16) / 15.5
    a = np.clip(1 - r, 0, 1) ** 2.2 + 0.6 * np.exp(-(r / 0.16) ** 2)
    return smooth(a)


def mote():
    n = 16
    y, x = np.mgrid[0:n, 0:n] + 0.5
    dx, dy = x - 8, y - 8
    r = np.hypot(dx, dy)
    core = np.exp(-(r / 1.5) ** 2)
    ray_x = np.exp(-(dy / 0.6) ** 2) * np.clip(1 - np.abs(dx) / 7.6, 0, 1) ** 1.6
    ray_y = np.exp(-(dx / 0.6) ** 2) * np.clip(1 - np.abs(dy) / 7.6, 0, 1) ** 1.6
    halo = 0.22 * np.clip(1 - r / 5.5, 0, 1) ** 2
    return smooth(np.maximum.reduce([core, ray_x, ray_y]) + halo)


# Руны: штрихи в квадрате 0..1 (свои значки, не алфавит).
GLYPHS = [
    [[(0.5, 0), (0.5, 1)], [(0.5, 0.22), (0.88, 0.02)], [(0.5, 0.5), (0.88, 0.3)]],
    [[(0.5, 0), (0.5, 1)], [(0.12, 0.12), (0.5, 0.45), (0.88, 0.12)]],
    [[(0.5, 0), (0.86, 0.36), (0.5, 0.72), (0.14, 0.36), (0.5, 0)], [(0.5, 0.72), (0.5, 1)]],
    [[(0.18, 0), (0.18, 1)], [(0.82, 0), (0.82, 1)], [(0.18, 0.3), (0.82, 0.7)]],
    [[(0.5, 0), (0.5, 1)], [(0.12, 0.36), (0.5, 0), (0.88, 0.36)], [(0.26, 0.78), (0.74, 0.78)]],
    [[(0.16, 0), (0.84, 0)], [(0.16, 0), (0.84, 1)], [(0.84, 0), (0.16, 1)], [(0.16, 1), (0.84, 1)]],
    [[(0.5, 0), (0.5, 1)], "circle", [(0.2, 0.82), (0.8, 0.82)]],
    [[(0.28, 0), (0.74, 0.34), (0.28, 0.64), (0.74, 1)], [(0.14, 0.5), (0.4, 0.5)]],
]


def draw_glyph(draw, k, cx, cy, h, width, angle=0.0):
    """Руна k: центр (cx, cy), высота h, ширина ≈0.62h, поворот angle (рад)."""
    w = h * 0.62
    ca, sa = math.cos(angle), math.sin(angle)

    def tr(p):
        px, py = (p[0] - 0.5) * w, (p[1] - 0.5) * h
        return (cx + px * ca - py * sa, cy + px * sa + py * ca)

    for stroke in GLYPHS[k]:
        if stroke == "circle":
            c = tr((0.5, 0.5))
            rr = h * 0.2
            draw.ellipse([c[0] - rr, c[1] - rr, c[0] + rr, c[1] + rr], outline=255, width=width)
        else:
            pts = [tr(p) for p in stroke]
            draw.line(pts, fill=255, width=width, joint="curve")


def ss_canvas(n, scale=4):
    img = Image.new("L", (n * scale, n * scale), 0)
    return img, ImageDraw.Draw(img), scale


def glowed(lines_img, n, glow_radius, glow_strength, core_gain=1.0):
    """Сжать сглаженный рисунок до n×n и добавить мягкое свечение (размытая копия)."""
    small = np.asarray(lines_img.resize((n, n), Image.LANCZOS), dtype=float) / 255
    blur = np.asarray(Image.fromarray(np.uint8(np.clip(small, 0, 1) * 255))
                      .filter(ImageFilter.GaussianBlur(glow_radius)), dtype=float) / 255
    return smooth(np.maximum(small * core_gain, 0) + blur * glow_strength)


def circle(draw, s, cx, cy, r, width):
    draw.ellipse([(cx - r) * s, (cy - r) * s, (cx + r) * s, (cy + r) * s], outline=255, width=max(1, round(width * s)))


def star(draw, s, cx, cy, r, n, step, width, rot=-math.pi / 2):
    pts = [(cx + r * math.cos(rot + 2 * math.pi * k / n), cy + r * math.sin(rot + 2 * math.pi * k / n)) for k in range(n)]
    seq = [pts[(i * step) % n] for i in range(n + 1)]
    draw.line([(px * s, py * s) for px, py in seq], fill=255, width=max(1, round(width * s)), joint="curve")
    return pts


def rune_circle():
    n = 128
    img, d, s = ss_canvas(n)
    c = 64
    circle(d, s, c, c, 57.5, 1.6)
    circle(d, s, c, c, 54.2, 1.0)
    circle(d, s, c, c, 44.2, 1.4)
    for k in range(72):
        if k % 3 == 0:
            continue
        a = 2 * math.pi * k / 72
        d.line([((c + 54.2 * math.cos(a)) * s, (c + 54.2 * math.sin(a)) * s),
                ((c + 56.6 * math.cos(a)) * s, (c + 56.6 * math.sin(a)) * s)], fill=255, width=s)
    for k in range(16):
        a = 2 * math.pi * k / 16
        r = 49.2
        draw_glyph(d, k % 8, (c + r * math.cos(a)) * s, (c + r * math.sin(a)) * s, 7.8 * s, round(1.0 * s),
                   angle=a + math.pi / 2)
    pts = star(d, s, c, c, 43.2, 7, 3, 1.1)
    star(d, s, c, c, 43.2, 7, 2, 0.8)
    for px, py in pts:
        circle(d, s, px, py, 2.6, 1.0)
    circle(d, s, c, c, 19, 1.2)
    circle(d, s, c, c, 3.2, 1.2)
    return glowed(img, n, 2.4, 0.75)


def rune_circle_inner():
    n = 128
    img, d, s = ss_canvas(n)
    c = 64
    circle(d, s, c, c, 41, 1.5)
    circle(d, s, c, c, 37, 0.9)
    for k in range(8):
        a = 2 * math.pi * k / 8 + math.pi / 8
        draw_glyph(d, (k * 3) % 8, (c + 39 * math.cos(a)) * s, (c + 39 * math.sin(a)) * s, 0.1 * s, s)
        px, py = c + 39 * math.cos(a), c + 39 * math.sin(a)
        dd = 2.4
        d.polygon([((px + dd * math.cos(a)) * s, (py + dd * math.sin(a)) * s),
                   ((px + dd * math.cos(a + math.pi / 2)) * s, (py + dd * math.sin(a + math.pi / 2)) * s),
                   ((px - dd * math.cos(a)) * s, (py - dd * math.sin(a)) * s),
                   ((px - dd * math.cos(a + math.pi / 2)) * s, (py - dd * math.sin(a + math.pi / 2)) * s)],
                  outline=255, fill=255)
    star(d, s, c, c, 35.5, 8, 3, 1.1, rot=0)
    star(d, s, c, c, 25, 4, 1, 1.0, rot=0)
    star(d, s, c, c, 25, 4, 1, 1.0, rot=math.pi / 4)
    circle(d, s, c, c, 14, 1.1)
    star(d, s, c, c, 12.5, 3, 1, 1.0)
    star(d, s, c, c, 12.5, 3, 1, 1.0, rot=math.pi / 2)
    return glowed(img, n, 2.4, 0.75)


def ring():
    n = 128
    y, x = np.mgrid[0:n, 0:n] + 0.5
    r = np.hypot(x - 64, y - 64)
    core = np.exp(-((r - 56) / 1.1) ** 2)
    glow = np.exp(-((r - 56) / 3.4) ** 2) * 0.55
    return smooth(core + glow)


def beam():
    w, h = 16, 64
    y, x = np.mgrid[0:h, 0:w] + 0.5
    ph = 2 * math.pi * y / h                     # всё периодично по высоте — полоса бесшовно прокручивается
    width = 3.0 * (1 + 0.12 * np.sin(3 * ph + 0.7) + 0.07 * np.sin(7 * ph + 2.1))
    bright = 0.86 + 0.08 * np.sin(2 * ph) + 0.06 * np.sin(5 * ph + 1.3) + 0.04 * np.sin(11 * ph + 0.4)
    dx = x - 8
    a = (np.exp(-(dx / width) ** 2) * 0.8 + np.exp(-(dx / 1.1) ** 2) * 0.55) * bright
    return smooth(a)


def glyph(k):
    n = 16
    img, d, s = ss_canvas(n)
    draw_glyph(d, k, 8 * s, 8 * s, 10 * s, round(1.25 * s))
    return glowed(img, n, 1.1, 0.7, core_gain=1.1)


# ---------------------------------------------------------------- сборка

def textures():
    t = {}
    t["item/apprentice_staff.png"] = apprentice_staff()
    t["item/archmage_staff.png"] = archmage_staff()
    t["item/grimoire.png"] = grimoire()
    for school in SCHOOLS:
        t[f"item/tome_{school}.png"] = tome(school)
    for kind in ("blade", "pickaxe", "axe", "shovel"):
        t[f"item/spectral_{kind}.png"] = spectral(kind)
    t["particle/spark.png"] = spark()
    t["particle/mote.png"] = mote()
    t["particle/rune_circle.png"] = rune_circle()
    t["particle/rune_circle_inner.png"] = rune_circle_inner()
    t["particle/ring.png"] = ring()
    t["particle/beam.png"] = beam()
    for k in range(8):
        t[f"particle/glyph_{k}.png"] = glyph(k)
    t["mob_effect/stoneskin.png"] = effect_stoneskin()
    t["mob_effect/paralysis.png"] = effect_paralysis()
    t["mob_effect/hex.png"] = effect_hex()
    t["mob_effect/undying.png"] = effect_undying()
    t["gui/mana_bar_empty.png"] = mana_bar(False)
    t["gui/mana_bar_full.png"] = mana_bar(True)
    for school in SCHOOLS:
        t[f"gui/school_{school}.png"] = school_icon(school)
    return t


def checker(w, h, cell, dark=False):
    y, x = np.mgrid[0:h, 0:w]
    c = ((x // cell + y // cell) % 2).astype(float)
    base = np.where(c > 0, 0.16, 0.22) if dark else np.where(c > 0, 0.62, 0.72)
    return Image.fromarray(np.uint8(np.stack([base] * 3 + [np.ones_like(base)], -1) * 255), "RGBA")


def sheet(images, scale, cols, dark=False, pad=8, slot=False):
    """Превью: картинки увеличены в scale раз (без сглаживания) на шахматке или на сером фоне ячейки инвентаря."""
    cells = [(name, im.resize((im.width * scale, im.height * scale), Image.NEAREST)) for name, im in images]
    cw = max(im.width for _, im in cells) + pad
    ch = max(im.height for _, im in cells) + pad
    rows = (len(cells) + cols - 1) // cols
    out = Image.new("RGBA", (cw * cols + pad, ch * rows + pad), (40, 40, 40, 255))
    for i, (_, im) in enumerate(cells):
        x, y = pad + (i % cols) * cw, pad + (i // cols) * ch
        bg = Image.new("RGBA", im.size, (139, 139, 139, 255)) if slot else checker(im.width, im.height, max(scale, 4), dark)
        bg.alpha_composite(im)
        out.paste(bg, (x, y))
    return out


def save_png(im, path):
    """PNG без метаданных (Pillow не пишет время): одинаковая картинка → побайтно одинаковый файл."""
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path, format="PNG")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--preview", type=Path, help="только превью в эту папку (текстуры в src не трогаются)")
    args = ap.parse_args()
    tex = textures()
    if not args.preview:
        for rel, im in tex.items():
            save_png(im, TEX / rel)
        print(f"записано {len(tex)} текстур в {TEX}")
    else:
        args.preview.mkdir(parents=True, exist_ok=True)
        items = [(k, v) for k, v in tex.items() if k.startswith("item/")]
        sheet(items[:8], 8, 4).save(args.preview / "items_a.png")
        sheet(items[8:], 8, 4).save(args.preview / "items_b.png")
        sheet(items, 3, 8, slot=True).save(args.preview / "items_slot.png")
        icons = [(k, v) for k, v in tex.items() if k.startswith(("mob_effect/", "gui/school"))]
        sheet(icons, 8, 6).save(args.preview / "icons.png")
        parts = [(k, v) for k, v in tex.items() if k.startswith("particle/") and v.width <= 32]
        sheet(parts, 8, 6, dark=True).save(args.preview / "particles_small.png")
        big = [(k, v) for k, v in tex.items() if k.startswith("particle/") and v.width > 32]
        sheet(big, 3, 2, dark=True).save(args.preview / "particles_big.png")
        bars = [(k, v) for k, v in tex.items() if k.startswith("gui/mana")]
        sheet(bars, 6, 1, dark=True).save(args.preview / "bars.png")
        print(f"превью в {args.preview}")


if __name__ == "__main__":
    main()
