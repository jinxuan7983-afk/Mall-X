#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""校验每个分类下的在售商品数"""
import hashlib
import json
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
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read().decode())


cats = post("/category/list", {}) if False else json.loads(
    urllib.request.urlopen(GW + "/category/list", timeout=15).read().decode())
print("分类列表:", [(c["id"], c["categoryName"]) for c in cats.get("records", [])])
grand = 0
for c in cats.get("records", []):
    r = post("/goods/list", {"pageNum": 1, "pageSize": 20, "categoryId": c["id"]})
    names = [x["goodsName"] for x in r.get("records", [])]
    grand += r.get("total", 0)
    print(f"  {c['categoryName']}(id={c['id']}): {r.get('total')} 件 {names}")
r = post("/goods/list", {"pageNum": 1, "pageSize": 1})
print("全站在售:", r.get("total"), "件 | 分类合计:", grand)
