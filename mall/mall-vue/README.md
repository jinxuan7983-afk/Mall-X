# Mall·X 多商家电商平台 · 前端(mall-vue)

依据《行业工程实践》实验指导书与教学大纲实现的 Mall-X 前端工程,技术栈 **Vue3 + TypeScript + Element Plus + ECharts + Pinia + Vue Router**(教学大纲指定),对接 mall 后端网关(`http://127.0.0.1:10010`)。

## 一、快速启动

```bash
cd mall-vue
npm install          # 推荐 --registry=https://registry.npmmirror.com
npm run dev          # http://localhost:5173
```

### 两种运行模式

| 模式 | 配置 | 说明 |
| --- | --- | --- |
| **mock 模式**(默认) | `.env.development` 中 `VITE_USE_MOCK=true` | 内置有状态 mock 服务(`src/mock/`)承接全部 `/api` 请求,无需启动后端/中间件;注册、审核、下单、支付、回调幂等、退款、提现全链路可走通,种子数据与 `sql/mallInsert.sql` 一致 |
| **真实联调模式** | `VITE_USE_MOCK=false` 后重启 dev | `/api` 代理转发至 mall-gateway `http://127.0.0.1:10010`,按 README 部署 `docker compose up -d` 中间件并启动三个微服务即可 |

> 签名密钥通过 `VITE_SIGN_SECRET` 配置(默认 `mallx-pay-secret-2026`),axios 拦截器按后端 `SignUtil` 同款规则(剔除空值/嵌套结构 → ASCII 升序 `k=v&` 拼接 → `&key=密钥` → MD5)自动为每个业务请求附加 `sign`。

## 二、演示账号

| 角色 | 账号 | 密码 | 入口 |
| --- | --- | --- | --- |
| 普通用户 | user1 | 123456 | 商城右上角「登录 / 注册」 |
| 商户 | shop1(星选数码旗舰店)/ shop2(鲜果之家) | 123456 | 商城右上角「商户入驻」 |
| 平台管理员 | admin | 123456 | 直接访问 `/admin/login` |

## 三、页面清单(对应指导书功能设计 3.1 / 3.2)

**用户商城端(`/`)**
- 首页:类目筛选 + 商品搜索 + 分页商品栅格(原型图 1.4.2「首页-商品展示」)
- 商品详情、购物车(localStorage 离线购物车,登录后自动合并——大纲核心技能点)、订单确认(多商户自动拆单)
- 收银台:三渠道选择(微信/支付宝/云闪付)→ `pay/create` 下单 → 跳转模拟收银台/支付宝沙箱 → 轮询 `pay/query` 自动确认支付结果
- 我的订单:按状态筛选、订单详情抽屉、去支付、确认收货

**商户经营后台(`/mch`)**
- 入驻注册(返回商户编号)、登录(待审核/禁用状态提示)
- 工作台:营收/手续费/净收益/可提余额 + 近7日走势 + 渠道分布(ECharts)
- 商品管理:发布/编辑/上下架;订单管理:状态筛选 + 发货(同步物流单号)
- 交易流水:支付订单对账(金额/手续费/渠道),支持对已支付订单**发起部分退款**
- 退款管理:退款单进度四步流程图 + 单号查询;资金管理:资金总览 + 提现申请/记录
- 店铺统计:时间范围筛选、双轴走势图、渠道明细表

**平台管理后台(`/admin`)**
- 数据看板:平台交易总额/笔数、七日走势(柱+线双轴)、渠道占比(ECharts——指导书 4.6)
- 商户审核:列表筛选、通过/驳回(驳回必填原因)、禁用/启用、**差异化费率配置**
- 支付订单:全平台多条件管控;退款审核:通过→渠道退款→原支付单流转「已退款」
- 渠道配置:微信/支付宝/云联开关、基础费率、支付宝沙箱密钥配置
- 提现审核、类目管理、操作员管理(ADMIN/OPS)、操作日志(AOP 自动记录)

## 四、与后端契约的关键约定

- 统一 POST + JSON,响应字段平铺顶层(`{id, message, ...}`),`id="0"` 为成功;HTTP 恒 200
- 分页响应:`{total, pages, current, size, records}`
- 三个登录接口免签名,其余业务接口自动携带 `sign`;`items` 等嵌套结构不参与签名
- Long 型 id 按数字处理;金额类参数传字符串防序列化差异;时间格式兼容 `yyyy-MM-dd HH:mm:ss` 与 ISO(带 T)两种
- 支付回调用后端模拟收银台完成(真实环境可配置支付宝沙箱);订单超时未支付由后端 RocketMQ 延迟关单,前端无需处理

## 五、目录结构

```
mall-vue/
├─ vite.config.ts          # mock 插件挂载 + /api 代理(10010)
├─ src/
│  ├─ api/client.ts        # axios 封装 + MD5 签名 + 统一错误提示
│  ├─ api/modules.ts       # 全部后端接口的类型化封装
│  ├─ mock/index.ts        # 有状态 mock 服务(契约对齐,开发用)
│  ├─ stores/              # session(三类会话) / cart(离线购物车)
│  ├─ layouts/             # 商城布局 / 控制台布局(商户·管理共用)
│  ├─ composables/useEcharts.ts
│  ├─ components/          # GoodsPic(渐变占位图) / StateTag(状态字典)
│  └─ views/
│     ├─ storefront/       # 商城 7 页
│     ├─ merchant/         # 商户端 8 页
│     └─ admin/            # 平台管理端 10 页
```
