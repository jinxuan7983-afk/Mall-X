import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from '@/stores/session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    /* ---------- 用户商城 ---------- */
    {
      path: '/',
      component: () => import('@/layouts/StoreLayout.vue'),
      children: [
        { path: '', name: 'home', component: () => import('@/views/storefront/Home.vue') },
        { path: 'goods/:goodsNum', name: 'goodsDetail', component: () => import('@/views/storefront/GoodsDetail.vue') },
        { path: 'cart', name: 'cart', component: () => import('@/views/storefront/Cart.vue') },
        { path: 'checkout', name: 'checkout', component: () => import('@/views/storefront/Checkout.vue') },
        { path: 'cashier', name: 'cashier', component: () => import('@/views/storefront/Cashier.vue') },
        { path: 'my/orders', name: 'myOrders', component: () => import('@/views/storefront/MyOrders.vue') },
        { path: 'user/login', name: 'userLogin', component: () => import('@/views/storefront/UserLogin.vue') },
      ],
    },
    /* ---------- 商户端 ---------- */
    { path: '/mch/login', name: 'mchLogin', component: () => import('@/views/merchant/MchLogin.vue') },
    {
      path: '/mch',
      component: () => import('@/layouts/ConsoleLayout.vue'),
      meta: { role: 'mch' },
      children: [
        { path: '', name: 'mchHome', component: () => import('@/views/merchant/Dashboard.vue'), meta: { title: '工作台' } },
        { path: 'goods', name: 'mchGoods', component: () => import('@/views/merchant/GoodsMgmt.vue'), meta: { title: '商品管理' } },
        { path: 'orders', name: 'mchOrders', component: () => import('@/views/merchant/OrderMgmt.vue'), meta: { title: '订单管理' } },
        { path: 'trade', name: 'mchTrade', component: () => import('@/views/merchant/TradeFlow.vue'), meta: { title: '交易流水' } },
        { path: 'refund', name: 'mchRefund', component: () => import('@/views/merchant/RefundMgmt.vue'), meta: { title: '退款管理' } },
        { path: 'fund', name: 'mchFund', component: () => import('@/views/merchant/FundMgmt.vue'), meta: { title: '资金管理' } },
        { path: 'stats', name: 'mchStats', component: () => import('@/views/merchant/MchStats.vue'), meta: { title: '店铺统计' } },
      ],
    },
    /* ---------- 平台管理端 ---------- */
    { path: '/admin/login', name: 'adminLogin', component: () => import('@/views/admin/AdminLogin.vue') },
    {
      path: '/admin',
      component: () => import('@/layouts/ConsoleLayout.vue'),
      meta: { role: 'admin' },
      children: [
        { path: '', name: 'adminHome', component: () => import('@/views/admin/Dashboard.vue'), meta: { title: '数据看板' } },
        { path: 'merchants', name: 'adminMch', component: () => import('@/views/admin/MchAudit.vue'), meta: { title: '商户审核' } },
        { path: 'goods', name: 'adminGoods', component: () => import('@/views/admin/GoodsAudit.vue'), meta: { title: '商品管理' } },
        { path: 'pay', name: 'adminPay', component: () => import('@/views/admin/PayOrders.vue'), meta: { title: '支付订单' } },
        { path: 'refunds', name: 'adminRefund', component: () => import('@/views/admin/RefundAudit.vue'), meta: { title: '退款审核' } },
        { path: 'channels', name: 'adminChannel', component: () => import('@/views/admin/ChannelConfig.vue'), meta: { title: '渠道配置' } },
        { path: 'withdraws', name: 'adminWithdraw', component: () => import('@/views/admin/WithdrawAudit.vue'), meta: { title: '提现审核' } },
        { path: 'categories', name: 'adminCategory', component: () => import('@/views/admin/CategoryMgmt.vue'), meta: { title: '类目管理' } },
        { path: 'logs', name: 'adminLogs', component: () => import('@/views/admin/OpLogs.vue'), meta: { title: '操作日志' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  const s = useSessionStore()
  if (to.meta.role === 'mch' && !s.isMch) {
    return { name: 'mchLogin', query: { redirect: to.fullPath } }
  }
  if (to.meta.role === 'admin' && !s.isAdmin) {
    return { name: 'adminLogin', query: { redirect: to.fullPath } }
  }
  // 下单、收银台与我的订单需要用户登录(购物车支持离线,不要求登录)
  if ((to.name === 'checkout' || to.name === 'cashier' || to.name === 'myOrders') && !s.isUser) {
    return { name: 'userLogin', query: { redirect: to.fullPath } }
  }
})

export default router
