import html
import io
import os
import struct
import sys
import urllib.parse
import urllib.request
import zipfile

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "record"))
import library  # noqa: E402

OUT = sys.argv[1]
FONTS = sys.argv[2]
library.FONTS = FONTS
os.makedirs(OUT, exist_ok=True)


def get(url, timeout=90):
    req = urllib.request.Request(url, headers={"User-Agent": "WikiFlertyStudio/1.0 (demo builder)"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def book(path):
    p = library.Blocks()
    p.feed(library.content(get("http://az.lib.ru/" + path).decode("cp1251", "replace")))
    p.flush()
    blocks = [b for b in p.out if not ("http" in b[1] or "//" in b[1])]
    return library.chapters(blocks)


def plain(t):
    return t.replace("\u0001", "").replace("\u0002", "")


def mobi(path, title, author, chaps, cover):
    body = "".join(
        ("<mbp:pagebreak/>" if i else "") + (f"<h2>{html.escape(t)}</h2>" if t else "") + "".join(f"<p>{library.esc(p)}</p>" for p in paras)
        for i, (t, paras) in enumerate(chaps)
    )
    raw = f"<html><head></head><body>{body}</body></html>".encode("utf-8")
    recs = [raw[k:k + 4096] for k in range(0, len(raw), 4096)]
    first_img = 1 + len(recs)

    def exth(items):
        b = b"".join(struct.pack(">II", t, 8 + len(v)) + v for t, v in items)
        pad = (4 - len(b) % 4) % 4
        return b"EXTH" + struct.pack(">II", 12 + len(b), len(items)) + b + b"\0" * pad

    ex = exth([(100, author.encode()), (503, title.encode()), (524, b"ru"), (201, struct.pack(">I", 0))])
    name = title.encode()
    m = bytearray(232)
    m[0:4] = b"MOBI"
    struct.pack_into(">IIII", m, 4, 232, 2, 65001, 4242)
    struct.pack_into(">I", m, 20, 6)
    struct.pack_into(">I", m, 64, first_img)
    struct.pack_into(">II", m, 68, 16 + 232 + len(ex), len(name))
    struct.pack_into(">I", m, 76, 1049)
    struct.pack_into(">I", m, 88, 6)
    struct.pack_into(">I", m, 92, first_img)
    struct.pack_into(">I", m, 112, 0x40)
    r0 = struct.pack(">HHIHHHH", 1, 0, len(raw), len(recs), 4096, 0, 0) + bytes(m) + ex + name + b"\0\0"
    allr = [r0] + recs + [cover, b"\xe9\x8e\r\n"]
    hdr = bytearray(78)
    short = title.encode("ascii", "ignore")[:31] or b"book"
    hdr[0:len(short)] = short
    hdr[60:68] = b"BOOKMOBI"
    struct.pack_into(">H", hdr, 76, len(allr))
    out = bytearray(hdr)
    off = 78 + 8 * len(allr) + 2
    for i, r in enumerate(allr):
        out += struct.pack(">II", off, i * 2)
        off += len(r)
    out += b"\0\0"
    for r in allr:
        out += r
    open(path, "wb").write(out)


def docx(path, title, author, chaps):
    W = 'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"'

    def run(text, extra=""):
        return f'<w:r>{extra}<w:t xml:space="preserve">{html.escape(plain(text))}</w:t></w:r>'

    paras = [f'<w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr>{run(title)}</w:p>', f'<w:p><w:pPr><w:jc w:val="center"/></w:pPr>{run(author)}</w:p>']
    note_done = False
    for t, ps in chaps:
        if t:
            paras.append(f'<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr>{run(t)}</w:p>')
        for p in ps:
            if not note_done and len(plain(p)) > 120:
                paras.append(f'<w:p>{run(p)}<w:r><w:rPr><w:vertAlign w:val="superscript"/></w:rPr><w:footnoteReference w:id="1"/></w:r></w:p>')
                note_done = True
            else:
                paras.append(f"<w:p>{run(p)}</w:p>")
    document = f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document {W}><w:body>{"".join(paras)}</w:body></w:document>'
    styles = f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles {W}><w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/></w:style><w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:pPr><w:outlineLvl w:val="0"/></w:pPr></w:style></w:styles>'
    notes = f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:footnotes {W}><w:footnote w:type="separator" w:id="-1"><w:p/></w:footnote><w:footnote w:id="1"><w:p><w:r><w:footnoteRef/></w:r>{run(" Сноска из документа Word: нажми на номер в тексте — и пояснение откроется прямо поверх страницы.")}</w:p></w:footnote></w:footnotes>'
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/word/footnotes.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml"/><Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/></Types>')
        z.writestr("_rels/.rels", '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/></Relationships>')
        z.writestr("word/_rels/document.xml.rels", '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footnotes" Target="footnotes.xml"/></Relationships>')
        z.writestr("word/document.xml", document)
        z.writestr("word/styles.xml", styles)
        z.writestr("word/footnotes.xml", notes)
        z.writestr("docProps/core.xml", f'<?xml version="1.0" encoding="UTF-8"?><cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>{html.escape(title)}</dc:title><dc:creator>{html.escape(author)}</dc:creator></cp:coreProperties>')


def rtf(path, title, author, chaps):
    def enc(s):
        return "".join(c if ord(c) < 128 and c not in "\\{}" else ("\\" + c if c in "\\{}" else f"\\u{ord(c) if ord(c) < 32768 else ord(c) - 65536}?") for c in plain(s))

    parts = [r"{\rtf1\ansi\ansicpg1251\uc1{\info{\title " + enc(title) + r"}{\author " + enc(author) + r"}}", r"{\stylesheet{\s0 Normal;}{\s1\outlinelevel0 heading 1;}}"]
    for t, ps in chaps:
        if t:
            parts.append(r"\pard\s1\outlinelevel0\qc\b " + enc(t) + r"\b0\par")
        for p in ps:
            parts.append(r"\pard\s0\qj " + enc(p) + r"\par")
    parts.append("}")
    open(path, "w", encoding="ascii").write("\n".join(parts))


NEMO = [
    "Little Nemo 1905-10-15.jpg", "Little Nemo 1905-10-22.jpg", "Little Nemo 1905-10-29.jpg", "Little Nemo 1905-11-05.jpg",
    "Little Nemo 1905-11-26.jpg", "Little Nemo 1906-01-07.jpg", "Little Nemo 1906-02-11.jpg", "Little Nemo 1906-07-22.jpg",
    "Little Nemo 1906-09-30.jpg", "Little Nemo 1907-02-10.jpg", "Little Nemo 1907-03-10.jpg", "Little Nemo 1908-07-26.jpg",
]


def comic_pages():
    pages = []
    for name in NEMO:
        if len(pages) >= 8:
            break
        try:
            url = "https://commons.wikimedia.org/wiki/Special:FilePath/" + urllib.parse.quote(name.replace(" ", "_")) + "?width=1400"
            data = get(url, 60)
            if data[:2] == b"\xff\xd8" and len(data) > 50_000:
                pages.append(data)
                print("comic page", name)
        except Exception as e:
            print("comic miss", name, e)
    return pages


def fake_pages():
    from PIL import Image, ImageDraw
    import random
    pages = []
    for n in range(6):
        rnd = random.Random(n)
        img = Image.new("RGB", (1200, 1800), (245, 240, 228))
        d = ImageDraw.Draw(img)
        y = 40
        while y < 1700:
            h = rnd.randint(380, 560)
            x = 40
            cols = rnd.choice([1, 2, 2, 3])
            w = (1120 - (cols - 1) * 30) // cols
            for c in range(cols):
                col = tuple(rnd.randint(60, 200) for _ in range(3))
                d.rectangle([x, y, x + w, min(y + h, 1760)], fill=col, outline=(20, 20, 20), width=6)
                d.ellipse([x + 30, y + 30, x + 30 + w // 2, y + 120], fill=(255, 255, 255), outline=(20, 20, 20), width=4)
                x += w + 30
            y += h + 30
        buf = io.BytesIO()
        img.save(buf, "JPEG", quality=88)
        pages.append(buf.getvalue())
    return pages


def comic_zip(path):
    pages = comic_pages()
    if len(pages) < 3:
        pages = fake_pages()
    cbz = io.BytesIO()
    with zipfile.ZipFile(cbz, "w", zipfile.ZIP_STORED) as z:
        for i, p in enumerate(pages, 1):
            z.writestr(f"nemo-{i:03d}.jpg", p)
    fbd = (
        '<?xml version="1.0" encoding="utf-8"?><FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"><description><title-info>'
        "<genre>comics</genre><author><first-name>Уинзор</first-name><last-name>Маккей</last-name></author>"
        "<book-title>Маленький Немо в Стране Снов</book-title><annotation><p>Классика комиксов: мальчик Немо каждую ночь попадает в волшебную Страну Снов.</p></annotation>"
        '<date>1905</date><lang>ru</lang><sequence name="Маленький Немо" number="1"/></title-info></description></FictionBook>'
    )
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("fli000001.cbz", cbz.getvalue())
        z.writestr("fli000001.fbd", fbd)


def main():
    made = []
    try:
        chaps = book("p/pushkin_a_s/text_0426.shtml")
        jpg = library.cover("Пиковая дама", "Александр Пушкин", ("#141414", "#000000", "#e8e2d4"), 7)
        mobi(os.path.join(OUT, "Пиковая дама.mobi"), "Пиковая дама", "Александр Пушкин", chaps, jpg)
        made.append("mobi")
    except Exception as e:
        print("mobi fail", e)
    try:
        chaps = book("c/chehow_a_p/text_1899_dama_s_sobachkoy.shtml")
        docx(os.path.join(OUT, "Дама с собачкой.docx"), "Дама с собачкой", "Антон Чехов", chaps)
        made.append("docx")
    except Exception as e:
        print("docx fail", e)
    try:
        chaps = book("t/turgenew_i_s/text_0110.shtml")
        rtf(os.path.join(OUT, "Ася.rtf"), "Ася", "Иван Тургенев", chaps)
        made.append("rtf")
    except Exception as e:
        print("rtf fail", e)
    try:
        comic_zip(os.path.join(OUT, "Mackay_Malenkij-Nemo.zip"))
        made.append("comic")
    except Exception as e:
        print("comic fail", e)
    font = os.path.join(FONTS, "Lobster-Regular.ttf")
    if os.path.exists(font):
        import shutil
        shutil.copy(font, os.path.join(OUT, "Lobster-Regular.ttf"))
        made.append("font")
    print("made", made)


if __name__ == "__main__":
    main()
