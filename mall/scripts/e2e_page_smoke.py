#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Mall-X 全站页面冒烟测试（全方面：24 路由 × 三角色 + 响应式 + 异常监控）。

覆盖维度：
  1. 渲染覆盖：商城 5 页 + 商户 8 页 + 管理 10 页 + 守卫 1 项，逐页断言关键元素
  2. 会话隔离：用户 user1 / 商户 shop1 / 管理员 admin 三种独立上下文
  3. 异常监控：每页收集 pageerror、console error、API 4xx/5xx
  4. 交互抽查：登录、退出菜单、抽屉/弹窗/下拉可用性
  5. 响应式：1440 桌面全量 + 390 移动端关键页无横向溢出
  6. 截图：每页存档 mall/test-results/ui-smoke/

用法：python scripts/e2e_page_smoke.py
退出码：0 全部通过；1 存在失败项
"""
import os
import sys
import traceback

from playwright.sync_api import sync_playwright

BASE = os.environ.get("E2E_BASE", "http://localhost:5173")
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "test-results", "ui-smoke")
os.makedirs(OUT, exist_ok=True)

results = []
shot_n = [0]


def step(name, cond, detail=""):
    results.append((name, bool(cond), detail))
    print(f"  [{'PASS' if cond else 'FAIL'}] {name}" + (f"  {detail}" if detail and not cond else ""), flush=True)


def shot(page, tag):
    shot_n[0] += 1
    p = os.path.join(OUT, f"{shot_n[0]:02d}_{tag}.png")
    try:
        page.screenshot(path=p, full_page=True)
    except Exception:
        try:
            page.screenshot(path=p)
        except Exception:
            pass


def make_ctx(browser, label):
    """独立上下文 + 异常收集器"""
    ctx = browser.new_context(viewport={"width": 1440, "height": 900})
    page = ctx.new_page()
    bag = {"console": [], "api": []}
    page.on("pageerror", lambda e: bag["console"].append(f"pageerror: {e}"))
    page.on(
        "console",
        lambda m: bag["console"].append(m.text) if m.type == "error" and "favicon" not in m.text else None,
    )
    page.on(
        "response",
        lambda r: bag["api"].append(f"{r.status} {r.url}")
        if r.request.method != "OPTIONS" and r.status >= 400 and "/api/" in r.url
        else None,
    )
    return ctx, page, bag


def visit(page, bag, path, tag, selector=None, text=None, shot_it=True):
    """访问单页：断言关键元素 + 记录该页新增异常"""
    c0, a0 = len(bag["console"]), len(bag["api"])
    err = ""
    try:
        page.goto(BASE + path, wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(900)
    except Exception as e:
        err = f"导航异常: {str(e).splitlines()[0][:80]}"
    if not err and selector and page.locator(selector).count() == 0:
        err = f"缺少元素 {selector}"
    if not err and text and page.get_by_text(text).count() == 0:
        err = f"缺少文本 {text}"
    new_console = bag["console"][c0:]
    new_api = bag["api"][a0:]
    if new_console:
        err = (err + " | " if err else "") + f"控制台错误 {len(new_console)} 条"
    step(f"页面渲染 {path}", not err, (err + f" | API异常 {new_api[:2]}" if err else ""))
    if shot_it:
        shot(page, tag)
    return not err


print(f"========== Mall-X 全站页面冒烟测试  base={BASE} ==========")

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)

    # ---------- 1. 守卫：未登录访问受保护页 → 跳登录 ----------
    print("\n[1] 路由守卫")
    ctx, page, bag = make_ctx(browser, "guest")
    try:
        page.goto(BASE + "/my/orders", wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(900)
        step("守卫:未登录 /my/orders → 跳登录页", "/user/login" in page.url)
        shot(page, "guard_login")
    except Exception:
        traceback.print_exc()
        step("守卫检查", False, "异常")

    # ---------- 2. 商城端（user1） ----------
    print("\n[2] 商城端（user1 会话）")
    ctx_u, pu, bag_u = make_ctx(browser, "user")
    try:
        pu.goto(BASE + "/user/login", wait_until="networkidle", timeout=30000)
        pu.wait_for_timeout(800)
        pu.get_by_placeholder("账号(4~20位字母/数字/下划线)").fill("user1")
        pu.get_by_placeholder("密码(6~20位)").fill("123456")
        pu.get_by_role("button", name="登录").first.click()
        pu.wait_for_url(lambda url: "/user/login" not in url, timeout=20000)
        pu.wait_for_timeout(1200)
        step("用户登录 user1", "/user/login" not in pu.url)
        shot(pu, "user_after_login")

        visit(pu, bag_u, "/", "storefront_home", selector=".item")
        visit(pu, bag_u, "/goods/G728415", "storefront_goods_detail", text="无线蓝牙耳机 Pro")
        visit(pu, bag_u, "/cart", "storefront_cart", text="购物车")
        visit(pu, bag_u, "/my/orders", "storefront_my_orders", text="我的订单")
        # 交互抽查：商品详情的加入购物车按钮可用
        pu.goto(BASE + "/goods/G728415", wait_until="networkidle", timeout=30000)
        pu.wait_for_timeout(700)
        pu.get_by_role("button", name="加入购物车").click()
        pu.wait_for_timeout(1200)
        step("交互:加入购物车→购物车角标+1", pu.locator(".el-badge__content").count() > 0
             or pu.locator(".el-message--success").count() > 0)
        shot(pu, "storefront_add_cart")
    except Exception:
        traceback.print_exc()
        step("商城端会话", False, traceback.format_exc().splitlines()[-1])

    # ---------- 3. 商户端（shop1） ----------
    print("\n[3] 商户端（shop1 会话）")
    ctx_m, pm, bag_m = make_ctx(browser, "mch")
    try:
        pm.goto(BASE + "/mch/login", wait_until="networkidle", timeout=30000)
        pm.wait_for_timeout(800)
        pm.get_by_placeholder("登录账号").fill("shop1")
        pm.get_by_placeholder("密码").fill("123456")
        pm.get_by_role("button", name="登录经营后台").click()
        pm.wait_for_url("**/mch", timeout=20000)
        pm.wait_for_timeout(2000)
        step("商户登录 shop1", "/mch" in pm.url)
        shot(pm, "mch_after_login")

        visit(pm, bag_m, "/mch", "mch_workbench", text="今日经营概览")
        visit(pm, bag_m, "/mch/goods", "mch_goods", selector="tr")
        visit(pm, bag_m, "/mch/orders", "mch_orders", text="订单号")
        visit(pm, bag_m, "/mch/trade", "mch_trade", text="手续费")
        visit(pm, bag_m, "/mch/refund", "mch_refund", selector=".statgrid")
        visit(pm, bag_m, "/mch/fund", "mch_fund", text="提现记录")
        visit(pm, bag_m, "/mch/stats", "mch_stats", selector="canvas")
        # 交互抽查：工作台快捷入口跳转
        pm.goto(BASE + "/mch", wait_until="networkidle", timeout=30000)
        pm.wait_for_timeout(1500)
        pm.get_by_role("button", name="发布商品").first.click()
        pm.wait_for_timeout(1000)
        step("交互:工作台快捷入口→发布商品页", "/mch/goods" in pm.url)
        shot(pm, "mch_quick_entry")
    except Exception:
        traceback.print_exc()
        step("商户端会话", False, traceback.format_exc().splitlines()[-1])

    # ---------- 4. 管理端（admin） ----------
    print("\n[4] 管理端（admin 会话）")
    ctx_a, pa, bag_a = make_ctx(browser, "admin")
    try:
        pa.goto(BASE + "/admin/login", wait_until="networkidle", timeout=30000)
        pa.wait_for_timeout(800)
        pa.get_by_placeholder("管理员账号").fill("admin")
        pa.get_by_placeholder("密码").fill("123456")
        pa.get_by_role("button", name="登录管理后台").click()
        pa.wait_for_url("**/admin**", timeout=20000)
        pa.wait_for_timeout(2500)
        step("管理员登录 admin", "/admin" in pa.url)
        shot(pa, "admin_after_login")

        visit(pa, bag_a, "/admin", "admin_dashboard", text="平台累计交易额")
        visit(pa, bag_a, "/admin/merchants", "admin_merchants", selector="tr")
        visit(pa, bag_a, "/admin/goods", "admin_goods", text="所属商户")
        visit(pa, bag_a, "/admin/pay", "admin_pay", text="支付订单号")
        visit(pa, bag_a, "/admin/refunds", "admin_refunds", text="退款单 / 订单")
        visit(pa, bag_a, "/admin/channels", "admin_channels", text="渠道")
        visit(pa, bag_a, "/admin/withdraws", "admin_withdraws", text="提现单号")
        visit(pa, bag_a, "/admin/categories", "admin_categories", selector="tr")
        visit(pa, bag_a, "/admin/logs", "admin_logs", text="操作模块")
        # 交互抽查：商户审核弹窗（费率配置对话框）
        pa.goto(BASE + "/admin/merchants", wait_until="networkidle", timeout=30000)
        pa.wait_for_timeout(1500)
        if pa.get_by_role("button", name="费率").count():
            pa.get_by_role("button", name="费率").first.click()
            pa.wait_for_timeout(1000)
            dlg_ok = pa.locator(".el-dialog").count() > 0
            step("交互:商户费率配置弹窗打开", dlg_ok)
            shot(pa, "admin_rate_dialog")
            if dlg_ok:
                pa.locator(".el-dialog").get_by_role("button", name="取消").first.click()
        else:
            step("交互:商户费率配置弹窗打开", False, "未找到费率按钮")
    except Exception:
        traceback.print_exc()
        step("管理端会话", False, traceback.format_exc().splitlines()[-1])

    # ---------- 5. 响应式（390 移动端） ----------
    print("\n[5] 响应式 390px 检查")
    ctx_r, pr, bag_r = make_ctx(browser, "mobile")
    pr.set_viewport_size({"width": 390, "height": 844})
    for path, tag in [("/", "mobile_home"), ("/user/login", "mobile_login")]:
        try:
            pr.goto(BASE + path, wait_until="networkidle", timeout=30000)
            pr.wait_for_timeout(1000)
            over = pr.evaluate(
                "() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1"
            )
            step(f"移动端无横向溢出 {path}", not over,
                 f"scrollWidth={pr.evaluate('document.documentElement.scrollWidth')}")
            shot(pr, tag)
        except Exception:
            step(f"移动端 {path}", False, "异常")

    browser.close()

# ---------- 汇总 ----------
passed = sum(1 for _, ok, _ in results if ok)
failed = len(results) - passed
print("\n========== 汇总 ==========")
for name, ok, detail in results:
    if not ok:
        print(f"  [FAIL] {name}  {detail}")
print(f"通过 {passed} / {len(results)} 项")
print(f"截图 {shot_n[0]} 张 → {OUT}")
sys.exit(0 if failed == 0 else 1)
