/**
 * Mall-X 内置 mock 服务(开发用)。
 * 与后端接口契约逐字段对齐:mall-mgr(:10020)/ mall-alipay(:10030) 经网关 10010 暴露的全部接口。
 * 有状态:注册/审核/下单/支付/发货/收货/退款/提现全链路可走通;支付回调按契约做幂等,
 * 支付成功后模拟 MQ 异步驱动业务订单 0→1(延时 1.2s)。
 * 签名校验与 SignUtil 一致,便于提前暴露前端签名实现问题;VITE_USE_MOCK=false 时本模块不加载。
 */
import type { Plugin, Connect } from 'vite'
import type { ServerResponse } from 'http'
import md5 from 'js-md5'

const SECRET = process.env.VITE_SIGN_SECRET || 'mallx-pay-secret-2026'
const SIGN_EXEMPT = ['/mch/login', '/user/login', '/admin/login', '/pay/callback/alipay']

/* ============================ 内存状态 ============================ */
let seq = 1000
const nextId = () => ++seq

function ts(d = new Date()) {
  const p = (n: number, l = 2) => String(n).padStart(l, '0')
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}${p(d.getMilliseconds(), 3)}`
}
const rand = (n: number) =>
  Array.from({ length: n }, () => Math.floor(Math.random() * 10)).join('')
const genNo = (prefix: string) => `${prefix}${ts()}${rand(5)}${String(seq % 10000).padStart(4, '0')}`
const dayStr = (d: Date) => {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
const iso = (d: Date) => d.toISOString().slice(0, 19)
const fmt = (d: Date) => {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
const round2 = (n: number) => Math.round(n * 100) / 100

interface Mch {
  id: number
  shopNum: string
  shopName: string
  shopPhone: string
  shopAddress: string
  username: string
  password: string
  shopState: 0 | 1 | 2
  auditRemark: string | null
  createdAt: string
}
interface User {
  id: number
  userNum: string
  username: string
  password: string
  nickname: string
}
interface AdminUser {
  id: number
  username: string
  password: string
  realName: string
  role: 'ADMIN' | 'OPS'
  state: 0 | 1
}
interface Goods {
  id: number
  goodsNum: string
  goodsName: string
  shopNum: string
  categoryId: number
  price: number
  stock: number
  sales: number
  goodsState: 0 | 1
  detail: string
  picUrl?: string
  createdAt: string
}
interface PayOrder {
  id: number
  payOrderId: string
  mchOrderNo: string
  shopNum: string
  shopName: string
  appId: string
  wayCode: string
  money: number
  mchFee: number
  subject: string
  userNum: string
  orderState: 0 | 1 | 2 | 3 | 4
  channelOrderNo: string | null
  payTime: string | null
  createdAt: string
  updatedAt: string
}
interface OrderItem {
  id: number
  orderNum: string
  goodsNum: string
  goodsName: string
  goodsPrice: number
  goodsCount: number
  totalMoney: number
}
interface BizOrder {
  orderNum: string
  userNum: string
  shopNum: string
  totalMoney: number
  orderState: 0 | 1 | 2 | 3 | 4
  payOrderId: string | null
  receiverName: string
  receiverPhone: string
  receiverAddress: string
  logisticsCompany: string | null
  logisticsNo: string | null
  createdAt: string
  payTime: string | null
  deliverTime: string | null
  receiveTime: string | null
}
interface RefundOrder {
  id: number
  refundOrderId: string
  payOrderId: string
  orderNum?: string
  shopNum: string
  userNum?: string
  refundMoney: number
  refundReason: string
  evidence?: string | null
  refundState: 0 | 1 | 2 | 3
  channelRefundNo: string | null
  auditRemark: string | null
  auditTime?: string | null
  refundTime: string | null
  createdAt: string
}
interface Withdraw {
  id: number
  withdrawNo: string
  shopNum: string
  money: number
  state: 0 | 1 | 2
  remark: string | null
  finishTime: string | null
  createdAt: string
}

const db = {
  admins: [
    { id: nextId(), username: 'admin', password: '123456', realName: '平台超级管理员', role: 'ADMIN' as const, state: 1 as const },
  ] as AdminUser[],
  channels: [
    { wayCode: 'WECHAT', channelName: '微信支付', switchStatus: 1, baseRate: 0.006, gatewayUrl: 'https://api.mch.weixin.qq.com', notifyKey: 'mallx-channel-key', appId: null },
    { wayCode: 'ALIPAY', channelName: '支付宝', switchStatus: 1, baseRate: 0.006, gatewayUrl: 'https://openapi-sandbox.dl.alipaydev.com/gateway.do', notifyKey: 'mallx-channel-key', appId: null },
    { wayCode: 'UNIONPAY', channelName: '银联支付', switchStatus: 1, baseRate: 0.005, gatewayUrl: 'https://gateway.95516.com', notifyKey: 'mallx-channel-key', appId: null },
  ],
  mchRates: [{ shopNum: 'SH417826', wayCode: 'ALIPAY', mchRate: 0.004 }],
  mchs: [
    { id: nextId(), shopNum: 'SH417826', shopName: '星选数码旗舰店', shopPhone: '13800000001', shopAddress: '广东省广州市天河区科技园1栋', username: 'shop1', password: '123456', shopState: 1 as const, auditRemark: null, createdAt: iso(new Date(Date.now() - 9 * 864e5)) },
    { id: nextId(), shopNum: 'SH692051', shopName: '鲜果之家', shopPhone: '13800000002', shopAddress: '广东省深圳市南山区果园路8号', username: 'shop2', password: '123456', shopState: 1 as const, auditRemark: null, createdAt: iso(new Date(Date.now() - 8 * 864e5)) },
    { id: nextId(), shopNum: 'SH340887', shopName: '新申请的奶茶铺', shopPhone: '13800000003', shopAddress: '湖南省长沙市岳麓区大学城2号', username: 'shop3', password: '123456', shopState: 0 as const, auditRemark: null, createdAt: iso(new Date(Date.now() - 1 * 864e5)) },
  ] as Mch[],
  users: [{ id: nextId(), userNum: 'U1000000001', username: 'user1', password: '123456', nickname: '小明' }] as User[],
  categories: [
    { id: 1, categoryName: '数码', parentId: 0, sort: 1 },
    { id: 2, categoryName: '生鲜', parentId: 0, sort: 2 },
    { id: 3, categoryName: '服饰', parentId: 0, sort: 3 },
  ],
  goods: [
    { id: nextId(), goodsNum: 'G728415', goodsName: '无线蓝牙耳机 Pro', shopNum: 'SH417826', categoryId: 1, price: 199.0, stock: 100, sales: 12, goodsState: 1 as const, detail: '高保真音质,40小时续航', createdAt: iso(new Date(Date.now() - 7 * 864e5)) },
    { id: nextId(), goodsNum: 'G391647', goodsName: '智能机械键盘 87键', shopNum: 'SH417826', categoryId: 1, price: 299.0, stock: 50, sales: 8, goodsState: 1 as const, detail: '全键无冲,青轴', createdAt: iso(new Date(Date.now() - 7 * 864e5)) },
    { id: nextId(), goodsNum: 'G562039', goodsName: '赣南脐橙 5斤装', shopNum: 'SH692051', categoryId: 2, price: 39.9, stock: 200, sales: 30, goodsState: 1 as const, detail: '产地直发,新鲜直达', createdAt: iso(new Date(Date.now() - 6 * 864e5)) },
    { id: nextId(), goodsNum: 'G847152', goodsName: '智利车厘子 2斤装', shopNum: 'SH692051', categoryId: 2, price: 89.9, stock: 80, sales: 15, goodsState: 1 as const, detail: '空运直达,个大味甜', createdAt: iso(new Date(Date.now() - 6 * 864e5)) },
    { id: nextId(), goodsNum: 'G193568', goodsName: '氮化镓充电器 65W', shopNum: 'SH417826', categoryId: 1, price: 129.0, stock: 60, sales: 21, goodsState: 1 as const, detail: '小巧便携,多口快充', createdAt: iso(new Date(Date.now() - 5 * 864e5)) },
    { id: nextId(), goodsNum: 'G675214', goodsName: '智利车厘子 JJ级 5斤装', shopNum: 'SH692051', categoryId: 2, price: 219.0, stock: 40, sales: 6, goodsState: 1 as const, detail: '礼盒装,送礼佳品', createdAt: iso(new Date(Date.now() - 4 * 864e5)) },
    { id: nextId(), goodsNum: 'G483906', goodsName: '新疆阿克苏苹果 9斤装', shopNum: 'SH692051', categoryId: 2, price: 59.9, stock: 120, sales: 18, goodsState: 1 as const, detail: '冰糖心,脆甜多汁', createdAt: iso(new Date(Date.now() - 3 * 864e5)) },
  ] as Goods[],
  payOrders: [] as PayOrder[],
  orderItems: [] as OrderItem[],
  orders: [] as BizOrder[],
  refunds: [] as RefundOrder[],
  withdraws: [
    { id: nextId(), withdrawNo: genNo('WD'), shopNum: 'SH692051', money: 500, state: 1 as const, remark: '同意', finishTime: fmt(new Date(Date.now() - 2 * 864e5)), createdAt: iso(new Date(Date.now() - 3 * 864e5)) },
    { id: nextId(), withdrawNo: genNo('WD'), shopNum: 'SH692051', money: 200, state: 0 as const, remark: null, finishTime: null, createdAt: iso(new Date(Date.now() - 1 * 864e5)) },
  ] as Withdraw[],
  logs: [
    { id: nextId(), module: 'login', content: '{"username":"admin"}', operator: 'admin', ip: '127.0.0.1', createdAt: iso(new Date(Date.now() - 2 * 36e5)) },
    { id: nextId(), module: 'auditMch', content: '{"shopNum":"SH692051","shopState":1}', operator: 'admin', ip: '127.0.0.1', createdAt: iso(new Date(Date.now() - 8 * 864e5)) },
  ],
}

/** 费率:商户差异化费率优先,否则渠道基础费率 */
function rateOf(shopNum: string, wayCode: string): number {
  const r = db.mchRates.find((x) => x.shopNum === shopNum && x.wayCode === wayCode)
  if (r) return r.mchRate
  return db.channels.find((c) => c.wayCode === wayCode)?.baseRate ?? 0.006
}

/** 种子:近 7 天支付订单 + 对应业务订单(保证统计图表有数据) */
;(function seed() {
  const goodsOf = ['G728415', 'G391647', 'G562039', 'G847152', 'G193568', 'G675214', 'G483906']
  const ways = ['WECHAT', 'ALIPAY', 'UNIONPAY']
  let i = 0
  for (let d = 6; d >= 0; d--) {
    const n = 2 + ((d * 7) % 2)
    for (let k = 0; k < n; k++) {
      i++
      const g = db.goods.find((x) => x.goodsNum === goodsOf[(i * 3 + k) % goodsOf.length])!
      const count = 1 + (i % 2)
      const total = round2(g.price * count)
      const shop = db.mchs.find((m) => m.shopNum === g.shopNum)!
      const way = ways[(i + k) % 3]
      const created = new Date(Date.now() - d * 864e5 - (k + 1) * 36e5 * 2)
      const orderNum = genNo('SO')
      const refunded = d === 4 && k === 0
      const closed = d === 5 && k === 1
      const pay: PayOrder = {
        id: nextId(),
        payOrderId: genNo('PAY'),
        mchOrderNo: orderNum,
        shopNum: shop.shopNum,
        shopName: shop.shopName,
        appId: 'mallx-app',
        wayCode: way,
        money: total,
        mchFee: round2(total * rateOf(shop.shopNum, way)),
        subject: `${g.goodsName}x${count}`,
        userNum: 'U1000000001',
        orderState: refunded ? 2 : closed ? 3 : 0,
        channelOrderNo: 'CH' + rand(14),
        payTime: closed ? null : fmt(created),
        createdAt: iso(created),
        updatedAt: iso(created),
      }
      db.payOrders.push(pay)
      const o: BizOrder = {
        orderNum,
        userNum: 'U1000000001',
        shopNum: shop.shopNum,
        totalMoney: total,
        orderState: refunded ? 3 : closed ? 4 : 3,
        payOrderId: closed ? null : pay.payOrderId,
        receiverName: '小明',
        receiverPhone: '13900000001',
        receiverAddress: '湖南省长沙市岳麓区潇湘中路1号',
        logisticsCompany: closed ? null : '顺丰速运',
        logisticsNo: closed ? null : 'SF' + rand(12),
        createdAt: iso(created),
        payTime: closed ? null : fmt(created),
        deliverTime: closed ? null : fmt(new Date(created.getTime() + 3 * 36e5)),
        receiveTime: closed || refunded ? null : fmt(new Date(created.getTime() + 26 * 36e5)),
      }
      db.orders.push(o)
      db.orderItems.push({
        id: nextId(),
        orderNum,
        goodsNum: g.goodsNum,
        goodsName: g.goodsName,
        goodsPrice: g.price,
        goodsCount: count,
        totalMoney: total,
      })
      if (refunded) {
        db.refunds.push({
          id: nextId(),
          refundOrderId: genNo('RF'),
          payOrderId: pay.payOrderId,
          orderNum,
          shopNum: shop.shopNum,
          userNum: 'U1000000001',
          refundMoney: round2(total / 2),
          refundReason: '商品破损,协商退款',
          evidence: null,
          refundState: 2,
          channelRefundNo: 'CH' + rand(14),
          auditRemark: '情况属实,同意退款',
          auditTime: fmt(new Date(created.getTime() + 5 * 36e5)),
          refundTime: fmt(new Date(created.getTime() + 5 * 36e5)),
          createdAt: iso(created),
        })
      }
    }
  }
  // 一笔待发货订单(商户端演示发货)
  {
    const g = db.goods[0]
    const orderNum = genNo('SO')
    const created = new Date(Date.now() - 2 * 36e5)
    const pay: PayOrder = {
      id: nextId(),
      payOrderId: genNo('PAY'),
      mchOrderNo: orderNum,
      shopNum: g.shopNum,
      shopName: '星选数码旗舰店',
      appId: 'mallx-app',
      wayCode: 'ALIPAY',
      money: g.price,
      mchFee: round2(g.price * rateOf(g.shopNum, 'ALIPAY')),
      subject: `${g.goodsName}x1`,
      userNum: 'U1000000001',
      orderState: 0,
      channelOrderNo: 'CH' + rand(14),
      payTime: fmt(created),
      createdAt: iso(created),
      updatedAt: iso(created),
    }
    db.payOrders.push(pay)
    db.orders.push({
      orderNum,
      userNum: 'U1000000001',
      shopNum: g.shopNum,
      totalMoney: g.price,
      orderState: 1,
      payOrderId: pay.payOrderId,
      receiverName: '小明',
      receiverPhone: '13900000001',
      receiverAddress: '湖南省长沙市岳麓区潇湘中路1号',
      logisticsCompany: null,
      logisticsNo: null,
      createdAt: iso(created),
      payTime: fmt(created),
      deliverTime: null,
      receiveTime: null,
    })
    db.orderItems.push({ id: nextId(), orderNum, goodsNum: g.goodsNum, goodsName: g.goodsName, goodsPrice: g.price, goodsCount: 1, totalMoney: g.price })
  }
  // 一笔退款待审核(用户申请,平台/商户双端可演示审核)
  {
    const pay = db.payOrders.find((p) => p.orderState === 0)!
    const ord = db.orders.find((o) => o.payOrderId === pay.payOrderId)
    db.refunds.push({
      id: nextId(),
      refundOrderId: genNo('RF'),
      payOrderId: pay.payOrderId,
      orderNum: ord?.orderNum,
      shopNum: pay.shopNum,
      userNum: pay.userNum,
      refundMoney: round2(pay.money / 3),
      refundReason: '商品质量问题：外包装有明显破损',
      evidence: null,
      refundState: 0,
      channelRefundNo: null,
      auditRemark: null,
      auditTime: null,
      refundTime: null,
      createdAt: iso(new Date(Date.now() - 36e5)),
    })
  }
})()

/* ============================ 工具 ============================ */
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

function signOf(body: Record<string, unknown>): string {
  const items = Object.entries(body)
    .filter(([k, v]) => k !== 'sign' && v !== null && v !== undefined && v !== '' && typeof v !== 'object')
    .map(([k, v]) => [k, String(v)] as const)
    .sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0))
  return md5(items.map(([k, v]) => `${k}=${v}`).join('&') + `&key=${SECRET}`)
}

function ok(res: ServerResponse, extra: Record<string, unknown> = {}) {
  res.setHeader('Content-Type', 'application/json; charset=utf-8')
  res.end(JSON.stringify({ id: '0', message: '操作成功', ...extra }))
}
function fail(res: ServerResponse, id: string, message: string) {
  res.setHeader('Content-Type', 'application/json; charset=utf-8')
  res.end(JSON.stringify({ id, message }))
}
function pageOf<T>(list: T[], pageNum: number, pageSize: number, map?: (x: T) => unknown) {
  const total = list.length
  const records = list.slice((pageNum - 1) * pageSize, pageNum * pageSize)
  return { total, pages: Math.ceil(total / pageSize) || 1, current: pageNum, size: pageSize, records: map ? records.map(map) : records }
}
const wayText = (w: string) => ({ WECHAT: '微信', ALIPAY: '支付宝', UNIONPAY: '银联' }[w] ?? w)

function requireSign(body: Record<string, unknown>, res: ServerResponse): boolean {
  const sign = body?.sign
  if (!sign) {
    fail(res, '401', '缺少签名参数sign')
    return false
  }
  if (signOf(body) !== sign) {
    fail(res, '401', '签名校验失败,禁止访问')
    return false
  }
  return true
}

/** 支付成功 → 模拟 MQ 异步驱动业务订单 0→1 */
function driveOrderPaid(pay: PayOrder) {
  setTimeout(() => {
    const o = db.orders.find((x) => x.orderNum === pay.mchOrderNo)
    if (o && o.orderState === 0) {
      o.orderState = 1
      o.payOrderId = pay.payOrderId
      o.payTime = pay.payTime
      for (const it of db.orderItems.filter((i) => i.orderNum === o.orderNum)) {
        const g = db.goods.find((x) => x.goodsNum === it.goodsNum)
        if (g) g.sales += it.goodsCount
      }
    }
  }, 1200)
}

/* ============================ 路由 ============================ */
async function handle(method: string, path: string, body: Record<string, unknown>, query: URLSearchParams, res: ServerResponse, reqHost: string) {
  await sleep(80 + Math.random() * 120)

  /* ---- 模拟收银台(GET,免签,返回 HTML) ---- */
  if (path === '/pay/mock/cashier' && method === 'GET') {
    const pay = db.payOrders.find((p) => p.payOrderId === query.get('payOrderId'))
    const rows = pay
      ? `<div class="row"><span>商品标题</span><b>${pay.subject}</b></div>
         <div class="row"><span>支付金额</span><b class="money">¥${pay.money.toFixed(2)}</b></div>
         <div class="row"><span>支付方式</span><b>${wayText(pay.wayCode)}</b></div>
         <a class="btn" href="/api/pay/mock/confirm?payOrderId=${pay.payOrderId}">确认支付</a>`
      : '<p class="tip">支付订单不存在</p>'
    res.setHeader('Content-Type', 'text/html; charset=utf-8')
    res.end(`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>Mall·X 收银台</title>
      <style>body{font-family:'PingFang SC','Microsoft YaHei',sans-serif;background:#f6f5f2;display:flex;justify-content:center;padding-top:10vh;margin:0}
      .card{background:#fff;border:1px solid #e8e4de;border-radius:4px;width:380px;padding:28px}
      h1{font-size:18px;margin:0 0 18px;color:#231f1c}.row{display:flex;justify-content:space-between;padding:10px 0;border-bottom:1px dashed #eee;color:#55504a}
      .money{color:#e23a2e;font-size:20px}.btn{display:block;text-align:center;margin-top:22px;background:#e23a2e;color:#fff;padding:12px;border-radius:4px;text-decoration:none}
      .tip{color:#8c847c}</style></head><body><div class="card"><h1>Mall·X 模拟收银台</h1>${rows}</div></body></html>`)
    return
  }
  if (path === '/pay/mock/confirm' && method === 'GET') {
    const pay = db.payOrders.find((p) => p.payOrderId === query.get('payOrderId'))
    let html: string
    if (!pay) html = '<p class="tip">支付订单不存在</p>'
    else if (pay.orderState === 4) {
      pay.orderState = 0
      pay.payTime = fmt(new Date())
      pay.channelOrderNo = 'CH' + rand(14)
      driveOrderPaid(pay)
      html = '<p class="done">✓ 支付成功</p><p class="tip">请返回商城查看订单状态</p>'
    } else html = '<p class="tip">该订单已处理,请勿重复支付</p>'
    res.setHeader('Content-Type', 'text/html; charset=utf-8')
    res.end(`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>支付结果</title>
      <style>body{font-family:'PingFang SC','Microsoft YaHei',sans-serif;background:#f6f5f2;display:flex;flex-direction:column;align-items:center;padding-top:14vh;margin:0}
      .done{color:#2f7d4f;font-size:22px}.tip{color:#8c847c}</style></head><body>${html}</body></html>`)
    return
  }
  if (path === '/category/list' && method === 'GET') {
    ok(res, { records: db.categories.slice().sort((a, b) => a.sort - b.sort) })
    return
  }
  /* 图形验证码（mock）：装饰性 SVG，注册时任意验证码均放行 */
  if (path === '/captcha/image' && method === 'GET') {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="120" height="42"><rect width="120" height="42" fill="#fdeeec"/><text x="60" y="29" font-size="22" font-weight="bold" text-anchor="middle" fill="#e23a2e" font-family="monospace" letter-spacing="4">MOCK</text></svg>`
    ok(res, {
      captchaKey: 'mock-' + genNo('C'),
      image: 'data:image/svg+xml;utf8,' + encodeURIComponent(svg),
      code: 'MOCK',
    })
    return
  }

  if (method !== 'POST') {
    fail(res, '400', '不支持的请求方式')
    return
  }

  /* ---- 登录类(免签) ---- */
  if (path === '/mch/login') {
    const m = db.mchs.find((x) => x.username === body.username && x.password === body.password)
    if (!m) return fail(res, '500', '商户账号或密码错误')
    if (m.shopState === 2) return fail(res, '500', '商户已被禁用,请联系平台管理员')
    return ok(res, { token: 'mock-jwt-' + genNo('T'), shopNum: m.shopNum, shopState: m.shopState })
  }
  if (path === '/user/login') {
    const u = db.users.find((x) => x.username === body.username && x.password === body.password)
    if (!u) return fail(res, '500', '用户账号或密码错误')
    return ok(res, { token: 'mock-jwt-' + genNo('T'), userNum: u.userNum })
  }
  if (path === '/admin/login') {
    const a = db.admins.find((x) => x.username === body.username && x.password === body.password)
    if (!a) return fail(res, '500', '管理员账号或密码错误')
    if (a.state === 0) return fail(res, '500', '账号已被禁用')
    return ok(res, { token: 'mock-jwt-' + genNo('T'), realName: a.realName, role: a.role })
  }

  /* ---- 其余接口:验签 ---- */
  if (!requireSign(body, res)) return

  switch (true) {
    /* ---------- 商户 ---------- */
    case path === '/mch/register': {
      for (const k of ['shopName', 'shopPhone', 'shopAddress', 'username', 'password'])
        if (!body[k]) return fail(res, '400', `${k}不能为空`)
      if (db.mchs.some((m) => m.username === body.username)) return fail(res, '500', '商户登录账号已存在')
      const shopNum = genNo('SH')
      db.mchs.push({
        id: nextId(),
        shopNum,
        shopName: String(body.shopName),
        shopPhone: String(body.shopPhone),
        shopAddress: String(body.shopAddress),
        username: String(body.username),
        password: String(body.password),
        shopState: 0,
        auditRemark: null,
        createdAt: iso(new Date()),
      })
      db.logs.push({ id: nextId(), module: 'mchRegister', content: JSON.stringify({ shopNum, shopName: body.shopName }), operator: 'system', ip: '127.0.0.1', createdAt: iso(new Date()) })
      return ok(res, { message: '商户注册成功,等待审核', shopNum })
    }
    case path === '/user/register': {
      if (!body.username || !body.password) return fail(res, '400', '账号与密码不能为空')
      if (db.users.some((u) => u.username === body.username)) return fail(res, '500', '用户账号已存在')
      const userNum = genNo('U')
      db.users.push({ id: nextId(), userNum, username: String(body.username), password: String(body.password), nickname: String(body.username) })
      return ok(res, { message: '注册成功', userNum })
    }

    /* ---------- 商品 ---------- */
    case path === '/goods/create': {
      for (const k of ['goodsName', 'shopNum', 'price', 'stock'])
        if (body[k] === undefined || body[k] === null || body[k] === '') return fail(res, '400', `${k}不能为空`)
      if (Number(body.price) < 0.01) return fail(res, '400', '价格必须大于0.01')
      const goodsNum = genNo('G')
      db.goods.push({
        id: nextId(),
        goodsNum,
        goodsName: String(body.goodsName),
        shopNum: String(body.shopNum),
        categoryId: body.categoryId ? Number(body.categoryId) : 0,
        price: Number(body.price),
        stock: Number(body.stock),
        sales: 0,
        goodsState: (body.goodsState === undefined ? 1 : Number(body.goodsState)) as 0 | 1,
        detail: body.detail ? String(body.detail) : '',
        createdAt: iso(new Date()),
      })
      return ok(res, { message: '商品发布成功', goodsNum })
    }
    case path === '/goods/update': {
      if (!body.goodsNum) return fail(res, '400', '编辑商品必须传goodsNum')
      const g = db.goods.find((x) => x.goodsNum === body.goodsNum)
      if (!g) return fail(res, '500', '商品不存在')
      if (body.shopNum && g.shopNum !== body.shopNum) return fail(res, '500', '无权操作其他商户商品')
      for (const k of ['goodsName', 'price', 'stock', 'goodsState', 'picUrl', 'detail', 'categoryId'])
        if (body[k] !== undefined && body[k] !== null && body[k] !== '') (g as unknown as Record<string, unknown>)[k] = k === 'categoryId' ? Number(body[k]) : k === 'goodsState' ? Number(body[k]) : k === 'stock' ? Number(body[k]) : k === 'price' ? Number(body[k]) : body[k]
      return ok(res, { message: '商品更新成功' })
    }
    case path === '/goods/list': {
      let list = db.goods.slice().sort((a, b) => b.id - a.id)
      if (body.goodsName) list = list.filter((g) => g.goodsName.includes(String(body.goodsName)))
      if (body.shopNum) list = list.filter((g) => g.shopNum === body.shopNum)
      if (body.categoryId) list = list.filter((g) => g.categoryId === Number(body.categoryId))
      if (body.goodsState !== undefined && body.goodsState !== '') list = list.filter((g) => g.goodsState === Number(body.goodsState))
      const pageNum = Number(body.pageNum || 1)
      const pageSize = Number(body.pageSize || 10)
      const shopName = (num: string) => db.mchs.find((m) => m.shopNum === num)?.shopName
      return ok(res, pageOf(list, pageNum, pageSize, (g) => ({ ...g, shopName: shopName(g.shopNum) })))
    }
    case path === '/goods/detail': {
      if (!body.goodsNum) return fail(res, '500', '商品不存在')
      const g = db.goods.find((x) => x.goodsNum === body.goodsNum)
      if (!g) return fail(res, '500', '商品不存在')
      return ok(res, { message: '查询成功', goods: { ...g, shopName: db.mchs.find((m) => m.shopNum === g.shopNum)?.shopName } })
    }

    /* ---------- 类目维护 ---------- */
    case path === '/admin/category/save': {
      if (body.id) {
        const c = db.categories.find((x) => x.id === Number(body.id))
        if (!c) return fail(res, '500', '类目不存在')
        if (body.categoryName) c.categoryName = String(body.categoryName)
        if (body.sort !== undefined) c.sort = Number(body.sort)
        return ok(res, { message: '类目更新成功' })
      }
      if (!body.categoryName) return fail(res, '400', 'categoryName不能为空')
      const id = nextId()
      db.categories.push({ id, categoryName: String(body.categoryName), parentId: Number(body.parentId ?? 0), sort: Number(body.sort ?? 0) })
      return ok(res, { message: '类目创建成功', categoryId: id })
    }

    /* ---------- 订单 ---------- */
    case path === '/order/create': {
      const items = (body.items as { goodsNum: string; count: number }[] | undefined) || []
      if (!body.userNum || !body.shopNum || items.length === 0) return fail(res, '400', '参数不完整')
      if (!body.receiverName || !body.receiverPhone || !body.receiverAddress) return fail(res, '400', '请填写收货信息')
      // 单订单仅同一商户;校验上架与库存(Lua 预扣 + DB 条件扣减)
      let total = 0
      const createdItems: OrderItem[] = []
      for (const it of items) {
        const g = db.goods.find((x) => x.goodsNum === it.goodsNum)
        if (!g || g.goodsState !== 1) return fail(res, '500', `商品[${it.goodsNum}]不存在或已下架`)
        if (g.shopNum !== body.shopNum) return fail(res, '500', '一笔订单仅支持同一商户的商品')
        if (g.stock < it.count) return fail(res, '500', `商品[${g.goodsName}]库存不足`)
        total += g.price * it.count
        createdItems.push({ id: nextId(), orderNum: '', goodsNum: g.goodsNum, goodsName: g.goodsName, goodsPrice: g.price, goodsCount: it.count, totalMoney: round2(g.price * it.count) })
      }
      const orderNum = genNo('SO')
      for (const it of createdItems) {
        it.orderNum = orderNum
        db.orderItems.push(it)
        const g = db.goods.find((x) => x.goodsNum === it.goodsNum)!
        g.stock -= it.goodsCount
      }
      db.orders.push({
        orderNum,
        userNum: String(body.userNum),
        shopNum: String(body.shopNum),
        totalMoney: round2(total),
        orderState: 0,
        payOrderId: null,
        receiverName: String(body.receiverName),
        receiverPhone: String(body.receiverPhone),
        receiverAddress: String(body.receiverAddress),
        logisticsCompany: null,
        logisticsNo: null,
        createdAt: iso(new Date()),
        payTime: null,
        deliverTime: null,
        receiveTime: null,
      })
      return ok(res, { message: '下单成功,请尽快支付', orderNum, totalMoney: round2(total) })
    }
    case path === '/order/list': {
      if (!body.userNum && !body.shopNum) return fail(res, '400', 'userNum与shopNum至少传一个')
      let list = db.orders.slice().sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
      if (body.userNum) list = list.filter((o) => o.userNum === body.userNum)
      if (body.shopNum) list = list.filter((o) => o.shopNum === body.shopNum)
      if (body.orderState !== undefined && body.orderState !== '') list = list.filter((o) => o.orderState === Number(body.orderState))
      const pageNum = Number(body.pageNum || 1)
      const pageSize = Number(body.pageSize || 10)
      const view = (o: BizOrder) => ({
        orderNum: o.orderNum,
        userNum: o.userNum,
        shopNum: o.shopNum,
        totalMoney: o.totalMoney,
        orderState: o.orderState,
        payOrderId: o.payOrderId,
        receiverName: o.receiverName,
        receiverPhone: o.receiverPhone,
        receiverAddress: o.receiverAddress,
        logisticsCompany: o.logisticsCompany,
        logisticsNo: o.logisticsNo,
        createdAt: o.createdAt ? fmt(new Date(o.createdAt)) : null,
        payTime: o.payTime,
        deliverTime: o.deliverTime,
        receiveTime: o.receiveTime,
      })
      return ok(res, pageOf(list, pageNum, pageSize, view))
    }
    case path === '/order/detail': {
      const o = db.orders.find((x) => x.orderNum === body.orderNum)
      if (!o) return fail(res, '500', '订单不存在')
      const items = db.orderItems.filter((i) => i.orderNum === o.orderNum)
      const view = {
        ...o,
        createdAt: fmt(new Date(o.createdAt)),
      }
      return ok(res, { message: '查询成功', order: { ...view, items } })
    }
    case path === '/order/deliver': {
      const o = db.orders.find((x) => x.orderNum === body.orderNum)
      if (!o) return fail(res, '500', '订单不存在')
      if (o.orderState !== 1) return fail(res, '500', '订单当前状态不可发货')
      if (!body.logisticsCompany || !body.logisticsNo) return fail(res, '400', '请填写物流公司与单号')
      o.orderState = 2
      o.logisticsCompany = String(body.logisticsCompany)
      o.logisticsNo = String(body.logisticsNo)
      o.deliverTime = fmt(new Date())
      return ok(res, { message: '发货成功' })
    }
    case path === '/order/confirm': {
      const o = db.orders.find((x) => x.orderNum === body.orderNum)
      if (!o) return fail(res, '500', '订单不存在')
      if (o.orderState !== 2) return fail(res, '500', '订单当前状态不可确认收货')
      o.orderState = 3
      o.receiveTime = fmt(new Date())
      return ok(res, { message: '确认收货成功' })
    }

    /* ---------- 支付 ---------- */
    case path === '/pay/create': {
      for (const k of ['shopNum', 'shopName', 'appId', 'wayCode', 'payMoney', 'subject', 'userNum', 'orderNum'])
        if (!body[k]) return fail(res, '400', `${k}不能为空`)
      const ch = db.channels.find((c) => c.wayCode === body.wayCode)
      if (!ch || ch.switchStatus !== 1) return fail(res, '500', '支付渠道未配置或已关闭')
      // 幂等:同商户订单号存在待支付单 → 返回原单
      const exist = db.payOrders.find((p) => p.shopNum === body.shopNum && p.mchOrderNo === body.orderNum && p.orderState === 4)
      if (exist) {
        const host = `${'http'}://${reqHost}`
        return ok(res, { message: '下单成功', payOrderId: exist.payOrderId, mchOrderNo: exist.mchOrderNo, payUrl: `${host}/api/pay/mock/cashier?payOrderId=${exist.payOrderId}`, orderState: exist.orderState })
      }
      if (db.payOrders.some((p) => p.shopNum === body.shopNum && p.mchOrderNo === body.orderNum && p.orderState !== 4))
        return fail(res, '500', '该订单已存在支付单,请勿重复支付')
      const payOrderId = genNo('PAY')
      const money = Number(body.payMoney)
      db.payOrders.push({
        id: nextId(),
        payOrderId,
        mchOrderNo: String(body.orderNum),
        shopNum: String(body.shopNum),
        shopName: String(body.shopName),
        appId: String(body.appId),
        wayCode: String(body.wayCode),
        money,
        mchFee: round2(money * rateOf(String(body.shopNum), String(body.wayCode))),
        subject: String(body.subject),
        userNum: String(body.userNum),
        orderState: 4,
        channelOrderNo: null,
        payTime: null,
        createdAt: iso(new Date()),
        updatedAt: iso(new Date()),
      })
      const host = `${'http'}://${reqHost}`
      return ok(res, { message: '下单成功', payOrderId, mchOrderNo: String(body.orderNum), payUrl: `${host}/api/pay/mock/cashier?payOrderId=${payOrderId}`, orderState: 4 })
    }
    case path === '/pay/callback': {
      for (const k of ['payOrderId', 'channelOrderNo', 'payState', 'payTime'])
        if (!body[k]) return fail(res, '400', `${k}不能为空`)
      const pay = db.payOrders.find((p) => p.payOrderId === body.payOrderId)
      if (!pay) return fail(res, '500', '支付订单不存在')
      if (pay.orderState !== 4) return ok(res, { message: '回调处理成功' }) // 状态机幂等
      const success = String(body.payState) === '0'
      pay.orderState = success ? 0 : 1
      pay.channelOrderNo = String(body.channelOrderNo)
      pay.payTime = String(body.payTime)
      pay.updatedAt = iso(new Date())
      if (success) driveOrderPaid(pay)
      return ok(res, { message: '回调处理成功' })
    }
    case path === '/pay/query': {
      if (!body.shopNum || !body.payOrderNum) return fail(res, '400', '参数不完整')
      const pay = db.payOrders.find((p) => p.payOrderId === body.payOrderNum)
      if (!pay) return fail(res, '500', '支付订单不存在')
      return ok(res, {
        message: '查询订单成功',
        payOrderId: pay.payOrderId,
        mchOrderNo: pay.mchOrderNo,
        appId: pay.appId,
        wayCode: pay.wayCode,
        money: pay.money,
        subject: pay.subject,
        createdAt: fmt(new Date(pay.createdAt)),
        payTime: pay.payTime,
        orderState: pay.orderState,
      })
    }
    case path === '/pay/list/mch': {
      if (!body.shopNum) return fail(res, '400', 'shopNum不能为空')
      let list = db.payOrders.filter((p) => p.shopNum === body.shopNum).slice().sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
      if (body.orderState !== undefined && body.orderState !== '') list = list.filter((p) => p.orderState === Number(body.orderState))
      if (body.wayCode) list = list.filter((p) => p.wayCode === body.wayCode)
      const pageNum = Number(body.pageNum || 1)
      const pageSize = Number(body.pageSize || 10)
      const view = (p: PayOrder) => ({
        payOrderId: p.payOrderId,
        mchOrderNo: p.mchOrderNo,
        wayCode: p.wayCode,
        money: p.money,
        mchFee: p.mchFee,
        orderState: p.orderState,
        createdAt: fmt(new Date(p.createdAt)),
        payTime: p.payTime,
        subject: p.subject,
      })
      return ok(res, pageOf(list, pageNum, pageSize, view))
    }

    /* ---------- 退款/售后 ---------- */
    case path === '/refund/create': {
      for (const k of ['payOrderId', 'refundMoney', 'refundReason', 'shopNum'])
        if (!body[k]) return fail(res, '400', `${k}不能为空`)
      const pay = db.payOrders.find((p) => p.payOrderId === body.payOrderId)
      if (!pay || pay.shopNum !== body.shopNum) return fail(res, '500', '支付订单不存在')
      if (pay.orderState !== 0) return fail(res, '500', '仅支付成功订单可退款')
      const refunded = db.refunds.filter((r) => r.payOrderId === pay.payOrderId && r.refundState !== 3).reduce((a, r) => a + r.refundMoney, 0)
      if (round2(refunded + Number(body.refundMoney)) > pay.money) return fail(res, '500', `退款金额超过可退余额 ${round2(pay.money - refunded)}`)
      const refundOrderId = genNo('RF')
      db.refunds.push({
        id: nextId(),
        refundOrderId,
        payOrderId: pay.payOrderId,
        orderNum: pay.mchOrderNo,
        shopNum: String(body.shopNum),
        userNum: pay.userNum,
        refundMoney: Number(body.refundMoney),
        refundReason: String(body.refundReason),
        evidence: null,
        refundState: 0,
        channelRefundNo: null,
        auditRemark: null,
        auditTime: null,
        refundTime: null,
        createdAt: iso(new Date()),
      })
      return ok(res, { message: '退款申请提交成功,等待审核', refundOrderId })
    }
    case path === '/refund/query': {
      const r = db.refunds.find((x) => x.refundOrderId === body.refundOrderId && x.shopNum === body.shopNum)
      if (!r) return fail(res, '500', '退款订单不存在')
      return ok(res, {
        message: '查询成功',
        refundOrderId: r.refundOrderId,
        payOrderId: r.payOrderId,
        orderNum: r.orderNum,
        refundMoney: r.refundMoney,
        refundState: r.refundState,
        refundReason: r.refundReason,
        evidence: r.evidence,
        auditRemark: r.auditRemark,
        auditTime: r.auditTime,
        refundTime: r.refundTime,
      })
    }
    /* 用户端:发起退款申请(订单维度,支持部分退款,可附凭证) */
    case path === '/refund/apply': {
      for (const k of ['orderNum', 'userNum', 'refundMoney', 'refundReason'])
        if (!body[k]) return fail(res, '400', `${k}不能为空`)
      const order = db.orders.find((o) => o.orderNum === body.orderNum)
      if (!order) return fail(res, '500', '订单不存在')
      if (order.userNum !== body.userNum) return fail(res, '500', '只能对本人订单发起退款申请')
      if (order.orderState === 0 || order.orderState === 4) return fail(res, '500', '订单当前状态不支持退款')
      const pay = db.payOrders.find((p) => p.payOrderId === order.payOrderId)
      if (!pay || pay.orderState !== 0) return fail(res, '500', '该订单当前支付状态不支持退款')
      if (db.refunds.some((r) => r.payOrderId === pay.payOrderId && (r.refundState === 0 || r.refundState === 1)))
        return fail(res, '500', '该订单已有退款申请正在处理中，请勿重复提交')
      const used = db.refunds.filter((r) => r.payOrderId === pay.payOrderId && r.refundState !== 3).reduce((a, r) => a + r.refundMoney, 0)
      if (round2(used + Number(body.refundMoney)) > pay.money)
        return fail(res, '500', `退款金额超出可退款余额 ${round2(pay.money - used)}`)
      const refundOrderId = genNo('RF')
      db.refunds.push({
        id: nextId(),
        refundOrderId,
        payOrderId: pay.payOrderId,
        orderNum: order.orderNum,
        shopNum: order.shopNum,
        userNum: order.userNum,
        refundMoney: Number(body.refundMoney),
        refundReason: String(body.refundReason),
        evidence: Array.isArray(body.evidence) && body.evidence.length ? JSON.stringify(body.evidence) : null,
        refundState: 0,
        channelRefundNo: null,
        auditRemark: null,
        auditTime: null,
        refundTime: null,
        createdAt: iso(new Date()),
      })
      db.logs.push({ id: nextId(), module: 'applyRefund', content: JSON.stringify({ refundOrderId, orderNum: order.orderNum }), operator: String(body.userNum), ip: '127.0.0.1', createdAt: iso(new Date()) })
      return ok(res, { message: '退款申请已提交，等待商户处理', refundOrderId })
    }
    /* 用户端:我的退款记录 */
    case path === '/refund/user/list': {
      const list = db.refunds.filter((r) => r.userNum === body.userNum).sort((a, b) => b.id - a.id)
      return ok(res, pageOf(list, Number(body.pageNum || 1), Number(body.pageSize || 10)))
    }
    /* 退款进度详情(用户/商户按归属校验) */
    case path === '/refund/detail': {
      const r = db.refunds.find((x) => x.refundOrderId === body.refundOrderId)
      if (!r) return fail(res, '500', '退款单不存在')
      if (body.userNum && r.userNum !== body.userNum) return fail(res, '500', '退款单不存在')
      if (body.shopNum && r.shopNum !== body.shopNum) return fail(res, '500', '退款单不存在')
      return ok(res, { message: '查询成功', refund: r })
    }
    /* 上传退款凭证(mock 直接回显 dataURL 作为地址) */
    case path === '/refund/evidence/upload': {
      const img = String(body.imageBase64 || '')
      if (!img.startsWith('data:image/')) return fail(res, '400', '图片数据格式不正确')
      return ok(res, { message: '凭证上传成功', url: img })
    }
    /* 商户端:退款列表 */
    case path === '/refund/mch/list': {
      let list = db.refunds.filter((r) => r.shopNum === body.shopNum).sort((a, b) => b.id - a.id)
      if (body.refundState !== undefined && body.refundState !== '') list = list.filter((r) => r.refundState === Number(body.refundState))
      return ok(res, pageOf(list, Number(body.pageNum || 1), Number(body.pageSize || 10)))
    }
    /* 商户端:审核退款(同意执行渠道退款/拒绝) */
    case path === '/refund/mch/audit': {
      const r = db.refunds.find((x) => x.refundOrderId === body.refundOrderId)
      if (!r) return fail(res, '500', '退款单不存在')
      if (r.refundState !== 0) return fail(res, '500', '该退款单已处理，请勿重复操作')
      const st = Number(body.auditState)
      r.auditRemark = body.auditRemark ? String(body.auditRemark) : null
      r.auditTime = fmt(new Date())
      if (st === 2) {
        r.refundState = 3
        return ok(res, { message: '退款申请已拒绝' })
      }
      if (st !== 1) return fail(res, '400', 'auditState仅支持1-同意/2-拒绝')
      r.refundState = 2
      r.channelRefundNo = 'CH' + rand(14)
      r.refundTime = fmt(new Date())
      const pay = db.payOrders.find((p) => p.payOrderId === r.payOrderId)
      if (pay) {
        const used = db.refunds.filter((x) => x.payOrderId === r.payOrderId && x.refundState !== 3).reduce((a, x) => a + x.refundMoney, 0)
        if (round2(used) >= pay.money) pay.orderState = 2
      }
      db.logs.push({ id: nextId(), module: 'auditRefundMch', content: JSON.stringify({ refundOrderId: r.refundOrderId, auditState: 1 }), operator: 'mch', ip: '127.0.0.1', createdAt: iso(new Date()) })
      return ok(res, { message: '退款成功' })
    }
    /* 商户端:退款统计概览 */
    case path === '/refund/mch/stats': {
      const list = db.refunds.filter((r) => r.shopNum === body.shopNum)
      const byState = (s: number) => list.filter((r) => r.refundState === s).length
      const sum = (arr: RefundOrder[]) => round2(arr.reduce((a, r) => a + r.refundMoney, 0))
      return ok(res, {
        message: '查询成功',
        waitCount: byState(0),
        processingCount: byState(1),
        successCount: byState(2),
        failCount: byState(3),
        totalCount: list.length,
        successMoney: sum(list.filter((r) => r.refundState === 2)),
        pendingMoney: sum(list.filter((r) => r.refundState === 0 || r.refundState === 1)),
      })
    }

    /* ---------- 统计 ---------- */
    case path === '/statis/platform': {
      const valid = db.payOrders.filter((p) => p.orderState === 0 || p.orderState === 2)
      const totalMoney = round2(valid.reduce((a, p) => a + p.money, 0))
      const byWay = new Map<string, { count: number; tradeMoney: number }>()
      for (const p of valid) {
        const e = byWay.get(p.wayCode) || { count: 0, tradeMoney: 0 }
        e.count++
        e.tradeMoney = round2(e.tradeMoney + p.money)
        byWay.set(p.wayCode, e)
      }
      const channelRatio = [...byWay.entries()].map(([wayCode, e]) => ({
        wayCode: wayText(wayCode),
        count: e.count,
        tradeMoney: e.tradeMoney,
        ratio: totalMoney ? ((e.tradeMoney / totalMoney) * 100).toFixed(2) : '0.00',
      }))
      const sevenDayData = Array.from({ length: 7 }, (_, i) => {
        const d = new Date(Date.now() - (6 - i) * 864e5)
        const key = dayStr(d)
        const day = valid.filter((p) => dayStr(new Date(p.createdAt)) === key)
        return { date: key, tradeMoney: round2(day.reduce((a, p) => a + p.money, 0)), tradeCount: day.length }
      })
      return ok(res, { message: '统计成功', totalMoney, totalCount: valid.length, channelRatio, sevenDayData })
    }
    case path === '/statis/mch': {
      if (!body.shopNum) return fail(res, '400', 'shopNum不能为空')
      const all = db.payOrders.filter((p) => p.shopNum === body.shopNum)
      const valid = all.filter((p) => p.orderState === 0 || p.orderState === 2)
      const totalIncome = round2(valid.reduce((a, p) => a + p.money, 0))
      const totalServiceFee = round2(valid.reduce((a, p) => a + p.mchFee, 0))
      const byWay = new Map<string, { tradeMoney: number; tradeCount: number; fee: number }>()
      for (const p of valid) {
        const e = byWay.get(p.wayCode) || { tradeMoney: 0, tradeCount: 0, fee: 0 }
        e.tradeMoney = round2(e.tradeMoney + p.money)
        e.tradeCount++
        e.fee = round2(e.fee + p.mchFee)
        byWay.set(p.wayCode, e)
      }
      const sevenDayTradeData = Array.from({ length: 7 }, (_, i) => {
        const key = dayStr(new Date(Date.now() - (6 - i) * 864e5))
        const day = valid.filter((p) => dayStr(new Date(p.createdAt)) === key)
        return { date: key, tradeMoney: round2(day.reduce((a, p) => a + p.money, 0)), tradeCount: day.length }
      })
      return ok(res, {
        message: '统计查询成功',
        totalIncome,
        totalServiceFee,
        actualIncome: round2(totalIncome - totalServiceFee),
        totalTradeCount: valid.length,
        successRate: all.length ? ((valid.length / all.length) * 100).toFixed(2) : '100.00',
        channelTradeData: [...byWay.entries()].map(([wayCode, e]) => ({ wayCode: wayText(wayCode), ...e })),
        sevenDayTradeData,
      })
    }

    /* ---------- 资金 ---------- */
    case path === '/fund/overview': {
      if (!body.shopNum) return fail(res, '400', 'shopNum不能为空')
      const pays = db.payOrders.filter((p) => p.shopNum === body.shopNum && (p.orderState === 0 || p.orderState === 2))
      const totalIncome = round2(pays.reduce((a, p) => a + p.money, 0))
      const totalServiceFee = round2(pays.reduce((a, p) => a + p.mchFee, 0))
      const refunded = round2(db.refunds.filter((r) => r.shopNum === body.shopNum && r.refundState === 2).reduce((a, r) => a + r.refundMoney, 0))
      const withdrawn = round2(db.withdraws.filter((w) => w.shopNum === body.shopNum && w.state !== 2).reduce((a, w) => a + w.money, 0))
      return ok(res, {
        message: '查询成功',
        fund: {
          totalIncome,
          totalServiceFee,
          actualIncome: round2(totalIncome - totalServiceFee),
          refunded,
          withdrawn,
          balance: Math.max(0, round2(totalIncome - totalServiceFee - refunded - withdrawn)),
        },
      })
    }
    case path === '/fund/withdraw/create': {
      if (!body.shopNum || !body.money) return fail(res, '400', '参数不完整')
      const overview = await new Promise<{ balance: number }>((resolve) => {
        const pays = db.payOrders.filter((p) => p.shopNum === body.shopNum && (p.orderState === 0 || p.orderState === 2))
        const totalIncome = round2(pays.reduce((a, p) => a + p.money, 0))
        const totalServiceFee = round2(pays.reduce((a, p) => a + p.mchFee, 0))
        const refunded = round2(db.refunds.filter((r) => r.shopNum === body.shopNum && r.refundState === 2).reduce((a, r) => a + r.refundMoney, 0))
        const withdrawn = round2(db.withdraws.filter((w) => w.shopNum === body.shopNum && w.state !== 2).reduce((a, w) => a + w.money, 0))
        resolve({ balance: Math.max(0, round2(totalIncome - totalServiceFee - refunded - withdrawn)) })
      })
      if (Number(body.money) > overview.balance) return fail(res, '500', `提现金额超过可提现余额 ${overview.balance}`)
      const withdrawNo = genNo('WD')
      db.withdraws.push({ id: nextId(), withdrawNo, shopNum: String(body.shopNum), money: Number(body.money), state: 0, remark: null, finishTime: null, createdAt: iso(new Date()) })
      return ok(res, { message: '提现申请提交成功', withdrawNo })
    }
    case path === '/fund/withdraw/list': {
      let list = db.withdraws.filter((w) => !body.shopNum || w.shopNum === body.shopNum).slice().sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
      const pageNum = Number(body.pageNum || 1)
      const pageSize = Number(body.pageSize || 10)
      return ok(res, pageOf(list, pageNum, pageSize, (w) => ({ ...w, createdAt: w.createdAt.includes('T') ? fmt(new Date(w.createdAt)) : w.createdAt })))
    }

    /* ---------- 平台管理 ---------- */
    case path === '/admin/mch/audit': {
      const m = db.mchs.find((x) => x.shopNum === body.shopNum)
      if (!m) return fail(res, '500', '商户不存在')
      const st = Number(body.shopState)
      if (st !== 1 && st !== 2) return fail(res, '400', 'shopState仅支持1-通过/2-驳回')
      if (st === 2 && !body.auditRemark) return fail(res, '400', '审核驳回需填写原因')
      m.shopState = st as 1 | 2
      m.auditRemark = body.auditRemark ? String(body.auditRemark) : null
      db.logs.push({ id: nextId(), module: 'auditMch', content: JSON.stringify({ shopNum: m.shopNum, shopState: st }), operator: 'admin', ip: '127.0.0.1', createdAt: iso(new Date()) })
      return ok(res, { message: '商户审核操作成功' })
    }
    case path === '/admin/mch/list': {
      let list = db.mchs.slice().sort((a, b) => b.id - a.id)
      if (body.shopName) list = list.filter((m) => m.shopName.includes(String(body.shopName)))
      if (body.shopState !== undefined && body.shopState !== '') list = list.filter((m) => m.shopState === Number(body.shopState))
      const pageNum = Number(body.pageNum || 1)
      const pageSize = Number(body.pageSize || 10)
      return ok(res, pageOf(list, pageNum, pageSize, (m) => ({ ...m, password: null, balance: 0 })))
    }
    case path === '/admin/mch/rate': {
      if (!body.shopNum || !body.wayCode || body.mchRate === undefined) return fail(res, '400', '参数不完整')
      const rate = Number(body.mchRate)
      if (rate < 0 || rate > 1) return fail(res, '400', '费率须在0~1之间')
      const found = db.mchRates.find((r) => r.shopNum === body.shopNum && r.wayCode === body.wayCode)
      if (found) found.mchRate = rate
      else db.mchRates.push({ shopNum: String(body.shopNum), wayCode: String(body.wayCode), mchRate: rate })
      return ok(res, { message: '商户费率配置成功' })
    }
    case path === '/admin/channel/config': {
      if (!body.wayCode) return fail(res, '400', 'wayCode不能为空')
      const c = db.channels.find((x) => x.wayCode === body.wayCode)
      if (!c) return fail(res, '500', '渠道不存在')
      for (const k of ['channelName', 'appId', 'privateKey', 'alipayPublicKey', 'gatewayUrl', 'notifyKey'])
        if (body[k] !== undefined && body[k] !== null && body[k] !== '') (c as unknown as Record<string, unknown>)[k] = body[k]
      if (body.switchStatus !== undefined) c.switchStatus = Number(body.switchStatus) as 0 | 1
      if (body.baseRate !== undefined && body.baseRate !== '') c.baseRate = Number(body.baseRate)
      db.logs.push({ id: nextId(), module: 'configChannel', content: JSON.stringify({ wayCode: c.wayCode }), operator: 'admin', ip: '127.0.0.1', createdAt: iso(new Date()) })
      return ok(res, { message: '支付渠道配置成功' })
    }
    case path === '/admin/channel/list':
      return ok(res, { message: '查询成功', records: db.channels.map((c) => ({ ...c, privateKey: null, alipayPublicKey: null })) })
    case path === '/admin/pay/list': {
      let list = db.payOrders.slice().sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
      if (body.shopNum) list = list.filter((p) => p.shopNum === body.shopNum)
      if (body.orderState !== undefined && body.orderState !== '') list = list.filter((p) => p.orderState === Number(body.orderState))
      if (body.wayCode) list = list.filter((p) => p.wayCode === body.wayCode)
      if (body.startTime) list = list.filter((p) => p.createdAt >= String(body.startTime))
      if (body.endTime) list = list.filter((p) => p.createdAt <= String(body.endTime).replace(' ', 'T'))
      const pageNum = Number(body.pageNum || 1)
      const pageSize = Number(body.pageSize || 10)
      return ok(res, pageOf(list, pageNum, pageSize))
    }
    case path === '/admin/refund/audit': {
      const r = db.refunds.find((x) => x.refundOrderId === body.refundOrderId)
      if (!r) return fail(res, '500', '退款订单不存在')
      if (r.refundState !== 0) return fail(res, '500', '该退款单已处理,请勿重复操作')
      const st = Number(body.auditState)
      r.auditRemark = body.auditRemark ? String(body.auditRemark) : null
      r.auditTime = fmt(new Date())
      if (st === 1) {
        r.refundState = 2
        r.channelRefundNo = 'CH' + rand(14)
        r.refundTime = fmt(new Date())
        const pay = db.payOrders.find((p) => p.payOrderId === r.payOrderId)
        if (pay) {
          const used = db.refunds.filter((x) => x.payOrderId === r.payOrderId && x.refundState !== 3).reduce((a, x) => a + x.refundMoney, 0)
          if (round2(used) >= pay.money) pay.orderState = 2
        }
        db.logs.push({ id: nextId(), module: 'auditRefund', content: JSON.stringify({ refundOrderId: r.refundOrderId, auditState: 1 }), operator: 'admin', ip: '127.0.0.1', createdAt: iso(new Date()) })
        return ok(res, { message: '退款成功' })
      }
      if (st === 2) {
        r.refundState = 3
        return ok(res, { message: '退款申请已驳回' })
      }
      return fail(res, '400', 'auditState仅支持1-通过/2-驳回')
    }
    case path === '/admin/refund/list': {
      let list = db.refunds.slice().sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
      if (body.shopNum) list = list.filter((r) => r.shopNum === body.shopNum)
      if (body.refundState !== undefined && body.refundState !== '') list = list.filter((r) => r.refundState === Number(body.refundState))
      const pageNum = Number(body.pageNum || 1)
      const pageSize = Number(body.pageSize || 10)
      return ok(res, pageOf(list, pageNum, pageSize))
    }
    case path === '/admin/withdraw/audit': {
      const w = db.withdraws.find((x) => x.withdrawNo === body.withdrawNo)
      if (!w) return fail(res, '500', '提现单不存在')
      if (w.state !== 0) return fail(res, '500', '该提现单已处理')
      const st = Number(body.state)
      if (st !== 1 && st !== 2) return fail(res, '400', 'state仅支持1-成功/2-失败')
      w.state = st as 1 | 2
      w.remark = body.remark ? String(body.remark) : null
      w.finishTime = fmt(new Date())
      return ok(res, { message: '提现审核完成' })
    }
    case path === '/admin/withdraw/list': {
      let list = db.withdraws.slice().sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
      if (body.shopNum) list = list.filter((w) => w.shopNum === body.shopNum)
      const pageNum = Number(body.pageNum || 1)
      const pageSize = Number(body.pageSize || 10)
      return ok(res, pageOf(list, pageNum, pageSize, (w) => ({ ...w, createdAt: w.createdAt.includes('T') ? fmt(new Date(w.createdAt)) : w.createdAt })))
    }
    case path === '/admin/user/create': {
      if (!body.username || !body.password || !body.realName) return fail(res, '400', '参数不完整')
      if (db.admins.some((a) => a.username === body.username)) return fail(res, '500', '账号已存在')
      db.admins.push({ id: nextId(), username: String(body.username), password: String(body.password), realName: String(body.realName), role: (body.role === 'ADMIN' ? 'ADMIN' : 'OPS') as 'ADMIN' | 'OPS', state: 1 })
      return ok(res, { message: '后台账号创建成功' })
    }
    case path === '/admin/mch/delete': {
      const idx = db.mchs.findIndex((m) => m.shopNum === body.shopNum)
      if (idx < 0) return fail(res, '500', '商户不存在或已被删除')
      const m = db.mchs[idx]
      if (db.goods.some((g) => g.shopNum === m.shopNum)) return fail(res, '500', '该商户下仍有商品，请先处理商品后再删除')
      if (db.orders.some((o) => o.shopNum === m.shopNum)) return fail(res, '500', '该商户存在业务订单，为保证交易可追溯不可删除')
      if (db.payOrders.some((p) => p.shopNum === m.shopNum)) return fail(res, '500', '该商户存在支付订单，为保证资金可追溯不可删除')
      if (db.refunds.some((r) => r.shopNum === m.shopNum)) return fail(res, '500', '该商户存在退款记录，为保证资金可追溯不可删除')
      if (db.withdraws.some((w) => w.shopNum === m.shopNum)) return fail(res, '500', '该商户存在提现记录，为保证资金可追溯不可删除')
      db.mchRates = db.mchRates.filter((r) => r.shopNum !== m.shopNum)
      db.mchs.splice(idx, 1)
      db.logs.push({ id: nextId(), module: 'deleteMch', content: JSON.stringify({ shopNum: m.shopNum }), operator: 'admin', ip: '127.0.0.1', createdAt: iso(new Date()) })
      return ok(res, { message: `商户「${m.shopName}」已删除` })
    }
    case path === '/admin/goods/delete': {
      const idx = db.goods.findIndex((g) => g.goodsNum === body.goodsNum)
      if (idx < 0) return fail(res, '500', '商品不存在或已被删除')
      const g = db.goods[idx]
      if (db.orderItems.some((it) => it.goodsNum === g.goodsNum))
        return fail(res, '500', '该商品已被订单引用，为保证交易可追溯不可删除，可改为下架')
      db.goods.splice(idx, 1)
      db.logs.push({ id: nextId(), module: 'deleteGoods', content: JSON.stringify({ goodsNum: g.goodsNum }), operator: 'admin', ip: '127.0.0.1', createdAt: iso(new Date()) })
      return ok(res, { message: `商品「${g.goodsName}」已删除` })
    }
    case path === '/admin/goods/pic': {
      const g = db.goods.find((x) => x.goodsNum === body.goodsNum)
      if (!g) return fail(res, '500', '商品不存在或已被删除')
      const img = String(body.imageBase64 || '')
      if (!img.startsWith('data:image/')) return fail(res, '400', '图片数据格式不正确')
      g.picUrl = img
      db.logs.push({ id: nextId(), module: 'updateGoodsPic', content: JSON.stringify({ goodsNum: g.goodsNum }), operator: 'admin', ip: '127.0.0.1', createdAt: iso(new Date()) })
      return ok(res, { message: '商品图片更新成功', picUrl: img })
    }
    case path === '/goods/delete': {
      const idx = db.goods.findIndex((x) => x.goodsNum === body.goodsNum)
      if (idx < 0) return fail(res, '500', '商品不存在或已被删除')
      const g = db.goods[idx]
      if (db.orderItems.some((it) => it.goodsNum === g.goodsNum))
        return fail(res, '500', '该商品已被订单引用，为保证交易可追溯不可删除，可改为下架')
      db.goods.splice(idx, 1)
      db.logs.push({ id: nextId(), module: 'mchDeleteGoods', content: JSON.stringify({ goodsNum: g.goodsNum }), operator: 'merchant', ip: '127.0.0.1', createdAt: iso(new Date()) })
      return ok(res, { message: `商品「${g.goodsName}」已删除` })
    }
    case path === '/admin/pay/delete': {
      const idx = db.payOrders.findIndex((p) => p.payOrderId === body.payOrderId)
      if (idx < 0) return fail(res, '500', '支付订单不存在或已被删除')
      const p = db.payOrders[idx]
      if (p.orderState === 0 || p.orderState === 2)
        return fail(res, '500', '支付成功/已退款的订单涉及资金流向，不可删除')
      db.payOrders.splice(idx, 1)
      db.logs.push({ id: nextId(), module: 'deletePay', content: JSON.stringify({ payOrderId: p.payOrderId }), operator: 'admin', ip: '127.0.0.1', createdAt: iso(new Date()) })
      return ok(res, { message: `支付订单「${p.payOrderId}」已删除` })
    }
    case path === '/admin/refund/delete': {
      const idx = db.refunds.findIndex((r) => r.refundOrderId === body.refundOrderId)
      if (idx < 0) return fail(res, '500', '退款单不存在或已被删除')
      const r = db.refunds[idx]
      if (r.refundState !== 3) return fail(res, '500', '仅已拒绝的记录可删除，待审核/处理中/已完成的记录须留痕')
      db.refunds.splice(idx, 1)
      db.logs.push({ id: nextId(), module: 'deleteRefund', content: JSON.stringify({ refundOrderId: r.refundOrderId }), operator: 'admin', ip: '127.0.0.1', createdAt: iso(new Date()) })
      return ok(res, { message: `退款单「${r.refundOrderId}」已删除` })
    }
    case path === '/admin/channel/delete': {
      const idx = db.channels.findIndex((c) => c.wayCode === body.wayCode)
      if (idx < 0) return fail(res, '500', '渠道配置不存在或已被删除')
      if (db.channels.length <= 1) return fail(res, '500', '系统须保留至少一个支付渠道，无法删除')
      const c = db.channels[idx]
      db.channels.splice(idx, 1)
      db.logs.push({ id: nextId(), module: 'deleteChannel', content: JSON.stringify({ wayCode: c.wayCode }), operator: 'admin', ip: '127.0.0.1', createdAt: iso(new Date()) })
      return ok(res, { message: `支付渠道「${c.channelName}」配置已删除` })
    }
    case path === '/admin/withdraw/delete': {
      const idx = db.withdraws.findIndex((w) => w.withdrawNo === body.withdrawNo)
      if (idx < 0) return fail(res, '500', '提现单不存在或已被删除')
      const w = db.withdraws[idx]
      if (w.state !== 2) return fail(res, '500', '仅已驳回的提现单可删除，审核中/已到账记录涉及资金流向须保留')
      db.withdraws.splice(idx, 1)
      db.logs.push({ id: nextId(), module: 'deleteWithdraw', content: JSON.stringify({ withdrawNo: w.withdrawNo }), operator: 'admin', ip: '127.0.0.1', createdAt: iso(new Date()) })
      return ok(res, { message: `提现单「${w.withdrawNo}」已删除` })
    }
    case path === '/admin/category/delete': {
      const cid = Number(body.categoryId)
      const idx = db.categories.findIndex((c) => c.id === cid)
      if (idx < 0) return fail(res, '500', '类目不存在或已被删除')
      if (db.categories.some((c) => c.parentId === cid)) return fail(res, '500', '该类目下存在子类目，请先删除子类目')
      if (db.goods.some((g) => g.categoryId === cid)) return fail(res, '500', '该类目下存在商品，无法删除')
      const c = db.categories[idx]
      db.categories.splice(idx, 1)
      db.logs.push({ id: nextId(), module: 'deleteCategory', content: JSON.stringify({ categoryId: cid }), operator: 'admin', ip: '127.0.0.1', createdAt: iso(new Date()) })
      return ok(res, { message: `类目「${c.categoryName}」已删除` })
    }
    case path === '/admin/log/delete': {
      const lid = Number(body.id)
      const idx = db.logs.findIndex((l) => l.id === lid)
      if (idx < 0) return fail(res, '500', '日志不存在或已被删除')
      db.logs.splice(idx, 1)
      db.logs.push({ id: nextId(), module: 'deleteLog', content: JSON.stringify({ id: lid }), operator: 'admin', ip: '127.0.0.1', createdAt: iso(new Date()) })
      return ok(res, { message: '操作日志已删除' })
    }
    case path === '/admin/log/list': {
      const pageNum = Number(body.pageNum || 1)
      const pageSize = Number(body.pageSize || 10)
      const list = db.logs.slice().sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
      return ok(res, pageOf(list, pageNum, pageSize))
    }

    default:
      return fail(res, '500', `mock 未实现的接口: ${path}`)
  }
}

/* ============================ Vite 插件 ============================ */
export function mallMock(): Plugin {
  return {
    name: 'mall-mock',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use('/api', ((req, res, next) => {
        const url = new URL(req.url || '/', 'http://mock.local')
        const path = url.pathname.replace(/\/+$/, '') || '/'
        const method = (req.method || 'GET').toUpperCase()
        if (method === 'POST') {
          const chunks: Buffer[] = []
          req.on('data', (c: Buffer) => chunks.push(c))
          req.on('end', () => {
            let body: Record<string, unknown> = {}
            try {
              body = JSON.parse(Buffer.concat(chunks).toString('utf-8') || '{}')
            } catch {
              body = {}
            }
            handle(method, path, body, url.searchParams, res, req.headers.host || 'localhost:5173').catch(() => fail(res, '500', 'mock 内部错误'))
          })
        } else {
          handle(method, path, {}, url.searchParams, res, req.headers.host || 'localhost:5173').catch(() => fail(res, '500', 'mock 内部错误'))
        }
      }) as Connect.NextHandleFunction)
    },
  }
}
