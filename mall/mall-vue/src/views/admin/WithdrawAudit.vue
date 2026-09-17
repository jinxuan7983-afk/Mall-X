<template>
  <div>
    <div class="mx-toolbar">
      <el-input v-model="shopNum" placeholder="按商户编号过滤" style="width: 200px" clearable @keyup.enter="load" />
      <el-button type="primary" :icon="Search" @click="load">查询</el-button>
      <span class="tip">商户在「资金管理」提交提现申请,平台在此审核打款。</span>
    </div>

    <el-table :data="records" v-loading="loading" border stripe>
      <el-table-column label="提现单号" min-width="180" show-overflow-tooltip>
        <template #default="{ row }"><span class="small num">{{ row.withdrawNo }}</span></template>
      </el-table-column>
      <el-table-column label="商户" width="130">
        <template #default="{ row }"><span class="small num">{{ row.shopNum }}</span></template>
      </el-table-column>
      <el-table-column label="金额" width="110" align="right">
        <template #default="{ row }"><span class="price">¥{{ money(row.money) }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="92" align="center">
        <template #default="{ row }"><StateTag kind="withdraw" :value="row.state" /></template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" />
      <el-table-column label="申请时间" width="150">
        <template #default="{ row }"><span class="small num">{{ fmtTime(row.createdAt) }}</span></template>
      </el-table-column>
      <el-table-column label="完成时间" width="150">
        <template #default="{ row }"><span class="small num">{{ fmtTime(row.finishTime) }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="175" align="center">
        <template #default="{ row }">
          <template v-if="row.state === 0">
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
import { Search } from '@element-plus/icons-vue'
import { adminWithdrawDelete, adminWithdrawList, withdrawAudit } from '@/api/modules'
import { fmtTime, money } from '@/api/client'
import { useAdminDelete } from '@/composables/useAdminDelete'
import type { Withdraw } from '@/types'
import StateTag from '@/components/StateTag.vue'

const records = ref<Withdraw[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = 10
const total = ref(0)
const shopNum = ref('')

async function load() {
  loading.value = true
  try {
    const r = await adminWithdrawList({ pageNum: page.value, pageSize, shopNum: shopNum.value || undefined })
    records.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function audit(row: Withdraw, state: 1 | 2) {
  let remark: string | undefined
  if (state === 2) {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回提现', { inputValidator: (v) => !!v || '请填写原因' })
    remark = value
  } else {
    await ElMessageBox.confirm(`确认向 ${row.shopNum} 打款 ¥${money(row.money)}?`, '提现审核', { type: 'warning' })
  }
  await withdrawAudit(row.withdrawNo, state, remark)
  ElMessage.success('提现审核完成')
  load()
}

const { canDelete, adminDelete } = useAdminDelete()

/** 删除提现单:仅已驳回可删,审核中/已到账涉及资金流向不可删(后端校验兜底) */
async function remove(row: Withdraw) {
  const ok = await adminDelete(
    `提现单「${row.withdrawNo}」`,
    '删除后不可恢复;任意状态的提现单均可删除。',
    (t) => adminWithdrawDelete(row.withdrawNo, t),
    `提现单「${row.withdrawNo}」已删除`,
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

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
