import random, sys, zipfile

random.seed(int(sys.argv[3]) if len(sys.argv) > 3 else 1)
words = ("утро ветер дорога город река лес свет окно письмо дом сад зима снег поле небо тишина голос "
         "шаг путь мост огонь ночь звезда берег море остров башня сердце память история книга страница").split()
verbs = "шёл смотрел думал ждал слушал писал читал помнил искал знал видел".split()
adj = "тихий долгий старый светлый холодный тёплый далёкий тёмный новый странный".split()

def sentence():
    n = random.randint(6, 14)
    out = []
    for i in range(n):
        r = random.random()
        out.append(random.choice(verbs if r < 0.2 else adj if r < 0.45 else words))
    s = " ".join(out)
    return s[0].upper() + s[1:] + random.choice([".", ".", ".", "!", "?", "…"])

def para():
    return " ".join(sentence() for _ in range(random.randint(3, 7)))

title = sys.argv[2]
chapters = []
for c in range(1, 9):
    body = "".join(f"<p>{para()}</p>\n" for _ in range(28))
    chapters.append((f"Глава {c}", body))

with zipfile.ZipFile(sys.argv[1], "w") as z:
    z.writestr(zipfile.ZipInfo("mimetype"), "application/epub+zip", compress_type=zipfile.ZIP_STORED)
    z.writestr("META-INF/container.xml", '<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>')
    items = "".join(f'<item id="c{i}" href="c{i}.xhtml" media-type="application/xhtml+xml"/>' for i in range(len(chapters)))
    spine = "".join(f'<itemref idref="c{i}"/>' for i in range(len(chapters)))
    nav = "".join(f'<li><a href="c{i}.xhtml">{t}</a></li>' for i, (t, _) in enumerate(chapters))
    z.writestr("OEBPS/content.opf", f'<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">demo-{title}</dc:identifier><dc:title>{title}</dc:title><dc:creator>Демо Автор</dc:creator><dc:language>ru</dc:language><meta property="dcterms:modified">2026-01-01T00:00:00Z</meta></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>{items}</manifest><spine>{spine}</spine></package>')
    z.writestr("OEBPS/nav.xhtml", f'<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>{title}</title></head><body><nav epub:type="toc"><ol>{nav}</ol></nav></body></html>')
    for i, (t, body) in enumerate(chapters):
        z.writestr(f"OEBPS/c{i}.xhtml", f'<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml" xml:lang="ru"><head><title>{t}</title></head><body><h2>{t}</h2>{body}</body></html>')
