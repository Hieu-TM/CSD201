/**
 * Data class to store benchmark results for a single run.
 * Used by BenchmarkRunner to collect and export results.
 */
public class BenchmarkResult {

    // ─── Common fields ─────────────────────────────────
    public String dataStructure;       // "HashTable" or "BloomFilter"
    public int n;                      // Dataset size
    public int memoryBudgetMb;         // JVM Max Memory limit (MB)
    public double insertTimeMs;        // Total insert time (milliseconds)
    public double lookupTimeMs;        // Total lookup time (milliseconds)
    public double throughputOpsPerSec; // Lookup operations per second
    public long memoryBytes;           // Estimated memory usage (bytes)
    public boolean isOOM;              // Whether OutOfMemoryError occurred

    // ─── Hash Table specific ───────────────────────────
    public int collisionCount;         // Total collisions during insert
    public double collisionRate;       // collisionCount / n
    public int maxChainLength;         // Longest chain in any bucket
    public double avgChainLength;      // Average chain length (non-empty buckets)
    public int tableCapacity;          // Final table capacity
    public int resizeCount;            // Number of resizes performed

    // ─── Bloom Filter specific ─────────────────────────
    public int mBits;                  // Number of bits in bit array
    public int kHashes;                // Number of hash functions
    public double fprEmpirical;        // Measured false positive rate
    public double fprTheoretical;      // Theoretical FPR
    public int falsePositiveCount;     // Number of false positives detected
    public int negativeQueryCount;     // Number of negative queries tested
    public double fillRatio;           // Fraction of bits set

    /**
     * Format this result as a CSV header line for Hash Table.
     */
    public static String htCsvHeader() {
        return "memory_budget_mb,n,insert_time_ms,lookup_time_ms,throughput_ops_sec,memory_bytes,memory_kb," +
               "collision_count,collision_rate,max_chain_len,avg_chain_len," +
               "table_capacity,resize_count,is_oom";
    }

    /**
     * Format this result as a CSV data line for Hash Table.
     */
    public String htCsvLine() {
        return String.format("%d,%d,%.2f,%.2f,%.0f,%d,%.1f,%d,%.4f,%d,%.2f,%d,%d,%b",
                memoryBudgetMb, n, insertTimeMs, lookupTimeMs, throughputOpsPerSec,
                memoryBytes, memoryBytes / 1024.0,
                collisionCount, collisionRate, maxChainLength, avgChainLength,
                tableCapacity, resizeCount, isOOM);
    }

    /**
     * Format this result as a CSV header line for Bloom Filter.
     */
    public static String bfCsvHeader() {
        return "memory_budget_mb,n,insert_time_ms,lookup_time_ms,throughput_ops_sec,memory_bytes,memory_kb," +
               "m_bits,k_hashes,fpr_empirical,fpr_theoretical," +
               "false_positive_count,negative_query_count,fill_ratio,is_oom";
    }

    /**
     * Format this result as a CSV data line for Bloom Filter.
     */
    public String bfCsvLine() {
        return String.format("%d,%d,%.2f,%.2f,%.0f,%d,%.1f,%d,%d,%.6f,%.6f,%d,%d,%.4f,%b",
                memoryBudgetMb, n, insertTimeMs, lookupTimeMs, throughputOpsPerSec,
                memoryBytes, memoryBytes / 1024.0,
                mBits, kHashes, fprEmpirical, fprTheoretical,
                falsePositiveCount, negativeQueryCount, fillRatio, isOOM);
    }

    /**
     * Format this result as a summary CSV header.
     */
    public static String summaryCsvHeader() {
        return "memory_budget_mb,n,structure,insert_time_ms,lookup_time_ms,throughput_ops_sec," +
               "memory_kb,collision_rate,fpr_empirical,is_oom";
    }

    /**
     * Format this result as a summary CSV line.
     */
    public String summaryCsvLine() {
        return String.format("%d,%d,%s,%.2f,%.2f,%.0f,%.1f,%.4f,%.6f,%b",
                memoryBudgetMb, n, dataStructure, insertTimeMs, lookupTimeMs, throughputOpsPerSec,
                memoryBytes / 1024.0, collisionRate, fprEmpirical, isOOM);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("  %-18s: %s\n", "Data Structure", dataStructure));
        sb.append(String.format("  %-18s: %,d\n", "N", n));
        sb.append(String.format("  %-18s: %.2f ms\n", "Insert Time", insertTimeMs));
        sb.append(String.format("  %-18s: %.2f ms\n", "Lookup Time", lookupTimeMs));
        sb.append(String.format("  %-18s: %,.0f ops/sec\n", "Throughput", throughputOpsPerSec));
        sb.append(String.format("  %-18s: %,.1f KB\n", "Memory", memoryBytes / 1024.0));

        if ("HashTable".equals(dataStructure)) {
            sb.append(String.format("  %-18s: %,d (rate=%.2f%%)\n", "Collisions",
                    collisionCount, collisionRate * 100));
            sb.append(String.format("  %-18s: %d (avg=%.2f)\n", "Max Chain",
                    maxChainLength, avgChainLength));
        } else {
            sb.append(String.format("  %-18s: %.4f%% (theory=%.4f%%)\n", "FPR",
                    fprEmpirical * 100, fprTheoretical * 100));
            sb.append(String.format("  %-18s: %d/%d\n", "False Positives",
                    falsePositiveCount, negativeQueryCount));
            sb.append(String.format("  %-18s: %,d bits, k=%d\n", "BF Params", mBits, kHashes));
        }

        if (isOOM) {
            sb.append("  *** OUT OF MEMORY ***\n");
        }

        return sb.toString();
    }
}
