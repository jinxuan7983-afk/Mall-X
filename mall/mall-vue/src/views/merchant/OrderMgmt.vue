<template>
  <div>
    <div class="mx-toolbar">
      <el-select v-model="stateFilter" placeholder="全部状态" clearable style="width: 140px" @change="load">
        <el-option v-for="(t, v) in bizStates" :key="v" :label="t" :value="Number(v)" />
      </el-select>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
      <span class="tip">支付成功后订单由消息队列异步驱动为「待发货」,有数秒延迟属正常。</span>
    </div>

    <el-table :data="records" v-loading="loading" border stripe>
      <el-table-column label="订单号" min-width="180">
        <template #default="{ row }"><span class="num small">{{ row.orderNum }}</span></template>
      </el-table-column>
      <el-table-column label="金额" width="110" align="right">
        <template #default="{ row }"><span class="price">¥{{ money(row.totalMoney) }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }"><StateTag kind="biz" :value="row.orderState" /></template>
      </el-table-column>
      <el-table-column prop="receiverName" label="收货人" width="90" />
      <el-table-column prop="receiverPhone" label="电话" width="120">
        <template #default="{ row }"><span class="num">{{ row.receiverPhone }}</span></template>
      </el-table-column>
      <el-table-column prop="receiverAddress" label="地址" min-width="180" show-overflow-tooltip />
      <el-table-column label="下单时间" width="160">
        <template #default="{ row }"><span class="num small">{{ row.createdAt }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center">
        <template #default="{ row }">
          <el-button v-if="row.orderState === 1" link type="primary" @click="openDeliver(row)">发货</el-button>
          <el-button link @click="showDetail(row.orderNum)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination background layout="total, prev, pager, next" :total="total" :page-size="pageSize" :current-page="page" @current-change="(p: number) => { page = p; load() }" />
    </div>

    <el-dialog v-model="deliverDialog" title="订单发货" width="440px">
      <el-form label-width="84px">
        <el-form-item label="物流公司" required>
          <el-select v-model="deliverForm.logisticsCompany" style="width: 100%" placeholder="选择物流公司">
            <el-option v-for="c in ['顺丰速运', '中通快递', '圆通速递', '韵达快递', '京东物流']" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="物流单号" required>
          <el-input v-model="deliverForm.logisticsNo" placeholder="物流运单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deliverDialog = false">取消</el-button>
        <el-button type="primary" :loading="delivering" @click="deliver">确认发货</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawer" title="订单详情" size="480px">
      <el-descriptions v-if="detail" :column="1" border size="small">
        <el-descriptions-item label="订单号"><span class="num">{{ detail.orderNum }}</span></el-descriptions-item>
        <el-descriptions-item label="状态"><StateTag kind="biz" :value="detail.orderState" /></el-descriptions-item>
        <el-descriptions-item label="金额"><span class="price">¥{{ money(detail.totalMoney) }}</span></el-descriptions-item>
        <el-descriptions-item label="支付单号"><span class="num">{{ detail.payOrderId || '—' }}</span></el-descriptions-item>
        <el-descriptions-item label="收货信息">{{ detail.receiverName }} {{ detail.receiverPhone }}<br />{{ detail.receiverAddress }}</el-descriptions-item>
        <el-descriptions-item label="物流">{{ detail.logisticsCompany ? `${detail.logisticsCompany} ${detail.logisticsNo}` : '—' }}</el-descriptions-item>
        <el-descriptions-item label="时间线">
          下单 {{ fmtTime(detail.createdAt) }}<br />
          支付 {{ fmtTime(detail.payTime) }}<br />
          发货 {{ fmtTime(detail.deliverTime) }}<br />
          收货 {{ fmtTime(detail.receiveTime) }}
        </el-descriptions-item>
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { orderDeliver, orderDetail, orderList } from '@/api/modules'
import { fmtTime, money } from '@/api/client'
import { useSessionStore } from '@/stores/session'
import type { BizOrder, OrderItem } from '@/types'
import StateTag from '@/components/StateTag.vue'

const session = useSessionStore()

const bizStates: Record<number, string> = { 0: '待支付', 1: '待发货', 2: '已发货', 3: '已完成', 4: '已关闭' }
const records = ref<BizOrder[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = 10
const total = ref(0)
const stateFilter = ref<number | undefined>()

const deliverDialog = ref(false)
const delivering = ref(false)
const deliverForm = reactive({ orderNum: '', logisticsCompany: '', logisticsNo: '' })

const drawer = ref(false)
const detail = ref<(BizOrder & { items: OrderItem[] }) | null>(null)

async function load() {
  loading.value = true
  try {
    const r = await orderList({
      shopNum: session.mch!.shopNum,
      pageNum: page.value,
      pageSize,
      orderState: stateFilter.value,
    } as never)
    records.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

function openDeliver(row: BizOrder) {
  Object.assign(deliverForm, { orderNum: row.orderNum, logisticsCompany: '', logisticsNo: '' })
  deliverDialog.value = true
}

async function deliver() {
  if (!deliverForm.logisticsCompany || !deliverForm.logisticsNo) {
    ElMessage.warning('请填写物流公司与单号')
    return
  }
  delivering.value = true
  try {
    await orderDeliver(deliverForm.orderNum, deliverForm.logisticsCompany, deliverForm.logisticsNo)
    ElMessage.success('发货成功')
    deliverDialog.value = false
    load()
  } finally {
    delivering.value = false
  }
}

async function showDetail(orderNum: string) {
  const r = await orderDetail(orderNum)
  detail.value = r.order
  drawer.value = true
}

onMounted(load)
</script>

<style scoped>
.tip {
  color: var(--mx-ink-3);
  font-size: 12px;
}

.small {
  font-size: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
