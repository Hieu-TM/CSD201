import java.util.BitSet;

public class BloomFilter {

    private BitSet bitArray;
    private int m;
    private int k;
    private int insertedCount;
    private double targetFPR;

    private static final int FNV_OFFSET_BASIS = 0x811c9dc5;
    private static final int FNV_PRIME = 0x01000193;

    public BloomFilter(int n, double targetFPR) {
        this.targetFPR = targetFPR;
        this.m = calculateM(n, targetFPR);
        this.k = calculateK(n, this.m);
        this.bitArray = new BitSet(this.m);
        this.insertedCount = 0;
    }

    private int calculateM(int n, double p) {
        double ln2sq = Math.log(2) * Math.log(2);
        return (int) Math.ceil(-n * Math.log(p) / ln2sq);
    }

    private int calculateK(int n, int m) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    private int hash1(String key) {
        return key.hashCode() & 0x7FFFFFFF;
    }

    private int hash2(String key) {
        int hash = FNV_OFFSET_BASIS;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= FNV_PRIME;
        }
        hash = hash & 0x7FFFFFFF;

        return hash | 1;
    }

    private int[] getHashPositions(String key) {
        int h1 = hash1(key);
        int h2 = hash2(key);
        int[] positions = new int[k];

        for (int i = 0; i < k; i++) {

            positions[i] = (int) (((long) h1 + (long) i * h2) % m);
            if (positions[i] < 0) positions[i] += m;
        }
        return positions;
    }

    public void insert(String key) {
        int[] positions = getHashPositions(key);
        for (int pos : positions) {
            bitArray.set(pos);
        }
        insertedCount++;
    }

    public boolean lookup(String key) {
        int[] positions = getHashPositions(key);
        for (int pos : positions) {
            if (!bitArray.get(pos)) {
                return false;
            }
        }
        return true;
    }

    public int getBitsSet() {
        return bitArray.cardinality();
    }

    public double getFillRatio() {
        return (double) getBitsSet() / m;
    }

    public double getTheoreticalFPR() {
        double exp = Math.exp(-(double) k * insertedCount / m);
        return Math.pow(1.0 - exp, k);
    }

    public int getM() { return m; }
    public int getK() { return k; }
    public int getInsertedCount() { return insertedCount; }
    public double getTargetFPR() { return targetFPR; }

    public long estimateMemoryBytes() {
        return (long) Math.ceil((double) m / 8);
    }

    public long actualJvmMemoryBytes() {
        long words = (long) Math.ceil((double) m / 64);
        return words * 8 + 32;
    }
}
