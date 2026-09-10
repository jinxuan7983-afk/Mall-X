#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验证商户台/管理台侧边栏底部文字已删除"""
from playwright.sync_api import sync_playwright

BASE = "http://localhost:5173"

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    ctx = browser.new_context(viewport={"width": 1440, "height": 900})
    page = ctx.new_page()

    # 商户台
    page.goto(BASE + "/mch/login", wait_until="networkidle", timeout=30000)
    page.get_by_placeholder("登录账号").fill("shop1")
    page.get_by_placeholder("密码").fill("123456")
    page.get_by_role("button", name="登录", exact=True).click()
    page.wait_for_url("**/mch", timeout=20000)
    page.wait_for_timeout(1000)
    mch_body = page.inner_text("body")
    print("商户台含'行业工程实践':", "行业工程实践" in mch_body)
    page.screenshot(path=r"C:\毕业设计\多商家电商平台\mall\test-results\70_mch_side.png")

    # 管理台
    page.goto(BASE + "/admin/login", wait_until="networkidle", timeout=30000)
    ins = page.locator("input:visible")
    ins.nth(0).fill("admin")
    ins.nth(1).fill("123456")
    page.get_by_role("button", name="登录", exact=True).click()
    page.wait_for_url("**/admin", timeout=20000)
    page.wait_for_timeout(1000)
    admin_body = page.inner_text("body")
    print("管理台含'行业工程实践':", "行业工程实践" in admin_body)
    page.screenshot(path=r"C:\毕业设计\多商家电商平台\mall\test-results\71_admin_side.png")

    browser.close()
