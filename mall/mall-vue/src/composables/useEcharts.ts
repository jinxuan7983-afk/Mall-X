import * as echarts from 'echarts'
import { onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

/** ECharts 挂载:自动 resize 与数据重渲染 */
export function useEcharts(
  el: Ref<HTMLElement | undefined>,
  option: Ref<echarts.EChartsOption>,
) {
  let chart: echarts.ECharts | null = null
  const ro = ref<ResizeObserver | null>(null)

  const render = () => {
    if (chart && el.value) chart.setOption(option.value, true)
  }

  onMounted(() => {
    if (!el.value) return
    chart = echarts.init(el.value)
    chart.setOption(option.value)
    ro.value = new ResizeObserver(() => chart?.resize())
    ro.value.observe(el.value)
  })

  watch(option, render, { deep: true })

  onBeforeUnmount(() => {
    ro.value?.disconnect()
    chart?.dispose()
    chart = null
  })

  return { render }
}

/** 全局图表配色:朱红 + 墨 + 陶灰系 */
export const chartPalette = ['#e23a2e', '#231f1c', '#c2a15c', '#8fb4cd', '#84b59a', '#9a8fc2']

export const chartBase: echarts.EChartsOption = {
  color: chartPalette,
  tooltip: { trigger: 'axis' },
  grid: { left: 12, right: 16, top: 36, bottom: 8, containLabel: true },
}
