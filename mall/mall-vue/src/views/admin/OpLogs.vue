<template>
  <div>
    <div class="mx-toolbar">
      <el-button :icon="Refresh" @click="load">刷新</el-button>
      <span class="tip">由 AOP 切面自动记录管理端全部操作,用于安全审计。</span>
    </div>

    <el-table :data="records" border stripe v-loading="loading">
      <el-table-column label="操作模块" width="170">
        <template #default="{ row }"><b>{{ moduleText(row) }}</b></template>
      </el-table-column>
      <el-table-column label="操作内容" min-width="320" show-overflow-tooltip>
        <template #default="{ row }"><span class="small">{{ contentText(row) }}</span></template>
      </el-table-column>
      <el-table-column prop="operator" label="操作人" width="110" />
      <el-table-column prop="ip" label="IP" width="140">
        <template #default="{ row }"><span class="num">{{ row.ip }}</span></template>
      </el-table-column>
      <el-table-column label="时间" width="180">
        <template #default="{ row }"><span class="small num">{{ fmtTime(row.createdAt) }}</span></template>
      </el-table-column>
      <el-table-column v-if="canDelete" label="操作" width="90" align="center">
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

/**
 * 操作日志可读化：后端 AOP 记录的是「方法名 + 参数 JSON」，
 * 此处翻译为中文动作描述与参数说明（仅前端展示，不改动原始审计数据）。
 */
const MODULE_TEXT: Record<string, string> = {
  login: '管理员登录',
  goodsList: '查询全平台商品',
  goodsState: '商品强制上下架',
  auditMch: '商户入驻审核',
  listMch: '查询商户列表',
  configRate: '配置商户费率',
  configChannel: '配置支付渠道',
  listChannels: '查询渠道配置',
  listPayOrders: '查询全平台支付订单',
  withdrawAudit: '提现审核',
  withdrawList: '查询提现记录',
  deleteMch: '删除商户',
  deleteGoods: '删除商品',
  updateGoodsPic: '上传/替换商品图片',
  deletePay: '删除支付订单',
  deleteChannel: '删除支付渠道',
  deleteWithdraw: '删除提现单',
  deleteLog: '删除操作日志',
  listLogs: '查询操作日志',
  // RefundController
  audit: '退款审核',
  list: '查询退款单',
  // CategoryController
  save: '保存类目',
}

const FIELD_TEXT: Record<string, string> = {
  shopNum: '商户号', shopName: '店铺', shopState: '商户状态', shopPhone: '联系电话',
  shopAddress: '经营地址', username: '账号', password: '密码', realName: '姓名', role: '角色',
  goodsNum: '商品号', goodsName: '商品', goodsState: '商品状态', goodsCount: '数量',
  price: '单价', stock: '库存', imageBase64: '图片数据',
  payOrderId: '支付单号', refundOrderId: '退款单号', refundMoney: '退款金额',
  refundReason: '退款原因', refundState: '退款状态', withdrawNo: '提现单号',
  orderNum: '订单号', userNum: '用户号', money: '金额', state: '状态',
  wayCode: '支付渠道', channelName: '渠道名称', switchStatus: '渠道开关', baseRate: '基础费率',
  gatewayUrl: '网关地址', appId: '应用ID', notifyUrl: '异步通知地址', returnUrl: '同步跳转地址',
  categoryId: '类目ID', categoryName: '类目名称', parentId: '父类目ID', sort: '排序',
  auditState: '审核结果', auditRemark: '审核备注', id: '记录ID',
  pageNum: '页码', pageSize: '每页条数',
}

/** 按字段语义翻译枚举值 */
function valueText(key: string, v: unknown): string {
  const s = String(v)
  if (key === 'auditState') return s === '1' ? '通过/同意' : s === '2' ? '驳回/拒绝' : s
  if (key === 'state') return s === '1' ? '通过(已到账)' : s === '2' ? '驳回(失败)' : s
  if (key === 'goodsState') return s === '1' ? '上架' : '下架'
  if (key === 'shopState') return s === '0' ? '待审核' : s === '1' ? '正常' : '禁用'
  if (key === 'refundState') {
    return ['待审核', '处理中', '已完成', '已拒绝'][Number(s)] ?? s
  }
  if (key === 'switchStatus') return s === '1' ? '启用' : '停用'
  if (key === 'password') return '******'
  if (key === 'imageBase64') return '<图片数据>'
  return s.length > 60 ? s.slice(0, 60) + '…' : s
}

function contentKeys(raw?: string): string[] {
  try {
    return Object.keys(JSON.parse(raw || '{}') as Record<string, unknown>)
  } catch {
    return []
  }
}

/** 方法名有歧义时（delete 同时被退款单与类目使用）按参数字段细化 */
function moduleText(row: OperateLog): string {
  if (row.module === 'delete') {
    const keys = contentKeys(row.content)
    if (keys.includes('refundOrderId')) return '删除退款单'
    if (keys.includes('categoryId')) return '删除类目'
    return '删除记录'
  }
  return MODULE_TEXT[row.module] ?? row.module
}

/** 参数 JSON → 「字段：值」中文描述 */
function contentText(row: OperateLog): string {
  const raw = (row.content ?? '').trim()
  if (!raw || raw === 'success') return '操作成功'
  try {
    const obj = JSON.parse(raw) as Record<string, unknown>
    const parts = Object.entries(obj)
      // sign 为请求签名、password 为敏感字段：均不展示
      .filter(([k, v]) => k !== 'sign' && k !== 'password' && v !== null && v !== undefined && v !== '')
      .map(([k, v]) => `${FIELD_TEXT[k] ?? k}：${valueText(k, v)}`)
    return parts.length ? parts.join('，') : '操作成功'
  } catch {
    // 非 JSON（如纯字符串参数）原样展示
    return raw
  }
}

/** 删除操作日志:用于清理误记录,删除动作本身也会写入新的审计日志 */
async function remove(row: OperateLog) {
  const ok = await adminDelete(
    `操作日志「${moduleText(row)}」`,
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
