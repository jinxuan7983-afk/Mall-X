package com.mallx.mgr.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mallx.common.constant.MallConstant;
import com.mallx.common.exception.BizException;
import com.mallx.common.result.R;
import com.mallx.common.util.OrderNoUtil;
import com.mallx.mgr.dto.ChannelStat;
import com.mallx.mgr.dto.WithdrawReq;
import com.mallx.mgr.entity.Withdraw;
import com.mallx.mgr.mapper.PayOrderViewMapper;
import com.mallx.mgr.mapper.WithdrawMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商户资金服务：账户资金总览、手续费、提现申请与记录（商户端资金管控）。
 */
@Service
@RequiredArgsConstructor
public class FundService {

    private final PayOrderViewMapper payOrderViewMapper;
    private final WithdrawMapper withdrawMapper;

    /** 资金总览：累计营收 - 手续费 = 净收益；再扣除已退款与已提现 = 可提现余额 */
    public R overview(String shopNum) {
        Map<String, Object> total = payOrderViewMapper.selectMchTotal(shopNum,
                "2000-01-01 00:00:00", "2100-01-01 00:00:00");
        BigDecimal totalIncome = toBd(total.get("totalMoney"));
        BigDecimal totalFee = toBd(total.get("totalFee"));
        BigDecimal actualIncome = totalIncome.subtract(totalFee);

        BigDecimal refunded = payOrderViewMapper.selectShopRefundedSum(shopNum);
        refunded = refunded == null ? BigDecimal.ZERO : refunded;

        BigDecimal withdrawn = withdrawMapper.selectList(Wrappers.<Withdraw>lambdaQuery()
                        .eq(Withdraw::getShopNum, shopNum)
                        .in(Withdraw::getState, MallConstant.WITHDRAW_WAIT, MallConstant.WITHDRAW_SUCCESS))
                .stream().map(Withdraw::getMoney).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal balance = actualIncome.subtract(refunded).subtract(withdrawn);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalIncome", totalIncome);
        m.put("totalServiceFee", totalFee);
        m.put("actualIncome", actualIncome);
        m.put("refunded", refunded);
        m.put("withdrawn", withdrawn);
        m.put("balance", balance.max(BigDecimal.ZERO));

        R r = R.ok("查询成功");
        r.set("fund", m);
        return r;
    }

    /** 提现申请：校验可提现余额 */
    public R withdrawCreate(WithdrawReq req) {
        R overview = overview(req.getShopNum());
        @SuppressWarnings("unchecked")
        Map<String, Object> fund = (Map<String, Object>) overview.get("fund");
        BigDecimal balance = new BigDecimal(String.valueOf(fund.get("balance")));
        if (req.getMoney().compareTo(balance) > 0) {
            throw new BizException("提现金额超过可提现余额 " + balance);
        }
        Withdraw w = new Withdraw();
        w.setWithdrawNo(OrderNoUtil.withdrawNo());
        w.setShopNum(req.getShopNum());
        w.setMoney(req.getMoney());
        w.setState(MallConstant.WITHDRAW_WAIT);
        withdrawMapper.insert(w);
        return R.ok("提现申请提交成功").set("withdrawNo", w.getWithdrawNo());
    }

    public R withdrawList(String shopNum, int pageNum, int pageSize) {
        Page<Withdraw> page = withdrawMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Withdraw>lambdaQuery()
                        .eq(shopNum != null && !shopNum.isBlank(), Withdraw::getShopNum, shopNum)
                        .orderByDesc(Withdraw::getId));
        R r = R.ok("查询成功");
        r.set("total", page.getTotal());
        r.set("pages", page.getPages());
        r.set("current", page.getCurrent());
        r.set("size", page.getSize());
        r.set("records", page.getRecords());
        return r;
    }

    /** 平台提现审核 */
    public R withdrawAudit(String withdrawNo, Integer state, String remark) {
        Withdraw w = withdrawMapper.selectOne(Wrappers.<Withdraw>lambdaQuery()
                .eq(Withdraw::getWithdrawNo, withdrawNo));
        if (w == null) {
            throw new BizException("提现单不存在");
        }
        if (w.getState() != MallConstant.WITHDRAW_WAIT) {
            throw new BizException("该提现单已处理");
        }
        if (state == null || (state != MallConstant.WITHDRAW_SUCCESS && state != MallConstant.WITHDRAW_FAIL)) {
            throw new BizException("state仅支持1-成功/2-失败");
        }
        w.setState(state);
        w.setRemark(remark);
        w.setFinishTime(LocalDateTime.now());
        withdrawMapper.updateById(w);
        return R.ok("提现审核完成");
    }

    private BigDecimal toBd(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(v));
    }
}
