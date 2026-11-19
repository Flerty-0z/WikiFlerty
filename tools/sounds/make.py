import os
import sys
import wave

import numpy as np
from scipy.signal import butter, sosfilt

SR = 44100
OUT = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/raw"
rng = np.random.default_rng(7)


def t(d):
    return np.arange(int(SR * d)) / SR


def env(n, attack, decay, hold=0.0):
    x = np.arange(n) / SR
    a = np.clip(x / max(attack, 1e-4), 0, 1)
    a = 0.5 - 0.5 * np.cos(np.pi * a)
    d = np.exp(-np.maximum(x - attack - hold, 0) / max(decay, 1e-4))
    return a * d


def at(sig, start, total):
    out = np.zeros(int(SR * total))
    i = int(SR * start)
    j = min(len(out), i + len(sig))
    out[i:j] += sig[: j - i]
    return out


def tone(f0, f1, dur, decay, attack=0.004, partials=((1, 1.0),), curve=1.0):
    x = t(dur)
    k = (x / dur) ** curve
    f = f0 + (f1 - f0) * k
    ph = 2 * np.pi * np.cumsum(f) / SR
    s = sum(a * np.sin(m * ph) for m, a in partials)
    return s * env(len(x), attack, decay)


def noise(dur, lo, hi, decay, attack=0.003):
    x = rng.standard_normal(int(SR * dur))
    sos = butter(2, [lo, hi], btype="band", fs=SR, output="sos")
    return sosfilt(sos, x) * env(len(x), attack, decay)


def sweep(dur, lo0, lo1, width, decay, attack=0.03):
    n = int(SR * dur)
    x = rng.standard_normal(n)
    out = np.zeros(n)
    seg = 256
    for i in range(0, n, seg):
        c = lo0 + (lo1 - lo0) * (i / n)
        sos = butter(2, [c, c + width], btype="band", fs=SR, output="sos")
        out[i:i + seg] = sosfilt(sos, x[i:i + seg])
    w = np.sin(np.pi * np.clip(np.arange(n) / n, 0, 1)) ** 1.5
    return out * w * env(n, attack, decay)


def mix(total, *parts):
    out = np.zeros(int(SR * total))
    for start, sig, gain in parts:
        out += at(sig * gain, start, total)
    return out


def soften(x, cut=4200):
    sos = butter(2, cut, btype="low", fs=SR, output="sos")
    y = sosfilt(sos, x)
    hp = butter(1, 90, btype="high", fs=SR, output="sos")
    y = sosfilt(hp, y)
    tail = rng.standard_normal(int(SR * 0.04)) * np.exp(-np.arange(int(SR * 0.04)) / (SR * 0.009))
    tail = sosfilt(butter(2, 2500, btype="low", fs=SR, output="sos"), tail)
    y = y + 0.18 * np.convolve(y, tail / np.abs(tail).sum())[: len(y)]
    fade = int(SR * 0.012)
    y[-fade:] *= np.linspace(1, 0, fade)
    return y


def save(name, x, level=-9.0, cut=4200):
    y = soften(x, cut)
    peak = np.max(np.abs(y)) or 1
    y = y / peak * (10 ** (level / 20))
    data = (y * 32767).astype("<i2")
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


WOOD = ((1, 1.0), (2.76, 0.10), (5.4, 0.03))
BELL = ((1, 1.0), (2.0, 0.18), (3.0, 0.05))
MARIMBA = ((1, 1.0), (3.93, 0.12), (9.2, 0.02))


def tock(f, dur=0.09, decay=0.035, glide=0.9):
    body = tone(f, f * glide, dur, decay, 0.0025, WOOD)
    return body + 0.05 * at(noise(0.02, 900, 2600, 0.004, 0.001), 0, dur)


def note(f, decay=0.12, dur=None, partials=MARIMBA, attack=0.003):
    return tone(f, f * 0.998, dur or decay * 4, decay, attack, partials)


for i, f in enumerate((510, 560, 470)):
    save(f"ui_tap{'' if i == 0 else '_' + str(i)}", tock(f), -10)

save("ui_toggle", mix(0.12, (0, tock(620, 0.1, 0.04), 1.0), (0, tone(210, 190, 0.08, 0.03), 0.35)), -10)
save("ui_check", mix(0.26, (0, note(587, 0.07), 0.8), (0.05, note(880, 0.09), 1.0)), -11)
save("ui_uncheck", mix(0.24, (0, note(740, 0.06), 0.8), (0.05, note(494, 0.08), 1.0)), -12)
save("ui_on", mix(0.2, (0, tone(392, 587, 0.1, 0.05, 0.004, BELL), 1.0), (0, tock(700, 0.05, 0.015), 0.4)), -11)
save("ui_off", mix(0.2, (0, tone(587, 392, 0.1, 0.05, 0.004, BELL), 0.9), (0, tock(520, 0.05, 0.015), 0.4)), -12)
save("ui_open", mix(0.22, (0, sweep(0.16, 700, 1600, 900, 0.08), 0.5), (0.03, tone(392, 523, 0.14, 0.06, 0.006, BELL), 1.0)), -11)
save("ui_close", mix(0.2, (0, sweep(0.14, 1500, 700, 900, 0.06), 0.45), (0.02, tone(523, 392, 0.12, 0.05, 0.006, BELL), 0.9)), -12)
save("ui_back", mix(0.14, (0, tock(380, 0.12, 0.04, 0.85), 1.0), (0, sweep(0.08, 1200, 700, 700, 0.04, 0.01), 0.25)), -11)
for i, (a, b) in enumerate(((300, 430), (330, 470), (280, 400))):
    save(f"ui_nav{'' if i == 0 else '_' + str(i)}", mix(0.13, (0, tone(a, b, 0.1, 0.035, 0.004, ((1, 1), (2, 0.12))), 1.0), (0, tock(b * 1.6, 0.04, 0.012), 0.25)), -10)
save("ui_segment", mix(0.08, (0, tock(820, 0.06, 0.018), 1.0), (0, tone(260, 240, 0.05, 0.02), 0.3)), -12)
save("ui_sheet", mix(0.28, (0, sweep(0.24, 500, 1300, 800, 0.12, 0.05), 0.9), (0.02, tone(160, 200, 0.12, 0.05), 0.35)), -15)
save("ui_confirm", mix(0.4, (0, note(523, 0.1), 0.85), (0.06, note(784, 0.16), 1.0)), -11)
save("ui_success", mix(0.6, (0, note(523, 0.11), 0.7), (0.07, note(659, 0.12), 0.8), (0.14, note(784, 0.22), 1.0)), -11)
save("ui_star", mix(0.4, (0, tone(988, 990, 0.38, 0.11, 0.004, BELL), 1.0), (0.03, tone(1319, 1320, 0.3, 0.08, 0.004, ((1, 1),)), 0.35)), -14, 3600)
save("ui_play", mix(0.2, (0, tone(392, 587, 0.14, 0.07, 0.006, MARIMBA), 1.0), (0, sweep(0.1, 600, 1200, 700, 0.05), 0.2)), -11)
save("ui_pause", mix(0.2, (0, tone(587, 392, 0.14, 0.06, 0.006, MARIMBA), 0.95), (0, sweep(0.1, 1200, 600, 700, 0.05), 0.18)), -12)
save("ui_bookmark", mix(0.16, (0, noise(0.05, 1200, 3200, 0.012, 0.002), 0.35), (0.01, tock(440, 0.12, 0.045), 1.0)), -11)
save("ui_search", mix(0.22, (0, tone(784, 880, 0.2, 0.06, 0.005, BELL), 1.0), (0.04, tone(1175, 1175, 0.14, 0.04, 0.005), 0.25)), -14, 3800)
save("ui_add", mix(0.26, (0, note(392, 0.07), 0.8), (0.05, note(587, 0.1), 1.0), (0, sweep(0.12, 700, 1500, 700, 0.06), 0.25)), -11)
save("ui_trash", mix(0.22, (0, tone(240, 170, 0.18, 0.06, 0.004, ((1, 1), (2, 0.3), (3, 0.08))), 1.0), (0, noise(0.12, 300, 1100, 0.04, 0.004), 0.5)), -11, 2600)
save("ui_copy", mix(0.16, (0, tock(700, 0.06, 0.02), 0.9), (0.055, tock(900, 0.07, 0.022), 1.0)), -12)
save("ui_pop", tone(320, 760, 0.07, 0.025, 0.003, ((1, 1), (2, 0.08)), 0.6), -12)
save("ui_tick", tock(1100, 0.03, 0.008), -16, 3600)
save("ui_pick", mix(0.18, (0, sweep(0.12, 600, 1300, 700, 0.05), 0.4), (0.02, tock(480, 0.1, 0.035, 1.05), 1.0)), -11)
save("ui_place", mix(0.18, (0, tone(280, 220, 0.14, 0.045, 0.003, ((1, 1), (2.4, 0.2))), 1.0), (0, noise(0.06, 400, 1400, 0.02), 0.3)), -11, 2800)
save("ui_deny", mix(0.24, (0, tock(330, 0.08, 0.03), 1.0), (0.09, tock(300, 0.1, 0.035), 0.9)), -12)


def rustle(dur):
    n = int(SR * dur)
    x = rng.standard_normal(n)
    sos = butter(2, [700, 3800], btype="band", fs=SR, output="sos")
    y = sosfilt(sos, x)
    grain = np.abs(sosfilt(butter(1, 40, fs=SR, output="sos"), rng.standard_normal(n)))
    grain = grain / grain.max()
    return y * (0.4 + grain) * env(n, 0.03, dur / 3)


save("ui_page", rustle(0.2), -15, 3200)
save("ui_reveal", mix(0.5, (0, sweep(0.4, 900, 2400, 1000, 0.2, 0.08), 0.5), (0.08, note(659, 0.15, partials=BELL), 0.6), (0.16, note(988, 0.18, partials=BELL), 0.45)), -14, 3800)
