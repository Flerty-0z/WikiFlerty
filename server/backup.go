package main

import (
	"bytes"
	"io"
	"net/http"
	"regexp"
	"sort"
	"time"
)

type BackupRec struct {
	Day  string `json:"day"`
	Size int64  `json:"size"`
	At   int64  `json:"at"`
}

const maxBackup = 8 << 20

var dayRe = regexp.MustCompile(`^\d{4}-\d{2}-\d{2}$`)

func backupKey(u *User, day string) string { return "backups/" + u.ID + "/" + day + ".json.gz" }

func (a *App) backupPut(w http.ResponseWriter, r *http.Request) {
	body, err := io.ReadAll(io.LimitReader(r.Body, maxBackup+1))
	if err != nil || len(body) < 2 || len(body) > maxBackup || body[0] != 0x1f || body[1] != 0x8b {
		reply(w, 400, map[string]string{"error": "bad_request", "message": "Копия повреждена или слишком большая"})
		return
	}
	a.st.mu.Lock()
	defer a.st.mu.Unlock()
	u, e := a.authed(r)
	if e != nil {
		reply(w, e.status, map[string]string{"error": e.code, "message": e.text})
		return
	}
	day := today()
	if err := a.st.s3.Put(backupKey(u, day), body); err != nil {
		reply(w, 503, map[string]string{"error": "storage", "message": "Облако временно недоступно"})
		return
	}
	kept := []BackupRec{{Day: day, Size: int64(len(body)), At: time.Now().UnixMilli()}}
	for _, b := range u.Backups {
		if b.Day != day {
			kept = append(kept, b)
		}
	}
	sort.Slice(kept, func(i, j int) bool { return kept[i].Day > kept[j].Day })
	for len(kept) > 7 {
		a.st.s3.Delete(backupKey(u, kept[len(kept)-1].Day))
		kept = kept[:len(kept)-1]
	}
	u.Backups = kept
	a.st.save(u)
	reply(w, 200, map[string]any{"ok": true, "backups": u.Backups})
}

func (a *App) backupGet(w http.ResponseWriter, r *http.Request) {
	day := r.URL.Query().Get("day")
	if !dayRe.MatchString(day) {
		reply(w, 400, map[string]string{"error": "bad_request"})
		return
	}
	a.st.mu.Lock()
	u, e := a.authed(r)
	if e != nil {
		a.st.mu.Unlock()
		reply(w, e.status, map[string]string{"error": e.code, "message": e.text})
		return
	}
	found := false
	for _, b := range u.Backups {
		found = found || b.Day == day
	}
	key := backupKey(u, day)
	a.st.mu.Unlock()
	if !found {
		reply(w, 404, map[string]string{"error": "missing", "message": "Такой копии нет"})
		return
	}
	data, ok, err := a.st.s3.Get(key)
	if err != nil || !ok {
		reply(w, 503, map[string]string{"error": "storage", "message": "Облако временно недоступно"})
		return
	}
	w.Header().Set("Content-Type", "application/gzip")
	w.Header().Set("Cache-Control", "no-store")
	io.Copy(w, bytes.NewReader(data))
}
