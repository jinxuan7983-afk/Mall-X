import { get, post, postFree } from './client'
import type {
  BizOrder,
  Category,
  ChannelConfig,
  ChannelRatio,
  ChannelTrade,
  DayPoint,
  FundOverview,
  Goods,
  Mch,
  OperateLog,
  Page,
  PayOrder,
  RefundOrder,
  RefundStats,
  R,
  Withdraw,
} from '@/types'

/* ---------- 用户 ---------- */
export const captchaImage = () =>
  get<{ captchaKey: string; image: string; code?: string }>('/captcha/image')
export const userRegister = (username: string, password: string, captchaKey: string, captchaCode: string) =>
  post<{ userNum: string }>('/user/register', { username, password, captchaKey, captchaCode })
export const userLogin = (username: string, password: string) =>
  postFree<{ token: string; userNum: string }>('/user/login', { username, password })

/* ---------- 商品 ---------- */
export const goodsList = (params: Record<string, unknown> = {}) =>
  post<Page<Goods>>('/goods/list', params)
export const goodsDetail = (goodsNum: string) =>
  post<{ goods: Goods }>('/goods/detail', { goodsNum })
export const goodsCreate = (p: Record<string, unknown>) => post<{ goodsNum: string }>('/goods/create', p)
export const goodsUpdate = (p: Record<string, unknown>) => post('/goods/update', p)

/* ---------- 类目 ---------- */
/** 类目列表:后端为 GET 免签接口 */
export const categoryList = () => get<{ records: Category[] }>('/category/list')
export const categorySave = (p: Record<string, unknown>) => post<{ categoryId?: number }>('/admin/category/save', p)

/* ---------- 订单 ---------- */
export interface OrderCreateResult {
  orderNum: string
  totalMoney: number
}
export const orderCreate = (p: {
  userNum: string
  shopNum: string
  items: { goodsNum: string; count: number }[]
  receiverName: string
  receiverPhone: string
  receiverAddress: string
}) => post<OrderCreateResult>('/order/create', p as unknown as Record<string, unknown>)
export const orderList = (p: { userNum?: string; shopNum?: string; pageNum?: number; pageSize?: number; orderState?: number }) =>
  post<Page<BizOrder>>('/order/list', p as unknown as Record<string, unknown>)
export const orderDetail = (orderNum: string) => post<{ order: BizOrder }>('/order/detail', { orderNum })
export const orderDeliver = (orderNum: string, logisticsCompany: string, logisticsNo: string) =>
  post('/order/deliver', { orderNum, logisticsCompany, logisticsNo })
export const orderConfirm = (orderNum: string) => post('/order/confirm', { orderNum })

/* ---------- 支付 ---------- */
export const payCreate = (p: {
  shopNum: string
  shopName: string
  appId: string
  wayCode: string
  payMoney: string
  subject: string
  userNum: string
  orderNum: string
}) =>
  post<{ payOrderId: string; mchOrderNo: string; payUrl: string; orderState: number }>('/pay/create', {
    ...p,
  } as unknown as Record<string, unknown>)
export const payQuery = (shopNum: string, payOrderNum: string) =>
  post<PayOrder>('/pay/query', { shopNum, appId: 'mallx-app', payOrderNum })
export const payListMch = (p: Record<string, unknown>) => post<Page<PayOrder>>('/pay/list/mch', p)

/* ---------- 退款/售后 ---------- */
export const refundCreate = (p: { payOrderId: string; refundMoney: string; refundReason: string; shopNum: string }) =>
  post<{ refundOrderId: string }>('/refund/create', { ...p } as unknown as Record<string, unknown>)
export const refundQuery = (refundOrderId: string, shopNum: string) =>
  post<RefundOrder>('/refund/query', { refundOrderId, shopNum })
export const refundAudit = (refundOrderId: string, auditState: 1 | 2, auditRemark?: string) =>
  post('/admin/refund/audit', { refundOrderId, auditState, auditRemark })
export const refundList = (p: Record<string, unknown> = {}) => post<Page<RefundOrder>>('/admin/refund/list', p)

/** 用户端:发起退款申请(订单维度;支持部分退款金额,凭证为已上传图片地址数组) */
export const refundApply = (p: {
  orderNum: string
  userNum: string
  refundMoney: string
  refundReason: string
  evidence: string[]
}) => post<{ refundOrderId: string }>('/refund/apply', { ...p } as unknown as Record<string, unknown>)
/** 用户端:我的退款记录(含进度状态) */
export const refundUserList = (p: Record<string, unknown> = {}) => post<Page<RefundOrder>>('/refund/user/list', p)
/** 退款进度详情(用户传 userNum / 商户传 shopNum 校验归属) */
export const refundDetail = (p: { refundOrderId: string; userNum?: string; shopNum?: string }) =>
  post<{ refund: RefundOrder }>('/refund/detail', p)
/** 上传退款凭证(压缩后的 base64 图片),返回可访问地址 */
export const refundEvidenceUpload = (imageBase64: string) =>
  post<{ url: string }>('/refund/evidence/upload', { imageBase64 })
/** 商户端:本商户退款单列表(支持状态筛选) */
export const refundMchList = (p: Record<string, unknown> = {}) => post<Page<RefundOrder>>('/refund/mch/list', p)
/** 商户端:审核退款(同意执行渠道退款/拒绝),登录令牌经请求头提交校验身份与归属 */
export const refundMchAudit = (
  refundOrderId: string,
  auditState: 1 | 2,
  auditRemark: string | undefined,
  operatorToken: string,
) =>
  post('/refund/mch/audit', { refundOrderId, auditState, auditRemark }, { headers: { 'X-Mch-Token': operatorToken } })
/** 商户端:退款统计概览(各状态笔数/金额) */
export const refundMchStats = (shopNum: string) => post<RefundStats>('/refund/mch/stats', { shopNum })

/* ---------- 商户 ---------- */
export const mchRegister = (p: {
  shopName: string
  shopPhone: string
  shopAddress: string
  username: string
  password: string
  captchaKey: string
  captchaCode: string
}) => post<{ shopNum: string }>('/mch/register', { ...p } as unknown as Record<string, unknown>)
export const mchLogin = (username: string, password: string) =>
  postFree<{ token: string; shopNum: string; shopState: 0 | 1 | 2 }>('/mch/login', { username, password })

/* ---------- 资金 ---------- */
export const fundOverview = (shopNum: string) => post<{ fund: FundOverview }>('/fund/overview', { shopNum })
export const withdrawCreate = (shopNum: string, money: string) =>
  post<{ withdrawNo: string }>('/fund/withdraw/create', { shopNum, money } as unknown as Record<string, unknown>)
export const withdrawList = (p: Record<string, unknown> = {}) => post<Page<Withdraw>>('/fund/withdraw/list', p)
export const withdrawAudit = (withdrawNo: string, state: 1 | 2, remark?: string) =>
  post('/admin/withdraw/audit', { withdrawNo, state, remark })
export const adminWithdrawList = (p: Record<string, unknown> = {}) => post<Page<Withdraw>>('/admin/withdraw/list', p)

/* ---------- 统计 ---------- */
export const statisPlatform = () =>
  post<{
    totalMoney: number
    totalCount: number
    channelRatio: ChannelRatio[]
    sevenDayData: DayPoint[]
  }>('/statis/platform', {})
export const statisMch = (shopNum: string, startTime?: string, endTime?: string) =>
  post<{
    totalIncome: number
    totalServiceFee: number
    actualIncome: number
    totalTradeCount: number
    successRate: string
    channelTradeData: ChannelTrade[]
    sevenDayTradeData: DayPoint[]
  }>('/statis/mch', { shopNum, startTime, endTime } as unknown as Record<string, unknown>)

/* ---------- 平台管理 ---------- */
export const adminLogin = (username: string, password: string) =>
  postFree<{ token: string; realName: string; role: 'ADMIN' | 'OPS' }>('/admin/login', { username, password })
export const adminMchList = (p: Record<string, unknown> = {}) => post<Page<Mch>>('/admin/mch/list', p)
export const adminMchAudit = (shopNum: string, shopState: 1 | 2, auditRemark?: string) =>
  post('/admin/mch/audit', { shopNum, shopState, auditRemark } as unknown as Record<string, unknown>)
export const adminMchRate = (shopNum: string, wayCode: string, mchRate: string) =>
  post('/admin/mch/rate', { shopNum, wayCode, mchRate } as unknown as Record<string, unknown>)
export const channelList = () => post<{ records: ChannelConfig[] }>('/admin/channel/list', {})
export const channelConfig = (p: Record<string, unknown>) => post('/admin/channel/config', p)
export const adminPayList = (p: Record<string, unknown> = {}) => post<Page<PayOrder>>('/admin/pay/list', p)

/* ---------- 管理端删除(仅 ADMIN,operatorToken 经请求头提交) ---------- */
export const adminMchDelete = (shopNum: string, operatorToken: string) =>
  post('/admin/mch/delete', { shopNum }, { headers: { 'X-Admin-Token': operatorToken } })
export const adminGoodsDelete = (goodsNum: string, operatorToken: string) =>
  post('/admin/goods/delete', { goodsNum }, { headers: { 'X-Admin-Token': operatorToken } })
export const adminPayDelete = (payOrderId: string, operatorToken: string) =>
  post('/admin/pay/delete', { payOrderId }, { headers: { 'X-Admin-Token': operatorToken } })
export const adminRefundDelete = (refundOrderId: string, operatorToken: string) =>
  post('/admin/refund/delete', { refundOrderId }, { headers: { 'X-Admin-Token': operatorToken } })
export const adminChannelDelete = (wayCode: string, operatorToken: string) =>
  post('/admin/channel/delete', { wayCode }, { headers: { 'X-Admin-Token': operatorToken } })
export const adminWithdrawDelete = (withdrawNo: string, operatorToken: string) =>
  post('/admin/withdraw/delete', { withdrawNo }, { headers: { 'X-Admin-Token': operatorToken } })
export const adminCategoryDelete = (categoryId: number, operatorToken: string) =>
  post('/admin/category/delete', { categoryId }, { headers: { 'X-Admin-Token': operatorToken } })
export const adminLogDelete = (id: number, operatorToken: string) =>
  post('/admin/log/delete', { id }, { headers: { 'X-Admin-Token': operatorToken } })
/** 上传/替换商品图片:仅 ADMIN,图片经前端压缩为 base64 提交,返回新的图片地址 */
export const adminGoodsPic = (goodsNum: string, imageBase64: string, operatorToken: string) =>
  post<{ picUrl: string }>('/admin/goods/pic', { goodsNum, imageBase64 }, { headers: { 'X-Admin-Token': operatorToken } })
/** 商户删除自有商品:仅商品归属商户可执行,被订单引用时禁止删除 */
export const mchGoodsDelete = (goodsNum: string, operatorToken: string) =>
  post('/goods/delete', { goodsNum }, { headers: { 'X-Mch-Token': operatorToken } })
export const adminLogList = (p: Record<string, unknown> = {}) => post<Page<OperateLog>>('/admin/log/list', p)
/* 平台商品管控 */
export const adminGoodsList = (p: Record<string, unknown> = {}) => post<Page<Goods>>('/admin/goods/list', p)
export const adminGoodsState = (goodsNum: string, goodsState: 0 | 1) =>
  post('/admin/goods/state', { goodsNum, goodsState })
export type { R }
