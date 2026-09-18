-- =========================================================
-- H2 内存数据库测试/演示表结构（兼容 MySQL 模式）
-- 仅用于 service-auth 模块本地运行与单元测试，与真实 MySQL 表结构保持一致
-- =========================================================

DROP TABLE IF EXISTS auth_account;
CREATE TABLE auth_account (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  username    VARCHAR(50)  NOT NULL,
  password    VARCHAR(100) NOT NULL,
  status      TINYINT      DEFAULT 1,
  del_flag    TINYINT      DEFAULT 0,
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
);
