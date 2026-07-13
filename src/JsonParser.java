import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

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

    public JsonParser parse(String filePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filePath)));

        this.datasetType = extractStringValue(content, "dataset_type");

        this.n = extractIntValue(content, "\"n\"");

        this.inserted = extractStringArray(content, "inserted");
        this.negativeQueries = extractStringArray(content, "negative_queries");
        this.mixedQueries = extractStringArray(content, "mixed_queries");

        return this;
    }

    private String extractStringValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyIdx = json.indexOf(pattern);
        if (keyIdx == -1) return "";

        int colonIdx = json.indexOf(':', keyIdx + pattern.length());
        if (colonIdx == -1) return "";

        int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote == -1) return "";

        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote == -1) return "";

        return json.substring(startQuote + 1, endQuote);
    }

    private int extractIntValue(String json, String keyPattern) {
        int keyIdx = json.indexOf(keyPattern);
        if (keyIdx == -1) return 0;

        int colonIdx = json.indexOf(':', keyIdx + keyPattern.length());
        if (colonIdx == -1) return 0;

        int start = colonIdx + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\t')) {
            start++;
        }

        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }

        return Integer.parseInt(json.substring(start, end));
    }

    private List<String> extractStringArray(String json, String key) {
        List<String> result = new ArrayList<>();

        String pattern = "\"" + key + "\"";
        int keyIdx = json.indexOf(pattern);
        if (keyIdx == -1) return result;

        int bracketStart = json.indexOf('[', keyIdx + pattern.length());
        if (bracketStart == -1) return result;

        int bracketEnd = findMatchingBracket(json, bracketStart);
        if (bracketEnd == -1) return result;

        String arrayContent = json.substring(bracketStart + 1, bracketEnd);

        boolean inString = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);

            if (!inString) {
                if (c == '"') {
                    inString = true;
                    current.setLength(0);
                }
            } else {
                if (c == '\\' && i + 1 < arrayContent.length()) {

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

    private int findMatchingBracket(String json, int openPos) {
        int depth = 0;
        boolean inString = false;

        for (int i = openPos; i < json.length(); i++) {
            char c = json.charAt(i);

            if (inString) {
                if (c == '\\') {
                    i++;
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

    public String getDatasetType() { return datasetType; }
    public int getN() { return n; }
    public List<String> getInserted() { return inserted; }
    public List<String> getNegativeQueries() { return negativeQueries; }
    public List<String> getMixedQueries() { return mixedQueries; }

    public static class StreamedQueries {
        public final List<String> negatives;
        public final List<String> mixed;
        StreamedQueries(List<String> negatives, List<String> mixed) {
            this.negatives = negatives;
            this.mixed = mixed;
        }
    }

    public static StreamedQueries streamParse(String filePath, Consumer<String> insertedSink)
            throws IOException {
        List<String> negatives = new ArrayList<>();
        List<String> mixed = new ArrayList<>();

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
                    done = true;
                }
            }
        } finally {
            try { r.close(); } catch (Throwable ignored) {  }
        }

        return new StreamedQueries(negatives, mixed);
    }

    private static void skipUntil(Reader r, char target) throws IOException {
        int c;
        while ((c = r.read()) != -1) {
            if (c == target) return;
        }
    }

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
