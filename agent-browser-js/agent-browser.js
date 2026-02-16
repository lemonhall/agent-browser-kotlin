// agent-browser.js (v1)
// Exposes window.__agentBrowser for Android WebView injection.
(function () {
  if (typeof window === 'undefined') return;
  if (window.__agentBrowser) return;

  function now() {
    return Date.now();
  }

  function safeString(v) {
    return typeof v === 'string' ? v : (v == null ? '' : String(v));
  }

  function clampString(s, maxLen) {
    var str = safeString(s).trim().replace(/\s+/g, ' ');
    if (!maxLen || maxLen <= 0) return '';
    if (str.length <= maxLen) return str;
    return str.slice(0, Math.max(0, maxLen - 1)) + '…';
  }

  function isVisible(el) {
    if (!el || el.nodeType !== 1) return false;
    if (el.hasAttribute && el.hasAttribute('hidden')) return false;
    var ariaHidden = el.getAttribute && el.getAttribute('aria-hidden');
    if (ariaHidden === 'true') return false;

    var style = null;
    try {
      style = window.getComputedStyle ? window.getComputedStyle(el) : null;
    } catch (_e0) {
      style = null;
    }
    if (style) {
      if (style.display === 'none') return false;
      if (style.visibility === 'hidden') return false;
      if (parseFloat(style.opacity) === 0) return false;
    }

    // NOTE: body/html often have offsetParent === null; treat them as visible roots.
    var tag = (el.tagName || '').toLowerCase();
    if (tag !== 'body' && tag !== 'html') {
      try {
        var pos = style ? style.position : '';
        if (el.offsetParent === null && pos !== 'fixed') return false;
      } catch (_e2) {}
    }
    return true;
  }

  function attr(el, name) {
    var v = el.getAttribute ? el.getAttribute(name) : null;
    return v == null ? null : String(v);
  }

  function makeSet(list) {
    var out = {};
    for (var i = 0; i < list.length; i++) out[list[i]] = true;
    return out;
  }

  function has(setObj, key) {
    return !!(setObj && key && setObj[key]);
  }

  // Align PRD-V4 5.2.3 role sets (agent-browser snapshot.ts style).
  var INTERACTIVE_ROLES = makeSet([
    'button', 'link', 'textbox', 'checkbox', 'radio',
    'combobox', 'listbox', 'menuitem', 'menuitemcheckbox',
    'menuitemradio', 'option', 'searchbox', 'slider',
    'spinbutton', 'switch', 'tab', 'treeitem',
    // cursor-interactive synthetic roles (PRD-V4 5.2.3)
    'clickable', 'focusable',
  ]);

  var CONTENT_ROLES = makeSet([
    'heading', 'cell', 'gridcell', 'columnheader', 'rowheader',
    'listitem', 'article', 'region', 'main', 'navigation',
    'img', 'progressbar', 'meter',
  ]);

  var STRUCTURAL_ROLES = makeSet([
    'generic', 'group', 'list', 'table', 'row', 'rowgroup',
    'grid', 'treegrid', 'menu', 'menubar', 'toolbar',
    'tablist', 'tree', 'directory', 'document', 'application',
    'presentation', 'none', 'form', 'banner', 'contentinfo',
    'complementary', 'dialog',
  ]);

  // NOTE: keep <a> interactive only when it has href (see computeRole + isStandardInteractive).
  var NATIVE_INTERACTIVE_TAGS = makeSet(['button', 'input', 'select', 'textarea', 'details', 'summary']);

  function computeRole(tag, el) {
    var explicit = attr(el, 'role');
    if (explicit) return explicit;

    // PRD-V4 5.2.2 implicit roles
    if (tag === 'a') return attr(el, 'href') ? 'link' : 'generic';
    if (tag === 'button') return 'button';
    if (tag === 'textarea') return 'textbox';
    if (tag === 'select') return 'combobox';
    if (tag === 'img') return 'img';
    if (tag === 'form') return 'form';
    if (tag === 'table') return 'table';
    if (tag === 'tr') return 'row';
    if (tag === 'td') return 'cell';
    if (tag === 'th') return 'columnheader';
    if (tag === 'nav') return 'navigation';
    if (tag === 'main') return 'main';
    if (tag === 'header') return 'banner';
    if (tag === 'footer') return 'contentinfo';
    if (tag === 'article') return 'article';
    if (tag === 'section') return 'region';
    if (tag === 'aside') return 'complementary';
    if (tag === 'dialog') return 'dialog';
    if (tag === 'details') return 'group';
    if (tag === 'summary') return 'button';
    if (tag === 'option') return 'option';
    if (tag === 'progress') return 'progressbar';
    if (tag === 'meter') return 'meter';
    if (tag === 'ul' || tag === 'ol') return 'list';
    if (tag === 'li') return 'listitem';
    if (tag === 'input') {
      var type = (attr(el, 'type') || 'text').toLowerCase();
      if (type === 'search') return 'searchbox';
      if (type === 'email' || type === 'text' || type === 'password' || type === 'tel' || type === 'url') return 'textbox';
      if (type === 'number') return 'spinbutton';
      if (type === 'checkbox') return 'checkbox';
      if (type === 'radio') return 'radio';
      if (type === 'submit' || type === 'reset' || type === 'button') return 'button';
      if (type === 'range') return 'slider';
      return 'textbox';
    }
    if (tag && tag.length === 2 && tag[0] === 'h') return 'heading';
    return 'generic';
  }

  function headingLevel(tag) {
    if (!tag || tag.length !== 2 || tag[0] !== 'h') return null;
    var n = parseInt(tag[1], 10);
    return (n >= 1 && n <= 6) ? n : null;
  }

  function computeNameFromLabelledBy(el, maxLen) {
    var v = attr(el, 'aria-labelledby');
    if (!v) return null;
    var ids = v.split(/\s+/);
    var parts = [];
    for (var i = 0; i < ids.length; i++) {
      var id = safeString(ids[i]).trim();
      if (!id) continue;
      try {
        var target = document.getElementById(id);
        if (!target) continue;
        var t = safeString(target.innerText || target.textContent || '').trim();
        if (t) parts.push(t);
      } catch (_e0) {}
    }
    if (parts.length === 0) return null;
    return clampString(parts.join(' '), maxLen);
  }

  function computeName(el, tag, role, maxLen) {
    var ariaLabel = attr(el, 'aria-label');
    if (ariaLabel) return clampString(ariaLabel, maxLen);

    var labelledBy = computeNameFromLabelledBy(el, maxLen);
    if (labelledBy) return labelledBy;

    if (role === 'img') {
      var alt = attr(el, 'alt');
      if (alt) return clampString(alt, maxLen);
    }

    var title = attr(el, 'title');
    if (title) return clampString(title, maxLen);

    var placeholder = attr(el, 'placeholder');
    if ((role === 'textbox' || role === 'searchbox') && placeholder) return clampString(placeholder, maxLen);

    var text = '';
    try {
      text = el.innerText || el.textContent || '';
    } catch (_e) {
      text = '';
    }
    return clampString(text, maxLen);
  }

  function isStandardInteractive(el, tag, role) {
    if (has(INTERACTIVE_ROLES, role)) return true;
    if (tag === 'a' && !!attr(el, 'href')) return true;
    if (has(NATIVE_INTERACTIVE_TAGS, tag)) return true;
    return false;
  }

  // PRD-V4 5.2.5: only active when cursorInteractive=true; skip interactive roles and native tags.
  function isCursorInteractiveCandidate(el, role, tag) {
    if (has(INTERACTIVE_ROLES, role)) return false;
    if (has(NATIVE_INTERACTIVE_TAGS, tag)) return false;

    var hasCursorPointer = false;
    try {
      var style = window.getComputedStyle ? window.getComputedStyle(el) : null;
      hasCursorPointer = !!(style && style.cursor === 'pointer');
    } catch (_e0) {}

    var hasOnClick = false;
    try {
      hasOnClick = (el && el.hasAttribute && el.hasAttribute('onclick')) || (el && el.onclick != null);
    } catch (_e1) {}

    var tabindex = attr(el, 'tabindex');
    var hasTabIndex = tabindex != null && tabindex !== '-1';

    return hasCursorPointer || hasOnClick || hasTabIndex;
  }

  function computeDirectText(el, maxLen) {
    var parts = [];
    try {
      var nodes = el.childNodes || [];
      for (var i = 0; i < nodes.length; i++) {
        var n = nodes[i];
        if (!n || n.nodeType !== 3) continue;
        var t = safeString(n.nodeValue);
        if (t && t.trim()) parts.push(t);
      }
    } catch (_e0) {
      parts = [];
    }
    if (parts.length === 0) return null;
    var joined = parts.join(' ');
    var clamped = clampString(joined, maxLen);
    return clamped || null;
  }

  function pickAttrs(el, maxValueLen) {
    // PRD-V4 5.2.1 whitelist (+ title/alt 作为 name 计算辅助仍保留在 name 字段中)
    var whitelist = ['href', 'name', 'type', 'value', 'placeholder', 'src', 'action', 'method'];
    var out = {};
    for (var i = 0; i < whitelist.length; i++) {
      var k = whitelist[i];
      var v = attr(el, k);
      if (v == null) continue;
      out[k] = clampString(v, maxValueLen);
    }
    return out;
  }

  function assignRef(el, ref) {
    try {
      el.setAttribute('data-agent-ref', ref);
    } catch (_e) {}
  }

  function clearOldRefs() {
    try {
      var old = document.querySelectorAll('[data-agent-ref]');
      for (var i = 0; i < old.length; i++) {
        try { old[i].removeAttribute('data-agent-ref'); } catch (_e) {}
      }
    } catch (_e2) {}
  }

  function findByRef(ref) {
    try {
      return document.querySelector('[data-agent-ref="' + ref.replace(/"/g, '\\"') + '"]');
    } catch (_e) {
      return null;
    }
  }

  function makeOk(type, extra) {
    var base = { ok: true, type: type, meta: { ts: now() } };
    if (!extra) return base;
    for (var k in extra) base[k] = extra[k];
    return base;
  }

  function makeErr(type, extra, code, message) {
    var base = { ok: false, type: type, meta: { ts: now() }, error: { code: code, message: message || '' } };
    if (!extra) return base;
    for (var k in extra) base[k] = extra[k];
    return base;
  }

  function looksLikeCookieBanner(el) {
    if (!el || el.nodeType !== 1) return false;
    function haystack(node) {
      var id = '';
      var cls = '';
      var label = '';
      var txt = '';
      try { id = safeString(node.id); } catch (_e0) { id = ''; }
      try { cls = safeString(node.className); } catch (_e1) { cls = ''; }
      try { label = safeString(attr(node, 'aria-label')); } catch (_e2) { label = ''; }
      try { txt = safeString(node.innerText || node.textContent || ''); } catch (_e3) { txt = ''; }
      return (id + ' ' + cls + ' ' + label + ' ' + txt).toLowerCase();
    }
    var node = el;
    for (var i = 0; i < 6 && node; i++) {
      var h = haystack(node);
      if (h.indexOf('cookie') >= 0 || h.indexOf('consent') >= 0 || h.indexOf('gdpr') >= 0) return true;
      node = node.parentElement;
    }
    return false;
  }

  function clickBlockedByAnotherElement(targetEl) {
    if (!targetEl || targetEl.nodeType !== 1) return null;
    try {
      if (targetEl.scrollIntoView) targetEl.scrollIntoView({ block: 'center' });
    } catch (_e0) {}
    var rect = null;
    try { rect = targetEl.getBoundingClientRect ? targetEl.getBoundingClientRect() : null; } catch (_e1) { rect = null; }
    if (!rect) return null;
    var cx = rect.left + rect.width / 2;
    var cy = rect.top + rect.height / 2;
    var topEl = null;
    try { topEl = document.elementFromPoint ? document.elementFromPoint(cx, cy) : null; } catch (_e2) { topEl = null; }
    if (!topEl) return null;
    if (topEl === targetEl) return null;
    try {
      if (targetEl.contains && targetEl.contains(topEl)) return null;
    } catch (_e3) {}
    return topEl;
  }

  function dispatchMouseClick(el) {
    try {
      if (el && el.scrollIntoView) el.scrollIntoView({ block: 'center' });
    } catch (_e0) {}
    try {
      el.focus && el.focus();
    } catch (_e) {}
    try {
      var opts = { bubbles: true, cancelable: true, view: window };
      var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
      if (rect) {
        opts.clientX = rect.left + rect.width / 2;
        opts.clientY = rect.top + rect.height / 2;
        opts.button = 0;
      }
      // Try a more "realistic" sequence first (align PRD intent), then fall back to el.click().
      if (typeof PointerEvent !== 'undefined') {
        el.dispatchEvent(new PointerEvent('pointerdown', opts));
        el.dispatchEvent(new PointerEvent('pointerup', opts));
      }
      el.dispatchEvent(new MouseEvent('mousedown', opts));
      el.dispatchEvent(new MouseEvent('mouseup', opts));
      el.dispatchEvent(new MouseEvent('click', opts));
      return;
    } catch (_e) {}
    try {
      if (typeof el.click === 'function') el.click();
    } catch (_e2) {}
  }

  function setValueWithEvents(el, value) {
    var v = safeString(value);
    try {
      if (el && el.scrollIntoView) el.scrollIntoView({ block: 'center' });
    } catch (_e0) {}
    try { el.focus && el.focus(); } catch (_e1) {}
    try {
      var proto = el instanceof HTMLInputElement ? HTMLInputElement.prototype : (el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : null);
      var desc = proto ? Object.getOwnPropertyDescriptor(proto, 'value') : null;
      if (desc && desc.set) desc.set.call(el, v);
      else el.value = v;
    } catch (_e) {
      try { el.value = v; } catch (_e2) {}
    }
    try { el.dispatchEvent(new Event('input', { bubbles: true })); } catch (_e3) {}
    try { el.dispatchEvent(new Event('change', { bubbles: true })); } catch (_e4) {}
  }

  function pageScrollByXY(x, y) {
    try { window.scrollBy(x, y); } catch (_e1) {
      try { window.scrollBy({ left: x, top: y, behavior: 'auto' }); } catch (_e2) {}
    }
    var sx = 0;
    var sy = 0;
    try { sx = window.scrollX || 0; sy = window.scrollY || 0; } catch (_e3) { sx = 0; sy = 0; }
    return { success: true, scrollX: Math.round(sx), scrollY: Math.round(sy) };
  }

  function pageScrollToXY(x, y) {
    try { window.scrollTo(x, y); } catch (_e1) {
      try { window.scrollTo({ left: x, top: y, behavior: 'auto' }); } catch (_e2) {}
    }
    var sx = 0;
    var sy = 0;
    try { sx = window.scrollX || 0; sy = window.scrollY || 0; } catch (_e3) { sx = 0; sy = 0; }
    return { success: true, scrollX: Math.round(sx), scrollY: Math.round(sy) };
  }

  function pageGetUrl() { return safeString(location && location.href); }
  function pageGetTitle() { return safeString(document && document.title); }

  function pagePressKey(key) {
    var kk = safeString(key);
    try {
      var el = document.activeElement;
      if (el && typeof KeyboardEvent !== 'undefined') {
        el.dispatchEvent(new KeyboardEvent('keydown', { key: kk, bubbles: true }));
        el.dispatchEvent(new KeyboardEvent('keyup', { key: kk, bubbles: true }));
      }
    } catch (_e1) {}
    return { success: true, key: kk };
  }

  window.__agentBrowser = {
    snapshot: function snapshot(options) {
      var cfg = options || {};
      var maxNodes = typeof cfg.maxNodes === 'number' && cfg.maxNodes > 0 ? cfg.maxNodes : 500;
      var maxTextPerNode = typeof cfg.maxTextPerNode === 'number' && cfg.maxTextPerNode > 0 ? cfg.maxTextPerNode : 200;
      var maxAttrValueLen = typeof cfg.maxAttrValueLen === 'number' && cfg.maxAttrValueLen > 0 ? cfg.maxAttrValueLen : 150;
      var interactiveOnly = cfg.interactiveOnly !== false;
      var cursorInteractive = cfg.cursorInteractive === true;
      var scopeSelector = cfg.scope || null;

      var started = now();
      clearOldRefs();

      var rootEl = null;
      try {
        rootEl = scopeSelector ? document.querySelector(scopeSelector) : document.body;
      } catch (_e) {
        rootEl = document.body;
      }
      if (!rootEl) {
        return makeErr('snapshot', {}, 'no_root', 'document.body not found');
      }

      var stats = { domNodes: 0, skippedHidden: 0, nodesVisited: 0, nodesEmitted: 0, truncated: false, truncateReasons: [] };
      var refs = {};
      var refSeq = 1;

      try {
        if (rootEl && rootEl.getElementsByTagName) stats.domNodes = (rootEl.getElementsByTagName('*').length || 0) + 1;
      } catch (_e0) {
        stats.domNodes = 0;
      }

      function walk(el, depth) {
        if (!el || el.nodeType !== 1) return null;
        if (!isVisible(el)) {
          stats.skippedHidden++;
          return null;
        }

        stats.nodesVisited++;
        if (stats.nodesVisited > maxNodes) {
          stats.truncated = true;
          stats.truncateReasons.push('maxNodes');
          return null;
        }

        var tag = (el.tagName || '').toLowerCase();
        var baseRole = computeRole(tag, el);
        var standardInteractive = isStandardInteractive(el, tag, baseRole);
        var cursorCandidate = cursorInteractive && isCursorInteractiveCandidate(el, baseRole, tag);

        var effectiveRole = baseRole;
        if (cursorCandidate) {
          var tabindex = attr(el, 'tabindex');
          effectiveRole = (tabindex != null && tabindex !== '-1') ? 'focusable' : 'clickable';
        }

        var interactive = standardInteractive || cursorCandidate;
        var isContent = has(CONTENT_ROLES, effectiveRole);

        var name = null;
        if (interactive || isContent) {
          name = computeName(el, tag, effectiveRole, maxTextPerNode);
        }

        var text = null;
        // PRD-V4 5.2.1: text is direct text children; keep for non-interactive structural leaves when interactiveOnly=false.
        text = computeDirectText(el, maxTextPerNode);

        var shouldRef = false;
        if (interactive) {
          shouldRef = true;
        } else if (isContent) {
          if (!interactiveOnly) shouldRef = true;
          else if (name && name.length) shouldRef = true; // PRD-V4: content+name also gets ref in interactiveOnly=true
        }

        var children = [];
        var childrenEls = el.children || [];
        for (var j = 0; j < childrenEls.length; j++) {
          var kept = walk(childrenEls[j], depth + 1);
          if (kept) children.push(kept);
          if (stats.truncated) break;
        }

        if (!shouldRef && children.length === 0) {
          if (interactiveOnly) return null;
          if (!text) return null;
        }

        var lvl = headingLevel(tag);
        var ref = null;
        if (shouldRef) {
          ref = 'e' + refSeq++;
          assignRef(el, ref);
          refs[ref] = {
            ref: ref,
            tag: tag,
            role: effectiveRole,
            name: name || null,
            attrs: pickAttrs(el, maxAttrValueLen),
            cursorInteractive: !!cursorCandidate,
            interactive: !!interactive,
            level: lvl,
          };
        }

        stats.nodesEmitted++;
        var node = { tag: tag, role: effectiveRole, children: children };
        if (ref) node.ref = ref;
        if (name) node.name = name;
        if (text) node.text = text;
        if (lvl != null) node.level = lvl;
        node.cursorInteractive = !!cursorCandidate;
        node.interactive = !!interactive;
        if (ref) node.attrs = refs[ref].attrs;
        return node;
      }

      var tree = walk(rootEl, 0) || { tag: 'body', role: 'document', children: [] };
      stats.jsTimeMs = now() - started;
      // PRD-V4 naming compatibility for stats fields.
      stats.visitedNodes = stats.nodesVisited;
      stats.emittedNodes = stats.nodesEmitted;
      return {
        version: 1,
        ok: true,
        type: 'snapshot',
        url: safeString(location && location.href),
        title: safeString(document && document.title),
        timestamp: now(),
        meta: { url: safeString(location && location.href), title: safeString(document && document.title), ts: now() },
        stats: stats,
        refs: refs,
        tree: tree,
      };
    },
    action: function action(ref, kind, _payload) {
      var r = safeString(ref);
      var k = safeString(kind);
      var payload = _payload || {};

      var el = findByRef(r);
      if (!el) return makeErr('action', { ref: r, action: k }, 'ref_not_found', 'ref ' + r + ' not found');

      if (k === 'click') {
        var blocker = clickBlockedByAnotherElement(el);
        if (blocker) {
          var msg = 'blocked by another element (modal or overlay)';
          if (looksLikeCookieBanner(blocker)) {
            msg += '; try dismissing cookie banners';
          }
          return makeErr('action', { ref: r, action: k }, 'element_blocked', msg);
        }
        dispatchMouseClick(el);
        return makeOk('action', { ref: r, action: k });
      }

      if (k === 'fill') {
        setValueWithEvents(el, payload.value);
        return makeOk('action', { ref: r, action: k });
      }

      if (k === 'select') {
        try {
          if (el && el.scrollIntoView) el.scrollIntoView({ block: 'center' });
        } catch (_e0) {}
        var tag = (el.tagName || '').toLowerCase();
        if (tag !== 'select') return makeErr('action', { ref: r, action: k }, 'not_a_select', 'ref ' + r + ' is not a <select>');
        var raw = payload.values != null ? payload.values : payload.value;
        var values = Array.isArray(raw) ? raw : [raw];
        var normalized = [];
        for (var i = 0; i < values.length; i++) normalized.push(safeString(values[i]));
        try {
          for (var j = 0; j < el.options.length; j++) {
            var opt = el.options[j];
            var optText = '';
            try { optText = safeString(opt.textContent).trim(); } catch (_e3) { optText = ''; }
            opt.selected = normalized.indexOf(safeString(opt.value)) >= 0 || normalized.indexOf(optText) >= 0;
          }
        } catch (_e4) {}
        try { el.dispatchEvent(new Event('change', { bubbles: true })); } catch (_e5) {}
        return makeOk('action', { ref: r, action: k, values: normalized });
      }

      if (k === 'clear') {
        setValueWithEvents(el, '');
        return makeOk('action', { ref: r, action: k });
      }

      if (k === 'focus') {
        try { el.focus && el.focus(); } catch (_e6) {}
        return makeOk('action', { ref: r, action: k });
      }

      if (k === 'hover') {
        try {
          if (el && el.scrollIntoView) el.scrollIntoView({ block: 'center' });
        } catch (_e7) {}
        try { el.dispatchEvent(new MouseEvent('mouseenter', { bubbles: true, cancelable: true, view: window })); } catch (_e8) {}
        try { el.dispatchEvent(new MouseEvent('mouseover', { bubbles: true, cancelable: true, view: window })); } catch (_e9) {}
        return makeOk('action', { ref: r, action: k });
      }

      if (k === 'scroll_into_view') {
        try {
          if (el && el.scrollIntoView) el.scrollIntoView({ block: 'center', behavior: 'smooth' });
        } catch (_e10) {}
        return makeOk('action', { ref: r, action: k });
      }

      if (k === 'check' || k === 'uncheck') {
        var t = (attr(el, 'type') || '').toLowerCase();
        var isCheckbox = (el.tagName || '').toLowerCase() === 'input' && t === 'checkbox';
        var isRadio = (el.tagName || '').toLowerCase() === 'input' && t === 'radio';
        if (!isCheckbox && !isRadio) return makeErr('action', { ref: r, action: k }, 'not_checkable', 'ref ' + r + ' is not checkable');
        if (k === 'check') {
          try {
            if (!el.checked) dispatchMouseClick(el);
          } catch (_e11) {}
          return makeOk('action', { ref: r, action: k, checked: !!el.checked });
        }
        // uncheck
        if (!isCheckbox) return makeErr('action', { ref: r, action: k }, 'not_uncheckable', 'ref ' + r + ' is not uncheckable');
        try {
          if (el.checked) dispatchMouseClick(el);
        } catch (_e12) {}
        return makeOk('action', { ref: r, action: k, checked: !!el.checked });
      }

      return makeErr('action', { ref: r, action: k }, 'unsupported_action', 'unsupported action: ' + k);
    },
    query: function query(ref, kind, _payload) {
      var r = safeString(ref);
      var k = safeString(kind);
      var payload = _payload || {};
      var limit = typeof payload.limitChars === 'number' && payload.limitChars > 0 ? payload.limitChars : 4000;

      var el = findByRef(r);
      if (!el) return makeErr('query', { ref: r, kind: k }, 'ref_not_found', 'ref ' + r + ' not found');

      if (k === 'text') {
        var t = '';
        try { t = el.innerText || el.textContent || ''; } catch (_e) { t = ''; }
        t = safeString(t);
        var truncated = t.length > limit;
        var value = truncated ? t.slice(0, limit) : t;
        return makeOk('query', { ref: r, kind: k, value: value, truncated: truncated });
      }

      if (k === 'attrs') {
        var out = {};
        try {
          var list = el.attributes || [];
          for (var ai = 0; ai < list.length; ai++) {
            var a = list[ai];
            if (!a) continue;
            out[safeString(a.name)] = clampString(a.value, 150);
          }
        } catch (_e0) {
          out = {};
        }
        var json = JSON.stringify(out);
        var tr = json.length > limit;
        var val = tr ? json.slice(0, limit) : json;
        return makeOk('query', { ref: r, kind: k, value: val, truncated: tr });
      }

      if (k === 'value') {
        var v = '';
        try { v = el.value != null ? el.value : ''; } catch (_e1) { v = ''; }
        v = safeString(v);
        var tv = v.length > limit;
        var vv = tv ? v.slice(0, limit) : v;
        return makeOk('query', { ref: r, kind: k, value: vv, truncated: tv });
      }

      if (k === 'html') {
        var h = '';
        try { h = el.innerHTML || ''; } catch (_e2) { h = ''; }
        h = safeString(h);
        var th = h.length > limit;
        var vh = th ? h.slice(0, limit) : h;
        return makeOk('query', { ref: r, kind: k, value: vh, truncated: th });
      }

      if (k === 'computed_styles') {
        var s = null;
        try { s = window.getComputedStyle ? window.getComputedStyle(el) : null; } catch (_e3) { s = null; }
        var obj = s ? {
          display: s.display,
          color: s.color,
          fontSize: s.fontSize,
          backgroundColor: s.backgroundColor,
          visibility: s.visibility,
        } : {};
        var st = JSON.stringify(obj);
        var ts = st.length > limit;
        var vs = ts ? st.slice(0, limit) : st;
        return makeOk('query', { ref: r, kind: k, value: vs, truncated: ts });
      }

      if (k === 'outerHTML') {
        var html = '';
        try { html = el.outerHTML || ''; } catch (_e2) { html = ''; }
        html = safeString(html);
        var tr2 = html.length > limit;
        var val2 = tr2 ? html.slice(0, limit) : html;
        return makeOk('query', { ref: r, kind: k, value: val2, truncated: tr2 });
      }

      return makeErr('query', { ref: r, kind: k }, 'unsupported_query', 'unsupported query: ' + k);
    },
    page: function page(kind, _payload) {
      var k = safeString(kind);
      var payload = _payload || {};

      if (k === 'info') {
        var vp = { width: 0, height: 0 };
        try { vp.width = window.innerWidth || 0; vp.height = window.innerHeight || 0; } catch (_e1) {}
        var sx = 0;
        var sy = 0;
        try { sx = window.scrollX || 0; sy = window.scrollY || 0; } catch (_e2) {}
        return makeOk('page', {
          kind: k,
          url: pageGetUrl(),
          title: pageGetTitle(),
          scrollX: Math.round(sx),
          scrollY: Math.round(sy),
          viewport: vp,
        });
      }

      if (k === 'scroll') {
        var dx = typeof payload.deltaX === 'number' ? payload.deltaX : 0;
        var dy = typeof payload.deltaY === 'number' ? payload.deltaY : 0;
        var x = typeof payload.x === 'number' ? payload.x : null;
        var y = typeof payload.y === 'number' ? payload.y : null;
        var behavior = safeString(payload.behavior || '');
        try {
          if (x != null || y != null) {
            if (window.scrollTo) window.scrollTo({ left: x || 0, top: y || 0, behavior: behavior || 'auto' });
          } else if (window.scrollBy) {
            window.scrollBy({ left: dx, top: dy, behavior: behavior || 'auto' });
          }
        } catch (_e3) {
          try {
            if (x != null || y != null) window.scrollTo(x || 0, y || 0);
            else window.scrollBy(dx, dy);
          } catch (_e4) {}
        }
        var sx2 = 0;
        var sy2 = 0;
        try { sx2 = window.scrollX || 0; sy2 = window.scrollY || 0; } catch (_e5) {}
        return makeOk('page', { kind: k, scrollX: Math.round(sx2), scrollY: Math.round(sy2) });
      }

      if (k === 'pressKey') {
        var res = pagePressKey(payload.key);
        return makeOk('page', { kind: k, key: res.key });
      }

      return makeErr('page', { kind: k }, 'unsupported_page', 'unsupported page: ' + k);
    },
  };

  // PRD-V4 compatible sugar: allow calling as object methods (page.scrollBy/getUrl/etc).
  try {
    var pageFn = window.__agentBrowser.page;
    pageFn.scrollBy = function (x, y) { return pageScrollByXY(x, y); };
    pageFn.scrollTo = function (x, y) { return pageScrollToXY(x, y); };
    pageFn.getUrl = function () { return pageGetUrl(); };
    pageFn.getTitle = function () { return pageGetTitle(); };
    pageFn.pressKey = function (key) { return pagePressKey(key); };
  } catch (_eFinal) {}
})();
