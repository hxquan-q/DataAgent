#!/usr/bin/env bash
# 备份元库 nl2sql_db（agent/datasource/metric 等配置）
# 用法:
#   bash scripts/backup-meta-db.sh           # 写出带时间戳的 dump
#   bash scripts/backup-meta-db.sh restore <file.sql>  # 恢复（需二次确认）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MYSQL_C="${MYSQL_C:-data-agent-mysql-inner}"
DB="${DB:-nl2sql_db}"
OUT_DIR="${OUT_DIR:-$ROOT/backups}"
cmd="${1:-dump}"

need_container() {
  if ! docker ps --format '{{.Names}}' | grep -qx "$MYSQL_C"; then
    echo "[backup] error: container $MYSQL_C not running" >&2
    exit 1
  fi
}

case "$cmd" in
  dump|"")
    need_container
    mkdir -p "$OUT_DIR"
    ts="$(date +%Y%m%d_%H%M%S)"
    out="$OUT_DIR/nl2sql_${ts}.sql"
    docker exec "$MYSQL_C" mysqldump -uroot -proot \
      --single-transaction --routines --triggers --set-gtid-purged=OFF \
      "$DB" >"$out" 2>/dev/null
    # 脱敏统计
    bytes="$(wc -c <"$out" | tr -d ' ')"
    agents="$(docker exec "$MYSQL_C" mysql -uroot -proot "$DB" -N -e 'SELECT COUNT(*) FROM agent' 2>/dev/null | tr -d '\r' || echo '?')"
    echo "[backup] wrote $out ($bytes bytes, agent_rows=$agents)"
    echo "[backup] restore: bash scripts/backup-meta-db.sh restore $out"
    ;;
  restore)
    file="${2:-}"
    if [[ -z "$file" || ! -f "$file" ]]; then
      echo "usage: $0 restore <dump.sql>" >&2
      exit 1
    fi
    need_container
    echo "[backup] WARNING: this will overwrite tables in $DB from $file"
    echo "[backup] type YES to continue:"
    read -r ans
    if [[ "$ans" != "YES" ]]; then
      echo "[backup] aborted"
      exit 1
    fi
    docker exec -i "$MYSQL_C" mysql -uroot -proot "$DB" <"$file"
    echo "[backup] restore done"
    ;;
  *)
    echo "usage: $0 {dump|restore <file.sql>}"
    exit 1
    ;;
esac
