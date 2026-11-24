package com.flerty.wikiflerty.reader

import android.content.Context
import com.flerty.wikiflerty.ai.AiException
import com.flerty.wikiflerty.ai.AiManager
import com.flerty.wikiflerty.ai.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class DualPara(val key: Int, val text: String)

data class DualScan(val href: String, val items: List<DualPara>, val first: Int)

class DualCache(context: Context, private val bookId: String) {
    private val dir = File(context.filesDir, "dual/" + hash(bookId))

    private fun file(href: String, lang: String) = File(dir, hash("$href|$lang") + ".json")

    suspend fun load(href: String, lang: String): MutableMap<String, String> = withContext(Dispatchers.IO) {
        val f = file(href, lang)
        if (!f.exists()) return@withContext mutableMapOf()
        val json = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return@withContext mutableMapOf()
        json.keys().asSequence().associateWith { json.optString(it) }.toMutableMap()
    }

    suspend fun save(href: String, lang: String, map: Map<String, String>) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val f = file(href, lang)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(JSONObject(map).toString())
        tmp.renameTo(f)
    }

    suspend fun clear() = withContext(Dispatchers.IO) { dir.deleteRecursively() }

    companion object {
        fun hash(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)

        fun key(text: String) = hash(text.replace(Regex("\\s+"), " ").trim())
    }
}

object DualTranslate {
    private const val GOOGLE_BATCH = 1400
    private const val AI_BATCH = 3500

    fun batches(items: List<DualPara>, ai: Boolean): List<List<DualPara>> {
        val limit = if (ai) AI_BATCH else GOOGLE_BATCH
        val out = mutableListOf<List<DualPara>>()
        var cur = mutableListOf<DualPara>()
        var size = 0
        items.forEach { p ->
            if (cur.isNotEmpty() && size + p.text.length > limit) {
                out += cur
                cur = mutableListOf()
                size = 0
            }
            cur += p
            size += p.text.length + 1
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    suspend fun google(batch: List<DualPara>, target: String): Map<Int, String> {
        val clean = batch.map { it.copy(text = it.text.replace(Regex("\\s+"), " ").trim()) }
        if (clean.size == 1 || clean.sumOf { it.text.length } > GOOGLE_BATCH) {
            return clean.associate { it.key to Translator.translate(it.text, target).text }
        }
        val joined = clean.joinToString("\n") { it.text }
        val lines = Translator.translate(joined, target).text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size == clean.size) return clean.mapIndexed { i, p -> p.key to lines[i] }.toMap()
        return clean.associate { it.key to Translator.translate(it.text, target).text }
    }

    suspend fun ai(manager: AiManager, batch: List<DualPara>, target: String, title: String): Map<Int, String> {
        val provider = manager.current() ?: throw AiException("Нет ключа ИИ")
        val model = manager.settings.value.modelFor(provider)
        val lang = Translator.title(target)
        val body = batch.mapIndexed { i, p -> "[${i + 1}] " + p.text.replace(Regex("\\s+"), " ").trim() }.joinToString("\n")
        val system = "Ты литературный переводчик. Переводишь художественный текст точно и естественно, сохраняя стиль автора, имена и тон."
        val prompt = buildString {
            append("Переведи каждый абзац книги")
            if (title.isNotBlank()) append(" «").append(title).append("»")
            append(" на язык: ").append(lang).append(".\n")
            append("Ответ — только строки вида «[номер] перевод», по одной строке на абзац, в том же порядке и с теми же номерами. Без пояснений.\n\n")
            append(body)
        }
        val answer = StringBuilder()
        manager.chat(provider, model, system, listOf(ChatMessage(true, prompt))).collect { answer.append(it) }
        val found = HashMap<Int, String>()
        Regex("^\\s*\\[(\\d+)]\\s*(.+)$", RegexOption.MULTILINE).findAll(answer).forEach { m ->
            val n = m.groupValues[1].toIntOrNull() ?: return@forEach
            val p = batch.getOrNull(n - 1) ?: return@forEach
            val t = m.groupValues[2].trim()
            if (t.isNotEmpty()) found[p.key] = t
        }
        if (found.isEmpty()) throw AiException("ИИ ответил не по форме")
        return found
    }

    fun parseScan(raw: String?): DualScan? {
        val text = raw?.trim() ?: return null
        val unq = if (text.startsWith("\"")) runCatching { JSONArray("[$text]").getString(0) }.getOrNull() else text
        val json = runCatching { JSONObject(unq ?: return null) }.getOrNull() ?: return null
        val arr = json.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            DualPara(o.optInt("k"), o.optString("t")).takeIf { it.text.isNotBlank() }
        }
        return DualScan(json.optString("href"), items, json.optInt("first", 0))
    }

    fun putCall(map: Map<Int, String>, mode: String, accent: Int): String {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k.toString(), v) }
        val color = String.format("#%06X", accent and 0xFFFFFF)
        return "window.__wfDual && window.__wfDual.put(${o}, ${JSONObject.quote(mode)}, ${JSONObject.quote(color)})"
    }

    val SCRIPT = """
        (function () {
          if (window.__wfDual) return;
          var SEL = "p,li,h1,h2,h3,h4,h5,h6,dd,dt,blockquote,figcaption";
          var mode = "under", color = "#e2626a", timer = 0, anchor = null;
          function style() {
            var st = document.getElementById("wf-dual-style");
            if (!st) { st = document.createElement("style"); st.id = "wf-dual-style"; (document.head || document.documentElement).appendChild(st); }
            var css =
              ".wf-dual{display:block;margin:0;padding:0}" +
              ".wf-dual[hidden]{display:none}" +
              ".wf-dual.wf-anim{overflow:hidden;transition:height .34s cubic-bezier(.2,.85,.25,1),opacity .3s ease}" +
              ".wf-dual.wf-fade{animation:wfDualFade .45s ease both}" +
              ".wf-dual-in{display:block;margin:.3em 0 .85em;padding:0 0 0 .75em;border-left:2px solid " + color + "66;opacity:.74;font-size:.9em;font-style:normal;font-weight:inherit;text-indent:0;text-align:inherit;line-height:inherit;transition:transform .34s cubic-bezier(.2,.85,.25,1)}" +
              ".wf-dual.wf-anim.wf-closed .wf-dual-in{transform:translateY(-.6em)}" +
              "@keyframes wfDualFade{from{opacity:0;transform:translateY(.3em)}to{opacity:1;transform:none}}" +
              ".wf-dual-tap{all:unset;display:inline-flex;align-items:center;justify-content:center;box-sizing:border-box;margin-left:.35em;margin-right:-2.05em;width:1.7em;height:1.35em;border-radius:.7em;border:1px solid " + color + "88;color:" + color + ";background:transparent;font-size:.72em;line-height:1;text-indent:0;vertical-align:.12em;cursor:pointer;-webkit-user-select:none;user-select:none;-webkit-tap-highlight-color:transparent;transition:background .25s ease,color .25s ease,border-color .25s ease,transform .18s cubic-bezier(.3,1.6,.5,1)}" +
              ".wf-dual-tap>span{display:inline-block;transition:transform .4s cubic-bezier(.3,1.4,.5,1)}" +
              ".wf-dual-tap:active,.wf-dual-tap.press{transform:scale(.82)}" +
              ".wf-dual-tap.on{background:" + color + ";border-color:" + color + ";color:#fff}" +
              ".wf-dual-tap.on>span{transform:rotate(180deg)}" +
              ".wf-dual-tap.busy{background:" + color + "22;color:" + color + "}" +
              ".wf-dual-tap.busy>span{animation:wfDualSpin .8s linear infinite}" +
              "@keyframes wfDualSpin{to{transform:rotate(360deg)}}" +
              ".wf-dual-tap.n{animation:wfTapIn .42s cubic-bezier(.2,.85,.25,1) both}" +
              "@keyframes wfTapIn{from{opacity:0;transform:scale(.6)}to{opacity:1;transform:none}}";
            if (st.textContent !== css) st.textContent = css;
          }
          function pick(sel) {
            var all = Array.prototype.slice.call(document.body ? document.body.querySelectorAll(sel) : []);
            return all.filter(function (el) {
              if (el.closest(".wf-dual")) return false;
              if (el.querySelector(sel)) return false;
              var t = (el.textContent || "").replace(/\s+/g, " ").trim();
              return /\p{L}.*\p{L}/u.test(t);
            });
          }
          function blocks() {
            var list = pick(SEL);
            return list.length >= 3 ? list : pick(SEL + ",div");
          }
          function textOf(el) {
            var c = el.cloneNode(true);
            Array.prototype.forEach.call(c.querySelectorAll(".wf-dual,.wf-dual-tap,sup,rt"), function (x) { x.remove(); });
            return (c.textContent || "").replace(/\s+/g, " ").trim();
          }
          function visible(el) {
            var r = el.getBoundingClientRect();
            return r.bottom > 0 && r.right > 0 && r.left < window.innerWidth && r.top < window.innerHeight;
          }
          function find(k) { return document.querySelector('[data-wfp="' + k + '"]'); }
          function holder(el, k) {
            var next = el.tagName === "LI" ? el.lastElementChild : el.nextElementSibling;
            if (next && next.classList.contains("wf-dual") && next.getAttribute("data-for") == k) return next;
            var tag = el.tagName === "LI" ? "span" : "div";
            var d = document.createElement(tag);
            d.className = "wf-dual";
            d.setAttribute("data-for", k);
            var inner = document.createElement(tag);
            inner.className = "wf-dual-in";
            d.appendChild(inner);
            if (el.tagName === "LI") el.appendChild(d); else el.parentNode.insertBefore(d, el.nextSibling);
            return d;
          }
          function firstVisible() {
            var list = document.querySelectorAll("[data-wfp]");
            for (var i = 0; i < list.length; i++) if (visible(list[i])) return list[i];
            return null;
          }
          function relayout() {
            clearTimeout(timer);
            var a = anchor || firstVisible();
            anchor = a;
            timer = setTimeout(function () {
              window.dispatchEvent(new Event("resize"));
              setTimeout(function () {
                if (a && !visible(a)) {
                  if (!a.id) a.id = "wfp-" + a.getAttribute("data-wfp");
                  try { window.readium && window.readium.scrollToId && window.readium.scrollToId(a.id); } catch (x) {}
                }
                anchor = null;
              }, 140);
            }, 90);
          }
          function after(d, f) {
            var fired = false;
            function end(e) { if (fired || (e && e.target !== d)) return; fired = true; d.removeEventListener("transitionend", end); f(); }
            d.addEventListener("transitionend", end);
            setTimeout(end, 480);
          }
          function soft() { try { window.__wfPeoplePlace && window.__wfPeoplePlace(); } catch (x) {} }
          function track() { try { window.__wfPeopleTrack && window.__wfPeopleTrack(520); } catch (x) {} }
          function paged() {
            var cs = getComputedStyle(document.documentElement);
            return (cs.columnCount && cs.columnCount !== "auto") || (cs.columnWidth && cs.columnWidth !== "auto") || document.scrollingElement.scrollWidth > window.innerWidth + 4;
          }
          function followers(d) {
            var out = [], n = d, guard = 0, w = window.innerWidth * 2.05, h = window.innerHeight * 2.05;
            while (n && n !== document.body && guard < 240) {
              var s = n.nextElementSibling;
              while (s && guard < 240) {
                guard++;
                var r = s.getBoundingClientRect();
                if (r.left >= w || r.top >= h) return out;
                out.push(s);
                s = s.nextElementSibling;
              }
              n = n.parentElement;
            }
            return out;
          }
          function swap(fade, apply) {
            fade.forEach(function (e) { e.style.transition = "opacity .14s ease"; e.style.opacity = "0"; });
            setTimeout(function () {
              apply();
              soft();
              requestAnimationFrame(function () {
                fade.forEach(function (e) { e.style.transition = "opacity .24s ease"; e.style.opacity = ""; });
                setTimeout(function () { fade.forEach(function (e) { e.style.transition = ""; }); soft(); }, 280);
              });
            }, 150);
          }
          function reveal(d) {
            track();
            if (paged()) {
              d.style.opacity = "0";
              swap(followers(d), function () {
                d.removeAttribute("hidden");
                void d.offsetHeight;
                d.style.transition = "opacity .26s ease";
                d.style.opacity = "1";
                setTimeout(function () { d.style.transition = ""; d.style.opacity = ""; }, 320);
              });
              return;
            }
            d.removeAttribute("hidden");
            var h = d.scrollHeight;
            d.classList.add("wf-closed");
            d.style.height = "0px";
            d.style.opacity = "0";
            void d.offsetHeight;
            d.classList.add("wf-anim");
            d.classList.remove("wf-closed");
            d.style.height = h + "px";
            d.style.opacity = "1";
            after(d, function () { d.classList.remove("wf-anim"); d.style.height = ""; d.style.opacity = ""; soft(); });
          }
          function conceal(d) {
            track();
            if (paged()) {
              swap([d].concat(followers(d)), function () { d.setAttribute("hidden", ""); d.style.transition = ""; d.style.opacity = ""; });
              return;
            }
            d.style.height = d.scrollHeight + "px";
            void d.offsetHeight;
            d.classList.add("wf-anim");
            d.classList.add("wf-closed");
            d.style.height = "0px";
            d.style.opacity = "0";
            after(d, function () { d.setAttribute("hidden", ""); d.classList.remove("wf-anim", "wf-closed"); d.style.height = ""; d.style.opacity = ""; soft(); });
          }
          function press(b) { b.classList.add("press"); setTimeout(function () { b.classList.remove("press"); }, 160); }
          function tapButton(el, k) {
            var b = el.querySelector(':scope > .wf-dual-tap');
            if (b) return b;
            b = document.createElement("button");
            b.className = "wf-dual-tap n";
            setTimeout(function () { b.classList.remove("n"); }, 600);
            b.type = "button";
            var g = document.createElement("span");
            g.textContent = "⇄";
            b.appendChild(g);
            b.setAttribute("data-for", k);
            b.addEventListener("click", function (e) {
              e.preventDefault(); e.stopPropagation();
              press(b);
              if (b.classList.contains("busy")) return;
              var d = document.querySelector('.wf-dual[data-for="' + k + '"]');
              if (d && d.textContent) {
                var show = d.hasAttribute("hidden");
                if (show) reveal(d); else conceal(d);
                b.classList.toggle("on", show);
              } else {
                b.classList.add("busy");
                try { window.${TextSelection.BRIDGE}.onEvent("dual:" + k); } catch (x) {}
              }
            }, true);
            ["touchstart", "touchend", "mousedown", "mouseup"].forEach(function (t) { b.addEventListener(t, function (e) { e.stopPropagation(); }, true); });
            if (el.tagName === "LI") { var d = el.querySelector(":scope > .wf-dual"); el.insertBefore(b, d); } else el.appendChild(b);
            return b;
          }
          window.__wfDual = {
            scan: function () {
              var list = blocks(), items = [], first = -1;
              list.forEach(function (el, i) {
                el.setAttribute("data-wfp", i);
                var t = textOf(el);
                items.push({ k: i, t: t.slice(0, 2400) });
                if (first < 0 && visible(el)) first = i;
              });
              return JSON.stringify({ href: location.pathname, items: items, first: Math.max(0, first) });
            },
            put: function (map, m, c) {
              mode = m || mode; color = c || color; style();
              var changed = false;
              Object.keys(map).forEach(function (k) {
                var el = find(k);
                if (!el) return;
                var d = holder(el, k);
                var inner = d.firstElementChild;
                var fresh = !inner.textContent;
                if (inner.textContent !== map[k]) inner.textContent = map[k];
                if (mode === "tap") {
                  var b = tapButton(el, k);
                  if (b.classList.contains("busy")) {
                    b.classList.remove("busy");
                    b.classList.add("on");
                    reveal(d);
                  } else if (b.classList.contains("on")) d.removeAttribute("hidden");
                  else d.setAttribute("hidden", "");
                } else {
                  d.removeAttribute("hidden");
                  if (fresh) { d.classList.add("wf-fade"); changed = true; }
                }
              });
              if (changed) relayout();
            },
            marks: function (c) {
              mode = "tap"; color = c || color; style();
              document.querySelectorAll("[data-wfp]").forEach(function (el) { tapButton(el, el.getAttribute("data-wfp")); });
              soft();
            },
            at: function (x, y) {
              var best = null, dist = 1e9;
              document.querySelectorAll(".wf-dual-tap").forEach(function (b) {
                var r = b.getBoundingClientRect();
                if (!r.width) return;
                var dx = Math.max(r.left - x, 0, x - r.right), dy = Math.max(r.top - y, 0, y - r.bottom);
                var d = Math.sqrt(dx * dx + dy * dy);
                if (d <= 16 && d < dist) { dist = d; best = b; }
              });
              if (!best) return false;
              best.click();
              return true;
            },
            fail: function (k) {
              var b = document.querySelector('.wf-dual-tap[data-for="' + k + '"]');
              if (b) b.classList.remove("busy", "on");
            },
            off: function () {
              var any = document.querySelector(".wf-dual,.wf-dual-tap");
              document.querySelectorAll(".wf-dual,.wf-dual-tap").forEach(function (x) { x.remove(); });
              if (any) relayout();
            }
          };
        })();
    """.trimIndent()
}
