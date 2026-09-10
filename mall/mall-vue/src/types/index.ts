// Mall-X 后端接口类型(依据 mall-mgr/mall-alipay Controller 契约)

/** 统一响应:业务字段平铺顶层,id="0" 为成功 */
export interface R {
  id: string
  message: string
  [key: string]: unknown
}

/** 分页响应(records 平铺顶层) */
export interface Page<T> {
  id: string
  message: string
  total: number
  pages: number
  current: number
  size: number
  records: T[]
}

export interface Goods {
  id: number
  goodsNum: string
  goodsName: string
  shopNum: string
  shopName?: string
  categoryId: number
  price: number
  stock: number
  sales: number
  goodsState: 0 | 1
  picUrl: string | null
  detail: string | null
  createdAt: string
  updatedAt: string
}

export interface Category {
  id: number
  categoryName: string
  parentId: number
  sort: number
}

export interface OrderItem {
  id: number
  orderNum: string
  goodsNum: string
  goodsName: string
  goodsPrice: number
  goodsCount: number
  totalMoney: number
}

/** 业务订单状态:0-待支付 1-已支付待发货 2-已发货 3-已完成 4-已关闭 */
export interface BizOrder {
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
  items?: OrderItem[]
}

/** 支付订单状态:0-支付成功 1-支付失败 2-已退款 3-订单关闭 4-待支付 */
export interface PayOrder {
  payOrderId: string
  mchOrderNo: string
  shopNum?: string
  shopName?: string
  appId?: string
  wayCode: WayCode
  money: number
  mchFee?: number
  subject: string
  userNum?: string
  orderState: 0 | 1 | 2 | 3 | 4
  channelOrderNo?: string | null
  payTime: string | null
  expireTime?: string | null
  createdAt: string
  updatedAt?: string
}

export type WayCode = 'WECHAT' | 'ALIPAY' | 'UNIONPAY'

/** 退款单:0-审核中 1-退款成功 2-退款失败 */
export interface RefundOrder {
  refundOrderId: string
  payOrderId: string
  shopNum: string
  refundMoney: number
  refundReason: string
  refundState: 0 | 1 | 2
  channelRefundNo: string | null
  auditRemark: string | null
  refundTime: string | null
  createdAt: string
}

export interface Mch {
  shopNum: string
  shopName: string
  shopPhone: string
  shopAddress: string
  username: string
  shopState: 0 | 1 | 2
  auditRemark: string | null
  balance: number
  createdAt: string
}

export interface FundOverview {
  totalIncome: number
  totalServiceFee: number
  actualIncome: number
  refunded: number
  withdrawn: number
  balance: number
}

export interface Withdraw {
  withdrawNo: string
  shopNum: string
  money: number
  state: 0 | 1 | 2
  remark: string | null
  finishTime: string | null
  createdAt: string
}

export interface ChannelConfig {
  wayCode: WayCode
  channelName: string
  switchStatus: 0 | 1
  baseRate: number
  appId: string | null
  gatewayUrl: string | null
  notifyKey: string | null
  createdAt: string
}

export interface AdminUser {
  username: string
  realName: string
  role: 'ADMIN' | 'OPS'
  state: 0 | 1
  createdAt: string
}

export interface OperateLog {
  module: string
  content: string
  operator: string
  ip: string
  createdAt: string
}

export interface DayPoint {
  date: string
  tradeMoney: number
  tradeCount: number
}

export interface ChannelRatio {
  wayCode: string
  count: number
  tradeMoney: number
  ratio: string
}

export interface ChannelTrade {
  wayCode: string
  tradeMoney: number
  tradeCount: number
  fee?: number
}
