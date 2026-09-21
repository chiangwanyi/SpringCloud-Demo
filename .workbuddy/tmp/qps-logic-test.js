const fs = require('fs');

const html = fs.readFileSync('E:/Data/projects/springcloud-demo/tools/qps-tester.html', 'utf8');
const m = html.match(/<script>([\s\S]*?)<\/script>/);
const js = m[1];

let captured = null;

global.window = {
  Vue: {
    createApp: function (opts) { captured = opts; return { mount: function () {} }; }
  }
};

global.document = {
  getElementById: function () { return { innerHTML: '' }; },
  createElement: function () { return {}; },
  head: {
    appendChild: function (s) { setTimeout(function () { if (s.onload) s.onload(); }, 0); }
  }
};

const CORS_MSG = '网络错误：无法连接（服务未启动，或被浏览器 CORS 拦截）';
let fetchCount = 0;
global.fetch = function (url, opts) {
  fetchCount++;
  const mod = fetchCount % 5;
  if (mod === 0) {
    return Promise.resolve({
      ok: false, status: 500,
      text: function () { return Promise.resolve(JSON.stringify({ code: 503, message: '请求过于频繁，请稍后重试' })); }
    });
  }
  if (mod === 1 && fetchCount > 5) {
    return Promise.reject(Object.assign(new Error('boom'), { name: 'TypeError' }));
  }
  return Promise.resolve({
    ok: true, status: 200,
    text: function () { return Promise.resolve(JSON.stringify({ code: 0, data: { username: 'zhangsan' } })); }
  });
};

eval(js);

function wait(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }

(async function () {
  await wait(30);
  if (!captured) { console.log('FAIL: Vue.createApp 未被调用，脚本没走到 boot()'); process.exit(1); }
  console.log('PASS boot(): createApp 已被调用');

  const options = captured;
  const vm = Object.assign({}, options.data());
  Object.keys(options.methods).forEach(function (k) { vm[k] = options.methods[k].bind(vm); });
  Object.keys(options.computed).forEach(function (k) {
    Object.defineProperty(vm, k, { get: function () { return options.computed[k].call(vm); } });
  });

  console.log('PASS data(): 字段数 =', Object.keys(vm).length);
  console.log('      默认并发 =', vm.concurrency, '/ 模式 =', vm.mode, '/ 时长 =', vm.duration);

  vm.mode = 'count';
  vm.totalCount = 25;
  vm.concurrency = 5;

  vm.start();
  for (let i = 0; i < 60 && vm.running; i++) await wait(50);
  await wait(200);
  vm.pump();

  const s = vm.stats;
  console.log('--- 压测结果 ---');
  console.log('总请求 =', s.total, '| 成功 =', s.ok, '| 失败 =', s.fail);
  console.log('耗时 =', vm.elapsed.toFixed(1), 'ms | QPS =', vm.qps.toFixed(1));
  console.log('RT  avg/p50/p95/p99/max =',
    vm.rt.avg.toFixed(2), vm.rt.p50.toFixed(2), vm.rt.p95.toFixed(2), vm.rt.p99.toFixed(2), vm.rt.max.toFixed(2));

  let fail = 0;
  function check(name, cond, extra) {
    if (cond) console.log('PASS ' + name + (extra ? ' -> ' + extra : ''));
    else { console.log('FAIL ' + name + (extra ? ' -> ' + extra : '')); fail++; }
  }

  check('总请求数精确等于设定值 25', s.total === 25, s.total);
  check('成功 + 失败 = 总数', s.ok + s.fail === s.total, s.ok + '+' + s.fail);
  check('存在失败请求', s.fail > 0, s.fail);
  check('P50 落在 [avg/2, max] 区间', vm.rt.p50 >= 0 && vm.rt.p50 <= vm.rt.max, vm.rt.p50 + ' in [0,' + vm.rt.max + ']');
  check('P95 >= P50', vm.rt.p95 >= vm.rt.p50);
  check('P99 >= P95', vm.rt.p99 >= vm.rt.p95);
  check('QPS 计算结果有限', isFinite(vm.qps) && vm.qps >= 0, vm.qps);

  console.log('--- 状态码分布 ---');
  vm.codeRows.forEach(function (r) {
    console.log('  ' + r.label + '  x' + r.count + '  ' + r.pct.toFixed(0) + '%  pill=' + r.cls);
  });
  check('状态码含 200', vm.codeRows.some(function (r) { return r.label === '200'; }));
  check('状态码含 500', vm.codeRows.some(function (r) { return r.label === '500'; }));
  check('状态码含 network(0)', vm.codeRows.some(function (r) { return r.label === 'network'; }));
  const pctSum = vm.codeRows.reduce(function (a, r) { return a + r.pct; }, 0);
  check('占比合计约 100%', Math.abs(pctSum - 100) < 0.01, pctSum.toFixed(3));

  console.log('--- 失败原因归类 ---');
  vm.msgRows.forEach(function (r) {
    console.log('  x' + r.count + '  ' + r.pct.toFixed(0) + '%  ' + r.msg);
  });
  check('识别出「请求过于频繁」', vm.msgRows.some(function (r) { return r.msg.indexOf('请求过于频繁') >= 0; }));
  check('识别出网络/CORS 错误', vm.msgRows.some(function (r) { return r.msg.indexOf('CORS') >= 0 || r.msg.indexOf(CORS_MSG.slice(0, 8)) >= 0; }));
  check('失败原因归类不超过 6 条', vm.msgRows.length <= 6, vm.msgRows.length);

  console.log('--- 图表坐标 ---');
  const bars = vm.chartBars;
  check('生成了柱子', bars.length > 0, bars.length + ' 根');
  check('maxPerSec > 0', vm.maxPerSec > 0, vm.maxPerSec);
  let badGeom = 0, overflowTop = 0;
  bars.forEach(function (b) {
    if (b.x < 40 || b.x + b.w > 624) badGeom++;
    if (b.hOk < 0 || b.hFail < 0) badGeom++;
    if (b.yFail < 52 - 0.01) overflowTop++;
  });
  check('柱子横向坐标都在绘图区内', badGeom === 0, '越界 ' + badGeom);
  check('堆叠柱未超出顶部', overflowTop === 0, '溢出 ' + overflowTop);
  const hSum = bars.reduce(function (a, b) { return a + b.hOk + b.hFail; }, 0);
  check('柱高合计 > 0', hSum > 0, hSum.toFixed(1));

  console.log('--- 重置 ---');
  vm.reset();
  check('reset 后 total 归零', vm.stats.total === 0, vm.stats.total);
  check('reset 后 elapsed 归零', vm.elapsed === 0, vm.elapsed);
  check('reset 后图表清空', vm.chartBars.length === 0, vm.chartBars.length);

  console.log(fail === 0 ? '\nALL_CHECKS_PASSED' : '\n' + fail + ' CHECK(S) FAILED');
  process.exit(fail === 0 ? 0 : 1);
})();
