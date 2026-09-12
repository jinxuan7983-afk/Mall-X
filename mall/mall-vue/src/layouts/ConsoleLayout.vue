<template>
  <el-container class="console">
    <!-- 玻璃底座:深墨画布 + 品牌色光斑(固定,不随滚动重绘) -->
    <div class="aurora aurora-a" aria-hidden="true"></div>
    <div class="aurora aurora-b" aria-hidden="true"></div>
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
  { path: '/admin/goods', title: '商品管理', icon: 'Goods' },
  { path: '/admin/pay', title: '支付订单', icon: 'Tickets' },
  { path: '/admin/refunds', title: '退款审核', icon: 'RefreshLeft' },
  { path: '/admin/channels', title: '渠道配置', icon: 'Connection' },
  { path: '/admin/withdraws', title: '提现审核', icon: 'Wallet' },
  { path: '/admin/categories', title: '类目管理', icon: 'Menu' },
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
/* 玻璃拟态画布:深墨渐变 + 品牌色光斑,让侧栏/顶栏玻璃"有东西可透" */
.console {
  height: 100%;
  overflow: hidden;
  position: relative;
  background: linear-gradient(155deg, #292520 0%, var(--mx-side) 45%, #1b1917 100%);
}

/* 内层容器:解除 flex 自动最小尺寸(min-height:auto)限制,
   保证窄屏(主轴垂直)下内容不会把容器撑破视口 —— 滚动只发生在 .main 内 */
.console :deep(.el-container) {
  min-height: 0;
}

/* 光斑:绝对定位于画布,脱离 flex 流,不参与布局 */
.aurora {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  pointer-events: none;
}

.aurora-a {
  width: 460px;
  height: 460px;
  top: -160px;
  left: -120px;
  background: radial-gradient(circle, rgba(226, 58, 46, 0.28), transparent 70%);
  z-index: 0;
}

.aurora-b {
  width: 420px;
  height: 420px;
  bottom: -180px;
  right: -140px;
  background: radial-gradient(circle, rgba(168, 134, 61, 0.2), transparent 70%);
  z-index: 0;
}

/* 侧栏:毛玻璃浮于画布之上(随画布锁定高度,不参与滚动) */
.side {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--mx-glass-fill);
  -webkit-backdrop-filter: blur(var(--mx-glass-blur)) saturate(150%);
  backdrop-filter: blur(var(--mx-glass-blur)) saturate(150%);
  border-right: 1px solid rgba(255, 255, 255, 0.12);
  z-index: 2;
}

/* 不支持 backdrop-filter:侧栏回退为近实体深色,保文字对比度 */
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .side {
    background: rgba(30, 27, 24, 0.96);
  }
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
  flex: none;
  background: rgba(255, 255, 255, 0.72);
  -webkit-backdrop-filter: blur(var(--mx-glass-blur)) saturate(160%);
  backdrop-filter: blur(var(--mx-glass-blur)) saturate(160%);
  border-bottom: 1px solid rgba(255, 255, 255, 0.5);
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  z-index: 10;
}

/* 不支持 backdrop-filter:顶栏回退为高填充白,保内容可读 */
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .head {
    background: rgba(255, 255, 255, 0.95);
  }
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
  position: relative;
  z-index: 1;
  /* 内容区为唯一滚动容器:内容不足一屏时无滚动条,内容超高时区内滚动 */
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
  min-height: 0;
}

/* ---------- 小屏自适应:侧栏转为顶部横向菜单,内容区独占剩余高度 ---------- */
@media (max-width: 900px) {
  .console {
    flex-direction: column;
  }

  .side {
    width: 100% !important;
    height: auto;
    flex: none;
    flex-direction: row;
    align-items: center;
    border-right: none;
    border-bottom: 1px solid rgba(255, 255, 255, 0.12);
  }

  .brand {
    display: none;
  }

  .el-menu {
    flex: 1;
    display: flex;
    flex-direction: row;
    align-items: center;
    overflow-x: auto;
    overflow-y: hidden;
    padding: 6px 8px;
    gap: 2px;
    scrollbar-width: none;
    -ms-overflow-style: none;
  }

  .el-menu::-webkit-scrollbar {
    display: none;
  }

  .el-menu :deep(.el-menu-item) {
    flex: 0 0 auto;
    height: 38px;
    line-height: 38px;
    padding: 0 12px;
    margin-bottom: 0;
    font-size: 13px;
  }

  .el-menu :deep(.el-menu-item:hover) {
    transform: none;
  }
}

@media (max-width: 760px) {
  .head {
    padding: 0 12px;
  }

  .title {
    font-size: 14px;
  }

  .who :deep(.el-divider) {
    display: none;
  }

  .who .back-home,
  .who .name {
    display: none;
  }

  .main {
    padding: 14px 12px 20px;
  }
}
</style>
