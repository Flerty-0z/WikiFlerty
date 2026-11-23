package main

import (
	"crypto/md5"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"html/template"
	"log"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"
)

const period = 30 * 24 * time.Hour

type Plan struct {
	ID    string
	Title string
	Limit int64
	Price int64
}

var plans = map[string]Plan{
	"plus":  {"plus", "Облако+", 5 * gb, 79},
	"plus2": {"plus2", "Облако++", 15 * gb, 149},
}

type Robokassa struct {
	Login string
	Pass1 string
	Pass2 string
	Test1 string
	Test2 string
	Hash  string
	Test  bool
}

func robokassaFromEnv() *Robokassa {
	return &Robokassa{
		Login: os.Getenv("ROBOKASSA_LOGIN"),
		Pass1: os.Getenv("ROBOKASSA_PASS1"),
		Pass2: os.Getenv("ROBOKASSA_PASS2"),
		Test1: os.Getenv("ROBOKASSA_TEST1"),
		Test2: os.Getenv("ROBOKASSA_TEST2"),
		Hash:  strings.ToLower(os.Getenv("ROBOKASSA_HASH")),
		Test:  os.Getenv("ROBOKASSA_TEST") == "1",
	}
}

func (k *Robokassa) ready() bool {
	if k == nil || k.Login == "" {
		return false
	}
	if k.Test {
		return k.Test1 != "" && k.Test2 != ""
	}
	return k.Pass1 != "" && k.Pass2 != ""
}

func (k *Robokassa) sum(s string) string {
	if k.Hash == "md5" {
		h := md5.Sum([]byte(s))
		return hex.EncodeToString(h[:])
	}
	h := sha256.Sum256([]byte(s))
	return hex.EncodeToString(h[:])
}

func (k *Robokassa) pass1(test bool) string {
	if test {
		return k.Test1
	}
	return k.Pass1
}

func (k *Robokassa) pass2(test bool) string {
	if test {
		return k.Test2
	}
	return k.Pass2
}

type Order struct {
	Inv     int64  `json:"inv"`
	Login   string `json:"login"`
	Plan    string `json:"plan"`
	Price   int64  `json:"price"`
	Test    bool   `json:"test"`
	Created int64  `json:"created"`
	Paid    int64  `json:"paid"`
}

func orderKey(inv int64) string { return "orders/" + strconv.FormatInt(inv, 10) + ".json" }

func (st *Store) loadOrder(inv int64) (*Order, error) {
	b, ok, err := st.s3.Get(orderKey(inv))
	if err != nil {
		return nil, err
	}
	if !ok {
		return nil, errNotFound
	}
	o := &Order{}
	if err := json.Unmarshal(b, o); err != nil {
		return nil, err
	}
	return o, nil
}

func (st *Store) saveOrder(o *Order) error {
	b, _ := json.Marshal(o)
	return st.s3.Put(orderKey(o.Inv), b)
}

func newInv() int64 {
	b := make([]byte, 4)
	rand.Read(b)
	return int64(binary.BigEndian.Uint32(b)&0x7fffffff) | 1<<20
}

func (u *User) active(now time.Time) (Plan, bool) {
	p, ok := plans[u.Paid]
	if !ok || u.Until <= now.UnixMilli() {
		return Plan{}, false
	}
	return p, true
}

func (u *User) Quota(now time.Time) int64 {
	if p, ok := u.active(now); ok && p.Limit > u.Limit {
		return p.Limit
	}
	return u.Limit
}

func (u *User) extend(p Plan, now time.Time) {
	start := now.UnixMilli()
	var carry int64
	if old, ok := u.active(now); ok {
		left := u.Until - start
		if old.ID == p.ID {
			carry = left
		} else {
			carry = left * old.Price / p.Price
		}
	}
	u.Paid = p.ID
	u.Until = start + carry + period.Milliseconds()
}

func receipt(p Plan) string {
	item := map[string]any{
		"name": "WikiFlerty: " + p.Title + " " + human(p.Limit) + " на 30 дней", "quantity": 1, "sum": p.Price,
		"tax": "none", "payment_method": "full_payment", "payment_object": "service",
	}
	b, _ := json.Marshal(map[string]any{"items": []any{item}})
	return url.QueryEscape(string(b))
}

func (a *App) payCreate(r *http.Request, u *User) (any, *apiError) {
	var b struct {
		Plan string `json:"plan"`
	}
	if e := decode(r, &b); e != nil {
		return nil, e
	}
	p, ok := plans[b.Plan]
	if !ok {
		return nil, fail(400, "bad_request", "Неизвестный тариф")
	}
	k := a.kassa
	if !k.ready() {
		return nil, fail(503, "pay_off", "Оплата пока не подключена, попробуй позже")
	}
	if !a.limiter.allow("pay:"+u.Login, 10, time.Hour) {
		return nil, fail(429, "slow", "Слишком много попыток оплаты, попробуй через час")
	}
	var o *Order
	for i := 0; i < 5; i++ {
		inv := newInv()
		if _, err := a.st.loadOrder(inv); err == errNotFound {
			o = &Order{Inv: inv, Login: u.Login, Plan: p.ID, Price: p.Price, Test: k.Test, Created: time.Now().UnixMilli()}
			break
		} else if err != nil {
			return nil, fail(503, "storage", "Облако временно недоступно")
		}
	}
	if o == nil {
		return nil, fail(503, "storage", "Облако временно недоступно")
	}
	if err := a.st.saveOrder(o); err != nil {
		return nil, fail(503, "storage", "Облако временно недоступно")
	}
	out := strconv.FormatInt(p.Price, 10)
	inv := strconv.FormatInt(o.Inv, 10)
	rec := receipt(p)
	q := url.Values{
		"MerchantLogin":  {k.Login},
		"OutSum":         {out},
		"InvId":          {inv},
		"Description":    {"WikiFlerty: " + p.Title + " на 30 дней"},
		"Receipt":        {rec},
		"Culture":        {"ru"},
		"Encoding":       {"utf-8"},
		"SignatureValue": {k.sum(k.Login + ":" + out + ":" + inv + ":" + rec + ":" + k.pass1(o.Test))},
	}
	if o.Test {
		q.Set("IsTest", "1")
	}
	return map[string]any{"url": "https://auth.robokassa.ru/Merchant/Index.aspx?" + q.Encode(), "inv": o.Inv}, nil
}

func (a *App) payResult(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, 64<<10)
	if err := r.ParseForm(); err != nil {
		http.Error(w, "bad request", 400)
		return
	}
	k := a.kassa
	outSum := r.Form.Get("OutSum")
	invText := r.Form.Get("InvId")
	sig := strings.ToLower(r.Form.Get("SignatureValue"))
	test := r.Form.Get("IsTest") == "1"
	inv, err := strconv.ParseInt(invText, 10, 64)
	if err != nil || k == nil || k.Login == "" || k.pass2(test) == "" {
		http.Error(w, "bad request", 400)
		return
	}
	want := k.sum(outSum + ":" + invText + ":" + k.pass2(test))
	if subtle.ConstantTimeCompare([]byte(sig), []byte(want)) != 1 {
		log.Println("pay: bad signature", invText)
		http.Error(w, "bad sign", 400)
		return
	}
	a.st.mu.Lock()
	defer a.st.mu.Unlock()
	o, err := a.st.loadOrder(inv)
	if err == errNotFound {
		http.Error(w, "unknown order", 404)
		return
	}
	if err != nil {
		http.Error(w, "storage", 503)
		return
	}
	if o.Paid != 0 {
		fmt.Fprintf(w, "OK%d", o.Inv)
		return
	}
	paid, _ := strconv.ParseFloat(outSum, 64)
	if test != o.Test || paid+0.001 < float64(o.Price) {
		log.Println("pay: mismatch", invText, outSum, test)
		http.Error(w, "mismatch", 400)
		return
	}
	p, ok := plans[o.Plan]
	if !ok {
		http.Error(w, "plan", 400)
		return
	}
	u, err := a.st.load(o.Login)
	if err == errNotFound {
		o.Paid = time.Now().UnixMilli()
		a.st.saveOrder(o)
		a.notify(fmt.Sprintf("Оплата #%d на %d ₽ пришла, но аккаунт %s уже удалён. Нужен возврат.", o.Inv, o.Price, o.Login))
		fmt.Fprintf(w, "OK%d", o.Inv)
		return
	}
	if err != nil {
		http.Error(w, "storage", 503)
		return
	}
	prevPaid, prevUntil := u.Paid, u.Until
	u.extend(p, time.Now())
	if err := a.st.save(u); err != nil {
		u.Paid, u.Until = prevPaid, prevUntil
		http.Error(w, "storage", 503)
		return
	}
	o.Paid = time.Now().UnixMilli()
	if err := a.st.saveOrder(o); err != nil {
		log.Println("pay: order save", err)
	}
	mark := ""
	if o.Test {
		mark = " (тест)"
	}
	a.notify(fmt.Sprintf("Оплата%s: %s — %s, %d ₽", mark, u.Login, p.Title, o.Price))
	fmt.Fprintf(w, "OK%d", o.Inv)
}

var okPage = template.Must(template.ParseFS(siteFiles, "site/ok.html"))

func (a *App) payOk(w http.ResponseWriter, r *http.Request) {
	data := struct {
		Plan string
		Grow string
		Wide bool
	}{"Тариф подключается", "больше места", false}
	q := r.URL.Query()
	out, invText, sig := q.Get("OutSum"), q.Get("InvId"), strings.ToLower(q.Get("SignatureValue"))
	k := a.kassa
	if inv, err := strconv.ParseInt(invText, 10, 64); err == nil && k != nil && sig != "" {
		valid := false
		for _, pass := range []string{k.Pass1, k.Test1} {
			if pass != "" && subtle.ConstantTimeCompare([]byte(sig), []byte(k.sum(out+":"+invText+":"+pass))) == 1 {
				valid = true
			}
		}
		if valid {
			a.st.mu.Lock()
			if o, err := a.st.loadOrder(inv); err == nil {
				if p, ok := plans[o.Plan]; ok {
					data.Plan = p.Title + " · " + human(p.Limit)
					data.Wide = p.ID == "plus2"
					data.Grow = "+" + human(p.Limit)
					if u, err := a.st.load(o.Login); err == nil && p.Limit > u.Limit {
						data.Grow = "+" + human(p.Limit-u.Limit)
					}
					data.Grow = strings.Replace(data.Grow, ".", ",", 1)
					data.Plan = strings.Replace(data.Plan, ".", ",", 1)
				}
			}
			a.st.mu.Unlock()
		}
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	okPage.Execute(w, data)
}
