#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Mall-X 后端接口全链路联调测试脚本（仅依赖 Python 标准库）。

覆盖：商户注册/审核/登录 → 商品发布 → 用户注册/下单(Lua扣库存) → 统一支付下单
      → 支付回调(幂等) → 订单流转(发货/收货) → 退款(审核/渠道执行) → 统计报表
      → 资金提现 → 管理端查询 → 限流验证 → 签名防篡改验证。

用法：python scripts/api_test.py [base_url]   # 默认 http://127.0.0.1:10010
"""
import hashlib
import json
import random
import string
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:10010"
SECRET = "mallx-pay-secret-2026"
INTERNAL_TOKEN = "mallx-internal-token"

PASS = 0
FAIL = 0


def sign_params(params: dict) -> str:
    """统一签名：剔除 sign/空值/嵌套结构，按 key ASCII 升序 k=v& 拼接，末尾拼 &key=密钥 后 MD5"""
    items = sorted((k, str(v)) for k, v in params.items()
                   if k != "sign" and v is not None and str(v) != ""
                   and not isinstance(v, (dict, list)))
    raw = "&".join(f"{k}={v}" for k, v in items) + f"&key={SECRET}"
    return hashlib.md5(raw.encode("utf-8")).hexdigest()


def post(path: str, params: dict, use_sign=True, headers=None) -> dict:
    body = {k: v for k, v in params.items() if v is not None}
    if use_sign:
        body["sign"] = sign_params(params)
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    hdrs = {"Content-Type": "application/json"}
    if headers:
        hdrs.update(headers)
    req = urllib.request.Request(BASE + path, data=data, headers=hdrs, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode("utf-8"))
        except Exception:
            return {"id": str(e.code), "message": f"HTTP {e.code}"}


def get_json(path: str) -> dict:
    req = urllib.request.Request(BASE + path, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode("utf-8"))
        except Exception:
            return {"id": str(e.code), "message": f"HTTP {e.code}"}


def fetch_captcha() -> tuple:
    """获取图形验证码（测试环境 echo-code=true 回显明文），返回 (captchaKey, code)"""
    r = get_json("/captcha/image")
    if r.get("id") != "0" or not r.get("code"):
        raise SystemExit(f"验证码接口不可用（需 echo-code=true）: {r}")
    return r["captchaKey"], r["code"]


def check(name, cond, detail=""):
    global PASS, FAIL
    if cond:
        PASS += 1
        print(f"  [PASS] {name}")
    else:
        FAIL += 1
        print(f"  [FAIL] {name}  {detail}")


def rand_str(n=8):
    return "".join(random.choices(string.ascii_lowercase + string.digits, k=n))


print(f"========== Mall-X 接口联调测试  base={BASE} ==========")

# ---------- 1. 商户注册 ----------
sfx = rand_str()
phone = "138" + "".join(random.choices(string.digits, k=8))  # 纯数字 11 位手机号
print("\n[1] 商户管理模块")
# 图形验证码负向用例：错误验证码必须被拒
ck_bad, _ = fetch_captcha()
r = post("/mch/register", {
    "shopName": f"联调测试商铺{sfx}", "shopPhone": phone,
    "shopAddress": "测试地址", "username": f"test{sfx}", "password": "123456",
    "captchaKey": ck_bad, "captchaCode": "0000"})
check("注册验证码错误被拒", r.get("id") != "0", r)

ck, cc = fetch_captcha()
r = post("/mch/register", {
    "shopName": f"联调测试商铺{sfx}", "shopPhone": phone,
    "shopAddress": "测试地址", "username": f"test{sfx}", "password": "123456",
    "captchaKey": ck, "captchaCode": cc})
check("4.1.1 商户注册", r.get("id") == "0" and "shopNum" in r, r)
shop_num = r.get("shopNum")

# 坏签名必须被拒
r = post("/mch/register", {"shopName": "x", "shopPhone": "1", "shopAddress": "x",
                           "username": "bad" + sfx, "password": "123456",
                           "captchaKey": ck, "captchaCode": cc}, use_sign=False)
check("签名缺失被拒绝", r.get("id") != "0", r)

# 手机号格式校验：非 11 位手机号必须被拒
ck, cc = fetch_captcha()
r = post("/mch/register", {
    "shopName": "格式校验商铺", "shopPhone": "12345", "shopAddress": "x",
    "username": "fmt" + sfx, "password": "123456",
    "captchaKey": ck, "captchaCode": cc})
check("商户手机号格式校验", r.get("id") != "0", r)

# ---------- 2. 管理员登录 + 商户审核 ----------
r = post("/admin/login", {"username": "admin", "password": "123456"}, use_sign=False)
check("管理员登录", r.get("id") == "0", r)
admin_token = r.get("token")

r = post("/admin/mch/audit", {"shopNum": shop_num, "shopState": 1, "auditRemark": "资料齐全"})
check("4.5.1 商户审核通过", r.get("id") == "0", r)

r = post("/mch/login", {"username": f"test{sfx}", "password": "123456"}, use_sign=False)
check("4.1.2 商户登录(shopState=1)", r.get("id") == "0" and int(r.get("shopState", -1)) == 1, r)

# ---------- 3. 商品发布 ----------
print("\n[2] 商品模块")
r = post("/goods/create", {"goodsName": "联调测试商品", "shopNum": shop_num, "price": "58.80",
                           "stock": 50, "goodsState": 1})
check("商品发布", r.get("id") == "0", r)
goods_num = r.get("goodsNum")

r = post("/goods/detail", {"goodsNum": goods_num})
check("商品详情(Redis缓存)", r.get("id") == "0", r)
stock0 = r.get("goods", {}).get("stock", 0)

# ---------- 4. 用户注册 + 下单 ----------
print("\n[3] 用户购物与下单")
ck, cc = fetch_captcha()
r = post("/user/register", {"username": "u" + sfx, "password": "123456",
                            "captchaKey": ck, "captchaCode": cc})
check("用户注册", r.get("id") == "0", r)
user_num = r.get("userNum")

# 弱密码/短用户名校验：5 位密码必须被拒
ck, cc = fetch_captcha()
r = post("/user/register", {"username": "weak" + sfx, "password": "12345",
                            "captchaKey": ck, "captchaCode": cc})
check("用户密码长度校验", r.get("id") != "0", r)

r = post("/user/login", {"username": "u" + sfx, "password": "123456"}, use_sign=False)
check("用户登录", r.get("id") == "0", r)

order_items = [{"goodsNum": goods_num, "count": 2}]
r = post("/order/create", {"userNum": user_num, "shopNum": shop_num, "items": order_items,
                           "receiverName": "小明", "receiverPhone": "13900000001",
                           "receiverAddress": "广州市天河区1号"})
check("下单(嵌套items不参与签名)", r.get("id") == "0", r)
order_num = r.get("orderNum")

r = post("/goods/detail", {"goodsNum": goods_num})
check("Lua预扣库存(50-2=48)", r.get("goods", {}).get("stock") == stock0 - 2,
      f"expect {stock0 - 2}, got {r.get('goods', {}).get('stock')}")

# 库存不足场景
r = post("/order/create", {"userNum": user_num, "shopNum": shop_num,
                           "items": [{"goodsNum": goods_num, "count": 99999}],
                           "receiverName": "x", "receiverPhone": "x", "receiverAddress": "x"})
check("库存不足下单被拒", r.get("id") != "0", r)

# ---------- 5. 统一支付下单 + 回调幂等 ----------
print("\n[4] 支付交易模块")
r = post("/pay/create", {"shopNum": shop_num, "shopName": f"联调测试商铺{sfx}", "appId": "mallx-app",
                         "wayCode": "ALIPAY", "payMoney": "117.60", "subject": "联调测试商品x2",
                         "userNum": user_num, "orderNum": order_num})
check("4.2.1 统一支付下单", r.get("id") == "0" and "payOrderId" in r and "payUrl" in r, r)
pay_order_id = r.get("payOrderId")

# 重复下单幂等
r2 = post("/pay/create", {"shopNum": shop_num, "shopName": f"联调测试商铺{sfx}", "appId": "mallx-app",
                          "wayCode": "ALIPAY", "payMoney": "117.60", "subject": "联调测试商品x2",
                          "userNum": user_num, "orderNum": order_num})
check("重复下单幂等(同一payOrderId)", r2.get("payOrderId") == pay_order_id, r2)

r = post("/pay/query", {"shopNum": shop_num, "appId": "mallx-app", "payOrderNum": pay_order_id})
check("4.3.1 订单查询(待支付)", r.get("id") == "0" and r.get("orderState") == 4, r)

pay_time = time.strftime("%Y-%m-%d %H:%M:%S")
cb = {"payOrderId": pay_order_id, "channelOrderNo": "CHTEST" + sfx, "payState": "0", "payTime": pay_time}
r = post("/pay/callback", cb)
check("4.2.2 支付回调成功", r.get("id") == "0", r)

r = post("/pay/callback", cb)
check("重复回调幂等(唯一索引+状态机)", r.get("id") == "0", r)

r = post("/pay/query", {"shopNum": shop_num, "appId": "mallx-app", "payOrderNum": pay_order_id})
check("订单流转为支付成功(state=0)", r.get("orderState") == 0, r)

r = post("/pay/list/mch", {"shopNum": shop_num, "pageNum": 1, "pageSize": 10})
check("4.3.2 商户订单列表分页", r.get("id") == "0" and r.get("total", 0) >= 1, r)

# ---------- 6. 订单流转：支付成功→发货→收货 ----------
print("\n[5] 订单履约")
deadline = time.time() + 75  # 消费者冷启动首条消息可能有数十秒延迟
state = None
while time.time() < deadline:  # 等待 MQ 支付成功消费者驱动订单状态
    r = post("/order/detail", {"orderNum": order_num})
    state = r.get("order", {}).get("orderState")
    if state == 1:
        break
    time.sleep(2)
check("支付成功事件驱动订单待发货(state=1)", state == 1, f"state={state}(MQ未消费则为0)")

r = post("/order/deliver", {"orderNum": order_num, "logisticsCompany": "顺丰",
                            "logisticsNo": "SF" + sfx})
check("商户发货(state=2)", r.get("id") == "0", r)

r = post("/order/confirm", {"orderNum": order_num})
check("确认收货(state=3)", r.get("id") == "0", r)

# ---------- 7. 退款闭环 ----------
print("\n[6] 退款管理模块")
r = post("/refund/create", {"payOrderId": pay_order_id, "refundMoney": "20.00",
                            "refundReason": "部分退货", "shopNum": shop_num})
check("4.4.1 商户发起退款", r.get("id") == "0" and "refundOrderId" in r, r)
refund_order_id = r.get("refundOrderId")

r = post("/refund/create", {"payOrderId": pay_order_id, "refundMoney": "99999.00",
                            "refundReason": "超额", "shopNum": shop_num})
check("超额退款被拒", r.get("id") != "0", r)

r = post("/refund/query", {"refundOrderId": refund_order_id, "shopNum": shop_num})
check("4.4.2 退款查询(待审核=0)", r.get("refundState") == 0, r)

r = post("/admin/refund/audit", {"refundOrderId": refund_order_id, "auditState": 1})
check("平台审核通过→渠道退款", r.get("id") == "0", r)

r = post("/refund/query", {"refundOrderId": refund_order_id, "shopNum": shop_num})
check("退款已完成(state=2)", r.get("refundState") == 2, r)

r = post("/pay/query", {"shopNum": shop_num, "appId": "mallx-app", "payOrderNum": pay_order_id})
check("部分退款后支付单保持支付成功(未退完不流转)", r.get("orderState") == 0, r)

# ---------- 7. 用户端售后：申请退款（含凭证）→ 商户端审核（同意/拒绝/重新申请） ----------
print("\n[6.1] 用户端售后 + 商户端审核")
r = post("/mch/login", {"username": f"test{sfx}", "password": "123456"}, use_sign=False)
mch_token = r.get("token")
check("商户登录获取令牌", bool(mch_token), r)

ev = post("/refund/evidence/upload",
          {"imageBase64": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q=="})
check("凭证上传返回本系统地址", ev.get("id") == "0" and str(ev.get("url", "")).startswith("/uploads/refund/"), ev)

r = post("/refund/apply", {"orderNum": order_num, "userNum": user_num, "refundMoney": "5.00",
                           "refundReason": "商品质量问题：外包装破损", "evidence": [ev.get("url")]})
check("用户端发起退款申请", r.get("id") == "0" and "refundOrderId" in r, r)
user_refund_id = r.get("refundOrderId")

r = post("/refund/apply", {"orderNum": order_num, "userNum": user_num, "refundMoney": "1.00",
                           "refundReason": "重复提交"})
check("同单处理中重复申请被拒", r.get("id") != "0", r)

r = post("/refund/user/list", {"userNum": user_num, "pageNum": 1, "pageSize": 10})
check("用户端退款记录列表", r.get("id") == "0" and any(
    x.get("refundOrderId") == user_refund_id for x in (r.get("records") or [])), r.get("total"))

r = post("/refund/detail", {"refundOrderId": user_refund_id, "userNum": user_num})
check("退款进度详情(待审核=0)", r.get("refund", {}).get("refundState") == 0, r)

r = post("/refund/detail", {"refundOrderId": user_refund_id, "userNum": "U100000000999"})
check("非申请人查看退款单被拒", r.get("id") != "0", r)

r = post("/refund/mch/list", {"shopNum": shop_num, "pageNum": 1, "pageSize": 10, "refundState": 0})
check("商户端待处理退款列表", r.get("id") == "0" and r.get("total", 0) >= 1, r)

r = post("/refund/mch/stats", {"shopNum": shop_num})
check("商户端退款统计", r.get("id") == "0" and r.get("waitCount", 0) >= 1, r)

r = post("/refund/mch/audit", {"refundOrderId": user_refund_id, "auditState": 1, "auditRemark": "无令牌"})
check("无登录令牌审核被拒", r.get("id") != "0", r)

r = post("/refund/mch/audit", {"refundOrderId": user_refund_id, "auditState": 1, "auditRemark": "越权"},
         headers={"X-Mch-Token": admin_token})
check("非商户令牌审核被拒", r.get("id") != "0", r)

r = post("/refund/mch/audit", {"refundOrderId": user_refund_id, "auditState": 1, "auditRemark": "已核实，同意退款"},
         headers={"X-Mch-Token": mch_token})
check("商户同意退款→执行渠道退款", r.get("id") == "0", r)

r = post("/refund/detail", {"refundOrderId": user_refund_id, "userNum": user_num})
refund_rec = r.get("refund", {})
check("两端状态同步为已完成(2)", refund_rec.get("refundState") == 2, refund_rec)
check("处理备注已记录", refund_rec.get("auditRemark") == "已核实，同意退款", refund_rec)
check("渠道退款流水号已写入", bool(refund_rec.get("channelRefundNo")), refund_rec)

# 拒绝流程
r = post("/refund/apply", {"orderNum": order_num, "userNum": user_num, "refundMoney": "2.00",
                           "refundReason": "协商不一致"})
reject_refund_id = r.get("refundOrderId")
check("第二笔退款申请(2.00)", r.get("id") == "0", r)
r = post("/refund/mch/audit", {"refundOrderId": reject_refund_id, "auditState": 2,
                               "auditRemark": "商品已使用，不支持退款"},
         headers={"X-Mch-Token": mch_token})
check("商户拒绝退款", r.get("id") == "0", r)
r = post("/refund/detail", {"refundOrderId": reject_refund_id, "userNum": user_num})
refund_rec = r.get("refund", {})
check("拒绝后状态已拒绝(3)且记录备注", refund_rec.get("refundState") == 3
      and refund_rec.get("auditRemark") == "商品已使用，不支持退款", refund_rec)

# 已拒绝后可重新申请（额度已释放）
r = post("/refund/apply", {"orderNum": order_num, "userNum": user_num, "refundMoney": "2.00",
                           "refundReason": "重新协商退款"})
check("已拒绝后可重新申请", r.get("id") == "0", r)

# ---------- 8. 数据统计 ----------
print("\n[7] 数据统计模块")
r = post("/statis/platform", {})
check("4.6.1 平台统计(总额/笔数/渠道占比/七日)", r.get("id") == "0"
      and "channelRatio" in r and len(r.get("sevenDayData", [])) == 7, r)

r = post("/statis/mch", {"shopNum": shop_num})
check("4.6.2 商户统计(营收/手续费/成功率)", r.get("id") == "0"
      and "totalServiceFee" in r and "successRate" in r, r)

# ---------- 9. 资金管理 ----------
print("\n[8] 资金管理模块")
r = post("/fund/overview", {"shopNum": shop_num})
check("资金总览", r.get("id") == "0" and "balance" in r.get("fund", {}), r)

r = post("/fund/withdraw/create", {"shopNum": shop_num, "money": "10.00"})
check("提现申请", r.get("id") == "0", r)
withdraw_no = r.get("withdrawNo")

r = post("/fund/withdraw/list", {"shopNum": shop_num})
check("提现记录", r.get("id") == "0" and r.get("total", 0) >= 1, r)

r = post("/admin/withdraw/audit", {"withdrawNo": withdraw_no, "state": 1, "remark": "同意"})
check("平台提现审核", r.get("id") == "0", r)

# ---------- 10. 管理端查询 ----------
print("\n[9] 平台管理端")
r = post("/admin/mch/list", {"pageNum": 1, "pageSize": 10})
check("商户列表", r.get("id") == "0", r)
r = post("/admin/pay/list", {"pageNum": 1, "pageSize": 10})
check("全平台支付订单列表", r.get("id") == "0", r)
r = post("/admin/refund/list", {"pageNum": 1, "pageSize": 10})
check("退款列表", r.get("id") == "0", r)
r = post("/admin/log/list", {"pageNum": 1, "pageSize": 10})
check("操作日志", r.get("id") == "0" and r.get("total", 0) >= 1, r)

# ---------- 10.2 管理端各模块删除（仅 ADMIN 可执行） ----------
print("\n[9.2] 管理端各模块删除")

# 商户：新注册商户（无业务数据）可删；有业务数据的商户禁止删
ck, cc = fetch_captcha()
r = post("/mch/register", {"shopName": f"删除测试商铺{sfx}", "shopPhone": "136" + "".join(random.choices(string.digits, k=8)),
                           "shopAddress": "删除测试地址", "username": f"delmch{sfx}", "password": "123456",
                           "captchaKey": ck, "captchaCode": cc})
del_shop = r.get("shopNum")
check("预置待删商户", r.get("id") == "0", r)
r = post("/admin/mch/delete", {"shopNum": del_shop})
check("无登录令牌删除商户被拒", r.get("id") != "0", r)
r = post("/admin/mch/delete", {"shopNum": del_shop}, headers={"X-Admin-Token": admin_token})
check("无业务数据的商户可删除", r.get("id") == "0", r)
r = post("/admin/mch/delete", {"shopNum": shop_num}, headers={"X-Admin-Token": admin_token})
check("有商品/订单的商户禁止删除", r.get("id") != "0", r)

# 商品：新建商品可删；被订单引用的商品禁止删
r = post("/goods/create", {"goodsName": f"删除测试商品{sfx}", "shopNum": "SH417826",
                           "categoryId": 1, "price": "9.90", "stock": 5})
del_goods = r.get("goodsNum")
check("预置待删商品", r.get("id") == "0", r)
r = post("/admin/goods/delete", {"goodsNum": del_goods}, headers={"X-Admin-Token": admin_token})
check("无订单引用的商品可删除", r.get("id") == "0", r)
r = post("/admin/goods/delete", {"goodsNum": "G728415"}, headers={"X-Admin-Token": admin_token})
check("被订单引用的商品禁止删除", r.get("id") != "0", r)

# 支付订单：待支付单可删；支付成功单禁止删
r = post("/pay/create", {"shopNum": "SH417826", "shopName": "星选数码旗舰店", "appId": "mallx-app",
                         "wayCode": "WECHAT", "payMoney": "1.00", "subject": "删除测试支付单",
                         "userNum": "U1000000001", "orderNum": "SO" + sfx + "D"})
del_pay = r.get("payOrderId")
check("预置待删支付单", r.get("id") == "0", r)
r = post("/admin/pay/delete", {"payOrderId": del_pay}, headers={"X-Admin-Token": admin_token})
check("待支付订单可删除", r.get("id") == "0", r)
r = post("/admin/pay/list", {"pageNum": 1, "pageSize": 5, "orderState": 0})
paid = ((r.get("records") or [{}])[0] or {}).get("payOrderId")
r = post("/admin/pay/delete", {"payOrderId": paid}, headers={"X-Admin-Token": admin_token})
check("支付成功订单禁止删除", r.get("id") != "0", r)

# 退款单：审核中禁止删；驳回(失败)后可删
r = post("/pay/create", {"shopNum": "SH417826", "shopName": "星选数码旗舰店", "appId": "mallx-app",
                         "wayCode": "WECHAT", "payMoney": "2.00", "subject": "删除测试退款单",
                         "userNum": "U1000000001", "orderNum": "SO" + sfx + "R"})
pay_r = r.get("payOrderId")
post("/pay/callback", {"payOrderId": pay_r, "channelOrderNo": "CHDEL" + sfx, "payState": "0",
                       "payTime": time.strftime("%Y-%m-%d %H:%M:%S")})
r = post("/refund/create", {"payOrderId": pay_r, "refundMoney": "0.50", "refundReason": "删除测试",
                            "shopNum": "SH417826"})
del_refund = r.get("refundOrderId")
check("预置待删退款单", r.get("id") == "0", r)
r = post("/admin/refund/delete", {"refundOrderId": del_refund}, headers={"X-Admin-Token": admin_token})
check("待审核退款单禁止删除", r.get("id") != "0", r)
r = post("/admin/refund/audit", {"refundOrderId": del_refund, "auditState": 2, "auditRemark": "删除测试驳回"})
check("退款单已驳回", r.get("id") == "0", r)
r = post("/admin/refund/delete", {"refundOrderId": del_refund}, headers={"X-Admin-Token": admin_token})
check("已拒绝退款单可删除", r.get("id") == "0", r)

# 渠道配置：新建测试渠道可删
r = post("/admin/channel/config", {"wayCode": "TESTPAY", "channelName": "删除测试渠道",
                                   "switchStatus": 1, "baseRate": "0.0060"})
check("预置待删渠道", r.get("id") == "0", r)
r = post("/admin/channel/delete", {"wayCode": "TESTPAY"}, headers={"X-Admin-Token": admin_token})
check("渠道配置可删除", r.get("id") == "0", r)

# 提现单：审核中禁止删；驳回后可删
r = post("/fund/withdraw/create", {"shopNum": shop_num, "money": "0.01"})
del_wd = r.get("withdrawNo")
check("预置待删提现单", r.get("id") == "0", r)
r = post("/admin/withdraw/delete", {"withdrawNo": del_wd}, headers={"X-Admin-Token": admin_token})
check("审核中提现单禁止删除", r.get("id") != "0", r)
r = post("/admin/withdraw/audit", {"withdrawNo": del_wd, "state": 2, "remark": "删除测试驳回"})
check("提现单已驳回", r.get("id") == "0", r)
r = post("/admin/withdraw/delete", {"withdrawNo": del_wd}, headers={"X-Admin-Token": admin_token})
check("已驳回提现单可删除", r.get("id") == "0", r)

# 类目：空类目可删；有商品引用的类目禁止删
r = post("/admin/category/save", {"categoryName": f"删除测试类目{sfx}", "parentId": 0, "sort": 99})
del_cat = r.get("categoryId")
check("预置待删类目", r.get("id") == "0", r)
r = post("/admin/category/delete", {"categoryId": del_cat}, headers={"X-Admin-Token": admin_token})
check("空类目可删除", r.get("id") == "0", r)
r = post("/admin/goods/list", {"pageNum": 1, "pageSize": 1})
gcid = ((r.get("records") or [{}])[0] or {}).get("categoryId")
r = post("/admin/category/delete", {"categoryId": gcid}, headers={"X-Admin-Token": admin_token})
check("有商品引用的类目禁止删除", r.get("id") != "0", r)

# 操作日志：单条可删，删除动作自身写入新审计日志
r = post("/admin/log/list", {"pageNum": 1, "pageSize": 1})
log_id = ((r.get("records") or [{}])[0] or {}).get("id")
check("预置待删日志", bool(log_id), r)
r = post("/admin/log/delete", {"id": log_id}, headers={"X-Admin-Token": admin_token})
check("操作日志可删除", r.get("id") == "0", r)
r = post("/admin/log/delete", {"id": log_id}, headers={"X-Admin-Token": admin_token})
check("重复删除被拒", r.get("id") != "0", r)

# ---------- 10.3 全额退完支付单流转（退款扣减商户余额，置于提现用例之后） ----------
print("\n[9.3] 全额退完支付单流转")
r = post("/refund/create", {"payOrderId": pay_order_id, "refundMoney": "90.60",
                            "refundReason": "剩余金额全额退款", "shopNum": shop_num})
check("发起剩余额度退款(90.60)", r.get("id") == "0", r)
r = post("/admin/refund/audit", {"refundOrderId": r.get("refundOrderId"), "auditState": 1})
check("平台审核剩余退款", r.get("id") == "0", r)
r = post("/pay/query", {"shopNum": shop_num, "appId": "mallx-app", "payOrderNum": pay_order_id})
check("全额退完支付单流转已退款(state=2)", r.get("orderState") == 2, r)

# ---------- 11. 网关限流 ----------
print("\n[10] 高并发防护：Redis+Lua 限流")

def burst(i):
    try:
        req = urllib.request.Request(BASE + "/statis/platform", data=b"{}",
                                     headers={"Content-Type": "application/json"}, method="POST")
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.read().decode("utf-8")
    except Exception:
        return ""

with ThreadPoolExecutor(max_workers=30) as pool:
    results = list(pool.map(burst, range(400)))
limited = sum(1 for x in results if "系统繁忙" in x)
print(f"  并发400请求，命中限流 {limited} 次")
check("超限统一返回「系统繁忙，请稍后再试」", limited > 0, "未触发限流，检查网关配置")

print(f"\n========== 测试完成：通过 {PASS} 项，失败 {FAIL} 项 ==========")
sys.exit(0 if FAIL == 0 else 1)
