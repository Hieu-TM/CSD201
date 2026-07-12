import java.util.LinkedList;

/**
 * Hash Table with Separate Chaining collision resolution.
 * 
 * Features:
 * - Dynamic resizing when load factor exceeds threshold
 * - Collision counting for performance analysis
 * - Memory usage estimation
 * 
 * Hash function: Java's String.hashCode() with modular compression
 * Load factor threshold: 0.75 (industry standard)
 */
public class HashTableSC {

    /**
     * Node in the linked list chain.
     */
    private static class Node {
        String key;
        Node next;

        Node(String key) {
            this.key = key;
            this.next = null;
        }
    }

    private Node[] table;           // Array of linked list heads
    private int size;               // Number of elements inserted
    private int capacity;           // Current table capacity
    private int collisionCount;     // Total collisions during inserts
    private int resizeCount;        // Number of times table was resized
    private static final double LOAD_FACTOR_THRESHOLD = 0.75;

    // Memory estimation constants (approximate for 64-bit JVM)
    private static final int NODE_OVERHEAD = 32;      // Object header + key ref + next ref + padding
    private static final int STRING_OVERHEAD = 40;    // String object overhead (header + char[] ref + hash + length)
    private static final int REF_SIZE = 8;            // Reference size on 64-bit JVM

    /**
     * Create a Hash Table with the given initial capacity.
     * @param initialCapacity Initial number of buckets
     */
    public HashTableSC(int initialCapacity) {
        this.capacity = nextPrime(initialCapacity);
        this.table = new Node[this.capacity];
        this.size = 0;
        this.collisionCount = 0;
        this.resizeCount = 0;
    }

    /**
     * Compute hash index for a key.
     * Uses Java's String.hashCode() with absolute value and modular compression.
     */
    private int hash(String key) {
        int h = key.hashCode();
        // Ensure non-negative: handle Integer.MIN_VALUE case
        h = h & 0x7FFFFFFF;
        return h % capacity;
    }

    /**
     * Insert a key into the hash table.
     * Counts collision if the bucket is non-empty.
     * Performs resize if load factor exceeds threshold.
     * 
     * @param key The key to insert
     * @return true if a collision occurred, false otherwise
     */
    public boolean insert(String key) {
        // Check load factor and resize if needed
        if ((double)(size + 1) / capacity > LOAD_FACTOR_THRESHOLD) {
            resize();
        }

        int index = hash(key);
        boolean collision = false;

        // Check if bucket is non-empty (collision)
        if (table[index] != null) {
            collision = true;
            collisionCount++;

            // Check if key already exists (no duplicate insert)
            Node current = table[index];
            while (current != null) {
                if (current.key.equals(key)) {
                    return collision; // Key already exists
                }
                current = current.next;
            }
        }

        // Insert at head of chain (O(1))
        Node newNode = new Node(key);
        newNode.next = table[index];
        table[index] = newNode;
        size++;

        return collision;
    }

    /**
     * Look up a key in the hash table.
     * @param key The key to search for
     * @return true if found, false otherwise
     */
    public boolean lookup(String key) {
        int index = hash(key);
        Node current = table[index];

        while (current != null) {
            if (current.key.equals(key)) {
                return true;
            }
            current = current.next;
        }
        return false;
    }

    /**
     * Resize the table to approximately double the capacity (next prime).
     * Rehashes all existing elements.
     */
    private void resize() {
        int newCapacity = nextPrime(capacity * 2);
        Node[] oldTable = table;
        int oldCapacity = capacity;

        table = new Node[newCapacity];
        capacity = newCapacity;
        size = 0;
        // Note: don't reset collisionCount — we keep cumulative count

        // Rehash all elements
        for (int i = 0; i < oldCapacity; i++) {
            Node current = oldTable[i];
            while (current != null) {
                Node next = current.next;
                // Direct insert without collision counting during rehash
                int index = hash(current.key);
                current.next = table[index];
                table[index] = current;
                size++;
                current = next;
            }
        }
        resizeCount++;
    }

    /**
     * Find the next prime number >= n.
     * Using prime table sizes improves hash distribution.
     */
    private int nextPrime(int n) {
        if (n <= 2) return 2;
        if (n % 2 == 0) n++;
        while (!isPrime(n)) {
            n += 2;
        }
        return n;
    }

    private boolean isPrime(int n) {
        if (n < 2) return false;
        if (n == 2 || n == 3) return true;
        if (n % 2 == 0 || n % 3 == 0) return false;
        for (int i = 5; i * i <= n; i += 6) {
            if (n % i == 0 || n % (i + 2) == 0) return false;
        }
        return true;
    }

    /**
     * Get the maximum chain length across all buckets.
     * Useful for analyzing worst-case lookup time.
     */
    public int getMaxChainLength() {
        int maxLen = 0;
        for (int i = 0; i < capacity; i++) {
            int len = 0;
            Node current = table[i];
            while (current != null) {
                len++;
                current = current.next;
            }
            if (len > maxLen) maxLen = len;
        }
        return maxLen;
    }

    /**
     * Get the average chain length (only non-empty buckets).
     */
    public double getAvgChainLength() {
        int nonEmpty = 0;
        int totalLen = 0;
        for (int i = 0; i < capacity; i++) {
            int len = 0;
            Node current = table[i];
            while (current != null) {
                len++;
                current = current.next;
            }
            if (len > 0) {
                nonEmpty++;
                totalLen += len;
            }
        }
        return nonEmpty == 0 ? 0.0 : (double) totalLen / nonEmpty;
    }

    // ─── Getters ───────────────────────────────────────

    public int getSize() { return size; }
    public int getCapacity() { return capacity; }
    public int getCollisionCount() { return collisionCount; }
    public int getResizeCount() { return resizeCount; }

    /**
     * Collision rate = collisions / total insertions.
     */
    public double getCollisionRate() {
        return size == 0 ? 0.0 : (double) collisionCount / size;
    }

    /**
     * Current load factor = size / capacity.
     */
    public double getLoadFactor() {
        return (double) size / capacity;
    }

    /**
     * Estimate memory usage in bytes.
     * Formula: table_array + (size * node_overhead) + (size * avg_key_bytes)
     */
    public long estimateMemoryBytes() {
        long arrayMem = (long) capacity * REF_SIZE;      // Node[] references
        long nodeMem = (long) size * NODE_OVERHEAD;       // Node objects
        long keyMem = (long) size * (STRING_OVERHEAD + 12); // String objects (~12 bytes avg content)
        return arrayMem + nodeMem + keyMem;
    }
}
