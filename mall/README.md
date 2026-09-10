# Mall-X 多商家电商平台

依据《行业工程实践》实验指导书与教学大纲实现的多商家入驻高并发电商平台，面向**普通用户 / 入驻商户 / 平台管理员**三类角色，
实现商品管理、商户入驻、购物车、下单交易、支付、订单履约（发货/收货）、售后退款、资金提现、统计报表等完整电商业务闭环。

- **后端**：Spring Boot 3 + Spring Cloud Alibaba 微服务架构（Nacos / Redis / RocketMQ / MySQL），支撑 QPS ≥ 1000
- **前端**：Vue 3 + TypeScript + Element Plus + ECharts + Pinia（PC 端，三角色单应用）

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

## 一、快速启动

### 方式一：一键启动（Windows，推荐）

双击或命令行运行项目根目录下的 **`start-all.bat`**：

```bat
start-all.bat        :: 完整联调：中间件容器 + 后端三服务 + 前端（真实网关模式）
start-all.bat mock   :: 仅前端，使用内置 mock 数据，不依赖后端与 Docker
```

脚本自动完成：中间件容器启动（15s）→ jar 缺失时自动 `mvn package` → 三个微服务各自独立窗口启动 →
前端依赖检查（npmmirror 镜像）与 Vite 启动。**停止服务：关闭对应的命令行窗口即可。**

> **端口自适应**：若本机 3306 已被原生 MySQL 占用，脚本会自动把容器 MySQL 映射到 **3307** 并让后端通过
> `MYSQL_PORT=3307` 连接（已在本机验证）；3306 空闲时仍走默认 3306，无需任何配置。
>
> **前提**：需保持 Docker Desktop 处于运行状态（桌面右下角鲸鱼图标）。容器随 Docker 引擎退出而全部停止，
> 若发现服务莫名不可用，先检查 Docker Desktop 是否还在运行，再运行本脚本即可整体拉起。

### 方式二：手动分步启动

**1. 启动中间件（Docker）**

```bash
cd docker
docker compose up -d mysql redis nacos rocketmq-namesrv rocketmq-broker
```

MySQL 首次启动自动按序执行 `sql/mallDb.sql`（建库）→ `sql/mallTable.sql`（建表）→ `sql/mallInsert.sql`
（初始化数据，含演示账号与商品；脚本头部已含 `SET NAMES utf8mb4`，中文数据编码正确）。若 MySQL 已手工执行过
SQL，请删除 `mysql-data` 卷后重新启动。三个 SQL 脚本头部新增的 `SET NAMES utf8mb4` 用于修复导入时
client 字符集为 latin1 导致的中文双重编码乱码问题。

> **端口冲突**：本机 3306 被原生 MySQL 占用时，为容器指定其他宿主机端口并让后端跟随：
>
> ```bash
> # Windows CMD：容器 MySQL 映射到 3307
> set MYSQL_HOST_PORT=3307
> docker compose up -d mysql redis nacos rocketmq-namesrv rocketmq-broker
> ```
>
> RocketMQ broker 默认 `brokerIP1=127.0.0.1`，适用于"容器跑中间件 + 宿主机跑微服务"的本地联调方式。

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
python scripts/api_test.py http://127.0.0.1:10010
```

脚本覆盖：注册→审核→登录→商品→下单（Lua 扣库存）→支付下单→回调幂等→发货→收货→退款→统计→资金→限流→防篡改。

### 演示账号

| 角色 | 账号 | 密码 | 说明 | 登录入口 |
| --- | --- | --- | --- | --- |
| 平台管理员 | admin | 123456 | 审核商户、配置费率/渠道、退款/提现审核 | 商城右上角「管理中心」 |
| 商户 | shop1 / shop2 | 123456 | 已审核通过，含演示商品（星选数码旗舰店 / 鲜果之家） | 商城右上角「商户入驻」 |
| 用户 | user1 | 123456 | 下单购物 | 商城右上角「登录 / 注册」 |

> 前端内置 mock 模式使用同一套演示账号，种子数据与 `sql/mallInsert.sql` 一致。

---

## 二、前端功能（mall-vue，详见 mall-vue/README.md）

| 端 | 路由 | 核心页面 |
| --- | --- | --- |
| 用户商城 | `/` | 商品列表（类目/搜索/分页）、商品详情、购物车（localStorage 离线购物车，登录后合并）、订单确认（多商户自动拆单）、收银台（三渠道 + 轮询支付结果）、我的订单（确认收货） |
| 商户后台 | `/mch` | 入驻注册、工作台（营收/手续费/净收益 + ECharts）、商品管理、订单发货、交易流水（对账/发起部分退款）、退款进度、资金/提现、店铺统计 |
| 平台后台 | `/admin` | 数据看板（交易额/七日走势/渠道占比）、商户审核与差异化费率、全平台支付订单、退款审核、渠道配置（支付宝沙箱参数）、提现审核、类目、操作员（ADMIN/OPS）、操作日志 |

---

## 三、接口清单（统一 POST + application/json，经网关 10010）

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

### 退款管理（4.4）
| 接口 | 说明 |
| --- | --- |
| `POST /refund/create` | 商户对已支付订单发起退款（支持部分退款，校验可退余额） |
| `POST /refund/query` | 退款审核状态与渠道退款进度查询 |
| `POST /admin/refund/audit` | 平台退款审核（通过→Feign 调支付微服务执行渠道退款） |
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
| `POST /admin/user/create` `/admin/user/list` | 后台操作员账号管理 |
| `POST /admin/log/list` | 后台操作日志查询（AOP 自动记录） |

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
| `POST /goods/create` `/goods/update` `/goods/list` `/goods/detail` | 商品管理（详情走 Redis 缓存） |
| `POST /order/create` | 下单：Redis Lua 原子预扣库存 + DB 条件扣减防超卖 + RocketMQ 延迟关单 |
| `POST /order/list` `/order/detail` | 订单查询（用户/商户双视角） |
| `POST /order/deliver` `/order/confirm` | 商户发货（同步物流单号）、用户确认收货 |
| `POST /fund/overview` `/fund/withdraw/create` `/fund/withdraw/list` | 资金总览、提现申请与记录 |

---

## 四、统一签名规则（防篡改）

所有业务请求必须携带 `sign`（登录接口按文档参数表不要求签名）：

1. 取全部请求参数（查询参数 + JSON 扁平字段），剔除 `sign`、空值与嵌套对象/数组；
2. 按参数名 ASCII 升序拼接：`k1=v1&k2=v2...`；
3. 末尾拼接 `&key=mallx-pay-secret-2026`，整体 MD5 得到 32 位小写签名。

示例：

```text
参数：shopNum=SH1000000001, payMoney=199.00
拼接：payMoney=199.00&shopNum=SH1000000001&key=mallx-pay-secret-2026
sign：md5(上述字符串)
```

密钥经 `mallx.sign.secret` 配置（默认 `mallx-pay-secret-2026`，前端 `VITE_SIGN_SECRET` 同步），
网关侧限流兜底、服务侧验签强制，验签失败统一返回 `{"id":"401","message":"签名校验失败，禁止访问"}`。
前端 axios 拦截器（`mall-vue/src/api/client.ts`）按同一规则自动签名，业务代码无感。

---

## 五、状态机说明

* **支付订单 orderState（文档 4.7）**：`0`-支付成功、`1`-支付失败、`2`-已退款、`3`-订单关闭；
  另设 `4`-待支付为下单初始态（文档 4.2.1 示例中的 `orderState:3` 与 4.7 节定义冲突，实现以 4.7 节为准，
  增加 4-待支付以保证"延迟关单"可观测）。
* **退款单 refundState**：`0`-审核中、`1`-退款成功、`2`-退款失败。
* **商户 shopState**：`0`-待审核、`1`-正常、`2`-禁用。
* **业务订单**：`0`-待支付、`1`-已支付待发货、`2`-已发货、`3`-已完成、`4`-已关闭。
* **提现 state**：`0`-审核中、`1`-成功、`2`-失败。

---

## 六、核心非功能设计（实训重点考核）

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
   限流降级与 fail-open 策略，支撑 QPS≥1000 压测。
6. **安全性**：全接口 MD5 验签防篡改、支付宝异步通知 RSA2 验签、密码 MD5 加盐存储、内部接口令牌鉴权、
   Long 型 ID 序列化为字符串防前端精度丢失。

---

## 七、Docker 容器化

* 中间件：`docker/docker-compose.yml`（MySQL/Redis/Nacos/RocketMQ，含健康检查与 SQL 自动初始化）。
* 微服务镜像：`mvn package` 后执行 `docker compose --profile app up -d`（共享 `docker/Dockerfile`，
  基于 eclipse-temurin:17-jre）。
* 可选 RocketMQ 控制台：`docker compose --profile dashboard up -d`（http://localhost:8180）。

---

## 八、常见问题

* **Nacos 未启动**：服务可启动但网关路由不可用（lb 解析失败），请先 `docker compose up -d nacos`。
* **Docker Desktop 必须保持运行**：所有中间件容器随 Docker 引擎启停。若服务整体莫名不可用，
  先确认桌面右下角鲸鱼图标仍在运行；引擎停止会导致五个容器同时退出，重新打开 Docker Desktop 后
  运行 `start-all.bat`（或 `docker compose up -d`）即可整体恢复，数据卷不丢失。
* **3306 端口被原生 MySQL 占用**：`start-all.bat` 已自动处理（容器 MySQL 改用 3307，后端跟随）；
  手动启动时参照「快速启动 → 方式二」的 `MYSQL_HOST_PORT` / `MYSQL_PORT` 说明。
* **Docker Hub 拉取镜像超时**：已在本机 `%USERPROFILE%\.docker\daemon.json` 配置国内镜像加速器
  （daocloud / 1panel / ustc 等），修改后需重启 Docker Desktop 生效。拉取中断直接重试即可断点续传。
* **SQL 中文乱码**：三个 SQL 脚本头部已含 `SET NAMES utf8mb4`。若历史数据卷中已存在乱码数据
  （表现为前端中文显示异常），删除 `mysql-data` 卷并重启容器重新初始化即可。
* **支付宝真实沙箱**：在管理端「渠道配置」为 ALIPAY 渠道配置 appId/私钥/支付宝公钥后，
  `/pay/create` 自动切换为支付宝沙箱页面支付；未配置时使用内置模拟收银台（`mallx.pay.mock-enabled` 可关）。
* **演示快速超时关单**：环境变量 `ORDER_CLOSE_DELAY_LEVEL=3`（10 秒）/ `PAY_CLOSE_DELAY_LEVEL=3` 后重启服务，
  下单后不支付即可观察延迟消息关单与库存回补。
* **HTTPS**：文档要求统一 HTTPS，生产部署可在网关前置 Nginx/SLB 终结 TLS；本工程默认 HTTP 便于联调。
* **前端 mock / 真实模式**：`mall-vue` 默认 mock（无需后端）；设 `VITE_USE_MOCK=false` 后走网关 10010。
  mock 种子数据与 `sql/mallInsert.sql` 一致，演示账号通用。
* **端口占用**：后端三服务占用 10010/10020/10030，前端 5173；被占用时启动会失败，可用
  `netstat -ano | findstr :10010` 排查并结束进程。
* **npm 安装慢/失败**：使用 `--registry=https://registry.npmmirror.com`（start-all.bat 已内置）。
* **服务启动失败的排查顺序**：① Docker Desktop 是否运行、五容器是否 Up（`docker ps`）；
  ② 10010/10020/10030 是否监听；③ 后端窗口/日志中 Nacos 报 `Client not connected` 说明 nacos 未就绪，
  等待其 healthy 后重启后端即可。全链路自检：`python scripts/api_test.py`。
