import java.util.BitSet;

/**
 * Bloom Filter implementation using a bit array and k hash functions.
 *
 * Parameters are calculated using the Mitzenmacher formula:
 *   m = ceil(-n * ln(p) / (ln2)^2)    — number of bits
 *   k = round((m/n) * ln(2))          — number of hash functions
 *
 * Hash functions are generated using the double hashing technique:
 *   h_i(key) = (h1(key) + i * h2(key)) mod m
 * where h1 = Java hashCode, h2 = FNV-1a variant
 *
 * Reference: Kirsch & Mitzenmacher, "Less Hashing, Same Performance"
 */
public class BloomFilter {

    // BitSet packs bits into long[] words (1 bit/bit), unlike boolean[] which
    // costs 1 BYTE per element in the JVM — that 8x gap previously made the
    // measured memory ~8x worse than the theoretical m/8 figure reported.
    private BitSet bitArray;
    private int m;                  // Number of bits
    private int k;                  // Number of hash functions
    private int insertedCount;      // Number of elements inserted
    private double targetFPR;       // Target false positive rate

    // FNV-1a constants for 32-bit
    private static final int FNV_OFFSET_BASIS = 0x811c9dc5;
    private static final int FNV_PRIME = 0x01000193;

    /**
     * Create a Bloom Filter optimized for n elements with target FPR.
     *
     * @param n Expected number of elements
     * @param targetFPR Target false positive rate (e.g., 0.01 for 1%)
     */
    public BloomFilter(int n, double targetFPR) {
        this.targetFPR = targetFPR;
        this.m = calculateM(n, targetFPR);
        this.k = calculateK(n, this.m);
        this.bitArray = new BitSet(this.m);
        this.insertedCount = 0;
    }

    /**
     * Calculate optimal number of bits m.
     * m = ceil(-n * ln(p) / (ln(2))^2)
     */
    private int calculateM(int n, double p) {
        double ln2sq = Math.log(2) * Math.log(2);
        return (int) Math.ceil(-n * Math.log(p) / ln2sq);
    }

    /**
     * Calculate optimal number of hash functions k.
     * k = round((m/n) * ln(2))
     */
    private int calculateK(int n, int m) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    /**
     * Compute the primary hash (h1) using Java's hashCode.
     * Returns a non-negative value.
     */
    private int hash1(String key) {
        return key.hashCode() & 0x7FFFFFFF;
    }

    /**
     * Compute the secondary hash (h2) using FNV-1a algorithm.
     * Returns a non-negative value, always odd (for better distribution).
     */
    private int hash2(String key) {
        int hash = FNV_OFFSET_BASIS;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= FNV_PRIME;
        }
        hash = hash & 0x7FFFFFFF;
        // Ensure hash2 is odd to guarantee full period when combined with even m
        return hash | 1;
    }

    /**
     * Get k hash positions for a key using double hashing.
     * h_i(key) = (h1 + i * h2) mod m, for i = 0, 1, ..., k-1
     * 
     * @param key The key to hash
     * @return Array of k bit positions
     */
    private int[] getHashPositions(String key) {
        int h1 = hash1(key);
        int h2 = hash2(key);
        int[] positions = new int[k];

        for (int i = 0; i < k; i++) {
            // Use long to prevent int overflow
            positions[i] = (int) (((long) h1 + (long) i * h2) % m);
            if (positions[i] < 0) positions[i] += m;
        }
        return positions;
    }

    /**
     * Insert a key into the Bloom Filter.
     * Sets k bits in the bit array.
     * 
     * @param key The key to insert
     */
    public void insert(String key) {
        int[] positions = getHashPositions(key);
        for (int pos : positions) {
            bitArray.set(pos);
        }
        insertedCount++;
    }

    /**
     * Look up a key in the Bloom Filter.
     * Checks if all k bits are set.
     * 
     * @param key The key to look up
     * @return true if the key MIGHT be in the set (possible false positive),
     *         false if the key is DEFINITELY NOT in the set
     */
    public boolean lookup(String key) {
        int[] positions = getHashPositions(key);
        for (int pos : positions) {
            if (!bitArray.get(pos)) {
                return false; // Definitely not in set
            }
        }
        return true; // Probably in set (may be false positive)
    }

    /**
     * Count how many bits are set in the bit array.
     */
    public int getBitsSet() {
        return bitArray.cardinality();
    }

    /**
     * Get the fill ratio (fraction of bits that are set).
     */
    public double getFillRatio() {
        return (double) getBitsSet() / m;
    }

    /**
     * Calculate the theoretical FPR based on actual parameters.
     * FPR = (1 - e^(-k*n/m))^k
     */
    public double getTheoreticalFPR() {
        double exp = Math.exp(-(double) k * insertedCount / m);
        return Math.pow(1.0 - exp, k);
    }

    // ─── Getters ───────────────────────────────────────

    public int getM() { return m; }
    public int getK() { return k; }
    public int getInsertedCount() { return insertedCount; }
    public double getTargetFPR() { return targetFPR; }

    /**
     * Theoretical minimum memory in bytes = m / 8 (bit-packed, no overhead).
     */
    public long estimateMemoryBytes() {
        return (long) Math.ceil((double) m / 8);
    }

    /**
     * Real JVM memory for the BitSet: bits are packed into long[] words
     * (8 bytes/64 bits) plus a small fixed object/array-header overhead.
     * This is what BenchmarkRunner reports, so HashTable and BloomFilter
     * are compared on the same basis (real heap footprint), not a mix of
     * measured-vs-theoretical numbers.
     */
    public long actualJvmMemoryBytes() {
        long words = (long) Math.ceil((double) m / 64);
        return words * 8 + 32; // 8 bytes/word + object/array header
    }
}
