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

  function isHiddenByStyle(el) {
    try {
      var style = window.getComputedStyle ? window.getComputedStyle(el) : null;
      if (!style) return false;
      if (style.display === 'none') return true;
      if (style.visibility === 'hidden') return true;
      if (style.opacity === '0') return true;
      return false;
    } catch (_e) {
      return false;
    }
  }

  function isVisible(el) {
    if (!el || el.nodeType !== 1) return false;
    if (el.hasAttribute && el.hasAttribute('hidden')) return false;
    var ariaHidden = el.getAttribute && el.getAttribute('aria-hidden');
    if (ariaHidden === 'true') return false;
    if (isHiddenByStyle(el)) return false;
    // offsetParent can be null for fixed-position elements; keep as a soft signal.
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
    var whitelist = ['href', 'name', 'type', 'value', 'placeholder', 'aria-label', 'role'];
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
      el.focus && el.focus();
    } catch (_e) {}
    try {
      if (typeof el.click === 'function') {
        el.click();
        return;
      }
    } catch (_e) {}
    try {
      var opts = { bubbles: true, cancelable: true, view: window };
      el.dispatchEvent(new MouseEvent('mousedown', opts));
      el.dispatchEvent(new MouseEvent('mouseup', opts));
      el.dispatchEvent(new MouseEvent('click', opts));
    } catch (_e) {}
  }

  function setValueWithEvents(el, value) {
    var v = safeString(value);
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

  window.__agentBrowser = {
    snapshot: function snapshot(options) {
      var cfg = options || {};
      var maxNodes = typeof cfg.maxNodes === 'number' && cfg.maxNodes > 0 ? cfg.maxNodes : 500;
      var maxTextPerNode = typeof cfg.maxTextPerNode === 'number' && cfg.maxTextPerNode > 0 ? cfg.maxTextPerNode : 200;
      var maxAttrValueLen = typeof cfg.maxAttrValueLen === 'number' && cfg.maxAttrValueLen > 0 ? cfg.maxAttrValueLen : 150;
      var interactiveOnly = cfg.interactiveOnly !== false;
      var cursorInteractive = cfg.cursorInteractive === true;
      var scopeSelector = cfg.scope || null;

      var rootEl = null;
      try {
        rootEl = scopeSelector ? document.querySelector(scopeSelector) : document.body;
      } catch (_e) {
        rootEl = document.body;
      }
      if (!rootEl) {
        return makeErr('snapshot', {}, 'no_root', 'document.body not found');
      }

      var stats = { nodesVisited: 0, nodesEmitted: 0, truncated: false, truncateReasons: [] };
      var refs = {};
      var refSeq = 1;

      function walk(el, depth) {
        if (!el || el.nodeType !== 1) return null;
        if (!isVisible(el)) return null;

        stats.nodesVisited++;
        if (stats.nodesVisited > maxNodes) {
          stats.truncated = true;
          stats.truncateReasons.push('maxNodes');
          return null;
        }

        var tag = (el.tagName || '').toLowerCase();
        var role = computeRole(tag, el);
        var name = computeName(el, tag, role, maxTextPerNode);
        var interactive = isInteractive(el, tag, role, cursorInteractive);

        var isContent =
          role === 'heading' ||
          role === 'img' ||
          tag === 'p' ||
          role === 'listitem' ||
          role === 'list';

        var includeNode = interactive || (!interactiveOnly && (isContent || role === 'main' || role === 'navigation'));
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
        if (interactive) {
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
      return {
        ok: true,
        type: 'snapshot',
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
        var attrs = pickAttrs(el, 150);
        var json = JSON.stringify(attrs);
        var tr = json.length > limit;
        var val = tr ? json.slice(0, limit) : json;
        return makeOk('query', { ref: r, kind: k, value: val, truncated: tr });
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
      return {
        ok: false,
        type: 'page',
        kind: String(kind || ''),
        meta: { ts: now() },
        error: { code: 'not_implemented', message: 'page not implemented in v1 skeleton' },
      };
    },
  };
})();
