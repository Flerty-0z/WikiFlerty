package com.flerty.wikiflerty.reader

import android.webkit.JavascriptInterface

class TextSelectionBridge(private val onEvent: (String) -> Unit) {
    @JavascriptInterface
    fun onEvent(type: String) = onEvent.invoke(type)
}

internal object TextSelection {
    const val BRIDGE = "WikiFlertySelection"

    fun script(accent: String): String = SCRIPT.replace("__ACCENT__", accent)

    private val SCRIPT = """
        (function () {
          if (window.__wfSel) return;
          var root = document.scrollingElement || document.documentElement;
          var st = {
            anchor: null, word: null, mode: null, locked: null, timer: 0, edgeTimer: 0, pressing: false,
            startX: 0, startY: 0, lastX: 0, lastY: 0, grabX: 0, grabY: 0, programmatic: false,
            swallow: false, turnX: 0, turnY: 0, afterTurn: false, scrollTimer: 0
          };
          var css = document.createElement("style");
          css.textContent =
            "html,body{-webkit-touch-callout:none!important}" +
            ".wf-h{position:fixed;width:44px;height:44px;z-index:2147483647;display:none;touch-action:none;-webkit-user-select:none;user-select:none}" +
            ".wf-h i{position:absolute;top:0;width:20px;height:20px;background:__ACCENT__;box-shadow:0 1px 4px rgba(0,0,0,.35)}" +
            ".wf-e i{left:12px;border-radius:0 50% 50% 50%}" +
            ".wf-s i{left:12px;border-radius:50% 0 50% 50%}";
          (document.head || document.documentElement).appendChild(css);
          function handle(cls) {
            var el = document.createElement("div");
            el.className = "wf-h " + cls;
            el.setAttribute("aria-hidden", "true");
            el.appendChild(document.createElement("i"));
            document.documentElement.appendChild(el);
            return el;
          }
          var hs = handle("wf-s");
          var he = handle("wf-e");

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
            el.style.left = (start ? x - 32 : x - 12) + "px";
            el.style.top = y + "px";
            el.style.display = "block";
          }
          function place() {
            var e = ends();
            if (!e) {
              hs.style.display = "none";
              he.style.display = "none";
              return;
            }
            var a = rectAt(e.start.node, e.start.offset);
            var b = rectAt(e.end.node, e.end.offset);
            var h = window.innerHeight;
            if (a && onPage(a) && a.bottom > 0 && a.top < h) show(hs, a.left, a.bottom, true); else hs.style.display = "none";
            if (b && onPage(b) && b.bottom > 0 && b.top < h) show(he, b.right, b.bottom, false); else he.style.display = "none";
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

          window.__wfSel = { place: place, clear: clearSelection };
        })();
    """.trimIndent()
}
