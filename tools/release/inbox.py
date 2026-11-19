import json
import os
import urllib.parse
import urllib.request

api = f"https://api.telegram.org/bot{os.environ['TELEGRAM_BOT_TOKEN']}/"


def call(method, params=None):
    req = urllib.request.Request(api + method, data=urllib.parse.urlencode(params or {}).encode())
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read())


for u in call("getUpdates", {"timeout": 0, "allowed_updates": json.dumps(["message", "my_chat_member", "channel_post"])}).get("result", []):
    m = u.get("message") or u.get("channel_post")
    if m:
        print("msg", m["chat"]["id"], m["chat"].get("type"), (m.get("text") or m.get("caption") or "")[:200])
        for e in m.get("entities", []) or []:
            if e["type"] == "custom_emoji":
                print("  emoji", e["custom_emoji_id"])
        if m.get("sticker"):
            print("  sticker set", m["sticker"].get("set_name"), m["sticker"].get("custom_emoji_id"))
        f = m.get("forward_origin") or {}
        if f.get("chat"):
            print("  forward from", f["chat"]["id"], f["chat"].get("title"), f["chat"].get("username"))
    c = u.get("my_chat_member")
    if c:
        print("member", c["chat"]["id"], c["chat"].get("type"), c["chat"].get("title"), c["chat"].get("username"), c["new_chat_member"]["status"])
