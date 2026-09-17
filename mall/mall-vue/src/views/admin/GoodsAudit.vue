<template>
  <div>
    <div class="mx-toolbar">
      <el-input v-model="q.goodsName" placeholder="按商品名称搜索" style="width: 190px" clearable @keyup.enter="load" />
      <el-input v-model="q.shopNum" placeholder="按商户编号筛选" style="width: 180px" clearable @keyup.enter="load" />
      <el-select v-model="q.categoryId" placeholder="全部类目" clearable style="width: 130px" @change="load">
        <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
      </el-select>
      <el-select v-model="q.goodsState" placeholder="全部状态" clearable style="width: 120px" @change="load">
        <el-option label="上架" :value="1" />
        <el-option label="下架" :value="0" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
      <span class="hint">平台商品管控:可强制下架违规商品,商品详情走 Redis 缓存。</span>
    </div>

    <el-table :data="records" v-loading="loading" border stripe>
      <el-table-column label="商品" min-width="210">
        <template #default="{ row }">
          <div class="gcell">
            <div class="pic"><GoodsPic :name="row.goodsName" :seed="row.goodsNum" :pic="row.picUrl || undefined" /></div>
            <div>
              <div class="nm">{{ row.goodsName }}</div>
              <div class="muted num">{{ row.goodsNum }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="商品图片" width="120" align="center">
        <template #default="{ row }">
          <div class="piccell">
            <div class="picbig"><GoodsPic :name="row.goodsName" :seed="row.goodsNum" :pic="row.picUrl || undefined" /></div>
            <el-button v-if="isAdmin" link type="primary" :icon="Upload" @click="openPicDialog(row)">更换</el-button>
            <span v-else class="muted">—</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="所属商户" min-width="150">
        <template #default="{ row }">
          <div>{{ row.shopName || '—' }}</div>
          <div class="muted num">{{ row.shopNum }}</div>
        </template>
      </el-table-column>
      <el-table-column label="类目" width="100">
        <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
      </el-table-column>
      <el-table-column label="价格" width="110" align="right">
        <template #default="{ row }"><span class="price">¥{{ money(row.price) }}</span></template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="80" align="right" />
      <el-table-column prop="sales" label="销量" width="80" align="right" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }"><StateTag kind="goods" :value="row.goodsState" /></template>
      </el-table-column>
      <el-table-column label="操作" width="200" align="center">
        <template #default="{ row }">
          <el-button link :type="row.goodsState === 1 ? 'warning' : 'success'" @click="toggle(row)">
            {{ row.goodsState === 1 ? '强制下架' : '恢复上架' }}
          </el-button>
          <el-button v-if="isAdmin" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        @current-change="(p: number) => { page = p; load() }"
      />
    </div>

    <el-dialog v-model="picDialog" :title="`更换商品图片 - ${picForm.goodsName}`" width="420px">
      <div class="upwrap">
        <div class="preview">
          <img v-if="picForm.preview" :src="picForm.preview" alt="新图片预览" />
          <GoodsPic v-else :name="picForm.goodsName" :seed="picForm.goodsNum" :pic="picForm.currentPic || undefined" />
        </div>
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          accept="image/jpeg,image/png,image/webp"
          :on-change="onFilePicked"
        >
          <el-button :icon="Upload">选择图片</el-button>
        </el-upload>
        <div class="hint">支持 JPG / PNG / WebP，自动压缩至 800px 内；上传成功后立即生效并同步商城端展示。</div>
      </div>
      <template #footer>
        <el-button @click="picDialog = false">取消</el-button>
        <el-button type="primary" :loading="picSaving" :disabled="!picForm.base64" @click="savePic">上传并生效</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { Refresh, Upload } from '@element-plus/icons-vue'
import { adminGoodsDelete, adminGoodsList, adminGoodsPic, adminGoodsState, categoryList } from '@/api/modules'
import { money } from '@/api/client'
import { useAdminDelete } from '@/composables/useAdminDelete'
import { compressImage } from '@/composables/useRefundEvidence'
import { useSessionStore } from '@/stores/session'
import type { Category, Goods } from '@/types'
import GoodsPic from '@/components/GoodsPic.vue'
import StateTag from '@/components/StateTag.vue'

const session = useSessionStore()
const records = ref<Goods[]>([])
const categories = ref<Category[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = 10
const q = reactive({ goodsName: '', shopNum: '', categoryId: undefined as number | undefined, goodsState: undefined as number | undefined })

const categoryName = (id?: number) => categories.value.find((c) => c.id === id)?.categoryName || '—'

async function load() {
  loading.value = true
  try {
    const r = await adminGoodsList({ pageNum: page.value, pageSize, ...q })
    records.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function toggle(row: Goods) {
  const next: 0 | 1 = row.goodsState === 1 ? 0 : 1
  const action = next === 0 ? '下架' : '上架'
  try {
    await ElMessageBox.confirm(`确认${action}商品「${row.goodsName}」?`, '平台商品管控', { type: 'warning' })
  } catch {
    return
  }
  await adminGoodsState(row.goodsNum, next)
  ElMessage.success(`商品已${action}`)
  load()
}

const { isAdmin, adminDelete } = useAdminDelete()

/** 删除商品:超级管理员不受订单引用限制,删除后同步清理详情缓存与库存 */
async function remove(row: Goods) {
  const ok = await adminDelete(
    `商品「${row.goodsName}」(${row.goodsNum})`,
    '删除后不可恢复,商品缓存与库存将同步清理;即使被订单引用也可删除。',
    (t) => adminGoodsDelete(row.goodsNum, t),
    `商品「${row.goodsName}」已删除`,
  )
  if (ok) {
    if (records.value.length === 1 && page.value > 1) page.value -= 1
    load()
  }
}

/* ---------- 商品图片上传 / 替换 ---------- */
const picDialog = ref(false)
const picSaving = ref(false)
const picForm = reactive({ goodsNum: '', goodsName: '', currentPic: '', preview: '', base64: '' })

function openPicDialog(row: Goods) {
  Object.assign(picForm, {
    goodsNum: row.goodsNum,
    goodsName: row.goodsName,
    currentPic: row.picUrl || '',
    preview: '',
    base64: '',
  })
  picDialog.value = true
}

async function onFilePicked(file: UploadFile) {
  const raw = file.raw
  if (!raw) return
  if (!/^image\/(jpeg|png|webp)$/i.test(raw.type)) {
    ElMessage.warning('仅支持 JPG / PNG / WebP 格式的图片')
    return
  }
  if (raw.size > 5 * 1024 * 1024) {
    ElMessage.warning('原始图片不能超过 5MB，请压缩后再上传')
    return
  }
  try {
    picForm.base64 = await compressImage(raw)
    picForm.preview = picForm.base64
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '图片处理失败，请更换图片重试')
  }
}

async function savePic() {
  const token = session.admin?.token
  if (!token) {
    ElMessage.warning('登录凭证已失效,请退出后重新登录')
    return
  }
  if (!picForm.base64) {
    ElMessage.warning('请先选择要上传的图片')
    return
  }
  picSaving.value = true
  try {
    const r = await adminGoodsPic(picForm.goodsNum, picForm.base64, token)
    ElMessage.success('商品图片已更新')
    picDialog.value = false
    const row = records.value.find((g) => g.goodsNum === picForm.goodsNum)
    if (row) row.picUrl = r.picUrl
    load()
  } finally {
    picSaving.value = false
  }
}

onMounted(async () => {
  try {
    const c = await categoryList()
    categories.value = c.records
  } catch {
    /* 类目加载失败不阻塞列表 */
  }
  load()
})
</script>

<style scoped>
.hint {
  color: var(--mx-ink-3);
  font-size: 12px;
}

.gcell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pic {
  width: 42px;
  height: 42px;
  border-radius: 8px;
  overflow: hidden;
  flex: none;
  background: var(--mx-paper);
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
}

.piccell {
  display: grid;
  justify-items: center;
  gap: 6px;
}

.picbig {
  width: 64px;
  height: 64px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--mx-paper);
}

.upwrap {
  display: grid;
  justify-items: center;
  gap: 14px;
}

.preview {
  width: 220px;
  height: 220px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--mx-line);
  background: var(--mx-paper);
}

.preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.upwrap .hint {
  color: var(--mx-ink-3);
  font-size: 12px;
  text-align: center;
  line-height: 1.7;
}
</style>
