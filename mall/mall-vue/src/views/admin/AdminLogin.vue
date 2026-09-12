<template>
  <div class="auth">
    <!-- 左侧品牌画卷 -->
    <aside class="hero">
      <div class="glow glow-a" aria-hidden="true"></div>
      <div class="glow glow-b" aria-hidden="true"></div>
      <div class="grid-lines" aria-hidden="true"></div>
      <div class="hero-inner">
        <div class="hero-brand">
          <span class="seal">M·X</span>
          <span class="brand-name">Mall·X 多商家电商平台</span>
        </div>
        <h1>平台掌舵<br />运筹帷幄</h1>
        <p class="hero-sub">商户审核、渠道管控、退款提现复核与操作审计,平台治理尽在掌握。</p>
        <ul class="points">
          <li>
            <span class="pt-ico"><el-icon :size="18"><DataAnalysis /></el-icon></span>
            <div>
              <b>全平台交易看板</b>
              <span>交易总额、渠道占比与七日走势实时呈现</span>
            </div>
          </li>
          <li>
            <span class="pt-ico"><el-icon :size="18"><DocumentChecked /></el-icon></span>
            <div>
              <b>风控审核闭环</b>
              <span>商户入驻、退款、提现三线复核,驳回留痕</span>
            </div>
          </li>
          <li>
            <span class="pt-ico"><el-icon :size="18"><Odometer /></el-icon></span>
            <div>
              <b>操作审计可溯源</b>
              <span>AOP 切面自动记录管理端全部敏感操作</span>
            </div>
          </li>
        </ul>
        <div class="hero-foot">多角色权限 ADMIN / OPS · 操作日志留痕 · 差异化费率配置</div>
      </div>
    </aside>

    <!-- 右侧表单 -->
    <main class="form-side">
      <div class="form-wrap">
        <div class="form-card">
          <a class="back" href="/">
            <el-icon><ArrowLeft /></el-icon>返回商城
          </a>

          <header class="head">
            <span class="seal-inline">M·X</span>
            <h2>平台管理后台</h2>
            <p>仅限授权管理员登录,操作将记入审计日志</p>
          </header>

          <el-form :model="form" @keyup.enter="submit">
            <el-form-item>
              <el-input v-model="form.username" placeholder="管理员账号" size="large">
                <template #prefix><el-icon><User /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item>
              <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password>
                <template #prefix><el-icon><Lock /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-button class="submit" type="primary" size="large" :loading="loading" @click="submit">
              登录管理后台
            </el-button>
          </el-form>

          <div class="demo">
            <span class="demo-label">演示账号</span>
            <button type="button" class="chip" @click="form.username = 'admin'; form.password = '123456'">
              admin · 平台超级管理员
            </button>
          </div>

          <div class="secure-note">
            <el-icon :size="13"><Lock /></el-icon>
            密码 MD5 加盐存储 · 接口全链路签名防篡改
          </div>
        </div>
        <div class="form-foot">© 2026 Mall·X</div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, DataAnalysis, DocumentChecked, Odometer, ArrowLeft } from '@element-plus/icons-vue'
import { adminLogin } from '@/api/modules'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const r = await adminLogin(form.username, form.password)
    session.setAdmin({ username: form.username, realName: r.realName, role: r.role, token: r.token })
    router.push('/admin')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth {
  height: 100%;
  overflow-y: auto;
  overscroll-behavior: contain;
  display: grid;
  grid-template-columns: minmax(440px, 46%) 1fr;
  background: var(--mx-paper);
}

/* ---------- 左侧品牌画卷(管理端:墨色 + 金色点缀) ---------- */
.hero {
  position: relative;
  overflow: hidden;
  color: #fff;
  background: linear-gradient(165deg, #262624 0%, #1f1e1c 48%, #161513 100%);
  display: flex;
  align-items: center;
}

.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  pointer-events: none;
}

.glow-a {
  width: 420px;
  height: 420px;
  top: -140px;
  right: -120px;
  background: radial-gradient(circle, rgba(168, 134, 61, 0.3), transparent 70%);
}

.glow-b {
  width: 360px;
  height: 360px;
  bottom: -140px;
  left: -110px;
  background: radial-gradient(circle, rgba(226, 58, 46, 0.24), transparent 70%);
}

/* 细网格纹理,呼应"账本"主题 */
.grid-lines {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.035) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(ellipse at 30% 40%, #000 30%, transparent 75%);
}

.hero-inner {
  position: relative;
  z-index: 1;
  width: min(420px, 86%);
  margin: 0 auto;
  padding: 56px 0;
}

.hero-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 44px;
}

.seal {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 6px;
  background: linear-gradient(135deg, var(--mx-gold), #8a6c2c);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  box-shadow: 0 4px 14px rgba(168, 134, 61, 0.4);
}

.brand-name {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.85);
  letter-spacing: 0.04em;
}

.hero h1 {
  margin: 0 0 14px;
  font-size: 40px;
  line-height: 1.28;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.hero-sub {
  margin: 0 0 40px;
  font-size: 15px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.62);
}

.points {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 14px;
}

.points li {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--mx-glass-fill);
  -webkit-backdrop-filter: blur(10px) saturate(140%);
  backdrop-filter: blur(10px) saturate(140%);
  border: var(--mx-glass-border);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.22);
}

.pt-ico {
  flex: none;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--mx-gold), #8a6c2c);
  color: #fff;
}

.points b {
  display: block;
  font-size: 14px;
  margin-bottom: 2px;
}

.points span {
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.55);
}

.hero-foot {
  margin-top: 44px;
  font-size: 12px;
  letter-spacing: 0.06em;
  color: rgba(255, 255, 255, 0.36);
}

/* ---------- 右侧表单 ---------- */
.form-side {
  display: grid;
  place-items: center;
  padding: 40px 24px;
}

.form-wrap {
  width: min(440px, 100%);
  display: grid;
  gap: 18px;
  justify-items: center;
}

.form-card {
  position: relative;
  width: 100%;
  background: var(--mx-card);
  border: 1px solid var(--mx-line);
  border-radius: 14px;
  padding: 42px 40px 30px;
  box-shadow: 0 2px 6px rgba(35, 31, 28, 0.04), 0 18px 48px rgba(35, 31, 28, 0.08);
  animation: rise 0.5s ease both;
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.back {
  position: absolute;
  top: 18px;
  left: 20px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--mx-ink-3);
  transition: color 0.2s;
}

.back:hover {
  color: var(--mx-gold);
}

.head {
  text-align: center;
  margin-bottom: 26px;
}

.seal-inline {
  display: inline-grid;
  place-items: center;
  width: 46px;
  height: 46px;
  margin-bottom: 14px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--mx-gold), #8a6c2c);
  color: #fff;
  font-weight: 700;
  font-size: 15px;
  box-shadow: 0 6px 16px rgba(168, 134, 61, 0.32);
}

.head h2 {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.head p {
  margin: 0;
  font-size: 13.5px;
  color: var(--mx-ink-3);
}

.el-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.submit {
  width: 100%;
  margin-top: 4px;
  border: none;
  background: linear-gradient(135deg, #2b2823, var(--mx-side));
  box-shadow: 0 6px 18px rgba(35, 31, 28, 0.3);
  transition: transform 0.2s, box-shadow 0.2s;
  letter-spacing: 0.14em;
}

.submit:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 24px rgba(35, 31, 28, 0.36);
  background: linear-gradient(135deg, #2b2823, var(--mx-side));
}

.submit:active {
  transform: none;
}

.demo {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 16px;
}

.demo-label {
  font-size: 12px;
  color: var(--mx-ink-3);
}

.chip {
  padding: 4px 12px;
  font-size: 12px;
  color: var(--mx-ink-2);
  background: #f4efe6;
  border: 1px solid transparent;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.2s;
}

.chip:hover {
  color: var(--mx-gold);
  border-color: var(--mx-gold);
  background: #fff;
}

.secure-note {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px dashed var(--mx-line);
  font-size: 12px;
  color: var(--mx-ink-3);
}

.form-foot {
  font-size: 12px;
  color: var(--mx-ink-3);
  letter-spacing: 0.04em;
}

/* ---------- 响应式 ---------- */
@media (max-width: 960px) {
  .auth {
    grid-template-columns: 1fr;
  }

  .hero {
    display: none;
  }

  .form-side {
    padding: 32px 16px;
  }

  .form-card {
    padding: 36px 24px 24px;
  }
}

@media (max-width: 420px) {
  .form-card {
    padding: 30px 18px 20px;
  }

  .back {
    position: static;
    display: inline-flex;
    margin-bottom: 12px;
  }
}
</style>
