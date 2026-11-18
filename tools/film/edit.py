import argparse
import glob
import json
import math
import os
import subprocess

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OW, OH, FPS = 1080, 1920, 30
BG_TOP = (14, 15, 19)
BG_BOTTOM = (24, 26, 33)
FRAME = (70, 74, 86)
TEXT = (244, 244, 246)
MUTED = (160, 164, 176)
ACCENT = (140, 120, 255)


def run(cmd):
    subprocess.run(cmd, check=True)


def probe(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path], capture_output=True, text=True).stdout
    return float(out.strip() or 0)


def font(path, size, weight=700):
    f = ImageFont.truetype(path, size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


def ease(x):
    x = min(1.0, max(0.0, x))
    return x * x * (3 - 2 * x)


def wrap(draw, text, f, width):
    lines, line = [], ""
    for word in text.split():
        trial = (line + " " + word).strip()
        if draw.textlength(trial, font=f) <= width or not line:
            line = trial
        else:
            lines.append(line)
            line = word
    if line:
        lines.append(line)
    return lines


class Footage:
    def __init__(self, results, latency):
        marks_path = glob.glob(os.path.join(results, "**", "marks.json"), recursive=True)[0]
        self.marks = json.load(open(marks_path))
        base = os.path.dirname(marks_path)
        parts = sorted(glob.glob(os.path.join(base, "part*.mp4")))
        self.width = self.marks["width"]
        self.height = self.marks["height"]
        self.density = self.marks.get("density", 2.75)
        starts = self.marks.get("starts") or [0]
        self.offset = self.marks.get("offset", 0) / 1000.0
        self.latency = latency
        self.raw = os.path.join(base, "raw.mp4")
        self.map = []
        if not os.path.exists(self.raw):
            fixed = []
            for i, p in enumerate(parts):
                f = p.replace(".mp4", "_cfr.mp4")
                run(["ffmpeg", "-v", "error", "-y", "-i", p, "-vf", f"fps={FPS},scale={self.width}:{self.height}", "-an", "-c:v", "libx264", "-crf", "14", "-preset", "veryfast", "-pix_fmt", "yuv420p", f])
                fixed.append(f)
            lst = os.path.join(base, "parts.txt")
            open(lst, "w").write("".join(f"file '{os.path.abspath(f)}'\n" for f in fixed))
            run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", self.raw])
        acc = 0.0
        for i, p in enumerate(parts):
            d = probe(p.replace(".mp4", "_cfr.mp4")) if os.path.exists(p.replace(".mp4", "_cfr.mp4")) else probe(p)
            self.map.append((starts[i] / 1000.0 if i < len(starts) else acc, acc, d))
            acc += d
        self.steps = {s["i"]: s for s in self.marks["steps"]}
        top = int(round(30 * self.density))
        bottom = int(round(18 * self.density))
        self.crop = (0, top, self.width, self.height - bottom)

    def rec(self, ms):
        return self.offset + ms / 1000.0 + self.latency

    def raw_time(self, t):
        for start, at, d in reversed(self.map):
            if t >= start:
                return at + min(d, t - start)
        return t

    def step_time(self, i, edge="start"):
        return self.raw_time(self.rec(self.steps[i][edge]))

    def frames(self, t0, dur):
        w = self.width
        h = self.height
        cmd = ["ffmpeg", "-v", "error", "-ss", f"{max(0, t0):.3f}", "-i", self.raw, "-t", f"{dur:.3f}", "-f", "rawvideo", "-pix_fmt", "rgb24", "-"]
        p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        size = w * h * 3
        try:
            while True:
                buf = p.stdout.read(size)
                if len(buf) < size:
                    break
                yield Image.frombuffer("RGB", (w, h), buf, "raw", "RGB", 0, 1)
        finally:
            p.kill()
            p.wait()


class Editor:
    def __init__(self, footage, fonts):
        self.f = footage
        mono = os.path.join(fonts, "JetBrainsMono.ttf")
        self.title = font(mono, 84, 800)
        self.sub = font(mono, 40, 500)
        self.cap = font(mono, 50, 750)
        cx0, cy0, cx1, cy1 = footage.crop
        self.cw, self.ch = cx1 - cx0, cy1 - cy0
        self.ph = 1600
        self.pw = int(round(self.ph * self.cw / self.ch))
        if self.pw > 900:
            self.pw = 900
            self.ph = int(round(self.pw * self.ch / self.cw))
        self.px = (OW - self.pw) // 2
        self.py = OH - self.ph - 70
        self.bg = self.background()
        r = 46
        self.mask = Image.new("L", (self.pw, self.ph), 0)
        ImageDraw.Draw(self.mask).rounded_rectangle([0, 0, self.pw - 1, self.ph - 1], r, fill=255)
        self.border = Image.new("RGBA", (OW, OH), (0, 0, 0, 0))
        d = ImageDraw.Draw(self.border)
        d.rounded_rectangle([self.px - 4, self.py - 4, self.px + self.pw + 3, self.py + self.ph + 3], r + 4, outline=FRAME + (255,), width=4)
        shadow = Image.new("RGBA", (OW, OH), (0, 0, 0, 0))
        ImageDraw.Draw(shadow).rounded_rectangle([self.px, self.py + 18, self.px + self.pw, self.py + self.ph + 18], r, fill=(0, 0, 0, 150))
        self.bg = Image.alpha_composite(self.bg.convert("RGBA"), shadow.filter(ImageFilter.GaussianBlur(28))).convert("RGB")

    def background(self):
        g = np.linspace(0, 1, OH)[:, None]
        top = np.array(BG_TOP)[None, None, :]
        bot = np.array(BG_BOTTOM)[None, None, :]
        arr = (top * (1 - g[..., None]) + bot * g[..., None]).repeat(OW, axis=1)
        glow = np.zeros((OH, OW))
        yy, xx = np.mgrid[0:OH, 0:OW]
        glow += np.exp(-(((xx - OW * 0.15) / 520) ** 2 + ((yy - OH * 0.08) / 420) ** 2)) * 0.10
        glow += np.exp(-(((xx - OW * 0.9) / 600) ** 2 + ((yy - OH * 0.95) / 500) ** 2)) * 0.08
        arr = arr + glow[..., None] * np.array(ACCENT)[None, None, :]
        return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))

    def caption_layer(self, text):
        layer = Image.new("RGBA", (OW, self.py), (0, 0, 0, 0))
        if not text:
            return layer
        d = ImageDraw.Draw(layer)
        lines = wrap(d, text, self.cap, OW - 200)
        lh = 64
        bw = max(d.textlength(l, font=self.cap) for l in lines) + 64
        bh = lh * len(lines) + 40
        x0 = (OW - bw) / 2
        y0 = (self.py - bh) / 2
        d.rounded_rectangle([x0, y0, x0 + bw, y0 + bh], 26, fill=(255, 255, 255, 18), outline=(255, 255, 255, 70), width=3)
        for i, l in enumerate(lines):
            tw = d.textlength(l, font=self.cap)
            d.text(((OW - tw) / 2, y0 + 20 + i * lh), l, font=self.cap, fill=TEXT + (255,))
        return layer

    def phone(self, img, zoom):
        cx0, cy0, cx1, cy1 = self.f.crop
        if zoom:
            zx0, zy0, zx1, zy1 = zoom
            box = (cx0 + zx0, cy0 + zy0, cx0 + zx1, cy0 + zy1)
        else:
            box = (cx0, cy0, cx1, cy1)
        return img.crop(tuple(int(round(v)) for v in box)).resize((self.pw, self.ph), Image.LANCZOS)

    def zoom_box(self, rect, k):
        if rect is None or k <= 0.001:
            return None
        cx0, cy0, cx1, cy1 = self.f.crop
        l, t, r, b = rect
        t -= cy0
        b -= cy0
        rw = max(r - l, 2)
        target = min(self.cw, max(rw * 2.0, self.cw * 0.55))
        scale = self.cw / target
        s = 1 + (scale - 1) * k
        w = self.cw / s
        h = self.ch / s
        mx = (l + r) / 2
        my = (t + b) / 2
        x = min(max(mx - w / 2, 0), self.cw - w)
        y = min(max(my - h / 2, 0), self.ch - h)
        return (x, y, x + w, y + h)

    def compose(self, phone_img, cap, cap_alpha, punch=0.0):
        frame = self.bg.copy()
        if punch > 0:
            s = 1 + 0.025 * punch
            w, h = int(self.pw * s), int(self.ph * s)
            p = phone_img.resize((w, h), Image.BILINEAR)
            m = self.mask.resize((w, h), Image.BILINEAR)
            frame.paste(p, (self.px - (w - self.pw) // 2, self.py - (h - self.ph) // 2), m)
        else:
            frame.paste(phone_img, (self.px, self.py), self.mask)
        frame = frame.convert("RGBA")
        frame = Image.alpha_composite(frame, self.border)
        if cap is not None and cap_alpha > 0:
            c = cap if cap_alpha >= 1 else Image.blend(Image.new("RGBA", cap.size, (0, 0, 0, 0)), cap, cap_alpha)
            dy = int((1 - cap_alpha) * 24)
            frame.alpha_composite(c, (0, dy))
        return frame.convert("RGB")

    def card(self, title, sub, dur, backdrop):
        n = int(round(dur * FPS))
        base = backdrop.filter(ImageFilter.GaussianBlur(30)) if backdrop is not None else self.bg
        if backdrop is not None:
            base = Image.blend(base.resize((OW, OH)), Image.new("RGB", (OW, OH), BG_TOP), 0.62)
        for i in range(n):
            t = i / FPS
            a = ease(t / 0.5)
            frame = base.copy().convert("RGBA")
            layer = Image.new("RGBA", (OW, OH), (0, 0, 0, 0))
            d = ImageDraw.Draw(layer)
            tw = d.textlength(title, font=self.title)
            lines = wrap(d, sub, self.sub, OW - 220) if sub else []
            bw = max([tw] + [d.textlength(l, font=self.sub) for l in lines]) + 110
            bh = 140 + 56 * len(lines) + (20 if lines else 0)
            x0 = (OW - bw) / 2
            y0 = (OH - bh) / 2 - 40 + (1 - a) * 40
            d.rounded_rectangle([x0, y0, x0 + bw, y0 + bh], 34, fill=(255, 255, 255, int(16 * a)), outline=(255, 255, 255, int(90 * a)), width=3)
            d.text(((OW - tw) / 2, y0 + 34), title, font=self.title, fill=TEXT + (int(255 * a),))
            for k, l in enumerate(lines):
                lw = d.textlength(l, font=self.sub)
                d.text(((OW - lw) / 2, y0 + 140 + k * 56), l, font=self.sub, fill=MUTED + (int(255 * a),))
            frame.alpha_composite(layer)
            yield frame.convert("RGB")

    def clip(self, scene, dur):
        f = self.f
        pad0, pad1 = scene.get("pad", [0.4, 0.8])
        t0 = f.step_time(scene["from"]) - pad0
        t1 = f.step_time(scene["to"], "end") + pad1
        speed = scene.get("speed", 1.0)
        natural = (t1 - t0) / speed
        zooms = []
        for z in scene.get("zoom", []):
            s = f.steps[z]
            rect = s.get("rect")
            if rect:
                zooms.append((f.step_time(z) - 0.5, f.step_time(z, "end") + scene.get("hold", 1.4), rect))
        cap = self.caption_layer(scene.get("caption", ""))
        src = f.frames(t0, t1 - t0 + 0.2)
        n = int(round(dur * FPS))
        current = None
        acc = 0.0
        for i in range(n):
            tt = t0 + min(i / FPS, natural) * speed
            while acc <= min(i / FPS, natural) * speed + 1e-6:
                nxt = next(src, None)
                if nxt is None:
                    break
                current = nxt
                acc += 1 / FPS
            if current is None:
                continue
            k, rect = 0.0, None
            for a, b, r in zooms:
                if a - 0.45 <= tt <= b + 0.45:
                    k = ease((tt - (a - 0.45)) / 0.45) * (1 - ease((tt - b) / 0.45))
                    rect = r
            ph = self.phone(current, self.zoom_box(rect, k))
            ca = ease(i / FPS / 0.3)
            punch = max(0.0, 1 - i / FPS / 0.25) if scene.get("punch", True) else 0.0
            yield self.compose(ph, cap, ca, punch)
        for _ in src:
            pass

    def first_frame(self, step):
        t = self.f.step_time(step)
        for img in self.f.frames(t, 0.1):
            return img.resize((OW, int(OW * img.height / img.width))).crop((0, 0, OW, OH))
        return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--results", required=True)
    ap.add_argument("--plan", required=True)
    ap.add_argument("--audio", required=True)
    ap.add_argument("--fonts", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--only", type=int, default=0)
    a = ap.parse_args()

    plan = json.load(open(a.plan))
    footage = Footage(a.results, plan.get("latency", 0.35))
    ed = Editor(footage, a.fonts)
    work = os.path.splitext(a.out)[0] + "_work"
    os.makedirs(work, exist_ok=True)

    timeline = []
    t = 0.0
    sfx = []
    for idx, scene in enumerate(plan["scenes"], 1):
        voice = idx if scene.get("say") else scene.get("voice")
        vpath = os.path.join(a.audio, f"line{voice:02d}.mp3") if voice else None
        vlen = probe(vpath) if vpath and os.path.exists(vpath) else 0.0
        if scene.get("type") == "card":
            dur = max(scene.get("min", 1.8), vlen + 0.45)
        else:
            f = footage
            pad0, pad1 = scene.get("pad", [0.4, 0.8])
            natural = (f.step_time(scene["to"], "end") + pad1 - (f.step_time(scene["from"]) - pad0)) / scene.get("speed", 1.0)
            dur = max(natural, vlen + 0.45, scene.get("min", 0))
            if scene.get("max"):
                dur = min(dur, max(scene["max"], vlen + 0.45))
            t0 = f.step_time(scene["from"]) - pad0
            for i in range(scene["from"], scene["to"] + 1):
                s = f.steps.get(i)
                if not s or not s["ok"]:
                    continue
                at = t + (f.step_time(i) - t0) / scene.get("speed", 1.0)
                if at - t > dur:
                    continue
                if s["do"] in ("tap", "show", "pick", "tapAt", "long", "longAt"):
                    sfx.append(("pop", at, 0.55))
                elif s["do"] in ("swipe", "repeat"):
                    sfx.append(("swipe", at, 0.45))
        if idx > 1:
            sfx.append(("whoosh", t - 0.12, 0.5))
        timeline.append((idx, scene, t, dur, vpath if vlen else None))
        t += dur
    total = t

    if a.only:
        timeline = [x for x in timeline if x[0] == a.only]

    files = []
    for idx, scene, start, dur, vpath in timeline:
        name = os.path.join(work, f"s{idx:02d}.mp4")
        tmp = name.replace(".mp4", ".tmp.mp4")
        enc = subprocess.Popen(
            ["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{OW}x{OH}", "-r", str(FPS), "-i", "-",
             "-c:v", "libx264", "-crf", "17", "-preset", "veryfast", "-pix_fmt", "yuv420p", "-r", str(FPS), tmp],
            stdin=subprocess.PIPE,
        )
        if scene.get("type") == "card":
            back = ed.first_frame(scene["backdrop"]) if scene.get("backdrop") else None
            gen = ed.card(scene.get("title", ""), scene.get("sub", ""), dur, back)
        else:
            gen = ed.clip(scene, dur)
        count = 0
        for frame in gen:
            enc.stdin.write(frame.tobytes())
            count += 1
        enc.stdin.close()
        enc.wait()
        os.replace(tmp, name)
        files.append(name)
        print(f"scene {idx} {dur:.2f}s frames={count}", flush=True)

    if a.only:
        return

    lst = os.path.join(work, "scenes.txt")
    open(lst, "w").write("".join(f"file '{os.path.abspath(f)}'\n" for f in files))
    video = os.path.join(work, "video.mp4")
    run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", video])

    inputs = ["-i", video]
    filters = []
    labels = []
    n = 1
    music = os.path.join(a.audio, "music.mp3")
    voices = []
    for idx, scene, start, dur, vpath in timeline:
        if vpath:
            inputs += ["-i", vpath]
            d = int((start + scene.get("voice_delay", 0.15)) * 1000)
            filters.append(f"[{n}:a]adelay={d}|{d},volume=1.0[v{n}]")
            voices.append(f"[v{n}]")
            n += 1
    for kind, at, vol in sfx:
        p = os.path.join(a.audio, f"sfx_{kind}.mp3")
        if not os.path.exists(p) or at < 0:
            continue
        inputs += ["-i", p]
        d = int(at * 1000)
        filters.append(f"[{n}:a]adelay={d}|{d},volume={vol}[x{n}]")
        labels.append(f"[x{n}]")
        n += 1
    if voices:
        filters.append(f"{''.join(voices)}amix=inputs={len(voices)}:normalize=0,apad[vo]")
    else:
        filters.append("anullsrc=r=44100:cl=stereo[vo]")
    mix = ["[voice]"]
    has_music = os.path.exists(music)
    if not has_music:
        filters.append("[vo]anull[voice]")
    if has_music:
        inputs += ["-stream_loop", "-1", "-i", music]
        filters.append(f"[{n}:a]volume={plan.get('music_volume', 0.32)},afade=t=out:st={max(0, total - 1.5):.2f}:d=1.5[m0]")
        filters.append("[vo]asplit=2[voice][key]")
        filters.append("[m0][key]sidechaincompress=threshold=0.04:ratio=6:attack=20:release=350[music]")
        mix.append("[music]")
        n += 1
    mix += labels
    filters.append(f"{''.join(mix)}amix=inputs={len(mix)}:normalize=0,atrim=0:{total:.3f},loudnorm=I=-14:TP=-1.5:LRA=11[aout]")
    run(["ffmpeg", "-v", "error", "-y", *inputs, "-filter_complex", ";".join(filters), "-map", "0:v", "-map", "[aout]",
         "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-shortest", a.out])
    print("done", a.out, f"{total:.1f}s")


if __name__ == "__main__":
    main()
