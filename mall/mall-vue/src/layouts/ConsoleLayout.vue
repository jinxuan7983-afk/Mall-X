<template>
  <el-container class="console">
    <el-aside width="212px" class="mx-side side">
      <router-link to="/" class="brand">
        <span class="seal">M·X</span>
        <div class="bt">
          <b>Mall·X</b>
          <i>{{ isMch ? '商户经营后台' : '平台管理后台' }}</i>
        </div>
      </router-link>
      <el-menu :default-active="$route.path" router background-color="transparent">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="head" height="56px">
        <div class="title">
          <el-icon class="t-ico"><component :is="isMch ? 'Shop' : 'DataBoard'" /></el-icon>
          {{ $route.meta.title }}
        </div>
        <div class="who">
          <el-link
            class="back-home"
            type="info"
            :underline="false"
            @click="router.push('/')"
          >
            <el-icon><ArrowLeft /></el-icon>商城首页
          </el-link>
          <el-divider direction="vertical" />
          <template v-if="isMch">
            <el-tag v-if="session.mch" :type="session.mch.shopState === 1 ? 'success' : 'warning'" size="small">
              {{ shopStateText }}
            </el-tag>
            <span class="name num">店铺 {{ session.mch?.shopNum }}</span>
          </template>
          <template v-else>
            <el-tag v-if="session.admin" type="info" size="small">{{ session.admin.role }}</el-tag>
            <span class="name">{{ session.admin?.realName }}</span>
          </template>
          <el-divider direction="vertical" />
          <el-link type="info" :underline="false" @click="quit">退出</el-link>
        </div>
      </el-header>
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="mx-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const isMch = computed(() => route.path.startsWith('/mch'))

const mchMenus = [
  { path: '/mch', title: '工作台', icon: 'HomeFilled' },
  { path: '/mch/goods', title: '商品管理', icon: 'Goods' },
  { path: '/mch/orders', title: '订单管理', icon: 'List' },
  { path: '/mch/trade', title: '交易流水', icon: 'Money' },
  { path: '/mch/refund', title: '退款管理', icon: 'RefreshLeft' },
  { path: '/mch/fund', title: '资金管理', icon: 'Wallet' },
  { path: '/mch/stats', title: '店铺统计', icon: 'DataAnalysis' },
]

const adminMenus = [
  { path: '/admin', title: '数据看板', icon: 'DataBoard' },
  { path: '/admin/merchants', title: '商户审核', icon: 'Shop' },
  { path: '/admin/pay', title: '支付订单', icon: 'Tickets' },
  { path: '/admin/refunds', title: '退款审核', icon: 'RefreshLeft' },
  { path: '/admin/channels', title: '渠道配置', icon: 'Connection' },
  { path: '/admin/withdraws', title: '提现审核', icon: 'Wallet' },
  { path: '/admin/categories', title: '类目管理', icon: 'Menu' },
  { path: '/admin/operators', title: '操作员管理', icon: 'UserFilled' },
  { path: '/admin/logs', title: '操作日志', icon: 'Document' },
]

const menus = computed(() => (isMch.value ? mchMenus : adminMenus))
const shopStateText = computed(() => ({ 0: '待审核', 1: '正常经营', 2: '已禁用' }[String(session.mch?.shopState) as '0' | '1' | '2'] ?? ''))

function quit() {
  if (isMch.value) {
    session.setMch(null)
    router.push('/mch/login')
  } else {
    session.setAdmin(null)
    router.push('/admin/login')
  }
}
</script>

<style scoped>
.console {
  min-height: 100vh;
}

.side {
  background: var(--mx-side);
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 18px 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.seal {
  width: 34px;
  height: 34px;
  background: linear-gradient(135deg, var(--mx-red), var(--mx-red-deep));
  color: #fff;
  border-radius: 6px;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 13px;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(226, 58, 46, 0.35);
}

.bt {
  line-height: 1.25;
}

.bt b {
  color: #fff;
  font-size: 16px;
  display: block;
}

.bt i {
  color: #8d857b;
  font-style: normal;
  font-size: 11px;
}

.el-menu {
  flex: 1;
  padding: 10px;
  gap: 2px;
  overflow-y: auto;
}

/* 菜单项胶囊化 */
.el-menu :deep(.el-menu-item) {
  border-radius: 8px;
  margin-bottom: 3px;
  transition: background-color 0.18s, color 0.18s, transform 0.18s;
}

.el-menu :deep(.el-menu-item:hover) {
  transform: translateX(2px);
}

.head {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--mx-line);
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: sticky;
  top: 0;
  z-index: 10;
}

.title {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
  letter-spacing: 0.02em;
}

.t-ico {
  color: var(--mx-red);
}

.back-home {
  font-size: 13px;
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.who {
  display: flex;
  align-items: center;
  gap: 10px;
}

.name {
  font-weight: 500;
}

.main {
  background: var(--mx-paper);
  padding: 20px;
  max-width: 1400px;
  width: 100%;
  margin: 0 auto;
  box-sizing: border-box;
}
</style>
