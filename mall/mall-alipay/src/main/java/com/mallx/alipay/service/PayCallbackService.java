package com.mallx.alipay.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mallx.alipay.entity.PayCallbackLog;
import com.mallx.alipay.entity.PayOrder;
import com.mallx.alipay.mapper.PayCallbackMapper;
import com.mallx.alipay.mapper.PayOrderMapper;
import com.mallx.alipay.mq.PayMqSender;
import com.mallx.common.constant.MallConstant;
import com.mallx.common.exception.BizException;
import com.mallx.common.mq.PaySuccessEvent;
import com.mallx.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 支付回调核心处理 —— 「分布式锁 + 数据库唯一索引 + 订单状态机」三重幂等设计：
 * 1. 分布式锁（Redisson）：同一支付订单的并发回调串行化，避免并发争抢；
 * 2. 数据库唯一索引：回调流水表 (pay_order_id, channel_order_no) 唯一，重复回调插入失败即短路返回；
 * 3. 订单状态机：仅允许 待支付(4) → 支付成功(0)/支付失败(1) 的合法流转，条件更新影响行数为0说明已处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayCallbackService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PayOrderMapper payOrderMapper;
    private final PayCallbackMapper payCallbackMapper;
    private final RedissonClient redissonClient;
    private final PayMqSender payMqSender;

    public R process(String payOrderId, String channelOrderNo, int payState, LocalDateTime payTime) {
        RLock lock = redissonClient.getLock(MallConstant.LOCK_PAY_CALLBACK + payOrderId);
        boolean locked = false;
        try {
            locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new BizException("系统繁忙，请稍后再试");
            }

            // 第一重幂等：回调流水唯一索引
            PayCallbackLog callbackLog = new PayCallbackLog();
            callbackLog.setPayOrderId(payOrderId);
            callbackLog.setChannelOrderNo(channelOrderNo);
            callbackLog.setPayState(payState);
            callbackLog.setPayTime(payTime);
            try {
                payCallbackMapper.insert(callbackLog);
            } catch (DuplicateKeyException e) {
                log.info("重复回调，唯一索引幂等拦截 payOrderId={} channelOrderNo={}", payOrderId, channelOrderNo);
                return R.ok("回调处理成功");
            }

            // 第三重幂等：订单状态机条件更新（仅待支付可流转）
            int targetState = payState == MallConstant.CALLBACK_SUCCESS
                    ? MallConstant.PAY_STATE_SUCCESS : MallConstant.PAY_STATE_FAIL;
            int updated = payOrderMapper.update(null, Wrappers.<PayOrder>lambdaUpdate()
                    .set(PayOrder::getOrderState, targetState)
                    .set(PayOrder::getChannelOrderNo, channelOrderNo)
                    .set(PayOrder::getPayTime, payTime)
                    .eq(PayOrder::getPayOrderId, payOrderId)
                    .eq(PayOrder::getOrderState, MallConstant.PAY_STATE_WAIT));
            if (updated == 0) {
                log.info("订单状态机幂等拦截（非待支付状态） payOrderId={}", payOrderId);
                return R.ok("回调处理成功");
            }

            if (targetState == MallConstant.PAY_STATE_SUCCESS) {
                PayOrder po = payOrderMapper.selectOne(Wrappers.<PayOrder>lambdaQuery()
                        .eq(PayOrder::getPayOrderId, payOrderId));
                payMqSender.sendPaySuccess(new PaySuccessEvent(
                        po.getPayOrderId(), po.getMchOrderNo(), po.getShopNum(), po.getWayCode(),
                        po.getMoney().toPlainString(), TIME_FMT.format(payTime)));
            }
            return R.ok("回调处理成功");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("回调处理被中断");
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }
}
