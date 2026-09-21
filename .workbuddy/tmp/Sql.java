import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

/**
 * 通用 SQL 执行 / 查询小工具（纯 ASCII 输出，规避 Windows 控制台编码问题）。
 *
 * 用法：java -cp <mysql-connector-j.jar> Sql.java "<SQL>" [数据库名，默认 leaf]
 *
 * 输出约定：字符串列除了原值，还会额外打印 len=N —— 控制台把 UTF-8 显示成乱码是
 * 显示层问题，但 N 能确切反映库里存了几个字符，用来判断有没有写入乱码。
 */
public class Sql {

    private static final String PARAMS =
            "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                    + "&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=5000";

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("usage: Sql \"<SQL>\" [db]");
            return;
        }
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        Class.forName("com.mysql.cj.jdbc.Driver");

        String sql = args[0];
        String db = args.length > 1 ? args[1] : "leaf";
        String url = "jdbc:mysql://rxs:3306/" + db + PARAMS;

        try (Connection c = DriverManager.getConnection(url, "root", "123456");
             Statement st = c.createStatement()) {
            boolean hasResultSet = st.execute(sql);
            if (!hasResultSet) {
                System.out.println("[update] affectedRows=" + st.getUpdateCount());
                return;
            }
            try (ResultSet rs = st.getResultSet()) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                StringBuilder head = new StringBuilder();
                for (int i = 1; i <= n; i++) {
                    head.append(md.getColumnLabel(i)).append(i < n ? " | " : "");
                }
                System.out.println(head);
                int rows = 0;
                while (rs.next()) {
                    StringBuilder line = new StringBuilder();
                    for (int i = 1; i <= n; i++) {
                        Object v = rs.getObject(i);
                        String cell;
                        if (v == null) {
                            cell = "NULL";
                        } else if (v instanceof Number || v instanceof Boolean) {
                            cell = String.valueOf(v);
                        } else {
                            String s = String.valueOf(v);
                            cell = s + " [len=" + s.length() + "]";
                        }
                        line.append(cell).append(i < n ? " | " : "");
                    }
                    System.out.println(line);
                    rows++;
                }
                System.out.println("[rows] " + rows);
            }
        }
    }
}
