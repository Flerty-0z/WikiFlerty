package main

import (
	"crypto/pbkdf2"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"strings"
	"sync"
	"time"
)

const iterations = 120000

type BookRec struct {
	Size int64  `json:"size"`
	Name string `json:"name"`
	Ext  string `json:"ext"`
	At   int64  `json:"at"`
}

type User struct {
	Login   string              `json:"login"`
	ID      string              `json:"id"`
	Salt    string              `json:"salt"`
	Hash    string              `json:"hash"`
	RSalt   string              `json:"rsalt"`
	RHash   string              `json:"rhash"`
	Sync    string              `json:"sync"`
	Created int64               `json:"created"`
	Limit   int64               `json:"limit"`
	Plan    string              `json:"plan"`
	Books   map[string]*BookRec `json:"books"`
	Tokens  []string            `json:"tokens"`
	Day     string              `json:"day"`
	UpDay   int64               `json:"upDay"`
	DownDay int64               `json:"downDay"`
}

func (u *User) Used() int64 {
	var n int64
	for _, b := range u.Books {
		n += b.Size
	}
	return n
}

func (u *User) roll(day string) {
	if u.Day != day {
		u.Day = day
		u.UpDay = 0
		u.DownDay = 0
	}
}

type Stats struct {
	Total   int64  `json:"total"`
	Users   int    `json:"users"`
	Day     string `json:"day"`
	DownDay int64  `json:"downDay"`
	UpDay   int64  `json:"upDay"`
	Alerted int    `json:"alerted"`
	DownHit string `json:"downHit"`
}

type Store struct {
	s3     *S3
	mu     sync.Mutex
	users  map[string]*User
	tokens map[string]string
	stats  *Stats
}

var errNotFound = errors.New("not found")

func randomHex(n int) string {
	b := make([]byte, n)
	rand.Read(b)
	return hex.EncodeToString(b)
}

func hashSecret(secret, salt string) string {
	k, _ := pbkdf2.Key(sha256.New, secret, []byte(salt), iterations, 32)
	return base64.StdEncoding.EncodeToString(k)
}

func sameHash(secret, salt, want string) bool {
	got := hashSecret(secret, salt)
	return subtle.ConstantTimeCompare([]byte(got), []byte(want)) == 1
}

func tokenKey(token string) string {
	h := sha256.Sum256([]byte(token))
	return hex.EncodeToString(h[:])
}

func today() string { return time.Now().UTC().Add(3 * time.Hour).Format("2006-01-02") }

func NewStore(s *S3) (*Store, error) {
	st := &Store{s3: s, users: map[string]*User{}, tokens: map[string]string{}}
	b, ok, err := s.Get("meta/stats.json")
	if err != nil {
		return nil, err
	}
	st.stats = &Stats{}
	if ok {
		json.Unmarshal(b, st.stats)
	}
	return st, nil
}

func (st *Store) saveStats() error {
	b, _ := json.Marshal(st.stats)
	return st.s3.Put("meta/stats.json", b)
}

func (st *Store) rollStats() {
	d := today()
	if st.stats.Day != d {
		st.stats.Day = d
		st.stats.DownDay = 0
		st.stats.UpDay = 0
	}
}

func userKey(login string) string { return "users/" + login + ".json" }

func (st *Store) load(login string) (*User, error) {
	if u, ok := st.users[login]; ok {
		return u, nil
	}
	b, ok, err := st.s3.Get(userKey(login))
	if err != nil {
		return nil, err
	}
	if !ok {
		return nil, errNotFound
	}
	u := &User{}
	if err := json.Unmarshal(b, u); err != nil {
		return nil, err
	}
	if u.Books == nil {
		u.Books = map[string]*BookRec{}
	}
	st.users[login] = u
	for _, t := range u.Tokens {
		st.tokens[t] = login
	}
	return u, nil
}

func (st *Store) save(u *User) error {
	b, _ := json.Marshal(u)
	return st.s3.Put(userKey(u.Login), b)
}

func (st *Store) byToken(token string) (*User, error) {
	if token == "" {
		return nil, errNotFound
	}
	k := tokenKey(token)
	if login, ok := st.tokens[k]; ok {
		u, err := st.load(login)
		if err != nil {
			return nil, err
		}
		for _, t := range u.Tokens {
			if t == k {
				return u, nil
			}
		}
		delete(st.tokens, k)
		return nil, errNotFound
	}
	b, ok, err := st.s3.Get("tokens/" + k)
	if err != nil {
		return nil, err
	}
	if !ok {
		return nil, errNotFound
	}
	login := strings.TrimSpace(string(b))
	u, err := st.load(login)
	if err != nil {
		return nil, err
	}
	for _, t := range u.Tokens {
		if t == k {
			st.tokens[k] = login
			return u, nil
		}
	}
	return nil, errNotFound
}

func (st *Store) issue(u *User) (string, error) {
	token := randomHex(32)
	k := tokenKey(token)
	if err := st.s3.Put("tokens/"+k, []byte(u.Login)); err != nil {
		return "", err
	}
	u.Tokens = append(u.Tokens, k)
	if len(u.Tokens) > 20 {
		old := u.Tokens[0]
		u.Tokens = u.Tokens[1:]
		st.s3.Delete("tokens/" + old)
		delete(st.tokens, old)
	}
	st.tokens[k] = u.Login
	return token, nil
}

func (st *Store) revokeAll(u *User, keep string) {
	var rest []string
	for _, t := range u.Tokens {
		if t == keep {
			rest = append(rest, t)
			continue
		}
		st.s3.Delete("tokens/" + t)
		delete(st.tokens, t)
	}
	u.Tokens = rest
}

func (st *Store) revoke(u *User, token string) {
	k := tokenKey(token)
	var rest []string
	for _, t := range u.Tokens {
		if t != k {
			rest = append(rest, t)
		}
	}
	u.Tokens = rest
	st.s3.Delete("tokens/" + k)
	delete(st.tokens, k)
}

func bookKey(u *User, fp, ext string) string { return "books/" + u.ID + "/" + fp + "." + ext }
