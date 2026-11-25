import argparse
import json
import math
import os
import subprocess
import sys

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from reel import Titles, Snow, place_tags, ease, back_ease, probe, run  # noqa: E402

OW, OH, FPS = 1080, 1920, 30
CW, CH = 700, 1442
BEZEL = 16
RAD = 58
CX, CY = OW / 2, 1110
CAM = 2600.0


def smooth(x):
    x = min(1.0, max(0.0, x))
    return x * x * x * (x * (x * 6 - 15) + 10)


def out_back(x, s=1.4):
    x = min(1.0, max(0.0, x))
    return 1 + (s + 1) * (x - 1) ** 3 + s * (x - 1) ** 2


def background(w, h):
    yy = np.linspace(0, 1, h)[:, None, None]
    xx = np.linspace(0, 1, w)[None, :, None]
    top = np.array([16, 40, 48])
    mid = np.array([12, 20, 30])
    bot = np.array([24, 16, 32])
    arr = np.where(yy < 0.5, top + (mid - top) * (yy / 0.5), mid + (bot - mid) * ((yy - 0.5) / 0.5))
    arr = arr * np.ones((1, w, 1))
    glow = np.exp(-(((xx - 0.2) / 0.5) ** 2 + ((yy - 0.12) / 0.35) ** 2)) * np.array([22, 55, 60])
    glow2 = np.exp(-(((xx - 0.85) / 0.45) ** 2 + ((yy - 0.92) / 0.3) ** 2)) * np.array([50, 22, 56])
    arr = arr + glow + glow2
    vig = 1 - 0.35 * (((xx - 0.5) * 1.6) ** 2 + ((yy - 0.5) * 1.2) ** 2)
    arr = arr * np.clip(vig, 0.55, 1)
    return np.clip(arr, 0, 255).astype(np.uint8)


def phone_shell():
    w, h = CW + BEZEL * 2, CH + BEZEL * 2
    body = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(body)
    d.rounded_rectangle([0, 0, w - 1, h - 1], RAD + BEZEL, fill=(12, 13, 16, 255))
    d.rounded_rectangle([1, 1, w - 2, h - 2], RAD + BEZEL - 1, outline=(120, 126, 140, 255), width=2)
    d.rounded_rectangle([5, 5, w - 6, h - 6], RAD + BEZEL - 5, outline=(40, 42, 50, 255), width=2)
    mask = Image.new("L", (CW, CH), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, CW - 1, CH - 1], RAD, fill=255)
    return np.asarray(body).astype(np.float32), np.asarray(mask).astype(np.float32)[..., None] / 255.0


def shadow_sprite(pad=120):
    w, h = CW + BEZEL * 2 + pad * 2, CH + BEZEL * 2 + pad * 2
    im = Image.new("L", (w, h), 0)
    ImageDraw.Draw(im).rounded_rectangle([pad, pad, w - pad, h - pad], RAD + BEZEL, fill=200)
    im = im.filter(ImageFilter.GaussianBlur(46))
    return np.asarray(im).astype(np.float32) / 255.0, pad


def rot(rx, ry, rz):
    rx, ry, rz = map(math.radians, (rx, ry, rz))
    X = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]])
    Y = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
    Z = np.array([[math.cos(rz), -math.sin(rz), 0], [math.sin(rz), math.cos(rz), 0], [0, 0, 1]])
    return Z @ Y @ X


def project(w, h, pose):
    R = rot(pose["rx"], pose["ry"], pose["rz"])
    s = pose["s"]
    pts = np.array([[-w / 2, -h / 2, 0], [w / 2, -h / 2, 0], [w / 2, h / 2, 0], [-w / 2, h / 2, 0]]) * s
    p = pts @ R.T
    z = p[:, 2] + CAM
    x = p[:, 0] * CAM / z + pose["x"]
    y = p[:, 1] * CAM / z + pose["y"]
    return np.stack([x, y], axis=1).astype(np.float32)


def pcm(path, sr=22050):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-vn", "-ac", "1", "-ar", str(sr), "-f", "f32le", "-"], stdout=subprocess.PIPE, check=True).stdout
    return np.frombuffer(raw, np.float32)


def onsets(a, sr=22050):
    h = int(0.005 * sr)
    n = len(a) // h
    e = 20 * np.log10(np.sqrt((a[:n * h].reshape(n, h) ** 2).mean(1)) + 1e-9)
    act = e > -48
    out = []
    i = 6
    while i < n:
        if act[i] and e[i] - e[i - 6:i].min() > 14:
            j = i
            while j < n and act[j:j + 20].any():
                j += 1
            out.append((i * h / sr, (j - i) * h / sr))
            i = j
        else:
            i += 1
    return out


class Source:
    def __init__(self, path):
        self.path = path

    def frames(self, t0, dur, speed=1.0):
        n_src = int(math.ceil(dur * speed * FPS)) + 2
        cmd = ["ffmpeg", "-v", "error", "-ss", f"{max(0, t0):.3f}", "-i", self.path, "-frames:v", str(n_src), "-f", "rawvideo", "-pix_fmt", "rgb24", "-"]
        p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        size = CW * CH * 3
        got = []
        while True:
            buf = p.stdout.read(size)
            if len(buf) < size:
                break
            got.append(np.frombuffer(buf, np.uint8).reshape(CH, CW, 3))
        p.wait()
        if not got:
            got = [np.zeros((CH, CW, 3), np.uint8)]
        n = int(round(dur * FPS))
        out = []
        for j in range(n):
            k = min(len(got) - 1, int(j * speed))
            out.append((got[k], t0 + k / FPS))
        return out


def apply_blur(img, st, blurs):
    for b in blurs:
        a0, a1 = b["at"]
        if not (a0 <= st <= a1):
            continue
        x0, y0, x1, y1 = b["box"]
        X0, Y0, X1, Y1 = int(x0 * CW), int(y0 * CH), int(x1 * CW), int(y1 * CH)
        if X1 - X0 < 4 or Y1 - Y0 < 4:
            continue
        if img.flags.writeable is False:
            img = img.copy()
        roi = img[Y0:Y1, X0:X1]
        small = cv2.resize(roi, (max(1, (X1 - X0) // 14), max(1, (Y1 - Y0) // 14)), interpolation=cv2.INTER_AREA)
        roi = cv2.resize(small, (X1 - X0, Y1 - Y0), interpolation=cv2.INTER_NEAREST)
        img[Y0:Y1, X0:X1] = cv2.GaussianBlur(roi, (0, 0), 6)
    return img


class Promo:
    def __init__(self, fonts, sources, audio=None):
        self.titles = Titles(fonts)
        self.clicks = {k: onsets(pcm(v)) for k, v in (audio or {}).items()}
        self.sources = {k: Source(v) for k, v in sources.items()}
        self.bgw, self.bgh = OW + 160, OH + 160
        self.bg = background(self.bgw, self.bgh)
        self.snow = Snow(110, 11)
        self.shell, self.mask = phone_shell()
        self.shadow, self.spad = shadow_sprite()
        self.events = []
        self.clock = 0.0
        self.side = 1

    def pose(self, t, dur, beat, push):
        g = self.clock + t
        ry = 7.5 * math.sin(2 * math.pi * g / 7.3)
        rx = 4.0 * math.sin(2 * math.pi * g / 5.1 + 1.0)
        rz = 1.2 * math.sin(2 * math.pi * g / 9.0 + 2.0)
        y = CY + 12 * math.sin(2 * math.pi * g / 4.3)
        x = CX + 10 * math.sin(2 * math.pi * g / 6.1 + 0.5)
        s = 0.94 + 0.012 * math.sin(2 * math.pi * g / 3.7)
        side = self.side
        e = 1 - (1 - min(1.0, max(0.0, t / 0.6))) ** 3
        ry += -72 * (1 - e)
        rz += side * 3 * (1 - e)
        x += 50 * (1 - e)
        s *= 0.9 + 0.1 * e
        o = min(1.0, max(0.0, (t - (dur - 0.34)) / 0.34)) ** 2
        ry += 72 * o
        x -= 50 * o
        rz -= side * 3 * o
        s *= 1 - 0.08 * o
        if push:
            k, px, py, tilt = push
            s *= k
            x += (CX - px) * (k - 1) * 0.94 * s / k
            y += (CY - 60 - py) * (k - 1) * 0.94 * s / k
            rx += tilt * 7
            ry += -tilt * 11 * side
        return dict(rx=rx, ry=ry, rz=rz, x=x, y=y, s=s), o

    def render_phone(self, frame, content, pose, glare):
        shell = self.shell.copy()
        c = content.astype(np.float32)
        a = self.mask
        region = shell[BEZEL:BEZEL + CH, BEZEL:BEZEL + CW]
        region[..., :3] = c * a + region[..., :3] * (1 - a)
        region[..., 3] = np.maximum(region[..., 3], a[..., 0] * 255)
        if glare is not None:
            h, w = shell.shape[:2]
            yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
            band = np.exp(-(((xx + yy * 0.55) - glare * (w + h)) / 90.0) ** 2) * 0.16
            shell[..., :3] = shell[..., :3] + band[..., None] * 255 * (shell[..., 3:4] / 255)
        w, h = shell.shape[1], shell.shape[0]
        dst = project(w, h, pose)
        src = np.float32([[0, 0], [w, 0], [w, h], [0, h]])
        H = cv2.getPerspectiveTransform(src, dst)
        sh_pose = dict(pose, x=pose["x"] + pose["ry"] * 2.2, y=pose["y"] + 34)
        sw, shh = self.shadow.shape[1], self.shadow.shape[0]
        sdst = project(sw, shh, sh_pose)
        Hs = cv2.getPerspectiveTransform(np.float32([[0, 0], [sw, 0], [sw, shh], [0, shh]]), sdst)
        sm = cv2.warpPerspective(self.shadow, Hs, (OW, OH), flags=cv2.INTER_LINEAR)
        frame *= (1 - 0.75 * sm[..., None])
        warped = cv2.warpPerspective(shell, H, (OW, OH), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, borderValue=(0, 0, 0, 0))
        al = warped[..., 3:4] / 255.0
        frame[:] = warped[..., :3] * al + frame * (1 - al)
        return frame

    def base(self, t_global, ry):
        ox = int(80 + max(-70, min(70, -ry * 3.0)))
        oy = int(80 + 30 * math.sin(t_global / 6.0))
        return self.bg[oy:oy + OH, ox:ox + OW].astype(np.float32)

    def overlay(self, frame, layer):
        arr = np.asarray(layer).astype(np.float32)
        al = arr[..., 3:4] / 255.0
        frame[:] = arr[..., :3] * al + frame * (1 - al)

    def text_layer(self, items, t, dur, dim=0.0):
        layer = Image.new("RGBA", (OW, OH), (0, 0, 0, 0))
        for k, (img, x, y, kind) in enumerate(items):
            start = 0.12 + k * 0.07
            a = ease((t - start) / 0.32)
            out = ease((t - (dur - 0.24)) / 0.2)
            vis = a * (1 - out) * (1 - 0.7 * dim)
            if vis <= 0.01:
                continue
            blur = (1 - a) * 12 + out * 12 + dim * 7
            im = img.filter(ImageFilter.GaussianBlur(blur)) if blur > 0.6 else img
            if vis < 0.99:
                im = im.copy()
                im.putalpha(im.getchannel("A").point(lambda v: int(v * vis)))
            ox, oy = img.info.get("off", (0, 0))
            sc = 0.9 + 0.1 * out_back(a, 1.6)
            if abs(sc - 1) > 0.005:
                w2, h2 = max(1, int(im.width * sc)), max(1, int(im.height * sc))
                im = im.resize((w2, h2), Image.BILINEAR)
                ox += (img.width - w2) / 2
                oy += (img.height - h2) / 2
            dy = int((1 - a) * 30)
            layer.alpha_composite(im, (int(x + ox), int(y + oy + dy)))
        return layer

    def tags(self, layer, placed, t, dur, delay):
        for k, (im, x, y) in enumerate(placed):
            start = delay + k * 0.13
            p = (t - start) / 0.34
            if p <= 0:
                continue
            out = ease((t - (dur - 0.22)) / 0.2)
            s = 0.55 + 0.45 * back_ease(p)
            vis = min(1.0, p * 2) * (1 - out)
            if vis <= 0.01:
                continue
            ang = (-3 if k % 2 == 0 else 3) * (1 - min(1, p))
            sm = im.resize((max(1, int(im.width * s)), max(1, int(im.height * s))), Image.BILINEAR)
            if abs(ang) > 0.1:
                sm = sm.rotate(ang, resample=Image.BILINEAR, expand=True)
            if vis < 0.99:
                sm.putalpha(sm.getchannel("A").point(lambda v: int(v * vis)))
            layer.alpha_composite(sm, (int(x + (im.width - sm.width) / 2), int(y + (im.height - sm.height) / 2)))

    def collect(self, beat, dur):
        src = self.sources[beat.get("src", "main")]
        spans = beat["spans"]
        x = 5
        parts = []
        for sp in spans:
            a, b = sp[0], sp[1]
            sp_speed = sp[2] if len(sp) > 2 else beat.get("speed", 1.0)
            parts.append(src.frames(a, (b - a) / sp_speed, sp_speed))
        seq = []
        cuts = []
        for i, fr in enumerate(parts):
            if seq and len(fr) > x:
                for j in range(x):
                    al = (j + 1) / (x + 1)
                    pa, ta = seq[-x + j]
                    pb, tb = fr[j]
                    seq[-x + j] = ((pa.astype(np.float32) * (1 - al) + pb.astype(np.float32) * al).astype(np.uint8), tb)
                cuts.append(len(seq) - x)
                seq.extend(fr[x:])
            else:
                seq.extend(fr)
        n = int(round(dur * FPS))
        if len(seq) > n and len(seq) <= n * 1.7:
            m = len(seq)
            seq = [seq[min(m - 1, int(j * m / n))] for j in range(n)]
            cuts = [int(c * n / m) for c in cuts]
        if len(seq) < n:
            m = len(seq)
            if m >= n * 0.68:
                seq = [seq[min(m - 1, int(j * m / n))] for j in range(n)]
                cuts = [int(c * n / m) for c in cuts]
            else:
                seq = [seq[min(m - 1, int(j * 0.75))] for j in range(int(m / 0.75))]
                seq += [seq[-1]] * (n - len(seq))
        return seq[:n], cuts

    def app_sounds(self, seq, dur, src):
        out, last = [], -9.0
        for t, length in self.clicks.get(src, []):
            if length > 0.5:
                continue
            for i in range(1, len(seq)):
                s0, s1 = seq[i - 1][1], seq[i][1]
                if s0 < t <= s1 and s1 - s0 < 0.2:
                    at = (i - 1 + (t - s0) / max(1e-6, s1 - s0)) / FPS
                    if 0.35 < at < dur - 0.25 and at - last > 0.28:
                        out.append(("app", self.clock + at, src, t, length))
                        last = at
                    break
        return out

    def beat_frames(self, beat, dur):
        items, _ = self.titles.word_images(beat, beat.get("top", 96 if beat.get("kicker") else 150))
        tags = self.titles.tag_images(beat.get("tags"))
        placed = place_tags(tags, beat.get("tags_y", 1640))
        seq, cuts = self.collect(beat, dur)
        if beat.get("listen"):
            sp = beat["spans"][0]
            self.events.append(("listen", self.clock, beat.get("src", "main"), sp[0], sp[1]))
        else:
            self.events.extend(self.app_sounds(seq, dur, beat.get("src", "main")))
        zooms = beat.get("zoom", [])
        blurs = beat.get("blur", [])
        n = len(seq)
        zoom_started = set()
        for i in range(n):
            t = i / FPS
            img, st = seq[i]
            img = apply_blur(np.array(img), st, blurs) if blurs else img
            push = None
            dim = 0.0
            for zi, z in enumerate(zooms):
                a0, a1 = z["at"]
                k = smooth((st - a0) / 0.45) * (1 - smooth((st - a1) / 0.45))
                if k > 0.001:
                    if zi not in zoom_started and k > 0.05:
                        zoom_started.add(zi)
                        self.events.append(("zoom", self.clock + t))
                    kk = 1 + (z.get("k", 1.5) - 1) * k
                    dim = max(dim, k)
                    pose0 = (CX - CW * 0.94 / 2 + z["c"][0] * CW * 0.94, CY - CH * 0.94 / 2 + z["c"][1] * CH * 0.94)
                    push = (kk, pose0[0], pose0[1], k)
            if push is None and beat.get("travel", True) is not False:
                y0, y1 = beat.get("travel") if isinstance(beat.get("travel"), list) else (0.3, 0.62)
                u = smooth((t - 0.45) / max(0.5, dur - 0.9))
                k = smooth((t - 0.45) / 0.9) * (1 - smooth((t - (dur - 0.55)) / 0.4))
                kk = 1 + beat.get("travel_k", 0.16) * k
                cy = y0 + (y1 - y0) * u
                cx = 0.5 + 0.06 * math.sin(u * math.pi) * self.side
                pose0 = (CX - CW * 0.94 / 2 + cx * CW * 0.94, CY - CH * 0.94 / 2 + cy * CH * 0.94)
                push = (kk, pose0[0], pose0[1], k * 0.8)
                dim = max(dim, k * 0.3)
            pose, o = self.pose(t, dur, beat, push)
            frame = self.base(self.clock + t, pose["ry"])
            glare = None
            gp = (t - 0.15) / 0.9
            if 0 <= gp <= 1:
                glare = gp
            self.render_phone(frame, img, pose, glare)
            layer = Image.new("RGBA", (OW, OH), (0, 0, 0, 0))
            self.snow.draw(layer, self.clock + t)
            layer.alpha_composite(self.text_layer(items, t, dur, dim))
            self.tags(layer, placed, t, dur, beat.get("tags_delay", 0.6))
            self.overlay(frame, layer)
            yield np.clip(frame, 0, 255).astype(np.uint8)
        for j in range(len(placed)):
            self.events.append(("pop", self.clock + beat.get("tags_delay", 0.6) + j * 0.13))
        self.events.append(("whoosh_out", self.clock + dur - 0.3))
        self.clock += dur
        self.side = -self.side

    def card_frames(self, beat, dur):
        items, _ = self.titles.word_images(dict(beat, size=beat.get("size", 170)), beat.get("top", 760))
        sub = beat.get("sub")
        placed = place_tags(self.titles.tag_images([sub] if sub else []), beat.get("tags_y", 1090))
        for i in range(int(round(dur * FPS))):
            t = i / FPS
            frame = self.base(self.clock + t, 0)
            tail = 0 if beat.get("fade_out", True) else 10
            layer = Image.new("RGBA", (OW, OH), (0, 0, 0, 0))
            self.snow.draw(layer, self.clock + t)
            layer.alpha_composite(self.text_layer(items, t, dur + tail))
            self.tags(layer, placed, t, dur + tail, 0.5)
            self.overlay(frame, layer)
            if beat.get("fade_black"):
                f = smooth((t - (dur - 0.8)) / 0.8)
                frame *= 1 - f
            yield np.clip(frame, 0, 255).astype(np.uint8)
        self.events.append(("impact", self.clock + 0.08))
        self.clock += dur


def encode(path, frames):
    tmp = path[:-4] + ".tmp.mp4"
    enc = subprocess.Popen(["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{OW}x{OH}", "-r", str(FPS), "-i", "-",
                            "-c:v", "libx264", "-crf", "17", "-preset", "veryfast", "-pix_fmt", "yuv420p", tmp], stdin=subprocess.PIPE)
    n = 0
    for fr in frames:
        enc.stdin.write(fr.tobytes())
        n += 1
    enc.stdin.close()
    enc.wait()
    if enc.returncode == 0:
        os.replace(tmp, path)
    return n


def mix(plan, video, events, total, audio, out, beats_at, voices=()):
    inputs = ["-i", video]
    filters, tracks = [], []
    n = 1

    def add(name, at, vol):
        nonlocal n
        p = os.path.join(audio, f"sfx_{name}.mp3")
        if not os.path.exists(p) or os.path.getsize(p) < 1500:
            return
        inputs.extend(["-i", p])
        d = max(0, int(at * 1000))
        filters.append(f"[{n}:a]lowpass=f=4500,adelay={d}|{d},volume={vol * plan.get('sfx_scale', 1.0):.3f}[s{n}]")
        tracks.append(f"[s{n}]")
        n += 1

    whooshes = ["w_soft", "w_deep", "w_soft", "w_tilt"]
    for k, at in enumerate(beats_at):
        if k == 0:
            add("impact", at + 0.15, 0.5)
        elif k == 1:
            add("w_soft", max(0, at - 0.18), 0.45)
        else:
            add(whooshes[k % len(whooshes)], max(0, at - 0.18), 0.5)
    pops = ["pop_a", "pop_b"]
    pc = 0
    sr = 48000
    snips = os.path.join(os.path.dirname(os.path.abspath(out)), "snips")
    os.makedirs(snips, exist_ok=True)
    cache, keys = {}, []

    def src_audio(name):
        if name not in cache:
            cache[name] = pcm(plan["app_audio"][name], sr)
        return cache[name]

    def snippet(name, t0, t1, fin, fout):
        a = src_audio(name)[int(t0 * sr):int(t1 * sr)].copy()
        fi, fo = int(fin * sr), int(fout * sr)
        a[:fi] *= np.linspace(0, 1, fi, dtype=np.float32)
        a[len(a) - fo:] *= np.linspace(1, 0, fo, dtype=np.float32)
        path = os.path.join(snips, f"{name}_{t0:.3f}.f32")
        a.astype(np.float32).tofile(path)
        return path

    def add_raw(path, at, vol, tag):
        nonlocal n
        inputs.extend(["-f", "f32le", "-ar", str(sr), "-ac", "1", "-i", path])
        d = max(0, int(at * 1000))
        filters.append(f"[{n}:a]adelay={d}|{d},volume={vol:.3f}[{tag}{n}]")
        n += 1
        return f"[{tag}{n - 1}]"

    for ev in events:
        kind, at = ev[0], ev[1]
        if kind == "pop":
            add(pops[pc % 2], at, 0.42)
            pc += 1
        elif kind == "app":
            _, _, name, t, length = ev
            seg = src_audio(name)[int(t * sr):int((t + 0.12) * sr)]
            peak = 20 * np.log10(np.abs(seg).max() + 1e-9) if len(seg) else -99
            if peak < -40:
                continue
            gain = min(8.0, 10 ** ((plan.get("app_peak", -11.0) - peak) / 20))
            p = snippet(name, max(0, t - 0.012), t + min(0.6, length + 0.12), 0.004, 0.05)
            tracks.append(add_raw(p, at - 0.012, gain, "c"))
        elif kind == "listen":
            _, _, name, t0, t1 = ev
            p = snippet(name, t0, t1, 0.25, 0.45)
            keys.append(add_raw(p, at, plan.get("listen_volume", 1.6), "l"))
        elif kind == "impact":
            add("impact", at, 0.55)
        elif kind == "sparkle":
            add("sparkle", at, 0.35)
    vtags = []
    tempo = plan.get("voice_tempo", 1.0)
    for p, at in voices:
        inputs.extend(["-i", p])
        d = max(0, int(at * 1000))
        filters.append(f"[{n}:a]atempo={tempo},adelay={d}|{d},volume={plan.get('voice_volume', 1.0)}[v{n}]")
        vtags.append(f"[v{n}]")
        n += 1
    music = os.path.join(audio, "music.mp3")
    has_music = os.path.exists(music)
    if has_music:
        inputs.extend(["-stream_loop", "-1", "-i", music])
        filters.append(f"[{n}:a]atrim=0:{total:.3f},volume={plan.get('music_volume', 0.5)},afade=t=in:d=1.0,afade=t=out:st={max(0, total - 2.5):.2f}:d=2.5[m0]")
        n += 1
    vtags += keys
    if vtags:
        filters.append(f"{''.join(vtags)}amix=inputs={len(vtags)}:normalize=0[vo]")
        if has_music:
            filters.append("[vo]asplit=2[vo1][key]")
            filters.append("[m0][key]sidechaincompress=threshold=0.03:ratio=5:attack=30:release=450[m]")
            tracks.append("[vo1]")
            tracks.append("[m]")
        else:
            tracks.append("[vo]")
    elif has_music:
        filters.append("[m0]anull[m]")
        tracks.append("[m]")
    filters.append(f"{''.join(tracks)}amix=inputs={len(tracks)}:normalize=0,atrim=0:{total:.3f},alimiter=limit=0.92[aout]")
    run(["ffmpeg", "-v", "error", "-y", *inputs, "-filter_complex", ";".join(filters), "-map", "0:v", "-map", "[aout]",
         "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-shortest", out])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--plan", required=True)
    ap.add_argument("--fonts", required=True)
    ap.add_argument("--audio", default="")
    ap.add_argument("--out", required=True)
    ap.add_argument("--only", default="")
    ap.add_argument("--stills", default="")
    a = ap.parse_args()
    plan = json.load(open(a.plan))
    promo = Promo(a.fonts, plan["sources"], plan.get("app_audio"))
    work = a.out[:-4] + "_work"
    os.makedirs(work, exist_ok=True)
    only = {int(x) for x in a.only.split(",") if x}
    files, starts, voices = [], [], []
    for k, b in enumerate(plan["beats"], 1):
        dur = b.get("dur")
        if not dur:
            dur = sum((s[1] - s[0]) / (s[2] if len(s) > 2 else b.get("speed", 1.0)) for s in b["spans"]) - 5 / FPS * (len(b["spans"]) - 1)
        line = b.get("line", f"line{k:02d}")
        vp = os.path.join(a.audio, f"{line}.mp3") if a.audio and line else ""
        if plan.get("voice") and vp and os.path.exists(vp):
            lead = plan.get("voice_lead", 0.45)
            need = lead + probe(vp) / plan.get("voice_tempo", 1.0) + plan.get("voice_tail", 0.4)
            dur = max(need, min(dur, need + plan.get("max_gap", 1.4)))
            voices.append((vp, promo.clock + lead))
        dur = round(dur * FPS) / FPS
        starts.append(promo.clock)
        name = os.path.join(work, f"b{k:02d}.mp4")
        files.append(name)
        if only and k not in only:
            promo.clock += dur
            promo.side = -promo.side if not b.get("card") else promo.side
            continue
        gen = promo.card_frames(b, dur) if b.get("card") else promo.beat_frames(b, dur)
        if a.stills:
            os.makedirs(a.stills, exist_ok=True)
            frames = list(gen)
            for q in (0.12, 0.5, 0.88):
                Image.fromarray(frames[min(len(frames) - 1, int(len(frames) * q))]).save(os.path.join(a.stills, f"b{k:02d}_{int(q * 100):02d}.jpg"), quality=86)
            gen = iter(frames)
        cnt = encode(name, gen)
        print(f"beat {k} {b.get('title', '')} {dur:.2f}s frames={cnt}", flush=True)
    json.dump({"events": promo.events, "starts": starts, "total": promo.clock, "voices": voices}, open(os.path.join(work, "events.json"), "w"))
    if only:
        return
    lst = os.path.join(work, "beats.txt")
    open(lst, "w").write("".join(f"file '{os.path.abspath(f)}'\n" for f in files))
    video = os.path.join(work, "video.mp4")
    run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", video])
    if a.audio:
        mix(plan, video, promo.events, promo.clock, a.audio, a.out, starts, voices)
    else:
        os.replace(video, a.out)
    print("done", a.out, f"{promo.clock:.1f}s")


if __name__ == "__main__":
    main()
