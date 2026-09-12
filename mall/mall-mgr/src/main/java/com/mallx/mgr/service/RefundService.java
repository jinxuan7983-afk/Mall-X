package com.mallx.mgr.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mallx.common.constant.MallConstant;
import com.mallx.common.exception.BizException;
import com.mallx.common.result.R;
import com.mallx.common.util.OrderNoUtil;
import com.mallx.mgr.dto.RefundApplyReq;
import com.mallx.mgr.dto.RefundAuditReq;
import com.mallx.mgr.dto.RefundCreateReq;
import com.mallx.mgr.dto.RefundMchAuditReq;
import com.mallx.mgr.dto.RefundQueryReq;
import com.mallx.mgr.entity.BizOrder;
import com.mallx.mgr.entity.PayOrderView;
import com.mallx.mgr.entity.RefundOrder;
import com.mallx.mgr.feign.PayClient;
import com.mallx.mgr.mapper.OrderMapper;
import com.mallx.mgr.mapper.PayOrderViewMapper;
import com.mallx.mgr.mapper.RefundOrderMapper;
import com.mallx.mgr.support.AdminGuard;
import com.mallx.mgr.support.MchGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 退款/售后服务：用户端申请（含凭证）、商户端审核（同意执行渠道退款/拒绝）、平台端审核（兜底管控）、
 * 退款进度查询与统计。状态流转：待审核(0) → 处理中(1) → 已完成(2)；待审核(0) → 已拒绝(3)。
 * 两端共用同一退款单数据源，状态变更即刷新可见（用户端与管理端/商户端实时一致）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 凭证图片大小上限（解码后字节数） */
    private static final long MAX_EVIDENCE_BYTES = 2L * 1024 * 1024;

    /** 前端压缩后提交的 data URL 图片（仅允许 jpg/png/webp） */
    private static final Pattern DATA_URL = Pattern.compile("^data:image/(jpeg|png|webp);base64,(.+)$",
            Pattern.CASE_INSENSITIVE);

    /** 凭证地址前缀：仅接纳本系统上传目录地址，防外部链接注入 */
    private static final String EVIDENCE_URL_PREFIX = "/uploads/refund/";

    private final RefundOrderMapper refundOrderMapper;
    private final PayOrderViewMapper payOrderViewMapper;
    private final OrderMapper orderMapper;
    private final PayClient payClient;
    private final RedissonClient redissonClient;
    private final AdminGuard adminGuard;
    private final MchGuard mchGuard;
    private final ObjectMapper objectMapper;

    @Value("${mallx.upload.dir:./uploads}")
    private String uploadDir;

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
        refund.setOrderNum(payOrder.getMchOrderNo());
        refund.setShopNum(req.getShopNum());
        refund.setUserNum(payOrder.getUserNum());
        refund.setRefundMoney(req.getRefundMoney());
        refund.setRefundReason(req.getRefundReason());
        refund.setRefundState(MallConstant.REFUND_WAIT);
        refundOrderMapper.insert(refund);
        return R.ok("退款申请提交成功，等待审核").set("refundOrderId", refund.getRefundOrderId());
    }

    /* ==================== 用户端：申请 / 进度 / 凭证 ==================== */

    /**
     * 用户端：对本人已支付订单发起退款申请（支持部分退款，可附凭证图片）。
     * 同一支付单存在待审核/处理中的退款时禁止重复提交；退款金额不得超过可退余额（未拒绝的退款占用额度）。
     */
    public R applyByUser(RefundApplyReq req) {
        BizOrder order = orderMapper.selectOne(Wrappers.<BizOrder>lambdaQuery()
                .eq(BizOrder::getOrderNum, req.getOrderNum()));
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!StringUtils.hasText(order.getUserNum()) || !order.getUserNum().equals(req.getUserNum())) {
            throw new BizException("只能对本人订单发起退款申请");
        }
        if (order.getOrderState() == MallConstant.ORDER_WAIT_PAY) {
            throw new BizException("订单尚未支付，无需申请退款");
        }
        if (order.getOrderState() == MallConstant.ORDER_CLOSED) {
            throw new BizException("订单已关闭，无法申请退款");
        }
        if (!StringUtils.hasText(order.getPayOrderId())) {
            throw new BizException("订单支付信息缺失，暂不支持退款");
        }
        PayOrderView payOrder = payOrderViewMapper.selectOne(Wrappers.<PayOrderView>lambdaQuery()
                .eq(PayOrderView::getPayOrderId, order.getPayOrderId()));
        if (payOrder == null || payOrder.getOrderState() == null
                || payOrder.getOrderState() != MallConstant.PAY_STATE_SUCCESS) {
            throw new BizException("该订单当前支付状态不支持退款");
        }
        Long processing = refundOrderMapper.selectCount(Wrappers.<RefundOrder>lambdaQuery()
                .eq(RefundOrder::getPayOrderId, payOrder.getPayOrderId())
                .in(RefundOrder::getRefundState, MallConstant.REFUND_WAIT, MallConstant.REFUND_PROCESSING));
        if (processing != null && processing > 0) {
            throw new BizException("该订单已有退款申请正在处理中，请勿重复提交");
        }
        BigDecimal refundable = refundableOf(payOrder);
        if (req.getRefundMoney().compareTo(refundable) > 0) {
            throw new BizException("退款金额超出可退款余额 " + refundable.toPlainString());
        }
        RefundOrder refund = new RefundOrder();
        refund.setRefundOrderId(OrderNoUtil.refundOrderId());
        refund.setPayOrderId(payOrder.getPayOrderId());
        refund.setOrderNum(order.getOrderNum());
        refund.setShopNum(order.getShopNum());
        refund.setUserNum(order.getUserNum());
        refund.setRefundMoney(req.getRefundMoney());
        refund.setRefundReason(req.getRefundReason());
        refund.setEvidence(toEvidenceJson(req.getEvidence()));
        refund.setRefundState(MallConstant.REFUND_WAIT);
        refundOrderMapper.insert(refund);
        return R.ok("退款申请已提交，等待商户处理").set("refundOrderId", refund.getRefundOrderId());
    }

    /** 用户端：我的退款申请列表（按申请时间倒序） */
    public R userList(String userNum, int pageNum, int pageSize) {
        if (!StringUtils.hasText(userNum)) {
            throw new BizException("userNum不能为空");
        }
        Page<RefundOrder> page = refundOrderMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<RefundOrder>lambdaQuery()
                        .eq(RefundOrder::getUserNum, userNum)
                        .orderByDesc(RefundOrder::getId));
        return pageResult(page);
    }

    /** 退款进度详情（用户传 userNum、商户传 shopNum 校验归属，可只传其一；管理端不传） */
    public R detail(String refundOrderId, String userNum, String shopNum) {
        RefundOrder refund = findRefund(refundOrderId);
        if (StringUtils.hasText(userNum) && !userNum.equals(refund.getUserNum())) {
            throw new BizException("退款单不存在");
        }
        if (StringUtils.hasText(shopNum) && !shopNum.equals(refund.getShopNum())) {
            throw new BizException("退款单不存在");
        }
        return R.ok("查询成功").set("refund", refund);
    }

    /** 上传退款凭证：base64 图片（≤2MB，jpg/png/webp）保存至本地上传目录，返回可访问地址 */
    public R uploadEvidence(String imageBase64) {
        if (!StringUtils.hasText(imageBase64)) {
            throw new BizException("请选择要上传的凭证图片");
        }
        String payload = imageBase64.trim();
        String ext = "jpg";
        Matcher matcher = DATA_URL.matcher(payload);
        if (matcher.matches()) {
            String type = matcher.group(1).toLowerCase();
            ext = "jpeg".equals(type) ? "jpg" : type;
            payload = matcher.group(2);
        } else if (payload.startsWith("data:")) {
            throw new BizException("仅支持 JPG/PNG/WebP 格式的图片");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(payload.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            throw new BizException("图片数据格式不正确");
        }
        if (bytes.length == 0) {
            throw new BizException("图片内容为空");
        }
        if (bytes.length > MAX_EVIDENCE_BYTES) {
            throw new BizException("凭证图片大小不能超过 2MB");
        }
        try {
            Path dir = Paths.get(uploadDir, "refund").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String fileName = "EV" + System.currentTimeMillis()
                    + String.format("%04d", (int) (Math.random() * 10000)) + "." + ext;
            Files.write(dir.resolve(fileName), bytes);
            return R.ok("凭证上传成功").set("url", EVIDENCE_URL_PREFIX + fileName);
        } catch (IOException e) {
            log.error("退款凭证保存失败", e);
            throw new BizException("凭证保存失败，请稍后重试");
        }
    }

    /* ==================== 商户端：列表 / 审核 / 统计 ==================== */

    /** 商户端：本商户退款单列表（支持状态筛选） */
    public R mchList(String shopNum, int pageNum, int pageSize, Integer refundState) {
        if (!StringUtils.hasText(shopNum)) {
            throw new BizException("shopNum不能为空");
        }
        Page<RefundOrder> page = refundOrderMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<RefundOrder>lambdaQuery()
                        .eq(RefundOrder::getShopNum, shopNum)
                        .eq(refundState != null, RefundOrder::getRefundState, refundState)
                        .orderByDesc(RefundOrder::getId));
        return pageResult(page);
    }

    /** 商户端：审核退款申请（同意→执行渠道退款；拒绝→驳回）。身份经 X-Mch-Token 校验，且仅能处理本商户退款单 */
    public R mchAudit(RefundMchAuditReq req, String operatorToken) {
        String shopNum = mchGuard.requireMch(operatorToken);
        return auditCore(req.getRefundOrderId(), req.getAuditState(), req.getAuditRemark(), shopNum);
    }

    /** 商户端：退款统计（各状态笔数 + 累计已完成退款金额，供退款管理页概览） */
    public R mchStats(String shopNum) {
        if (!StringUtils.hasText(shopNum)) {
            throw new BizException("shopNum不能为空");
        }
        R r = R.ok("查询成功");
        r.set("waitCount", countByState(shopNum, MallConstant.REFUND_WAIT));
        r.set("processingCount", countByState(shopNum, MallConstant.REFUND_PROCESSING));
        r.set("successCount", countByState(shopNum, MallConstant.REFUND_SUCCESS));
        r.set("failCount", countByState(shopNum, MallConstant.REFUND_FAIL));
        r.set("totalCount", countByState(shopNum, null));
        BigDecimal successMoney = payOrderViewMapper.selectShopRefundedSum(shopNum);
        r.set("successMoney", successMoney == null ? BigDecimal.ZERO : successMoney);
        BigDecimal waitMoney = refundOrderMapper.selectList(Wrappers.<RefundOrder>lambdaQuery()
                        .eq(RefundOrder::getShopNum, shopNum)
                        .in(RefundOrder::getRefundState, MallConstant.REFUND_WAIT, MallConstant.REFUND_PROCESSING))
                .stream().map(RefundOrder::getRefundMoney)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        r.set("pendingMoney", waitMoney);
        return r;
    }

    /* ==================== 内部工具 ==================== */

    private RefundOrder findRefund(String refundOrderId) {
        if (!StringUtils.hasText(refundOrderId)) {
            throw new BizException("请指定退款单号");
        }
        RefundOrder refund = refundOrderMapper.selectOne(Wrappers.<RefundOrder>lambdaQuery()
                .eq(RefundOrder::getRefundOrderId, refundOrderId));
        if (refund == null) {
            throw new BizException("退款单不存在");
        }
        return refund;
    }

    /** 可退余额 = 支付金额 - 未拒绝的退款金额（待审核/处理中/已完成均占用额度） */
    private BigDecimal refundableOf(PayOrderView payOrder) {
        BigDecimal used = payOrderViewMapper.selectRefundedSum(payOrder.getPayOrderId());
        return payOrder.getMoney().subtract(used == null ? BigDecimal.ZERO : used);
    }

    /** 凭证地址归一化为 JSON 数组字符串（仅保留本系统上传地址，最多 6 张） */
    private String toEvidenceJson(List<String> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return null;
        }
        List<String> urls = evidence.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(u -> u.startsWith(EVIDENCE_URL_PREFIX))
                .distinct()
                .limit(6)
                .toList();
        if (urls.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(urls);
        } catch (Exception e) {
            throw new BizException("凭证数据格式不正确");
        }
    }

    private long countByState(String shopNum, Integer state) {
        Long count = refundOrderMapper.selectCount(Wrappers.<RefundOrder>lambdaQuery()
                .eq(RefundOrder::getShopNum, shopNum)
                .eq(state != null, RefundOrder::getRefundState, state));
        return count == null ? 0 : count;
    }

    private R pageResult(Page<RefundOrder> page) {
        R r = R.ok("查询成功");
        r.set("total", page.getTotal());
        r.set("pages", page.getPages());
        r.set("current", page.getCurrent());
        r.set("size", page.getSize());
        r.set("records", page.getRecords());
        return r;
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
        r.set("orderNum", refund.getOrderNum());
        r.set("refundMoney", refund.getRefundMoney());
        r.set("refundState", refund.getRefundState());
        r.set("refundReason", refund.getRefundReason());
        r.set("evidence", refund.getEvidence());
        r.set("auditRemark", refund.getAuditRemark());
        r.set("auditTime", refund.getAuditTime() == null ? null : TIME_FMT.format(refund.getAuditTime()));
        r.set("refundTime", refund.getRefundTime() == null ? null : TIME_FMT.format(refund.getRefundTime()));
        return r;
    }

    /** 平台退款审核（兜底管控：商户未处理时平台可直接处理） */
    public R audit(RefundAuditReq req) {
        return auditCore(req.getRefundOrderId(), req.getAuditState(), req.getAuditRemark(), null);
    }

    /**
     * 审核核心（商户端/平台端共用，分布式锁防并发重复审核）。
     * 同意：待审核→处理中（先落库保证两端进度可见）→ 调用渠道退款 → 成功置已完成；渠道失败置已拒绝并记录原因。
     * 拒绝：待审核→已拒绝并记录处理备注。
     * expectShopNum 非空时校验退款单归属（商户端仅能处理本商户退款单）。
     */
    private R auditCore(String refundOrderId, Integer auditState, String auditRemark, String expectShopNum) {
        RLock lock = redissonClient.getLock(MallConstant.LOCK_REFUND_AUDIT + refundOrderId);
        boolean locked = false;
        try {
            locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new BizException("系统繁忙，请稍后再试");
            }
            RefundOrder refund = findRefund(refundOrderId);
            if (expectShopNum != null && !expectShopNum.equals(refund.getShopNum())) {
                throw new BizException("无权处理该退款单");
            }
            if (refund.getRefundState() != MallConstant.REFUND_WAIT) {
                throw new BizException("该退款单已处理，请勿重复操作");
            }
            if (auditState != null && auditState == 2) {
                refund.setRefundState(MallConstant.REFUND_FAIL);
                refund.setAuditRemark(auditRemark);
                refund.setAuditTime(LocalDateTime.now());
                refundOrderMapper.updateById(refund);
                return R.ok("退款申请已拒绝");
            }
            // 同意 → 先置「处理中」并落库，状态机与两端进度即时可见
            refund.setRefundState(MallConstant.REFUND_PROCESSING);
            refund.setAuditRemark(auditRemark);
            refund.setAuditTime(LocalDateTime.now());
            refundOrderMapper.updateById(refund);
            // 支付微服务执行渠道退款
            Map<String, Object> body = Map.of(
                    "payOrderId", refund.getPayOrderId(),
                    "refundMoney", refund.getRefundMoney().toPlainString(),
                    "refundReason", refund.getRefundReason() == null ? "售后退款" : refund.getRefundReason());
            R channelResult;
            try {
                channelResult = payClient.executeRefund(internalToken, body);
            } catch (Exception e) {
                log.error("渠道退款执行异常 refundOrderId={}", refund.getRefundOrderId(), e);
                markRefundFailed(refund, "渠道退款执行异常：" + e.getMessage());
                throw new BizException("渠道退款执行异常，退款单已标记为已拒绝");
            }
            if (!"0".equals(String.valueOf(channelResult.get("id")))) {
                markRefundFailed(refund, "渠道退款失败：" + channelResult.get("message"));
                throw new BizException(String.valueOf(channelResult.get("message")));
            }
            refund.setRefundState(MallConstant.REFUND_SUCCESS);
            refund.setChannelRefundNo(String.valueOf(channelResult.get("channelRefundNo")));
            refund.setRefundTime(LocalDateTime.now());
            refundOrderMapper.updateById(refund);
            // 全额退完时原支付订单流转为已退款；部分退款保持支付成功（额度扣减由退款单累计体现）
            PayOrderView payOrder = payOrderViewMapper.selectOne(Wrappers.<PayOrderView>lambdaQuery()
                    .eq(PayOrderView::getPayOrderId, refund.getPayOrderId()));
            if (payOrder != null) {
                BigDecimal used = payOrderViewMapper.selectRefundedSum(refund.getPayOrderId());
                if (used != null && used.compareTo(payOrder.getMoney()) >= 0) {
                    payOrderViewMapper.update(null, Wrappers.<PayOrderView>lambdaUpdate()
                            .set(PayOrderView::getOrderState, MallConstant.PAY_STATE_REFUNDED)
                            .eq(PayOrderView::getPayOrderId, refund.getPayOrderId())
                            .eq(PayOrderView::getOrderState, MallConstant.PAY_STATE_SUCCESS));
                }
            }
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

    /** 渠道退款失败：置已拒绝并记录原因（留痕可查） */
    private void markRefundFailed(RefundOrder refund, String reason) {
        refund.setRefundState(MallConstant.REFUND_FAIL);
        refund.setAuditRemark(reason);
        refundOrderMapper.updateById(refund);
    }

    /** 退款列表分页（平台端） */
    public R list(int pageNum, int pageSize, String shopNum, Integer refundState) {
        Page<RefundOrder> page = refundOrderMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<RefundOrder>lambdaQuery()
                        .eq(shopNum != null && !shopNum.isBlank(), RefundOrder::getShopNum, shopNum)
                        .eq(refundState != null, RefundOrder::getRefundState, refundState)
                        .orderByDesc(RefundOrder::getId));
        return pageResult(page);
    }

    /**
     * 平台删除退款单（仅 ADMIN 可执行）。
     * 删除策略：仅已拒绝记录可删；待审核/处理中/已完成涉及渠道资金流向，须留痕禁止删除。
     */
    public R deleteRefund(String refundOrderId, String operatorToken) {
        adminGuard.requireAdmin(operatorToken);
        RefundOrder refund = findRefund(refundOrderId);
        if (refund.getRefundState() != MallConstant.REFUND_FAIL) {
            throw new BizException("仅已拒绝的记录可删除，待审核/处理中/已完成的记录须留痕");
        }
        refundOrderMapper.deleteById(refund.getId());
        return R.ok("退款单「" + refundOrderId + "」已删除");
    }
}
