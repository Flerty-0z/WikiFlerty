import re
import sys
import zipfile

src, out = sys.argv[1], sys.argv[2]
zin = zipfile.ZipFile(src)
names = zin.namelist()
zout = zipfile.ZipFile(out, "w")
zout.writestr(zipfile.ZipInfo("mimetype"), "application/epub+zip", compress_type=zipfile.ZIP_STORED)
note = [0]


def para(m):
    inner = m.group(1)
    note[0] += 1
    n = note[0]
    parts = re.split(r"(?<=[.!?…])\s+", inner, maxsplit=2)
    if n % 3 == 0 and len(parts) > 1:
        parts[0] = parts[0] + f'<a class="note" href="#n{n}" id="r{n}"><sup>[{n}]</sup></a>'
    if n % 4 == 0 and len(parts) > 1:
        words = parts[1].split(" ")
        if len(words) > 6:
            words[2] = "<i>" + words[2]
            words[4] = words[4] + "</i>"
            parts[1] = " ".join(words)
    if n % 5 == 0:
        words = parts[0].split(" ", 1)
        if len(words) == 2:
            parts[0] = f"<b>{words[0]}</b> {words[1]}"
    if n % 7 == 0 and len(parts) > 1:
        parts[-1] = f'<span class="em">{parts[-1]}</span>'
    return '<p class="p1">' + " ".join(parts) + "</p>"


for name in names:
    if name == "mimetype":
        continue
    data = zin.read(name)
    if name.endswith(".xhtml") and "nav" not in name:
        text = data.decode("utf-8")
        text = re.sub(r"<h[1-6][^>]*>(.*?)</h[1-6]>", r'<div class="title1"><p class="p">\1</p></div>', text, flags=re.S)
        text = re.sub(r"<p>(.*?)</p>", para, text, flags=re.S)
        text = re.sub(r"<body>(.*)</body>", r'<body class="z"><span id="id1">\1</span></body>', text, flags=re.S)
        data = text.encode("utf-8")
    if name.endswith(".opf"):
        text = data.decode("utf-8")
        text = re.sub(r"<dc:title>.*?</dc:title>", "<dc:title>Проба разметки</dc:title>", text)
        text = text.replace("urn:wf-demo:", "urn:wf-litres-like:")
        data = text.encode("utf-8")
    zout.writestr(name, data, compress_type=zipfile.ZIP_DEFLATED)
zout.close()
print("litres-like", out, note[0])
