<template>
  <div class="cashier">
    <div class="head">
      <h2>Mall·X 收银台</h2>
      <p class="tip">超时未支付订单将自动关闭,请尽快完成付款。</p>
    </div>

    <div v-for="o in orders" :key="o.orderNum" class="mx-card card">
      <div class="row">
        <div>
          <div class="shop">{{ o.shopName }}</div>
          <div class="muted num">{{ o.orderNum }}</div>
        </div>
        <div class="amount price"><span class="yen">¥</span>{{ money(o.totalMoney) }}</div>
      </div>

      <template v-if="o.state === 'none'">
        <div class="payway">
          <span class="lbl">支付方式</span>
          <el-radio-group v-model="o.wayCode">
            <el-radio-button value="WECHAT">微信支付</el-radio-button>
            <el-radio-button value="ALIPAY">支付宝</el-radio-button>
            <el-radio-button value="UNIONPAY">云闪付</el-radio-button>
          </el-radio-group>
        </div>
        <div class="act">
          <el-button type="primary" size="large" :loading="o.loading" @click="pay(o)">立即支付</el-button>
        </div>
      </template>

      <template v-else-if="o.state === 'paying'">
        <el-alert type="info" :closable="false" show-icon title="已跳转支付,等待支付结果…">
          <template #default>
            <div class="paying-tip">
              支付完成后本页会自动确认。
              <el-link type="primary" @click="openCashier(o)">重新打开收银台</el-link>
              <el-link type="primary" style="margin-left: 8px" @click="simulateConfirm(o)">模拟支付成功</el-link>
            </div>
          </template>
        </el-alert>
        <div class="poll num">已查询 {{ o.polls }} 次</div>
      </template>

      <template v-else-if="o.state === 'paid'">
        <el-alert type="success" :closable="false" show-icon title="支付成功!" description="商户确认后开始发货,可在我的订单中查看进度。" />
      </template>

      <template v-else-if="o.state === 'fail'">
        <el-alert type="error" :closable="false" show-icon :title="o.error || '支付未完成'" />
        <div class="act">
          <el-button @click="o.state = 'none'">重新支付</el-button>
        </div>
      </template>
    </div>

    <div class="done-row" v-if="orders.length && orders.every((o) => o.state === 'paid')">
      <el-button type="primary" size="large" @click="$router.push('/my/orders')">查看我的订单</el-button>
      <el-button size="large" @click="$router.push('/')">继续购物</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { orderDetail, payCreate, payQuery } from '@/api/modules'
import { money } from '@/api/client'
import { useSessionStore } from '@/stores/session'

interface PayCtx {
  orderNum: string
  shopNum: string
  shopName: string
  subject: string
  totalMoney: number
  wayCode: string
  state: 'none' | 'paying' | 'paid' | 'fail'
  payOrderId?: string
  payUrl?: string
  loading: boolean
  polls: number
  error?: string
}

const route = useRoute()
const session = useSessionStore()

const orders = reactive<PayCtx[]>([])

const nums = String(route.query.orders || '').split(',').filter(Boolean)

onMounted(async () => {
  // 即时展示缓存(Checkout 写入);缺数据时以订单详情接口自愈,
  // 覆盖「我的订单 → 去支付」等无缓存进入路径
  let ctx: Record<string, { shopNum: string; shopName: string; subject: string; totalMoney: number }> = {}
  try {
    ctx = JSON.parse(sessionStorage.getItem('mallx_payctx') || '{}')
  } catch {
    ctx = {}
  }
  for (const n of nums) {
    const c = ctx[n]
    const entry = reactive<PayCtx>({
      orderNum: n,
      shopNum: c?.shopNum || '',
      shopName: c?.shopName || '',
      subject: c?.subject || '商品',
      totalMoney: c?.totalMoney ?? 0,
      wayCode: 'ALIPAY',
      state: 'none',
      loading: false,
      polls: 0,
    })
    orders.push(entry)
    if (!c?.totalMoney) {
      try {
        const d = await orderDetail(n)
        entry.shopNum = d.order.shopNum
        entry.totalMoney = d.order.totalMoney
        if (!entry.shopName) entry.shopName = d.order.shopNum
        if (d.order.items?.length) entry.subject = d.order.items.map((i) => i.goodsName).join('、').slice(0, 40)
      } catch {
        /* 详情获取失败保持现状,支付时由后端校验 */
      }
    }
  }
  if (orders.length === 0) ElMessage.warning('缺少订单信息')
})

let timer: number | undefined

function openCashier(o: PayCtx) {
  if (o.payUrl) window.open(o.payUrl, '_blank')
}

/** 真实环境里 payUrl 为后端模拟收银台(或支付宝沙箱);mock 环境下也可手动确认 */
async function simulateConfirm(o: PayCtx) {
  if (!o.payUrl) return
  // payUrl 指向 /api/pay/mock/cashier,确认地址为 /api/pay/mock/confirm
  const confirmUrl = o.payUrl.replace('/pay/mock/cashier', '/pay/mock/confirm')
  window.open(confirmUrl, '_blank')
}

async function pay(o: PayCtx) {
  // 必须先在用户点击的同步调用栈里开窗：await payCreate 之后再调用 window.open
  // 已脱离用户手势，会被浏览器弹窗拦截（表现为"已跳转支付"却没有收银台页面）。
  const win = window.open('', '_blank')
  o.loading = true
  try {
    const r = await payCreate({
      shopNum: o.shopNum,
      shopName: o.shopName,
      appId: 'mallx-app',
      wayCode: o.wayCode,
      payMoney: o.totalMoney.toFixed(2),
      subject: o.subject,
      userNum: session.user!.userNum,
      orderNum: o.orderNum,
    })
    o.payOrderId = r.payOrderId
    o.payUrl = r.payUrl
    o.state = 'paying'
    o.polls = 0
    if (win && o.payUrl) {
      // 复用同步打开的窗口，规避弹窗拦截
      win.location.href = o.payUrl
    } else {
      win?.close()
      openCashier(o)
    }
    startPolling()
  } catch (e) {
    win?.close()
    o.state = 'fail'
    o.error = e instanceof Error ? e.message : '下单失败'
  } finally {
    o.loading = false
  }
}

function startPolling() {
  if (timer) return
  timer = window.setInterval(async () => {
    const pending = orders.filter((o) => o.state === 'paying')
    if (pending.length === 0) {
      window.clearInterval(timer)
      timer = undefined
      return
    }
    for (const o of pending) {
      try {
        o.polls++
        // 支付单状态 4→0 即成功;同时兼容订单已被延迟关单(3)
        const r = await payQuery(o.shopNum, o.payOrderId!)
        if (r.orderState === 0) {
          o.state = 'paid'
        } else if (r.orderState === 3) {
          o.state = 'fail'
          o.error = '订单已超时关闭'
        }
      } catch {
        /* 轮询失败忽略,等待下轮 */
      }
    }
  }, 2000)
}

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.cashier {
  max-width: 620px;
  margin: 0 auto;
}

.head h2 {
  margin: 6px 0 4px;
}

.tip {
  color: var(--mx-ink-3);
  font-size: 13px;
  margin: 0 0 18px;
}

.card {
  padding: 18px 22px;
  margin-bottom: 14px;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 14px;
  border-bottom: 1px dashed var(--mx-line);
  margin-bottom: 14px;
}

.shop {
  font-weight: 600;
  margin-bottom: 4px;
}

.muted {
  color: var(--mx-ink-3);
  font-size: 12px;
}

.amount {
  font-size: 28px;
}

.payway {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.lbl {
  color: var(--mx-ink-2);
  font-size: 13px;
}

.act {
  text-align: right;
}

.paying-tip {
  display: flex;
  align-items: center;
  gap: 8px;
}

.poll {
  color: var(--mx-ink-3);
  font-size: 12px;
  text-align: right;
  margin-top: 8px;
}

.done-row {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
}
</style>
