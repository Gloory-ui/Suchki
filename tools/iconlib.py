"""Рисование значков заклинаний: медальон 32×32 в цвете стихии и символ заклинания из простых фигур.

Значки стихий лежат в tools/spellicons/<стихия>.py — по функции на заклинание, имя функции = id заклинания:

    from iconlib import spell, star, teardrop

    @spell("fire")                      # стихия = School.id: fire, water, wind, earth, lightning, light, dark,
    def fire_arrow(s):                  #   space, life, mind, summon, fusion
        s("a").poly([...])              # слой цвета «a» (акцент стихии); координаты — от центра, y вниз
        s("m").circle(0, 0, 3)          # слой светлого основного цвета
        s("h", bevel=False).line([(-4, 0), (4, 0)], 1.0)   # блик без объёма

Параметры @spell(element, accent=None, combo=None):
- accent — другой цвет символа (ключ ACCENT_HEX или стихии): @spell("water", accent="ice") — лёд в стихии воды;
- combo — стихии слияния: @spell("fusion", combo=("fire", "dark")) — диск медальона из секторов этих стихий,
  акцент символа — их смесь (как Spell.color() в Java).
Уже нарисованную функцию можно зарегистрировать ещё раз: register("id", "fire", функция, accent=..., combo=...).
Старые 49 рисунков лежат в tools/spellicons/classic.py (функции без регистрации — берите и переиспользуйте).

Цвета слоя s(цвет, thr=0.45, bevel=True):
- "m" — основной (светлый), "a" — акцент (цвет стихии), "h" — почти белый блик, "d" — тёмные детали, "s" — тень;
- готовые: gold, red, bone, stone, stone_d, earth, earth_d, steel, steel_d, steel_h, wood, white, black;
- любая стихия или акцент: "<имя>" (основной цвет), "<имя>_m" (светлый), "<имя>_h" (блик), "<имя>_d" (тёмный) —
  например "fire_m", "ice", "void_d"; или свой цвет: s(rgb("#ff00aa")).
Слой (методы цепочкой, координаты в пикселях от центра C, y вниз): poly(pts), ellipse(x, y, rx, ry), circle(x, y, r),
ring(x, y, r, w), arc(x, y, r, a0, a1, w) — углы по часовой от «вправо» (−90 — вверх), line(pts, w, caps=True),
rect(x0, y0, x1, y1); cut() — то же, но вырезает дырку; флаги outline_only (только контур) и dashed (пунктир).
Фигуры: star, heart, teardrop, leaf, tapered, figure, almond, sword, crescent, burst, polygon, spiral, wave, bolt,
arrow, orbit; преобразования: rot(pts, deg, about), move(pts, dx, dy).
Символ не выходит за круг радиуса SYMBOL_R ≈ 10.4 px. Значок собирается в render(): медальон, слои с 8-кратным
суперсэмплингом, объём (светлая кромка сверху-слева, тень снизу-справа), тёмный контур и свечение.
"""

import math

import numpy as np
from PIL import Image, ImageDraw

from gen_textures import (BLACK, SCHOOL_HEX, WHITE, dilate, element_disc, medallion, mix, rgb, school_icon, shift,
                          tone_ramp)

SS = 8                      # суперсэмплинг
C = 16.5                    # центр медальона в пикселях (центр пикселя 16); координаты символов — от него
SYMBOL_R = 10.4             # символ не выходит за тёмное внутреннее кольцо диска

# Дополнительные цвета символа (accent=... или s("ice")): не стихии, но частые мотивы.
ACCENT_HEX = {
    "ice": "#8FD8FF", "frost": "#C8F0FF", "blood": "#E8364C", "poison": "#8BE04A", "storm": "#B9A8FF",
    "void": "#7A5CFF", "shadow": "#6A3FA0", "sun": "#FFB82E", "moon": "#C8D8FF", "gold": "#FFD04A",
    "lava": "#FF4A1A", "crystal": "#C8A8FF", "spirit": "#9FFFF0", "time": "#E8D8A0",
}
FIRE = {"a": rgb("#FF7A2E"), "m": rgb("#FFC447"), "h": rgb("#FFF5CF")}
FIXED = {
    "gold": rgb("#ffd04a"), "red": rgb("#e8364c"), "bone": rgb("#efe8dc"),
    "stone": rgb("#b6b6be"), "stone_d": rgb("#5c5c68"), "earth": rgb("#b4895a"), "earth_d": rgb("#6a4a2c"),
    "steel": rgb("#d2d8e0"), "steel_d": rgb("#68727f"), "steel_h": rgb("#f4f7fa"), "wood": rgb("#a8743f"),
    "white": WHITE, "black": BLACK,
}


def hex_of(name):
    return SCHOOL_HEX.get(name) or ACCENT_HEX[name]


def blend(names):
    """Смесь цветов стихий (как Spell.blend в Java: среднее по каналам)."""
    return np.mean([rgb(hex_of(n)) for n in names], axis=0)


def colors(element, accent=None, combo=None):
    """Палитра диска и цвета символа для стихии (accent — другой цвет символа, combo — стихии слияния)."""
    pal = tone_ramp(SCHOOL_HEX[element])
    if accent:
        base = rgb(hex_of(accent))
    elif combo:
        base = blend(combo)
    else:
        base = rgb(SCHOOL_HEX[element])
    fire = (accent or element) == "fire" and not combo
    c = {
        "m": FIRE["m"] if fire else mix(base, WHITE, 0.58),   # основной (светлый); у огня — жёлтое пламя
        "a": base,                                            # акцент — цвет стихии
        "h": FIRE["h"] if fire else mix(base, WHITE, 0.87),   # почти белый
        "d": mix(pal[0], BLACK, 0.25),                        # тёмные детали
        "s": mix(base, pal[0], 0.55),                         # тень (силуэт-тень, тёмный двойник)
        **FIXED,
    }
    for name in list(SCHOOL_HEX) + list(ACCENT_HEX):
        col = rgb(hex_of(name))
        c.setdefault(name, col)                    # готовые цвета (earth, gold…) важнее — старые рисунки на них
        c.setdefault(f"{name}_m", FIRE["m"] if name == "fire" else mix(col, WHITE, 0.58))
        c.setdefault(f"{name}_h", FIRE["h"] if name == "fire" else mix(col, WHITE, 0.87))
        c.setdefault(f"{name}_d", mix(col, BLACK, 0.55))
    return pal, c


# ---------------------------------------------------------------- векторные слои

def rot(pts, deg, about=(0.0, 0.0)):
    """Поворот точек на deg градусов (при y вниз: минус — против часовой, т. е. «вверх»)."""
    a = math.radians(deg)
    ca, sa = math.cos(a), math.sin(a)
    ox, oy = about
    return [(ox + (x - ox) * ca - (y - oy) * sa, oy + (x - ox) * sa + (y - oy) * ca) for x, y in pts]


def move(pts, dx, dy):
    return [(x + dx, y + dy) for x, y in pts]


class Layer:
    def __init__(self, color, thr=0.45, bevel=True):
        self.im = Image.new("L", (32 * SS, 32 * SS), 0)
        self.d = ImageDraw.Draw(self.im)
        self.color, self.thr, self.bevel = color, thr, bevel
        self.outline_only = False
        self.dashed = False

    @staticmethod
    def p(x, y):
        return ((C + x) * SS, (C + y) * SS)

    def poly(self, pts, fill=255):
        self.d.polygon([self.p(*q) for q in pts], fill=fill)
        return self

    def ellipse(self, x, y, rx, ry, fill=255):
        self.d.ellipse([(C + x - rx) * SS, (C + y - ry) * SS, (C + x + rx) * SS, (C + y + ry) * SS], fill=fill)
        return self

    def circle(self, x, y, r, fill=255):
        return self.ellipse(x, y, r, r, fill)

    def ring(self, x, y, r, w, fill=255):
        R = r + w / 2
        self.d.ellipse([(C + x - R) * SS, (C + y - R) * SS, (C + x + R) * SS, (C + y + R) * SS],
                       outline=fill, width=max(1, round(w * SS)))
        return self

    def arc(self, x, y, r, a0, a1, w, fill=255):
        """Дуга: углы в градусах по часовой от направления вправо (−90 — вверх)."""
        R = r + w / 2
        self.d.arc([(C + x - R) * SS, (C + y - R) * SS, (C + x + R) * SS, (C + y + R) * SS], a0, a1,
                   fill=fill, width=max(1, round(w * SS)))
        return self

    def line(self, pts, w, fill=255, caps=True):
        self.d.line([self.p(*q) for q in pts], fill=fill, width=max(1, round(w * SS)), joint="curve")
        if caps:
            for q in (pts[0], pts[-1]):
                self.circle(q[0], q[1], w / 2, fill)
        return self

    def rect(self, x0, y0, x1, y1, fill=255):
        self.d.rectangle([self.p(x0, y0), self.p(x1, y1)], fill=fill)
        return self

    def cut(self):
        """Тот же слой, но рисует «дырку» (fill=0) — пример: layer.cut().circle(...)."""
        return _Cut(self)

    def mask(self):
        a = np.asarray(self.im.resize((32, 32), Image.BOX), dtype=float) / 255
        m = a >= self.thr
        if self.outline_only:
            inner = m & shift(m, 1, 0) & shift(m, -1, 0) & shift(m, 0, 1) & shift(m, 0, -1)
            m = m & ~inner
        if self.dashed:
            y, x = np.mgrid[0:32, 0:32]
            m = m & ((x + y) % 3 != 0)
        return m


class _Cut:
    def __init__(self, layer):
        self.layer = layer

    def __getattr__(self, name):
        fn = getattr(self.layer, name)
        return lambda *a, **k: fn(*a, **{**k, "fill": 0})


class Sym:
    """Холст символа: s("m") — новый слой цвета «m» (порядок слоёв = порядок рисования, верхний — последний)."""

    def __init__(self, c):
        self.c = c
        self.layers = []

    def __call__(self, color, **kw):
        lay = Layer(self.c[color] if isinstance(color, str) else np.asarray(color, dtype=float), **kw)
        self.layers.append(lay)
        return lay


# ---------------------------------------------------------------- готовые фигуры

def star(lay, x, y, R, r, n=4, rot_deg=-90.0):
    pts = []
    for k in range(2 * n):
        a = math.radians(rot_deg + 180.0 * k / n)
        rr = R if k % 2 == 0 else r
        pts.append((x + rr * math.cos(a), y + rr * math.sin(a)))
    return lay.poly(pts)


def heart(lay, x, y, s):
    """Сердце: центр (x, y), полуширина ≈ 2s."""
    lay.circle(x - s, y - 0.4 * s, s * 1.05).circle(x + s, y - 0.4 * s, s * 1.05)
    return lay.poly([(x - 2.0 * s, y), (x + 2.0 * s, y), (x, y + 2.15 * s)])


def teardrop(lay, x, y, r, tip, angle=-90.0):
    """Капля: круг радиуса r в (x, y), остриё на расстоянии tip от центра в направлении angle."""
    a = math.radians(angle)
    tx, ty = x + tip * math.cos(a), y + tip * math.sin(a)
    th = math.acos(min(1.0, r / tip))
    p1 = (x + r * math.cos(a + th), y + r * math.sin(a + th))
    p2 = (x + r * math.cos(a - th), y + r * math.sin(a - th))
    lay.circle(x, y, r)
    return lay.poly([p1, (tx, ty), p2, (x, y)])


def leaf(lay, p0, p1, w, n=16):
    (x0, y0), (x1, y1) = p0, p1
    dx, dy = x1 - x0, y1 - y0
    ln = math.hypot(dx, dy)
    nx, ny = -dy / ln, dx / ln
    side1, side2 = [], []
    for i in range(n + 1):
        t = i / n
        hw = w * math.sin(math.pi * t) ** 0.8
        cx, cy = x0 + dx * t, y0 + dy * t
        side1.append((cx + nx * hw, cy + ny * hw))
        side2.append((cx - nx * hw, cy - ny * hw))
    return lay.poly(side1 + side2[::-1])


def tapered(lay, p0, p1, w0, w1):
    (x0, y0), (x1, y1) = p0, p1
    ln = math.hypot(x1 - x0, y1 - y0)
    nx, ny = -(y1 - y0) / ln, (x1 - x0) / ln
    return lay.poly([(x0 + nx * w0, y0 + ny * w0), (x1 + nx * w1, y1 + ny * w1),
                     (x1 - nx * w1, y1 - ny * w1), (x0 - nx * w0, y0 - ny * w0)])


def figure(lay, x, y=0.0, k=1.0):
    """Силуэт человека ростом ≈16k, ступни на y+8.5k; между головой и плечами — пиксель зазора."""
    lay.circle(x, y - 6.0 * k, 2.3 * k)
    return lay.poly([(x - 3.3 * k, y - 2.4 * k), (x + 3.3 * k, y - 2.4 * k), (x + 2.6 * k, y + 3.6 * k),
                     (x + 3.0 * k, y + 8.5 * k), (x + 0.9 * k, y + 8.5 * k), (x, y + 4.4 * k),
                     (x - 0.9 * k, y + 8.5 * k), (x - 3.0 * k, y + 8.5 * k), (x - 2.6 * k, y + 3.6 * k)])


def almond(lay, w, h, x=0.0, y=0.0, n=20):
    top = [(x - w + 2 * w * i / n, y - h * math.sin(math.pi * i / n)) for i in range(n + 1)]
    bot = [(x + w - 2 * w * i / n, y + h * math.sin(math.pi * i / n)) for i in range(n + 1)]
    return lay.poly(top + bot)


def sword(s, angle, length=17.0, blade="m", hilt="a"):
    """Меч вдоль направления angle (через центр): остриё вперёд."""
    L = length / 2
    bl = [(L, 0), (L - 2.4, -1.25), (-L + 5.2, -1.25), (-L + 5.2, 1.25), (L - 2.4, 1.25)]
    s(blade).poly(rot(bl, angle))
    s(hilt).poly(rot([(-L + 5.2, -3.6), (-L + 6.6, -3.6), (-L + 6.6, 3.6), (-L + 5.2, 3.6)], angle))
    s(hilt).poly(rot([(-L, -0.9), (-L + 5.2, -0.9), (-L + 5.2, 0.9), (-L, 0.9)], angle))


def crescent(lay, x, y, r, dx, dy, r2=None):
    """Полумесяц: круг (x, y, r) минус круг, сдвинутый на (dx, dy) (радиус r2, по умолчанию r)."""
    lay.circle(x, y, r)
    lay.cut().circle(x + dx, y + dy, r if r2 is None else r2)
    return lay


def burst(lay, x, y, R, r, n=8, rot_deg=-90.0):
    """Взрыв-звезда: n лучей длиной R, впадины r (то же, что star с большим n)."""
    return star(lay, x, y, R, r, n, rot_deg)


def polygon(lay, x, y, r, n, rot_deg=-90.0):
    """Правильный n-угольник радиуса r."""
    return lay.poly([(x + r * math.cos(math.radians(rot_deg + 360.0 * k / n)),
                      y + r * math.sin(math.radians(rot_deg + 360.0 * k / n))) for k in range(n)])


def spiral(lay, x, y, r0, r1, turns, w, rot_deg=0.0, n=160):
    """Спираль от радиуса r0 до r1 за turns оборотов (по часовой при y вниз), линия толщиной w."""
    pts = []
    for k in range(n + 1):
        t = k / n
        a = math.radians(rot_deg) + t * turns * 2 * math.pi
        r = r0 + (r1 - r0) * t
        pts.append((x + r * math.cos(a), y + r * math.sin(a)))
    return lay.line(pts, w)


def wave(lay, x0, x1, y, amp, periods, w, phase=0.0, n=60):
    """Волна-синусоида от x0 до x1 на высоте y."""
    pts = [(x0 + (x1 - x0) * k / n, y + amp * math.sin(phase + 2 * math.pi * periods * k / n)) for k in range(n + 1)]
    return lay.line(pts, w)


def bolt(lay, pts, w_glow=3.4, core=None, w_core=1.4):
    """Молния по ломаной: слой lay — свечение толщиной w_glow; core — слой ядра (толщина w_core)."""
    lay.line(pts, w_glow)
    if core is not None:
        core.line(pts, w_core)
    return lay


def arrow(lay, p0, p1, w, head=3.0):
    """Стрелка от p0 к p1: древко толщиной w и треугольный наконечник длиной head."""
    (x0, y0), (x1, y1) = p0, p1
    ln = math.hypot(x1 - x0, y1 - y0)
    ux, uy = (x1 - x0) / ln, (y1 - y0) / ln
    bx, by = x1 - ux * head, y1 - uy * head
    lay.line([(x0, y0), (bx, by)], w)
    return lay.poly([(x1, y1), (bx - uy * head * 0.7, by + ux * head * 0.7), (bx + uy * head * 0.7, by - ux * head * 0.7)])


def orbit(lay, x, y, rx, ry, w, rot_deg=0.0, a0=0.0, a1=360.0, n=72):
    """Наклонённый эллипс-орбита (или его дуга от a0 до a1 градусов) толщиной w."""
    a = math.radians(rot_deg)
    pts = []
    for k in range(n + 1):
        t = math.radians(a0 + (a1 - a0) * k / n)
        ex, ey = rx * math.cos(t), ry * math.sin(t)
        pts.append((x + ex * math.cos(a) - ey * math.sin(a), y + ex * math.sin(a) + ey * math.cos(a)))
    return lay.line(pts, w, caps=a1 - a0 < 360)


# ---------------------------------------------------------------- реестр

class Icon:
    def __init__(self, sid, element, fn, accent=None, combo=None, origin=""):
        self.id, self.element, self.fn, self.accent, self.combo, self.origin = sid, element, fn, accent, combo, origin


REGISTRY = {}


def register(sid, element, fn, accent=None, combo=None):
    """Зарегистрировать значок заклинания sid стихии element, нарисованный функцией fn(s)."""
    if element not in SCHOOL_HEX:
        raise ValueError(f"значок {sid}: неизвестная стихия {element!r} (есть: {', '.join(SCHOOL_HEX)})")
    if accent is not None and accent not in SCHOOL_HEX and accent not in ACCENT_HEX:
        raise ValueError(f"значок {sid}: неизвестный accent {accent!r}")
    for e in combo or ():
        if e not in SCHOOL_HEX or e == "fusion":
            raise ValueError(f"значок {sid}: в combo неизвестная стихия {e!r}")
    if sid in REGISTRY:
        raise ValueError(f"значок {sid} уже есть ({REGISTRY[sid].origin})")
    REGISTRY[sid] = Icon(sid, element, fn, accent, tuple(combo) if combo else None, getattr(fn, "__module__", ""))
    return fn


def spell(element, accent=None, combo=None):
    """Декоратор: значок для заклинания с id = имени функции."""
    def reg(fn):
        return register(fn.__name__, element, fn, accent, combo)
    return reg


# ---------------------------------------------------------------- сборка значка

def draw(element, fn, accent=None, combo=None):
    """Значок: медальон стихии (у слияния с combo — сектора стихий) и символ из слоёв fn(s)."""
    pal, c = colors(element, accent, combo)
    if combo:
        cv, rim, disc = medallion(None, [tone_ramp(SCHOOL_HEX[e]) for e in combo])
    else:
        cv, rim, disc = element_disc(element)
    s = Sym(c)
    fn(s)
    inside = np.hypot(cv.x - C, cv.y - C) <= SYMBOL_R
    union = np.zeros((32, 32), dtype=bool)
    for lay in s.layers:
        m = lay.mask() & inside
        if not m.any():
            continue
        col = np.asarray(lay.color)
        cv.rgb[m] = col
        if lay.bevel:
            ul, dr = shift(m, 1, 1), shift(m, -1, -1)
            cv.rgb[m & ~ul & dr] = mix(col, WHITE, 0.38)
            cv.rgb[m & ~dr & ul] = col * 0.74
        union |= m
    ring1 = dilate(union, 1) & ~union & disc
    cv.rgb[ring1] = mix(pal[0], BLACK, 0.3)
    ring2 = dilate(union, 2) & ~dilate(union, 1) & disc
    cv.tint(ring2, c["a"], 0.24)
    cv.glow(rim | disc, mix(c["a"], WHITE, 0.4), [(1, 0.3), (2, 0.12)])
    return cv.image()


def render(sid):
    icon = REGISTRY[sid]
    return draw(icon.element, icon.fn, icon.accent, icon.combo)


def placeholder(element):
    """Значок-заглушка для заклинания без своего рисунка: значок стихии (лучше, чем текстура «нет текстуры»)."""
    return school_icon(element)
