#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""探针：管理台退款审核 UI 链路逐帧观察"""
import os
import traceback

from playwright.sync_api import sync_playwright

BASE = "http://localhost:5173"
OUT = r"C:\毕业设计\多商家电商平台\mall\test-results"

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    ctx = browser.new_context(viewport={"width": 1440, "height": 900})
    page = ctx.new_page()
    page.on("pageerror", lambda e: print("PAGEERROR:", str(e)[:200]))
    page.on("response", lambda r: print("RESP:", r.status, r.url) if ("refund/audit" in r.url or "withdraw/audit" in r.url or "category/save" in r.url) else None)

    page.goto(BASE + "/admin/login", wait_until="networkidle", timeout=30000)
    ins = page.locator("input:visible")
    ins.nth(0).fill("admin")
    ins.nth(1).fill("123456")
    page.get_by_role("button", name="登录").click()
    page.wait_for_url("**/admin", timeout=20000)
    page.wait_for_timeout(800)

    page.locator(".el-menu-item", has_text="退款审核").click()
    page.wait_for_timeout(1500)
    pend = page.locator(".el-table__row", has_text="审核中")
    print("pending rows:", pend.count())
    if pend.count() == 0:
        print("没有待审核退款单，先用 API 造一条")
        import hashlib, json, time, urllib.request
        GW = "http://127.0.0.1:10010"; SECRET = "mallx-pay-secret-2026"
        def post(path, params):
            items = sorted((k, str(v)) for k, v in params.items() if k != "sign" and v is not None and str(v) != "" and not isinstance(v, (dict, list)))
            raw = "&".join(f"{k}={v}" for k, v in items) + f"&key={SECRET}"
            body = dict(params); body["sign"] = hashlib.md5(raw.encode()).hexdigest()
            req = urllib.request.Request(GW + path, data=json.dumps(body, ensure_ascii=False).encode(), headers={"Content-Type": "application/json"})
            return json.loads(urllib.request.urlopen(req, timeout=15).read().decode())
        r = post("/order/create", {"userNum": "U1000000001", "shopNum": "SH1000000001",
                                   "items": [{"goodsNum": "G1000000001", "count": 1}],
                                   "receiverName": "小明", "receiverPhone": "13900000001", "receiverAddress": "广州市天河区1号"})
        r2 = post("/pay/create", {"shopNum": "SH1000000001", "shopName": "星选数码旗舰店", "appId": "mallx-app",
                                  "wayCode": "ALIPAY", "payMoney": "199.00", "subject": "探针退款",
                                  "userNum": "U1000000001", "orderNum": r["orderNum"]})
        post("/pay/callback", {"payOrderId": r2["payOrderId"], "channelOrderNo": "CHPROBE" + str(int(time.time())),
                               "payState": "0", "payTime": time.strftime("%Y-%m-%d %H:%M:%S")})
        r3 = post("/refund/create", {"payOrderId": r2["payOrderId"], "refundMoney": "5.00",
                                     "refundReason": "探针退款", "shopNum": "SH1000000001"})
        print("created refund:", r3)
        page.get_by_role("button", name="刷新").click()
        page.wait_for_timeout(1500)
        pend = page.locator(".el-table__row", has_text="审核中")
        print("pending rows after refresh:", pend.count())

    if pend.count():
        pend.first.get_by_role("button", name="通过").click()
        page.wait_for_timeout(1000)
        box = page.locator(".el-message-box")
        print("msgbox count:", box.count())
        if box.count():
            print("msgbox text:", box.inner_text()[:150].replace("\n", " | "))
            btns = box.locator("button").all_inner_texts()
            print("msgbox buttons:", btns)
            box.locator("button", has_text="确定").click()
            page.wait_for_timeout(2000)
        print("after: pending rows =", page.locator(".el-table__row", has_text="审核中").count())
        page.screenshot(path=f"{OUT}\\60_probe_refund.png", full_page=True)

    browser.close()
