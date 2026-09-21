import com.jwy.scd.support.OrderNoGenerator;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 订单号生成器并发压测：旧实现（SO + 秒级时间戳 + 3 位随机） vs 新实现（SO + yyyyMMdd + 雪花 ID）。
 *
 * <p>刻意把两个指标分开测，避免互相污染：
 * <ul>
 *     <li><b>唯一性</b>：所有号码塞进 Set，统计重复——大 Set 的写入开销不该算进生成器性能；</li>
 *     <li><b>纯生成吞吐</b>：不分配、不入容器，只把 hash 异或进线程本地变量，量的是生成器本身。</li>
 * </ul>
 */
public class OrderNoStressTest {

    private static PrintStream out;

    /** 复刻线上出问题的那段代码 */
    private static final DateTimeFormatter OLD_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static String oldGenerate() {
        return "SO" + LocalDateTime.now().format(OLD_FMT)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }

    /** 模拟：每秒 qps 个请求打进来，持续 seconds 秒，用 threads 个工作线程（类比 Tomcat 线程池） */
    private static void measure(String label, Supplier<String> gen, int qps, int seconds, int threads) {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Map<String, AtomicInteger> counter = new ConcurrentHashMap<>();

        for (int s = 0; s < seconds; s++) {
            CountDownLatch latch = new CountDownLatch(qps);
            for (int i = 0; i < qps; i++) {
                pool.execute(() -> {
                    try {
                        counter.computeIfAbsent(gen.get(), k -> new AtomicInteger()).incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            try {
                latch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        pool.shutdown();

        int total = counter.values().stream().mapToInt(AtomicInteger::get).sum();
        int unique = counter.size();
        long dupKeys = counter.values().stream().filter(v -> v.get() > 1).count();
        int dupExtra = total - unique;

        out.printf("  %-24s 请求=%-6d 唯一=%-6d 撞号码个数=%-4d 多出来的重复单号=%-5d%n",
                label, total, unique, dupKeys, dupExtra);
        if (dupExtra > 0) {
            counter.entrySet().stream()
                    .filter(e -> e.getValue().get() > 1)
                    .limit(2)
                    .forEach(e -> out.printf("      样例：%s 共出现 %d 次%n", e.getKey(), e.getValue().get()));
        }
    }

    /** 只测生成器本身：不分配、不入容器 */
    private static void pureThroughput(String label, Supplier<String> gen, int threads, int perThread) {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicLong sink = new AtomicLong();
        long t0 = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                long acc = 0;
                try {
                    for (int i = 0; i < perThread; i++) {
                        acc += gen.get().hashCode();
                    }
                } finally {
                    sink.addAndGet(acc);
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long costNs = System.nanoTime() - t0;
        pool.shutdown();
        long total = (long) threads * perThread;
        out.printf("  %-24s 生成=%-9d 吞吐=%,d 个/秒 (%.0f ns/个) [sink=%d]%n",
                label, total, total * 1_000_000_000L / Math.max(costNs, 1),
                (double) costNs / total, sink.get() & 0xffff);
    }

    /** 唯一性大样本测试：并发生成 total 个，全部塞进 Set 看是否有重复 */
    private static void uniqueness(String label, Supplier<String> gen, int threads, int perThread) {
        Set<String> ids = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        ids.add(gen.get());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pool.shutdown();
        long total = (long) threads * perThread;
        out.printf("  %-24s 生成=%-9d 唯一=%-9d 重复=%d%n", label, total, ids.size(), total - ids.size());
    }

    public static void main(String[] args) throws Exception {
        out = new PrintStream(new FileOutputStream(args[0]), true, StandardCharsets.UTF_8);
        OrderNoGenerator generator = new OrderNoGenerator();

        out.println("生成器压测报告  —— " + LocalDateTime.now());
        out.println();
        out.println("【1】模拟『每秒 100 单』持续 5 秒（32 个工作线程）");
        measure("旧：SO+秒级时间戳+3位随机", OrderNoStressTest::oldGenerate, 100, 5, 32);
        measure("新：SO+yyyyMMdd+雪花ID", generator::next, 100, 5, 32);

        out.println();
        out.println("【2】模拟『每秒 500 单』持续 5 秒（64 个工作线程）");
        measure("旧：SO+秒级时间戳+3位随机", OrderNoStressTest::oldGenerate, 500, 5, 64);
        measure("新：SO+yyyyMMdd+雪花ID", generator::next, 500, 5, 64);

        out.println();
        out.println("【3】大样本唯一性：16 线程 × 100000 个");
        uniqueness("旧：SO+秒级时间戳+3位随机", OrderNoStressTest::oldGenerate, 16, 100_000);
        uniqueness("新：SO+yyyyMMdd+雪花ID", generator::next, 16, 100_000);

        out.println();
        out.println("【4】纯生成吞吐（不分配、不入容器，只看生成器本身）：16 线程 × 500000 个");
        pureThroughput("旧：SO+秒级时间戳+3位随机", OrderNoStressTest::oldGenerate, 16, 500_000);
        pureThroughput("新：SO+yyyyMMdd+雪花ID", generator::next, 16, 500_000);

        out.println();
        out.println("【5】新订单号格式示例：" + generator.next() + "（长度 " + generator.next().length() + "）");
        out.close();
    }
}
