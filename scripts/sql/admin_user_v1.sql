-- hybrid / 已有元库：仅追加管理员表（不改 sql.init，不 down -v）
-- 用法：mysql -h127.0.0.1 -P3306 -uroot -p nl2sql_db < scripts/sql/admin_user_v1.sql
-- 风险前建议：bash scripts/backup-meta-db.sh

CREATE TABLE IF NOT EXISTS admin_user (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL COMMENT '登录名',
  password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
  display_name  VARCHAR(64)  NULL COMMENT '显示名',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
  last_login_at TIMESTAMP    NULL COMMENT '最近登录时间',
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_username (username)
) ENGINE=InnoDB COMMENT='平台管理员';
