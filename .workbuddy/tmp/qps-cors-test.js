/**
 * qps-tester.html 回归测试（无浏览器）—— 本次新增「走网关 + 自动登录 + 带令牌」后的验证。
 *
 * 做法：从 HTML 抽出 <script> 真实代码，mock 掉 window / document / fetch，
 * 捕获 Vue 的 options 对象，把 methods 绑到 vm、computed 用 defineProperty 挂上，
 * 然后跑真实流程断言。
 */
const fs = require('fs');

const HTML = 'E:/Data/projects/springcloud-demo/tools/qps-tester.html';
const html = fs.readFileSync(HTML, 'utf8');
const m = html.match(/<script>([\s\S]*?)<\/script>/);
if (!m) { console.error('未找到 <script> 块'); process.exit(1); }
const js = m[1];

let captured = null;
let mounted = false;

global.window = {};
window.Vue = {
  createApp: function (options) {
    captured = options;
    return { mount: function () { mounted = true; } };
  }
};

const appEl = { innerHTML: '' };
global.document = {
  getElementById: function () { return appEl; },
  createElement: function () {
    const el = {};
    Object.defineProperty(el, 'src', {
      set: function () { setTimeout(function () { if (el.onload) el.onload(); }, 0); }
    });
    return el;
  },
  head: { appendChild: function () {} },
  body: { appendChild: function () {} }
};

let fetchCalls = [];
let fetchHandler = null;

global.fetch = function (url, opts) {
  fetchCalls.push({ url: url, opts: opts || {} });
  if (fetchHandler) return Promise.resolve(fetchHandler(url, opts));
  return Promise.resolve({ ok: true, status: 200, text: function () { return Promise.resolve('{}'); } });
};

eval(js);

function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }

function buildVm() {
  const vm = Object.assign({}, captured.data());
  Object.keys(captured.methods).forEach(function (k) { vm[k] = captured.methods[k].bind(vm); });
  Object.keys(captured.computed).forEach(function (k) {
    Object.defineProperty(vm, k, { get: function () { return captured.computed[k].call(vm); } });
  });
  return vm;
}

let pass = 0, fail = 0;
function ok(name, cond, extra) {
  if (cond) { pass++; console.log('  PASS  ' + name); }
  else { fail++; console.log('  FAIL  ' + name + (extra !== undefined ? '   -> ' + JSON.stringify(extra) : '')); }
}

const okResp = function (obj) {
  return { ok: true, status: 200, text: function () { return Promise.resolve(JSON.stringify(obj)); } };
};

(async function run() {
  await sleep(40);

  console.log('--- 1. 引导与默认值 ---');
  ok('Vue createApp 被调用', captured !== null);
  ok('app 已 mount', mounted);
  const vm = buildVm();
  ok('默认目标地址指向网关 :8080', vm.url === 'http://127.0.0.1:8080/api/order', vm.url);
  ok('默认登录账号 admin', vm.loginUser === 'admin', vm.loginUser);
  ok('初始令牌为空', vm.token === '', vm.token);
  ok('computed hasBody 可用', vm.hasBody === true, vm.hasBody);

  console.log('--- 2. login() 成功路径 ---');
  fetchHandler = function () { return okResp({ token: 'tok_abcdef123456', expiresIn: 1800 }); };
  fetchCalls = [];
  await vm.login();
  ok('登录请求打到 /api/auth/login', fetchCalls.length === 1 && fetchCalls[0].url.endsWith('/api/auth/login'), fetchCalls.map(c => c.url));
  ok('请求体带上了用户名密码', /"username":"admin"/.test(fetchCalls[0].opts.body) && /"password":"admin123"/.test(fetchCalls[0].opts.body), fetchCalls[0].opts.body);
  ok('令牌已写入 vm.token', vm.token === 'tok_abcdef123456', vm.token);
  ok('tokenMsg 提示已获取', /已获取令牌/.test(vm.tokenMsg), vm.tokenMsg);
  ok('有效期换算为分钟', /30 分钟/.test(vm.tokenMsg), vm.tokenMsg);
  ok('logining 复位为 false', vm.logining === false);

  console.log('--- 3. fire() 带上 Authorization 头 ---');
  fetchHandler = function () { return okResp({ ok: true }); };
  fetchCalls = [];
  await vm.fire();
  const h = fetchCalls[0].opts.headers || {};
  ok('业务请求带 Authorization', h['Authorization'] === 'Bearer tok_abcdef123456', h);
  ok('Content-Type 同时存在', h['Content-Type'] === 'application/json', h);

  console.log('--- 4. 令牌为空时不带 Authorization ---');
  vm.token = '';
  fetchCalls = [];
  await vm.fire();
  ok('无令牌时不带 Authorization', (fetchCalls[0].opts.headers || {})['Authorization'] === undefined, fetchCalls[0].opts.headers);

  console.log('--- 5. 直连 8083 时不自动登录 ---');
  vm.token = '';
  vm.url = 'http://127.0.0.1:8083/api/order';
  vm.concurrency = 1; vm.mode = 'count'; vm.totalCount = 1;
  fetchCalls = [];
  fetchHandler = function () { return okResp({ ok: true }); };
  await vm.start();
  await sleep(60);
  ok('未调用登录接口', fetchCalls.every(c => !c.url.endsWith('/api/auth/login')), fetchCalls.map(c => c.url));

  console.log('--- 6. 走网关且令牌为空时自动登录 ---');
  vm.url = 'http://127.0.0.1:8080/api/order';
  vm.token = '';
  vm.concurrency = 1; vm.mode = 'count'; vm.totalCount = 1;
  fetchCalls = [];
  fetchHandler = function (url) {
    if (String(url).endsWith('/api/auth/login')) return okResp({ token: 'auto_tok_9999' });
    return okResp({ ok: true });
  };
  await vm.start();
  await sleep(80);
  const loginCalls = fetchCalls.filter(c => c.url.endsWith('/api/auth/login'));
  ok('自动触发了登录', loginCalls.length === 1, fetchCalls.map(c => c.url));
  ok('自动登录后令牌已写入', vm.token === 'auto_tok_9999', vm.token);
  const biz = fetchCalls.filter(c => !c.url.endsWith('/api/auth/login'));
  ok('业务请求带上了自动获取的令牌', biz.length > 0 && biz.every(c => c.opts.headers['Authorization'] === 'Bearer auto_tok_9999'),
    biz.map(c => c.opts.headers));

  console.log('--- 7. login() 失败路径 ---');
  vm.token = '';
  fetchHandler = function () { return { ok: false, status: 401, text: function () { return Promise.resolve('{"detail":"用户名或密码错误"}'); } }; };
  await vm.login();
  ok('失败时 tokenMsg 报告错误', /^登录失败/.test(vm.tokenMsg), vm.tokenMsg);
  ok('失败时 token 保持为空', vm.token === '', vm.token);

  console.log('--- 8. 响应缺少 token 字段 ---');
  fetchHandler = function () { return okResp({ code: 200, data: { nickname: '管理员' } }); };
  await vm.login();
  ok('缺 token 时提示未找到字段', /未找到 token 字段/.test(vm.tokenMsg), vm.tokenMsg);

  console.log('');
  console.log('结果: ' + pass + ' 通过, ' + fail + ' 失败');
  process.exit(fail ? 1 : 0);
})();
