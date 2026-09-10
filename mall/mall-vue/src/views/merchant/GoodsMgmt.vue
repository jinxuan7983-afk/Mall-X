<template>
  <div>
    <div class="mx-toolbar">
      <el-select v-model="stateFilter" placeholder="全部状态" clearable style="width: 130px">
        <el-option label="上架" :value="1" />
        <el-option label="下架" :value="0" />
      </el-select>
      <el-button type="primary" :icon="Plus" @click="openCreate">发布商品</el-button>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-table :data="records" v-loading="loading" border stripe>
      <el-table-column label="商品" min-width="220">
        <template #default="{ row }">
          <div class="gcell">
            <div class="pic"><GoodsPic :name="row.goodsName" :seed="row.goodsNum" /></div>
            <div>
              <div class="nm">{{ row.goodsName }}</div>
              <div class="muted num">{{ row.goodsNum }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="价格" width="110" align="right">
        <template #default="{ row }"><span class="price">¥{{ money(row.price) }}</span></template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="90" align="right" />
      <el-table-column prop="sales" label="销量" width="90" align="right" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }"><StateTag kind="goods" :value="row.goodsState" /></template>
      </el-table-column>
      <el-table-column label="操作" width="210" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.goodsState === 1 ? 'warning' : 'success'" @click="toggleState(row)">
            {{ row.goodsState === 1 ? '下架' : '上架' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination background layout="total, prev, pager, next" :total="total" :page-size="pageSize" :current-page="page" @current-change="(p: number) => { page = p; load() }" />
    </div>

    <el-dialog v-model="dialog" :title="editing ? '编辑商品' : '发布商品'" width="520px">
      <el-form :model="form" label-width="84px">
        <el-form-item label="商品名称" required><el-input v-model="form.goodsName" placeholder="商品名称" /></el-form-item>
        <el-form-item label="价格(元)" required>
          <el-input v-model="form.price" placeholder="0.01 起" type="number" />
        </el-form-item>
        <el-form-item label="库存" required><el-input v-model="form.stock" type="number" placeholder="库存数量" /></el-form-item>
        <el-form-item label="类目">
          <el-select v-model="form.categoryId" placeholder="选择类目" clearable style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品描述"><el-input v-model="form.detail" type="textarea" :rows="2" placeholder="卖点、规格等" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { categoryList, goodsCreate, goodsList, goodsUpdate } from '@/api/modules'
import { money } from '@/api/client'
import { useSessionStore } from '@/stores/session'
import type { Category, Goods } from '@/types'
import GoodsPic from '@/components/GoodsPic.vue'
import StateTag from '@/components/StateTag.vue'

const session = useSessionStore()
const records = ref<Goods[]>([])
const categories = ref<Category[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = 10
const total = ref(0)
const stateFilter = ref<number | undefined>()

const dialog = ref(false)
const editing = ref<Goods | null>(null)
const saving = ref(false)
const form = reactive({ goodsName: '', price: '', stock: '', categoryId: undefined as number | undefined, detail: '' })

async function load() {
  loading.value = true
  try {
    const r = await goodsList({
      pageNum: page.value,
      pageSize,
      shopNum: session.mch!.shopNum,
      goodsState: stateFilter.value,
    })
    records.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function loadCats() {
  try {
    categories.value = (await categoryList()).records
  } catch {
    /* ignore */
  }
}

function openCreate() {
  editing.value = null
  Object.assign(form, { goodsName: '', price: '', stock: '', categoryId: undefined, detail: '' })
  dialog.value = true
}

function openEdit(row: Goods) {
  editing.value = row
  Object.assign(form, {
    goodsName: row.goodsName,
    price: String(row.price),
    stock: String(row.stock),
    categoryId: row.categoryId || undefined,
    detail: row.detail || '',
  })
  dialog.value = true
}

async function save() {
  if (!form.goodsName || !form.price || form.stock === '') {
    ElMessage.warning('请填写名称、价格与库存')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await goodsUpdate({
        goodsNum: editing.value.goodsNum,
        shopNum: session.mch!.shopNum,
        goodsName: form.goodsName,
        price: form.price,
        stock: form.stock,
        categoryId: form.categoryId,
        detail: form.detail,
      } as never)
      ElMessage.success('商品已更新')
    } else {
      await goodsCreate({
        goodsName: form.goodsName,
        shopNum: session.mch!.shopNum,
        price: form.price,
        stock: form.stock,
        categoryId: form.categoryId,
        detail: form.detail,
        goodsState: 1,
      } as never)
      ElMessage.success('商品已发布')
    }
    dialog.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function toggleState(row: Goods) {
  // 后端 /goods/update 为全字段校验(goodsName/price/stock 必填),上下架需携带现有数据
  await goodsUpdate({
    goodsNum: row.goodsNum,
    shopNum: session.mch!.shopNum,
    goodsName: row.goodsName,
    price: String(row.price),
    stock: row.stock,
    categoryId: row.categoryId ?? undefined,
    detail: row.detail || '',
    goodsState: row.goodsState === 1 ? 0 : 1,
  } as never)
  ElMessage.success(row.goodsState === 1 ? '已下架' : '已上架')
  load()
}

onMounted(() => {
  load()
  loadCats()
})
</script>

<style scoped>
.gcell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pic {
  width: 44px;
  height: 44px;
  border-radius: var(--mx-radius);
  overflow: hidden;
  flex-shrink: 0;
}

.nm {
  font-weight: 500;
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
