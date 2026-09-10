<template>
  <div>
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
          <div class="chart-head">近7日平台交易走势</div>
          <div ref="lineEl" class="chart"></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="mx-card chart-card">
          <div class="chart-head">支付渠道交易占比</div>
          <div ref="pieEl" class="chart"></div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, type Ref } from 'vue'
import { statisPlatform } from '@/api/modules'
import { money } from '@/api/client'
import { useEcharts, chartBase } from '@/composables/useEcharts'
import type { EChartsOption } from 'echarts'

const cards = ref([
  { label: '平台累计交易额(元)', value: '—', price: true },
  { label: '累计交易笔数', value: '—', price: false },
  { label: '支付渠道数', value: '—', price: false },
  { label: '今日交易额(元)', value: '—', price: true },
])

const lineEl = ref<HTMLElement>() as Ref<HTMLElement | undefined>
const pieEl = ref<HTMLElement>() as Ref<HTMLElement | undefined>
const lineOpt = ref<EChartsOption>({ ...chartBase })
const pieOpt = ref<EChartsOption>({ ...chartBase, tooltip: { trigger: 'item' } })

useEcharts(lineEl, lineOpt)
useEcharts(pieEl, pieOpt)

onMounted(async () => {
  const st = await statisPlatform()
  const today = st.sevenDayData[st.sevenDayData.length - 1]
  cards.value = [
    { label: '平台累计交易额(元)', value: money(st.totalMoney), price: true },
    { label: '累计交易笔数', value: String(st.totalCount), price: false },
    { label: '支付渠道数', value: String(st.channelRatio.length), price: false },
    { label: '今日交易额(元)', value: money(today?.tradeMoney ?? 0), price: true },
  ]
  lineOpt.value = {
    ...chartBase,
    xAxis: { type: 'category', data: st.sevenDayData.map((d) => d.date.slice(5)) },
    yAxis: [{ type: 'value', name: '金额' }, { type: 'value', name: '笔数', minInterval: 1, splitLine: { show: false } }],
    series: [
      { type: 'bar', name: '交易金额', data: st.sevenDayData.map((d) => d.tradeMoney), barWidth: 20, itemStyle: { color: '#e23a2e', borderRadius: [3, 3, 0, 0] } },
      { type: 'line', name: '交易笔数', yAxisIndex: 1, data: st.sevenDayData.map((d) => d.tradeCount), smooth: true },
    ],
  }
  pieOpt.value = {
    ...chartBase,
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 10, itemHeight: 10 },
    series: [{ type: 'pie', radius: ['42%', '68%'], center: ['50%', '46%'], data: st.channelRatio.map((c) => ({ name: c.wayCode, value: c.tradeMoney })), label: { show: false } }],
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
  height: 300px;
}
</style>
