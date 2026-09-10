<template>
  <div>
    <!-- 新建顶级类目必须写 openCreate() 显式调用,避免把事件对象传入 pid -->
    <div class="mx-toolbar">
      <el-button type="primary" :icon="Plus" @click="openCreate()">新建类目</el-button>
      <span class="tip">类目用于商城首页筛选与商品归类。</span>
    </div>

    <el-table :data="records" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="类目名称" min-width="180">
        <template #default="{ row }">
          <el-input v-if="editingId === row.id" v-model="editName" size="small" style="width: 180px" />
          <span v-else>{{ row.categoryName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="排序" width="110" align="right">
        <template #default="{ row }"><span class="num">{{ row.sort }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="200" align="center">
        <template #default="{ row }">
          <template v-if="editingId === row.id">
            <el-button link type="success" @click="saveEdit(row)">保存</el-button>
            <el-button link @click="editingId = 0">取消</el-button>
          </template>
          <template v-else>
            <el-button link type="primary" @click="startEdit(row)">重命名</el-button>
            <el-button link type="primary" @click="openCreate(row.id)">添加子类目</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="parentName ? `添加子类目 - ${parentName}` : '新建顶级类目'" width="400px">
      <el-form label-width="84px">
        <el-form-item label="类目名称" required>
          <el-input v-model="newName" placeholder="类目名称" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input v-model="newSort" type="number" placeholder="数值越小越靠前" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { categoryList, categorySave } from '@/api/modules'
import type { Category } from '@/types'

const records = ref<Category[]>([])
const loading = ref(false)
const dialog = ref(false)
const saving = ref(false)
const newName = ref('')
const newSort = ref('0')
const parentId = ref(0)
const parentName = ref('')
const editingId = ref(0)
const editName = ref('')

async function load() {
  loading.value = true
  try {
    records.value = (await categoryList()).records
  } finally {
    loading.value = false
  }
}

function openCreate(pid = 0) {
  parentId.value = pid
  parentName.value = pid ? records.value.find((c) => c.id === pid)?.categoryName || '' : ''
  newName.value = ''
  newSort.value = '0'
  dialog.value = true
}

function startEdit(row: Category) {
  editingId.value = row.id
  editName.value = row.categoryName
}

async function saveEdit(row: Category) {
  if (!editName.value.trim()) {
    ElMessage.warning('类目名称不能为空')
    return
  }
  await categorySave({ id: row.id, categoryName: editName.value.trim() })
  ElMessage.success('类目更新成功')
  editingId.value = 0
  load()
}

async function save() {
  if (!newName.value.trim()) {
    ElMessage.warning('请填写类目名称')
    return
  }
  saving.value = true
  try {
    await categorySave({ categoryName: newName.value.trim(), parentId: parentId.value, sort: Number(newSort.value || 0) })
    ElMessage.success('类目创建成功')
    dialog.value = false
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
</style>
