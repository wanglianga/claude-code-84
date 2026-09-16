/* 口岸跨境电商仓包裹查验与退运申报服务 - 演示前端 */
const state = {
  token: localStorage.getItem('token') || null,
  user: JSON.parse(localStorage.getItem('user') || 'null'),
  tab: null,
};

/* ---------------- 基础工具 ---------------- */
async function api(path, opts = {}) {
  const headers = { 'Content-Type': 'application/json', ...(opts.headers || {}) };
  if (state.token) headers['Authorization'] = 'Bearer ' + state.token;
  const resp = await fetch(path, { ...opts, headers });
  if (resp.status === 401) { logout(); throw new Error('登录已过期'); }
  const text = await resp.text();
  let data; try { data = JSON.parse(text); } catch { data = text; }
  if (!resp.ok) throw new Error((data && data.error) || ('请求失败 ' + resp.status));
  return data;
}
const get = (p) => api(p);
const post = (p, body) => api(p, { method: 'POST', body: body ? JSON.stringify(body) : undefined });
const put = (p, body) => api(p, { method: 'PUT', body: JSON.stringify(body) });

function toast(msg, isErr) {
  const box = document.getElementById('toast');
  const el = document.createElement('div');
  el.className = 'toast-item' + (isErr ? ' err' : '');
  el.textContent = msg;
  box.appendChild(el);
  setTimeout(() => el.remove(), 3200);
}
async function run(fn, okMsg) {
  try { const r = await fn(); if (okMsg) toast(okMsg); return r; }
  catch (e) { toast(e.message, true); throw e; }
}
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const fmt = (t) => t ? String(t).replace('T', ' ').substring(0, 19) : '-';

/* ---------------- 文案映射 ---------------- */
const PKG_STATUS = {
  RECEIVED: ['已入仓', 'b-blue'], PRECHECK_PASSED: ['预检通过', 'b-green'],
  PRECHECK_FAILED: ['预检未通过', 'b-red'], DECLARED: ['已申报', 'b-blue'],
  CUSTOMS_REVIEW: ['海关审单中', 'b-blue'], INSPECTION: ['海关查验中', 'b-orange'],
  RELEASED: ['已放行', 'b-green'], DELIVERING: ['派送中', 'b-green'], DELIVERED: ['已签收', 'b-green'],
  SUPPLEMENT_REQUIRED: ['待补材料', 'b-orange'], HOLD: ['异常隔离', 'b-red'],
  DETAINED: ['海关扣留', 'b-red'], RETURNING: ['退运中', 'b-purple'],
  RETURNED: ['已退运', 'b-purple'], DESTROYED: ['已销毁', 'b-gray'],
};
const DEC_STATUS = {
  DRAFT: '草稿', SUBMITTED: '已提交海关', ACCEPTED: '海关审单通过', INSPECTION_REQUIRED: '待查验',
  INSPECTING: '查验中', INSPECTION_PASSED: '查验通过', RELEASED: '已放行',
  SUPPLEMENT_REQUIRED: '待补材料', FAILED_DETAINED: '已扣留', FAILED_RETURN: '退运处置',
  FAILED_DESTROY: '销毁处置', RETURNED: '已退运（终态）', DESTROYED: '已销毁（终态）', CLOSED: '已结案',
};
const TAX_BADGE = {
  PENDING: ['待缴', 'b-orange'], PAID: ['已缴', 'b-green'],
  REFUNDED: ['已退', 'b-gray'], VOID: ['已作废', 'b-red'],
};
const TAX_STATUS_NAME = { PENDING: '待缴', PAID: '已缴', REFUNDED: '已退', VOID: '已作废（不可缴纳）' };
const ROLE_NAME = { MERCHANT: '商家', WAREHOUSE: '仓库', BROKER: '报关员', CS: '客服', CUSTOMS: '海关', FINANCE: '财务', CONSUMER: '消费者', ADMIN: '管理员' };
const CHECK_NAME = { RESTRICTED_GOODS: '禁限售', PRICE_ANOMALY: '价格异常', ID_CARD_DUPLICATE: '身份证重复', TAX_RULE: '税费规则', RECIPIENT_FREQUENCY: '收件人频次', MERCHANT_RISK: '商家风险', BRAND_PRICE: '品牌成交价复核' };
const LEVEL_BADGE = { PASS: ['通过', 'b-green'], WARN: ['预警', 'b-orange'], FAIL: ['不通过', 'b-red'] };
const MAT_NAME = { INVOICE: '发票', CERT: '认证证明', PHOTO: '照片', EXPLANATION: '情况说明', OTHER: '其他', PURCHASE_PROOF: '采购凭证', PROMO_EXPLANATION: '促销说明', PAYMENT_RECORD: '付款记录' };
const REVIEW_STATUS = { AWAITING_EVIDENCE: ['待传三证', 'b-orange'], UNDER_REVIEW: ['待报关员复核', 'b-blue'], COMPLETED: ['复核完成', 'b-green'] };
const REVIEW_DECISION = { PASS: '继续申报（价格合理）', SUPPLEMENT_TAX: '补税（认定成交价）', MANUAL_INSPECTION: '转人工查验' };
const ACT_NAME = { OPEN_PHOTO: '开箱拍照', VERIFY_GOODS: '核对商品', SUPPLEMENT_DOC: '补充票据', SUBMIT_EXPLANATION: '提交说明' };
const REASON_NAME = { GOODS_MISMATCH: '商品与申报不符', MISSING_CERT: '缺少认证', PRICE_TOO_LOW: '价格明显偏低', RECIPIENT_INFO_ERROR: '收件人资料错误', PACKAGE_DAMAGED: '包裹破损', MERCHANT_RETURN_REQUEST: '商家要求退运', OTHER: '其他' };
const badge = (m, k) => { const v = m[k]; return v ? `<span class="badge ${v[1]}">${v[0]}</span>` : esc(k); };
const pkgBadge = (s) => badge(PKG_STATUS, s);

/* ---------------- 登录/登出 ---------------- */
async function doLogin() {
  const username = document.getElementById('login-username').value.trim();
  const password = document.getElementById('login-password').value;
  try {
    const data = await post('/api/auth/login', { username, password });
    state.token = data.token; state.user = data.user;
    localStorage.setItem('token', data.token);
    localStorage.setItem('user', JSON.stringify(data.user));
    renderShell();
  } catch (e) { document.getElementById('login-error').textContent = e.message; }
}
function logout() {
  state.token = null; state.user = null;
  localStorage.removeItem('token'); localStorage.removeItem('user');
  renderShell();
}
function renderShell() {
  const ub = document.getElementById('user-box');
  if (state.user) {
    ub.innerHTML = `<span>${esc(state.user.displayName)} · ${ROLE_NAME[state.user.role] || state.user.role}</span><button onclick="logout()">退出</button>`;
    document.getElementById('login-section').style.display = 'none';
    document.getElementById('app-section').style.display = '';
    renderTabs();
  } else {
    ub.innerHTML = '';
    document.getElementById('login-section').style.display = '';
    document.getElementById('app-section').style.display = 'none';
  }
}

/* ---------------- 公开查询 ---------------- */
const STAGES = ['已入仓', '申报准备中', '海关申报中', '海关查验中', '海关已放行', '国内派送中', '已签收'];
async function doTrack() {
  const wb = document.getElementById('track-input').value.trim();
  const box = document.getElementById('track-result');
  if (!wb) return;
  try {
    const d = await get('/api/public/track/' + encodeURIComponent(wb));
    const isEnd = ['包裹已退运', '包裹已按海关要求处置'].includes(d.stage);
    const steps = STAGES.map(s => `<div class="stage-step ${stageReached(d.stage, s) ? 'done' : ''}">${s}</div>`).join('');
    box.innerHTML = `<div class="section-title">${esc(d.waybillNo)} · ${esc(d.goodsName)} ×${d.quantity}</div>
      ${isEnd ? `<div class="badge b-purple" style="font-size:14px">${esc(d.stage)}</div>` : `<div class="stage-line">${steps}</div>`}
      <div class="badge b-blue" style="font-size:14px">${esc(d.stage)}</div>
      <span style="margin-left:10px;color:#667085">${esc(d.stageDesc)}</span>
      <div class="timeline">${(d.timeline || []).map(t => `<div class="t-item"><div class="t-time">${fmt(t.time)}</div><div>${esc(t.text)}</div></div>`).join('')}</div>`;
  } catch (e) { box.innerHTML = `<div class="error">${esc(e.message)}</div>`; }
}
function stageReached(cur, s) {
  if (cur === '已签收') return true;
  const order = ['已入仓', '申报准备中', '海关申报中', '海关查验中', '海关已放行', '国内派送中', '已签收'];
  const ci = order.indexOf(cur), si = order.indexOf(s);
  if (cur === '申报资料补充中' || cur === '海关处理中') return si <= 3;
  if (cur === '包裹退运中') return si <= 2;
  return ci >= si && si <= ci;
}

/* ---------------- 工作台标签页 ---------------- */
const TABS = [
  { id: 'packages', name: '📦 包裹', roles: ['MERCHANT', 'WAREHOUSE', 'BROKER', 'CS', 'CUSTOMS', 'FINANCE', 'ADMIN'] },
  { id: 'create', name: '➕ 入仓登记', roles: ['MERCHANT'] },
  { id: 'declarations', name: '📋 申报单', roles: ['MERCHANT', 'BROKER', 'CUSTOMS', 'FINANCE', 'ADMIN'] },
  { id: 'inspections', name: '🔍 海关查验', roles: ['WAREHOUSE', 'CUSTOMS', 'BROKER', 'ADMIN'] },
  { id: 'returns', name: '↩️ 退运/销毁', roles: ['MERCHANT', 'WAREHOUSE', 'CUSTOMS', 'CS', 'ADMIN'] },
  { id: 'price-review', name: '🧾 价格复核', roles: ['MERCHANT', 'BROKER', 'CUSTOMS', 'ADMIN'] },
  { id: 'finance', name: '💰 税费赔付', roles: ['FINANCE', 'ADMIN', 'CS', 'WAREHOUSE'] },
  { id: 'batches', name: '🗂 批次处理', roles: ['MERCHANT', 'WAREHOUSE', 'ADMIN'] },
  { id: 'urges', name: '🔔 催件', roles: ['CS', 'ADMIN'] },
  { id: 'merchants', name: '🛡 商家风控', roles: ['ADMIN', 'CUSTOMS'] },
  { id: 'consumer', name: '🧾 我的包裹', roles: ['CONSUMER'] },
];
function renderTabs() {
  const nav = document.getElementById('tabs');
  const tabs = TABS.filter(t => t.roles.includes(state.user.role));
  if (!state.tab || !tabs.find(t => t.id === state.tab)) state.tab = tabs[0].id;
  nav.innerHTML = tabs.map(t => `<button class="${t.id === state.tab ? 'active' : ''}" onclick="switchTab('${t.id}')">${t.name}</button>`).join('');
  renderTab();
}
function switchTab(id) { state.tab = id; renderTabs(); }
async function renderTab() {
  const c = document.getElementById('content');
  c.innerHTML = '<div class="hint">加载中...</div>';
  try {
    if (state.tab === 'packages') await viewPackages(c);
    else if (state.tab === 'create') viewCreate(c);
    else if (state.tab === 'declarations') await viewDeclarations(c);
    else if (state.tab === 'inspections') await viewInspections(c);
    else if (state.tab === 'returns') await viewReturns(c);
    else if (state.tab === 'price-review') await viewPriceReviews(c);
    else if (state.tab === 'finance') await viewFinance(c);
    else if (state.tab === 'batches') await viewBatches(c);
    else if (state.tab === 'urges') await viewUrges(c);
    else if (state.tab === 'merchants') await viewMerchants(c);
    else if (state.tab === 'consumer') await viewConsumer(c);
  } catch (e) { c.innerHTML = `<div class="error">${esc(e.message)}</div>`; }
}
const refresh = () => renderTab();

/* ---------------- 包裹列表 ---------------- */
async function viewPackages(c) {
  const list = await get('/api/packages');
  const role = state.user.role;
  const rows = list.map(p => `<tr>
    <td><a href="javascript:showArchive(${p.id})">${esc(p.waybillNo)}</a>${p.customsDelayed ? ' <span class="badge b-red">海关延迟</span>' : ''}</td>
    <td>${esc(p.goodsName)}<br><span class="t-time">${p.brand ? esc(p.brand) + ' · ' : ''}${esc(p.hsCode)} · ¥${p.declaredPrice} ×${p.quantity}${p.taxablePrice && Number(p.taxablePrice) !== Number(p.declaredPrice) ? ` · <span style="color:#b54708">计税价¥${p.taxablePrice}</span>` : ''}</span></td>
    <td>${esc(p.recipientName)}</td>
    <td>${esc(p.warehouseLocation || '-')}</td>
    <td>${p.batchNo ? esc(p.batchNo) : '-'}</td>
    <td>${pkgBadge(p.status)}${p.packageType === 'CONSOLIDATED' ? ' <span class="badge b-purple">合包</span>' : ''}${p.tradeMode === 'GENERAL' ? ' <span class="badge b-orange">一般贸易</span>' : ''}</td>
    <td>${parcelActions(p, role)}</td></tr>`).join('');
  c.innerHTML = `<div class="row" style="justify-content:space-between"><h3>包裹列表（${list.length}）</h3>
    <button class="gray sm" onclick="refresh()">刷新</button></div>
    <table><thead><tr><th>运单号</th><th>商品</th><th>收件人</th><th>仓位</th><th>批次</th><th>状态</th><th>操作</th></tr></thead>
    <tbody>${rows || '<tr><td colspan="7" class="hint">暂无包裹</td></tr>'}</tbody></table>`;
}
function parcelActions(p, role) {
  const b = [];
  const canPrecheck = ['MERCHANT', 'WAREHOUSE', 'BROKER', 'ADMIN'].includes(role)
    && ['RECEIVED', 'PRECHECK_FAILED', 'HOLD', 'PRECHECK_PASSED'].includes(p.status);
  if (canPrecheck) b.push(`<button class="sm" onclick="doPrecheck(${p.id})">预检</button>`);
  if (['MERCHANT', 'BROKER'].includes(role) && p.status === 'PRECHECK_PASSED')
    b.push(`<button class="sm green" onclick="doCreateDec(${p.id})">创建申报单</button>`);
  if (['MERCHANT', 'WAREHOUSE', 'BROKER'].includes(role))
    b.push(`<button class="sm gray" onclick="doUploadParcelMat(${p.id})">传材料</button>`);
  if (['MERCHANT', 'BROKER'].includes(role) && ['RECEIVED', 'PRECHECK_PASSED', 'PRECHECK_FAILED', 'SUPPLEMENT_REQUIRED'].includes(p.status) && p.tradeMode === 'BONDED')
    b.push(`<button class="sm orange" onclick="doConvert(${p.id})">转一般贸易</button>`);
  if (role === 'WAREHOUSE' && p.status === 'RELEASED')
    b.push(`<button class="sm green" onclick="doDispatch(${p.id})">派送</button>`);
  if (role === 'WAREHOUSE' && p.status === 'DELIVERING')
    b.push(`<button class="sm green" onclick="doDeliver(${p.id})">签收</button>`);
  if (['MERCHANT', 'CS', 'CUSTOMS'].includes(role) && ['RECEIVED', 'PRECHECK_PASSED', 'PRECHECK_FAILED', 'HOLD', 'DECLARED', 'CUSTOMS_REVIEW', 'SUPPLEMENT_REQUIRED', 'DETAINED'].includes(p.status))
    b.push(`<button class="sm red" onclick="doReturnReq(${p.id})">退运/销毁</button>`);
  b.push(`<button class="sm gray" onclick="showArchive(${p.id})">档案</button>`);
  return b.join('');
}
const doPrecheck = (id) => run(async () => {
  const rs = await post(`/api/packages/${id}/precheck`);
  showPrecheck(rs);
  refresh();
}, '预检完成');
const doCreateDec = (id) => run(async () => { await post(`/api/declarations?parcelId=${id}`); refresh(); }, '申报单已创建（六方协同）');
const doConvert = (id) => run(async () => { await post(`/api/packages/${id}/convert-trade-mode`); refresh(); }, '已转一般贸易');
const doDispatch = (id) => run(async () => { await post(`/api/packages/${id}/dispatch`); refresh(); }, '已安排派送');
const doDeliver = (id) => run(async () => { await post(`/api/packages/${id}/deliver`); refresh(); }, '已签收');
function doUploadParcelMat(id) {
  const type = prompt('材料类型 INVOICE发票/CERT认证/PHOTO照片/EXPLANATION说明/OTHER其他：', 'INVOICE');
  if (!type) return;
  const name = prompt('文件名：', '发票-' + Date.now() + '.pdf');
  if (!name) return;
  run(async () => { await post(`/api/packages/${id}/materials`, { materialType: type.trim().toUpperCase(), fileName: name, fileUrl: 'https://files.example.com/' + encodeURIComponent(name) }); }, '材料已上传');
}
function doReturnReq(id) {
  const type = confirm('确定申请退运？（取消则申请销毁）') ? 'RETURN' : 'DESTROY';
  const reason = prompt(type === 'RETURN' ? '退运原因：' : '销毁原因：', type === 'RETURN' ? '商家要求退运' : '海关要求销毁');
  if (!reason) return;
  run(async () => { await post(`/api/returns?parcelId=${id}`, { type, reason }); refresh(); }, '处置申请已提交，待海关核准');
}

/* ---------------- 预检结果 ---------------- */
function showPrecheck(rs) {
  openModal('申报前检查结果', `<table><thead><tr><th>检查项</th><th>结论</th><th>说明</th></tr></thead><tbody>` +
    rs.map(r => { const lv = LEVEL_BADGE[r.level]; return `<tr><td>${CHECK_NAME[r.checkType] || r.checkType}</td><td><span class="badge ${lv[1]}">${lv[0]}</span></td><td>${esc(r.message)}</td></tr>`; }).join('') +
    `</tbody></table>`);
}

/* ---------------- 包裹档案 ---------------- */
async function showArchive(id) {
  try {
    const a = await get(`/api/packages/${id}/archive`);
    const p = a.parcel;
    const events = (a.events || []).map(e => `<div class="t-item">
      <div class="t-time">${fmt(e.createdAt)}</div>
      <div><span class="t-node">${esc(e.node)}</span> ${e.fromStatus && e.fromStatus !== e.toStatus ? `<span class="t-time">${esc(e.fromStatus)} → </span><b>${esc(e.toStatus)}</b>` : ''}</div>
      <div class="t-remark">${esc(e.remark || '')}</div>
      <div class="t-actor">责任人：${esc(e.actor || '-')}（${ROLE_NAME[e.actorRole] || e.actorRole || '-'}）</div></div>`).join('');
    const mats = (a.materials || []).map(m => `<tr><td>${MAT_NAME[m.materialType] || m.materialType}</td><td>${esc(m.fileName)}</td><td>${esc(m.uploadedBy || '-')}</td><td>${fmt(m.createdAt)}</td></tr>`).join('');
    const taxes = (a.taxes || []).map(t => `<tr><td>${esc(t.taxType)}</td><td>¥${t.amount}</td><td>${badge(TAX_BADGE, t.status)}</td><td>${esc(t.paidBy || '-')}</td><td>${esc(t.voidReason || '-')}</td></tr>`).join('');
    const returns = (a.returnOrders || []).map(r => `<tr><td>${esc(r.returnNo)}</td><td>${r.type === 'RETURN' ? '退运' : '销毁'}</td><td>${esc(r.reason)}</td>
      <td>${badge({ REQUESTED: ['待核准', 'b-orange'], APPROVED: ['已核准', 'b-blue'], REJECTED: ['已驳回', 'b-red'], EXECUTING: ['执行中', 'b-blue'], COMPLETED: ['已完成', 'b-green'] }, r.status)}</td>
      <td>${esc(r.approvedBy || '-')}</td><td>${fmt(r.completedAt)}</td></tr>`).join('');
    const priceReviews = (a.priceReviews || []).map(r => `<tr><td>${esc(r.reviewNo)}</td><td>${esc(r.brand)}</td><td>¥${r.declaredPrice} / ¥${r.referenceAvgPrice ?? '-'}</td>
      <td>${badge(REVIEW_STATUS, r.status)}</td><td>${r.decision ? REVIEW_DECISION[r.decision] : '-'}${r.revisedUnitPrice ? '（认定¥' + r.revisedUnitPrice + '）' : ''}</td><td>${esc(r.decidedBy || '-')}</td></tr>`).join('');
    const brandRule = a.brandPriceRule;
    const comps = (a.compensations || []).map(x => `<tr><td>¥${x.amount}</td><td>${esc(x.reason)}</td><td>${esc(x.responsibleParty)}</td><td>${badge({ PENDING: ['待审批', 'b-orange'], APPROVED: ['已审批', 'b-blue'], PAID: ['已支付', 'b-green'], REJECTED: ['已驳回', 'b-red'] }, x.status)}</td></tr>`).join('');
    const decs = (a.declarations || []).map(dv => {
      const d = dv.declaration;
      const parts = (dv.participants || []).map(x => `<span class="badge b-blue">${ROLE_NAME[x.role]}·${esc(x.displayName || '')}</span>`).join(' ');
      const insp = (dv.inspections || []).map(iv => `<div>查验指令 ${esc(iv.order.orderNo)}：${esc(iv.order.instruction)}（${esc(iv.order.status)}${iv.order.verdict ? ' / ' + esc(iv.order.verdict) : ''}${iv.order.failReason ? ' / ' + (REASON_NAME[iv.order.failReason] || iv.order.failReason) : ''}）
        ${(iv.actions || []).map(ac => `<br>· ${ACT_NAME[ac.actionType] || ac.actionType}：${esc(ac.notes || '')}${ac.photoUrl ? ' 📷' : ''}`).join('')}</div>`).join('');
      return `<div class="section-title">申报单 ${esc(d.declarationNo)}（${DEC_STATUS[d.status] || d.status}，税费 ¥${d.taxAmount ?? '-'}）</div>
        <div>${parts}</div>${insp}`;
    }).join('');
    openModal(`包裹档案 · ${esc(p.waybillNo)}`, `
      <div class="kv">
        <div><b>商品：</b>${esc(p.goodsName)}（${esc(p.hsCode)}）${p.brand ? '　<b>品牌：</b>' + esc(p.brand) : ''}</div>
        <div><b>申报价：</b>¥${p.declaredPrice} ×${p.quantity}${p.taxablePrice && Number(p.taxablePrice) !== Number(p.declaredPrice) ? `　<b style="color:#b54708">计税价：</b>¥${p.taxablePrice}（价格复核补税认定）` : ''}</div>
        <div><b>收件人：</b>${esc(p.recipientName)} ${esc(p.recipientIdCard)}</div>
        <div><b>商家：</b>${esc(a.merchant ? a.merchant.name : '-')}</div>
        <div><b>批次/仓位：</b>${esc(p.batchNo || '-')} / ${esc(p.warehouseLocation || '-')}</div>
        <div><b>物流渠道：</b>${esc(p.logisticsChannel)}</div>
        <div><b>状态：</b>${pkgBadge(p.status)}</div>
        <div><b>贸易模式：</b>${p.tradeMode === 'BONDED' ? '保税仓' : '一般贸易'}</div>
      </div>
      ${(a.orders || []).length ? `<div class="section-title">合包子订单</div>` + a.orders.map(o => `<span class="badge b-purple">${esc(o.platform)} / ${esc(o.orderNo)}</span>`).join(' ') : ''}
      ${decs}
      <div class="section-title">材料（${(a.materials || []).length}）</div>
      <table><tbody>${mats || '<tr><td class="hint">无</td></tr>'}</tbody></table>
      <div class="section-title">税费清算（${(a.taxes || []).length}）</div>
      <table><thead><tr><th>税种</th><th>金额</th><th>状态</th><th>缴纳/退款人</th><th>作废原因</th></tr></thead>
      <tbody>${taxes || '<tr><td colspan="5" class="hint">无</td></tr>'}</tbody></table>
      <div class="section-title">退运/销毁处置（${(a.returnOrders || []).length}）</div>
      <table><thead><tr><th>处置单号</th><th>类型</th><th>原因</th><th>状态</th><th>核準人</th><th>完成时间</th></tr></thead>
      <tbody>${returns || '<tr><td colspan="6" class="hint">无</td></tr>'}</tbody></table>
      ${(a.priceReviews || []).length || brandRule ? `<div class="section-title">申报价格异常复核（${(a.priceReviews || []).length}）${brandRule ? `　<span class="t-time">同品牌历史均价 ¥${brandRule.avgDealPrice}（${brandRule.reviewFlag ? '重点复核' : '正常'}，${brandRule.dealCount} 笔成交）</span>` : ''}</div>
      <table><thead><tr><th>复核单号</th><th>品牌</th><th>申报/历史均价</th><th>状态</th><th>结论</th><th>复核人</th></tr></thead>
      <tbody>${priceReviews || '<tr><td colspan="6" class="hint">无</td></tr>'}</tbody></table>` : ''}
      <div class="section-title">赔付（${(a.compensations || []).length}）</div>
      <table><tbody>${comps || '<tr><td class="hint">无</td></tr>'}</tbody></table>
      <div class="section-title">全链路事件（时效/责任留痕，含退运销毁与税费清算结论）</div>
      <div class="timeline">${events}</div>`);
  } catch (e) { toast(e.message, true); }
}
function openModal(title, html) {
  document.getElementById('modal-title').innerHTML = title;
  document.getElementById('modal-content').innerHTML = html;
  document.getElementById('modal').style.display = '';
}
function closeModal() { document.getElementById('modal').style.display = 'none'; }

/* ---------------- 入仓登记 / 合包 ---------------- */
function viewCreate(c) {
  c.innerHTML = `<h3>包裹入仓登记</h3>
    <div class="form-grid">
      <div><label>运单号*</label><input id="f-waybill" value="WB${Date.now()}"></div>
      <div><label>商品编码(HS)*</label><input id="f-hs" value="0402109000"></div>
      <div><label>商品名称*</label><input id="f-goods" value="婴幼儿配方奶粉"></div>
      <div><label>品牌（价格复核比对用）</label><input id="f-brand" value="A2至初"></div>
      <div><label>申报价格*</label><input id="f-price" type="number" value="218"></div>
      <div><label>数量*</label><input id="f-qty" type="number" value="1"></div>
      <div><label>收件人*</label><input id="f-recip" value="测试收件人"></div>
      <div><label>身份证*</label><input id="f-idcard" value="330106199201011234"></div>
      <div><label>电话*</label><input id="f-phone" value="13800000000"></div>
      <div><label>批次号</label><input id="f-batch" placeholder="如 BATCH002"></div>
      <div><label>仓位</label><input id="f-loc" value="A-01-01"></div>
      <div><label>物流渠道*</label><input id="f-channel" value="顺丰国际"></div>
      <div><label>贸易模式</label><select id="f-trade"><option value="BONDED">保税仓</option><option value="GENERAL">一般贸易</option></select></div>
    </div>
    <div class="row"><button onclick="doCreateParcel(false)">入仓登记</button>
    <button class="orange" onclick="doCreateParcel(true)">多平台合包登记</button>
    <span class="hint">合包需填写至少两个平台订单</span></div>
    <div id="create-msg"></div>`;
}
async function doCreateParcel(consolidate) {
  const v = (id) => document.getElementById(id).value.trim();
  const body = {
    waybillNo: v('f-waybill'), hsCode: v('f-hs'), brand: v('f-brand') || null, goodsName: v('f-goods'),
    declaredPrice: parseFloat(v('f-price')), quantity: parseInt(v('f-qty')),
    recipientName: v('f-recip'), recipientIdCard: v('f-idcard'), recipientPhone: v('f-phone'),
    batchNo: v('f-batch') || null, warehouseLocation: v('f-loc'), logisticsChannel: v('f-channel'),
    tradeMode: document.getElementById('f-trade').value,
  };
  if (consolidate) {
    const raw = prompt('平台订单（每行一个，格式：平台,订单号）：', '天猫国际,TM' + Date.now() + '\n京东国际,JD' + (Date.now() + 1));
    if (!raw) return;
    body.orders = raw.split('\n').filter(Boolean).map(line => {
      const [platform, orderNo] = line.split(',');
      return { platform: (platform || '').trim(), orderNo: (orderNo || '').trim() };
    });
  }
  await run(async () => {
    await post(consolidate ? '/api/packages/consolidate' : '/api/packages', body);
    state.tab = 'packages'; renderTabs();
  }, consolidate ? '合包包裹已入仓' : '包裹已入仓');
}

/* ---------------- 申报单 ---------------- */
async function viewDeclarations(c) {
  const list = await get('/api/declarations');
  const role = state.user.role;
  const rows = await Promise.all(list.map(async d => {
    const acts = [];
    if (['MERCHANT', 'BROKER'].includes(role) && ['DRAFT', 'SUPPLEMENT_REQUIRED'].includes(d.status))
      acts.push(`<button class="sm green" onclick="doSubmitDec(${d.id})">提交海关</button>`);
    if (['MERCHANT', 'WAREHOUSE', 'BROKER'].includes(role))
      acts.push(`<button class="sm gray" onclick="doUploadDecMat(${d.id})">传材料</button>`);
    if (role === 'MERCHANT' && d.status === 'SUPPLEMENT_REQUIRED')
      acts.push(`<button class="sm red" onclick="doRefuse(${d.id})">拒绝补材料</button>`);
    acts.push(`<button class="sm gray" onclick="showDec(${d.id})">详情</button>`);
    return `<tr><td>${esc(d.declarationNo)}</td><td>${esc(d.status ? (DEC_STATUS[d.status] || d.status) : '')}</td>
      <td>¥${d.taxAmount ?? '-'}</td><td>${esc(d.failReason ? (REASON_NAME[d.failReason] || d.failReason) : '-')}</td>
      <td>${fmt(d.submittedAt)}</td><td>${acts.join('')}</td></tr>`;
  }));
  c.innerHTML = `<div class="row" style="justify-content:space-between"><h3>申报单（六方协同：商家/仓库/报关员/客服/海关/财务）</h3>
    <button class="gray sm" onclick="refresh()">刷新</button></div>
    <table><thead><tr><th>申报单号</th><th>状态</th><th>税费</th><th>不通过原因</th><th>提交时间</th><th>操作</th></tr></thead>
    <tbody>${rows.join('') || '<tr><td colspan="6" class="hint">暂无申报单</td></tr>'}</tbody></table>`;
}
const doSubmitDec = (id) => run(async () => { await post(`/api/declarations/${id}/submit`); refresh(); }, '已提交海关，等待回执');
const doRefuse = (id) => run(async () => {
  if (!confirm('确认拒绝补充材料？包裹将被扣留并计商家违规一次。')) return;
  await post(`/api/declarations/${id}/refuse-supplement`); refresh();
}, '已拒绝补材料，包裹扣留');
function doUploadDecMat(id) {
  const type = prompt('材料类型 INVOICE/CERT/PHOTO/EXPLANATION/OTHER：', 'CERT');
  if (!type) return;
  const name = prompt('文件名：', '材料-' + Date.now() + '.pdf');
  if (!name) return;
  run(async () => { await post(`/api/declarations/${id}/materials`, { materialType: type.trim().toUpperCase(), fileName: name, fileUrl: 'https://files.example.com/' + encodeURIComponent(name) }); }, '材料已上传');
}
async function showDec(id) {
  try {
    const d = await get(`/api/declarations/${id}`);
    const parts = (d.participants || []).map(x => `<tr><td>${ROLE_NAME[x.role]}</td><td>${esc(x.displayName || '')}</td></tr>`).join('');
    const mats = (d.materials || []).map(m => `<tr><td>${MAT_NAME[m.materialType]}</td><td>${esc(m.fileName)}</td><td>${esc(m.uploadedBy || '-')}</td><td>${fmt(m.createdAt)}</td></tr>`).join('');
    const taxes = (d.taxes || []).map(t => `<tr><td>${esc(t.taxType)}</td><td>¥${t.amount}</td><td>${badge(TAX_BADGE, t.status)}</td><td>${esc(t.voidReason || '-')}</td></tr>`).join('');
    openModal('申报单详情', `<div class="kv"><div><b>单号：</b>${esc(d.declaration.declarationNo)}</div>
      <div><b>状态：</b>${DEC_STATUS[d.declaration.status] || d.declaration.status}</div>
      <div><b>补材料要求：</b>${esc(d.declaration.supplementNote || '-')}</div></div>
      <div class="section-title">协同方</div><table><tbody>${parts}</tbody></table>
      <div class="section-title">材料</div><table><tbody>${mats || '<tr><td class="hint">无</td></tr>'}</tbody></table>
      <div class="section-title">税费清算</div><table><thead><tr><th>税种</th><th>金额</th><th>状态</th><th>作废原因</th></tr></thead><tbody>${taxes || '<tr><td colspan="4" class="hint">无</td></tr>'}</tbody></table>`);
  } catch (e) { toast(e.message, true); }
}

/* ---------------- 海关查验 ---------------- */
async function viewInspections(c) {
  const list = await get('/api/inspections/orders');
  const role = state.user.role;
  const rows = list.map(o => {
    const acts = [];
    if (role === 'WAREHOUSE' && o.status !== 'COMPLETED')
      acts.push(`<button class="sm" onclick="doInspAction(${o.id},'OPEN_PHOTO')">开箱拍照</button>
        <button class="sm" onclick="doInspAction(${o.id},'VERIFY_GOODS')">核对商品</button>
        <button class="sm" onclick="doInspAction(${o.id},'SUPPLEMENT_DOC')">补充票据</button>
        <button class="sm" onclick="doInspAction(${o.id},'SUBMIT_EXPLANATION')">提交说明</button>`);
    if (['CUSTOMS', 'BROKER'].includes(role) && o.status !== 'COMPLETED')
      acts.push(`<button class="sm green" onclick="doInspResult(${o.id},true)">查验通过</button>
        <button class="sm red" onclick="doInspResult(${o.id},false)">查验不通过</button>`);
    acts.push(`<button class="sm gray" onclick="showInspActions(${o.id})">记录</button>`);
    return `<tr><td>${esc(o.orderNo)}</td><td>${esc(o.instruction)}</td>
      <td>${badge({ PENDING: ['待执行', 'b-orange'], IN_PROGRESS: ['执行中', 'b-blue'], COMPLETED: ['已完成', 'b-green'] }, o.status)}</td>
      <td>${o.verdict ? esc(o.verdict) : '-'}${o.failReason ? '<br>' + (REASON_NAME[o.failReason] || o.failReason) : ''}</td>
      <td>${acts.join('')}</td></tr>`;
  }).join('');
  const customsExtra = ['CUSTOMS', 'ADMIN'].includes(role) ? `
    <div class="row" style="margin-bottom:10px">
      <button class="orange sm" onclick="doProcessTasks()">立即处理海关回执</button>
      <button class="red sm" onclick="doSimDelay()">模拟海关系统延迟</button>
    </div>` : '';
  c.innerHTML = `<div class="row" style="justify-content:space-between"><h3>海关查验指令</h3>
    <button class="gray sm" onclick="refresh()">刷新</button></div>${customsExtra}
    <table><thead><tr><th>指令号</th><th>指令内容</th><th>状态</th><th>结论</th><th>操作</th></tr></thead>
    <tbody>${rows || '<tr><td colspan="5" class="hint">暂无查验指令</td></tr>'}</tbody></table>`;
}
function doInspAction(id, type) {
  let photoUrl = null;
  const notes = prompt(`【${ACT_NAME[type]}】备注：`, '');
  if (notes === null) return;
  if (type === 'OPEN_PHOTO') {
    photoUrl = prompt('照片地址：', 'https://img.example.com/open-' + Date.now() + '.jpg');
    if (!photoUrl) return;
  }
  run(async () => { await post(`/api/inspections/orders/${id}/actions`, { actionType: type, notes, photoUrl }); refresh(); }, '查验动作已记录');
}
function doInspResult(id, pass) {
  if (pass) {
    if (!confirm('确认查验通过？')) return;
    return run(async () => { await post(`/api/inspections/orders/${id}/result`, { pass: true, note: '查验相符' }); refresh(); }, '查验通过');
  }
  const reason = prompt('不通过原因：GOODS_MISMATCH商品与申报不符 / MISSING_CERT缺少认证 / PRICE_TOO_LOW价格明显偏低 / RECIPIENT_INFO_ERROR收件人资料错误 / PACKAGE_DAMAGED包裹破损 / MERCHANT_RETURN_REQUEST商家要求退运 / OTHER其他', 'GOODS_MISMATCH');
  if (!reason) return;
  const action = prompt('处置方式：SUPPLEMENT补材料 / DETAIN扣留 / RETURN退运 / DESTROY销毁', 'SUPPLEMENT');
  if (!action) return;
  run(async () => { await post(`/api/inspections/orders/${id}/result`, { pass: false, failReason: reason.trim().toUpperCase(), failAction: action.trim().toUpperCase(), note: '海关查验不通过' }); refresh(); }, '查验结论已提交');
}
async function showInspActions(id) {
  try {
    const list = await get(`/api/inspections/orders/${id}/actions`);
    openModal('查验执行记录', `<table><thead><tr><th>时间</th><th>动作</th><th>备注</th><th>操作人</th></tr></thead><tbody>` +
      (list.map(a => `<tr><td>${fmt(a.createdAt)}</td><td>${ACT_NAME[a.actionType]}</td><td>${esc(a.notes || '')}${a.photoUrl ? ' 📷' : ''}</td><td>${esc(a.operator || '')}</td></tr>`).join('') || '<tr><td colspan="4" class="hint">暂无记录</td></tr>') +
      `</tbody></table>`);
  } catch (e) { toast(e.message, true); }
}
const doProcessTasks = () => run(async () => { const r = await post('/api/customs/process-tasks'); toast('已处理 ' + r.processed + ' 个海关回执'); refresh(); });
function doSimDelay() {
  const s = prompt('模拟海关系统延迟秒数：', '120');
  if (!s) return;
  run(async () => { const r = await post('/api/customs/simulate-delay', { seconds: parseInt(s) }); toast('已注入延迟：' + r.delayed + ' 个任务延后 ' + s + ' 秒'); });
}

/* ---------------- 退运/销毁 ---------------- */
async function viewReturns(c) {
  const list = await get('/api/returns');
  const role = state.user.role;
  const rows = list.map(r => {
    const acts = [];
    if (role === 'CUSTOMS' && r.status === 'REQUESTED')
      acts.push(`<button class="sm green" onclick="doReturnAct(${r.id},'approve')">核准</button>
        <button class="sm red" onclick="doReturnAct(${r.id},'reject')">驳回</button>`);
    if (role === 'WAREHOUSE' && r.status === 'APPROVED')
      acts.push(`<button class="sm orange" onclick="doReturnAct(${r.id},'execute')">执行${r.type === 'RETURN' ? '退运' : '销毁'}</button>`);
    return `<tr><td>${esc(r.returnNo)}</td><td>${r.type === 'RETURN' ? '退运' : '销毁'}</td><td>${esc(r.reason)}</td>
      <td>${badge({ REQUESTED: ['待核准', 'b-orange'], APPROVED: ['已核准', 'b-blue'], REJECTED: ['已驳回', 'b-red'], EXECUTING: ['执行中', 'b-blue'], COMPLETED: ['已完成', 'b-green'] }, r.status)}</td>
      <td>${esc(r.requestedBy || '-')}</td><td>${acts.join('') || '-'}</td></tr>`;
  }).join('');
  c.innerHTML = `<div class="row" style="justify-content:space-between"><h3>退运 / 销毁处置单</h3>
    <button class="gray sm" onclick="refresh()">刷新</button></div>
    <table><thead><tr><th>单号</th><th>类型</th><th>原因</th><th>状态</th><th>申请人</th><th>操作</th></tr></thead>
    <tbody>${rows || '<tr><td colspan="6" class="hint">暂无处置单</td></tr>'}</tbody></table>`;
}
const doReturnAct = (id, act) => run(async () => { await post(`/api/returns/${id}/${act}`); refresh(); }, '操作成功');

/* ---------------- 申报价格异常复核 ---------------- */
async function viewPriceReviews(c) {
  const role = state.user.role;
  const [list, rules] = await Promise.all([get('/api/price-reviews'), get('/api/brand-price-rules')]);
  let watches = [];
  if (['ADMIN', 'CUSTOMS', 'BROKER'].includes(role)) {
    try { watches = await get('/api/brand-price-rules/watches'); } catch { watches = []; }
  }
  const rows = list.map(r => {
    const acts = [];
    if (['MERCHANT', 'BROKER', 'CS', 'ADMIN'].includes(role) && r.status !== 'COMPLETED')
      acts.push(`<button class="sm" onclick="doReviewEvidence(${r.id})">传三证</button>`);
    if (['BROKER', 'CUSTOMS', 'ADMIN'].includes(role) && r.status === 'UNDER_REVIEW')
      acts.push(`<button class="sm green" onclick="doReviewDecision(${r.id},'PASS')">继续申报</button>
        <button class="sm orange" onclick="doReviewDecision(${r.id},'SUPPLEMENT_TAX')">补税</button>
        <button class="sm red" onclick="doReviewDecision(${r.id},'MANUAL_INSPECTION')">转人工查验</button>`);
    acts.push(`<button class="sm gray" onclick="showReview(${r.id})">详情</button>`);
    const dec = r.decision ? `<br><span class="t-time">结论：${REVIEW_DECISION[r.decision] || r.decision}${r.revisedUnitPrice ? '（认定¥' + r.revisedUnitPrice + '）' : ''}</span>` : '';
    return `<tr><td>${esc(r.reviewNo)}</td><td>${esc(r.brand)}<br><span class="t-time">${esc(r.hsCode)}</span></td>
      <td>¥${r.declaredPrice}<br><span class="t-time">历史均价 ¥${r.referenceAvgPrice ?? '-'}</span></td>
      <td>${badge(REVIEW_STATUS, r.status)}${dec}</td>
      <td>${esc(r.decidedBy || '-')}</td><td>${acts.join('')}</td></tr>`;
  }).join('');
  const ruleRows = rules.map(r => `<tr><td>${esc(r.brand)}</td><td>${esc(r.hsCode)}</td><td>¥${r.avgDealPrice}</td><td>${r.dealCount}</td>
    <td>${r.reviewFlag ? '<span class="badge b-red">重点复核</span>' : '<span class="badge b-green">正常</span>'}</td>
    <td>${esc(r.lastReviewDecision ? (REVIEW_DECISION[r.lastReviewDecision] || r.lastReviewDecision) : '-')}</td></tr>`).join('');
  c.innerHTML = `<div class="row" style="justify-content:space-between"><h3>申报价格异常复核</h3>
      <button class="gray sm" onclick="refresh()">刷新</button></div>
    <div class="hint">系统发现某品牌申报价远低于同品牌同类历史成交价时自动立案：商家上传 <b>采购凭证 / 促销说明 / 付款记录</b> 三证，报关员复核为“继续申报 / 补税 / 转人工查验”，结论影响商家风险并沉淀为品牌规则。</div>
    <table><thead><tr><th>复核单号</th><th>品牌/HS</th><th>申报价/历史均价</th><th>状态与结论</th><th>复核人</th><th>操作</th></tr></thead>
    <tbody>${rows || '<tr><td colspan="6" class="hint">暂无复核单</td></tr>'}</tbody></table>
    <div class="section-title" style="margin-top:18px">同品牌同类成交价规则（结论沉淀，后续申报提前提示）</div>
    <table><thead><tr><th>品牌</th><th>HS编码</th><th>历史成交均价</th><th>成交笔数</th><th>预审强度</th><th>最近复核结论</th></tr></thead>
    <tbody>${ruleRows || '<tr><td colspan="6" class="hint">暂无</td></tr>'}</tbody></table>`;
  if (['ADMIN', 'CUSTOMS'].includes(role)) {
    const active = watches.filter(w => w.stricterReview);
    const wRows = active.map(w => `<tr><td>${esc(w.brand)}</td><td>${esc(w.hsCode)}</td>
      <td>${esc(w.lastDecision ? (REVIEW_DECISION[w.lastDecision] || w.lastDecision) : '-')}</td>
      <td><button class="sm red" onclick="doReleaseWatch(${w.id})">解除重点复核</button></td></tr>`).join('');
    c.innerHTML += `<div class="section-title" style="margin-top:18px">商家价格重点复核名单（${active.length} 条未解除）
      <span class="t-time">单票 PASS 不会解除；仅此处显式解除，或把商家调离 HIGH 风险时自动解除</span></div>
      <table><thead><tr><th>品牌</th><th>HS编码</th><th>来源结论</th><th>操作</th></tr></thead>
      <tbody>${wRows || '<tr><td colspan="4" class="hint">暂无未解除记录</td></tr>'}</tbody></table>`;
  }
}
const doReleaseWatch = (id) => run(async () => {
  if (!confirm('解除该商家该品牌品类的重点复核？解除后同品类恢复普通 60% 阈值预审。')) return;
  await post(`/api/brand-price-rules/watches/${id}/release`); refresh();
}, '已解除重点复核，恢复普通预审');
function doReviewEvidence(id) {
  const type = prompt('凭证类型：PURCHASE_PROOF采购凭证 / PROMO_EXPLANATION促销说明 / PAYMENT_RECORD付款记录', 'PURCHASE_PROOF');
  if (!type) return;
  const name = prompt('文件名：', type + '-' + Date.now() + '.pdf');
  if (!name) return;
  run(async () => { await post(`/api/price-reviews/${id}/evidence`, { materialType: type.trim().toUpperCase(), fileName: name, fileUrl: 'https://files.example.com/' + encodeURIComponent(name) }); refresh(); }, '凭证已上传（三证齐备后自动转报关员复核）');
}
function doReviewDecision(id, decision) {
  let price = null, note = '';
  if (!confirm('确认结论：' + REVIEW_DECISION[decision] + '？')) return;
  if (decision === 'SUPPLEMENT_TAX') {
    const p = prompt('认定计税单价（留空则按历史成交均价）：', '');
    if (p === null) return; price = p ? parseFloat(p) : null;
    note = prompt('复核备注：', '申报价偏低，按认定成交价补税') || '申报价偏低，按认定成交价补税';
  } else if (decision === 'MANUAL_INSPECTION') {
    note = prompt('复核备注：', '凭证不足，转人工查验') || '凭证不足，转人工查验';
  } else {
    note = prompt('复核备注：', '凭证齐备、促销价合理，继续申报') || '凭证齐备、价格合理';
  }
  run(async () => { await post(`/api/price-reviews/${id}/decision`, { decision, revisedUnitPrice: price, note }); refresh(); }, '复核结论已出具');
}
async function showReview(id) {
  try {
    const d = await get(`/api/price-reviews/${id}`);
    const r = d.review;
    const ev = (d.evidence || []).map(m => `<tr><td>${MAT_NAME[m.materialType] || m.materialType}</td><td>${esc(m.fileName)}</td><td>${esc(m.uploadedBy || '-')}</td><td>${fmt(m.createdAt)}</td></tr>`).join('');
    openModal('价格复核 · ' + r.reviewNo, `<div class="kv">
      <div><b>品牌/品类：</b>${esc(r.brand)} / ${esc(r.hsCode)}</div>
      <div><b>申报单价：</b>¥${r.declaredPrice}　<b>历史均价：</b>¥${r.referenceAvgPrice ?? '-'}</div>
      <div><b>状态：</b>${badge(REVIEW_STATUS, r.status)}　<b>结论：</b>${r.decision ? REVIEW_DECISION[r.decision] : '-'}</div>
      <div><b>认定计税单价：</b>${r.revisedUnitPrice ? '¥' + r.revisedUnitPrice : '-'}　<b>复核人：</b>${esc(r.decidedBy || '-')}</div>
      <div><b>备注：</b>${esc(r.decisionNote || '-')}</div></div>
      <div class="section-title">三证材料</div>
      <table><thead><tr><th>类型</th><th>文件</th><th>上传人</th><th>时间</th></tr></thead><tbody>${ev || '<tr><td colspan="4" class="hint">暂无</td></tr>'}</tbody></table>`);
  } catch (e) { toast(e.message, true); }
}

/* ---------------- 税费赔付 ---------------- */
async function viewFinance(c) {
  const role = state.user.role;
  const taxes = await get('/api/finance/taxes');
  const comps = await get('/api/finance/compensations');
  const taxRows = taxes.map(t => `<tr><td>${esc(t.taxType)}</td><td>¥${t.amount}</td>
    <td>${badge(TAX_BADGE, t.status)}${t.voidReason ? `<br><span class="t-time">${esc(t.voidReason)}</span>` : ''}</td>
    <td>${esc(t.paidBy || '-')}</td>
    <td>${role === 'FINANCE' && t.status === 'PENDING' ? `<button class="sm green" onclick="doPayTax(${t.id})">缴纳</button>` : ''}</td></tr>`).join('');
  const compRows = comps.map(x => `<tr><td>¥${x.amount}</td><td>${esc(x.reason)}</td><td>${esc(x.responsibleParty)}</td>
    <td>${badge({ PENDING: ['待审批', 'b-orange'], APPROVED: ['已审批', 'b-blue'], PAID: ['已支付', 'b-green'], REJECTED: ['已驳回', 'b-red'] }, x.status)}</td>
    <td>${role === 'FINANCE' && x.status === 'PENDING' ? `<button class="sm" onclick="doCompAct(${x.id},'approve')">审批</button>` : ''}
        ${role === 'FINANCE' && x.status === 'APPROVED' ? `<button class="sm green" onclick="doCompAct(${x.id},'pay')">支付</button>` : ''}</td></tr>`).join('');
  const createComp = ['CS', 'WAREHOUSE', 'FINANCE'].includes(role) ?
    `<button class="sm orange" onclick="doCreateComp()">登记赔付</button>` : '';
  c.innerHTML = `<div class="row" style="justify-content:space-between"><h3>税费记录</h3>
    <span><button class="gray sm" onclick="refresh()">刷新</button></span></div>
    <table><thead><tr><th>税种</th><th>金额</th><th>状态</th><th>缴纳人</th><th>操作</th></tr></thead>
    <tbody>${taxRows || '<tr><td colspan="5" class="hint">暂无</td></tr>'}</tbody></table>
    <div class="row" style="justify-content:space-between;margin-top:18px"><h3>赔付记录</h3><span>${createComp}</span></div>
    <table><thead><tr><th>金额</th><th>原因</th><th>责任方</th><th>状态</th><th>操作</th></tr></thead>
    <tbody>${compRows || '<tr><td colspan="5" class="hint">暂无</td></tr>'}</tbody></table>`;
}
const doPayTax = (id) => run(async () => { await post(`/api/finance/taxes/${id}/pay`); refresh(); }, '税费已缴纳');
const doCompAct = (id, act) => run(async () => { await post(`/api/finance/compensations/${id}/${act}`); refresh(); }, '操作成功');
function doCreateComp() {
  const parcelId = prompt('包裹ID：'); if (!parcelId) return;
  const amount = prompt('赔付金额：', '50'); if (!amount) return;
  const reason = prompt('赔付原因：', '包裹破损'); if (!reason) return;
  const party = prompt('责任方 MERCHANT商家/WAREHOUSE仓库/LOGISTICS物流/PLATFORM平台：', 'LOGISTICS'); if (!party) return;
  run(async () => { await post('/api/finance/compensations', { parcelId: parseInt(parcelId), amount: parseFloat(amount), reason, responsibleParty: party.trim().toUpperCase() }); refresh(); }, '赔付已登记');
}

/* ---------------- 批次处理 ---------------- */
async function viewBatches(c) {
  const list = await get('/api/batches');
  const role = state.user.role;
  const rows = list.map(b => `<tr><td>${esc(b.batchNo)}</td>
    <td>${badge({ OPEN: ['待处理', 'b-orange'], PROCESSING: ['处理中', 'b-blue'], PROCESSED: ['已处理', 'b-green'] }, b.status)}</td>
    <td>${b.totalCount}</td><td><span class="badge b-green">${b.normalCount}</span></td>
    <td><span class="badge b-red">${b.abnormalCount}</span></td>
    <td>${['MERCHANT', 'WAREHOUSE'].includes(role) && b.status !== 'PROCESSED' ? `<button class="sm green" onclick="doBatchProcess(${b.id})">批量处理</button>` : ''}
        <button class="sm gray" onclick="showBatch(${b.id})">详情</button></td></tr>`).join('');
  const createBtn = role === 'MERCHANT' ? `<div class="row" style="margin-bottom:10px">
    <input id="new-batch-no" placeholder="新批次号，如 BATCH002" value="BATCH${Date.now()}">
    <button class="sm" onclick="doCreateBatch()">创建批次</button>
    <span class="hint">创建包裹时填写批次号即可入批</span></div>` : '';
  c.innerHTML = `<h3>批次处理（异常包裹与正常放行队列分离）</h3>${createBtn}
    <table><thead><tr><th>批次号</th><th>状态</th><th>总数</th><th>正常队列</th><th>异常隔离</th><th>操作</th></tr></thead>
    <tbody>${rows || '<tr><td colspan="6" class="hint">暂无批次</td></tr>'}</tbody></table>`;
}
const doCreateBatch = () => run(async () => {
  await post('/api/batches', { batchNo: document.getElementById('new-batch-no').value.trim() }); refresh();
}, '批次已创建');
const doBatchProcess = (id) => run(async () => {
  const r = await post(`/api/batches/${id}/process`);
  openModal('批次处理结果', `<div class="section-title">正常放行队列（${r.normalQueue.length}）</div>
    ${r.normalQueue.map(i => `<span class="badge b-green">${esc(i.waybillNo)}</span>`).join(' ') || '无'}
    <div class="section-title">异常隔离队列（${r.abnormalQueue.length}）</div>
    ${r.abnormalQueue.map(i => `<div><span class="badge b-red">${esc(i.waybillNo)}</span> ${(i.reasons || []).map(esc).join('；')}</div>`).join('') || '无'}`);
  refresh();
}, '批次处理完成');
async function showBatch(id) {
  try {
    const d = await get(`/api/batches/${id}`);
    openModal('批次详情 · ' + esc(d.batch.batchNo), `<table><thead><tr><th>运单号</th><th>商品</th><th>状态</th></tr></thead><tbody>` +
      (d.parcels || []).map(p => `<tr><td>${esc(p.waybillNo)}</td><td>${esc(p.goodsName)}</td><td>${pkgBadge(p.status)}</td></tr>`).join('') +
      `</tbody></table>`);
  } catch (e) { toast(e.message, true); }
}

/* ---------------- 催件 ---------------- */
async function viewUrges(c) {
  const list = await get('/api/urges');
  const rows = list.map(u => `<tr><td>${fmt(u.createdAt)}</td><td>${esc(u.consumerName || '-')}</td>
    <td>${esc(u.message || '')}</td>
    <td>${badge({ OPEN: ['待处理', 'b-orange'], HANDLED: ['已处理', 'b-green'] }, u.status)}</td>
    <td>${esc(u.handleNote || '-')}</td>
    <td>${state.user.role === 'CS' && u.status === 'OPEN' ? `<button class="sm green" onclick="doHandleUrge(${u.id})">处理</button>` : ''}</td></tr>`).join('');
  c.innerHTML = `<div class="row" style="justify-content:space-between"><h3>消费者催件</h3>
    <button class="gray sm" onclick="refresh()">刷新</button></div>
    <table><thead><tr><th>时间</th><th>消费者</th><th>内容</th><th>状态</th><th>处理备注</th><th>操作</th></tr></thead>
    <tbody>${rows || '<tr><td colspan="6" class="hint">暂无催件</td></tr>'}</tbody></table>`;
}
function doHandleUrge(id) {
  const note = prompt('处理备注：', '已联系物流加急处理');
  if (!note) return;
  run(async () => { await post(`/api/urges/${id}/handle`, { note }); refresh(); }, '催件已处理');
}

/* ---------------- 商家风控 ---------------- */
async function viewMerchants(c) {
  const list = await get('/api/merchants');
  const rows = list.map(m => `<tr><td>${esc(m.code)}</td><td>${esc(m.name)}</td>
    <td>${badge({ LOW: ['低风险', 'b-green'], MEDIUM: ['中风险', 'b-orange'], HIGH: ['高风险', 'b-red'] }, m.riskLevel)}</td>
    <td>${m.inspectionRatio}%</td><td>${m.batchLimit}</td>
    <td>${m.requireAdvanceDocs ? '<span class="badge b-orange">是</span>' : '否'}</td>
    <td>${m.violationCount}</td>
    <td><button class="sm" onclick="doEditRisk(${m.id},'${m.riskLevel}',${m.inspectionRatio},${m.batchLimit},${m.requireAdvanceDocs})">调整风控</button></td></tr>`).join('');
  c.innerHTML = `<h3>商家风控（高风险：提高抽检比例 / 限制批量申报 / 要求提前传票）</h3>
    <table><thead><tr><th>编码</th><th>商家</th><th>风险等级</th><th>抽检比例</th><th>批次限额</th><th>提前传票</th><th>违规次数</th><th>操作</th></tr></thead>
    <tbody>${rows}</tbody></table>`;
}
function doEditRisk(id, level, ratio, limit, docs) {
  const nl = prompt('风险等级 LOW/MEDIUM/HIGH：', level); if (!nl) return;
  const nr = prompt('抽检比例(%)：', ratio); if (nr === null) return;
  const nb = prompt('批次申报限额：', limit); if (nb === null) return;
  const nd = confirm('是否要求提前上传完整票据？（确定=是）');
  run(async () => { await put(`/api/merchants/${id}/risk`, { riskLevel: nl.trim().toUpperCase(), inspectionRatio: parseInt(nr), batchLimit: parseInt(nb), requireAdvanceDocs: nd }); refresh(); }, '风控参数已更新');
}

/* ---------------- 消费者 ---------------- */
async function viewConsumer(c) {
  const list = await get('/api/consumer/packages');
  const rows = list.map(p => `<tr><td>${esc(p.waybillNo)}</td><td>${esc(p.goodsName)}</td>
    <td>${pkgBadge(p.status)}</td>
    <td><button class="sm gray" onclick="doTrackWaybill('${esc(p.waybillNo)}')">查进度</button>
        <button class="sm orange" onclick="doUrge('${esc(p.waybillNo)}')">催件</button></td></tr>`).join('');
  c.innerHTML = `<h3>我的包裹（仅展示必要进度）</h3>
    <table><thead><tr><th>运单号</th><th>商品</th><th>状态</th><th>操作</th></tr></thead>
    <tbody>${rows || '<tr><td colspan="4" class="hint">暂无包裹</td></tr>'}</tbody></table>
    <div id="consumer-track"></div>`;
}
async function doTrackWaybill(wb) {
  const box = document.getElementById('consumer-track');
  try {
    const d = await get('/api/public/track/' + encodeURIComponent(wb));
    box.innerHTML = `<div class="section-title">${esc(d.waybillNo)} 进度：${esc(d.stage)}</div>
      <div class="timeline">${(d.timeline || []).map(t => `<div class="t-item"><div class="t-time">${fmt(t.time)}</div><div>${esc(t.text)}</div></div>`).join('')}</div>`;
  } catch (e) { toast(e.message, true); }
}
function doUrge(wb) {
  const msg = prompt('催件内容：', '请尽快处理我的包裹');
  if (!msg) return;
  run(async () => { await post(`/api/consumer/packages/${wb}/urge`, { message: msg }); }, '催件已提交，客服将尽快处理');
}

/* ---------------- 启动 ---------------- */
renderShell();
