/**
 * DataAgent embed widget SDK — 浮动对话窗启动器（零依赖）。
 *
 * 编程式：
 *   DataAgent.init({ agentId, token, position, primaryColor, title, baseUrl })
 *   DataAgent.open() | close() | toggle() | destroy()
 *   DataAgent.on('ready', fn) | off('ready', fn)
 *   DataAgent.setContext({ userId, page, ... })     // 宿主上下文，合并进每条 query
 *   DataAgent.openWithQuery('各区域销售额')          // 打开并预填查询
 *
 * 安全模式（推荐）：不传 token，传 tokenEndpoint —— 指向宿主后端一个返回
 *   { data: { token: "das_...", expiresIn: 1800 } } 的 GET 端点。宿主后端用发布令牌
 *   （= DataAgent Agent.apiKey，仅服务端持有）调 POST /api/embed/public/{agentId}/exchange
 *   换得短期会话令牌返回。发布令牌永不进浏览器；widget 在 ~80% 寿命自动刷新。
 *
 * Token 经 postMessage 传给 iframe（不进 URL）；iframe 加载 /embed/{agentId}。
 *
 * 兼容 script 标签自动初始化（data-agent-id + data-token 或 data-token-endpoint）。
 */
(function (global) {
  'use strict';

  var HOST_SOURCE = 'dataagent-host';
  var EMBED_SOURCE = 'dataagent-embed';
  var POSITIONS = ['bottom-right', 'bottom-left', 'top-right', 'top-left'];
  var DEFAULT_POSITION = 'bottom-right';
  var DEFAULT_COLOR = '#07C05F';
  var DEFAULT_TITLE = 'DataAgent 助手';
  var DEFAULT_WIDTH = 420;
  var DEFAULT_HEIGHT = 620;

  var instance = null;
  var listeners = {};

  function emit(event, payload) {
    (listeners[event] || []).slice().forEach(function (fn) {
      try { fn(payload); } catch (e) { console.error('[DataAgent]', e); }
    });
  }

  function positionStyles(position, kind) {
    var isLeft = position.indexOf('left') >= 0;
    var isTop = position.indexOf('top') >= 0;
    var h = isLeft ? 'left:24px' : 'right:24px';
    if (kind === 'launcher') return h + ';' + (isTop ? 'top:24px' : 'bottom:24px');
    return h + ';' + (isTop ? 'top:88px' : 'bottom:88px');
  }

  function createWidget(opts) {
    var agentId = opts.agentId || opts.agent || opts.channel;
    var staticToken = opts.token || '';
    var tokenEndpoint = opts.tokenEndpoint || opts.token_endpoint || '';
    if (!agentId || (!staticToken && !tokenEndpoint)) {
      console.warn('[DataAgent] agentId 和 (token 或 tokenEndpoint) 必填');
      return null;
    }

    var currentToken = staticToken || '';
    var tokenInFlight = null;
    var refreshTimer = null;
    var pendingContext = null;
    var pendingQuery = null;
    var iframeReady = false;
    var embedOrigin = '';

    var baseUrl = (opts.baseUrl || opts.base || '').replace(/\/$/, '');
    if (!baseUrl) {
      var script = document.currentScript;
      baseUrl = script && script.src ? script.src.replace(/\/dataagent-widget\.js.*$/, '') : (global.location ? global.location.origin : '');
    }
    try { embedOrigin = new URL(baseUrl + '/embed/' + agentId, global.location.href).origin; }
    catch (e) { embedOrigin = baseUrl; }

    function scheduleRefresh(expiresIn) {
      if (!tokenEndpoint || !expiresIn) return;
      if (refreshTimer) clearTimeout(refreshTimer);
      var delay = Math.max(Math.floor(Number(expiresIn) * 0.8), 30) * 1000;
      refreshTimer = setTimeout(function () {
        loadToken(true).then(function (t) { if (t) postToEmbed({ type: 'token', token: t }); }).catch(function () {});
      }, delay);
    }

    function loadToken(force) {
      if (staticToken) return Promise.resolve(staticToken);
      if (currentToken && !force) return Promise.resolve(currentToken);
      if (tokenInFlight) return tokenInFlight;
      tokenInFlight = fetch(tokenEndpoint, { method: 'GET', credentials: 'include', headers: { Accept: 'application/json' } })
        .then(function (res) { if (!res.ok) throw new Error('token endpoint HTTP ' + res.status); return res.json(); })
        .then(function (d) {
          var inner = (d && d.data) || d || {};
          var tok = inner.token || inner.sessionToken || '';
          if (!tok) throw new Error('token endpoint 未返回 token');
          currentToken = tok;
          scheduleRefresh(inner.expiresIn || inner.expires_in);
          return tok;
        })
        .catch(function (e) { console.error('[DataAgent] 加载 token 失败', e); throw e; })
        .then(function (t) { tokenInFlight = null; return t; }, function (e) { tokenInFlight = null; throw e; });
      return tokenInFlight;
    }

    function postToEmbed(msg) {
      msg.source = HOST_SOURCE;
      // 含 token 的消息禁止 targetOrigin='*'，防止任意页面窃取会话令牌
      if (!embedOrigin || embedOrigin === '*') {
        console.error('[DataAgent] refuse postMessage: embedOrigin unknown; pass absolute baseUrl');
        return;
      }
      if (iframe && iframe.contentWindow) {
        try { iframe.contentWindow.postMessage(msg, embedOrigin); } catch (e) {}
      }
    }

    var position = POSITIONS.indexOf(opts.position) >= 0 ? opts.position : DEFAULT_POSITION;
    var primaryColor = opts.primaryColor || opts.primary_color || DEFAULT_COLOR;
    var title = opts.title || DEFAULT_TITLE;
    var panelWidth = Number(opts.width) > 0 ? Number(opts.width) : DEFAULT_WIDTH;
    var panelHeight = Number(opts.height) > 0 ? Number(opts.height) : DEFAULT_HEIGHT;

    var launcher = document.createElement('button');
    launcher.type = 'button';
    launcher.setAttribute('aria-label', title);
    launcher.textContent = '💬';
    launcher.style.cssText = ['position:fixed', 'z-index:2147483000', 'width:56px', 'height:56px',
      'border-radius:50%', 'border:none', 'cursor:pointer', 'font-size:24px',
      'box-shadow:0 4px 16px rgba(0,0,0,.18)', 'background:' + primaryColor, 'color:#fff',
      'opacity:0.92', 'transition:opacity .2s', positionStyles(position, 'launcher')].join(';');

    var panel = document.createElement('div');
    panel.style.cssText = ['position:fixed', 'z-index:2147482999', 'width:' + panelWidth + 'px',
      'max-width:calc(100vw - 32px)', 'height:' + panelHeight + 'px', 'max-height:calc(100vh - 100px)',
      'border-radius:12px', 'overflow:hidden', 'box-shadow:0 8px 32px rgba(0,0,0,.2)', 'display:none',
      'background:#fff', positionStyles(position, 'panel')].join(';');

    var iframe = document.createElement('iframe');
    iframe.src = baseUrl + '/embed/' + encodeURIComponent(agentId);
    iframe.style.cssText = 'width:100%;height:100%;border:none';
    iframe.setAttribute('allow', 'clipboard-write');
    panel.appendChild(iframe);

    var open = false;
    function setOpen(v) {
      open = v;
      panel.style.display = v ? 'block' : 'none';
      launcher.style.opacity = v ? '1' : '0.92';
    }
    launcher.addEventListener('click', function () { setOpen(!open); });

    function onMessage(ev) {
      if (embedOrigin && ev.origin !== embedOrigin) return;
      var data = ev.data || {};
      if (data.source !== EMBED_SOURCE) return;
      if (data.type === 'ready') {
        iframeReady = true;
        loadToken(false).then(function (t) {
          postToEmbed({ type: 'token', token: t });
          if (pendingContext) postToEmbed({ type: 'context', context: pendingContext });
          if (pendingQuery) postToEmbed({ type: 'query', query: pendingQuery });
          emit('ready', {});
        }).catch(function () {});
      }
    }
    global.addEventListener('message', onMessage);

    var api = {
      open: function () { setOpen(true); return api; },
      close: function () { setOpen(false); return api; },
      toggle: function () { setOpen(!open); return api; },
      setContext: function (ctx) {
        pendingContext = ctx || null;
        if (iframeReady) postToEmbed({ type: 'context', context: ctx });
        return api;
      },
      openWithQuery: function (q) {
        setOpen(true);
        pendingQuery = q;
        if (iframeReady) postToEmbed({ type: 'query', query: q });
        return api;
      },
      destroy: function () {
        if (refreshTimer) clearTimeout(refreshTimer);
        global.removeEventListener('message', onMessage);
        if (launcher.parentNode) launcher.parentNode.removeChild(launcher);
        if (panel.parentNode) panel.parentNode.removeChild(panel);
        instance = null;
        listeners = {};
      }
    };
    document.body.appendChild(launcher);
    document.body.appendChild(panel);
    return api;
  }

  global.DataAgent = {
    init: function (opts) {
      if (instance) instance.destroy();
      instance = createWidget(opts || {});
      return instance;
    },
    on: function (event, fn) { (listeners[event] = listeners[event] || []).push(fn); return global.DataAgent; },
    off: function (event, fn) {
      listeners[event] = (listeners[event] || []).filter(function (f) { return f !== fn; });
      return global.DataAgent;
    }
  };

  // script 标签自动初始化：data-agent-id + (data-token | data-token-endpoint)
  var cur = document.currentScript;
  if (cur && cur.dataset) {
    var a = cur.dataset.agentId, t = cur.dataset.token, te = cur.dataset.tokenEndpoint;
    if (a && (t || te)) {
      var autoOpts = { agentId: a };
      if (t) autoOpts.token = t;
      if (te) autoOpts.tokenEndpoint = te;
      if (cur.dataset.position) autoOpts.position = cur.dataset.position;
      if (cur.dataset.primaryColor) autoOpts.primaryColor = cur.dataset.primaryColor;
      if (cur.dataset.title) autoOpts.title = cur.dataset.title;
      global.DataAgent.init(autoOpts);
    }
  }
})(typeof window !== 'undefined' ? window : this);
