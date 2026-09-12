#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""商户工作台 E2E v2：
API 预置 shop1 已支付订单 → 登录 → 工作台 → 商品管理(发布/上下架,回归BUG-02)
→ 订单发货 → 交易流水退款 → 退款进度 → 资金提现 → 店铺统计"""
import hashlib
import json
import os
import sys
import time
import traceback
import urllib.request

from playwright.sync_api import sync_playwright

BASE = "http://localhost:5173"
GW = "http://127.0.0.1:10010"
SECRET = "mallx-pay-secret-2026"
SHOP1 = "SH417826"
OUT = r"C:\毕业设计\多商家电商平台\mall\test-results"
os.makedirs(OUT, exist_ok=True)

results = []
console_errors = []
failed_api = []


def step(name, cond, detail=""):
    results.append((name, bool(cond), detail))
    print(f"  [{'PASS' if cond else 'FAIL'}] {name}" + (f"  {detail}" if detail and not cond else ""))


def sign_params(params):
    items = sorted((k, str(v)) for k, v in params.items()
                   if k != "sign" and v is not None and str(v) != "" and not isinstance(v, (dict, list)))
    raw = "&".join(f"{k}={v}" for k, v in items) + f"&key={SECRET}"
    return hashlib.md5(raw.encode("utf-8")).hexdigest()


def api_post(path, params):
    body = dict(params)
    body["sign"] = sign_params(params)
    req = urllib.request.Request(GW + path, data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
                                 headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read().decode("utf-8"))


def wait_state(page, row_text, tag_text, timeout=8000):
    """等待目标行内的状态标签(StateTag 组件)翻转为指定文案,规避异步刷新竞态"""
    try:
        page.locator(".el-table__row", has_text=row_text).first.locator(
            ".state-tag", has_text=tag_text).first.wait_for(state="visible", timeout=timeout)
        return True
    except Exception:
        return False


def prep_paid_order():
    """给 shop1 预置一笔已支付订单，等待 MQ 翻待发货"""
    r = api_post("/order/create", {
        "userNum": "U1000000001", "shopNum": SHOP1,
        "items": [{"goodsNum": "G728415", "count": 1}],
        "receiverName": "小明", "receiverPhone": "13900000001", "receiverAddress": "广州市天河区1号"})
    if r.get("id") != "0":
        return None, f"order/create: {r}"
    order_num = r["orderNum"]
    r = api_post("/pay/create", {
        "shopNum": SHOP1, "shopName": "星选数码旗舰店", "appId": "mallx-app",
        "wayCode": "WECHAT", "payMoney": "199.00", "subject": "无线蓝牙耳机 Pro",
        "userNum": "U1000000001", "orderNum": order_num})
    if r.get("id") != "0":
        return None, f"pay/create: {r}"
    cb = {"payOrderId": r["payOrderId"], "channelOrderNo": "CHE2E" + str(int(time.time())),
          "payState": "0", "payTime": time.strftime("%Y-%m-%d %H:%M:%S")}
    r = api_post("/pay/callback", cb)
    if r.get("id") != "0":
        return None, f"pay/callback: {r}"
    state = None
    for _ in range(40):  # 最长 80s 等 MQ 消费
        d = api_post("/order/detail", {"orderNum": order_num})
        state = d.get("order", {}).get("orderState")
        if state == 1:
            break
        time.sleep(2)
    return (order_num if state == 1 else None), f"orderState={state}"


with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    ctx = browser.new_context(viewport={"width": 1440, "height": 900})
    page = ctx.new_page()
    page.on("console", lambda m: console_errors.append(m.text) if m.type == "error" else None)
    page.on("pageerror", lambda e: console_errors.append(f"pageerror: {e}"))
    page.on("response", lambda r: failed_api.append(f"{r.status} {r.url}") if r.request.method != "OPTIONS" and r.status >= 400 and "/api/" in r.url else None)

    # 0. API 预置已支付订单
    order_num, info = prep_paid_order()
    step("预置shop1已支付订单(MQ翻待发货)", order_num is not None, info)

    # 1. 商户登录
    try:
        page.goto(BASE + "/mch/login", wait_until="networkidle", timeout=30000)
        page.get_by_placeholder("登录账号").fill("shop1")
        page.get_by_placeholder("密码").fill("123456")
        page.get_by_role("button", name="登录").click()
        page.wait_for_url("**/mch", timeout=20000)
        page.wait_for_load_state("networkidle", timeout=20000)
        page.wait_for_timeout(1200)
        step("商户登录进入工作台", "/mch" in page.url and "工作台" in page.inner_text("body"))
        step("工作台ECharts图表渲染", page.locator("canvas").count() >= 1, f"canvas={page.locator('canvas').count()}")
        page.screenshot(path=f"{OUT}\\20_mch_dashboard.png", full_page=True)
    except Exception:
        traceback.print_exc(); step("商户登录/工作台", False, "exception")

    # 2. 商品管理：发布商品
    try:
        page.locator(".el-menu-item", has_text="商品管理").click()
        page.wait_for_timeout(1000)
        rows0 = page.locator(".el-table__row").count()
        page.get_by_role("button", name="发布商品").click()
        page.wait_for_timeout(500)
        page.get_by_placeholder("商品名称").fill("E2E测试商品A")
        page.get_by_placeholder("0.01 起").fill("66.60")
        page.get_by_placeholder("库存数量").fill("30")
        dlg = page.locator(".el-dialog", has_text="发布商品")
        dlg.locator(".el-select").first.click()
        page.locator(".el-select-dropdown__item:visible", has_text="数码").first.click()
        page.get_by_placeholder("卖点、规格等").fill("E2E 自动化测试创建")
        page.screenshot(path=f"{OUT}\\21_goods_dialog.png")
        page.get_by_role("button", name="保存").click()
        page.wait_for_timeout(1500)
        rows1 = page.locator(".el-table__row").count()
        step("发布商品成功(行数+1)", rows1 == rows0 + 1, f"{rows0}->{rows1}")
        step("新商品出现在列表", page.locator(".el-table__row", has_text="E2E测试商品A").count() > 0)
        page.screenshot(path=f"{OUT}\\22_goods_list.png", full_page=True)
    except Exception:
        traceback.print_exc(); step("发布商品", False, "exception")

    # 3. 商品上下架切换（BUG-02 回归）
    try:
        row = page.locator(".el-table__row", has_text="E2E测试商品A").first
        row.get_by_role("button", name="下架").click()
        page.wait_for_timeout(1500)
        row = page.locator(".el-table__row", has_text="E2E测试商品A").first
        ok1 = row.locator(".state-tag", has_text="下架").count() > 0
        row.get_by_role("button", name="上架").click()
        page.wait_for_timeout(1500)
        row = page.locator(".el-table__row", has_text="E2E测试商品A").first
        ok2 = row.locator(".state-tag", has_text="上架").count() > 0
        step("商品下架/上架切换(BUG-02回归)", ok1 and ok2, f"off={ok1} on={ok2}")
    except Exception:
        traceback.print_exc(); step("商品上下架", False, "exception")

    # 4. 订单管理：待发货订单发货
    try:
        page.locator(".el-menu-item", has_text="订单管理").click()
        page.wait_for_timeout(1200)
        page.screenshot(path=f"{OUT}\\23_mch_orders.png", full_page=True)
        pend = page.locator(".el-table__row", has_text="待发货")
        step("存在待发货订单", pend.count() > 0, f"n={pend.count()}")
        if pend.count():
            pend.first.get_by_role("button", name="发货").click()
            page.wait_for_timeout(500)
            ddlg = page.locator(".el-dialog", has_text="订单发货")
            ddlg.locator(".el-select").first.click()
            page.locator(".el-select-dropdown__item:visible", has_text="顺丰速运").first.click()
            page.get_by_placeholder("物流运单号").fill(f"SF{int(time.time())}")
            page.screenshot(path=f"{OUT}\\24_deliver_dialog.png")
            page.get_by_role("button", name="确认发货").click()
            page.wait_for_timeout(1500)
            step(f"预置订单({order_num})流转已发货", wait_state(page, order_num, "已发货"))
            page.screenshot(path=f"{OUT}\\25_mch_orders_after.png", full_page=True)
    except Exception:
        traceback.print_exc(); step("订单发货", False, "exception")

    # 5. 交易流水：对支付成功订单发起退款
    try:
        page.locator(".el-menu-item", has_text="交易流水").click()
        page.wait_for_timeout(1200)
        page.screenshot(path=f"{OUT}\\26_trade_flow.png", full_page=True)
        refund_btn = page.get_by_role("button", name="退款")
        step("交易流水存在可退款订单", refund_btn.count() > 0)
        if refund_btn.count():
            refund_btn.first.click()
            page.wait_for_timeout(500)
            page.get_by_placeholder("支持部分退款").fill("10")
            page.get_by_placeholder("退款原因").fill("E2E自动化测试退款")
            page.screenshot(path=f"{OUT}\\27_refund_dialog.png")
            page.get_by_role("button", name="提交申请").click()
            page.wait_for_timeout(1500)
            step("退款申请提交(弹窗关闭)", page.locator(".el-dialog:visible").count() == 0)
    except Exception:
        traceback.print_exc(); step("交易流水退款", False, "exception")

    # 6. 退款管理：统计概览 + 商户端审核（拒绝本次发起的申请）
    try:
        page.locator(".el-menu-item", has_text="退款管理").click()
        page.wait_for_timeout(1200)
        step("退款统计卡渲染(4 项)", page.locator(".statgrid .stat").count() == 4)
        step("退款列表已加载", page.locator(".el-table__row").count() > 0)
        page.screenshot(path=f"{OUT}\\28_refund_mgmt.png", full_page=True)

        r2 = api_post("/refund/mch/list", {"shopNum": SHOP1, "pageNum": 1, "pageSize": 20, "refundState": 0})
        rid = next((rec.get("refundOrderId") for rec in r2.get("records", [])), None)
        if rid:
            row = page.locator(".el-table__row", has_text=rid)
            step("待审核退款单在列表中", row.count() > 0, rid)
            row.get_by_role("button", name="拒绝").click()
            page.wait_for_timeout(700)
            dialog = page.locator(".el-message-box")
            step("拒绝弹窗出现", dialog.count() > 0)
            dialog.locator("input").fill("E2E自动化：不符合退款条件")
            dialog.get_by_role("button", name="确认拒绝").click()
            page.wait_for_timeout(1800)
            step("退款状态翻已拒绝", wait_state(page, rid, "已拒绝"))
            step("拒绝反馈提示可见", page.locator(".el-message", has_text="已拒绝").count() > 0)
        else:
            step("退款管理审核", False, "未查到待审核退款单")
        page.screenshot(path=f"{OUT}\\28b_refund_audit.png", full_page=True)
    except Exception:
        traceback.print_exc(); step("退款管理", False, "exception")

    # 7. 资金管理：总览卡片 + 提现申请
    try:
        page.locator(".el-menu-item", has_text="资金管理").click()
        page.wait_for_timeout(1200)
        page.screenshot(path=f"{OUT}\\29_fund.png", full_page=True)
        step("资金卡片已填充(无 — 占位)", page.locator(".mx-stat", has_text="—").count() == 0)
        page.get_by_role("button", name="提现", exact=True).click()
        page.wait_for_timeout(500)
        page.get_by_placeholder("不超过可提现余额").fill("1")
        page.get_by_role("button", name="提交申请").click()
        page.wait_for_timeout(1500)
        step("提现申请提交并出现在列表(审核中)", page.locator(".el-table__row", has_text="审核中").count() > 0)
        page.screenshot(path=f"{OUT}\\29b_fund_withdraw.png", full_page=True)
    except Exception:
        traceback.print_exc(); step("资金管理", False, "exception")

    # 8. 店铺统计
    try:
        page.locator(".el-menu-item", has_text="店铺统计").click()
        page.wait_for_timeout(1500)
        page.screenshot(path=f"{OUT}\\30_mch_stats.png", full_page=True)
        step("店铺统计图表渲染", page.locator("canvas").count() >= 2, f"canvas={page.locator('canvas').count()}")
    except Exception:
        traceback.print_exc(); step("店铺统计", False, "exception")

    browser.close()

print("\n---- 控制台错误 ----")
for e in console_errors[:10]:
    print("  CONSOLE:", e[:200])
print("---- 失败接口(>=400) ----")
for e in failed_api[:10]:
    print("  API:", e[:200])
npass = sum(1 for _, ok, _ in results if ok)
print(f"\n========== 商户端E2E: 通过 {npass}/{len(results)} ==========")
sys.exit(0)
