<template>
  <div>
    <!-- 统计概览 -->
    <div class="statgrid">
      <div class="mx-card stat">
        <div class="lb">待处理</div>
        <div class="vl num">{{ stats.waitCount }}<span class="u">笔</span></div>
        <div class="sub price">¥{{ money(stats.pendingMoney) }}</div>
      </div>
      <div class="mx-card stat">
        <div class="lb">处理中</div>
        <div class="vl num wait">{{ stats.processingCount }}<span class="u">笔</span></div>
        <div class="sub muted">渠道退款执行中</div>
      </div>
      <div class="mx-card stat">
        <div class="lb">已完成</div>
        <div class="vl num ok">{{ stats.successCount }}<span class="u">笔</span></div>
        <div class="sub price">累计退款 ¥{{ money(stats.successMoney) }}</div>
      </div>
      <div class="mx-card stat">
        <div class="lb">已拒绝</div>
        <div class="vl num bad">{{ stats.failCount }}<span class="u">笔</span></div>
        <div class="sub muted">共 {{ stats.totalCount }} 笔申请</div>
      </div>
    </div>

    <div class="mx-toolbar">
      <el-select v-model="stateFilter" placeholder="全部状态" clearable style="width: 140px" @change="load">
        <el-option label="待审核" :value="0" />
        <el-option label="处理中" :value="1" />
        <el-option label="已完成" :value="2" />
        <el-option label="已拒绝" :value="3" />
      </el-select>
      <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
      <span class="tip">同意退款即执行渠道退款并原路退回；拒绝须填写处理备注。商户主动发起退款可在「交易流水」操作。</span>
    </div>

    <el-table :data="records" v-loading="loading" border stripe>
      <el-table-column label="退款单 / 订单" min-width="200">
        <template #default="{ row }">
          <div class="small num">{{ row.refundOrderId }}</div>
          <div class="small num muted">{{ row.orderNum || row.payOrderId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="申请方" width="96" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.userNum ? 'success' : 'info'" effect="plain">
            {{ row.userNum ? '用户申请' : '商户发起' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="退款金额" width="108" align="right">
        <template #default="{ row }"><span class="price">¥{{ money(row.refundMoney) }}</span></template>
      </el-table-column>
      <el-table-column prop="refundReason" label="退款原因" min-width="140" show-overflow-tooltip />
      <el-table-column label="凭证" width="76" align="center">
        <template #default="{ row }">
          <el-image
            v-if="evidenceList(row).length"
            :src="evidenceList(row)[0]"
            :preview-src-list="evidenceList(row)"
            fit="cover"
            class="evthumb"
          />
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="92" align="center">
        <template #default="{ row }"><StateTag kind="refund" :value="row.refundState" /></template>
      </el-table-column>
      <el-table-column label="申请时间" width="150">
        <template #default="{ row }"><span class="small num">{{ fmtTime(row.createdAt) }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="180" align="center" fixed="right">
        <template #default="{ row }">
          <template v-if="row.refundState === 0">
            <el-button link type="success" @click="audit(row, 1)">同意退款</el-button>
            <el-button link type="danger" @click="audit(row, 2)">拒绝</el-button>
          </template>
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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

    <el-drawer v-model="detailDrawer" title="退款详情" size="450px">
      <template v-if="detailRefund">
        <div class="rfhead">
          <StateTag kind="refund" :value="detailRefund.refundState" />
          <span class="small num">{{ detailRefund.refundOrderId }}</span>
        </div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="关联订单"><span class="num">{{ detailRefund.orderNum || '—' }}</span></el-descriptions-item>
          <el-descriptions-item label="原支付单"><span class="num">{{ detailRefund.payOrderId }}</span></el-descriptions-item>
          <el-descriptions-item label="申请方">
            {{ detailRefund.userNum ? `用户 ${detailRefund.userNum}` : '商户发起' }}
          </el-descriptions-item>
          <el-descriptions-item label="退款金额"><span class="price">¥{{ money(detailRefund.refundMoney) }}</span></el-descriptions-item>
          <el-descriptions-item label="退款原因">{{ detailRefund.refundReason }}</el-descriptions-item>
          <el-descriptions-item label="处理备注">{{ detailRefund.auditRemark || '—' }}</el-descriptions-item>
        </el-descriptions>

        <h4 style="margin: 16px 0 8px">处理进度</h4>
        <el-timeline>
          <el-timeline-item :timestamp="fmtTime(detailRefund.createdAt)" type="primary">提交退款申请</el-timeline-item>
          <el-timeline-item
            :timestamp="detailRefund.auditTime ? fmtTime(detailRefund.auditTime) : '待处理'"
            :type="detailRefund.auditTime ? (detailRefund.refundState >= 2 ? 'success' : 'warning') : 'info'"
            :hollow="!detailRefund.auditTime"
          >商户审核</el-timeline-item>
          <el-timeline-item
            :timestamp="detailRefund.refundTime ? fmtTime(detailRefund.refundTime) : (detailRefund.refundState === 3 ? '流程终止' : '—')"
            :type="detailRefund.refundState === 2 ? 'success' : detailRefund.refundState === 3 ? 'danger' : 'info'"
            :hollow="detailRefund.refundState < 2"
          >{{ detailRefund.refundState === 2 ? '退款完成，原路退回' : detailRefund.refundState === 3 ? '退款未通过' : '等待处理' }}</el-timeline-item>
        </el-timeline>

        <template v-if="evidenceList(detailRefund).length">
          <h4 style="margin: 4px 0 8px">用户凭证</h4>
          <div class="evgrid">
            <el-image
              v-for="u in evidenceList(detailRefund)"
              :key="u"
              :src="u"
              :preview-src-list="evidenceList(detailRefund)"
              fit="cover"
              class="evimg"
            />
          </div>
        </template>
      </template>
      <el-empty v-else description="暂无数据" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { refundMchAudit, refundMchList, refundMchStats } from '@/api/modules'
import { fmtTime, money } from '@/api/client'
import { useSessionStore } from '@/stores/session'
import type { RefundOrder, RefundStats } from '@/types'
import StateTag from '@/components/StateTag.vue'

const session = useSessionStore()
const records = ref<RefundOrder[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = 10
const total = ref(0)
const stateFilter = ref<number | undefined>()
const stats = ref<RefundStats>({
  waitCount: 0,
  processingCount: 0,
  successCount: 0,
  failCount: 0,
  totalCount: 0,
  successMoney: 0,
  pendingMoney: 0,
})

const detailDrawer = ref(false)
const detailRefund = ref<RefundOrder | null>(null)

function evidenceList(r: RefundOrder): string[] {
  if (!r.evidence) return []
  try {
    const arr = JSON.parse(r.evidence)
    return Array.isArray(arr) ? (arr as string[]) : []
  } catch {
    return []
  }
}

async function load() {
  loading.value = true
  try {
    const r = await refundMchList({
      shopNum: session.mch!.shopNum,
      pageNum: page.value,
      pageSize,
      refundState: stateFilter.value,
    })
    records.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    stats.value = await refundMchStats(session.mch!.shopNum)
  } catch {
    /* 统计加载失败不阻塞列表 */
  }
}

function refreshAll() {
  load()
  loadStats()
}

function openDetail(row: RefundOrder) {
  detailRefund.value = row
  detailDrawer.value = true
}

/**
 * 审核退款：1-同意（执行渠道退款，可填写处理备注） 2-拒绝（须填写拒绝原因）。
 * 登录令牌经请求头提交，由后端校验身份与退款单归属。
 */
async function audit(row: RefundOrder, auditState: 1 | 2) {
  const token = session.mch?.token
  if (!token) {
    ElMessage.warning('登录凭证已失效，请退出后重新登录')
    return
  }
  let remark: string | undefined
  try {
    if (auditState === 2) {
      const { value } = await ElMessageBox.prompt('请填写拒绝原因（将展示给用户）', '拒绝退款申请', {
        confirmButtonText: '确认拒绝',
        confirmButtonClass: 'el-button--danger',
        inputValidator: (v) => (v && v.trim() ? true : '请填写拒绝原因'),
      })
      remark = value.trim()
    } else {
      const { value } = await ElMessageBox.prompt(
        `同意后将立即执行渠道退款 ¥${money(row.refundMoney)}，可填写处理备注（选填）。`,
        '同意退款',
        { confirmButtonText: '同意并退款', inputPlaceholder: '选填，如：情况属实，同意退款', inputValidator: () => true },
      )
      remark = value?.trim() || undefined
    }
  } catch {
    return // 用户取消
  }
  try {
    await refundMchAudit(row.refundOrderId, auditState, remark, token)
    ElMessage.success(auditState === 1 ? '退款已完成' : '退款申请已拒绝')
    refreshAll()
  } catch {
    // 失败原因已由统一请求层提示
    refreshAll()
  }
}

onMounted(() => {
  load()
  loadStats()
})
</script>

<style scoped>
.statgrid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}

.stat {
  padding: 14px 16px;
}

.stat .lb {
  color: var(--mx-ink-3);
  font-size: 12.5px;
}

.stat .vl {
  font-size: 24px;
  font-weight: 600;
  margin: 4px 0 2px;
}

.stat .vl .u {
  font-size: 12px;
  font-weight: 400;
  color: var(--mx-ink-3);
  margin-left: 4px;
}

.stat .vl.ok {
  color: var(--mx-green);
}

.stat .vl.bad {
  color: var(--mx-red);
}

.stat .vl.wait {
  color: var(--mx-amber);
}

.stat .sub {
  font-size: 12px;
}

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

.rfhead {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.evthumb {
  width: 40px;
  height: 40px;
  border-radius: 6px;
  display: block;
  margin: 0 auto;
}

.evgrid {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.evimg {
  width: 84px;
  height: 84px;
  border-radius: 8px;
  display: block;
}

@media (max-width: 900px) {
  .statgrid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
