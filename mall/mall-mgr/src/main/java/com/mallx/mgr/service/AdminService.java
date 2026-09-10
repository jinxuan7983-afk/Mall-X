package com.mallx.mgr.service;

import cn.hutool.crypto.digest.DigestUtil;
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
import com.mallx.mgr.entity.ChannelConfig;
import com.mallx.mgr.entity.Mch;
import com.mallx.mgr.entity.MchRate;
import com.mallx.mgr.entity.OperateLog;
import com.mallx.mgr.entity.PayOrderView;
import com.mallx.mgr.mapper.AdminUserMapper;
import com.mallx.mgr.mapper.ChannelConfigMapper;
import com.mallx.mgr.mapper.MchMapper;
import com.mallx.mgr.mapper.MchRateMapper;
import com.mallx.mgr.mapper.OperateLogMapper;
import com.mallx.mgr.mapper.PayOrderViewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
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

    public R createUser(String username, String password, String realName, String role) {
        Long exists = adminUserMapper.selectCount(Wrappers.<AdminUser>lambdaQuery()
                .eq(AdminUser::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BizException("账号已存在");
        }
        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPassword(DigestUtil.md5Hex(password + MallConstant.PASSWORD_SALT));
        admin.setRealName(realName);
        admin.setRole(role == null || role.isBlank() ? "OPS" : role);
        admin.setState(1);
        adminUserMapper.insert(admin);
        return R.ok("后台账号创建成功");
    }

    public R listUsers(int pageNum, int pageSize) {
        Page<AdminUser> page = adminUserMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<AdminUser>lambdaQuery().orderByDesc(AdminUser::getId));
        page.getRecords().forEach(u -> u.setPassword(null));
        return pageResult(page);
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
