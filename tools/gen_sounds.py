#!/usr/bin/env python3
"""Синтез звуков магии Suchki: всё своё, на numpy — без скачанных сэмплов.

Запуск из корня мода:
    python tools/gen_sounds.py                  — записать assets/suchki/sounds/magic/*.ogg, удалить лишние, проверить
    python tools/gen_sounds.py --check          — только проверка готовых файлов (таблица)
    python tools/gen_sounds.py --preview DIR    — только спектрограммы в DIR (файлы в src не трогаются)
    python tools/gen_sounds.py --only REGEX     — только звуки, чьё имя подходит (для отладки; лишние не удаляются)
    python tools/gen_sounds.py --jobs N         — число процессов синтеза (по умолчанию — по ядрам; результат тот же)
Нужны numpy, soundfile (с OGG/Vorbis), для превью — matplotlib. Файлы, которых нет в списке (старые события),
при полной записи удаляются. Имя файла ↔ событие: cast_fire_1..3.ogg ↔ «magic.cast.fire», charge_fire.ogg ↔
«magic.charge.fire», explosion_huge.ogg ↔ «magic.explosion.huge» (sounds.json пишет tools/gen_magic.py).

Формат: моно (в Minecraft позиционируются только моно-звуки), 44100 Гц, Ogg Vorbis (качество 0,6, у петель
0,8 — см. write_ogg), пик −1 dBFS (после кодирования не выше −0,5), короткие фейды. Петли (charge_*, loop_*)
бесшовные: тональные слои укладываются в длину петли целым числом периодов, шумовые — сводятся кроссфейдом хвоста
в начало, эффекты после этого кольцевые. Повторяемость: у каждого файла своё зерно (crc32 имени), серийный номер
потока Ogg — тоже от имени (libsndfile ставит случайный), так что повторный запуск даёт побайтно те же файлы.

Стиль — «аниме-ульта» (разобран по спектрограммам роликов-образцов): зарядка — пучок тонов, ползущих вверх, мерцающий
держащийся тон и растущий суб-гул; выпуск — очень быстрый свип вверх («шшинг») и широкополосный удар; дальше — сжатая
«стена звука» (суб, плотный шум, треск, повторные удары) и нисходящий хвост «вжуууум»; всё насыщено (tanh) и с большой
реверберацией. Петли зарядки — гул + фактура стихии + мерцающая гармоническая подложка, часто с бесконечным
глиссандо Шепарда–Риссе (тон «вечно растёт» и при этом бесшовен; клиент ещё и поднимает высоту с силой заряда).

Приёмы: шумы с полосовыми фильтрами и свипами (оконное БПФ), колокола, гонги и металл из негармонических партиалов,
FM, хор (пилы с вибрато через форманты гласных), гранулярные искры, треск и пузыри, суб-удары с понижением тона
и сатурацией, флэнжер, компрессор, биткраш и заикание (глитч), реверберация — свёртка с синтезированным откликом.
"""

import argparse
import math
import os
import re
import struct
import zlib
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/suchki/sounds/magic"
SR = 44100
PEAK = 10 ** (-1 / 20)          # −1 dBFS
LOOP_LEN = 3.0
LOOP_XFADE = 0.35
TAU = 2 * np.pi


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


def mix(*layers):
    """Сумма слоёв (уровень, сигнал): каждый слой нормирован по пику, так что уровни читаются как доли."""
    return sum(a * norm(x) for a, x in layers)


def place(buf, sig, t0):
    i = ns(t0)
    if i < 0:
        sig, i = sig[-i:], 0
    if i >= len(buf):
        return buf
    k = min(len(sig), len(buf) - i)
    buf[i:i + k] += sig[:k]
    return buf


def phase(freq, n):
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    return TAU * np.cumsum(f) / SR


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


def band(x, lo, hi, order=2, circular=False):
    return ffilt(x, lambda f: hp(f, lo, order) * lp(f, hi, order), circular)


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


def track_bp(x, pts, width=0.6):
    """Полоса, центр которой идёт по точкам [(t, Гц), …] (интерполяция по логарифму)."""
    ts, fs = zip(*pts)
    lf = np.log(fs)
    return sfilt(x, lambda t, f: bp(f, np.exp(np.interp(t, ts, lf)), width))


def track_lp(x, pts, order=2):
    """Срез ФНЧ по точкам [(t, Гц), …]."""
    ts, fs = zip(*pts)
    lf = np.log(fs)
    return sfilt(x, lambda t, f: lp(f, np.exp(np.interp(t, ts, lf)), order))


def smooth_noise(rng, n, rate, lo=-1.0, hi=1.0):
    """Плавная случайная кривая (для блуждания фильтров и «дыхания» громкости)."""
    k = max(4, int(n / SR * rate) + 4)
    pts = rng.uniform(lo, hi, k)
    xs = np.linspace(0, n, k)
    return np.interp(np.arange(n), xs, pts)


def flanger(x, rate, base_ms, depth_ms, mix_=0.7, ph0=0.0, circular=False):
    n = len(x)
    d = (base_ms + depth_ms * (0.5 + 0.5 * np.sin(TAU * rate * tt(n) + ph0))) * SR / 1000
    pos = np.arange(n) - d
    if circular:
        pos = np.mod(pos, n)
    i0 = np.floor(pos).astype(int)
    fr = pos - i0
    a = np.where((i0 >= 0) & (i0 < n), x[np.clip(i0, 0, n - 1)], 0.0) if not circular else x[i0 % n]
    b = np.where((i0 + 1 >= 0) & (i0 + 1 < n), x[np.clip(i0 + 1, 0, n - 1)], 0.0) if not circular else x[(i0 + 1) % n]
    return x + mix_ * (a * (1 - fr) + b * fr)


FORMANTS = {"a": [(800, 1.0, 0.18), (1150, 0.6, 0.16), (2900, 0.25, 0.12), (3900, 0.12, 0.1)],
            "o": [(450, 1.0, 0.2), (800, 0.6, 0.18), (2830, 0.15, 0.12), (3500, 0.08, 0.1)],
            "e": [(400, 1.0, 0.18), (2000, 0.5, 0.12), (2550, 0.3, 0.1), (3500, 0.1, 0.1)],
            "i": [(300, 1.0, 0.2), (2300, 0.45, 0.1), (3000, 0.3, 0.1), (3800, 0.1, 0.1)],
            "u": [(320, 1.0, 0.22), (800, 0.35, 0.2), (2400, 0.08, 0.12), (3300, 0.04, 0.1)]}


def formant(x, vowel="a", circular=False):
    peaks = FORMANTS[vowel]
    return ffilt(x, lambda f: 0.04 + sum(a * bp(f, fc, w) for fc, a, w in peaks), circular)


def compress(x, thr_db=-20.0, ratio=4.0, attack=0.004, release=0.15, block=64):
    """Компрессор: огибающая по блокам, атака/спад, без подъёма тихого. Нужен для «стены звука» —
    громкость прижата к потолку и почти не гуляет, как в образцах."""
    n = len(x)
    nb = -(-n // block)
    xp = np.zeros(nb * block)
    xp[:n] = x / max(1e-9, np.max(np.abs(x)))
    e = np.sqrt(np.mean(xp.reshape(nb, block) ** 2, axis=1)) * 1.414 + 1e-9
    gr = np.maximum(20 * np.log10(e) - thr_db, 0) * (1 - 1 / ratio)
    a, r = math.exp(-block / SR / attack), math.exp(-block / SR / release)
    g = np.empty(nb)
    s = 0.0
    for i in range(nb):
        c = a if gr[i] > s else r
        s = c * s + (1 - c) * gr[i]
        g[i] = s
    gain = 10 ** (-np.interp(np.arange(n), (np.arange(nb) + 0.5) * block, g) / 20)
    return xp[:n] * gain


def limit(x, ceiling_db=-6.0, release=0.08, block=32):
    """Лимитер с упреждением: всё выше порога прижимается мгновенно, отпускание плавное. После нормировки по пику
    тело звука становится на |ceiling_db| дБ плотнее — та самая «сжатость» аниме-эффектов, без хруста клиппинга."""
    n = len(x)
    nb = -(-n // block)
    xp = np.zeros(nb * block)
    xp[:n] = norm(x)
    pk = np.max(np.abs(xp.reshape(nb, block)), axis=1) + 1e-9
    g = np.minimum(1.0, 10 ** (ceiling_db / 20) / pk)
    g = np.minimum(g, np.concatenate([g[1:], [1.0]]))
    g = np.minimum(g, np.concatenate([[1.0], g[:-1]]))
    r = 1 - math.exp(-block / SR / release)
    s = 1.0
    for i in range(nb):
        s = min(g[i], s + (1 - s) * r)
        g[i] = s
    return xp[:n] * np.interp(np.arange(n), (np.arange(nb) + 0.5) * block, g)


def crush(x, bits=5, hold=6):
    """Биткраш: понижение частоты дискретизации (удержание отсчёта) и разрядности."""
    y = x[(np.arange(len(x)) // hold) * hold]
    k = 2 ** (bits - 1)
    return np.round(y * k) / k


def stutter(rng, x, n, seg=(0.012, 0.045), reps=(2, 6), gap=0.35):
    """Заикание: короткие куски x повторяются по 2–5 раз подряд, иногда между ними тишина."""
    out = np.zeros(n)
    pos = 0
    while pos < n:
        m = ns(rng.uniform(*seg))
        s0 = int(rng.integers(0, max(1, len(x) - m)))
        piece = x[s0:s0 + m] * np.minimum(1, np.minimum(np.arange(m), m - 1 - np.arange(m)) / 24)
        for _ in range(int(rng.integers(*reps))):
            k = min(m, n - pos)
            if k <= 0:
                break
            out[pos:pos + k] += piece[:k]
            pos += m
        if rng.uniform() < gap:
            pos += m
    return out


def brown(rng, n, fc=150.0):
    """Гул: коричневый шум ниже fc."""
    return norm(ffilt(colored(rng, n, -6), lambda f: lp(f, fc, 2)))


def roar(rng, n, lo=300.0, hi=1200.0, rate=4.0, flick=8.0, width=1.0):
    """Рёв пламени: розовый шум в блуждающей полосе, с мерцанием громкости."""
    wob = smooth_noise(rng, n, rate, math.log(lo), math.log(hi))
    y = sfilt(colored(rng, n, -3), lambda t, f: bp(f, np.exp(np.interp(t, tt(n), wob)), width))
    return norm(y) * (0.7 + 0.3 * smooth_noise(rng, n, flick))


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


def ramp(n, t0, t1, p=1.0):
    """0 до t0, рост до 1 к t1 (со степенью p), дальше 1."""
    return np.clip((tt(n) - t0) / max(1e-6, t1 - t0), 0, 1) ** p


def amp(n, *pts):
    """Огибающая по точкам (t, уровень), линейно."""
    ts, vs = zip(*pts)
    return np.interp(tt(n), ts, vs)


def glide(n, f0, f1, dur, t0=0.0, curve="exp"):
    """Частота: f0 → f1 за dur секунд (по логарифму), дальше держится."""
    k = np.clip((tt(n) - t0) / dur, 0, 1)
    if curve == "exp":
        return f0 * (f1 / f0) ** k
    return f0 + (f1 - f0) * k


def fcurve(n, pts):
    """Кривая частоты по точкам [(t, Гц), …], интерполяция по логарифму."""
    ts, fs = zip(*pts)
    return np.exp(np.interp(tt(n), ts, np.log(fs)))


def acc(t0, t1, f0, f1, p=1.6, k=24):
    """Точки кривой f0→f1 за t0…t1 по логарифму с ускорением p (p > 1 — медленно вначале, стремительно в конце)."""
    return [(t0 + (t1 - t0) * i / k, f0 * (f1 / f0) ** ((i / k) ** p)) for i in range(k + 1)]


def thump(n, f_start=110.0, f_end=40.0, decay=0.25, drop=0.05, drive=2.0, t0=0.0):
    """Суб-удар: синус с быстрым падением тона и сатурацией."""
    t = np.clip(tt(n) - t0, 0, None)
    f = f_end + (f_start - f_end) * np.exp(-t / drop)
    x = sine(f, n) * env(n, 0.002, decay, t0=t0)
    return sat(x, drive)


BELL = [(0.5, 0.22, 1.5), (1.0, 1.0, 1.0), (2.0, 0.5, 0.62), (2.76, 0.42, 0.5), (3.0, 0.22, 0.45),
        (4.07, 0.2, 0.32), (5.43, 0.13, 0.24), (6.8, 0.08, 0.17), (8.93, 0.05, 0.12)]
METAL = [(1.0, 1.0, 1.0), (1.47, 0.7, 0.8), (2.09, 0.6, 0.7), (2.56, 0.5, 0.6), (3.18, 0.45, 0.5), (4.11, 0.35, 0.4),
         (5.43, 0.25, 0.3), (6.79, 0.18, 0.22), (8.21, 0.12, 0.16)]
GONG = [(1.0, 1.0, 1.0), (1.52, 0.8, 0.9), (2.03, 0.7, 0.85), (2.71, 0.6, 0.8), (3.39, 0.5, 0.75), (4.1, 0.45, 0.7),
        (5.2, 0.35, 0.6), (6.4, 0.3, 0.5), (7.7, 0.22, 0.45), (9.3, 0.15, 0.4)]
GLASS = [(1.0, 1.0, 1.0), (2.32, 0.5, 0.45), (3.71, 0.3, 0.3), (5.1, 0.12, 0.2)]


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
        out += a * e * (np.sin(TAU * f * t) + 0.6 * np.sin(TAU * (f + beat) * t + 1.3))
    return out * on


def chord(n, freqs, kind="saw", detune=7.0, voices=3, vib_rate=5.0, vib_depth=0.004, kmax=40, rng=None, q=None):
    """Аккорд из расстроенных голосов с вибрато. q — квантование частоты (для петель)."""
    q = q or (lambda f: f)
    out = np.zeros(n)
    for i, f in enumerate(freqs):
        for v in range(voices):
            c = (v - (voices - 1) / 2) * detune
            fv = q(cents(f, c))
            ph = rng.uniform(0, TAU) if rng is not None else 0.0
            vib = 1 + vib_depth * np.sin(TAU * q(vib_rate) * tt(n) + ph)
            out += additive(fv * vib, n, kind, kmax, ph0=ph)
    return out / max(1, len(freqs) * voices) ** 0.5


def choir(rng, n, notes, vowels=("a",), weights=None, detune=9.0, voices=3, vib=5.0, depth=0.005, q=None,
          circular=False):
    """Хор без слов: расстроенные пилы с вибрато через форманты гласных; weights — веса гласных во времени."""
    src = chord(n, notes, "saw", detune, voices, vib, depth, 50, rng, q)
    outs = [formant(src, v, circular) for v in vowels]
    if weights is None:
        return sum(outs) / len(outs)
    return sum(o * w for o, w in zip(outs, weights))


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
        g = np.sin(TAU * f * tg) * np.exp(-tg / d) * np.minimum(1, tg / 0.001) * rng.lognormal(0, 0.4)
        place(out, g, t0)
    return out


def crackles(rng, n, rate, band_=(1200, 7000), dur=(0.001, 0.008), density=None, t_from=0.0, t_to=None):
    """Треск: щелчки из шума с короткой экспонентой, потом полоса band_."""
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
    lo, hi_ = band_
    return ffilt(out, lambda f: hp(f, lo, 2) * lp(f, hi_, 2))


def pings(rng, n, count, fmin, fmax, t_from, t_to, decay=(0.04, 0.2), density=None):
    """Кристаллические «тинги»: по 3 негармонических партиала на каждый."""
    out = np.zeros(n)
    for _ in range(count):
        t0 = rng.uniform(t_from, t_to)
        if density is not None and rng.uniform() > density(t0):
            continue
        f = math.exp(rng.uniform(math.log(fmin), math.log(fmax)))
        d = rng.uniform(*decay)
        m = ns(min(d * 6, 0.8))
        tg = tt(m)
        g = (np.sin(TAU * f * tg) + 0.4 * np.sin(TAU * f * 2.32 * tg) * np.exp(-tg / (d * 0.4))
             + 0.25 * np.sin(TAU * f * 3.71 * tg) * np.exp(-tg / (d * 0.25)))
        g *= np.exp(-tg / d) * np.minimum(1, tg / 0.0008) * rng.lognormal(0, 0.35)
        place(out, g, t0)
    return out


def bubbles(rng, n, rate, fmin=150.0, fmax=900.0, dur=(0.015, 0.05), rise=1.7, density=None, t_from=0.0, t_to=None):
    """Пузыри и капли: синус с быстрым подъёмом тона и коротким спадом."""
    out = np.zeros(n)
    t_to = n / SR if t_to is None else t_to
    for _ in range(rng.poisson(rate * max(0.0, t_to - t_from))):
        t0 = rng.uniform(t_from, t_to)
        if density is not None and rng.uniform() > density(t0):
            continue
        f0 = math.exp(rng.uniform(math.log(fmin), math.log(fmax)))
        d = rng.uniform(*dur)
        m = ns(d * 4)
        b = sine(glide(m, f0, f0 * rise, d), m) * env(m, 0.002, d * 0.6) * rng.lognormal(0, 0.4)
        place(out, b, t0)
    return out


def whisper(rng, n, level_rate=3.0):
    """Шёпот: шум в «согласных» полосах, громкость — слоги со случайными паузами."""
    x = white(rng, n)
    centers = smooth_noise(rng, n, 2.0, math.log(2200), math.log(5200))
    hush = sfilt(x, lambda t, f: bp(f, np.exp(np.interp(t, tt(n), centers)), 0.45) + 0.3 * bp(f, 900, 0.5))
    syll = np.clip(smooth_noise(rng, n, level_rate * 2, -0.6, 1.0), 0, 1) ** 1.5
    return hush * syll


def inhale(rng, n, t_hit, f0=400.0, f1=3000.0, width=0.8, slope=-3):
    """Вдох перед ударом: шум-свип с нарастанием (как обратный звук), обрывается в момент удара."""
    x = sweep_bp(colored(rng, n, slope), f0, f1, 0.0, t_hit, width)
    cut = np.clip((t_hit + 0.008 - tt(n)) / 0.012, 0, 1)
    return norm(x * ramp(n, 0.0, t_hit, 2.5) * cut)


def click(rng, n, t0, dur=0.004, lo=1500.0):
    """Щелчок: короткая вспышка шума выше lo."""
    m = ns(dur * 6)
    out = np.zeros(n)
    place(out, ffilt(white(rng, m) * np.exp(-tt(m) / dur), lambda f: hp(f, lo)), t0)
    return out


def tick(rng, n, t0, f=2400.0, decay=0.012):
    """Тик часов: щелчок + короткий резонанс корпуса."""
    m = ns(0.12)
    tg = tt(m)
    g = (np.sin(TAU * f * tg) + 0.5 * np.sin(TAU * f * 1.51 * tg) + 0.3 * np.sin(TAU * f * 0.5 * tg)) * np.exp(-tg / decay)
    g += 0.6 * rng.standard_normal(m) * np.exp(-tg / 0.0015)
    out = np.zeros(n)
    place(out, g, t0)
    return out


def rswells(rng, n, peaks, length=0.8, lo=300.0, hi=4000.0, slope=-3.0, power=3.0):
    """Обратные «вдохи»: шум нарастает к моменту peak и резко (за 12 мс) обрывается."""
    out = np.zeros(n)
    for p in peaks:
        m = ns(length)
        x = band(colored(rng, m, slope), lo, hi) * (tt(m) / length) ** power
        x[-ns(0.012):] *= np.linspace(1, 0, ns(0.012))
        place(out, x, p - length)
    return out


def shepard(n, f_lo, f_hi, ratio=2.0, direction=1, steps=1):
    """Бесконечное глиссандо Шепарда–Риссе с периодом n отсчётов: синусы через ratio, за период каждый сдвигается
    на steps ступеней вверх (direction=1) или вниз (−1); громкость — колокол по логарифму частоты (на краях 0).
    Фазы сцеплены: конец партиала j продолжается началом партиала j±steps, поэтому петля без шва."""
    U = math.log(f_hi / f_lo) / math.log(ratio)
    js = list(range(-steps, int(math.ceil(U)) + steps + 1))
    if direction < 0:
        js.reverse()
    u_t = direction * steps * np.arange(n) / n
    out = np.zeros(n)
    end = {}
    for j in js:
        u = j + u_t
        f = f_lo * ratio ** u
        cs = np.cumsum(f)
        p0 = end.get(j - steps * direction, 0.37 * j)
        end[j] = (p0 + TAU * cs[-1] / SR) % TAU
        a = np.where((u > 0) & (u < U), np.sin(np.pi * np.clip(u / U, 0, 1)) ** 2, 0.0)
        if a.any():
            out += a * np.sin(p0 + TAU * (cs - f) / SR)
    return out


def gliss(rng, n, pts, voices=(1.0,), harm=8, kind="saw", detune=6.0, vib=(0.0, 0.0)):
    """Стопка глиссандо: голоса (кратные основной частоте по кривой pts) со своими гармониками —
    на спектрограмме пучок параллельных наклонных линий, как в нарастаниях аниме."""
    f = fcurve(n, pts)
    if vib[1]:
        f = f * (1 + vib[1] * np.sin(TAU * vib[0] * tt(n)))
    out = np.zeros(n)
    for r in voices:
        for c in ((-detune, detune) if detune else (0.0,)):
            out += additive(f * r * 2 ** (c / 1200), n, kind, harm, ph0=rng.uniform(0, TAU))
    return out


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
    "hall": make_ir(15, 3.2, 3.6, 8500, 1300, predelay=0.03, early=12),
    "huge": make_ir(16, 5.0, 5.5, 7000, 900, predelay=0.045, early=14),
}


def reverb(x, room, wet, circular=False):
    """Свёртка с откликом. У петель — кольцевая: отклик сворачивается по длине петли (установившийся режим)."""
    h = ROOMS[room]
    n = len(x)
    if circular:
        hh = np.zeros(n)
        for k in range(0, len(h), n):
            seg = h[k:k + n]
            hh[:len(seg)] += seg
        y = np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(hh), n)
    else:
        m = 1 << int(math.ceil(math.log2(n + len(h))))
        y = np.fft.irfft(np.fft.rfft(x, m) * np.fft.rfft(h, m), m)[:n]
    return x + wet * y * np.max(np.abs(x)) / max(1e-9, np.max(np.abs(y)))


# ---------------------------------------------------------------- кирпичики «аниме-ульты»

def riser(rng, n, t0, t1, f0=110.0, f1=880.0, voices=(1.0, 1.5, 2.0), harm=6, p=1.7, tone=659.3,
          trem=(3.0, 18.0), sub=0.5, air=0.5, stack=0.85, sparkle=0.25, build=1.5):
    """Нарастание: пучок тонов скользит вверх с ускорением, второй пучок вступает с середины, мерцающий держащийся
    тон, шум-свип вверх, растущий суб-гул, тремоло ускоряется, общая громкость растёт (крещендо).
    Конец — на максимуме (дальше играет выпуск)."""
    D = t1 - t0
    k = ramp(n, t0, t1)
    rate = trem[0] + (trem[1] - trem[0]) * k ** 2
    lfo = 0.65 + 0.35 * np.sin(phase(rate, n))
    st = gliss(rng, n, acc(t0, t1, f0, f1, p), voices, harm, detune=3.0)
    st = ffilt(st, lambda f: hp(f, 140) * lp(f, 7000)) * lfo
    tm = t0 + 0.4 * D
    st2 = gliss(rng, n, acc(tm, t1, f0 * 2, f1 * 2.5, p), (1.0, 1.26), 5, detune=3.0) * ramp(n, tm, t1, 1.5) * lfo
    hiss = sweep_bp(white(rng, n), 300, 10000, t0, t1, 0.7, p) * k ** 1.5
    rum = brown(rng, n, 120) * (0.3 + 0.7 * k)
    sb = sine(fcurve(n, [(t0, 36.0), (t1, 52.0)]), n) * (0.2 + 0.8 * k)
    held = tone * (1 + 0.006 * np.sin(TAU * 5.5 * tt(n)))
    held = (sine(held, n) + 0.45 * sine(2 * held, n) + 0.2 * sine(3 * held, n)) * ramp(n, t0 + 0.2 * D, t0 + 0.6 * D)
    held *= 0.75 + 0.25 * np.sin(TAU * 7 * tt(n))
    cym = band(colored(rng, n, -2), 3000, 14000) * ramp(n, t1 - min(0.9, 0.5 * D), t1, 3)
    spark = sparkles(rng, n, 70, 3000, 11000, (0.01, 0.06), density=lambda q_: ((q_ - t0) / D) ** 2, t_from=t0, t_to=t1)
    x = mix((stack, st), (0.45 * stack, st2), (air, hiss), (0.5 * sub, rum), (sub, sat(sb, 2.0)), (0.3, held),
            (0.3, cym), (sparkle, spark))
    return x * (0.05 + 0.95 * k ** build) * (tt(n) >= t0)


def shing(rng, n, t0, dur=0.22, f0=700.0, f1=12000.0, ring=0.45):
    """«Шшинг»: стремительный свип вверх до верха спектра (тон + шум следом) и металлический звон в конце."""
    f = fcurve(n, acc(t0, t0 + dur, f0, f1, 1.8))
    # атака сразу (выпуск должен звучать с первых миллисекунд, без провала после нарастания), дальше рост к верху
    e = ramp(n, t0, t0 + 0.012) * (0.4 + 0.6 * ramp(n, t0, t0 + dur, 1.2)) * np.exp(-np.clip(tt(n) - t0 - dur, 0, None) / 0.045)
    tone = (sine(f, n) + 0.35 * sine(2 * f, n) * np.clip((16000 - 2 * f) / 3000, 0, 1)) * e
    hiss = sfilt(white(rng, n), lambda tc, ff: bp(ff, np.interp(tc, tt(n), f), 0.3)) * e
    rg = bell(n, 3150.0, 0.5, t0 + dur * 0.9, METAL, beat=3.0)
    return mix((0.55, tone), (0.75, hiss), (ring, rg))


def boom(rng, n, t0, size=1.0, f_hi=130.0, f_lo=28.0, bright=9000.0):
    """Широкополосный удар: суб с падением тона, «тело», вспышка шума на весь спектр, гаснущий взрыв."""
    sub = thump(n, f_hi, f_lo, 0.55 * size, drop=0.07 * size, drive=3.0, t0=t0)
    body = thump(n, 340, 110, 0.1 * size, drop=0.012, drive=2.5, t0=t0)
    cr = np.zeros(n)
    place(cr, white(rng, ns(0.03)) * np.exp(-tt(ns(0.03)) / 0.0035), t0)
    blast = sfilt(colored(rng, n, -2.5), lambda tc, f: lp(f, bright * (250 / bright) ** np.clip((tc - t0) / (1.2 * size), 0, 1), 2))
    blast *= env(n, 0.002, 0.35 * size, t0=t0)
    return mix((0.85, sub), (0.55, body), (0.55, cr), (0.95, blast))


def wall(rng, n, t0, t1, lp0=7000.0, lp1=1200.0, sub_f=34.0, notes=(36.7, 55.0, 73.4), hits=3, crackle=0.35,
         fade=1.2, growl=0.5):
    """Стена звука: плотный шум с медленно закрывающимся фильтром, суб, перегруженный низкий аккорд, треск,
    повторные удары; всё сжато компрессором — громкость прижата к потолку, как в образцах."""
    D = max(1e-3, t1 - t0)
    nz = 0.6 * colored(rng, n, -4.5) + 0.4 * colored(rng, n, -2)
    nz = sfilt(nz, lambda tc, f: lp(f, lp0 * (lp1 / lp0) ** np.clip((tc - t0) / D, 0, 1), 2) * (0.4 + 0.6 * bp(f, 700, 1.6)))
    nz *= 0.8 + 0.2 * smooth_noise(rng, n, 2.5)
    sb = sine(sub_f * (1 + 0.04 * smooth_noise(rng, n, 1.5)), n)
    sb = sat(sb + 0.4 * sine(sub_f * 2.003, n), 2.0)
    gr = np.zeros(n)
    for f in notes:
        for c in (-9, 9):
            gr += additive(cents(f, c), n, "saw", 40, ph0=rng.uniform(0, TAU))
    gr = sat(norm(ffilt(gr, lambda f: lp(f, 900, 2))), 3.0)
    cr = crackles(rng, n, 80, (1200, 9000), (0.0006, 0.005), t_from=t0, t_to=t1)
    hs = np.zeros(n)
    for i in range(hits):
        th = t0 + 0.35 + (D - 0.9) * (i + rng.uniform(0.1, 0.8)) / max(1, hits)
        hs += thump(n, rng.uniform(95, 140), 30, 0.35, drop=0.05, drive=2.5, t0=th) * (0.9 - 0.15 * i)
    x = mix((0.95, nz), (0.55, sb), (growl, gr), (crackle, cr), (0.5, hs))
    x = compress(x, -24, 8, 0.004, 0.25)
    shape = ramp(n, t0, t0 + 0.03) * (1 - ramp(n, t1 - fade, t1, 0.8))
    return x * shape


def vwoom(rng, n, t0, dur, f0=1200.0, f1=90.0, voices=(1.0, 0.75, 0.5), harm=7, curve=0.75, power=1.2):
    """Нисходящий хвост «вжуууум»: пучок тонов с гармониками скользит вниз и гаснет (энергия уходит)."""
    k = ramp(n, t0, t0 + dur)
    f = f0 * (f1 / f0) ** (k ** curve)
    out = np.zeros(n)
    for r in voices:
        for c in (-7, 7):
            out += additive(f * r * 2 ** (c / 1200), n, "saw", harm, ph0=rng.uniform(0, TAU))
    out = sfilt(out, lambda tc, ff: lp(ff, np.interp(tc, tt(n), f) * 5, 2))
    return out * ramp(n, t0, t0 + 0.08) * (1 - k) ** power * (tt(n) < t0 + dur)


def csub(L, q, f, pulse=1 / 3, depth=0.25, drive=1.8):
    """Суб зарядки: синус + обертоны, медленная пульсация; сатурация даёт гармоники, слышные и в маленьких динамиках."""
    t = tt(L)
    x = sine(q(f), L) + 0.5 * sine(q(2 * f), L) + 0.25 * sine(q(3 * f), L) + 0.1 * sine(q(4 * f), L)
    x *= (1 - depth) + depth * np.sin(TAU * q(pulse) * t)
    return sat(0.7 * x, drive)


# ---------------------------------------------------------------- петли зарядки (по стихиям)

def charge_fire(rng, L, N, q):
    t = tt(L)
    bed = additive(q(55), L, "saw", 60) + additive(q(55.33), L, "saw", 60) + 0.7 * additive(q(82.33), L, "saw", 40)
    bed = ffilt(bed, lambda f: lp(f, 650, 2) * (0.5 + bp(f, 220, 0.6)), circular=True) * (0.8 + 0.2 * np.sin(TAU * q(2 / 3) * t))
    tonal = mix((0.42, csub(L, q, 41.33)), (0.4, bed), (0.36, shepard(L, 160, 3200, 1.5, 1)), (0.15, shepard(L, 400, 6000, 2.0, 1, 2)))
    swirl = sfilt(colored(rng, N, -3), lambda tc, f: bp(f, 1000 * 2 ** (1.1 * np.sin(TAU * tc)), 0.5))
    noisy = mix((0.9, roar(rng, N, 280, 1300)), (0.25, brown(rng, N, 160)),
                (0.55, crackles(rng, N, 30, (1500, 8000), (0.0008, 0.006))), (0.35, swirl))
    return tonal, noisy


def charge_water(rng, L, N, q):
    t = tt(L)
    pad = chord(L, [hz("D3"), hz("A3"), hz("E4"), hz("F#4")], "tri", 8, 2, 4.0, 0.004, 25, rng, q)
    pad = flanger(ffilt(pad, lambda f: lp(f, 2200), circular=True), q(1 / 3), 0.6, 3.5, 0.8, circular=True)
    tonal = mix((0.4, csub(L, q, 36.67, 2 / 3, 0.3)), (0.45, pad), (0.22, shepard(L, 250, 3000, 2.0, 1)))
    waves = band(colored(rng, N, -4), 150, 700) * (0.35 + 0.65 * np.sin(np.pi * tt(N) / 1.5) ** 2)
    hiss = band(white(rng, N), 2500, 9000) * (0.5 + 0.5 * np.sin(np.pi * (tt(N) - 0.3) / 1.5) ** 2)
    noisy = mix((0.6, waves), (0.55, bubbles(rng, N, 14, 200, 1000)), (0.35, pings(rng, N, 5, 2500, 7000, 0, N / SR)),
                (0.15, hiss), (0.15, brown(rng, N, 120)))
    return tonal, noisy


def charge_wind(rng, L, N, q):
    t = tt(L)
    pipes = (sine(q(220), L) + 0.7 * sine(q(330), L) * (0.6 + 0.4 * np.sin(TAU * q(2 / 3) * t))
             + 0.5 * sine(q(495), L) * (0.6 + 0.4 * np.sin(TAU * q(1) * t + 1)) + 0.35 * sine(q(660), L))
    oo = choir(rng, L, [hz("A3"), hz("E4")], ("u",), q=q, circular=True)
    tonal = mix((0.3, csub(L, q, 40.0, 1 / 3, 0.35)), (0.25, pipes), (0.25, oo), (0.2, shepard(L, 300, 4500, 2.0, 1)))
    x = white(rng, N)
    c1 = smooth_noise(rng, N, 1.5, math.log(350), math.log(2400))
    c2 = smooth_noise(rng, N, 1.2, math.log(500), math.log(3200))
    whistle = sfilt(x, lambda tc, f: bp(f, np.exp(np.interp(tc, tt(N), c1)), 0.16) + 0.7 * bp(f, np.exp(np.interp(tc, tt(N), c2)), 0.2))
    body = ffilt(colored(rng, N, -3), lambda f: lp(f, 900))
    gust = 0.6 + 0.4 * smooth_noise(rng, N, 1.6)
    noisy = mix((0.6, whistle * gust), (0.55, body * gust), (0.15, band(white(rng, N), 5000, 12000)))
    return tonal, noisy


def charge_earth(rng, L, N, q):
    t = tt(L)
    drone = additive(q(41.33), L, "saw", 80) + 0.8 * additive(q(61.67), L, "saw", 60)
    drone = ffilt(drone, lambda f: lp(f, 350, 2) * (0.3 + bp(f, 123, 0.15) + 0.7 * bp(f, 185, 0.12)), circular=True)
    stone = (sine(q(110), L) + sine(q(110.33), L) + 0.6 * sine(q(165), L)) * (0.7 + 0.3 * np.sin(TAU * q(1 / 3) * t))
    tonal = mix((0.45, csub(L, q, 32.67, 4 / 3, 0.3, 2.4)), (0.45, drone), (0.22, stone), (0.2, shepard(L, 70, 900, 2.0, 1)))
    thuds = np.zeros(N)
    for tq in (0.75, 1.5, 2.25, 3.0):
        thuds += thump(N, 90, 38, 0.18, drive=2.5, t0=tq)
    crunch = ffilt(crackles(rng, N, 22, (150, 1500), (0.002, 0.012)), lambda f: lp(f, 1600))
    noisy = mix((0.3, brown(rng, N, 180)), (0.65, crunch), (0.4, thuds), (0.25, crackles(rng, N, 8, (1000, 4000))),
                (0.3, band(colored(rng, N, -3), 200, 1500) * (0.6 + 0.4 * smooth_noise(rng, N, 6))))
    return tonal, noisy


def charge_lightning(rng, L, N, q):
    t = tt(L)
    hum = ffilt(additive(q(50), L, "saw", 60) + 0.6 * additive(q(100.33), L, "square", 40), lambda f: lp(f, 2500) * hp(f, 80), circular=True)
    hum *= 0.8 + 0.2 * np.sin(TAU * q(4) * t)
    tonal = mix((0.35, csub(L, q, 41.33, 1, 0.2)), (0.4, hum), (0.32, shepard(L, 600, 9000, 2.0, 1, 2)))
    jitter = 1 + 0.05 * smooth_noise(rng, N, 120)
    flutter = 0.35 + 0.65 * (smooth_noise(rng, N, 40) > -0.1)
    buzz = sfilt(additive(110 * jitter, N, "saw", 80), lambda tc, f: bp(f, 1200, 1.0)) * flutter
    zaps = np.zeros(N)
    for _ in range(4):
        m = ns(0.12)
        place(zaps, additive(glide(m, rng.uniform(2000, 3500), 180, 0.1), m, "saw", 25) * env(m, 0.001, 0.05), rng.uniform(0, N / SR))
    sizzle = band(white(rng, N), 4000, 12000) * (0.5 + 0.5 * smooth_noise(rng, N, 30))
    noisy = mix((0.45, buzz), (0.55, crackles(rng, N, 90, (2000, 10000), (0.0004, 0.003))), (0.3, zaps), (0.12, sizzle),
                (0.12, brown(rng, N, 120)))
    return tonal, noisy


def charge_light(rng, L, N, q):
    t = tt(L)
    w = 0.5 + 0.5 * np.cos(TAU * t / LOOP_LEN)
    ch = choir(rng, L, [hz("A3"), hz("C#4"), hz("E4"), hz("A4"), hz("E5")], ("a", "o"), (w, 1 - w), q=q, circular=True)
    ring = (sine(q(1760), L) + 0.6 * sine(q(1760.33), L) + 0.5 * sine(q(2637), L)) * (0.7 + 0.3 * np.sin(TAU * q(6) * t))
    tonal = mix((0.3, csub(L, q, 55.0, 1 / 3, 0.2, 1.4)), (0.55, ch), (0.14, ring), (0.3, shepard(L, 500, 9000, 2.0, 1)))
    strikes = np.zeros(N)
    for tq, f in ((0.4, hz("E6")), (1.9, hz("A6"))):
        strikes += bell(N, f, 0.6, t0=tq)
    noisy = mix((0.35, sparkles(rng, N, 12, 4000, 11000, (0.02, 0.1))), (0.05, band(white(rng, N), 7000, 14000)),
                (0.2, strikes))
    return tonal, noisy


def charge_dark(rng, L, N, q):
    t = tt(L)
    drone = (additive(q(36.67), L, "saw", 90) + additive(q(37.0), L, "saw", 90) + 0.7 * additive(q(55.0), L, "saw", 60)
             + 0.5 * additive(q(77.67), L, "saw", 50))
    drone = ffilt(drone * (0.75 + 0.25 * np.sin(TAU * q(2 / 3) * t)), lambda f: lp(f, 480, 3), circular=True)
    growl = formant(ffilt(additive(q(73.33), L, "saw", 60), lambda f: lp(f, 2000), circular=True), "o", circular=True)
    tonal = mix((0.45, csub(L, q, 32.67, 1 / 3, 0.3, 2.2)), (0.5, drone), (0.3, growl),
                (0.3, shepard(L, 80, 1600, 2 ** 0.5, -1)))
    noisy = mix((0.45, whisper(rng, N, 2.5)), (0.3, whisper(rng, N, 1.5)),
                (0.5, rswells(rng, N, (0.9, 2.4), 0.8, 200, 2500)), (0.2, brown(rng, N, 110)))
    return tonal, noisy


def charge_space(rng, L, N, q):
    t = tt(L)
    sb = csub(L, q, 30.67, 1, 0.6, 2.2)
    glass = sum(sine(q(f), L) * (0.6 + 0.4 * np.sin(TAU * q(r) * t + i)) for i, (f, r) in
                enumerate(((987.67, 1 / 3), (1318.33, 2 / 3), (1567.67, 1), (2093.33, 4 / 3))))
    warp = np.sin(TAU * q(220) * t + (1.5 + np.sin(TAU * q(1 / 3) * t)) * np.sin(TAU * q(330) * t))
    pad = ffilt(additive(q(110), L, "saw", 40) + additive(q(164.67), L, "saw", 30) + additive(q(207.67), L, "saw", 30),
                lambda f: lp(f, 1500), circular=True)
    pad = flanger(pad, q(1 / 3), 0.5, 4.0, 0.9, circular=True)
    tonal = mix((0.45, sb), (0.2, glass), (0.2, warp), (0.35, pad), (0.2, shepard(L, 200, 4000, 2.0, -1)))
    ph = flanger(band(colored(rng, N, -3), 300, 6000), 0.5, 0.5, 6.0, 0.9)
    noisy = mix((0.4, rswells(rng, N, (0.75, 1.5, 2.25, 3.0), 0.35, 800, 9000, -2, 4)), (0.4, ph), (0.1, brown(rng, N, 90)))
    return tonal, noisy


def charge_life(rng, L, N, q):
    t = tt(L)
    pad = chord(L, [hz("D3"), hz("A3"), hz("F#4"), hz("E5")], "tri", 7, 2, 4.0, 0.004, 25, rng, q)
    pad = ffilt(pad, lambda f: lp(f, 2200), circular=True) * (0.75 + 0.25 * np.sin(TAU * q(1 / 3) * t))
    tonal = mix((0.3, csub(L, q, 36.67, 1 / 3, 0.2, 1.4)), (0.55, pad), (0.2, shepard(L, 400, 6000, 2.0, 1)))
    heart = np.zeros(N)
    for tq in (0.2, 1.7, 3.2):
        heart += thump(N, 75, 45, 0.09, drive=1.6, t0=tq) + 0.6 * thump(N, 70, 45, 0.1, drive=1.6, t0=tq + 0.22)
    chimes = np.zeros(N)
    for tq, nt in zip(rng.uniform(0, N / SR, 5), rng.choice(["D6", "E6", "F#6", "A6", "B6", "D7"], 5)):
        chimes += bell(N, hz(nt), 0.5, t0=tq, bright=0.5) * rng.uniform(0.4, 1.0)
    rustle = np.zeros(N)
    for _ in range(rng.poisson(9 * N / SR)):
        m = ns(rng.uniform(0.02, 0.08))
        place(rustle, rng.standard_normal(m) * np.hanning(m) * rng.uniform(0.3, 1.0), rng.uniform(0, N / SR))
    rustle = band(rustle, 2000, 7000)
    noisy = mix((0.3, heart), (0.3, chimes), (0.22, rustle), (0.2, sparkles(rng, N, 5, 4000, 9000, (0.03, 0.12))))
    return tonal, noisy


def charge_mind(rng, L, N, q):
    t = tt(L)
    glass = (sine(q(hz("E6")), L) + 0.8 * sine(q(hz("F6")), L) + 0.6 * sine(q(hz("B5") + 1 / 3), L) + 0.5 * sine(q(hz("B5")), L))
    glass *= 0.55 + 0.45 * np.sin(TAU * q(7) * t)
    pad = chord(L, [hz("C4"), hz("F#4"), hz("B4")], "tri", 12, 2, 3.0, 0.006, 25, rng, q)
    pad = flanger(ffilt(pad, lambda f: lp(f, 2500), circular=True), q(2 / 3), 0.8, 5.0, 0.9, circular=True)
    tonal = mix((0.3, csub(L, q, 36.67, 4 / 3, 0.45, 1.6)), (0.22, glass), (0.4, pad), (0.22, shepard(L, 250, 5000, 1.5, -1)))
    glitches = np.zeros(N)
    src = mix((1, band(white(rng, ns(0.5)), 800, 9000)), (0.8, additive(rng.uniform(300, 900), ns(0.5), "square", 20)))
    for _ in range(rng.poisson(3 * N / SR)):
        g = crush(stutter(rng, src, ns(rng.uniform(0.04, 0.12))), 4, 8)
        place(glitches, g * rng.uniform(0.3, 1.0), rng.uniform(0, N / SR))
    noisy = mix((0.4, whisper(rng, N, 3.0)), (0.3, whisper(rng, N, 2.0)), (0.22, glitches))
    return tonal, noisy


def charge_summon(rng, L, N, q):
    t = tt(L)
    ch = choir(rng, L, [hz("D3"), hz("A3"), hz("D4"), hz("F4")], ("o", "u"), q=q, circular=True)
    f0 = q(415.33)
    metal = sum(a * (sine(q(f0 * r), L) + sine(q(f0 * r) + 1 / 3, L)) for r, a in ((1, 1.0), (2.76, 0.6), (5.4, 0.35), (8.93, 0.2)))
    metal *= 0.6 + 0.4 * np.sin(TAU * q(2) * t)
    tonal = mix((0.35, csub(L, q, 36.67, 2 / 3, 0.3)), (0.5, ch), (0.2, metal), (0.2, shepard(L, 300, 5000, 1.5, 1)))
    lo_, hi_ = math.log(300), math.log(3000)
    swirl = sfilt(white(rng, N), lambda tc, f: bp(f, np.exp(lo_ + (hi_ - lo_) * (0.5 + 0.5 * np.sin(TAU * (2 / 3) * tc))), 0.35)
                  + 0.7 * bp(f, np.exp(lo_ + (hi_ - lo_) * (0.5 - 0.5 * np.sin(TAU * (2 / 3) * tc))), 0.35))
    noisy = mix((0.4, swirl), (0.2, ffilt(colored(rng, N, -3), lambda f: lp(f, 600))), (0.15, sparkles(rng, N, 6, 3000, 9000)))
    return tonal, noisy


def charge_fusion(rng, L, N, q):
    t = tt(L)
    ch = choir(rng, L, [hz("A3"), hz("E4"), hz("B4")], ("a",), q=q, circular=True)
    gr = ffilt(additive(q(36.67), L, "saw", 90) + additive(q(55.33), L, "saw", 60), lambda f: lp(f, 420, 3), circular=True)
    gr *= 0.75 + 0.25 * np.sin(TAU * q(2 / 3) * t)
    tonal = mix((0.42, csub(L, q, 32.67, 2 / 3, 0.35, 2.2)), (0.35, ch), (0.4, gr), (0.26, shepard(L, 400, 8000, 2.0, 1)),
                (0.22, shepard(L, 60, 1200, 2.0, -1)))
    noisy = mix((0.3, crackles(rng, N, 40, (1500, 9000))), (0.25, sparkles(rng, N, 10, 4000, 11000)),
                (0.4, roar(rng, N, 300, 1500)), (0.25, whisper(rng, N, 2.0)))
    return tonal, noisy


# ---------------------------------------------------------------- касты (выпуск) по стихиям

def cast_fire(rng, v, n):
    th = 0.05 + 0.01 * v
    hi = (1900, 2400, 1600)[v]
    fw = track_bp(colored(rng, n, -3), [(th, 260), (th + 0.18, hi), (th + 0.75, 550)], 1.0) * env(n, 0.03, 0.32 + 0.05 * v, t0=th)
    body = ffilt(colored(rng, n, -6), lambda f: lp(f, 700)) * env(n, 0.02, 0.42, t0=th)
    cr = crackles(rng, n, 55, (1500, 8000), density=lambda t: math.exp(-max(0.0, t - th) / 0.4), t_from=th)
    sw = gliss(rng, n, acc(th, th + 0.32, 280, 1500, 1.3), (1.0, 1.5), 6) * env(n, 0.02, 0.12, hold=0.18, t0=th)
    x = mix((0.35, inhale(rng, n, th, 300, 1500)), (0.85, fw), (0.5, body), (0.35, cr),
            (0.6, thump(n, 105 - 8 * v, 42, 0.15, t0=th)), (0.18, sw))
    if v == 2:          # двойной выдох — «фвумм-фвумм»
        x += 0.6 * norm(track_bp(colored(rng, n, -3), [(th + 0.2, 300), (th + 0.38, 2000), (th + 0.9, 500)], 1.0)
                        * env(n, 0.03, 0.3, t0=th + 0.2))
    return reverb(sat(norm(x), 1.6), "medium", 0.25)


def cast_water(rng, v, n):
    th = 0.04
    if v == 2:          # лёд: щелчок, свип вниз, кристаллы, хруст
        sweep = sweep_bp(white(rng, n), 7500, 2400, th, th + 0.3, 0.5) * env(n, 0.004, 0.22, t0=th)
        ping = pings(rng, n, 14, 2500, 8000, th, th + 0.25, (0.04, 0.25))
        crunch = crackles(rng, n, 180, (2500, 9000), (0.0005, 0.003), t_from=th, t_to=th + 0.16)
        x = mix((0.4, inhale(rng, n, th, 3000, 9000, 0.5, 0)), (0.5, click(rng, n, th, 0.002, 3000)), (0.6, sweep),
                (0.6, ping), (0.4, crunch), (0.35, thump(n, 160, 80, 0.06, t0=th)), (0.2, bell(n, 2637, 0.4, th + 0.05, GLASS)))
        return reverb(x, "medium", 0.3)
    jet = track_bp(colored(rng, n, -3) + 0.4 * norm(white(rng, n)), [(th, 450), (th + 0.12, 2600 - 700 * v), (th + 0.7, 650)], 0.9)
    jet *= env(n, 0.02, 0.28 + 0.12 * v, t0=th)
    spray = band(white(rng, n), 3000, 12000) * env(n, 0.005, 0.12 + 0.05 * v, t0=th)
    bub = bubbles(rng, n, 160, 250, 1800, density=lambda t: math.exp(-max(0.0, t - th) / 0.25), t_from=th, t_to=0.9)
    waves = band(colored(rng, n, -4), 120, 650) * swell(n, th + 0.12, decay=0.4) * v
    x = mix((0.3, inhale(rng, n, th, 300, 1500)), (0.8, jet), (0.3, spray), (0.45, bub), (0.4, waves),
            (0.5, thump(n, 95, 40, 0.18, t0=th)))
    return reverb(sat(norm(x), 1.3), "medium", 0.28)


def cast_wind(rng, v, n):
    th = 0.03
    x = colored(rng, n, -2.5)
    if v == 1:          # «режущий» взмах: очень быстрый свист и тонкий звон
        wh = track_bp(x, [(0, 5000), (th + 0.06, 7000), (th + 0.2, 1800), (th + 0.6, 900)], 0.5) * env(n, 0.01, 0.16, t0=th)
        whistle = sine(fcurve(n, [(th, 3600), (th + 0.25, 1400)]), n) * env(n, 0.005, 0.12, t0=th)
        ring = bell(n, 3900, 0.25, th + 0.05, METAL, beat=4.0)
        y = mix((0.9, wh), (0.25, whistle), (0.2, ring), (0.3, thump(n, 140, 60, 0.06, t0=th)))
    else:
        wh = track_bp(x, [(0, 400), (th + 0.15, 3800), (th + 0.75, 800)], 0.8) * env(n, 0.06, 0.3, t0=0.0)
        whistle = sfilt(white(rng, n), lambda tc, f: bp(f, 2400 * 2 ** (-1.2 * np.clip(tc / 0.6, 0, 1)), 0.08)) * env(n, 0.05, 0.25)
        y = mix((0.9, wh), (0.35, whistle), (0.35, ffilt(colored(rng, n, -5), lambda f: lp(f, 300)) * env(n, 0.05, 0.3)),
                (0.25, thump(n, 90, 45, 0.12, t0=th)))
        if v == 2:      # вихрь: второй, встречный порыв
            y += 0.7 * norm(track_bp(colored(rng, n, -2.5), [(0.18, 600), (0.32, 3200), (0.9, 700)], 0.7) * env(n, 0.05, 0.28, t0=0.18))
    return reverb(y, "medium", 0.3)


def cast_earth(rng, v, n):
    th = 0.03
    boom_ = thump(n, 82 - 8 * v, 30, 0.28, drive=3.0, t0=th)
    crunch = ffilt(crackles(rng, n, 100, (150, 1500), (0.002, 0.012), t_from=th, t_to=th + 0.3), lambda f: lp(f, 1500))
    rum = brown(rng, n, 200) * env(n, 0.02, 0.5, t0=th)
    debris = crackles(rng, n, 35, (1000, 4500), (0.001, 0.005), t_from=th + 0.1, t_to=0.75)
    grind = track_bp(colored(rng, n, -4), [(th, 110), (th + 0.35, 700)], 0.6) * env(n, 0.05, 0.25, hold=0.1, t0=th)
    rum = band(colored(rng, n, -4), 50, 500) * env(n, 0.02, 0.45, t0=th)
    rock = band(white(rng, n), 250, 3500) * env(n, 0.001, 0.05, t0=th) + band(colored(rng, n, -3), 150, 2000) * env(n, 0.005, 0.18, t0=th)
    x = mix((0.55, boom_), (0.9, crunch), (0.4, rum), (0.45, debris), (0.6, grind), (0.6, rock),
            (0.45, thump(n, 240 - 15 * v, 110, 0.09, drop=0.015, drive=2.5, t0=th)))
    if v == 2:          # треск расколотого камня
        x += 0.5 * norm(click(rng, n, th + 0.01, 0.006, 600))
    return reverb(sat(norm(x), 1.6), "medium", 0.25)


def cast_lightning(rng, v, n):
    th = 0.02
    zap = additive(fcurve(n, [(th, 3200 - 300 * v), (th + 0.11, 160)]), n, "saw", 30) * env(n, 0.001, 0.08, t0=th)
    flutter = 0.4 + 0.6 * (smooth_noise(rng, n, 55) > -0.2)
    buzz = sfilt(additive(115 * (1 + 0.06 * smooth_noise(rng, n, 120)), n, "saw", 80), lambda t, f: bp(f, 1100, 1.1))
    buzz *= env(n, 0.004, 0.28, t0=th) * flutter
    cr = crackles(rng, n, 260, (2000, 11000), (0.0004, 0.003), density=lambda t: math.exp(-max(0, t - th) / 0.18), t_from=th)
    roll = band(colored(rng, n, -3), 60, 1200) * env(n, 0.03, 0.35, t0=th + 0.05) * (0.6 + 0.4 * smooth_noise(rng, n, 9))
    x = mix((0.6, zap), (0.55, buzz), (0.55, cr), (0.7, click(rng, n, th, 0.0025, 800)), (0.35, roll),
            (0.4, thump(n, 120, 50, 0.08, t0=th)))
    return reverb(sat(norm(x), 2.0), "medium", 0.25)


def cast_light(rng, v, n):
    th = 0.04
    f0 = (hz("E5"), hz("F#5"), hz("A5"))[v]
    up = (sine(fcurve(n, acc(0, th + 0.1, 900, 6500, 1.5)), n)) * ramp(n, 0, th) * np.exp(-np.clip(tt(n) - th - 0.1, 0, None) / 0.04)
    b = bell(n, f0, 0.7, th) + 0.5 * bell(n, f0 * 1.5, 0.5, th + 0.02) + 0.3 * bell(n, f0 * 2, 0.4, th + 0.04)
    ch = choir(rng, n, [f0 / 2, f0 / 2 * 1.26, f0 / 2 * 1.5, f0], ("a", "o")) * env(n, 0.05, 0.45, t0=th)
    flash = band(white(rng, n), 4000, 14000) * env(n, 0.002, 0.06, t0=th)
    x = mix((0.35, up), (0.6, b), (0.5, ch), (0.25, flash), (0.4, sparkles(rng, n, 30, 4500, 11000, (0.02, 0.1), t_from=th, t_to=0.6)),
            (0.3, thump(n, 130, 70, 0.08, t0=th)))
    return reverb(x, "large", 0.42)


def cast_dark(rng, v, n):
    th = 0.1 + 0.02 * v
    pre = rswells(rng, n, [th], th, 200, 2000, -3, 3)
    hit = thump(n, 72, 30, 0.35, drive=2.5, t0=th)
    gr = additive(55, n, "saw", 60) + additive(55 * 1.06, n, "saw", 60) + 0.6 * additive(82.4 * 0.97, n, "saw", 40)
    gr = sfilt(gr * env(n, 0.01, 0.42, t0=th), lambda t, f: lp(f, 200 + 1000 * np.exp(-np.clip(t - th, 0, None) / 0.25), 2))
    fall = sweep_bp(colored(rng, n, -3), 700, 160, th, th + 0.55, 0.8) * env(n, 0.01, 0.32, t0=th)
    wh = whisper(rng, n, 4.0) * env(n, 0.05, 0.3, t0=th)
    x = mix((0.45, pre), (0.5, hit), (0.65, gr), (0.6, fall), (0.4, wh), (0.45, thump(n, 210, 90, 0.08, drop=0.015, drive=2.5, t0=th)))
    return reverb(sat(norm(x), 1.6), "dark", 0.4)


def cast_space(rng, v, n):
    th = 0.12 - 0.02 * v
    pre = rswells(rng, n, [th], th, 600, 9000, -2, 4)
    f = fcurve(n, [(th, 2200 - 300 * v), (th + 0.3, 180 + 40 * v)])
    warp = np.sin(phase(f, n) + 2.0 * np.sin(phase(1.5 * f, n))) * env(n, 0.003, 0.25, t0=th)
    swoosh = flanger(track_bp(colored(rng, n, -2), [(th, 6000), (th + 0.4, 500)], 0.6) * env(n, 0.005, 0.3, t0=th), 3.0, 0.6, 4.0, 0.9)
    glass = bell(n, (1975.5, 2349.3, 2637.0)[v], 0.5, th, GLASS, beat=2.0)
    x = mix((0.5, pre), (0.6, warp), (0.55, swoosh), (0.4, glass), (0.5, thump(n, 90, 28, 0.3, drive=2.5, t0=th)),
            (0.3, click(rng, n, th, 0.002, 2000)))
    return reverb(x, "large", 0.4)


def cast_life(rng, v, n):
    th = 0.06
    notes = [["D6", "F#6", "A6"], ["E6", "A6", "B6"], ["A5", "D6", "F#6"]][v]
    ch = sum(bell(n, hz(nt), 0.5, th + 0.06 * i, bright=0.5) * (1 - 0.15 * i) for i, nt in enumerate(notes))
    pad = ffilt(chord(n, [hz("D4"), hz("F#4"), hz("A4"), hz("E5")], "tri", 6, 2, 5.0, 0.004, 25, rng), lambda f: lp(f, 2800))
    pad *= env(n, 0.1, 0.45, hold=0.1)
    up = sweep_bp(white(rng, n), 600, 5000, 0, th + 0.25, 0.7) * swell(n, th + 0.2, decay=0.25)
    rustle = np.zeros(n)
    for _ in range(14):
        m = ns(rng.uniform(0.01, 0.05))
        place(rustle, rng.standard_normal(m) * np.hanning(m), rng.uniform(th, th + 0.4))
    x = mix((0.55, ch), (0.5, pad), (0.35, up), (0.25, band(rustle, 2500, 8000)),
            (0.3, sparkles(rng, n, 25, 4000, 10000, (0.03, 0.12), t_from=th, t_to=0.8)), (0.3, thump(n, 100, 55, 0.12, t0=th)))
    return reverb(x, "large", 0.4)


def cast_mind(rng, v, n):
    th = 0.05
    t = tt(n)
    glass = sum(sine(cents(hz(nt), 8 * v), n) for nt in ("E6", "F6", "B6")) * (0.55 + 0.45 * np.sin(TAU * 11 * t))
    glass *= env(n, 0.06, 0.3, hold=0.1)
    wob = sine(fcurve(n, [(0, 600), (0.5, 900 + 100 * v)]) * (1 + 0.03 * np.sin(TAU * 9 * t)), n) * env(n, 0.02, 0.3, t0=th)
    src = mix((1, band(white(rng, ns(0.4)), 600, 9000)), (0.9, additive(700 + 150 * v, ns(0.4), "square", 20)))
    gl = np.zeros(n)
    place(gl, crush(stutter(rng, src, ns(0.22)), 4, 6) * env(ns(0.22), 0.002, 0.15), th + 0.05)
    whoosh = flanger(sweep_bp(white(rng, n), 800, 5000, 0, 0.4, 0.6) * env(n, 0.05, 0.25), 4.0, 1.0, 5.0, 0.9)
    x = mix((0.45, glass), (0.45, wob), (0.4, gl), (0.4, whoosh), (0.35, whisper(rng, n, 5.0) * env(n, 0.03, 0.35)),
            (0.3, thump(n, 110, 60, 0.1, t0=th)))
    return reverb(x, "large", 0.38)


def cast_summon(rng, v, n):
    t_hit = 0.2 + 0.03 * v
    swirl = sfilt(white(rng, n), lambda tc, f: bp(f, 250 * (12 + 2 * v) ** np.clip(tc / t_hit, 0, 1) * 2 ** (0.3 * np.sin(TAU * 6 * tc)), 0.4))
    swirl *= swell(n, t_hit, decay=0.35)
    hum = ffilt(additive(82.4, n, "saw", 40), lambda f: lp(f, 600)) * swell(n, t_hit, power=1.5, decay=0.6)
    metal = bell(n, (415.3, 466.2, 392.0)[v], 0.8, t_hit, METAL, beat=1.5)
    ch = choir(rng, n, [hz("D4"), hz("A4"), hz("F5")], ("o",)) * env(n, 0.1, 0.5, t0=t_hit - 0.05)
    x = mix((0.6, swirl), (0.3, hum), (0.5, metal), (0.4, ch), (0.5, thump(n, 85, 32, 0.35, drive=2.2, t0=t_hit)),
            (0.25, sparkles(rng, n, 25, 3000, 9000, t_from=t_hit, t_to=1.0)))
    return reverb(x, "large", 0.42)


def cast_fusion(rng, v, n):
    th = 0.12
    sh = shing(rng, n, 0.0, th, 900, 9000, 0.35)
    gr = additive(41.2, n, "saw", 70) + additive(41.2 * 1.5, n, "saw", 50)
    gr = sfilt(gr * env(n, 0.01, 0.5, t0=th), lambda t, f: lp(f, 250 + 1500 * np.exp(-np.clip(t - th, 0, None) / 0.2), 2))
    b = bell(n, (hz("E5"), hz("G5"), hz("B4"))[v], 0.7, th) + 0.6 * bell(n, hz("B5"), 0.5, th + 0.03)
    x = mix((0.5, sh), (0.75, boom(rng, n, th, 0.55)), (0.5, gr), (0.5, b),
            (0.35, crackles(rng, n, 80, (1500, 9000), density=lambda t: math.exp(-max(0, t - th) / 0.35), t_from=th)),
            (0.3, sparkles(rng, n, 30, 4000, 11000, t_from=th, t_to=1.0)), (0.4, roar(rng, n, 300, 1500) * env(n, 0.03, 0.4, t0=th)))
    return reverb(sat(norm(x), 1.6), "hall", 0.35)


# ---------------------------------------------------------------- попадания по стихиям

def impact_fire(rng, v, n):
    burst = track_lp(colored(rng, n, -2.5), [(0, 9000), (0.35, 800), (1.0, 400)]) * env(n, 0.001, 0.22 + 0.04 * v)
    rr = roar(rng, n, 250, 900) * env(n, 0.03, 0.45, t0=0.03)
    cr = crackles(rng, n, 70, (1500, 8000), density=lambda t: math.exp(-t / 0.35))
    x = mix((0.9, burst), (0.6, rr), (0.45, cr), (0.6, thump(n, 110 + 10 * v, 40, 0.2, drive=2.5)), (0.45, click(rng, n, 0, 0.003, 600)),
            (0.35, thump(n, 260, 120, 0.07, drop=0.012, drive=2.5)))
    return reverb(sat(norm(x), 1.8), "medium", 0.28)


def impact_water(rng, v, n):
    if v == 2:          # ледяной осколок разбивается
        x = mix((0.7, pings(rng, n, 30, 2000, 9000, 0.0, 0.25, (0.03, 0.25))), (0.5, sweep_bp(white(rng, n), 8000, 5000, 0, 0.1, 0.5) * env(n, 0.001, 0.08)),
                (0.45, crackles(rng, n, 220, (3000, 10000), (0.0004, 0.002), density=lambda t: math.exp(-t / 0.08))),
                (0.4, thump(n, 150, 75, 0.06)), (0.3, click(rng, n, 0, 0.002, 2500)))
        return reverb(x, "medium", 0.3)
    splash = band(white(rng, n), 700, 11000) * env(n, 0.002, 0.12 + 0.05 * v)
    body = track_bp(colored(rng, n, -3), [(0, 1800), (0.4, 400)], 1.0) * env(n, 0.005, 0.25)
    drops = bubbles(rng, n, 220, 600, 4500, (0.008, 0.03), 1.5, density=lambda t: math.exp(-t / 0.3))
    x = mix((0.75, splash), (0.6, body), (0.45, drops), (0.6, thump(n, 100, 45, 0.15, drive=2.0)),
            (0.3, bubbles(rng, n, 40, 150, 500, (0.03, 0.06), density=lambda t: math.exp(-t / 0.4))))
    return reverb(sat(norm(x), 1.3), "medium", 0.3)


def impact_wind(rng, v, n):
    wh = track_bp(colored(rng, n, -2), [(0, 3000 + 500 * v), (0.5, 450)], 0.9) * env(n, 0.003, 0.22)
    sw = sfilt(white(rng, n), lambda tc, f: bp(f, 1600 * 2 ** (0.8 * np.sin(TAU * (5 + v) * tc)) * 2 ** (-np.clip(tc, 0, 1)), 0.15))
    sw *= env(n, 0.01, 0.3)
    x = mix((0.85, wh), (0.35, sw), (0.5, click(rng, n, 0, 0.003, 1500)), (0.4, thump(n, 110, 50, 0.1)))
    return reverb(x, "medium", 0.3)


def impact_earth(rng, v, n):
    x = mix((0.55, thump(n, 85 - 6 * v, 28, 0.3, drive=3.0)), (0.5, thump(n, 230 - 15 * v, 100, 0.1, drop=0.015, drive=2.5)),
            (0.9, ffilt(crackles(rng, n, 180, (150, 2000), (0.002, 0.012), t_to=0.35), lambda f: lp(f, 2000))),
            (0.6, band(white(rng, n), 250, 4000) * env(n, 0.001, 0.06) + band(colored(rng, n, -3), 150, 2000) * env(n, 0.005, 0.2)),
            (0.4, crackles(rng, n, 60, (1000, 5000), (0.001, 0.006), density=lambda t: math.exp(-t / 0.4), t_from=0.05)),
            (0.45, band(colored(rng, n, -4), 50, 700) * env(n, 0.01, 0.5)), (0.45, click(rng, n, 0, 0.005, 300)))
    return reverb(sat(norm(x), 1.8), "medium", 0.3)


def impact_lightning(rng, v, n):
    cr = crackles(rng, n, 380, (1500, 11000), (0.0003, 0.003), density=lambda t: math.exp(-t / 0.09))
    buzz = sfilt(additive(120 * (1 + 0.08 * smooth_noise(rng, n, 150)), n, "saw", 80), lambda t, f: bp(f, 1100, 1.2))
    buzz *= env(n, 0.002, 0.12 + 0.03 * v) * (0.5 + 0.5 * (smooth_noise(rng, n, 60) > 0))
    roll = band(colored(rng, n, -3), 60, 1500) * env(n, 0.02, 0.35, t0=0.03) * (0.5 + 0.5 * smooth_noise(rng, n, 8))
    x = mix((0.9, click(rng, n, 0, 0.003, 700)), (0.65, cr), (0.5, buzz), (0.5, roll), (0.35, thump(n, 130, 45, 0.12, drive=2.5)))
    return reverb(sat(norm(x), 2.0), "medium", 0.3)


def impact_light(rng, v, n):
    f0 = (hz("A5"), hz("C#6"), hz("E6"))[v]
    b = bell(n, f0, 0.55) + 0.4 * bell(n, f0 / 2, 0.65) + 0.3 * bell(n, f0 * 1.5, 0.4, 0.01)
    flash = band(white(rng, n), 3000, 14000) * env(n, 0.001, 0.05)
    ch = choir(rng, n, [f0 / 4, f0 / 4 * 1.5, f0 / 2], ("a",)) * env(n, 0.01, 0.3)
    x = mix((0.7, b), (0.4, flash), (0.4, ch), (0.4, sparkles(rng, n, 40, 4500, 11000, (0.02, 0.1), t_to=0.4)),
            (0.4, thump(n, 120, 60, 0.07)))
    return reverb(x, "large", 0.38)


def impact_dark(rng, v, n):
    hit = thump(n, 75 - 5 * v, 34, 0.2, drive=2.5)
    body = thump(n, 260 - 20 * v, 140, 0.07, drop=0.02, drive=3.0)
    dull = ffilt(colored(rng, n, -3), lambda f: lp(f, 1100) * hp(f, 120)) * env(n, 0.002, 0.09)
    suck = gliss(rng, n, [(0, 400), (0.5, 60)], (1.0, 1.5), 8) * env(n, 0.01, 0.25)
    suck = ffilt(suck, lambda f: lp(f, 1200))
    x = mix((0.6, hit), (0.5, body), (0.65, dull), (0.4, suck), (0.35, whisper(rng, n, 5.0) * env(n, 0.02, 0.3)))
    return reverb(sat(norm(x), 1.5), "dark", 0.35)


def impact_space(rng, v, n):
    th = 0.09
    pre = rswells(rng, n, [th], th, 800, 10000, -2, 4)
    ring = flanger(bell(n, (196.0, 233.1, 174.6)[v], 0.6, th, GONG, beat=1.0), 2.0, 0.5, 3.0, 0.8)
    glass = bell(n, 2489.0 + 200 * v, 0.4, th, GLASS, beat=3.0)
    warb = np.sin(phase(fcurve(n, [(th, 1200), (th + 0.4, 300)]), n) + 3 * np.sin(phase(37.0, n))) * env(n, 0.005, 0.2, t0=th)
    x = mix((0.55, pre), (0.65, thump(n, 100, 26, 0.35, drive=3.0, t0=th)), (0.55, ring), (0.35, glass), (0.35, warb),
            (0.35, thump(n, 300, 120, 0.06, drop=0.01, drive=2.0, t0=th)))
    return reverb(sat(norm(x), 1.4), "large", 0.4)


def impact_life(rng, v, n):
    notes = [["D6", "A6"], ["E6", "B6"], ["F#6", "D7"]][v]
    ch = sum(bell(n, hz(nt), 0.5, 0.02 + 0.05 * i, bright=0.5) for i, nt in enumerate(notes))
    bloom = sweep_bp(white(rng, n), 1500, 7000, 0.0, 0.5, 0.6) * env(n, 0.05, 0.3)
    rustle = np.zeros(n)
    for _ in range(12):
        m = ns(rng.uniform(0.01, 0.05))
        place(rustle, rng.standard_normal(m) * np.hanning(m), rng.uniform(0, 0.35))
    x = mix((0.6, thump(n, 110, 55, 0.12, drive=1.8)), (0.5, ch), (0.3, bloom), (0.3, band(rustle, 2500, 8000)),
            (0.3, sparkles(rng, n, 35, 4000, 10000, (0.03, 0.12), density=lambda t: min(1.0, t / 0.3), t_to=0.8)))
    return reverb(x, "large", 0.35)


def impact_mind(rng, v, n):
    t = tt(n)
    rm = sine(700 + 120 * v, n) * sine(83 + 20 * v, n) * env(n, 0.002, 0.2)
    src = mix((1, band(white(rng, ns(0.3)), 600, 9000)), (0.9, additive(500 + 200 * v, ns(0.3), "square", 20)))
    gl = np.zeros(n)
    place(gl, crush(stutter(rng, src, ns(0.2)), 4, 7), 0.0)
    pulse = sine(fcurve(n, [(0, 900), (0.3, 300)]) * (1 + 0.04 * np.sin(TAU * 13 * t)), n) * env(n, 0.003, 0.18)
    x = mix((0.5, rm), (0.4, gl * env(n, 0.001, 0.15)), (0.45, pulse), (0.3, whisper(rng, n, 6.0) * env(n, 0.01, 0.25)),
            (0.35, bell(n, 2093.0, 0.35, 0, GLASS)), (0.4, thump(n, 120, 60, 0.08)))
    return reverb(flanger(x, 5.0, 0.8, 3.0, 0.6), "large", 0.35)


def impact_summon(rng, v, n):
    metal = bell(n, (311.1, 349.2, 277.2)[v], 0.45, 0.0, METAL, beat=2.5, bright=1.3)
    ghost = track_bp(colored(rng, n, -3), [(0, 2500), (0.5, 600)], 0.7) * env(n, 0.005, 0.25)
    ch = choir(rng, n, [hz("D4"), hz("A4")], ("o",)) * env(n, 0.01, 0.3)
    x = mix((0.7, metal), (0.45, ghost), (0.35, ch), (0.6, thump(n, 120, 50, 0.12, drive=2.2)), (0.45, click(rng, n, 0, 0.002, 2000)))
    return reverb(x, "large", 0.35)


def impact_fusion(rng, v, n):
    b = bell(n, (hz("E5"), hz("A5"), hz("D5"))[v], 0.6) + 0.5 * bell(n, hz("B5"), 0.5, 0.02)
    gr = sfilt((additive(41.2, n, "saw", 70) + additive(43.7, n, "saw", 70)) * env(n, 0.005, 0.4), lambda t, f: lp(f, 250 + 1500 * np.exp(-t / 0.15), 2))
    x = mix((0.75, boom(rng, n, 0.0, 0.6)), (0.55, b), (0.45, gr),
            (0.4, crackles(rng, n, 100, (1500, 9000), density=lambda t: math.exp(-t / 0.3))),
            (0.3, sparkles(rng, n, 40, 4000, 11000, t_to=0.6)))
    return reverb(sat(norm(x), 1.6), "hall", 0.35)


# ---------------------------------------------------------------- сигналы каста и интерфейса

def ready(rng, v, n):
    x = mix((0.7, bell(n, hz("E6"), 0.32) + 0.6 * bell(n, hz("B6"), 0.26, t0=0.04) + 0.35 * bell(n, hz("E7"), 0.22, t0=0.08)),
            (0.35, sparkles(rng, n, 40, 5000, 12000, (0.01, 0.06), t_to=0.35)), (0.3, thump(n, 170, 85, 0.07, drive=1.5)))
    return reverb(x, "medium", 0.35)


def stage(rng, v, n):
    f = glide(n, 880, 1318.5, 0.05)
    x = (sine(f, n) + 0.3 * sine(2 * f, n) + 0.15 * sine(2.76 * f, n) * env(n, 0.001, 0.03)) * env(n, 0.002, 0.09)
    x = mix((0.8, x), (0.2, sweep_bp(white(rng, n), 2000, 6000, 0, 0.08, 0.5) * env(n, 0.003, 0.05)))
    return reverb(x, "small", 0.3)


def peak(rng, v, n):
    whoosh = sweep_bp(white(rng, n), 200, 4000, 0.0, 0.3, 0.7) * swell(n, 0.28, decay=0.18)
    sb = sat(sine(45, n) * swell(n, 0.3, power=1.5, decay=0.3), 2.0)
    stab = chord(n, [hz("D3"), hz("A3"), hz("D4"), hz("F#4")], "saw", 10, 3, 5.5, 0.003, 60, rng)
    stab = sfilt(stab * env(n, 0.006, 0.42, t0=0.28), lambda t, f: lp(f, 800 + 5200 * np.exp(-np.clip(t - 0.28, 0, None) / 0.18) * (t > 0.27), 2))
    x = mix((0.55, whoosh), (0.45, sb), (0.75, stab), (0.4, sparkles(rng, n, 50, 3000, 11000, (0.02, 0.15), t_from=0.28, t_to=0.8)),
            (0.35, bell(n, hz("D6"), 0.6, 0.28)))
    return sat(reverb(x, "large", 0.4), 1.4)


def fizzle(rng, v, n):
    hiss = sweep_bp(white(rng, n), 5000, 600, 0.0, 0.5, 0.6) * env(n, 0.005, 0.25)
    crk = crackles(rng, n, 80, (1500, 8000), density=lambda t: math.exp(-t / 0.22))
    tone = sine(glide(n, 900, 90, 0.55) * (1 + 0.04 * smooth_noise(rng, n, 30)), n) * env(n, 0.003, 0.25)
    src = mix((1, additive(600, ns(0.2), "square", 20)), (0.6, white(rng, ns(0.2))))
    gl = np.zeros(n)
    place(gl, crush(stutter(rng, src, ns(0.12)), 4, 10) * np.linspace(1, 0.2, ns(0.12)), 0.0)
    smoke = ffilt(colored(rng, n, -3), lambda f: lp(f, 1500)) * env(n, 0.05, 0.35)
    return reverb(mix((0.6, hiss), (0.45, crk), (0.4, tone), (0.3, gl), (0.25, smoke)), "medium", 0.3)


def learn(rng, v, n):
    x = np.zeros(n)
    for i, note in enumerate(["C5", "E5", "G5", "B5", "D6", "E6"]):
        x += bell(n, hz(note), 0.9, t0=0.07 * i) * (1 - 0.08 * i)
    ch = choir(rng, n, [hz("C4"), hz("E4"), hz("G4"), hz("B4"), hz("D5")], ("a", "o")) * env(n, 0.3, 0.7, hold=0.3, t0=0.1)
    rustle = np.zeros(n)
    for _ in range(9):
        m = ns(rng.uniform(0.012, 0.045))
        place(rustle, rng.standard_normal(m) * np.hanning(m) * rng.uniform(0.4, 1.0), rng.uniform(0.0, 0.45))
    rustle = ffilt(rustle, lambda f: bp(f, 4200, 1.0))
    shim = shepard(n, 800, 9000, 2.0, 1) * env(n, 0.4, 0.6, hold=0.4)
    x = mix((0.6, x), (0.45, ch), (0.3, rustle), (0.4, sparkles(rng, n, 40, 4000, 11000, (0.02, 0.12), t_from=0.1, t_to=1.3)),
            (0.15, shim), (0.3, thump(n, 110, 55, 0.2, drive=1.5)))
    return reverb(x, "large", 0.45)


def select(rng, v, n):
    tk = bell(n, 2637.0, 0.07, 0.0, GLASS, beat=5.0) + 0.5 * bell(n, 3951.1, 0.05, 0.012, GLASS, beat=5.0)
    sw = sweep_bp(white(rng, n), 2500, 6000, 0, 0.06, 0.5) * env(n, 0.003, 0.03)
    return reverb(mix((0.7, tk), (0.25, sw), (0.3, click(rng, n, 0, 0.0012, 2000))), "small", 0.3)


def multicast(rng, v, n):
    x = np.zeros(n)
    for i, f in enumerate((hz("A5"), hz("E6"), hz("A6"))):
        t0 = 0.075 * i
        up = sine(fcurve(n, [(t0, f * 0.5), (t0 + 0.05, f)]), n) * env(n, 0.003, 0.05, hold=0.03, t0=t0)
        x += (bell(n, f, 0.35, t0 + 0.04, GLASS, beat=3.0) + 0.5 * up) * (1 - 0.1 * i)
    echo = x.copy()
    for k, d in enumerate((0.13, 0.26, 0.39)):
        place(echo, x * 0.45 ** (k + 1), d)
    shim = flanger(sweep_bp(white(rng, n), 1500, 8000, 0, 0.3, 0.5) * env(n, 0.02, 0.2), 6.0, 0.5, 3.0, 0.9)
    return reverb(mix((0.7, echo), (0.3, shim), (0.3, thump(n, 150, 70, 0.08, drive=1.5))), "medium", 0.35)


def circle_draw(rng, v, n):
    f = hz(("E6", "G6", "A6", "B6", "D7", "E7")[v])
    t = tt(n)
    stroke = amp(n, (0, 0), (0.025, 1), (0.16 + 0.02 * v, 0.7), (0.3, 0))
    grain = 0.4 + 0.6 * (smooth_noise(rng, n, 260) > 0.1)
    scratch = sfilt(white(rng, n), lambda tc, ff: bp(ff, (4200 + 500 * v) * 2 ** (0.4 * np.clip(tc / 0.25, 0, 1) * (1 if v % 2 else -1)), 0.35))
    scratch *= stroke * grain
    chime = bell(n, f, 0.28, 0.05, GLASS, beat=2.0) + 0.4 * bell(n, f * 1.5, 0.2, 0.06, GLASS, beat=2.5)
    glow = (sine(f / 2, n) + 0.4 * sine(f * 0.75, n)) * env(n, 0.04, 0.12, hold=0.05) * (0.7 + 0.3 * np.sin(TAU * 18 * t))
    return reverb(mix((0.45, scratch), (0.6, chime), (0.15, glow)), "medium", 0.3)


def circle_complete(rng, v, n):
    th = 0.09
    pre = rswells(rng, n, [th], th, 1500, 10000, -1, 3)
    lock = click(rng, n, th, 0.002, 2500) + 0.6 * norm(bell(n, 1250.0, 0.06, th, METAL, beat=4.0))
    ch = sum(bell(n, hz(nt), 0.75, th + 0.012 * i, bright=0.7) * (1 - 0.1 * i) for i, nt in enumerate(("E5", "B5", "E6", "G#6")))
    pad = choir(rng, n, [hz("E4"), hz("B4"), hz("G#5")], ("a",)) * env(n, 0.02, 0.45, t0=th)
    x = mix((0.35, pre), (0.5, lock), (0.7, ch), (0.3, pad), (0.75, thump(n, 140, 48, 0.25, drive=2.2, t0=th)),
            (0.3, sparkles(rng, n, 45, 4000, 11000, (0.02, 0.1), t_from=th, t_to=0.9)))
    return reverb(x, "large", 0.38)


def circle_big(rng, v, n):
    th = 0.45
    sb = sat(sine(fcurve(n, [(0, 30), (th, 40), (2.5, 33)]), n) * swell(n, th, power=2.0, decay=1.0), 2.0)
    whoom = ffilt(additive(55, n, "saw", 40) + additive(82.6, n, "saw", 30) + additive(55.4, n, "saw", 40), lambda f: lp(f, 420, 2))
    whoom *= swell(n, th, power=2.0, decay=0.8)
    air = sweep_bp(white(rng, n), 250, 2500, 0, th, 0.8) * swell(n, th, power=3.0, decay=0.25)
    g = bell(n, 73.4, 2.0, th, GONG, beat=0.5)
    shim = gliss(rng, n, acc(th, th + 1.2, 1500, 4500, 0.7), (1.0, 1.26, 1.5), 2, "sine") * env(n, 0.05, 0.5, hold=0.3, t0=th)
    x = mix((0.35, sb), (0.4, whoom), (0.5, air), (0.6, g), (0.4, shim), (0.45, thump(n, 90, 28, 0.6, drive=2.5, t0=th)),
            (0.3, sparkles(rng, n, 30, 3000, 10000, (0.03, 0.2), t_from=th, t_to=2.2)))
    return sat(reverb(x, "huge", 0.5), 1.3)


def riser_short(rng, v, n):
    x = riser(rng, n, 0.0, 2.0, 160.0, 1100.0, (1.0, 1.5, 2.0), 6, 1.6, hz("E5"), (4.0, 20.0), 0.5, 0.5, build=1.3)
    return sat(norm(compress(reverb(x, "hall", 0.3), -14, 2, 0.01, 0.3)), 1.3)


def riser_long(rng, v, n):
    x = riser(rng, n, 0.0, 5.0, 110.0, 950.0, (1.0, 1.5, 2.0), 6, 1.8, hz("E5"), (2.5, 22.0), 0.55, 0.5, build=1.6)
    x += 0.4 * norm(riser(rng, n, 1.8, 5.0, 260.0, 2000.0, (1.0, 1.26), 5, 1.5, hz("B5"), (5.0, 24.0), 0.0, 0.0, 0.8, 0.0, 1.2))
    return sat(norm(compress(reverb(x, "hall", 0.35), -14, 2, 0.01, 0.35)), 1.4)


def release_heavy(rng, v, n):
    th = 0.16
    x = mix((0.6, shing(rng, n, 0.0, th, 650, 12000)), (1.0, boom(rng, n, th, 1.0)), (0.8, wall(rng, n, th, 0.95, 9000, 2500, 36, hits=1, fade=0.55)),
            (0.45, vwoom(rng, n, th + 0.12, 1.15, 950, 70)))
    return sat(reverb(norm(x), "hall", 0.35), 1.6)


def release_ultimate(rng, v, n):
    th = 0.32
    pre = rswells(rng, n, [th], th, 500, 12000, -1.5, 2.5)
    held = sine(1180 * (1 + 0.005 * np.sin(TAU * 6 * tt(n))), n) * env(n, 0.2, 0.45, hold=1.9, t0=th) * (0.7 + 0.3 * np.sin(TAU * 9 * tt(n)))
    tail_rum = brown(rng, n, 120) * ramp(n, th, th + 0.1) * (1 - ramp(n, 3.4, 5.9, 0.7))
    x = mix((0.4, pre), (0.65, shing(rng, n, 0.0, th, 500, 14000, 0.5)), (1.0, boom(rng, n, th, 1.6, 150, 24)),
            (1.0, wall(rng, n, th, 4.0, 9500, 1500, 33, hits=4, crackle=0.4, fade=1.5)), (0.12, held),
            (0.5, vwoom(rng, n, 2.8, 3.1, 1600, 75, (1.0, 0.75, 0.5), 8, 0.7, 1.0)), (0.45, tail_rum))
    return sat(reverb(norm(x), "huge", 0.45), 1.5)


def glitch(rng, v, n):
    m = ns(0.5)
    beeps = np.zeros(m)
    for _ in range(10):
        k = ns(rng.uniform(0.015, 0.04))
        place(beeps, additive(rng.uniform(400, 2600), k, "square", 12), rng.uniform(0, 0.45))
    src = mix((1.0, beeps), (0.7, band(white(rng, m), 500, 10000)), (0.6, additive(rng.uniform(80, 160), m, "saw", 60)))
    bits, hold = ((4, 6), (3, 10), (5, 4))[v]
    x = crush(stutter(rng, src, n, (0.008, 0.035), (2, 5), 0.3), bits, hold)
    x *= 0.3 + 0.7 * (smooth_noise(rng, n, 70) > -0.3)
    x = band(x, 120, 12000)
    return reverb(x, "small", 0.15)


# ---------------------------------------------------------------- большие эффекты

def explosion(rng, v, n):
    x = mix((1.0, boom(rng, n, 0.0, 1.1 + 0.15 * v, 120 + 10 * v, 26)),
            (0.6, roar(rng, n, 250, 1100) * env(n, 0.05, 0.6, t0=0.02)),
            (0.45, crackles(rng, n, 110, (800, 7000), (0.001, 0.01), density=lambda t: math.exp(-t / 0.6))),
            (0.3, crackles(rng, n, 30, (2000, 9000), (0.0005, 0.003), t_from=0.3, t_to=1.8)),
            (0.4, brown(rng, n, 120) * env(n, 0.05, 0.9)), (0.45, click(rng, n, 0, 0.004, 400)),
            (0.12, bell(n, hz("D5"), 1.0, 0.06, bright=0.6) + 0.7 * bell(n, hz("A5"), 0.9, 0.08, bright=0.6)))
    return sat(reverb(norm(x), "hall", 0.4), 2.2)


def explosion_huge(rng, v, n):
    th = 0.24
    pre = rswells(rng, n, [th], th, 300, 9000, -2, 2.5)
    b2 = boom(rng, n, th + 0.07, 1.4, 110, 30)
    debris = crackles(rng, n, 70, (900, 6000), (0.001, 0.008), t_from=th + 0.6, t_to=6.0, density=lambda t: math.exp(-(t - th) / 2.0))
    fire = crackles(rng, n, 50, (1500, 9000), (0.0005, 0.004), t_from=th + 0.3, t_to=6.5, density=lambda t: math.exp(-(t - th) / 2.5))
    tail = brown(rng, n, 140) * (0.75 + 0.25 * smooth_noise(rng, n, 1.5)) * ramp(n, th, th + 0.2) * (1 - ramp(n, 3.5, 7.9, 0.6))
    x = mix((0.4, pre), (0.5, shing(rng, n, 0.0, th - 0.01, 600, 13000, 0.3)), (1.0, boom(rng, n, th, 2.2, 140, 22)),
            (0.7, b2), (1.0, wall(rng, n, th, 4.8, 11000, 700, 30, (30.9, 46.2, 61.7), hits=5, crackle=0.5, fade=2.2, growl=0.4)),
            (0.35, debris), (0.25, fire), (0.75, tail), (0.25, vwoom(rng, n, 3.6, 3.8, 700, 45, (1.0, 0.5), 8, 0.8, 1.0)))
    return sat(reverb(norm(x), "huge", 0.5), 1.8)


def meteor_fall(rng, v, n):
    T = 2.5
    f = fcurve(n, acc(0, T, 3200, 300, 0.7))
    whistle = sfilt(white(rng, n), lambda tc, ff: bp(ff, np.interp(tc, tt(n), f), 0.14))
    tone = sine(f * (1 + 0.004 * np.sin(TAU * 7 * tt(n))), n)
    near = ramp(n, 0, T, 1.8) * 0.9 + 0.1
    rr = roar(rng, n, 200, 900, 6, 12) * ramp(n, 0, T, 1.5)
    x = mix((0.6, whistle * near), (0.2, tone * near), (0.7, rr), (0.5, brown(rng, n, 120) * ramp(n, 0.5, T, 2)),
            (0.3, crackles(rng, n, 60, (1200, 7000), density=lambda t: min(1.0, t / 2.0))))
    return sat(reverb(x, "hall", 0.3), 1.4)


def pillar(rng, v, n):
    th = 0.45
    rise = riser(rng, n, 0.0, th, 300, 1500, (1.0, 1.5), 5, 1.4, hz("A5"), (6, 16), 0.3, 0.4)
    ch = choir(rng, n, [hz("D3"), hz("A3"), hz("D4"), hz("F#4"), hz("A4")], ("a", "o")) * ramp(n, th - 0.1, th + 0.2) * np.exp(-np.clip(tt(n) - 2.0, 0, None) / 0.4)
    b = bell(n, hz("D5"), 1.4, th) + 0.6 * bell(n, hz("D4"), 1.6, th) + 0.4 * bell(n, hz("A5"), 1.0, th + 0.02)
    held = sine(1760 * (1 + 0.004 * np.sin(TAU * 5 * tt(n))), n) * env(n, 0.1, 0.8, hold=1.0, t0=th)
    x = mix((0.5, rise), (0.6, ch), (0.45, b), (0.12, held), (0.7, thump(n, 110, 36, 0.5, drive=2.0, t0=th)),
            (0.3, sparkles(rng, n, 50, 4000, 11000, (0.02, 0.12), t_from=th, t_to=2.4)),
            (0.35, sweep_bp(white(rng, n), 2000, 9000, th, 2.0, 0.6) * env(n, 0.05, 0.6, t0=th)))
    return reverb(x, "huge", 0.45)


def shockwave(rng, v, n):
    pas = track_bp(colored(rng, n, -2), [(0, 4500), (0.15, 2500), (0.9, 160)], 1.0) * env(n, 0.003, 0.45)
    whum = sat(sine(fcurve(n, [(0, 140), (0.6, 38)]), n) * env(n, 0.005, 0.5), 2.5)
    x = mix((0.75, boom(rng, n, 0.0, 0.9)), (0.9, pas), (0.45, whum),
            (0.3, crackles(rng, n, 80, (1000, 6000), density=lambda t: math.exp(-t / 0.4), t_from=0.05)))
    return sat(reverb(norm(x), "hall", 0.35), 1.8)


def thunder(rng, v, n):
    tear = crackles(rng, n, 2500, (600, 14000), (0.0002, 0.0025), t_to=0.12 + 0.04 * v, density=lambda t: math.exp(-t / 0.06))
    roll = np.zeros(n)
    t = tt(n)
    for i in range(4 + v):
        c = rng.uniform(0.25, 2.6)
        roll += np.exp(-0.5 * ((t - c) / rng.uniform(0.15, 0.45)) ** 2) * math.exp(-c / 1.6) * rng.uniform(0.6, 1.0)
    roll = (roll + 0.15 * np.exp(-t / 1.5)) * track_lp(colored(rng, n, -3.5), [(0, 2500), (1.0, 900 + 150 * v), (3.5, 400)])
    low = brown(rng, n, 110) * env(n, 0.05, 1.3, t0=0.02)
    x = mix((1.0, click(rng, n, 0, 0.0025, 400)), (0.9, tear), (0.6, thump(n, 100, 30, 0.6, drive=3.0, t0=0.01)), (0.9, roll), (0.4, low),
            (0.4, thump(n, 320, 120, 0.08, drop=0.01, drive=2.5, t0=0.005)))
    return sat(reverb(norm(x), "huge", 0.4), 1.7)


def rumble(rng, v, n):
    T = n / SR
    shape = ramp(n, 0, 0.5) * (1 - ramp(n, T - 1.1, T, 0.7))
    thuds = sum(thump(n, rng.uniform(55, 80), 28, 0.25, drive=2.5, t0=rng.uniform(0.1, T - 0.6)) for _ in range(8))
    grind = ffilt(crackles(rng, n, 60, (100, 800), (0.003, 0.02)), lambda f: lp(f, 900))
    rubble = band(colored(rng, n, -3), 150, 1200) * (0.4 + 0.6 * np.clip(smooth_noise(rng, n, 5), 0, 1))
    x = mix((0.6, brown(rng, n, 110) * (0.7 + 0.3 * smooth_noise(rng, n, 3))), (0.5, thuds), (0.7, grind), (0.75, rubble),
            (0.2, crackles(rng, n, 15, (1000, 3500))), (0.4, sat(sine(31 * (1 + 0.05 * smooth_noise(rng, n, 2)), n), 2.0)))
    return sat(norm(x * shape), 1.6)


def freeze(rng, v, n):
    creak = sfilt(additive(fcurve(n, [(0, 85), (1.0, 150)]) * (1 + 0.03 * smooth_noise(rng, n, 40)), n, "saw", 80),
                  lambda t, f: bp(f, 700, 0.9)) * env(n, 0.15, 0.4, hold=0.4)
    crunch = crackles(rng, n, 260, (2500, 9500), (0.0004, 0.003), density=lambda t: min(1.0, t / 0.7) * (t < 1.0))
    ping = pings(rng, n, 20, 2500, 9000, 0.05, 1.1, (0.04, 0.25))
    hiss = band(white(rng, n), 5000, 14000) * ramp(n, 0, 0.9, 1.5) * (1 - ramp(n, 0.95, 1.05))
    x = mix((0.35, creak), (0.55, crunch), (0.5, ping), (0.25, hiss), (0.4, sweep_bp(white(rng, n), 7000, 2500, 0, 0.3, 0.5) * env(n, 0.003, 0.2)),
            (0.45, bell(n, 3135.9, 0.6, 1.0, GLASS, beat=2.0)), (0.35, thump(n, 160, 80, 0.08, t0=1.0)))
    return reverb(x, "medium", 0.35)


def shatter(rng, v, n):
    x = mix((0.8, band(white(rng, n), 1500, 14000) * env(n, 0.001, 0.07)), (0.6, click(rng, n, 0, 0.002, 800)),
            (0.7, pings(rng, n, 45 + 12 * v, 1800, 9500, 0, 0.5, (0.03, 0.3), density=lambda t: math.exp(-t / 0.18))),
            (0.45, crackles(rng, n, 400, (2000, 11000), (0.0003, 0.002), density=lambda t: math.exp(-t / 0.25))),
            (0.45, thump(n, 170, 70, 0.08, drive=2.0)), (0.3, bell(n, 2793.0 - 300 * v, 0.5, 0.0, GLASS, beat=3.0)))
    return reverb(x, "medium", 0.32)


def gust(rng, v, n):
    T = n / SR
    pk = 0.3 + 0.08 * v
    body = track_bp(colored(rng, n, -2.5), [(0, 350), (pk, 1600 + 300 * v), (T, 500)], 1.0) * swell(n, pk, power=2.0, decay=0.45)
    c1 = smooth_noise(rng, n, 2.0, math.log(700), math.log(2600))
    whistle = sfilt(white(rng, n), lambda tc, f: bp(f, np.exp(np.interp(tc, tt(n), c1)), 0.1)) * swell(n, pk, power=1.5, decay=0.5)
    x = mix((0.9, body), (0.3, whistle), (0.4, brown(rng, n, 200) * swell(n, pk, power=1.5, decay=0.5)))
    return reverb(x, "large", 0.3)


def beam_blast(rng, v, n):
    th = 0.55
    T = n / SR
    ch = riser(rng, n, 0.0, th, 220, 1300, (1.0, 1.5, 2.0), 7, 1.4, 880.0, (8, 24), 0.5, 0.45)
    buzz = sum(additive(f, n, "saw", 60, ph0=rng.uniform(0, TAU)) for f in (55.0, 55.4, 82.6, 110.3))
    buzz = sat(norm(band(buzz, 120, 3200)), 3.0) * (0.75 + 0.25 * np.sin(TAU * 28 * tt(n)))
    nz = band(colored(rng, n, -2), 400, 7000) * (0.8 + 0.2 * smooth_noise(rng, n, 20))
    whine = sine(fcurve(n, [(th, 1760), (2.1, 1760), (T, 400)]) * (1 + 0.006 * np.sin(TAU * 7 * tt(n))), n)
    whine += 0.6 * sine(fcurve(n, [(th, 2637), (2.1, 2637), (T, 600)]), n)
    beam = mix((0.8, buzz), (0.7, nz), (0.25, whine), (0.35, crackles(rng, n, 120, (2000, 10000), t_from=th, t_to=2.4)),
               (0.6, sat(sine(36.7, n), 2.0)))
    beam = compress(beam, -22, 6, 0.004, 0.2) * ramp(n, th, th + 0.02) * (1 - ramp(n, 1.9, T - 0.05, 0.8))
    x = mix((0.6, ch * (tt(n) < th + 0.01)), (0.5, shing(rng, n, th - 0.1, 0.1, 1200, 12000, 0.3)), (0.85, boom(rng, n, th, 0.8)),
            (1.0, beam), (0.45, vwoom(rng, n, 1.9, 1.25, 1400, 110, (1.0, 0.5), 7, 0.8, 1.2)))
    return sat(reverb(norm(x), "hall", 0.35), 1.6)


def timestop(rng, v, n):
    th = 0.55
    T = n / SR
    rev = rswells(rng, n, [th], th, 400, 9000, -2, 3)
    dunn = bell(n, 55.0, 2.5, th, GONG, beat=0.3) + 0.6 * bell(n, hz("E6"), 1.5, th, beat=0.4)
    frozen = (sine(hz("A6"), n) + 0.7 * sine(hz("A6") + 0.7, n) + 0.5 * sine(hz("E7"), n)) * env(n, 0.4, 1.2, hold=0.8, t0=th)
    frozen = flanger(frozen, 0.3, 0.6, 3.0, 0.8)
    ticks = np.zeros(n)
    tq = th + 0.08
    gap = 0.17
    for i in range(7):
        if tq > T - 0.3:
            break
        ticks += tick(rng, n, tq, (2400.0 if i % 2 == 0 else 1800.0) * (1 - 0.03 * i)) * (1 - 0.11 * i)
        tq += gap
        gap *= 1.33
    x = mix((0.6, rev), (0.5, thump(n, 80, 30, 0.6, drive=2.5, t0=th)), (0.65, dunn), (0.22, frozen), (0.5, ticks))
    return reverb(x, "hall", 0.45)


def domain(rng, v, n):
    th = 0.3
    T = n / SR
    pre = rswells(rng, n, [th], th, 300, 8000, -2, 3)
    g = bell(n, 49.0, 3.0, th, GONG, beat=0.4, bright=1.2)
    pad = choir(rng, n, [hz("C3"), hz("G3"), hz("C#4"), hz("G#4")], ("o", "u"), detune=14, vib=4.0)
    pad = flanger(pad, 0.4, 0.8, 4.0, 0.8) * ramp(n, th, th + 0.9) * (1 - ramp(n, T - 1.0, T))
    glass = sum(sine(f, n) * (0.6 + 0.4 * np.sin(TAU * r * tt(n) + i)) for i, (f, r) in enumerate(((1567.0, 0.7), (2217.0, 1.1), (2960.0, 0.5))))
    glass *= ramp(n, th + 0.3, th + 1.5) * (1 - ramp(n, T - 1.0, T))
    drone = sat(sine(32.7, n) + 0.4 * sine(65.5, n), 2.0) * ramp(n, th, th + 0.2) * (1 - ramp(n, T - 1.2, T))
    x = mix((0.45, pre), (1.0, boom(rng, n, th, 1.4, 120, 26)), (0.6, g), (0.45, pad), (0.12, glass), (0.55, drone),
            (0.2, sparkles(rng, n, 10, 3000, 9000, (0.05, 0.2), t_from=th, t_to=T - 0.5)))
    return sat(reverb(norm(x), "huge", 0.5), 1.4)


def teleport(rng, v, n):
    up, down = 0.18, 0.25
    f = np.where(tt(n) < up, glide(n, 300, 2400, up), glide(n, 2400, 240, down, t0=up))
    tone = np.sin(phase(f, n) + 1.2 * np.sin(phase(1.5 * f, n))) * env(n, 0.003, 0.22, hold=up)
    swoosh = sfilt(white(rng, n), lambda tc, ff: bp(ff, np.interp(tc, tt(n), f) * 1.4, 0.5)) * env(n, 0.003, 0.22, hold=up)
    x = mix((0.6, tone), (0.6, swoosh), (0.3, click(rng, n, up, 0.002, 2000)), (0.3, thump(n, 120, 40, 0.15, t0=up)),
            (0.3, sparkles(rng, n, 40, 3000, 10000, (0.02, 0.08), t_from=up, t_to=0.6)))
    return reverb(flanger(x, 4.0, 0.5, 3.0, 0.5), "medium", 0.3)


def summon(rng, v, n):
    th = 0.6
    swirl = sfilt(white(rng, n), lambda tc, f: bp(f, 250 * 14 ** np.clip(tc / th, 0, 1) * 2 ** (0.35 * np.sin(TAU * 7 * tc)), 0.4))
    swirl *= swell(n, th, decay=0.5)
    ch = choir(rng, n, [hz("D3"), hz("A3"), hz("D4"), hz("F4"), hz("A4")], ("o", "a")) * env(n, 0.15, 0.8, hold=0.4, t0=th - 0.1)
    metal = bell(n, 415.3, 1.2, th, METAL, beat=1.2) + 0.5 * bell(n, 830.6, 0.9, th + 0.02, METAL, beat=2.0)
    x = mix((0.55, swirl), (0.95, boom(rng, n, th, 0.8, 110, 30)), (0.45, ch), (0.4, metal),
            (0.3, sparkles(rng, n, 30, 3000, 10000, (0.03, 0.15), t_from=th, t_to=2.0)),
            (0.3, ffilt(additive(82.4, n, "saw", 40), lambda f: lp(f, 500)) * env(n, 0.3, 0.6, hold=0.4)))
    return sat(reverb(norm(x), "huge", 0.4), 1.3)


def vanish(rng, v, n):
    f = glide(n, 2000, 550, 0.6)
    tones = (sine(f, n) + 0.6 * sine(f * 1.5, n) + 0.4 * sine(f * 2.01, n)) * env(n, 0.03, 0.28, hold=0.05)
    tones = flanger(tones, 4.0, 1.0, 4.0, 0.8)
    air = sweep_bp(white(rng, n), 4000, 700, 0.0, 0.6, 0.7) * env(n, 0.02, 0.28)
    x = mix((0.5, tones), (0.5, air), (0.35, sparkles(rng, n, 30, 3000, 9000, (0.02, 0.1), density=lambda t: math.exp(-t / 0.2))))
    return reverb(x, "large", 0.45)


def ward_hit(rng, v, n):
    ring = bell(n, 420 + 30 * v, 0.28, partials=[(1.0, 1.0, 1.0), (1.64, 0.7, 0.8), (2.69, 0.5, 0.6), (4.38, 0.3, 0.4),
                                                  (7.9, 0.22, 0.18), (11.3, 0.12, 0.12)])
    hum = ffilt(additive(98, n, "saw", 40), lambda f: lp(f, 1000)) * env(n, 0.002, 0.1)
    x = mix((0.6, thump(n, 160 + 20 * v, 90, 0.05, drive=1.5)), (0.4, click(rng, n, 0, 0.0015, 1200)), (0.55, ring), (0.35, hum),
            (0.25, bell(n, 2349.0, 0.25, 0.005, GLASS, beat=3.0)))
    return reverb(x, "small", 0.25)


# ---------------------------------------------------------------- петли потоков и долгих эффектов

def loop_flames(rng, L, N, q):
    swirl = sfilt(colored(rng, N, -3), lambda tc, f: bp(f, 900 * 2 ** (0.9 * np.sin(TAU * tc * 2 / 3)), 0.6))
    noisy = mix((0.75, roar(rng, N, 300, 1200)), (0.5, brown(rng, N, 220)), (0.45, crackles(rng, N, 45, (1500, 8000), (0.0008, 0.007))),
                (0.25, swirl))
    return 0.3 * csub(L, q, 41.33, 2 / 3, 0.3), noisy


def loop_beam(rng, L, N, q):
    t = tt(L)
    buzz = additive(q(110), L, "saw", 60) + additive(q(110.33), L, "saw", 60) + 0.6 * additive(q(220), L, "square", 40) + 0.5 * additive(q(165), L, "saw", 40)
    buzz = ffilt(buzz, lambda f: lp(f, 2800, 2) * (0.3 + bp(f, 880, 0.8)), circular=True)
    whine = (sine(q(1760), L) + 0.5 * sine(q(2640), L)) * (0.6 + 0.4 * np.sin(TAU * q(12) * t))
    tonal = mix((0.6, sat(norm(buzz), 2.0) * (0.85 + 0.15 * np.sin(TAU * q(1 / 3) * t))), (0.18, whine), (0.3, csub(L, q, 55, 2, 0.2)),
                (0.15, shepard(L, 800, 8000, 2.0, 1, 2)))
    noisy = mix((0.12, band(white(rng, N), 5000, 13000)), (0.25, crackles(rng, N, 25, (3000, 9000))),
                (0.3, band(colored(rng, N, -2), 400, 5000) * (0.7 + 0.3 * smooth_noise(rng, N, 25))))
    return tonal, noisy


def loop_drain(rng, L, N, q):
    drone = ffilt(additive(q(49.0), L, "saw", 50) + 0.7 * additive(q(73.33), L, "saw", 40), lambda f: lp(f, 320, 3), circular=True)
    wob = smooth_noise(rng, N, 2.5, math.log(140), math.log(520))
    goo = sfilt(colored(rng, N, -3), lambda t, f: bp(f, np.exp(np.interp(t, tt(N), wob)), 0.35))
    tonal = mix((0.5, drone), (0.26, shepard(L, 100, 2000, 2.0, -1)), (0.3, csub(L, q, 36.67, 2 / 3, 0.35)))
    noisy = mix((0.5, goo), (0.3, bubbles(rng, N, 6, 120, 320, (0.03, 0.05))), (0.35, whisper(rng, N, 2.0)),
                (0.35, rswells(rng, N, (1.0, 2.5), 0.7, 300, 3000)))
    return tonal, noisy


def loop_heal(rng, L, N, q):
    t = tt(L)
    high = (sine(q(hz("A5")), L) + 0.8 * sine(q(hz("C#6")), L) + 0.7 * sine(q(hz("E6")), L)) * (0.7 + 0.3 * np.sin(TAU * q(1) * t))
    mid = ffilt(chord(L, [hz("A3"), hz("E4"), hz("A4"), hz("C#5")], "tri", 6, 2, 4.0, 0.004, 15, rng, q), lambda f: lp(f, 1800), circular=True)
    mid *= 0.8 + 0.2 * np.sin(TAU * q(2 / 3) * t)
    tonal = mix((0.3, high), (0.45, mid), (0.15, shepard(L, 500, 7000, 2.0, 1)), (0.25, csub(L, q, 55, 1 / 3, 0.2, 1.3)))
    chimes = np.zeros(N)
    for tq, nt in zip(rng.uniform(0, N / SR, 4), rng.choice(["A6", "B6", "C#7", "E7"], 4)):
        chimes += bell(N, hz(nt), 0.5, t0=tq, bright=0.5)
    noisy = mix((0.3, sparkles(rng, N, 10, 4000, 9000, (0.03, 0.14))), (0.03, band(white(rng, N), 6000, 14000)), (0.2, chimes))
    return tonal, noisy


def loop_ward(rng, L, N, q):
    t = tt(L)
    hum = additive(q(98.0), L, "saw", 60) + additive(q(98.33), L, "saw", 60)
    hum = ffilt(hum, lambda f: lp(f, 1200, 2) * (0.4 + bp(f, 294, 0.3) + 0.6 * bp(f, 588, 0.25)), circular=True)
    glassy = (sine(q(784), L) + 0.7 * sine(q(1176), L) + 0.4 * sine(q(1568.33), L)) * (0.6 + 0.4 * np.sin(TAU * q(2) * t))
    tonal = mix((0.6, hum), (0.28, glassy), (0.2, csub(L, q, 49, 1, 0.3, 1.5)))
    noisy = ffilt(white(rng, N), lambda f: bp(f, 6000, 0.8)) * 0.05 * (0.6 + 0.4 * smooth_noise(rng, N, 20))
    return tonal, noisy


def loop_wind(rng, L, N, q):
    x = white(rng, N)
    c1 = smooth_noise(rng, N, 1.5, math.log(350), math.log(2400))
    c2 = smooth_noise(rng, N, 1.2, math.log(500), math.log(3200))
    whistle = sfilt(x, lambda t, f: bp(f, np.exp(np.interp(t, tt(N), c1)), 0.18) + 0.7 * bp(f, np.exp(np.interp(t, tt(N), c2)), 0.22))
    body = ffilt(colored(rng, N, -3), lambda f: lp(f, 900))
    gust_ = 0.6 + 0.4 * smooth_noise(rng, N, 1.5)
    return np.zeros(L), mix((0.6, whistle), (0.55, body), (0.35, brown(rng, N, 150))) * gust_


def loop_lightning(rng, L, N, q):
    t = tt(L)
    hum = ffilt(additive(q(50), L, "saw", 60) + 0.6 * additive(q(100.33), L, "square", 40), lambda f: lp(f, 3000) * hp(f, 80), circular=True)
    tonal = mix((0.45, hum * (0.8 + 0.2 * np.sin(TAU * q(6) * t))), (0.2, csub(L, q, 50, 2, 0.2)))
    flutter = 0.3 + 0.7 * (smooth_noise(rng, N, 45) > -0.2)
    buzz = sfilt(additive(115 * (1 + 0.06 * smooth_noise(rng, N, 120)), N, "saw", 80), lambda tc, f: bp(f, 1100, 1.0)) * flutter
    zaps = np.zeros(N)
    for _ in range(6):
        m = ns(0.12)
        place(zaps, additive(glide(m, rng.uniform(2000, 3500), 160, 0.1), m, "saw", 25) * env(m, 0.001, 0.05), rng.uniform(0, N / SR))
    snaps = np.zeros(N)
    for _ in range(5):
        place(snaps, click(rng, ns(0.05), 0.0, 0.002, 600), rng.uniform(0, N / SR))
    noisy = mix((0.5, buzz), (0.6, crackles(rng, N, 140, (2000, 11000), (0.0004, 0.003))), (0.35, zaps), (0.35, snaps),
                (0.15, brown(rng, N, 200) * (0.5 + 0.5 * smooth_noise(rng, N, 4))))
    return tonal, noisy


def loop_water(rng, L, N, q):
    wob = smooth_noise(rng, N, 3.0, math.log(400), math.log(1600))
    flow = sfilt(colored(rng, N, -3), lambda t, f: bp(f, np.exp(np.interp(t, tt(N), wob)), 0.8))
    waves = band(colored(rng, N, -4), 120, 600) * (0.4 + 0.6 * np.sin(np.pi * tt(N) / 1.5) ** 2)
    noisy = mix((0.5, flow), (0.55, bubbles(rng, N, 70, 250, 2500, (0.01, 0.04))), (0.45, waves),
                (0.15, band(white(rng, N), 3000, 10000) * (0.6 + 0.4 * smooth_noise(rng, N, 8))))
    return 0.25 * csub(L, q, 36.67, 2 / 3, 0.3), noisy


def loop_void(rng, L, N, q):
    t = tt(L)
    glass = (sine(q(1567.67), L) + 0.7 * sine(q(1661.33), L)) * (0.5 + 0.5 * np.sin(TAU * q(1 / 3) * t))
    tonal = mix((0.6, csub(L, q, 29.33, 2, 0.55, 2.4)), (0.32, shepard(L, 60, 1500, 2.0, -1)), (0.17, shepard(L, 300, 3000, 1.5, -1, 2)),
                (0.08, glass))
    fl = flanger(band(colored(rng, N, -3), 200, 5000), 0.7, 0.5, 6.0, 0.9)
    noisy = mix((0.35, fl), (0.45, rswells(rng, N, (0.6, 1.35, 2.1, 2.85), 0.6, 300, 6000, -2, 3)), (0.35, brown(rng, N, 90)))
    return tonal, noisy


def loop_storm(rng, L, N, q):
    rain = mix((0.5, crackles(rng, N, 900, (2500, 12000), (0.0002, 0.0012))), (0.5, band(white(rng, N), 3000, 11000)))
    rain *= 0.8 + 0.2 * smooth_noise(rng, N, 1.0)
    c1 = smooth_noise(rng, N, 0.8, math.log(300), math.log(1100))
    howl = sfilt(white(rng, N), lambda tc, f: bp(f, np.exp(np.interp(tc, tt(N), c1)), 0.35)) * (0.4 + 0.6 * smooth_noise(rng, N, 0.7, 0, 1))
    t = tt(N)
    rolls = np.zeros(N)
    for c in (1.2, 4.3):
        rolls += np.exp(-0.5 * ((t - c) / 0.45) ** 2) + 0.6 * np.exp(-0.5 * ((t - c - 0.8) / 0.4) ** 2)
    rolls *= ffilt(colored(rng, N, -3.5), lambda f: lp(f, 900) * hp(f, 40))
    crack = crackles(rng, N, 1500, (500, 4000), (0.0003, 0.002), t_from=4.2, t_to=4.32)
    noisy = mix((0.5, rain), (0.22, howl), (0.8, rolls), (0.35, brown(rng, N, 100) * (0.7 + 0.3 * smooth_noise(rng, N, 0.8))),
                (0.35, crack))
    return 0.25 * csub(L, q, 33.33, 1 / 6, 0.3), noisy


def loop_chant(rng, L, N, q):
    t = tt(L)
    P = L / SR
    wa = 0.5 + 0.5 * np.cos(TAU * t / P)
    wo = 0.5 + 0.5 * np.cos(TAU * (t / P - 1 / 3))
    wu = 0.5 + 0.5 * np.cos(TAU * (t / P - 2 / 3))
    ch = choir(rng, L, [hz("D3"), hz("A3"), hz("D4"), hz("F4")], ("a", "o", "u"), (wa, wo, wu), 10, 3, q(4.5), 0.006, q, True)
    om = formant(ffilt(chord(L, [hz("D2"), hz("A2")], "saw", 6, 2, q(3), 0.003, 60, rng, q), lambda f: lp(f, 1500), circular=True), "u", circular=True)
    hi = choir(rng, L, [hz("A4"), hz("D5")], ("i", "e"), (wa, 1 - wa), 8, 2, q(5.5), 0.006, q, True)
    tonal = mix((0.55, ch * (0.8 + 0.2 * np.sin(TAU * q(1 / 3) * t))), (0.35, om), (0.15, hi * (0.5 + 0.5 * np.sin(TAU * q(1 / 6) * t))))
    breath = formant(white(rng, N), "a") * (0.3 + 0.7 * np.clip(smooth_noise(rng, N, 4, -0.5, 1.0), 0, 1))
    noisy = mix((0.4, whisper(rng, N, 3.5)), (0.3, whisper(rng, N, 2.5)), (0.12, breath))
    return tonal, noisy


# ---------------------------------------------------------------- список файлов

ELEMENTS = ("fire", "water", "wind", "earth", "lightning", "light", "dark", "space", "life", "mind", "summon", "fusion")

# имя петли: (функция, длина, комната, доля реверберации)
LOOPS = {f"charge_{e}": (globals()[f"charge_{e}"], LOOP_LEN, "medium", 0.25) for e in ELEMENTS}
LOOPS.update({"charge_light": (charge_light, LOOP_LEN, "large", 0.3), "charge_dark": (charge_dark, LOOP_LEN, "dark", 0.35),
              "charge_space": (charge_space, LOOP_LEN, "large", 0.3), "charge_life": (charge_life, LOOP_LEN, "large", 0.3),
              "charge_mind": (charge_mind, LOOP_LEN, "large", 0.3), "charge_summon": (charge_summon, LOOP_LEN, "large", 0.3)})
LOOPS.update({
    "loop_flames": (loop_flames, LOOP_LEN, "small", 0.15), "loop_beam": (loop_beam, LOOP_LEN, "medium", 0.2),
    "loop_drain": (loop_drain, LOOP_LEN, "dark", 0.25), "loop_heal": (loop_heal, LOOP_LEN, "large", 0.35),
    "loop_ward": (loop_ward, LOOP_LEN, "small", 0.15), "loop_wind": (loop_wind, 4.0, "small", 0.15),
    "loop_lightning": (loop_lightning, LOOP_LEN, "medium", 0.2), "loop_water": (loop_water, 4.0, "small", 0.2),
    "loop_void": (loop_void, LOOP_LEN, "dark", 0.35), "loop_storm": (loop_storm, 6.0, "large", 0.25),
    "loop_chant": (loop_chant, 6.0, "hall", 0.35),
})

# имя: (функция, вариантов, длительность)
ONESHOTS = {}
for _e in ELEMENTS:
    ONESHOTS[f"cast_{_e}"] = (globals()[f"cast_{_e}"], 3, 1.6 if _e in ("summon", "fusion", "light", "dark") else 1.4)
    ONESHOTS[f"impact_{_e}"] = (globals()[f"impact_{_e}"], 3, 1.4)
ONESHOTS.update({
    "ready": (ready, 1, 0.8), "stage": (stage, 1, 0.35), "peak": (peak, 1, 1.3), "fizzle": (fizzle, 1, 0.9),
    "learn": (learn, 1, 2.4), "select": (select, 1, 0.3), "multicast": (multicast, 1, 1.0),
    "circle_draw": (circle_draw, 6, 0.6), "circle_complete": (circle_complete, 1, 1.6), "circle_big": (circle_big, 1, 3.2),
    "riser_short": (riser_short, 1, 2.0), "riser_long": (riser_long, 1, 5.0), "release_heavy": (release_heavy, 1, 1.6),
    "release_ultimate": (release_ultimate, 1, 6.0), "glitch": (glitch, 3, 0.35),
    "explosion": (explosion, 3, 2.6), "explosion_huge": (explosion_huge, 1, 8.0), "meteor_fall": (meteor_fall, 1, 2.5),
    "pillar": (pillar, 1, 3.2), "shockwave": (shockwave, 1, 2.2), "thunder": (thunder, 3, 3.6), "rumble": (rumble, 1, 3.0),
    "freeze": (freeze, 1, 1.7), "shatter": (shatter, 2, 1.4), "gust": (gust, 3, 1.6), "beam_blast": (beam_blast, 1, 3.4),
    "timestop": (timestop, 1, 3.2), "domain": (domain, 1, 4.2), "teleport": (teleport, 1, 0.9), "summon": (summon, 1, 2.8),
    "vanish": (vanish, 1, 1.0), "ward_hit": (ward_hit, 2, 0.5),
})
# у нарастаний конец — на максимуме (дальше сразу выпуск), поэтому короткий фейд
SHORT_FADE = {"riser_short", "riser_long", "meteor_fall"}
# порог лимитера (дБ от пика) — насколько плотнее делается тело звука; сигналы интерфейса почти не трогаются
LIMIT = {"select": -1.5, "stage": -1.5, "ready": -3.0, "circle_draw": -3.0, "glitch": -3.0, "multicast": -3.0, "learn": -3.0,
         "riser_short": -2.0, "riser_long": -2.0}
LIMIT_DEFAULT = -5.0


def make_loop(fn, rng, length):
    """fn(rng, L, N, q) → (тональное длины L, шумовое длины N = L + X). Тональное строго периодично: частоты
    квантуются q к кратным 1/длины, фильтры — кольцевые на длине L. Шумовое сводится кроссфейдом хвоста в начало
    (равная мощность): на стыке звучит естественное продолжение последнего сэмпла."""
    L, X = ns(length), ns(LOOP_XFADE)
    q = lambda f: np.round(np.asarray(f) * length) / length
    tonal, noisy = fn(rng, L, L + X, q)
    w = np.arange(X) / X
    nz = noisy[:L].copy()
    nz[:X] = noisy[:X] * np.sin(0.5 * np.pi * w) + noisy[L:L + X] * np.cos(0.5 * np.pi * w)
    return tonal + nz


def finish(x, loop=False, fade_in=0.004, fade_out=0.04):
    """Убрать постоянную составляющую и инфранизы, фейды (у разовых), пик −1 dBFS. Лимитер и сатурация поднимают
    хвосты, поэтому у разовых длинный плавный фейд (последние 35 %): звук гаснет сам, а не обрывается."""
    x = ffilt(x - np.mean(x), lambda f: hp(f, 28, 2), circular=loop)
    if not loop:
        n = len(x)
        a, b = ns(fade_in), ns(fade_out)
        x[:a] *= np.sin(0.5 * np.pi * np.arange(a) / a) ** 2
        x[n - b:] *= np.cos(0.5 * np.pi * np.arange(b) / b) ** 2
    return x * PEAK / np.max(np.abs(x))


def jobs():
    """Все файлы: (имя файла, ключ рецепта, вариант)."""
    out = [(name, name, 0) for name in LOOPS]
    for name, (_, variants, _) in ONESHOTS.items():
        out += [(name if variants == 1 else f"{name}_{v + 1}", name, v) for v in range(variants)]
    return out


def render_one(job):
    fname, key, v = job
    rng = np.random.default_rng(zlib.crc32(fname.encode()))
    if key in LOOPS:
        fn, length, room, wet = LOOPS[key]
        x = reverb(make_loop(fn, rng, length), room, wet, circular=True)
        return fname, finish(sat(norm(x), 1.2), loop=True), True
    fn, _, dur = ONESHOTS[key]
    x = limit(fn(rng, v, ns(dur)), LIMIT.get(key, LIMIT_DEFAULT))
    fo = 0.03 if key in SHORT_FADE else dur * 0.35
    return fname, finish(x, fade_out=fo), False


def render_all(only=None, workers=None):
    todo = [j for j in jobs() if only is None or re.search(only, j[0])]
    workers = workers or min(8, os.cpu_count() or 1)
    if workers > 1 and len(todo) > 1:
        with ProcessPoolExecutor(workers) as ex:
            res = list(ex.map(render_one, todo))
    else:
        res = [render_one(j) for j in todo]
    return {name: (x, loop) for name, x, loop in res}


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


def _write_job(item):
    name, x, loop = item
    write_ogg(OUT / f"{name}.ogg", x, name, loop)
    return name


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
    size = 0
    for p in paths:
        x, sr = sf.read(p, dtype="float64", always_2d=True)
        size += p.stat().st_size
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
    print(f"\nфайлов: {len(rows)}, всего {size / 1e6:.1f} МБ, всё в порядке: {'да' if ok else 'НЕТ'}")
    return ok


# ---------------------------------------------------------------- превью (спектрограммы)

def _spec(x, N=2048, hop=256, bins=320):
    """Спектрограмма на логарифмической оси частот 30 Гц…16 кГц (дБ)."""
    win = np.hanning(N)
    xp = np.concatenate([np.zeros(N // 2), x, np.zeros(N)])
    frames = max(1, 1 + (len(xp) - N) // hop)
    idx = np.arange(N)[None, :] + hop * np.arange(frames)[:, None]
    S = 20 * np.log10(np.abs(np.fft.rfft(xp[idx] * win, axis=1)) / (N / 4) + 1e-7)
    pos = np.geomspace(30, 16000, bins) / (SR / N)
    i0 = np.floor(pos).astype(int)
    fr = pos - i0
    return (S[:, i0] * (1 - fr) + S[:, i0 + 1] * fr).T


def _rms_db(x, win=0.05):
    w = ns(win)
    return 20 * np.log10(np.sqrt(np.convolve(x ** 2, np.ones(w) / w, mode="same") + 1e-12))


def _draw(ax, x, label=None, fs=8, rms=True):
    """Спектрограмма; поверх — голубая кривая RMS (шкала −60…0 дБ на всю высоту)."""
    S = _spec(x)
    T = len(x) / SR
    lo, hi = math.log10(30), math.log10(16000)
    ax.imshow(S, origin="lower", aspect="auto", cmap="magma", vmin=-95, vmax=-15, extent=(0, T, lo, hi), interpolation="nearest")
    if rms:
        r = np.clip(_rms_db(x), -60, 0)
        k = max(1, len(x) // 2000)
        ax.plot(tt(len(x))[::k], lo + (hi - lo) * (r[::k] + 60) / 60, color="#5fe3ff", lw=0.7, alpha=0.85)
    ax.set_ylim(lo, hi)
    ticks = [50, 100, 200, 500, 1000, 2000, 5000, 10000]
    ax.set_yticks([math.log10(v) for v in ticks])
    ax.set_yticklabels(["50", "100", "200", "500", "1k", "2k", "5k", "10k"], fontsize=fs - 2)
    ax.tick_params(axis="x", labelsize=fs - 1)
    if label:
        ax.set_ylabel(label, rotation=0, ha="right", va="center", fontsize=fs)


def preview(sounds, folder):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    folder.mkdir(parents=True, exist_ok=True)
    names = list(sounds)
    groups = {}
    for e in ELEMENTS:
        groups[f"el_{e}"] = [n for n in names if n == f"charge_{e}" or re.fullmatch(f"(cast|impact)_{e}(_\\d)?", n)]
    used = {n for g in groups.values() for n in g}
    cues = [n for n in names if n not in used and re.match(r"(ready|stage|peak|fizzle|learn|select|multicast|circle_|glitch)", n)]
    big = [n for n in names if n not in used and n not in cues and not n.startswith("loop_")]
    loops = [n for n in names if n.startswith("loop_")]
    for i in range(0, len(cues), 9):
        groups[f"cues_{i // 9 + 1}"] = cues[i:i + 9]
    for i in range(0, len(big), 9):
        groups[f"fx_{i // 9 + 1}"] = big[i:i + 9]
    for i in range(0, len(loops), 6):
        groups[f"loops_{i // 6 + 1}"] = loops[i:i + 6]
    for gname, chunk in groups.items():
        if not chunk:
            continue
        # петли — двумя периодами подряд: щелчок на стыке был бы виден посередине
        sig = {n: np.tile(sounds[n][0], 2) if sounds[n][1] else sounds[n][0] for n in chunk}
        T = max(len(x) for x in sig.values()) / SR
        fig, axes = plt.subplots(len(chunk), 1, figsize=(max(7.0, min(16.0, 2.2 * T + 3)), 1.35 * len(chunk) + 0.4), squeeze=False)
        for ax, name in zip(axes[:, 0], chunk):
            _draw(ax, sig[name], name)
            ax.set_xlim(0, T)
        fig.tight_layout()
        fig.savefig(folder / f"{gname}.png", dpi=80)
        plt.close(fig)
    for name in ("riser_short", "riser_long", "release_heavy", "release_ultimate", "explosion_huge", "beam_blast",
                 "explosion_1", "thunder_1", "timestop", "domain", "circle_big", "pillar", "summon", "meteor_fall"):
        if name in sounds:
            big_plot(sounds[name][0], name, folder / f"big_{name}.png")


def big_plot(x, title, path):
    """Крупная спектрограмма с кривой RMS — в том же виде, что и разбор роликов-образцов."""
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    fig, (a1, a2) = plt.subplots(2, 1, figsize=(16, 6.6), gridspec_kw={"height_ratios": [3, 1]}, sharex=True)
    _draw(a1, x, fs=10, rms=False)
    a1.set_ylabel("Гц")
    a1.set_title(f"{title} ({len(x) / SR:.1f} с)")
    a2.plot(tt(len(x)), _rms_db(x), lw=0.8)
    a2.set_ylim(-60, 0)
    a2.set_ylabel("дБ")
    a2.set_xlabel("с")
    a2.grid(alpha=0.3)
    fig.tight_layout()
    fig.savefig(path, dpi=80)
    plt.close(fig)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--check", action="store_true", help="только проверить готовые файлы")
    ap.add_argument("--preview", type=Path, help="только спектрограммы в эту папку")
    ap.add_argument("--only", help="только звуки, чьё имя подходит под регулярное выражение")
    ap.add_argument("--jobs", type=int, default=0, help="процессов для синтеза (по умолчанию — по числу ядер)")
    args = ap.parse_args()
    if args.check:
        raise SystemExit(0 if check(sorted(OUT.glob("*.ogg"))) else 1)
    sounds = render_all(args.only, args.jobs or None)
    if args.preview:
        preview(sounds, args.preview)
        print(f"спектрограммы {len(sounds)} звуков в {args.preview}")
        return
    items = [(name, x, loop) for name, (x, loop) in sounds.items()]
    with ProcessPoolExecutor(args.jobs or min(8, os.cpu_count() or 1)) as ex:
        list(ex.map(_write_job, items))
    print(f"записано {len(sounds)} файлов в {OUT}")
    if args.only is None:
        stale = [p for p in OUT.glob("*.ogg") if p.stem not in sounds]
        for p in stale:
            p.unlink()
        if stale:
            print(f"удалены устаревшие: {', '.join(sorted(p.stem for p in stale))}")
    print()
    raise SystemExit(0 if check(sorted(OUT.glob("*.ogg"))) else 1)


if __name__ == "__main__":
    main()
