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
	Edited  bool                `json:"edited,omitempty"`
	Ch      int                 `json:"ch,omitempty"`
	ChTitle string              `json:"chTitle,omitempty"`
	Kind    string              `json:"kind,omitempty"`
}

type ChatThread struct {
	Title  string `json:"title"`
	Author string `json:"author,omitempty"`
	Last   int64  `json:"last"`
	Count  int    `json:"count"`
}

type SprintSeat struct {
	Pages  int   `json:"pages"`
	Joined int64 `json:"joined"`
}

type ChatSprint struct {
	ID      int64                  `json:"id"`
	By      string                 `json:"by"`
	Start   int64                  `json:"start"`
	End     int64                  `json:"end"`
	Minutes int                    `json:"minutes"`
	People  map[string]*SprintSeat `json:"people"`
}

type ChatGoal struct {
	Month   string         `json:"month"`
	Target  int            `json:"target"`
	Total   int            `json:"total"`
	Readers map[string]int `json:"readers"`
	Day     string         `json:"day"`
	Today   map[string]int `json:"today"`
}

type ChatIdea struct {
	ID     int64    `json:"id"`
	Login  string   `json:"login"`
	Title  string   `json:"title"`
	Text   string   `json:"text,omitempty"`
	At     int64    `json:"at"`
	Votes  []string `json:"votes"`
	Status string   `json:"status"`
	Note   string   `json:"note,omitempty"`
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
	ID    int64  `json:"id"`
	Room  string `json:"room"`
	From  string `json:"from"`
	Text  string `json:"text"`
	At    int64  `json:"at"`
	Title string `json:"title,omitempty"`
}

type chatRoomState struct {
	msgs   []*ChatMsg
	ver    int64
	wake   chan struct{}
	seen   map[string]time.Time
	dirty  bool
	last   map[string]time.Time
	loaded bool
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
	threads  map[string]*ChatThread
	sprint   *ChatSprint
	lastRun  *ChatSprint
	starts   map[string]time.Time
	goal     *ChatGoal
	ideas    []*ChatIdea
	ideaDay  map[string]int
}

var bookRoomRe = regexp.MustCompile(`^b:[0-9a-f]{16}$`)

func isBook(id string) bool { return bookRoomRe.MatchString(id) }

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
		threads: map[string]*ChatThread{}, starts: map[string]time.Time{}, ideaDay: map[string]int{},
		goal: &ChatGoal{Target: 100000, Readers: map[string]int{}, Today: map[string]int{}},
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
	c.loadJSON("chat/threads.json", &c.threads)
	c.loadJSON("chat/goal.json", c.goal)
	c.loadJSON("chat/ideas.json", &c.ideas)
	if c.threads == nil {
		c.threads = map[string]*ChatThread{}
	}
	if c.goal.Readers == nil {
		c.goal.Readers = map[string]int{}
	}
	if c.goal.Today == nil {
		c.goal.Today = map[string]int{}
	}
	if c.goal.Target <= 0 || c.goal.Target == 50000 {
		c.goal.Target = 100000
	}
	for _, i := range c.ideas {
		if i.ID > c.lastID {
			c.lastID = i.ID
		}
	}
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
		c.room(r.ID)
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
	if !st.loaded {
		st.loaded = true
		var msgs []*ChatMsg
		c.loadJSON(roomKey(id), &msgs)
		st.msgs = msgs
		for _, m := range msgs {
			if m.ID > c.lastID {
				c.lastID = m.ID
			}
			if m.Ver > c.ver {
				c.ver = m.Ver
			}
		}
		st.ver = c.ver
	}
	return st
}

func roomKey(id string) string {
	if isBook(id) {
		return "chat/books/" + strings.TrimPrefix(id, "b:") + ".json"
	}
	return "chat/rooms/" + id + ".json"
}

func (c *Chat) roomOK(id string) bool {
	if isBook(id) {
		return true
	}
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
			puts[roomKey(id)] = b
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
		case "threads":
			v = c.threads
		case "goal":
			v = c.goal
		case "ideas":
			v = c.ideas
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

func visible(m *ChatMsg, upto int) bool { return !m.Gone && m.Ch <= upto }

func (c *Chat) since(st *chatRoomState, after int64, upto int) []*ChatMsg {
	out := []*ChatMsg{}
	if after <= 0 {
		start := len(st.msgs)
		n := 0
		for start > 0 && n < chatFirst {
			start--
			if visible(st.msgs[start], upto) {
				n++
			}
		}
		for _, m := range st.msgs[start:] {
			if visible(m, upto) {
				out = append(out, m)
			}
		}
		return out
	}
	for _, m := range st.msgs {
		if m.Ver > after && (m.Gone || m.Ch <= upto) {
			out = append(out, c.publicMsg(m))
		}
	}
	return out
}

func locked(st *chatRoomState, upto int) []map[string]any {
	per := map[int]int{}
	titles := map[int]string{}
	for _, m := range st.msgs {
		if !m.Gone && m.Ch > upto {
			per[m.Ch]++
			if titles[m.Ch] == "" {
				titles[m.Ch] = m.ChTitle
			}
		}
	}
	keys := make([]int, 0, len(per))
	for k := range per {
		keys = append(keys, k)
	}
	sort.Ints(keys)
	out := []map[string]any{}
	for _, k := range keys {
		out = append(out, map[string]any{"ch": k, "title": titles[k], "count": per[k]})
	}
	return out
}

func (b *chatReq) limit() int {
	if isBook(b.Room) {
		return b.Upto
	}
	return 1 << 30
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
	Q       string      `json:"q"`
	Upto    int         `json:"upto"`
	Ch      int         `json:"ch"`
	ChTitle string      `json:"chTitle"`
	Title   string      `json:"title"`
	Author  string      `json:"author"`
	Minutes int         `json:"minutes"`
	Pages   int         `json:"pages"`
	Status  string      `json:"status"`
	Target  int         `json:"target"`
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
			msgs := c.since(st, b.After, b.limit())
			_, per := c.online(time.Now())
			res := map[string]any{"msgs": msgs, "ver": st.ver, "online": per[b.Room], "slow": c.cfg.Slow}
			if isBook(b.Room) {
				res["locked"] = locked(st, b.Upto)
			}
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
	case "search":
		return c.search(b)
	case "ideas":
		return c.ideaList(login), nil
	case "sprintState":
		return c.sprintView(login, now), nil
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
	case "edit":
		return c.edit(b, login, now)
	case "unsend":
		return c.unsend(b, login, now)
	case "sprint":
		return c.sprintOp(b, login, now)
	case "goal":
		return c.goalAdd(b, login, now)
	case "idea":
		return c.ideaOp(b, login, now)
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
	c.rollGoal(now)
	res["goal"] = map[string]any{"month": c.goal.Month, "target": c.goal.Target, "total": c.goal.Total, "readers": len(c.goal.Readers)}
	if q := c.quoteOfDay(now); q != nil {
		res["quote"] = q
	}
	if sp := c.sprintView(login, now); sp["sprint"] != nil {
		res["sprint"] = sp["sprint"]
	}
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
		if m.ID < b.Before && visible(m, b.limit()) {
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
	if isBook(b.Room) {
		msg.Ch = bound(b.Ch, 0, 5000)
		msg.ChTitle = clip(b.ChTitle, 60)
		th := c.threads[b.Room]
		if th == nil {
			if strings.TrimSpace(b.Title) == "" {
				return nil, fail(400, "bad_request", "Неизвестная книга")
			}
			th = &ChatThread{}
			c.threads[b.Room] = th
		}
		if t := clip(b.Title, 120); t != "" {
			th.Title = t
			th.Author = clip(b.Author, 80)
		}
		th.Last = now.UnixMilli()
		th.Count++
		c.mark("threads")
	}
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
		if e := c.checkText(all); e != nil {
			return nil, e
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
	keep := chatKeep
	if isBook(b.Room) {
		keep = 1000
	}
	if len(st.msgs) > keep {
		st.msgs = append([]*ChatMsg(nil), st.msgs[len(st.msgs)-keep:]...)
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
		title := ""
		if th := c.threads[msg.Room]; th != nil {
			title = th.Title
		}
		list := append(c.mentions[t], ChatMention{ID: msg.ID, Room: msg.Room, From: login, Text: clip(previewText(msg), 90), At: msg.At, Title: title})
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
	case "goal":
		c.rollGoal(now)
		c.goal.Target = bound(b.Target, 100, 100000000)
		c.mark("goal")
	case "idea":
		for _, i := range c.ideas {
			if i.ID == b.ID {
				switch b.Status {
				case "new", "review", "work", "done", "no":
					i.Status = b.Status
				}
				if b.Text != "" || b.Status != "" {
					i.Note = clip(cleanText(b.Text), 200)
				}
				if b.Op == "idea" && b.Status == "delete" {
					i.Status = "gone"
				}
				c.mark("ideas")
			}
		}
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

func (c *Chat) Forget(login string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	delete(c.members, login)
	delete(c.mentions, login)
	c.mark("members")
	c.mark("mentions")
}

func (c *Chat) checkText(all string) *apiError {
	if why := blockedText(all); why != "" {
		return fail(400, "filter", why)
	}
	if c.dirtyText(all) {
		return fail(400, "filter", "Сообщение не прошло фильтр: без мата и оскорблений")
	}
	return nil
}

const editWindow = 10 * 60 * 1000

func (c *Chat) own(b *chatReq, login string, now time.Time) (*ChatMsg, *apiError) {
	m := c.find(b.Room, b.ID)
	if m == nil || m.Gone {
		return nil, fail(404, "gone", "Сообщение уже удалено")
	}
	if m.Login != login {
		return nil, fail(403, "own", "Это не твоё сообщение")
	}
	if now.UnixMilli()-m.At > editWindow && !c.admins[login] {
		return nil, fail(403, "late", "Изменить или удалить можно в течение 10 минут")
	}
	return m, nil
}

func (c *Chat) edit(b *chatReq, login string, now time.Time) (any, *apiError) {
	m, e := c.own(b, login, now)
	if e != nil {
		return nil, e
	}
	text := clip(cleanText(b.Text), chatMaxText)
	if text == "" && m.Quote == nil && m.Book == nil {
		return nil, fail(400, "empty", "Пустое сообщение")
	}
	if !c.admins[login] {
		if e := c.checkText(text); e != nil {
			return nil, e
		}
	}
	if text == m.Text {
		return map[string]any{"msg": m}, nil
	}
	c.log("edit", map[string]any{"id": m.ID, "old": m.Text, "new": text})
	m.Text = text
	m.Edited = true
	m.Ver = c.bump(m.Room)
	return map[string]any{"msg": m}, nil
}

func (c *Chat) unsend(b *chatReq, login string, now time.Time) (any, *apiError) {
	m, e := c.own(b, login, now)
	if e != nil {
		return nil, e
	}
	c.remove(m, login)
	return map[string]any{"ok": true}, nil
}

func (c *Chat) search(b *chatReq) (any, *apiError) {
	if !c.roomOK(b.Room) {
		return nil, fail(404, "room", "Такой комнаты нет")
	}
	q := strings.ToLower(strings.TrimSpace(b.Q))
	if utf8.RuneCountInString(q) < 2 {
		return map[string]any{"msgs": []*ChatMsg{}}, nil
	}
	st := c.room(b.Room)
	out := []*ChatMsg{}
	for i := len(st.msgs) - 1; i >= 0 && len(out) < 50; i-- {
		m := st.msgs[i]
		if !visible(m, b.limit()) {
			continue
		}
		hay := m.Text + " " + m.Login
		if m.Quote != nil {
			hay += " " + m.Quote.Text + " " + m.Quote.Book
		}
		if m.Book != nil {
			hay += " " + m.Book.Title + " " + m.Book.Author
		}
		if strings.Contains(strings.ToLower(hay), q) {
			out = append(out, m)
		}
	}
	return map[string]any{"msgs": out}, nil
}

func (c *Chat) quoteOfDay(now time.Time) map[string]any {
	var best *ChatMsg
	score := -1
	since := now.Add(-24 * time.Hour).UnixMilli()
	week := now.Add(-7 * 24 * time.Hour).UnixMilli()
	for _, r := range c.cfg.Rooms {
		st := c.rooms[r.ID]
		if st == nil {
			continue
		}
		for _, m := range st.msgs {
			if m.Gone || m.Quote == nil || m.At < week {
				continue
			}
			n := 0
			for _, l := range m.Reacts {
				n += len(l)
			}
			if m.At >= since {
				n += 1000
			}
			if n > score || (n == score && best != nil && m.At > best.At) {
				best, score = m, n
			}
		}
	}
	if best == nil {
		return nil
	}
	n := 0
	for _, l := range best.Reacts {
		n += len(l)
	}
	return map[string]any{"id": best.ID, "room": best.Room, "login": best.Login, "text": best.Quote.Text, "book": best.Quote.Book, "author": best.Quote.Author, "reacts": n}
}

func (c *Chat) sprintAlive(now time.Time) {
	if c.sprint != nil && now.UnixMilli() > c.sprint.End {
		c.lastRun = c.sprint
		c.sprint = nil
	}
	if c.lastRun != nil && now.UnixMilli() > c.lastRun.End+15*60*1000 {
		c.lastRun = nil
	}
}

func sprintJSON(sp *ChatSprint, login string, now time.Time) map[string]any {
	people := []map[string]any{}
	for l, seat := range sp.People {
		people = append(people, map[string]any{"login": l, "pages": seat.Pages})
	}
	sort.Slice(people, func(i, j int) bool {
		if people[i]["pages"].(int) != people[j]["pages"].(int) {
			return people[i]["pages"].(int) > people[j]["pages"].(int)
		}
		return people[i]["login"].(string) < people[j]["login"].(string)
	})
	_, joined := sp.People[login]
	return map[string]any{"id": sp.ID, "by": sp.By, "start": sp.Start, "end": sp.End, "minutes": sp.Minutes, "people": people, "joined": joined, "now": now.UnixMilli(), "done": now.UnixMilli() > sp.End}
}

func (c *Chat) sprintView(login string, now time.Time) map[string]any {
	c.sprintAlive(now)
	switch {
	case c.sprint != nil:
		return map[string]any{"sprint": sprintJSON(c.sprint, login, now)}
	case c.lastRun != nil:
		return map[string]any{"sprint": sprintJSON(c.lastRun, login, now)}
	}
	return map[string]any{"sprint": nil}
}

func (c *Chat) sprintOp(b *chatReq, login string, now time.Time) (any, *apiError) {
	c.sprintAlive(now)
	switch b.Op {
	case "start":
		if !c.cfg.On {
			return nil, fail(403, "off", "Сообщество временно выключено")
		}
		if _, ok := c.cfg.Bans[login]; ok {
			return nil, fail(403, "banned", "Ты не можешь писать в сообществе")
		}
		if c.sprint != nil {
			return nil, fail(409, "busy", "Спринт уже идёт — присоединяйся")
		}
		if t, ok := c.starts[login]; ok && now.Sub(t) < 30*time.Minute && !c.admins[login] {
			return nil, fail(429, "slow", "Новый спринт можно начать через полчаса после прошлого")
		}
		minutes := b.Minutes
		if minutes != 15 && minutes != 25 && minutes != 45 {
			minutes = 25
		}
		start := now.Add(time.Minute).UnixMilli()
		c.sprint = &ChatSprint{ID: c.nextID(), By: login, Start: start, End: start + int64(minutes)*60*1000, Minutes: minutes, People: map[string]*SprintSeat{login: {Joined: now.UnixMilli()}}}
		c.starts[login] = now
		st := c.room("general")
		msg := &ChatMsg{ID: c.nextID(), Room: "general", Login: login, Admin: c.admins[login], Kind: "sprint", Text: fmt.Sprintf("Начинаю спринт чтения на %d минут — присоединяйся!", minutes), At: now.UnixMilli()}
		msg.Ver = c.bump("general")
		st.msgs = append(st.msgs, msg)
		c.log("sprint", c.sprint)
	case "join":
		if c.sprint == nil {
			return nil, fail(404, "none", "Спринт уже закончился")
		}
		if _, ok := c.sprint.People[login]; !ok {
			if len(c.sprint.People) >= 500 {
				return nil, fail(409, "full", "Слишком много участников")
			}
			c.sprint.People[login] = &SprintSeat{Joined: now.UnixMilli()}
		}
	case "leave":
		if c.sprint != nil {
			delete(c.sprint.People, login)
		}
	case "pages":
		sp := c.sprint
		if sp == nil && c.lastRun != nil && now.UnixMilli()-c.lastRun.End < 2*60*1000 {
			sp = c.lastRun
		}
		if sp != nil {
			if seat, ok := sp.People[login]; ok {
				from := sp.Start
				if seat.Joined > from {
					from = seat.Joined
				}
				mins := float64(minI64(now.UnixMilli(), sp.End)-from) / 60000
				cap := int(mins*6) + 2
				seat.Pages = bound(b.Pages, seat.Pages, maxI(cap, seat.Pages))
			}
		}
	}
	return c.sprintView(login, now), nil
}

func minI64(a, b int64) int64 {
	if a < b {
		return a
	}
	return b
}

func maxI(a, b int) int {
	if a > b {
		return a
	}
	return b
}

func (c *Chat) rollGoal(now time.Time) {
	month := now.UTC().Add(3 * time.Hour).Format("2006-01")
	if c.goal.Month != month {
		c.goal.Month = month
		c.goal.Total = 0
		c.goal.Readers = map[string]int{}
		c.mark("goal")
	}
	day := today()
	if c.goal.Day != day {
		c.goal.Day = day
		c.goal.Today = map[string]int{}
		c.mark("goal")
	}
}

func (c *Chat) goalAdd(b *chatReq, login string, now time.Time) (any, *apiError) {
	c.rollGoal(now)
	add := bound(b.Pages, 0, 600)
	room := 1500 - c.goal.Today[login]
	if add > room {
		add = maxI(room, 0)
	}
	if add > 0 {
		c.goal.Today[login] += add
		c.goal.Readers[login] += add
		c.goal.Total += add
		c.mark("goal")
	}
	return map[string]any{"month": c.goal.Month, "target": c.goal.Target, "total": c.goal.Total, "readers": len(c.goal.Readers), "mine": c.goal.Readers[login]}, nil
}

func (c *Chat) ideaList(login string) map[string]any {
	list := []map[string]any{}
	for _, i := range c.ideas {
		if i.Status == "gone" {
			continue
		}
		mine := false
		for _, v := range i.Votes {
			if v == login {
				mine = true
			}
		}
		list = append(list, map[string]any{"id": i.ID, "login": i.Login, "title": i.Title, "text": i.Text, "at": i.At, "votes": len(i.Votes), "voted": mine, "status": i.Status, "note": i.Note})
	}
	return map[string]any{"ideas": list}
}

func (c *Chat) ideaOp(b *chatReq, login string, now time.Time) (any, *apiError) {
	switch b.Op {
	case "add":
		if !c.cfg.On {
			return nil, fail(403, "off", "Сообщество временно выключено")
		}
		if _, ok := c.cfg.Bans[login]; ok {
			return nil, fail(403, "banned", "Ты не можешь писать в сообществе")
		}
		if _, ok := c.muted(login, now); ok {
			return nil, fail(403, "muted", "Пока нельзя писать")
		}
		key := login + "\n" + today()
		if c.ideaDay[key] >= 3 && !c.admins[login] {
			return nil, fail(429, "slow", "Не больше трёх идей в день")
		}
		title := clip(cleanText(b.Title), 80)
		text := clip(cleanText(b.Text), 600)
		if utf8.RuneCountInString(title) < 4 {
			return nil, fail(400, "short", "Опиши идею хотя бы парой слов")
		}
		if !c.admins[login] {
			if e := c.checkText(title + " " + text); e != nil {
				return nil, e
			}
		}
		c.ideaDay[key]++
		idea := &ChatIdea{ID: c.nextID(), Login: login, Title: title, Text: text, At: now.UnixMilli(), Votes: []string{login}, Status: "new"}
		c.ideas = append(c.ideas, idea)
		c.log("idea", idea)
		if c.notify != nil {
			c.notify("Сообщество: новая идея от " + login + ": " + title)
		}
	case "vote":
		for _, i := range c.ideas {
			if i.ID != b.ID || i.Status == "gone" {
				continue
			}
			idx := -1
			for k, v := range i.Votes {
				if v == login {
					idx = k
				}
			}
			if idx >= 0 {
				i.Votes = append(i.Votes[:idx], i.Votes[idx+1:]...)
			} else {
				i.Votes = append(i.Votes, login)
			}
		}
	case "delete":
		for _, i := range c.ideas {
			if i.ID == b.ID && (c.admins[login] || (i.Login == login && now.UnixMilli()-i.At < editWindow)) {
				i.Status = "gone"
			}
		}
	default:
		return nil, fail(400, "bad_request", "Неизвестное действие")
	}
	c.mark("ideas")
	return c.ideaList(login), nil
}
