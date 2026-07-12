import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Simple JSON parser for the RBL dataset files.
 * Parses fixed-format JSON without external libraries.
 *
 * Expected JSON structure:
 * {
 *   "dataset_type": "username",
 *   "n": 1000,
 *   "inserted": ["key1", "key2", ...],
 *   "negative_queries": ["neg1", "neg2", ...],
 *   "mixed_queries": ["mix1", "mix2", ...]
 * }
 */
public class JsonParser {

    private String datasetType;
    private int n;
    private List<String> inserted;
    private List<String> negativeQueries;
    private List<String> mixedQueries;

    public JsonParser() {
        this.inserted = new ArrayList<>();
        this.negativeQueries = new ArrayList<>();
        this.mixedQueries = new ArrayList<>();
    }

    /**
     * Parse a dataset JSON file.
     * @param filePath Path to the JSON file
     * @return this JsonParser instance (for chaining)
     * @throws IOException if file cannot be read
     */
    public JsonParser parse(String filePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filePath)));

        // Parse dataset_type
        this.datasetType = extractStringValue(content, "dataset_type");

        // Parse n
        this.n = extractIntValue(content, "\"n\"");

        // Parse arrays
        this.inserted = extractStringArray(content, "inserted");
        this.negativeQueries = extractStringArray(content, "negative_queries");
        this.mixedQueries = extractStringArray(content, "mixed_queries");

        return this;
    }

    /**
     * Extract a string value for a given key from JSON content.
     */
    private String extractStringValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyIdx = json.indexOf(pattern);
        if (keyIdx == -1) return "";

        int colonIdx = json.indexOf(':', keyIdx + pattern.length());
        if (colonIdx == -1) return "";

        // Find the opening quote
        int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote == -1) return "";

        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote == -1) return "";

        return json.substring(startQuote + 1, endQuote);
    }

    /**
     * Extract an integer value for a given key pattern from JSON content.
     */
    private int extractIntValue(String json, String keyPattern) {
        int keyIdx = json.indexOf(keyPattern);
        if (keyIdx == -1) return 0;

        int colonIdx = json.indexOf(':', keyIdx + keyPattern.length());
        if (colonIdx == -1) return 0;

        // Find the start of the number
        int start = colonIdx + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\t')) {
            start++;
        }

        // Find the end of the number
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }

        return Integer.parseInt(json.substring(start, end));
    }

    /**
     * Extract a string array for a given key from JSON content.
     * Handles the format: "key": ["val1", "val2", ...]
     */
    private List<String> extractStringArray(String json, String key) {
        List<String> result = new ArrayList<>();

        String pattern = "\"" + key + "\"";
        int keyIdx = json.indexOf(pattern);
        if (keyIdx == -1) return result;

        // Find the opening bracket
        int bracketStart = json.indexOf('[', keyIdx + pattern.length());
        if (bracketStart == -1) return result;

        // Find the matching closing bracket
        int bracketEnd = findMatchingBracket(json, bracketStart);
        if (bracketEnd == -1) return result;

        // Extract content between brackets
        String arrayContent = json.substring(bracketStart + 1, bracketEnd);

        // Parse individual strings — manual state machine for speed
        boolean inString = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);

            if (!inString) {
                if (c == '"') {
                    inString = true;
                    current.setLength(0); // reset buffer
                }
            } else {
                if (c == '\\' && i + 1 < arrayContent.length()) {
                    // Handle escape sequences
                    current.append(arrayContent.charAt(i + 1));
                    i++;
                } else if (c == '"') {
                    inString = false;
                    result.add(current.toString());
                } else {
                    current.append(c);
                }
            }
        }

        return result;
    }

    /**
     * Find the matching closing bracket for an opening bracket.
     */
    private int findMatchingBracket(String json, int openPos) {
        int depth = 0;
        boolean inString = false;

        for (int i = openPos; i < json.length(); i++) {
            char c = json.charAt(i);

            if (inString) {
                if (c == '\\') {
                    i++; // skip escaped character
                } else if (c == '"') {
                    inString = false;
                }
            } else {
                if (c == '"') {
                    inString = true;
                } else if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    // ─── Getters ───────────────────────────────────────

    public String getDatasetType() { return datasetType; }
    public int getN() { return n; }
    public List<String> getInserted() { return inserted; }
    public List<String> getNegativeQueries() { return negativeQueries; }
    public List<String> getMixedQueries() { return mixedQueries; }

    // ─── Streaming parse (memory-efficient) ─────────────

    /**
     * Result of {@link #streamParse}: the small, fixed-size (5,000-element)
     * query lists. The "inserted" array is NOT materialized here — its
     * elements are pushed one at a time to the caller-supplied consumer.
     */
    public static class StreamedQueries {
        public final List<String> negatives;
        public final List<String> mixed;
        StreamedQueries(List<String> negatives, List<String> mixed) {
            this.negatives = negatives;
            this.mixed = mixed;
        }
    }

    /**
     * Streams a dataset file without ever holding the full "inserted" array
     * in memory: each element is pushed to {@code insertedSink} as soon as
     * it's read, then discarded. Only "negative_queries" and "mixed_queries"
     * (fixed at 5,000 elements each, regardless of N) are collected into
     * lists, since their memory cost doesn't grow with the dataset size.
     *
     * This exists because {@link #parse} reads the whole file into a String
     * and then a second time into a List&lt;String&gt;, which for large N can
     * itself exceed a constrained memory budget before any data structure is
     * even built — the exact scenario this method is meant to avoid for the
     * Bloom Filter benchmark. Relies on "inserted" appearing before
     * "negative_queries" before "mixed_queries" in the file, matching the
     * field order generate_dataset.py writes; a differently-ordered dataset
     * file would silently stream nothing for a key seen after its section
     * is skipped.
     *
     * @param filePath      Path to the JSON dataset file
     * @param insertedSink  Called once per element of the "inserted" array
     */
    public static StreamedQueries streamParse(String filePath, Consumer<String> insertedSink)
            throws IOException {
        List<String> negatives = new ArrayList<>();
        List<String> mixed = new ArrayList<>();

        // NOTE: deliberately NOT try-with-resources. Under a tight -Xmx an
        // OutOfMemoryError thrown inside the body would trigger the auto-close,
        // whose own failure the JVM tries to addSuppressed onto the (often
        // preallocated, shared) OOM instance — which throws
        // "IllegalArgumentException: Self-suppression not permitted" and masks
        // the OOM so callers' catch(OutOfMemoryError) never sees it, crashing
        // the whole run. Manual try/finally with a quiet close keeps the
        // original OutOfMemoryError catchable.
        Reader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8),
                1 << 16);
        try {
            StringBuilder keyBuf = new StringBuilder();
            int c;
            boolean done = false;
            while (!done && (c = r.read()) != -1) {
                if (c != '"') continue;

                keyBuf.setLength(0);
                int ch;
                while ((ch = r.read()) != -1 && ch != '"') {
                    if (ch == '\\') {
                        int esc = r.read();
                        if (esc == -1) break;
                        keyBuf.append((char) esc);
                    } else {
                        keyBuf.append((char) ch);
                    }
                }
                String key = keyBuf.toString();

                if (key.equals("inserted")) {
                    skipUntil(r, '[');
                    streamStringArray(r, insertedSink);
                } else if (key.equals("negative_queries")) {
                    skipUntil(r, '[');
                    streamStringArray(r, negatives::add);
                } else if (key.equals("mixed_queries")) {
                    skipUntil(r, '[');
                    streamStringArray(r, mixed::add);
                    done = true; // last field written by generate_dataset.py
                }
            }
        } finally {
            try { r.close(); } catch (Throwable ignored) { /* never mask an OOM */ }
        }

        return new StreamedQueries(negatives, mixed);
    }

    private static void skipUntil(Reader r, char target) throws IOException {
        int c;
        while ((c = r.read()) != -1) {
            if (c == target) return;
        }
    }

    /**
     * Consumes a JSON string array from just after its opening '[' up to and
     * including the matching ']', pushing each string element to sink as
     * soon as it is parsed. Assumes a flat array of strings (no nesting),
     * which matches every array in the dataset schema.
     */
    private static void streamStringArray(Reader r, Consumer<String> sink) throws IOException {
        StringBuilder cur = new StringBuilder();
        boolean inString = false;
        int c;
        while ((c = r.read()) != -1) {
            char ch = (char) c;
            if (!inString) {
                if (ch == '"') {
                    inString = true;
                    cur.setLength(0);
                } else if (ch == ']') {
                    return;
                }
            } else {
                if (ch == '\\') {
                    int next = r.read();
                    if (next == -1) break;
                    cur.append((char) next);
                } else if (ch == '"') {
                    inString = false;
                    sink.accept(cur.toString());
                } else {
                    cur.append(ch);
                }
            }
        }
    }
}
