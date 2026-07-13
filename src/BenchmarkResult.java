public class BenchmarkResult {

    public String dataStructure;
    public int n;
    public int memoryBudgetMb;
    public double insertTimeMs;
    public double lookupTimeMs;
    public double throughputOpsPerSec;
    public double throughputStdevPct;
    public long memoryBytes;
    public long memoryModelBytes;
    public boolean isOOM;

    public int collisionCount;
    public double collisionRate;
    public int maxChainLength;
    public double avgChainLength;
    public int tableCapacity;
    public int resizeCount;

    public int mBits;
    public int kHashes;
    public double fprEmpirical;
    public double fprTheoretical;
    public int falsePositiveCount;
    public int negativeQueryCount;
    public double fillRatio;

    public static String htCsvHeader() {
        return "memory_budget_mb,n,insert_time_ms,lookup_time_ms,throughput_ops_sec,throughput_stdev_pct," +
               "memory_bytes,memory_kb,memory_model_kb," +
               "collision_count,collision_rate,max_chain_len,avg_chain_len," +
               "table_capacity,resize_count,is_oom";
    }

    public String htCsvLine() {
        return String.format("%d,%d,%.2f,%.2f,%.0f,%.1f,%d,%.1f,%.1f,%d,%.4f,%d,%.2f,%d,%d,%b",
                memoryBudgetMb, n, insertTimeMs, lookupTimeMs, throughputOpsPerSec, throughputStdevPct,
                memoryBytes, memoryBytes / 1024.0, memoryModelBytes / 1024.0,
                collisionCount, collisionRate, maxChainLength, avgChainLength,
                tableCapacity, resizeCount, isOOM);
    }

    public static String bfCsvHeader() {
        return "memory_budget_mb,n,insert_time_ms,lookup_time_ms,throughput_ops_sec,throughput_stdev_pct," +
               "memory_bytes,memory_kb,memory_model_kb," +
               "m_bits,k_hashes,fpr_empirical,fpr_theoretical," +
               "false_positive_count,negative_query_count,fill_ratio,is_oom";
    }

    public String bfCsvLine() {
        return String.format("%d,%d,%.2f,%.2f,%.0f,%.1f,%d,%.1f,%.1f,%d,%d,%.6f,%.6f,%d,%d,%.4f,%b",
                memoryBudgetMb, n, insertTimeMs, lookupTimeMs, throughputOpsPerSec, throughputStdevPct,
                memoryBytes, memoryBytes / 1024.0, memoryModelBytes / 1024.0,
                mBits, kHashes, fprEmpirical, fprTheoretical,
                falsePositiveCount, negativeQueryCount, fillRatio, isOOM);
    }

    public static String summaryCsvHeader() {
        return "memory_budget_mb,n,structure,insert_time_ms,lookup_time_ms,throughput_ops_sec,throughput_stdev_pct," +
               "memory_kb,collision_rate,fpr_empirical,is_oom";
    }

    public String summaryCsvLine() {
        return String.format("%d,%d,%s,%.2f,%.2f,%.0f,%.1f,%.1f,%.4f,%.6f,%b",
                memoryBudgetMb, n, dataStructure, insertTimeMs, lookupTimeMs, throughputOpsPerSec, throughputStdevPct,
                memoryBytes / 1024.0, collisionRate, fprEmpirical, isOOM);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("  %-18s: %s\n", "Data Structure", dataStructure));
        sb.append(String.format("  %-18s: %,d\n", "N", n));
        sb.append(String.format("  %-18s: %.2f ms\n", "Insert Time", insertTimeMs));
        sb.append(String.format("  %-18s: %.2f ms\n", "Lookup Time", lookupTimeMs));
        sb.append(String.format("  %-18s: %,.0f ops/sec (±%.1f%%)\n", "Throughput",
                throughputOpsPerSec, throughputStdevPct));
        sb.append(String.format("  %-18s: %,.1f KB measured (model %,.1f KB)\n", "Memory",
                memoryBytes / 1024.0, memoryModelBytes / 1024.0));

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
