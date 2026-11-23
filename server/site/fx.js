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
