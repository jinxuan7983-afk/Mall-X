<template>
  <div>
    <div class="mx-toolbar">
      <el-date-picker
        v-model="range"
        type="datetimerange"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        value-format="YYYY-MM-DD HH:mm:ss"
        @change="load"
      />
      <el-button @click="range = null; load()">近7天</el-button>
      <span class="tip">不选时间默认统计近 7 天数据。</span>
    </div>

    <el-row :gutter="14">
      <el-col :span="6" v-for="c in cards" :key="c.label">
        <div class="mx-card mx-stat">
          <div class="label">{{ c.label }}</div>
          <div class="value" :class="{ price: c.price }">{{ c.value }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="14" style="margin-top: 14px">
      <el-col :span="16">
        <div class="mx-card chart-card">
          <div class="chart-head">近7日交易金额与笔数</div>
          <div ref="lineEl" class="chart"></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="mx-card chart-card">
          <div class="chart-head">渠道交易分布</div>
          <div ref="pieEl" class="chart"></div>
        </div>
      </el-col>
    </el-row>

    <h3 class="sub-title">分渠道明细</h3>
    <el-table :data="channelData" border stripe size="small">
      <el-table-column prop="wayCode" label="渠道" width="120" />
      <el-table-column label="交易金额" align="right">
        <template #default="{ row }"><span class="price">¥{{ money(row.tradeMoney) }}</span></template>
      </el-table-column>
      <el-table-column prop="tradeCount" label="交易笔数" align="right" />
      <el-table-column label="手续费" align="right">
        <template #default="{ row }"><span class="num">¥{{ money(row.fee) }}</span></template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, type Ref } from 'vue'
import { statisMch } from '@/api/modules'
import { money } from '@/api/client'
import { useSessionStore } from '@/stores/session'
import { useEcharts, chartBase } from '@/composables/useEcharts'
import type { EChartsOption } from 'echarts'

const session = useSessionStore()
const shopNum = computed(() => session.mch!.shopNum)

const range = ref<[string, string] | null>(null)
const cards = ref([
  { label: '累计营收(元)', value: '—', price: true },
  { label: '累计手续费(元)', value: '—', price: false },
  { label: '实际净收益(元)', value: '—', price: true },
  { label: '交易成功率', value: '—', price: false },
])
const channelData = ref<{ wayCode: string; tradeMoney: number; tradeCount: number; fee?: number }[]>([])

const lineEl = ref<HTMLElement>() as Ref<HTMLElement | undefined>
const pieEl = ref<HTMLElement>() as Ref<HTMLElement | undefined>
const lineOpt = ref<EChartsOption>({ ...chartBase })
const pieOpt = ref<EChartsOption>({ ...chartBase, tooltip: { trigger: 'item' } })

useEcharts(lineEl, lineOpt)
useEcharts(pieEl, pieOpt)

async function load() {
  const [startTime, endTime] = range.value || [undefined, undefined]
  const st = await statisMch(shopNum.value, startTime, endTime)
  cards.value = [
    { label: '累计营收(元)', value: money(st.totalIncome), price: true },
    { label: '累计手续费(元)', value: money(st.totalServiceFee), price: false },
    { label: '实际净收益(元)', value: money(st.actualIncome), price: true },
    { label: '交易成功率', value: `${st.successRate}%`, price: false },
  ]
  channelData.value = st.channelTradeData
  lineOpt.value = {
    ...chartBase,
    xAxis: { type: 'category', data: st.sevenDayTradeData.map((d) => d.date.slice(5)) },
    yAxis: [{ type: 'value', name: '金额' }, { type: 'value', name: '笔数', minInterval: 1, splitLine: { show: false } }],
    series: [
      { type: 'bar', name: '交易金额', data: st.sevenDayTradeData.map((d) => d.tradeMoney), barWidth: 18, itemStyle: { color: '#e23a2e', borderRadius: [3, 3, 0, 0] } },
      { type: 'line', name: '交易笔数', yAxisIndex: 1, data: st.sevenDayTradeData.map((d) => d.tradeCount), smooth: true },
    ],
  }
  pieOpt.value = {
    ...chartBase,
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 10, itemHeight: 10 },
    series: [{ type: 'pie', radius: ['42%', '68%'], center: ['50%', '46%'], data: st.channelTradeData.map((c) => ({ name: c.wayCode, value: c.tradeMoney })), label: { show: false } }],
  }
}

onMounted(load)
</script>

<style scoped>
.tip {
  color: var(--mx-ink-3);
  font-size: 12px;
}

.chart-card {
  padding: 14px 18px;
}

.chart-head {
  font-weight: 600;
  margin-bottom: 8px;
}

.chart {
  height: 280px;
}

.sub-title {
  margin: 20px 0 12px;
  font-size: 15px;
}
</style>
