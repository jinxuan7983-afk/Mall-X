<template>
  <div>
    <div class="mx-toolbar">
      <el-select v-model="stateFilter" placeholder="全部状态" clearable style="width: 140px" @change="load">
        <el-option v-for="(t, v) in payStates" :key="v" :label="t" :value="Number(v)" />
      </el-select>
      <el-select v-model="wayFilter" placeholder="全部渠道" clearable style="width: 140px" @change="load">
        <el-option label="微信支付" value="WECHAT" />
        <el-option label="支付宝" value="ALIPAY" />
        <el-option label="云闪付" value="UNIONPAY" />
      </el-select>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
      <span class="tip">支持按状态、支付方式筛选交易订单,用于对账溯源。</span>
    </div>

    <el-table :data="records" v-loading="loading" border stripe>
      <el-table-column label="支付订单号" min-width="180">
        <template #default="{ row }"><span class="small num">{{ row.payOrderId }}</span></template>
      </el-table-column>
      <el-table-column label="商户订单号" min-width="180">
        <template #default="{ row }"><span class="small num">{{ row.mchOrderNo }}</span></template>
      </el-table-column>
      <el-table-column prop="subject" label="商品" min-width="140" show-overflow-tooltip />
      <el-table-column label="渠道" width="90" align="center">
        <template #default="{ row }">{{ wayText(row.wayCode) }}</template>
      </el-table-column>
      <el-table-column label="金额" width="110" align="right">
        <template #default="{ row }"><span class="price">¥{{ money(row.money) }}</span></template>
      </el-table-column>
      <el-table-column label="手续费" width="100" align="right">
        <template #default="{ row }"><span class="num">¥{{ money(row.mchFee) }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="96" align="center">
        <template #default="{ row }"><StateTag kind="pay" :value="row.orderState" /></template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }"><span class="small num">{{ row.createdAt }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="90" align="center">
        <template #default="{ row }">
          <el-button v-if="row.orderState === 0" link type="warning" @click="openRefund(row)">退款</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination background layout="total, prev, pager, next" :total="total" :page-size="pageSize" :current-page="page" @current-change="(p: number) => { page = p; load() }" />
    </div>

    <el-dialog v-model="refundDialog" title="发起退款" width="460px">
      <el-form label-width="84px">
        <el-form-item label="支付订单"><span class="num">{{ refundForm.payOrderId }}</span></el-form-item>
        <el-form-item label="可退金额"><span class="price">¥{{ money(refundable) }}</span></el-form-item>
        <el-form-item label="退款金额" required>
          <el-input v-model="refundForm.refundMoney" type="number" placeholder="支持部分退款" />
        </el-form-item>
        <el-form-item label="退款原因" required>
          <el-input v-model="refundForm.refundReason" type="textarea" :rows="2" placeholder="退款原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundDialog = false">取消</el-button>
        <el-button type="primary" :loading="refunding" @click="submitRefund">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { payListMch, refundCreate } from '@/api/modules'
import { money, wayCodeText } from '@/api/client'
import { useSessionStore } from '@/stores/session'
import type { PayOrder } from '@/types'
import StateTag from '@/components/StateTag.vue'

const session = useSessionStore()

const payStates: Record<number, string> = { 0: '支付成功', 1: '支付失败', 2: '已退款', 3: '订单关闭', 4: '待支付' }
const records = ref<PayOrder[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = 10
const total = ref(0)
const stateFilter = ref<number | undefined>()
const wayFilter = ref<string | undefined>()

const refundDialog = ref(false)
const refunding = ref(false)
const refundable = ref(0)
const refundForm = reactive({ payOrderId: '', refundMoney: '', refundReason: '' })

const wayText = (w: string) => wayCodeText[w] || w

async function load() {
  loading.value = true
  try {
    const r = await payListMch({
      shopNum: session.mch!.shopNum,
      pageNum: page.value,
      pageSize,
      orderState: stateFilter.value,
      wayCode: wayFilter.value,
    })
    records.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

function openRefund(row: PayOrder) {
  // 可退金额 = 支付金额( mock 与后端均按整单口径校验,这里以订单金额为上限提示)
  refundable.value = row.money
  Object.assign(refundForm, { payOrderId: row.payOrderId, refundMoney: String(row.money), refundReason: '' })
  refundDialog.value = true
}

async function submitRefund() {
  if (!refundForm.refundMoney || !refundForm.refundReason) {
    ElMessage.warning('请填写退款金额与原因')
    return
  }
  refunding.value = true
  try {
    const r = await refundCreate({
      payOrderId: refundForm.payOrderId,
      refundMoney: refundForm.refundMoney,
      refundReason: refundForm.refundReason,
      shopNum: session.mch!.shopNum,
    })
    ElMessage.success(`退款申请已提交(${r.refundOrderId}),等待平台审核`)
    refundDialog.value = false
    load()
  } finally {
    refunding.value = false
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

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
