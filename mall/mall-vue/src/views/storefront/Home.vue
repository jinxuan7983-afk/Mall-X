<template>
  <div>
    <section class="hero mx-card">
      <div class="hero-main">
        <h1>多商家入驻,一站式购齐</h1>
        <p>Mall·X 汇聚数码、生鲜、服饰等商户,下单支付到收货全程可追踪。</p>
      </div>
      <div class="cats">
        <button
          v-for="c in [{ id: 0, categoryName: '全部' }, ...categories]"
          :key="c.id"
          class="cat"
          :class="{ on: categoryId === c.id }"
          @click="pickCat(c.id)"
        >
          {{ c.categoryName }}
        </button>
      </div>
    </section>

    <section class="grid" v-loading="loading">
      <router-link v-for="g in goods" :key="g.goodsNum" class="item mx-card" :to="`/goods/${g.goodsNum}`">
        <div class="pic"><GoodsPic :name="g.goodsName" :seed="g.goodsNum" :pic="g.picUrl || undefined" /></div>
        <div class="info">
          <div class="nm">{{ g.goodsName }}</div>
          <div class="shop">{{ g.shopName || g.shopNum }}</div>
          <div class="row">
            <span class="price"><span class="yen">¥</span>{{ money(g.price) }}</span>
            <span class="sales">已售 {{ g.sales }}</span>
          </div>
        </div>
      </router-link>
      <div v-if="!loading && goods.length === 0" class="mx-empty" style="grid-column: 1/-1">
        没有找到相关商品,换个关键词试试
      </div>
    </section>

    <div class="pager" v-if="total > pageSize">
      <el-pagination
        background
        layout="prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        @current-change="switchPage"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { categoryList, goodsList } from '@/api/modules'
import { money } from '@/api/client'
import type { Category, Goods } from '@/types'
import GoodsPic from '@/components/GoodsPic.vue'

const route = useRoute()
const router = useRouter()

const categories = ref<Category[]>([])
const goods = ref<Goods[]>([])
const loading = ref(false)
const categoryId = ref(Number(route.query.categoryId || 0))
const kw = ref(String(route.query.kw || ''))
const page = ref(1)
const pageSize = 12
const total = ref(0)

async function loadCats() {
  try {
    const r = await categoryList()
    categories.value = r.records
  } catch {
    /* 类目加载失败不阻塞商品列表 */
  }
}

async function loadGoods() {
  loading.value = true
  try {
    const r = await goodsList({
      pageNum: page.value,
      pageSize,
      goodsName: kw.value || undefined,
      categoryId: categoryId.value || undefined,
      goodsState: 1,
    })
    goods.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

function pickCat(id: number) {
  categoryId.value = id
  page.value = 1
}

function switchPage(p: number) {
  page.value = p
}

watch([categoryId, kw], loadGoods)
watch(
  () => route.query,
  (q) => {
    kw.value = String(q.kw || '')
    categoryId.value = Number(q.categoryId || 0)
    page.value = 1
  },
)

onMounted(() => {
  loadCats()
  loadGoods()
})
</script>

<style scoped>
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 30px;
  margin-bottom: 18px;
  flex-wrap: wrap;
  border: none;
  border-radius: var(--mx-radius-lg);
  background: linear-gradient(120deg, #2b2622 0%, var(--mx-side) 55%, #34211d 100%);
  color: #fff;
  position: relative;
  overflow: hidden;
}

.hero::before {
  content: '';
  position: absolute;
  width: 300px;
  height: 300px;
  right: -80px;
  top: -120px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(226, 58, 46, 0.4), transparent 70%);
  filter: blur(50px);
  pointer-events: none;
}

.hero-main {
  position: relative;
}

.hero-main h1 {
  margin: 0 0 8px;
  font-size: 25px;
  letter-spacing: 0.03em;
}

.hero-main p {
  margin: 0;
  color: rgba(255, 255, 255, 0.62);
  font-size: 13px;
}

.cats {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  position: relative;
}

.cat {
  border: 1px solid rgba(255, 255, 255, 0.22);
  background: rgba(255, 255, 255, 0.07);
  padding: 7px 16px;
  border-radius: 999px;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.85);
  font-size: 13px;
  transition: all 0.15s;
  backdrop-filter: blur(4px);
}

.cat:hover {
  border-color: var(--mx-red);
  color: #fff;
  background: rgba(226, 58, 46, 0.32);
}

.cat.on {
  background: linear-gradient(135deg, var(--mx-red), var(--mx-red-deep));
  border-color: transparent;
  color: #fff;
  box-shadow: 0 4px 12px rgba(226, 58, 46, 0.4);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(212px, 1fr));
  gap: 14px;
  min-height: 200px;
}

.item {
  overflow: hidden;
  border-radius: var(--mx-radius-lg);
  animation: mx-rise 0.4s ease both;
  transition: box-shadow 0.2s, transform 0.2s, border-color 0.2s;
}

.item:hover {
  box-shadow: var(--mx-shadow-lg);
  transform: translateY(-3px);
  border-color: #e3d9d2;
}

.item :deep(.pic),
.item :deep(.pic *) {
  transition: transform 0.25s ease;
}

.item:hover :deep(.pic) {
  transform: scale(1.03);
}

.pic {
  aspect-ratio: 1;
}

.info {
  padding: 12px 14px 14px;
}

.nm {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shop {
  color: var(--mx-ink-3);
  font-size: 12px;
  margin: 3px 0 8px;
}

.row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.price {
  font-size: 20px;
}

.sales {
  color: var(--mx-ink-3);
  font-size: 12px;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 22px;
}
</style>
