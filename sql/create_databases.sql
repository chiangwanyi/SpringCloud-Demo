-- =========================================================
-- 首次部署时由 DBA 在 MySQL 上执行一次：
-- 为 service-system / service-order 分别创建独立数据库。
-- 表结构由各自应用的 src/main/resources/schema.sql 手动执行一次
-- （application.yml 里 sql.init.mode=never，启动时不会自动建表，避免清空业务数据）。
--
-- 注意（2026-09-20 重构）：service-auth 已改为「无状态认证服务」——
-- 不再保存账号密码、不连数据库，登录凭据统一由 service-system 校验，
-- 因此不再需要 service_auth 数据库。原建库语句见文件末尾注释。
-- =========================================================

CREATE DATABASE IF NOT EXISTS service_system
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

CREATE DATABASE IF NOT EXISTS service_order
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

-- 可选：为两个服务分别创建专用账号并授权，避免共用 root（生产推荐）。
-- CREATE USER IF NOT EXISTS 'sys_user'@'%' IDENTIFIED BY 'Sys@123456';
-- GRANT ALL PRIVILEGES ON service_system.* TO 'sys_user'@'%';
-- CREATE USER IF NOT EXISTS 'order_user'@'%' IDENTIFIED BY 'Order@123456';
-- GRANT ALL PRIVILEGES ON service_order.* TO 'order_user'@'%';
-- FLUSH PRIVILEGES;

-- =========================================================
-- 【已废弃】service_auth 数据库：
-- service-auth 重构后不再持久化任何数据（账号密码统一存 sys_user），
-- 该库及 auth_account 表已无用途。为兼容旧部署保留注释供参考，新部署无需创建。
-- =========================================================
-- CREATE DATABASE IF NOT EXISTS service_auth
--   DEFAULT CHARACTER SET utf8mb4
--   COLLATE utf8mb4_general_ci;
-- CREATE USER IF NOT EXISTS 'auth_user'@'%' IDENTIFIED BY 'Auth@123456';
-- GRANT ALL PRIVILEGES ON service_auth.* TO 'auth_user'@'%';
