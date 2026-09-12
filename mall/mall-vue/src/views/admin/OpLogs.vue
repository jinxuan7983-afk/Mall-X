<template>
  <div>
    <div class="mx-toolbar">
      <el-button :icon="Refresh" @click="load">刷新</el-button>
      <span class="tip">由 AOP 切面自动记录管理端全部操作,用于安全审计。</span>
    </div>

    <el-table :data="records" border stripe v-loading="loading">
      <el-table-column prop="module" label="操作模块" width="160">
        <template #default="{ row }"><b>{{ row.module }}</b></template>
      </el-table-column>
      <el-table-column prop="content" label="操作内容" min-width="300" show-overflow-tooltip>
        <template #default="{ row }"><span class="small num">{{ row.content }}</span></template>
      </el-table-column>
      <el-table-column prop="operator" label="操作人" width="110" />
      <el-table-column prop="ip" label="IP" width="140">
        <template #default="{ row }"><span class="num">{{ row.ip }}</span></template>
      </el-table-column>
      <el-table-column label="时间" width="180">
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
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { adminLogDelete, adminLogList } from '@/api/modules'
import { fmtTime } from '@/api/client'
import { useAdminDelete } from '@/composables/useAdminDelete'
import type { OperateLog } from '@/types'

const records = ref<OperateLog[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = 10
const total = ref(0)

async function load() {
  loading.value = true
  try {
    const r = await adminLogList({ pageNum: page.value, pageSize })
    records.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

const { canDelete, adminDelete } = useAdminDelete()

/** 删除操作日志:用于清理误记录,删除动作本身也会写入新的审计日志 */
async function remove(row: OperateLog) {
  const ok = await adminDelete(
    `操作日志「${row.module}」`,
    '删除后不可恢复;本次删除动作本身会写入新的审计日志。',
    (t) => adminLogDelete(row.id, t),
    '操作日志已删除',
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

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
