-- 部门
CREATE TABLE sys_dept
(
    id          BIGINT       NOT NULL COMMENT '主键',
    parent_id   BIGINT   DEFAULT NULL COMMENT '父部门ID',
    dept_name   VARCHAR(100) NOT NULL COMMENT '部门名称',
    status      TINYINT  DEFAULT 1 COMMENT '0禁用 1正常',
    del_flag    TINYINT  DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 角色
CREATE TABLE sys_role
(
    id          BIGINT       NOT NULL COMMENT '主键',
    role_name   VARCHAR(100) NOT NULL COMMENT '角色名称',
    role_code   VARCHAR(100) NOT NULL COMMENT '角色标识',
    status      TINYINT  DEFAULT 1 COMMENT '0禁用 1正常',
    del_flag    TINYINT  DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户
CREATE TABLE sys_user
(
    id          BIGINT       NOT NULL COMMENT '主键',
    dept_id     BIGINT   DEFAULT NULL COMMENT '部门ID',
    username    VARCHAR(50)  NOT NULL COMMENT '用户名',
    password    VARCHAR(100) NOT NULL COMMENT '密码',
    nickname    VARCHAR(100) NOT NULL COMMENT '昵称',
    status      TINYINT  DEFAULT 1 COMMENT '0禁用 1正常',
    del_flag    TINYINT  DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户角色中间表
CREATE TABLE sys_user_role
(
    id      BIGINT NOT NULL COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
