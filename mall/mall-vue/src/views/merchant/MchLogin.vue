<template>
  <div class="auth">
    <!-- 左侧品牌画卷 -->
    <aside class="hero">
      <div class="glow glow-a" aria-hidden="true"></div>
      <div class="glow glow-b" aria-hidden="true"></div>
      <div class="hero-inner">
        <div class="hero-brand">
          <span class="seal">M·X</span>
          <span class="brand-name">Mall·X 多商家电商平台</span>
        </div>
        <h1>把好生意<br />搬进 Mall·X</h1>
        <p class="hero-sub">入驻审核、商品发布、订单履约、资金提现,经营所需一站就绪。</p>
        <ul class="points">
          <li>
            <span class="pt-ico"><el-icon :size="18"><TrendCharts /></el-icon></span>
            <div>
              <b>经营数据实时可视</b>
              <span>营收、手续费、净收益与七日走势一屏掌握</span>
            </div>
          </li>
          <li>
            <span class="pt-ico"><el-icon :size="18"><Van /></el-icon></span>
            <div>
              <b>订单履约高效顺畅</b>
              <span>支付回调幂等处理,发货收货全程可追踪</span>
            </div>
          </li>
          <li>
            <span class="pt-ico"><el-icon :size="18"><Medal /></el-icon></span>
            <div>
              <b>资金安全按日结算</b>
              <span>差异化费率透明对账,余额提现随时可查</span>
            </div>
          </li>
        </ul>
        <div class="hero-foot">Spring Cloud 微服务 · Vue3 + TypeScript · 支付宝沙箱对接</div>
      </div>
    </aside>

    <!-- 右侧表单 -->
    <main class="form-side">
      <div class="form-wrap">
        <div class="form-card">
          <a class="back" href="/">
            <el-icon><ArrowLeft /></el-icon>返回商城
          </a>

          <template v-if="!registered">
            <header class="head">
              <h2>商户经营后台</h2>
              <p>登录后管理店铺、商品与订单,或申请入驻开启经营</p>
            </header>

            <el-tabs v-model="mode" stretch class="auth-tabs">
              <el-tab-pane label="商户登录" name="login" />
              <el-tab-pane label="入驻注册" name="register" />
            </el-tabs>

            <!-- 登录 -->
            <el-form v-if="mode === 'login'" :model="loginForm" @keyup.enter="doLogin">
              <el-form-item>
                <el-input v-model="loginForm.username" placeholder="登录账号" size="large">
                  <template #prefix><el-icon><User /></el-icon></template>
                </el-input>
              </el-form-item>
              <el-form-item>
                <el-input v-model="loginForm.password" type="password" placeholder="密码" size="large" show-password>
                  <template #prefix><el-icon><Lock /></el-icon></template>
                </el-input>
              </el-form-item>
              <el-button class="submit" type="primary" size="large" :loading="loading" @click="doLogin">
                登录经营后台
              </el-button>
              <div class="demo">
                <span class="demo-label">演示账号</span>
                <button
                  v-for="d in demoShops"
                  :key="d.username"
                  type="button"
                  class="chip"
                  @click="fillDemo(d.username)"
                >
                  {{ d.username }} · {{ d.name }}
                </button>
              </div>
            </el-form>

            <!-- 注册 -->
            <el-form v-else ref="regFormRef" :model="regForm" :rules="regRules" label-width="84px" @keyup.enter="doRegister">
              <el-form-item label="店铺名称" prop="shopName">
                <el-input v-model="regForm.shopName" placeholder="店铺名称(64字以内)" maxlength="64" />
              </el-form-item>
              <el-form-item label="联系电话" prop="shopPhone">
                <el-input v-model="regForm.shopPhone" placeholder="11位手机号" maxlength="11" />
              </el-form-item>
              <el-form-item label="经营地址" prop="shopAddress">
                <el-input v-model="regForm.shopAddress" placeholder="省市区 + 详细地址" maxlength="255" />
              </el-form-item>
              <el-form-item label="登录账号" prop="username">
                <el-input v-model="regForm.username" placeholder="4~20位字母/数字/下划线" />
              </el-form-item>
              <el-form-item label="登录密码" prop="password">
                <el-input v-model="regForm.password" type="password" show-password placeholder="6~20位" />
              </el-form-item>
              <el-form-item label="验证码" prop="captchaCode">
                <div class="captcha-row">
                  <el-input v-model="regForm.captchaCode" placeholder="验证码" maxlength="4" />
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
              <el-button class="submit" type="primary" size="large" :loading="loading" @click="doRegister">提交入驻申请</el-button>
              <p class="hint">提交后由平台管理员审核,审核通过即可登录经营。</p>
            </el-form>
          </template>

          <!-- 注册成功:展示商户编号 -->
          <el-result v-if="registered" icon="success" title="入驻申请已提交" sub-title="等待平台管理员审核,可先用登录页查看审核状态">
            <template #extra>
              <div class="shopnum">
                商户编号<span class="num">{{ registered }}</span>
              </div>
              <el-button type="primary" @click="mode = 'login'; registered = ''">去登录</el-button>
            </template>
          </el-result>
        </div>
        <div class="form-foot">© 2026 Mall·X</div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Key, TrendCharts, Van, Medal, ArrowLeft } from '@element-plus/icons-vue'
import { captchaImage, mchLogin, mchRegister } from '@/api/modules'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const mode = ref<'login' | 'register'>('login')
const loading = ref(false)
const registered = ref('')
const regFormRef = ref<FormInstance>()
const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({
  shopName: '',
  shopPhone: '',
  shopAddress: '',
  username: '',
  password: '',
  captchaCode: '',
})
const captchaImg = ref('')
const captchaKey = ref('')

const demoShops = [
  { username: 'shop1', name: '星选数码旗舰店' },
  { username: 'shop2', name: '鲜果之家' },
]

const regRules: FormRules = {
  shopName: [
    { required: true, message: '请输入店铺名称', trigger: 'blur' },
    { max: 64, message: '店铺名称不能超过64个字', trigger: 'blur' },
  ],
  shopPhone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入11位有效手机号', trigger: 'blur' },
  ],
  shopAddress: [
    { required: true, message: '请输入经营地址', trigger: 'blur' },
    { max: 255, message: '地址不能超过255个字', trigger: 'blur' },
  ],
  username: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{4,20}$/, message: '账号需为4~20位字母、数字或下划线', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入登录密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度需为6~20位', trigger: 'blur' },
  ],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

function fillDemo(username: string) {
  loginForm.username = username
  loginForm.password = '123456'
}

/** 加载图形验证码,失败静默(提交时后端兜底提示) */
async function loadCaptcha() {
  try {
    const r = await captchaImage()
    captchaKey.value = r.captchaKey
    captchaImg.value = r.image
    regForm.captchaCode = ''
  } catch {
    /* 保持旧图 */
  }
}

watch(mode, (m) => {
  if (m === 'register') loadCaptcha()
})

async function doLogin() {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const r = await mchLogin(loginForm.username, loginForm.password)
    // 契约:登录响应仅含 shopNum/shopState,店铺名以编号展示
    session.setMch({ shopNum: r.shopNum, shopName: r.shopNum, shopState: r.shopState, token: r.token })
    // 待审核/禁用提示由工作台顶部 alert 持续展示,这里不再重复弹 toast
    router.push('/mch')
  } finally {
    loading.value = false
  }
}

async function doRegister() {
  const valid = await regFormRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const r = await mchRegister({
      shopName: regForm.shopName,
      shopPhone: regForm.shopPhone,
      shopAddress: regForm.shopAddress,
      username: regForm.username,
      password: regForm.password,
      captchaKey: captchaKey.value,
      captchaCode: regForm.captchaCode,
    })
    registered.value = r.shopNum
    ElMessage.success('注册成功')
  } catch {
    loadCaptcha()
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

/* ---------- 左侧品牌画卷 ---------- */
.hero {
  position: relative;
  overflow: hidden;
  color: #fff;
  background: linear-gradient(165deg, #2b2622 0%, var(--mx-side) 48%, #1b1917 100%);
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
  background: radial-gradient(circle, rgba(226, 58, 46, 0.38), transparent 70%);
}

.glow-b {
  width: 360px;
  height: 360px;
  bottom: -140px;
  left: -110px;
  background: radial-gradient(circle, rgba(168, 134, 61, 0.28), transparent 70%);
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
  background: linear-gradient(135deg, var(--mx-red), var(--mx-red-deep));
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  box-shadow: 0 4px 14px rgba(226, 58, 46, 0.4);
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
  background: linear-gradient(135deg, var(--mx-red), var(--mx-red-deep));
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
  width: min(460px, 100%);
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
  padding: 38px 40px 30px;
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
  color: var(--mx-red);
}

.head {
  text-align: center;
  margin-bottom: 8px;
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
  margin-top: 10px;
}

.el-form :deep(.el-form-item) {
  margin-bottom: 20px;
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
  height: 38px;
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

/* 演示账号一键填充 */
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

.hint {
  margin: 14px 0 0;
  color: var(--mx-ink-3);
  font-size: 12px;
  text-align: center;
}

.shopnum {
  margin-bottom: 14px;
  font-size: 13px;
  color: var(--mx-ink-2);
}

.shopnum .num {
  display: block;
  font-size: 22px;
  font-weight: 700;
  color: var(--mx-red);
  margin-top: 4px;
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
    padding: 32px 24px 24px;
  }
}

@media (max-width: 420px) {
  .form-card {
    padding: 28px 18px 20px;
  }

  .back {
    position: static;
    display: inline-flex;
    margin-bottom: 12px;
  }
}
</style>
