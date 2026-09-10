package com.mallx.mgr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallx.mgr.dto.ChannelStat;
import com.mallx.mgr.entity.PayOrderView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 支付订单统计查询（数据统计接口 4.6 底层查询）。
 * 说明：mall-mgr 与 mall-alipay 共用 mall_db，统计侧对支付订单表做只读聚合；
 * 企业实践中可通过数据仓库/接口聚合实现跨服务统计，课程项目为简化同库查询。
 */
@Mapper
public interface PayOrderViewMapper extends BaseMapper<PayOrderView> {

    /** 平台累计交易：支付成功与已退款均计入交易 */
    @Select("SELECT IFNULL(SUM(money), 0) AS totalMoney, COUNT(*) AS totalCount "
            + "FROM t_pay_order WHERE order_state IN (0, 2)")
    Map<String, Object> selectPlatformTotal();

    /** 平台各渠道交易占比 */
    @Select("SELECT way_code AS wayCode, COUNT(*) AS count, IFNULL(SUM(money), 0) AS tradeMoney "
            + "FROM t_pay_order WHERE order_state IN (0, 2) GROUP BY way_code")
    List<ChannelStat> selectPlatformChannelRatio();

    /** 近 N 天每日交易金额与笔数 */
    @Select("SELECT DATE_FORMAT(IFNULL(pay_time, created_at), '%Y-%m-%d') AS date, "
            + "IFNULL(SUM(money), 0) AS tradeMoney, COUNT(*) AS tradeCount "
            + "FROM t_pay_order "
            + "WHERE order_state IN (0, 2) AND IFNULL(pay_time, created_at) >= #{startTime} "
            + "GROUP BY date ORDER BY date")
    List<Map<String, Object>> selectDailyTrade(@Param("startTime") String startTime);

    /** 商户累计营收与手续费 */
    @Select("SELECT IFNULL(SUM(money), 0) AS totalMoney, IFNULL(SUM(mch_fee), 0) AS totalFee, COUNT(*) AS totalCount "
            + "FROM t_pay_order "
            + "WHERE shop_num = #{shopNum} AND order_state IN (0, 2) "
            + "AND IFNULL(pay_time, created_at) >= #{startTime} AND IFNULL(pay_time, created_at) < #{endTime}")
    Map<String, Object> selectMchTotal(@Param("shopNum") String shopNum,
                                       @Param("startTime") String startTime,
                                       @Param("endTime") String endTime);

    /** 商户各渠道交易数据 */
    @Select("SELECT way_code AS wayCode, COUNT(*) AS count, IFNULL(SUM(money), 0) AS tradeMoney, "
            + "IFNULL(SUM(mch_fee), 0) AS fee "
            + "FROM t_pay_order "
            + "WHERE shop_num = #{shopNum} AND order_state IN (0, 2) "
            + "AND IFNULL(pay_time, created_at) >= #{startTime} AND IFNULL(pay_time, created_at) < #{endTime} "
            + "GROUP BY way_code")
    List<ChannelStat> selectMchChannelTrade(@Param("shopNum") String shopNum,
                                            @Param("startTime") String startTime,
                                            @Param("endTime") String endTime);

    /** 商户近 N 天每日交易 */
    @Select("SELECT DATE_FORMAT(IFNULL(pay_time, created_at), '%Y-%m-%d') AS date, "
            + "IFNULL(SUM(money), 0) AS tradeMoney, COUNT(*) AS tradeCount "
            + "FROM t_pay_order "
            + "WHERE shop_num = #{shopNum} AND order_state IN (0, 2) "
            + "AND IFNULL(pay_time, created_at) >= #{startTime} "
            + "GROUP BY date ORDER BY date")
    List<Map<String, Object>> selectMchDailyTrade(@Param("shopNum") String shopNum,
                                                  @Param("startTime") String startTime);

    /** 商户交易笔数统计（按终态拆分成功/失败，用于成功率） */
    @Select("SELECT IFNULL(SUM(CASE WHEN order_state IN (0, 2) THEN 1 ELSE 0 END), 0) AS successCount, "
            + "IFNULL(SUM(CASE WHEN order_state = 1 THEN 1 ELSE 0 END), 0) AS failCount "
            + "FROM t_pay_order WHERE shop_num = #{shopNum} AND order_state IN (0, 1, 2)")
    Map<String, Object> selectMchTradeCount(@Param("shopNum") String shopNum);

    /** 商户已退款总额（可退款余额校验用） */
    @Select("SELECT IFNULL(SUM(refund_money), 0) FROM t_refund_order "
            + "WHERE pay_order_id = #{payOrderId} AND refund_state = 1")
    BigDecimal selectRefundedSum(@Param("payOrderId") String payOrderId);

    /** 商户累计退款成功总额（资金总览用） */
    @Select("SELECT IFNULL(SUM(refund_money), 0) FROM t_refund_order "
            + "WHERE shop_num = #{shopNum} AND refund_state = 1")
    BigDecimal selectShopRefundedSum(@Param("shopNum") String shopNum);
}
