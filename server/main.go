package main

import (
	"crypto/rand"
	"crypto/tls"
	"embed"
	"encoding/json"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	gb             = int64(1) << 30
	mb             = int64(1) << 20
	freeLimit      = 500 * mb
	maxFile        = 200 * mb
	userUpPerDay   = 2 * gb
	userDownPerDay = 3 * gb
	linkLife       = 15 * time.Minute
)

var (
	globalCap     = envInt("GLOBAL_CAP_GB", 250) * gb
	globalDownDay = envInt("GLOBAL_DOWN_GB", 10) * gb
	loginRe       = regexp.MustCompile(`^[a-z0-9_]{3,24}$`)
	fpRe          = regexp.MustCompile(`^[0-9a-f]{64}$`)
	codeAlphabet  = "ABCDEFGHJKMNPQRSTVWXYZ23456789"
)

func envInt(name string, def int64) int64 {
	if v, err := strconv.ParseInt(os.Getenv(name), 10, 64); err == nil && v > 0 {
		return v
	}
	return def
}

type pendingUpload struct {
	size    int64
	ext     string
	name    string
	inbox   bool
	expires time.Time
}

type Presence struct {
	mu   sync.Mutex
	seen map[string]time.Time
}

func (p *Presence) touch(id string, reading, peek bool) int {
	p.mu.Lock()
	defer p.mu.Unlock()
	now := time.Now()
	switch {
	case peek:
	case reading && id != "" && len(id) <= 64 && (len(p.seen) < 200000 || !p.seen[id].IsZero()):
		p.seen[id] = now
	case !reading && id != "":
		delete(p.seen, id)
	}
	n := 0
	for k, t := range p.seen {
		if now.Sub(t) > 75*time.Second {
			delete(p.seen, k)
			continue
		}
		n++
	}
	return n
}

type App struct {
	presence *Presence
	st       *Store
	pending  map[string]map[string]*pendingUpload
	limiter  *Limiter
	bot      string
	owner    string
	kassa    *Robokassa
}

type Limiter struct {
	mu    sync.Mutex
	hits  map[string][]time.Time
	fails map[string][]time.Time
}

func (l *Limiter) allow(key string, n int, per time.Duration) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := time.Now()
	list := l.hits[key]
	kept := list[:0]
	for _, t := range list {
		if now.Sub(t) < per {
			kept = append(kept, t)
		}
	}
	if len(kept) >= n {
		l.hits[key] = kept
		return false
	}
	l.hits[key] = append(kept, now)
	return true
}

func (l *Limiter) failed(key string) {
	l.mu.Lock()
	defer l.mu.Unlock()
	l.fails[key] = append(l.fails[key], time.Now())
}

func (l *Limiter) locked(key string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := time.Now()
	kept := l.fails[key][:0]
	for _, t := range l.fails[key] {
		if now.Sub(t) < 15*time.Minute {
			kept = append(kept, t)
		}
	}
	l.fails[key] = kept
	return len(kept) >= 6
}

func (l *Limiter) clear(key string) {
	l.mu.Lock()
	defer l.mu.Unlock()
	delete(l.fails, key)
}

type apiError struct {
	status int
	code   string
	text   string
}

func fail(status int, code, text string) *apiError { return &apiError{status, code, text} }

func reply(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}

func clientIP(r *http.Request) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}

func recoveryCode() string {
	b := make([]byte, 20)
	rand.Read(b)
	var s strings.Builder
	for i, c := range b {
		if i > 0 && i%5 == 0 {
			s.WriteByte('-')
		}
		s.WriteByte(codeAlphabet[int(c)%len(codeAlphabet)])
	}
	return s.String()
}

func cleanCode(s string) string {
	var b strings.Builder
	for _, c := range strings.ToUpper(s) {
		if strings.ContainsRune(codeAlphabet, c) {
			b.WriteRune(c)
		}
	}
	return b.String()
}

func (a *App) notify(text string) {
	if a.st == nil {
		return
	}
	key := fmt.Sprintf("notify/%d-%s.txt", time.Now().UnixNano(), randomHex(3))
	go func() {
		if err := a.st.s3.Put(key, []byte(text)); err != nil {
			log.Println("notify:", err)
			return
		}
		kickNotify()
	}()
}

var kick struct {
	sync.Mutex
	armed bool
}

func kickNotify() {
	token := os.Getenv("GH_DISPATCH")
	if token == "" {
		return
	}
	kick.Lock()
	if kick.armed {
		kick.Unlock()
		return
	}
	kick.armed = true
	kick.Unlock()
	time.AfterFunc(3*time.Second, func() {
		kick.Lock()
		kick.armed = false
		kick.Unlock()
		req, _ := http.NewRequest("POST", "https://api.github.com/repos/Flerty-0z/WikiFlerty/actions/workflows/notify.yml/dispatches", strings.NewReader(`{"ref":"main"}`))
		req.Header.Set("Authorization", "Bearer "+token)
		req.Header.Set("Accept", "application/vnd.github+json")
		req.Header.Set("Content-Type", "application/json")
		resp, err := (&http.Client{Timeout: 20 * time.Second}).Do(req)
		if err != nil {
			log.Println("kick:", err)
			return
		}
		if resp.StatusCode/100 != 2 {
			log.Println("kick: status", resp.StatusCode)
		}
		resp.Body.Close()
	})
}

func human(n int64) string {
	if n >= gb {
		return fmt.Sprintf("%.1f ГБ", float64(n)/float64(gb))
	}
	return fmt.Sprintf("%d МБ", n/mb)
}

func (a *App) checkCap() {
	s := a.st.stats
	level := 0
	if s.Total >= globalCap {
		level = 2
	} else if s.Total >= globalCap*8/10 {
		level = 1
	}
	if level > s.Alerted {
		if level == 2 {
			a.notify("Облако WikiFlerty заполнено: " + human(s.Total) + " из " + human(globalCap) + ". Новые загрузки остановлены.")
		} else {
			a.notify("Облако WikiFlerty заполнено на 80%: " + human(s.Total) + " из " + human(globalCap) + ".")
		}
	}
	s.Alerted = level
}

func (a *App) view(u *User) map[string]any {
	books := []map[string]any{}
	for fp, b := range u.Books {
		books = append(books, map[string]any{"fp": fp, "size": b.Size, "name": b.Name, "ext": b.Ext, "at": b.At, "inbox": b.Inbox})
	}
	now := time.Now()
	plan, until := u.Plan, int64(0)
	if p, ok := u.active(now); ok {
		plan, until = p.ID, u.Until
	}
	return map[string]any{
		"login": u.Login, "used": u.Used(), "limit": u.Quota(now), "base": u.Limit, "plan": plan, "until": until,
		"books": books, "maxFile": maxFile, "created": u.Created, "pay": a.kassa.open(u.Login), "shop": a.kassa != nil && a.kassa.Login != "",
	}
}

func decode(r *http.Request, v any) *apiError {
	r.Body = http.MaxBytesReader(nil, r.Body, 16<<10)
	if err := json.NewDecoder(r.Body).Decode(v); err != nil {
		return fail(400, "bad_request", "Неверный запрос")
	}
	return nil
}

func bearer(r *http.Request) string {
	h := r.Header.Get("Authorization")
	if strings.HasPrefix(h, "Bearer ") {
		return strings.TrimSpace(h[7:])
	}
	return ""
}

func (a *App) authed(r *http.Request) (*User, *apiError) {
	u, err := a.st.byToken(bearer(r))
	if err == errNotFound {
		return nil, fail(401, "auth", "Нужно войти в аккаунт заново")
	}
	if err != nil {
		log.Println("auth:", err)
		return nil, fail(503, "storage", "Облако временно недоступно")
	}
	return u, nil
}

//go:embed site
var siteFiles embed.FS

var sitePages = map[string]string{"/": "site/index.html", "/offer": "site/offer.html", "/privacy": "site/privacy.html", "/download": "site/download.html", "/account": "site/account.html", "/pay/fail": "site/fail.html", "/style.css": "site/style.css", "/logo.png": "site/logo.png", "/fonts/JetBrainsMono-Regular.woff2": "site/fonts/JetBrainsMono-Regular.woff2", "/fonts/JetBrainsMono-Bold.woff2": "site/fonts/JetBrainsMono-Bold.woff2", "/fx.js": "site/fx.js", "/s/tap.wav": "site/s/tap.wav", "/s/open.wav": "site/s/open.wav", "/s/download.wav": "site/s/download.wav", "/s/pop.wav": "site/s/pop.wav"}

func siteHost(r *http.Request) bool {
	host := strings.ToLower(r.Host)
	if h, _, err := net.SplitHostPort(host); err == nil {
		host = h
	}
	return host == "wikiflerty.ru" || host == "www.wikiflerty.ru"
}

func serveSite(w http.ResponseWriter, r *http.Request) {
	name, ok := sitePages[strings.TrimSuffix(r.URL.Path, "/")]
	if r.URL.Path == "/" {
		name, ok = sitePages["/"], true
	}
	if !ok || (r.Method != http.MethodGet && r.Method != http.MethodHead) {
		http.NotFound(w, r)
		return
	}
	b, err := siteFiles.ReadFile(name)
	if err != nil {
		http.NotFound(w, r)
		return
	}
	cache := "public, max-age=600"
	switch {
	case strings.HasSuffix(name, ".css"):
		w.Header().Set("Content-Type", "text/css; charset=utf-8")
	case strings.HasSuffix(name, ".png"):
		w.Header().Set("Content-Type", "image/png")
		cache = "public, max-age=86400"
	case strings.HasSuffix(name, ".js"):
		w.Header().Set("Content-Type", "text/javascript; charset=utf-8")
	case strings.HasSuffix(name, ".wav"):
		w.Header().Set("Content-Type", "audio/wav")
		cache = "public, max-age=2592000"
	case strings.HasSuffix(name, ".woff2"):
		w.Header().Set("Content-Type", "font/woff2")
		cache = "public, max-age=2592000, immutable"
	default:
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Header().Set("X-Frame-Options", "DENY")
		w.Header().Set("Referrer-Policy", "same-origin")
	}
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.Header().Set("Cache-Control", cache)
	w.Write(b)
}

func (a *App) route(w http.ResponseWriter, r *http.Request) {
	if siteHost(r) && (r.URL.Path == "/download/x64" || r.URL.Path == "/download/x32") {
		a.download(w, r, strings.TrimPrefix(r.URL.Path, "/download/"))
		return
	}
	if siteHost(r) && r.URL.Path == "/download/info" {
		info := a.apkInfo()
		if info == nil {
			info = map[string]any{}
		}
		reply(w, 200, info)
		return
	}
	if siteHost(r) && r.URL.Path == "/pay/ok" {
		a.payOk(w, r)
		return
	}
	if siteHost(r) && !strings.HasPrefix(r.URL.Path, "/v1/") {
		serveSite(w, r)
		return
	}
	ip := clientIP(r)
	if !a.limiter.allow("ip:"+ip, 240, time.Minute) {
		reply(w, 429, map[string]string{"error": "slow", "message": "Слишком много запросов, подожди минуту"})
		return
	}
	if r.URL.Path == "/v1/pay/result" {
		a.payResult(w, r)
		return
	}
	if r.URL.Path == "/v1/crash" && r.Method == http.MethodPost {
		a.crash(w, r, ip)
		return
	}
	if r.URL.Path == "/v1/backup/put" && r.Method == http.MethodPost {
		a.backupPut(w, r)
		return
	}
	if r.URL.Path == "/v1/backup/get" {
		a.backupGet(w, r)
		return
	}
	if r.URL.Path == "/v1/books/put" || r.URL.Path == "/v1/books/get" {
		a.relay(w, r)
		return
	}
	if r.URL.Path == "/v1/health" {
		reply(w, 200, map[string]any{"ok": true})
		return
	}
	if r.Method != http.MethodPost {
		reply(w, 405, map[string]string{"error": "method"})
		return
	}
	if r.URL.Path == "/v1/ping" {
		var b struct {
			ID      string `json:"id"`
			Reading bool   `json:"reading"`
			Peek    bool   `json:"peek"`
		}
		if e := decode(r, &b); e != nil {
			reply(w, e.status, map[string]string{"error": e.code})
			return
		}
		reply(w, 200, map[string]any{"reading": a.presence.touch(b.ID, b.Reading, b.Peek)})
		return
	}
	a.st.mu.Lock()
	result, e := a.handle(r, ip)
	a.st.mu.Unlock()
	if e != nil {
		reply(w, e.status, map[string]string{"error": e.code, "message": e.text})
		return
	}
	reply(w, 200, result)
}

func (a *App) handle(r *http.Request, ip string) (any, *apiError) {
	switch r.URL.Path {
	case "/v1/register":
		return a.register(r, ip)
	case "/v1/login":
		return a.login(r, ip)
	case "/v1/recover":
		return a.recover(r, ip)
	}
	u, e := a.authed(r)
	if e != nil {
		return nil, e
	}
	u.roll(today())
	switch r.URL.Path {
	case "/v1/me":
		return a.view(u), nil
	case "/v1/logout":
		a.st.revoke(u, bearer(r))
		a.st.save(u)
		return map[string]any{"ok": true}, nil
	case "/v1/password":
		return a.password(r, u)
	case "/v1/sync":
		return a.setSync(r, u)
	case "/v1/delete":
		return a.remove(r, u)
	case "/v1/books/upload":
		return a.upload(r, u)
	case "/v1/books/commit":
		return a.commit(r, u)
	case "/v1/books/url":
		return a.link(r, u)
	case "/v1/books/delete":
		return a.deleteBook(r, u)
	case "/v1/pay/create":
		return a.payCreate(r, u)
	case "/v1/backup/list":
		return map[string]any{"backups": u.Backups}, nil
	}
	return nil, fail(404, "not_found", "Неизвестный запрос")
}

type authBody struct {
	Login    string `json:"login"`
	Password string `json:"password"`
	Sync     string `json:"sync"`
	Recovery string `json:"recovery"`
	Old      string `json:"old"`
}

func (b *authBody) norm() { b.Login = strings.ToLower(strings.TrimSpace(b.Login)) }

func validPassword(p string) *apiError {
	if len([]rune(p)) < 8 {
		return fail(400, "weak", "Пароль должен быть не короче 8 символов")
	}
	if len(p) > 200 {
		return fail(400, "weak", "Слишком длинный пароль")
	}
	return nil
}

func (a *App) register(r *http.Request, ip string) (any, *apiError) {
	if !a.limiter.allow("reg:"+ip, 5, time.Hour) {
		return nil, fail(429, "slow", "Слишком много регистраций, попробуй позже")
	}
	var b authBody
	if e := decode(r, &b); e != nil {
		return nil, e
	}
	b.norm()
	if !loginRe.MatchString(b.Login) {
		return nil, fail(400, "login", "Логин: от 3 до 24 символов, латиница, цифры и _")
	}
	if e := validPassword(b.Password); e != nil {
		return nil, e
	}
	if len(b.Sync) < 10 || len(b.Sync) > 64 {
		return nil, fail(400, "bad_request", "Неверный запрос")
	}
	if _, err := a.st.load(b.Login); err == nil {
		return nil, fail(409, "taken", "Этот логин уже занят")
	} else if err != errNotFound {
		log.Println("register:", err)
		return nil, fail(503, "storage", "Облако временно недоступно")
	}
	recovery := recoveryCode()
	u := &User{
		Login: b.Login, ID: randomHex(12), Salt: randomHex(16), RSalt: randomHex(16),
		Sync: b.Sync, Created: time.Now().UnixMilli(), Limit: freeLimit, Plan: "free",
		Books: map[string]*BookRec{},
	}
	u.Hash = hashSecret(b.Password, u.Salt)
	u.RHash = hashSecret(cleanCode(recovery), u.RSalt)
	token, err := a.st.issue(u)
	if err == nil {
		err = a.st.save(u)
	}
	if err != nil {
		log.Println("register save:", err)
		return nil, fail(503, "storage", "Облако временно недоступно")
	}
	a.st.users[u.Login] = u
	a.st.stats.Users++
	a.st.saveStats()
	return map[string]any{"token": token, "recovery": recovery, "sync": u.Sync, "account": a.view(u)}, nil
}

func (a *App) login(r *http.Request, ip string) (any, *apiError) {
	if !a.limiter.allow("auth:"+ip, 20, 10*time.Minute) {
		return nil, fail(429, "slow", "Слишком много попыток, подожди 10 минут")
	}
	var b authBody
	if e := decode(r, &b); e != nil {
		return nil, e
	}
	b.norm()
	if a.limiter.locked("login:" + b.Login) {
		return nil, fail(429, "locked", "Слишком много неверных попыток, подожди 15 минут")
	}
	u, err := a.st.load(b.Login)
	if err != nil && err != errNotFound {
		return nil, fail(503, "storage", "Облако временно недоступно")
	}
	if err == errNotFound || !sameHash(b.Password, u.Salt, u.Hash) {
		a.limiter.failed("login:" + b.Login)
		return nil, fail(403, "wrong", "Неверный логин или пароль")
	}
	a.limiter.clear("login:" + b.Login)
	token, err := a.st.issue(u)
	if err == nil {
		err = a.st.save(u)
	}
	if err != nil {
		return nil, fail(503, "storage", "Облако временно недоступно")
	}
	return map[string]any{"token": token, "sync": u.Sync, "account": a.view(u)}, nil
}

func (a *App) recover(r *http.Request, ip string) (any, *apiError) {
	if !a.limiter.allow("auth:"+ip, 20, 10*time.Minute) {
		return nil, fail(429, "slow", "Слишком много попыток, подожди 10 минут")
	}
	var b authBody
	if e := decode(r, &b); e != nil {
		return nil, e
	}
	b.norm()
	if a.limiter.locked("login:" + b.Login) {
		return nil, fail(429, "locked", "Слишком много неверных попыток, подожди 15 минут")
	}
	if e := validPassword(b.Password); e != nil {
		return nil, e
	}
	u, err := a.st.load(b.Login)
	if err != nil && err != errNotFound {
		return nil, fail(503, "storage", "Облако временно недоступно")
	}
	if err == errNotFound || !sameHash(cleanCode(b.Recovery), u.RSalt, u.RHash) {
		a.limiter.failed("login:" + b.Login)
		return nil, fail(403, "wrong", "Неверный логин или код восстановления")
	}
	a.limiter.clear("login:" + b.Login)
	a.st.revokeAll(u, "")
	recovery := recoveryCode()
	u.Salt = randomHex(16)
	u.Hash = hashSecret(b.Password, u.Salt)
	u.RSalt = randomHex(16)
	u.RHash = hashSecret(cleanCode(recovery), u.RSalt)
	token, err := a.st.issue(u)
	if err == nil {
		err = a.st.save(u)
	}
	if err != nil {
		return nil, fail(503, "storage", "Облако временно недоступно")
	}
	return map[string]any{"token": token, "recovery": recovery, "sync": u.Sync, "account": a.view(u)}, nil
}

func (a *App) password(r *http.Request, u *User) (any, *apiError) {
	var b authBody
	if e := decode(r, &b); e != nil {
		return nil, e
	}
	if !sameHash(b.Old, u.Salt, u.Hash) {
		return nil, fail(403, "wrong", "Текущий пароль введён неверно")
	}
	if e := validPassword(b.Password); e != nil {
		return nil, e
	}
	u.Salt = randomHex(16)
	u.Hash = hashSecret(b.Password, u.Salt)
	a.st.revokeAll(u, tokenKey(bearer(r)))
	if err := a.st.save(u); err != nil {
		return nil, fail(503, "storage", "Облако временно недоступно")
	}
	return map[string]any{"ok": true}, nil
}

func (a *App) setSync(r *http.Request, u *User) (any, *apiError) {
	var b authBody
	if e := decode(r, &b); e != nil {
		return nil, e
	}
	if len(b.Sync) < 10 || len(b.Sync) > 64 {
		return nil, fail(400, "bad_request", "Неверный запрос")
	}
	u.Sync = b.Sync
	if err := a.st.save(u); err != nil {
		return nil, fail(503, "storage", "Облако временно недоступно")
	}
	return map[string]any{"sync": u.Sync}, nil
}

func (a *App) remove(r *http.Request, u *User) (any, *apiError) {
	var b authBody
	if e := decode(r, &b); e != nil {
		return nil, e
	}
	if !sameHash(b.Password, u.Salt, u.Hash) {
		return nil, fail(403, "wrong", "Пароль введён неверно")
	}
	var freed int64
	for _, b := range u.Backups {
		a.st.s3.Delete(backupKey(u, b.Day))
	}
	for fp, rec := range u.Books {
		if err := a.st.s3.Delete(bookKey(u, fp, rec.Ext)); err != nil {
			return nil, fail(503, "storage", "Облако временно недоступно")
		}
		freed += rec.Size
		delete(u.Books, fp)
	}
	a.st.revokeAll(u, "")
	if err := a.st.s3.Delete(userKey(u.Login)); err != nil {
		return nil, fail(503, "storage", "Облако временно недоступно")
	}
	delete(a.st.users, u.Login)
	delete(a.pending, u.Login)
	a.st.stats.Total -= freed
	if a.st.stats.Total < 0 {
		a.st.stats.Total = 0
	}
	if a.st.stats.Users > 0 {
		a.st.stats.Users--
	}
	a.checkCap()
	a.st.saveStats()
	return map[string]any{"ok": true}, nil
}

type bookBody struct {
	Fp    string `json:"fp"`
	Size  int64  `json:"size"`
	Name  string `json:"name"`
	Ext   string `json:"ext"`
	Inbox bool   `json:"inbox"`
}

var inboxExt = map[string]bool{"epub": true, "pdf": true, "fb2": true, "fbz": true, "zip": true, "mobi": true, "azw3": true, "azw": true, "prc": true, "docx": true, "odt": true, "rtf": true, "txt": true, "html": true, "htm": true, "md": true, "cbz": true, "cbr": true}

func magicFits(ext string, head []byte) bool {
	h := string(head)
	switch ext {
	case "epub", "docx", "odt", "cbz", "zip", "fbz":
		return strings.HasPrefix(h, "PK\x03\x04")
	case "pdf":
		return strings.HasPrefix(h, "%PDF")
	case "cbr":
		return strings.HasPrefix(h, "Rar!")
	case "rtf":
		return strings.HasPrefix(h, "{\\rtf")
	}
	return len(head) > 0
}

func (a *App) reserved(login string) int64 {
	var n int64
	now := time.Now()
	for fp, p := range a.pending[login] {
		if now.After(p.expires) {
			delete(a.pending[login], fp)
			continue
		}
		n += p.size
	}
	return n
}

func (a *App) upload(r *http.Request, u *User) (any, *apiError) {
	var b bookBody
	if e := decode(r, &b); e != nil {
		return nil, e
	}
	if !fpRe.MatchString(b.Fp) {
		return nil, fail(400, "bad_request", "Неверный запрос")
	}
	b.Ext = strings.ToLower(b.Ext)
	if (!b.Inbox && b.Ext != "epub" && b.Ext != "pdf") || (b.Inbox && !inboxExt[b.Ext]) {
		return nil, fail(400, "format", "Этот формат нельзя загрузить в облако")
	}
	if _, ok := u.Books[b.Fp]; ok {
		return map[string]any{"exists": true}, nil
	}
	if b.Size <= 0 {
		return nil, fail(400, "bad_request", "Неверный запрос")
	}
	if b.Size > maxFile {
		return nil, fail(413, "too_big", "Книга больше 200 МБ, в облако её загрузить нельзя")
	}
	reserved := a.reserved(u.Login)
	if u.Used()+reserved+b.Size > u.Quota(time.Now()) {
		return nil, fail(403, "quota", "Место в облаке закончилось")
	}
	if u.UpDay+reserved+b.Size > userUpPerDay {
		return nil, fail(429, "daily", "Лимит загрузок на сегодня исчерпан, продолжу завтра")
	}
	if a.st.stats.Total+b.Size > globalCap {
		a.checkCap()
		return nil, fail(503, "full", "Облако временно заполнено, загрузка продолжится позже")
	}
	name := []rune(strings.TrimSpace(b.Name))
	if len(name) > 200 {
		name = name[:200]
	}
	if a.pending[u.Login] == nil {
		a.pending[u.Login] = map[string]*pendingUpload{}
	}
	a.pending[u.Login][b.Fp] = &pendingUpload{size: b.Size, ext: b.Ext, name: string(name), inbox: b.Inbox, expires: time.Now().Add(linkLife + 5*time.Minute)}
	return map[string]any{"url": a.st.s3.Presign("PUT", bookKey(u, b.Fp, b.Ext), linkLife)}, nil
}

func (a *App) commit(r *http.Request, u *User) (any, *apiError) {
	var b bookBody
	if e := decode(r, &b); e != nil {
		return nil, e
	}
	if _, ok := u.Books[b.Fp]; ok {
		return a.view(u), nil
	}
	p := a.pending[u.Login][b.Fp]
	if p == nil {
		return nil, fail(404, "no_upload", "Загрузка не найдена, начни заново")
	}
	key := bookKey(u, b.Fp, p.ext)
	size, head, err := a.st.s3.Head4(key)
	if err != nil {
		return nil, fail(503, "storage", "Облако временно недоступно")
	}
	if size < 0 {
		return nil, fail(404, "no_upload", "Файл не дошёл до облака, попробуй ещё раз")
	}
	magicOK := magicFits(p.ext, head)
	if size != p.size || size > maxFile || !magicOK {
		a.st.s3.Delete(key)
		delete(a.pending[u.Login], b.Fp)
		return nil, fail(400, "format", "Файл повреждён или не похож на книгу")
	}
	delete(a.pending[u.Login], b.Fp)
	u.Books[b.Fp] = &BookRec{Size: size, Name: p.name, Ext: p.ext, At: time.Now().UnixMilli(), Inbox: p.inbox}
	u.UpDay += size
	if err := a.st.save(u); err != nil {
		delete(u.Books, b.Fp)
		return nil, fail(503, "storage", "Облако временно недоступно")
	}
	a.st.rollStats()
	a.st.stats.Total += size
	a.st.stats.UpDay += size
	a.checkCap()
	a.st.saveStats()
	return a.view(u), nil
}

func (a *App) link(r *http.Request, u *User) (any, *apiError) {
	var b bookBody
	if e := decode(r, &b); e != nil {
		return nil, e
	}
	rec, ok := u.Books[b.Fp]
	if !ok {
		return nil, fail(404, "missing", "Этой книги нет в облаке")
	}
	if u.DownDay+rec.Size > userDownPerDay {
		return nil, fail(429, "daily", "Лимит скачиваний на сегодня исчерпан, продолжу завтра")
	}
	a.st.rollStats()
	if a.st.stats.DownDay+rec.Size > globalDownDay {
		if a.st.stats.DownHit != a.st.stats.Day {
			a.st.stats.DownHit = a.st.stats.Day
			a.notify("WikiFlerty: дневной лимит скачиваний из облака (" + human(globalDownDay) + ") исчерпан, скачивания продолжатся завтра.")
			a.st.saveStats()
		}
		return nil, fail(503, "busy", "Облако на сегодня перегружено, скачивание продолжится завтра")
	}
	u.DownDay += rec.Size
	a.st.stats.DownDay += rec.Size
	a.st.save(u)
	a.st.saveStats()
	grant(u.Login, b.Fp)
	return map[string]any{"url": a.st.s3.Presign("GET", bookKey(u, b.Fp, rec.Ext), linkLife), "size": rec.Size, "ext": rec.Ext}, nil
}

func (a *App) deleteBook(r *http.Request, u *User) (any, *apiError) {
	var b bookBody
	if e := decode(r, &b); e != nil {
		return nil, e
	}
	rec, ok := u.Books[b.Fp]
	if !ok {
		return a.view(u), nil
	}
	if err := a.st.s3.Delete(bookKey(u, b.Fp, rec.Ext)); err != nil {
		return nil, fail(503, "storage", "Облако временно недоступно")
	}
	delete(u.Books, b.Fp)
	if err := a.st.save(u); err != nil {
		u.Books[b.Fp] = rec
		return nil, fail(503, "storage", "Облако временно недоступно")
	}
	a.st.stats.Total -= rec.Size
	if a.st.stats.Total < 0 {
		a.st.stats.Total = 0
	}
	a.checkCap()
	a.st.saveStats()
	return a.view(u), nil
}

func main() {
	s3 := &S3{
		Host:   os.Getenv("S3_HOST"),
		Region: os.Getenv("S3_REGION"),
		Access: os.Getenv("S3_ACCESS"),
		Secret: os.Getenv("S3_SECRET"),
		Client: &http.Client{Timeout: 30 * time.Second},
	}
	if len(os.Args) > 1 && os.Args[1] == "check" {
		key := "meta/check-" + randomHex(4)
		if err := s3.Put(key, []byte(`{"ok":true}`)); err != nil {
			log.Fatal("put: ", err)
		}
		if b, ok, err := s3.Get(key); err != nil || !ok || string(b) != `{"ok":true}` {
			log.Fatal("get: ", err, ok)
		}
		if err := s3.Delete(key); err != nil {
			log.Fatal("delete: ", err)
		}
		fmt.Println("bucket ok")
		return
	}
	st, err := NewStore(s3)
	if err != nil {
		log.Fatal("store: ", err)
	}
	app := &App{
		presence: &Presence{seen: map[string]time.Time{}},
		st:       st,
		pending:  map[string]map[string]*pendingUpload{},
		limiter:  &Limiter{hits: map[string][]time.Time{}, fails: map[string][]time.Time{}},
		bot:      os.Getenv("BOT_TOKEN"),
		owner:    os.Getenv("OWNER_CHAT"),
		kassa:    robokassaFromEnv(),
	}
	mux := http.NewServeMux()
	mux.HandleFunc("/", app.route)
	srv := &http.Server{
		Addr:              ":443",
		Handler:           mux,
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      60 * time.Second,
		IdleTimeout:       90 * time.Second,
		MaxHeaderBytes:    16 << 10,
		TLSConfig:         tlsConfig(),
	}
	log.Println("listening")
	log.Fatal(srv.ListenAndServeTLS("", ""))
}

type certFile struct {
	mu    sync.Mutex
	cert  string
	key   string
	stamp time.Time
	last  time.Time
	value *tls.Certificate
}

func (c *certFile) get() *tls.Certificate {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.cert == "" {
		return nil
	}
	if c.value != nil && time.Since(c.last) < time.Minute {
		return c.value
	}
	c.last = time.Now()
	info, err := os.Stat(c.cert)
	if err != nil {
		return c.value
	}
	if c.value != nil && !info.ModTime().After(c.stamp) {
		return c.value
	}
	pair, err := tls.LoadX509KeyPair(c.cert, c.key)
	if err != nil {
		log.Println("cert:", err)
		return c.value
	}
	c.value = &pair
	c.stamp = info.ModTime()
	return c.value
}

func tlsConfig() *tls.Config {
	self := &certFile{cert: os.Getenv("TLS_CERT"), key: os.Getenv("TLS_KEY")}
	public := &certFile{cert: os.Getenv("PUBLIC_CERT"), key: os.Getenv("PUBLIC_KEY")}
	domain := strings.ToLower(os.Getenv("PUBLIC_DOMAIN"))
	return &tls.Config{
		MinVersion: tls.VersionTLS12,
		GetCertificate: func(h *tls.ClientHelloInfo) (*tls.Certificate, error) {
			name := strings.ToLower(h.ServerName)
			if domain != "" && (name == domain || name == "wikiflerty.ru" || name == "www.wikiflerty.ru") {
				if c := public.get(); c != nil {
					return c, nil
				}
			}
			if c := self.get(); c != nil {
				return c, nil
			}
			return nil, fmt.Errorf("no certificate")
		},
	}
}
