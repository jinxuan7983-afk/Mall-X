package com.mallx.mgr.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mallx.common.constant.MallConstant;
import com.mallx.common.exception.BizException;
import com.mallx.common.jwt.JwtUtil;
import com.mallx.common.result.R;
import com.mallx.mgr.dto.AuditReq;
import com.mallx.mgr.dto.ChannelConfigReq;
import com.mallx.mgr.dto.LoginReq;
import com.mallx.mgr.dto.MchRateReq;
import com.mallx.mgr.entity.AdminUser;
import com.mallx.mgr.entity.BizOrder;
import com.mallx.mgr.entity.ChannelConfig;
import com.mallx.mgr.entity.Goods;
import com.mallx.mgr.entity.Mch;
import com.mallx.mgr.entity.MchRate;
import com.mallx.mgr.entity.OperateLog;
import com.mallx.mgr.entity.PayOrderView;
import com.mallx.mgr.entity.RefundOrder;
import com.mallx.mgr.entity.Withdraw;
import com.mallx.mgr.mapper.AdminUserMapper;
import com.mallx.mgr.mapper.ChannelConfigMapper;
import com.mallx.mgr.mapper.GoodsMapper;
import com.mallx.mgr.mapper.MchMapper;
import com.mallx.mgr.mapper.MchRateMapper;
import com.mallx.mgr.mapper.OperateLogMapper;
import com.mallx.mgr.mapper.OrderMapper;
import com.mallx.mgr.mapper.PayOrderViewMapper;
import com.mallx.mgr.mapper.RefundOrderMapper;
import com.mallx.mgr.mapper.WithdrawMapper;
import com.mallx.mgr.support.AdminGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 平台管理服务：管理员登录、商户审核、费率配置、渠道配置（接口文档 4.5）。
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminUserMapper adminUserMapper;
    private final MchMapper mchMapper;
    private final MchRateMapper mchRateMapper;
    private final ChannelConfigMapper channelConfigMapper;
    private final PayOrderViewMapper payOrderViewMapper;
    private final OperateLogMapper operateLogMapper;
    private final GoodsMapper goodsMapper;
    private final OrderMapper orderMapper;
    private final RefundOrderMapper refundOrderMapper;
    private final WithdrawMapper withdrawMapper;
    private final AdminGuard adminGuard;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${mallx.jwt.secret:mallx-jwt-secret-2026}")
    private String jwtSecret;

    public R login(LoginReq req) {
        AdminUser admin = adminUserMapper.selectOne(Wrappers.<AdminUser>lambdaQuery()
                .eq(AdminUser::getUsername, req.getUsername()));
        if (admin == null || !admin.getPassword().equals(DigestUtil.md5Hex(req.getPassword() + MallConstant.PASSWORD_SALT))) {
            throw new BizException("账号或密码错误");
        }
        if (admin.getState() != null && admin.getState() != 1) {
            throw new BizException("账号已被禁用");
        }
        String token = JwtUtil.create(jwtSecret, admin.getUsername(), admin.getRole(), admin.getRealName());
        return R.ok("登录成功")
                .set("token", token)
                .set("realName", admin.getRealName())
                .set("role", admin.getRole());
    }

    /**
     * 商户删除（仅 ADMIN 可执行）。
     * 删除策略：存在商品/业务订单/支付订单/退款/提现等业务数据时禁止删除（保证交易与资金可追溯）；
     * 通过校验后物理删除商户，并级联清理其差异化费率配置（配置类数据随主体删除）。
     */
    public R deleteMch(String shopNum, String operatorToken) {
        adminGuard.requireAdmin(operatorToken);
        if (!StringUtils.hasText(shopNum)) {
            throw new BizException("请指定要删除的商户");
        }
        Mch mch = mchMapper.selectOne(Wrappers.<Mch>lambdaQuery().eq(Mch::getShopNum, shopNum));
        if (mch == null) {
            throw new BizException("商户不存在或已被删除");
        }
        if (exists(goodsMapper, Wrappers.<Goods>lambdaQuery().eq(Goods::getShopNum, shopNum))) {
            throw new BizException("该商户下仍有商品，请先处理商品后再删除");
        }
        if (exists(orderMapper, Wrappers.<BizOrder>lambdaQuery().eq(BizOrder::getShopNum, shopNum))) {
            throw new BizException("该商户存在业务订单，为保证交易可追溯不可删除");
        }
        if (exists(payOrderViewMapper, Wrappers.<PayOrderView>lambdaQuery().eq(PayOrderView::getShopNum, shopNum))) {
            throw new BizException("该商户存在支付订单，为保证资金可追溯不可删除");
        }
        if (exists(refundOrderMapper, Wrappers.<RefundOrder>lambdaQuery().eq(RefundOrder::getShopNum, shopNum))) {
            throw new BizException("该商户存在退款记录，为保证资金可追溯不可删除");
        }
        if (exists(withdrawMapper, Wrappers.<Withdraw>lambdaQuery().eq(Withdraw::getShopNum, shopNum))) {
            throw new BizException("该商户存在提现记录，为保证资金可追溯不可删除");
        }
        mchRateMapper.delete(Wrappers.<MchRate>lambdaQuery().eq(MchRate::getShopNum, shopNum));
        mchMapper.deleteById(mch.getId());
        return R.ok("商户「" + mch.getShopName() + "」已删除");
    }

    /**
     * 支付订单删除（仅 ADMIN 可执行）。
     * 删除策略：仅支付失败/订单关闭/待支付状态可删；支付成功与已退款涉及资金流向，须留痕禁止删除。
     */
    public R deletePayOrder(String payOrderId, String operatorToken) {
        adminGuard.requireAdmin(operatorToken);
        if (!StringUtils.hasText(payOrderId)) {
            throw new BizException("请指定要删除的支付订单");
        }
        PayOrderView payOrder = payOrderViewMapper.selectOne(Wrappers.<PayOrderView>lambdaQuery()
                .eq(PayOrderView::getPayOrderId, payOrderId));
        if (payOrder == null) {
            throw new BizException("支付订单不存在或已被删除");
        }
        int state = payOrder.getOrderState() == null ? -1 : payOrder.getOrderState();
        if (state == MallConstant.PAY_STATE_SUCCESS || state == MallConstant.PAY_STATE_REFUNDED) {
            throw new BizException("支付成功/已退款的订单涉及资金流向，不可删除");
        }
        payOrderViewMapper.deleteById(payOrder.getId());
        return R.ok("支付订单「" + payOrderId + "」已删除");
    }

    /** 渠道配置删除（仅 ADMIN 可执行）；保护规则：系统须保留至少一个支付渠道 */
    public R deleteChannel(String wayCode, String operatorToken) {
        adminGuard.requireAdmin(operatorToken);
        if (!StringUtils.hasText(wayCode)) {
            throw new BizException("请指定要删除的支付渠道");
        }
        ChannelConfig config = channelConfigMapper.selectOne(Wrappers.<ChannelConfig>lambdaQuery()
                .eq(ChannelConfig::getWayCode, wayCode));
        if (config == null) {
            throw new BizException("渠道配置不存在或已被删除");
        }
        Long total = channelConfigMapper.selectCount(null);
        if (total != null && total <= 1) {
            throw new BizException("系统须保留至少一个支付渠道，无法删除");
        }
        channelConfigMapper.deleteById(config.getId());
        return R.ok("支付渠道「" + config.getChannelName() + "」配置已删除");
    }

    /** 操作日志删除（仅 ADMIN 可执行）；用于清理误记录场景，删除动作本身亦会被审计切面留痕 */
    public R deleteLog(Long id, String operatorToken) {
        adminGuard.requireAdmin(operatorToken);
        if (id == null) {
            throw new BizException("请指定要删除的日志");
        }
        OperateLog logEntity = operateLogMapper.selectById(id);
        if (logEntity == null) {
            throw new BizException("日志不存在或已被删除");
        }
        operateLogMapper.deleteById(id);
        return R.ok("操作日志已删除");
    }

    /** 关联数据存在性校验（null 安全） */
    private <T> boolean exists(BaseMapper<T> mapper, Wrapper<T> wrapper) {
        Long count = mapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    /** 商户审核：1-审核通过 2-审核驳回（驳回需填写原因） */
    public R auditMch(AuditReq req) {
        if (req.getShopState() != null && req.getShopState() != 1 && req.getShopState() != 2) {
            throw new BizException("shopState仅支持1-审核通过/2-审核驳回");
        }
        Mch mch = mchMapper.selectOne(Wrappers.<Mch>lambdaQuery().eq(Mch::getShopNum, req.getShopNum()));
        if (mch == null) {
            throw new BizException("商户不存在");
        }
        if (req.getShopState() == 2 && !StringUtils.hasText(req.getAuditRemark())) {
            throw new BizException("审核驳回需填写原因");
        }
        mch.setShopState(req.getShopState() == 1 ? MallConstant.MCH_NORMAL : MallConstant.MCH_DISABLED);
        mch.setAuditRemark(req.getAuditRemark());
        mchMapper.updateById(mch);
        return R.ok("商户审核操作成功");
    }

    /** 商户列表分页 */
    public R listMch(int pageNum, int pageSize, String shopName, Integer shopState) {
        Page<Mch> page = mchMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Mch>lambdaQuery()
                        .like(StringUtils.hasText(shopName), Mch::getShopName, shopName)
                        .eq(shopState != null, Mch::getShopState, shopState)
                        .orderByDesc(Mch::getId));
        page.getRecords().forEach(m -> m.setPassword(null));
        return pageResult(page);
    }

    /** 商户差异化费率配置（upsert） */
    public R configMchRate(MchRateReq req) {
        MchRate exist = mchRateMapper.selectOne(Wrappers.<MchRate>lambdaQuery()
                .eq(MchRate::getShopNum, req.getShopNum())
                .eq(MchRate::getWayCode, req.getWayCode()));
        if (exist != null) {
            exist.setMchRate(req.getMchRate());
            mchRateMapper.updateById(exist);
        } else {
            MchRate rate = new MchRate();
            rate.setShopNum(req.getShopNum());
            rate.setWayCode(req.getWayCode());
            rate.setMchRate(req.getMchRate());
            mchRateMapper.insert(rate);
        }
        return R.ok("商户费率配置成功");
    }

    /** 支付渠道参数/开关/基础费率配置（upsert） */
    public R configChannel(ChannelConfigReq req) {
        ChannelConfig exist = channelConfigMapper.selectOne(Wrappers.<ChannelConfig>lambdaQuery()
                .eq(ChannelConfig::getWayCode, req.getWayCode()));
        if (exist == null) {
            exist = new ChannelConfig();
            exist.setWayCode(req.getWayCode());
            exist.setChannelName(StringUtils.hasText(req.getChannelName()) ? req.getChannelName() : req.getWayCode());
            exist.setSwitchStatus(1);
            exist.setBaseRate(new java.math.BigDecimal("0.0060"));
        }
        if (StringUtils.hasText(req.getChannelName())) {
            exist.setChannelName(req.getChannelName());
        }
        if (req.getSwitchStatus() != null) {
            exist.setSwitchStatus(req.getSwitchStatus());
        }
        if (req.getBaseRate() != null) {
            exist.setBaseRate(req.getBaseRate());
        }
        if (StringUtils.hasText(req.getAppId())) {
            exist.setAppId(req.getAppId());
        }
        if (StringUtils.hasText(req.getPrivateKey())) {
            exist.setPrivateKey(req.getPrivateKey());
        }
        if (StringUtils.hasText(req.getAlipayPublicKey())) {
            exist.setAlipayPublicKey(req.getAlipayPublicKey());
        }
        if (StringUtils.hasText(req.getGatewayUrl())) {
            exist.setGatewayUrl(req.getGatewayUrl());
        }
        if (exist.getId() != null) {
            channelConfigMapper.updateById(exist);
        } else {
            channelConfigMapper.insert(exist);
        }
        return R.ok("支付渠道配置成功");
    }

    public R listChannels() {
        var list = channelConfigMapper.selectList(Wrappers.<ChannelConfig>lambdaQuery()
                .orderByAsc(ChannelConfig::getId));
        list.forEach(c -> {
            c.setPrivateKey(null);
            c.setAlipayPublicKey(null);
        });
        return R.ok("查询成功").set("records", list);
    }

    /** 全平台支付订单分页（全局订单管控，支持多条件筛选） */
    public R listPayOrders(int pageNum, int pageSize, String shopNum, Integer orderState,
                           String wayCode, String startTime, String endTime) {
        Page<PayOrderView> page = payOrderViewMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<PayOrderView>lambdaQuery()
                        .eq(StringUtils.hasText(shopNum), PayOrderView::getShopNum, shopNum)
                        .eq(orderState != null, PayOrderView::getOrderState, orderState)
                        .eq(StringUtils.hasText(wayCode), PayOrderView::getWayCode, wayCode)
                        .ge(StringUtils.hasText(startTime), PayOrderView::getCreatedAt,
                                parseTime(startTime, LocalDateTime.now().minusDays(7)))
                        .le(StringUtils.hasText(endTime), PayOrderView::getCreatedAt,
                                parseTime(endTime, LocalDateTime.now()))
                        .orderByDesc(PayOrderView::getId));
        return pageResult(page);
    }

    /** 操作日志分页查询 */
    public R listLogs(int pageNum, int pageSize) {
        Page<OperateLog> page = operateLogMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<OperateLog>lambdaQuery().orderByDesc(OperateLog::getId));
        return pageResult(page);
    }

    private LocalDateTime parseTime(String time, LocalDateTime def) {
        try {
            return LocalDateTime.parse(time, TIME_FMT);
        } catch (Exception e) {
            return def;
        }
    }

    private R pageResult(Page<?> page) {
        R r = R.ok("查询成功");
        r.set("total", page.getTotal());
        r.set("pages", page.getPages());
        r.set("current", page.getCurrent());
        r.set("size", page.getSize());
        r.set("records", page.getRecords());
        return r;
    }
}
