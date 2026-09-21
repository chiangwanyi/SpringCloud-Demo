import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 初始化 service-id-gen 的号段库（幂等）。
 *
 * 用法（JDK 21 单文件源码启动）：
 *   java -cp <mysql-connector-j.jar> InitLeafDb.java
 */
public class InitLeafDb {

    private static final String HOST = "rxs";
    private static final int PORT = 3306;
    private static final String USER = "root";
    private static final String PASSWORD = "123456";
    private static final String PARAMS =
            "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                    + "&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=5000";

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");

        // ---------- 1. 建库 ----------
        String serverUrl = "jdbc:mysql://" + HOST + ":" + PORT + "/" + PARAMS;
        try (Connection c = DriverManager.getConnection(serverUrl, USER, PASSWORD);
             Statement st = c.createStatement()) {
            System.out.println("[connect] " + serverUrl);
            System.out.println("[mysql  ] " + version(c));
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `leaf` "
                    + "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            System.out.println("[ok     ] database `leaf` ready");
        }

        // ---------- 2. 建表 + 种子行 ----------
        String leafUrl = "jdbc:mysql://" + HOST + ":" + PORT + "/leaf" + PARAMS;
        try (Connection c = DriverManager.getConnection(leafUrl, USER, PASSWORD);
             Statement st = c.createStatement()) {

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `leaf_alloc` (
                      `biz_tag`     VARCHAR(128) NOT NULL DEFAULT ''  COMMENT '业务标识，同时是号段冲突域',
                      `max_id`      BIGINT       NOT NULL DEFAULT 0   COMMENT '已批发出去的最大值（不是下一个可用值）',
                      `step`        INT          NOT NULL             COMMENT '每次批发的段长度',
                      `description` VARCHAR(256)          DEFAULT NULL COMMENT '备注',
                      `update_time` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (`biz_tag`)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Leaf-segment 号段账本'
                    """);
            System.out.println("[ok     ] table `leaf_alloc` ready");

            // ON DUPLICATE KEY 保证重复执行不会把已推进的 max_id 重置回去
            int rows = st.executeUpdate("INSERT INTO `leaf_alloc` (`biz_tag`, `max_id`, `step`, `description`) "
                    + "VALUES ('order', 0, 100000, '业务订单：订单主键与订单号号段') "
                    + "ON DUPLICATE KEY UPDATE `biz_tag` = `biz_tag`");
            System.out.println("[seed   ] insert affectedRows=" + rows + " (0 表示已存在，未改动)");

            try (ResultSet rs = st.executeQuery(
                    "SELECT biz_tag, max_id, step, description FROM leaf_alloc ORDER BY biz_tag")) {
                System.out.println("---- leaf_alloc 当前内容 ----");
                System.out.printf("%-12s %-14s %-10s %s%n", "biz_tag", "max_id", "step", "description");
                while (rs.next()) {
                    System.out.printf("%-12s %-14d %-10d %s%n",
                            rs.getString("biz_tag"), rs.getLong("max_id"),
                            rs.getInt("step"), rs.getString("description"));
                }
            }
        }
        System.out.println("[done   ] 号段库初始化完成");
    }

    private static String version(Connection c) throws Exception {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT VERSION()")) {
            return rs.next() ? rs.getString(1) : "unknown";
        }
    }
}
