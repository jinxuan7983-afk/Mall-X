#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验证相关性：/admin/channel/config 保存后 /admin/category/save 是否开始 500"""
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
print("A) 类目保存(渠道配置前) ->", post("/admin/category/save", {"categoryName": "相关A" + sfx, "parentId": 0, "sort": 8}))

ch = post("/admin/channel/list", {})
print("channel/list ->", str(ch)[:200])
alipay = next((c for c in ch.get("records", []) if c.get("wayCode") == "ALIPAY"), None)
if alipay:
    payload = {"wayCode": "ALIPAY", "channelName": alipay.get("channelName"), "switchStatus": alipay.get("switchStatus"),
               "baseRate": "0.0055", "appId": alipay.get("appId") or "", "gatewayUrl": alipay.get("gatewayUrl") or "",
               "privateKey": "", "alipayPublicKey": ""}
    print("B) 渠道配置保存 ->", post("/admin/channel/config", payload))

print("C) 类目保存(渠道配置后) ->", post("/admin/category/save", {"categoryName": "相关C" + sfx, "parentId": 0, "sort": 8}))
time.sleep(2)
print("D) 再来一次类目保存 ->", post("/admin/category/save", {"categoryName": "相关D" + sfx, "parentId": 0, "sort": 8}))
