-- ============================================================================
--  service-id-gen 号段库（Leaf-segment）
--
--  执行一次即可（幂等，重复执行不会清数据）：
--      mysql -h rxs -u root -p123456 < sql/id-gen.sql
--
--  ⚠️ 这个库必须【独立】于业务库（biz_order 所在的库）。
--     号段账本只有一行热点记录，每次取段都要对那一行加 X 锁；
--     和业务表挤在一个库/实例上，会让"取段"和"下单"互相争资源。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `leaf`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `leaf`;

-- ----------------------------------------------------------------------------
--  号段账本：一行 = 一个业务标签（biz_tag）的 ID 账本
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `leaf_alloc`
(
    `biz_tag`     VARCHAR(128) NOT NULL DEFAULT '' COMMENT '业务标识，主键，同时是号段冲突域',
    `max_id`      BIGINT       NOT NULL DEFAULT 0 COMMENT '已批发出去的最大值（注意：不是"下一个可用值"）',
    `step`        INT          NOT NULL COMMENT '每次批发的段长度',
    `description` VARCHAR(256)          DEFAULT NULL COMMENT '备注',
    `update_time` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`biz_tag`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='Leaf-segment 号段账本';

-- ----------------------------------------------------------------------------
--  种子行
--
--  ★ max_id 初始化为 0（不是 Leaf 官方 DDL 的默认值 1）
--    - max_id = 0、step = 100000  →  第一个号段是 [1, 100000]，ID 从 1 开始
--    - max_id = 1、step = 100000  →  第一个号段是 [2, 100001]，白扔一个号且反直觉
--
--  ★ step 怎么定：官方口径是「高峰发号 QPS × 600（≈10 分钟）」，
--    含义是"即使号段库宕机，还能继续发号 10~20 分钟"。
--      - 高峰 50 QPS   → step 30000
--      - 高峰 167 QPS  → step 100000（本行取值）
--      - 高峰 2000 QPS → step 1200000
--    step 与"发号 QPS"成反比地影响数据库压力：DB 写入 QPS = 发号 QPS / step，
--    所以 step 放大 10 倍，数据库压力就小 10 倍，代价只是重启时丢弃的号更多。
--
--  ★ 想让双 buffer 切换"肉眼可见"（本地演示/压测）时，
--    把 step 临时调小即可，不需要重启服务（step 存在数据库里，每次取段现读）：
--      UPDATE leaf_alloc SET step = 2000 WHERE biz_tag = 'order';
-- ----------------------------------------------------------------------------
INSERT INTO `leaf_alloc` (`biz_tag`, `max_id`, `step`, `description`)
VALUES ('order', 0, 100000, '业务订单号（orderNo）号段')
ON DUPLICATE KEY UPDATE `biz_tag` = `biz_tag`;

-- ★ 另建一个 demo 号段，给压测/演示专用。
--   存在的理由：验证多实例唯一性时要把 max_id 重置为 0，如果拿业务在用的 'order' 去压，
--   重置后新发的号会和历史上已经落库的订单号重叠 —— 唯一键直接拒单，很难排查。
--   给它一个独立的 biz_tag，测试再怎么折腾都碰不到业务账本。
INSERT INTO `leaf_alloc` (`biz_tag`, `max_id`, `step`, `description`)
VALUES ('demo', 0, 2000, '演示/压测专用：验证双 buffer 与多实例唯一性')
ON DUPLICATE KEY UPDATE `biz_tag` = `biz_tag`;

-- 查看账本当前状态
-- SELECT biz_tag, max_id, step, update_time FROM leaf_alloc;
