import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;

public class BenchmarkRunner {

    private static final String DATASET_DIR = "datasets";
    private static final String RESULTS_DIR = "results";

    private static final int[] ALL_SIZES = {
        1_000, 5_000, 10_000, 25_000, 50_000,
        100_000, 250_000, 500_000, 1_000_000, 2_000_000
    };

    private static final double BF_TARGET_FPR = 0.01;

    private static final int WARMUP_ITERATIONS = 10;

    private static final int INSERT_TRIALS = 3;

    private static final int LOOKUP_TRIALS = 7;

    private static final int TARGET_LOOKUP_OPS = 2_000_000;

    private static final int VERIFY_SAMPLE_SIZE = 1000;

    static volatile long BLACKHOLE;

    private static int memoryBudgetMb = 256;

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("  RBL BENCHMARK: Hash Table (Separate Chaining) vs Bloom Filter");
        System.out.println("  CSD201 — Summer 2026 | Nhom 2 SE20B02");
        System.out.println("=".repeat(70));

        List<Integer> sizeList = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--budget") && i + 1 < args.length) {
                memoryBudgetMb = Integer.parseInt(args[++i]);
            } else {
                sizeList.add(Integer.parseInt(args[i]));
            }
        }

        int[] sizes;
        if (!sizeList.isEmpty()) {
            sizes = sizeList.stream().mapToInt(i -> i).toArray();
        } else {
            sizes = ALL_SIZES;
        }

        Runtime rt = Runtime.getRuntime();
        System.out.printf("\n  JVM Max Memory : %,d MB%n", rt.maxMemory() / (1024 * 1024));
        System.out.printf("  JVM Total Memory: %,d MB%n", rt.totalMemory() / (1024 * 1024));
        System.out.printf("  Available CPUs : %d%n", rt.availableProcessors());
        System.out.printf("  Dataset dir    : %s%n", new File(DATASET_DIR).getAbsolutePath());
        System.out.printf("  Sizes to test  : %s%n", Arrays.toString(sizes));

        new File(RESULTS_DIR).mkdirs();

        List<BenchmarkResult> htResults = new ArrayList<>();
        List<BenchmarkResult> bfResults = new ArrayList<>();

        for (int n : sizes) {
            System.out.println("\n" + "─".repeat(70));
            System.out.printf("  BENCHMARK N = %,d%n", n);
            System.out.println("─".repeat(70));

            String datasetFile = DATASET_DIR + "/username_n" + n + ".json";
            if (!new File(datasetFile).exists()) {
                System.out.printf("  ⚠ File not found: %s — SKIPPING%n", datasetFile);
                continue;
            }

            System.out.println("\n  [BF] Bloom Filter (k=" + calculateK(n) + ", m=" +
                    String.format("%,d", calculateM(n)) + " bits):");
            BenchmarkResult bfResult = benchmarkBloomFilterStreaming(datasetFile, n);
            bfResults.add(bfResult);
            System.out.println(bfResult);

            forceGC();

            System.out.println("\n  [HT] Hash Table Separate Chaining:");
            BenchmarkResult htResult = benchmarkHashTableStreaming(datasetFile, n);
            htResults.add(htResult);
            System.out.println(htResult);

            forceGC();
        }

        System.out.println("\n" + "=".repeat(70));
        System.out.println("  WRITING RESULTS");
        System.out.println("=".repeat(70));

        writeHTResults(htResults);
        writeBFResults(bfResults);
        writeSummary(htResults, bfResults);

        printComparisonTable(htResults, bfResults);

        System.out.println("\n✅ Benchmark complete! Results saved to: " +
                new File(RESULTS_DIR).getAbsolutePath());
    }

    private static BenchmarkResult benchmarkHashTableStreaming(String datasetFile, int n) {
        BenchmarkResult result = new BenchmarkResult();
        result.dataStructure = "HashTable";
        result.n = n;
        result.memoryBudgetMb = memoryBudgetMb;
        result.isOOM = false;

        try {

            final List<String> sample = new ArrayList<>();
            JsonParser.StreamedQueries streamed = JsonParser.streamParse(datasetFile, key -> {
                if (sample.size() < VERIFY_SAMPLE_SIZE) sample.add(key);
            });
            List<String> mixed = streamed.mixed;

            HashTableSC ht = null;
            double insertTimeTotal = 0;
            long measuredMemMin = Long.MAX_VALUE;
            for (int t = 0; t < INSERT_TRIALS; t++) {
                ht = null;
                forceGC();
                long baseline = usedMemory();

                final HashTableSC h = new HashTableSC(n);
                final long[] insertNanos = {0};
                JsonParser.streamParse(datasetFile, key -> {
                    long t0 = System.nanoTime();
                    h.insert(key);
                    insertNanos[0] += System.nanoTime() - t0;
                });
                insertTimeTotal += insertNanos[0] / 1_000_000.0;

                forceGC();
                long after = usedMemory();
                measuredMemMin = Math.min(measuredMemMin, after - baseline);
                ht = h;
            }
            result.insertTimeMs = insertTimeTotal / INSERT_TRIALS;
            result.memoryBytes = Math.max(0, measuredMemMin);
            result.memoryModelBytes = ht.estimateMemoryBytes();

            result.collisionCount = ht.getCollisionCount();
            result.collisionRate = ht.getCollisionRate();
            result.maxChainLength = ht.getMaxChainLength();
            result.avgChainLength = ht.getAvgChainLength();
            result.tableCapacity = ht.getCapacity();
            result.resizeCount = ht.getResizeCount();

            final HashTableSC htRef = ht;
            double[] lk = measureLookup(htRef::lookup, mixed);
            result.lookupTimeMs = lk[0];
            result.throughputStdevPct = lk[1];
            result.throughputOpsPerSec = lk[2];

            for (String key : sample) {
                if (!ht.lookup(key)) {
                    System.out.printf("  ⚠ CORRECTNESS ERROR: key '%s' not found!%n", key);
                    break;
                }
            }

        } catch (IOException e) {
            System.out.printf("  ✗ Error streaming file: %s%n", e.getMessage());
        } catch (OutOfMemoryError e) {
            result.isOOM = true;
            result.memoryBytes = Runtime.getRuntime().maxMemory();
            System.out.printf("  ✗ OOM at N=%,d (during Hash Table build)%n", n);
        }

        return result;
    }

    private static BenchmarkResult benchmarkBloomFilterStreaming(String datasetFile, int n) {
        BenchmarkResult result = new BenchmarkResult();
        result.dataStructure = "BloomFilter";
        result.n = n;
        result.memoryBudgetMb = memoryBudgetMb;
        result.isOOM = false;

        try {

            final List<String> sample = new ArrayList<>();
            JsonParser.StreamedQueries streamed = JsonParser.streamParse(datasetFile, key -> {
                if (sample.size() < VERIFY_SAMPLE_SIZE) sample.add(key);
            });
            List<String> negatives = streamed.negatives;
            List<String> mixed = streamed.mixed;
            List<String> insertedSample = sample;

            BloomFilter bf = null;
            double insertTimeTotal = 0;
            long measuredMemMin = Long.MAX_VALUE;
            for (int t = 0; t < INSERT_TRIALS; t++) {
                bf = null;
                forceGC();
                long baseline = usedMemory();

                final BloomFilter b = new BloomFilter(n, BF_TARGET_FPR);
                final long[] insertNanos = {0};
                JsonParser.streamParse(datasetFile, key -> {
                    long t0 = System.nanoTime();
                    b.insert(key);
                    insertNanos[0] += System.nanoTime() - t0;
                });
                insertTimeTotal += insertNanos[0] / 1_000_000.0;

                forceGC();
                long after = usedMemory();
                measuredMemMin = Math.min(measuredMemMin, after - baseline);
                bf = b;
            }
            result.insertTimeMs = insertTimeTotal / INSERT_TRIALS;
            result.mBits = bf.getM();
            result.kHashes = bf.getK();

            result.memoryBytes = Math.max(0, measuredMemMin);
            result.memoryModelBytes = bf.actualJvmMemoryBytes();
            result.fillRatio = bf.getFillRatio();

            final BloomFilter bfRef = bf;
            double[] lk = measureLookup(bfRef::lookup, mixed);
            result.lookupTimeMs = lk[0];
            result.throughputStdevPct = lk[1];
            result.throughputOpsPerSec = lk[2];

            result.negativeQueryCount = negatives.size();
            int fp = 0;
            for (String neg : negatives) {
                if (bf.lookup(neg)) {
                    fp++;
                }
            }
            result.falsePositiveCount = fp;
            result.fprEmpirical = (double) fp / negatives.size();
            result.fprTheoretical = bf.getTheoreticalFPR();

            for (String key : insertedSample) {
                if (!bf.lookup(key)) {
                    System.out.printf("  ⚠ FALSE NEGATIVE ERROR: key '%s' not found!%n", key);
                    break;
                }
            }

        } catch (IOException e) {
            System.out.printf("  ✗ Error streaming file: %s%n", e.getMessage());
        } catch (OutOfMemoryError e) {
            result.isOOM = true;
            result.memoryBytes = Runtime.getRuntime().maxMemory();
            System.out.printf("  ✗ OOM at N=%,d%n", n);
        }

        return result;
    }

    private static void writeHTResults(List<BenchmarkResult> results) {
        String path = RESULTS_DIR + "/hashtable_results.csv";
        boolean fileExists = new File(path).exists() && new File(path).length() > 0;
        try (PrintWriter pw = new PrintWriter(new FileWriter(path, true))) {
            if (!fileExists) {
                pw.println(BenchmarkResult.htCsvHeader());
            }
            for (BenchmarkResult r : results) {
                pw.println(r.htCsvLine());
            }
            System.out.printf("  ✓ Hash Table results → %s%n", path);
        } catch (IOException e) {
            System.out.printf("  ✗ Error writing %s: %s%n", path, e.getMessage());
        }
    }

    private static void writeBFResults(List<BenchmarkResult> results) {
        String path = RESULTS_DIR + "/bloomfilter_results.csv";
        boolean fileExists = new File(path).exists() && new File(path).length() > 0;
        try (PrintWriter pw = new PrintWriter(new FileWriter(path, true))) {
            if (!fileExists) {
                pw.println(BenchmarkResult.bfCsvHeader());
            }
            for (BenchmarkResult r : results) {
                pw.println(r.bfCsvLine());
            }
            System.out.printf("  ✓ Bloom Filter results → %s%n", path);
        } catch (IOException e) {
            System.out.printf("  ✗ Error writing %s: %s%n", path, e.getMessage());
        }
    }

    private static void writeSummary(List<BenchmarkResult> htResults,
            List<BenchmarkResult> bfResults) {
        String path = RESULTS_DIR + "/summary_comparison.csv";
        boolean fileExists = new File(path).exists() && new File(path).length() > 0;
        try (PrintWriter pw = new PrintWriter(new FileWriter(path, true))) {
            if (!fileExists) {
                pw.println(BenchmarkResult.summaryCsvHeader());
            }
            for (BenchmarkResult r : htResults) {
                pw.println(r.summaryCsvLine());
            }
            for (BenchmarkResult r : bfResults) {
                pw.println(r.summaryCsvLine());
            }
            System.out.printf("  ✓ Summary comparison → %s%n", path);
        } catch (IOException e) {
            System.out.printf("  ✗ Error writing %s: %s%n", path, e.getMessage());
        }
    }

    private static void printComparisonTable(List<BenchmarkResult> htResults,
            List<BenchmarkResult> bfResults) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  COMPARISON TABLE: Hash Table vs Bloom Filter");
        System.out.println("=".repeat(70));

        System.out.printf("%-10s │ %-15s %-15s │ %-10s %-10s │ %-8s │ %-8s │ %-5s%n",
                "N", "HT ops/sec", "BF ops/sec",
                "HT KB", "BF KB", "Ratio",
                "HT coll%", "BF FPR");
        System.out.println("─".repeat(95));

        Map<Integer, BenchmarkResult> htMap = new HashMap<>();
        for (BenchmarkResult r : htResults) htMap.put(r.n, r);

        Map<Integer, BenchmarkResult> bfMap = new HashMap<>();
        for (BenchmarkResult r : bfResults) bfMap.put(r.n, r);

        for (int n : ALL_SIZES) {
            BenchmarkResult ht = htMap.get(n);
            BenchmarkResult bf = bfMap.get(n);
            if (ht == null || bf == null) continue;

            String htOps = ht.isOOM ? "OOM" : String.format("%,.0f", ht.throughputOpsPerSec);
            String bfOps = bf.isOOM ? "OOM" : String.format("%,.0f", bf.throughputOpsPerSec);
            String htKb = ht.isOOM ? "OOM" : String.format("%,.1f", ht.memoryBytes / 1024.0);
            String bfKb = bf.isOOM ? "OOM" : String.format("%,.1f", bf.memoryBytes / 1024.0);

            double ratio = 0;
            String ratioStr = "-";
            if (!ht.isOOM && !bf.isOOM && bf.memoryBytes > 0) {
                ratio = (double) ht.memoryBytes / bf.memoryBytes;
                ratioStr = String.format("%.1f×", ratio);
            }

            String collStr = ht.isOOM ? "-" : String.format("%.1f%%", ht.collisionRate * 100);
            String fprStr = bf.isOOM ? "-" : String.format("%.3f%%", bf.fprEmpirical * 100);

            System.out.printf("%-10s │ %-15s %-15s │ %-10s %-10s │ %-8s │ %-8s │ %-5s%n",
                    String.format("%,d", n), htOps, bfOps, htKb, bfKb,
                    ratioStr, collStr, fprStr);
        }
    }

    private static void forceGC() {
        System.gc();
        System.gc();
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
    }

    private static double[] measureLookup(Predicate<String> lookup, List<String> mixed) {
        int size = Math.max(1, mixed.size());
        int repeats = Math.max(1, TARGET_LOOKUP_OPS / size);

        long sink = 0;

        for (int w = 0; w < WARMUP_ITERATIONS; w++) {
            for (String key : mixed) {
                sink += lookup.test(key) ? 1 : 0;
            }
        }

        double[] passMs = new double[LOOKUP_TRIALS];
        for (int t = 0; t < LOOKUP_TRIALS; t++) {
            long start = System.nanoTime();
            for (int r = 0; r < repeats; r++) {
                for (String key : mixed) {
                    sink += lookup.test(key) ? 1 : 0;
                }
            }
            long end = System.nanoTime();
            passMs[t] = (end - start) / 1_000_000.0;
        }
        BLACKHOLE = sink;

        double medianFullMs = median(passMs);
        double perPassMs = medianFullMs / repeats;
        double throughput = size / (perPassMs / 1000.0);
        double stdevPct = stdevPct(passMs);

        return new double[]{perPassMs, stdevPct, throughput};
    }

    private static double median(double[] values) {
        double[] copy = values.clone();
        Arrays.sort(copy);
        int mid = copy.length / 2;
        return copy.length % 2 == 0 ? (copy[mid - 1] + copy[mid]) / 2.0 : copy[mid];
    }

    private static double stdevPct(double[] values) {
        if (values.length < 2) return 0.0;
        double mean = 0;
        for (double v : values) mean += v;
        mean /= values.length;
        if (mean == 0) return 0.0;
        double sumSq = 0;
        for (double v : values) sumSq += (v - mean) * (v - mean);
        double stdev = Math.sqrt(sumSq / (values.length - 1));
        return stdev / mean * 100.0;
    }

    private static long usedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static int calculateM(int n) {
        double ln2sq = Math.log(2) * Math.log(2);
        return (int) Math.ceil(-n * Math.log(BF_TARGET_FPR) / ln2sq);
    }

    private static int calculateK(int n) {
        int m = calculateM(n);
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }
}
