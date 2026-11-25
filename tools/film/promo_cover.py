import argparse
import json
import subprocess

import numpy as np
from PIL import Image

from promo import CH, CW, OH, OW, Promo, apply_blur
from reel import place_tags


def grab(path, t):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-ss", f"{t:.3f}", "-i", path, "-frames:v", "1", "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], stdout=subprocess.PIPE, check=True).stdout
    return np.frombuffer(raw, np.uint8).reshape(CH, CW, 3).copy()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--plan", required=True)
    ap.add_argument("--spec", required=True)
    ap.add_argument("--fonts", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    plan = json.load(open(a.plan))
    spec = json.load(open(a.spec))
    p = Promo(a.fonts, plan["sources"])
    frame = p.base(spec.get("bg_t", 3.0), 0)
    for ph in spec["phones"]:
        img = grab(plan["sources"][ph.get("src", "main")], ph["t"])
        img = apply_blur(img, ph["t"], ph.get("blur", []))
        pose = dict(rx=ph.get("rx", 0), ry=ph.get("ry", 0), rz=ph.get("rz", 0), x=ph["x"], y=ph["y"], s=ph["s"])
        p.render_phone(frame, img, pose, ph.get("glare"))
    layer = Image.new("RGBA", (OW, OH), (0, 0, 0, 0))
    p.snow.draw(layer, spec.get("bg_t", 3.0))
    items, _ = p.titles.word_images(spec, spec.get("top", 90))
    layer.alpha_composite(p.text_layer(items, 10.0, 100.0))
    placed = place_tags(p.titles.tag_images(spec.get("tags")), spec.get("tags_y", 1700))
    p.tags(layer, placed, 10.0, 100.0, 0.0)
    p.overlay(frame, layer)
    out = Image.fromarray(np.clip(frame, 0, 255).astype(np.uint8))
    out.save(a.out, quality=93)


if __name__ == "__main__":
    main()
