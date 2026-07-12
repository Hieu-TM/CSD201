import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Main benchmark runner: Hash Table (Separate Chaining) vs Bloom Filter.
 * 
 * For each dataset size N (1K → 2M):
 *   1. Load dataset from JSON
 *   2. Benchmark Hash Table: insert, lookup, measure collisions & memory
 *   3. Benchmark Bloom Filter: insert, lookup, measure FPR & memory
 *   4. Write results to CSV
 * 
 * Usage:
 *   java BenchmarkRunner                     (run all sizes)
 *   java BenchmarkRunner 1000 5000 10000     (run specific sizes)
 * 
 * JVM flags for memory limit:
 *   java -Xmx16m BenchmarkRunner            (16MB hard limit)
 *   java -Xmx64m BenchmarkRunner            (64MB hard limit)
 */
public class BenchmarkRunner {

    // Dataset directory relative to working directory
    private static final String DATASET_DIR = "datasets";
    private static final String RESULTS_DIR = "results";

    // All benchmark sizes
    private static final int[] ALL_SIZES = {
        1_000, 5_000, 10_000, 25_000, 50_000,
        100_000, 250_000, 500_000, 1_000_000, 2_000_000
    };

    // Bloom Filter target FPR
    private static final double BF_TARGET_FPR = 0.01; // 1%

    // Number of warmup iterations for lookup timing
    private static final int WARMUP_ITERATIONS = 2;

    // Memory budget being tested
    private static int memoryBudgetMb = 256;

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("  RBL BENCHMARK: Hash Table (Separate Chaining) vs Bloom Filter");
        System.out.println("  CSD201 — Summer 2026 | Nhom 2 SE20B02");
        System.out.println("=".repeat(70));

        // Determine which sizes to benchmark and parse flags
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

        // Print JVM memory info
        Runtime rt = Runtime.getRuntime();
        System.out.printf("\n  JVM Max Memory : %,d MB%n", rt.maxMemory() / (1024 * 1024));
        System.out.printf("  JVM Total Memory: %,d MB%n", rt.totalMemory() / (1024 * 1024));
        System.out.printf("  Available CPUs : %d%n", rt.availableProcessors());
        System.out.printf("  Dataset dir    : %s%n", new File(DATASET_DIR).getAbsolutePath());
        System.out.printf("  Sizes to test  : %s%n", Arrays.toString(sizes));

        // Create results directory
        new File(RESULTS_DIR).mkdirs();

        // Collect results
        List<BenchmarkResult> htResults = new ArrayList<>();
        List<BenchmarkResult> bfResults = new ArrayList<>();

        // Run benchmarks for each size
        for (int n : sizes) {
            System.out.println("\n" + "─".repeat(70));
            System.out.printf("  BENCHMARK N = %,d%n", n);
            System.out.println("─".repeat(70));

            String datasetFile = DATASET_DIR + "/username_n" + n + ".json";
            if (!new File(datasetFile).exists()) {
                System.out.printf("  ⚠ File not found: %s — SKIPPING%n", datasetFile);
                continue;
            }

            // Load dataset
            System.out.printf("  Loading %s ...%n", datasetFile);
            JsonParser parser;
            try {
                parser = new JsonParser().parse(datasetFile);
            } catch (IOException e) {
                System.out.printf("  ✗ Error reading file: %s%n", e.getMessage());
                continue;
            } catch (OutOfMemoryError e) {
                System.out.printf("  ✗ OOM while loading JSON (N=%,d)%n", n);
                // Record OOM for both
                BenchmarkResult htOom = createOOMResult("HashTable", n);
                BenchmarkResult bfOom = createOOMResult("BloomFilter", n);
                htResults.add(htOom);
                bfResults.add(bfOom);
                continue;
            }

            List<String> inserted = parser.getInserted();
            List<String> negatives = parser.getNegativeQueries();
            List<String> mixed = parser.getMixedQueries();

            System.out.printf("  Loaded: %,d inserted, %,d negatives, %,d mixed%n",
                    inserted.size(), negatives.size(), mixed.size());

            // ─── Hash Table Benchmark ───────────────────────
            System.out.println("\n  [HT] Hash Table Separate Chaining:");
            BenchmarkResult htResult = benchmarkHashTable(inserted, mixed, n);
            htResults.add(htResult);
            System.out.println(htResult);

            // Force GC between runs
            forceGC();

            // ─── Bloom Filter Benchmark ─────────────────────
            System.out.println("  [BF] Bloom Filter (k=" + calculateK(n) + ", m=" + 
                    String.format("%,d", calculateM(n)) + " bits):");
            BenchmarkResult bfResult = benchmarkBloomFilter(inserted, negatives, mixed, n);
            bfResults.add(bfResult);
            System.out.println(bfResult);

            // Force GC after each size
            forceGC();
        }

        // Write results to CSV
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  WRITING RESULTS");
        System.out.println("=".repeat(70));

        writeHTResults(htResults);
        writeBFResults(bfResults);
        writeSummary(htResults, bfResults);

        // Print comparison table
        printComparisonTable(htResults, bfResults);

        System.out.println("\n✅ Benchmark complete! Results saved to: " + 
                new File(RESULTS_DIR).getAbsolutePath());
    }

    // ─── Hash Table Benchmark ──────────────────────────────────

    private static BenchmarkResult benchmarkHashTable(List<String> inserted, 
            List<String> mixed, int n) {
        BenchmarkResult result = new BenchmarkResult();
        result.dataStructure = "HashTable";
        result.n = n;
        result.memoryBudgetMb = memoryBudgetMb;
        result.isOOM = false;

        try {
            // Measure memory before
            forceGC();
            long memBefore = usedMemory();

            // Create and populate hash table
            HashTableSC ht = new HashTableSC(n);

            // ─── INSERT benchmark ───
            long insertStart = System.nanoTime();
            for (String key : inserted) {
                ht.insert(key);
            }
            long insertEnd = System.nanoTime();
            result.insertTimeMs = (insertEnd - insertStart) / 1_000_000.0;

            // Measure memory after insert
            forceGC();
            long memAfter = usedMemory();
            long measuredMem = memAfter - memBefore;
            long estimatedMem = ht.estimateMemoryBytes();
            // Use the larger of measured vs estimated (measured can be noisy)
            result.memoryBytes = Math.max(measuredMem, estimatedMem);

            // Collision stats
            result.collisionCount = ht.getCollisionCount();
            result.collisionRate = ht.getCollisionRate();
            result.maxChainLength = ht.getMaxChainLength();
            result.avgChainLength = ht.getAvgChainLength();
            result.tableCapacity = ht.getCapacity();
            result.resizeCount = ht.getResizeCount();

            // ─── LOOKUP benchmark (warmup + timed) ───
            // Warmup
            for (int w = 0; w < WARMUP_ITERATIONS; w++) {
                for (String key : mixed) {
                    ht.lookup(key);
                }
            }

            // Timed lookup
            long lookupStart = System.nanoTime();
            for (String key : mixed) {
                ht.lookup(key);
            }
            long lookupEnd = System.nanoTime();
            result.lookupTimeMs = (lookupEnd - lookupStart) / 1_000_000.0;
            result.throughputOpsPerSec = mixed.size() / (result.lookupTimeMs / 1000.0);

            // Verify correctness: all inserted keys should be found
            int verifyCount = Math.min(100, inserted.size());
            for (int i = 0; i < verifyCount; i++) {
                if (!ht.lookup(inserted.get(i))) {
                    System.out.printf("  ⚠ CORRECTNESS ERROR: key '%s' not found!%n", 
                            inserted.get(i));
                    break;
                }
            }

        } catch (OutOfMemoryError e) {
            result.isOOM = true;
            result.memoryBytes = Runtime.getRuntime().maxMemory();
            System.out.printf("  ✗ OOM at N=%,d%n", n);
        }

        return result;
    }

    // ─── Bloom Filter Benchmark ────────────────────────────────

    private static BenchmarkResult benchmarkBloomFilter(List<String> inserted,
            List<String> negatives, List<String> mixed, int n) {
        BenchmarkResult result = new BenchmarkResult();
        result.dataStructure = "BloomFilter";
        result.n = n;
        result.memoryBudgetMb = memoryBudgetMb;
        result.isOOM = false;

        try {
            // Create Bloom Filter
            BloomFilter bf = new BloomFilter(n, BF_TARGET_FPR);
            result.mBits = bf.getM();
            result.kHashes = bf.getK();

            // ─── INSERT benchmark ───
            long insertStart = System.nanoTime();
            for (String key : inserted) {
                bf.insert(key);
            }
            long insertEnd = System.nanoTime();
            result.insertTimeMs = (insertEnd - insertStart) / 1_000_000.0;

            // Memory
            result.memoryBytes = bf.estimateMemoryBytes();
            result.fillRatio = bf.getFillRatio();

            // ─── LOOKUP benchmark (warmup + timed) ───
            // Warmup
            for (int w = 0; w < WARMUP_ITERATIONS; w++) {
                for (String key : mixed) {
                    bf.lookup(key);
                }
            }

            // Timed lookup
            long lookupStart = System.nanoTime();
            for (String key : mixed) {
                bf.lookup(key);
            }
            long lookupEnd = System.nanoTime();
            result.lookupTimeMs = (lookupEnd - lookupStart) / 1_000_000.0;
            result.throughputOpsPerSec = mixed.size() / (result.lookupTimeMs / 1000.0);

            // ─── FPR measurement using pure negatives ───
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

            // Verify: no false negatives (all inserted must be found)
            int verifyCount = Math.min(100, inserted.size());
            for (int i = 0; i < verifyCount; i++) {
                if (!bf.lookup(inserted.get(i))) {
                    System.out.printf("  ⚠ FALSE NEGATIVE ERROR: key '%s' not found!%n",
                            inserted.get(i));
                    break;
                }
            }

        } catch (OutOfMemoryError e) {
            result.isOOM = true;
            result.memoryBytes = Runtime.getRuntime().maxMemory();
            System.out.printf("  ✗ OOM at N=%,d%n", n);
        }

        return result;
    }

    // ─── Result Output ─────────────────────────────────────────

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

    // ─── Comparison Table ──────────────────────────────────────

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

    // ─── Utility Methods ───────────────────────────────────────

    private static BenchmarkResult createOOMResult(String ds, int n) {
        BenchmarkResult r = new BenchmarkResult();
        r.dataStructure = ds;
        r.n = n;
        r.memoryBudgetMb = memoryBudgetMb;
        r.isOOM = true;
        r.memoryBytes = Runtime.getRuntime().maxMemory();
        return r;
    }

    private static void forceGC() {
        System.gc();
        System.gc();
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
    }

    private static long usedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    /**
     * Calculate m (bits) for Bloom Filter using Mitzenmacher formula.
     */
    private static int calculateM(int n) {
        double ln2sq = Math.log(2) * Math.log(2);
        return (int) Math.ceil(-n * Math.log(BF_TARGET_FPR) / ln2sq);
    }

    /**
     * Calculate k (hash functions) for Bloom Filter.
     */
    private static int calculateK(int n) {
        int m = calculateM(n);
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }
}
