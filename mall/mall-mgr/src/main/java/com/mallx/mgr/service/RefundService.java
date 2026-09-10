package com.mallx.mgr.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mallx.common.constant.MallConstant;
import com.mallx.common.exception.BizException;
import com.mallx.common.result.R;
import com.mallx.common.util.OrderNoUtil;
import com.mallx.mgr.dto.RefundAuditReq;
import com.mallx.mgr.dto.RefundCreateReq;
import com.mallx.mgr.dto.RefundQueryReq;
import com.mallx.mgr.entity.PayOrderView;
import com.mallx.mgr.entity.RefundOrder;
import com.mallx.mgr.feign.PayClient;
import com.mallx.mgr.mapper.PayOrderViewMapper;
import com.mallx.mgr.mapper.RefundOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 退款服务：商户发起退款（4.4.1）、退款查询（4.4.2）、平台退款审核。
 * 审核通过后调用支付微服务执行渠道退款，成功后支付订单流转为已退款。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RefundOrderMapper refundOrderMapper;
    private final PayOrderViewMapper payOrderViewMapper;
    private final PayClient payClient;
    private final RedissonClient redissonClient;

    @org.springframework.beans.factory.annotation.Value("${mallx.internal.token:mallx-internal-token}")
    private String internalToken;

    /** 商户针对已支付有效订单发起退款申请（支持部分退款） */
    public R create(RefundCreateReq req) {
        PayOrderView payOrder = payOrderViewMapper.selectOne(Wrappers.<PayOrderView>lambdaQuery()
                .eq(PayOrderView::getPayOrderId, req.getPayOrderId()));
        if (payOrder == null || !payOrder.getShopNum().equals(req.getShopNum())) {
            throw new BizException("原支付订单不存在");
        }
        if (payOrder.getOrderState() != MallConstant.PAY_STATE_SUCCESS) {
            throw new BizException("仅支付成功的订单可发起退款");
        }
        BigDecimal refunded = payOrderViewMapper.selectRefundedSum(req.getPayOrderId());
        BigDecimal refundable = payOrder.getMoney().subtract(refunded == null ? BigDecimal.ZERO : refunded);
        if (req.getRefundMoney().compareTo(refundable) > 0) {
            throw new BizException("退款金额超出可退款余额 " + refundable);
        }
        RefundOrder refund = new RefundOrder();
        refund.setRefundOrderId(OrderNoUtil.refundOrderId());
        refund.setPayOrderId(req.getPayOrderId());
        refund.setShopNum(req.getShopNum());
        refund.setRefundMoney(req.getRefundMoney());
        refund.setRefundReason(req.getRefundReason());
        refund.setRefundState(MallConstant.REFUND_WAIT);
        refundOrderMapper.insert(refund);
        return R.ok("退款申请提交成功，等待审核").set("refundOrderId", refund.getRefundOrderId());
    }

    public R query(RefundQueryReq req) {
        RefundOrder refund = refundOrderMapper.selectOne(Wrappers.<RefundOrder>lambdaQuery()
                .eq(RefundOrder::getRefundOrderId, req.getRefundOrderId()));
        if (refund == null || !refund.getShopNum().equals(req.getShopNum())) {
            throw new BizException("退款订单不存在");
        }
        R r = R.ok("查询成功");
        r.set("refundOrderId", refund.getRefundOrderId());
        r.set("payOrderId", refund.getPayOrderId());
        r.set("refundMoney", refund.getRefundMoney());
        r.set("refundState", refund.getRefundState());
        r.set("refundTime", refund.getRefundTime() == null ? null : TIME_FMT.format(refund.getRefundTime()));
        r.set("refundReason", refund.getRefundReason());
        return r;
    }

    /** 平台退款审核：1-通过执行渠道退款 2-驳回（分布式锁防并发重复审核） */
    public R audit(RefundAuditReq req) {
        RLock lock = redissonClient.getLock(MallConstant.LOCK_REFUND_AUDIT + req.getRefundOrderId());
        boolean locked = false;
        try {
            locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new BizException("系统繁忙，请稍后再试");
            }
            RefundOrder refund = refundOrderMapper.selectOne(Wrappers.<RefundOrder>lambdaQuery()
                    .eq(RefundOrder::getRefundOrderId, req.getRefundOrderId()));
            if (refund == null) {
                throw new BizException("退款订单不存在");
            }
            if (refund.getRefundState() != MallConstant.REFUND_WAIT) {
                throw new BizException("该退款单已审核，请勿重复操作");
            }
            if (req.getAuditState() != null && req.getAuditState() == 2) {
                refund.setRefundState(MallConstant.REFUND_FAIL);
                refund.setAuditRemark(req.getAuditRemark());
                refundOrderMapper.updateById(refund);
                return R.ok("退款申请已驳回");
            }
            // 审核通过 → 支付微服务执行渠道退款
            Map<String, Object> body = Map.of(
                    "payOrderId", refund.getPayOrderId(),
                    "refundMoney", refund.getRefundMoney().toPlainString(),
                    "refundReason", refund.getRefundReason() == null ? "售后退款" : refund.getRefundReason());
            R channelResult;
            try {
                channelResult = payClient.executeRefund(internalToken, body);
            } catch (Exception e) {
                log.error("渠道退款执行异常 refundOrderId={}", refund.getRefundOrderId(), e);
                refund.setRefundState(MallConstant.REFUND_FAIL);
                refund.setAuditRemark("渠道退款执行异常：" + e.getMessage());
                refundOrderMapper.updateById(refund);
                throw new BizException("渠道退款执行异常，退款单已标记失败");
            }
            if (!"0".equals(String.valueOf(channelResult.get("id")))) {
                refund.setRefundState(MallConstant.REFUND_FAIL);
                refund.setAuditRemark("渠道退款失败：" + channelResult.get("message"));
                refundOrderMapper.updateById(refund);
                throw new BizException(String.valueOf(channelResult.get("message")));
            }
            refund.setRefundState(MallConstant.REFUND_SUCCESS);
            refund.setChannelRefundNo(String.valueOf(channelResult.get("channelRefundNo")));
            refund.setRefundTime(LocalDateTime.now());
            refund.setAuditRemark(req.getAuditRemark());
            refundOrderMapper.updateById(refund);
            // 原支付订单流转为已退款（状态机：仅支付成功可流转）
            payOrderViewMapper.update(null, Wrappers.<PayOrderView>lambdaUpdate()
                    .set(PayOrderView::getOrderState, MallConstant.PAY_STATE_REFUNDED)
                    .eq(PayOrderView::getPayOrderId, refund.getPayOrderId())
                    .eq(PayOrderView::getOrderState, MallConstant.PAY_STATE_SUCCESS));
            return R.ok("退款成功");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("退款审核被中断");
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }

    /** 退款列表分页（平台端） */
    public R list(int pageNum, int pageSize, String shopNum, Integer refundState) {
        var page = refundOrderMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize),
                Wrappers.<RefundOrder>lambdaQuery()
                        .eq(shopNum != null && !shopNum.isBlank(), RefundOrder::getShopNum, shopNum)
                        .eq(refundState != null, RefundOrder::getRefundState, refundState)
                        .orderByDesc(RefundOrder::getId));
        R r = R.ok("查询成功");
        r.set("total", page.getTotal());
        r.set("pages", page.getPages());
        r.set("current", page.getCurrent());
        r.set("size", page.getSize());
        r.set("records", page.getRecords());
        return r;
    }
}
