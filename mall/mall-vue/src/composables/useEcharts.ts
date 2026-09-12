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
    ro.value = new ResizeObserver(() => {
      // 防护:组件卸载/元素脱离文档/尺寸为 0 时跳过。
      // ECharts 内部 resize 会读取 getBoundingClientRect,元素已移除时会抛
      // "Cannot read properties of null (reading 'getBoundingClientRect')"。
      try {
        const dom = el.value
        if (!chart || chart.isDisposed() || !dom || !dom.isConnected) return
        const rect = dom.getBoundingClientRect()
        if (rect.width === 0 && rect.height === 0) return
        chart.resize()
      } catch {
        /* 第三方测量异常静默:不影响业务渲染 */
      }
    })
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
