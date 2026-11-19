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
apk_dir = sys.argv[4] if len(sys.argv) > 4 else ""
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
            fname = os.path.basename(path) if path.endswith(".apk") else k + ".png"
            ctype = "application/vnd.android.package-archive" if path.endswith(".apk") else "image/png"
            parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{k}\"; filename=\"{fname}\"\r\nContent-Type: {ctype}\r\n\r\n".encode() + open(path, "rb").read() + b"\r\n")
        parts.append(f"--{boundary}--\r\n".encode())
        req = urllib.request.Request(api + method, data=b"".join(parts), headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
    else:
        req = urllib.request.Request(api + method, data=urllib.parse.urlencode(params or {}).encode())
    try:
        with urllib.request.urlopen(req, timeout=300 if files else 60) as r:
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


def animated():
    here = os.path.dirname(os.path.abspath(__file__))
    path = os.path.join(here, "packs.txt")
    found = {}
    if not os.path.exists(path):
        return found
    for name in [x.strip() for x in open(path).read().split() if x.strip()]:
        for st in call("getStickerSet", {"name": name}).get("result", {}).get("stickers", []):
            key = (st.get("emoji") or "").replace("\ufe0f", "")
            if key:
                found.setdefault(key, []).append(st["custom_emoji_id"])
    return found


try:
    emoji_ids = animated()
except Exception as e:
    print("emoji", e)
    emoji_ids = {}


def emoji(char, pick_any=False):
    ids = emoji_ids.get(char.replace("\ufe0f", ""))
    if not ids:
        return html.escape(char)
    return f'<tg-emoji emoji-id="{ids[0]}">{html.escape(char)}</tg-emoji>'


def line(item):
    head, sep, rest = item.partition(" ")
    if sep and not head.startswith("—") and head.replace("\ufe0f", "") in emoji_ids:
        return f"{emoji(head)} {html.escape(rest)}"
    return html.escape(item)


app_icon, gh_icon = icons()
logo = f'<tg-emoji emoji-id="{app_icon}">\U0001F4D6</tg-emoji>' if app_icon else ""
contact_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), "contact.txt")
contact = open(contact_file, encoding="utf-8").read().strip() if os.path.exists(contact_file) else "Если что-то не так, пишите @F1erty0z"
body = "\n\n".join(line(i) for i in items)
text = (
    f"<b>Ребята, вышло обновление {version}</b>\u00a0{logo}\n\n"
    f"<blockquote>{body}</blockquote>\n\n"
    f"<blockquote>Огромная просьба перед обновлением сделать «Резервную копию» {emoji('🛡')}\n\n"
    f"{contact}</blockquote>"
)


def keyboard(links=None):
    links = links or {}
    return {
        "inline_keyboard": [
            [{"text": "Скачать x32", "url": links.get("x32") or f"{apk}-x32.apk"}, {"text": "Скачать x64", "url": links.get("x64") or f"{apk}-x64.apk"}],
            [{"text": "Открыть на GitHub", "url": url, "style": "danger"}],
        ]
    }


def send(target):
    params = {
        "chat_id": target,
        "text": text,
        "parse_mode": "HTML",
        "link_preview_options": json.dumps({"is_disabled": True}),
        "reply_markup": json.dumps(keyboard(), ensure_ascii=False),
    }
    for attempt in range(4):
        try:
            r = call("sendMessage", params)
            print("sent", target, r.get("ok"), r.get("description", ""))
            if r.get("ok"):
                return r["result"]["message_id"]
        except Exception as e:
            print("retry", attempt + 1, e)
        time.sleep(10 * (attempt + 1))
    return None


CAPTIONS = {"x64": "x64 — для большинства телефонов", "x32": "x32 — для старых телефонов"}
for arch in ("x64", "x32"):
    path = os.path.join(apk_dir, f"WikiFlerty-{version}-{arch}.apk") if apk_dir else ""
    if not path or not os.path.exists(path):
        continue
    if os.path.getsize(path) > 50 * 1048576:
        print("too big", arch, os.path.getsize(path))
        continue
    for attempt in range(3):
        try:
            r = call("sendDocument", {"chat_id": chat, "caption": f"WikiFlerty {version} · {CAPTIONS[arch]}"}, {"document": path})
            print("file", arch, r.get("ok"), r.get("description", ""))
            if r.get("ok"):
                break
        except Exception as e:
            print("file retry", arch, e)
        time.sleep(5)

if not send(chat):
    sys.exit("не отправилось")

channel_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), "channel.txt")
channel = open(channel_file).read().strip() if os.path.exists(channel_file) else ""
wait = int(os.environ.get("WAIT_MINUTES") or "180")
if channel and wait > 0:
    pending = call("getUpdates", {"offset": -1, "timeout": 0}).get("result", [])
    offset = pending[-1]["update_id"] + 1 if pending else 0
    mark = f"обновление {version}"
    deadline = time.time() + wait * 60
    post_id = None
    files = {}
    shown = None
    while time.time() < deadline and not (post_id and len(files) == 2 and shown == files):
        r = call("getUpdates", {"offset": offset, "timeout": 50, "allowed_updates": json.dumps(["channel_post"])})
        for u in r.get("result", []):
            offset = u["update_id"] + 1
            post = u.get("channel_post")
            if not post or str(post["chat"]["id"]) != channel:
                continue
            name = (post.get("document") or {}).get("file_name", "")
            for arch in ("x64", "x32"):
                if name == f"WikiFlerty-{version}-{arch}.apk":
                    user = post["chat"].get("username")
                    files[arch] = f"https://t.me/{user}/{post['message_id']}" if user else f"https://t.me/c/{str(channel)[4:]}/{post['message_id']}"
            if post_id is None and mark in (post.get("text") or "") and not post.get("reply_markup"):
                post_id = post["message_id"]
        if post_id and shown != files:
            e = call("editMessageReplyMarkup", {"chat_id": channel, "message_id": post_id, "reply_markup": json.dumps(keyboard(dict(files)), ensure_ascii=False)})
            print("attach", e.get("ok"), sorted(files), e.get("description", ""))
            if e.get("ok"):
                first = shown is None
                shown = dict(files)
                if first:
                    call("sendMessage", {"chat_id": chat, "text": "Кнопки добавлены к посту в канале" + (", скачивание идёт из файлов в канале" if len(files) == 2 else "")})
                elif len(files) == 2:
                    call("sendMessage", {"chat_id": chat, "text": "Кнопки скачивания теперь ведут на файлы в канале"})
        if r.get("ok") is False:
            time.sleep(5)
    attached = shown is not None
    print("attached" if attached else "no post")
