import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 建库建表执行器（受约束）。
 *
 * 安全约束：
 *  1. 只允许 CREATE DATABASE / CREATE TABLE / DROP TABLE / INSERT 四类语句；
 *     出现 DROP DATABASE / TRUNCATE / DELETE / UPDATE / ALTER / GRANT 等一律拒绝并终止。
 *  2. 执行前先确认目标库 service_order 中没有任何表（避免误删既有数据）。
 *  3. 只连接 rxs 上的 service_order，绝不触碰 service_system / service_auth 及其他库。
 */
public class SchemaRunner {

    private static final String DB_NAME = "service_order";
    private static final String BASE_URL =
            "jdbc:mysql://rxs:3306/?useUnicode=true&characterEncoding=utf8"
                    + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";

    public static void main(String[] args) throws Exception {
        Path sqlFile = Path.of(args[0]);
        Path outFile = Path.of(args[1]);

        try (BufferedWriter w = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {

            // ---------- 步骤 1：安全前置检查 ----------
            try (Connection c = DriverManager.getConnection(BASE_URL, "root", "123456");
                 Statement s = c.createStatement()) {
                List<String> existing = new ArrayList<>();
                try (ResultSet rs = s.executeQuery(
                        "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='" + DB_NAME + "'")) {
                    while (rs.next()) {
                        existing.add(rs.getString(1));
                    }
                }
                w.write("[安全检查] " + DB_NAME + " 中既有表数量 = " + existing.size()
                        + (existing.isEmpty() ? "" : " -> " + existing) + "\n");
                if (!existing.isEmpty()) {
                    w.write("!! 目标库已有表，为避免误删数据，已终止执行。请人工确认后再处理。\n");
                    return;
                }
            }

            // ---------- 步骤 2：建库 ----------
            try (Connection c = DriverManager.getConnection(BASE_URL, "root", "123456");
                 Statement s = c.createStatement()) {
                s.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DB_NAME
                        + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
                w.write("[建库] CREATE DATABASE IF NOT EXISTS " + DB_NAME + " -> OK\n");
            }

            // ---------- 步骤 3：逐条执行 schema.sql ----------
            String raw = Files.readString(sqlFile, StandardCharsets.UTF_8);
            StringBuilder cleaned = new StringBuilder();
            for (String line : raw.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                    continue;
                }
                cleaned.append(line).append('\n');
            }

            String dbUrl = "jdbc:mysql://rxs:3306/" + DB_NAME
                    + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                    + "&useSSL=false&allowPublicKeyRetrieval=true";

            int ok = 0;
            try (Connection c = DriverManager.getConnection(dbUrl, "root", "123456");
                 Statement s = c.createStatement()) {
                for (String stmt : cleaned.toString().split(";")) {
                    String sql = stmt.trim();
                    if (sql.isEmpty()) {
                        continue;
                    }
                    String head = sql.toUpperCase(Locale.ROOT);

                    boolean allowed = head.startsWith("CREATE DATABASE")
                            || head.startsWith("CREATE TABLE")
                            || head.startsWith("DROP TABLE")
                            || head.startsWith("INSERT");
                    if (!allowed) {
                        w.write("!! 拒绝执行（不在白名单内）: " + sql.substring(0, Math.min(60, sql.length())) + "\n");
                        return;
                    }

                    s.executeUpdate(sql);
                    ok++;
                    w.write("[执行] " + sql.substring(0, Math.min(70, sql.length())).replace('\n', ' ') + " ... OK\n");
                }
            }
            w.write("[完成] 共执行 " + ok + " 条语句\n");

            // ---------- 步骤 4：回读结果 ----------
            try (Connection c = DriverManager.getConnection(dbUrl, "root", "123456");
                 Statement s = c.createStatement()) {
                w.write("=== " + DB_NAME + " 表清单 ===\n");
                try (ResultSet rs = s.executeQuery("SHOW TABLES")) {
                    while (rs.next()) {
                        w.write("  " + rs.getString(1) + "\n");
                    }
                }
                w.write("=== biz_product 演示数据 ===\n");
                try (ResultSet rs = s.executeQuery("SELECT id, name, price, stock, status FROM biz_product ORDER BY id")) {
                    while (rs.next()) {
                        w.write("  id=" + rs.getLong(1) + " " + rs.getString(2)
                                + " price=" + rs.getBigDecimal(3) + " stock=" + rs.getInt(4)
                                + " status=" + rs.getInt(5) + "\n");
                    }
                }
            }
        }
    }
}
