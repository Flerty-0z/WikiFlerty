import argparse
import glob
import json
import math
import os
import random
import subprocess

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OW, OH, FPS = 1080, 1920, 30
WHITE = (255, 255, 255)
YELLOW = (255, 225, 72)
INK = (14, 14, 18)
PW = 720
PX = (OW - PW) // 2
PY = 392
RADIUS = 46


def run(cmd):
    subprocess.run(cmd, check=True)


def probe(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path], capture_output=True, text=True).stdout
    return float(out.strip() or 0)


def ease(x):
    x = min(1.0, max(0.0, x))
    return x * x * (3 - 2 * x)


def back_ease(x):
    x = min(1.0, max(0.0, x))
    c = 1.70158
    return 1 + (c + 1) * (x - 1) ** 3 + c * (x - 1) ** 2


def load_font(path, size, weight=None):
    f = ImageFont.truetype(path, size)
    if weight is not None:
        try:
            f.set_variation_by_axes([weight])
        except Exception:
            pass
    return f


class Footage:
    def __init__(self, results, latency):
        path = glob.glob(os.path.join(results, "**", "marks.json"), recursive=True)[0]
        self.marks = json.load(open(path))
        base = os.path.dirname(path)
        parts = sorted(p for p in glob.glob(os.path.join(base, "part*.mp4")) if "_cfr" not in p)
        self.w = self.marks["width"]
        self.h = self.marks["height"]
        density = self.marks.get("density", 2.75)
        starts = self.marks.get("starts") or [0]
        self.offset = self.marks.get("offset", 0) / 1000.0
        self.latency = latency
        self.raw = os.path.join(base, "raw.mp4")
        if not os.path.exists(self.raw):
            fixed = []
            for p in parts:
                f = p.replace(".mp4", "_cfr.mp4")
                if not os.path.exists(f):
                    run(["ffmpeg", "-v", "error", "-y", "-i", p, "-vf", f"fps={FPS}", "-an", "-c:v", "libx264", "-crf", "12", "-preset", "veryfast", "-pix_fmt", "yuv420p", f])
                fixed.append(f)
            lst = os.path.join(base, "parts.txt")
            open(lst, "w").write("".join(f"file '{os.path.abspath(f)}'\n" for f in fixed))
            run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", self.raw])
        self.map = []
        acc = 0.0
        for i, p in enumerate(parts):
            d = probe(p.replace(".mp4", "_cfr.mp4"))
            self.map.append((starts[i] / 1000.0 if i < len(starts) else acc, acc, d))
            acc += d
        self.steps = {s["i"]: s for s in self.marks["steps"]}
        self.names = {s["mark"]: s["i"] for s in self.marks["steps"] if s.get("mark")}
        self.top = int(round(30 * density))
        self.bottom = int(round(18 * density))
        self.ch = self.h - self.top - self.bottom
        self.ph = int(round(PW * self.ch / self.w))

    def idx(self, ref):
        if isinstance(ref, int):
            return ref
        name, _, plus = str(ref).partition("+")
        return (self.names[name] if name in self.names else int(name)) + int(plus or 0)

    def ok(self, ref):
        try:
            return self.steps[self.idx(ref)]["ok"]
        except Exception:
            return False

    def raw_time(self, t):
        for start, at, d in reversed(self.map):
            if t >= start:
                return at + min(d, t - start)
        return t

    def at(self, ref, edge="start"):
        s = self.steps[self.idx(ref)]
        return self.raw_time(self.offset + s[edge] / 1000.0 + self.latency)

    def rect(self, ref):
        s = self.steps.get(self.idx(ref))
        return s.get("rect") if s else None

    def frames(self, t0, dur):
        vf = f"crop={self.w}:{self.ch}:0:{self.top},scale={PW}:{self.ph}:flags=lanczos"
        cmd = ["ffmpeg", "-v", "error", "-ss", f"{max(0, t0):.3f}", "-i", self.raw, "-t", f"{dur + 0.3:.3f}", "-vf", vf, "-f", "rawvideo", "-pix_fmt", "rgb24", "-"]
        p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        size = PW * self.ph * 3
        try:
            while True:
                buf = p.stdout.read(size)
                if len(buf) < size:
                    break
                yield Image.frombuffer("RGB", (PW, self.ph), buf, "raw", "RGB", 0, 1)
        finally:
            p.kill()
            p.wait()


class Snow:
    def __init__(self, n=90, seed=7):
        r = random.Random(seed)
        self.flakes = [(r.random() * OW, r.random() * OH, r.uniform(1.2, 3.6), r.uniform(18, 60), r.uniform(0, 6.28), r.uniform(0.25, 0.8)) for _ in range(n)]

    def draw(self, img, t):
        d = ImageDraw.Draw(img, "RGBA")
        for x, y, s, v, ph, a in self.flakes:
            yy = (y + v * t) % (OH + 20) - 10
            xx = (x + 14 * math.sin(t * 0.6 + ph)) % OW
            d.ellipse([xx - s, yy - s, xx + s, yy + s], fill=(255, 255, 255, int(255 * a * 0.55)))


def background():
    yy = np.linspace(0, 1, OH)[:, None, None]
    xx = np.linspace(0, 1, OW)[None, :, None]
    top = np.array([16, 40, 48])
    mid = np.array([12, 20, 30])
    bot = np.array([22, 16, 30])
    arr = np.where(yy < 0.5, top + (mid - top) * (yy / 0.5), mid + (bot - mid) * ((yy - 0.5) / 0.5))
    arr = arr * np.ones((1, OW, 1))
    glow = np.exp(-(((xx - 0.2) / 0.5) ** 2 + ((yy - 0.1) / 0.35) ** 2)) * np.array([20, 50, 55])
    glow2 = np.exp(-(((xx - 0.85) / 0.45) ** 2 + ((yy - 0.95) / 0.3) ** 2)) * np.array([45, 20, 50])
    arr = arr + glow + glow2
    vig = 1 - 0.35 * (((xx - 0.5) * 1.6) ** 2 + ((yy - 0.5) * 1.2) ** 2)
    arr = arr * np.clip(vig, 0.55, 1)
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))


class Titles:
    def __init__(self, fonts):
        self.title_path = os.path.join(fonts, "Title.ttf")
        self.kicker_path = os.path.join(fonts, "Kicker.ttf")
        self.tag = load_font(self.title_path, 50, 800)

    def title_font(self, size):
        return load_font(self.title_path, size, 900)

    def kicker_font(self, size):
        return load_font(self.kicker_path, size, 500)

    def words(self, text, hl, font, color_default=WHITE):
        out = []
        hl = [h.upper() for h in (hl or [])]
        for w in text.split():
            color = YELLOW if any(w.upper().strip(".,!?«»") == h or h in w.upper() for h in hl) else color_default
            out.append((w, color))
        return out

    def layout(self, beat, width=1000):
        title = beat.get("title", "")
        kicker = beat.get("kicker", "")
        size = beat.get("size", 132)
        f = self.title_font(size)
        while f.getlength(title) > width and size > 72:
            size -= 4
            f = self.title_font(size)
        lines = [title] if f.getlength(title) <= width else self.wrap(title, f, width)
        kf = self.kicker_font(beat.get("kicker_size", 64))
        return f, lines, kf, kicker

    def wrap(self, text, f, width):
        lines, line = [], ""
        for w in text.split():
            trial = (line + " " + w).strip()
            if f.getlength(trial) <= width or not line:
                line = trial
            else:
                lines.append(line)
                line = w
        if line:
            lines.append(line)
        return lines

    def word_images(self, beat, top):
        f, lines, kf, kicker = self.layout(beat)
        hl = beat.get("hl") or []
        items = []
        y = top
        if kicker:
            words = self.words(kicker, [], kf)
            total = sum(kf.getlength(w + " ") for w, _ in words) - kf.getlength(" ")
            x = (OW - total) / 2
            for w, c in words:
                items.append((self.render(w, kf, c, shadow=True), x, y, "kicker"))
                x += kf.getlength(w + " ")
            y += 84
        asc, desc = f.getmetrics()
        lh = int((asc + desc) * 0.92)
        for line in lines:
            words = self.words(line, hl, f)
            total = sum(f.getlength(w + " ") for w, _ in words) - f.getlength(" ")
            x = (OW - total) / 2
            for w, c in words:
                items.append((self.render(w, f, c, shadow=True), x, y, "title"))
                x += f.getlength(w + " ")
            y += lh
        return items, y

    def render(self, text, f, color, shadow=False):
        l, t, r, b = f.getbbox(text)
        pad = 30
        img = Image.new("RGBA", (int(r - l) + pad * 2, int(b - t) + pad * 2), (0, 0, 0, 0))
        if shadow:
            sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
            ImageDraw.Draw(sh).text((pad - l, pad - t + 6), text, font=f, fill=(0, 0, 0, 150))
            img = Image.alpha_composite(img, sh.filter(ImageFilter.GaussianBlur(9)))
        ImageDraw.Draw(img).text((pad - l, pad - t), text, font=f, fill=color + (255,))
        img.info["off"] = (l - pad, t - pad)
        return img

    def tag_images(self, tags):
        out = []
        for i, text in enumerate(tags or []):
            yellow = i % 2 == 1
            f = self.tag
            l, t, r, b = f.getbbox(text.upper())
            w, h = int(r - l) + 44, int(b - t) + 30
            img = Image.new("RGBA", (w + 20, h + 20), (0, 0, 0, 0))
            d = ImageDraw.Draw(img)
            d.rounded_rectangle([10, 14, 10 + w, 14 + h], 14, fill=(0, 0, 0, 90))
            img = img.filter(ImageFilter.GaussianBlur(5))
            d = ImageDraw.Draw(img)
            bg = YELLOW if yellow else (255, 255, 255)
            fg = INK
            d.rounded_rectangle([10, 10, 10 + w, 10 + h], 14, fill=bg + (255,))
            d.text((10 + 22 - l, 10 + 15 - t), text.upper(), font=f, fill=fg + (255,))
            out.append(img)
        return out


def place_tags(imgs, y0, maxw=980):
    rows, row, rw = [], [], 0
    for im in imgs:
        if row and rw + im.width > maxw:
            rows.append(row)
            row, rw = [], 0
        row.append(im)
        rw += im.width - 6
    if row:
        rows.append(row)
    out = []
    y = y0
    for r in rows:
        total = sum(im.width - 6 for im in r)
        x = (OW - total) / 2
        for im in r:
            out.append((im, x, y))
            x += im.width - 6
        y += r[0].height - 4
    return out


def find_color(img, hexcolor, tol=38):
    c = np.array([int(hexcolor[i:i + 2], 16) for i in (1, 3, 5)])
    a = np.asarray(img).astype(int)
    h = a.shape[0]
    m = (np.abs(a - c).sum(axis=2) < tol)
    m[: int(h * 0.1)] = False
    m[int(h * 0.94):] = False
    ys, xs = np.nonzero(m)
    if len(xs) < 6:
        return None
    order = np.argsort(ys)
    y0 = ys[order[0]]
    sel = np.abs(ys - y0) < 40
    return float(np.median(xs[sel])), float(np.median(ys[sel]))


class Cuts:
    X = 6

    def __init__(self, f, beat):
        a, b = f.idx(beat["from"]), f.idx(beat["to"])
        pad0, pad1 = beat.get("pad", [0.25, 0.6])
        t0 = f.at(a) - pad0
        t1 = f.at(b, "end") + pad1
        slow = {f.idx(x) for x in beat.get("slow", []) if not isinstance(x, str) or x.partition("+")[0] in f.names}
        keep = beat.get("keep", 0.6)
        spans = []
        for i in range(a, b + 1):
            if i not in f.steps:
                continue
            tail = beat.get("hold", 1.2) if i in slow else keep
            spans.append([max(t0, f.at(i) - 0.3), min(t1, f.at(i, "end") + tail)])
        spans.sort()
        if spans:
            spans[0][0] = t0
            spans[-1][1] = max(spans[-1][1], t1)
        else:
            spans = [[t0, t1]]
        gap = beat.get("min_gap", 0.7)
        merged = []
        for x, y in spans:
            if merged and x - merged[-1][1] < gap:
                merged[-1][1] = max(merged[-1][1], y)
            else:
                merged.append([x, y])
        self.spans = merged
        self.extend = beat.get("extend", "end")

    @property
    def length(self):
        return sum(y - x for x, y in self.spans) - self.X / FPS * (len(self.spans) - 1)

    def fit(self, dur):
        d = dur - self.length
        if d > 0:
            if self.extend == "start":
                self.spans[0][0] -= d
            else:
                self.spans[-1][1] += d
        elif d < 0:
            cut = -d
            while cut > 1e-6 and self.spans:
                x, y = self.spans[-1]
                if y - x - 0.4 > cut or len(self.spans) == 1:
                    self.spans[-1][1] = y - cut
                    cut = 0
                else:
                    cut -= (y - x) - self.X / FPS
                    self.spans.pop()

    def frames(self, f):
        tail = []
        for k, (x, y) in enumerate(self.spans):
            n = int(round((y - x) * FPS))
            got = []
            for j, img in enumerate(f.frames(x, y - x)):
                if j >= n:
                    break
                got.append((img, x + j / FPS))
            while got and len(got) < n:
                got.append(got[-1])
            start = 0
            if tail:
                for j in range(min(self.X, len(got))):
                    a = (j + 1) / (self.X + 1)
                    yield Image.blend(tail[j][0], got[j][0], a), got[j][1]
                start = min(self.X, len(got))
            last = k == len(self.spans) - 1
            body = got[start:] if last else got[start:len(got) - self.X]
            for item in body:
                yield item
            tail = [] if last else got[len(got) - self.X:]


class Warp:
    def __init__(self, f, beat):
        a, b = f.idx(beat["from"]), f.idx(beat["to"])
        pad0, pad1 = beat.get("pad", [0.25, 0.6])
        self.t0 = f.at(a) - pad0
        self.t1 = f.at(b, "end") + pad1
        slow = {f.idx(x) for x in beat.get("slow", []) if not isinstance(x, str) or x.partition("+")[0] in f.names}
        keep = beat.get("keep", 0.5)
        spans = []
        for i in range(a, b + 1):
            if i not in f.steps:
                continue
            hold = beat.get("hold", 1.2) if i in slow else keep
            spans.append((max(self.t0, f.at(i) - 0.3), min(self.t1, f.at(i, "end") + hold), 1.0 if i in slow else beat.get("speed", 1.6)))
        cuts = sorted({self.t0, self.t1} | {x for s in spans for x in s[:2] if self.t0 <= x <= self.t1})
        gap = beat.get("gap", 5.0)
        self.segs = []
        for x, y in zip(cuts, cuts[1:]):
            m = (x + y) / 2
            rates = [r for s0, s1, r in spans if s0 <= m <= s1]
            r = min(rates) if rates else gap
            self.segs.append([x, y, r])
        self.scale = 1.0
        self.extend = beat.get("extend", "end")
        self.stretch = beat.get("stretch", False)

    @property
    def length(self):
        return sum((y - x) / (r * self.scale) for x, y, r in self.segs)

    def fit(self, dur):
        if self.length <= 0:
            return
        tries = 0
        while not self.stretch and self.length < dur * 0.8 and tries < 20:
            if self.extend == "start":
                x, y, r = self.segs[0]
                self.segs.insert(0, [x - 0.35, x, 1.0])
                self.t0 = x - 0.35
            else:
                x, y, r = self.segs[-1]
                self.segs.append([y, y + 0.35, 1.0])
                self.t1 = y + 0.35
            tries += 1
        self.scale *= self.length / dur

    def src(self, o):
        acc = 0.0
        for x, y, r in self.segs:
            d = (y - x) / (r * self.scale)
            if o <= acc + d:
                return x + (o - acc) * r * self.scale
            acc += d
        return self.t1


class Reel:
    def __init__(self, footage, fonts):
        self.f = footage
        self.titles = Titles(fonts)
        self.bg = background()
        self.snow = Snow()
        self.clock = 0.0
        ph = footage.ph if footage else 1500
        self.mask = Image.new("L", (PW, ph), 0)
        ImageDraw.Draw(self.mask).rounded_rectangle([0, 0, PW - 1, ph - 1], RADIUS, fill=255)
        self.frame_layer = Image.new("RGBA", (OW, OH), (0, 0, 0, 0))
        d = ImageDraw.Draw(self.frame_layer)
        d.rounded_rectangle([PX - 3, PY - 3, PX + PW + 2, PY + ph + 2], RADIUS + 3, outline=(255, 255, 255, 60), width=3)
        sh = Image.new("RGBA", (OW, OH), (0, 0, 0, 0))
        ImageDraw.Draw(sh).rounded_rectangle([PX, PY + 22, PX + PW, PY + ph + 22], RADIUS, fill=(0, 0, 0, 170))
        self.plain = self.bg.copy()
        self.bg = Image.alpha_composite(self.bg.convert("RGBA"), sh.filter(ImageFilter.GaussianBlur(30))).convert("RGB")

    def text_layer(self, items, t, dur, base=None):
        layer = base or Image.new("RGBA", (OW, OH), (0, 0, 0, 0))
        for k, (img, x, y, kind) in enumerate(items):
            start = 0.05 + k * 0.06
            a = ease((t - start) / 0.3)
            out = ease((t - (dur - 0.22)) / 0.2)
            vis = a * (1 - out)
            if vis <= 0.01:
                continue
            blur = (1 - a) * 14 + out * 14
            im = img.filter(ImageFilter.GaussianBlur(blur)) if blur > 0.5 else img
            if vis < 0.99:
                alpha = im.getchannel("A").point(lambda v: int(v * vis))
                im = im.copy()
                im.putalpha(alpha)
            ox, oy = img.info.get("off", (0, 0))
            dy = int((1 - a) * 26)
            layer.alpha_composite(im, (int(x + ox), int(y + oy + dy)))
        return layer

    def tags_layer(self, layer, placed, t, dur, delay=0.55):
        for k, (im, x, y) in enumerate(placed):
            start = delay + k * 0.12
            p = (t - start) / 0.32
            if p <= 0:
                continue
            out = ease((t - (dur - 0.2)) / 0.18)
            s = 0.6 + 0.4 * back_ease(p)
            vis = min(1.0, p * 2) * (1 - out)
            if vis <= 0.01:
                continue
            w, h = max(1, int(im.width * s)), max(1, int(im.height * s))
            sm = im.resize((w, h), Image.BILINEAR)
            if vis < 0.99:
                sm.putalpha(sm.getchannel("A").point(lambda v: int(v * vis)))
            layer.alpha_composite(sm, (int(x + (im.width - w) / 2), int(y + (im.height - h) / 2)))
        return layer

    def zoom(self, img, rect, k):
        if not rect or k <= 0.001:
            return img
        f = self.f
        sx = PW / f.w
        l, t, r, b = rect
        cx = (l + r) / 2 * sx
        cy = ((t + b) / 2 - f.top) * sx
        z = 1 + (k * (self.beat_zoom - 1))
        w, h = PW / z, f.ph / z
        x = min(max(cx - w / 2, 0), PW - w)
        y = min(max(cy - h / 2, 0), f.ph - h)
        return img.crop((int(x), int(y), int(x + w), int(y + h))).resize((PW, f.ph), Image.BICUBIC)

    def zoom_at(self, img, c, z):
        if z <= 1.001:
            return img
        h = self.f.ph
        w2, h2 = PW / z, h / z
        x = min(max(c[0] - w2 / 2, 0), PW - w2)
        y = min(max(c[1] - h2 / 2, 0), h - h2)
        return img.crop((int(x), int(y), int(x + w2), int(y + h2))).resize((PW, h), Image.BICUBIC)

    def compose(self, phone, layer, t_global):
        frame = (self.bg if phone is not None else self.plain).copy()
        if phone is not None:
            frame.paste(phone, (PX, PY), self.mask)
        frame = frame.convert("RGBA")
        if phone is not None:
            frame.alpha_composite(self.frame_layer)
        self.snow.draw(frame, t_global)
        frame.alpha_composite(layer)
        return frame.convert("RGB")

    def beat(self, beat, dur):
        f = self.f
        items, bottom = self.titles.word_images(beat, beat.get("top", 118 if beat.get("kicker") else 190))
        placed = place_tags(self.titles.tag_images(beat.get("tags")), beat.get("tags_y", 1560))
        if beat.get("stretch"):
            w = Warp(f, beat)
            w.fit(dur)
            print(f"  stretch {w.t1 - w.t0:.1f}s x{w.scale:.2f}", flush=True)

            def stream():
                src = f.frames(w.t0, w.t1 - w.t0)
                cur, ft = None, w.t0 - 1 / FPS
                for i in range(int(round(dur * FPS))):
                    st = w.src(i / FPS)
                    while ft + 1 / FPS <= st + 1e-6:
                        nxt = next(src, None)
                        if nxt is None:
                            break
                        cur, ft = nxt, ft + 1 / FPS
                    yield cur, st
                src.close()
            source = stream()
        else:
            c = Cuts(f, beat)
            c.fit(dur)
            print(f"  cuts {[(round(x, 1), round(y, 1)) for x, y in c.spans]}", flush=True)
            source = c.frames(f)
        zoom = beat.get("zoom")
        zrect = f.rect(zoom) if zoom else None
        self.beat_zoom = beat.get("zoom_k", 1.35)
        za = f.at(zoom) - 0.5 if zoom else 0
        zb = f.at(zoom, "end") + beat.get("zoom_hold", 1.5) if zoom else 0
        n = int(round(dur * FPS))
        color = beat.get("zoom_color")
        cbox = None
        last = (None, 0.0)
        for i in range(n):
            t = i / FPS
            item = next(source, None)
            if item is None:
                item = last
            last = item
            cur, st = item
            ph = cur
            if ph is not None and zrect:
                k = ease((st - (za - 0.4)) / 0.4) * (1 - ease((st - zb) / 0.4))
                ph = self.zoom(ph, zrect, k)
            if ph is not None and color:
                if cbox is None and t >= beat.get("color_at", 0.3):
                    cbox = find_color(ph, color) or (PW / 2, f.ph / 2)
                if cbox is not None:
                    k = ease((t - beat.get("color_at", 0.3)) / 0.6)
                    ph = self.zoom_at(ph, cbox, 1 + (beat.get("zoom_k", 2.0) - 1) * k)
            layer = self.text_layer(items, t, dur)
            self.tags_layer(layer, placed, t, dur)
            yield self.compose(ph, layer, self.clock + t)
        self.clock += dur

    def card(self, beat, dur):
        items, _ = self.titles.word_images(dict(beat, size=beat.get("size", 170)), beat.get("top", 760))
        sub = beat.get("sub")
        placed = place_tags(self.titles.tag_images([sub] if sub else []), beat.get("tags_y", 1080))
        n = int(round(dur * FPS))
        for i in range(n):
            t = i / FPS
            layer = self.text_layer(items, t, dur + (0 if beat.get("fade_out", True) else 10))
            self.tags_layer(layer, placed, t, dur + (0 if beat.get("fade_out", True) else 10), delay=0.5)
            yield self.compose(None, layer, self.clock + t)
        self.clock += dur


def encode(path, frames):
    tmp = path.replace(".mp4", ".tmp.mp4")
    enc = subprocess.Popen(
        ["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{OW}x{OH}", "-r", str(FPS), "-i", "-",
         "-c:v", "libx264", "-crf", "18", "-preset", "veryfast", "-pix_fmt", "yuv420p", tmp],
        stdin=subprocess.PIPE,
    )
    n = 0
    for fr in frames:
        enc.stdin.write(fr.tobytes())
        n += 1
    enc.stdin.close()
    enc.wait()
    os.replace(tmp, path)
    return n


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--results", required=True)
    ap.add_argument("--plan", required=True)
    ap.add_argument("--audio", required=True)
    ap.add_argument("--fonts", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--stills", default="")
    ap.add_argument("--only", default="")
    a = ap.parse_args()

    plan = json.load(open(a.plan))
    footage = Footage(a.results, plan.get("latency", 0.3))
    reel = Reel(footage, a.fonts)
    work = os.path.splitext(a.out)[0] + "_work"
    os.makedirs(work, exist_ok=True)
    only = {int(x) for x in a.only.split(",") if x}

    beats = [b for b in plan["beats"] if not b.get("draft_skip")]
    beats = [b for b in beats if b.get("card") or (footage.idx(b["from"]) in footage.steps and footage.ok(b.get("need", b["from"])))]
    skipped = [b.get("title") for b in plan["beats"] if b not in beats and not b.get("draft_skip")]
    if skipped:
        print("skipped (step failed):", skipped)
    files = []
    timeline = []
    t = 0.0
    for k, b in enumerate(beats, 1):
        dur = b.get("dur", 3.2)
        vp = os.path.join(a.audio, f"beat{plan['beats'].index(b) + 1:02d}.mp3")
        if plan.get("voice") and b.get("say") and os.path.exists(vp):
            dur = max(dur, probe(vp) / plan.get("voice_tempo", 1.0) + 0.35)
        if not b.get("card") and not b.get("stretch"):
            natural = Cuts(footage, b).length
            if natural > dur:
                dur = min(natural, dur * 1.35)
        timeline.append((k, b, t, dur))
        t += dur
    for k, b, start, dur in timeline:
        if only and k not in only:
            reel.clock += dur
            continue
        name = os.path.join(work, f"b{k:02d}.mp4")
        gen = reel.card(b, dur) if b.get("card") else reel.beat(b, dur)
        if a.stills:
            os.makedirs(a.stills, exist_ok=True)
            frames = list(gen)
            for q in (0.45, 0.85):
                frames[min(len(frames) - 1, int(len(frames) * q))].save(os.path.join(a.stills, f"b{k:02d}_{int(q * 100)}.jpg"), quality=88)
            gen = iter(frames)
        n = encode(name, gen)
        files.append(name)
        print(f"beat {k} {b.get('title')} {dur:.2f}s frames={n}", flush=True)
    if only:
        return
    lst = os.path.join(work, "beats.txt")
    open(lst, "w").write("".join(f"file '{os.path.abspath(f)}'\n" for f in files))
    video = os.path.join(work, "video.mp4")
    run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", video])
    total = t
    music = os.path.join(a.audio, "music.mp3")
    voices = []
    if plan.get("voice"):
        for k, b, start, dur in timeline:
            p = os.path.join(a.audio, f"beat{plan['beats'].index(b) + 1:02d}.mp3")
            if b.get("say") and os.path.exists(p):
                voices.append((p, start + 0.1))
    inputs = ["-i", video]
    filters = []
    mix = []
    n = 1
    for p, at in voices:
        inputs += ["-i", p]
        d = int(at * 1000)
        filters.append(f"[{n}:a]atempo={plan.get('voice_tempo', 1.0)},adelay={d}|{d},volume={plan.get('voice_volume', 1.0)}[v{n}]")
        mix.append(f"[v{n}]")
        n += 1
    if os.path.exists(music):
        mlen = probe(music)
        if mlen and mlen < total + 1:
            reps = int(total // max(1, mlen - 3)) + 1
            long = os.path.join(work, "music_long.wav")
            ins = []
            for _ in range(reps + 1):
                ins += ["-i", music]
            chain = "[0:a]"
            fc = []
            for r in range(1, reps + 1):
                fc.append(f"{chain}[{r}:a]acrossfade=d=3:c1=tri:c2=tri[x{r}]")
                chain = f"[x{r}]"
            run(["ffmpeg", "-v", "error", "-y", *ins, "-filter_complex", ";".join(fc), "-map", chain, long])
            music = long
        inputs += ["-i", music]
        vol = plan.get("music_volume", 0.55 if not voices else 0.28)
        filters.append(f"[{n}:a]atrim=0:{total:.3f},volume={vol},afade=t=in:d=0.6,afade=t=out:st={max(0, total - 1.8):.2f}:d=1.8[m]")
        mix.append("[m]")
        n += 1
    if not mix:
        run(["ffmpeg", "-v", "error", "-y", "-i", video, "-c", "copy", "-movflags", "+faststart", a.out])
    else:
        filters.append(f"{''.join(mix)}amix=inputs={len(mix)}:normalize=0,atrim=0:{total:.3f},alimiter=limit=0.9[aout]")
        run(["ffmpeg", "-v", "error", "-y", *inputs, "-filter_complex", ";".join(filters), "-map", "0:v", "-map", "[aout]",
             "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-shortest", a.out])
    print("done", a.out, f"{total:.1f}s", "beats", len(beats))


if __name__ == "__main__":
    main()
