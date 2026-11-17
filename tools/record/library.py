import html
import io
import os
import random
import re
import sys
import urllib.request
import zipfile
from html.parser import HTMLParser

from PIL import Image, ImageDraw, ImageFilter, ImageFont

BOOKS = [
    ("p/pushkin_a_s/text_0430.shtml", "Капитанская дочка", "Александр Пушкин", ("#1d3b2a", "#0b1a12", "#e7d7a8")),
    ("l/lermontow_m_j/text_0410.shtml", "Герой нашего времени", "Михаил Лермонтов", ("#1c2742", "#0a0f1e", "#d9e2f2")),
    ("d/dostoewskij_f_m/text_0230.shtml", "Белые ночи", "Фёдор Достоевский", ("#e9e4da", "#b9b2a4", "#1f2430")),
    ("g/gogolx_n_w/text_0050.shtml", "Вий", "Николай Гоголь", ("#3a1020", "#14050b", "#f0d6b0")),
    ("t/turgenew_i_s/text_0040.shtml", "Отцы и дети", "Иван Тургенев", ("#2b4a5c", "#0f1d26", "#f2ead8")),
    ("c/chehow_a_p/text_1899_dama_s_sobachkoy.shtml", "Дама с собачкой", "Антон Чехов", ("#5b6e8c", "#26314a", "#fbf6ea")),
    ("k/kuprin_a_i/text_0170.shtml", "Гранатовый браслет", "Александр Куприн", ("#5a0f1c", "#22050b", "#f3d9c2")),
    ("p/pushkin_a_s/text_0426.shtml", "Пиковая дама", "Александр Пушкин", ("#141414", "#000000", "#e8e2d4")),
    ("t/turgenew_i_s/text_0070.shtml", "Муму", "Иван Тургенев", ("#3d5233", "#162014", "#efe9d6")),
    ("b/bulgakow_m_a/text_1924_belaya_gvardia.shtml", "Белая гвардия", "Михаил Булгаков", ("#eef0f2", "#c9ced6", "#1b2233")),
]

FONTS = sys.argv[2] if len(sys.argv) > 2 else "fonts"


def fetch(path):
    req = urllib.request.Request("http://az.lib.ru/" + path, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=90) as r:
        return r.read().decode("cp1251", "replace")


def content(page):
    start = page.find("Собственно произведение")
    start = page.find("-->", start) + 3 if start >= 0 else 0
    end = page.find("<!-----", start)
    body = page[start:end if end > 0 else len(page)]
    body = re.sub(r"(?is)<h4>.*?</h4>", " ", body)
    body = re.sub(r"(?is)<script.*?</script>|<!--.*?-->|<form.*?</form>", " ", body)
    return body


class Blocks(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.out = []
        self.buf = []
        self.head = 0
        self.italic = 0

    def flush(self):
        text = re.sub(r"\s+", " ", "".join(self.buf)).strip()
        self.buf = []
        if text:
            self.out.append(("h" if self.head else "p", text))

    def handle_starttag(self, tag, attrs):
        if tag in ("p", "dd", "div", "br", "li", "tr", "blockquote", "center"):
            self.flush()
        if tag in ("h1", "h2", "h3"):
            self.flush()
            self.head += 1
        if tag in ("i", "em"):
            self.buf.append("\u0001")

    def handle_endtag(self, tag):
        if tag in ("h1", "h2", "h3"):
            self.flush()
            self.head = max(0, self.head - 1)
        elif tag in ("p", "div", "dd", "li", "center"):
            self.flush()
        if tag in ("i", "em"):
            self.buf.append("\u0002")

    def handle_data(self, data):
        self.buf.append(data)


def esc(s):
    s = html.escape(s, quote=False)
    s = s.replace("\u0001", "<i>").replace("\u0002", "</i>")
    opened = s.count("<i>") - s.count("</i>")
    return s + "</i>" * max(0, opened)


def chapters(blocks):
    out, cur = [], None
    for kind, text in blocks:
        plain = text.replace("\u0001", "").replace("\u0002", "")
        heading = kind == "h" or (len(plain) < 40 and re.fullmatch(r"(Глава|ГЛАВА)\s+[\wIVXLC]+\.?.*|[IVXLC]{1,6}\.?", plain))
        if heading and len(plain) < 120:
            if cur and cur[1]:
                out.append(cur)
            cur = [plain, []]
        else:
            if cur is None:
                cur = ["", []]
            cur[1].append(text)
    if cur and cur[1]:
        out.append(cur)
    merged = []
    for title, paras in out:
        if merged and len(paras) < 3 and not merged[-1][1]:
            merged[-1][1].extend(paras)
        else:
            merged.append([title, paras])
    return [c for c in merged if c[1]]


def font(name, size, weight=None):
    f = ImageFont.truetype(os.path.join(FONTS, name), size)
    if weight:
        try:
            f.set_variation_by_axes([weight])
        except Exception:
            pass
    return f


def wrap(draw, text, f, width):
    words, lines, line = text.split(), [], ""
    for w in words:
        t = (line + " " + w).strip()
        if draw.textlength(t, font=f) <= width:
            line = t
        else:
            if line:
                lines.append(line)
            line = w
    if line:
        lines.append(line)
    return lines


def cover(title, author, palette, seed):
    rnd = random.Random(seed)
    w, h = 1200, 1800
    top, bottom, ink = palette
    img = Image.new("RGB", (w, h))
    t, b = Image.new("RGB", (1, 2)), None
    grad = Image.linear_gradient("L").resize((w, h))
    a = Image.new("RGB", (w, h), top)
    z = Image.new("RGB", (w, h), bottom)
    img = Image.composite(z, a, grad)
    noise = Image.effect_noise((w, h), 18).convert("RGB")
    img = Image.blend(img, noise, 0.05)
    d = ImageDraw.Draw(img)
    m = 70
    d.rectangle([m, m, w - m, h - m], outline=ink, width=4)
    d.rectangle([m + 18, m + 18, w - m - 18, h - m - 18], outline=ink, width=1)
    fa = font("Oswald.ttf", 54, 500)
    name = author.upper()
    tw = d.textlength(name, font=fa)
    d.text(((w - tw) / 2, 230), name, font=fa, fill=ink)
    size = 150 if len(title) < 12 else 120 if len(title) < 20 else 100
    ft = font("Playfair.ttf", size, 700)
    lines = wrap(d, title, ft, w - 2 * m - 120)
    lh = int(size * 1.15)
    y = (h - lh * len(lines)) / 2 - 40
    for line in lines:
        lw = d.textlength(line, font=ft)
        d.text(((w - lw) / 2, y), line, font=ft, fill=ink)
        y += lh
    cy = y + 70
    d.line([(w / 2 - 140, cy), (w / 2 - 30, cy)], fill=ink, width=3)
    d.line([(w / 2 + 30, cy), (w / 2 + 140, cy)], fill=ink, width=3)
    d.regular_polygon((w / 2, cy, 14), 4, fill=ink)
    for _ in range(rnd.randint(3, 6)):
        r = rnd.randint(80, 260)
        x, yy = rnd.randint(-100, w + 100), rnd.randint(h - 600, h + 100)
        glow = Image.new("L", (w, h), 0)
        ImageDraw.Draw(glow).ellipse([x - r, yy - r, x + r, yy + r], fill=40)
        glow = glow.filter(ImageFilter.GaussianBlur(60))
        img = Image.composite(Image.new("RGB", (w, h), ink), img, glow)
    d = ImageDraw.Draw(img)
    fy = font("Oswald.ttf", 34, 400)
    label = "КЛАССИКА"
    lw = d.textlength(label, font=fy)
    d.text(((w - lw) / 2, h - m - 120), label, font=fy, fill=ink)
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=90)
    return buf.getvalue()


def epub(path, title, author, chaps, jpg, uid):
    with zipfile.ZipFile(path, "w") as z:
        z.writestr(zipfile.ZipInfo("mimetype"), "application/epub+zip", compress_type=zipfile.ZIP_STORED)
        z.writestr("META-INF/container.xml", '<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>', compress_type=zipfile.ZIP_DEFLATED)
        items = ['<item id="cover" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>', '<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>']
        spine, nav = [], []
        for i, (t, paras) in enumerate(chaps):
            name = f"c{i}.xhtml"
            items.append(f'<item id="c{i}" href="{name}" media-type="application/xhtml+xml"/>')
            spine.append(f'<itemref idref="c{i}"/>')
            label = t or (title if i == 0 else f"Часть {i + 1}")
            nav.append(f'<li><a href="{name}">{html.escape(label)}</a></li>')
            head = f"<h2>{html.escape(t)}</h2>" if t else ""
            body = "".join(f"<p>{esc(p)}</p>" for p in paras)
            z.writestr(f"OEBPS/{name}", f'<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml" xml:lang="ru"><head><title>{html.escape(label)}</title></head><body>{head}{body}</body></html>', compress_type=zipfile.ZIP_DEFLATED)
        z.writestr("OEBPS/cover.jpg", jpg)
        z.writestr("OEBPS/nav.xhtml", f'<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>{html.escape(title)}</title></head><body><nav epub:type="toc"><ol>{"".join(nav)}</ol></nav></body></html>', compress_type=zipfile.ZIP_DEFLATED)
        z.writestr("OEBPS/content.opf", f'<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">urn:wf-demo:{uid}</dc:identifier><dc:title>{html.escape(title)}</dc:title><dc:creator>{html.escape(author)}</dc:creator><dc:language>ru</dc:language><meta name="cover" content="cover"/><meta property="dcterms:modified">2026-01-01T00:00:00Z</meta></metadata><manifest>{"".join(items)}</manifest><spine>{"".join(spine)}</spine></package>', compress_type=zipfile.ZIP_DEFLATED)


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    local = os.environ.get("LOCAL_HTML")
    for n, (path, title, author, palette) in enumerate(BOOKS):
        try:
            page = open(local, encoding="cp1251", errors="replace").read() if local else fetch(path)
            p = Blocks()
            p.feed(content(page))
            p.flush()
            chaps = chapters(p.out)
            if not chaps:
                print("empty", title)
                continue
            epub(os.path.join(out, f"{n + 1:02d}.epub"), title, author, chaps, cover(title, author, palette, n), n)
            print("ok", title, len(chaps), sum(len(c[1]) for c in chaps))
        except Exception as e:
            print("fail", title, e)
        if local:
            break


if __name__ == "__main__":
    main()
