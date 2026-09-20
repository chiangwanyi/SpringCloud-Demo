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

-- =========================================================
-- 默认演示数据（service-system）
-- 应用启动时 sql.init.mode=always 会先 DROP 再 CREATE 表，随后执行以下 INSERT，
-- 因此每次启动都能拿到一份干净的演示数据。密码为明文，仅用于演示。
-- =========================================================

INSERT INTO sys_dept (id, dept_name, status) VALUES
  (1, '研发部', 1);

INSERT INTO sys_role (id, role_name, role_code, status) VALUES
  (1, '管理员', 'ADMIN', 1),
  (2, '普通用户', 'USER', 1);

INSERT INTO sys_user (id, dept_id, username, password, nickname, status) VALUES
  (1, 1, 'admin', 'admin123', '管理员', 1),
  (2, 1, 'zhangsan', '123456', '张三', 1);

INSERT INTO sys_user_role (id, user_id, role_id) VALUES
  (1, 1, 1),
  (2, 2, 2);
