package main

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"log"
	"net/http"
	"strings"
	"sync"
	"time"
)

var crashSeen = struct {
	sync.Mutex
	day   string
	count map[string]int
}{count: map[string]int{}}

func crashSignature(text string) (string, string, []string) {
	var head string
	var frames []string
	for _, l := range strings.Split(text, "\n") {
		t := strings.TrimSpace(l)
		if head == "" && (strings.Contains(t, "Exception") || strings.Contains(t, "Error")) && !strings.HasPrefix(t, "at ") {
			head = t
			continue
		}
		if head != "" && strings.HasPrefix(t, "at ") && len(frames) < 4 {
			frames = append(frames, strings.TrimPrefix(t, "at "))
		}
	}
	kind := head
	if i := strings.Index(kind, ":"); i > 0 {
		kind = kind[:i]
	}
	h := sha256.Sum256([]byte(kind + "|" + strings.Join(frames, "|")))
	return hex.EncodeToString(h[:6]), head, frames
}

func (a *App) crash(w http.ResponseWriter, r *http.Request, ip string) {
	if !a.limiter.allow("crash:"+ip, 10, time.Hour) {
		reply(w, 429, map[string]string{"error": "slow"})
		return
	}
	var b struct {
		Device  string `json:"device"`
		Version string `json:"version"`
		Text    string `json:"text"`
	}
	r.Body = http.MaxBytesReader(w, r.Body, 48<<10)
	if e := decode(r, &b); e != nil || strings.TrimSpace(b.Text) == "" {
		reply(w, 400, map[string]string{"error": "bad_request"})
		return
	}
	clip := func(s string, n int) string {
		if len(s) > n {
			return s[:n]
		}
		return s
	}
	device, version := clip(b.Device, 200), clip(b.Version, 40)
	text := clip(b.Text, 24000)
	sig, head, frames := crashSignature(text)
	day := today()
	crashSeen.Lock()
	if crashSeen.day != day {
		crashSeen.day = day
		crashSeen.count = map[string]int{}
	}
	crashSeen.count[sig]++
	n := crashSeen.count[sig]
	crashSeen.Unlock()
	key := fmt.Sprintf("crashes/%s/%s-%d-%s.txt", day, sig, time.Now().UnixNano(), randomHex(2))
	body := device + "\n" + version + "\n\n" + text
	go func() {
		if err := a.st.s3.Put(key, []byte(body)); err != nil {
			log.Println("crash store:", err)
		}
	}()
	if n == 1 || n == 5 || n == 20 || n == 100 {
		msg := "Сбой WikiFlerty " + version
		if n > 1 {
			msg = fmt.Sprintf("Сбой повторился %d раз за сегодня · %s", n, version)
		}
		msg += "\n" + device + "\n\n" + clip(head, 300)
		for _, f := range frames {
			msg += "\n· " + clip(f, 160)
		}
		msg += "\n\nКод: " + sig
		a.notify(msg)
	}
	reply(w, 200, map[string]any{"ok": true})
}
