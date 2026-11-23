(function () {
  var d = document, still = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;
  var sel = "main > *, .frame, .tile, .grid > *, .plans > *, .shelf, .note, .field, .choice";
  var list = Array.prototype.slice.call(d.querySelectorAll(sel)).filter(function (el, i, all) {
    return !all.some(function (o) { return o !== el && o.contains(el) && o.matches(".tile, .frame, .field, .choice, .note"); });
  });
  if (!still && "IntersectionObserver" in window) {
    var seen = new WeakMap();
    list.forEach(function (el) {
      var sibs = el.parentNode ? Array.prototype.indexOf.call(el.parentNode.children, el) : 0;
      el.style.setProperty("--fx-d", Math.min(sibs, 6) * 60 + "ms");
      el.classList.remove("rise");
      el.classList.add("fx");
    });
    var io = new IntersectionObserver(function (es) {
      es.forEach(function (e) {
        var el = e.target;
        if (e.isIntersecting) {
          el.classList.toggle("fx-up", e.boundingClientRect.top < 0);
          requestAnimationFrame(function () { el.classList.add("in"); });
          seen.set(el, true);
        } else if (seen.get(el)) {
          el.classList.remove("in");
          el.classList.toggle("fx-up", e.boundingClientRect.top < 0);
        }
      });
    }, { threshold: 0.08, rootMargin: "0px 0px -4% 0px" });
    list.forEach(function (el) { io.observe(el); });
  }
  Array.prototype.forEach.call(d.querySelectorAll(".belt"), function (belt) {
    var base = Array.prototype.slice.call(belt.children), need = Math.max(screen.width || 0, innerWidth, 1200) + 120, guard = 0;
    while (belt.scrollWidth < need && guard++ < 30) base.forEach(function (n) { belt.appendChild(n.cloneNode(true)); });
    Array.prototype.slice.call(belt.children).forEach(function (n) { belt.appendChild(n.cloneNode(true)); });
    belt.parentNode.style.setProperty("--t", Math.round(belt.scrollWidth / 2 / 28) + "s");
  });
  function arrow(d) { return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="' + d + '"/></svg>'; }
  Array.prototype.forEach.call(d.querySelectorAll(".rail"), function (rail) {
    var items = Array.prototype.slice.call(rail.children);
    if (items.length < 2) return;
    var bar = d.createElement("div");
    bar.className = "railbar";
    var dots = d.createElement("div");
    dots.className = "dots";
    var arrows = d.createElement("div");
    arrows.className = "arrows";
    arrows.innerHTML = '<button type="button" aria-label="Назад">' + arrow("M15 5l-7 7 7 7") + '</button><button type="button" aria-label="Дальше">' + arrow("M9 5l7 7-7 7") + "</button>";
    items.forEach(function (it, i) {
      var b = d.createElement("button");
      b.type = "button";
      b.setAttribute("aria-label", "Карточка " + (i + 1));
      b.addEventListener("click", function () { go(i); });
      dots.appendChild(b);
    });
    bar.appendChild(dots);
    bar.appendChild(arrows);
    rail.parentNode.insertBefore(bar, rail.nextSibling);
    var cur = -1, held = false, timer = 0;
    function index() {
      var x = rail.scrollLeft, best = 0, dist = 1e9;
      items.forEach(function (it, i) { var dd = Math.abs(it.offsetLeft - rail.offsetLeft - x); if (dd < dist) { dist = dd; best = i; } });
      if (rail.scrollLeft + rail.clientWidth >= rail.scrollWidth - 4) best = items.length - 1;
      return best;
    }
    function mark() {
      var i = index();
      if (i === cur) return;
      if (cur >= 0 && held) play("tap", 0.18);
      cur = i;
      Array.prototype.forEach.call(dots.children, function (b, k) { b.classList.toggle("on", k === i); });
      items.forEach(function (it, k) { it.classList.toggle("cur", k === i); });
    }
    function go(i) {
      i = (i + items.length) % items.length;
      rail.scrollTo({ left: items[i].offsetLeft - rail.offsetLeft, behavior: still ? "auto" : "smooth" });
    }
    arrows.children[0].addEventListener("click", function () { held = true; go(index() - 1); });
    arrows.children[1].addEventListener("click", function () { held = true; go(index() + 1); });
    var raf = 0;
    rail.addEventListener("scroll", function () { cancelAnimationFrame(raf); raf = requestAnimationFrame(mark); }, { passive: true });
    ["pointerdown", "touchstart", "wheel"].forEach(function (t) { rail.addEventListener(t, function () { held = true; }, { passive: true }); });
    mark();
    var auto = +rail.getAttribute("data-auto") || 0;
    if (auto && !still) timer = setInterval(function () {
      if (held) { clearInterval(timer); return; }
      var r = rail.getBoundingClientRect();
      if (r.bottom < 0 || r.top > innerHeight) return;
      go(index() + 1);
    }, auto);
  });
  var key = "wfSound", on = true;
  try { on = localStorage.getItem(key) !== "0"; } catch (x) {}
  var cache = {};
  function play(n, v) {
    if (!on) return;
    try {
      var a = cache[n];
      if (!a) { a = cache[n] = new Audio("/s/" + n + ".wav"); a.preload = "auto"; }
      a.volume = v || 0.32;
      a.currentTime = 0;
      var p = a.play();
      if (p && p.catch) p.catch(function () {});
    } catch (x) {}
  }
  window.wfSound = play;
  d.addEventListener("click", function (e) {
    var t = e.target.closest && e.target.closest("a, button, label, summary, [role=tab]");
    if (!t || t.classList.contains("snd")) return;
    var href = t.getAttribute && t.getAttribute("href");
    var name = href && /^\/download\/x/.test(href) ? "download" : t.matches("a[href]") ? "open" : "tap";
    play(name);
    if (on && href && t.matches("a[href]") && !t.target && /^\/(?!\/)/.test(href) && !/^\/download\/x/.test(href) && !e.metaKey && !e.ctrlKey && !e.shiftKey && e.button === 0) {
      e.preventDefault();
      d.documentElement.classList.add("leaving");
      setTimeout(function () { location.href = href; }, 170);
    }
  }, true);
  window.addEventListener("pageshow", function () { d.documentElement.classList.remove("leaving"); });
  var b = d.createElement("button");
  b.type = "button";
  b.className = "snd";
  function paint() {
    b.setAttribute("aria-label", on ? "Выключить звуки" : "Включить звуки");
    b.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 10v4h4l5 4V6L8 10z"/>' +
      (on ? '<path d="M16.5 9a4 4 0 0 1 0 6M19 6.5a7.5 7.5 0 0 1 0 11"/>' : '<path d="M17 9l5 6M22 9l-5 6"/>') + "</svg>";
    b.classList.toggle("off", !on);
  }
  b.addEventListener("click", function () {
    on = !on;
    try { localStorage.setItem(key, on ? "1" : "0"); } catch (x) {}
    paint();
    play("pop");
  });
  paint();
  d.body.appendChild(b);
})();
