#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""商城端 E2E v3：
未登录守卫 → 首页/分类/详情 → 未登录加购弹登录框 → 登录回跳加购
→ 立即购买直达结算(不混入购物车,支付闭环) → 购物车链路(互不污染,支付闭环) → 我的订单"""
import os
import sys
import traceback

from playwright.sync_api import sync_playwright

BASE = "http://localhost:5173"
OUT = r"C:\毕业设计\多商家电商平台\mall\test-results"
os.makedirs(OUT, exist_ok=True)

results = []
console_errors = []
failed_api = []


def step(name, cond, detail=""):
    results.append((name, bool(cond), detail))
    print(f"  [{'PASS' if cond else 'FAIL'}] {name}" + (f"  {detail}" if detail and not cond else ""))


with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    ctx = browser.new_context(viewport={"width": 1440, "height": 900})
    page = ctx.new_page()
    page.on("console", lambda m: console_errors.append(m.text) if m.type == "error" else None)
    page.on("pageerror", lambda e: console_errors.append(f"pageerror: {e}"))
    page.on("response", lambda r: failed_api.append(f"{r.status} {r.url}") if r.request.method != "OPTIONS" and r.status >= 400 and "/api/" in r.url else None)

    def pay_on_cashier(tag):
        """收银台:立即支付 → 模拟渠道确认支付 → 轮询等待支付成功"""
        with ctx.expect_page(timeout=15000) as pop_info:
            page.get_by_role("button", name="立即支付").first.click()
        pop = pop_info.value
        pop.wait_for_load_state("networkidle", timeout=20000)
        pop.locator("a", has_text="确认支付").first.click()
        pop.wait_for_load_state("networkidle", timeout=20000)
        paid = False
        for _ in range(15):
            page.wait_for_timeout(2000)
            if page.locator(".el-alert--success", has_text="支付成功").count() > 0:
                paid = True
                break
        page.screenshot(path=f"{OUT}\\{tag}_paid.png", full_page=True)
        return paid

    def submit_checkout(tag):
        """结算页:填收货信息 → 提交订单并支付 → 进入收银台"""
        page.get_by_placeholder("姓名").fill("小明")
        page.get_by_placeholder("11位手机号").fill("13900000009")
        page.get_by_placeholder("省市区 + 详细地址").fill("北京市海淀区中关村大街1号")
        page.screenshot(path=f"{OUT}\\{tag}_checkout.png", full_page=True)
        page.get_by_role("button", name="提交订单并支付").click()
        page.wait_for_url("**/cashier**", timeout=30000)
        page.wait_for_timeout(1000)

    # 0. 未登录访问 /my/orders → 跳登录页(BUG-01 回归)
    try:
        page.goto(BASE + "/my/orders", wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(600)
        step("BUG-01 未登录访问我的订单→跳登录页", "/user/login" in page.url, f"url={page.url}")
    except Exception:
        traceback.print_exc(); step("BUG-01 未登录访问我的订单→跳登录页", False, "exception")

    # 1. 首页
    try:
        page.goto(BASE + "/", wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(1000)
        page.screenshot(path=f"{OUT}\\01_home.png", full_page=True)
        cards = page.locator("a.item").count()
        step("首页商品卡片>=10", cards >= 10, f"cards={cards}")
        body = page.inner_text("body")
        step("分类页签齐全(数码/生鲜/服饰/图书)", all(k in body for k in ["数码", "生鲜", "服饰", "图书"]))
    except Exception:
        traceback.print_exc(); step("首页加载", False, "exception")

    # 2. 服饰分类筛选
    try:
        page.locator("button.cat", has_text="服饰").click()
        page.wait_for_timeout(1200)
        n = page.locator("a.item").count()
        page.screenshot(path=f"{OUT}\\02_home_clothing.png", full_page=True)
        step("服饰分类筛选=4件", n == 4, f"cards={n}")
        page.locator("button.cat", has_text="图书").click()
        page.wait_for_timeout(1200)
        n2 = page.locator("a.item").count()
        step("图书分类筛选=2件", n2 == 2, f"cards={n2}")
        page.locator("button.cat", has_text="全部").click()
        page.wait_for_timeout(800)
    except Exception:
        traceback.print_exc(); step("分类筛选", False, "exception")

    # 3. 商品详情(记录商品名,后面校验购物车/直购互不污染)
    first_name = ""
    try:
        page.locator("a.item").first.click()
        page.wait_for_url("**/goods/**", timeout=15000)
        page.wait_for_load_state("networkidle", timeout=20000)
        page.wait_for_timeout(800)
        page.screenshot(path=f"{OUT}\\03_goods_detail.png", full_page=True)
        first_name = page.locator(".detail h1").inner_text().strip()
        body = page.inner_text("body")
        step("商品详情(名称/价格/库存)", "¥" in body and "库存" in body and "件" in body, f"name={first_name}")
    except Exception:
        traceback.print_exc(); step("商品详情页", False, "exception")

    # 4. 未登录加购 → 弹登录提示 → 去登录(BUG-04 回归)
    try:
        page.get_by_role("button", name="加入购物车").click()
        box = page.locator(".el-message-box", has_text="请先登录")
        box.wait_for(state="visible", timeout=5000)
        step("BUG-04 未登录加购弹出登录提示", True)
        page.screenshot(path=f"{OUT}\\04_login_prompt.png")
        box.get_by_role("button", name="去登录").click()
        page.wait_for_url("**/user/login**", timeout=15000)
        page.locator(".el-message-box").wait_for(state="hidden", timeout=5000)
        step("去登录跳转登录页(带redirect)", "redirect=" in page.url, page.url)
    except Exception:
        traceback.print_exc(); step("BUG-04 未登录加购弹出登录提示", False, "exception")

    # 5. 登录 → 回跳详情 → 加购成功
    try:
        page.get_by_placeholder("账号").fill("user1")
        page.get_by_placeholder("密码").fill("123456")
        page.get_by_role("button", name="登录", exact=True).click()
        page.wait_for_url("**/goods/**", timeout=20000)
        page.wait_for_timeout(800)
        step("登录后回跳商品详情页", first_name in page.locator(".detail h1").inner_text(), page.url)
        page.get_by_role("button", name="加入购物车").click()
        page.wait_for_timeout(800)
        step("登录后加入购物车成功", page.locator(".el-message", has_text="已加入购物车").count() > 0)
    except Exception:
        traceback.print_exc(); step("登录后加购", False, "exception")

    # 6. 立即购买(第二件商品) → 直达结算,不经过购物车 → 支付闭环
    second_name = ""
    try:
        page.goto(BASE + "/", wait_until="networkidle", timeout=20000)
        page.locator("a.item").nth(1).click()
        page.wait_for_url("**/goods/**", timeout=15000)
        page.wait_for_load_state("networkidle", timeout=20000)
        page.wait_for_timeout(800)
        second_name = page.locator(".detail h1").inner_text().strip()
        page.get_by_role("button", name="立即购买").click()
        page.wait_for_url("**/checkout**", timeout=20000)
        page.wait_for_timeout(800)
        step("立即购买直达结算页(from=buy)", "from=buy" in page.url, page.url)
        cbody = page.inner_text("body")
        step("结算页仅含直购商品", second_name in cbody and first_name not in cbody,
             f"want={second_name} not={first_name}")
        step("直购为1件1笔订单", "共1件,拆分为1笔订单" in cbody.replace(" ", ""))
        page.screenshot(path=f"{OUT}\\07_buynow_checkout.png", full_page=True)
        submit_checkout("buynow")
        ok = pay_on_cashier("buynow")
        step("立即购买订单支付成功", ok)
    except Exception:
        traceback.print_exc(); step("立即购买链路", False, "exception")

    # 7. 我的订单可见
    try:
        if page.get_by_role("button", name="查看我的订单").count():
            page.get_by_role("button", name="查看我的订单").click()
        else:
            page.goto(BASE + "/my/orders", wait_until="networkidle", timeout=20000)
        page.wait_for_load_state("networkidle", timeout=20000)
        page.wait_for_timeout(800)
        step("我的订单可见", page.locator(".order").count() > 0, f"rows={page.locator('.order').count()}")
        step("订单状态为待发货", page.locator(".order .el-tag", has_text="待发货").count() > 0)
        page.screenshot(path=f"{OUT}\\08_my_orders.png", full_page=True)
    except Exception:
        traceback.print_exc(); step("我的订单", False, "exception")

    # 8. 购物车:仅含加购商品(未被立即购买污染) → 去结算 → 支付闭环
    try:
        page.goto(BASE + "/cart", wait_until="networkidle", timeout=20000)
        page.wait_for_timeout(600)
        page.screenshot(path=f"{OUT}\\09_cart.png", full_page=True)
        cbody = page.inner_text("body")
        step("购物车仅含加购商品", first_name in cbody and second_name not in cbody,
             f"want={first_name} not={second_name}")
        page.get_by_role("button", name="去结算").click()
        page.wait_for_url("**/checkout**", timeout=20000)
        page.wait_for_timeout(800)
        kbody = page.inner_text("body")
        step("购物车结算页不含直购商品", first_name in kbody and second_name not in kbody)
        submit_checkout("cart")
        ok = pay_on_cashier("cart")
        step("购物车订单支付成功", ok)
    except Exception:
        traceback.print_exc(); step("购物车链路", False, "exception")

    # 9. 订单详情抽屉
    try:
        page.goto(BASE + "/my/orders", wait_until="networkidle", timeout=20000)
        page.wait_for_timeout(800)
        page.get_by_role("button", name="订单详情").first.click()
        page.wait_for_timeout(800)
        step("订单详情抽屉打开", page.locator(".el-drawer").count() > 0)
        page.screenshot(path=f"{OUT}\\10_order_drawer.png")
    except Exception:
        traceback.print_exc(); step("订单详情抽屉", False, "exception")

    browser.close()

print("\n---- 控制台错误 ----")
for e in console_errors[:10]:
    print("  CONSOLE:", e[:200])
print("---- 失败接口(>=400) ----")
for e in failed_api[:10]:
    print("  API:", e[:200])
npass = sum(1 for _, ok, _ in results if ok)
print(f"\n========== 商城端E2E: 通过 {npass}/{len(results)} ==========")
sys.exit(0)
