<template>
  <span class="state-tag" :class="`st-${tone}`">
    <i class="dot" aria-hidden="true"></i>{{ text }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/** 状态字典 → 圆点标签。kind 区分各状态机(契约 4.7/README 五) */
const props = defineProps<{
  kind: 'pay' | 'biz' | 'refund' | 'shop' | 'withdraw' | 'goods' | 'channel'
  value: number | string
  effect?: 'light' | 'plain' | 'dark'
}>()

type Tone = 'ok' | 'wait' | 'bad' | 'off' | 'todo'

const table: Record<string, Record<number, { text: string; tone: Tone }>> = {
  pay: {
    0: { text: '支付成功', tone: 'ok' },
    1: { text: '支付失败', tone: 'bad' },
    2: { text: '已退款', tone: 'wait' },
    3: { text: '订单关闭', tone: 'off' },
    4: { text: '待支付', tone: 'todo' },
  },
  biz: {
    0: { text: '待支付', tone: 'todo' },
    1: { text: '待发货', tone: 'wait' },
    2: { text: '已发货', tone: 'ok' },
    3: { text: '已完成', tone: 'off' },
    4: { text: '已关闭', tone: 'off' },
  },
  refund: {
    0: { text: '待审核', tone: 'wait' },
    1: { text: '处理中', tone: 'todo' },
    2: { text: '已完成', tone: 'ok' },
    3: { text: '已拒绝', tone: 'bad' },
  },
  shop: {
    0: { text: '待审核', tone: 'wait' },
    1: { text: '正常', tone: 'ok' },
    2: { text: '已禁用', tone: 'bad' },
  },
  withdraw: {
    0: { text: '审核中', tone: 'wait' },
    1: { text: '已到账', tone: 'ok' },
    2: { text: '已驳回', tone: 'bad' },
  },
  goods: {
    0: { text: '下架', tone: 'off' },
    1: { text: '上架', tone: 'ok' },
  },
  channel: {
    0: { text: '已关闭', tone: 'off' },
    1: { text: '开启', tone: 'ok' },
  },
}

const conf = computed(
  () => table[props.kind]?.[Number(props.value)] ?? { text: String(props.value), tone: 'off' as Tone },
)
const text = computed(() => conf.value.text)
const tone = computed(() => conf.value.tone)
</script>

<style scoped>
.state-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px 2px 8px;
  border-radius: 999px;
  font-size: 12.5px;
  font-weight: 500;
  line-height: 20px;
  white-space: nowrap;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex: none;
  box-shadow: 0 0 0 3px color-mix(in srgb, currentColor 16%, transparent);
}

/* 语义配色:文字与圆点同色,底为柔和 tint */
.st-ok {
  color: var(--mx-green);
  background: #eaf5ee;
}

.st-wait {
  color: var(--mx-amber);
  background: #fdf4e6;
}

.st-bad {
  color: var(--mx-red);
  background: var(--mx-red-tint);
}

.st-off {
  color: var(--mx-ink-3);
  background: #f4f2ef;
}

.st-todo {
  color: var(--mx-red-deep);
  background: #fdeeec;
}
</style>
