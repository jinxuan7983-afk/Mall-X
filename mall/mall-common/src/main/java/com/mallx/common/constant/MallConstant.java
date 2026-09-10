package com.mallx.common.constant;

/**
 * 全局常量：状态机、Redis Key 前缀、密钥盐值等。
 */
public final class MallConstant {

    private MallConstant() {
    }

    /** 密码加盐 */
    public static final String PASSWORD_SALT = "mallx-salt";

    /** ===== 支付订单状态（接口文档 4.7 节：0-支付成功、1-支付失败、2-已退款、3-订单关闭；4-待支付为初始状态） ===== */
    public static final int PAY_STATE_SUCCESS = 0;
    public static final int PAY_STATE_FAIL = 1;
    public static final int PAY_STATE_REFUNDED = 2;
    public static final int PAY_STATE_CLOSED = 3;
    public static final int PAY_STATE_WAIT = 4;

    /** 支付回调状态：0-成功 1-失败 */
    public static final int CALLBACK_SUCCESS = 0;
    public static final int CALLBACK_FAIL = 1;

    /** ===== 退款状态：0-审核中 1-退款成功 2-退款失败 ===== */
    public static final int REFUND_WAIT = 0;
    public static final int REFUND_SUCCESS = 1;
    public static final int REFUND_FAIL = 2;

    /** ===== 商户状态：0-待审核 1-正常 2-禁用 ===== */
    public static final int MCH_WAIT_AUDIT = 0;
    public static final int MCH_NORMAL = 1;
    public static final int MCH_DISABLED = 2;

    /** ===== 业务订单状态：0-待支付 1-已支付待发货 2-已发货 3-已完成 4-已关闭 ===== */
    public static final int ORDER_WAIT_PAY = 0;
    public static final int ORDER_PAID = 1;
    public static final int ORDER_DELIVERED = 2;
    public static final int ORDER_FINISHED = 3;
    public static final int ORDER_CLOSED = 4;

    /** 提现状态：0-审核中 1-成功 2-失败 */
    public static final int WITHDRAW_WAIT = 0;
    public static final int WITHDRAW_SUCCESS = 1;
    public static final int WITHDRAW_FAIL = 2;

    /** ===== Redis Key ===== */
    public static final String KEY_STOCK = "stock:goods:";
    public static final String KEY_GOODS_CACHE = "goods:info:";
    public static final String KEY_CHANNEL_CACHE = "channel:config:";
    public static final String LOCK_PAY_CALLBACK = "lock:pay:callback:";
    public static final String LOCK_REFUND_AUDIT = "lock:refund:audit:";
    public static final String LOCK_ORDER_CREATE = "lock:order:goods:";

    /** ===== RocketMQ Topic ===== */
    public static final String TOPIC_PAY_SUCCESS = "mall-pay-success-topic";
    public static final String TOPIC_PAY_CLOSE = "mall-pay-close-topic";
    public static final String TOPIC_ORDER_CLOSE = "mall-order-close-topic";

    /** 内部接口鉴权头 */
    public static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";
}
