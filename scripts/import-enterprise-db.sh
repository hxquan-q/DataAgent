#!/usr/bin/env bash
# Import enterprise sample database (from data/aaa.sql) into MySQL datasource container.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SQL_FILE="${ROOT_DIR}/docker-file/config/mysql/enterprise_db.sql"
SRC_SQL="${ROOT_DIR}/data/aaa.sql"
CONTAINER="${MYSQL_DATASOURCE_CONTAINER:-data-agent-mysql-datasource}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"

if [[ ! -f "$SRC_SQL" ]]; then
  echo "Missing source SQL: $SRC_SQL" >&2
  exit 1
fi

mkdir -p "$(dirname "$SQL_FILE")"
{
  cat <<'HEADER'
/*
 * Enterprise sample database for DataAgent (from data/aaa.sql)
 */
CREATE DATABASE IF NOT EXISTS enterprise_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE enterprise_db;
HEADER
  cat "$SRC_SQL"
} > "$SQL_FILE"

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "Container $CONTAINER is not running." >&2
  echo "Start first: cd docker-file && docker compose up -d mysql-data" >&2
  exit 1
fi

echo "Importing enterprise_db into $CONTAINER ..."
docker exec -i "$CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" < "$SQL_FILE"
echo "Import finished."
docker exec "$CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "USE enterprise_db; SHOW TABLES; SELECT COUNT(*) AS customer_order_forecast_rows FROM customer_order_forecast;"
