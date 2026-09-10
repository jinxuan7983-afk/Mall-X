import { get, post, postFree } from './client'
import type {
  AdminUser,
  BizOrder,
  Category,
  ChannelConfig,
  FundOverview,
  Goods,
  Mch,
  OperateLog,
  Page,
  PayOrder,
  RefundOrder,
  R,
  Withdraw,
} from '@/types'

/* ---------- 用户 ---------- */
export const userRegister = (username: string, password: string) =>
  post<{ userNum: string }>('/user/register', { username, password })
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

/* ---------- 退款 ---------- */
export const refundCreate = (p: { payOrderId: string; refundMoney: string; refundReason: string; shopNum: string }) =>
  post<{ refundOrderId: string }>('/refund/create', { ...p } as unknown as Record<string, unknown>)
export const refundQuery = (refundOrderId: string, shopNum: string) =>
  post<RefundOrder>('/refund/query', { refundOrderId, shopNum })
export const refundAudit = (refundOrderId: string, auditState: 1 | 2, auditRemark?: string) =>
  post('/admin/refund/audit', { refundOrderId, auditState, auditRemark })
export const refundList = (p: Record<string, unknown> = {}) => post<Page<RefundOrder>>('/admin/refund/list', p)

/* ---------- 商户 ---------- */
export const mchRegister = (p: { shopName: string; shopPhone: string; shopAddress: string; username: string; password: string }) =>
  post<{ shopNum: string }>('/mch/register', { ...p } as unknown as Record<string, unknown>)
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
    channelRatio: { wayCode: string; count: number; tradeMoney: number; ratio: string }[]
    sevenDayData: { date: string; tradeMoney: number; tradeCount: number }[]
  }>('/statis/platform', {})
export const statisMch = (shopNum: string, startTime?: string, endTime?: string) =>
  post<{
    totalIncome: number
    totalServiceFee: number
    actualIncome: number
    totalTradeCount: number
    successRate: string
    channelTradeData: { wayCode: string; tradeMoney: number; tradeCount: number; fee?: number }[]
    sevenDayTradeData: { date: string; tradeMoney: number; tradeCount: number }[]
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
export const adminUserCreate = (p: { username: string; password: string; realName: string; role?: string }) =>
  post('/admin/user/create', { ...p } as unknown as Record<string, unknown>)
export const adminUserList = (p: Record<string, unknown> = {}) => post<Page<AdminUser>>('/admin/user/list', p)
export const adminLogList = (p: Record<string, unknown> = {}) => post<Page<OperateLog>>('/admin/log/list', p)
export type { R }
