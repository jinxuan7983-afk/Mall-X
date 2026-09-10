<template>
  <div>
    <h2 class="mx-page-title">我的订单</h2>

    <el-tabs v-model="tab" class="mx-card tabs" @tab-change="load">
      <el-tab-pane v-for="t in tabs" :key="String(t.value)" :label="t.label" :name="String(t.value)" />
    </el-tabs>

    <div v-loading="loading" style="min-height: 200px">
      <div v-if="records.length === 0 && !loading" class="mx-empty mx-card">暂无订单</div>

      <div v-for="o in records" :key="o.orderNum" class="mx-card order">
        <div class="ohead">
          <span class="num muted">{{ o.orderNum }}</span>
          <span class="muted num">店铺 {{ o.shopNum }}</span>
          <StateTag kind="biz" :value="o.orderState" />
          <span class="muted num">{{ o.createdAt }}</span>
        </div>
        <div class="obody">
          <div class="items">
            <div v-for="it in o.items" :key="it.goodsNum" class="it">
              <GoodsPic :name="it.goodsName" :seed="it.goodsNum" />
              <span class="nm">{{ it.goodsName }}</span>
              <span class="muted num">x{{ it.goodsCount }}</span>
            </div>
          </div>
          <div class="side">
            <div class="price total"><span class="yen">¥</span>{{ money(o.totalMoney) }}</div>
            <div class="ops">
              <el-button v-if="o.orderState === 0" type="primary" size="small" @click="toPay(o.orderNum)">去支付</el-button>
              <el-button v-if="o.orderState === 2" type="primary" size="small" @click="confirm(o.orderNum)">确认收货</el-button>
              <el-button size="small" @click="showDetail(o.orderNum)">订单详情</el-button>
            </div>
          </div>
        </div>
        <div v-if="o.orderState === 2" class="logi">
          {{ o.logisticsCompany }} {{ o.logisticsNo }}
        </div>
      </div>
    </div>

    <div class="pager" v-if="total > pageSize">
      <el-pagination background layout="prev, pager, next" :total="total" :page-size="pageSize" :current-page="page" @current-change="(p: number) => { page = p; load() }" />
    </div>

    <el-drawer v-model="drawer" title="订单详情" size="460px">
      <el-descriptions v-if="detail" :column="1" border size="small">
        <el-descriptions-item label="订单号"><span class="num">{{ detail.orderNum }}</span></el-descriptions-item>
        <el-descriptions-item label="状态"><StateTag kind="biz" :value="detail.orderState" /></el-descriptions-item>
        <el-descriptions-item label="金额"><span class="price">¥{{ money(detail.totalMoney) }}</span></el-descriptions-item>
        <el-descriptions-item label="收货人">{{ detail.receiverName }} {{ detail.receiverPhone }}</el-descriptions-item>
        <el-descriptions-item label="地址">{{ detail.receiverAddress }}</el-descriptions-item>
        <el-descriptions-item label="物流">{{ detail.logisticsCompany ? `${detail.logisticsCompany} ${detail.logisticsNo}` : '—' }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ fmtTime(detail.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ fmtTime(detail.payTime) }}</el-descriptions-item>
        <el-descriptions-item label="发货时间">{{ fmtTime(detail.deliverTime) }}</el-descriptions-item>
        <el-descriptions-item label="收货时间">{{ fmtTime(detail.receiveTime) }}</el-descriptions-item>
      </el-descriptions>
      <h4 style="margin: 16px 0 8px">商品明细</h4>
      <el-table v-if="detail" :data="detail.items" size="small">
        <el-table-column prop="goodsName" label="商品" />
        <el-table-column prop="goodsCount" label="数量" width="64" />
        <el-table-column label="小计" width="100" align="right">
          <template #default="{ row }">¥{{ money(row.totalMoney) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderConfirm, orderDetail, orderList } from '@/api/modules'
import { fmtTime, money } from '@/api/client'
import { useSessionStore } from '@/stores/session'
import type { BizOrder, OrderItem } from '@/types'
import GoodsPic from '@/components/GoodsPic.vue'
import StateTag from '@/components/StateTag.vue'

interface ListOrder extends BizOrder {
  items: OrderItem[]
  shopName?: string
}

const router = useRouter()
const session = useSessionStore()

const tabs = [
  { label: '全部', value: -1 },
  { label: '待支付', value: 0 },
  { label: '待发货', value: 1 },
  { label: '已发货', value: 2 },
  { label: '已完成', value: 3 },
  { label: '已关闭', value: 4 },
]
const tab = ref('-1')
const records = ref<ListOrder[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = 10
const total = ref(0)
const drawer = ref(false)
const detail = ref<(BizOrder & { items: OrderItem[] }) | null>(null)

async function load() {
  loading.value = true
  try {
    const r = await orderList({
      userNum: session.user!.userNum,
      pageNum: page.value,
      pageSize,
      orderState: tab.value === '-1' ? undefined : Number(tab.value),
    } as never)
    // 列表不含明细,并行补充商品信息用于展示
    const withItems = await Promise.all(
      r.records.map(async (o) => {
        try {
          const d = await orderDetail(o.orderNum)
          return { ...o, items: d.order.items || [] }
        } catch {
          return { ...o, items: [] }
        }
      }),
    )
    records.value = withItems
    total.value = r.total
  } finally {
    loading.value = false
  }
}

function toPay(orderNum: string) {
  router.push({ name: 'cashier', query: { orders: orderNum } })
}

async function confirm(orderNum: string) {
  await ElMessageBox.confirm('确认已收到货?', '确认收货', { type: 'warning' })
  await orderConfirm(orderNum)
  ElMessage.success('确认收货成功')
  load()
}

async function showDetail(orderNum: string) {
  const r = await orderDetail(orderNum)
  detail.value = r.order
  drawer.value = true
}

onMounted(load)
</script>

<style scoped>
.tabs {
  padding: 4px 16px 0;
  margin-bottom: 14px;
}

.order {
  margin-bottom: 12px;
  overflow: hidden;
}

.ohead {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 18px;
  border-bottom: 1px solid var(--mx-line);
  background: #fcfbf9;
  font-size: 13px;
}

.muted {
  color: var(--mx-ink-3);
}

.obody {
  display: flex;
  align-items: stretch;
}

.items {
  flex: 1;
  border-right: 1px dashed var(--mx-line);
}

.it {
  display: grid;
  grid-template-columns: 52px 1fr 50px;
  gap: 12px;
  align-items: center;
  padding: 10px 18px;
}

.it :deep(.gp) {
  width: 52px;
  height: 52px;
  border-radius: var(--mx-radius);
  overflow: hidden;
}

.it .nm {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side {
  width: 220px;
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: flex-end;
  gap: 10px;
}

.total {
  font-size: 22px;
}

.logi {
  padding: 8px 18px;
  background: #fcfbf9;
  border-top: 1px dashed var(--mx-line);
  color: var(--mx-ink-2);
  font-size: 12px;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
