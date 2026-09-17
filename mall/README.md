# Mall-X 多商家电商平台

依据《行业工程实践》实验指导书与教学大纲实现的多商家入驻高并发电商平台，面向**普通用户 / 入驻商户 / 平台管理员**三类角色，
实现商品管理、商户入驻、购物车、下单交易、支付、订单履约（发货/收货）、售后退款、资金提现、统计报表等完整电商业务闭环。

| 层 | 技术 |
| --- | --- |
| 后端 | Spring Boot 3.2 · Spring Cloud Alibaba · Nacos · Redis · RocketMQ · MySQL 8 · MyBatis-Plus · Redisson |
| 前端 | Vue 3 · TypeScript · Vite 6 · Element Plus · ECharts · Pinia |
| 部署 | Docker Compose（中间件与微服务镜像），支撑 QPS ≥ 1000 |

```
                       ┌─────────────────────────────────────────────┐
   mall-vue 前端       │              Gateway :10010                 │
   ──────────────────► │  路由转发 · Redis+Lua限流 · Sentinel熔断降级  │
                       └───────────────┬───────────────┬─────────────┘
                                       │               │
                    /mch /admin /statis│               │ /pay/**
                    /refund /order ... │               │
                             ┌─────────▼───┐   ┌───────▼────────┐
                             │  mall-mgr   │   │  mall-alipay   │
                             │  :10020     │◄──┤  :10030        │
                             │ 业务微服务    │Feign│ 支付微服务     │
                             └──────┬──────┘   └───────┬────────┘
                                    │                  │
              ┌──────────┬──────────┼────────┬─────────┴───────┐
           ┌──▼───┐  ┌───▼──┐  ┌────▼───┐ ┌──▼─────┐  ┌────────▼──────┐
           │MySQL │  │Redis │  │RocketMQ│ │ Nacos  │  │支付宝沙箱/     │
           │mall_db│ │Lua/锁│  │延迟消息 │ │注册中心 │  │模拟收银台      │
           └──────┘  └──────┘  └────────┘ └────────┘  └───────────────┘
```

**模块划分（对应指导书 5 部署设计）**

| 模块 | 说明 | 端口 |
| --- | --- | --- |
| `mall` | 父模块（聚合 POM） | - |
| `mall-common` | 公共模块：统一响应、MD5 签名验签、JWT、全局异常、MQ 事件定义 | - |
| `mall-gateway` | 网关：统一入口、路由、Redis+Lua 原子限流、Sentinel 熔断降级 | 10010 |
| `mall-mgr` | 业务微服务：商户/管理员/退款/统计/商品/订单/类目/资金 | 10020 |
| `mall-alipay` | 支付微服务：统一支付下单、支付宝沙箱对接、回调验签与三重幂等、支付订单查询 | 10030 |
| `mall-vue` | 前端：用户商城 + 商户经营后台 + 平台管理后台（Vue3 + TS + Element Plus） | 5173 (dev) |

---

## 目录

1. [环境要求与快速开始](#一环境要求与快速开始)
   - [1.1 环境要求](#11-环境要求) · [1.2 一键启动](#12-一键启动windows推荐) · [1.3 手动分步启动](#13-手动分步启动) · [1.4 演示账号](#14-演示账号) · [1.5 两种运行方式对比](#15-两种运行方式对比)
2. [项目结构](#二项目结构)
3. [前端功能](#三前端功能mall-vue详见-mall-vuereadmemd)
4. [接口清单](#四接口清单统一-post--applicationjson经网关-10010)
5. [统一签名规则](#五统一签名规则防篡改)
6. [状态机说明](#六状态机说明)
7. [核心非功能设计](#七核心非功能设计实训重点考核)
8. [Docker 容器化](#八docker-容器化)
9. [测试与质量保障](#九测试与质量保障)
10. [常见问题](#十常见问题)

---

## 一、环境要求与快速开始

### 1.1 环境要求

| 组件 | 建议版本 | 用途 |
| --- | --- | --- |
| JDK | 17+（工程编译目标 17，验证时使用 Zulu 21） | 运行 / 构建后端 |
| Maven | 3.8+（也可用 IntelliJ IDEA 内置 Maven，`start-all.bat` 会自动探测） | 构建后端可执行 jar |
| Node.js | 18+（验证时使用 v24） | 前端依赖安装、`npm run dev` / `npm run build` |
| Docker Desktop | 较新版本即可 | 中间件与微服务容器：MySQL / Redis / Nacos / RocketMQ |

> **分发出的源码包**不含 `node_modules`、`target`（可执行 jar）、`dist` 与容器数据卷，
> 因此首次运行需要联网（前端 `npm install`、Maven 依赖下载、Docker 基础镜像拉取）。
> 若接收方网络受限，可另外提供 `mall-*/target/*.jar` 与 `mall-vue/node_modules` 一并拷入对应目录。

### 1.2 一键启动（Windows，推荐）

双击或命令行运行项目根目录下的 **`start-all.bat`**：

```bat
start-all.bat              :: 完整联调：中间件容器 + 后端三服务 + 前端（真实网关模式）
start-all.bat mock         :: 仅前端，使用内置 mock 数据，不依赖后端与 Docker
start-all.bat no-docker    :: 复用已在运行的中间件容器，只拉起后端与前端
start-all.bat rebuild      :: 强制 mvn clean package -DskipTests 后再启动
start-all.bat dashboard    :: 额外启动 RocketMQ 控制台（http://localhost:8180）
```

参数可组合，例如 `start-all.bat rebuild no-docker`。

脚本自动完成：环境自检（java / node）→ Docker 未运行则自动拉起 Docker Desktop 并等待引擎就绪 →
中间件容器启动后**轮询等待** mysql/redis/nacos 健康与 RocketMQ 端口就绪（不再固定死等 15s）→
jar 缺失时自动 `mvn package` → 三个微服务各自独立窗口启动并等待端口就绪 →
前端依赖检查（npmmirror 镜像）与 Vite 启动。**停止服务：关闭对应的命令行窗口即可。**

| 场景 | 脚本的处理方式 |
| --- | --- |
| 本机 3306 被原生 MySQL 占用 | 自动把容器 MySQL 映射到 **3307** 并让后端跟随（`MYSQL_PORT=3307`）；3306 空闲时走默认 3306 |
| `mallx-mysql` 容器已在运行 | 直接沿用其当前映射端口，不会因重复运行而重建容器 |
| 项目目录被移动过 | 首次 `docker compose up` 失败时自动 `--force-recreate` 重建容器套用新路径（数据卷不受影响） |
| 源码比 jar 新 | 比对时间戳并提示，执行 `rebuild` 或在 IDE 重新打包即可；无 mvn 时明确提示而非静默用旧包 |
| PATH 中没有 mvn | 依次探测 `%USERPROFILE%\tools\apache-maven-*` 与 IntelliJ IDEA 内置 Maven |

> **前提**：需保持 Docker Desktop 处于运行状态（桌面右下角鲸鱼图标）。容器随 Docker 引擎退出而全部停止，
> 若发现服务莫名不可用，先检查 Docker Desktop 是否还在运行，再运行本脚本即可整体拉起。

### 1.3 手动分步启动

**1. 启动中间件（Docker）**

```bash
cd docker
docker compose up -d mysql redis nacos rocketmq-namesrv rocketmq-broker
```

MySQL 首次启动自动按序执行 `sql/mallDb.sql`（建库）→ `sql/mallTable.sql`（建表）→ `sql/mallInsert.sql`
（初始化数据，含演示账号与商品）→ `sql/demoData.sql`（可选演示交易数据：近 7 天订单/支付/退款/提现，
时间动态生成，使看板与统计图表有正常展示）。脚本头部已含 `SET NAMES utf8mb4`，中文数据编码正确。

> 已有数据卷时如需补演示数据，手动执行：
> `docker exec -i mallx-mysql mysql -uroot -proot123 --default-character-set=utf8mb4 mall_db < sql/demoData.sql`
> （脚本可重复执行：先按编号段清理上次演示数据再写入，刷新为最新"近 7 天"）

**2. 启动后端微服务**

```bash
mvn clean package -DskipTests
java -jar mall-gateway/target/mall-gateway-1.0.0.jar
java -jar mall-mgr/target/mall-mgr-1.0.0.jar
java -jar mall-alipay/target/mall-alipay-1.0.0.jar
```

中间件地址可通过环境变量覆盖：`NACOS_ADDR`、`MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_PWD`、`REDIS_HOST`、
`ROCKETMQ_ADDR`。容器 MySQL 使用非 3306 宿主机端口时，后端三服务启动前需设置 `MYSQL_PORT` 与之保持一致：

```bash
# Windows CMD 示例（容器映射到 3307 时）
set MYSQL_PORT=3307
java -jar mall-gateway/target/mall-gateway-1.0.0.jar
java -jar mall-mgr/target/mall-mgr-1.0.0.jar
java -jar mall-alipay/target/mall-alipay-1.0.0.jar
```

**3. 启动前端**

```bash
cd mall-vue
npm install --registry=https://registry.npmmirror.com   # 首次运行
npm run dev                                             # http://localhost:5173
```

前端默认走 `VITE_USE_MOCK=true`（内置 mock）。联调真实后端时设置环境变量后启动：

```bash
# Windows CMD
set VITE_USE_MOCK=false && npm run dev
```

**4. 联调验证**

```bash
python scripts/api_test.py            # 全链路自动化测试（默认走网关 10010）
```

### 1.4 演示账号

| 角色 | 账号 | 密码 | 说明 | 登录入口 |
| --- | --- | --- | --- | --- |
| 平台管理员 | admin | 123456 | 审核商户、配置费率/渠道、退款/提现审核 | 直接访问 `/admin/login` |
| 商户 | shop1 / shop2 | 123456 | 已审核通过，含演示商品（星选数码旗舰店 / 鲜果之家） | 商城右上角「商户入驻」 |
| 用户 | user1 / user2 | 123456 | 下单购物（小明 / 小美） | 商城右上角「登录 / 注册」 |

> 前端内置 mock 模式使用同一套演示账号，种子数据与 `sql/mallInsert.sql` 一致。
> `sql/demoData.sql`（04 初始化脚本）额外提供近 7 天完整交易链路：11 笔订单覆盖
> 待支付/待发货/已发货/已完成/已关闭，含 1 笔退款已完成 + 1 笔退款待审核（均关联用户 user2，
> 可登录 user2 在「我的订单」查看退款进度、登录 shop2 在「退款售后」处理退款申请）、
> 1 笔提现已到账 + 1 笔提现审核中，手续费按渠道费率计算、商户资金余额自洽。

### 1.5 两种运行方式对比

| | 方式一：容器化（`docker compose --profile app up -d`） | 方式二：本地 `java -jar`（`start-all.bat`） |
| --- | --- | --- |
| 后端进程 | Docker 容器内 | 宿主机独立命令行窗口 |
| 中间件地址 | 服务名互访（`mysql:3306` / `nacos:8848` / `rocketmq-namesrv:9876`） | `127.0.0.1` + 宿主机映射端口 |
| 适用场景 | 交付、演示、免装 JDK/Maven | 本地开发调试、IDE 断点 |

> **两者互斥**（都占用 10010/10020/10030）。切换前先 `docker compose --profile app stop`，或关闭 `MallX-*` 命令行窗口。

---

## 二、项目结构

```
mall/
├── pom.xml                     # 父 POM（聚合 4 个模块）
├── start-all.bat               # Windows 一键启动脚本
├── README.md / TEST_PLAN.md    # 说明与测试计划
├── .dockerignore               # 裁剪镜像构建上下文（仅保留各模块 jar）
├── docker/
│   ├── docker-compose.yml      # 中间件 + 微服务（--profile app）
│   ├── Dockerfile              # 微服务通用镜像（eclipse-temurin:17-jre）
│   ├── broker.conf             # RocketMQ broker 配置
│   └── .env                    # 宿主机端口默认值（MYSQL_HOST_PORT）
├── sql/                        # 01 建库 → 02 建表 → 03 初始化 → 04 演示交易数据
├── scripts/                    # 8 个自动化测试脚本（见「九、测试与质量保障」）
├── docs/                       # 设计与测试文档
├── mall-common/                # 公共模块
├── mall-gateway/               # 网关 :10010
├── mall-mgr/                   # 业务微服务 :10020
├── mall-alipay/                # 支付微服务 :10030
├── mall-vue/                   # 前端 :5173
└── uploads/                    # 上传文件（商品图片/退款凭证），容器内挂载至 /app/uploads
```

---

## 三、前端功能（mall-vue，详见 mall-vue/README.md）

| 端 | 路由 | 核心页面 |
| --- | --- | --- |
| 用户商城 | `/` | 商品列表（类目/搜索/分页）、商品详情、购物车（localStorage 离线购物车，登录后合并）、订单确认（多商户自动拆单）、收银台（三渠道 + 轮询支付结果）、我的订单（确认收货、**申请退款**：填写原因/部分金额、上传凭证、查看退款进度时间线并自动轮询刷新） |
| 商户后台 | `/mch` | 入驻注册（省市区三级下拉 + 详细地址）、工作台（营收/手续费/净收益 + ECharts）、商品管理（发布/编辑/上下架/删除，商户删除自有商品时被订单引用会被拒绝）、订单发货、交易流水（对账/代客发起退款）、**退款售后**（统计概览、待处理列表、同意退款/拒绝并填写处理备注、凭证查看、进度详情）、资金/提现、店铺统计 |
| 平台后台 | `/admin` | 数据看板（交易额/七日走势/渠道占比）、商户审核与差异化费率、全平台支付订单、退款审核（四态流转/凭证查看/兜底管控）、渠道配置（支付宝沙箱参数）、提现审核、类目、操作日志（**已做中文可读化**）；各模块支持删除（见下）；商品图片支持上传/替换（前端压缩、上传后实时生效） |

**平台端删除策略**：仅 `ADMIN` 角色可删除，前端弹出二次确认框；**超级管理员不再受业务数据保护规则拦截**
（如存在订单的商户、被订单引用的商品、已支付的支付单等均可删除）。
例外：**商户删除自有商品**仍受"被订单引用不可删除"限制，以保障交易可追溯。

> **布局与响应式**：全站采用「视口锁定」布局——顶栏/侧栏/内容区几何固定，仅内容区作为唯一滚动容器
> （`html/body` 已禁止文档级滚动与下拉回弹，内容不足一屏时不出现滚动条）。已适配桌面（1920/1440）、
> 平板（1024/768）与手机（390/360）：窄屏下管理端/商户端侧栏自动转为顶部横向菜单并可横滑，
> 商城顶部导航小屏横滑，工具栏自动换行、表格在卡片内横向滚动，各宽高比下均无溢出或布局错乱。
>
> **表格列宽约定**：表格列使用固定 `width`（不随屏幕拉伸，保证各分辨率观感一致）。
> **列数较多时不要使用 `fixed="right"`**——表格总宽超出可视区时固定列会浮盖相邻列造成遮挡；
> 改为普通列 + 横向滚动即可（当前全站仅「渠道配置」因列少保留固定列）。

---

## 四、接口清单（统一 POST + application/json，经网关 10010）

### 商户管理（指导书 4.1）
| 接口 | 说明 |
| --- | --- |
| `POST /mch/register` | 商户注册，返回生成的商户唯一编号 shopNum，初始待审核 |
| `POST /mch/login` | 商户登录，返回 token / shopNum / shopState(0待审核1正常2禁用) |

### 支付交易（4.2 / 4.3，mall-alipay）
| 接口 | 说明 |
| --- | --- |
| `POST /pay/create` | 统一支付下单（WECHAT/ALIPAY/UNIONPAY），返回 payOrderId、payUrl；同商户订单号重复下单幂等 |
| `POST /pay/callback` | 支付结果回调：分布式锁+唯一索引+状态机三重幂等 |
| `POST /pay/query` | 按 payOrderNum+shopNum 查询订单详情与状态 |
| `POST /pay/list/mch` | 商户订单分页，支持 orderState/wayCode 筛选 |

### 退款/售后（4.4 + 售后功能）
| 接口 | 说明 |
| --- | --- |
| `POST /refund/create` | 商户对已支付订单发起退款（支持部分退款，校验可退余额） |
| `POST /refund/query` | 退款审核状态与渠道退款进度查询 |
| `POST /refund/apply` | 用户端按订单申请退款（填写原因/金额、上传凭证；仅本人已支付订单，同单处理中禁止重复提交） |
| `POST /refund/user/list` `/refund/detail` | 用户端退款记录与进度详情（详情按 userNum/shopNum 校验归属） |
| `POST /refund/evidence/upload` | 退款凭证上传（base64 压缩图 ≤2MB，存 `/uploads/refund/`，最多 3 张随申请提交） |
| `POST /refund/mch/list` `/refund/mch/stats` | 商户端退款列表（状态筛选）与统计概览（各状态笔数/待处理与累计退款金额） |
| `POST /refund/mch/audit` | 商户端审核：同意→立即执行渠道退款；拒绝→驳回并填写处理备注（`X-Mch-Token` 校验身份与退款单归属） |
| `POST /admin/refund/audit` | 平台退款审核（兜底管控，与商户端共用同一状态机） |
| `POST /admin/refund/list` | 平台退款列表分页 |

### 平台管理端（4.5 + 3.2 功能设计）
| 接口 | 说明 |
| --- | --- |
| `POST /admin/login` | 管理员登录（多角色：ADMIN/OPS） |
| `POST /admin/mch/audit` | 商户审核：shopState 1-通过 / 2-驳回（驳回必填原因） |
| `POST /admin/mch/list` | 商户列表分页、状态筛选 |
| `POST /admin/mch/rate` | 商户差异化费率配置（优先级高于渠道基础费率） |
| `POST /admin/channel/config` | 支付渠道参数/开关/基础费率配置 |
| `POST /admin/channel/list` | 渠道配置查询 |
| `POST /admin/pay/list` | 全平台支付订单管控（多条件筛选） |
| `POST /admin/withdraw/audit` | 提现审核 |
| `POST /admin/log/list` `/admin/log/delete` | 后台操作日志查询/单条删除（AOP 自动记录，敏感操作操作人取自登录令牌；删除动作自身亦留痕） |
| `POST /admin/mch/delete` `/admin/goods/delete` | 商户/商品删除：仅 ADMIN + 前端二次确认；**超级管理员不受业务数据限制**（商品端仅在商户自助删除时校验订单引用） |
| `POST /admin/goods/pic` | 商品图片上传/替换（仅 ADMIN，前端压缩后 base64 提交；更新后详情缓存即时失效，文件存于 `uploads/` 并经 `/uploads/**` 访问） |
| `POST /admin/pay/delete` `/admin/refund/delete` `/admin/withdraw/delete` | 支付单/退款单/提现单删除：仅 ADMIN + 前端二次确认；**任意状态均可删除** |
| `POST /admin/channel/delete` `/admin/category/delete` | 渠道/类目删除：仅 ADMIN + 前端二次确认；**不再强制保留渠道、也不再拦截子类目或商品引用** |

### 数据统计（4.6）
| 接口 | 说明 |
| --- | --- |
| `POST /statis/platform` | 平台交易总额/笔数、渠道交易占比、七日交易走势（ECharts 适配） |
| `POST /statis/mch` | 商户营收/手续费/净收益/交易笔数/成功率/渠道分布/七日走势，支持时间范围 |

### 电商业务扩展（支撑功能设计 1.2/3.1 业务闭环）
| 接口 | 说明 |
| --- | --- |
| `POST /user/register` `/user/login` | 用户注册登录 |
| `GET /category/list`、`POST /admin/category/save` | 类目查询与维护 |
| `POST /goods/create` `/goods/update` `/goods/list` `/goods/detail` `/goods/delete` | 商品管理（详情走 Redis 缓存；商户删除仅限自有商品，被订单引用时禁止删除） |
| `POST /order/create` | 下单：Redis Lua 原子预扣库存 + DB 条件扣减防超卖 + RocketMQ 延迟关单 |
| `POST /order/list` `/order/detail` | 订单查询（用户/商户双视角） |
| `POST /order/deliver` `/order/confirm` | 商户发货（同步物流单号）、用户确认收货 |
| `POST /fund/overview` `/fund/withdraw/create` `/fund/withdraw/list` | 资金总览、提现申请与记录 |

---

## 五、统一签名规则（防篡改）

所有业务请求必须携带 `sign`（登录接口按文档参数表不要求签名）：

1. 取全部请求参数（查询参数 + JSON 扁平字段），剔除 `sign`、空值与嵌套对象/数组；
2. 按参数名 ASCII 升序拼接：`k1=v1&k2=v2...`；
3. 末尾拼接 `&key=mallx-pay-secret-2026`，整体 MD5 得到 32 位小写签名。

示例：

```text
参数：shopNum=SH417826, payMoney=199.00
拼接：payMoney=199.00&shopNum=SH417826&key=mallx-pay-secret-2026
sign：md5(上述字符串)
```

密钥经 `mallx.sign.secret` 配置（默认 `mallx-pay-secret-2026`，前端 `VITE_SIGN_SECRET` 同步），
网关侧限流兜底、服务侧验签强制，验签失败统一返回 `{"id":"401","message":"签名校验失败，禁止访问"}`。
前端 axios 拦截器（`mall-vue/src/api/client.ts`）按同一规则自动签名，业务代码无感。

---

## 六、状态机说明

* **支付订单 orderState（文档 4.7）**：`0`-支付成功、`1`-支付失败、`2`-已退款、`3`-订单关闭；
  另设 `4`-待支付为下单初始态（文档 4.2.1 示例中的 `orderState:3` 与 4.7 节定义冲突，实现以 4.7 节为准，
  增加 4-待支付以保证"延迟关单"可观测）。
* **退款单 refundState（售后四态）**：`0`-待审核、`1`-处理中（审核通过、渠道退款执行中）、`2`-已完成、
  `3`-已拒绝（审核拒绝或渠道退款失败，备注说明原因）；流转：待审核→(同意)处理中→已完成 / 待审核→(拒绝)已拒绝；
  全额退完时原支付单流转为「已退款」，部分退款保持支付成功（额度由退款单累计占用，已拒绝释放）。
* **商户 shopState**：`0`-待审核、`1`-正常、`2`-禁用。
* **业务订单**：`0`-待支付、`1`-已支付待发货、`2`-已发货、`3`-已完成、`4`-已关闭。
  全额退款完成时，若订单仍处于「待发货」，会同步流转为「已关闭」以阻止继续发货。
* **提现 state**：`0`-审核中、`1`-成功、`2`-失败。

---

## 七、核心非功能设计（实训重点考核）

1. **网关限流熔断**：`RateLimitFilter` 全局/单 IP 双维度 Redis+Lua 固定窗口原子限流
   （默认全局 1000 QPS / 单 IP 200 QPS，可配），超限统一返回 `{"message":"系统繁忙，请稍后再试"}`；
   叠加 Sentinel 网关流控规则（QPS 1000），熔断降级同文案兜底；Redis 故障时限流器 fail-open 保可用。
2. **Redis 原子并发控制**：库存扣减/回补由 Lua 脚本在 Redis 端原子完成（`lua/stock_deduct.lua`），
   配合数据库 `stock >= n` 条件更新双保险防超卖；分布式锁（Redisson）用于回调与退款审核的并发串行化。
3. **支付回调幂等（三重机制）**：
   ① Redisson 分布式锁按 payOrderId 串行化；
   ② 回调流水表 `(pay_order_id, channel_order_no)` 唯一索引，重复回调插入失败直接短路返回；
   ③ 订单状态机条件更新（仅 待支付→成功/失败），影响行数为 0 即视为已处理。
4. **消息队列异步容错**：RocketMQ 延迟消息（级别 16 = 30 分钟，可通过环境变量调整为演示用短延迟）
   实现支付订单与业务订单超时自动关闭 + 库存回补；支付成功事件异步驱动订单流转与销量累计（业务解耦）。
5. **高并发适配**：商品详情/渠道配置多级缓存、统计接口聚合查询、Feign+负载均衡服务间调用、
   限流降级与 fail-open 策略，支撑 QPS≥1000 压测（实测约 1100 QPS）。
6. **安全性**：全接口 MD5 验签防篡改、支付宝异步通知 RSA2 验签、密码 MD5 加盐存储、内部接口令牌鉴权、
   Long 型 ID 序列化为字符串防前端精度丢失。
7. **操作审计**：管理端动作由 AOP 切面自动记录（方法名 + 参数 JSON + 操作人 + IP），
   前端展示层做了**中文可读化**（模块名与字段名翻译、枚举值语义化、自动过滤 `sign`/`password`），
   原始审计数据保持不变。

---

## 八、Docker 容器化

**中间件**：`docker/docker-compose.yml`（MySQL/Redis/Nacos/RocketMQ，含健康检查与 SQL 自动初始化）。

**微服务镜像**：`mvn package` 后执行 `docker compose --profile app up -d`（共享 `docker/Dockerfile`，
基于 `eclipse-temurin:17-jre`，构建上下文已由根目录 `.dockerignore` 裁剪为仅各模块可执行 jar）。
容器内以服务名互访（`mysql:3306` / `nacos:8848` / `rocketmq-namesrv:9876` / `redis:6379`），
宿主机端口映射仍为 10010/10020/10030，前端 `VITE_USE_MOCK=false` 模式无需任何改动。

容器化部署关键配置：

| 配置项 | 位置 | 说明 |
| --- | --- | --- |
| `MYSQL_HOST_PORT=3307` | `docker/.env` | 宿主机端口默认值（本机 3306 被原生 MySQL 占用）。shell 环境变量优先级更高，所以 `start-all.bat` 的自动判定仍生效 |
| `brokerIP1=host.docker.internal` | `docker/broker.conf` | RocketMQ broker 注册给客户端的地址，必须**容器内与宿主机后端都能访问**。写死 `127.0.0.1` 只适用于"后端跑宿主机"，后端进容器后会连接失败（`RemotingConnectException`） |
| `PAY_MOCK_BASE_URL` | compose（mall-alipay） | 收银台页面地址。网关经 `lb://` 转发会带上容器内网 IP（`172.x.x.x`），宿主机浏览器不可达，故显式指向 `http://localhost:10010` |
| `../uploads:/app/uploads` | compose（mall-mgr） | 上传文件持久化，容器重建不丢图片，且与本地 `java -jar` 运行共享同一批文件 |
| `.dockerignore` | 项目根 | 排除 `node_modules`/`target/classes`/`dist` 等，构建上下文从数百 MB 降至约 148 MB |

> 首次构建需拉取基础镜像，国内网络直连 docker.io 可能被拒绝（本机未配置镜像加速器），可用公开镜像源：
> ```bash
> docker pull docker.m.daocloud.io/library/eclipse-temurin:17-jre
> docker tag docker.m.daocloud.io/library/eclipse-temurin:17-jre eclipse-temurin:17-jre
> ```

可选 RocketMQ 控制台：`docker compose --profile dashboard up -d`（http://localhost:8180）。

---

## 九、测试与质量保障

`scripts/` 下 8 个脚本，仅依赖 Python 标准库或 Playwright，覆盖接口层与真实浏览器三层角色。

| 脚本 | 类型 | 覆盖内容 |
| --- | --- | --- |
| `e2e_page_smoke.py` | 浏览器 | 三端全部页面渲染、路由守卫、关键交互、390px 响应式无溢出 |
| `e2e_storefront.py` | 浏览器 | 用户商城：分类筛选、登录回跳、加购、直购/购物车双链路下单支付、订单状态 |
| `e2e_merchant.py` | 浏览器 | 商户端：发布商品、上下架、发货、对账、退款申请与拒绝、提现、统计图表 |
| `e2e_admin.py` | 浏览器 | 管理端：商户/退款/提现审核、渠道费率、类目、日志、删除二次确认 |
| `e2e_full_flow.py` | 浏览器 | 五阶段自洽闭环：入驻→审核→上架→购物→支付→发货→退款→提现→审核→收货 |
| `api_test.py` | 接口 | 全链路 88 项：签名、幂等、库存、退款双端、统计、资金、删除矩阵、限流 |
| `api_boundary_test.py` | 接口 | 防篡改、验证码防重放、输入边界、SQL 注入、权限控制 |
| `perf_test.py` | 接口 | 响应时间分位（p50/p95/p99）、并发吞吐、限流保护 |

**推荐执行顺序**（脚本间有数据依赖）：

```bash
python scripts/e2e_page_smoke.py       # 1) 页面冒烟
python scripts/e2e_storefront.py       # 2) 商城端
python scripts/e2e_merchant.py         # 3) 商户端
python scripts/e2e_admin.py            # 4) 管理端
python scripts/e2e_full_flow.py        # 5) 全流程闭环
python scripts/perf_test.py            # 6) 性能压测
python scripts/api_test.py             # 7) 接口全链路（会删除演示数据，务必靠后）
python scripts/api_boundary_test.py    # 8) 安全与边界
```

> ⚠️ `api_test.py` 会删除演示的支付单/提现单/商品，**必须排在 e2e 与 perf 之后**，
> 否则后续用例会因数据被删而"假失败"（表现如"商品详情 成功 0/30""找不到审核中提现单"）。
> 另外 `api_test` 末尾有 400 并发压测，紧随其后的脚本建议间隔数秒再执行，避免被限流误伤。

### 重置测试数据

测试会向库中写入大量临时数据（随机后缀的商户/用户/商品、订单、日志等）。恢复为纯演示数据：

```bash
cd docker
docker compose stop mysql
docker compose rm -f mysql
docker volume rm docker_mysql-data                        # 删除数据卷
docker compose up -d mysql                                # 首次启动自动重跑 sql/ 四个脚本
docker compose --profile app up -d                        # 重新拉起后端容器
# 可选：清理测试上传的凭证与缓存
docker exec mallx-mgr sh -c 'rm -rf /app/uploads/refund/* /app/uploads/goods/*'
docker exec mallx-redis redis-cli FLUSHALL
```

> 数据卷被删除后，MySQL 会重新执行 `sql/01~04`，回到「2 商户 / 2 用户 / 10 商品 / 11 订单」的演示状态。
> 若只想保留数据而非清空，可先备份：`docker exec mallx-mysql sh -c "mysqldump -uroot -proot123 mall_db > /tmp/b.sql" && docker cp mallx-mysql:/tmp/b.sql ./backup.sql`。

---

## 十、常见问题

* **Nacos 未启动**：服务可启动但网关路由不可用（lb 解析失败），请先 `docker compose up -d nacos`。
* **Docker Desktop 必须保持运行**：所有容器随 Docker 引擎启停。若服务整体莫名不可用，
  先确认桌面右下角鲸鱼图标仍在运行；引擎停止会导致全部容器退出，重新打开 Docker Desktop 后
  运行 `start-all.bat`（或 `docker compose up -d`）即可整体恢复，数据卷不丢失。
* **3306 端口被原生 MySQL 占用**：`start-all.bat` 已自动处理（容器 MySQL 改用 3307，后端跟随）；
  手动启动时设置 `MYSQL_HOST_PORT=3307`（compose）与 `MYSQL_PORT=3307`（后端）。
* **容器化后支付下单报 `RemotingConnectException: connect to 127.0.0.1:10911 failed`**：
  broker 注册地址写死成了 `127.0.0.1`。确认 `docker/broker.conf` 中为 `brokerIP1 = host.docker.internal`，
  并重启 broker（`docker compose restart rocketmq-broker`）。
* **容器化后点「立即支付」收银台打不开**：`payUrl` 返回了容器内网地址。确认 compose 中 mall-alipay 的
  `PAY_MOCK_BASE_URL=http://localhost:10010`，重建该容器即可。
* **Docker Hub 拉取镜像超时**：直连 `registry-1.docker.io` 可能被拒。可用公开镜像源拉取后重打标签
  （见「八、Docker 容器化」），拉取中断直接重试即可断点续传。
* **移动项目目录后启动失败**（`bind source path does not exist`）：容器把旧绝对路径固化在挂载配置里。
  `start-all.bat` 会自动 `--force-recreate` 兜底；手动执行
  `docker compose up -d --force-recreate mysql rocketmq-broker` 亦可（数据卷不受影响）。
* **SQL 中文乱码**：四个 SQL 脚本头部已含 `SET NAMES utf8mb4`。若历史数据卷中已存在乱码数据
  （表现为前端中文显示异常），删除 `mysql-data` 卷并重启容器重新初始化即可。
* **支付宝真实沙箱**：在管理端「渠道配置」为 ALIPAY 渠道配置 appId/私钥/支付宝公钥后，
  `/pay/create` 自动切换为支付宝沙箱页面支付；未配置时使用内置模拟收银台（`mallx.pay.mock-enabled` 可关）。
* **演示快速超时关单**：环境变量 `ORDER_CLOSE_DELAY_LEVEL=3`（10 秒）/`PAY_CLOSE_DELAY_LEVEL=3` 后重启服务，
  下单后不支付即可观察延迟消息关单与库存回补。
* **HTTPS**：文档要求统一 HTTPS，生产部署可在网关前置 Nginx/SLB 终结 TLS；本工程默认 HTTP 便于联调。
* **前端 mock / 真实模式**：`mall-vue` 默认 mock（无需后端）；设 `VITE_USE_MOCK=false` 后走网关 10010。
  mock 种子数据与 `sql/mallInsert.sql` 一致，演示账号通用。
* **端口占用**：后端三服务占用 10010/10020/10030，前端 5173；被占用时启动会失败，可用
  `netstat -ano | findstr :10010` 排查并结束进程。若既跑了容器化后端又想跑本地 `java -jar`，会互相抢端口。
* **npm 安装慢/失败**：使用 `--registry=https://registry.npmmirror.com`（start-all.bat 已内置）。
* **服务启动失败的排查顺序**：① Docker Desktop 是否运行、容器是否全部 Up（`docker ps`）；
  ② 10010/10020/10030 是否监听；③ 后端窗口/日志中 Nacos 报 `Client not connected` 说明 nacos 未就绪，
  等待其 healthy 后重启后端即可。全链路自检：`python scripts/api_test.py`。

---

## 附：文档索引

| 文档 | 内容 |
| --- | --- |
| `TEST_PLAN.md` | 测试计划与用例设计 |
| `docs/` | 设计说明、基准文档等 |
| `mall-vue/README.md` | 前端工程说明 |
| `《行业工程实践》教学大纲.pdf` / `《行业工程实践》实验指导书.pdf` | 课题目录与要求 |
