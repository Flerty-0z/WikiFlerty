import json, os, re, subprocess, urllib.request, urllib.parse
item = os.environ.get("ITEM", "belkintales_1406_librivox")
parts = int(os.environ.get("PARTS", "2"))
os.makedirs("out", exist_ok=True)
def get(u):
    req = urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0 synclab"})
    return urllib.request.urlopen(req, timeout=60).read()
meta = json.loads(get(f"https://archive.org/metadata/{item}"))
files = [f["name"] for f in meta["files"] if f["name"].endswith(".mp3") and "64kb" in f["name"]]
if not files:
    files = [f["name"] for f in meta["files"] if f["name"].endswith(".mp3")]
files.sort()
print("\n".join(f["name"] + " " + str(f.get("length", "")) + " " + str(f.get("title", "")) for f in meta["files"] if f["name"].endswith(".mp3")))
for i, name in enumerate(files[:parts]):
    data = get(f"https://archive.org/download/{item}/" + urllib.parse.quote(name))
    open(f"out/{i:02d}.mp3", "wb").write(data)
    print("got", name, len(data))
cands = [
    "https://ru.wikisource.org/w/index.php?title=" + urllib.parse.quote("Повести покойного Ивана Петровича Белкина (Пушкин)") + "&action=raw",
    "https://ru.wikisource.org/w/index.php?title=" + urllib.parse.quote("Выстрел (Пушкин)") + "&action=raw",
    "https://ru.wikisource.org/w/index.php?title=" + urllib.parse.quote("Повести Белкина (Пушкин)") + "&action=raw",
    "http://lib.ru/LITRA/PUSHKIN/belkin.txt",
    "http://az.lib.ru/p/pushkin_a_s/text_0090.shtml",
]
for u in cands:
    try:
        b = get(u)
        name = re.sub(r"[^a-z0-9]+", "_", u.lower())[-60:]
        open("out/text_" + name + ".txt", "wb").write(b)
        print("text ok", u, len(b))
    except Exception as e:
        print("text fail", u, e)
