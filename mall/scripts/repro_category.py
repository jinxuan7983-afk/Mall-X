#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""用正确路径 /admin/category/save 复现类目保存"""
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
print("数字sort ->", post("/admin/category/save", {"categoryName": "E2E类目A" + sfx, "parentId": 0, "sort": 9}))
print("字符串sort ->", post("/admin/category/save", {"categoryName": "E2E类目B" + sfx, "parentId": 0, "sort": "9"}))
print("无sort   ->", post("/admin/category/save", {"categoryName": "E2E类目C" + sfx, "parentId": 0}))
