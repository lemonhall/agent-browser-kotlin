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

  function computeRole(tag, el) {
    var explicit = attr(el, 'role');
    if (explicit) return explicit;

    if (tag === 'a') return 'link';
    if (tag === 'button') return 'button';
    if (tag === 'textarea') return 'textbox';
    if (tag === 'select') return 'combobox';
    if (tag === 'img') return 'img';
    if (tag === 'form') return 'form';
    if (tag === 'nav') return 'navigation';
    if (tag === 'main') return 'main';
    if (tag === 'header') return 'banner';
    if (tag === 'footer') return 'contentinfo';
    if (tag === 'ul' || tag === 'ol') return 'list';
    if (tag === 'li') return 'listitem';
    if (tag === 'input') {
      var type = (attr(el, 'type') || 'text').toLowerCase();
      if (type === 'search') return 'searchbox';
      if (type === 'email' || type === 'text' || type === 'password' || type === 'tel' || type === 'url') return 'textbox';
      if (type === 'checkbox') return 'checkbox';
      if (type === 'radio') return 'radio';
      if (type === 'submit' || type === 'button' || type === 'reset') return 'button';
      return 'textbox';
    }
    if (tag && tag.length === 2 && tag[0] === 'h') return 'heading';
    return 'generic';
  }

  function computeName(el, tag, role, maxLen) {
    var ariaLabel = attr(el, 'aria-label');
    if (ariaLabel) return clampString(ariaLabel, maxLen);

    if (role === 'img') {
      var alt = attr(el, 'alt');
      if (alt) return clampString(alt, maxLen);
    }

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

  function isInteractive(el, tag, role, cursorInteractive) {
    if (role === 'button' || role === 'link' || role === 'checkbox' || role === 'radio' || role === 'combobox' || role === 'textbox' || role === 'searchbox') return true;
    if (tag === 'a' && !!attr(el, 'href')) return true;
    if (tag === 'input' || tag === 'select' || tag === 'textarea' || tag === 'button') return true;
    if (attr(el, 'onclick') != null) return true;
    var tabindex = attr(el, 'tabindex');
    if (tabindex != null && tabindex !== '-1') return true;
    if (cursorInteractive) {
      try {
        var style = window.getComputedStyle ? window.getComputedStyle(el) : null;
        if (style && style.cursor === 'pointer') return true;
      } catch (_e) {}
    }
    return false;
  }

  function pickAttrs(el, maxValueLen) {
    var whitelist = ['href', 'name', 'type', 'value', 'placeholder', 'aria-label', 'role', 'alt'];
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
    return { success: true, scrollX: window.scrollX || 0, scrollY: window.scrollY || 0 };
  }

  function pageScrollToXY(x, y) {
    try { window.scrollTo(x, y); } catch (_e1) {
      try { window.scrollTo({ left: x, top: y, behavior: 'auto' }); } catch (_e2) {}
    }
    return { success: true, scrollX: window.scrollX || 0, scrollY: window.scrollY || 0 };
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
        var role = computeRole(tag, el);
        var interactive = isInteractive(el, tag, role, cursorInteractive);

        var isContent =
          role === 'heading' ||
          role === 'img' ||
          tag === 'p' ||
          role === 'listitem' ||
          role === 'list';

        var includeNode = interactive || (!interactiveOnly && (isContent || role === 'main' || role === 'navigation'));
        var name = null;
        if (interactive || (!interactiveOnly && isContent)) {
          name = computeName(el, tag, role, maxTextPerNode);
        }
        if (!includeNode) {
          // Even if we don't include this node, its descendants may contain interactive/content.
          var keptChildren = [];
          var kids = el.children || [];
          for (var i = 0; i < kids.length; i++) {
            var childNode = walk(kids[i], depth + 1);
            if (childNode) keptChildren.push(childNode);
            if (stats.truncated) break;
          }
          if (keptChildren.length === 0) return null;
          return { tag: tag, role: role, name: name || null, children: keptChildren };
        }

        var ref = null;
        var shouldRef = interactive || (!interactiveOnly && isContent);
        if (shouldRef) {
          ref = 'e' + refSeq++;
          assignRef(el, ref);
          refs[ref] = {
            ref: ref,
            tag: tag,
            role: role,
            name: name || null,
            attrs: pickAttrs(el, maxAttrValueLen),
          };
        }

        var children = [];
        var childrenEls = el.children || [];
        for (var j = 0; j < childrenEls.length; j++) {
          var kept = walk(childrenEls[j], depth + 1);
          if (kept) children.push(kept);
          if (stats.truncated) break;
        }

        stats.nodesEmitted++;
        var node = { tag: tag, role: role, children: children };
        if (ref) node.ref = ref;
        if (name) node.name = name;
        if (!interactiveOnly && isContent && name) node.text = name;
        if (ref) node.attrs = refs[ref].attrs;
        return node;
      }

      var tree = walk(rootEl, 0) || { tag: 'body', role: 'document', children: [] };
      stats.jsTimeMs = now() - started;
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
          scrollX: sx,
          scrollY: sy,
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
        return makeOk('page', { kind: k, scrollX: sx2, scrollY: sy2 });
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
