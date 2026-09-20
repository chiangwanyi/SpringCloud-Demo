-- =========================================================
-- 首次部署时由 DBA 在 MySQL 上执行一次：
-- 为 service-system / service-auth / service-order 分别创建独立数据库。
-- 表结构由各自应用的 src/main/resources/schema.sql 手动执行一次
-- （application.yml 里 sql.init.mode=never，启动时不会自动建表，避免清空业务数据）。
-- =========================================================

CREATE DATABASE IF NOT EXISTS service_system
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

CREATE DATABASE IF NOT EXISTS service_auth
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

CREATE DATABASE IF NOT EXISTS service_order
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

-- 可选：为两个服务分别创建专用账号并授权，避免共用 root（生产推荐）。
-- CREATE USER IF NOT EXISTS 'sys_user'@'%' IDENTIFIED BY 'Sys@123456';
-- GRANT ALL PRIVILEGES ON service_system.* TO 'sys_user'@'%';
-- CREATE USER IF NOT EXISTS 'auth_user'@'%' IDENTIFIED BY 'Auth@123456';
-- GRANT ALL PRIVILEGES ON service_auth.* TO 'auth_user'@'%';
-- FLUSH PRIVILEGES;
