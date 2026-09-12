<template>
  <div class="gp" :style="style">
    <img
      v-if="showImg"
      :src="src"
      :alt="name"
      class="gp-img"
      :class="{ 'gp-dim': !imgLoaded }"
      loading="lazy"
      @load="imgLoaded = true"
      @error="onError"
    />
    <span class="gp-label" :class="{ 'gp-hide': imgLoaded }">{{ label }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{ name: string; seed?: string; pic?: string; big?: boolean }>()

/**
 * 图片地址优先级：商品自定义图片(pic，如管理端上传的 /uploads/goods/xxx.jpg)
 * → 约定式本地图 /goods/{seed}.jpg；加载失败回退渐变占位。
 * 说明：图片元素与占位文字均不因失败而移除（仅切换显隐），
 * 避免外部测量脚本在元素已被卸载时读取位置信息而报错；
 * 失败的资源会在会话内缓存，同一地址不重复发起请求。
 */
const failedSources = new Set<string>()

const imgLoaded = ref(false)
const imgFailed = ref(false)
const src = computed(() => {
  const custom = props.pic
  if (custom && custom.trim()) return custom.trim()
  return props.seed ? `/goods/${props.seed}.jpg` : ''
})

const showImg = computed(() => {
  const url = src.value
  return !!url && !failedSources.has(url) && !imgFailed.value
})

/** 地址变化(如替换图片后)时重置加载状态，确保新图即时展示 */
watch(src, () => {
  imgFailed.value = false
  imgLoaded.value = false
})

function onError() {
  imgFailed.value = true
  if (src.value) failedSources.add(src.value)
}

const palettes = [
  ['#f3e2d0', '#d8a87b'],
  ['#e8e4f4', '#9a8fc2'],
  ['#dce9df', '#84b59a'],
  ['#f4e3e0', '#d98d84'],
  ['#e2ecf4', '#8fb4cd'],
  ['#f0eac9', '#c2b06a'],
]

const style = computed(() => {
  const s = (props.seed || props.name || 'x').split('').reduce((a, c) => a + c.charCodeAt(0), 0)
  const [c1, c2] = palettes[s % palettes.length]
  return {
    background: `linear-gradient(135deg, ${c1}, ${c2})`,
    fontSize: props.big ? '44px' : '22px',
  }
})

const label = computed(() => (props.name || '品').slice(0, 1))
</script>

<style scoped>
.gp {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(35, 31, 28, 0.72);
  font-weight: 600;
  user-select: none;
  overflow: hidden;
}

.gp-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: opacity 0.25s ease;
}

.gp-dim {
  opacity: 0;
}

.gp-label {
  position: relative;
  transition: opacity 0.25s ease;
}

.gp-hide {
  opacity: 0;
}
</style>
