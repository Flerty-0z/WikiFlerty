package main

import (
	"io"
	"log"
	"net/http"
	"strconv"
	"sync"
	"time"
)

var (
	relaySlots = make(chan struct{}, 4)
	grants     = struct {
		sync.Mutex
		m map[string]time.Time
	}{m: map[string]time.Time{}}
)

func grant(login, fp string) {
	grants.Lock()
	now := time.Now()
	for k, t := range grants.m {
		if now.After(t) {
			delete(grants.m, k)
		}
	}
	grants.m[login+"/"+fp] = now.Add(linkLife + 5*time.Minute)
	grants.Unlock()
}

func takeGrant(login, fp string) bool {
	grants.Lock()
	defer grants.Unlock()
	t, ok := grants.m[login+"/"+fp]
	if ok {
		delete(grants.m, login+"/"+fp)
	}
	return ok && time.Now().Before(t)
}

func relayFail(w http.ResponseWriter, status int, code, text string) {
	reply(w, status, map[string]string{"error": code, "message": text})
}

func (a *App) relay(w http.ResponseWriter, r *http.Request) {
	fp := r.URL.Query().Get("fp")
	if !fpRe.MatchString(fp) {
		relayFail(w, 400, "bad_request", "Неверный запрос")
		return
	}
	a.st.mu.Lock()
	u, e := a.authed(r)
	if e != nil {
		a.st.mu.Unlock()
		relayFail(w, e.status, e.code, e.text)
		return
	}
	var target string
	var size int64
	var ext string
	upload := r.URL.Path == "/v1/books/put"
	if upload {
		p := a.pending[u.Login][fp]
		if p == nil || time.Now().After(p.expires) {
			a.st.mu.Unlock()
			relayFail(w, 404, "no_upload", "Загрузка не найдена, начни заново")
			return
		}
		size, ext = p.size, p.ext
		target = a.st.s3.Presign("PUT", bookKey(u, fp, ext), linkLife)
	} else {
		rec, ok := u.Books[fp]
		if !ok || !takeGrant(u.Login, fp) {
			a.st.mu.Unlock()
			relayFail(w, 404, "missing", "Этой книги нет в облаке")
			return
		}
		size, ext = rec.Size, rec.Ext
		target = a.st.s3.Presign("GET", bookKey(u, fp, ext), linkLife)
	}
	a.st.mu.Unlock()

	select {
	case relaySlots <- struct{}{}:
		defer func() { <-relaySlots }()
	case <-time.After(20 * time.Second):
		relayFail(w, 503, "busy", "Облако занято, попробую позже")
		return
	}
	relayClient := &http.Client{Transport: a.st.s3.Client.Transport, Timeout: 20 * time.Minute}
	rc := http.NewResponseController(w)
	rc.SetReadDeadline(time.Now().Add(20 * time.Minute))
	rc.SetWriteDeadline(time.Now().Add(20 * time.Minute))

	if upload {
		if r.Method != http.MethodPut || r.ContentLength != size {
			relayFail(w, 400, "bad_request", "Неверный размер файла")
			return
		}
		req, _ := http.NewRequestWithContext(r.Context(), "PUT", target, io.LimitReader(r.Body, size))
		req.ContentLength = size
		if ext == "pdf" {
			req.Header.Set("Content-Type", "application/pdf")
		} else {
			req.Header.Set("Content-Type", "application/epub+zip")
		}
		resp, err := relayClient.Do(req)
		if err != nil {
			log.Println("relay put:", err)
			relayFail(w, 502, "network", "Связь прервалась, загрузка продолжится позже")
			return
		}
		resp.Body.Close()
		if resp.StatusCode/100 != 2 {
			relayFail(w, 502, "upload", "Не получилось загрузить книгу, попробую позже")
			return
		}
		reply(w, 200, map[string]any{"ok": true})
		return
	}
	req, _ := http.NewRequestWithContext(r.Context(), "GET", target, nil)
	resp, err := relayClient.Do(req)
	if err != nil {
		log.Println("relay get:", err)
		relayFail(w, 502, "network", "Связь прервалась, скачивание продолжится позже")
		return
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		relayFail(w, 502, "download", "Не получилось скачать книгу, попробую позже")
		return
	}
	w.Header().Set("Content-Type", "application/octet-stream")
	w.Header().Set("Content-Length", strconv.FormatInt(size, 10))
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(200)
	io.Copy(w, io.LimitReader(resp.Body, size))
}
