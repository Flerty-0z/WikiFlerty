import argparse
import json
import os

from PIL import Image, ImageDraw, ImageFilter

import reel


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--results", required=True)
    ap.add_argument("--plan", required=True)
    ap.add_argument("--fonts", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    plan = json.load(open(a.plan))
    c = plan.get("cover", {})
    f = reel.Footage(a.results, plan.get("latency", 0.3))
    titles = reel.Titles(a.fonts)

    frame = reel.background().convert("RGBA")
    shot = None
    for img in f.frames(f.at(c.get("shot", "intro")) + c.get("shot_offset", 1.0), 0.2):
        shot = img
        break

    pw = c.get("phone_w", 600)
    if shot is not None:
        ph = int(shot.height * pw / shot.width)
        phone = shot.resize((pw, ph), Image.LANCZOS)
        mask = Image.new("L", (pw, ph), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, pw - 1, ph - 1], 40, fill=255)
        px = (reel.OW - pw) // 2
        py = c.get("phone_y", 760)
        sh = Image.new("RGBA", frame.size, (0, 0, 0, 0))
        ImageDraw.Draw(sh).rounded_rectangle([px, py + 26, px + pw, py + ph + 26], 40, fill=(0, 0, 0, 190))
        frame.alpha_composite(sh.filter(ImageFilter.GaussianBlur(34)))
        frame.paste(phone, (px, py), mask)
        ImageDraw.Draw(frame).rounded_rectangle([px - 3, py - 3, px + pw + 2, py + ph + 2], 43, outline=(255, 255, 255, 70), width=3)

    reel.Snow(140, 11).draw(frame, 3.0)

    shade = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(shade)
    for i in range(336):
        d.line([(0, 1250 + i * 2), (reel.OW, 1250 + i * 2)], fill=(8, 12, 18, min(225, int(i * 1.0))), width=2)
    frame.alpha_composite(shade)

    y = c.get("top", 250)
    for block in c.get("lines", []):
        beat = {"title": block["text"], "hl": block.get("hl", []), "size": block.get("size", 150), "kicker": block.get("kicker", "")}
        items, bottom = titles.word_images(beat, y)
        for img, x, yy, kind in items:
            ox, oy = img.info.get("off", (0, 0))
            frame.alpha_composite(img, (int(x + ox), int(yy + oy)))
        y = bottom + block.get("gap", 6)

    tags = titles.tag_images(c.get("tags", []))
    for im, x, yy in reel.place_tags(tags, c.get("tags_y", 1330), 940):
        frame.alpha_composite(im, (int(x), int(yy)))

    frame.convert("RGB").save(a.out, quality=95)
    print("cover", a.out)


if __name__ == "__main__":
    main()
