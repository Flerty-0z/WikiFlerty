package main

import (
	"bytes"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"net/http"
	"sort"
	"strconv"
	"strings"
	"time"
)

type S3 struct {
	Host   string
	Region string
	Access string
	Secret string
	Client *http.Client
	Now    func() time.Time
}

func hmacSHA(key []byte, data string) []byte {
	m := hmac.New(sha256.New, key)
	m.Write([]byte(data))
	return m.Sum(nil)
}

func sha(data []byte) string {
	h := sha256.Sum256(data)
	return hex.EncodeToString(h[:])
}

func encode(s string, slash bool) string {
	var b strings.Builder
	for _, c := range []byte(s) {
		if (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~' || (slash && c == '/') {
			b.WriteByte(c)
		} else {
			fmt.Fprintf(&b, "%%%02X", c)
		}
	}
	return b.String()
}

func canonicalQuery(q map[string]string) string {
	keys := make([]string, 0, len(q))
	for k := range q {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	parts := make([]string, 0, len(keys))
	for _, k := range keys {
		parts = append(parts, encode(k, false)+"="+encode(q[k], false))
	}
	return strings.Join(parts, "&")
}

func (s *S3) signingKey(day string) []byte {
	k := hmacSHA([]byte("AWS4"+s.Secret), day)
	k = hmacSHA(k, s.Region)
	k = hmacSHA(k, "s3")
	return hmacSHA(k, "aws4_request")
}

func (s *S3) now() time.Time {
	if s.Now != nil {
		return s.Now().UTC()
	}
	return time.Now().UTC()
}

func (s *S3) Presign(method, key string, expires time.Duration) string {
	t := s.now()
	stamp := t.Format("20060102T150405Z")
	day := t.Format("20060102")
	scope := day + "/" + s.Region + "/s3/aws4_request"
	path := "/" + encode(key, true)
	q := map[string]string{
		"X-Amz-Algorithm":     "AWS4-HMAC-SHA256",
		"X-Amz-Credential":    s.Access + "/" + scope,
		"X-Amz-Date":          stamp,
		"X-Amz-Expires":       strconv.Itoa(int(expires.Seconds())),
		"X-Amz-SignedHeaders": "host",
	}
	cq := canonicalQuery(q)
	creq := method + "\n" + path + "\n" + cq + "\nhost:" + s.Host + "\n\nhost\nUNSIGNED-PAYLOAD"
	sts := "AWS4-HMAC-SHA256\n" + stamp + "\n" + scope + "\n" + sha([]byte(creq))
	sig := hex.EncodeToString(hmacSHA(s.signingKey(day), sts))
	return "https://" + s.Host + path + "?" + cq + "&X-Amz-Signature=" + sig
}

func (s *S3) sign(method, key string, body []byte, extra map[string]string) (map[string]string, string) {
	t := s.now()
	stamp := t.Format("20060102T150405Z")
	day := t.Format("20060102")
	scope := day + "/" + s.Region + "/s3/aws4_request"
	path := "/" + encode(key, true)
	payload := sha(body)
	headers := map[string]string{"host": s.Host, "x-amz-content-sha256": payload, "x-amz-date": stamp}
	for k, v := range extra {
		headers[strings.ToLower(k)] = v
	}
	names := make([]string, 0, len(headers))
	for k := range headers {
		names = append(names, k)
	}
	sort.Strings(names)
	var ch strings.Builder
	for _, k := range names {
		ch.WriteString(k + ":" + strings.TrimSpace(headers[k]) + "\n")
	}
	signed := strings.Join(names, ";")
	creq := method + "\n" + path + "\n\n" + ch.String() + "\n" + signed + "\n" + payload
	sts := "AWS4-HMAC-SHA256\n" + stamp + "\n" + scope + "\n" + sha([]byte(creq))
	sig := hex.EncodeToString(hmacSHA(s.signingKey(day), sts))
	return headers, "AWS4-HMAC-SHA256 Credential=" + s.Access + "/" + scope + ",SignedHeaders=" + signed + ",Signature=" + sig
}

func (s *S3) do(method, key string, body []byte, extra map[string]string) (*http.Response, error) {
	headers, auth := s.sign(method, key, body, extra)
	req, err := http.NewRequest(method, "https://"+s.Host+"/"+encode(key, true), bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	for k, v := range headers {
		if k != "host" {
			req.Header.Set(k, v)
		}
	}
	req.Header.Set("Authorization", auth)
	req.ContentLength = int64(len(body))
	return s.Client.Do(req)
}

type S3Error struct {
	Status int
	Body   string
}

func (e *S3Error) Error() string { return "s3 " + strconv.Itoa(e.Status) + ": " + e.Body }

func failed(r *http.Response) error {
	b, _ := io.ReadAll(io.LimitReader(r.Body, 2048))
	return &S3Error{r.StatusCode, string(b)}
}

func (s *S3) Get(key string) ([]byte, bool, error) {
	r, err := s.do("GET", key, nil, nil)
	if err != nil {
		return nil, false, err
	}
	defer r.Body.Close()
	if r.StatusCode == 404 {
		return nil, false, nil
	}
	if r.StatusCode != 200 {
		return nil, false, failed(r)
	}
	b, err := io.ReadAll(io.LimitReader(r.Body, 8<<20))
	return b, err == nil, err
}

func (s *S3) Head4(key string) (int64, []byte, error) {
	r, err := s.do("GET", key, nil, map[string]string{"range": "bytes=0-7"})
	if err != nil {
		return -1, nil, err
	}
	defer r.Body.Close()
	if r.StatusCode == 404 || r.StatusCode == 416 {
		return -1, nil, nil
	}
	if r.StatusCode != 206 && r.StatusCode != 200 {
		return -1, nil, failed(r)
	}
	head, _ := io.ReadAll(io.LimitReader(r.Body, 8))
	size := int64(-1)
	if cr := r.Header.Get("Content-Range"); cr != "" {
		if i := strings.LastIndex(cr, "/"); i >= 0 {
			size, _ = strconv.ParseInt(cr[i+1:], 10, 64)
		}
	} else {
		size = r.ContentLength
	}
	return size, head, nil
}

func (s *S3) Put(key string, data []byte) error {
	r, err := s.do("PUT", key, data, map[string]string{"content-type": "application/json"})
	if err != nil {
		return err
	}
	defer r.Body.Close()
	if r.StatusCode != 200 {
		return failed(r)
	}
	io.Copy(io.Discard, r.Body)
	return nil
}

func (s *S3) Delete(key string) error {
	r, err := s.do("DELETE", key, nil, nil)
	if err != nil {
		return err
	}
	defer r.Body.Close()
	if r.StatusCode != 204 && r.StatusCode != 200 && r.StatusCode != 404 {
		return failed(r)
	}
	return nil
}
