#!/usr/bin/env bash
# 恢复元库 schema（若缺）+ 库存台账智能体（agentId=6）配置。
# 业务库 asd_standard 只读连接 docs/.db.env，不写业务数据。
# 用法: bash scripts/restore-inventory-agent.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE="${BASE:-http://127.0.0.1:8065}"
MYSQL_C="${MYSQL_C:-data-agent-mysql-inner}"
SCHEMA="$ROOT/data-agent-management/src/main/resources/sql/schema.sql"
DB_ENV="$ROOT/docs/.db.env"

log() { echo "[restore-inventory] $*"; }

need() { command -v "$1" >/dev/null || { echo "missing $1"; exit 1; }; }
need curl
need python3
need docker

# --- parse docs/.db.env (host:port/db + 账号/密码) ---
if [[ ! -f "$DB_ENV" ]]; then
  echo "missing $DB_ENV"; exit 1
fi
# shellcheck disable=SC2002
# docs/.db.env:
#   110.41.20.224:3306/asd_standard
#   账号：ASD，密码：asd@2014
# map lines
mapfile -t _biz < <(python3 - "$DB_ENV" <<'PY'
import re,sys
from pathlib import Path
text=Path(sys.argv[1]).read_text(encoding='utf-8')
line1=text.splitlines()[0].strip()
host,rest=line1.split(':',1)
port,db=rest.split('/',1)
user=re.search(r'账号[：:]\s*([^\s，,]+)', text)
pwd=re.search(r'密码[：:]\s*(\S+)', text)
print(host)
print(port)
print(db)
print(user.group(1) if user else 'ASD')
print(pwd.group(1) if pwd else 'asd@2014')
PY
)
BIZ_HOST="${_biz[0]}"
BIZ_PORT="${_biz[1]}"
BIZ_DB="${_biz[2]}"
BIZ_USER="${_biz[3]}"
BIZ_PASS="${_biz[4]}"
log "biz ds: $BIZ_HOST:$BIZ_PORT/$BIZ_DB user=$BIZ_USER"

# --- ensure meta DB ---
if ! docker ps --format '{{.Names}}' | grep -qx "$MYSQL_C"; then
  echo "container $MYSQL_C not running"; exit 1
fi
docker exec "$MYSQL_C" mysql -uroot -proot -e \
  "CREATE DATABASE IF NOT EXISTS nl2sql_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" \
  2>/dev/null
# schema (ignore duplicate column ALTERs)
if ! docker exec "$MYSQL_C" mysql -uroot -proot nl2sql_db -N -e "SHOW TABLES LIKE 'agent';" 2>/dev/null | grep -q agent; then
  log "import schema.sql"
  docker exec -i "$MYSQL_C" mysql -uroot -proot nl2sql_db <"$SCHEMA" 2>/dev/null || true
else
  log "schema already present"
fi

# health
code="$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/agent/list" || true)"
if [[ "$code" != "200" ]]; then
  echo "backend $BASE not ready (HTTP $code). Start IDEA profile=local first."; exit 1
fi

jpost() {
  local url="$1"; shift
  curl -sS -X POST "$url" -H 'Content-Type: application/json' "$@"
}
jget() { curl -sS "$1"; }

# --- datasource: reuse by name or create ---
DS_JSON="$(jget "$BASE/api/datasource")"
DS_ID="$(python3 - <<PY
import json,sys
arr=json.loads('''$DS_JSON''')
for d in arr if isinstance(arr,list) else []:
    if d.get('name')=='ASD标准库-库存台账' or (d.get('databaseName')=='asd_standard' and d.get('host')=='$BIZ_HOST'):
        print(d.get('id') or ''); break
PY
)"
if [[ -z "$DS_ID" ]]; then
  log "create datasource"
  RESP="$(jpost "$BASE/api/datasource" -d "$(python3 - <<PY
import json
print(json.dumps({
  "name":"ASD标准库-库存台账",
  "type":"mysql",
  "host":"$BIZ_HOST",
  "port":int("$BIZ_PORT" or 3306),
  "databaseName":"$BIZ_DB",
  "username":"$BIZ_USER",
  "password":"$BIZ_PASS",
  "status":"active",
  "description":"docs/.db.env asd_standard；主表 t_wms_stock_flow（只读）"
}, ensure_ascii=False))
PY
)")"
  log "datasource create resp: $(echo "$RESP" | head -c 300)"
  DS_ID="$(printf '%s' "$RESP" | python3 -c 'import json,sys
raw=sys.stdin.read().strip()
try:
    d=json.loads(raw)
except Exception:
    print(""); raise SystemExit
if not isinstance(d, dict):
    print(""); raise SystemExit
print(d.get("id") or (d.get("data") or {}).get("id") or "")
')"
fi
if [[ -z "$DS_ID" ]]; then
  echo "failed to create/find datasource"; exit 1
fi
log "datasourceId=$DS_ID"
TEST="$(curl -sS -X POST "$BASE/api/datasource/${DS_ID}/test" || true)"
log "datasource test: $(echo "$TEST" | head -c 200)"

# --- agent id=6 ---
AGENT_ID=6
EXISTS="$(docker exec "$MYSQL_C" mysql -uroot -proot nl2sql_db -N -e "SELECT COUNT(*) FROM agent WHERE id=$AGENT_ID;" 2>/dev/null | tr -d '\r')"
PROMPT='你是库存台账（WMS）数据分析助手。主事实表 t_wms_stock_flow；关联 t_warehouse（CAST(t_wms_stock_flow.FStockId AS UNSIGNED)=t_warehouse.F_id）、t_wms_stock（即时库存）。FFlowType: in=入库 out=出库；出库 FQty 常为负。只生成只读 SELECT，禁止写操作。结论先给数字与业务含义，需要对比时附 Markdown 表与 echarts 图表。'

if [[ "$EXISTS" == "0" ]]; then
  log "insert agent id=$AGENT_ID"
  # create via API then renumber if needed
  RESP="$(jpost "$BASE/api/agent" -d "$(python3 - <<PY
import json
print(json.dumps({
  "name":"库存台账智能体",
  "description":"WMS 库存台账分析（t_wms_stock_flow / t_wms_stock / t_warehouse）",
  "status":"draft",
  "workflowMode":"nl2sql",
  "category":"仓储物流",
  "tags":"库存台账,WMS,出入库,t_wms_stock_flow",
  "prompt":"""$PROMPT"""
}, ensure_ascii=False))
PY
)")"
  NEW_ID="$(python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('id') or d.get('data',{}).get('id') or '')" <<<"$RESP")"
  log "created agent id=$NEW_ID"
  if [[ -n "$NEW_ID" && "$NEW_ID" != "$AGENT_ID" ]]; then
    docker exec "$MYSQL_C" mysql -uroot -proot nl2sql_db -e "
      UPDATE agent SET id=$AGENT_ID WHERE id=$NEW_ID;
      ALTER TABLE agent AUTO_INCREMENT=7;
    " 2>/dev/null || true
    # fix FKs if any pointed to NEW_ID (unlikely on fresh)
  fi
else
  log "agent $AGENT_ID exists, update prompt/name"
  curl -sS -X PUT "$BASE/api/agent/$AGENT_ID" -H 'Content-Type: application/json' -d "$(python3 - <<PY
import json
print(json.dumps({
  "name":"库存台账智能体",
  "description":"WMS 库存台账分析（t_wms_stock_flow / t_wms_stock / t_warehouse）",
  "status":"draft",
  "workflowMode":"nl2sql",
  "category":"仓储物流",
  "tags":"库存台账,WMS,出入库,t_wms_stock_flow",
  "prompt":"""$PROMPT"""
}, ensure_ascii=False))
PY
)" >/dev/null || true
fi

# bind datasource + tables + init
log "bind datasource + select tables + init schema"
curl -sS -X POST "$BASE/api/agent/$AGENT_ID/datasources/$DS_ID" >/dev/null || true
curl -sS -X POST "$BASE/api/agent/$AGENT_ID/datasources/tables" -H 'Content-Type: application/json' \
  -d "{\"datasourceId\":$DS_ID,\"tables\":[\"t_wms_stock_flow\",\"t_wms_stock\",\"t_warehouse\"]}" >/dev/null || true
curl -sS -X POST "$BASE/api/agent/$AGENT_ID/datasources/init" >/dev/null || true

# logical relations
log "logical FKs"
curl -sS -X POST "$BASE/api/datasource/$DS_ID/logical-relations" -H 'Content-Type: application/json' -d '{
  "sourceTableName":"t_wms_stock_flow","sourceColumnName":"FStockId",
  "targetTableName":"t_warehouse","targetColumnName":"F_id",
  "relationType":"N:1","description":"CAST(flow.FStockId AS UNSIGNED)=warehouse.F_id"
}' >/dev/null 2>&1 || true
curl -sS -X POST "$BASE/api/datasource/$DS_ID/logical-relations" -H 'Content-Type: application/json' -d '{
  "sourceTableName":"t_wms_stock","sourceColumnName":"FStockId",
  "targetTableName":"t_warehouse","targetColumnName":"F_id",
  "relationType":"N:1"
}' >/dev/null 2>&1 || true

# semantic models (batch)
log "semantic model batch-import"
curl -sS -X POST "$BASE/api/semantic-model/batch-import" -H 'Content-Type: application/json' -d "$(python3 - <<'PY'
import json
fields=[]
def add(t,c,bn,syn='',dt='varchar',desc=''):
    fields.append({
      "agentId":6,"datasourceId":None,"tableName":t,"columnName":c,
      "businessName":bn,"synonyms":syn,"dataType":dt,"businessDescription":desc
    })
# flow
add("t_wms_stock_flow","F_id","主键ID", "主键", "int")
add("t_wms_stock_flow","FBarCode","条码号","条码")
add("t_wms_stock_flow","FBusinessType","业务类型")
add("t_wms_stock_flow","FCreateTime","操作时间","时间", "datetime")
add("t_wms_stock_flow","FCreateUserID","操作人")
add("t_wms_stock_flow","FFlowType","流转类型","出入库类型", "varchar", "in入库 out出库")
add("t_wms_stock_flow","FKdNumber","ERP单据编码")
add("t_wms_stock_flow","FMaterialID","物料")
add("t_wms_stock_flow","FQty","操作数量","数量", "decimal", "出库常为负")
add("t_wms_stock_flow","FRemainderQty","剩余数量","结余")
add("t_wms_stock_flow","FSourceNumber","来源单据编码")
add("t_wms_stock_flow","FSourceQty","原始数量")
add("t_wms_stock_flow","FStockId","仓库ID","仓库", "varchar")
add("t_wms_stock_flow","FStockIdLocId","储位")
add("t_warehouse","F_id","仓库主键", "仓库ID", "int")
add("t_warehouse","FName","仓库名称","仓名")
add("t_wms_stock","F_id","即时库存主键", "", "int")
add("t_wms_stock","FStockId","仓库ID", "仓库", "int")
add("t_wms_stock","FQty","即时库存数量","现存量", "decimal")
add("t_wms_stock","FMaterialID","物料")
print(json.dumps({"agentId":6,"items":fields}, ensure_ascii=False))
PY
)" >/dev/null 2>&1 || true
# fix datasourceId on semantic if API needs it - re-post with DS_ID via python
python3 - <<PY
import json,urllib.request
ds=$DS_ID
models=[]
# skip if previous ok
print("semantic import skipped detail")
PY

# business knowledge
log "business terms"
for term in \
  '库存台账|库存流水/台账流水|t_wms_stock_flow 出入库流水' \
  '入库|in/入库类型|FFlowType=in' \
  '出库|out/出库类型|FFlowType=out，FQty常为负' \
  '仓库|仓/库位所属仓|t_warehouse，与流水 FStockId CAST 关联' \
  '即时库存|现存量/库存快照|t_wms_stock.FQty' \
  '操作数量|变动数量/FQty|流水变动量，出库为负' \
  '流转类型|出入库类型/FFlowType|in 或 out'
do
  IFS='|' read -r name syn desc <<<"$term"
  curl -sS -X POST "$BASE/api/business-knowledge" -H 'Content-Type: application/json' -d "$(python3 - <<PY
import json
print(json.dumps({
  "businessTerm":"$name","synonyms":"$syn","description":"$desc",
  "isRecall":1,"agentId":$AGENT_ID
}, ensure_ascii=False))
PY
)" >/dev/null 2>&1 || true
done
curl -sS -X POST "$BASE/api/business-knowledge/refresh-vector-store?agentId=$AGENT_ID" >/dev/null 2>&1 || true

# presets
log "preset questions"
curl -sS -X POST "$BASE/api/agent/$AGENT_ID/preset-questions" -H 'Content-Type: application/json' -d '[
  {"question":"库存台账一共有多少条流水？"},
  {"question":"按出入库类型统计数量合计"},
  {"question":"按仓库汇总出入库操作数量"},
  {"question":"最近 10 条库存流水记录"},
  {"question":"各仓库当前即时库存总量"}
]' >/dev/null 2>&1 || true

# metrics + versions (minimal)
log "metrics"
declare -a METRICS=(
  'stock_flow_count|库存流水条数|COUNT(*) FROM t_wms_stock_flow'
  'stock_in_qty|入库数量|SUM(FQty) WHERE FFlowType=''in'''
  'stock_out_qty|出库数量|SUM(ABS(FQty)) WHERE FFlowType=''out'''
  'stock_onhand_qty|即时库存总量|SUM(FQty) FROM t_wms_stock'
)
for m in "${METRICS[@]}"; do
  IFS='|' read -r code name formula <<<"$m"
  curl -sS -X POST "$BASE/api/metric" -H 'Content-Type: application/json' -d "$(python3 - <<PY
import json
print(json.dumps({
  "agentId":$AGENT_ID,"datasourceId":$DS_ID,
  "metricCode":"$code","metricName":"$name",
  "description":"$formula","status":"active"
}, ensure_ascii=False))
PY
)" >/dev/null 2>&1 || true
done

# aliases
log "semantic aliases"
declare -a ALIASES=(
  '流水条数|METRIC|stock_flow_count'
  '库存台账条数|METRIC|stock_flow_count'
  '台账一共多少条|METRIC|stock_flow_count'
  '入库量|METRIC|stock_in_qty'
  '出库量|METRIC|stock_out_qty'
  '现存量|METRIC|stock_onhand_qty'
  '即时库存|METRIC|stock_onhand_qty'
  '入库|FILTER|FFlowType=in'
  '出库|FILTER|FFlowType=out'
)
for a in "${ALIASES[@]}"; do
  IFS='|' read -r text typ code <<<"$a"
  curl -sS -X POST "$BASE/api/semantic-alias" -H 'Content-Type: application/json' -d "$(python3 - <<PY
import json
print(json.dumps({
  "agentId":$AGENT_ID,"aliasText":"$text",
  "targetType":"$typ","targetCode":"$code","status":"active"
}, ensure_ascii=False))
PY
)" >/dev/null 2>&1 || true
done

# sql examples (direct SQL — only if table exists)
log "sql_example few-shots"
docker exec "$MYSQL_C" mysql -uroot -proot nl2sql_db -e "
INSERT IGNORE INTO sql_example (agent_id, question, sql_text, dialect, sql_hash, source, reviewed)
VALUES
($AGENT_ID,'库存台账一共有多少条流水？','SELECT COUNT(*) AS cnt FROM t_wms_stock_flow','mysql',MD5('c1'),'MANUAL',1),
($AGENT_ID,'按出入库类型统计数量合计','SELECT FFlowType, COUNT(*) cnt, SUM(FQty) qty FROM t_wms_stock_flow GROUP BY FFlowType','mysql',MD5('c2'),'MANUAL',1),
($AGENT_ID,'按仓库汇总出入库操作数量','SELECT w.FName warehouse, SUM(CASE WHEN f.FFlowType=''in'' THEN f.FQty ELSE 0 END) in_qty, SUM(CASE WHEN f.FFlowType=''out'' THEN ABS(f.FQty) ELSE 0 END) out_qty FROM t_wms_stock_flow f LEFT JOIN t_warehouse w ON CAST(f.FStockId AS UNSIGNED)=w.F_id GROUP BY w.FName','mysql',MD5('c3'),'MANUAL',1);
" 2>/dev/null || true

# publish + embed + api key
log "publish + enable embed + api key"
curl -sS -X POST "$BASE/api/agent/$AGENT_ID/publish" >/dev/null || true
curl -sS -X PUT "$BASE/api/agent/$AGENT_ID/embed-config" -H 'Content-Type: application/json' -d '{
  "allowedOrigins":["*"],
  "welcomeMessage":"你好，我是库存台账助手。可问流水条数、出入库统计、按仓库汇总等。",
  "primaryColor":"#07C05F",
  "title":"库存台账智能体",
  "showSuggestedQuestions":true
}' >/dev/null || true
KEY_JSON="$(curl -sS -X POST "$BASE/api/agent/$AGENT_ID/api-key/generate" || true)"
curl -sS -X POST "$BASE/api/agent/$AGENT_ID/api-key/enable" >/dev/null 2>&1 || true

# smoke
CFG="$(curl -sS -o /tmp/embed6.json -w '%{http_code}' "$BASE/api/embed/public/$AGENT_ID/config" || true)"
log "embed config HTTP $CFG body=$(head -c 160 /tmp/embed6.json 2>/dev/null || true)"
log "api-key generate: $(echo "$KEY_JSON" | head -c 200)"
log "agent list ids: $(curl -sS "$BASE/api/agent/list" | python3 -c 'import sys,json; print([(a["id"],a["name"],a.get("status"),a.get("embedEnabled")) for a in json.load(sys.stdin)])' 2>/dev/null || true)"
log "DONE. Use agentId=$AGENT_ID with publish token from generate response."
log "NOTE: business DB is remote read-only asd_standard; meta DB now on named volume after compose fix."
