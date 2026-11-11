package com.flerty.wikiflerty.reader

import android.webkit.JavascriptInterface

class TextSelectionBridge(
    private val onEvent: (String) -> Unit,
    private val onFootnote: (String, String) -> Unit,
    private val onHandles: (String) -> Unit,
    private val onBoot: () -> String = { "" },
) {
    @JavascriptInterface
    fun boot(): String = runCatching { onBoot() }.getOrDefault("")

    @JavascriptInterface
    fun onHandles(data: String) = onHandles.invoke(data)

    @JavascriptInterface
    fun onEvent(type: String) = onEvent.invoke(type)

    @JavascriptInterface
    fun onFootnote(text: String, url: String) = onFootnote.invoke(text, url)
}

internal object TextSelection {
    const val BRIDGE = "WikiFlertySelection"

    fun script(accent: String): String = SCRIPT.replace("__ACCENT__", accent) + "\n" + NOTES + "\n" + AUTO + "\n" + PEOPLE.replace("__ACCENT__", accent) + "\n" + FEED + "\n" + BIONIC + "\n" + KITBOOK

    fun peopleCall(people: List<com.flerty.wikiflerty.data.BookCharacter>): String {
        val keys = org.json.JSONArray()
        people.forEach { p ->
            p.name.split(Regex("[\\s\\-]+")).map { it.trim('.', ',', '«', '»', '"', '\'', '(', ')') }
                .filter { it.length >= 2 && it.first().isLetter() }
                .forEach { word ->
                    var stem = word.lowercase().replace('ё', 'е')
                    val short = stem.length <= 3
                    if (stem.length > 4 && stem.last() in "йь") stem = stem.dropLast(1)
                    if (stem.length > 3 && stem.last() in "аеиоуыэюяy") stem = stem.dropLast(1)
                    keys.put(org.json.JSONArray().put(stem).put(if (short) 0 else 3).put(p.id))
                }
        }
        return "window.__wfPeople && window.__wfPeople($keys)"
    }

    private val AUTO = """
        (function () {
          if (window.__wfAuto) return;
          var speed = 0, raf = 0, last = 0, acc = 0, ended = false;
          function atEnd() {
            var root = document.scrollingElement || document.documentElement;
            return window.scrollY + window.innerHeight >= root.scrollHeight - 2;
          }
          function step(t) {
            if (!speed) { raf = 0; last = 0; return; }
            if (last) {
              acc += (t - last) * speed / 1000;
              var px = Math.floor(acc);
              if (px >= 1) { window.scrollBy(0, px); acc -= px; }
            }
            last = t;
            if (atEnd()) {
              if (!ended) {
                ended = true;
                speed = 0;
                raf = 0;
                last = 0;
                try { window.${BRIDGE}.onEvent("auto-end"); } catch (e) {}
                return;
              }
            } else {
              ended = false;
            }
            raf = requestAnimationFrame(step);
          }
          window.__wfPage = function (dir) {
            var root = document.scrollingElement || document.documentElement;
            var top = root.scrollTop;
            var max = root.scrollHeight - window.innerHeight;
            if ((dir > 0 && top >= max - 2) || (dir < 0 && top <= 2)) return false;
            window.scrollBy({ top: dir * window.innerHeight * 0.88, behavior: "smooth" });
            return true;
          };
          window.__wfAuto = {
            start: function (s) { speed = s; ended = false; if (!raf) { last = 0; raf = requestAnimationFrame(step); } },
            stop: function () { speed = 0; }
          };
        })();
    """.trimIndent()

    private val KITBOOK = """
        (function () {
          if (window.__wfKitReady) return;
          window.__wfKitReady = true;
          function relayout() { setTimeout(function () { window.dispatchEvent(new Event("resize")); }, 60); }
          function svg(body, w) {
            return "url(\"data:image/svg+xml;utf8," + encodeURIComponent("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 " + (w || 180) + " 20'>" + body + "</svg>") + "\")";
          }
          function ornament(o) {
            var a = o.acc, f = o.fg;
            switch (o.kit) {
              case "zen": return svg("<path d='M56 11c22-4 46 4 68-1' stroke='" + f + "' stroke-width='1.6' stroke-linecap='round' fill='none' opacity='.55'/><circle cx='90' cy='10' r='4.5' fill='" + a + "'/>");
              case "comic": return svg("<path d='M34 14l14-9 14 9 14-9 14 9 14-9 14 9 14-9 14 9' stroke='#141210' stroke-width='6' fill='none' stroke-linejoin='round' stroke-linecap='round'/><path d='M34 14l14-9 14 9 14-9 14 9 14-9 14 9 14-9 14 9' stroke='" + a + "' stroke-width='2.6' fill='none' stroke-linejoin='round' stroke-linecap='round'/>");
              case "news": return svg("<rect x='0' y='5' width='180' height='3' fill='" + f + "'/><rect x='0' y='11' width='180' height='1' fill='" + f + "'/>");
              case "sea": return svg("<path d='M22 10q8.5-8 17 0t17 0 17 0 17 0 17 0 17 0 17 0 17 0' stroke='" + a + "' stroke-width='1.9' fill='none' stroke-linecap='round'/>");
              case "clay": return svg("<circle cx='68' cy='10' r='5.5' fill='" + a + "'/><circle cx='90' cy='10' r='5.5' fill='#B48CFF'/><circle cx='112' cy='10' r='5.5' fill='#8AD09A'/><circle cx='66' cy='8' r='1.6' fill='#fff' opacity='.7'/><circle cx='88' cy='8' r='1.6' fill='#fff' opacity='.7'/><circle cx='110' cy='8' r='1.6' fill='#fff' opacity='.7'/>");
              default: return svg("<path d='M12 10H72M108 10H168' stroke='" + a + "' stroke-width='1.2'/><path d='M90 3l7 7-7 7-7-7z' fill='" + a + "'/><circle cx='78' cy='10' r='1.7' fill='" + a + "'/><circle cx='102' cy='10' r='1.7' fill='" + a + "'/>");
            }
          }
          function css(o) {
            var a = o.acc, f = o.fg, b = o.bg, cap;
            switch (o.kit) {
              case "zen":
                cap = "font-family:Georgia,'Noto Serif',serif;font-weight:600;font-size:2.9em;color:" + b + ";background:" + a + ";padding:.1em .15em .05em;border-radius:.1em;margin:.06em .16em 0 0;line-height:.9";
                break;
              case "comic":
                cap = "font-family:Impact,'Arial Black','Roboto Condensed',sans-serif;font-weight:900;font-size:3.6em;color:" + a + ";text-shadow:-1px -1px 0 #141210,1px -1px 0 #141210,-1px 1px 0 #141210,1px 1px 0 #141210,.07em .07em 0 #141210;margin:.02em .14em 0 0;line-height:.84";
                break;
              case "news":
                cap = "font-family:Georgia,'Noto Serif',serif;font-weight:900;font-size:3.9em;color:" + f + ";margin:.02em .1em 0 0;line-height:.8";
                break;
              case "sea":
                cap = "font-family:Georgia,'Noto Serif',serif;font-style:italic;font-weight:700;font-size:3.4em;color:" + a + ";text-shadow:0 0 .2em " + a + "77;margin:.04em .12em 0 0;line-height:.84";
                break;
              case "clay":
                cap = "font-family:'Arial Rounded MT Bold','Nunito',sans-serif;font-weight:900;font-size:3.4em;color:" + a + ";text-shadow:0 .05em 0 " + o.deep + ",0 .1em .12em rgba(0,0,0,.22);margin:.02em .12em 0 0;line-height:.86";
                break;
              default:
                cap = "font-family:Georgia,'Noto Serif',serif;font-weight:700;font-size:3.1em;color:" + a + ";background:" + a + "1A;border:1px solid " + a + "99;border-radius:3px;padding:.08em .14em .03em;margin:.06em .16em 0 0;line-height:.9;text-shadow:0 1px 0 rgba(0,0,0,.2)";
            }
            var wide = o.kit === "news";
            return "p.wf-dropcap{text-indent:0!important}" +
              "p.wf-dropcap::first-letter{float:left;font-style:normal;" + cap + "}" +
              ".wf-head::after{content:'';display:block;height:1em;margin:.55em auto 0;width:" + (wide ? "100%" : "9em") + ";max-width:100%;" +
              "background:" + ornament(o) + " center/" + (wide ? "100% 100%" : "contain") + " no-repeat}";
          }
          var SKIP = "blockquote,.epigraph,.poem,.stanza,.cite,.annotation,.subtitle,header,figure,table,li,aside,nav,.footnote,.note";
          function pick(head) {
            if (!head) return null;
            var all = document.body.querySelectorAll("p");
            var seen = 0;
            for (var i = 0; i < all.length; i++) {
              var p = all[i];
              if (!(head.compareDocumentPosition(p) & Node.DOCUMENT_POSITION_FOLLOWING)) continue;
              if (head.contains(p)) continue;
              if (++seen > 5) return null;
              if (p.closest(SKIP)) continue;
              var t = (p.textContent || "").replace(/^\s+/, "");
              if (t.length < 60) continue;
              if (!/^[«"„“']?\p{L}/u.test(t)) return null;
              var al = getComputedStyle(p).textAlign;
              if (al === "center" || al === "right") continue;
              return p;
            }
            return null;
          }
          window.__wfKit = function (o) {
            if (!document.body) return;
            var st = document.getElementById("wf-kit-style");
            var old = document.querySelectorAll(".wf-dropcap,.wf-head");
            for (var i = 0; i < old.length; i++) { old[i].classList.remove("wf-dropcap"); old[i].classList.remove("wf-head"); }
            if (!o || !o.on) {
              if (st) { st.remove(); relayout(); }
              return;
            }
            if (!st) {
              st = document.createElement("style");
              st.id = "wf-kit-style";
              (document.head || document.documentElement).appendChild(st);
            }
            var next = css(o);
            var changed = st.textContent !== next;
            if (changed) st.textContent = next;
            var head = document.body.querySelector("h1,h2,h3,.title");
            if (head && (head.textContent || "").trim().length > 0) {
              head.classList.add("wf-head");
              var p = pick(head);
              if (p) p.classList.add("wf-dropcap");
            }
            relayout();
          };
        })();
    """.trimIndent()

    private val BIONIC = """
        (function () {
          if (window.__wfBionicReady) return;
          window.__wfBionicReady = true;
          var applied = false;
          var word = /[\p{L}\p{M}]+/gu;
          function strong(len) { return len <= 3 ? 1 : Math.ceil(len * 0.45); }
          function relayout() { setTimeout(function () { window.dispatchEvent(new Event("resize")); }, 60); }
          function apply() {
            if (applied || !document.body) return;
            if (!document.getElementById("wf-bio-style")) {
              var st = document.createElement("style");
              st.id = "wf-bio-style";
              st.textContent = "b.wf-bio{font-weight:700;font-style:inherit;color:inherit}";
              (document.head || document.documentElement).appendChild(st);
            }
            var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
              acceptNode: function (n) {
                var p = n.parentNode;
                if (!p || /^(SCRIPT|STYLE|TITLE|CODE|PRE)$/i.test(p.nodeName)) return NodeFilter.FILTER_REJECT;
                if (p.classList && p.classList.contains("wf-bio")) return NodeFilter.FILTER_REJECT;
                return /\p{L}/u.test(n.nodeValue) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
              }
            });
            var nodes = [], n;
            while ((n = walker.nextNode())) nodes.push(n);
            nodes.forEach(function (node) {
              var text = node.nodeValue, frag = document.createDocumentFragment(), last = 0, m;
              word.lastIndex = 0;
              while ((m = word.exec(text))) {
                if (m.index > last) frag.appendChild(document.createTextNode(text.slice(last, m.index)));
                var k = strong(m[0].length);
                var b = document.createElement("b");
                b.className = "wf-bio";
                b.textContent = m[0].slice(0, k);
                frag.appendChild(b);
                if (k < m[0].length) frag.appendChild(document.createTextNode(m[0].slice(k)));
                last = m.index + m[0].length;
              }
              if (last < text.length) frag.appendChild(document.createTextNode(text.slice(last)));
              if (node.parentNode) node.parentNode.replaceChild(frag, node);
            });
            applied = true;
            relayout();
            if (window.__wfPeople && window.__wfPeopleLast) window.__wfPeople(window.__wfPeopleLast);
          }
          function remove() {
            var list = document.querySelectorAll("b.wf-bio");
            if (!list.length) { applied = false; return; }
            for (var i = 0; i < list.length; i++) list[i].replaceWith(document.createTextNode(list[i].textContent));
            if (document.body) document.body.normalize();
            applied = false;
            relayout();
            if (window.__wfPeople && window.__wfPeopleLast) window.__wfPeople(window.__wfPeopleLast);
          }
          window.__wfBionic = function (on) { if (on) apply(); else remove(); };
        })();
    """.trimIndent()

    private val FEED = """
        (function () {
          if (window.__wfFeedReady) return;
          window.__wfFeedReady = true;
          var on = false, prev = false, next = false, last = 0, edge = null, dir = 0, pull = 0, sent = "";
          var NEED = 90;
          function root() { return document.scrollingElement || document.documentElement; }
          function atEnd() { return window.scrollY + window.innerHeight >= root().scrollHeight - 3; }
          function atTop() { return window.scrollY <= 1; }
          function send(v) {
            if (v === sent) return;
            sent = v;
            try { window.${BRIDGE}.onEvent("feed:" + v); } catch (x) {}
          }
          function report() {
            var has = dir > 0 ? next : prev;
            var k = Math.round(Math.min(1, pull / NEED) * 40) / 40;
            send(dir + ":" + k + ":" + (has ? 1 : 0));
          }
          function reset() { edge = null; dir = 0; pull = 0; }
          function hide() { send("0:0:0"); }
          window.__wfFeed = function (enabled, hasPrev, hasNext) {
            on = !!enabled; prev = !!hasPrev; next = !!hasNext;
            var old = document.getElementById("wf-feed");
            if (old) old.remove();
            if (!on) { reset(); hide(); }
          };
          window.addEventListener("touchstart", function (e) {
            if (!on || e.touches.length !== 1) return;
            last = e.touches[0].clientY;
            reset();
          }, { passive: true, capture: true });
          window.addEventListener("touchmove", function (e) {
            if (!on || e.touches.length !== 1) return;
            var y = e.touches[0].clientY;
            if (!dir) {
              var sel = window.getSelection && window.getSelection();
              if (sel && !sel.isCollapsed) { last = y; return; }
              if (y < last && atEnd()) { dir = 1; edge = y; }
              else if (y > last && atTop()) { dir = -1; edge = y; }
              last = y;
              if (!dir) return;
            }
            pull = dir > 0 ? edge - y : y - edge;
            if (pull < -8) { reset(); hide(); return; }
            pull = Math.max(0, pull);
            report();
          }, { passive: true, capture: true });
          function end() {
            if (!on || !dir) return;
            var ready = pull >= NEED && (dir > 0 ? next : prev);
            var d = dir;
            reset();
            hide();
            if (ready) { try { window.${BRIDGE}.onEvent(d > 0 ? "feed-next" : "feed-prev"); } catch (x) {} }
          }
          window.addEventListener("touchend", end, { passive: true, capture: true });
          window.addEventListener("touchcancel", function () { reset(); hide(); }, { passive: true, capture: true });
        })();
    """.trimIndent()

    private val PEOPLE = """
        (function () {
          if (window.__wfPeopleReady) return;
          window.__wfPeopleReady = true;
          var hits = [];
          var word = /(^|[^\p{L}\p{N}])(\p{Lu}[\p{L}]*)/gu;
          window.__wfPeople = function (list) {
            window.__wfPeopleLast = list;
            hits = [];
            try { if (window.CSS && CSS.highlights) CSS.highlights.delete("wf-hero"); } catch (x) {}
            if (!document.body || !list || !list.length) { place(); return; }
            var map = {};
            list.forEach(function (k) { var o = map[k[0]]; if (!o || o.s < k[1]) map[k[0]] = { s: k[1], id: k[2] }; });
            var BLOCK = "p,div,li,h1,h2,h3,h4,h5,h6,blockquote,td,th,dd,dt,section,article,figcaption,body";
            var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
              acceptNode: function (n) {
                var p = n.parentNode;
                if (!p || /^(SCRIPT|STYLE|TITLE)$/i.test(p.nodeName)) return NodeFilter.FILTER_REJECT;
                return NodeFilter.FILTER_ACCEPT;
              }
            });
            function locate(run, pos) {
              for (var i = run.parts.length - 1; i >= 0; i--) {
                var part = run.parts[i];
                if (pos >= part.at) return { node: part.node, off: Math.min(pos - part.at, part.node.nodeValue.length) };
              }
              return null;
            }
            function scan(run) {
              var text = run.text;
              if (!text || text.length < 2) return;
              word.lastIndex = 0;
              var m;
              while ((m = word.exec(text))) {
                var w = m[2];
                var low = w.toLowerCase().replace(/ё/g, "е");
                var start = m.index + m[1].length;
                for (var k = low.length; k >= Math.max(2, low.length - 3); k--) {
                  var hit = map[low.slice(0, k)];
                  if (hit && low.length - k <= hit.s) {
                    var a = locate(run, start), b = locate(run, start + w.length);
                    if (a && b) {
                      try {
                        var r = document.createRange();
                        r.setStart(a.node, a.off);
                        r.setEnd(b.node, b.off);
                        hits.push({ r: r, id: hit.id });
                      } catch (x) {}
                    }
                    break;
                  }
                }
              }
            }
            var run = null, n;
            while ((n = walker.nextNode())) {
              var block = n.parentNode.closest ? n.parentNode.closest(BLOCK) : null;
              if (!run || run.block !== block) {
                if (run) scan(run);
                run = { block: block, text: "", parts: [] };
              }
              run.parts.push({ node: n, at: run.text.length });
              run.text += n.nodeValue || "";
            }
            if (run) scan(run);
            var st = document.getElementById("wf-hero-style");
            if (!st) {
              st = document.createElement("style");
              st.id = "wf-hero-style";
              (document.head || document.documentElement).appendChild(st);
            }
            st.textContent =
              "#wf-hero-flags,#wf-hero-flags i{margin:0!important;padding:0!important;border:0!important;background-color:transparent!important;pointer-events:none!important;-webkit-user-select:none;user-select:none}" +
              "#wf-hero-flags{position:absolute!important;left:0;top:0;width:0;height:0;z-index:2;transition:opacity .3s ease}" +
              "#wf-hero-flags i{position:absolute!important;display:block!important;background-repeat:no-repeat!important;background-size:100% 100%!important;background-image:" + flag + "!important;transform-origin:15% 95%;transition:transform .16s ease}";
            place();
          };
          var flag = 'url("data:image/svg+xml,' + encodeURIComponent(
            "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 26 30'>" +
            "<rect x='1' y='1' width='2.6' height='28' rx='1.3' fill='__ACCENT__'/>" +
            "<path d='M3.6 2.2h18.2c1.5 0 2.3 1.6 1.4 2.8l-2.9 4 2.9 4c.9 1.2.1 2.8-1.4 2.8H3.6z' fill='__ACCENT__'/>" +
            "<path d='M10.6 6.6c.3-1.5 1.6-2.4 3.2-2.4 1.9 0 3.2 1.1 3.2 2.7 0 1.2-.7 1.9-1.6 2.5-.8.5-1 .8-1 1.6' fill='none' stroke='white' stroke-width='1.8' stroke-linecap='round'/>" +
            "<circle cx='14.4' cy='13.3' r='1.05' fill='white'/></svg>") + '")';
          var layer = null, pending = 0, sizes = new Map();
          function place() {
            var host = document.documentElement;
            if (!hits.length) { if (layer && layer.isConnected) layer.remove(); return; }
            var fresh = !layer;
            if (!layer) {
              layer = document.createElement("div");
              layer.id = "wf-hero-flags";
              layer.setAttribute("aria-hidden", "true");
            }
            if (!layer.isConnected) host.appendChild(layer);
            if (fresh) layer.style.opacity = "0";
            var o = layer.getBoundingClientRect();
            sizes = new Map();
            var frag = document.createDocumentFragment();
            for (var i = 0; i < hits.length; i++) {
              var rs = hits[i].r.getClientRects(), b = null;
              for (var j = 0; j < rs.length; j++) if (rs[j].width > 0) { b = rs[j]; break; }
              if (!b) continue;
              var el = hits[i].r.startContainer.parentElement;
              var fs = sizes.get(el);
              if (fs === undefined) { fs = el ? parseFloat(getComputedStyle(el).fontSize) || 16 : 16; sizes.set(el, fs); }
              var h = Math.max(13, fs), w = h * 26 / 30;
              var f = document.createElement("i");
              f.style.left = (b.left - o.left - fs * 0.22) + "px";
              f.style.top = (b.top - o.top + b.height * 0.2 - h * 0.52) + "px";
              f.style.width = w + "px";
              f.style.height = h + "px";
              hits[i].flag = f;
              frag.appendChild(f);
            }
            while (layer.firstChild) layer.removeChild(layer.firstChild);
            layer.appendChild(frag);
            if (fresh) requestAnimationFrame(function () { requestAnimationFrame(function () { if (layer) layer.style.opacity = "1"; }); });
          }
          function later() {
            if (pending) clearTimeout(pending);
            pending = setTimeout(function () { pending = 0; requestAnimationFrame(place); }, 140);
          }
          window.addEventListener("resize", later);
          try { new MutationObserver(later).observe(document.documentElement, { attributes: true, attributeFilter: ["style", "class"] }); } catch (x) {}
          try { if (window.ResizeObserver && document.body) new ResizeObserver(later).observe(document.body); } catch (x) {}
          try { if (document.fonts && document.fonts.ready) document.fonts.ready.then(later); } catch (x) {}
          function flagAt(x, y) {
            var best = -1, bestD = 1e9;
            for (var i = 0; i < hits.length; i++) {
              var f = hits[i].flag;
              if (!f || !f.isConnected) continue;
              var b = f.getBoundingClientRect();
              if (!b.width) continue;
              var cx = b.left + b.width * 0.55, cy = b.top + b.height * 0.3;
              var reach = Math.max(18, b.width * 0.9);
              var dx = x - cx, dy = y - cy, dd = dx * dx + dy * dy;
              if (Math.abs(dx) <= reach && Math.abs(dy) <= reach && dd < bestD) { best = i; bestD = dd; }
            }
            return best;
          }
          function bounce(i) {
            var f = hits[i] && hits[i].flag;
            if (!f) return;
            f.style.transform = "scale(1.3)";
            setTimeout(function () { f.style.transform = ""; }, 160);
          }
          window.__wfFlagAt = function (x, y) {
            var i = flagAt(x, y);
            if (i < 0) return "";
            bounce(i);
            return hits[i].id;
          };
          window.addEventListener("click", function (e) {
            if (!hits.length || e.defaultPrevented) return;
            var sel = window.getSelection && window.getSelection();
            if (sel && !sel.isCollapsed) return;
            if (e.target && e.target.closest && e.target.closest("a[href]")) return;
            var best = flagAt(e.clientX, e.clientY);
            if (best < 0) return;
            e.preventDefault();
            e.stopImmediatePropagation();
            bounce(best);
            try { window.${BRIDGE}.onEvent("person:" + hits[best].id); } catch (x) {}
          }, true);
        })();
    """.trimIndent()

    private val NOTES = """
        (function () {
          if (window.__wfNotes) return;
          window.__wfNotes = true;
          var OPS = "http://www.idpf.org/2007/ops";
          function attr(a, name) {
            return (a.getAttribute(name) || "") + " " + (a.getAttributeNS ? a.getAttributeNS(OPS, name.replace("epub:", "")) || "" : "");
          }
          function looksLikeNote(a) {
            var kind = attr(a, "epub:type") + " " + (a.getAttribute("role") || "") + " " + (a.getAttribute("class") || "");
            if (/noteref|footnote|endnote|doc-noteref|note-?ref|fn/i.test(kind)) return true;
            var text = (a.textContent || "").replace(/\s+/g, "");
            if (!text) return false;
            if (text.length <= 6 && (a.closest("sup") || a.querySelector("sup"))) return true;
            return /^[\[\(\{]?(\d{1,4}|[*†‡§¶]{1,3}|[a-zа-я])[\]\)\}]?$/i.test(text);
          }
          function blockOf(el) {
            var block = el;
            var inline = /^(A|SPAN|SUP|SUB|EM|I|B|STRONG|SMALL)$/i;
            if (inline.test(block.tagName) || (block.textContent || "").trim().length < 3) {
              block = el.closest("aside, li, dd, p, section, div, td, blockquote") || el.parentElement || el;
            }
            var text = (block.innerText || block.textContent || "").trim();
            if (text.replace(/\s+/g, "").length < 6) {
              var parts = [text];
              var next = block.nextElementSibling;
              for (var i = 0; next && i < 6; i++) {
                if (next.id && i > 0) break;
                if (/^H[1-6]$/i.test(next.tagName) && i > 0) break;
                parts.push((next.innerText || next.textContent || "").trim());
                next = next.nextElementSibling;
              }
              text = parts.join("\n").trim();
            }
            return text.replace(/\n{3,}/g, "\n\n").slice(0, 4000);
          }
          function findIn(doc, id) {
            var el = doc.getElementById(id);
            if (!el) {
              var named = doc.getElementsByName ? doc.getElementsByName(id) : [];
              el = named && named.length ? named[0] : null;
            }
            return el;
          }
          function show(text, url) {
            try { window.${BRIDGE}.onFootnote(text, url); } catch (e) { location.href = url; }
          }
          window.addEventListener("click", function (e) {
            var a = e.target && e.target.closest ? e.target.closest("a[href]") : null;
            if (!a) return;
            var href = a.getAttribute("href") || "";
            if (href.indexOf("#") < 0 || /^[a-z]+:/i.test(href) && href.indexOf(location.origin) !== 0) return;
            if (!looksLikeNote(a)) return;
            var url = a.href;
            var id = decodeURIComponent(url.split("#")[1] || "");
            if (!id) return;
            e.preventDefault();
            e.stopImmediatePropagation();
            var base = url.split("#")[0];
            if (base === location.href.split("#")[0]) {
              var el = findIn(document, id);
              if (el) show(blockOf(el), url); else location.href = url;
              return;
            }
            fetch(base).then(function (r) { return r.text(); }).then(function (html) {
              var doc = new DOMParser().parseFromString(html, "application/xhtml+xml");
              if (doc.getElementsByTagName("parsererror").length) doc = new DOMParser().parseFromString(html, "text/html");
              var el = findIn(doc, id);
              if (!el) { location.href = url; return; }
              var holder = document.createElement("div");
              holder.style.cssText = "position:absolute;left:-99999px;top:0;width:600px";
              var block = el;
              var inline = /^(A|SPAN|SUP|SUB|EM|I|B|STRONG|SMALL)$/i;
              if (inline.test(block.tagName) || (block.textContent || "").trim().length < 3) {
                block = el.closest("aside, li, dd, p, section, div, td, blockquote") || el.parentNode || el;
              }
              var copy = document.importNode(block, true);
              var wrap = document.createElement("div");
              wrap.appendChild(copy);
              var sib = block.nextElementSibling;
              for (var i = 0; sib && i < 6; i++) {
                if ((block.textContent || "").replace(/\s+/g, "").length >= 6) break;
                if (sib.id || /^H[1-6]$/i.test(sib.tagName)) break;
                wrap.appendChild(document.importNode(sib, true));
                sib = sib.nextElementSibling;
              }
              holder.appendChild(wrap);
              document.body.appendChild(holder);
              var text = (wrap.innerText || wrap.textContent || "").trim().replace(/\n{3,}/g, "\n\n").slice(0, 4000);
              holder.remove();
              if (text) show(text, url); else location.href = url;
            }).catch(function () { location.href = url; });
          }, true);
          window.__wfGo = function (url) { location.href = url; };
        })();
    """.trimIndent()

    private val SCRIPT = """
        (function () {
          if (window.__wfSel) {
            try { window.__wfSel.heal(); } catch (e) {}
            return;
          }
          var root = document.scrollingElement || document.documentElement;
          var st = {
            anchor: null, word: null, mode: null, locked: null, timer: 0, edgeTimer: 0, pressing: false,
            startX: 0, startY: 0, lastX: 0, lastY: 0, grabX: 0, grabY: 0, programmatic: false,
            swallow: false, turnX: 0, turnY: 0, afterTurn: false, scrollTimer: 0, turning: 0, turnTarget: 0
          };
          var css = document.createElement("style");
          css.textContent =
            "html,body,body *{-webkit-touch-callout:none!important;-webkit-user-drag:none!important}" +
            ".wf-h{position:fixed;width:44px;height:44px;z-index:2147483647;display:none;touch-action:none;-webkit-user-select:none;user-select:none}" +
            ".wf-h i{position:absolute;top:0;width:20px;height:20px;background:__ACCENT__;box-shadow:0 1px 4px rgba(0,0,0,.35)}" +
            ".wf-e i{left:12px;border-radius:0 50% 50% 50%}" +
            ".wf-s i{left:12px;border-radius:50% 0 50% 50%}";
          (document.head || document.documentElement).appendChild(css);
          function handle(cls, start) {
            var el = document.createElement("div");
            el.className = "wf-h " + cls;
            el.setAttribute("aria-hidden", "true");
            el.style.cssText = "position:fixed;left:0;top:0;width:44px;height:44px;z-index:2147483647;display:none;touch-action:none;-webkit-user-select:none;user-select:none;pointer-events:auto;margin:0;padding:0;transform:none";
            var dot = document.createElement("i");
            dot.style.cssText = "position:absolute;top:0;left:12px;width:20px;height:20px;display:block;background:__ACCENT__;box-shadow:0 1px 4px rgba(0,0,0,.35);border-radius:" + (start ? "50% 0 50% 50%" : "0 50% 50% 50%");
            el.appendChild(dot);
            document.documentElement.appendChild(el);
            return el;
          }
          var hs = handle("wf-s", true);
          var he = handle("wf-e", false);
          var probe = document.createElement("div");
          probe.setAttribute("aria-hidden", "true");
          probe.style.cssText = "position:fixed;left:0;top:0;width:0;height:0;visibility:hidden;pointer-events:none";
          document.documentElement.appendChild(probe);
          function heal() {
            var host = document.documentElement;
            if (!css.isConnected) (document.head || host).appendChild(css);
            if (!hs.isConnected) host.appendChild(hs);
            if (!he.isConnected) host.appendChild(he);
            if (!probe.isConnected) host.appendChild(probe);
          }

          function notify(type) {
            try { window.${BRIDGE}.onEvent(type); } catch (e) {}
          }
          function scrollMode() {
            return document.documentElement.style.getPropertyValue("--USER__view").trim() == "readium-scroll-on";
          }
          function onPage(r) {
            return r && r.right > -1 && r.left < window.innerWidth + 1;
          }
          function rectAt(node, offset) {
            try {
              var r = document.createRange();
              r.setStart(node, offset);
              r.collapse(true);
              var rs = r.getClientRects();
              if (rs.length) return rs[0];
              if (node.nodeType === 3 && offset > 0) {
                r.setStart(node, offset - 1);
                r.setEnd(node, offset);
                rs = r.getClientRects();
                if (rs.length) {
                  var b = rs[rs.length - 1];
                  return { left: b.right, right: b.right, top: b.top, bottom: b.bottom, height: b.height };
                }
              }
              if (node.nodeType === 3 && offset < node.data.length) {
                r.setStart(node, offset);
                r.setEnd(node, offset + 1);
                rs = r.getClientRects();
                if (rs.length) return { left: rs[0].left, right: rs[0].left, top: rs[0].top, bottom: rs[0].bottom, height: rs[0].height };
              }
              var bb = r.getBoundingClientRect();
              return bb.height ? bb : null;
            } catch (e) {
              return null;
            }
          }
          function caretAt(x, y) {
            var r = document.caretRangeFromPoint ? document.caretRangeFromPoint(x, y) : null;
            if (!r || !r.startContainer) return null;
            var node = r.startContainer;
            if (node.nodeType !== 3) return null;
            if (node.parentElement && node.parentElement.closest(".wf-h")) return null;
            var c = { node: node, offset: r.startOffset };
            var box = rectAt(c.node, c.offset);
            if (!onPage(box)) return null;
            return c;
          }
          function isWordChar(ch) {
            return /[\p{L}\p{N}\p{M}­'’\-]/u.test(ch);
          }
          function wordAround(c) {
            var text = c.node.data;
            var i = c.offset;
            if (i >= text.length || !isWordChar(text[i])) {
              if (i > 0 && isWordChar(text[i - 1])) i--; else return null;
            }
            var s = i, e = i + 1;
            while (s > 0 && isWordChar(text[s - 1])) s--;
            while (e < text.length && isWordChar(text[e])) e++;
            while (s < e && /['’\-]/.test(text[s])) s++;
            while (e > s && /['’\-]/.test(text[e - 1])) e--;
            if (e <= s) return null;
            return { start: { node: c.node, offset: s }, end: { node: c.node, offset: e } };
          }
          function wordAt(x, y) {
            var c = caretAt(x, y);
            if (!c) return null;
            var w = wordAround(c);
            if (!w) return null;
            var r = document.createRange();
            r.setStart(w.start.node, w.start.offset);
            r.setEnd(w.end.node, w.end.offset);
            var rs = r.getClientRects();
            for (var k = 0; k < rs.length; k++) {
              var b = rs[k];
              if (x >= b.left - 10 && x <= b.right + 10 && y >= b.top - 10 && y <= b.bottom + 10) return w;
            }
            return null;
          }
          function before(a, b) {
            var r = document.createRange();
            r.setStart(a.node, a.offset);
            r.collapse(true);
            return r.comparePoint(b.node, b.offset) > 0;
          }
          function same(a, b) {
            return a.node === b.node && a.offset === b.offset;
          }
          function ends() {
            var sel = window.getSelection();
            if (!sel || sel.isCollapsed || !sel.rangeCount) return null;
            var r = sel.getRangeAt(0);
            return { start: { node: r.startContainer, offset: r.startOffset }, end: { node: r.endContainer, offset: r.endOffset } };
          }
          function select(a, b) {
            if (same(a, b)) return;
            var sel = window.getSelection();
            st.programmatic = true;
            try {
              if (before(a, b)) sel.setBaseAndExtent(a.node, a.offset, b.node, b.offset);
              else sel.setBaseAndExtent(b.node, b.offset, a.node, a.offset);
            } catch (e) {}
            st.programmatic = false;
            place();
          }
          function show(el, x, y, start) {
            var o = probe.getBoundingClientRect();
            el.style.left = (x - o.left + (start ? -32 : -12)) + "px";
            el.style.top = (y - o.top) + "px";
            el.style.display = "block";
          }
          var lastSent = null;
          function report(s, e, more) {
            var k = window.devicePixelRatio || 1;
            var msg = s || e ? JSON.stringify({ s: s ? [s[0] * k, s[1] * k] : null, e: e ? [e[0] * k, e[1] * k] : null, n: more ? 1 : 0 }) : "";
            if (msg === lastSent) return;
            lastSent = msg;
            try { window.${BRIDGE}.onHandles(msg); } catch (x) {}
          }
          function place() {
            heal();
            hs.style.display = "none";
            he.style.display = "none";
            var e = ends();
            if (!e) {
              report(null, null);
              return;
            }
            var a = rectAt(e.start.node, e.start.offset);
            var b = rectAt(e.end.node, e.end.offset);
            var h = window.innerHeight;
            var sa = a && onPage(a) && a.bottom > 0 && a.top < h ? [a.left, a.bottom] : null;
            var sb = b && onPage(b) && b.bottom > 0 && b.top < h ? [b.right, b.bottom] : null;
            var more = !!sb && !st.mode && !scrollMode() && root.scrollLeft + window.innerWidth < root.scrollWidth - 2 && edgeOf(b, 1);
            report(sa, sb, more);
          }
          function grab(mode, x, y) {
            var sel = ends();
            if (!sel) return false;
            clearTimeout(st.timer);
            st.pressing = false;
            lock();
            st.mode = mode;
            var p = mode === "start" ? sel.start : sel.end;
            var r = rectAt(p.node, p.offset);
            st.grabX = r ? x - (mode === "start" ? r.left : r.right) : 0;
            st.grabY = r ? y - (r.top + r.bottom) / 2 : 24;
            st.lastX = x;
            st.lastY = y;
            notify("drag");
            return true;
          }
          function dragTo(x, y) {
            if (!st.mode) return;
            st.lastX = x;
            st.lastY = y;
            extend(x - st.grabX, y - st.grabY);
            edges();
          }
          function release() {
            stopEdges();
            if (!st.mode) return;
            st.mode = null;
            st.word = null;
            st.afterTurn = false;
            place();
            notify(ends() ? "done" : "clear");
          }
          function lock() {
            if (st.locked === null) st.locked = root.scrollLeft;
          }
          function clearSelection() {
            st.programmatic = true;
            try { window.getSelection().removeAllRanges(); } catch (e) {}
            st.programmatic = false;
            st.mode = null;
            st.word = null;
            st.locked = null;
            place();
            notify("clear");
          }
          function extend(x, y) {
            if (st.afterTurn) {
              if (Math.abs(x - st.turnX) < 14 && Math.abs(y - st.turnY) < 14) return;
              st.afterTurn = false;
            }
            var c = caretAt(x, y);
            var e = ends();
            if (!c) return;
            if (st.mode === "long") {
              var w = wordAround(c);
              if (w && (!onPage(rectAt(w.end.node, w.end.offset)) || !onPage(rectAt(w.start.node, w.start.offset)))) w = null;
              if (before(c, st.word.start)) select(st.word.end, w ? w.start : c);
              else select(st.word.start, w ? w.end : c);
              return;
            }
            if (!e) return;
            var wd = wordAround(c);
            if (wd && (!onPage(rectAt(wd.end.node, wd.end.offset)) || !onPage(rectAt(wd.start.node, wd.start.offset)))) wd = null;
            if (st.mode === "end") {
              var to = wd && before(e.start, wd.end) ? wd.end : c;
              if (!before(e.start, to)) return;
              select(e.start, to);
            } else if (st.mode === "start") {
              var from = wd && before(wd.start, e.end) ? wd.start : c;
              if (!before(from, e.end)) return;
              select(from, e.end);
            }
          }
          function forward() {
            return st.mode === "end" || (st.mode === "long" && st.word && !before(caretAt(st.lastX, st.lastY) || st.word.start, st.word.start));
          }
          function turn(dir) {
            st.edgeTimer = 0;
            if (!st.mode || st.turning) return;
            if (scrollMode()) return;
            var w = window.innerWidth;
            var target = root.scrollLeft + dir * w;
            if (target < -1 || target > root.scrollWidth - w + 1) return;
            st.turning = dir;
            st.turnTarget = target;
            st.locked = null;
            notify("sel-turn:" + dir);
            setTimeout(function () { if (st.turning === dir) turned(); }, 700);
          }
          function turned() {
            var dir = st.turning;
            st.turning = 0;
            if (Math.abs(root.scrollLeft - st.turnTarget) > 2) root.scrollLeft = st.turnTarget;
            st.locked = st.turnTarget;
            if (!st.mode) { place(); return; }
            var e = ends();
            var w = window.innerWidth, h = window.innerHeight;
            var at = caretAt(st.lastX - st.grabX, st.lastY - st.grabY);
            var probe = at || (dir > 0 ? caretAt(24, 24) : caretAt(w - 24, h - 24));
            if (e && probe) {
              if (dir > 0) select(st.mode === "start" ? e.end : e.start, probe);
              else select(probe, st.mode === "end" ? e.start : e.end);
            }
            st.afterTurn = false;
            place();
            edges();
          }
          function autoScroll(dy) {
            clearInterval(st.scrollTimer);
            st.scrollTimer = setInterval(function () {
              if (!st.mode) { clearInterval(st.scrollTimer); return; }
              window.scrollBy(0, dy);
              extend(st.lastX, st.lastY - st.grabY);
            }, 40);
          }
          function edges() {
            var x = st.lastX, y = st.lastY;
            var w = window.innerWidth, h = window.innerHeight;
            var fwd = forward();
            var c = caretAt(x, y - st.grabY);
            var cb = c ? rectAt(c.node, c.offset) : null;
            var py = y - st.grabY;
            var onLast = fwd && !!cb && edgeOf(cb, 1);
            var onFirst = !fwd && !!cb && edgeOf(cb, -1);
            var below = onLast && (py > cb.top + cb.height * 0.5 || x > w * 0.55);
            var above = onFirst && (py < cb.bottom - cb.height * 0.5 || x < w * 0.45);
            var atEnd = y > h - 22 || x > w - 14 || below || (!c && y > h * 0.6);
            var atStart = y < 22 || x < 14 || above || (!c && y < h * 0.4);
            if (scrollMode()) {
              clearInterval(st.scrollTimer);
              if (fwd && y > h - 48) autoScroll(10);
              else if (!fwd && y < 48) autoScroll(-10);
              return;
            }
            var want = fwd && atEnd ? 1 : (!fwd && atStart ? -1 : 0);
            if (!want) {
              clearTimeout(st.edgeTimer);
              st.edgeTimer = 0;
              return;
            }
            if (!st.edgeTimer) st.edgeTimer = setTimeout(function () { turn(want); }, 600);
          }
          function edgeOf(cb, dir) {
            var w = window.innerWidth, h = window.innerHeight;
            var y = dir > 0 ? cb.bottom + cb.height * 0.7 : cb.top - cb.height * 0.7;
            if (y >= h - 1 || y <= 1) return true;
            var xs = [w * 0.5, w * 0.25, w * 0.75];
            for (var i = 0; i < xs.length; i++) {
              var r = document.caretRangeFromPoint ? document.caretRangeFromPoint(xs[i], y) : null;
              if (!r || !r.startContainer || r.startContainer.nodeType !== 3) continue;
              var b = rectAt(r.startContainer, r.startOffset);
              if (!b || !onPage(b) || !b.height) continue;
              if (dir > 0 ? b.top > cb.top + cb.height * 0.5 : b.bottom < cb.bottom - cb.height * 0.5) return false;
            }
            return true;
          }
          function stopEdges() {
            clearTimeout(st.edgeTimer);
            st.edgeTimer = 0;
            clearInterval(st.scrollTimer);
          }
          function longPress() {
            st.pressing = false;
            var w = wordAt(st.startX, st.startY);
            if (!w) return;
            lock();
            st.word = w;
            st.mode = "long";
            st.grabY = 0;
            select(w.start, w.end);
            notify("start");
          }

          document.addEventListener("selectstart", function (e) {
            if (!st.programmatic) e.preventDefault();
          }, true);
          document.addEventListener("contextmenu", function (e) { e.preventDefault(); }, true);
          document.addEventListener("dragstart", function (e) {
            e.preventDefault();
            e.stopPropagation();
          }, true);
          document.addEventListener("selectionchange", function () {
            var sel = window.getSelection();
            if (!sel || sel.isCollapsed) {
              st.locked = null;
              if (!st.programmatic) st.mode = null;
            }
            place();
          });
          window.addEventListener("scroll", function () {
            if (st.locked !== null && ends() && root.scrollLeft !== st.locked) root.scrollLeft = st.locked;
            place();
          }, true);
          window.addEventListener("resize", place);
          window.addEventListener("click", function (e) {
            if (st.swallow) {
              st.swallow = false;
              e.preventDefault();
              e.stopImmediatePropagation();
            }
          }, true);

          document.addEventListener("touchstart", function (e) {
            if (e.touches.length > 1) {
              clearTimeout(st.timer);
              st.pressing = false;
              return;
            }
            var t = e.touches[0];
            st.startX = st.lastX = t.clientX;
            st.startY = st.lastY = t.clientY;
            var h = e.target && e.target.closest ? e.target.closest(".wf-h") : null;
            var sel = ends();
            if (h && sel) {
              e.preventDefault();
              e.stopPropagation();
              lock();
              st.mode = h === hs ? "start" : "end";
              var p = st.mode === "start" ? sel.start : sel.end;
              var r = rectAt(p.node, p.offset);
              st.grabX = r ? t.clientX - (st.mode === "start" ? r.left : r.right) : 0;
              st.grabY = r ? t.clientY - (r.top + r.bottom) / 2 : 24;
              notify("drag");
              return;
            }
            st.pressing = true;
            clearTimeout(st.timer);
            st.timer = setTimeout(longPress, 430);
          }, { capture: true, passive: false });

          document.addEventListener("touchmove", function (e) {
            var t = e.touches[0];
            if (!t) return;
            st.lastX = t.clientX;
            st.lastY = t.clientY;
            if (st.pressing && (Math.abs(t.clientX - st.startX) > 10 || Math.abs(t.clientY - st.startY) > 10)) {
              clearTimeout(st.timer);
              st.pressing = false;
            }
            if (st.mode) {
              e.preventDefault();
              e.stopPropagation();
              extend(t.clientX - st.grabX, t.clientY - st.grabY);
              edges();
            }
          }, { capture: true, passive: false });

          function finish(e, cancelled) {
            clearTimeout(st.timer);
            stopEdges();
            if (st.mode) {
              if (e && e.cancelable) e.preventDefault();
              if (e) e.stopPropagation();
              st.mode = null;
              st.word = null;
              st.afterTurn = false;
              st.swallow = true;
              setTimeout(function () { st.swallow = false; }, 600);
              place();
              notify(ends() ? "done" : "clear");
              return;
            }
            if (st.pressing && !cancelled) {
              st.pressing = false;
              if (ends()) {
                st.swallow = true;
                setTimeout(function () { st.swallow = false; }, 600);
                clearSelection();
              }
            }
            st.pressing = false;
          }
          document.addEventListener("touchend", function (e) { finish(e, false); }, { capture: true, passive: false });
          document.addEventListener("touchcancel", function (e) { finish(null, true); }, true);

          function box() {
            var sel = window.getSelection();
            if (!sel || sel.isCollapsed || !sel.rangeCount) return "";
            var rs = sel.getRangeAt(0).getClientRects();
            var h = window.innerHeight, t = 1e9, b = -1e9;
            for (var i = 0; i < rs.length; i++) {
              var r = rs[i];
              if (!r.width || !onPage(r) || r.bottom < 0 || r.top > h) continue;
              t = Math.min(t, r.top);
              b = Math.max(b, r.bottom);
            }
            if (b < t) return "";
            var k = window.devicePixelRatio || 1;
            return (t * k) + "," + (b * k);
          }
          function sentenceEnd(from) {
            var w = window.innerWidth;
            var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
            walker.currentNode = from.node;
            var node = from.node, off = from.offset, last = null, count = 0;
            while (node && count < 4000) {
              var text = node.nodeValue || "";
              for (var i = off; i < text.length; i++) {
                var r = rectAt(node, i + 1);
                if (!r || !onPage(r)) return last;
                last = { node: node, offset: i + 1 };
                count++;
                if (/[.!?…]/.test(text[i])) {
                  var j = i + 1;
                  while (j < text.length && /[»"”’)\]]/.test(text[j])) j++;
                  if (j >= text.length || /\s/.test(text[j])) return { node: node, offset: j };
                }
              }
              node = walker.nextNode();
              off = 0;
            }
            return last;
          }
          function prepPage() {
            st.locked = null;
            st.turnTarget = root.scrollLeft + window.innerWidth;
          }
          function nextPage() {
            if (Math.abs(root.scrollLeft - st.turnTarget) > 2) root.scrollLeft = st.turnTarget;
            st.locked = st.turnTarget;
            var e = ends();
            if (!e) { place(); return; }
            var first = caretAt(20, 20) || caretAt(40, 40);
            var start = first && before(e.end, first) ? first : e.end;
            var to = sentenceEnd(start) || first;
            if (to && before(e.start, to)) select(e.start, to);
            place();
            notify("done");
          }
          window.__wfSel = {
            prepPage: prepPage,
            nextPage: nextPage,
            turned: turned,
            box: box,
            place: place,
            clear: clearSelection,
            heal: function () { heal(); lastSent = null; place(); },
            grab: grab,
            drag: dragTo,
            release: release
          };
        })();
    """.trimIndent()
}
