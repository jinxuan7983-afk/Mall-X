package com.mallx.mgr.controller;

import com.mallx.common.result.R;
import com.mallx.mgr.dto.DeliverReq;
import com.mallx.mgr.dto.OrderCreateReq;
import com.mallx.mgr.dto.OrderListReq;
import com.mallx.mgr.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 订单接口：下单、查询、发货、确认收货（交易履约全链路）。
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 用户下单（购物车结算，Lua预扣库存 + MQ延迟关单） */
    @PostMapping("/create")
    public R create(@Valid @RequestBody OrderCreateReq req) {
        return orderService.create(req);
    }

    /** 订单列表（userNum 用户侧 / shopNum 商户侧） */
    @PostMapping("/list")
    public R list(@RequestBody OrderListReq req) {
        return orderService.list(req);
    }

    /** 订单详情（含明细） */
    @PostMapping("/detail")
    public R detail(@RequestBody Map<String, Object> req) {
        Object orderNum = req.get("orderNum");
        return orderService.detail(orderNum == null ? null : String.valueOf(orderNum));
    }

    /** 商户发货（同步物流信息） */
    @PostMapping("/deliver")
    public R deliver(@Valid @RequestBody DeliverReq req) {
        return orderService.deliver(req);
    }

    /** 用户确认收货 */
    @PostMapping("/confirm")
    public R confirm(@RequestBody Map<String, Object> req) {
        Object orderNum = req.get("orderNum");
        return orderService.confirm(orderNum == null ? null : String.valueOf(orderNum));
    }
}
