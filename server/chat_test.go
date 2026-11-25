package main

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestChat(t *testing.T) {
	fs := fakeS3()
	defer fs.Close()
	s3 := &S3{Host: strings.TrimPrefix(fs.URL, "https://"), Region: "ru-3", Access: "a", Secret: "b", Client: fs.Client()}
	st, err := NewStore(s3)
	if err != nil {
		t.Fatal(err)
	}
	app := &App{presence: &Presence{seen: map[string]time.Time{}}, st: st, pending: map[string]map[string]*pendingUpload{}, limiter: &Limiter{hits: map[string][]time.Time{}, fails: map[string][]time.Time{}}}
	app.chat = NewChat(s3, nil)
	srv := httptest.NewServer(http.HandlerFunc(app.route))
	defer srv.Close()
	users := map[string]*client{}
	for _, l := range []string{"f1erty0z", "reader_a", "reader_b", "reader_c", "reader_d"} {
		c := &client{t: t, srv: srv}
		code, out := c.call("/v1/register", map[string]any{"login": l, "password": "secret123", "sync": "ABCDE-FGHJK-MNPQR"})
		if code != 200 {
			t.Fatal(code, out)
		}
		c.token = out["token"].(string)
		users[l] = c
	}
	guest := &client{t: t, srv: srv}
	code, out := guest.call("/v1/chat/state", map[string]any{})
	if code != 200 || out["on"] != true || len(out["rooms"].([]any)) != 6 {
		t.Fatal("state", code, out)
	}
	if code, _ := guest.call("/v1/chat/send", map[string]any{"room": "general", "text": "привет"}); code != 401 {
		t.Fatal("guest send", code)
	}
	a, b := users["reader_a"], users["reader_b"]
	if code, out := a.call("/v1/chat/send", map[string]any{"room": "general", "text": "привет"}); code != 403 || out["error"] != "rules" {
		t.Fatal("rules", code, out)
	}
	for _, c := range users {
		c.call("/v1/chat/accept", map[string]any{})
	}
	code, out = a.call("/v1/chat/send", map[string]any{"room": "general", "text": "Кто дочитал «Дракулу»?"})
	if code != 200 {
		t.Fatal("send", code, out)
	}
	first := int64(out["msg"].(map[string]any)["id"].(float64))
	if code, out := a.call("/v1/chat/send", map[string]any{"room": "general", "text": "ещё"}); code != 429 || out["error"] != "slow" {
		t.Fatal("slow", code, out)
	}
	for _, bad := range []string{"заходи на example.com", "звони +7 912 345-67-89", "ну ты и сука", "это пиздец", "ну блять"} {
		if code, out := b.call("/v1/chat/send", map[string]any{"room": "advice", "text": bad}); code != 400 || out["error"] != "filter" {
			t.Fatal("filter", bad, code, out)
		}
	}
	for _, ok := range []string{"колебаться не стоит", "хлеба и зрелищ", "застрахуемся заранее", "сукно было синим"} {
		c := users["reader_c"]
		app.chat.mu.Lock()
		app.chat.rooms["reading"].last = map[string]time.Time{}
		app.chat.mu.Unlock()
		if code, out := c.call("/v1/chat/send", map[string]any{"room": "reading", "text": ok}); code != 200 {
			t.Fatal("false positive", ok, code, out)
		}
	}
	code, out = b.call("/v1/chat/send", map[string]any{"room": "general", "text": "Я дочитал, @reader_a", "reply": first, "quote": map[string]any{"text": "Рукописи не горят.", "book": "Мастер и Маргарита", "chapter": "гл. 24"}})
	if code != 200 || out["msg"].(map[string]any)["reply"] == nil {
		t.Fatal("reply", code, out)
	}
	second := int64(out["msg"].(map[string]any)["id"].(float64))
	_, out = a.call("/v1/chat/state", map[string]any{})
	if len(out["me"].(map[string]any)["mentions"].([]any)) != 1 || out["latest"].(map[string]any)["login"] != "reader_b" {
		t.Fatal("mentions", out)
	}
	code, out = guest.call("/v1/chat/poll", map[string]any{"room": "general", "after": 0})
	if code != 200 || len(out["msgs"].([]any)) != 2 {
		t.Fatal("poll", code, out)
	}
	ver := int64(out["ver"].(float64))
	done := make(chan map[string]any)
	go func() {
		_, o := guest.call("/v1/chat/poll", map[string]any{"room": "general", "after": ver})
		done <- o
	}()
	time.Sleep(200 * time.Millisecond)
	a.call("/v1/chat/react", map[string]any{"room": "general", "id": second, "kind": "heart"})
	select {
	case o := <-done:
		m := o["msgs"].([]any)[0].(map[string]any)
		if len(m["reacts"].(map[string]any)["heart"].([]any)) != 1 {
			t.Fatal("react", o)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("long poll did not wake")
	}
	for _, l := range []string{"reader_b", "reader_c", "reader_d"} {
		users[l].call("/v1/chat/report", map[string]any{"room": "general", "id": first, "reason": "spam"})
	}
	_, out = guest.call("/v1/chat/poll", map[string]any{"room": "general", "after": 0})
	if len(out["msgs"].([]any)) != 1 {
		t.Fatal("autohide", out)
	}
	if code, _ := a.call("/v1/chat/reports", map[string]any{}); code != 403 {
		t.Fatal("not admin", code)
	}
	boss := users["f1erty0z"]
	_, out = boss.call("/v1/chat/reports", map[string]any{})
	if len(out["reports"].([]any)) != 1 {
		t.Fatal("reports", out)
	}
	boss.call("/v1/chat/admin", map[string]any{"op": "keep", "id": first})
	_, out = guest.call("/v1/chat/poll", map[string]any{"room": "general", "after": 0})
	if len(out["msgs"].([]any)) != 2 {
		t.Fatal("keep", out)
	}
	boss.call("/v1/chat/admin", map[string]any{"op": "mute", "login": "reader_b", "hours": 24, "reason": "спам"})
	app.chat.mu.Lock()
	app.chat.rooms["ideas"].last = map[string]time.Time{}
	app.chat.mu.Unlock()
	if code, out := b.call("/v1/chat/send", map[string]any{"room": "ideas", "text": "а теперь?"}); code != 403 || out["error"] != "muted" {
		t.Fatal("mute", code, out)
	}
	boss.call("/v1/chat/admin", map[string]any{"op": "ban", "login": "reader_a", "reason": "спам"})
	_, out = guest.call("/v1/chat/poll", map[string]any{"room": "general", "after": 0})
	if len(out["msgs"].([]any)) != 1 {
		t.Fatal("ban cleanup", out)
	}
	boss.call("/v1/chat/admin", map[string]any{"op": "power", "on": false})
	if _, out := guest.call("/v1/chat/poll", map[string]any{"room": "general", "after": 0}); out["off"] != true {
		t.Fatal("power", out)
	}
	if code, _ := users["reader_c"].call("/v1/chat/send", map[string]any{"room": "audio", "text": "есть кто?"}); code != 403 {
		t.Fatal("off send", code)
	}
	app.chat.Flush()
	again := NewChat(s3, nil)
	if again.cfg.On || len(again.rooms["general"].msgs) != 2 || again.cfg.Bans["reader_a"] == "" {
		t.Fatal("persist", again.cfg.On, len(again.rooms["general"].msgs))
	}
	users["reader_c"].call("/v1/chat/me", map[string]any{"profile": map[string]any{"reading": "Три мушкетёра", "progress": 38, "books": 47, "streak": 31, "hours": 212}})
	if _, out := guest.call("/v1/chat/profile", map[string]any{"login": "reader_c"}); out["reading"] != "Три мушкетёра" {
		t.Fatal("profile", out)
	}
}
