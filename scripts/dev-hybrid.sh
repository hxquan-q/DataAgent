#!/usr/bin/env bash
# 混合开发：Docker 只跑 MySQL(+模拟数据源)；后端 IDEA/JRebel；前端 pnpm dev
# 公网入口：nginx-dev-proxy 占用 3301 → 宿主 3000(前端) + 8065(后端)
#
# 用法：
#   bash scripts/dev-hybrid.sh up         # 停 Docker 前后端、MySQL 可达、起 3301 公网代理
#   bash scripts/dev-hybrid.sh frontend   # 启动 pnpm dev :3000
#   bash scripts/dev-hybrid.sh proxy      # 仅（重）启 3301 公网代理
#   bash scripts/dev-hybrid.sh down       # 拆代理、恢复 Docker 前后端
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE="$ROOT/docker-file/docker-compose.yml"
NET="data-agent-network"
PROXY_NAME="data-agent-frontend-dev-proxy"
PROXY_CONF="$ROOT/docker-file/config/nginx-dev-proxy.conf"

cmd="${1:-up}"

ensure_mysql_port() {
  if ss -lntp 2>/dev/null | grep -q ':3306 '; then
    echo "[ok] 127.0.0.1:3306 already listening"
    return 0
  fi
  if ! docker ps --format '{{.Names}}' | grep -qx 'data-agent-mysql-inner'; then
    echo "[info] starting mysql containers..."
    docker compose -f "$COMPOSE" up -d mysql mysql-data postgres-data
  fi
  docker rm -f data-agent-mysql-portfwd >/dev/null 2>&1 || true
  docker run -d --name data-agent-mysql-portfwd --network "$NET" \
    -p 3306:3306 alpine/socat \
    TCP-LISTEN:3306,fork,reuseaddr TCP:data-agent-mysql-inner:3306 >/dev/null
  echo "[ok] socat: host:3306 -> data-agent-mysql-inner:3306 (no recreate, data safe)"
}

# 业务模拟库：宿主 IDEA 无法解析 mysql-data/postgres-data，需 portfwd + 改 datasource 表
ensure_business_ports() {
  docker compose -f "$COMPOSE" up -d mysql-data postgres-data >/dev/null 2>&1 || true
  docker rm -f data-agent-mysql-data-portfwd data-agent-pg-data-portfwd >/dev/null 2>&1 || true
  docker run -d --name data-agent-mysql-data-portfwd --network "$NET" \
    -p 3307:3307 alpine/socat \
    TCP-LISTEN:3307,fork,reuseaddr TCP:mysql-data:3306 >/dev/null
  docker run -d --name data-agent-pg-data-portfwd --network "$NET" \
    -p 5433:5433 alpine/socat \
    TCP-LISTEN:5433,fork,reuseaddr TCP:postgres-data:5432 >/dev/null
  echo "[ok] socat: host:3307 -> mysql-data:3306 ; host:5433 -> postgres-data:5432"
}

# mode=host → IDEA 用 127.0.0.1；mode=docker → 容器后端用内网名
switch_datasource_hosts() {
  local mode="${1:-host}"
  if ! docker ps --format '{{.Names}}' | grep -qx 'data-agent-mysql-inner'; then
    echo "[warn] data-agent-mysql-inner not running; skip datasource host switch"
    return 0
  fi
  if [[ "$mode" == "host" ]]; then
    docker exec data-agent-mysql-inner mysql -uroot -proot nl2sql_db -e "
UPDATE datasource SET host='127.0.0.1', port=3307,
  connection_url=REPLACE(REPLACE(IFNULL(connection_url,''), 'mysql-data:3306', '127.0.0.1:3307'), 'mysql-data', '127.0.0.1')
WHERE host IN ('mysql-data','127.0.0.1') AND type='mysql' AND database_name IN ('product_db','enterprise_db');
UPDATE datasource SET host='127.0.0.1', port=5433,
  connection_url=REPLACE(REPLACE(IFNULL(connection_url,''), 'postgres-data:5432', '127.0.0.1:5433'), 'postgres-data', '127.0.0.1')
WHERE (host IN ('postgres-data','127.0.0.1') AND type='postgresql' AND database_name IN ('data_warehouse','china_population_db'))
   OR (host='postgres-data');
" >/dev/null 2>&1 || true
    echo "[ok] datasource hosts → 127.0.0.1:3307/5433 (IDEA hybrid)"
  else
    docker exec data-agent-mysql-inner mysql -uroot -proot nl2sql_db -e "
UPDATE datasource SET host='mysql-data', port=3306,
  connection_url=REPLACE(REPLACE(IFNULL(connection_url,''), '127.0.0.1:3307', 'mysql-data:3306'), '127.0.0.1', 'mysql-data')
WHERE host='127.0.0.1' AND port=3307 AND type='mysql';
UPDATE datasource SET host='postgres-data', port=5432,
  connection_url=REPLACE(REPLACE(IFNULL(connection_url,''), '127.0.0.1:5433', 'postgres-data:5432'), '127.0.0.1', 'postgres-data')
WHERE host='127.0.0.1' AND port=5433 AND type='postgresql';
" >/dev/null 2>&1 || true
    echo "[ok] datasource hosts → mysql-data/postgres-data (Docker backend)"
  fi
}

ensure_public_proxy() {
  # 3301 公网入口 → 127.0.0.1:3000 + :8065（--network host）
  docker rm -f "$PROXY_NAME" >/dev/null 2>&1 || true
  # 确保 3301 未被旧 frontend 占用
  if ss -lntp 2>/dev/null | grep -q ':3301 '; then
    echo "[warn] :3301 already in use; stop whatever holds it first"
    ss -lntp 2>/dev/null | grep 3301 || true
    return 1
  fi
  docker run -d --name "$PROXY_NAME" --network host \
    -v "$PROXY_CONF:/etc/nginx/nginx.conf:ro" \
    nginx:alpine >/dev/null
  echo "[ok] public proxy $PROXY_NAME on :3301 → Nuxt:3000 + API:8065"
}

case "$cmd" in
  up)
    echo "==> hybrid dev UP"
    docker stop data-agent-backend data-agent-frontend 2>/dev/null || true
    echo "[ok] stopped data-agent-backend / data-agent-frontend (8065/3301 free)"
    ensure_mysql_port
    ensure_business_ports
    switch_datasource_hosts host
    ensure_public_proxy
    PUBLIC_IP="$(curl -s --max-time 2 ifconfig.me 2>/dev/null || echo '183.36.30.83')"
    cat <<EOF

------------------------------------------------------------
公网访问（推荐，与原先 3301 一致）
  http://${PUBLIC_IP}:3301
  http://${PUBLIC_IP}:3301/system/embed
  （nginx 反代：页面→3000，/api→8065）

本机访问
  前端直连: http://localhost:3000
  后端:     http://localhost:8065

IDEA + JRebel 后端
  Open: $ROOT
  Main: com.alibaba.cloud.ai.dataagent.DataAgentApplication
  Profiles: local   |  JDK 17  |  端口 8065
  验证: curl http://localhost:8065/api/agent/list
  数据源测连: curl -X POST http://localhost:8065/api/datasource/3/test

前端（需另开终端，若未在跑）
  bash scripts/dev-hybrid.sh frontend
  # 或: cd data-agent-frontend-nuxt && pnpm dev --host 0.0.0.0 --port 3000

MySQL 元库
  127.0.0.1:3306 / nl2sql_db / root / root
业务模拟库（IDEA 用，hybrid 已改 datasource 表）
  MySQL  127.0.0.1:3307 → mysql-data
  PG     127.0.0.1:5433 → postgres-data
------------------------------------------------------------
EOF
    ;;
  proxy)
    ensure_public_proxy
    ;;
  frontend)
    cd "$ROOT/data-agent-frontend-nuxt"
    exec pnpm dev --host 0.0.0.0 --port 3000
    ;;
  down)
    echo "==> hybrid dev DOWN — restore Docker backend/frontend"
    switch_datasource_hosts docker
    docker rm -f "$PROXY_NAME" data-agent-mysql-portfwd \
      data-agent-mysql-data-portfwd data-agent-pg-data-portfwd 2>/dev/null || true
    docker compose -f "$COMPOSE" up -d --no-build backend frontend
    echo "[ok] backend:8065 frontend:3301 up (images unchanged; datasource hosts restored)"
    ;;
  *)
    echo "usage: $0 {up|down|frontend|proxy}"
    exit 1
    ;;
esac
