SET NAMES utf8mb4;

-- =============================================================
-- Mall-X 多商家电商平台 · 建表脚本 mallTable.sql
-- 订单状态统一说明(orderState)：0-支付成功、1-支付失败、2-已退款、3-订单关闭、4-待支付
-- 退款状态(refundState)：0-审核中、1-退款成功、2-退款失败
-- 商户状态(shopState)：0-待审核、1-正常、2-禁用
-- =============================================================
USE mall_db;

-- 平台管理员
CREATE TABLE IF NOT EXISTS t_admin_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(50)  NOT NULL COMMENT '登录账号',
    password    VARCHAR(64)  NOT NULL COMMENT '登录密码(MD5加盐)',
    real_name   VARCHAR(50)  DEFAULT NULL COMMENT '真实姓名',
    role        VARCHAR(20)  NOT NULL DEFAULT 'ADMIN' COMMENT '角色 ADMIN-超级管理员 OPS-运营',
    state       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1-正常 0-禁用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '平台管理员';

-- 商户
CREATE TABLE IF NOT EXISTS t_mch (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    shop_num     VARCHAR(32)   NOT NULL COMMENT '商户唯一编号',
    shop_name    VARCHAR(64)   NOT NULL COMMENT '商户店铺名称',
    shop_phone   VARCHAR(20)   NOT NULL COMMENT '商户联系手机号',
    shop_address VARCHAR(255)  DEFAULT NULL COMMENT '商户经营地址',
    username     VARCHAR(50)   NOT NULL COMMENT '商户登录账号',
    password     VARCHAR(64)   NOT NULL COMMENT '商户登录密码(MD5加盐)',
    shop_state   TINYINT       NOT NULL DEFAULT 0 COMMENT '商户状态 0-待审核 1-正常 2-禁用',
    audit_remark VARCHAR(255)  DEFAULT NULL COMMENT '审核备注',
    balance      DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '可提现余额',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mch_shop_num (shop_num),
    UNIQUE KEY uk_mch_username (username),
    KEY idx_mch_state (shop_state)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商户';

-- 商户差异化费率（优先级高于渠道基础费率）
CREATE TABLE IF NOT EXISTS t_mch_rate (
    id        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    shop_num  VARCHAR(32)   NOT NULL COMMENT '商户编号',
    way_code  VARCHAR(20)   NOT NULL COMMENT '支付渠道编码',
    mch_rate  DECIMAL(8,4)  NOT NULL COMMENT '商户费率，如0.0060表示千分之六',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mch_rate (shop_num, way_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商户差异化费率';

-- 支付渠道配置
CREATE TABLE IF NOT EXISTS t_channel_config (
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    way_code          VARCHAR(20)   NOT NULL COMMENT '渠道编码 WECHAT/ALIPAY/UNIONPAY',
    channel_name      VARCHAR(50)   NOT NULL COMMENT '渠道名称',
    switch_status     TINYINT       NOT NULL DEFAULT 1 COMMENT '渠道开关 1-开启 0-关闭',
    base_rate         DECIMAL(8,4)  NOT NULL DEFAULT 0.0060 COMMENT '渠道基础费率',
    app_id            VARCHAR(64)   DEFAULT NULL COMMENT '渠道应用ID',
    private_key       TEXT          DEFAULT NULL COMMENT '商户私钥',
    alipay_public_key TEXT          DEFAULT NULL COMMENT '平台公钥',
    gateway_url       VARCHAR(255)  DEFAULT NULL COMMENT '渠道网关地址',
    notify_key        VARCHAR(64)   DEFAULT NULL COMMENT '回调验签密钥',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_way_code (way_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '支付渠道配置';

-- 支付订单
CREATE TABLE IF NOT EXISTS t_pay_order (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    pay_order_id     VARCHAR(64)   NOT NULL COMMENT '支付系统唯一订单号',
    mch_order_no     VARCHAR(64)   NOT NULL COMMENT '商户侧订单号',
    shop_num         VARCHAR(32)   NOT NULL COMMENT '商户编号',
    shop_name        VARCHAR(64)   DEFAULT NULL COMMENT '商户名称',
    app_id           VARCHAR(64)   DEFAULT NULL COMMENT '应用唯一标识',
    way_code         VARCHAR(20)   NOT NULL COMMENT '支付方式 WECHAT/ALIPAY/UNIONPAY',
    money            DECIMAL(12,2) NOT NULL COMMENT '交易支付金额(元)',
    mch_fee          DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '手续费(元)',
    subject          VARCHAR(128)  NOT NULL COMMENT '商品交易标题',
    user_num         VARCHAR(32)   DEFAULT NULL COMMENT '用户唯一编号',
    order_state      TINYINT       NOT NULL DEFAULT 4 COMMENT '0-支付成功 1-支付失败 2-已退款 3-订单关闭 4-待支付',
    channel_order_no VARCHAR(64)   DEFAULT NULL COMMENT '第三方渠道订单号',
    pay_time         DATETIME      DEFAULT NULL COMMENT '支付完成时间',
    expire_time      DATETIME      DEFAULT NULL COMMENT '订单过期时间',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '订单创建时间',
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pay_order_id (pay_order_id),
    UNIQUE KEY uk_mch_order (shop_num, mch_order_no),
    KEY idx_pay_state (order_state),
    KEY idx_pay_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '支付订单';

-- 支付回调流水（唯一索引 + 状态机 + 分布式锁 三重幂等之唯一索引层）
CREATE TABLE IF NOT EXISTS t_pay_callback (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    pay_order_id     VARCHAR(64)  NOT NULL COMMENT '支付系统订单号',
    channel_order_no VARCHAR(64)  NOT NULL COMMENT '第三方渠道订单号',
    pay_state        TINYINT      NOT NULL COMMENT '支付状态 0-成功 1-失败',
    pay_time         DATETIME     DEFAULT NULL COMMENT '第三方支付完成时间',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '回调时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_callback (pay_order_id, channel_order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '支付回调流水';

-- 退款订单
CREATE TABLE IF NOT EXISTS t_refund_order (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    refund_order_id  VARCHAR(64)   NOT NULL COMMENT '退款订单唯一编号',
    pay_order_id     VARCHAR(64)   NOT NULL COMMENT '原支付订单号',
    order_num        VARCHAR(64)   DEFAULT NULL COMMENT '关联业务订单号(用户端按订单申请时记录)',
    shop_num         VARCHAR(32)   NOT NULL COMMENT '商户编号',
    user_num         VARCHAR(32)   DEFAULT NULL COMMENT '申请用户编号(用户端发起时记录)',
    refund_money     DECIMAL(12,2) NOT NULL COMMENT '退款金额',
    refund_reason    VARCHAR(255)  DEFAULT NULL COMMENT '退款原因',
    evidence         VARCHAR(1024) DEFAULT NULL COMMENT '退款凭证图片地址(JSON数组)',
    refund_state     TINYINT       NOT NULL DEFAULT 0 COMMENT '退款状态 0-待审核 1-处理中 2-已完成 3-已拒绝',
    channel_refund_no VARCHAR(64)  DEFAULT NULL COMMENT '渠道退款流水号',
    audit_remark     VARCHAR(255)  DEFAULT NULL COMMENT '审核/处理备注',
    audit_time       DATETIME      DEFAULT NULL COMMENT '审核时间',
    refund_time      DATETIME      DEFAULT NULL COMMENT '退款完成时间',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_order_id (refund_order_id),
    KEY idx_refund_pay (pay_order_id),
    KEY idx_refund_shop (shop_num),
    KEY idx_refund_user (user_num)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '退款订单';

-- 平台用户（消费者）
CREATE TABLE IF NOT EXISTS t_user (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_num   VARCHAR(32)  NOT NULL COMMENT '用户唯一编号',
    username   VARCHAR(50)  NOT NULL COMMENT '登录账号',
    password   VARCHAR(64)  NOT NULL COMMENT '登录密码(MD5加盐)',
    nickname   VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    phone      VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_num (user_num),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '平台用户';

-- 商品类目
CREATE TABLE IF NOT EXISTS t_category (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    category_name VARCHAR(50) NOT NULL COMMENT '类目名称',
    parent_id     BIGINT      NOT NULL DEFAULT 0 COMMENT '父类目ID 0-一级类目',
    sort          INT         NOT NULL DEFAULT 0 COMMENT '排序',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品类目';

-- 商品（含库存，配合 Redis+Lua 原子扣减）
CREATE TABLE IF NOT EXISTS t_goods (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    goods_num   VARCHAR(32)   NOT NULL COMMENT '商品唯一编号',
    goods_name  VARCHAR(128)  NOT NULL COMMENT '商品名称',
    shop_num    VARCHAR(32)   NOT NULL COMMENT '所属商户编号',
    category_id BIGINT        DEFAULT NULL COMMENT '类目ID',
    price       DECIMAL(12,2) NOT NULL COMMENT '商品价格(元)',
    stock       INT           NOT NULL DEFAULT 0 COMMENT '数据库库存',
    sales       INT           NOT NULL DEFAULT 0 COMMENT '累计销量',
    goods_state TINYINT       NOT NULL DEFAULT 1 COMMENT '商品状态 1-上架 0-下架',
    pic_url     VARCHAR(255)  DEFAULT NULL COMMENT '商品图片',
    detail      VARCHAR(1024) DEFAULT NULL COMMENT '商品详情',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_goods_num (goods_num),
    KEY idx_goods_shop (shop_num),
    KEY idx_goods_state (goods_state)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品';

-- 业务订单（用户购物订单）
CREATE TABLE IF NOT EXISTS t_order (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_num        VARCHAR(64)   NOT NULL COMMENT '业务订单号',
    user_num         VARCHAR(32)   NOT NULL COMMENT '用户编号',
    shop_num         VARCHAR(32)   NOT NULL COMMENT '商户编号',
    total_money      DECIMAL(12,2) NOT NULL COMMENT '订单总金额',
    order_state      TINYINT       NOT NULL DEFAULT 0 COMMENT '业务订单状态 0-待支付 1-已支付待发货 2-已发货 3-已完成 4-已关闭',
    pay_order_id     VARCHAR(64)   DEFAULT NULL COMMENT '关联支付订单号',
    receiver_name    VARCHAR(50)   DEFAULT NULL COMMENT '收货人',
    receiver_phone   VARCHAR(20)   DEFAULT NULL COMMENT '收货电话',
    receiver_address VARCHAR(255)  DEFAULT NULL COMMENT '收货地址',
    logistics_company VARCHAR(50)  DEFAULT NULL COMMENT '物流公司',
    logistics_no     VARCHAR(64)   DEFAULT NULL COMMENT '物流单号',
    pay_time         DATETIME      DEFAULT NULL COMMENT '支付时间',
    deliver_time     DATETIME      DEFAULT NULL COMMENT '发货时间',
    receive_time     DATETIME      DEFAULT NULL COMMENT '收货时间',
    close_time       DATETIME      DEFAULT NULL COMMENT '关闭时间',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_num (order_num),
    KEY idx_order_user (user_num),
    KEY idx_order_shop (shop_num),
    KEY idx_order_state (order_state)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '业务订单';

-- 订单明细
CREATE TABLE IF NOT EXISTS t_order_item (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_num   VARCHAR(64)   NOT NULL COMMENT '业务订单号',
    goods_num   VARCHAR(32)   NOT NULL COMMENT '商品编号',
    goods_name  VARCHAR(128)  NOT NULL COMMENT '商品名称',
    goods_price DECIMAL(12,2) NOT NULL COMMENT '商品单价',
    goods_count INT           NOT NULL COMMENT '购买数量',
    total_money DECIMAL(12,2) NOT NULL COMMENT '小计金额',
    PRIMARY KEY (id),
    KEY idx_item_order (order_num)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单明细';

-- 提现记录
CREATE TABLE IF NOT EXISTS t_withdraw (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    withdraw_no VARCHAR(64)   NOT NULL COMMENT '提现单号',
    shop_num    VARCHAR(32)   NOT NULL COMMENT '商户编号',
    money       DECIMAL(12,2) NOT NULL COMMENT '提现金额',
    state       TINYINT       NOT NULL DEFAULT 0 COMMENT '提现状态 0-审核中 1-成功 2-失败',
    remark      VARCHAR(255)  DEFAULT NULL COMMENT '备注',
    finish_time DATETIME      DEFAULT NULL COMMENT '完成时间',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_withdraw_no (withdraw_no),
    KEY idx_withdraw_shop (shop_num)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '提现记录';

-- 后台操作日志
CREATE TABLE IF NOT EXISTS t_operate_log (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    module     VARCHAR(50)  DEFAULT NULL COMMENT '操作模块',
    content    VARCHAR(512) DEFAULT NULL COMMENT '操作内容',
    operator   VARCHAR(50)  DEFAULT NULL COMMENT '操作人',
    ip         VARCHAR(64)  DEFAULT NULL COMMENT '操作IP',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_log_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '后台操作日志';
