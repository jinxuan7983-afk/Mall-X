#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""平台管理端 E2E：API 预置待审核商户 → 登录看板 → 商户审核 → 退款审核 → 提现审核
→ 渠道配置 → 类目管理 → 支付订单 → 操作日志"""
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
SFX = str(int(time.time()))[-6:]


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


def api_get(path):
    req = urllib.request.Request(GW + path, method="GET")
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read().decode("utf-8"))


def confirm_box(page):
    """处理 ElMessageBox 确认框(等待其渲染出现,避免动画竞态)"""
    try:
        box = page.locator(".el-message-box")
        box.wait_for(state="visible", timeout=5000)
        box.get_by_role("button", name="确定").click()
        box.wait_for(state="hidden", timeout=5000)
        page.wait_for_timeout(800)
    except Exception:
        pass  # 该操作未弹确认框


def wait_state(page, row_text, tag_text, timeout=8000):
    """等待目标行内的状态标签(StateTag 组件)翻转为指定文案,规避异步刷新竞态"""
    try:
        page.locator(".el-table__row", has_text=row_text).first.locator(
            ".state-tag", has_text=tag_text).first.wait_for(state="visible", timeout=timeout)
        return True
    except Exception:
        return False


with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    ctx = browser.new_context(viewport={"width": 1440, "height": 900})
    page = ctx.new_page()
    page.on("console", lambda m: console_errors.append(m.text) if m.type == "error" else None)
    page.on("pageerror", lambda e: console_errors.append(f"pageerror: {e}"))
    page.on("response", lambda r: failed_api.append(f"{r.status} {r.url}") if r.request.method != "OPTIONS" and r.status >= 400 and "/api/" in r.url else None)

    # 0. API 预置待审核商户 + 退款审核单 + 提现审核单
    shop_name = f"E2E审核商铺{SFX}"
    cap = api_get("/captcha/image")
    r = api_post("/mch/register", {
        "shopName": shop_name, "shopPhone": "137" + SFX.ljust(8, "0"),
        "shopAddress": "E2E测试地址", "username": f"e2emch{SFX}", "password": "123456",
        "captchaKey": cap.get("captchaKey"), "captchaCode": cap.get("code")})
    step("预置待审核商户", r.get("id") == "0", str(r))
    try:
        ro = api_post("/order/create", {
            "userNum": "U1000000001", "shopNum": SHOP1,
            "items": [{"goodsNum": "G728415", "count": 1}],
            "receiverName": "小明", "receiverPhone": "13900000001", "receiverAddress": "广州市天河区1号"})
        po = api_post("/pay/create", {
            "shopNum": SHOP1, "shopName": "星选数码旗舰店", "appId": "mallx-app",
            "wayCode": "ALIPAY", "payMoney": "199.00", "subject": "管理端审核预置",
            "userNum": "U1000000001", "orderNum": ro["orderNum"]})
        api_post("/pay/callback", {"payOrderId": po["payOrderId"], "channelOrderNo": "CHADM" + SFX,
                                   "payState": "0", "payTime": time.strftime("%Y-%m-%d %H:%M:%S")})
        rr = api_post("/refund/create", {"payOrderId": po["payOrderId"], "refundMoney": "5.00",
                                         "refundReason": "E2E管理端退款审核", "shopNum": SHOP1})
        wr = api_post("/fund/withdraw/create", {"shopNum": SHOP1, "money": "2.00"})
        step("预置退款/提现审核单", rr.get("id") == "0" and wr.get("id") == "0", f"{rr}|{wr}")
    except Exception as e:
        step("预置退款/提现审核单", False, str(e)[:120])

    # 1. 管理员登录 + 看板
    try:
        page.goto(BASE + "/admin/login", wait_until="networkidle", timeout=30000)
        ins = page.locator("input:visible")
        ins.nth(0).fill("admin")
        ins.nth(1).fill("123456")
        page.get_by_role("button", name="登录").click()
        page.wait_for_url("**/admin", timeout=20000)
        page.wait_for_load_state("networkidle", timeout=20000)
        page.wait_for_timeout(1500)
        step("管理员登录进入看板", "/admin" in page.url)
        step("看板ECharts图表渲染", page.locator("canvas").count() >= 2, f"canvas={page.locator('canvas').count()}")
        page.screenshot(path=f"{OUT}\\40_admin_dashboard.png", full_page=True)
    except Exception:
        traceback.print_exc(); step("管理员登录/看板", False, "exception")

    # 2. 商户审核：搜索→通过
    try:
        page.locator(".el-menu-item", has_text="商户审核").click()
        page.wait_for_timeout(1000)
        page.get_by_placeholder("按店铺名称搜索").fill(shop_name)
        page.get_by_role("button", name="查询").click()
        page.wait_for_timeout(1200)
        row = page.locator(".el-table__row", has_text=shop_name)
        step("待审核商户可搜索到", row.count() > 0)
        if row.count():
            row.first.get_by_role("button", name="通过").click()
            confirm_box(page)
            page.wait_for_timeout(1200)
            step("商户审核通过(状态翻正常)", wait_state(page, shop_name, "正常"))
            page.screenshot(path=f"{OUT}\\41_mch_audit.png", full_page=True)
    except Exception:
        traceback.print_exc(); step("商户审核", False, "exception")

    # 3. 退款审核：shop1 的审核中退款单 → 通过
    try:
        page.locator(".el-menu-item", has_text="退款审核").click()
        page.wait_for_timeout(1200)
        pend = page.locator(".el-table__row", has_text="待审核")
        step("存在待审核退款单", pend.count() > 0, f"n={pend.count()}")
        page.screenshot(path=f"{OUT}\\42_refund_audit_before.png", full_page=True)
        if pend.count():
            rid = pend.first.inner_text().split()[0]  # 行首即退款单号
            pend.first.get_by_role("button", name="通过").click()
            confirm_box(page)
            page.wait_for_timeout(1500)
            step("退款审核通过(该单翻已完成)", wait_state(page, rid, "已完成"), rid)
            page.screenshot(path=f"{OUT}\\43_refund_audit_after.png", full_page=True)
    except Exception:
        traceback.print_exc(); step("退款审核", False, "exception")

    # 4. 提现审核：shop1 的审核中提现单 → 通过
    try:
        page.locator(".el-menu-item", has_text="提现审核").click()
        page.wait_for_timeout(1200)
        page.get_by_placeholder("按商户编号过滤").fill(SHOP1)
        page.get_by_role("button", name="查询").click()
        page.wait_for_timeout(1200)
        pend = page.locator(".el-table__row", has_text="审核中")
        step("存在审核中提现单", pend.count() > 0, f"n={pend.count()}")
        if pend.count():
            wno = pend.first.inner_text().split()[0]  # 行首即提现单号
            pend.first.get_by_role("button", name="通过").click()
            confirm_box(page)
            page.wait_for_timeout(1500)
            step("提现审核通过(该单翻已到账)", wait_state(page, wno, "已到账"), wno)
            page.screenshot(path=f"{OUT}\\44_withdraw_audit.png", full_page=True)
    except Exception:
        traceback.print_exc(); step("提现审核", False, "exception")

    # 5. 渠道配置：ALIPAY 基础费率修改
    try:
        page.locator(".el-menu-item", has_text="渠道配置").click()
        page.wait_for_timeout(1000)
        row = page.locator(".el-table__row", has_text="ALIPAY").first
        step("渠道列表3行齐全", page.locator(".el-table__row").count() >= 3)
        row.get_by_role("button", name="配置").click()
        page.wait_for_timeout(500)
        dlg = page.locator(".el-dialog", has_text="配置渠道")
        rate_input = dlg.get_by_placeholder("0~1,如 0.0060")
        rate_input.fill("0.0055")
        page.screenshot(path=f"{OUT}\\45_channel_dialog.png")
        dlg.get_by_role("button", name="保存配置").click()
        page.wait_for_timeout(1500)
        row = page.locator(".el-table__row", has_text="ALIPAY").first
        step("渠道费率保存生效(0.0055)", "0.0055" in row.inner_text())
        page.screenshot(path=f"{OUT}\\46_channel_after.png", full_page=True)
    except Exception:
        traceback.print_exc(); step("渠道配置", False, "exception")

    # 6. 类目管理：新建类目
    try:
        page.locator(".el-menu-item", has_text="类目管理").click()
        page.wait_for_timeout(1000)
        rows0 = page.locator(".el-table__row").count()
        page.get_by_role("button", name="新建类目").click()
        page.wait_for_timeout(500)
        cat_name = f"图书{SFX}"
        page.get_by_placeholder("类目名称").fill(cat_name)
        page.get_by_placeholder("数值越小越靠前").fill("9")
        page.get_by_role("button", name="保存").click()
        page.wait_for_timeout(1500)
        step("新建类目[图书]成功", page.locator(".el-table__row", has_text=cat_name).count() > 0)
        page.screenshot(path=f"{OUT}\\47_category.png", full_page=True)

        # 删除刚创建的空类目(仅 ADMIN 可执行) → 确认框 → 行消失
        page.locator(".el-table__row", has_text=cat_name).first.get_by_role("button", name="删除").click()
        page.wait_for_timeout(600)
        step("类目删除前弹出确认框", page.locator(".el-message-box", has_text="删除确认").count() > 0)
        confirm_box(page)
        page.wait_for_timeout(1500)
        step("空类目删除成功且行消失", page.locator(".el-table__row", has_text=cat_name).count() == 0)
        page.screenshot(path=f"{OUT}\\47b_category_delete.png", full_page=True)
    except Exception:
        traceback.print_exc(); step("类目管理", False, "exception")

    # 7. 支付订单：列表与退款状态
    try:
        page.locator(".el-menu-item", has_text="支付订单").click()
        page.wait_for_timeout(1200)
        n = page.locator(".el-table__row").count()
        step("支付订单列表有数据", n > 0, f"rows={n}")
        # 已退款单可能因数据增长不在第一页,改用接口按状态过滤校验
        pay_refunded = api_post("/admin/pay/list", {"pageNum": 1, "pageSize": 50, "orderState": 2})
        step("已退款支付单可见", any(rec.get("orderState") == 2 for rec in pay_refunded.get("records", [])),
             f"total={pay_refunded.get('total')}")
        page.screenshot(path=f"{OUT}\\50_admin_pay.png", full_page=True)
    except Exception:
        traceback.print_exc(); step("支付订单", False, "exception")

    # 9. 操作日志
    try:
        page.locator(".el-menu-item", has_text="操作日志").click()
        page.wait_for_timeout(1200)
        n = page.locator(".el-table__row").count()
        step("操作日志有记录", n > 0, f"rows={n}")
        page.screenshot(path=f"{OUT}\\51_admin_logs.png", full_page=True)

        # 删除一条日志(仅 ADMIN 可执行),删除动作自身写入新的审计日志
        page.locator(".el-table__row").first.get_by_role("button", name="删除").click()
        page.wait_for_timeout(600)
        step("日志删除前弹出确认框", page.locator(".el-message-box", has_text="删除确认").count() > 0)
        confirm_box(page)
        page.wait_for_timeout(1500)
        step("日志删除成功提示可见", page.locator(".el-message", has_text="已删除").count() > 0)
        page.screenshot(path=f"{OUT}\\51b_log_delete.png", full_page=True)
    except Exception:
        traceback.print_exc(); step("操作日志", False, "exception")

    browser.close()

print("\n---- 控制台错误 ----")
for e in console_errors[:10]:
    print("  CONSOLE:", e[:200])
print("---- 失败接口(>=400) ----")
for e in failed_api[:10]:
    print("  API:", e[:200])
npass = sum(1 for _, ok, _ in results if ok)
print(f"\n========== 管理端E2E: 通过 {npass}/{len(results)} ==========")
sys.exit(0)
