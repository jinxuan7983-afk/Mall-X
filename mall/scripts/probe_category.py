#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""探针2：捕获浏览器实际发送的 /admin/category/save 请求体"""
import json
import os

from playwright.sync_api import sync_playwright

BASE = "http://localhost:5173"
OUT = r"C:\毕业设计\多商家电商平台\mall\test-results"

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    ctx = browser.new_context(viewport={"width": 1440, "height": 900})
    page = ctx.new_page()

    def on_req(req):
        if "category/save" in req.url:
            print("REQUEST URL:", req.url)
            print("REQUEST BODY:", req.post_data)

    page.on("request", on_req)
    page.on("response", lambda r: print("RESPONSE:", r.status, r.url, r.text()[:200]) if "category/save" in r.url else None)
    page.on("pageerror", lambda e: print("PAGEERROR:", str(e)[:150]))

    page.goto(BASE + "/admin/login", wait_until="networkidle", timeout=30000)
    ins = page.locator("input:visible")
    ins.nth(0).fill("admin")
    ins.nth(1).fill("123456")
    page.get_by_role("button", name="登录").click()
    page.wait_for_url("**/admin", timeout=20000)
    page.wait_for_timeout(600)

    page.locator(".el-menu-item", has_text="类目管理").click()
    page.wait_for_timeout(1200)
    page.get_by_role("button", name="新建类目").click()
    page.wait_for_timeout(500)
    page.get_by_placeholder("类目名称").fill("探针类目X")
    page.get_by_placeholder("数值越小越靠前").fill("9")
    page.locator(".el-dialog", has_text="新建顶级类目").get_by_role("button", name="保存").click()
    page.wait_for_timeout(2000)
    print("dialog still open:", page.locator(".el-dialog", has_text="新建顶级类目").count() > 0)
    page.screenshot(path=f"{OUT}\\61_probe_category.png")

    browser.close()
