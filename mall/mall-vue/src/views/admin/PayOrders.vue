<template>
  <div>
    <div class="mx-toolbar">
      <el-input v-model="filters.shopNum" placeholder="商户编号" style="width: 200px" clearable />
      <el-select v-model="filters.orderState" placeholder="订单状态" clearable style="width: 130px">
        <el-option v-for="(t, v) in payStates" :key="v" :label="t" :value="Number(v)" />
      </el-select>
      <el-select v-model="filters.wayCode" placeholder="支付渠道" clearable style="width: 130px">
        <el-option label="微信支付" value="WECHAT" />
        <el-option label="支付宝" value="ALIPAY" />
        <el-option label="云闪付" value="UNIONPAY" />
      </el-select>
      <el-date-picker
        v-model="range"
        type="datetimerange"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        value-format="YYYY-MM-DD HH:mm:ss"
      />
      <el-button type="primary" :icon="Search" @click="load">查询</el-button>
      <span class="tip">不选时间默认查询近 7 天。</span>
    </div>

    <el-table :data="records" v-loading="loading" border stripe>
      <el-table-column label="支付订单号" min-width="180">
        <template #default="{ row }"><span class="small num">{{ row.payOrderId }}</span></template>
      </el-table-column>
      <el-table-column label="商户" min-width="140">
        <template #default="{ row }">
          <div>{{ row.shopName }}</div>
          <div class="muted num">{{ row.shopNum }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="subject" label="商品" min-width="140" show-overflow-tooltip />
      <el-table-column label="渠道" width="90" align="center">
        <template #default="{ row }">{{ wayText(row.wayCode) }}</template>
      </el-table-column>
      <el-table-column label="金额" width="110" align="right">
        <template #default="{ row }"><span class="price">¥{{ money(row.money) }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="96" align="center">
        <template #default="{ row }"><StateTag kind="pay" :value="row.orderState" /></template>
      </el-table-column>
      <el-table-column label="支付时间" width="170">
        <template #default="{ row }"><span class="small num">{{ fmtTime(row.payTime) }}</span></template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }"><span class="small num">{{ fmtTime(row.createdAt) }}</span></template>
      </el-table-column>
      <el-table-column v-if="canDelete" label="操作" width="90" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination background layout="total, prev, pager, next" :total="total" :page-size="pageSize" :current-page="page" @current-change="(p: number) => { page = p; load() }" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { adminPayDelete, adminPayList } from '@/api/modules'
import { fmtTime, money, wayCodeText } from '@/api/client'
import { useAdminDelete } from '@/composables/useAdminDelete'
import type { PayOrder } from '@/types'
import StateTag from '@/components/StateTag.vue'

const payStates: Record<number, string> = { 0: '支付成功', 1: '支付失败', 2: '已退款', 3: '订单关闭', 4: '待支付' }
const records = ref<PayOrder[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = 10
const total = ref(0)
const range = ref<[string, string] | null>(null)
const filters = reactive<{ shopNum: string; orderState: number | undefined; wayCode: string | undefined }>({
  shopNum: '',
  orderState: undefined,
  wayCode: undefined,
})

const wayText = (w: string) => wayCodeText[w] || w

const { canDelete, adminDelete } = useAdminDelete()

/** 删除支付订单:仅失败/关闭/待支付可删,成功与已退款涉及资金流向不可删(后端校验兜底) */
async function remove(row: PayOrder) {
  const ok = await adminDelete(
    `支付订单「${row.payOrderId}」`,
    '仅支付失败/订单关闭/待支付的订单可删除,支付成功与已退款订单涉及资金流向不可删除。',
    (t) => adminPayDelete(row.payOrderId, t),
    `支付订单「${row.payOrderId}」已删除`,
  )
  if (ok) {
    if (records.value.length === 1 && page.value > 1) page.value -= 1
    load()
  }
}

async function load() {
  loading.value = true
  try {
    const r = await adminPayList({
      pageNum: page.value,
      pageSize,
      shopNum: filters.shopNum || undefined,
      orderState: filters.orderState,
      wayCode: filters.wayCode,
      startTime: range.value?.[0],
      endTime: range.value?.[1],
    })
    records.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
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

.muted {
  color: var(--mx-ink-3);
  font-size: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
