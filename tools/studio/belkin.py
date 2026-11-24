import html, re, sys, zipfile
t = open(sys.argv[1], "rb").read().decode("koi8-r", "replace")
t = t.split("<pre>", 2)[-1]
t = re.sub(r"<h2>(.*?)</h2>", lambda m: "\n\n     ##" + re.sub("<[^>]+>", "", m.group(1)) + "\n\n", t)
t = html.unescape(re.sub(r"<[^>]+>", "", t))
paras, cur = [], []
for ln in t.split("\n"):
    if not ln.strip():
        if cur:
            paras.append(" ".join(cur)); cur = []
        continue
    if re.match(r"^\s{3,}\S", ln) and cur:
        paras.append(" ".join(cur)); cur = []
    cur.append(ln.strip())
if cur:
    paras.append(" ".join(cur))
paras = [re.sub(r"\s+", " ", p).strip() for p in paras if re.search(r"\w", p)]
paras = [p for p in paras if not p.startswith("---") and "magister" not in p and "Колесникова" not in p]
body, started = [], False
for p in paras:
    if p.startswith("##ВЫСТРЕЛ"):
        break
    if p.startswith("##"):
        h = p[2:]
        body.append("<h2>" + html.escape(h.capitalize() if h.isupper() else h) + "</h2>"); started = True
    else:
        body.append("<p>" + html.escape(p) + "</p>")
front = ("<h1>Повести покойного Ивана Петровича Белкина</h1>"
         "<p>Все права защищены. Данная электронная книга предназначена исключительно для частного использования в личных (некоммерческих) целях. Электронная книга, ее части, фрагменты и элементы, включая текст, изображения и иное, не подлежат копированию и любому другому использованию без разрешения правообладателя.</p>"
         "<p>Копирование, воспроизведение и иное использование электронной книги, ее частей, фрагментов и элементов, выходящее за пределы частного использования в личных (некоммерческих) целях, без согласия правообладателя является незаконным и влечет уголовную, административную и гражданскую ответственность.</p>")
def page(title, b):
    return f'<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" xml:lang="ru"><head><title>{title}</title></head><body>{b}</body></html>'
opf = ('<?xml version="1.0" encoding="utf-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">'
       '<dc:identifier id="id">wf-belkin</dc:identifier><dc:title>Повести Белкина</dc:title><dc:creator>Пушкин</dc:creator><dc:language>ru</dc:language><meta property="dcterms:modified">2026-01-01T00:00:00Z</meta></metadata>'
       '<manifest><item id="f" href="f.xhtml" media-type="application/xhtml+xml"/><item id="c" href="c.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/></manifest><spine><itemref idref="f"/><itemref idref="c"/></spine></package>')
nav = '<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>nav</title></head><body><nav epub:type="toc"><ol><li><a href="f.xhtml">Титул</a></li><li><a href="c.xhtml">От издателя</a></li></ol></nav></body></html>'
with zipfile.ZipFile(sys.argv[2], "w") as z:
    z.writestr(zipfile.ZipInfo("mimetype"), "application/epub+zip")
    z.writestr("META-INF/container.xml", '<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="o.opf" media-type="application/oebps-package+xml"/></rootfiles></container>', zipfile.ZIP_DEFLATED)
    z.writestr("o.opf", opf, zipfile.ZIP_DEFLATED)
    z.writestr("f.xhtml", page("Титул", front), zipfile.ZIP_DEFLATED)
    z.writestr("c.xhtml", page("От издателя", "".join(body)), zipfile.ZIP_DEFLATED)
    z.writestr("nav.xhtml", nav, zipfile.ZIP_DEFLATED)
print("belkin paragraphs", len(body))
