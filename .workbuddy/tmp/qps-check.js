
(function () {
  var CDNS = [
    'https://unpkg.com/vue@3.4.38/dist/vue.global.prod.js',
    'https://cdn.jsdelivr.net/npm/vue@3.4.38/dist/vue.global.prod.js',
    'https://cdnjs.cloudflare.com/ajax/libs/vue/3.4.38/vue.global.prod.js'
  ];

  function loadVue(i) {
    if (i >= CDNS.length) {
      document.getElementById('app').innerHTML =
        '<div class="wrap"><div class="err">Vue 3 加载失败：已尝试 unpkg / jsDelivr / cdnjs 三个 CDN 均不可达。请连外网后刷新，或把 vue.global.prod.js 下载到本目录并改用本地引用。</div></div>';
      return;
    }
    var s = document.createElement('script');
    s.src = CDNS[i];
    s.onload = function () { if (window.Vue) boot(); else loadVue(i + 1); };
    s.onerror = function () { loadVue(i + 1); };
    document.head.appendChild(s);
  }

  function boot() {
    var createApp = window.Vue.createApp;

    var app = createApp({
      data: function () {
        return {
          vueReady: true,
          loadError: '',

          url: 'http://127.0.0.1:8083/api/order',
          method: 'POST',
          contentType: 'application/json',
          body: JSON.stringify({ userId: 2, items: [{ productId: 3, quantity: 1 }] }, null, 2),
          timeout: 8000,

          concurrency: 6,
          mode: 'duration',
          duration: 3,
          totalCount: 20,

          running: false,
          halted: false,
          elapsed: 0,
          tick: 0,

          stats: { total: 0, ok: 0, fail: 0 },
          rt: { avg: 0, p50: 0, p95: 0, p99: 0, max: 0 },
          codeRows: [],
          msgRows: [],
          chartBars: [],
          maxPerSec: 0
        };
      },

      computed: {
        hasBody: function () {
          return this.method !== 'GET' && this.method !== 'DELETE';
        },
        ready: function () {
          return !!this.url && (this.mode === 'duration' ? this.duration > 0 : this.totalCount > 0) && this.concurrency > 0;
        },
        qps: function () {
          var sec = this.elapsed / 1000;
          return sec > 0.001 ? this.stats.total / sec : 0;
        },
        successRate: function () {
          return this.stats.total ? (this.stats.ok / this.stats.total) * 100 : 0;
        },
        rateClass: function () {
          if (!this.stats.total) return '';
          if (this.stats.fail === 0) return 'ok';
          return this.successRate >= 80 ? '' : 'fail';
        },
        elapsedText: function () {
          return (this.elapsed / 1000).toFixed(1) + 's';
        },
        statusDot: function () {
          if (this.running) return 'live';
          if (!this.stats.total) return '';
          return this.halted ? 'halt' : 'done';
        },
        statusText: function () {
          if (this.running) return '压测进行中…';
          if (!this.stats.total) return '待开始';
          return this.halted ? '已手动停止' : '压测已完成';
        },
        corsCmdChrome: function () {
          return '"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe" --disable-web-security --user-data-dir="C:\\chrome-cors-test"';
        },
        corsCmdEdge: function () {
          return '"C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe" --disable-web-security --user-data-dir="C:\\edge-cors-test"';
        }
      },

      mounted: function () {
        var self = this;
        setInterval(function () { self.pump(); }, 120);
      },

      methods: {
        fmt: function (v, d) {
          if (v === null || v === undefined || isNaN(v)) return '0';
          return Number(v).toFixed(d === undefined ? 0 : d);
        },

        copy: function (text) {
          var self = this;
          if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(function () {
              self.flash('已复制');
            }, function () { self.flash('复制失败，请手动选中'); });
          } else {
            self.flash('请手动选中复制');
          }
        },

        flash: function (m) {
          var el = document.createElement('div');
          el.textContent = m;
          el.style.cssText = 'position:fixed;left:50%;bottom:32px;transform:translateX(-50%);background:#1f2328;color:#fff;' +
            'padding:8px 16px;border-radius:8px;font-size:13px;z-index:99;opacity:.95';
          document.body.appendChild(el);
          setTimeout(function () { el.remove(); }, 1400);
        },

        applyPreset: function (name) {
          if (name === 'limit') { this.concurrency = 6; this.mode = 'duration'; this.duration = 3; }
          if (name === 'baseline') { this.concurrency = 1; this.mode = 'count'; this.totalCount = 10; }
          if (name === 'burst') { this.concurrency = 50; this.mode = 'duration'; this.duration = 5; }
        },

        reset: function () {
          RAW.reset();
          this.halted = false;
          this.elapsed = 0;
          this.pump();
        },

        start: function () {
          if (this.running) return;
          RAW.reset();
          this.halted = false;
          this.running = true;
          RAW.startTs = performance.now();
          this.pump();

          var self = this;
          var n = Math.max(1, Math.min(200, Math.floor(this.concurrency) || 1));
          var workers = [];
          for (var i = 0; i < n; i++) workers.push(this.worker());
          Promise.all(workers).then(function () {
            self.running = false;
            self.elapsed = performance.now() - RAW.startTs;
            self.pump();
          });
        },

        stop: function () {
          if (!this.running) return;
          this.halted = true;
          this.running = false;
        },

        worker: function () {
          var self = this;
          function loop() {
            if (!self.running) return Promise.resolve();
            if (RAW.exceeded(self)) { self.running = false; return Promise.resolve(); }
            if (self.mode === 'count') {
              if (RAW.issued >= self.totalCount) { self.running = false; return Promise.resolve(); }
              RAW.issued++;
            }
            return self.fire().then(loop);
          }
          return loop();
        },

        fire: function () {
          var self = this;
          var opts = { method: this.method, headers: {} };
          if (this.hasBody) {
            if (this.contentType) opts.headers['Content-Type'] = this.contentType;
            opts.body = this.body;
          }

          var ctrl = ('AbortController' in window) ? new AbortController() : null;
          var timer = null;
          if (ctrl) {
            opts.signal = ctrl.signal;
            timer = setTimeout(function () { ctrl.abort(); }, Math.max(200, this.timeout || 8000));
          }

          var t0 = performance.now();
          return fetch(this.url, opts).then(function (res) {
            return res.text().then(function (text) {
              return { ok: res.ok, status: res.status, rt: performance.now() - t0, msg: res.ok ? '' : extractMsg(text) };
            });
          }).catch(function (e) {
            var msg = (e && e.name === 'AbortError') ? '请求超时（' + self.timeout + 'ms 内未返回）'
              : '网络错误：无法连接（服务未启动，或被浏览器 CORS 拦截）';
            return { ok: false, status: 0, rt: performance.now() - t0, msg: msg };
          }).then(function (r) {
            if (timer) clearTimeout(timer);
            RAW.record(r);
            return r;
          });
        },

        pump: function () {
          if (!this.stats.total && !this.running) {
            this.elapsed = this.stats.total ? this.elapsed : 0;
          }
          if (this.running) this.elapsed = performance.now() - RAW.startTs;

          this.stats = { total: RAW.total, ok: RAW.ok, fail: RAW.fail };
          this.rt = RAW.percentiles();

          var total = RAW.total || 1;
          var codes = Object.keys(RAW.codes).map(function (k) { return { code: k, count: RAW.codes[k] }; })
            .sort(function (a, b) { return b.count - a.count; });
          this.codeRows = codes.map(function (c) {
            var n = Number(c.code);
            var label = n === 0 ? 'network' : String(n);
            var cls = n === 0 ? 'warn' : (n >= 200 && n < 300 ? 'ok' : 'bad');
            var barCls = n === 0 ? 'gray' : (n >= 200 && n < 300 ? '' : 'red');
            return { code: c.code, label: label, count: c.count, cls: cls, barCls: barCls, pct: (c.count / total) * 100 };
          });

          var msgs = Object.keys(RAW.msgs).map(function (k) { return { msg: k, count: RAW.msgs[k] }; })
            .sort(function (a, b) { return b.count - a.count; }).slice(0, 6);
          var failTotal = RAW.fail || 1;
          this.msgRows = msgs.map(function (m) {
            return { msg: m.msg, count: m.count, pct: (m.count / failTotal) * 100 };
          });

          this.buildChart();
        },

        buildChart: function () {
          var PAD_L = 44, PAD_R = 20, TOP = 52, BASE = 152;
          var W = 640;
          var usable = W - PAD_L - PAD_R;

          var tl = RAW.timeline;
          var from = Math.max(0, tl.length - 60);
          var slice = tl.slice(from, tl.length);
          if (!slice.length) { this.chartBars = []; this.maxPerSec = 0; return; }

          var maxV = 1;
          for (var i = 0; i < slice.length; i++) {
            var t = (slice[i].ok || 0) + (slice[i].fail || 0);
            if (t > maxV) maxV = t;
          }
          this.maxPerSec = maxV;

          var n = slice.length;
          var slot = usable / n;
          var barW = Math.min(40, Math.max(2, slot - (slot > 12 ? 3 : 1)));
          var scale = (BASE - TOP) / maxV;
          var out = [];

          for (var j = 0; j < n; j++) {
            var b = slice[j];
            var okV = b.ok || 0, failV = b.fail || 0;
            var hOk = okV * scale;
            var hFail = failV * scale;
            var x = PAD_L + j * slot + (slot - barW) / 2;
            var yOk = BASE - hOk;
            out.push({
              x: x,
              w: barW,
              yOk: yOk,
              hOk: hOk,
              yFail: yOk - hFail,
              hFail: hFail,
              label: (j === 0 || (from + j) % 5 === 0) ? String(from + j) : ''
            });
          }
          this.chartBars = out;
        }
      }
    });

    app.mount('#app');
  }

  function extractMsg(text) {
    if (!text) return '(空响应体)';
    try {
      var o = JSON.parse(text);
      var m = o.message || o.msg || o.error || o.detail;
      if (m) return String(m).slice(0, 120);
      return JSON.stringify(o).slice(0, 120);
    } catch (e) {
      return String(text).replace(/\s+/g, ' ').slice(0, 120);
    }
  }

  var RAW = {
    startTs: 0,
    total: 0, ok: 0, fail: 0, issued: 0,
    rts: [],
    codes: {},
    msgs: {},
    timeline: [],

    reset: function () {
      this.startTs = 0;
      this.total = this.ok = this.fail = this.issued = 0;
      this.rts = [];
      this.codes = {};
      this.msgs = {};
      this.timeline = [];
    },

    exceeded: function (vm) {
      if (vm.mode === 'duration') {
        return (performance.now() - this.startTs) >= vm.duration * 1000;
      }
      return this.issued >= vm.totalCount;
    },

    record: function (r) {
      this.total++;
      this.rts.push(r.rt);
      if (r.ok) this.ok++; else this.fail++;

      var ck = String(r.status);
      this.codes[ck] = (this.codes[ck] || 0) + 1;
      if (!r.ok && r.msg) this.msgs[r.msg] = (this.msgs[r.msg] || 0) + 1;

      var sec = Math.floor((performance.now() - this.startTs) / 1000);
      while (this.timeline.length <= sec) this.timeline.push({ ok: 0, fail: 0 });
      if (r.ok) this.timeline[sec].ok++; else this.timeline[sec].fail++;
    },

    percentiles: function () {
      var a = this.rts;
      if (!a.length) return { avg: 0, p50: 0, p95: 0, p99: 0, max: 0 };
      if (a.length > 20000) {
        var step = Math.ceil(a.length / 20000);
        var samp = [];
        for (var k = 0; k < a.length; k += step) samp.push(a[k]);
        a = samp;
      }
      var s = a.slice().sort(function (x, y) { return x - y; });
      var sum = 0;
      for (var i = 0; i < a.length; i++) sum += a[i];
      function pick(p) {
        var idx = Math.min(s.length - 1, Math.max(0, Math.ceil((p / 100) * s.length) - 1));
        return s[idx];
      }
      return { avg: sum / s.length, p50: pick(50), p95: pick(95), p99: pick(99), max: s[s.length - 1] };
    }
  };

  loadVue(0);
})();
