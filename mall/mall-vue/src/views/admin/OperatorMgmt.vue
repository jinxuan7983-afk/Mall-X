<template>
  <div>
    <div class="mx-toolbar">
      <el-button type="primary" :icon="Plus" @click="dialog = true">创建操作员</el-button>
      <span class="tip">ADMIN 可管理全部功能;OPS 为运营操作员。当前角色:{{ session.admin?.role }}</span>
    </div>

    <el-table :data="records" border stripe v-loading="loading">
      <el-table-column prop="username" label="账号" width="140" />
      <el-table-column prop="realName" label="姓名" width="160" />
      <el-table-column label="角色" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" size="small">{{ row.role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.state === 1 ? 'success' : 'info'" size="small">{{ row.state === 1 ? '正常' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }"><span class="small num">{{ fmtTime(row.createdAt) }}</span></template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination background layout="total, prev, pager, next" :total="total" :page-size="pageSize" :current-page="page" @current-change="(p: number) => { page = p; load() }" />
    </div>

    <el-dialog v-model="dialog" title="创建后台操作员" width="440px">
      <el-form :model="form" label-width="84px">
        <el-form-item label="账号" required><el-input v-model="form.username" placeholder="登录账号" /></el-form-item>
        <el-form-item label="密码" required><el-input v-model="form.password" type="password" show-password placeholder="密码" /></el-form-item>
        <el-form-item label="姓名" required><el-input v-model="form.realName" placeholder="真实姓名" /></el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="form.role">
            <el-radio value="OPS">OPS 运营</el-radio>
            <el-radio value="ADMIN">ADMIN 管理员</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { adminUserCreate, adminUserList } from '@/api/modules'
import { fmtTime } from '@/api/client'
import { useSessionStore } from '@/stores/session'
import type { AdminUser } from '@/types'

const session = useSessionStore()
const records = ref<AdminUser[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = 10
const total = ref(0)
const dialog = ref(false)
const saving = ref(false)
const form = reactive({ username: '', password: '', realName: '', role: 'OPS' })

async function load() {
  loading.value = true
  try {
    const r = await adminUserList({ pageNum: page.value, pageSize })
    records.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!form.username || !form.password || !form.realName) {
    ElMessage.warning('请完整填写账号、密码与姓名')
    return
  }
  saving.value = true
  try {
    await adminUserCreate({ ...form })
    ElMessage.success('后台账号创建成功')
    dialog.value = false
    Object.assign(form, { username: '', password: '', realName: '', role: 'OPS' })
    load()
  } finally {
    saving.value = false
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
