-- =========================================================
-- service-order 订单库表结构（MySQL）
--
-- 执行方式（首次部署执行一次即可，应用不会自动建表，见 application.yml 的 sql.init.mode=never）：
--   mysql -h rxs -P 3306 -u root -p123456 < schema.sql
-- 或在 Navicat / DataGrip 中连接 service_order 库后执行本文件内容。
--
-- 注意：DROP TABLE 会清空数据，仅适合学习演示环境；生产环境请改用增量迁移脚本
-- （Flyway / Liquibase）。
-- =========================================================

DROP TABLE IF EXISTS biz_order_item;
DROP TABLE IF EXISTS biz_order;
DROP TABLE IF EXISTS biz_product;

-- ---------------------------
-- 商品表（含库存）
-- 说明：商品本应属独立的 service-product，此处为教学演示先放在订单库中，
--       使「扣库存 + 写订单」处于同一个本地事务，无需分布式事务即可保证一致。
-- ---------------------------
CREATE TABLE biz_product (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  name        VARCHAR(200)  NOT NULL,
  price       DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  stock       INT           NOT NULL DEFAULT 0,
  status      TINYINT       NOT NULL DEFAULT 1 COMMENT '1=上架 0=下架',
  del_flag    TINYINT       NOT NULL DEFAULT 0 COMMENT '0=正常 1=已删除',
  create_time DATETIME      DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_product_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- ---------------------------
-- 订单主表
-- ---------------------------
CREATE TABLE biz_order (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  order_no     VARCHAR(40)   NOT NULL COMMENT '业务订单号，服务端生成',
  user_id      BIGINT        NOT NULL COMMENT '下单用户 ID（service-system 的 sys_user.id）',
  username     VARCHAR(50)   DEFAULT NULL COMMENT '下单用户名快照，经 Feign 从 service-system 取得',
  total_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  status       TINYINT       NOT NULL DEFAULT 0 COMMENT '0=已创建 1=已完成 2=已取消',
  del_flag     TINYINT       NOT NULL DEFAULT 0 COMMENT '0=正常 1=已删除',
  create_time  DATETIME      DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_order_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

-- ---------------------------
-- 订单明细表（存下单时刻的商品快照）
-- ---------------------------
CREATE TABLE biz_order_item (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  order_id     BIGINT        NOT NULL,
  product_id   BIGINT        NOT NULL,
  product_name VARCHAR(200)  NOT NULL COMMENT '下单时刻的商品名快照',
  price        DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '下单时刻的单价快照',
  quantity     INT           NOT NULL DEFAULT 1,
  amount       DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '小计 = price × quantity',
  create_time  DATETIME      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_item_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- =========================================================
-- 演示数据
-- 提示：user_id 请与 service-system 库里 sys_user 的 id 对应，
--       否则下单时 Feign 会返回「下单用户不存在」。
--       service-system 的默认演示用户为 1=admin、2=zhangsan。
-- =========================================================

INSERT INTO biz_product (id, name, price, stock, status) VALUES
  (1, '机械键盘',   399.00, 100, 1),
  (2, '无线鼠标',   129.00, 200, 1),
  (3, '显示器 27寸', 1299.00,  30, 1),
  (4, '已下架商品',  99.00,  50, 0);
