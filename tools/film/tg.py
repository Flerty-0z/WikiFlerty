import json
import os
import sys
import urllib.parse
import urllib.request
import uuid

token = os.environ["TELEGRAM_BOT_TOKEN"]
api = f"https://api.telegram.org/bot{token}/"
chat_file = sys.argv[1]


def get(method, **params):
    with urllib.request.urlopen(api + method + "?" + urllib.parse.urlencode(params), timeout=60) as r:
        return json.loads(r.read())


chat = open(chat_file).read().strip() if os.path.exists(chat_file) else ""
if not chat:
    for u in reversed(get("getUpdates").get("result", [])):
        m = u.get("message") or u.get("my_chat_member") or {}
        c = (m.get("chat") or {}).get("id")
        if c:
            chat = str(c)
            break
if not chat:
    sys.exit("Нет чата: открой бота и нажми Start")
open(chat_file, "w").write(chat)

if len(sys.argv) < 3 or sys.argv[2] == "ping":
    text = sys.argv[3] if len(sys.argv) > 3 else "Бот WikiFlerty Studio на связи. Сюда будут приходить готовые ролики."
    print("sent", get("sendMessage", chat_id=chat, text=text).get("ok"))
    sys.exit(0)

video = sys.argv[2]
caption = open(sys.argv[3], encoding="utf-8").read() if len(sys.argv) > 3 and os.path.exists(sys.argv[3]) else ""
boundary = uuid.uuid4().hex
parts = []
for k, v in (("chat_id", chat), ("caption", caption[:1000]), ("supports_streaming", "true")):
    parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n".encode())
parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"video\"; filename=\"{os.path.basename(video)}\"\r\nContent-Type: video/mp4\r\n\r\n".encode())
parts.append(open(video, "rb").read())
parts.append(f"\r\n--{boundary}--\r\n".encode())
import time
body = b"".join(parts)
for attempt in range(4):
    try:
        req = urllib.request.Request(api + "sendVideo", data=body, headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
        with urllib.request.urlopen(req, timeout=900) as r:
            print("sent", json.loads(r.read()).get("ok"))
        break
    except Exception as e:
        print("retry", attempt + 1, e)
        time.sleep(20 * (attempt + 1))
else:
    sys.exit("не отправилось")
