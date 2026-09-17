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
          <router-link to="/" class="lk">首页</router-link>
          <router-link to="/my/orders" class="lk">我的订单</router-link>
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
  // 购物车存放在与账号无关的 localStorage 键中，退出时必须清空，
  // 否则上一个账号加入的商品会残留给下一个登录用户
  cart.clear()
  router.push('/')
}
</script>

<style scoped>
.store {
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.topbar {
  flex: none;
  position: relative;
  z-index: 20;
  background: rgba(255, 255, 255, 0.72);
  -webkit-backdrop-filter: blur(var(--mx-glass-blur)) saturate(160%);
  backdrop-filter: blur(var(--mx-glass-blur)) saturate(160%);
  border-bottom: 1px solid rgba(255, 255, 255, 0.6);
  box-shadow: 0 1px 24px rgba(35, 31, 28, 0.05);
}

/* 不支持 backdrop-filter:回退为高填充白,保导航可读 */
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .topbar {
    background: rgba(255, 255, 255, 0.96);
  }
}

.inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 24px;
  height: 60px;
  min-width: 0;
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
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px 20px 48px;
  box-sizing: border-box;
}

@media (max-width: 760px) {
  .search {
    display: none;
  }

  .links {
    gap: 12px;
  }
}

/* 小屏:压缩品牌区与导航,导航可横向滑动(不撑破布局,功能不丢失) */
@media (max-width: 600px) {
  .inner {
    gap: 10px;
    padding: 0 12px;
  }

  .name {
    font-size: 15px;
  }

  .name i {
    display: none;
  }

  .links {
    gap: 2px;
    flex-wrap: nowrap;
    overflow-x: auto;
    scrollbar-width: none;
    -ms-overflow-style: none;
  }

  .links::-webkit-scrollbar {
    display: none;
  }

  .lk {
    padding: 6px 8px;
    font-size: 13px;
    white-space: nowrap;
  }

  .body {
    padding: 14px 12px 40px;
  }
}
</style>
