package main

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"
)

func fakeS3() *httptest.Server {
	var mu sync.Mutex
	objs := map[string][]byte{}
	return httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		mu.Lock()
		defer mu.Unlock()
		k := r.URL.Path
		switch r.Method {
		case "PUT":
			b, _ := io.ReadAll(r.Body)
			objs[k] = b
		case "GET":
			b, ok := objs[k]
			if !ok {
				w.WriteHeader(404)
				return
			}
			if rg := r.Header.Get("Range"); rg != "" {
				end := 8
				if len(b) < end {
					end = len(b)
				}
				w.Header().Set("Content-Range", "bytes 0-7/"+strconv.Itoa(len(b)))
				w.WriteHeader(206)
				w.Write(b[:end])
				return
			}
			w.Write(b)
		case "DELETE":
			delete(objs, k)
			w.WriteHeader(204)
		}
	}))
}

type client struct {
	t     *testing.T
	srv   *httptest.Server
	token string
}

func (c *client) call(path string, body any) (int, map[string]any) {
	b, _ := json.Marshal(body)
	req, _ := http.NewRequest("POST", c.srv.URL+path, bytes.NewReader(b))
	if c.token != "" {
		req.Header.Set("Authorization", "Bearer "+c.token)
	}
	r, err := http.DefaultClient.Do(req)
	if err != nil {
		c.t.Fatal(err)
	}
	defer r.Body.Close()
	var out map[string]any
	json.NewDecoder(r.Body).Decode(&out)
	return r.StatusCode, out
}

func TestFlow(t *testing.T) {
	fs := fakeS3()
	defer fs.Close()
	s3 := &S3{Host: strings.TrimPrefix(fs.URL, "https://"), Region: "ru-3", Access: "a", Secret: "b", Client: fs.Client()}
	st, err := NewStore(s3)
	if err != nil {
		t.Fatal(err)
	}
	app := &App{presence: &Presence{seen: map[string]time.Time{}}, st: st, pending: map[string]map[string]*pendingUpload{}, limiter: &Limiter{hits: map[string][]time.Time{}, fails: map[string][]time.Time{}}}
	srv := httptest.NewServer(http.HandlerFunc(app.route))
	defer srv.Close()
	c := &client{t: t, srv: srv}
	c.call("/v1/ping", map[string]any{"id": "a", "reading": true})
	if _, out := c.call("/v1/ping", map[string]any{"id": "b", "reading": false}); out["reading"].(float64) != 1 {
		t.Fatal("ping", out)
	}
	code, out := c.call("/v1/register", map[string]any{"login": "Tester_1", "password": "secret123", "sync": "ABCDE-FGHJK-MNPQR"})
	if code != 200 {
		t.Fatal(code, out)
	}
	c.token = out["token"].(string)
	recovery := out["recovery"].(string)
	if code, _ := c.call("/v1/register", map[string]any{"login": "tester_1", "password": "secret123", "sync": "ABCDE-FGHJK-MNPQR"}); code != 409 {
		t.Fatal("dup", code)
	}
	fp := strings.Repeat("a", 64)
	data := append([]byte("PK\x03\x04"), bytes.Repeat([]byte("x"), 100)...)
	code, out = c.call("/v1/books/upload", map[string]any{"fp": fp, "size": len(data), "name": "Book", "ext": "epub"})
	if code != 200 {
		t.Fatal(code, out)
	}
	put, _ := http.NewRequest("PUT", out["url"].(string), bytes.NewReader(data))
	r, err := fs.Client().Do(put)
	if err != nil || r.StatusCode != 200 {
		t.Fatal(err)
	}
	code, out = c.call("/v1/books/commit", map[string]any{"fp": fp})
	if code != 200 || out["used"].(float64) != float64(len(data)) {
		t.Fatal(code, out)
	}
	code, out = c.call("/v1/books/url", map[string]any{"fp": fp})
	if code != 200 {
		t.Fatal(code, out)
	}
	code, out = c.call("/v1/books/upload", map[string]any{"fp": strings.Repeat("b", 64), "size": 300 << 20, "name": "Big", "ext": "epub"})
	if code != 413 {
		t.Fatal("big", code, out)
	}
	bad := strings.Repeat("c", 64)
	c.call("/v1/books/upload", map[string]any{"fp": bad, "size": 5, "name": "Bad", "ext": "pdf"})
	s3.Put(bookKey(st.users["tester_1"], bad, "pdf"), []byte("hello"))
	if code, _ := c.call("/v1/books/commit", map[string]any{"fp": bad}); code != 400 {
		t.Fatal("magic", code)
	}
	c.token = ""
	if code, _ := c.call("/v1/login", map[string]any{"login": "tester_1", "password": "wrongpass"}); code != 403 {
		t.Fatal("wrong", code)
	}
	code, out = c.call("/v1/recover", map[string]any{"login": "tester_1", "recovery": strings.ToLower(recovery), "password": "newsecret1"})
	if code != 200 || out["sync"] != "ABCDE-FGHJK-MNPQR" {
		t.Fatal(code, out)
	}
	code, out = c.call("/v1/login", map[string]any{"login": "tester_1", "password": "newsecret1"})
	if code != 200 {
		t.Fatal(code, out)
	}
	c.token = out["token"].(string)
	code, out = c.call("/v1/me", map[string]any{})
	if code != 200 || len(out["books"].([]any)) != 1 {
		t.Fatal(code, out)
	}
	if code, _ := c.call("/v1/delete", map[string]any{"password": "newsecret1"}); code != 200 {
		t.Fatal("delete", code)
	}
	if st.stats.Total != 0 || st.stats.Users != 0 {
		t.Fatal(st.stats)
	}
	if code, _ := c.call("/v1/me", map[string]any{}); code != 401 {
		t.Fatal("after delete", code)
	}
}
