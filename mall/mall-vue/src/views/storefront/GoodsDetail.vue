<template>
  <div v-if="goods" class="detail">
    <div class="mx-card left"><GoodsPic :name="goods.goodsName" :seed="goods.goodsNum" :pic="goods.picUrl || undefined" big /></div>
    <div class="right">
      <h1>{{ goods.goodsName }}</h1>
      <div class="pricebar">
        <span class="price big"><span class="yen">¥</span>{{ money(goods.price) }}</span>
        <span class="muted">已售 {{ goods.sales }}</span>
      </div>
      <el-descriptions :column="1" border size="small" style="margin: 18px 0">
        <el-descriptions-item label="店铺">{{ goods.shopName || goods.shopNum }}</el-descriptions-item>
        <el-descriptions-item label="库存">
          <span :class="{ warn: goods.stock < 10 }">{{ goods.stock }} 件</span>
        </el-descriptions-item>
        <el-descriptions-item label="发货">商户直发 · 支付后48小时内发货</el-descriptions-item>
      </el-descriptions>
      <div class="buyrow">
        <el-input-number v-model="count" :min="1" :max="goods.stock" />
        <el-button type="primary" size="large" @click="addCart">加入购物车</el-button>
        <el-button size="large" @click="buyNow">立即购买</el-button>
      </div>
      <p class="hint">库存紧张时以下单为准,超时未支付订单将自动关闭并回补库存。</p>
    </div>
  </div>
  <div v-else class="mx-empty" v-loading="loading" style="min-height: 300px"></div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { goodsDetail } from '@/api/modules'
import { money } from '@/api/client'
import { useCartStore } from '@/stores/cart'
import { useSessionStore } from '@/stores/session'
import type { Goods } from '@/types'
import GoodsPic from '@/components/GoodsPic.vue'

const route = useRoute()
const router = useRouter()
const cart = useCartStore()
const session = useSessionStore()

const goods = ref<Goods | null>(null)
const loading = ref(true)
const count = ref(1)

async function load() {
  loading.value = true
  try {
    const r = await goodsDetail(String(route.params.goodsNum))
    goods.value = r.goods
  } finally {
    loading.value = false
  }
}

/** 未登录不允许加购:弹出登录提示,确认后带跳转地址去登录页 */
function requireLogin(): boolean {
  if (session.isUser) return true
  ElMessageBox.confirm('登录后才能加入购物车,现在去登录吗?', '请先登录', {
    confirmButtonText: '去登录',
    cancelButtonText: '再逛逛',
    type: 'info',
  })
    .then(() => router.push({ name: 'userLogin', query: { redirect: route.fullPath } }))
    .catch(() => {})
  return false
}

function addCart() {
  if (!goods.value) return
  if (!requireLogin()) return
  cart.add(
    {
      goodsNum: goods.value.goodsNum,
      goodsName: goods.value.goodsName,
      price: goods.value.price,
      stock: goods.value.stock,
      shopNum: goods.value.shopNum,
      shopName: String(goods.value.shopName || goods.value.shopNum),
      picSeed: goods.value.goodsNum,
    },
    count.value,
  )
  ElMessage.success('已加入购物车')
}

function buyNow() {
  if (!requireLogin()) return
  if (!goods.value) return
  // 立即购买:走 sessionStorage 直购通道直达结算页,不写入购物车
  sessionStorage.setItem(
    'mallx_buynow',
    JSON.stringify({
      goodsNum: goods.value.goodsNum,
      goodsName: goods.value.goodsName,
      price: goods.value.price,
      count: count.value,
      stock: goods.value.stock,
      shopNum: goods.value.shopNum,
      shopName: String(goods.value.shopName || goods.value.shopNum),
      picSeed: goods.value.goodsNum,
    }),
  )
  router.push({ path: '/checkout', query: { from: 'buy' } })
}

onMounted(load)
</script>

<style scoped>
.detail {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 24px;
}

.left {
  aspect-ratio: 1;
  overflow: hidden;
}

.right h1 {
  font-size: 22px;
  margin: 4px 0 14px;
}

.pricebar {
  display: flex;
  align-items: baseline;
  gap: 16px;
  background: var(--mx-red-tint);
  border-radius: var(--mx-radius);
  padding: 14px 18px;
}

.price.big {
  font-size: 32px;
}

.muted {
  color: var(--mx-ink-3);
  font-size: 12px;
}

.warn {
  color: var(--mx-red);
  font-weight: 600;
}

.buyrow {
  display: flex;
  gap: 12px;
  margin-top: 6px;
}

.hint {
  color: var(--mx-ink-3);
  font-size: 12px;
  margin-top: 18px;
}

@media (max-width: 760px) {
  .detail {
    grid-template-columns: 1fr;
  }
}
</style>
