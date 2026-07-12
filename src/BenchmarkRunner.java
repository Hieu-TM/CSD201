import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;

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

    // Number of warmup iterations for lookup timing (enough to let the JIT
    // compile the lookup loop to C2 before any timed pass).
    private static final int WARMUP_ITERATIONS = 10;

    // Number of fresh-build trials for insert timing / memory (averaged / min)
    private static final int INSERT_TRIALS = 3;

    // Number of timed passes for lookup timing (median taken)
    private static final int LOOKUP_TRIALS = 7;

    // Each timed lookup pass repeats the mixed-query set until it performs at
    // least this many operations, so the measured interval is tens of ms —
    // well above timer/GC noise. Without this, a single 5,000-query pass takes
    // ~0.2 ms and throughput swings ~10x on pure measurement noise.
    private static final int TARGET_LOOKUP_OPS = 2_000_000;

    // How many inserted keys to sample for the correctness check
    private static final int VERIFY_SAMPLE_SIZE = 1000;

    // Blackhole: consuming lookup results here prevents the JIT from proving
    // the lookup calls are side-effect-free and eliminating the whole loop
    // (dead-code elimination), which otherwise inflates throughput arbitrarily.
    static volatile long BLACKHOLE;

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

            // ─── Bloom Filter Benchmark (streamed — never holds the full
            // N-element "inserted" list in memory) ──────────────────────
            // Benchmarked first and independently of the Hash Table so its
            // OOM threshold reflects its own memory footprint, not the cost
            // of materializing a shared dataset list.
            System.out.println("\n  [BF] Bloom Filter (k=" + calculateK(n) + ", m=" +
                    String.format("%,d", calculateM(n)) + " bits):");
            BenchmarkResult bfResult = benchmarkBloomFilterStreaming(datasetFile, n);
            bfResults.add(bfResult);
            System.out.println(bfResult);

            forceGC();

            // ─── Hash Table Benchmark (streamed) ─────────────
            // Built via the same streaming path as the Bloom Filter so the two
            // are compared on equal footing: the Hash Table's OOM threshold now
            // reflects its own footprint (Node[] + nodes + retained key
            // Strings), not the extra cost of materializing a full parsed list.
            System.out.println("\n  [HT] Hash Table Separate Chaining:");
            BenchmarkResult htResult = benchmarkHashTableStreaming(datasetFile, n);
            htResults.add(htResult);
            System.out.println(htResult);

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

    // ─── Hash Table Benchmark (streamed) ───────────────────────

    /**
     * Benchmarks the Hash Table by streaming the dataset file directly, exactly
     * like {@link #benchmarkBloomFilterStreaming}. The Hash Table retains every
     * key as a live object, so its measured heap footprint (and therefore its
     * OOM threshold) genuinely includes those Strings — but the source list is
     * never materialized separately, so the OOM reflects the structure itself
     * rather than the cost of a full JSON parse. Memory is the real measured
     * Runtime heap delta (min over trials), not a fixed per-element model.
     */
    private static BenchmarkResult benchmarkHashTableStreaming(String datasetFile, int n) {
        BenchmarkResult result = new BenchmarkResult();
        result.dataStructure = "HashTable";
        result.n = n;
        result.memoryBudgetMb = memoryBudgetMb;
        result.isOOM = false;

        try {
            // ─── Pass A: collect the fixed-size query list + a verify sample.
            // These are kept live, so they sit in BOTH the baseline and the
            // post-build snapshot and cancel out of the memory delta.
            final List<String> sample = new ArrayList<>();
            JsonParser.StreamedQueries streamed = JsonParser.streamParse(datasetFile, key -> {
                if (sample.size() < VERIFY_SAMPLE_SIZE) sample.add(key);
            });
            List<String> mixed = streamed.mixed; // negatives unused for the Hash Table

            // ─── INSERT benchmark + real memory measurement.
            // Rebuild fresh INSERT_TRIALS times (re-streaming the file); average
            // the insert time and take the MIN measured heap delta (least GC
            // noise). Only the last build is kept for lookup/collision stats.
            HashTableSC ht = null;
            double insertTimeTotal = 0;
            long measuredMemMin = Long.MAX_VALUE;
            for (int t = 0; t < INSERT_TRIALS; t++) {
                ht = null;            // release the previous build first
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
                ht = h;               // keep this build
            }
            result.insertTimeMs = insertTimeTotal / INSERT_TRIALS;
            result.memoryBytes = Math.max(0, measuredMemMin);
            result.memoryModelBytes = ht.estimateMemoryBytes();

            // Collision stats (from the retained build)
            result.collisionCount = ht.getCollisionCount();
            result.collisionRate = ht.getCollisionRate();
            result.maxChainLength = ht.getMaxChainLength();
            result.avgChainLength = ht.getAvgChainLength();
            result.tableCapacity = ht.getCapacity();
            result.resizeCount = ht.getResizeCount();

            // ─── LOOKUP benchmark (large workload, blackholed, median) ───
            final HashTableSC htRef = ht;
            double[] lk = measureLookup(htRef::lookup, mixed);
            result.lookupTimeMs = lk[0];
            result.throughputStdevPct = lk[1];
            result.throughputOpsPerSec = lk[2];

            // Verify correctness: sampled inserted keys should be found
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

    // ─── Bloom Filter Benchmark (streamed) ─────────────────────

    /**
     * Benchmarks the Bloom Filter by streaming the dataset file directly
     * (see {@link JsonParser#streamParse}) instead of using a pre-parsed
     * "inserted" list. This keeps the Bloom Filter's memory footprint (and
     * therefore its OOM threshold) independent of the Hash Table benchmark's
     * dataset-loading cost — previously both structures shared one parsed
     * list, so at low memory budgets the OOM always happened during JSON
     * parsing before either structure was tested, making it look like both
     * structures failed together regardless of their actual memory models.
     */
    private static BenchmarkResult benchmarkBloomFilterStreaming(String datasetFile, int n) {
        BenchmarkResult result = new BenchmarkResult();
        result.dataStructure = "BloomFilter";
        result.n = n;
        result.memoryBudgetMb = memoryBudgetMb;
        result.isOOM = false;

        try {
            // ─── Pass A: collect the fixed-size query lists + a verify sample
            // (kept live, so they cancel out of the memory delta below).
            final List<String> sample = new ArrayList<>();
            JsonParser.StreamedQueries streamed = JsonParser.streamParse(datasetFile, key -> {
                if (sample.size() < VERIFY_SAMPLE_SIZE) sample.add(key);
            });
            List<String> negatives = streamed.negatives;
            List<String> mixed = streamed.mixed;
            List<String> insertedSample = sample;

            // ─── INSERT benchmark + memory measurement, using the SAME
            // protocol as the Hash Table so both are compared on one basis:
            // rebuild fresh INSERT_TRIALS times (re-streaming), average the
            // insert() time, and take the MIN measured heap delta. Only
            // insert() time is measured (I/O and parsing excluded). Because the
            // Bloom Filter does not retain the key Strings, this delta captures
            // just the BitSet.
            BloomFilter bf = null;
            double insertTimeTotal = 0;
            long measuredMemMin = Long.MAX_VALUE;
            for (int t = 0; t < INSERT_TRIALS; t++) {
                bf = null;            // release the previous build first
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
                bf = b;               // keep this build
            }
            result.insertTimeMs = insertTimeTotal / INSERT_TRIALS;
            result.mBits = bf.getM();
            result.kHashes = bf.getK();

            // Memory: measured heap delta (min over trials), same basis as the
            // Hash Table. The measured column is noise-dominated at small N
            // because the BitSet is tiny (~KB < GC noise); the analytic
            // BitSet footprint (memoryModelBytes) is exact and is the figure to
            // trust for the Bloom Filter — see DATASET_DOCUMENTATION.md §5.
            result.memoryBytes = Math.max(0, measuredMemMin);
            result.memoryModelBytes = bf.actualJvmMemoryBytes();
            result.fillRatio = bf.getFillRatio();

            // ─── LOOKUP benchmark (large workload, blackholed, median) ───
            final BloomFilter bfRef = bf;
            double[] lk = measureLookup(bfRef::lookup, mixed);
            result.lookupTimeMs = lk[0];
            result.throughputStdevPct = lk[1];
            result.throughputOpsPerSec = lk[2];

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

            // Verify: no false negatives among the sampled inserted keys
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

    private static void forceGC() {
        System.gc();
        System.gc();
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
    }

    /**
     * Times a lookup workload robustly and returns
     * {@code [perPassMs, throughputStdevPct, throughputOpsPerSec]}.
     *
     * Each timed pass repeats the mixed-query set enough times to perform at
     * least {@link #TARGET_LOOKUP_OPS} operations, so the interval is well above
     * timer/GC noise. Results are consumed into {@link #BLACKHOLE} so the JIT
     * cannot eliminate the loop. Throughput is derived from the MEDIAN pass
     * (robust to a single GC pause); the reported per-pass time is normalized
     * back to one sweep of {@code mixed} so it stays comparable across N.
     */
    private static double[] measureLookup(Predicate<String> lookup, List<String> mixed) {
        int size = Math.max(1, mixed.size());
        int repeats = Math.max(1, TARGET_LOOKUP_OPS / size);

        long sink = 0;

        // Warmup (untimed) — trigger JIT compilation of the lookup loop.
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
        double perPassMs = medianFullMs / repeats;        // one sweep of `mixed`
        double throughput = size / (perPassMs / 1000.0);  // ops/sec
        double stdevPct = stdevPct(passMs);

        return new double[]{perPassMs, stdevPct, throughput};
    }

    private static double median(double[] values) {
        double[] copy = values.clone();
        Arrays.sort(copy);
        int mid = copy.length / 2;
        return copy.length % 2 == 0 ? (copy[mid - 1] + copy[mid]) / 2.0 : copy[mid];
    }

    /** Sample standard deviation as a percentage of the mean. */
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
