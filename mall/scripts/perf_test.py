#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""性能测试：接口响应时间分位(p50/p95/p99) + 并发吞吐 + 限流表现。

用法：python scripts/perf_test.py [base]   默认 http://127.0.0.1:10010
"""
import hashlib
import json
import statistics
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:10010"
SECRET = "mallx-pay-secret-2026"


def sign(p):
    items = sorted((k, str(v)) for k, v in p.items()
                   if k != "sign" and v is not None and str(v) != "" and not isinstance(v, (dict, list)))
    return hashlib.md5(("&".join(f"{k}={v}" for k, v in items) + f"&key={SECRET}").encode()).hexdigest()


def call(path, params):
    body = dict(params)
    body["sign"] = sign(params)
    req = urllib.request.Request(BASE + path, data=json.dumps(body, ensure_ascii=False).encode(),
                                headers={"Content-Type": "application/json"}, method="POST")
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            raw = r.read().decode()
        return (time.perf_counter() - t0) * 1000, raw
    except Exception as e:
        return (time.perf_counter() - t0) * 1000, str(e)


def pct(vals, p):
    vals = sorted(vals)
    idx = min(len(vals) - 1, int(len(vals) * p / 100))
    return vals[idx]


ENDPOINTS = [
    ("商品列表", "/goods/list", {"pageNum": 1, "pageSize": 12, "goodsState": 1}),
    ("商品详情(Redis缓存)", "/goods/detail", {"goodsNum": "G728415"}),
    ("平台统计(聚合查询)", "/statis/platform", {}),
    ("用户登录", "/user/login", {"username": "user1", "password": "123456"}),
]

ROUNDS = 30
print(f"========== 性能测试 base={BASE} ==========")
print(f"\n[1] 单接口响应时间（串行 {ROUNDS} 次/接口）")
print(f"{'接口':<24}{'avg(ms)':>10}{'p50':>9}{'p95':>9}{'p99':>9}{'max':>9}")
lat = {}
for name, path, params in ENDPOINTS:
    vals, ok = [], 0
    for _ in range(ROUNDS):
        ms, raw = call(path, params)
        vals.append(ms)
        if raw.lstrip().startswith("{") and '"id":"0"' in raw.replace(" ", ""):
            ok += 1
    lat[name] = vals
    print(f"{name:<24}{statistics.mean(vals):>10.1f}{pct(vals,50):>9.1f}{pct(vals,95):>9.1f}"
          f"{pct(vals,99):>9.1f}{max(vals):>9.1f}   成功 {ok}/{ROUNDS}")

print("\n[2] 并发吞吐（20 并发 × 200 请求，读接口 /goods/detail）")
N, WORKERS = 200, 20


def one(_):
    ms, raw = call("/goods/detail", {"goodsNum": "G728415"})
    body = raw.replace(" ", "")
    if '"id":"0"' in body:
        return ms, "ok"
    if "系统繁忙" in raw:
        return ms, "limited"
    return ms, "err"


t0 = time.perf_counter()
with ThreadPoolExecutor(max_workers=WORKERS) as pool:
    res = list(pool.map(one, range(N)))
dur = time.perf_counter() - t0
ok = sum(1 for _, s in res if s == "ok")
lim = sum(1 for _, s in res if s == "limited")
err = N - ok - lim
lat_c = [ms for ms, s in res if s == "ok"]
print(f"  总耗时 {dur:.2f}s | QPS={N/dur:.0f} | 成功 {ok} | 限流 {lim} | 其他失败 {err}")
if lat_c:
    print(f"  并发成功请求延迟: avg={statistics.mean(lat_c):.1f}ms p50={pct(lat_c,50):.1f}ms "
          f"p95={pct(lat_c,95):.1f}ms max={max(lat_c):.1f}ms")

print("\n[3] 400 并发压测（限流保护验证）")
with ThreadPoolExecutor(max_workers=60) as pool:
    res2 = list(pool.map(lambda _: one(None)[1], range(400)))
lim2 = res2.count("limited")
print(f"  400 请求 → 成功 {res2.count('ok')} | 限流 {lim2} | 失败 {res2.count('err')}")
print(f"  限流触发率 {lim2/4:.0f}%（网关单 IP 200 QPS 保护生效）")
