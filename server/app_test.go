package main

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
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
	for _, path := range []string{"/", "/offer", "/privacy", "/style.css"} {
		req, _ := http.NewRequest("GET", srv.URL+path, nil)
		req.Host = "wikiflerty.ru"
		resp, err := http.DefaultClient.Do(req)
		if err != nil || resp.StatusCode != 200 {
			t.Fatal("site", path, err)
		}
		resp.Body.Close()
	}
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
	fp2 := strings.Repeat("d", 64)
	data2 := append([]byte("%PDF-1.4 "), bytes.Repeat([]byte("y"), 300)...)
	if code, out := c.call("/v1/books/upload", map[string]any{"fp": fp2, "size": len(data2), "name": "Relay", "ext": "pdf"}); code != 200 {
		t.Fatal(code, out)
	}
	rq, _ := http.NewRequest("PUT", srv.URL+"/v1/books/put?fp="+fp2, bytes.NewReader(data2))
	rq.Header.Set("Authorization", "Bearer "+c.token)
	if r, err := http.DefaultClient.Do(rq); err != nil || r.StatusCode != 200 {
		t.Fatal("relay put", err, r.StatusCode)
	}
	if code, out := c.call("/v1/books/commit", map[string]any{"fp": fp2}); code != 200 {
		t.Fatal("relay commit", code, out)
	}
	gq, _ := http.NewRequest("GET", srv.URL+"/v1/books/get?fp="+fp2, nil)
	gq.Header.Set("Authorization", "Bearer "+c.token)
	if r, _ := http.DefaultClient.Do(gq); r.StatusCode != 404 {
		t.Fatal("relay get without grant", r.StatusCode)
	}
	c.call("/v1/books/url", map[string]any{"fp": fp2})
	if r, err := http.DefaultClient.Do(gq); err != nil || r.StatusCode != 200 {
		t.Fatal("relay get", err)
	} else {
		b, _ := io.ReadAll(r.Body)
		r.Body.Close()
		if !bytes.Equal(b, data2) {
			t.Fatal("relay body")
		}
	}
	c.call("/v1/books/delete", map[string]any{"fp": fp2})
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

func TestPay(t *testing.T) {
	fs := fakeS3()
	defer fs.Close()
	s3 := &S3{Host: strings.TrimPrefix(fs.URL, "https://"), Region: "ru-3", Access: "a", Secret: "b", Client: fs.Client()}
	st, _ := NewStore(s3)
	k := &Robokassa{Login: "wikiflerty", Test1: "t1", Test2: "t2", Hash: "sha256", Test: true}
	app := &App{presence: &Presence{seen: map[string]time.Time{}}, st: st, pending: map[string]map[string]*pendingUpload{}, limiter: &Limiter{hits: map[string][]time.Time{}, fails: map[string][]time.Time{}}, kassa: k}
	srv := httptest.NewServer(http.HandlerFunc(app.route))
	defer srv.Close()
	c := &client{t: t, srv: srv}
	_, out := c.call("/v1/register", map[string]any{"login": "payer", "password": "secret123", "sync": "ABCDE-FGHJK-MNPQR"})
	c.token = out["token"].(string)
	pay := func(plan string) (int64, string) {
		code, out := c.call("/v1/pay/create", map[string]any{"plan": plan})
		if code != 200 {
			t.Fatal(code, out)
		}
		link, _ := url.Parse(out["url"].(string))
		q := link.Query()
		rec := q.Get("Receipt")
		if q.Get("SignatureValue") != k.sum("wikiflerty:"+q.Get("OutSum")+":"+q.Get("InvId")+":"+rec+":t1") || q.Get("IsTest") != "1" {
			t.Fatal("link", link)
		}
		if strings.Contains(rec, "{") {
			t.Fatal("receipt not encoded", rec)
		}
		inv := q.Get("InvId")
		sum := q.Get("OutSum") + ".000000"
		form := url.Values{"OutSum": {sum}, "InvId": {inv}, "IsTest": {"1"}, "SignatureValue": {strings.ToUpper(k.sum(sum + ":" + inv + ":t2"))}}
		r, err := http.PostForm(srv.URL+"/v1/pay/result", form)
		if err != nil {
			t.Fatal(err)
		}
		b, _ := io.ReadAll(r.Body)
		r.Body.Close()
		n, _ := strconv.ParseInt(inv, 10, 64)
		return n, string(b)
	}
	bad := url.Values{"OutSum": {"79"}, "InvId": {"123"}, "IsTest": {"1"}, "SignatureValue": {"00"}}
	if r, _ := http.PostForm(srv.URL+"/v1/pay/result", bad); r.StatusCode != 400 {
		t.Fatal("bad sign accepted")
	}
	inv, body := pay("plus")
	if body != "OK"+strconv.FormatInt(inv, 10) {
		t.Fatal(body)
	}
	_, me := c.call("/v1/me", map[string]any{})
	if me["limit"].(float64) != float64(5*gb) || me["plan"] != "plus" {
		t.Fatal(me)
	}
	u := st.users["payer"]
	first := u.Until
	pay("plus")
	if d := u.Until - first; d < period.Milliseconds()-5000 {
		t.Fatal("extend", d)
	}
	pay("plus2")
	if u.Paid != "plus2" || u.Quota(time.Now()) != 15*gb {
		t.Fatal("upgrade", u.Paid)
	}
	left := u.Until - time.Now().UnixMilli()
	want := period.Milliseconds() + 2*period.Milliseconds()*79/149
	if left < want-10000 || left > want+10000 {
		t.Fatal("carry", left, want)
	}
	okReq, _ := http.NewRequest("GET", srv.URL+"/pay/ok?OutSum=149.000000&InvId="+strconv.FormatInt(inv, 10)+"&SignatureValue="+k.sum("149.000000:"+strconv.FormatInt(inv, 10)+":t1"), nil)
	okReq.Host = "wikiflerty.ru"
	if r, err := http.DefaultClient.Do(okReq); err != nil || r.StatusCode != 200 {
		t.Fatal("ok page", err)
	} else {
		b, _ := io.ReadAll(r.Body)
		r.Body.Close()
		if !strings.Contains(string(b), "Облако") || !strings.Contains(string(b), "4,5 ГБ") {
			t.Fatal("ok page body", string(b))
		}
	}
	u.Until = time.Now().Add(-time.Minute).UnixMilli()
	if _, me := c.call("/v1/me", map[string]any{}); me["limit"].(float64) != float64(freeLimit) || me["plan"] != "free" {
		t.Fatal("expire", me)
	}
}
