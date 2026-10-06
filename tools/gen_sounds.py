#!/usr/bin/env python3
"""Синтез звуков магии Suchki: всё своё, на numpy — без скачанных сэмплов.

Запуск из корня мода:
    python tools/gen_sounds.py                  — записать assets/suchki/sounds/magic/*.ogg и проверить их
    python tools/gen_sounds.py --check          — только проверка готовых файлов (таблица)
    python tools/gen_sounds.py --preview DIR    — только спектрограммы в DIR (файлы в src не трогаются)
Нужны numpy, soundfile (с OGG/Vorbis), для превью — matplotlib.

Формат: моно (в Minecraft позиционируются только моно-звуки), 44100 Гц, Ogg Vorbis (качество 0,6, у петель
0,8 — см. write_ogg), пик −1 dBFS (после кодирования не выше −0,5), короткие фейды. Петли (charge_*, loop_*) бесшовные: тональные слои укладываются в длину
петли целым числом периодов, шумовые — сводятся кроссфейдом хвоста в начало, эффекты после этого кольцевые.
Повторяемость: у каждого файла своё зерно (crc32 имени), серийный номер потока Ogg — тоже от имени
(libsndfile ставит случайный), так что повторный запуск даёт побайтно те же файлы.

Приёмы: шумы с полосовыми фильтрами и свипами (оконное БПФ), колокола из негармонических партиалов,
FM, хор (пилы с вибрато через форманты «а»), гранулярные искры и треск, суб-удары с понижением тона
и сатурацией, флэнжер, реверберация — свёртка с синтезированным откликом помещения.
"""

import argparse
import math
import struct
import zlib
from pathlib import Path

import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/suchki/sounds/magic"
SR = 44100
PEAK = 10 ** (-1 / 20)          # −1 dBFS
LOOP_LEN = 3.0
LOOP_XFADE = 0.35


# ---------------------------------------------------------------- основа

def ns(d):
    return int(round(d * SR))


def tt(n):
    return np.arange(n) / SR


NOTE = {"C": -9, "C#": -8, "D": -7, "D#": -6, "E": -5, "F": -4, "F#": -3, "G": -2, "G#": -1, "A": 0, "A#": 1, "B": 2}


def hz(note):
    return 440.0 * 2 ** ((NOTE[note[:-1]] + 12 * (int(note[-1]) - 4)) / 12)


def cents(f, c):
    return f * 2 ** (c / 1200)


def norm(x):
    m = np.max(np.abs(x))
    return x / m if m > 0 else x


def sat(x, drive):
    return np.tanh(drive * x) / np.tanh(drive)


def place(buf, sig, t0):
    i = ns(t0)
    if i >= len(buf):
        return buf
    k = min(len(sig), len(buf) - i)
    buf[i:i + k] += sig[:k]
    return buf


def phase(freq, n):
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    return 2 * np.pi * np.cumsum(f) / SR


def sine(freq, n, ph0=0.0):
    return np.sin(phase(freq, n) + ph0)


def additive(freq, n, kind="saw", kmax=120, ph0=0.0):
    """Пила/квадрат/треугольник без алиасинга: гармоники гаснут плавно у 16 кГц."""
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    p = phase(f, n) + ph0
    out = np.zeros(n)
    kmax = min(kmax, int(17000 / max(1.0, float(np.min(f)))) + 1)
    for k in range(1, kmax + 1):
        if kind in ("square", "tri") and k % 2 == 0:
            continue
        lim = np.clip((16000 - k * f) / 2500, 0, 1)
        if not lim.any():
            break
        a = (((-1) ** ((k - 1) // 2)) / k ** 2) if kind == "tri" else 1 / k
        out += a * lim * np.sin(k * p)
    return out


# ---------------------------------------------------------------- шумы и фильтры

def white(rng, n):
    return rng.standard_normal(n)


def colored(rng, n, slope_db_oct):
    """Цветной шум через БПФ (−3 дБ/окт — розовый, −6 — коричневый). Периодичен по длине n."""
    X = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    f[0] = f[1]
    X *= (f / 1000) ** (slope_db_oct / 6.02)
    X[0] = 0
    return norm(np.fft.irfft(X, n))


def bp(f, fc, width_oct):
    return np.exp(-0.5 * (np.log2(np.maximum(f, 1.0) / fc) / width_oct) ** 2)


def lp(f, fc, order=2):
    return 1 / np.sqrt(1 + (f / fc) ** (2 * order))


def hp(f, fc, order=2):
    return 1 / np.sqrt(1 + (fc / np.maximum(f, 1e-3)) ** (2 * order))


def ffilt(x, gain, circular=False):
    """Неподвижный фильтр через БПФ (нулевая фаза). circular=True — для петель (сохраняет бесшовность)."""
    n = len(x)
    pad = 0 if circular else ns(0.25)
    m = n + pad
    X = np.fft.rfft(x, m)
    f = np.fft.rfftfreq(m, 1 / SR)
    return np.fft.irfft(X * gain(f), m)[:n]


def sfilt(x, gain, N=2048, hop=512):
    """Движущийся фильтр: оконное БПФ, gain(t, f) — t в секундах (столбец), f в Гц (строка)."""
    n = len(x)
    win = np.hanning(N + 1)[:-1]
    xp = np.concatenate([np.zeros(N), x, np.zeros(N + hop)])
    frames = 1 + (len(xp) - N) // hop
    idx = np.arange(N)[None, :] + hop * np.arange(frames)[:, None]
    F = np.fft.rfft(xp[idx] * win, axis=1)
    tc = (np.arange(frames) * hop + N / 2 - N) / SR
    f = np.fft.rfftfreq(N, 1 / SR)
    y = np.fft.irfft(F * gain(tc[:, None], f[None, :]), N, axis=1) * win
    out = np.zeros(len(xp))
    for i in range(frames):
        out[i * hop:i * hop + N] += y[i]
    return out[N:N + n] / 1.5


def sweep_bp(x, f0, f1, t0, t1, width=0.6, curve=1.0):
    """Полоса, центр которой ползёт от f0 к f1 (по логарифму) за время t0…t1."""
    def g(t, f):
        k = np.clip((t - t0) / max(1e-6, t1 - t0), 0, 1) ** curve
        return bp(f, f0 * (f1 / f0) ** k, width)
    return sfilt(x, g)


def smooth_noise(rng, n, rate, lo=-1.0, hi=1.0):
    """Плавная случайная кривая (для блуждания фильтров и «дыхания» громкости)."""
    k = max(4, int(n / SR * rate) + 4)
    pts = rng.uniform(lo, hi, k)
    xs = np.linspace(0, n, k)
    return np.interp(np.arange(n), xs, pts)


def flanger(x, rate, base_ms, depth_ms, mix=0.7, ph0=0.0, circular=False):
    n = len(x)
    d = (base_ms + depth_ms * (0.5 + 0.5 * np.sin(2 * np.pi * rate * tt(n) + ph0))) * SR / 1000
    pos = np.arange(n) - d
    if circular:
        pos = np.mod(pos, n)
    i0 = np.floor(pos).astype(int)
    fr = pos - i0
    a = np.where((i0 >= 0) & (i0 < n), x[np.clip(i0, 0, n - 1)], 0.0) if not circular else x[i0 % n]
    b = np.where((i0 + 1 >= 0) & (i0 + 1 < n), x[np.clip(i0 + 1, 0, n - 1)], 0.0) if not circular else x[(i0 + 1) % n]
    return x + mix * (a * (1 - fr) + b * fr)


FORMANTS = {"a": [(800, 1.0, 0.18), (1150, 0.6, 0.16), (2900, 0.25, 0.12), (3900, 0.12, 0.1)],
            "o": [(450, 1.0, 0.2), (800, 0.6, 0.18), (2830, 0.15, 0.12), (3500, 0.08, 0.1)]}


def formant(x, vowel="a", circular=False):
    peaks = FORMANTS[vowel]
    return ffilt(x, lambda f: 0.04 + sum(a * bp(f, fc, w) for fc, a, w in peaks), circular)


# ---------------------------------------------------------------- огибающие и голоса

def env(n, attack=0.004, decay=0.3, hold=0.0, t0=0.0):
    """Атака (по синусу) → удержание → экспоненциальный спад; до t0 — тишина."""
    t = tt(n) - t0
    rise = np.sin(0.5 * np.pi * np.clip(t / max(attack, 1e-5), 0, 1))
    fall = np.exp(-np.clip(t - attack - hold, 0, None) / decay)
    return np.where(t < 0, 0.0, rise * fall)


def swell(n, t_peak, t_end=None, power=2.0, decay=0.3):
    """Нарастание к t_peak (как обратный звук), затем спад."""
    t = tt(n)
    up = np.clip(t / t_peak, 0, 1) ** power
    down = np.exp(-np.clip(t - t_peak, 0, None) / decay)
    e = np.where(t < t_peak, up, down)
    if t_end is not None:
        e = e * (t < t_end)
    return e


def glide(n, f0, f1, dur, t0=0.0, curve="exp"):
    """Частота: f0 → f1 за dur секунд (по логарифму), дальше держится."""
    k = np.clip((tt(n) - t0) / dur, 0, 1)
    if curve == "exp":
        return f0 * (f1 / f0) ** k
    return f0 + (f1 - f0) * k


def thump(n, f_start=110.0, f_end=40.0, decay=0.25, drop=0.05, drive=2.0, t0=0.0):
    """Суб-удар: синус с быстрым падением тона и сатурацией."""
    t = np.clip(tt(n) - t0, 0, None)
    f = f_end + (f_start - f_end) * np.exp(-t / drop)
    x = sine(f, n) * env(n, 0.002, decay, t0=t0)
    return sat(x, drive)


BELL = [(0.5, 0.22, 1.5), (1.0, 1.0, 1.0), (2.0, 0.5, 0.62), (2.76, 0.42, 0.5), (3.0, 0.22, 0.45),
        (4.07, 0.2, 0.32), (5.43, 0.13, 0.24), (6.8, 0.08, 0.17), (8.93, 0.05, 0.12)]


def bell(n, f0, decay=0.8, t0=0.0, partials=BELL, beat=0.7, bright=1.0):
    """Колокол: негармонические партиалы, каждый — пара со сдвигом (переливы)."""
    t = np.clip(tt(n) - t0, 0, None)
    on = tt(n) >= t0
    out = np.zeros(n)
    for r, a, dm in partials:
        f = f0 * r
        if f > 16000:
            continue
        a = a * (bright if r > 2.5 else 1.0)
        e = np.exp(-t / (decay * dm)) * np.minimum(1, t / 0.0015)
        out += a * e * (np.sin(2 * np.pi * f * t) + 0.6 * np.sin(2 * np.pi * (f + beat) * t + 1.3))
    return out * on


def chord(n, freqs, kind="saw", detune=7.0, voices=3, vib_rate=5.0, vib_depth=0.004, kmax=40, rng=None, q=None):
    """Аккорд из расстроенных голосов с вибрато. q — квантование частоты (для петель)."""
    q = q or (lambda f: f)
    out = np.zeros(n)
    for i, f in enumerate(freqs):
        for v in range(voices):
            c = (v - (voices - 1) / 2) * detune
            fv = q(cents(f, c))
            ph = rng.uniform(0, 2 * np.pi) if rng is not None else 0.0
            vib = 1 + vib_depth * np.sin(2 * np.pi * q(vib_rate) * tt(n) + ph)
            out += additive(fv * vib, n, kind, kmax, ph0=ph)
    return out / max(1, len(freqs) * voices) ** 0.5


def sparkles(rng, n, rate, fmin=3000, fmax=9000, decay=(0.02, 0.12), density=None, t_from=0.0, t_to=None):
    """Искры: короткие синусоидальные «зёрна» в случайные моменты; density(t) — множитель частоты появления."""
    out = np.zeros(n)
    t_to = n / SR if t_to is None else t_to
    count = rng.poisson(rate * max(0.0, t_to - t_from))
    for _ in range(count):
        t0 = rng.uniform(t_from, t_to)
        if density is not None and rng.uniform() > density(t0):
            continue
        f = math.exp(rng.uniform(math.log(fmin), math.log(fmax)))
        d = rng.uniform(*decay)
        m = ns(min(d * 6, 0.6))
        tg = tt(m)
        g = np.sin(2 * np.pi * f * tg) * np.exp(-tg / d) * np.minimum(1, tg / 0.001) * rng.lognormal(0, 0.4)
        place(out, g, t0)
    return out


def crackles(rng, n, rate, band=(1200, 7000), dur=(0.001, 0.008), density=None, t_from=0.0, t_to=None):
    """Треск: щелчки из шума с короткой экспонентой, потом полоса band."""
    out = np.zeros(n)
    t_to = n / SR if t_to is None else t_to
    count = rng.poisson(rate * max(0.0, t_to - t_from))
    for _ in range(count):
        t0 = rng.uniform(t_from, t_to)
        if density is not None and rng.uniform() > density(t0):
            continue
        d = rng.uniform(*dur)
        m = ns(d * 5)
        g = rng.standard_normal(m) * np.exp(-tt(m) / d) * rng.lognormal(0, 0.6)
        place(out, g, t0)
    lo, hi_ = band
    return ffilt(out, lambda f: hp(f, lo, 2) * lp(f, hi_, 2))


def pings(rng, n, count, fmin, fmax, t_from, t_to, decay=(0.04, 0.2)):
    """Кристаллические «тинги»: по 3 негармонических партиала на каждый."""
    out = np.zeros(n)
    for _ in range(count):
        t0 = rng.uniform(t_from, t_to)
        f = math.exp(rng.uniform(math.log(fmin), math.log(fmax)))
        d = rng.uniform(*decay)
        m = ns(min(d * 6, 0.8))
        tg = tt(m)
        g = (np.sin(2 * np.pi * f * tg) + 0.4 * np.sin(2 * np.pi * f * 2.32 * tg) * np.exp(-tg / (d * 0.4))
             + 0.25 * np.sin(2 * np.pi * f * 3.71 * tg) * np.exp(-tg / (d * 0.25)))
        g *= np.exp(-tg / d) * np.minimum(1, tg / 0.0008) * rng.lognormal(0, 0.35)
        place(out, g, t0)
    return out


def whisper(rng, n, level_rate=3.0):
    """Шёпот: шум в «согласных» полосах, громкость — слоги со случайными паузами."""
    x = white(rng, n)
    centers = smooth_noise(rng, n, 2.0, math.log(2200), math.log(5200))
    hush = sfilt(x, lambda t, f: bp(f, np.exp(np.interp(t, tt(n), centers)), 0.45) + 0.3 * bp(f, 900, 0.5))
    syll = np.clip(smooth_noise(rng, n, level_rate * 2, -0.6, 1.0), 0, 1) ** 1.5
    return hush * syll


# ---------------------------------------------------------------- реверберация

def make_ir(seed, rt60, length, bright=9000.0, dark=1800.0, predelay=0.012, early=8):
    rng = np.random.default_rng(seed)
    n = ns(length)
    t = tt(n)
    x = rng.standard_normal(n) * np.exp(-6.91 * t / rt60)
    ir = sfilt(x, lambda tc, f: lp(f, bright * (dark / bright) ** np.clip(tc / rt60, 0, 1), 1))
    ir[:ns(predelay)] = 0
    for _ in range(early):
        p = ns(predelay + rng.uniform(0.002, 0.05))
        ir[p] += rng.uniform(0.4, 0.9) * rng.choice([-1, 1]) * np.sqrt(np.mean(ir[ns(predelay):ns(predelay) + 2000] ** 2)) * 8
    return ir / np.sqrt(np.sum(ir ** 2))


ROOMS = {
    "small": make_ir(11, 0.45, 0.6, 7000, 2000),
    "medium": make_ir(12, 1.1, 1.4, 8000, 1800),
    "large": make_ir(13, 2.2, 2.6, 9000, 1500, predelay=0.02),
    "dark": make_ir(14, 1.6, 2.0, 3500, 700, predelay=0.018),
}


def reverb(x, room, wet, circular=False):
    h = ROOMS[room]
    n = len(x)
    if circular:
        m = n
        hh = h if len(h) <= n else h[:n]
        y = np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(hh, m), m)
    else:
        m = 1 << int(math.ceil(math.log2(n + len(h))))
        y = np.fft.irfft(np.fft.rfft(x, m) * np.fft.rfft(h, m), m)[:n]
    return x + wet * y * np.max(np.abs(x)) / max(1e-9, np.max(np.abs(y)))


# ---------------------------------------------------------------- сборка: разовые и петли

def finish(x, loop=False, fade_in=0.004, fade_out=0.04):
    """Убрать постоянную составляющую и инфранизы, фейды (у разовых), пик −1 dBFS."""
    x = ffilt(x - np.mean(x), lambda f: hp(f, 28, 2), circular=loop)
    if not loop:
        n = len(x)
        a, b = ns(fade_in), ns(fade_out)
        x[:a] *= np.sin(0.5 * np.pi * np.arange(a) / a) ** 2
        x[n - b:] *= np.cos(0.5 * np.pi * np.arange(b) / b) ** 2
    return x * PEAK / np.max(np.abs(x))


def make_loop(fn, rng):
    """fn(rng, L, N, q) → (тональное длины L, шумовое длины N = L + X). Тональное строго периодично: частоты
    квантуются q к кратным 1/L, фильтры — кольцевые на длине L. Шумовое сводится кроссфейдом хвоста в начало
    (равная мощность): на стыке звучит естественное продолжение последнего сэмпла."""
    L, X = ns(LOOP_LEN), ns(LOOP_XFADE)
    q = lambda f: np.round(np.asarray(f) * LOOP_LEN) / LOOP_LEN
    tonal, noisy = fn(rng, L, L + X, q)
    w = np.arange(X) / X
    nz = noisy[:L].copy()
    nz[:X] = noisy[:X] * np.sin(0.5 * np.pi * w) + noisy[L:L + X] * np.cos(0.5 * np.pi * w)
    return tonal + nz


# ---------------------------------------------------------------- рецепты: петли зарядки

def charge_destruction(rng, L, N, q):
    hum = 0.5 * sine(q(55), L) + 0.35 * additive(q(110), L, "saw", 30) * (1 + 0.15 * np.sin(2 * np.pi * q(1 / 3) * tt(L)))
    hum = ffilt(hum, lambda f: lp(f, 600, 2), circular=True)
    roar = colored(rng, N, -3)
    wob = smooth_noise(rng, N, 3.0, math.log(380), math.log(1000))
    roar = sfilt(roar, lambda t, f: bp(f, np.exp(np.interp(t, tt(N), wob)), 0.9)) * (0.75 + 0.25 * smooth_noise(rng, N, 6.0))
    rumble = ffilt(colored(rng, N, -6), lambda f: lp(f, 160, 2))
    crack = crackles(rng, N, 22, (1500, 7500), (0.0008, 0.006))
    noisy = 0.6 * norm(roar) + 0.45 * norm(rumble) + 0.4 * norm(crack)
    return 0.35 * norm(hum), noisy


def charge_restoration(rng, L, N, q):
    notes = [hz("D4"), hz("F#4"), hz("A4"), hz("E5")]
    choir = formant(chord(L, notes, "saw", 8, 3, 5.0, 0.005, 40, rng, q), "a", circular=True)
    low = 0.3 * sine(q(hz("D3")), L)
    shimmer = (0.18 * sine(q(hz("D6")), L) + 0.12 * sine(q(hz("A6")), L)) * (0.6 + 0.4 * np.sin(2 * np.pi * q(2 / 3) * tt(L)))
    tonal = norm(choir) * 0.7 + low + shimmer
    breath = formant(white(rng, N), "a") * 0.08
    spark = sparkles(rng, N, 4, 4000, 9000, (0.04, 0.15)) * 0.25
    return tonal, breath + spark


def charge_light(rng, L, N, q):
    t = tt(L)
    trem = 0.75 + 0.25 * np.sin(2 * np.pi * q(6) * t)
    ring = (sine(q(880), L) + 0.7 * sine(q(880) + 1 / 3, L) + 0.6 * sine(q(1318.5), L) + 0.45 * sine(q(1760), L)
            + 0.2 * sine(q(2217.5), L)) * trem
    body = 0.6 * sine(q(110), L) + 0.4 * sine(q(220), L) + 0.2 * additive(q(220), L, "tri", 15)
    fm = 0.25 * np.sin(2 * np.pi * q(1760) * t + 0.8 * np.sin(2 * np.pi * q(1760 * 1.5) * t))
    tonal = 0.45 * norm(ring) + 0.4 * norm(body) + fm
    noisy = sparkles(rng, N, 9, 4000, 10000, (0.02, 0.1)) * 0.35 + ffilt(white(rng, N), lambda f: hp(f, 6000)) * 0.03
    return tonal, noisy


def charge_dark(rng, L, N, q):
    drone = (additive(q(73.33), L, "saw", 60) + additive(q(73.67), L, "saw", 60) + 0.7 * additive(q(110.0), L, "saw", 40)
             + 0.45 * additive(q(103.67), L, "saw", 40))
    drone = ffilt(drone * (0.8 + 0.2 * np.sin(2 * np.pi * q(1 / 3) * tt(L))), lambda f: lp(f, 480, 3), circular=True)
    sub = 0.5 * sine(q(36.67), L)
    noisy = whisper(rng, N) * 0.5 + ffilt(colored(rng, N, -6), lambda f: lp(f, 120)) * 0.25
    return norm(drone) * 0.75 + sub, noisy


def charge_alteration(rng, L, N, q):
    t = tt(L)
    base = additive(q(61.67), L, "saw", 80)
    res = ffilt(base, lambda f: 0.15 + bp(f, 123.3, 0.08) + 0.8 * bp(f, 185, 0.08) + 0.6 * bp(f, 370, 0.07)
                + 0.3 * bp(f, 740, 0.06), circular=True)
    bowl = (0.5 * sine(q(246.67), L) + 0.5 * sine(q(247.0), L) + 0.3 * sine(q(593.33), L) + 0.18 * sine(q(1015.0), L))
    tonal = norm(res) * 0.7 + norm(bowl) * 0.4 * (0.8 + 0.2 * np.sin(2 * np.pi * q(1 / 3) * t))
    noisy = ffilt(colored(rng, N, -6), lambda f: lp(f, 110)) * 0.3
    return tonal, noisy


def charge_illusion(rng, L, N, q):
    t = tt(L)
    notes = [hz("E4"), hz("G4"), hz("B4"), hz("F#5")]
    pad = ffilt(chord(L, notes, "saw", 9, 3, 5.0, 0.004, 40, rng, q), lambda f: lp(f, 3200, 2), circular=True)
    pad = flanger(pad, q(2 / 3), 0.8, 4.5, 0.9, circular=True)
    glass = (sine(q(hz("E6")), L) + 0.7 * sine(q(hz("B6")), L)) * (0.55 + 0.45 * np.sin(2 * np.pi * q(8) * t))
    tonal = norm(pad) * 0.7 + norm(glass) * 0.22
    noisy = sparkles(rng, N, 7, 3500, 9500, (0.03, 0.14)) * 0.3
    return tonal, noisy


def charge_conjuration(rng, L, N, q):
    t = tt(L)
    lo_, hi_ = math.log(300), math.log(3000)
    swirl = sfilt(white(rng, N), lambda tc, f: bp(f, np.exp(lo_ + (hi_ - lo_) * (0.5 + 0.5 * np.sin(2 * np.pi * (2 / 3) * tc))), 0.35)
                  + 0.7 * bp(f, np.exp(lo_ + (hi_ - lo_) * (0.5 - 0.5 * np.sin(2 * np.pi * (2 / 3) * tc))), 0.35))
    hum = ffilt(additive(q(82.33), L, "saw", 40), lambda f: lp(f, 700, 2), circular=True)
    whirl = np.sin(2 * np.pi * q(330) * t + 9 * np.sin(2 * np.pi * q(2 / 3) * t)) * 0.3
    return norm(hum) * 0.5 + whirl, norm(swirl) * 0.6


# ---------------------------------------------------------------- сигналы, изучение

def ready(rng, v, n):
    x = bell(n, hz("E6"), 0.28) + 0.5 * bell(n, hz("B6"), 0.2, t0=0.03)
    x += sparkles(rng, n, 30, 5000, 11000, (0.01, 0.06), t_to=0.3) * 0.5
    return reverb(x, "small", 0.35)


def stage(rng, v, n):
    f = glide(n, 880, 1318.5, 0.05)
    e = env(n, 0.002, 0.08)
    x = (sine(f, n) + 0.3 * sine(2 * f, n) + 0.15 * sine(2.76 * f, n) * env(n, 0.001, 0.03)) * e
    return reverb(x, "small", 0.25)


def peak(rng, v, n):
    whoosh = sweep_bp(white(rng, n), 200, 3500, 0.0, 0.3, 0.7) * swell(n, 0.28, decay=0.18)
    sub = sat(sine(45, n) * swell(n, 0.3, power=1.5, decay=0.4), 2.0)
    stab = chord(n, [hz("D3"), hz("A3"), hz("D4"), hz("F#4")], "saw", 10, 3, 5.5, 0.003, 60, rng)
    stab = sfilt(stab * env(n, 0.006, 0.42, t0=0.28), lambda t, f: lp(f, 800 + 5200 * np.exp(-np.clip(t - 0.28, 0, None) / 0.18) * (t > 0.27), 2))
    spark = sparkles(rng, n, 40, 3000, 10000, (0.02, 0.15), t_from=0.28, t_to=0.75) * 0.5
    x = 0.55 * norm(whoosh) + 0.8 * norm(sub) + 0.75 * norm(stab) + spark
    return sat(reverb(x, "large", 0.4), 1.4)


def fizzle(rng, v, n):
    hiss = sweep_bp(white(rng, n), 5000, 700, 0.0, 0.45, 0.6) * env(n, 0.005, 0.22)
    crack = crackles(rng, n, 70, (1500, 8000), density=lambda t: math.exp(-t / 0.2))
    tone = sine(glide(n, 700, 120, 0.4) * (1 + 0.03 * smooth_noise(rng, n, 30)), n) * env(n, 0.003, 0.2) * 0.4
    smoke = ffilt(colored(rng, n, -3), lambda f: lp(f, 1500)) * env(n, 0.05, 0.3) * 0.25
    return reverb(0.7 * norm(hiss) + 0.5 * norm(crack) + tone + smoke, "small", 0.25)


def learn(rng, v, n):
    x = np.zeros(n)
    for i, note in enumerate(["C5", "E5", "G5", "B5", "D6"]):
        x += bell(n, hz(note), 0.9, t0=0.065 * i) * (1 - 0.1 * i)
    pad = ffilt(chord(n, [hz("C4"), hz("E4"), hz("G4"), hz("B4"), hz("D5")], "tri", 6, 2, 5.0, 0.004, 20, rng),
                lambda f: lp(f, 2500)) * env(n, 0.22, 0.6, hold=0.2)
    rustle = np.zeros(n)
    for k in range(7):
        m = ns(rng.uniform(0.012, 0.045))
        burst = rng.standard_normal(m) * np.hanning(m) * rng.uniform(0.4, 1.0)
        place(rustle, burst, rng.uniform(0.0, 0.4))
    rustle = ffilt(rustle, lambda f: bp(f, 4200, 1.0))
    spark = sparkles(rng, n, 35, 4000, 11000, (0.02, 0.12), t_from=0.1, t_to=0.9) * 0.4
    mix_ = 0.6 * norm(x) + 0.45 * norm(pad) + 0.35 * norm(rustle) + spark
    return reverb(mix_, "large", 0.45)


# ---------------------------------------------------------------- касты

def cast_fire(rng, v, n):
    hi = [1800, 2400][v]
    whoosh = sfilt(colored(rng, n, -3), lambda t, f: bp(f, np.where(t < 0.22, 250 * (hi / 250) ** np.clip(t / 0.22, 0, 1),
                                                                     hi * (800 / hi) ** np.clip((t - 0.22) / 0.4, 0, 1)), 1.0))
    whoosh *= env(n, 0.03, 0.28 + 0.06 * v)
    roar = ffilt(colored(rng, n, -6), lambda f: lp(f, 650)) * env(n, 0.02, 0.35)
    crack = crackles(rng, n, 45, (1500, 7000), density=lambda t: math.exp(-t / 0.35))
    x = 0.8 * norm(whoosh) + 0.5 * norm(roar) + 0.35 * norm(crack) + 0.45 * thump(n, 95, 45, 0.12)
    return sat(reverb(x, "small", 0.22), 1.3)


def cast_frost(rng, v, n):
    click = np.zeros(n)
    place(click, ffilt(white(rng, ns(0.006)) * np.hanning(ns(0.006)), lambda f: hp(f, 3000)), 0.0)
    whoosh = sweep_bp(white(rng, n), 7000, 2400 + 400 * v, 0.0, 0.3, 0.5) * env(n, 0.004, 0.22)
    ping = pings(rng, n, 9 + 2 * v, 2500, 7500, 0.0, 0.16, (0.04, 0.2))
    crunch = crackles(rng, n, 160, (2500, 9000), (0.0005, 0.003), t_to=0.14)
    x = 0.5 * norm(click) + 0.6 * norm(whoosh) + 0.55 * norm(ping) + 0.4 * norm(crunch) + 0.25 * thump(n, 150, 75, 0.06)
    return reverb(x, "small", 0.28)


def cast_shock(rng, v, n):
    zap = additive(glide(n, 2600 - 300 * v, 180, 0.12), n, "saw", 30) * env(n, 0.001, 0.07)
    jitter = 1 + 0.06 * smooth_noise(rng, n, 120)
    flutter = 0.5 + 0.5 * (smooth_noise(rng, n, 50) > -0.2)
    buzz = sfilt(additive(110 * jitter, n, "saw", 80), lambda t, f: bp(f, 900, 1.2)) * env(n, 0.004, 0.25) * flutter
    crack = crackles(rng, n, 220, (2000, 10000), (0.0004, 0.003), density=lambda t: math.exp(-t / 0.15))
    x = 0.6 * norm(zap) + 0.6 * norm(buzz) + 0.55 * norm(crack)
    return sat(reverb(x, "small", 0.2), 2.0)


def cast_holy(rng, v, n):
    f0 = [hz("E5"), hz("F#5")][v]
    b = bell(n, f0, 0.6) + 0.45 * bell(n, f0 * 1.5, 0.45, t0=0.02)
    whoosh = sweep_bp(white(rng, n), 800, 6000, 0.0, 0.3, 0.7) * swell(n, 0.12, decay=0.2) * 0.6
    choir = formant(chord(n, [f0 / 2, f0 / 2 * 1.26, f0 / 2 * 1.5], "saw", 8, 3, 5.0, 0.004, 40, rng), "a") * env(n, 0.08, 0.4)
    spark = sparkles(rng, n, 25, 4500, 11000, (0.02, 0.1), t_to=0.5) * 0.4
    return reverb(0.6 * norm(b) + 0.4 * norm(whoosh) + 0.5 * norm(choir) + spark, "large", 0.42)


def cast_dark(rng, v, n):
    t_hit = 0.12 + 0.03 * v
    cut = np.clip((t_hit + 0.006 - tt(n)) / 0.012, 0, 1)          # «вдох» гаснет за 12 мс, без щелчка
    pre = ffilt(colored(rng, n, -3), lambda f: lp(f, 900)) * np.clip(tt(n) / t_hit, 0, 1) ** 3 * cut
    hit = thump(n, 72, 30, 0.35, drive=2.5, t0=t_hit)
    growl = additive(55, n, "saw", 60) + additive(55 * 1.06, n, "saw", 60)
    growl = sfilt(growl * env(n, 0.01, 0.4, t0=t_hit), lambda t, f: lp(f, 200 + 900 * np.exp(-np.clip(t - t_hit, 0, None) / 0.25), 2))
    whoosh = sweep_bp(colored(rng, n, -3), 600, 180, t_hit, t_hit + 0.5, 0.8) * env(n, 0.01, 0.3, t0=t_hit)
    x = 0.45 * norm(pre) + 0.9 * norm(hit) + 0.55 * norm(growl) + 0.45 * norm(whoosh)
    return sat(reverb(x, "dark", 0.38), 1.6)


def cast_arcane(rng, v, n):
    fc = glide(n, 1800 + 200 * v, 320, 0.18)
    idx = 3.0 * env(n, 0.001, 0.08) + 0.5
    pew = np.sin(phase(fc, n) + idx * np.sin(phase(2.0 * fc, n))) * env(n, 0.002, 0.12)
    low = sine(fc / 2, n) * env(n, 0.002, 0.1) * 0.4
    swoosh = sfilt(white(rng, n), lambda t, f: bp(f, np.interp(t, tt(n), fc) * 1.5, 0.5)) * env(n, 0.004, 0.12) * 0.4
    spark = sparkles(rng, n, 35, 4000, 10000, (0.015, 0.08), t_to=0.2) * 0.35
    return reverb(0.75 * norm(pew) + low + 0.5 * norm(swoosh) + spark, "medium", 0.3)


def cast_heal(rng, v, n):
    notes = [[hz("D4"), hz("F#4"), hz("A4"), hz("D5")], [hz("G4"), hz("B4"), hz("D5"), hz("G5")]][v]
    pad = ffilt(chord(n, notes, "tri", 6, 2, 5.0, 0.004, 25, rng), lambda f: lp(f, 3000)) * env(n, 0.12, 0.5, hold=0.1)
    b = bell(n, notes[2] * 2, 0.6, t0=0.05) * 0.3
    air = ffilt(white(rng, n), lambda f: hp(f, 4000)) * swell(n, 0.25, decay=0.3) * 0.08
    spark = sparkles(rng, n, 12, 4000, 9000, (0.03, 0.12), t_to=0.7) * 0.25
    return reverb(0.7 * norm(pad) + b + air + spark, "large", 0.45)


def cast_illusion(rng, v, n):
    t = tt(n)
    glass = sum(sine(cents(hz(nt), 6 * v), n) for nt in ("E6", "G6", "B6")) * (0.6 + 0.4 * np.sin(2 * np.pi * 11 * t))
    glass *= env(n, 0.05, 0.3, hold=0.1)
    spark = sparkles(rng, n, 50, 3500, 10000, (0.02, 0.12), density=lambda tt_: min(1.0, tt_ / 0.25) if tt_ < 0.3 else math.exp(-(tt_ - 0.3) / 0.1))
    whoosh = sfilt(white(rng, n), lambda tc, f: bp(f, 1000 * 4 ** np.sin(np.pi * np.clip(tc / 0.6, 0, 1)), 0.6)) * env(n, 0.06, 0.25, hold=0.1)
    x = 0.5 * norm(glass) + 0.5 * norm(spark) + 0.45 * norm(whoosh)
    return reverb(flanger(x, 3.0 + v, 1.0, 5.0, 0.8), "large", 0.38)


def cast_summon(rng, v, n):
    t = tt(n)
    swirl = sfilt(white(rng, n), lambda tc, f: bp(f, 200 * (12 + 2 * v) ** np.clip(tc / 0.5, 0, 1) * 2 ** (0.3 * np.sin(2 * np.pi * 6 * tc)), 0.4))
    swirl *= swell(n, 0.42, decay=0.35)
    hum = ffilt(additive(82.4, n, "saw", 40), lambda f: lp(f, 600)) * swell(n, 0.35, power=1.5, decay=0.6)
    sub = sine(41.2, n) * swell(n, 0.35, power=1.5, decay=0.6) * 0.6
    b = bell(n, [hz("A4"), hz("C5")][v], 0.55, t0=0.45) * 0.5
    return reverb(0.6 * norm(swirl) + 0.5 * norm(hum) + sub + b, "large", 0.4)


def cast_teleport(rng, v, n):
    up, down = 0.2 - 0.03 * v, 0.25
    f = np.where(tt(n) < up, glide(n, 300, 2200, up), glide(n, 2200, 250, down, t0=up))
    tone = np.sin(phase(f, n) + 1.2 * np.sin(phase(1.5 * f, n))) * env(n, 0.003, 0.2, hold=up)
    swoosh = sfilt(white(rng, n), lambda tc, ff: bp(ff, np.interp(tc, tt(n), f) * 1.4, 0.5)) * env(n, 0.003, 0.2, hold=up)
    click = np.zeros(n)
    place(click, white(rng, ns(0.004)) * np.hanning(ns(0.004)), 0)
    return reverb(0.6 * norm(tone) + 0.6 * norm(swoosh) + 0.2 * click, "small", 0.25)


def cast_earth(rng, v, n):
    boom = thump(n, 80 - 8 * v, 30, 0.35, drive=3.0)
    crunch = ffilt(crackles(rng, n, 90, (150, 1400), (0.002, 0.012), t_to=0.28), lambda f: lp(f, 1400))
    rumble = ffilt(colored(rng, n, -6), lambda f: lp(f, 200)) * env(n, 0.02, 0.5)
    debris = crackles(rng, n, 30, (1000, 4000), (0.001, 0.005), t_from=0.1, t_to=0.55) * 0.5
    x = 0.9 * norm(boom) + 0.6 * norm(crunch) + 0.55 * norm(rumble) + 0.3 * norm(debris)
    return sat(reverb(x, "medium", 0.25), 1.5)


# ---------------------------------------------------------------- попадания

def impact_fire(rng, v, n):
    burst = sfilt(colored(rng, n, -3), lambda t, f: bp(f, 1300 * (0.5 ** np.clip(t / 0.3, 0, 1)), 1.1)) * env(n, 0.001, 0.15 + 0.03 * v)
    crack = crackles(rng, n, 60, (1500, 7000), density=lambda t: math.exp(-t / 0.25))
    x = 0.8 * norm(burst) + 0.4 * norm(crack) + 0.7 * thump(n, 100 + 10 * v, 45, 0.12)
    return sat(reverb(x, "small", 0.25), 1.4)


def impact_frost(rng, v, n):
    ping = pings(rng, n, 22 + 5 * v, 2000, 9000, 0.0, 0.25, (0.03, 0.25))
    glass = sweep_bp(white(rng, n), 8000, 5000, 0.0, 0.1, 0.5) * env(n, 0.001, 0.08)
    shards = crackles(rng, n, 200, (3000, 10000), (0.0004, 0.002), density=lambda t: math.exp(-t / 0.08))
    x = 0.65 * norm(ping) + 0.5 * norm(glass) + 0.45 * norm(shards) + 0.35 * thump(n, 150, 80, 0.05)
    return reverb(x, "small", 0.3)


def impact_shock(rng, v, n):
    crack_ = np.zeros(n)
    place(crack_, ffilt(white(rng, ns(0.012)) * np.exp(-tt(ns(0.012)) / 0.003), lambda f: hp(f, 1000)), 0)
    burst = crackles(rng, n, 320, (1500, 10000), (0.0003, 0.003), density=lambda t: math.exp(-t / 0.08))
    buzz = sfilt(additive(120 * (1 + 0.08 * smooth_noise(rng, n, 150)), n, "saw", 80), lambda t, f: bp(f, 1100, 1.2))
    buzz *= env(n, 0.002, 0.12 + 0.03 * v) * (0.5 + 0.5 * (smooth_noise(rng, n, 60) > 0))
    x = 0.8 * norm(crack_) + 0.6 * norm(burst) + 0.5 * norm(buzz)
    return sat(reverb(x, "small", 0.2), 2.0)


def impact_holy(rng, v, n):
    f0 = [hz("A5"), hz("C#6"), hz("E6")][v]
    b = bell(n, f0, 0.5) + 0.35 * bell(n, f0 / 2, 0.6)
    spark = sparkles(rng, n, 35, 4500, 11000, (0.02, 0.1), t_to=0.3) * 0.4
    return reverb(0.75 * norm(b) + spark + 0.3 * thump(n, 120, 70, 0.05), "medium", 0.35)


def impact_dark(rng, v, n):
    hit = thump(n, 75 - 5 * v, 34, 0.18, drive=2.5)
    body = thump(n, 260 - 20 * v, 140, 0.07, drop=0.02, drive=3.0)          # «тело» удара — слышно и в мелких динамиках
    dull = ffilt(colored(rng, n, -3), lambda f: lp(f, 1100) * hp(f, 120)) * env(n, 0.002, 0.09)
    tail = ffilt(colored(rng, n, -3), lambda f: lp(f, 400)) * env(n, 0.02, 0.3) * 0.4
    return sat(reverb(0.85 * norm(hit) + 0.45 * norm(body) + 0.6 * norm(dull) + tail, "dark", 0.32), 1.5)


def impact_arcane(rng, v, n):
    pop = np.zeros(n)
    place(pop, sine(glide(ns(0.03), 900, 400, 0.02), ns(0.03)) * env(ns(0.03), 0.0005, 0.008), 0)
    puff = ffilt(white(rng, n), lambda f: bp(f, 2000, 1.0)) * env(n, 0.0005, 0.012)
    chime = bell(n, [1500, 1800, 2150][v], 0.16)
    spark = sparkles(rng, n, 45, 4000, 11000, (0.015, 0.07), t_to=0.15) * 0.4
    return reverb(0.6 * norm(pop) + 0.5 * norm(puff) + 0.55 * norm(chime) + spark, "small", 0.25)


# ---------------------------------------------------------------- большие

def explosion(rng, v, n):
    boom = thump(n, 65 + 6 * v, 28, 0.6, drop=0.08, drive=3.0)
    blast = sfilt(colored(rng, n, -3), lambda t, f: lp(f, 6000 * (400 / 6000) ** np.clip(t / 1.0, 0, 1), 2)) * env(n, 0.003, 0.5)
    debris = crackles(rng, n, 90, (800, 6000), (0.001, 0.01), density=lambda t: math.exp(-t / 0.5))
    shimmer = (bell(n, hz("D5"), 1.0, t0=0.08, bright=0.6) + 0.7 * bell(n, hz("F5"), 1.0, t0=0.1, bright=0.6)
               + 0.6 * bell(n, hz("A5"), 0.9, t0=0.12, bright=0.6)) * 0.2
    spark = sparkles(rng, n, 30, 3000, 9000, (0.03, 0.15), t_from=0.1, t_to=1.2) * 0.25
    rumble = ffilt(colored(rng, n, -6), lambda f: lp(f, 120)) * env(n, 0.05, 0.8)
    x = 0.9 * norm(boom) + 0.75 * norm(blast) + 0.4 * norm(debris) + shimmer + spark + 0.5 * norm(rumble)
    return sat(reverb(x, "large", 0.38), 2.2)


def meteor_fall(rng, v, n):
    f = glide(n, 3000, 350, 1.9)
    whistle = sfilt(white(rng, n), lambda t, ff: bp(ff, 3000 * (350 / 3000) ** np.clip(t / 1.9, 0, 1), 0.15))
    tone = sine(f, n) * 0.25
    roar = ffilt(colored(rng, n, -3), lambda ff: lp(ff, 1200))
    approach = np.clip(tt(n) / 1.6, 0, 1) ** 1.8 * 0.9 + 0.1
    crack = crackles(rng, n, 50, (1200, 6000), density=lambda t: min(1.0, t / 1.6))
    x = (0.6 * norm(whistle) + tone + 0.5 * norm(roar) * approach ** 1.3) * approach + 0.3 * norm(crack)
    return reverb(x, "medium", 0.25)


def pillar(rng, v, n):
    notes = [hz("D3"), hz("A3"), hz("D4"), hz("F#4"), hz("A4")]
    ch = chord(n, notes, "saw", 9, 3, 5.0, 0.005, 50, rng)
    choir = 0.7 * formant(ch, "a") + 0.4 * formant(ch, "o")
    choir *= np.clip(tt(n) / 0.45, 0, 1) ** 1.5 * np.exp(-np.clip(tt(n) - 1.6, 0, None) / 0.35)
    b = bell(n, hz("D5"), 1.2) + 0.6 * bell(n, hz("D4"), 1.4)
    rise = sweep_bp(white(rng, n), 2000, 9000, 0.0, 1.2, 0.6) * swell(n, 1.0, decay=0.5) * 0.4
    spark = sparkles(rng, n, 40, 4000, 11000, (0.02, 0.12), density=lambda t: min(1.0, t / 1.2), t_to=2.0) * 0.35
    sub = sine(hz("D2"), n) * swell(n, 0.6, power=1.5, decay=0.9) * 0.5
    x = 0.65 * norm(choir) + 0.45 * norm(b) + rise + spark + sub
    return reverb(x, "large", 0.45)


# ---------------------------------------------------------------- петли потоков

def loop_flames(rng, L, N, q):
    roar = colored(rng, N, -3)
    wob = smooth_noise(rng, N, 4.0, math.log(300), math.log(1200))
    roar = sfilt(roar, lambda t, f: bp(f, np.exp(np.interp(t, tt(N), wob)), 1.0)) * (0.7 + 0.3 * smooth_noise(rng, N, 8.0))
    rumble = ffilt(colored(rng, N, -6), lambda f: lp(f, 220))
    crack = crackles(rng, N, 45, (1500, 8000), (0.0008, 0.007))
    return np.zeros(L), 0.75 * norm(roar) + 0.5 * norm(rumble) + 0.45 * norm(crack)


def loop_beam(rng, L, N, q):
    t = tt(L)
    buzz = additive(q(110), L, "saw", 60) + additive(q(110.33), L, "saw", 60) + 0.6 * additive(q(220), L, "square", 40)
    buzz = ffilt(buzz, lambda f: lp(f, 2600, 2) * (0.3 + bp(f, 880, 0.8)), circular=True)
    whine = sine(q(1760), L) * (0.6 + 0.4 * np.sin(2 * np.pi * q(12) * t)) * 0.25
    tonal = norm(buzz) * (0.85 + 0.15 * np.sin(2 * np.pi * q(1 / 3) * t)) * 0.7 + whine
    noisy = ffilt(white(rng, N), lambda f: hp(f, 5000)) * 0.04 + crackles(rng, N, 12, (3000, 9000)) * 0.15
    return tonal, noisy


def loop_drain(rng, L, N, q):
    drone = ffilt(additive(q(49.0), L, "saw", 50) + 0.7 * additive(q(73.33), L, "saw", 40), lambda f: lp(f, 320, 3), circular=True)
    goo = colored(rng, N, -3)
    wob = smooth_noise(rng, N, 2.5, math.log(140), math.log(520))
    goo = sfilt(goo, lambda t, f: bp(f, np.exp(np.interp(t, tt(N), wob)), 0.35))
    bubbles = np.zeros(N)
    for _ in range(rng.poisson(5 * N / SR)):
        m = ns(0.08)
        f0 = rng.uniform(120, 320)
        b = sine(glide(m, f0, f0 * 1.8, 0.06), m) * env(m, 0.004, 0.025)
        place(bubbles, b, rng.uniform(0, N / SR))
    noisy = 0.55 * norm(goo) + 0.35 * norm(bubbles) + 0.35 * whisper(rng, N, 2.0)
    return norm(drone) * 0.55, noisy


def loop_heal(rng, L, N, q):
    t = tt(L)
    high = (sine(q(hz("A5")), L) + 0.8 * sine(q(hz("C#6")), L) + 0.7 * sine(q(hz("E6")), L)) * (0.7 + 0.3 * np.sin(2 * np.pi * q(1) * t))
    mid = ffilt(additive(q(hz("A4")), L, "tri", 12), lambda f: lp(f, 1500), circular=True) * (0.8 + 0.2 * np.sin(2 * np.pi * q(2 / 3) * t))
    noisy = sparkles(rng, N, 10, 4000, 9000, (0.03, 0.14)) * 0.3 + ffilt(white(rng, N), lambda f: hp(f, 6000)) * 0.02
    return 0.35 * norm(high) + 0.4 * norm(mid), noisy


def loop_ward(rng, L, N, q):
    t = tt(L)
    hum = additive(q(98.0), L, "saw", 60) + additive(q(98.33), L, "saw", 60)
    hum = ffilt(hum, lambda f: lp(f, 1200, 2) * (0.4 + bp(f, 294, 0.3) + 0.6 * bp(f, 588, 0.25)), circular=True)
    glassy = (sine(q(784), L) + 0.7 * sine(q(1176), L) + 0.4 * sine(q(1568.33), L)) * (0.6 + 0.4 * np.sin(2 * np.pi * q(2) * t))
    noisy = ffilt(white(rng, N), lambda f: bp(f, 6000, 0.8)) * 0.05 * (0.6 + 0.4 * smooth_noise(rng, N, 20))
    return 0.6 * norm(hum) + 0.25 * norm(glassy), noisy


def loop_wind(rng, L, N, q):
    x = white(rng, N)
    c1 = smooth_noise(rng, N, 1.5, math.log(350), math.log(2400))
    c2 = smooth_noise(rng, N, 1.2, math.log(500), math.log(3200))
    whistle = sfilt(x, lambda t, f: bp(f, np.exp(np.interp(t, tt(N), c1)), 0.18) + 0.7 * bp(f, np.exp(np.interp(t, tt(N), c2)), 0.22))
    body = ffilt(colored(rng, N, -3), lambda f: lp(f, 900))
    gust = 0.65 + 0.35 * smooth_noise(rng, N, 1.5)
    return np.zeros(L), (0.6 * norm(whistle) + 0.5 * norm(body)) * gust


# ---------------------------------------------------------------- прочее

def vanish(rng, v, n):
    f = glide(n, 2000, 600, 0.55)
    tones = (sine(f, n) + 0.6 * sine(f * 1.5, n) + 0.4 * sine(f * 2.01, n)) * env(n, 0.03, 0.25, hold=0.05)
    tones = flanger(tones, 4.0, 1.0, 4.0, 0.8)
    air = sweep_bp(white(rng, n), 4000, 800, 0.0, 0.55, 0.7) * env(n, 0.02, 0.25)
    spark = sparkles(rng, n, 30, 3000, 9000, (0.02, 0.1), density=lambda t: math.exp(-t / 0.2)) * 0.35
    return reverb(0.5 * norm(tones) + 0.5 * norm(air) + spark, "large", 0.45)


def ward_hit(rng, v, n):
    thunk = thump(n, 160 + 20 * v, 90, 0.05, drive=1.5)
    click = ffilt(white(rng, n), lambda f: bp(f, 1500, 0.8)) * env(n, 0.0005, 0.006)
    ring = bell(n, 420 + 30 * v, 0.25, partials=[(1.0, 1.0, 1.0), (1.64, 0.7, 0.8), (2.69, 0.5, 0.6), (4.38, 0.3, 0.4),
                                                  (7.9, 0.22, 0.18), (11.3, 0.12, 0.12)])
    hum = ffilt(additive(98, n, "saw", 40), lambda f: lp(f, 1000)) * env(n, 0.002, 0.08)
    return reverb(0.6 * norm(thunk) + 0.4 * norm(click) + 0.55 * norm(ring) + 0.35 * norm(hum), "small", 0.2)


# ---------------------------------------------------------------- список файлов

LOOPS = {f"charge_{s}": globals()[f"charge_{s}"] for s in
         ("destruction", "restoration", "light", "dark", "alteration", "illusion", "conjuration")}
LOOPS.update({f"loop_{s}": globals()[f"loop_{s}"] for s in ("flames", "beam", "drain", "heal", "ward", "wind")})

# имя: (функция, вариантов, длительность)
ONESHOTS = {
    "ready": (ready, 1, 0.6), "stage": (stage, 1, 0.3), "peak": (peak, 1, 1.0), "fizzle": (fizzle, 1, 0.6),
    "learn": (learn, 1, 1.5),
    "cast_fire": (cast_fire, 2, 0.8), "cast_frost": (cast_frost, 2, 0.7), "cast_shock": (cast_shock, 2, 0.7),
    "cast_holy": (cast_holy, 2, 1.0), "cast_dark": (cast_dark, 2, 1.0), "cast_arcane": (cast_arcane, 2, 0.6),
    "cast_heal": (cast_heal, 2, 1.0), "cast_illusion": (cast_illusion, 2, 0.9), "cast_summon": (cast_summon, 2, 1.2),
    "cast_teleport": (cast_teleport, 2, 0.6), "cast_earth": (cast_earth, 2, 1.0),
    "impact_fire": (impact_fire, 3, 0.7), "impact_frost": (impact_frost, 3, 0.8), "impact_shock": (impact_shock, 3, 0.5),
    "impact_holy": (impact_holy, 3, 0.9), "impact_dark": (impact_dark, 3, 0.6), "impact_arcane": (impact_arcane, 3, 0.5),
    "explosion": (explosion, 3, 2.0), "meteor_fall": (meteor_fall, 1, 2.0), "pillar": (pillar, 1, 2.5),
    "vanish": (vanish, 1, 0.7), "ward_hit": (ward_hit, 2, 0.4),
}


def render_all():
    out = {}
    for name, fn in LOOPS.items():
        rng = np.random.default_rng(zlib.crc32(name.encode()))
        x = make_loop(fn, rng)
        wet = {"charge_restoration": ("large", 0.35), "charge_light": ("medium", 0.3), "charge_dark": ("dark", 0.35),
               "charge_illusion": ("large", 0.3), "charge_conjuration": ("medium", 0.3), "loop_heal": ("large", 0.35),
               "loop_drain": ("dark", 0.25)}.get(name, ("small", 0.15))
        x = reverb(x, wet[0], wet[1], circular=True)
        out[name] = (finish(x, loop=True), True)
    for name, (fn, variants, dur) in ONESHOTS.items():
        for v in range(variants):
            fname = name if variants == 1 else f"{name}_{v + 1}"
            rng = np.random.default_rng(zlib.crc32(fname.encode()))
            x = fn(rng, v, ns(dur))
            out[fname] = (finish(x, fade_out=min(0.12, dur * 0.2)), False)
    return out


# ---------------------------------------------------------------- запись Ogg и проверка

_CRC = []
for _i in range(256):
    _r = _i << 24
    for _ in range(8):
        _r = ((_r << 1) ^ 0x04C11DB7) if _r & 0x80000000 else (_r << 1)
    _CRC.append(_r & 0xFFFFFFFF)


def _ogg_crc(b):
    crc = 0
    for byte in b:
        crc = ((crc << 8) & 0xFFFFFFFF) ^ _CRC[((crc >> 24) ^ byte) & 0xFF]
    return crc


def fix_ogg_serial(path, serial):
    """libsndfile ставит потоку Ogg случайный серийный номер — заменяем на постоянный и пересчитываем CRC страниц."""
    data = bytearray(path.read_bytes())
    pos = 0
    while pos < len(data):
        assert data[pos:pos + 4] == b"OggS", f"{path.name}: не страница Ogg на {pos}"
        nseg = data[pos + 26]
        size = 27 + nseg + sum(data[pos + 27:pos + 27 + nseg])
        data[pos + 14:pos + 18] = struct.pack("<I", serial)
        data[pos + 22:pos + 26] = b"\0\0\0\0"
        data[pos + 22:pos + 26] = struct.pack("<I", _ogg_crc(bytes(data[pos:pos + size])))
        pos += size
    path.write_bytes(bytes(data))


def write_ogg(path, x, name, loop):
    """Разовые — качество Vorbis 0,6; петли — 0,8: шум кодека в конце и в начале файла независим, и на
    стыке получается ступенька его размера (при 0,6 ≈ −37…−40 dBFS, при 0,8 ≈ −60 dBFS — неслышно).
    Если после кодирования пик вылез выше −0,5 dBFS (у плотных звуков), громкость чуть снижается и файл
    кодируется заново."""
    path.parent.mkdir(parents=True, exist_ok=True)
    for _ in range(4):
        sf.write(path, x.astype(np.float32), SR, format="OGG", subtype="VORBIS", compression_level=0.2 if loop else 0.4)
        pk = np.max(np.abs(sf.read(path, dtype="float64")[0]))
        if pk <= 10 ** (-0.5 / 20):
            break
        x = x * PEAK / pk
    fix_ogg_serial(path, zlib.crc32(name.encode()) & 0x7FFFFFFF)


def seam_score(y):
    """Щелчок на стыке петли: вторая разность (излом формы волны) на стыке относительно 99,9-го процентиля
    вторых разностей внутри петли. Щелчок — резкий излом, заметно больше любого внутреннего (≫1); естественные
    перепады (у пилы и т. п.) есть и внутри, поэтому ложной тревоги не дают. < 1 — стык не выделяется."""
    yy = np.concatenate([y[-3:], y[:3]])
    seam = max(abs(yy[1] - 2 * yy[2] + yy[3]), abs(yy[2] - 2 * yy[3] + yy[4]))
    inner = np.abs(y[2:] - 2 * y[1:-1] + y[:-2])
    return float(seam / np.percentile(inner, 99.9))


def check(paths):
    """Таблица: длительность, каналы, частота, пик, RMS, клиппинг, у петель — щелчок на стыке (seam_score)."""
    rows = []
    ok = True
    for p in paths:
        x, sr = sf.read(p, dtype="float64", always_2d=True)
        ch = x.shape[1]
        y = x[:, 0]
        pk = np.max(np.abs(y))
        clip = int(np.sum(np.abs(y) >= 0.999))
        loop = p.stem.startswith(("charge_", "loop_"))
        seam = ""
        if loop:
            seam = f"{seam_score(y):.2f}"
        good = ch == 1 and sr == SR and clip == 0 and pk < 1.0 and (not loop or float(seam) < 1.0)
        ok &= good
        rms = 20 * math.log10(np.sqrt(np.mean(y ** 2)))
        rows.append((p.stem, len(y) / sr, ch, sr, 20 * math.log10(pk), rms, clip, seam, good))
    print("| файл | длит., с | кан. | Гц | пик, dBFS | RMS, dBFS | клиппинг | стык/норма | итог |")
    print("|---|---|---|---|---|---|---|---|---|")
    for r in rows:
        print(f"| {r[0]} | {r[1]:.2f} | {r[2]} | {r[3]} | {r[4]:.2f} | {r[5]:.1f} | {r[6]} | {r[7] or '—'} | "
              f"{'ок' if r[8] else 'ОШИБКА'} |")
    print(f"\nфайлов: {len(rows)}, всё в порядке: {'да' if ok else 'НЕТ'}")
    return ok


def preview(sounds, folder):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    folder.mkdir(parents=True, exist_ok=True)
    names = list(sounds)
    for start in range(0, len(names), 12):
        chunk = names[start:start + 12]
        fig, axes = plt.subplots(len(chunk), 1, figsize=(10, 1.25 * len(chunk)))
        for ax, name in zip(np.atleast_1d(axes), chunk):
            x = sounds[name][0]
            ax.specgram(x, NFFT=1024, Fs=SR, noverlap=768, cmap="magma", vmin=-110, vmax=-10)
            ax.set_ylim(0, 12000)
            ax.set_xlim(0, 3.0)
            ax.set_yticks([])
            ax.set_ylabel(name, rotation=0, ha="right", va="center", fontsize=8)
            ax.tick_params(axis="x", labelsize=7)
        fig.tight_layout()
        fig.savefig(folder / f"spectro_{start // 12 + 1}.png", dpi=80)
        plt.close(fig)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--check", action="store_true", help="только проверить готовые файлы")
    ap.add_argument("--preview", type=Path, help="только спектрограммы в эту папку")
    args = ap.parse_args()
    if args.check:
        raise SystemExit(0 if check(sorted(OUT.glob("*.ogg"))) else 1)
    sounds = render_all()
    if args.preview:
        preview(sounds, args.preview)
        print(f"спектрограммы {len(sounds)} звуков в {args.preview}")
        return
    for name, (x, loop) in sounds.items():
        write_ogg(OUT / f"{name}.ogg", x, name, loop)
    print(f"записано {len(sounds)} файлов в {OUT}\n")
    raise SystemExit(0 if check(sorted(OUT.glob("*.ogg"))) else 1)


if __name__ == "__main__":
    main()
