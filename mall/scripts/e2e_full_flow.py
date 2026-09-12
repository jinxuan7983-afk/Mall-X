#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Mall-X 全流程浏览器 E2E（真实联调模式，自洽三角色业务闭环 + 交互守卫）。

自洽设计：先由新商户上架商品 → 用户购买该商品 → 订单回到该商户履约/退款/提现，
全程使用随机后缀的新账号与新品，互不干扰、可重复执行。

覆盖：
  阶段1 商户入驻：注册(图形验证码) → 平台审核通过 → 登录工作台 → 发布商品(提取商品编号)
  阶段2 用户购物：未登录守卫 → 注册(验证码,自动登录) → 商品详情 → 加购 → 购物车
                → 结算 → 收银台 → 模拟渠道支付(轮询确认) → 订单待发货(MQ事件驱动)
  阶段3 商户履约：订单发货(物流单号) → 交易对账 → 发起部分退款 → 退款进度 → 提现申请
  阶段4 平台审核：数据看板 → 退款审核(→退款成功) → 提现审核(→已到账) → 操作日志(AOP)
  阶段5 用户回访：确认收货 → 订单已完成

验证码获取：拦截 /api/captcha/image 响应中的 echo code（测试环境 echo-code=true）。
用法：python scripts/e2e_full_flow.py          截图：mall/test-results/full-flow/
退出码：0 全部通过；1 存在失败项
"""
import os
import random
import re
import string
import sys
import traceback

from playwright.sync_api import sync_playwright

BASE = os.environ.get("E2E_BASE", "http://localhost:5173")
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "test-results", "full-flow")
os.makedirs(OUT, exist_ok=True)

results = []
console_errors = []
failed_api = []
shot_seq = [0]


def step(name, cond, detail=""):
    results.append((name, bool(cond), detail))
    print(f"  [{'PASS' if cond else 'FAIL'}] {name}" + (f"  {detail}" if detail and not cond else ""), flush=True)


def shot(page, tag):
    shot_seq[0] += 1
    path = os.path.join(OUT, f"{shot_seq[0]:02d}_{tag}.png")
    try:
        page.screenshot(path=path, full_page=True)
    except Exception:
        try:
            page.screenshot(path=path)
        except Exception:
            pass
    return path


def rand(n=6):
    return "".join(random.choices(string.ascii_lowercase + string.digits, k=n))


SUFFIX = rand()
USER = "flow" + SUFFIX
SHOP_USER = "flowmch" + SUFFIX
SHOP_NAME = f"全流程旗舰店{SUFFIX}"
PHONE = "138" + "".join(random.choices(string.digits, k=8))
PWD = "flow123456"
GOODS_NAME = f"全流程测试商品{SUFFIX}"

captcha_code = {"v": ""}


def bind_captcha_listener(page):
    def on_response(r):
        if "/captcha/image" in r.url:
            try:
                j = r.json()
                if j.get("code"):
                    captcha_code["v"] = j["code"]
            except Exception:
                pass

    page.on("response", on_response)


def confirm_box(page, timeout=6000):
    """点击 ElMessageBox 确认框的「确定」（审核类操作为 MessageBox 而非 Dialog）"""
    page.locator(".el-message-box").last.get_by_role("button", name="确定").first.click(timeout=timeout)


def fill_captcha(page, trigger=None):
    """触发/等待验证码响应后填入（echo code 自动获取）"""
    captcha_code["v"] = ""
    if trigger is not None:
        trigger.click()
    for _ in range(20):
        if captcha_code["v"]:
            break
        page.wait_for_timeout(300)
    code = captcha_code["v"]
    page.get_by_placeholder("验证码").fill(code)
    return code


print(f"========== Mall-X 全流程 E2E  base={BASE} ==========")
print(f"测试账号：商户 {SHOP_USER} / 用户 {USER} / 手机 {PHONE}")

goods_num = ""

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    ctx = browser.new_context(viewport={"width": 1440, "height": 900})
    page = ctx.new_page()
    page.on("console", lambda m: console_errors.append(m.text) if m.type == "error" else None)
    page.on("pageerror", lambda e: console_errors.append(f"pageerror: {e}"))
    page.on(
        "response",
        lambda r: failed_api.append(f"{r.status} {r.url}")
        if r.request.method != "OPTIONS" and r.status >= 400 and "/api/" in r.url
        else None,
    )
    bind_captcha_listener(page)

    # ============ 阶段 1：商户入驻 → 审核 → 上架（先建货，闭环之源） ============
    print("\n[1] 商户端：入驻/审核/上架")
    try:
        page.goto(BASE + "/mch/login", wait_until="networkidle", timeout=30000)
        page.get_by_role("tab", name="入驻注册").click()
        page.wait_for_timeout(800)
        page.get_by_placeholder("店铺名称(64字以内)").fill(SHOP_NAME)
        page.get_by_placeholder("11位手机号").fill(PHONE)
        page.get_by_placeholder("省市区 + 详细地址").fill("广东省广州市白云区全流程大道 1 号")
        page.get_by_placeholder("4~20位字母/数字/下划线").fill(SHOP_USER)
        page.get_by_placeholder("6~20位").fill(PWD)
        fill_captcha(page, trigger=page.locator("img[alt='验证码']").first)
        shot(page, "mch_register_filled")
        page.get_by_role("button", name="提交入驻申请").click()
        page.wait_for_timeout(2500)
        shop_num_text = page.locator(".shopnum .num").inner_text() if page.locator(".shopnum .num").count() else ""
        step("商户入驻提交→返回商户编号", bool(shop_num_text), f"shopNum={shop_num_text}")
        shot(page, "mch_register_done")

        # 管理员审核通过
        page.goto(BASE + "/admin/login", wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(800)
        page.get_by_placeholder("管理员账号").fill("admin")
        page.get_by_placeholder("密码").fill("123456")
        page.get_by_role("button", name="登录管理后台").click()
        page.wait_for_url("**/admin**", timeout=20000)
        page.wait_for_timeout(2000)
        step("管理员登录成功", "/admin" in page.url)
        shot(page, "admin_dashboard")

        page.goto(BASE + "/admin/merchants", wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(1500)
        row = page.locator("tr", has_text=SHOP_USER).first
        row.locator("button", has_text="通过").first.click()
        page.wait_for_timeout(900)
        confirm_box(page)
        page.wait_for_timeout(2000)
        row_after = page.locator("tr", has_text=SHOP_USER).first
        step("商户审核通过(→正常经营)", "正常" in (row_after.inner_text() if row_after.count() else ""))
        shot(page, "admin_mch_approved")

        # 商户登录 → 工作台 → 发布商品
        page.goto(BASE + "/mch/login", wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(800)
        page.get_by_placeholder("登录账号").fill(SHOP_USER)
        page.get_by_placeholder("密码").fill(PWD)
        page.get_by_role("button", name="登录经营后台").click()
        page.wait_for_url("**/mch", timeout=20000)
        page.wait_for_timeout(2500)
        step("商户登录→工作台渲染", page.get_by_text("今日经营概览").count() > 0)
        shot(page, "mch_dashboard")

        page.goto(BASE + "/mch/goods", wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(1800)
        page.get_by_role("button", name="发布商品").first.click()
        page.wait_for_timeout(800)
        page.get_by_placeholder("商品名称").fill(GOODS_NAME)
        page.get_by_placeholder("0.01 起").fill("66.00")
        page.get_by_placeholder("库存数量").fill("20")
        page.locator(".el-dialog .el-select").first.click()
        page.wait_for_timeout(700)
        page.locator(".el-select-dropdown__item:visible").first.click()
        page.get_by_placeholder("卖点、规格等").fill("全流程自动化测试商品")
        shot(page, "mch_goods_form")
        page.locator(".el-dialog").get_by_role("button", name="保存").first.click()
        page.wait_for_timeout(2500)
        row_goods = page.locator("tr", has_text=GOODS_NAME).first
        step("商户发布商品成功", row_goods.count() > 0)
        if row_goods.count():
            m = re.search(r"G\d+", row_goods.inner_text())
            goods_num = m.group(0) if m else ""
        step("提取新商品编号(闭环购买目标)", bool(goods_num), f"goodsNum={goods_num}")
        shot(page, "mch_goods_created")
    except Exception:
        traceback.print_exc()
        step("阶段1异常中断", False, traceback.format_exc().splitlines()[-1])
        shot(page, "stage1_error")

    # ============ 阶段 2：用户注册 → 购买新商户商品 → 支付 ============
    print("\n[2] 用户端：注册与购物闭环")
    try:
        # 未登录守卫
        page.goto(BASE + "/my/orders", wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(500)
        step("守卫:未登录访问我的订单→跳登录页", "/user/login" in page.url, f"url={page.url}")
        shot(page, "user_guard_login")

        # 用户注册（验证码）
        page.get_by_role("tab", name="注册").click()
        page.wait_for_timeout(800)
        page.get_by_placeholder("账号(4~20位字母/数字/下划线)").fill(USER)
        page.get_by_placeholder("密码(6~20位)").fill(PWD)
        page.get_by_placeholder("确认密码").fill(PWD)
        fill_captcha(page, trigger=page.locator("img[alt='验证码']").first)
        shot(page, "user_register_filled")
        page.get_by_role("button", name="注册并登录").click()
        page.wait_for_url(BASE + "/**", timeout=20000)
        page.wait_for_timeout(1500)
        step("用户注册成功(验证码)→自动登录", "/user/login" not in page.url, f"url={page.url}")
        shot(page, "user_home_logged_in")

        # 商品详情（新商户的商品）
        page.goto(BASE + f"/goods/{goods_num}", wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(800)
        step("新商品详情页渲染", page.get_by_role("heading", name=GOODS_NAME).count() > 0)
        shot(page, "user_goods_detail")

        # 加购 → 购物车 → 结算
        page.get_by_role("button", name="加入购物车").click()
        page.wait_for_timeout(1200)
        step("已登录加购成功", page.locator(".el-message--success").count() > 0
             or page.locator(".el-badge__content").count() > 0)
        shot(page, "user_add_cart")

        page.goto(BASE + "/cart", wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(800)
        step("购物车含新商品", page.get_by_text(GOODS_NAME).count() > 0)
        shot(page, "user_cart")
        page.get_by_role("button", name="去结算").click()
        page.wait_for_timeout(1500)
        step("进入订单确认页", "checkout" in page.url or page.get_by_text("收货信息").count() > 0)
        shot(page, "user_checkout")

        page.get_by_placeholder("姓名").fill("全流程测试员")
        page.get_by_placeholder("11位手机号").fill("13912345678")
        page.get_by_placeholder("省市区 + 详细地址").fill("广东省广州市天河区全流程测试路 88 号")
        page.get_by_role("button", name="提交订单并支付").click()
        page.wait_for_url("**/cashier**", timeout=30000)
        page.wait_for_timeout(1000)
        step("到达收银台(订单已创建)", "/cashier" in page.url)
        shot(page, "user_cashier")

        # 模拟渠道支付
        with ctx.expect_page(timeout=20000) as pop_info:
            page.get_by_role("button", name="立即支付").click()
        pop = pop_info.value
        pop.wait_for_load_state("networkidle", timeout=20000)
        shot(pop, "user_mock_cashier")
        pop.locator("a", has_text="确认支付").first.click()
        pop.wait_for_load_state("networkidle", timeout=20000)
        paid = False
        for _ in range(15):
            page.wait_for_timeout(2000)
            if page.locator(".el-alert--success", has_text="支付成功").count() > 0:
                paid = True
                break
        step("模拟渠道支付成功(前端轮询确认)", paid)
        shot(page, "user_paid")

        page.get_by_role("button", name="查看我的订单").click()
        page.wait_for_timeout(2000)
        ready = False
        for _ in range(20):
            if page.get_by_text("待发货").count() > 0:
                ready = True
                break
            page.wait_for_timeout(2000)
        step("MQ 事件驱动订单→待发货", ready)
        shot(page, "user_orders_wait_ship")
    except Exception:
        traceback.print_exc()
        step("阶段2异常中断", False, traceback.format_exc().splitlines()[-1])
        shot(page, "stage2_error")

    # ============ 阶段 3：商户履约（发货/退款/提现） ============
    print("\n[3] 商户端：发货、对账与资金")
    try:
        page.goto(BASE + "/mch/orders", wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(2000)
        has_ship = page.get_by_role("button", name="发货").count() > 0
        step("新商户订单列表含待发货订单", has_ship)
        if has_ship:
            page.get_by_role("button", name="发货").first.click()
            page.wait_for_timeout(1200)
            page.locator(".el-dialog .el-select").first.click()
            page.wait_for_timeout(700)
            page.locator(".el-select-dropdown__item:visible", has_text="顺丰").first.click()
            page.locator(".el-dialog input[placeholder='物流运单号']").fill("SF" + SUFFIX.upper())
            shot(page, "mch_deliver_form")
            page.locator(".el-dialog").get_by_role("button", name="确认发货").first.click()
            page.wait_for_timeout(2500)
        step("商户发货成功", page.get_by_text("已发货").count() > 0)
        shot(page, "mch_delivered")

        # 交易流水 → 部分退款
        page.goto(BASE + "/mch/trade", wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(2000)
        step("交易流水渲染(含手续费)", page.get_by_text("手续费").count() > 0)
        shot(page, "mch_trade_flow")
        page.get_by_role("button", name="退款").first.click()
        page.wait_for_timeout(1200)
        page.get_by_placeholder("支持部分退款").fill("10")
        page.get_by_placeholder("退款原因").fill("全流程测试部分退款")
        shot(page, "mch_refund_form")
        page.locator(".el-dialog").get_by_role("button", name="提交申请").first.click()
        page.wait_for_timeout(2500)
        step("商户发起部分退款成功", page.locator(".el-message--success").count() > 0)

        page.goto(BASE + "/mch/refund", wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(1500)
        step("退款管理统计与列表渲染", page.locator(".statgrid .stat").count() == 4
             and page.locator(".el-table__row").count() > 0)
        shot(page, "mch_refund_progress")

        # 提现申请
        page.goto(BASE + "/mch/fund", wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(1800)
        page.get_by_role("button", name="提现").first.click()
        page.wait_for_timeout(1200)
        page.get_by_placeholder("不超过可提现余额").fill("1")
        shot(page, "mch_withdraw_form")
        page.locator(".el-dialog").get_by_role("button", name="提交申请").first.click()
        page.wait_for_timeout(2500)
        step("商户提现申请成功", page.get_by_text("审核中").count() > 0
             or page.locator(".el-message--success").count() > 0)
        shot(page, "mch_withdraw_created")
    except Exception:
        traceback.print_exc()
        step("阶段3异常中断", False, traceback.format_exc().splitlines()[-1])
        shot(page, "stage3_error")

    # ============ 阶段 4：平台审核 ============
    print("\n[4] 管理端：退款与提现审核")
    try:
        page.goto(BASE + "/admin/refunds", wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(2000)
        shot(page, "admin_refund_list")
        row = page.locator("tr", has_text="待审核").first
        if row.count():
            row.locator("button", has_text="通过").first.click()
            page.wait_for_timeout(900)
            confirm_box(page)
            page.wait_for_timeout(2500)
        step("平台退款审核→已完成", page.get_by_text("已完成").count() > 0)
        shot(page, "admin_refund_audited")

        page.goto(BASE + "/admin/withdraws", wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(2000)
        row = page.locator("tr", has_text="审核中").first
        if row.count():
            row.locator("button", has_text="通过").first.click()
            page.wait_for_timeout(900)
            confirm_box(page)
            page.wait_for_timeout(2500)
        step("平台提现审核→已到账", page.get_by_text("已到账").count() > 0)
        shot(page, "admin_withdraw_audited")

        # 平台商品管控:检索新商品 → 强制下架 → 恢复上架
        page.goto(BASE + "/admin/goods", wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(2000)
        page.get_by_placeholder("按商品名称搜索").fill(GOODS_NAME)
        page.get_by_role("button", name="查询").click()
        page.wait_for_timeout(2000)
        has_new = page.get_by_text(GOODS_NAME).count() > 0
        step("平台商品管控:可检索到新发布商品", has_new)
        shot(page, "admin_goods_list")
        if has_new:
            page.get_by_role("button", name="强制下架").first.click()
            page.wait_for_timeout(900)
            confirm_box(page)
            page.wait_for_timeout(2500)
            step("平台强制下架生效(状态转为下架)", page.get_by_role("button", name="恢复上架").count() > 0)
            shot(page, "admin_goods_off")
            page.get_by_role("button", name="恢复上架").first.click()
            page.wait_for_timeout(900)
            confirm_box(page)
            page.wait_for_timeout(2500)
            step("平台恢复上架成功", page.get_by_role("button", name="强制下架").count() > 0)
            shot(page, "admin_goods_on")

        page.goto(BASE + "/admin/logs", wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(1500)
        step("操作日志(AOP)含审核记录",
             page.get_by_text("withdrawAudit").count() > 0 or page.get_by_text("refundAudit").count() > 0)
        shot(page, "admin_logs")
    except Exception:
        traceback.print_exc()
        step("阶段4异常中断", False, traceback.format_exc().splitlines()[-1])
        shot(page, "stage4_error")

    # ============ 阶段 5：用户确认收货 ============
    print("\n[5] 用户回访：确认收货")
    try:
        page.goto(BASE + "/my/orders", wait_until="networkidle", timeout=30000)
        page.wait_for_timeout(2000)
        if page.get_by_role("button", name="确认收货").count():
            page.get_by_role("button", name="确认收货").first.click()
            page.wait_for_timeout(900)
            confirm_box(page)
            page.wait_for_timeout(2500)
        step("用户确认收货→订单已完成", page.get_by_text("已完成").count() > 0)
        shot(page, "user_order_completed")
    except Exception:
        traceback.print_exc()
        step("阶段5异常中断", False, traceback.format_exc().splitlines()[-1])
        shot(page, "stage5_error")

    browser.close()

# ================= 汇总 =================
passed = sum(1 for _, ok, _ in results if ok)
failed = len(results) - passed
print("\n========== 汇总 ==========")
for name, ok, detail in results:
    if not ok:
        print(f"  [FAIL] {name}  {detail}")
print(f"通过 {passed} / {len(results)} 项")
print(f"截图 {shot_seq[0]} 张 → {OUT}")

real_errors = [e for e in console_errors if "favicon" not in e]
if real_errors:
    print(f"\n浏览器控制台错误 {len(real_errors)} 条（前 5）：")
    for e in real_errors[:5]:
        print("  -", e[:160])
api_errors = [e for e in failed_api if "/captcha/" not in e]
if api_errors:
    print(f"\n失败请求 {len(api_errors)} 条（前 5）：")
    for e in api_errors[:5]:
        print("  -", e[:160])

sys.exit(0 if failed == 0 else 1)
