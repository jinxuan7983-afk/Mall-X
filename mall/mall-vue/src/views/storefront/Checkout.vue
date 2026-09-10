<template>
  <div>
    <el-steps :active="2" align-center class="steps">
      <el-step title="确认订单" />
      <el-step title="在线支付" />
      <el-step title="商户发货" />
      <el-step title="确认收货" />
    </el-steps>

    <div v-for="(grp, gi) in groups" :key="grp.shopNum" class="mx-card group">
      <div class="ghead">
        <el-icon><Shop /></el-icon>
        <b>{{ grp.shopName }}</b>
        <span class="muted">订单 {{ gi + 1 }}</span>
        <em class="price"><span class="yen">¥</span>{{ money(grp.total) }}</em>
      </div>
      <div v-for="it in grp.items" :key="it.goodsNum" class="line">
        <div class="pic"><GoodsPic :name="it.goodsName" :seed="it.picSeed" /></div>
        <div class="nm">{{ it.goodsName }}</div>
        <span class="muted num">x{{ it.count }}</span>
        <span class="price"><span class="yen">¥</span>{{ money(it.price * it.count) }}</span>
      </div>
    </div>

    <div class="mx-card addr">
      <h3>收货信息</h3>
      <el-form :model="form" label-width="76px" style="max-width: 460px">
        <el-form-item label="收货人" required>
          <el-input v-model="form.receiverName" placeholder="姓名" />
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="form.receiverPhone" placeholder="11位手机号" />
        </el-form-item>
        <el-form-item label="收货地址" required>
          <el-input v-model="form.receiverAddress" type="textarea" :rows="2" placeholder="省市区 + 详细地址" />
        </el-form-item>
      </el-form>
    </div>

    <div class="mx-card settle">
      <span>共 <b class="num">{{ totalCount }}</b> 件,拆分为 <b class="num">{{ groups.length }}</b> 笔订单,合计</span>
      <span class="price total"><span class="yen">¥</span>{{ money(totalMoney) }}</span>
      <el-button type="primary" size="large" :loading="submitting" @click="submit">提交订单并支付</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Shop } from '@element-plus/icons-vue'
import { orderCreate, payCreate } from '@/api/modules'
import { money } from '@/api/client'
import { useCartStore } from '@/stores/cart'
import { useSessionStore } from '@/stores/session'
import GoodsPic from '@/components/GoodsPic.vue'

const route = useRoute()
const router = useRouter()
const cart = useCartStore()
const session = useSessionStore()

/** 立即购买:商品由 GoodsDetail 写入 sessionStorage,直达结算,不经过购物车 */
const fromBuy = route.query.from === 'buy'
const buyItem = (() => {
  try {
    return JSON.parse(sessionStorage.getItem('mallx_buynow') || 'null') as
      { goodsNum: string; goodsName: string; price: number; count: number; stock: number; shopNum: string; shopName: string; picSeed: string } | null
  } catch {
    return null
  }
})()

const groups = computed(() => {
  if (fromBuy && buyItem) {
    return [{ shopNum: buyItem.shopNum, shopName: buyItem.shopName, total: buyItem.price * buyItem.count, items: [buyItem] }]
  }
  return cart.groups
})
const totalCount = computed(() => (fromBuy && buyItem ? buyItem.count : cart.count))
const totalMoney = computed(() => groups.value.reduce((s, g) => s + g.total, 0))
const submitting = ref(false)
const form = reactive({
  receiverName: '',
  receiverPhone: '',
  receiverAddress: '',
})

async function submit() {
  if (!form.receiverName || !form.receiverPhone || !form.receiverAddress) {
    ElMessage.warning('请完整填写收货信息')
    return
  }
  submitting.value = true
  try {
    const userNum = session.user!.userNum
    const orders: { orderNum: string; shopNum: string; totalMoney: number }[] = []
    for (const grp of groups.value) {
      const r = await orderCreate({
        userNum,
        shopNum: grp.shopNum,
        items: grp.items.map((i) => ({ goodsNum: i.goodsNum, count: i.count })),
        receiverName: form.receiverName,
        receiverPhone: form.receiverPhone,
        receiverAddress: form.receiverAddress,
      })
      orders.push({ orderNum: r.orderNum, shopNum: grp.shopNum, totalMoney: r.totalMoney })
    }
    // 记录商户名供收银台展示(pay/create 需要 shopName);按订单号为键
    const payctx: Record<string, { shopNum: string; shopName: string; subject: string; totalMoney: number }> = {}
    orders.forEach((o, i) => {
      payctx[o.orderNum] = {
        shopNum: o.shopNum,
        shopName: groups.value[i]?.shopName || o.shopNum,
        subject: groups.value[i]?.items.map((x) => x.goodsName).join('、').slice(0, 40) || '商品',
        totalMoney: o.totalMoney,
      }
    })
    sessionStorage.setItem('mallx_payctx', JSON.stringify(payctx))
    if (fromBuy) sessionStorage.removeItem('mallx_buynow')
    else cart.clear()
    router.push({ name: 'cashier', query: { orders: orders.map((o) => o.orderNum).join(',') } })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.steps {
  background: #fff;
  border: 1px solid var(--mx-line);
  border-radius: var(--mx-radius);
  padding: 18px 0 8px;
  margin-bottom: 16px;
}

.group {
  margin-bottom: 14px;
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
  color: var(--mx-ink-3);
  font-size: 13px;
}

.ghead em {
  margin-left: auto;
  font-style: normal;
  font-size: 16px;
}

.line {
  display: grid;
  grid-template-columns: 64px 1fr 60px 110px;
  gap: 16px;
  align-items: center;
  padding: 12px 18px;
  border-bottom: 1px dashed var(--mx-line);
}

.line:last-child {
  border-bottom: none;
}

.pic {
  width: 64px;
  height: 64px;
  border-radius: var(--mx-radius);
  overflow: hidden;
}

.addr {
  padding: 18px 22px;
  margin-bottom: 14px;
}

.addr h3 {
  margin: 0 0 14px;
  font-size: 15px;
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
</style>
