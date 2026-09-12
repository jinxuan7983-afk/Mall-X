package com.mallx.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 单元测试：业务单号生成器（唯一性/前缀/并发安全）。
 */
class OrderNoUtilTest {

    @Test
    @DisplayName("前缀与格式:各业务单号前缀正确且含时间戳")
    void prefixCorrect() {
        assertTrue(OrderNoUtil.orderNum().startsWith("SO"));
        assertTrue(OrderNoUtil.payOrderId().startsWith("PAY"));
        assertTrue(OrderNoUtil.refundOrderId().startsWith("RF"));
        assertTrue(OrderNoUtil.withdrawNo().startsWith("WD"));
        assertTrue(OrderNoUtil.shopNum().startsWith("SH"));
        assertTrue(OrderNoUtil.userNum().startsWith("U"));
        assertTrue(OrderNoUtil.goodsNum().startsWith("G"));
        assertTrue(OrderNoUtil.orderNum().length() > 10);
    }

    @Test
    @DisplayName("唯一性:单线程 5000 次生成无重复")
    void uniqueInSingleThread() {
        Set<String> set = IntStream.range(0, 5000).mapToObj(i -> OrderNoUtil.orderNum())
                .collect(Collectors.toCollection(HashSet::new));
        assertEquals(5000, set.size(), "存在重复单号");
    }

    @Test
    @DisplayName("并发安全:16 线程各 200 次生成无重复")
    void uniqueUnderConcurrency() throws Exception {
        int threads = 16;
        int perThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<List<String>>> tasks = IntStream.range(0, threads)
                    .<Callable<List<String>>>mapToObj(t -> () ->
                            IntStream.range(0, perThread).mapToObj(i -> OrderNoUtil.payOrderId()).collect(Collectors.toList()))
                    .collect(Collectors.toList());
            Set<String> all = Collections.synchronizedSet(new HashSet<>());
            for (Future<List<String>> f : pool.invokeAll(tasks)) {
                all.addAll(f.get());
            }
            assertEquals(threads * perThread, all.size(), "并发下出现重复单号");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("格式:前缀+17位时间戳+5位随机+4位序列,长度固定")
    void formatStable() {
        String no = OrderNoUtil.orderNum();
        assertEquals(2 + 17 + 5 + 4, no.length(), "单号长度异常: " + no);
        assertTrue(no.substring(2, 19).matches("\\d{17}"), "时间戳段应为17位数字: " + no);
        assertTrue(no.substring(19).matches("\\d{9}"), "随机+序列段应为9位数字: " + no);
    }
}
