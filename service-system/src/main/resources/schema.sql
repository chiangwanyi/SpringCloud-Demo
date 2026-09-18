-- =========================================================
-- H2 内存数据库测试/演示表结构（兼容 MySQL 模式）
-- 仅用于 service-system 模块本地运行与单元测试，与真实 MySQL 表结构保持一致
-- =========================================================

DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  dept_id     BIGINT       DEFAULT NULL,
  username    VARCHAR(50)  NOT NULL,
  password    VARCHAR(100) NOT NULL,
  nickname    VARCHAR(100) NOT NULL,
  status      TINYINT      DEFAULT 1,
  del_flag    TINYINT      DEFAULT 0,
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
);

DROP TABLE IF EXISTS sys_dept;
CREATE TABLE sys_dept (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  parent_id   BIGINT       DEFAULT NULL,
  dept_name   VARCHAR(100) NOT NULL,
  status      TINYINT      DEFAULT 1,
  del_flag    TINYINT      DEFAULT 0,
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  role_name   VARCHAR(100) NOT NULL,
  role_code   VARCHAR(100) NOT NULL,
  status      TINYINT      DEFAULT 1,
  del_flag    TINYINT      DEFAULT 0,
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (role_code)
);

DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
  id      BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id)
);
