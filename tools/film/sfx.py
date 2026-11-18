import math
import os
import subprocess
import sys
import wave

import numpy as np

SR = 44100
rng = np.random.default_rng(7)


def bandpass(x, f0, f1, q):
    n = len(x)
    out = np.zeros(n)
    y1 = y2 = x1 = x2 = 0.0
    freqs = np.geomspace(f0, f1, n)
    for i in range(0, n):
        if i % 64 == 0:
            w = 2 * math.pi * freqs[i] / SR
            alpha = math.sin(w) / (2 * q)
            b0, b2 = alpha, -alpha
            a0, a1, a2 = 1 + alpha, -2 * math.cos(w), 1 - alpha
            b0, b2, a1, a2 = b0 / a0, b2 / a0, a1 / a0, a2 / a0
        v = x[i]
        y = b0 * v + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1 = x1, v
        y2, y1 = y1, y
        out[i] = y
    return out


def lowpass(x, cutoff):
    a = math.exp(-2 * math.pi * cutoff / SR)
    out = np.zeros_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        out[i] = acc
    return out


def env(n, peak=0.6, attack_pow=2.0, release_pow=3.0):
    t = np.linspace(0, 1, n)
    up = (t / peak) ** attack_pow
    down = ((1 - t) / (1 - peak)) ** release_pow
    return np.where(t < peak, up, down)


def pan(mono, start=-0.7, end=0.7):
    p = np.linspace(start, end, len(mono))
    left = mono * np.sqrt((1 - p) / 2)
    right = mono * np.sqrt((1 + p) / 2)
    return np.stack([left, right], axis=1)


def norm(x, db=-4.0):
    m = np.abs(x).max() or 1
    return x / m * 10 ** (db / 20)


def whoosh(dur, f0, f1, q, peak, p0=-0.7, p1=0.7, body=0.0):
    n = int(SR * dur)
    noise = rng.standard_normal(n)
    x = bandpass(noise, f0, f1, q) * env(n, peak)
    if body:
        t = np.arange(n) / SR
        sweep = np.sin(2 * math.pi * np.cumsum(np.geomspace(f0 * 0.5, f1 * 0.25, n)) / SR)
        x += body * sweep * env(n, peak, 2, 4) * np.abs(x).max()
    return pan(norm(x), p0, p1)


def pop(f0, f1, dur=0.16, click=0.15):
    n = int(SR * dur)
    t = np.arange(n) / SR
    f = f1 + (f0 - f1) * np.exp(-t * 45)
    x = np.sin(2 * math.pi * np.cumsum(f) / SR) * np.exp(-t * 28)
    c = rng.standard_normal(n) * np.exp(-t * 400) * click
    x = x + lowpass(c, 6000)
    return pan(norm(x, -6), 0, 0)


def riser(dur=1.5):
    n = int(SR * dur)
    t = np.arange(n) / SR
    noise = bandpass(rng.standard_normal(n), 400, 6000, 1.2)
    f = np.geomspace(180, 900, n)
    tone = np.sin(2 * math.pi * np.cumsum(f) / SR) + 0.5 * np.sin(2 * math.pi * np.cumsum(f * 1.5) / SR)
    e = (t / dur) ** 2.4
    x = (noise / (np.abs(noise).max() or 1) * 0.8 + tone * 0.35) * e
    fade = np.minimum(1, (dur - t) / 0.05)
    return pan(norm(x * fade, -5), -0.3, 0.3)


def hit(dur=2.2):
    n = int(SR * dur)
    t = np.arange(n) / SR
    sub = np.sin(2 * math.pi * (52 * t + 30 * (1 - np.exp(-t * 18)) / 18)) * np.exp(-t * 2.6)
    burst = lowpass(rng.standard_normal(n), 1800) * np.exp(-t * 16) * 3
    shimmer = sum(np.sin(2 * math.pi * f * t + k) * np.exp(-t * (2.0 + k)) for k, f in enumerate([1320, 1760, 2640, 3520])) * 0.12
    x = sub + burst * 0.4 + shimmer
    return pan(norm(x, -4), 0, 0)


def save(path, stereo):
    tmp = path + ".wav"
    data = (np.clip(stereo, -1, 1) * 32767).astype(np.int16)
    with wave.open(tmp, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", tmp, "-b:a", "192k", path], check=True)
    os.remove(tmp)


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    sounds = {
        "w1": whoosh(0.55, 500, 4200, 1.4, 0.62, -0.8, 0.8),
        "w2": whoosh(0.45, 900, 6500, 1.8, 0.55, 0.7, -0.7),
        "w3": whoosh(0.75, 220, 2600, 1.1, 0.6, -0.5, 0.6, body=0.35),
        "w4": whoosh(0.4, 1500, 7000, 2.2, 0.5, 0.6, -0.4),
        "p1": pop(1100, 420),
        "p2": pop(1700, 650, 0.12, 0.25),
        "zoom": whoosh(0.6, 300, 5200, 1.3, 0.85, -0.2, 0.2, body=0.25),
        "riser": riser(),
        "hit": hit(),
    }
    for name, x in sounds.items():
        save(os.path.join(out, f"sfx_{name}.mp3"), x)
        print("sfx", name)


if __name__ == "__main__":
    main()
