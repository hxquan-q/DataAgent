#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from __future__ import annotations
import json, sys, time, urllib.error, urllib.parse, urllib.request
BASE = "http://127.0.0.1:8065"
AGENT_ID = 2

def req(method, path, data=None, query=None, timeout=180):
    url = BASE + path
    if query:
        url += "?" + urllib.parse.urlencode(query)
    body = None
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if data is not None:
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
            if not raw:
                return None
            try:
                return json.loads(raw)
            except json.JSONDecodeError:
                return raw
    except urllib.error.HTTPError as exc:
        err = exc.read().decode("utf-8", errors="ignore")
        raise RuntimeError(f"HTTP {exc.code} {method} {path}: {err[:800]}") from exc

def pretty(obj):
    return json.dumps(obj, ensure_ascii=False, indent=2)

def update_general_settings():
    print("=== 1. General settings ===")
    agent = req("GET", f"/api/agent/{AGENT_ID}")
    payload = {
        "id": AGENT_ID,
        "name": "销售数据分析智能体",
        "description": "专注于商品库销售数据、订单、用户与业务指标（GMV/客单价/复购率）分析。已配置语义模型、业务知识、提示词与示例问答。",
        "avatar": agent.get("avatar") or "/avatars/sales-agent.png",
        "status": "published",
        "prompt": "你是销售数据分析专家，面向 product_db 商品业务库。优先使用已配置的语义模型与业务知识。统计销售额/GMV 时使用实付金额 price2，并过滤 order_status IN (1,2)。生成 SQL 必须可执行，结果用简洁中文解释并给出关键指标。",
        "category": "业务分析",
        "tags": "销售分析,业务指标,客户分析,GMV,示例就绪",
        "adminId": agent.get("adminId", 2100246635),
        "apiKeyEnabled": agent.get("apiKeyEnabled", 0),
    }
    print(pretty(req("PUT", f"/api/agent/{AGENT_ID}", payload))[:500])

def setup_preset_questions():
    print("=== 2. Preset questions ===")
    existing = req("GET", f"/api/agent/{AGENT_ID}/preset-questions") or []
    if isinstance(existing, dict):
        existing = existing.get("data") or []
    for item in existing:
        qid = item.get("id")
        if qid is not None:
            try:
                req("DELETE", f"/api/agent/{AGENT_ID}/preset-questions/{qid}")
            except Exception as exc:
                print("delete preset failed:", exc)
    questions = [
        "查询有多少个用户和多少个商品",
        "统计各订单状态的订单数量",
        "计算已支付和已发货订单的总GMV",
        "按用户统计累计消费金额 Top 5",
        "查询高价值用户有哪些",
    ]
    try:
        payload = [{"question": q, "sortOrder": i + 1, "isActive": True} for i, q in enumerate(questions)]
        print("batch presets:", pretty(req("POST", f"/api/agent/{AGENT_ID}/preset-questions", payload))[:400])
        return
    except Exception as exc:
        print("batch preset failed, fallback single:", exc)
    for i, question in enumerate(questions):
        payload = {"question": question, "sortOrder": i + 1, "isActive": True}
        print(question, "->", pretty(req("POST", f"/api/agent/{AGENT_ID}/preset-questions", payload))[:200])

def setup_business_knowledge():
    print("=== 3. Business knowledge ===")
    existing = req("GET", "/api/business-knowledge", query={"agentId": AGENT_ID})
    for item in (existing or {}).get("data") or []:
        try:
            req("DELETE", f"/api/business-knowledge/{item['id']}")
        except Exception as exc:
            print("delete biz failed:", exc)
    items = [
        {"agentId": AGENT_ID, "businessTerm": "GMV", "description": "GMV（商品交易总额）= 订单表 orders 中 order_status 为 1(已支付) 或 2(已发货) 的 price2 实付金额总和，不扣除退款。不要使用 price 原价字段。", "synonyms": "流水,交易额,全站销售额,成交额,销售总额", "isRecall": True},
        {"agentId": AGENT_ID, "businessTerm": "客单价", "description": "客单价 = 有效订单总实付金额 SUM(price2) / 有效订单数 COUNT(*)，有效订单指 order_status IN (1,2)。", "synonyms": "平均订单金额,单均价,AOV", "isRecall": True},
        {"agentId": AGENT_ID, "businessTerm": "高价值用户", "description": "高价值用户（VIP）：累计消费金额>=10000，或累计有效订单数>=10，或用户等级 user_level 为 2(金卡)/3(钻石)。累计消费使用 orders.price2 且 order_status IN (1,2)。", "synonyms": "VIP用户,高潜客户,重点客户,核心用户", "isRecall": True},
        {"agentId": AGENT_ID, "businessTerm": "复购率", "description": "复购率 = 有效购买次数>=2 的去重用户数 / 有效购买去重用户数。有效购买订单 order_status IN (1,2)。", "synonyms": "再次购买率,回购率", "isRecall": True},
    ]
    for item in items:
        print(item["businessTerm"], "->", pretty(req("POST", "/api/business-knowledge", item))[:300])

def setup_semantic_model():
    print("=== 4. Semantic model ===")
    existing = req("GET", "/api/semantic-model", query={"agentId": AGENT_ID})
    for item in (existing or {}).get("data") or []:
        try:
            req("DELETE", f"/api/semantic-model/{item['id']}")
        except Exception as exc:
            print("delete semantic failed:", exc)
    items = [
        {"tableName": "users", "columnName": "id", "businessName": "用户ID", "synonyms": "用户编号,会员ID,客户ID", "dataType": "bigint", "businessDescription": "用户主键"},
        {"tableName": "users", "columnName": "username", "businessName": "用户名", "synonyms": "账号,登录名,会员名", "dataType": "varchar", "businessDescription": "用户登录名"},
        {"tableName": "users", "columnName": "email", "businessName": "邮箱", "synonyms": "电子邮箱,邮件地址", "dataType": "varchar", "businessDescription": "用户邮箱"},
        {"tableName": "users", "columnName": "user_level", "businessName": "用户等级", "synonyms": "会员等级,VIP等级", "dataType": "int", "businessDescription": "用户等级：0普通,1银卡,2金卡,3钻石"},
        {"tableName": "products", "columnName": "id", "businessName": "商品ID", "synonyms": "产品ID,SKU编号", "dataType": "bigint", "businessDescription": "商品主键"},
        {"tableName": "products", "columnName": "name", "businessName": "商品名称", "synonyms": "产品名,商品名,品名", "dataType": "varchar", "businessDescription": "商品名称"},
        {"tableName": "products", "columnName": "price", "businessName": "商品价格", "synonyms": "售价,单价,标价", "dataType": "decimal", "businessDescription": "商品标价"},
        {"tableName": "products", "columnName": "stock", "businessName": "库存数量", "synonyms": "库存,存量,余量", "dataType": "int", "businessDescription": "当前库存"},
        {"tableName": "orders", "columnName": "id", "businessName": "订单ID", "synonyms": "订单编号,order_id", "dataType": "bigint", "businessDescription": "订单主键"},
        {"tableName": "orders", "columnName": "user_id", "businessName": "下单用户ID", "synonyms": "购买用户,客户编号", "dataType": "bigint", "businessDescription": "关联 users.id"},
        {"tableName": "orders", "columnName": "product_id", "businessName": "下单商品ID", "synonyms": "购买商品,产品编号", "dataType": "bigint", "businessDescription": "关联 products.id"},
        {"tableName": "orders", "columnName": "price", "businessName": "订单原价", "synonyms": "原价,标价金额", "dataType": "decimal", "businessDescription": "订单原价，统计GMV时不要用此字段"},
        {"tableName": "orders", "columnName": "price2", "businessName": "实付金额", "synonyms": "成交价,支付金额,订单金额,销售额,amount", "dataType": "decimal", "businessDescription": "用户实付金额，统计销售额/GMV必须用此字段"},
        {"tableName": "orders", "columnName": "order_status", "businessName": "订单状态", "synonyms": "状态,支付状态", "dataType": "int", "businessDescription": "0待支付,1已支付,2已发货,3已取消,4退款中。计算GMV通常只统计1和2"},
        {"tableName": "orders", "columnName": "create_time", "businessName": "下单时间", "synonyms": "创建时间,订单时间,成交时间", "dataType": "datetime", "businessDescription": "订单创建时间"},
    ]
    for endpoint in ["/api/semantic-model/batch-import", "/api/semantic-model/batch"]:
        try:
            payload = {"agentId": AGENT_ID, "items": items}
            print(endpoint, "->", pretty(req("POST", endpoint, payload))[:500])
            return
        except Exception as exc:
            print(endpoint, "failed:", exc)
    for item in items:
        payload = {"agentId": AGENT_ID, **item}
        print(f"{item['tableName']}.{item['columnName']}", "->", pretty(req("POST", "/api/semantic-model", payload))[:200])

def setup_prompt_config():
    print("=== 5. Prompt config ===")
    prompts = [
        {"name": "销售分析-规划增强", "promptType": "planner", "agentId": AGENT_ID, "optimizationPrompt": "面向销售数据分析场景：优先围绕 users/products/orders 规划步骤；涉及 GMV/客单价/复购率/高价值用户时必须引用业务知识定义；明确过滤 order_status IN (1,2) 与使用 price2。", "enabled": True, "description": "销售智能体规划节点增强提示", "priority": 100, "displayOrder": 1},
        {"name": "销售分析-SQL增强", "promptType": "sql-generator", "agentId": AGENT_ID, "optimizationPrompt": "生成 MySQL SQL 时遵守：1) 销售额/GMV 用 orders.price2；2) 有效订单 order_status IN (1,2)；3) 字段名与语义模型一致；4) 只输出可执行 SQL，避免使用不存在的表/字段。", "enabled": True, "description": "销售智能体 SQL 生成增强提示", "priority": 100, "displayOrder": 1},
        {"name": "销售分析-报告增强", "promptType": "report-generator", "agentId": AGENT_ID, "optimizationPrompt": "用简洁中文总结销售洞察：先给关键数字，再给对比与业务建议；避免空话，突出 GMV、订单量、用户价值等指标。", "enabled": True, "description": "销售智能体报告生成增强提示", "priority": 100, "displayOrder": 1},
        {"name": "销售分析-改写增强", "promptType": "rewrite", "agentId": AGENT_ID, "optimizationPrompt": "将口语问题改写为清晰的数据分析查询，保留时间范围、指标与维度，不改变用户意图。", "enabled": True, "description": "销售智能体问题改写增强提示", "priority": 100, "displayOrder": 1},
        {"name": "销售分析-Python增强", "promptType": "python-generator", "agentId": AGENT_ID, "optimizationPrompt": "基于查询结果做聚合、排序和简单可视化数据准备，代码需可直接执行并输出可读结论。", "enabled": True, "description": "销售智能体 Python 分析增强提示", "priority": 100, "displayOrder": 1},
    ]
    for prompt in prompts:
        print(prompt["promptType"], "->", pretty(req("POST", "/api/prompt-config/save", prompt))[:300])

def retry_embeddings():
    print("=== 6. Retry embeddings ===")
    knowledge = req("POST", "/api/agent-knowledge/query/page", {"pageNum": 1, "pageSize": 50, "agentId": AGENT_ID})
    for item in (knowledge or {}).get("data") or []:
        kid = item["id"]
        for method in ("POST", "GET"):
            try:
                print("retry knowledge", method, kid, req(method, f"/api/agent-knowledge/retry-embedding/{kid}"))
                break
            except Exception as exc:
                print("retry knowledge fail", method, kid, exc)
    biz = req("GET", "/api/business-knowledge", query={"agentId": AGENT_ID})
    for item in (biz or {}).get("data") or []:
        bid = item["id"]
        for method in ("POST", "GET"):
            try:
                print("retry biz", method, bid, req(method, f"/api/business-knowledge/retry-embedding/{bid}"))
                break
            except Exception as exc:
                print("retry biz fail", method, bid, exc)
    try:
        print("refresh vector", req("POST", "/api/business-knowledge/refresh-vector-store"))
    except Exception as exc:
        print("refresh vector failed:", exc)

def verify():
    print("=== 7. Verify ===")
    print("agent:", pretty(req("GET", f"/api/agent/{AGENT_ID}"))[:500])
    print("biz:", pretty(req("GET", "/api/business-knowledge", query={"agentId": AGENT_ID}))[:1000])
    print("semantic:", pretty(req("GET", "/api/semantic-model", query={"agentId": AGENT_ID}))[:1000])
    print("prompt:", pretty(req("GET", "/api/prompt-config/list"))[:1000])
    print("preset:", pretty(req("GET", f"/api/agent/{AGENT_ID}/preset-questions"))[:1000])
    print("knowledge:", pretty(req("POST", "/api/agent-knowledge/query/page", {"pageNum": 1, "pageSize": 20, "agentId": AGENT_ID}))[:1000])

def run_sample_chat():
    print("=== 8. Sample chat ===")
    session = req("POST", f"/api/agent/{AGENT_ID}/sessions", {"title": "示例对话-数据问答配置验证"})
    print("session:", pretty(session)[:400])
    thread_id = session.get("id") or (session.get("data") or {}).get("id")
    if not thread_id:
        raise RuntimeError(f"cannot get session id: {session}")
    query = "查询有多少个用户和多少个商品"
    encoded = urllib.parse.quote(query)
    candidates = [
        f"{BASE}/api/stream/search?agentId={AGENT_ID}&threadId={thread_id}&query={encoded}",
        f"{BASE}/api/agent/{AGENT_ID}/sessions/stream?threadId={thread_id}&query={encoded}",
    ]
    events, texts_by_node = [], {}
    last_err = None
    for url in candidates:
        print("try stream:", url)
        try:
            request = urllib.request.Request(url, method="GET", headers={"Accept": "text/event-stream"})
            with urllib.request.urlopen(request, timeout=300) as response:
                for raw_line in response:
                    line = raw_line.decode("utf-8", errors="ignore").strip()
                    if not line.startswith("data:"):
                        continue
                    payload = line[5:].strip()
                    if not payload or payload == "[DONE]":
                        continue
                    try:
                        event = json.loads(payload)
                    except json.JSONDecodeError:
                        continue
                    events.append(event)
                    node = event.get("nodeName") or event.get("node") or "unknown"
                    text = event.get("text") or ""
                    if text:
                        texts_by_node[node] = texts_by_node.get(node, "") + text
            if events:
                break
        except Exception as exc:
            last_err = exc
            print("stream failed:", exc)
    if not events and last_err:
        raise RuntimeError(f"sample chat failed: {last_err}")
    print("event_count:", len(events))
    print("nodes:", list(texts_by_node.keys()))
    for node, text in texts_by_node.items():
        print(f"\n## {node}\n{text[:1500]}")
    out_path = "/tmp/dataagent_sample_chat.json"
    with open(out_path, "w", encoding="utf-8") as fh:
        json.dump({"threadId": thread_id, "query": query, "eventCount": len(events), "textsByNode": texts_by_node, "eventsTail": events[-20:]}, fh, ensure_ascii=False, indent=2)
    print("saved:", out_path)

def main():
    print("ping:", req("GET", "/echo/ok"))
    update_general_settings()
    setup_preset_questions()
    setup_business_knowledge()
    setup_semantic_model()
    setup_prompt_config()
    retry_embeddings()
    time.sleep(2)
    verify()
    run_sample_chat()
    print("ALL DONE")
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print("FAILED:", exc, file=sys.stderr)
        raise
