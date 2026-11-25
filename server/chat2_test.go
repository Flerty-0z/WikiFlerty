package main

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestChatMore(t *testing.T) {
	fs := fakeS3()
	defer fs.Close()
	s3 := &S3{Host: strings.TrimPrefix(fs.URL, "https://"), Region: "ru-3", Access: "a", Secret: "b", Client: fs.Client()}
	st, _ := NewStore(s3)
	app := &App{presence: &Presence{seen: map[string]time.Time{}}, st: st, pending: map[string]map[string]*pendingUpload{}, limiter: &Limiter{hits: map[string][]time.Time{}, fails: map[string][]time.Time{}}}
	app.chat = NewChat(s3, nil)
	srv := httptest.NewServer(http.HandlerFunc(app.route))
	defer srv.Close()
	users := map[string]*client{}
	for _, l := range []string{"f1erty0z", "anna", "boris"} {
		c := &client{t: t, srv: srv}
		_, out := c.call("/v1/register", map[string]any{"login": l, "password": "secret123", "sync": "ABCDE-FGHJK-MNPQR"})
		c.token = out["token"].(string)
		c.call("/v1/chat/accept", map[string]any{})
		users[l] = c
	}
	a, b, boss := users["anna"], users["boris"], users["f1erty0z"]
	_, out := a.call("/v1/chat/send", map[string]any{"room": "general", "text": "Привет, читатели"})
	id := out["msg"].(map[string]any)["id"]
	if code, out := a.call("/v1/chat/edit", map[string]any{"room": "general", "id": id, "text": "Привет всем читателям"}); code != 200 || out["msg"].(map[string]any)["edited"] != true {
		t.Fatal("edit", code, out)
	}
	if code, _ := b.call("/v1/chat/edit", map[string]any{"room": "general", "id": id, "text": "чужое"}); code != 403 {
		t.Fatal("foreign edit", code)
	}
	_, out = b.call("/v1/chat/search", map[string]any{"room": "general", "q": "читателям"})
	if len(out["msgs"].([]any)) != 1 {
		t.Fatal("search", out)
	}
	app.chat.mu.Lock()
	m := app.chat.find("general", int64(id.(float64)))
	m.At -= 11 * 60 * 1000
	app.chat.mu.Unlock()
	if code, out := a.call("/v1/chat/unsend", map[string]any{"room": "general", "id": id}); code != 403 || out["error"] != "late" {
		t.Fatal("late", code, out)
	}
	book := "b:0123456789abcdef"
	for i, ch := range []int{2, 5, 9} {
		app.chat.mu.Lock()
		app.chat.room(book).last = map[string]time.Time{}
		app.chat.mu.Unlock()
		if code, out := a.call("/v1/chat/send", map[string]any{"room": book, "text": "мысль о главе " + string(rune(0x430+i)), "ch": ch, "chTitle": "Глава", "title": "Дракула", "author": "Брэм Стокер"}); code != 200 {
			t.Fatal("book send", i, code, out)
		}
	}
	_, out = b.call("/v1/chat/poll", map[string]any{"room": book, "after": 0, "upto": 5})
	if len(out["msgs"].([]any)) != 2 || len(out["locked"].([]any)) != 1 {
		t.Fatal("spoilers", out)
	}
	_, out = b.call("/v1/chat/search", map[string]any{"room": book, "q": "мысль", "upto": 3})
	if len(out["msgs"].([]any)) != 1 {
		t.Fatal("search spoilers", out)
	}
	if code, out := b.call("/v1/chat/sprint", map[string]any{"op": "start", "minutes": 15}); code != 200 || out["sprint"] == nil {
		t.Fatal("sprint", code, out)
	}
	if code, _ := a.call("/v1/chat/sprint", map[string]any{"op": "start", "minutes": 15}); code != 409 {
		t.Fatal("sprint busy", code)
	}
	a.call("/v1/chat/sprint", map[string]any{"op": "join"})
	app.chat.mu.Lock()
	app.chat.sprint.Start -= 10 * 60 * 1000
	app.chat.mu.Unlock()
	_, out = a.call("/v1/chat/sprint", map[string]any{"op": "pages", "pages": 1000})
	people := out["sprint"].(map[string]any)["people"].([]any)
	if len(people) != 2 || people[0].(map[string]any)["pages"].(float64) > 70 {
		t.Fatal("sprint pages cap", out)
	}
	a.call("/v1/chat/goal", map[string]any{"pages": 120})
	_, out = b.call("/v1/chat/goal", map[string]any{"pages": 5000})
	if out["total"].(float64) != 720 || out["readers"].(float64) != 2 {
		t.Fatal("goal", out)
	}
	if code, out := a.call("/v1/chat/idea", map[string]any{"op": "add", "title": "Тёмная тема для словаря", "text": "Чтобы вечером не слепило"}); code != 200 || len(out["ideas"].([]any)) != 1 {
		t.Fatal("idea", code, out)
	}
	_, out = a.call("/v1/chat/ideas", map[string]any{})
	iid := out["ideas"].([]any)[0].(map[string]any)["id"]
	_, out = b.call("/v1/chat/idea", map[string]any{"op": "vote", "id": iid})
	if out["ideas"].([]any)[0].(map[string]any)["votes"].(float64) != 2 {
		t.Fatal("vote", out)
	}
	boss.call("/v1/chat/admin", map[string]any{"op": "idea", "id": iid, "status": "work", "text": "Делаю"})
	_, out = b.call("/v1/chat/ideas", map[string]any{})
	if out["ideas"].([]any)[0].(map[string]any)["status"] != "work" {
		t.Fatal("status", out)
	}
	app.chat.mu.Lock()
	app.chat.rooms["reading"].last = map[string]time.Time{}
	app.chat.mu.Unlock()
	_, out = b.call("/v1/chat/send", map[string]any{"room": "reading", "text": "", "quote": map[string]any{"text": "Рукописи не горят.", "book": "Мастер и Маргарита"}})
	qid := out["msg"].(map[string]any)["id"]
	a.call("/v1/chat/react", map[string]any{"room": "reading", "id": qid, "kind": "heart"})
	_, out = b.call("/v1/chat/state", map[string]any{})
	if out["quote"].(map[string]any)["text"] != "Рукописи не горят." || out["goal"].(map[string]any)["total"].(float64) != 720 || out["sprint"] == nil {
		t.Fatal("state extras", out)
	}
	app.chat.Flush()
	again := NewChat(s3, nil)
	if len(again.ideas) != 1 || again.goal.Total != 720 {
		t.Fatal("persist", len(again.ideas), again.goal.Total)
	}
	again.mu.Lock()
	n := len(again.room(book).msgs)
	again.mu.Unlock()
	if n != 3 {
		t.Fatal("book persist", n)
	}
}
