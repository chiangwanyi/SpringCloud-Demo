import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/** 只读探测：列出库、表与演示用户，不执行任何写操作。 */
public class Inspect {

    private static final String BASE_URL =
            "jdbc:mysql://rxs:3306/?useUnicode=true&characterEncoding=utf8"
                    + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";

    public static void main(String[] args) throws Exception {
        Path outFile = Path.of(args[0]);
        try (BufferedWriter w = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8);
             Connection c = DriverManager.getConnection(BASE_URL, "root", "123456");
             Statement s = c.createStatement()) {

            w.write("=== DATABASES ===\n");
            try (ResultSet rs = s.executeQuery("SHOW DATABASES")) {
                while (rs.next()) {
                    w.write("  " + rs.getString(1) + "\n");
                }
            }

            w.write("=== TABLES IN service_order ===\n");
            writeTables(s, w, "service_order");
            w.write("=== TABLES IN service_system ===\n");
            writeTables(s, w, "service_system");
            w.write("=== TABLES IN service_auth ===\n");
            writeTables(s, w, "service_auth");

            w.write("=== service_system.sys_user (用于对齐演示用户 ID) ===\n");
            try (ResultSet rs = s.executeQuery(
                    "SELECT id, username, nickname, status FROM service_system.sys_user ORDER BY id")) {
                while (rs.next()) {
                    w.write("  id=" + rs.getLong("id")
                            + " username=" + rs.getString("username")
                            + " nickname=" + rs.getString("nickname")
                            + " status=" + rs.getInt("status") + "\n");
                }
            } catch (SQLException e) {
                w.write("  (读取失败: " + e.getMessage() + ")\n");
            }
        }
    }

    private static void writeTables(Statement s, BufferedWriter w, String schema) throws Exception {
        try (ResultSet rs = s.executeQuery(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='" + schema + "'")) {
            boolean any = false;
            while (rs.next()) {
                any = true;
                w.write("  " + rs.getString(1) + "\n");
            }
            if (!any) {
                w.write("  (库不存在或没有表)\n");
            }
        }
    }
}
