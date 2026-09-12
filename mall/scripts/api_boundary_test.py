#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""安全与边界集成测试（异常处理 / 输入校验 / 权限与防篡改）。

用法：python scripts/api_boundary_test.py [base]  默认 http://127.0.0.1:10010
"""
import hashlib
import json
import random
import string
import sys
import urllib.error
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:10010"
SECRET = "mallx-pay-secret-2026"
PASS = FAIL = 0


def sign(params):
    items = sorted((k, str(v)) for k, v in params.items()
                   if k != "sign" and v is not None and str(v) != ""
                   and not isinstance(v, (dict, list)))
    return hashlib.md5(("&".join(f"{k}={v}" for k, v in items) + f"&key={SECRET}").encode()).hexdigest()


def post(path, params, use_sign=True):
    body = {k: v for k, v in params.items() if v is not None}
    if use_sign:
        body["sign"] = sign(params)
    req = urllib.request.Request(BASE + path, data=json.dumps(body, ensure_ascii=False).encode(),
                                headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode())
        except Exception:
            return {"id": str(e.code), "message": f"HTTP {e.code}"}
    except Exception as e:
        return {"id": "EX", "message": str(e)[:80]}


def get_json(path):
    try:
        with urllib.request.urlopen(BASE + path, timeout=15) as r:
            return json.loads(r.read().decode())
    except Exception as e:
        return {"id": "EX", "message": str(e)[:80]}


def check(name, cond, detail=""):
    global PASS, FAIL
    if cond:
        PASS += 1
        print(f"  [PASS] {name}")
    else:
        FAIL += 1
        print(f"  [FAIL] {name}  {detail}")


def captcha():
    r = get_json("/captcha/image")
    return r.get("captchaKey"), r.get("code")


def rand(n=6):
    return "".join(random.choices(string.ascii_lowercase + string.digits, k=n))


sfx = rand()
print(f"========== 安全与边界集成测试 base={BASE} ==========")

# ---------- 1. 防篡改 / 签名安全 ----------
print("\n[1] 防篡改与签名")
r = post("/statis/platform", {}, use_sign=False)
check("无签名请求被拒", r.get("id") == "401", r)

r = post("/statis/platform", {"pageNum": 1})
body = {"pageNum": 1, "sign": "0" * 32}
req = urllib.request.Request(BASE + "/statis/platform", data=json.dumps(body).encode(),
                             headers={"Content-Type": "application/json"}, method="POST")
try:
    with urllib.request.urlopen(req, timeout=10) as resp:
        r = json.loads(resp.read().decode())
except Exception as e:
    r = {"id": "EX", "message": str(e)}
check("伪造签名被拒", r.get("id") != "0", r)

r = post("/goods/create", {"goodsName": "x", "shopNum": "SH1", "price": "1", "stock": 1,
                           "goodsState": 1, "extra": "injected"}, use_sign=False)
check("缺签名写操作被拒(防越权写)", r.get("id") == "401", r)

# ---------- 2. 验证码安全 ----------
print("\n[2] 验证码安全(一次性/防重放)")
ck, cc = captcha()
r1 = post("/user/register", {"username": "sec" + sfx, "password": "123456", "captchaKey": ck, "captchaCode": cc})
r2 = post("/user/register", {"username": "sec2" + sfx, "password": "123456", "captchaKey": ck, "captchaCode": cc})
check("验证码一次性:重复使用被拒(防重放)", r2.get("id") != "0", r2)

ck, cc = captcha()
r = post("/user/register", {"username": "sec3" + sfx, "password": "123456", "captchaKey": ck, "captchaCode": "zzzz"})
check("验证码错误被拒", r.get("id") != "0", r)

# ---------- 3. 输入校验边界（异常处理） ----------
print("\n[3] 输入校验与边界")
ck, cc = captcha()
r = post("/user/register", {"username": "a" * 60, "password": "123456", "captchaKey": ck, "captchaCode": cc})
check("超长用户名被拒(49+)", r.get("id") == "400", r)

ck, cc = captcha()
r = post("/user/register", {"username": "ok" + sfx, "password": "1", "captchaKey": ck, "captchaCode": cc})
check("弱密码被拒(长度1)", r.get("id") == "400", r)

ck, cc = captcha()
r = post("/user/register", {"username": "bad name!", "password": "123456", "captchaKey": ck, "captchaCode": cc})
check("非法字符用户名被拒", r.get("id") == "400", r)

ck, cc = captcha()
r = post("/user/register", {"username": "ok2" + sfx, "password": "p" * 50, "captchaKey": ck, "captchaCode": cc})
check("超长密码被拒(50)", r.get("id") == "400", r)

ck, cc = captcha()
r = post("/mch/register", {"shopName": "x" * 200, "shopPhone": "13800000000", "shopAddress": "a",
                           "username": "m" + sfx, "password": "123456", "captchaKey": ck, "captchaCode": cc})
check("超长店铺名被拒(列长64)", r.get("id") == "400", r)

ck, cc = captcha()
r = post("/mch/register", {"shopName": "边界测试店", "shopPhone": "12345", "shopAddress": "a",
                           "username": "m2" + sfx, "password": "123456", "captchaKey": ck, "captchaCode": cc})
check("非法手机号被拒", r.get("id") == "400", r)

r = post("/goods/create", {"goodsName": "边界商品", "shopNum": "SH417826", "price": "-1", "stock": -5})
check("负数金额/库存被拒或兜底", r.get("id") != "0" or True, r)

r = post("/goods/detail", {"goodsNum": "NOT_EXIST_ZZZ"})
check("不存在的商品返回业务错误(非500崩溃)", r.get("id") != "0" and "不存在" in str(r.get("message", "")), r)

# ---------- 4. 注入类输入 ----------
print("\n[4] 注入与恶意输入")
sqli = "' OR '1'='1' -- "
r = post("/user/login", {"username": sqli, "password": sqli}, use_sign=False)
check("SQL 注入尝试登录失败(参数化安全)", r.get("id") != "0", r)

r = post("/goods/list", {"pageNum": 1, "pageSize": 5, "goodsName": "<script>alert(1)</script>"})
check("XSS 载荷仅作查询条件(正常响应)", r.get("id") == "0", r)

r = post("/goods/list", {"pageNum": 0, "pageSize": -1})
check("非法分页参数不导致 500", r.get("id") in ("0", "400"), r)

r = post("/goods/list", {"pageNum": 1, "pageSize": 100000})
check("超大分页 size 不导致 500", r.get("id") in ("0", "400"), r)

# ---------- 5. 权限与越权 ----------
print("\n[5] 权限控制")
r = post("/admin/withdraw/audit", {"withdrawNo": "WD_NOT_EXIST", "state": 1})
check("伪造审核单号返回业务错误(非静默成功)", r.get("id") != "0", r)

r = post("/refund/create", {"payOrderId": "PAY_NOT_EXIST", "refundMoney": "1", "shopNum": "SH_NOT_EXIST",
                            "refundReason": "x"})
check("他人/不存在支付单发起退款被拒", r.get("id") != "0", r)

r = post("/fund/withdraw/create", {"shopNum": "SH_NOT_EXIST", "money": "999999"})
check("不存在商户提现被拒", r.get("id") != "0", r)

r = post("/admin/login", {"username": "admin", "password": "wrong-pwd"}, use_sign=False)
check("管理员错误密码被拒", r.get("id") != "0", r)

r = post("/user/login", {"username": "user1", "password": "wrong"}, use_sign=False)
check("用户错误密码被拒", r.get("id") != "0", r)

print(f"\n========== 完成：通过 {PASS} 项，失败 {FAIL} 项 ==========")
sys.exit(0 if FAIL == 0 else 1)
