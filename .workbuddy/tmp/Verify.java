import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/** 校验中文是否正确落库（以 UTF-8 写文件，避开控制台编码干扰）。 */
public class Verify {

    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://rxs:3306/service_order?useUnicode=true&characterEncoding=utf8"
                + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
        try (BufferedWriter w = Files.newBufferedWriter(Path.of(args[0]), StandardCharsets.UTF_8);
             Connection c = DriverManager.getConnection(url, "root", "123456");
             Statement s = c.createStatement()) {

            w.write("=== biz_product 名称（应与 schema.sql 中的中文一致）===\n");
            try (ResultSet rs = s.executeQuery("SELECT id, name FROM biz_product ORDER BY id")) {
                while (rs.next()) {
                    w.write("  id=" + rs.getLong(1) + " name=" + rs.getString(2) + "\n");
                }
            }
            w.write("=== biz_order 订单（username 由 Feign 从 service-system 取得）===\n");
            try (ResultSet rs = s.executeQuery(
                    "SELECT id, order_no, user_id, username, total_amount, status FROM biz_order ORDER BY id")) {
                while (rs.next()) {
                    w.write("  id=" + rs.getLong(1) + " orderNo=" + rs.getString(2)
                            + " userId=" + rs.getLong(3) + " username=" + rs.getString(4)
                            + " total=" + rs.getBigDecimal(5) + " status=" + rs.getInt(6) + "\n");
                }
            }
            w.write("=== biz_order_item 明细快照 ===\n");
            try (ResultSet rs = s.executeQuery(
                    "SELECT id, order_id, product_name, price, quantity, amount FROM biz_order_item ORDER BY id")) {
                while (rs.next()) {
                    w.write("  id=" + rs.getLong(1) + " orderId=" + rs.getLong(2)
                            + " productName=" + rs.getString(3) + " price=" + rs.getBigDecimal(4)
                            + " qty=" + rs.getInt(5) + " amount=" + rs.getBigDecimal(6) + "\n");
                }
            }
            w.write("=== 库/表字符集 ===\n");
            try (ResultSet rs = s.executeQuery(
                    "SELECT TABLE_NAME, TABLE_COLLATION FROM information_schema.TABLES "
                            + "WHERE TABLE_SCHEMA='service_order'")) {
                while (rs.next()) {
                    w.write("  " + rs.getString(1) + " -> " + rs.getString(2) + "\n");
                }
            }
        }
    }
}
