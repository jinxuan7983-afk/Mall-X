<template>
  <div>
    <div class="mx-toolbar">
      <el-select v-model="stateFilter" placeholder="全部状态" clearable style="width: 140px" @change="load">
        <el-option label="审核中" :value="0" />
        <el-option label="退款成功" :value="1" />
        <el-option label="退款失败" :value="2" />
      </el-select>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
      <span class="tip">审核通过后经支付微服务执行渠道退款,原支付单流转为「已退款」。</span>
    </div>

    <el-table :data="records" v-loading="loading" border stripe>
      <el-table-column label="退款单号" min-width="180">
        <template #default="{ row }"><span class="small num">{{ row.refundOrderId }}</span></template>
      </el-table-column>
      <el-table-column label="原支付单号" min-width="180">
        <template #default="{ row }"><span class="small num">{{ row.payOrderId }}</span></template>
      </el-table-column>
      <el-table-column label="商户" width="150">
        <template #default="{ row }"><span class="small num">{{ row.shopNum }}</span></template>
      </el-table-column>
      <el-table-column label="退款金额" width="110" align="right">
        <template #default="{ row }"><span class="price">¥{{ money(row.refundMoney) }}</span></template>
      </el-table-column>
      <el-table-column prop="refundReason" label="退款原因" min-width="140" show-overflow-tooltip />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }"><StateTag kind="refund" :value="row.refundState" /></template>
      </el-table-column>
      <el-table-column label="申请时间" width="170">
        <template #default="{ row }"><span class="small num">{{ fmtTime(row.createdAt) }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="120" align="center" fixed="right">
        <template #default="{ row }">
          <template v-if="row.refundState === 0">
            <el-button link type="success" @click="audit(row, 1)">通过</el-button>
            <el-button link type="danger" @click="audit(row, 2)">驳回</el-button>
          </template>
          <span v-else class="muted">已处理</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination background layout="total, prev, pager, next" :total="total" :page-size="pageSize" :current-page="page" @current-change="(p: number) => { page = p; load() }" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { refundAudit, refundList } from '@/api/modules'
import { fmtTime, money } from '@/api/client'
import type { RefundOrder } from '@/types'
import StateTag from '@/components/StateTag.vue'

const records = ref<RefundOrder[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = 10
const total = ref(0)
const stateFilter = ref<number | undefined>()

async function load() {
  loading.value = true
  try {
    const r = await refundList({ pageNum: page.value, pageSize, refundState: stateFilter.value })
    records.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function audit(row: RefundOrder, auditState: 1 | 2) {
  let remark: string | undefined
  if (auditState === 2) {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回退款申请', { inputValidator: (v) => !!v || '请填写原因' })
    remark = value
  } else {
    await ElMessageBox.confirm(`确认通过退款 ¥${money(row.refundMoney)}?通过后将执行渠道退款。`, '退款审核', { type: 'warning' })
  }
  await refundAudit(row.refundOrderId, auditState, remark)
  ElMessage.success(auditState === 1 ? '退款成功' : '退款申请已驳回')
  load()
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
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
