<template>
  <div>
    <h2 class="mx-page-title">我的订单</h2>

    <el-tabs v-model="tab" class="mx-card tabs" @tab-change="load">
      <el-tab-pane v-for="t in tabs" :key="String(t.value)" :label="t.label" :name="String(t.value)" />
    </el-tabs>

    <div v-loading="loading" style="min-height: 200px">
      <div v-if="records.length === 0 && !loading" class="mx-empty mx-card">暂无订单</div>

      <div v-for="o in records" :key="o.orderNum" class="mx-card order">
        <div class="ohead">
          <span class="num muted">{{ o.orderNum }}</span>
          <span class="muted num">店铺 {{ o.shopNum }}</span>
          <StateTag kind="biz" :value="o.orderState" />
          <span class="muted num">{{ o.createdAt }}</span>
        </div>
        <div class="obody">
          <div class="items">
            <div v-for="it in o.items" :key="it.goodsNum" class="it">
              <GoodsPic :name="it.goodsName" :seed="it.goodsNum" />
              <span class="nm">{{ it.goodsName }}</span>
              <span class="muted num">x{{ it.goodsCount }}</span>
            </div>
          </div>
          <div class="side">
            <div class="price total"><span class="yen">¥</span>{{ money(o.totalMoney) }}</div>
            <div class="ops">
              <el-button v-if="o.orderState === 0" type="primary" size="small" @click="toPay(o.orderNum)">去支付</el-button>
              <el-button v-if="o.orderState === 2" type="primary" size="small" @click="confirm(o.orderNum)">确认收货</el-button>
              <el-button
                v-if="canRefundOrder(o)"
                size="small"
                plain
                :type="refundBtnType(o)"
                @click="onRefundAction(o)"
              >{{ refundBtnText(o) }}</el-button>
              <el-button size="small" link @click="showDetail(o.orderNum)">订单详情</el-button>
            </div>
          </div>
        </div>
        <div v-if="o.orderState === 2" class="logi">
          {{ o.logisticsCompany }} {{ o.logisticsNo }}
        </div>
      </div>
    </div>

    <div class="pager" v-if="total > pageSize">
      <el-pagination background layout="prev, pager, next" :total="total" :page-size="pageSize" :current-page="page" @current-change="(p: number) => { page = p; load() }" />
    </div>

    <el-drawer v-model="drawer" title="订单详情" size="460px">
      <el-descriptions v-if="detail" :column="1" border size="small">
        <el-descriptions-item label="订单号"><span class="num">{{ detail.orderNum }}</span></el-descriptions-item>
        <el-descriptions-item label="状态"><StateTag kind="biz" :value="detail.orderState" /></el-descriptions-item>
        <el-descriptions-item label="金额"><span class="price">¥{{ money(detail.totalMoney) }}</span></el-descriptions-item>
        <el-descriptions-item label="收货人">{{ detail.receiverName }} {{ detail.receiverPhone }}</el-descriptions-item>
        <el-descriptions-item label="地址">{{ detail.receiverAddress }}</el-descriptions-item>
        <el-descriptions-item label="物流">{{ detail.logisticsCompany ? `${detail.logisticsCompany} ${detail.logisticsNo}` : '—' }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ fmtTime(detail.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ fmtTime(detail.payTime) }}</el-descriptions-item>
        <el-descriptions-item label="发货时间">{{ fmtTime(detail.deliverTime) }}</el-descriptions-item>
        <el-descriptions-item label="收货时间">{{ fmtTime(detail.receiveTime) }}</el-descriptions-item>
      </el-descriptions>
      <h4 style="margin: 16px 0 8px">商品明细</h4>
      <el-table v-if="detail" :data="detail.items" size="small">
        <el-table-column prop="goodsName" label="商品" />
        <el-table-column prop="goodsCount" label="数量" width="64" />
        <el-table-column label="小计" width="100" align="right">
          <template #default="{ row }">¥{{ money(row.totalMoney) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 退款进度 -->
    <el-drawer v-model="progressDrawer" title="退款进度" size="440px" @closed="closeProgress">
      <div v-loading="progressLoading" style="min-height: 160px">
        <template v-if="progressRefund">
          <div class="rfhead">
            <StateTag kind="refund" :value="progressRefund.refundState" />
            <span class="num small">{{ progressRefund.refundOrderId }}</span>
          </div>
          <div class="rfrow"><span class="muted">退款金额</span><span class="price">¥{{ money(progressRefund.refundMoney) }}</span></div>
          <div class="rfrow"><span class="muted">退款原因</span><span class="rfval">{{ progressRefund.refundReason }}</span></div>
          <el-timeline style="margin-top: 14px">
            <el-timeline-item :timestamp="fmtTime(progressRefund.createdAt)" type="primary">
              提交退款申请
            </el-timeline-item>
            <el-timeline-item
              :timestamp="progressRefund.auditTime ? fmtTime(progressRefund.auditTime) : '待商户处理'"
              :type="progressRefund.auditTime ? (progressRefund.refundState >= 2 ? 'success' : 'warning') : 'info'"
              :hollow="progressRefund.refundState === 0"
            >
              {{ progressRefund.auditRemark || (progressRefund.auditTime ? '商户已审核' : '等待商户审核') }}
            </el-timeline-item>
            <el-timeline-item
              :timestamp="progressRefund.refundTime ? fmtTime(progressRefund.refundTime) : (progressRefund.refundState === 3 ? '流程终止' : '等待退款到账')"
              :type="progressRefund.refundState === 2 ? 'success' : progressRefund.refundState === 3 ? 'danger' : 'info'"
              :hollow="progressRefund.refundState < 2"
            >
              {{ progressRefund.refundState === 2 ? '退款已完成，原路退回' : progressRefund.refundState === 3 ? '退款申请未通过' : '退款处理中' }}
            </el-timeline-item>
          </el-timeline>
          <template v-if="evidenceList(progressRefund).length">
            <h4 style="margin: 4px 0 8px">申请凭证</h4>
            <div class="evgrid">
              <el-image
                v-for="u in evidenceList(progressRefund)"
                :key="u"
                :src="u"
                :preview-src-list="evidenceList(progressRefund)"
                fit="cover"
                class="evimg"
              />
            </div>
          </template>
          <p class="tip">进度每 8 秒自动刷新，与商户端实时同步。</p>
          <el-button size="small" :icon="Refresh" @click="refreshProgress()">立即刷新</el-button>
        </template>
        <el-empty v-else description="暂无退款记录" />
      </div>
    </el-drawer>

    <!-- 申请退款 -->
    <el-dialog v-model="applyDialog" title="申请退款" width="480px">
      <template v-if="applyForm.order">
        <div class="rfrow"><span class="muted">订单号</span><span class="num small">{{ applyForm.order.orderNum }}</span></div>
        <div class="rfrow"><span class="muted">订单金额</span><span class="price">¥{{ money(applyForm.order.totalMoney) }}</span></div>
        <el-form label-width="80px" style="margin-top: 8px">
          <el-form-item label="退款金额" required>
            <el-input-number
              v-model="applyForm.money"
              :min="0.01"
              :max="Number(applyForm.order.totalMoney)"
              :precision="2"
              :step="1"
              style="width: 180px"
            />
            <span class="tip" style="margin-left: 8px">支持部分退款</span>
          </el-form-item>
          <el-form-item label="退款原因" required>
            <el-select v-model="applyForm.reason" placeholder="请选择退款原因" style="width: 100%">
              <el-option v-for="x in reasonOptions" :key="x" :label="x" :value="x" />
            </el-select>
          </el-form-item>
          <el-form-item label="补充说明">
            <el-input
              v-model="applyForm.remark"
              type="textarea"
              :rows="2"
              maxlength="100"
              show-word-limit
              placeholder="选填，补充问题描述"
            />
          </el-form-item>
          <el-form-item label="上传凭证">
            <div class="evgrid">
              <div v-for="u in evidenceUrls" :key="u" class="evbox">
                <el-image :src="u" fit="cover" class="evimg" />
                <span class="evdel" @click="removeEvidence(u)">×</span>
              </div>
              <label v-if="evidenceUrls.length < 3" class="evbox evadd">
                <el-icon v-if="evidenceUploading" class="is-loading"><Loading /></el-icon>
                <template v-else>
                  <el-icon><Plus /></el-icon>
                  <span>凭证 {{ evidenceUrls.length }}/3</span>
                </template>
                <input type="file" accept="image/jpeg,image/png,image/webp" hidden @change="onPickEvidence" />
              </label>
            </div>
          </el-form-item>
        </el-form>
        <el-alert type="info" :closable="false" title="提交后由商户审核，审核通过后退款将原路退回。" />
      </template>
      <template #footer>
        <el-button @click="applyDialog = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, Plus, Refresh } from '@element-plus/icons-vue'
import { orderConfirm, orderDetail, orderList, refundApply, refundDetail, refundUserList } from '@/api/modules'
import { fmtTime, money } from '@/api/client'
import { useRefundEvidence } from '@/composables/useRefundEvidence'
import { useSessionStore } from '@/stores/session'
import type { BizOrder, OrderItem, RefundOrder } from '@/types'
import GoodsPic from '@/components/GoodsPic.vue'
import StateTag from '@/components/StateTag.vue'

interface ListOrder extends BizOrder {
  items: OrderItem[]
  shopName?: string
}

const router = useRouter()
const session = useSessionStore()

const tabs = [
  { label: '全部', value: -1 },
  { label: '待支付', value: 0 },
  { label: '待发货', value: 1 },
  { label: '已发货', value: 2 },
  { label: '已完成', value: 3 },
  { label: '已关闭', value: 4 },
]
const tab = ref('-1')
const records = ref<ListOrder[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = 10
const total = ref(0)
const drawer = ref(false)
const detail = ref<(BizOrder & { items: OrderItem[] }) | null>(null)

/* ---------- 退款售后 ---------- */
/** 退款单映射:orderNum → 最新一笔退款记录(订单卡片展示入口与状态) */
const refundMap = ref<Record<string, RefundOrder>>({})
const applyDialog = ref(false)
const applying = ref(false)
const reasonOptions = ['商品质量问题', '商品与描述不符', '卖家发错货', '收到商品破损', '不想要了', '物流问题', '其他']
const applyForm = reactive({ order: null as ListOrder | null, money: 0, reason: '', remark: '' })
const {
  urls: evidenceUrls,
  uploading: evidenceUploading,
  add: addEvidence,
  remove: removeEvidence,
  clear: clearEvidence,
} = useRefundEvidence(3)
const progressDrawer = ref(false)
const progressLoading = ref(false)
const progressRefund = ref<RefundOrder | null>(null)
let progressTimer: number | undefined

async function load() {
  loading.value = true
  try {
    const r = await orderList({
      userNum: session.user!.userNum,
      pageNum: page.value,
      pageSize,
      orderState: tab.value === '-1' ? undefined : Number(tab.value),
    } as never)
    // 列表不含明细,并行补充商品信息用于展示
    const withItems = await Promise.all(
      r.records.map(async (o) => {
        try {
          const d = await orderDetail(o.orderNum)
          return { ...o, items: d.order.items || [] }
        } catch {
          return { ...o, items: [] }
        }
      }),
    )
    records.value = withItems
    total.value = r.total
    // 并行加载本人退款记录:与商户端/管理端同源，状态变更刷新即同步
    try {
      const rf = await refundUserList({ userNum: session.user!.userNum, pageNum: 1, pageSize: 200 })
      const map: Record<string, RefundOrder> = {}
      for (const r2 of rf.records) {
        if (r2.orderNum && !map[r2.orderNum]) map[r2.orderNum] = r2
      }
      refundMap.value = map
    } catch {
      /* 退款记录加载失败不阻塞订单列表 */
    }
  } finally {
    loading.value = false
  }
}

function toPay(orderNum: string) {
  router.push({ name: 'cashier', query: { orders: orderNum } })
}

async function confirm(orderNum: string) {
  await ElMessageBox.confirm('确认已收到货?', '确认收货', { type: 'warning' })
  await orderConfirm(orderNum)
  ElMessage.success('确认收货成功')
  load()
}

async function showDetail(orderNum: string) {
  const r = await orderDetail(orderNum)
  detail.value = r.order
  drawer.value = true
}

/* ---------- 退款申请与进度 ---------- */

function refundOf(o: ListOrder): RefundOrder | undefined {
  return o.orderNum ? refundMap.value[o.orderNum] : undefined
}

/** 按钮文案:进行中→退款进度；已完成→退款详情；已拒绝→重新申请；无→申请退款 */
function refundBtnText(o: ListOrder): string {
  const r = refundOf(o)
  if (!r) return '申请退款'
  if (r.refundState === 0 || r.refundState === 1) return '退款进度'
  if (r.refundState === 2) return '退款详情'
  return '重新申请'
}

/** 退款按钮语义色:与退款状态标签(StateTag)颜色对应——可申请蓝 / 进行中黄 / 已完成绿 / 已被拒红 */
function refundBtnType(o: ListOrder): 'primary' | 'warning' | 'success' | 'danger' {
  const r = refundOf(o)
  if (!r) return 'primary'
  if (r.refundState === 0 || r.refundState === 1) return 'warning'
  if (r.refundState === 2) return 'success'
  return 'danger'
}

/** 仅已支付/已发货/已完成且存在支付单的订单可申请退款 */
function canRefundOrder(o: ListOrder): boolean {
  return o.orderState !== 0 && o.orderState !== 4 && !!o.payOrderId
}

function onRefundAction(o: ListOrder) {
  const r = refundOf(o)
  if (!r || r.refundState === 3) openApply(o)
  else openProgress(r.refundOrderId)
}

function openApply(o: ListOrder) {
  applyForm.order = o
  applyForm.money = Number(o.totalMoney)
  applyForm.reason = ''
  applyForm.remark = ''
  clearEvidence()
  applyDialog.value = true
}

async function onPickEvidence(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (file) await addEvidence(file)
}

async function submitApply() {
  if (!applyForm.order) return
  if (!applyForm.reason) {
    ElMessage.warning('请选择退款原因')
    return
  }
  const reasonText = applyForm.reason + (applyForm.remark.trim() ? `：${applyForm.remark.trim()}` : '')
  applying.value = true
  try {
    await refundApply({
      orderNum: applyForm.order.orderNum,
      userNum: session.user!.userNum,
      refundMoney: applyForm.money.toFixed(2),
      refundReason: reasonText.slice(0, 200),
      evidence: [...evidenceUrls.value],
    })
    ElMessage.success('退款申请已提交，等待商户处理')
    applyDialog.value = false
    await load()
  } catch {
    // 失败原因已由统一请求层提示(如:已有退款处理中/超出可退余额)
  } finally {
    applying.value = false
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

async function openProgress(refundOrderId: string) {
  progressDrawer.value = true
  await refreshProgress(refundOrderId)
  // 打开期间每 8 秒静默刷新，实现与商户端处理进度的实时同步
  if (progressTimer) window.clearInterval(progressTimer)
  progressTimer = window.setInterval(() => refreshProgress(refundOrderId, true), 8000)
}

async function refreshProgress(refundOrderId?: string, silent = false) {
  const rid = refundOrderId || progressRefund.value?.refundOrderId
  if (!rid) return
  if (!silent) progressLoading.value = true
  try {
    const r = await refundDetail({ refundOrderId: rid, userNum: session.user!.userNum })
    progressRefund.value = r.refund
    if (r.refund.orderNum) refundMap.value[r.refund.orderNum] = r.refund
  } catch {
    // 静默刷新失败不打扰用户
  } finally {
    if (!silent) progressLoading.value = false
  }
}

function closeProgress() {
  if (progressTimer) {
    window.clearInterval(progressTimer)
    progressTimer = undefined
  }
}

onMounted(load)
</script>

<style scoped>
.tabs {
  padding: 4px 16px 0;
  margin-bottom: 14px;
}

.order {
  margin-bottom: 12px;
  overflow: hidden;
}

.ohead {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 18px;
  border-bottom: 1px solid var(--mx-line);
  background: #fcfbf9;
  font-size: 13px;
}

.muted {
  color: var(--mx-ink-3);
}

.obody {
  display: flex;
  align-items: stretch;
}

.items {
  flex: 1;
  border-right: 1px dashed var(--mx-line);
}

.it {
  display: grid;
  grid-template-columns: 52px 1fr 50px;
  gap: 12px;
  align-items: center;
  padding: 10px 18px;
}

.it :deep(.gp) {
  width: 52px;
  height: 52px;
  border-radius: var(--mx-radius);
  overflow: hidden;
}

.it .nm {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side {
  width: 220px;
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: flex-end;
  gap: 10px;
}

/* 操作区:主操作(实心) → 售后操作(描边语义色) → 订单详情(链接),右对齐且窄屏自动换行 */
.ops {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  row-gap: 8px;
}

.ops :deep(.el-button + .el-button) {
  margin-left: 8px;
}

.total {
  font-size: 22px;
}

.logi {
  padding: 8px 18px;
  background: #fcfbf9;
  border-top: 1px dashed var(--mx-line);
  color: var(--mx-ink-2);
  font-size: 12px;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

/* ---------- 退款售后 ---------- */
.rfhead {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.rfrow {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin: 6px 0;
  font-size: 13px;
}

.rfval {
  max-width: 68%;
  text-align: right;
  word-break: break-all;
}

.tip {
  color: var(--mx-ink-3);
  font-size: 12px;
}

.small {
  font-size: 12px;
}

.evgrid {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.evbox {
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px dashed var(--mx-line);
  display: grid;
  place-items: center;
}

.evimg {
  width: 100%;
  height: 100%;
  display: block;
}

.evdel {
  position: absolute;
  top: 1px;
  right: 1px;
  cursor: pointer;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
  border-radius: 50%;
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  font-size: 13px;
  line-height: 1;
}

.evadd {
  cursor: pointer;
  color: var(--mx-ink-3);
  font-size: 12px;
  gap: 2px;
  flex-direction: column;
}
</style>
