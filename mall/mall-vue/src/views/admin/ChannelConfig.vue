<template>
  <div>
    <el-alert type="info" :closable="false" style="margin-bottom: 14px" title="配置渠道开关、基础费率与接口参数">
      <template #default>ALIPAY 渠道配置沙箱密钥后,统一支付下单将切换为支付宝沙箱收银台;未配置时使用内置模拟收银台。</template>
    </el-alert>

    <el-table :data="records" v-loading="loading" border stripe>
      <el-table-column label="渠道" width="120">
        <template #default="{ row }"><b>{{ row.channelName }}</b></template>
      </el-table-column>
      <el-table-column prop="wayCode" label="编码" width="110">
        <template #default="{ row }"><span class="num">{{ row.wayCode }}</span></template>
      </el-table-column>
      <el-table-column label="开关" width="90" align="center">
        <template #default="{ row }"><StateTag kind="channel" :value="row.switchStatus" /></template>
      </el-table-column>
      <el-table-column label="基础费率" width="110" align="right">
        <template #default="{ row }"><span class="num">{{ Number(row.baseRate).toFixed(4) }}</span></template>
      </el-table-column>
      <el-table-column label="网关地址" min-width="220">
        <template #default="{ row }"><span class="small muted">{{ row.gatewayUrl || '—' }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">配置</el-button>
          <el-button v-if="canDelete" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="`配置渠道 - ${form.channelName}`" width="540px">
      <el-form :model="form" label-width="104px">
        <el-form-item label="渠道开关">
          <el-switch v-model="form.switchStatus" :active-value="1" :inactive-value="0" active-text="开启" inactive-text="关闭" />
        </el-form-item>
        <el-form-item label="基础费率">
          <el-input v-model="form.baseRate" type="number" step="0.0001" placeholder="0~1,如 0.0060" />
        </el-form-item>
        <el-form-item label="appId">
          <el-input v-model="form.appId" placeholder="支付宝沙箱 APPID" />
        </el-form-item>
        <el-form-item label="应用私钥">
          <el-input v-model="form.privateKey" type="textarea" :rows="2" placeholder="应用私钥(留空则不修改)" />
        </el-form-item>
        <el-form-item label="支付宝公钥">
          <el-input v-model="form.alipayPublicKey" type="textarea" :rows="2" placeholder="支付宝公钥(留空则不修改)" />
        </el-form-item>
        <el-form-item label="网关地址">
          <el-input v-model="form.gatewayUrl" placeholder="https://openapi-sandbox.dl.alipaydev.com/gateway.do" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminChannelDelete, channelConfig, channelList } from '@/api/modules'
import { useAdminDelete } from '@/composables/useAdminDelete'
import type { ChannelConfig } from '@/types'
import StateTag from '@/components/StateTag.vue'

const records = ref<ChannelConfig[]>([])
const loading = ref(false)
const dialog = ref(false)
const saving = ref(false)
const form = reactive({
  wayCode: '',
  channelName: '',
  switchStatus: 1 as 0 | 1,
  baseRate: '',
  appId: '',
  privateKey: '',
  alipayPublicKey: '',
  gatewayUrl: '',
})

async function load() {
  loading.value = true
  try {
    records.value = (await channelList()).records
  } finally {
    loading.value = false
  }
}

function openEdit(row: ChannelConfig) {
  Object.assign(form, {
    wayCode: row.wayCode,
    channelName: row.channelName,
    switchStatus: row.switchStatus,
    baseRate: String(row.baseRate),
    appId: row.appId || '',
    privateKey: '',
    alipayPublicKey: '',
    gatewayUrl: row.gatewayUrl || '',
  })
  dialog.value = true
}

async function save() {
  saving.value = true
  try {
    await channelConfig({
      wayCode: form.wayCode,
      channelName: form.channelName,
      switchStatus: form.switchStatus,
      baseRate: form.baseRate || undefined,
      appId: form.appId || undefined,
      privateKey: form.privateKey || undefined,
      alipayPublicKey: form.alipayPublicKey || undefined,
      gatewayUrl: form.gatewayUrl || undefined,
    })
    ElMessage.success('支付渠道配置成功')
    dialog.value = false
    load()
  } finally {
    saving.value = false
  }
}

const { canDelete, adminDelete } = useAdminDelete()

/** 删除渠道配置:系统须保留至少一个支付渠道(后端校验兜底) */
async function remove(row: ChannelConfig) {
  const ok = await adminDelete(
    `支付渠道「${row.channelName}」(${row.wayCode})`,
    '删除后该渠道将无法用于收款,系统须保留至少一个支付渠道。',
    (t) => adminChannelDelete(row.wayCode, t),
    `支付渠道「${row.channelName}」配置已删除`,
  )
  if (ok) load()
}

onMounted(load)
</script>

<style scoped>
.small {
  font-size: 12px;
}

.muted {
  color: var(--mx-ink-3);
}
</style>
