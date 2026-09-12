<template>
  <div>
    <div class="mx-toolbar">
      <el-input v-model="shopName" placeholder="按店铺名称搜索" style="width: 200px" clearable @keyup.enter="load" />
      <el-select v-model="stateFilter" placeholder="全部状态" clearable style="width: 130px" @change="load">
        <el-option label="待审核" :value="0" />
        <el-option label="正常" :value="1" />
        <el-option label="已禁用" :value="2" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="load">查询</el-button>
    </div>

    <el-table :data="records" v-loading="loading" border stripe>
      <el-table-column label="商户" min-width="190">
        <template #default="{ row }">
          <div class="nm">{{ row.shopName }}</div>
          <div class="muted num">{{ row.shopNum }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="username" label="账号" width="110" />
      <el-table-column prop="shopPhone" label="电话" width="118">
        <template #default="{ row }"><span class="num">{{ row.shopPhone }}</span></template>
      </el-table-column>
      <el-table-column prop="shopAddress" label="经营地址" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="96" align="center">
        <template #default="{ row }"><StateTag kind="shop" :value="row.shopState" /></template>
      </el-table-column>
      <el-table-column label="审核备注" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.auditRemark || '—' }}</template>
      </el-table-column>
      <el-table-column label="入驻时间" width="110">
        <template #default="{ row }"><span class="small num">{{ fmtTime(row.createdAt).slice(0, 10) }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="240" align="center" fixed="right">
        <template #default="{ row }">
          <template v-if="row.shopState === 0">
            <el-button link type="success" @click="audit(row, 1)">通过</el-button>
            <el-button link type="danger" @click="audit(row, 2)">驳回</el-button>
          </template>
          <template v-else-if="row.shopState === 1">
            <el-button link type="warning" @click="disable(row)">禁用</el-button>
          </template>
          <template v-else>
            <el-button link type="success" @click="enable(row)">启用</el-button>
          </template>
          <el-button link type="primary" @click="openRate(row)">费率</el-button>
          <el-button v-if="canDelete" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination background layout="total, prev, pager, next" :total="total" :page-size="pageSize" :current-page="page" @current-change="(p: number) => { page = p; load() }" />
    </div>

    <el-dialog v-model="rateDialog" title="差异化费率配置" width="460px">
      <el-form label-width="96px">
        <el-form-item label="商户">
          <span>{{ rateShopName }}</span>
        </el-form-item>
        <el-form-item label="支付渠道" required>
          <el-select v-model="rateForm.wayCode" style="width: 100%">
            <el-option label="微信支付" value="WECHAT" />
            <el-option label="支付宝" value="ALIPAY" />
            <el-option label="云闪付" value="UNIONPAY" />
          </el-select>
        </el-form-item>
        <el-form-item label="商户费率" required>
          <el-input v-model="rateForm.mchRate" type="number" step="0.0001" placeholder="如 0.0040,优先于渠道基础费率" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rateDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { adminMchAudit, adminMchDelete, adminMchList, adminMchRate } from '@/api/modules'
import { fmtTime } from '@/api/client'
import { useAdminDelete } from '@/composables/useAdminDelete'
import type { Mch } from '@/types'
import StateTag from '@/components/StateTag.vue'

const records = ref<Mch[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = 10
const total = ref(0)
const shopName = ref('')
const stateFilter = ref<number | undefined>()

const rateDialog = ref(false)
const saving = ref(false)
const rateShopName = ref('')
const rateForm = reactive({ shopNum: '', wayCode: 'ALIPAY', mchRate: '0.0040' })

async function load() {
  loading.value = true
  try {
    const r = await adminMchList({
      pageNum: page.value,
      pageSize,
      shopName: shopName.value || undefined,
      shopState: stateFilter.value,
    })
    records.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function audit(row: Mch, shopState: 1 | 2) {
  let remark: string | undefined
  if (shopState === 2) {
    const { value } = await ElMessageBox.prompt('请填写驳回原因(必填)', '驳回入驻申请', { inputValidator: (v) => !!v || '驳回必须填写原因' })
    remark = value
  } else {
    await ElMessageBox.confirm(`确认通过「${row.shopName}」的入驻申请?`, '商户审核', { type: 'success' })
  }
  await adminMchAudit(row.shopNum, shopState, remark)
  ElMessage.success('商户审核操作成功')
  load()
}

async function disable(row: Mch) {
  await ElMessageBox.confirm(`确认禁用「${row.shopName}」?禁用后商户将无法登录。`, '商户管控', { type: 'warning' })
  await adminMchAudit(row.shopNum, 2, '违规经营,平台禁用')
  ElMessage.success('已禁用')
  load()
}

async function enable(row: Mch) {
  await adminMchAudit(row.shopNum, 1, '重新启用')
  ElMessage.success('已启用')
  load()
}

const { canDelete, adminDelete } = useAdminDelete()

/** 删除商户:仅无商品/订单/资金等业务数据的商户可删,后端校验兜底 */
async function remove(row: Mch) {
  const ok = await adminDelete(
    `商户「${row.shopName}」(${row.shopNum})`,
    '仅无商品、订单与资金记录的商户可删除,删除后不可恢复。',
    (t) => adminMchDelete(row.shopNum, t),
    `商户「${row.shopName}」已删除`,
  )
  if (ok) {
    if (records.value.length === 1 && page.value > 1) page.value -= 1
    load()
  }
}

function openRate(row: Mch) {
  rateShopName.value = `${row.shopName}(${row.shopNum})`
  Object.assign(rateForm, { shopNum: row.shopNum, wayCode: 'ALIPAY', mchRate: '0.0040' })
  rateDialog.value = true
}

async function saveRate() {
  saving.value = true
  try {
    await adminMchRate(rateForm.shopNum, rateForm.wayCode, rateForm.mchRate)
    ElMessage.success('商户费率配置成功')
    rateDialog.value = false
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.nm {
  font-weight: 500;
}

.muted {
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
