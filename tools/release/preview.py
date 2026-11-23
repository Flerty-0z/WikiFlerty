import html
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

chat = open(sys.argv[1]).read().strip()
version = sys.argv[2]
apk_dir = sys.argv[3]
token = os.environ["TELEGRAM_BOT_TOKEN"]
api = f"https://api.telegram.org/bot{token}/"
here = os.path.dirname(os.path.abspath(__file__))
repo = os.environ.get("REPO", "Flerty-0z/WikiFlerty")
release = f"https://github.com/{repo}/releases/tag/v{version}"


def call(method, params=None, files=None):
    if files:
        boundary = uuid.uuid4().hex
        parts = []
        for k, v in (params or {}).items():
            parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n".encode())
        for k, (name, path) in files.items():
            parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{k}\"; filename=\"{name}\"\r\nContent-Type: application/vnd.android.package-archive\r\n\r\n".encode() + open(path, "rb").read() + b"\r\n")
        parts.append(f"--{boundary}--\r\n".encode())
        req = urllib.request.Request(api + method, data=b"".join(parts), headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
    else:
        req = urllib.request.Request(api + method, data=urllib.parse.urlencode(params or {}).encode())
    try:
        with urllib.request.urlopen(req, timeout=600 if files else 60) as r:
            return json.loads(r.read())
    except urllib.error.HTTPError as e:
        return json.loads(e.read() or b"{}")


emoji_ids = {}
for name in [x.strip() for x in open(os.path.join(here, "packs.txt")).read().split() if x.strip()]:
    for st in call("getStickerSet", {"name": name}).get("result", {}).get("stickers", []):
        key = (st.get("emoji") or "").replace("️", "")
        if key:
            emoji_ids.setdefault(key, []).append(st["custom_emoji_id"])

me = call("getMe").get("result", {}).get("username", "")
logo_set = call("getStickerSet", {"name": f"wikiflerty_icons_by_{me}"}).get("result", {}).get("stickers", [])
logo = f'<tg-emoji emoji-id="{logo_set[0]["custom_emoji_id"]}">\U0001F4D6</tg-emoji>' if logo_set else ""


def em(char):
    ids = emoji_ids.get(char.replace("️", ""))
    return f'<tg-emoji emoji-id="{ids[0]}">{html.escape(char)}</tg-emoji>' if ids else html.escape(char)


contact = open(os.path.join(here, "contact.txt"), encoding="utf-8").read().strip()
backup = f"Огромная просьба перед обновлением сделать «Резервную копию» {em('🛡')}"
head = f"<b>Ребята, вышло обновление {version}</b> {logo}"

ITEMS = [
    ("☁️", "Облако", "Тарифы облака", "Облако+ на 5 ГБ за 79 ₽ и Облако++ на 15 ГБ за 149 ₽ в месяц, оплата картой или через СБП прямо в приложении"),
    ("📚", "Облако", "Новый «Аккаунт»", "занятое место показано полкой с книгами, книги в облаке листаются лентой и ищутся по названию"),
    ("👤", "Облако", "Аккаунт при первом запуске", "можно сразу зарегистрироваться или пропустить"),
    ("👀", "Облако", "Счётчик онлайна", "в настройках видно, сколько человек сейчас в приложении"),
    ("⚡️", "Чтение", "Скорочтение", "книга показывается по одному слову с нужной скоростью"),
    ("🗂", "Библиотека", "Полки по папкам", "при автоимпорте подпапки сами становятся полками"),
    ("✏️", "Библиотека", "Правки в файле", "название, автор и описание сохраняются в книгу, когда ей делишься"),
    ("🖼", "Чтение", "Картинки", "открываются удержанием пальца, как в комиксах"),
    ("📄", "Чтение", "PDF", "текст собирается в нормальные абзацы, появились картинки"),
    ("🛠", "Исправления", "Фикс багов", "импорт больших DOCX, загрузка книг в облако, озвучка и панель выделения"),
]


def dash(i):
    e, _, t, d = i
    return f"{em(e)} — {html.escape(t)}: {html.escape(d)}"


def plain(htm):
    import re
    return re.sub(r"<[^>]+>", "", htm)


def buttons():
    apk = f"https://github.com/{repo}/releases/download/v{version}/WikiFlerty-{version}"
    return json.dumps({"inline_keyboard": [
        [{"text": "Скачать x32", "url": f"{apk}-x32.apk"}, {"text": "Скачать x64", "url": f"{apk}-x64.apk"}],
        [{"text": "Открыть на GitHub", "url": release, "style": "danger"}],
    ]}, ensure_ascii=False)


def telegraph():
    acc = json.loads(urllib.request.urlopen("https://api.telegra.ph/createAccount?" + urllib.parse.urlencode({"short_name": "WikiFlerty", "author_name": "WikiFlerty"}), timeout=30).read())
    tok = acc["result"]["access_token"]
    nodes = [{"tag": "p", "children": ["Что нового в WikiFlerty " + version + ". Перед обновлением сделайте «Резервную копию» в настройках."]}]
    groups = {}
    for i in ITEMS:
        groups.setdefault(i[1], []).append(i)
    for g, items in groups.items():
        nodes.append({"tag": "h3", "children": [g]})
        nodes.append({"tag": "ul", "children": [{"tag": "li", "children": [{"tag": "b", "children": [t]}, " — " + d]} for _, _, t, d in items]})
    nodes.append({"tag": "p", "children": [{"tag": "a", "attrs": {"href": release}, "children": ["Скачать на GitHub"]}]})
    page = json.loads(urllib.request.urlopen(urllib.request.Request("https://api.telegra.ph/createPage", data=urllib.parse.urlencode({
        "access_token": tok, "title": f"WikiFlerty {version} — что нового", "author_name": "WikiFlerty",
        "author_url": "https://t.me/wikiflerty", "content": json.dumps(nodes, ensure_ascii=False)}).encode()), timeout=30).read())
    return page["result"]["url"]


try:
    article = telegraph()
except Exception as e:
    print("telegraph", e)
    article = release

files = {}
for arch in ("x64", "x32"):
    path = os.path.join(apk_dir, sorted(x for x in os.listdir(apk_dir) if x.endswith(f"-{arch}.apk"))[0])
    r = call("sendDocument", {"chat_id": chat, "disable_notification": "true"}, {"document": (f"WikiFlerty-{version}-{arch}.apk", path)})
    print("upload", arch, r.get("ok"), r.get("description", ""))
    if r.get("ok"):
        files[arch] = r["result"]["document"]["file_id"]
        call("deleteMessage", {"chat_id": chat, "message_id": r["result"]["message_id"]})


def album(caption):
    order = [("x64", "x64 — для большинства телефонов"), ("x32", "x32 — для старых телефонов")]
    media, upload = [], {}
    for n, (arch, _) in enumerate(order):
        item = {"type": "document"}
        if arch in files:
            item["media"] = files[arch]
        else:
            item["media"] = f"attach://f{arch}"
            upload[f"f{arch}"] = (f"WikiFlerty-{version}-{arch}.apk", os.path.join(apk_dir, sorted(x for x in os.listdir(apk_dir) if x.endswith(f"-{arch}.apk"))[0]))
        if n == len(order) - 1:
            item["caption"] = caption
            item["parse_mode"] = "HTML"
        media.append(item)
    r = call("sendMediaGroup", {"chat_id": chat, "media": json.dumps(media, ensure_ascii=False)}, upload or None)
    if r.get("ok"):
        for (arch, _), m in zip(order, r["result"]):
            files[arch] = m["document"]["file_id"]
    return r


def text(body, markup=True, preview=None):
    p = {"chat_id": chat, "text": body, "parse_mode": "HTML"}
    p["link_preview_options"] = json.dumps(preview or {"is_disabled": True})
    if markup:
        p["reply_markup"] = buttons()
    return call("sendMessage", p)


def fit(build, items):
    items = list(items)
    while items:
        c = build(items)
        if len(plain(c)) <= 1024:
            return c
        items.pop(-2 if len(items) > 1 else -1)
    return build([])


V = []
V.append(("Как сейчас", lambda: text(f"{head}\n\n<blockquote>" + "\n\n".join(dash(i) for i in ITEMS) + f"</blockquote>\n\n<blockquote>{backup}\n\n{contact}</blockquote>")))
V.append(("Файлы с подписью", lambda: album(fit(lambda its: f"{head}\n\n<blockquote>" + "\n\n".join(dash(i) for i in its) + f"</blockquote>\n\n<blockquote>{backup}\n\n{contact}</blockquote>", ITEMS))))
V.append(("Статья + файлы", lambda: album(f"{head}\n\n<blockquote>" + "\n\n".join(dash(i) for i in ITEMS[:3]) + f"</blockquote>\n\n{em('📖')} <a href=\"{article}\">Все изменения — в статье</a>\n\n<blockquote>{backup}\n\n{contact}</blockquote>")))


def sections():
    groups = {}
    for i in ITEMS:
        groups.setdefault(i[1], []).append(i)
    out = [head]
    for g, items in groups.items():
        out.append(f"<b>{html.escape(g)}</b>\n<blockquote>" + "\n".join(f"{em(e)} {html.escape(t)} — {html.escape(d)}" for e, _, t, d in items) + "</blockquote>")
    out.append(f"<blockquote>{backup}\n\n{contact}</blockquote>")
    return "\n\n".join(out)


V.append(("По разделам", lambda: text(sections())))
V.append(("Главное + раскрыть", lambda: album(fit(lambda its: f"{head}\n\n<blockquote>" + "\n\n".join(dash(i) for i in its[:3]) + "</blockquote>\n<blockquote expandable><b>Ещё в обновлении</b>\n\n" + "\n".join(dash(i) for i in its[3:]) + f"</blockquote>\n\n<blockquote>{backup}\n\n{contact}</blockquote>", ITEMS))))
nums = ["1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟"]
V.append(("Нумерованный", lambda: text(f"{head}\n\n" + "\n\n".join(f"{em(nums[k])} <b>{html.escape(t)}</b>\n{html.escape(d[0].upper() + d[1:])}" for k, (_, _, t, d) in enumerate(ITEMS)) + f"\n\n<blockquote>{backup}\n\n{contact}</blockquote>")))
V.append(("Коротко", lambda: album(f"{head}\n\n<blockquote>" + "\n".join(f"{em(e)} {html.escape(t)}" for e, _, t, _ in ITEMS) + f"</blockquote>\n\n{em('📖')} <a href=\"{article}\">Подробнее о каждом пункте</a>\n\n<blockquote>{backup}\n\n{contact}</blockquote>")))
V.append(("Моно", lambda: album(fit(lambda its: f"{head}\n\n<pre>" + "\n".join(html.escape(f"+ {t.lower()}") for _, _, t, _ in its) + f"</pre>\n<blockquote>{backup}\n\n{contact}</blockquote>", ITEMS))))
V.append(("Главная фишка", lambda: text(f"{head}\n\n{em(ITEMS[0][0])} <b>{html.escape(ITEMS[0][2])}</b>\n<blockquote>{html.escape(ITEMS[0][3][0].upper() + ITEMS[0][3][1:])}. Бесплатное место для книг тоже остаётся.</blockquote>\n\n<b>А ещё:</b>\n" + "\n".join(f"— {html.escape(t)}" for _, _, t, _ in ITEMS[1:]) + f"\n\n<blockquote>{backup}\n\n{contact}</blockquote>")))
V.append(("Статья сверху", lambda: text(f"{head}\n\n<blockquote>" + "\n".join(f"{em(e)} {html.escape(t)}" for e, _, t, _ in ITEMS) + f"</blockquote>\n\n<blockquote>{backup}\n\n{contact}</blockquote>\n\n<a href=\"{article}\">Все изменения</a>", preview={"url": article, "prefer_large_media": True, "show_above_text": True})))

call("sendMessage", {"chat_id": chat, "text": f"Варианты оформления анонса · {len(V)} шт. (полный набор, прошлая отправка была неполной)\nТолько для просмотра, версия не публикуется. Файлы — текущая тестовая сборка."})
for n, (title, fn) in enumerate(V, 1):
    call("sendMessage", {"chat_id": chat, "text": f"<b>Вариант {n} · {html.escape(title)}</b>", "parse_mode": "HTML"})
    for attempt in range(3):
        r = fn()
        print(n, title, r.get("ok"), r.get("description", ""))
        if r.get("ok"):
            break
        time.sleep(5)
    time.sleep(2)
