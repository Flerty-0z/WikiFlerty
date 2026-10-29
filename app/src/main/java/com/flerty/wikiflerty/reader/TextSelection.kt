package com.flerty.wikiflerty.reader

import android.webkit.JavascriptInterface

class TextSelectionBridge(
    private val onEvent: (String) -> Unit,
    private val onFootnote: (String, String) -> Unit,
    private val onHandles: (String) -> Unit,
) {
    @JavascriptInterface
    fun onHandles(data: String) = onHandles.invoke(data)

    @JavascriptInterface
    fun onEvent(type: String) = onEvent.invoke(type)

    @JavascriptInterface
    fun onFootnote(text: String, url: String) = onFootnote.invoke(text, url)
}

internal object TextSelection {
    const val BRIDGE = "WikiFlertySelection"

    fun script(accent: String): String = SCRIPT.replace("__ACCENT__", accent) + "\n" + NOTES + "\n" + AUTO

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
            swallow: false, turnX: 0, turnY: 0, afterTurn: false, scrollTimer: 0
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
          function report(s, e) {
            var k = window.devicePixelRatio || 1;
            var msg = s || e ? JSON.stringify({ s: s ? [s[0] * k, s[1] * k] : null, e: e ? [e[0] * k, e[1] * k] : null }) : "";
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
            report(sa, sb);
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
              if (before(c, st.word.start)) select(st.word.end, w ? w.start : c);
              else select(st.word.start, w ? w.end : c);
              return;
            }
            if (!e) return;
            var wd = wordAround(c);
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
            if (!st.mode) return;
            if (scrollMode()) return;
            var w = window.innerWidth;
            var target = (st.locked === null ? root.scrollLeft : st.locked) + dir * w;
            if (target < 0 || target > root.scrollWidth - w + 1) return;
            st.locked = target;
            root.scrollLeft = target;
            st.afterTurn = true;
            st.turnX = st.lastX;
            st.turnY = st.lastY;
            var e = ends();
            if (e) {
              var probe = dir > 0 ? caretAt(24, 24) : caretAt(w - 24, window.innerHeight - 24);
              if (probe) {
                if (dir > 0) select(st.mode === "start" ? e.end : e.start, probe);
                else select(probe, st.mode === "end" ? e.start : e.end);
              }
            }
            place();
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
            var atEnd = y > h - 22 || x > w - 14 || (!c && y > h * 0.6);
            var atStart = y < 22 || x < 14 || (!c && y < h * 0.4);
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
            if (!st.edgeTimer) st.edgeTimer = setTimeout(function () { turn(want); }, 650);
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

          window.__wfSel = {
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
