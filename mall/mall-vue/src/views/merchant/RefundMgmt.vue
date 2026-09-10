<template>
  <div>
    <div class="mx-toolbar">
      <el-input v-model="queryNo" placeholder="输入退款单号查询进度" style="width: 260px" clearable />
      <el-button type="primary" @click="queryOne">查询进度</el-button>
      <span class="tip">发起退款请到「交易流水」,对已支付订单点击退款。</span>
    </div>

    <el-alert v-if="queried" :type="queried.refundState === 1 ? 'success' : queried.refundState === 2 ? 'error' : 'info'" :closable="false" style="margin-bottom: 14px">
      <template #title>
        <b class="num">{{ queried.refundOrderId }}</b>
        <el-divider direction="vertical" />
        金额 ¥{{ money(queried.refundMoney) }}
        <el-divider direction="vertical" />
        {{ refundText(queried.refundState) }}
        <el-divider direction="vertical" />
        {{ queried.refundReason }}
        <template v-if="queried.refundTime">
          <el-divider direction="vertical" />
          {{ queried.refundTime }}
        </template>
      </template>
    </el-alert>

    <div class="mx-card" style="padding: 18px">
      <el-steps :active="stepActive" align-center :process-status="stepError ? 'error' : 'process'">
        <el-step title="商户发起" description="交易流水页操作" />
        <el-step title="平台审核" :description="auditDesc" />
        <el-step title="渠道执行" description="审核通过后执行" />
        <el-step title="到账" description="退款成功" />
      </el-steps>
      <p class="hint">
        退款流程:商户针对已支付订单发起退款 → 平台管理员审核(通过后经支付微服务执行渠道退款)→ 退款到账,原支付单流转为「已退款」。
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { refundQuery } from '@/api/modules'
import { money } from '@/api/client'
import { useSessionStore } from '@/stores/session'
import type { RefundOrder } from '@/types'

const session = useSessionStore()
const queryNo = ref('')
const queried = ref<RefundOrder | null>(null)

const refundText = (s: number) => ({ 0: '审核中,等待平台管理员处理', 1: '退款成功', 2: '退款失败,请联系平台' }[s] ?? '')

/** 步骤条:0-审核中停在「平台审核」;1-成功走完全部 4 步;2-驳回停在「平台审核」并标红 */
const stepActive = computed(() => {
  if (!queried.value) return 0
  if (queried.value.refundState === 1) return 4
  return 1
})
const stepError = computed(() => queried.value?.refundState === 2)
const auditDesc = computed(() => {
  if (!queried.value) return '等待平台管理员处理'
  return { 0: '退款单状态:审核中', 1: '审核通过', 2: '审核驳回,流程终止' }[queried.value.refundState] ?? ''
})

async function queryOne() {
  if (!queryNo.value) return
  const r = await refundQuery(queryNo.value.trim(), session.mch!.shopNum)
  queried.value = r
}
</script>

<style scoped>
.tip {
  color: var(--mx-ink-3);
  font-size: 12px;
}

.hint {
  color: var(--mx-ink-3);
  font-size: 12px;
  margin: 18px 0 0;
}
</style>
