package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"regexp"
	"sort"
	"strings"
	"sync"
	"time"
	"unicode"
	"unicode/utf8"
)

const (
	chatKeep      = 300
	chatFirst     = 60
	chatPage      = 50
	chatMaxText   = 800
	chatHideAt    = 3
	chatPollWait  = 25 * time.Second
	chatOnlineFor = 70 * time.Second
)

type ChatRef struct {
	ID    int64  `json:"id"`
	Login string `json:"login"`
	Text  string `json:"text"`
}

type ChatQuote struct {
	Text    string `json:"text"`
	Book    string `json:"book"`
	Author  string `json:"author,omitempty"`
	Chapter string `json:"chapter,omitempty"`
}

type ChatBook struct {
	Title  string `json:"title"`
	Author string `json:"author,omitempty"`
	Tint   int    `json:"tint"`
}

type ChatMsg struct {
	ID      int64               `json:"id"`
	Ver     int64               `json:"ver"`
	Room    string              `json:"room"`
	Login   string              `json:"login"`
	Admin   bool                `json:"admin,omitempty"`
	Text    string              `json:"text"`
	At      int64               `json:"at"`
	Reading string              `json:"reading,omitempty"`
	Reply   *ChatRef            `json:"reply,omitempty"`
	Quote   *ChatQuote          `json:"quote,omitempty"`
	Book    *ChatBook           `json:"book,omitempty"`
	Reacts  map[string][]string `json:"reacts,omitempty"`
	Gone    bool                `json:"gone,omitempty"`
}

type ChatRoom struct {
	ID    string `json:"id"`
	Title string `json:"title"`
	Icon  string `json:"icon"`
}

type ChatPin struct {
	Text string `json:"text"`
	At   int64  `json:"at"`
}

type ChatMute struct {
	Until int64  `json:"until"`
	Why   string `json:"why,omitempty"`
}

type ChatConfig struct {
	On    bool                `json:"on"`
	Slow  int                 `json:"slow"`
	Rooms []ChatRoom          `json:"rooms"`
	Words []string            `json:"words"`
	Pin   *ChatPin            `json:"pin,omitempty"`
	Mutes map[string]ChatMute `json:"mutes"`
	Bans  map[string]string   `json:"bans"`
}

type ChatMember struct {
	Accepted int64    `json:"accepted,omitempty"`
	Since    int64    `json:"since,omitempty"`
	Reading  string   `json:"reading,omitempty"`
	Progress int      `json:"progress,omitempty"`
	Books    int      `json:"books,omitempty"`
	Streak   int      `json:"streak,omitempty"`
	Hours    int      `json:"hours,omitempty"`
	Achv     []string `json:"achv,omitempty"`
}

type ChatReport struct {
	Msg     ChatMsg           `json:"msg"`
	Reasons map[string]string `json:"reasons"`
	State   string            `json:"state"`
	At      int64             `json:"at"`
}

type ChatMention struct {
	ID   int64  `json:"id"`
	Room string `json:"room"`
	From string `json:"from"`
	Text string `json:"text"`
	At   int64  `json:"at"`
}

type chatRoomState struct {
	msgs  []*ChatMsg
	ver   int64
	wake  chan struct{}
	seen  map[string]time.Time
	dirty bool
	last  map[string]time.Time
}

type Chat struct {
	mu       sync.Mutex
	s3       *S3
	cfg      *ChatConfig
	rooms    map[string]*chatRoomState
	members  map[string]*ChatMember
	reports  map[int64]*ChatReport
	mentions map[string][]ChatMention
	admins   map[string]bool
	ver      int64
	lastID   int64
	dirty    map[string]bool
	journal  []string
	recent   map[string]string
	notify   func(string)
}

var defaultRooms = []ChatRoom{
	{"general", "Общий", "chat"},
	{"reading", "Что читаю", "book"},
	{"advice", "Посоветуйте книгу", "search"},
	{"audio", "Аудиокниги", "head"},
	{"comics", "Комиксы", "comic"},
	{"ideas", "Идеи для WikiFlerty", "bulb"},
}

func NewChat(s3 *S3, notify func(string)) *Chat {
	c := &Chat{
		s3: s3, rooms: map[string]*chatRoomState{}, members: map[string]*ChatMember{},
		reports: map[int64]*ChatReport{}, mentions: map[string][]ChatMention{},
		admins: map[string]bool{}, dirty: map[string]bool{}, recent: map[string]string{}, notify: notify,
	}
	admins := os.Getenv("CHAT_ADMINS")
	if admins == "" {
		admins = "f1erty0z"
	}
	for _, a := range strings.Split(admins, ",") {
		if a = strings.TrimSpace(strings.ToLower(a)); a != "" {
			c.admins[a] = true
		}
	}
	c.cfg = &ChatConfig{On: true, Slow: 10, Rooms: append([]ChatRoom(nil), defaultRooms...), Mutes: map[string]ChatMute{}, Bans: map[string]string{}}
	c.loadJSON("chat/config.json", c.cfg)
	if c.cfg.Mutes == nil {
		c.cfg.Mutes = map[string]ChatMute{}
	}
	if c.cfg.Bans == nil {
		c.cfg.Bans = map[string]string{}
	}
	if len(c.cfg.Rooms) == 0 {
		c.cfg.Rooms = append([]ChatRoom(nil), defaultRooms...)
	}
	c.loadJSON("chat/members.json", &c.members)
	c.loadJSON("chat/reports.json", &c.reports)
	c.loadJSON("chat/mentions.json", &c.mentions)
	if c.members == nil {
		c.members = map[string]*ChatMember{}
	}
	if c.reports == nil {
		c.reports = map[int64]*ChatReport{}
	}
	if c.mentions == nil {
		c.mentions = map[string][]ChatMention{}
	}
	for _, r := range c.cfg.Rooms {
		st := c.room(r.ID)
		var msgs []*ChatMsg
		c.loadJSON("chat/rooms/"+r.ID+".json", &msgs)
		st.msgs = msgs
		for _, m := range msgs {
			if m.ID > c.lastID {
				c.lastID = m.ID
			}
			if m.Ver > c.ver {
				c.ver = m.Ver
			}
		}
	}
	for _, st := range c.rooms {
		st.ver = c.ver
	}
	return c
}

func (c *Chat) loadJSON(key string, v any) {
	if c.s3 == nil {
		return
	}
	b, ok, err := c.s3.Get(key)
	if err != nil {
		log.Println("chat load", key, err)
		return
	}
	if ok {
		if err := json.Unmarshal(b, v); err != nil {
			log.Println("chat parse", key, err)
		}
	}
}

func (c *Chat) room(id string) *chatRoomState {
	st := c.rooms[id]
	if st == nil {
		st = &chatRoomState{wake: make(chan struct{}), seen: map[string]time.Time{}, last: map[string]time.Time{}}
		c.rooms[id] = st
	}
	return st
}

func (c *Chat) roomOK(id string) bool {
	for _, r := range c.cfg.Rooms {
		if r.ID == id {
			return true
		}
	}
	return false
}

func (c *Chat) bump(room string) int64 {
	c.ver++
	st := c.room(room)
	st.ver = c.ver
	st.dirty = true
	close(st.wake)
	st.wake = make(chan struct{})
	return c.ver
}

func (c *Chat) bumpAll() {
	for _, r := range c.cfg.Rooms {
		c.bump(r.ID)
	}
}

func (c *Chat) mark(key string) { c.dirty[key] = true }

func (c *Chat) log(kind string, v any) {
	b, _ := json.Marshal(map[string]any{"t": kind, "at": time.Now().UnixMilli(), "v": v})
	c.journal = append(c.journal, string(b))
}

func (c *Chat) nextID() int64 {
	id := time.Now().UnixMilli() * 1000
	if id <= c.lastID {
		id = c.lastID + 1
	}
	c.lastID = id
	return id
}

func (c *Chat) find(room string, id int64) *ChatMsg {
	st := c.rooms[room]
	if st == nil {
		return nil
	}
	i := sort.Search(len(st.msgs), func(i int) bool { return st.msgs[i].ID >= id })
	if i < len(st.msgs) && st.msgs[i].ID == id {
		return st.msgs[i]
	}
	return nil
}

func (c *Chat) Run() {
	t := time.NewTicker(15 * time.Second)
	for range t.C {
		c.Flush()
	}
}

func (c *Chat) Flush() {
	c.mu.Lock()
	puts := map[string][]byte{}
	for id, st := range c.rooms {
		if st.dirty {
			st.dirty = false
			b, _ := json.Marshal(st.msgs)
			puts["chat/rooms/"+id+".json"] = b
		}
	}
	for key := range c.dirty {
		var v any
		switch key {
		case "config":
			v = c.cfg
		case "members":
			v = c.members
		case "reports":
			v = c.reports
		case "mentions":
			v = c.mentions
		}
		b, _ := json.Marshal(v)
		puts["chat/"+key+".json"] = b
	}
	c.dirty = map[string]bool{}
	if len(c.journal) > 0 {
		now := time.Now().UTC()
		puts[fmt.Sprintf("chat/log/%s/%d.jsonl", now.Format("2006-01-02"), now.UnixNano())] = []byte(strings.Join(c.journal, "\n") + "\n")
		c.journal = nil
	}
	c.mu.Unlock()
	if c.s3 == nil {
		return
	}
	for k, b := range puts {
		if err := c.s3.Put(k, b); err != nil {
			log.Println("chat flush", k, err)
		}
	}
}

func (c *Chat) muted(login string, now time.Time) (ChatMute, bool) {
	m, ok := c.cfg.Mutes[login]
	if !ok {
		return m, false
	}
	if m.Until <= now.UnixMilli() {
		delete(c.cfg.Mutes, login)
		c.mark("config")
		return m, false
	}
	return m, true
}

var (
	linkRe  = regexp.MustCompile(`(?i)(https?://|www\.|t\.me/|telegram\.me|vk\.com|[a-z0-9-]{2,}\.(ru|com|net|org|me|io|su|xyz|info|top|site|online|ly|gg|link|club|pro|shop|store|app|рф)(\b|/))`)
	phoneRe = regexp.MustCompile(`(\+?\d[\d\s\-().]{8,}\d)`)
	mentRe  = regexp.MustCompile(`@([a-z0-9_]{3,24})`)
)

var lookalike = strings.NewReplacer("a", "а", "b", "в", "c", "с", "e", "е", "h", "н", "k", "к", "m", "м", "o", "о", "p", "р", "t", "т", "x", "х", "y", "у", "3", "з", "0", "о", "6", "б", "ё", "е")

var badPrefixes = []string{"хуй", "хуе", "хуя", "хуи", "хую", "пизд", "ебат", "ебан", "ебал", "ебл", "ебу", "ебе", "ебн", "бля", "блят", "мудак", "мудил", "мудо", "пидор", "пидар", "пидр", "гандон", "шлюх", "залуп", "дроч", "сучар", "сучк", "уебок", "уебищ", "еблан", "манда", "мразь", "долбое"}
var badWords = map[string]bool{"сука": true, "суки": true, "сук": true, "суке": true, "суку": true, "сукой": true, "нахуй": true, "похуй": true, "нахер": true, "хер": true}
var verbPrefixes = []string{"", "вы", "за", "на", "от", "отъ", "по", "у", "раз", "разъ", "с", "съ", "об", "объ", "до", "про", "пере", "из", "изъ", "при", "под", "подъ", "в", "въ", "недо"}

func (c *Chat) dirtyText(text string) bool {
	low := lookalike.Replace(strings.ToLower(text))
	words := strings.FieldsFunc(low, func(r rune) bool { return !unicode.IsLetter(r) })
	for _, w := range words {
		if badWords[w] || strings.Contains(w, "пизд") || strings.Contains(w, "хуй") || strings.Contains(w, "долбое") {
			return true
		}
		for _, p := range verbPrefixes {
			if !strings.HasPrefix(w, p) {
				continue
			}
			rest := strings.TrimPrefix(w, p)
			for _, b := range badPrefixes {
				if strings.HasPrefix(rest, b) {
					return true
				}
			}
		}
	}
	for _, x := range c.cfg.Words {
		x = lookalike.Replace(strings.ToLower(strings.TrimSpace(x)))
		if x != "" && strings.Contains(low, x) {
			return true
		}
	}
	return false
}

func blockedText(text string) string {
	if linkRe.MatchString(text) {
		return "Ссылки в чате запрещены"
	}
	digits := 0
	for _, m := range phoneRe.FindAllString(text, -1) {
		n := 0
		for _, r := range m {
			if r >= '0' && r <= '9' {
				n++
			}
		}
		if n > digits {
			digits = n
		}
	}
	if digits >= 10 {
		return "Номера телефонов в чате запрещены"
	}
	return ""
}

func clip(s string, n int) string {
	s = strings.TrimSpace(s)
	if utf8.RuneCountInString(s) <= n {
		return s
	}
	r := []rune(s)
	return strings.TrimSpace(string(r[:n])) + "…"
}

func cleanText(s string) string {
	s = strings.ReplaceAll(s, "\r", "")
	var b strings.Builder
	nl := 0
	for _, r := range s {
		if r == '\n' {
			nl++
			if nl > 2 {
				continue
			}
		} else {
			nl = 0
		}
		if unicode.IsControl(r) && r != '\n' {
			continue
		}
		b.WriteRune(r)
	}
	return strings.TrimSpace(b.String())
}

func (c *Chat) publicMsg(m *ChatMsg) *ChatMsg {
	if m.Gone {
		return &ChatMsg{ID: m.ID, Ver: m.Ver, Room: m.Room, Gone: true}
	}
	return m
}

func (c *Chat) since(st *chatRoomState, after int64) []*ChatMsg {
	out := []*ChatMsg{}
	if after <= 0 {
		start := len(st.msgs)
		n := 0
		for start > 0 && n < chatFirst {
			start--
			if !st.msgs[start].Gone {
				n++
			}
		}
		for _, m := range st.msgs[start:] {
			if !m.Gone {
				out = append(out, m)
			}
		}
		return out
	}
	for _, m := range st.msgs {
		if m.Ver > after {
			out = append(out, c.publicMsg(m))
		}
	}
	return out
}

func (c *Chat) online(now time.Time) (int, map[string]int) {
	all := map[string]bool{}
	per := map[string]int{}
	for id, st := range c.rooms {
		for k, t := range st.seen {
			if now.Sub(t) > chatOnlineFor {
				delete(st.seen, k)
				continue
			}
			all[k] = true
			per[id]++
		}
	}
	return len(all), per
}

func (c *Chat) lastOf(st *chatRoomState) *ChatMsg {
	for i := len(st.msgs) - 1; i >= 0; i-- {
		if !st.msgs[i].Gone {
			return st.msgs[i]
		}
	}
	return nil
}

type chatReq struct {
	Room    string      `json:"room"`
	After   int64       `json:"after"`
	Before  int64       `json:"before"`
	ID      int64       `json:"id"`
	Text    string      `json:"text"`
	Reply   int64       `json:"reply"`
	Quote   *ChatQuote  `json:"quote"`
	Book    *ChatBook   `json:"book"`
	Kind    string      `json:"kind"`
	Reason  string      `json:"reason"`
	Login   string      `json:"login"`
	Op      string      `json:"op"`
	Hours   int         `json:"hours"`
	Slow    int         `json:"slow"`
	On      bool        `json:"on"`
	Words   []string    `json:"words"`
	Rooms   []ChatRoom  `json:"rooms"`
	Viewer  string      `json:"viewer"`
	Mention int64       `json:"mention"`
	Profile *ChatMember `json:"profile"`
}

var reactKinds = map[string]bool{"heart": true, "flame": true, "thumb": true, "smile": true, "mark": true}
var reportReasons = map[string]string{"spam": "спам", "rude": "грубость", "spoiler": "спойлер", "other": "другое"}

func (a *App) chatRoute(w http.ResponseWriter, r *http.Request, ip string) {
	var b chatReq
	if e := decodeChat(r, &b); e != nil {
		reply(w, e.status, map[string]string{"error": e.code, "message": e.text})
		return
	}
	login := ""
	if bearer(r) != "" {
		a.st.mu.Lock()
		u, e := a.authed(r)
		a.st.mu.Unlock()
		if e != nil && e.code != "auth" {
			reply(w, e.status, map[string]string{"error": e.code, "message": e.text})
			return
		}
		if u != nil {
			login = u.Login
		}
	}
	viewer := login
	if viewer == "" {
		viewer = "g:" + ip + ":" + clip(b.Viewer, 40)
	}
	op := strings.TrimPrefix(r.URL.Path, "/v1/chat/")
	if op == "poll" {
		a.chatPoll(w, r, &b, viewer, login)
		return
	}
	c := a.chat
	c.mu.Lock()
	out, e := c.handle(op, &b, login, viewer, time.Now())
	c.mu.Unlock()
	if e != nil {
		reply(w, e.status, map[string]string{"error": e.code, "message": e.text})
		return
	}
	reply(w, 200, out)
}

func decodeChat(r *http.Request, v any) *apiError {
	r.Body = http.MaxBytesReader(nil, r.Body, 32<<10)
	if err := json.NewDecoder(r.Body).Decode(v); err != nil {
		return fail(400, "bad_request", "Неверный запрос")
	}
	return nil
}

func (a *App) chatPoll(w http.ResponseWriter, r *http.Request, b *chatReq, viewer, login string) {
	c := a.chat
	deadline := time.Now().Add(chatPollWait)
	for {
		c.mu.Lock()
		if !c.roomOK(b.Room) {
			c.mu.Unlock()
			reply(w, 404, map[string]string{"error": "room", "message": "Такой комнаты нет"})
			return
		}
		st := c.room(b.Room)
		st.seen[viewer] = time.Now()
		if !c.cfg.On {
			c.mu.Unlock()
			reply(w, 200, map[string]any{"off": true, "ver": c.ver})
			return
		}
		if st.ver > b.After || b.After <= 0 {
			msgs := c.since(st, b.After)
			_, per := c.online(time.Now())
			res := map[string]any{"msgs": msgs, "ver": st.ver, "online": per[b.Room], "slow": c.cfg.Slow}
			c.mu.Unlock()
			reply(w, 200, res)
			return
		}
		wake := st.wake
		c.mu.Unlock()
		left := time.Until(deadline)
		if left <= 0 {
			c.mu.Lock()
			_, per := c.online(time.Now())
			res := map[string]any{"msgs": []*ChatMsg{}, "ver": b.After, "online": per[b.Room], "slow": c.cfg.Slow}
			c.mu.Unlock()
			reply(w, 200, res)
			return
		}
		select {
		case <-wake:
		case <-time.After(left):
		case <-r.Context().Done():
			return
		}
	}
}

func (c *Chat) handle(op string, b *chatReq, login, viewer string, now time.Time) (any, *apiError) {
	switch op {
	case "state":
		return c.state(b, login, now), nil
	case "history":
		return c.history(b)
	case "profile":
		return c.profile(b.Login)
	}
	if login == "" {
		return nil, fail(401, "auth", "Войди в аккаунт, чтобы писать в сообществе")
	}
	switch op {
	case "accept":
		m := c.member(login, now)
		m.Accepted = now.UnixMilli()
		c.mark("members")
		return map[string]any{"ok": true}, nil
	case "me":
		return c.saveProfile(b, login, now)
	case "send":
		return c.send(b, login, now)
	case "react":
		return c.react(b, login)
	case "report":
		return c.report(b, login, now)
	case "reports":
		if !c.admins[login] {
			return nil, fail(403, "admin", "Нет доступа")
		}
		return c.reportList(), nil
	case "admin":
		if !c.admins[login] {
			return nil, fail(403, "admin", "Нет доступа")
		}
		return c.admin(b, login, now)
	}
	return nil, fail(404, "not_found", "Неизвестный запрос")
}

func (c *Chat) member(login string, now time.Time) *ChatMember {
	m := c.members[login]
	if m == nil {
		m = &ChatMember{Since: now.UnixMilli()}
		c.members[login] = m
		c.mark("members")
	}
	return m
}

func (c *Chat) state(b *chatReq, login string, now time.Time) map[string]any {
	total, per := c.online(now)
	rooms := []map[string]any{}
	var latest *ChatMsg
	for _, r := range c.cfg.Rooms {
		st := c.room(r.ID)
		item := map[string]any{"id": r.ID, "title": r.Title, "icon": r.Icon, "online": per[r.ID], "ver": st.ver}
		if m := c.lastOf(st); m != nil {
			item["last"] = map[string]any{"id": m.ID, "login": m.Login, "text": clip(previewText(m), 90), "at": m.At}
			if latest == nil || m.ID > latest.ID {
				latest = m
			}
		}
		rooms = append(rooms, item)
	}
	me := map[string]any{"login": login}
	if login != "" {
		m := c.members[login]
		me["accepted"] = m != nil && m.Accepted > 0
		me["admin"] = c.admins[login]
		if mu, ok := c.muted(login, now); ok {
			me["muted"] = mu.Until
			me["why"] = mu.Why
		}
		if why, ok := c.cfg.Bans[login]; ok {
			me["banned"] = true
			me["why"] = why
		}
		ms := []ChatMention{}
		for _, x := range c.mentions[login] {
			if x.ID > b.Mention {
				ms = append(ms, x)
			}
		}
		me["mentions"] = ms
		if c.admins[login] {
			n := 0
			for _, r := range c.reports {
				if r.State == "new" || r.State == "hidden" {
					n++
				}
			}
			me["reports"] = n
		}
	}
	res := map[string]any{"on": c.cfg.On, "slow": c.cfg.Slow, "online": total, "rooms": rooms, "me": me}
	if c.cfg.Pin != nil && c.cfg.Pin.Text != "" {
		res["pin"] = c.cfg.Pin
	}
	if latest != nil {
		title := latest.Room
		for _, r := range c.cfg.Rooms {
			if r.ID == latest.Room {
				title = r.Title
			}
		}
		res["latest"] = map[string]any{"id": latest.ID, "room": latest.Room, "roomTitle": title, "login": latest.Login, "text": clip(previewText(latest), 90), "at": latest.At}
	}
	return res
}

func previewText(m *ChatMsg) string {
	switch {
	case m.Text != "":
		return m.Text
	case m.Quote != nil:
		return "Цитата: «" + m.Quote.Text + "»"
	case m.Book != nil:
		return "Книга: " + m.Book.Title
	}
	return ""
}

func (c *Chat) history(b *chatReq) (any, *apiError) {
	if !c.roomOK(b.Room) {
		return nil, fail(404, "room", "Такой комнаты нет")
	}
	st := c.room(b.Room)
	out := []*ChatMsg{}
	for i := len(st.msgs) - 1; i >= 0 && len(out) < chatPage; i-- {
		m := st.msgs[i]
		if m.ID < b.Before && !m.Gone {
			out = append(out, m)
		}
	}
	for i, j := 0, len(out)-1; i < j; i, j = i+1, j-1 {
		out[i], out[j] = out[j], out[i]
	}
	return map[string]any{"msgs": out, "more": len(out) == chatPage}, nil
}

func (c *Chat) profile(login string) (any, *apiError) {
	login = strings.ToLower(strings.TrimSpace(login))
	m := c.members[login]
	if m == nil {
		return nil, fail(404, "member", "Такого читателя нет")
	}
	return map[string]any{"login": login, "admin": c.admins[login], "since": m.Since, "reading": m.Reading, "progress": m.Progress, "books": m.Books, "streak": m.Streak, "hours": m.Hours, "achv": m.Achv}, nil
}

func (c *Chat) saveProfile(b *chatReq, login string, now time.Time) (any, *apiError) {
	m := c.member(login, now)
	if p := b.Profile; p != nil {
		m.Reading = clip(p.Reading, 80)
		m.Progress = bound(p.Progress, 0, 100)
		m.Books = bound(p.Books, 0, 100000)
		m.Streak = bound(p.Streak, 0, 100000)
		m.Hours = bound(p.Hours, 0, 1000000)
		achv := []string{}
		for _, x := range p.Achv {
			if len(achv) < 8 && len(x) <= 32 {
				achv = append(achv, x)
			}
		}
		m.Achv = achv
		c.mark("members")
	}
	return map[string]any{"ok": true}, nil
}

func bound(v, lo, hi int) int {
	if v < lo {
		return lo
	}
	if v > hi {
		return hi
	}
	return v
}

func (c *Chat) send(b *chatReq, login string, now time.Time) (any, *apiError) {
	admin := c.admins[login]
	if !c.cfg.On && !admin {
		return nil, fail(403, "off", "Сообщество временно выключено")
	}
	if !c.roomOK(b.Room) {
		return nil, fail(404, "room", "Такой комнаты нет")
	}
	m := c.member(login, now)
	if m.Accepted == 0 {
		return nil, fail(403, "rules", "Сначала прими правила сообщества")
	}
	if why, ok := c.cfg.Bans[login]; ok && !admin {
		return nil, fail(403, "banned", "Ты не можешь писать в сообществе. Причина: "+why)
	}
	if mu, ok := c.muted(login, now); ok && !admin {
		left := time.Until(time.UnixMilli(mu.Until)).Round(time.Minute)
		return nil, fail(403, "muted", fmt.Sprintf("Писать можно будет через %s", humanLeft(left)))
	}
	st := c.room(b.Room)
	if !admin && c.cfg.Slow > 0 {
		if t, ok := st.last[login]; ok {
			if wait := time.Duration(c.cfg.Slow)*time.Second - now.Sub(t); wait > 0 {
				return nil, fail(429, "slow", fmt.Sprintf("Медленный режим: подожди %d с", int(wait.Seconds()+0.99)))
			}
		}
	}
	text := clip(cleanText(b.Text), chatMaxText)
	msg := &ChatMsg{Room: b.Room, Login: login, Admin: admin, Text: text, At: now.UnixMilli(), Reading: m.Reading}
	if q := b.Quote; q != nil && strings.TrimSpace(q.Text) != "" {
		msg.Quote = &ChatQuote{Text: clip(cleanText(q.Text), 600), Book: clip(q.Book, 120), Author: clip(q.Author, 80), Chapter: clip(q.Chapter, 60)}
	}
	if bk := b.Book; bk != nil && strings.TrimSpace(bk.Title) != "" {
		msg.Book = &ChatBook{Title: clip(bk.Title, 120), Author: clip(bk.Author, 80), Tint: bound(bk.Tint, 0, 11)}
	}
	if msg.Text == "" && msg.Quote == nil && msg.Book == nil {
		return nil, fail(400, "empty", "Пустое сообщение")
	}
	if !admin {
		all := msg.Text
		if msg.Quote != nil {
			all += " " + msg.Quote.Text + " " + msg.Quote.Book
		}
		if msg.Book != nil {
			all += " " + msg.Book.Title + " " + msg.Book.Author
		}
		if why := blockedText(all); why != "" {
			return nil, fail(400, "filter", why)
		}
		if c.dirtyText(all) {
			return nil, fail(400, "filter", "Сообщение не прошло фильтр: без мата и оскорблений")
		}
		key := login + "\n" + strings.ToLower(all)
		if c.recent[key] != "" {
			return nil, fail(429, "repeat", "Такое сообщение уже отправлено")
		}
		if len(c.recent) > 5000 {
			c.recent = map[string]string{}
		}
		c.recent[key] = b.Room
		time.AfterFunc(time.Minute, func() {
			c.mu.Lock()
			delete(c.recent, key)
			c.mu.Unlock()
		})
	}
	if b.Reply > 0 {
		if r := c.find(b.Room, b.Reply); r != nil && !r.Gone {
			msg.Reply = &ChatRef{ID: r.ID, Login: r.Login, Text: clip(previewText(r), 80)}
		}
	}
	msg.ID = c.nextID()
	msg.Ver = c.bump(b.Room)
	st.msgs = append(st.msgs, msg)
	if len(st.msgs) > chatKeep {
		st.msgs = append([]*ChatMsg(nil), st.msgs[len(st.msgs)-chatKeep:]...)
	}
	st.last[login] = now
	targets := map[string]bool{}
	if msg.Reply != nil && msg.Reply.Login != login {
		targets[msg.Reply.Login] = true
	}
	for _, x := range mentRe.FindAllStringSubmatch(strings.ToLower(msg.Text), 5) {
		if x[1] != login && c.members[x[1]] != nil {
			targets[x[1]] = true
		}
	}
	for t := range targets {
		list := append(c.mentions[t], ChatMention{ID: msg.ID, Room: msg.Room, From: login, Text: clip(previewText(msg), 90), At: msg.At})
		if len(list) > 30 {
			list = list[len(list)-30:]
		}
		c.mentions[t] = list
		c.mark("mentions")
	}
	c.log("send", msg)
	return map[string]any{"msg": msg}, nil
}

func humanLeft(d time.Duration) string {
	if d < time.Minute {
		return "минуту"
	}
	h := int(d.Hours())
	m := int(d.Minutes()) % 60
	if h == 0 {
		return fmt.Sprintf("%d мин", m)
	}
	if m == 0 {
		return fmt.Sprintf("%d ч", h)
	}
	return fmt.Sprintf("%d ч %d мин", h, m)
}

func (c *Chat) react(b *chatReq, login string) (any, *apiError) {
	if !reactKinds[b.Kind] {
		return nil, fail(400, "bad_request", "Неверная реакция")
	}
	m := c.find(b.Room, b.ID)
	if m == nil || m.Gone {
		return nil, fail(404, "gone", "Сообщение удалено")
	}
	if _, ok := c.cfg.Bans[login]; ok {
		return nil, fail(403, "banned", "Ты не можешь писать в сообществе")
	}
	if m.Reacts == nil {
		m.Reacts = map[string][]string{}
	}
	list := m.Reacts[b.Kind]
	idx := -1
	for i, x := range list {
		if x == login {
			idx = i
		}
	}
	if idx >= 0 {
		list = append(list[:idx], list[idx+1:]...)
	} else {
		list = append(list, login)
	}
	if len(list) == 0 {
		delete(m.Reacts, b.Kind)
	} else {
		m.Reacts[b.Kind] = list
	}
	m.Ver = c.bump(b.Room)
	return map[string]any{"msg": m}, nil
}

func (c *Chat) report(b *chatReq, login string, now time.Time) (any, *apiError) {
	reason, ok := reportReasons[b.Reason]
	if !ok {
		reason = reportReasons["other"]
	}
	m := c.find(b.Room, b.ID)
	if m == nil || m.Gone {
		return map[string]any{"ok": true}, nil
	}
	if m.Login == login {
		return nil, fail(400, "self", "Нельзя пожаловаться на своё сообщение")
	}
	rep := c.reports[m.ID]
	if rep == nil {
		rep = &ChatReport{Msg: *m, Reasons: map[string]string{}, State: "new", At: now.UnixMilli()}
		c.reports[m.ID] = rep
	}
	if rep.State == "kept" {
		return map[string]any{"ok": true}, nil
	}
	rep.Reasons[login] = reason
	if len(rep.Reasons) >= chatHideAt && rep.State == "new" {
		rep.State = "hidden"
		m.Gone = true
		m.Ver = c.bump(m.Room)
		c.log("autohide", m.ID)
		if c.notify != nil {
			c.notify(fmt.Sprintf("Сообщество: скрыто сообщение %s после %d жалоб: %s", m.Login, len(rep.Reasons), clip(previewText(m), 120)))
		}
	} else if len(rep.Reasons) == 1 && c.notify != nil {
		c.notify(fmt.Sprintf("Сообщество: жалоба (%s) на %s: %s", reason, m.Login, clip(previewText(m), 120)))
	}
	c.mark("reports")
	return map[string]any{"ok": true}, nil
}

func (c *Chat) reportList() map[string]any {
	list := []map[string]any{}
	for _, r := range c.reports {
		if r.State != "new" && r.State != "hidden" {
			continue
		}
		counts := map[string]int{}
		for _, x := range r.Reasons {
			counts[x]++
		}
		list = append(list, map[string]any{"msg": r.Msg, "count": len(r.Reasons), "reasons": counts, "state": r.State, "at": r.At})
	}
	sort.Slice(list, func(i, j int) bool { return list[i]["at"].(int64) > list[j]["at"].(int64) })
	mutes := []map[string]any{}
	for l, m := range c.cfg.Mutes {
		mutes = append(mutes, map[string]any{"login": l, "until": m.Until, "why": m.Why})
	}
	bans := []map[string]any{}
	for l, why := range c.cfg.Bans {
		bans = append(bans, map[string]any{"login": l, "why": why})
	}
	return map[string]any{"reports": list, "mutes": mutes, "bans": bans, "words": c.cfg.Words, "rooms": c.cfg.Rooms, "on": c.cfg.On, "slow": c.cfg.Slow, "pin": c.cfg.Pin}
}

func (c *Chat) remove(m *ChatMsg, by string) {
	if m.Gone {
		return
	}
	m.Gone = true
	m.Ver = c.bump(m.Room)
	c.log("delete", map[string]any{"id": m.ID, "by": by})
}

func (c *Chat) admin(b *chatReq, login string, now time.Time) (any, *apiError) {
	target := strings.ToLower(strings.TrimSpace(b.Login))
	why := clip(b.Reason, 120)
	switch b.Op {
	case "power":
		c.cfg.On = b.On
		c.bumpAll()
	case "slow":
		c.cfg.Slow = bound(b.Slow, 0, 600)
		c.bumpAll()
	case "pin":
		if t := clip(cleanText(b.Text), 300); t != "" {
			c.cfg.Pin = &ChatPin{Text: t, At: now.UnixMilli()}
		} else {
			c.cfg.Pin = nil
		}
	case "words":
		words := []string{}
		for _, w := range b.Words {
			if w = strings.TrimSpace(w); w != "" && len(words) < 2000 {
				words = append(words, clip(w, 40))
			}
		}
		c.cfg.Words = words
	case "rooms":
		rooms := []ChatRoom{}
		seen := map[string]bool{}
		for _, r := range b.Rooms {
			r.ID = strings.ToLower(strings.TrimSpace(r.ID))
			if r.ID == "" || seen[r.ID] || len(r.ID) > 24 || strings.TrimSpace(r.Title) == "" {
				continue
			}
			seen[r.ID] = true
			rooms = append(rooms, ChatRoom{ID: r.ID, Title: clip(r.Title, 40), Icon: clip(r.Icon, 16)})
		}
		if len(rooms) == 0 {
			return nil, fail(400, "rooms", "Нужна хотя бы одна комната")
		}
		c.cfg.Rooms = rooms
		c.bumpAll()
	case "delete":
		m := c.find(b.Room, b.ID)
		if m == nil {
			return nil, fail(404, "gone", "Сообщение не найдено")
		}
		c.remove(m, login)
		if r := c.reports[m.ID]; r != nil {
			r.State = "done"
			c.mark("reports")
		}
	case "keep":
		if r := c.reports[b.ID]; r != nil {
			r.State = "kept"
			if m := c.find(r.Msg.Room, r.Msg.ID); m != nil && m.Gone {
				m.Gone = false
				m.Ver = c.bump(m.Room)
			}
			c.mark("reports")
		}
	case "mute":
		if target == "" {
			return nil, fail(400, "login", "Нужен логин")
		}
		hours := bound(b.Hours, 1, 24*30)
		c.cfg.Mutes[target] = ChatMute{Until: now.Add(time.Duration(hours) * time.Hour).UnixMilli(), Why: why}
		c.settle(b, login)
		c.log("mute", map[string]any{"login": target, "hours": hours, "why": why})
	case "unmute":
		delete(c.cfg.Mutes, target)
	case "ban":
		if target == "" {
			return nil, fail(400, "login", "Нужен логин")
		}
		if why == "" {
			why = "нарушение правил"
		}
		c.cfg.Bans[target] = why
		c.settle(b, login)
		for _, st := range c.rooms {
			for _, m := range st.msgs {
				if m.Login == target && now.UnixMilli()-m.At < 24*3600*1000 {
					c.remove(m, login)
				}
			}
		}
		c.log("ban", map[string]any{"login": target, "why": why})
	case "unban":
		delete(c.cfg.Bans, target)
	default:
		return nil, fail(400, "bad_request", "Неизвестное действие")
	}
	c.mark("config")
	return c.reportList(), nil
}

func (c *Chat) settle(b *chatReq, login string) {
	if b.ID == 0 {
		return
	}
	room := b.Room
	if r := c.reports[b.ID]; r != nil {
		room = r.Msg.Room
		r.State = "done"
		c.mark("reports")
	}
	if m := c.find(room, b.ID); m != nil {
		c.remove(m, login)
	}
}
