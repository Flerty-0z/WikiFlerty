import json
import os
import urllib.parse
import urllib.request

owner = os.environ.get("OWNER", "")
api = f"https://api.telegram.org/bot{os.environ['TELEGRAM_BOT_TOKEN']}/"


def call(method, params=None):
    req = urllib.request.Request(api + method, data=urllib.parse.urlencode(params or {}).encode())
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read())


for u in call("getUpdates", {"timeout": 0, "allowed_updates": json.dumps(["message", "my_chat_member", "channel_post"])}).get("result", []):
    m = u.get("message") or u.get("channel_post")
    if m and (str(m["chat"]["id"]) == owner or m["chat"].get("type") == "channel"):
        print("msg", m["chat"]["id"], m["chat"].get("type"), json.dumps(m.get("text") or m.get("caption") or "", ensure_ascii=False))
        for e in m.get("entities", []) or []:
            if e["type"] == "custom_emoji":
                print("  emoji", e["offset"], e["length"], e["custom_emoji_id"])
            elif e["type"] in ("text_link", "url", "mention", "blockquote", "expandable_blockquote", "bold", "italic"):
                print("  ent", e["type"], e["offset"], e["length"], e.get("url", ""))
        if m.get("sticker"):
            print("  sticker set", m["sticker"].get("set_name"), m["sticker"].get("custom_emoji_id"))
        if m["chat"].get("type") == "channel":
            print("  post", m["message_id"], "buttons", json.dumps(m.get("reply_markup", {}), ensure_ascii=False)[:300], "via", (m.get("via_bot") or {}).get("username"))
        f = m.get("forward_origin") or {}
        if f.get("chat"):
            print("  forward from", f["chat"]["id"], f["chat"].get("title"), f["chat"].get("username"))
    c = u.get("my_chat_member")
    if c and c["chat"].get("type") in ("channel", "group", "supergroup"):
        print("member", c["chat"]["id"], c["chat"].get("type"), c["chat"].get("title"), c["chat"].get("username"), c["new_chat_member"]["status"])
