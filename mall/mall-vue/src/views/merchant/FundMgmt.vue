<template>
  <div>
    <el-row :gutter="14">
      <el-col :span="4" v-for="c in cards" :key="c.label">
        <div class="mx-card mx-stat">
          <div class="label">{{ c.label }}</div>
          <div class="value" :class="{ price: c.price }">{{ c.value }}</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="mx-card mx-stat wd">
          <div class="label">申请提现</div>
          <el-button type="primary" style="width: 100%; margin-top: 8px" @click="wdDialog = true">提现</el-button>
        </div>
      </el-col>
    </el-row>

    <h3 class="sub-title">提现记录</h3>
    <el-table :data="records" v-loading="loading" border stripe>
      <el-table-column label="提现单号" min-width="190">
        <template #default="{ row }"><span class="small num">{{ row.withdrawNo }}</span></template>
      </el-table-column>
      <el-table-column label="金额" width="120" align="right">
        <template #default="{ row }"><span class="price">¥{{ money(row.money) }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }"><StateTag kind="withdraw" :value="row.state" /></template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" />
      <el-table-column label="完成时间" width="170">
        <template #default="{ row }"><span class="small num">{{ fmtTime(row.finishTime) }}</span></template>
      </el-table-column>
      <el-table-column label="申请时间" width="170">
        <template #default="{ row }"><span class="small num">{{ fmtTime(row.createdAt) }}</span></template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination background layout="total, prev, pager, next" :total="total" :page-size="pageSize" :current-page="page" @current-change="(p: number) => { page = p; loadList() }" />
    </div>

    <el-dialog v-model="wdDialog" title="申请提现" width="420px">
      <el-form label-width="84px">
        <el-form-item label="可提余额"><span class="price">¥{{ money(balance) }}</span></el-form-item>
        <el-form-item label="提现金额" required>
          <el-input v-model="wdMoney" type="number" placeholder="不超过可提现余额" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="wdDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitWd">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fundOverview, withdrawCreate, withdrawList } from '@/api/modules'
import { fmtTime, money } from '@/api/client'
import { useSessionStore } from '@/stores/session'
import type { FundOverview, Withdraw } from '@/types'
import StateTag from '@/components/StateTag.vue'

const session = useSessionStore()

const fund = ref<FundOverview | null>(null)
const records = ref<Withdraw[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = 10
const total = ref(0)
const wdDialog = ref(false)
const saving = ref(false)
const wdMoney = ref('')

const cards = ref([
  { label: '累计营收(元)', value: '—', price: true },
  { label: '累计手续费(元)', value: '—', price: false },
  { label: '净收益(元)', value: '—', price: true },
  { label: '累计退款(元)', value: '—', price: false },
  { label: '已提现(元)', value: '—', price: false },
  { label: '可提余额(元)', value: '—', price: true },
])

const balance = ref(0)

async function loadFund() {
  const r = await fundOverview(session.mch!.shopNum)
  fund.value = r.fund
  balance.value = r.fund.balance
  cards.value = [
    { label: '累计营收(元)', value: money(r.fund.totalIncome), price: true },
    { label: '累计手续费(元)', value: money(r.fund.totalServiceFee), price: false },
    { label: '净收益(元)', value: money(r.fund.actualIncome), price: true },
    { label: '累计退款(元)', value: money(r.fund.refunded), price: false },
    { label: '已提现(元)', value: money(r.fund.withdrawn), price: false },
    { label: '可提余额(元)', value: money(r.fund.balance), price: true },
  ]
}

async function loadList() {
  loading.value = true
  try {
    const r = await withdrawList({ shopNum: session.mch!.shopNum, pageNum: page.value, pageSize })
    records.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function submitWd() {
  if (!wdMoney.value) {
    ElMessage.warning('请填写提现金额')
    return
  }
  saving.value = true
  try {
    const r = await withdrawCreate(session.mch!.shopNum, wdMoney.value)
    ElMessage.success(`提现申请已提交(${r.withdrawNo}),等待平台审核`)
    wdDialog.value = false
    wdMoney.value = ''
    await Promise.all([loadFund(), loadList()])
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadFund().catch(() => {})
  loadList()
})
</script>

<style scoped>
.sub-title {
  margin: 20px 0 12px;
  font-size: 15px;
}

.wd .el-button {
  margin-left: 0;
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
