package main

import (
	"encoding/json"
	"io"
	"log"
	"net/http"
	"strconv"
	"sync"
	"time"
)

var (
	downloadSlots = make(chan struct{}, 8)
	apkInfo       = struct {
		sync.Mutex
		at   time.Time
		data map[string]any
	}{}
)

func (a *App) apkInfo() map[string]any {
	apkInfo.Lock()
	defer apkInfo.Unlock()
	if apkInfo.data != nil && time.Since(apkInfo.at) < time.Minute {
		return apkInfo.data
	}
	b, ok, err := a.st.s3.Get("public/apk/info.json")
	if err != nil || !ok {
		return apkInfo.data
	}
	var m map[string]any
	if json.Unmarshal(b, &m) == nil {
		apkInfo.data, apkInfo.at = m, time.Now()
	}
	return apkInfo.data
}

func (a *App) download(w http.ResponseWriter, r *http.Request, arch string) {
	if !a.limiter.allow("apk:"+clientIP(r), 20, time.Hour) {
		http.Error(w, "Слишком много скачиваний, попробуйте через час", 429)
		return
	}
	info := a.apkInfo()
	if info == nil {
		http.Error(w, "Файл пока не готов", 503)
		return
	}
	version, _ := info["version"].(string)
	select {
	case downloadSlots <- struct{}{}:
		defer func() { <-downloadSlots }()
	case <-time.After(30 * time.Second):
		http.Error(w, "Сервер занят, попробуйте через минуту", 503)
		return
	}
	rc := http.NewResponseController(w)
	rc.SetWriteDeadline(time.Now().Add(30 * time.Minute))
	client := &http.Client{Transport: a.st.s3.Client.Transport, Timeout: 30 * time.Minute}
	req, _ := http.NewRequestWithContext(r.Context(), "GET", a.st.s3.Presign("GET", "public/apk/WikiFlerty-"+arch+".apk", linkLife), nil)
	resp, err := client.Do(req)
	if err != nil {
		log.Println("download:", err)
		http.Error(w, "Не получилось скачать, попробуйте позже", 502)
		return
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		http.Error(w, "Файл пока не готов", 503)
		return
	}
	w.Header().Set("Content-Type", "application/vnd.android.package-archive")
	w.Header().Set("Content-Disposition", `attachment; filename="WikiFlerty-`+version+`-`+arch+`.apk"`)
	if resp.ContentLength > 0 {
		w.Header().Set("Content-Length", strconv.FormatInt(resp.ContentLength, 10))
	}
	w.Header().Set("Cache-Control", "no-store")
	io.Copy(w, resp.Body)
}
