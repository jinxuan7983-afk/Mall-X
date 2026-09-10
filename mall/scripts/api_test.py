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


def post(path: str, params: dict, use_sign=True) -> dict:
    body = {k: v for k, v in params.items() if v is not None}
    if use_sign:
        body["sign"] = sign_params(params)
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=data,
                                 headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode("utf-8"))
        except Exception:
            return {"id": str(e.code), "message": f"HTTP {e.code}"}


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
print("\n[1] 商户管理模块")
r = post("/mch/register", {
    "shopName": f"联调测试商铺{sfx}", "shopPhone": "138" + sfx[:8].ljust(8, "0"),
    "shopAddress": "测试地址", "username": f"test{sfx}", "password": "123456"})
check("4.1.1 商户注册", r.get("id") == "0" and "shopNum" in r, r)
shop_num = r.get("shopNum")

# 坏签名必须被拒
r = post("/mch/register", {"shopName": "x", "shopPhone": "1", "shopAddress": "x",
                           "username": "bad" + sfx, "password": "1"}, use_sign=False)
check("签名缺失被拒绝", r.get("id") != "0", r)

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
r = post("/user/register", {"username": "u" + sfx, "password": "123456"})
check("用户注册", r.get("id") == "0", r)
user_num = r.get("userNum")

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
check("4.4.2 退款查询(审核中)", r.get("refundState") == 0, r)

r = post("/admin/refund/audit", {"refundOrderId": refund_order_id, "auditState": 1})
check("平台审核通过→渠道退款", r.get("id") == "0", r)

r = post("/refund/query", {"refundOrderId": refund_order_id, "shopNum": shop_num})
check("退款成功(state=1)", r.get("refundState") == 1, r)

r = post("/pay/query", {"shopNum": shop_num, "appId": "mallx-app", "payOrderNum": pay_order_id})
check("支付单流转已退款(state=2)", r.get("orderState") == 2, r)

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
