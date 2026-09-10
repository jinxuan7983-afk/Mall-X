#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""复现管理端三个可疑接口：类目保存 / 退款审核 / 提现审核"""
import hashlib
import json
import time
import urllib.request

GW = "http://127.0.0.1:10010"
SECRET = "mallx-pay-secret-2026"


def post(path, params):
    items = sorted((k, str(v)) for k, v in params.items()
                   if k != "sign" and v is not None and str(v) != "" and not isinstance(v, (dict, list)))
    raw = "&".join(f"{k}={v}" for k, v in items) + f"&key={SECRET}"
    body = dict(params)
    body["sign"] = hashlib.md5(raw.encode()).hexdigest()
    req = urllib.request.Request(GW + path, data=json.dumps(body, ensure_ascii=False).encode(),
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return {"http": e.code, "body": e.read().decode()[:300]}


sfx = str(int(time.time()))[-6:]
print("1) category/save ->", post("/category/save", {"categoryName": "E2E测试类目" + sfx, "parentId": 0, "sort": 9}))

r = post("/admin/refund/list", {"pageNum": 1, "pageSize": 10})
pend = [x for x in r.get("records", []) if x.get("refundState") == 0]
print("2) pending refund:", [(x.get("refundOrderId"), x.get("shopNum")) for x in pend[:2]])
if pend:
    rid = pend[0]["refundOrderId"]
    print("   refund/audit  ->", post("/admin/refund/audit", {"refundOrderId": rid, "auditState": 1}))

r2 = post("/fund/withdraw/list", {"shopNum": "SH1000000001"})
pw = [x for x in r2.get("records", []) if x.get("state") == 0]
print("3) pending withdraw:", [x.get("withdrawNo") for x in pw[:2]])
if pw:
    wd = pw[0]["withdrawNo"]
    print("   withdraw/audit->", post("/admin/withdraw/audit", {"withdrawNo": wd, "state": 1, "remark": "E2E"}))
