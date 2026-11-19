import json
import os
import urllib.parse
import urllib.request

api = f"https://api.telegram.org/bot{os.environ['TELEGRAM_BOT_TOKEN']}/"
owner = os.environ["OWNER"]
here = os.path.dirname(os.path.abspath(__file__))
channel = open(os.path.join(here, "channel.txt")).read().strip()


def call(method, params=None):
    req = urllib.request.Request(api + method, data=urllib.parse.urlencode(params or {}).encode())
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read())
    except urllib.error.HTTPError as e:
        return json.loads(e.read() or b"{}")


ids = []
for u in call("getUpdates", {"timeout": 0}).get("result", []):
    m = u.get("message")
    if m and str(m["chat"]["id"]) == owner:
        for e in m.get("entities", []) or []:
            if e["type"] == "custom_emoji":
                ids.append(e["custom_emoji_id"])
        if m.get("sticker", {}).get("custom_emoji_id"):
            ids.append(m["sticker"]["custom_emoji_id"])
print("got", len(ids))
packs = {}
if ids:
    for st in call("getCustomEmojiStickers", {"custom_emoji_ids": json.dumps(ids)}).get("result", []):
        packs[st.get("set_name")] = st["custom_emoji_id"]
catalog = {}
for name in packs:
    s = call("getStickerSet", {"name": name}).get("result", {})
    items = [{"e": x.get("emoji"), "id": x["custom_emoji_id"], "a": x.get("is_animated") or x.get("is_video")} for x in s.get("stickers", [])]
    catalog[name] = {"title": s.get("title"), "items": items}
    print("pack", name, s.get("title"), len(items), "".join(x["e"] or "" for x in items))
json.dump(catalog, open("catalog.json", "w"), ensure_ascii=False)
sample = next(iter(packs.values()), None)


def kept(res):
    return any(e.get("type") == "custom_emoji" for e in (res.get("entities") or [])), "icon_custom_emoji_id" in json.dumps(res.get("reply_markup", {})), "style" in json.dumps(res.get("reply_markup", {}))


if sample:
    r = call("sendMessage", {
        "chat_id": owner,
        "text": "проба <tg-emoji emoji-id=\"%s\">\u2b50</tg-emoji> проверка" % sample,
        "parse_mode": "HTML",
        "reply_markup": json.dumps({"inline_keyboard": [[{"text": "проба", "url": "https://github.com", "icon_custom_emoji_id": sample, "style": "danger"}]]}),
    })
    src = r["result"]["message_id"]
    print("probe owner", kept(r["result"]))
    trash = [(owner, src)]
    for how in ("forwardMessage", "copyMessage"):
        f = call(how, {"chat_id": channel, "from_chat_id": owner, "message_id": src})
        res = f.get("result", {})
        mid = res.get("message_id")
        if how == "copyMessage" and mid:
            full = call("forwardMessage", {"chat_id": owner, "from_chat_id": channel, "message_id": mid}).get("result", {})
            print("probe", how, f.get("ok"), f.get("description", ""), kept(full))
            if full.get("message_id"):
                trash.append((owner, full["message_id"]))
        else:
            print("probe", how, f.get("ok"), f.get("description", ""), kept(res))
        if mid:
            trash.append((channel, mid))
    for c, mid in trash:
        call("deleteMessage", {"chat_id": c, "message_id": mid})
