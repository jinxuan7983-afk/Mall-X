<template>
  <div class="auth">
    <div class="panel">
      <!-- 左侧品牌画卷 -->
      <aside class="hero">
        <div class="glow glow-a" aria-hidden="true"></div>
        <div class="glow glow-b" aria-hidden="true"></div>
        <div class="hero-inner">
          <div class="hero-brand">
            <span class="seal">M·X</span>
            <span class="brand-name">Mall·X 多商家电商平台</span>
          </div>
          <h1>多商家入驻<br />一站式购齐</h1>
          <p class="hero-sub">数码、生鲜、服饰、图书,下单支付到收货,全程可追踪。</p>
          <ul class="points">
            <li>
              <span class="pt-ico"><el-icon :size="18"><Shop /></el-icon></span>
              <div>
                <b>多商户一键购齐</b>
                <span>跨店合并结算,按店铺自动拆单履约</span>
              </div>
            </li>
            <li>
              <span class="pt-ico"><el-icon :size="18"><Van /></el-icon></span>
              <div>
                <b>订单全程可追踪</b>
                <span>支付结果实时轮询,发货收货状态同步</span>
              </div>
            </li>
            <li>
              <span class="pt-ico"><el-icon :size="18"><ShoppingCartFull /></el-icon></span>
              <div>
                <b>离线购物车云端同步</b>
                <span>未登录也能先加购,登录后自动合并</span>
              </div>
            </li>
          </ul>
          <div class="hero-foot">支付宝 / 微信 / 云闪付 · 三渠道聚合收银</div>
        </div>
      </aside>

      <!-- 右侧表单区 -->
      <main class="form-side">
        <header class="head">
          <h2>{{ mode === 'login' ? '欢迎回来' : '加入 Mall·X' }}</h2>
          <p>{{ mode === 'login' ? '登录后下单购物,购物车将自动合并' : '注册即送 Mall·X 会员身份' }}</p>
        </header>

        <el-tabs v-model="mode" stretch class="auth-tabs">
          <el-tab-pane label="登录" name="login" />
          <el-tab-pane label="注册" name="register" />
        </el-tabs>

        <el-form ref="formRef" :model="form" :rules="rules" @keyup.enter="submit">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="账号(4~20位字母/数字/下划线)" size="large">
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码(6~20位)" size="large" show-password>
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item v-if="mode === 'register'" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="确认密码"
              size="large"
              show-password
            >
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item v-if="mode === 'register'" prop="captchaCode">
            <div class="captcha-row">
              <el-input v-model="form.captchaCode" placeholder="验证码" size="large" maxlength="4">
                <template #prefix><el-icon><Key /></el-icon></template>
              </el-input>
              <img
                v-if="captchaImg"
                :src="captchaImg"
                class="captcha-img"
                title="看不清?点击刷新"
                alt="验证码"
                @click="loadCaptcha"
              />
            </div>
          </el-form-item>
          <el-button class="submit" type="primary" size="large" :loading="loading" @click="submit">
            {{ mode === 'login' ? '登录' : '注册并登录' }}
          </el-button>
        </el-form>

        <div class="demo">
          <span class="demo-label">演示账号</span>
          <button type="button" class="chip" @click="fillDemo">user1 · 体验用户</button>
        </div>

        <div class="foot-links">
          <span>想开店?</span>
          <router-link to="/mch/login">商户入驻</router-link>
          <el-divider direction="vertical" />
          <router-link to="/">继续逛逛</router-link>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Key, Shop, Van, ShoppingCartFull } from '@element-plus/icons-vue'
import { captchaImage, userLogin, userRegister } from '@/api/modules'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const formRef = ref<FormInstance>()
const mode = ref<'login' | 'register'>('login')
const loading = ref(false)
const form = reactive({ username: '', password: '', confirmPassword: '', captchaCode: '' })
const captchaImg = ref('')
const captchaKey = ref('')

const rules: FormRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{4,20}$/, message: '账号需为4~20位字母、数字或下划线', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度需为6~20位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) =>
        value === form.password ? callback() : callback(new Error('两次输入的密码不一致')),
      trigger: 'blur',
    },
  ],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

function fillDemo() {
  form.username = 'user1'
  form.password = '123456'
  form.confirmPassword = ''
}

/** 加载图形验证码(仅注册模式使用,失败静默由提交时后端兜底) */
async function loadCaptcha() {
  try {
    const r = await captchaImage()
    captchaKey.value = r.captchaKey
    captchaImg.value = r.image
    form.captchaCode = ''
  } catch {
    /* 保持旧图,提交时后端会给出明确错误 */
  }
}

watch(mode, (m) => {
  formRef.value?.clearValidate()
  if (m === 'register') loadCaptcha()
})

async function submit() {
  if (mode.value === 'register') {
    const valid = await formRef.value?.validate().then(() => true).catch(() => false)
    if (!valid) return
    loading.value = true
    try {
      await userRegister(form.username, form.password, captchaKey.value, form.captchaCode)
      ElMessage.success('注册成功,已自动登录')
    } catch {
      loadCaptcha()
      return
    } finally {
      loading.value = false
    }
  }
  if (!form.username || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const r = await userLogin(form.username, form.password)
    session.setUser({ userNum: r.userNum, username: form.username })
    router.push(String(route.query.redirect || '/'))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth {
  min-height: 62vh;
  display: grid;
  place-items: center;
  padding: 40px 16px 72px;
}

.panel {
  width: min(920px, 100%);
  display: grid;
  grid-template-columns: minmax(360px, 44%) 1fr;
  background: var(--mx-card);
  border: 1px solid var(--mx-line);
  border-radius: 16px;
  overflow: hidden;
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

/* ---------- 左侧品牌画卷 ---------- */
.hero {
  position: relative;
  overflow: hidden;
  color: #fff;
  background: linear-gradient(165deg, #2b2622 0%, var(--mx-side) 48%, #1b1917 100%);
}

.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(64px);
  pointer-events: none;
}

.glow-a {
  width: 340px;
  height: 340px;
  top: -120px;
  right: -100px;
  background: radial-gradient(circle, rgba(226, 58, 46, 0.4), transparent 70%);
}

.glow-b {
  width: 300px;
  height: 300px;
  bottom: -120px;
  left: -90px;
  background: radial-gradient(circle, rgba(168, 134, 61, 0.26), transparent 70%);
}

.hero-inner {
  position: relative;
  z-index: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 44px 36px;
}

.hero-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 30px;
}

.seal {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 6px;
  background: linear-gradient(135deg, var(--mx-red), var(--mx-red-deep));
  color: #fff;
  font-weight: 700;
  font-size: 13px;
  box-shadow: 0 4px 14px rgba(226, 58, 46, 0.4);
}

.brand-name {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.85);
  letter-spacing: 0.04em;
}

.hero h1 {
  margin: 0 0 12px;
  font-size: 32px;
  line-height: 1.3;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.hero-sub {
  margin: 0 0 28px;
  font-size: 13.5px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.62);
}

.points {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
}

.points li {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 14px;
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
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--mx-red), var(--mx-red-deep));
  color: #fff;
}

.points b {
  display: block;
  font-size: 13.5px;
  margin-bottom: 2px;
}

.points span {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
}

.hero-foot {
  margin-top: 28px;
  font-size: 11.5px;
  letter-spacing: 0.06em;
  color: rgba(255, 255, 255, 0.36);
}

/* ---------- 右侧表单区 ---------- */
.form-side {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 44px 46px 36px;
}

.head {
  text-align: center;
  margin-bottom: 18px;
}

.head h2 {
  margin: 0 0 6px;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.head p {
  margin: 0;
  font-size: 13.5px;
  color: var(--mx-ink-3);
}

/* tabs 主题色统一为朱红 */
.auth-tabs :deep(.el-tabs__item.is-active),
.auth-tabs :deep(.el-tabs__item:hover) {
  color: var(--mx-red);
}

.auth-tabs :deep(.el-tabs__active-bar) {
  background-color: var(--mx-red);
}

.auth-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
}

.el-form {
  margin-top: 8px;
}

.el-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
  align-items: center;
}

.captcha-row :deep(.el-input) {
  flex: 1;
}

.captcha-img {
  height: 40px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid var(--mx-line);
  flex: none;
}

.submit {
  width: 100%;
  margin-top: 4px;
  border: none;
  background: linear-gradient(135deg, var(--mx-red), var(--mx-red-deep));
  box-shadow: 0 6px 18px rgba(226, 58, 46, 0.28);
  transition: transform 0.2s, box-shadow 0.2s;
  letter-spacing: 0.14em;
}

.submit:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 24px rgba(226, 58, 46, 0.34);
  background: linear-gradient(135deg, var(--mx-red), var(--mx-red-deep));
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
  background: var(--mx-red-tint);
  border: 1px solid transparent;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.2s;
}

.chip:hover {
  color: var(--mx-red);
  border-color: var(--mx-red);
  background: #fff;
}

.foot-links {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 22px;
  font-size: 13px;
  color: var(--mx-ink-3);
}

.foot-links a {
  color: var(--mx-red);
  transition: opacity 0.2s;
}

.foot-links a:hover {
  opacity: 0.75;
}

/* ---------- 响应式 ---------- */
@media (max-width: 820px) {
  .panel {
    grid-template-columns: 1fr;
  }

  .hero {
    display: none;
  }

  .form-side {
    padding: 32px 24px 28px;
  }
}

@media (max-width: 420px) {
  .auth {
    padding: 24px 12px 48px;
  }

  .form-side {
    padding: 28px 18px 22px;
  }
}
</style>
