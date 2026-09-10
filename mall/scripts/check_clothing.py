#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验证服饰分类商品：带签名请求 /goods/list?categoryId=3"""
import hashlib
import json
import sys
import urllib.request

BASE = "http://127.0.0.1:10010"
SECRET = "mallx-pay-secret-2026"


def sign_params(params):
    items = sorted((k, str(v)) for k, v in params.items()
                   if k != "sign" and v is not None and str(v) != ""
                   and not isinstance(v, (dict, list)))
    raw = "&".join(f"{k}={v}" for k, v in items) + f"&key={SECRET}"
    return hashlib.md5(raw.encode("utf-8")).hexdigest()


def post(path, params, use_sign=True):
    body = {k: v for k, v in params.items() if v is not None}
    if use_sign:
        body["sign"] = sign_params(params)
    req = urllib.request.Request(BASE + path, data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
                                 headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        return {"id": "-1", "message": str(e)}


r = post("/goods/list", {"pageNum": 1, "pageSize": 10, "categoryId": 3})
print("categoryId=3 ->", json.dumps(r, ensure_ascii=False)[:500])
names = [x.get("goodsName") for x in r.get("records", [])] if isinstance(r.get("records"), list) else r.get("records")
ok = r.get("id") == "0" and names and len(names) > 0
print("RESULT:", "PASS" if ok else "FAIL", "| 服饰商品:", names)

r2 = post("/goods/list", {"pageNum": 1, "pageSize": 20})
total = r2.get("total")
print("全部分类 total =", total, "(期望 8)")
