import html
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

chat_file, version, notes_file = sys.argv[1], sys.argv[2], sys.argv[3]
repo = os.environ.get("REPO", "Flerty-0z/WikiFlerty")
token = os.environ["TELEGRAM_BOT_TOKEN"]
api = f"https://api.telegram.org/bot{token}/"

chat = open(chat_file).read().strip() if os.path.exists(chat_file) else ""
if not chat:
    sys.exit("Нет чата: открой бота и нажми Start")


def call(method, params=None, files=None):
    if files:
        boundary = uuid.uuid4().hex
        parts = []
        for k, v in (params or {}).items():
            parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n".encode())
        for k, path in files.items():
            parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{k}\"; filename=\"{k}.png\"\r\nContent-Type: image/png\r\n\r\n".encode() + open(path, "rb").read() + b"\r\n")
        parts.append(f"--{boundary}--\r\n".encode())
        req = urllib.request.Request(api + method, data=b"".join(parts), headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
    else:
        req = urllib.request.Request(api + method, data=urllib.parse.urlencode(params or {}).encode())
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read())
    except urllib.error.HTTPError as e:
        return json.loads(e.read() or b"{}")


def icons():
    try:
        me = call("getMe")["result"]["username"]
        name = f"wikiflerty_icons_by_{me}"
        found = call("getStickerSet", {"name": name})
        if not found.get("ok"):
            here = os.path.join(os.path.dirname(os.path.abspath(__file__)), "emoji")
            made = call("createNewStickerSet", {
                "user_id": chat,
                "name": name,
                "title": "WikiFlerty",
                "sticker_type": "custom_emoji",
                "needs_repainting": "true",
                "stickers": json.dumps([
                    {"sticker": "attach://app", "format": "static", "emoji_list": ["\U0001F4D6"]},
                    {"sticker": "attach://gh", "format": "static", "emoji_list": ["\U0001F419"]},
                ]),
            }, {"app": os.path.join(here, "app.png"), "gh": os.path.join(here, "gh.png")})
            print("set", made.get("ok"), made.get("description", ""))
            found = call("getStickerSet", {"name": name})
        ids = [st["custom_emoji_id"] for st in found["result"]["stickers"]]
        return ids[0], ids[1]
    except Exception as e:
        print("icons", e)
        return None, None


items = [p.strip() for p in open(notes_file, encoding="utf-8").read().strip().split("\n\n") if p.strip()]
url = f"https://github.com/{repo}/releases/tag/v{version}"
apk = f"https://github.com/{repo}/releases/download/v{version}/WikiFlerty-{version}"
body = "\n\n".join(html.escape(i) for i in items)
text = (
    f"<b>Ребята, вышло обновление {version}</b>\n\n"
    f"{body}\n\n"
    "Огромная просьба перед обновлением сделать «Резервную копию»\n\n"
    "Если что-то не так, пишите @F1erty0z"
)
app_icon, gh_icon = icons()


def button(text, link, style, icon):
    b = {"text": text, "url": link, "style": style}
    if icon:
        b["icon_custom_emoji_id"] = icon
    return b


def keyboard(plain=False):
    pick = (lambda i: None) if plain else (lambda i: i)
    return {
        "inline_keyboard": [
            [button("Скачать x32", f"{apk}-x32.apk", "primary", pick(app_icon)), button("Скачать x64", f"{apk}-x64.apk", "success", pick(app_icon))],
            [button("Открыть на GitHub", url, "danger", pick(gh_icon))],
        ]
    }


params = {
    "chat_id": chat,
    "text": text,
    "parse_mode": "HTML",
    "link_preview_options": json.dumps({"is_disabled": True}),
    "reply_markup": json.dumps(keyboard(), ensure_ascii=False),
}
for attempt in range(4):
    try:
        r = call("sendMessage", params)
        if not r.get("ok") and app_icon and "emoji" in r.get("description", "").lower():
            print("icons off", r.get("description"))
            params["reply_markup"] = json.dumps(keyboard(plain=True), ensure_ascii=False)
            r = call("sendMessage", params)
        print("sent", r.get("ok"), r.get("description", ""))
        if r.get("ok"):
            break
    except Exception as e:
        print("retry", attempt + 1, e)
    time.sleep(10 * (attempt + 1))
else:
    sys.exit("не отправилось")
