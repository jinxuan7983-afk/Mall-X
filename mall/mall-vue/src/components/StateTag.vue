<template>
  <el-tag :type="tagType" :effect="effect" size="small" disable-transitions>{{ text }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/** 状态字典 → 标签。kind 区分各状态机(契约 4.7/README 五) */
const props = defineProps<{ kind: 'pay' | 'biz' | 'refund' | 'shop' | 'withdraw' | 'goods' | 'channel'; value: number | string; effect?: 'light' | 'plain' | 'dark' }>()

const table: Record<string, Record<number, { text: string; type: 'success' | 'warning' | 'danger' | 'info' | 'primary' }>> = {
  pay: {
    0: { text: '支付成功', type: 'success' },
    1: { text: '支付失败', type: 'danger' },
    2: { text: '已退款', type: 'warning' },
    3: { text: '订单关闭', type: 'info' },
    4: { text: '待支付', type: 'primary' },
  },
  biz: {
    0: { text: '待支付', type: 'primary' },
    1: { text: '待发货', type: 'warning' },
    2: { text: '已发货', type: 'success' },
    3: { text: '已完成', type: 'success' },
    4: { text: '已关闭', type: 'info' },
  },
  refund: {
    0: { text: '审核中', type: 'warning' },
    1: { text: '退款成功', type: 'success' },
    2: { text: '退款失败', type: 'danger' },
  },
  shop: {
    0: { text: '待审核', type: 'warning' },
    1: { text: '正常', type: 'success' },
    2: { text: '已禁用', type: 'danger' },
  },
  withdraw: {
    0: { text: '审核中', type: 'warning' },
    1: { text: '已到账', type: 'success' },
    2: { text: '已驳回', type: 'danger' },
  },
  goods: {
    0: { text: '下架', type: 'info' },
    1: { text: '上架', type: 'success' },
  },
  channel: {
    0: { text: '已关闭', type: 'info' },
    1: { text: '开启', type: 'success' },
  },
}

const conf = computed(() => table[props.kind]?.[Number(props.value)] ?? { text: String(props.value), type: 'info' as const })
const tagType = computed(() => conf.value.type)
const text = computed(() => conf.value.text)
const effect = computed(() => props.effect ?? 'light')
</script>
