package com.mallx.mgr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallx.mgr.entity.BizOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderMapper extends BaseMapper<BizOrder> {

    /** 状态机：支付成功后 待支付→已支付 */
    @Update("UPDATE t_order SET order_state = 1, pay_order_id = #{payOrderId}, pay_time = #{payTime} "
            + "WHERE order_num = #{orderNum} AND order_state = 0")
    int markPaid(@Param("orderNum") String orderNum, @Param("payOrderId") String payOrderId,
                 @Param("payTime") String payTime);

    /** 状态机：已支付→已发货 */
    @Update("UPDATE t_order SET order_state = 2, logistics_company = #{company}, logistics_no = #{logisticsNo}, "
            + "deliver_time = NOW() WHERE order_num = #{orderNum} AND order_state = 1")
    int markDelivered(@Param("orderNum") String orderNum, @Param("company") String company,
                      @Param("logisticsNo") String logisticsNo);

    /** 状态机：已发货→已完成（确认收货） */
    @Update("UPDATE t_order SET order_state = 3, receive_time = NOW() "
            + "WHERE order_num = #{orderNum} AND order_state = 2")
    int markReceived(@Param("orderNum") String orderNum);

    /** 状态机：待支付→已关闭 */
    @Update("UPDATE t_order SET order_state = 4, close_time = NOW() "
            + "WHERE order_num = #{orderNum} AND order_state = 0")
    int markClosed(@Param("orderNum") String orderNum);

    /**
     * 状态机：全额退款完成 → 已关闭。
     * 仅作用于「待发货」订单（已发货/已完成不在其列），
     * 避免退款完成后订单仍停留在「待发货」而被继续发货。
     */
    @Update("UPDATE t_order SET order_state = 4, close_time = NOW() "
            + "WHERE order_num = #{orderNum} AND order_state = 1")
    int markClosedByRefund(@Param("orderNum") String orderNum);
}
