#!/usr/bin/env bash
# 混合开发：数据平面稳态 + 开发平面热部署
#   Docker 只跑 MySQL 元库(+模拟数据源) + 端口转发
#   后端 IDEA/JRebel :8065；前端 pnpm dev :3000
#   公网入口：nginx-dev-proxy :3301 → 宿主 3000 + 8065
#
# 数据安全（默认）：
#   - 不 recreate mysql-inner
#   - 不改元库任何表（含 datasource.host）
#   - 不执行 SQL_INIT
# 可选写库（仅全栈 Docker 演示需要业务源 host 内网名时）：
#   WRITE_HOSTS=1 bash scripts/dev-hybrid.sh up|down
#
# 用法：
#   bash scripts/dev-hybrid.sh up         # 停 Docker 前后端、起转发与 3301 代理
#   bash scripts/dev-hybrid.sh frontend   # 启动 pnpm dev :3000
#   bash scripts/dev-hybrid.sh proxy      # 仅（重）启 3301 公网代理
#   bash scripts/dev-hybrid.sh status     # 检查端口/容器/后端
#   bash scripts/dev-hybrid.sh down       # 拆代理；可选起 Docker 前后端
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE="$ROOT/docker-file/docker-compose.yml"
NET="data-agent-network"
PROXY_NAME="data-agent-frontend-dev-proxy"
PROXY_CONF="$ROOT/docker-file/config/nginx-dev-proxy.conf"
WRITE_HOSTS="${WRITE_HOSTS:-0}"

cmd="${1:-up}"

log() { echo "[hybrid] $*"; }
warn() { echo "[hybrid][warn] $*" >&2; }

port_listening() {
  ss -lntp 2>/dev/null | grep -q ":$1 "
}

ensure_mysql_port() {
  if port_listening 3306; then
    log "ok: 127.0.0.1:3306 already listening"
    return 0
  fi
  if ! docker ps --format '{{.Names}}' | grep -qx 'data-agent-mysql-inner'; then
    log "info: starting data plane (mysql + simulators), never recreates existing volumes"
    docker compose -f "$COMPOSE" up -d mysql mysql-data postgres-data
  fi
  # 仅当容器未映射 3306 时用 socat（不 recreate mysql）
  docker rm -f data-agent-mysql-portfwd >/dev/null 2>&1 || true
  docker run -d --name data-agent-mysql-portfwd --network "$NET" \
    -p 3306:3306 alpine/socat \
    TCP-LISTEN:3306,fork,reuseaddr TCP:data-agent-mysql-inner:3306 >/dev/null
  log "ok: socat host:3306 -> data-agent-mysql-inner:3306 (no recreate, data safe)"
}

# 业务模拟库：宿主 IDEA 用 127.0.0.1:3307/5433 访问
ensure_business_ports() {
  docker compose -f "$COMPOSE" up -d mysql-data postgres-data >/dev/null 2>&1 || true
  docker rm -f data-agent-mysql-data-portfwd data-agent-pg-data-portfwd >/dev/null 2>&1 || true
  docker run -d --name data-agent-mysql-data-portfwd --network "$NET" \
    -p 3307:3307 alpine/socat \
    TCP-LISTEN:3307,fork,reuseaddr TCP:mysql-data:3306 >/dev/null
  docker run -d --name data-agent-pg-data-portfwd --network "$NET" \
    -p 5433:5433 alpine/socat \
    TCP-LISTEN:5433,fork,reuseaddr TCP:postgres-data:5432 >/dev/null
  log "ok: socat host:3307->mysql-data:3306 ; host:5433->postgres-data:5432"
}

# 可选：写元库 datasource host（默认关闭）
# mode=host → 127.0.0.1:3307/5433；mode=docker → 内网服务名
switch_datasource_hosts() {
  local mode="${1:-host}"
  if [[ "$WRITE_HOSTS" != "1" ]]; then
    log "skip: datasource host rewrite (set WRITE_HOSTS=1 to enable; default leaves meta DB untouched)"
    return 0
  fi
  if ! docker ps --format '{{.Names}}' | grep -qx 'data-agent-mysql-inner'; then
    warn "data-agent-mysql-inner not running; skip host switch"
    return 0
  fi
  warn "WRITE_HOSTS=1 → updating meta DB datasource hosts (mode=$mode)"
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
    log "ok: datasource hosts → 127.0.0.1:3307/5433"
  else
    docker exec data-agent-mysql-inner mysql -uroot -proot nl2sql_db -e "
UPDATE datasource SET host='mysql-data', port=3306,
  connection_url=REPLACE(REPLACE(IFNULL(connection_url,''), '127.0.0.1:3307', 'mysql-data:3306'), '127.0.0.1', 'mysql-data')
WHERE host='127.0.0.1' AND port=3307 AND type='mysql';
UPDATE datasource SET host='postgres-data', port=5432,
  connection_url=REPLACE(REPLACE(IFNULL(connection_url,''), '127.0.0.1:5433', 'postgres-data:5432'), '127.0.0.1', 'postgres-data')
WHERE host='127.0.0.1' AND port=5433 AND type='postgresql';
" >/dev/null 2>&1 || true
    log "ok: datasource hosts → mysql-data/postgres-data"
  fi
}

ensure_public_proxy() {
  docker rm -f "$PROXY_NAME" >/dev/null 2>&1 || true
  if port_listening 3301; then
    # 可能是旧 frontend；up 路径应已 stop。此处仍检测。
    warn ":3301 already in use"
    ss -lntp 2>/dev/null | grep 3301 || true
    return 1
  fi
  docker run -d --name "$PROXY_NAME" --network host \
    -v "$PROXY_CONF:/etc/nginx/nginx.conf:ro" \
    nginx:alpine >/dev/null
  log "ok: public proxy $PROXY_NAME on :3301 → Nuxt:3000 + API:8065"
}

print_status() {
  echo "==> hybrid status"
  docker ps --format 'table {{.Names}}\t{{.Status}}' | grep -E 'NAMES|data-agent' || true
  echo "--- ports ---"
  ss -lntp 2>/dev/null | grep -E ':(8065|3301|3000|3306|3307|5433)\s' || true
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 http://127.0.0.1:8065/api/agent/list || true)"
  echo "backend /api/agent/list → HTTP ${code:-down}"
  if docker ps --format '{{.Names}}' | grep -qx 'data-agent-mysql-inner'; then
    docker exec data-agent-mysql-inner mysql -uroot -proot nl2sql_db -N -e \
      "SELECT CONCAT('agent=',COUNT(*)) FROM agent; SELECT CONCAT('datasource=',COUNT(*)) FROM datasource;" \
      2>/dev/null || true
  fi
}

case "$cmd" in
  up)
    echo "==> hybrid dev UP (meta DB untouched by default)"
    docker stop data-agent-backend data-agent-frontend 2>/dev/null || true
    log "ok: stopped data-agent-backend / data-agent-frontend (free 8065/3301 if held by Docker)"
    if port_listening 8065; then
      log "info: :8065 already listening (expect IDEA+JRebel)"
    else
      log "info: :8065 free — start IDEA DataAgent-local (profiles=local, JDK17)"
    fi
    ensure_mysql_port
    ensure_business_ports
    switch_datasource_hosts host
    ensure_public_proxy
    PUBLIC_IP="$(curl -s --max-time 2 ifconfig.me 2>/dev/null || echo '183.36.30.83')"
    cat <<EOF

------------------------------------------------------------
公网: http://${PUBLIC_IP}:3301  （页面→:3000  API→:8065）
本机: 前端 http://localhost:3000  后端 http://localhost:8065

IDEA + JRebel
  Run: DataAgent-local  |  Profiles: local  |  JDK 17  |  :8065
  验证: curl http://localhost:8065/api/agent/list
  sql.init: never（application-local.yml）— Restart 不写库

前端
  bash scripts/dev-hybrid.sh frontend

数据平面（勿 recreate）
  元库  127.0.0.1:3306 / nl2sql_db / root/root
  业务  127.0.0.1:3307 → mysql-data ; 127.0.0.1:5433 → postgres-data
  备份  bash scripts/backup-meta-db.sh
  写库 host 仅当 WRITE_HOSTS=1（默认关闭）

文档: docs/dev/HYBRID_HOTDEPLOY.md
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
  status)
    print_status
    ;;
  down)
    echo "==> hybrid dev DOWN"
    switch_datasource_hosts docker
    if [[ "$WRITE_HOSTS" != "1" ]]; then
      warn "Docker backend 读业务源时若 datasource.host 仍是 127.0.0.1，容器内连不通模拟库"
      warn "全栈演示前请改 UI 内 host 为 mysql-data/postgres-data，或 WRITE_HOSTS=1 $0 down"
    fi
    docker rm -f "$PROXY_NAME" data-agent-mysql-portfwd \
      data-agent-mysql-data-portfwd data-agent-pg-data-portfwd 2>/dev/null || true
    # 若 IDEA 仍占 8065，Docker backend 起不来 — 先提示
    if port_listening 8065; then
      warn ":8065 still in use — stop IDEA before starting Docker backend"
      warn "only restoring frontend proxy port path; skip backend up if bind fails"
    fi
    docker compose -f "$COMPOSE" up -d --no-build backend frontend || {
      warn "compose up backend/frontend failed (often port conflict with IDEA)"
      exit 1
    }
    log "ok: docker backend:8065 frontend:3301 (SQL_INIT=never; meta volume intact)"
    ;;
  *)
    echo "usage: $0 {up|down|frontend|proxy|status}"
    echo "env: WRITE_HOSTS=1  # optional meta DB datasource host rewrite"
    exit 1
    ;;
esac
