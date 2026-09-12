<template>
  <div>
    <el-alert
      v-if="session.mch?.shopState === 0"
      type="warning"
      show-icon
      :closable="false"
      title="入驻申请审核中"
      description="平台管理员审核通过后即可正常经营,当前功能仅支持浏览。"
      style="margin-bottom: 16px"
    />
    <el-alert
      v-else-if="session.mch?.shopState === 2"
      type="error"
      show-icon
      :closable="false"
      title="商户已被禁用"
      description="请联系平台管理员处理。"
      style="margin-bottom: 16px"
    />

    <h2 class="mx-page-title">今日经营概览</h2>
    <el-row :gutter="14">
      <el-col :span="6" v-for="c in cards" :key="c.label">
        <div class="mx-card mx-stat" :class="{ accent: c.price }">
          <div class="stat-top">
            <div class="label">{{ c.label }}</div>
            <el-icon class="stat-ico"><component :is="c.icon" /></el-icon>
          </div>
          <div class="value" :class="{ price: c.price }">{{ c.value }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="16">
        <div class="mx-card chart-card">
          <div class="chart-head">近7日交易走势</div>
          <div ref="lineEl" class="chart"></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="mx-card chart-card">
          <div class="chart-head">支付渠道分布</div>
          <div ref="pieEl" class="chart"></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="24">
        <div class="mx-card quick">
          <div class="chart-head">快捷入口</div>
          <div class="qrow">
            <el-button :icon="Plus" @click="$router.push('/mch/goods')">发布商品</el-button>
            <el-button :icon="Van" @click="$router.push('/mch/orders')">处理发货</el-button>
            <el-button :icon="RefreshLeft" @click="$router.push('/mch/refund')">发起退款</el-button>
            <el-button :icon="Wallet" @click="$router.push('/mch/fund')">申请提现</el-button>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Plus, Van, RefreshLeft, Wallet } from '@element-plus/icons-vue'
import { statisMch, fundOverview } from '@/api/modules'
import { money } from '@/api/client'
import { useSessionStore } from '@/stores/session'
import { useEcharts, chartBase } from '@/composables/useEcharts'
import type { Ref } from 'vue'
import type { EChartsOption } from 'echarts'

const session = useSessionStore()
const shopNum = computed(() => session.mch!.shopNum)

const cards = ref([
  { label: '累计营收(元)', value: '—', price: true, icon: 'Money' },
  { label: '累计手续费(元)', value: '—', price: false, icon: 'Tickets' },
  { label: '实际净收益(元)', value: '—', price: true, icon: 'TrendCharts' },
  { label: '可提现余额(元)', value: '—', price: true, icon: 'Wallet' },
])

const lineEl = ref<HTMLElement>() as Ref<HTMLElement | undefined>
const pieEl = ref<HTMLElement>() as Ref<HTMLElement | undefined>
const lineOpt = ref<EChartsOption>({ ...chartBase })
const pieOpt = ref<EChartsOption>({ ...chartBase, tooltip: { trigger: 'item' } })

useEcharts(lineEl, lineOpt)
useEcharts(pieEl, pieOpt)

onMounted(async () => {
  try {
    const [st, fund] = await Promise.all([statisMch(shopNum.value), fundOverview(shopNum.value)])
    cards.value = [
      { label: '累计营收(元)', value: money(st.totalIncome), price: true, icon: 'Money' },
      { label: '累计手续费(元)', value: money(st.totalServiceFee), price: false, icon: 'Tickets' },
      { label: '实际净收益(元)', value: money(st.actualIncome), price: true, icon: 'TrendCharts' },
      { label: '可提现余额(元)', value: money(fund.fund.balance), price: true, icon: 'Wallet' },
    ]
    lineOpt.value = {
      ...chartBase,
      xAxis: { type: 'category', data: st.sevenDayTradeData.map((d) => d.date.slice(5)) },
      yAxis: { type: 'value' },
      series: [{ type: 'line', name: '交易金额', data: st.sevenDayTradeData.map((d) => d.tradeMoney), smooth: true, areaStyle: { opacity: 0.08 } }],
    }
    pieOpt.value = {
      ...chartBase,
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, icon: 'circle', itemWidth: 10, itemHeight: 10 },
      series: [
        {
          type: 'pie',
          radius: ['42%', '68%'],
          center: ['50%', '46%'],
          data: st.channelTradeData.map((c) => ({ name: c.wayCode, value: c.tradeMoney })),
          label: { show: false },
        },
      ],
    }
  } catch {
    /* 统计加载失败时保持占位 */
  }
})
</script>

<style scoped>
.chart-card {
  padding: 14px 18px;
}

.chart-head {
  font-weight: 600;
  margin-bottom: 8px;
}

.chart {
  height: 260px;
}

.quick {
  padding: 14px 18px;
}

.qrow {
  display: flex;
  gap: 10px;
  padding: 6px 0;
}
</style>
