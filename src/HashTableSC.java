import java.util.LinkedList;

public class HashTableSC {

    private static class Node {
        String key;
        Node next;

        Node(String key) {
            this.key = key;
            this.next = null;
        }
    }

    private Node[] table;
    private int size;
    private int capacity;
    private int collisionCount;
    private int resizeCount;
    private static final double LOAD_FACTOR_THRESHOLD = 0.75;

    private static final int NODE_OVERHEAD = 32;
    private static final int STRING_OVERHEAD = 40;
    private static final int REF_SIZE = 8;

    public HashTableSC(int initialCapacity) {
        this.capacity = nextPrime(initialCapacity);
        this.table = new Node[this.capacity];
        this.size = 0;
        this.collisionCount = 0;
        this.resizeCount = 0;
    }

    private int hash(String key) {
        int h = key.hashCode();

        h = h & 0x7FFFFFFF;
        return h % capacity;
    }

    public boolean insert(String key) {

        if ((double)(size + 1) / capacity > LOAD_FACTOR_THRESHOLD) {
            resize();
        }

        int index = hash(key);
        boolean collision = false;

        if (table[index] != null) {
            collision = true;
            collisionCount++;

            Node current = table[index];
            while (current != null) {
                if (current.key.equals(key)) {
                    return collision;
                }
                current = current.next;
            }
        }

        Node newNode = new Node(key);
        newNode.next = table[index];
        table[index] = newNode;
        size++;

        return collision;
    }

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

    private void resize() {
        int newCapacity = nextPrime(capacity * 2);
        Node[] oldTable = table;
        int oldCapacity = capacity;

        table = new Node[newCapacity];
        capacity = newCapacity;
        size = 0;

        for (int i = 0; i < oldCapacity; i++) {
            Node current = oldTable[i];
            while (current != null) {
                Node next = current.next;

                int index = hash(current.key);
                current.next = table[index];
                table[index] = current;
                size++;
                current = next;
            }
        }
        resizeCount++;
    }

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

    public int getSize() { return size; }
    public int getCapacity() { return capacity; }
    public int getCollisionCount() { return collisionCount; }
    public int getResizeCount() { return resizeCount; }

    public double getCollisionRate() {
        return size == 0 ? 0.0 : (double) collisionCount / size;
    }

    public double getLoadFactor() {
        return (double) size / capacity;
    }

    public long estimateMemoryBytes() {
        long arrayMem = (long) capacity * REF_SIZE;
        long nodeMem = (long) size * NODE_OVERHEAD;
        long keyMem = (long) size * (STRING_OVERHEAD + 12);
        return arrayMem + nodeMem + keyMem;
    }
}
