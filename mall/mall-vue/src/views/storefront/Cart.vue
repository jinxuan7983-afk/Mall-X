<template>
  <div>
    <h2 class="mx-page-title">购物车 <span class="sub">{{ cart.count }} 件商品</span></h2>

    <div v-if="cart.items.length === 0" class="mx-empty mx-card">
      <p>购物车还是空的</p>
      <el-button type="primary" @click="$router.push('/')">去逛逛</el-button>
    </div>

    <template v-else>
      <div v-for="grp in cart.groups" :key="grp.shopNum" class="mx-card group">
        <div class="ghead">
          <el-icon><Shop /></el-icon>
          <b>{{ grp.shopName }}</b>
          <span class="muted num">小计 <em class="price"><span class="yen">¥</span>{{ money(grp.total) }}</em></span>
        </div>
        <div v-for="it in grp.items" :key="it.goodsNum" class="line">
          <div class="pic"><GoodsPic :name="it.goodsName" :seed="it.picSeed" /></div>
          <div class="mid">
            <div class="nm">{{ it.goodsName }}</div>
            <div class="price"><span class="yen">¥</span>{{ money(it.price) }}</div>
          </div>
          <el-input-number :model-value="it.count" :min="1" :max="it.stock" size="small" @change="(v?: number) => cart.setCount(it.goodsNum, v || 1)" />
          <span class="price sum"><span class="yen">¥</span>{{ money(it.price * it.count) }}</span>
          <el-button link type="danger" @click="cart.remove(it.goodsNum)">删除</el-button>
        </div>
      </div>

      <div class="mx-card settle">
        <span>共 <b class="num">{{ cart.count }}</b> 件,合计</span>
        <span class="price total"><span class="yen">¥</span>{{ money(cart.total) }}</span>
        <el-button type="primary" size="large" :disabled="cart.items.length === 0" @click="toCheckout">去结算</el-button>
      </div>
      <p class="hint">多商户商品将按店铺拆分为多笔订单分别结算支付。</p>
    </template>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Shop } from '@element-plus/icons-vue'
import { useCartStore } from '@/stores/cart'
import { money } from '@/api/client'
import GoodsPic from '@/components/GoodsPic.vue'

const router = useRouter()
const cart = useCartStore()

function toCheckout() {
  router.push('/checkout')
}
</script>

<style scoped>
.sub {
  font-size: 13px;
  font-weight: 400;
  color: var(--mx-ink-3);
  margin-left: 8px;
}

.group {
  margin-bottom: 14px;
  overflow: hidden;
}

.ghead {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--mx-line);
  background: #fcfbf9;
}

.muted {
  margin-left: auto;
  color: var(--mx-ink-3);
  font-size: 13px;
}

.line {
  display: grid;
  grid-template-columns: 76px 1fr 130px 110px 48px;
  gap: 16px;
  align-items: center;
  padding: 14px 18px;
  border-bottom: 1px dashed var(--mx-line);
}

.line:last-child {
  border-bottom: none;
}

.pic {
  width: 76px;
  height: 76px;
  border-radius: var(--mx-radius);
  overflow: hidden;
}

.nm {
  margin-bottom: 6px;
}

.sum {
  text-align: right;
}

.settle {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  padding: 14px 20px;
}

.total {
  font-size: 26px;
}

.hint {
  color: var(--mx-ink-3);
  font-size: 12px;
  text-align: right;
}
</style>
