<template>
  <div class="store">
    <header class="topbar">
      <div class="inner">
        <router-link to="/" class="brand">
          <span class="seal">M·X</span>
          <span class="name">Mall·X <i>多商家电商平台</i></span>
        </router-link>
        <div class="search">
          <el-input
            v-model="kw"
            placeholder="搜索商品,回车确认"
            clearable
            @keyup.enter="goSearch"
          >
            <template #append>
              <el-button :icon="Search" @click="goSearch" />
            </template>
          </el-input>
        </div>
        <nav class="links">
          <router-link to="/my/orders" class="lk">我的订单</router-link>
          <router-link to="/mch/login" class="lk">商户入驻</router-link>
          <router-link to="/admin/login" class="lk">管理中心</router-link>
          <router-link to="/cart" class="lk cart">
            <el-badge :value="cart.count" :hidden="cart.count === 0" type="danger">
              <el-icon :size="18"><ShoppingCart /></el-icon>
            </el-badge>
            购物车
          </router-link>
          <template v-if="session.user">
            <el-dropdown>
              <span class="lk user">
                <el-icon><User /></el-icon>
                {{ session.user.username }}
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="$router.push('/my/orders')">我的订单</el-dropdown-item>
                  <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <router-link v-else to="/user/login" class="lk strong">登录 / 注册</router-link>
        </nav>
      </div>
    </header>
    <main class="body">
      <router-view v-slot="{ Component }">
        <transition name="mx-fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
    <footer class="foot">
      <div class="inner">
        <span>Mall·X 多商家电商平台 · 行业工程实践课程项目</span>
        <span>Spring Cloud 微服务 · Vue3 + TypeScript + Element Plus</span>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, ShoppingCart, User } from '@element-plus/icons-vue'
import { useSessionStore } from '@/stores/session'
import { useCartStore } from '@/stores/cart'

const router = useRouter()
const session = useSessionStore()
const cart = useCartStore()
const kw = ref('')

function goSearch() {
  router.push({ name: 'home', query: kw.value ? { kw: kw.value } : {} })
}
function logout() {
  session.setUser(null)
  router.push('/')
}
</script>

<style scoped>
.store {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--mx-line);
}

.inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 24px;
  height: 60px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.seal {
  width: 34px;
  height: 34px;
  background: var(--mx-red);
  color: #fff;
  border-radius: 4px;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 13px;
  letter-spacing: 0.02em;
}

.name {
  font-weight: 700;
  font-size: 17px;
  letter-spacing: 0.01em;
}

.name i {
  font-style: normal;
  font-weight: 400;
  font-size: 12px;
  color: var(--mx-ink-3);
  margin-left: 6px;
}

.search {
  flex: 1;
  max-width: 420px;
}

.links {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-left: auto;
}

.lk {
  color: var(--mx-ink-2);
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 14px;
  padding: 6px 10px;
  border-radius: 8px;
  transition: color 0.18s, background-color 0.18s;
}

.lk:hover {
  color: var(--mx-red);
  background: var(--mx-red-tint);
}

.lk.strong {
  color: var(--mx-red);
  font-weight: 600;
}

.cart {
  gap: 4px;
}

.user {
  cursor: pointer;
  outline: none;
}

.body {
  flex: 1;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px 20px 48px;
  box-sizing: border-box;
}

.foot {
  border-top: 1px solid var(--mx-line);
  background: #fff;
}

.foot .inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px 20px;
  display: flex;
  justify-content: space-between;
  color: var(--mx-ink-3);
  font-size: 12px;
}

@media (max-width: 760px) {
  .search {
    display: none;
  }

  .links {
    gap: 12px;
  }
}
</style>
