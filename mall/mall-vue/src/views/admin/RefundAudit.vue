<template>
  <div>
    <div class="mx-toolbar">
      <el-select v-model="stateFilter" placeholder="全部状态" clearable style="width: 140px" @change="load">
        <el-option label="待审核" :value="0" />
        <el-option label="处理中" :value="1" />
        <el-option label="已完成" :value="2" />
        <el-option label="已拒绝" :value="3" />
      </el-select>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
      <span class="tip">同意后立即执行渠道退款,全额退完原支付单流转为「已退款」;退款状态与用户端/商户端实时同步。</span>
    </div>

    <el-table :data="records" v-loading="loading" border stripe>
      <el-table-column label="退款单 / 订单" min-width="210">
        <template #default="{ row }">
          <div class="small num">{{ row.refundOrderId }}</div>
          <div class="small num muted">{{ row.orderNum || row.payOrderId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="商户" width="120">
        <template #default="{ row }"><span class="small num">{{ row.shopNum }}</span></template>
      </el-table-column>
      <el-table-column label="申请方" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.userNum ? 'success' : 'info'" effect="plain">
            {{ row.userNum ? '用户申请' : '商户发起' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="退款金额" width="110" align="right">
        <template #default="{ row }"><span class="price">¥{{ money(row.refundMoney) }}</span></template>
      </el-table-column>
      <el-table-column prop="refundReason" label="退款原因" min-width="140" show-overflow-tooltip />
      <el-table-column label="凭证" width="70" align="center">
        <template #default="{ row }">
          <el-image
            v-if="evidenceList(row).length"
            :src="evidenceList(row)[0]"
            :preview-src-list="evidenceList(row)"
            fit="cover"
            class="evthumb"
          />
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="92" align="center">
        <template #default="{ row }"><StateTag kind="refund" :value="row.refundState" /></template>
      </el-table-column>
      <el-table-column label="申请时间" width="150">
        <template #default="{ row }"><span class="small num">{{ fmtTime(row.createdAt) }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="180" align="center" fixed="right">
        <template #default="{ row }">
          <template v-if="row.refundState === 0">
            <el-button link type="success" @click="audit(row, 1)">通过</el-button>
            <el-button link type="danger" @click="audit(row, 2)">驳回</el-button>
          </template>
          <span v-else class="muted">已处理</span>
          <el-button v-if="canDelete" link type="danger" @click="remove(row)">删除</el-button>
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
import { adminRefundDelete, refundAudit, refundList } from '@/api/modules'
import { fmtTime, money } from '@/api/client'
import { useAdminDelete } from '@/composables/useAdminDelete'
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

function evidenceList(r: RefundOrder): string[] {
  if (!r.evidence) return []
  try {
    const arr = JSON.parse(r.evidence)
    return Array.isArray(arr) ? (arr as string[]) : []
  } catch {
    return []
  }
}

async function audit(row: RefundOrder, auditState: 1 | 2) {
  let remark: string | undefined
  try {
    if (auditState === 2) {
      const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回退款申请', { inputValidator: (v) => (v && v.trim() ? true : '请填写原因') })
      remark = value.trim()
    } else {
      await ElMessageBox.confirm(`确认通过退款 ¥${money(row.refundMoney)}?通过后将立即执行渠道退款。`, '退款审核', { type: 'warning' })
    }
  } catch {
    return // 用户取消
  }
  try {
    await refundAudit(row.refundOrderId, auditState, remark)
    ElMessage.success(auditState === 1 ? '退款已完成' : '退款申请已驳回')
    load()
  } catch {
    // 失败原因已由统一请求层提示
    load()
  }
}

const { canDelete, adminDelete } = useAdminDelete()

/** 删除退款单:仅已拒绝可删,待审核/处理中/已完成记录须留痕(后端校验兜底) */
async function remove(row: RefundOrder) {
  const ok = await adminDelete(
    `退款单「${row.refundOrderId}」`,
    '仅已拒绝的记录可删除,待审核、处理中与已完成的记录须留痕不可删除。',
    (t) => adminRefundDelete(row.refundOrderId, t),
    `退款单「${row.refundOrderId}」已删除`,
  )
  if (ok) {
    if (records.value.length === 1 && page.value > 1) page.value -= 1
    load()
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
}

.evthumb {
  width: 40px;
  height: 40px;
  border-radius: 6px;
  display: block;
  margin: 0 auto;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
