import os
import sys
import wave

import numpy as np
from scipy.signal import butter, sosfilt

SR = 44100
OUT = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/raw"
rng = np.random.default_rng(7)

WOOD = ((1, 1.0), (2.76, 0.06))
BELL = ((1, 1.0), (2.0, 0.12), (3.0, 0.03))
MARIMBA = ((1, 1.0), (3.93, 0.08))
FELT = ((1, 1.0), (2.0, 0.08))
PURE = ((1, 1.0),)


def smooth_end(x, part=0.3):
    n = len(x)
    k = max(8, int(n * part))
    x[-k:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, k))
    return x


def env(n, attack, decay):
    x = np.arange(n) / SR
    a = np.clip(x / max(attack, 1e-4), 0, 1)
    a = 0.5 - 0.5 * np.cos(np.pi * a)
    return a * np.exp(-np.maximum(x - attack, 0) / max(decay, 1e-4))


def tone(f0, f1, decay, attack=0.006, partials=FELT, glide=0.08, dur=None):
    dur = dur or attack + decay * 7
    n = int(SR * dur)
    x = np.arange(n) / SR
    k = np.clip(x / max(glide, 1e-4), 0, 1)
    k = 1 - (1 - k) ** 2
    f = f0 + (f1 - f0) * k
    ph = 2 * np.pi * np.cumsum(f) / SR
    s = np.zeros(n)
    for m, a in partials:
        s += a * np.sin(m * ph) * np.exp(-x * (m - 1) * 18)
    return smooth_end(s * env(n, attack, decay))


def band(n, lo, hi):
    return sosfilt(butter(2, [lo, hi], btype="band", fs=SR, output="sos"), rng.standard_normal(n))


def hiss(lo, hi, decay, attack=0.01, dur=None):
    dur = dur or attack + decay * 6
    n = int(SR * dur)
    return smooth_end(band(n, lo, hi) * env(n, attack, decay))


def sweep(dur, f0, f1, width):
    n = int(SR * dur)
    x = rng.standard_normal(n)
    out = np.zeros(n)
    seg = 256
    for i in range(0, n, seg):
        c = f0 + (f1 - f0) * (i / n)
        out[i:i + seg] = sosfilt(butter(2, [c, c + width], btype="band", fs=SR, output="sos"), x[i:i + seg])
    w = np.sin(np.pi * np.arange(n) / n) ** 2
    return out * w


def mix(*parts):
    end = max(int(SR * s) + len(sig) for s, sig, g in parts)
    out = np.zeros(end)
    for start, sig, gain in parts:
        i = int(SR * start)
        out[i:i + len(sig)] += sig * gain
    return out


def tock(f, decay=0.032, attack=0.005, drop=0.94):
    return tone(f, f * drop, decay, attack, WOOD, 0.05)


def note(f, decay=0.11, partials=MARIMBA, attack=0.005):
    return tone(f, f, decay, attack, partials, 0.01)


def room(y):
    n = int(SR * 0.06)
    tail = rng.standard_normal(n) * np.exp(-np.arange(n) / (SR * 0.012))
    tail = sosfilt(butter(2, 2200, btype="low", fs=SR, output="sos"), tail)
    wet = np.convolve(y, tail / np.abs(tail).sum())
    out = np.concatenate([y, np.zeros(len(wet) - len(y))])
    return out + 0.2 * wet


def finish(x, cut):
    y = sosfilt(butter(2, cut, btype="low", fs=SR, output="sos"), x)
    y = sosfilt(butter(2, cut * 1.25, btype="low", fs=SR, output="sos"), y)
    y = sosfilt(butter(1, 120, btype="high", fs=SR, output="sos"), y)
    y = room(y)
    a = np.abs(y)
    last = len(y) - np.argmax(a[::-1] > a.max() * 0.001)
    y = y[: min(len(y), last + int(SR * 0.01))]
    k = min(len(y) // 3, int(SR * 0.03))
    y[-k:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, k))
    lead = int(SR * 0.002)
    return np.concatenate([np.zeros(lead), y])


def save(name, x, loud=-25.0, cut=3200, peak=-9.0):
    y = finish(x, cut)
    active = y[np.abs(y) > np.abs(y).max() * 0.05]
    rms = np.sqrt(np.mean(active ** 2)) if len(active) else 1
    y = y * (10 ** ((loud + 3) / 20) / rms)
    top = np.abs(y).max()
    cap = 10 ** (peak / 20)
    if top > cap:
        y *= cap / top
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((y * 32767).astype("<i2").tobytes())


def rustle(dur, lo=700, hi=3400):
    n = int(SR * dur)
    y = band(n, lo, hi)
    grain = np.abs(sosfilt(butter(1, 40, fs=SR, output="sos"), rng.standard_normal(n)))
    return smooth_end(y * (0.4 + grain / grain.max()) * np.sin(np.pi * np.arange(n) / n) ** 1.2)


for i, f in enumerate((520, 570, 480)):
    save(f"ui_tap{'' if i == 0 else '_' + str(i)}", mix((0, tock(f), 1.0), (0, tone(f / 2, f / 2, 0.02, 0.004, PURE), 0.25)))
save("ui_toggle", mix((0, tock(640, 0.036), 1.0), (0, tone(240, 230, 0.03, 0.005, PURE), 0.4)))
save("ui_segment", mix((0, tock(760, 0.024, 0.004), 1.0), (0, tone(380, 380, 0.02, 0.004, PURE), 0.3)), -27)
save("ui_tick", tock(980, 0.012, 0.003), -30, 2800)
for i, (a, b) in enumerate(((300, 440), (330, 480), (280, 410))):
    save(f"ui_nav{'' if i == 0 else '_' + str(i)}", mix((0, tone(a, b, 0.04, 0.006, FELT, 0.06), 1.0), (0.012, tock(b * 1.5, 0.018), 0.22)))
save("ui_back", mix((0, tone(460, 330, 0.045, 0.006, FELT, 0.07), 1.0), (0, tock(300, 0.03), 0.4)))
save("ui_open", mix((0, sweep(0.16, 600, 1300, 700), 0.35), (0.03, tone(392, 523, 0.06, 0.008, BELL, 0.05), 1.0)), -26)
save("ui_close", mix((0, sweep(0.13, 1300, 600, 700), 0.32), (0.02, tone(523, 392, 0.05, 0.008, BELL, 0.05), 0.95)), -27)
save("ui_sheet", mix((0, sweep(0.24, 450, 1100, 700), 0.8), (0.03, tone(200, 260, 0.05, 0.01, PURE, 0.1), 0.5)), -28)
save("ui_search", mix((0, tone(740, 880, 0.06, 0.008, BELL, 0.04), 1.0), (0.05, tone(1108, 1108, 0.04, 0.008, PURE), 0.25)), -28)
save("ui_on", mix((0, tone(392, 587, 0.05, 0.007, BELL, 0.05), 1.0), (0, tock(720, 0.015), 0.3)), -26)
save("ui_off", mix((0, tone(587, 392, 0.05, 0.007, BELL, 0.05), 0.95), (0, tock(500, 0.015), 0.3)), -27)
save("ui_check", mix((0, note(587, 0.06), 0.8), (0.055, note(880, 0.08), 1.0)), -27)
save("ui_uncheck", mix((0, note(740, 0.05), 0.8), (0.055, note(494, 0.07), 1.0)), -27)
save("ui_pop", tone(330, 700, 0.03, 0.006, FELT, 0.05), -27)
save("ui_confirm", mix((0, note(523, 0.09), 0.85), (0.065, note(784, 0.14), 1.0)), -26)
save("ui_add", mix((0, note(392, 0.06), 0.8), (0.055, note(587, 0.09), 1.0), (0, sweep(0.12, 600, 1300, 600), 0.15)), -26)
save("ui_bookmark", mix((0, hiss(1000, 2600, 0.01, 0.004), 0.25), (0.008, tock(430, 0.045), 1.0), (0.008, tone(215, 215, 0.03, 0.006, PURE), 0.35)))
save("ui_copy", mix((0, tock(660, 0.022), 0.9), (0.06, tock(860, 0.026), 1.0)), -27)
save("ui_trash", mix((0, tone(250, 175, 0.06, 0.008, ((1, 1), (2, 0.25), (3, 0.06)), 0.1), 1.0), (0, hiss(300, 1000, 0.035, 0.008), 0.35)), -25, 2400)
save("ui_deny", mix((0, tock(340, 0.03), 1.0), (0.1, tock(300, 0.035), 0.9)), -27)
save("ui_play", mix((0, tone(392, 587, 0.07, 0.008, MARIMBA, 0.06), 1.0), (0, sweep(0.1, 600, 1100, 600), 0.15)), -26)
save("ui_pause", mix((0, tone(587, 392, 0.065, 0.008, MARIMBA, 0.06), 0.95), (0, sweep(0.1, 1100, 600, 600), 0.13)), -27)
save("ui_success", mix((0, note(523, 0.1), 0.7), (0.075, note(659, 0.11), 0.8), (0.15, note(784, 0.2), 1.0)), -26)
save("ui_star", mix((0, tone(988, 988, 0.1, 0.008, BELL, 0.01), 1.0), (0.035, tone(1319, 1319, 0.07, 0.01, PURE, 0.01), 0.3)), -29, 3000)
save("ui_pick", mix((0, sweep(0.12, 500, 1100, 600), 0.3), (0.02, tone(440, 520, 0.035, 0.006, WOOD, 0.05), 1.0)), -26)
save("ui_place", mix((0, tone(300, 230, 0.045, 0.006, ((1, 1), (2.4, 0.18)), 0.05), 1.0), (0, hiss(400, 1300, 0.018, 0.005), 0.25)), -25, 2800)
save("ui_page", rustle(0.2), -30, 3000)
save("ui_reveal", mix((0, sweep(0.4, 800, 2000, 800), 0.4), (0.08, note(659, 0.13, BELL), 0.6), (0.17, note(988, 0.16, BELL), 0.45)), -28, 3200)

save("ui_edit", mix((0, rustle(0.05, 1200, 3000), 0.5), (0.07, rustle(0.06, 1300, 3000), 0.45), (0.02, tock(600, 0.02), 0.6)), -29, 3000)
save("ui_ai", mix((0, note(1047, 0.05, BELL), 0.7), (0.05, note(1319, 0.05, BELL), 0.6), (0.1, note(1568, 0.08, BELL), 0.55), (0, sweep(0.22, 900, 1800, 700), 0.15)), -30, 3000)
save("ui_expand", mix((0, tone(350, 520, 0.035, 0.007, FELT, 0.07), 1.0), (0.03, tock(700, 0.015), 0.25)), -27)
save("ui_collapse", mix((0, tone(520, 350, 0.035, 0.007, FELT, 0.07), 1.0), (0.03, tock(460, 0.015), 0.25)), -27)
save("ui_share", mix((0, sweep(0.18, 500, 1500, 700), 0.5), (0.06, note(660, 0.07, BELL), 0.8), (0.11, note(880, 0.08, BELL), 0.55)), -27)
save("ui_photo", mix((0, hiss(500, 2000, 0.008, 0.003), 0.6), (0, tone(320, 280, 0.02, 0.004, PURE), 0.7), (0.07, hiss(500, 1800, 0.01, 0.003), 0.5), (0.07, tone(260, 240, 0.025, 0.004, PURE), 0.6)), -28, 2800)
save("ui_voice", mix((0, tone(220, 247, 0.07, 0.02, ((1, 1), (2, 0.35), (3, 0.15), (4, 0.06)), 0.12), 1.0)), -28, 2600)
save("ui_timer", mix((0, tock(720, 0.02), 1.0), (0.11, tock(560, 0.024), 0.9)), -27)
save("ui_note", mix((0, rustle(0.08, 800, 2600), 0.4), (0.04, note(523, 0.07, FELT), 1.0)), -27)
save("ui_go", mix((0, note(494, 0.04, FELT), 0.85), (0.045, note(740, 0.06, FELT), 1.0)), -27)
save("ui_more", mix((0, tock(620, 0.014), 0.8), (0.045, tock(620, 0.014), 0.7), (0.09, tock(620, 0.016), 0.6)), -28)
save("ui_marker", mix((0, sweep(0.16, 700, 1400, 500), 0.9), (0.02, tone(330, 360, 0.04, 0.02, PURE, 0.1), 0.3)), -30, 2600)
save("ui_download", mix((0, note(659, 0.05, FELT), 0.8), (0.05, note(440, 0.08, FELT), 1.0), (0, sweep(0.14, 1300, 600, 600), 0.18)), -27)
save("ui_dice", mix(*[(i * 0.045 + float(rng.uniform(0, 0.01)), tock(float(rng.uniform(500, 800)), 0.014), 0.9 - i * 0.12) for i in range(5)]), -27)
save("ui_undo", mix((0, sweep(0.12, 1200, 600, 600), 0.35), (0.01, tone(587, 440, 0.04, 0.006, FELT, 0.08), 1.0), (0.06, tock(380, 0.02), 0.4)), -27)
save("ui_color", mix((0, tone(660, 990, 0.03, 0.004, PURE, 0.025), 1.0), (0.02, tone(330, 330, 0.03, 0.006, PURE), 0.25)), -28, 3000)
save("ui_skip_back", mix((0, tock(600, 0.018), 0.85), (0.05, tock(500, 0.02), 1.0)), -27)
save("ui_skip_next", mix((0, tock(500, 0.018), 0.85), (0.05, tock(600, 0.02), 1.0)), -27)
save("ui_hold", mix((0, tone(260, 300, 0.09, 0.05, FELT, 0.2), 1.0), (0.04, tone(520, 600, 0.06, 0.04, PURE, 0.15), 0.25)), -28, 2600)
save("ui_sync", mix((0, sweep(0.3, 600, 1400, 600), 0.4), (0.0, note(440, 0.05, FELT), 0.6), (0.09, note(554, 0.05, FELT), 0.6), (0.18, note(659, 0.08, FELT), 0.7)), -28)
save("ui_read", mix((0, rustle(0.16, 600, 2800), 0.55), (0.06, note(392, 0.1, MARIMBA), 0.7), (0.06, note(587, 0.12, MARIMBA), 0.55)), -27)
save("ui_sign_in", mix((0, hiss(900, 2400, 0.008, 0.003), 0.3), (0, tock(560, 0.022), 0.8), (0.05, tone(523, 523, 0.07, 0.006, BELL, 0.01), 0.75), (0.11, tone(784, 784, 0.12, 0.008, BELL, 0.01), 0.9)), -27)
save("ui_sign_up", mix((0, note(392, 0.16, FELT), 0.7), (0.06, note(494, 0.16, FELT), 0.7), (0.12, note(587, 0.2, FELT), 0.75), (0.2, tone(784, 784, 0.18, 0.012, BELL, 0.01), 0.45)), -27)
save("ui_sign_out", mix((0, note(587, 0.07, FELT), 0.85), (0.08, note(440, 0.1, FELT), 1.0), (0.08, tock(300, 0.03), 0.35)), -27)
save("ui_wipe", mix((0, tone(196, 160, 0.12, 0.012, ((1, 1), (2, 0.2)), 0.2), 1.0), (0, hiss(250, 900, 0.06, 0.01), 0.3)), -26, 2200)
save("ui_plan", mix((0, tone(660, 655, 0.07, 0.003, ((1, 1), (2, 0.3), (3, 0.12)), 0.02), 1.0), (0, tock(900, 0.01, 0.002), 0.25)), -28, 3000)
save("ui_pay", mix((0, sweep(0.2, 500, 1500, 600), 0.3), (0.02, tone(440, 660, 0.08, 0.008, MARIMBA, 0.09), 1.0), (0.12, note(880, 0.08, BELL), 0.45)), -27)
save("ui_grow", mix(*[(i * 0.07, tock(float(f), 0.03), 0.75) for i, f in enumerate((420, 470, 520, 560, 610))], (0.4, note(523, 0.22, BELL), 0.6), (0.46, note(659, 0.22, BELL), 0.55), (0.52, note(784, 0.3, BELL), 0.6)), -26)
save("ui_send", mix((0, sweep(0.16, 600, 1600, 700), 0.35), (0.03, tone(523, 698, 0.05, 0.006, FELT, 0.05), 0.9), (0.09, note(880, 0.07, BELL), 0.5)), -27)
save("ui_incoming", mix((0, note(698, 0.06, FELT), 0.8), (0.06, note(587, 0.09, FELT), 0.7)), -31)
save("ui_react", mix((0, tone(520, 760, 0.03, 0.004, PURE, 0.03), 0.9), (0.035, tone(880, 880, 0.04, 0.005, BELL, 0.01), 0.5)), -28, 3000)
save("ui_report", mix((0, tock(330, 0.03), 0.9), (0.07, tone(262, 220, 0.06, 0.008, FELT, 0.08), 1.0)), -27, 2600)
save("ui_mention", mix((0, note(784, 0.06, BELL), 0.7), (0.07, note(988, 0.09, BELL), 0.6), (0, tock(700, 0.012), 0.3)), -29, 3000)
save("ui_rules", mix((0, rustle(0.12, 700, 2600), 0.45), (0.05, note(440, 0.08, FELT), 0.7), (0.12, note(659, 0.12, FELT), 0.8)), -27)
