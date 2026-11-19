import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

target, message_id, version = sys.argv[1], sys.argv[2], sys.argv[3]
repo = os.environ.get("REPO", "Flerty-0z/WikiFlerty")
api = f"https://api.telegram.org/bot{os.environ['TELEGRAM_BOT_TOKEN']}/"


def call(method, params=None):
    req = urllib.request.Request(api + method, data=urllib.parse.urlencode(params or {}).encode())
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read())
    except urllib.error.HTTPError as e:
        return json.loads(e.read() or b"{}")


me = call("getMe")["result"]["username"]
ids = [s["custom_emoji_id"] for s in call("getStickerSet", {"name": f"wikiflerty_icons_by_{me}"}).get("result", {}).get("stickers", [])] or [None, None]
url = f"https://github.com/{repo}/releases/tag/v{version}"
apk = f"https://github.com/{repo}/releases/download/v{version}/WikiFlerty-{version}"


def button(text, link, style, icon):
    b = {"text": text, "url": link}
    if style:
        b["style"] = style
    if icon:
        b["icon_custom_emoji_id"] = icon
    return b


markup = {"inline_keyboard": [
    [button("Скачать x32", f"{apk}-x32.apk", None, ids[0]), button("Скачать x64", f"{apk}-x64.apk", None, ids[0])],
    [button("Открыть на GitHub", url, "danger", ids[1])],
]}
r = call("editMessageReplyMarkup", {"chat_id": target, "message_id": message_id, "reply_markup": json.dumps(markup, ensure_ascii=False)})
print("edit", r.get("ok"), r.get("description", ""), json.dumps(r.get("result", {}).get("reply_markup", {}), ensure_ascii=False)[:400])
