package com.flerty.wikiflerty.reader

internal object SelectionGuard {
    val script = """
        (function () {
          if (window.__wfSelectionGuard) return;
          window.__wfSelectionGuard = true;
          var root = document.scrollingElement || document.documentElement;
          var locked = null;
          var good = null;
          var restoring = false;
          function onPage(node, offset) {
            try {
              var range = document.createRange();
              range.setStart(node, offset);
              range.collapse(true);
              var rects = range.getClientRects();
              var box = rects.length ? rects[0] : range.getBoundingClientRect();
              if (!box || (box.left === 0 && box.right === 0 && box.top === 0 && box.bottom === 0)) return true;
              return box.left < window.innerWidth && box.right > 0;
            } catch (e) {
              return true;
            }
          }
          document.addEventListener("selectionchange", function () {
            var selection = window.getSelection();
            if (!selection || selection.isCollapsed || !selection.focusNode) {
              locked = null;
              good = null;
              restoring = false;
              return;
            }
            if (restoring) {
              restoring = false;
              return;
            }
            if (locked === null) locked = root.scrollLeft;
            if (onPage(selection.focusNode, selection.focusOffset) && onPage(selection.anchorNode, selection.anchorOffset)) {
              good = [selection.anchorNode, selection.anchorOffset, selection.focusNode, selection.focusOffset];
            } else if (good) {
              restoring = true;
              selection.setBaseAndExtent(good[0], good[1], good[2], good[3]);
            }
          });
          window.addEventListener("scroll", function () {
            if (locked !== null && root.scrollLeft !== locked) root.scrollLeft = locked;
          }, true);
        })();
    """.trimIndent()
}
