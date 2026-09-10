<template>
  <div class="gp" :style="style">{{ label }}</div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ name: string; seed?: string; big?: boolean }>()

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
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(35, 31, 28, 0.72);
  font-weight: 600;
  user-select: none;
}
</style>
