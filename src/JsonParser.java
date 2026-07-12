import java.io.*;
import java.nio.file.*;
import java.util.*;

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
}
