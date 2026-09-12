package com.mallx.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务单号生成工具：前缀 + 时间戳 + 随机序列，保证单机不重复。
 */
public class OrderNoUtil {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final AtomicLong SEQ = new AtomicLong(0);

    public static synchronized String gen(String prefix) {
        long seq = SEQ.incrementAndGet() % 100000;
        String rand = String.format("%05d", ThreadLocalRandom.current().nextInt(100000));
        return prefix + LocalDateTime.now().format(FMT) + rand + String.format("%04d", seq % 10000);
    }

    public static String payOrderId()  { return gen("PAY"); }
    public static String refundOrderId() { return gen("RF"); }
    public static String orderNum()    { return gen("SO"); }
    public static String channelOrderNo() { return gen("CH"); }
    public static String withdrawNo()  { return gen("WD"); }

    /** 商户编号：SH + 6 位随机数字（简短格式；唯一性由 uk_mch_shop_num 唯一索引与注册重试共同保证） */
    public static String shopNum()     { return "SH" + randomDigits(); }

    public static String userNum()     { return gen("U"); }

    /** 商品编号：G + 6 位随机数字（简短格式；唯一性由 uk_goods_goods_num 唯一索引与发布重试共同保证） */
    public static String goodsNum()    { return "G" + randomDigits(); }

    /** 6 位随机数字（100000-999999，首位非 0 保证定长） */
    private static String randomDigits() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(100000, 1000000));
    }
}
