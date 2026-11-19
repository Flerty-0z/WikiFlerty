import html
import json
import os
import sys
import time
import urllib.parse
import urllib.request

chat_file, version, notes_file = sys.argv[1], sys.argv[2], sys.argv[3]
repo = os.environ.get("REPO", "Flerty-0z/WikiFlerty")
token = os.environ["TELEGRAM_BOT_TOKEN"]
api = f"https://api.telegram.org/bot{token}/"

chat = open(chat_file).read().strip() if os.path.exists(chat_file) else ""
if not chat:
    sys.exit("Нет чата: открой бота и нажми Start")

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
keyboard = {
    "inline_keyboard": [
        [{"text": "Скачать x32", "url": f"{apk}-x32.apk", "style": "primary"}, {"text": "Скачать x64", "url": f"{apk}-x64.apk", "style": "success"}],
        [{"text": "Открыть на GitHub", "url": url, "style": "danger"}],
    ]
}
params = {
    "chat_id": chat,
    "text": text,
    "parse_mode": "HTML",
    "link_preview_options": json.dumps({"is_disabled": True}),
    "reply_markup": json.dumps(keyboard, ensure_ascii=False),
}
data = urllib.parse.urlencode(params).encode()
for attempt in range(4):
    try:
        with urllib.request.urlopen(urllib.request.Request(api + "sendMessage", data=data), timeout=60) as r:
            print("sent", json.loads(r.read()).get("ok"))
        break
    except Exception as e:
        print("retry", attempt + 1, e)
        time.sleep(10 * (attempt + 1))
else:
    sys.exit("не отправилось")
