package com.mallx.mgr.service;

import com.mallx.mgr.dto.ChannelStat;
import com.mallx.mgr.dto.StatisMchReq;
import com.mallx.mgr.mapper.PayOrderViewMapper;
import com.mallx.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据统计服务（接口文档 4.6）：平台交易统计、商户营收统计，适配 ECharts 可视化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 渠道编码 → 展示名称 */
    private static final Map<String, String> CHANNEL_NAMES = Map.of(
            "WECHAT", "微信", "ALIPAY", "支付宝", "UNIONPAY", "银联");

    private final PayOrderViewMapper payOrderViewMapper;

    /** 平台交易统计：总交易额、总笔数、渠道占比、七日走势 */
    public R platform() {
        Map<String, Object> total = payOrderViewMapper.selectPlatformTotal();
        BigDecimal totalMoney = toBigDecimal(total.get("totalMoney"));
        long totalCount = toLong(total.get("totalCount"));

        List<ChannelStat> channels = payOrderViewMapper.selectPlatformChannelRatio();
        List<Map<String, Object>> channelRatio = new ArrayList<>();
        for (ChannelStat cs : channels) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("wayCode", CHANNEL_NAMES.getOrDefault(cs.getWayCode(), cs.getWayCode()));
            m.put("count", cs.getCount());
            m.put("tradeMoney", cs.getTradeMoney());
            m.put("ratio", totalCount == 0 ? 0 : toBigDecimal(cs.getCount())
                    .multiply(new BigDecimal("100")).divide(new BigDecimal(totalCount), 2, RoundingMode.HALF_UP));
            channelRatio.add(m);
        }

        LocalDateTime start = LocalDate.now().minusDays(6).atStartOfDay();
        List<Map<String, Object>> daily = payOrderViewMapper.selectDailyTrade(TIME_FMT.format(start));
        List<Map<String, Object>> sevenDayData = fillSevenDays(daily);

        R r = R.ok("统计成功");
        r.set("totalMoney", totalMoney);
        r.set("totalCount", totalCount);
        r.set("channelRatio", channelRatio);
        r.set("sevenDayData", sevenDayData);
        return r;
    }

    /** 商户交易统计：营收、手续费、净收益、交易笔数、成功率、渠道分布、七日走势 */
    public R mch(StatisMchReq req) {
        LocalDateTime endTime = req.getEndTime() == null || req.getEndTime().isBlank()
                ? LocalDateTime.now()
                : LocalDateTime.parse(req.getEndTime(), TIME_FMT);
        LocalDateTime startTime = req.getStartTime() == null || req.getStartTime().isBlank()
                ? endTime.minusDays(7)
                : LocalDateTime.parse(req.getStartTime(), TIME_FMT);

        Map<String, Object> total = payOrderViewMapper.selectMchTotal(req.getShopNum(),
                TIME_FMT.format(startTime), TIME_FMT.format(endTime.plusSeconds(1)));
        BigDecimal totalMoney = toBigDecimal(total.get("totalMoney"));
        BigDecimal totalFee = toBigDecimal(total.get("totalFee"));
        long totalCount = toLong(total.get("totalCount"));

        Map<String, Object> counts = payOrderViewMapper.selectMchTradeCount(req.getShopNum());
        long successCount = toLong(counts.get("successCount"));
        long failCount = toLong(counts.get("failCount"));
        BigDecimal successRate = (successCount + failCount) == 0 ? BigDecimal.ZERO
                : new BigDecimal(successCount).multiply(new BigDecimal("100"))
                .divide(new BigDecimal(successCount + failCount), 2, RoundingMode.HALF_UP);

        List<ChannelStat> channels = payOrderViewMapper.selectMchChannelTrade(req.getShopNum(),
                TIME_FMT.format(startTime), TIME_FMT.format(endTime.plusSeconds(1)));
        List<Map<String, Object>> channelTradeData = new ArrayList<>();
        for (ChannelStat cs : channels) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("wayCode", CHANNEL_NAMES.getOrDefault(cs.getWayCode(), cs.getWayCode()));
            m.put("tradeMoney", cs.getTradeMoney());
            m.put("tradeCount", cs.getCount());
            m.put("fee", cs.getFee());
            channelTradeData.add(m);
        }

        List<Map<String, Object>> daily = payOrderViewMapper.selectMchDailyTrade(req.getShopNum(),
                TIME_FMT.format(endTime.minusDays(7)));
        List<Map<String, Object>> sevenDayTradeData = fillSevenDays(daily);

        R r = R.ok("统计查询成功");
        r.set("totalIncome", totalMoney);
        r.set("totalServiceFee", totalFee);
        r.set("actualIncome", totalMoney.subtract(totalFee));
        r.set("totalTradeCount", totalCount);
        r.set("successRate", successRate);
        r.set("channelTradeData", channelTradeData);
        r.set("sevenDayTradeData", sevenDayTradeData);
        return r;
    }

    /** 近7天数据补零：无交易的日期补 0，保证图表连续 */
    private List<Map<String, Object>> fillSevenDays(List<Map<String, Object>> daily) {
        Map<String, Map<String, Object>> byDate = new HashMap<>();
        for (Map<String, Object> row : daily) {
            byDate.put(String.valueOf(row.get("date")), row);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String date = DAY_FMT.format(LocalDate.now().minusDays(i));
            Map<String, Object> row = byDate.get(date);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", date);
            m.put("tradeMoney", row == null ? BigDecimal.ZERO : toBigDecimal(row.get("tradeMoney")));
            m.put("tradeCount", row == null ? 0L : toLong(row.get("tradeCount")));
            result.add(m);
        }
        return result;
    }

    private BigDecimal toBigDecimal(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(v));
    }

    private long toLong(Object v) {
        return v == null ? 0L : Long.parseLong(String.valueOf(v));
    }
}
