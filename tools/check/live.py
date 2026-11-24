import hashlib, json, secrets, string, time, urllib.request, urllib.error
B = "https://sync.wikiflerty.ru/v1/"
tok = None
def call(path, body=None, method="POST", raw=None, url=None):
    data = raw if raw is not None else json.dumps(body or {}).encode()
    req = urllib.request.Request(url or B + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if tok:
        req.add_header("Authorization", "Bearer " + tok)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            t = r.read()
            return r.status, (json.loads(t) if t[:1] in (b"{", b"[") else t)
    except urllib.error.HTTPError as e:
        t = e.read()
        try:
            return e.code, json.loads(t)
        except Exception:
            return e.code, t[:200]
ok = True
def check(name, cond, info=""):
    global ok
    ok = ok and cond
    print(("OK  " if cond else "FAIL"), name, info)
login = "check_" + secrets.token_hex(4)
pw = "pass-" + secrets.token_hex(6)
code, out = call("register", {"login": login, "password": pw, "sync": "".join(secrets.choice(string.ascii_uppercase) for _ in range(25))})
check("регистрация", code == 200 and "token" in out, code)
tok = out.get("token")
code, me = call("me")
check("профиль", code == 200, code)
check("бесплатно 500 МБ", me.get("limit") == 500 * 1024 * 1024, me.get("limit"))
check("магазин виден", me.get("shop") is True, me.get("shop"))
check("оплата закрыта", me.get("pay") is False, me.get("pay"))
code, out = call("pay/create", {"plan": "plus"})
check("оплата не создаётся", code == 503, (code, out))
data = b"%PDF-1.4 " + secrets.token_bytes(400)
fp = hashlib.sha256(data).hexdigest()
code, out = call("books/upload", {"fp": fp, "size": len(data), "name": "Проверка", "ext": "pdf"})
check("загрузка книги: ссылка", code == 200 and "url" in out, code)
if code == 200:
    req = urllib.request.Request(out["url"], data=data, method="PUT")
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            put = r.status
    except urllib.error.HTTPError as e:
        put = e.code
    check("загрузка книги: файл", put == 200, put)
    code, out = call("books/commit", {"fp": fp})
    check("загрузка книги: в облаке", code == 200 and out.get("used") == len(data), (code, out.get("used") if isinstance(out, dict) else out))
    code, out = call("books/url", {"fp": fp})
    check("скачивание: ссылка", code == 200 and "url" in out, code)
    if code == 200:
        with urllib.request.urlopen(out["url"], timeout=60) as r:
            got = r.read()
        check("скачивание: тот же файл", got == data, len(got))
    code, out = call("books/delete", {"fp": fp})
    check("удаление книги", code == 200, code)
code, out = call("backup/list")
check("облачные копии", code == 200 and "backups" in out, code)
code, out = call("delete", {"password": pw})
check("удаление аккаунта", code == 200, code)
tok = None
code, out = call("login", {"login": login, "password": pw})
check("вход после удаления закрыт", code != 200, code)
space = "".join(secrets.choice(string.ascii_lowercase + string.digits) for _ in range(25))
fs = "https://firestore.googleapis.com/v1/projects/wikiflerty-main/databases/(default)/documents/spaces/" + space + "/check?key=AIzaSyCu6eR2CIY4EKqllpI8AJ6NfX1urr_klYM"
code, out = call(None, raw=json.dumps({"fields": {"v": {"stringValue": "ok"}}}).encode(), method="PATCH", url=fs)
check("синхронизация прогресса: запись", code == 200, (code, str(out)[:160]))
code, out = call(None, raw=None, method="GET", url=fs) if False else (None, None)
req = urllib.request.Request(fs, method="GET")
try:
    with urllib.request.urlopen(req, timeout=60) as r:
        got = json.loads(r.read())
    check("синхронизация прогресса: чтение", got.get("fields", {}).get("v", {}).get("stringValue") == "ok")
except urllib.error.HTTPError as e:
    check("синхронизация прогресса: чтение", False, e.code)
req = urllib.request.Request(fs, method="DELETE")
try:
    urllib.request.urlopen(req, timeout=60)
except Exception:
    pass
print("ИТОГ", "всё в порядке" if ok else "есть ошибки")
