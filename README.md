# 🔬 Performance Crossover Point: Hash Table vs Bloom Filter

**Môn học:** Data Structures & Algorithms (CSD201) — Summer 2026  
**Nhóm:** Nhóm 2 — SE20B02  
**Ngôn ngữ:** Java (Pure — không thư viện ngoài)

---

## 📋 Mục lục

- [Tổng quan](#-tổng-quan)
- [Câu hỏi nghiên cứu](#-câu-hỏi-nghiên-cứu)
- [Kiến trúc dự án](#-kiến-trúc-dự-án)
- [Cấu trúc dữ liệu](#-cấu-trúc-dữ-liệu)
  - [Hash Table (Separate Chaining)](#1-hash-table-separate-chaining--hashtablescjava)
  - [Bloom Filter](#2-bloom-filter--bloomfilterjava)
- [Hệ thống Benchmark](#-hệ-thống-benchmark)
- [Dataset](#-dataset)
- [Tham số lý thuyết](#-tham-số-lý-thuyết-bloom-filter)
- [Kết quả thực nghiệm](#-kết-quả-thực-nghiệm)
- [Hướng dẫn chạy](#-hướng-dẫn-chạy)
- [Cấu trúc Output](#-cấu-trúc-output)
- [Tài liệu tham khảo](#-tài-liệu-tham-khảo)

---

## 🎯 Tổng quan

Dự án nghiên cứu sự đánh đổi kinh điển giữa **Không gian (Space)** và **Thời gian/Độ chính xác (Time/Accuracy)** trong bài toán **Membership Query** — kiểm tra một phần tử có thuộc một tập hợp hay không.

Thay vì chỉ so sánh lý thuyết, dự án thực hiện **benchmark thực nghiệm** dưới các ràng buộc bộ nhớ cứng (JVM `-Xmx`) để tìm ra **"Điểm giao cắt" (Crossover Point)** — thời điểm Hash Table sụp đổ do cạn kiệt RAM và Bloom Filter chứng minh tính ưu việt.

### Điểm nổi bật

| Đặc điểm | Chi tiết |
|---|---|
| **Pure Java** | Không sử dụng thư viện ngoài (`HashMap`, `HashSet`, Guava...) |
| **Tự cài đặt hoàn toàn** | Hash Table (Separate Chaining), Bloom Filter, JSON Parser |
| **Benchmark nghiêm ngặt** | Warmup JIT, multi-trial, median timing, GC isolation |
| **Scale lớn** | Từ 1,000 → 2,000,000 phần tử × 5 memory budget (16–256 MB) |
| **Reproducible** | Random seed = 42, deterministic dataset generation |

---

## ❓ Câu hỏi nghiên cứu

> *"Cho trước một ngân sách bộ nhớ cố định M, tại ngưỡng quy mô dữ liệu N nào thì Hash Table bắt đầu suy thoái nghiêm trọng về hiệu năng (do cạn kiệt RAM và áp lực GC), và Bloom Filter chứng minh được tính ưu việt về Throughput, với sự đánh đổi FPR thực tế so với lý thuyết là bao nhiêu?"*

---

## 📁 Kiến trúc dự án

```
CSD201-RBL/
├── src/                          # Mã nguồn Java
│   ├── BloomFilter.java          # Bloom Filter (BitSet + Double Hashing)
│   ├── HashTableSC.java          # Hash Table Separate Chaining
│   ├── JsonParser.java           # JSON Parser tự viết (batch + streaming)
│   ├── BenchmarkRunner.java      # Orchestrator: chạy benchmark, đo lường, xuất CSV
│   └── BenchmarkResult.java      # Data class cho kết quả benchmark
│
├── datasets/                     # Dataset JSON (sinh bởi generate_dataset.py)
│   ├── username_n1000.json       # 1K phần tử
│   ├── username_n5000.json       # 5K phần tử
│   ├── ...                       # (10 kích thước: 1K → 2M)
│   ├── username_n2000000.json    # 2M phần tử (~28 MB)
│   └── reference_*.json          # Dataset tham khảo (URL, Integer ID, SHA-256)
│
├── results/                      # Output CSV từ benchmark
│   ├── hashtable_results.csv     # Kết quả chi tiết Hash Table
│   ├── bloomfilter_results.csv   # Kết quả chi tiết Bloom Filter
│   └── summary_comparison.csv    # Bảng so sánh tổng hợp
│
├── paper_ieee/                   # Bài báo IEEE format (LaTeX)
│   ├── main.tex
│   └── figures/
│
├── generate_dataset.py           # Script Python sinh dataset
├── run_benchmark.bat             # Script chạy benchmark tự động
├── research_draft.md             # Bản thảo nghiên cứu
├── DATASET_DOCUMENTATION.md      # Tài liệu dataset chi tiết
└── README.md                    
```

---

## 🧱 Cấu trúc dữ liệu

### 1. Hash Table (Separate Chaining) — `HashTableSC.java`

Cài đặt thủ công Hash Table sử dụng mảng linked list (Separate Chaining).

**Thông số kỹ thuật:**

| Thành phần | Mô tả |
|---|---|
| **Hash function** | `String.hashCode()` + bitmask `& 0x7FFFFFFF` + modular compression |
| **Collision resolution** | Separate Chaining (singly linked list, insert at head) |
| **Load factor threshold** | 0.75 (tự động resize khi vượt ngưỡng) |
| **Table capacity** | Luôn là số nguyên tố (giảm clustering) |
| **Resize strategy** | Double capacity → tìm số nguyên tố kế tiếp → rehash toàn bộ |

**Mô hình bộ nhớ (~100 bytes/phần tử):**

```
Per element:
  Node object overhead      : 32 bytes  (header + key ref + next ref + padding)
  String object             : 40 bytes  (header + char[] ref + hash + length)
  String payload (~12 chars): ~12 bytes
  Table array ref           : 8 bytes   (amortized per slot)
  ─────────────────────────────────
  Total                     : ~92-100 bytes/element
```

**API:**

```java
HashTableSC(int initialCapacity)   // Khởi tạo với capacity = nextPrime(initialCapacity)
boolean insert(String key)          // Insert, trả về true nếu collision
boolean lookup(String key)          // Lookup, O(1) amortized
int getCollisionCount()             // Tổng số collision
double getCollisionRate()           // Tỷ lệ collision
int getMaxChainLength()             // Chuỗi dài nhất
double getAvgChainLength()          // Trung bình chuỗi (chỉ bucket non-empty)
long estimateMemoryBytes()          // Ước lượng bộ nhớ theo mô hình
```

---

### 2. Bloom Filter — `BloomFilter.java`

Cài đặt Bloom Filter sử dụng `java.util.BitSet` và kỹ thuật Double Hashing.

**Thông số kỹ thuật:**

| Thành phần | Mô tả |
|---|---|
| **Bit array** | `java.util.BitSet` (packed vào `long[]`, 1 bit/bit thật) |
| **Tham số m** (số bits) | `m = ⌈-n · ln(p) / (ln 2)²⌉` — Công thức Mitzenmacher |
| **Tham số k** (số hash) | `k = round((m/n) · ln 2)` |
| **Hash h₁** | `String.hashCode() & 0x7FFFFFFF` |
| **Hash h₂** | FNV-1a 32-bit (kết quả OR 1 → luôn lẻ, đảm bảo full period) |
| **Double Hashing** | `hᵢ(key) = (h₁ + i · h₂) mod m`, cho `i = 0, 1, ..., k-1` |
| **Target FPR** | 1% (p = 0.01) |

**Mô hình bộ nhớ (~1.2 bytes/phần tử):**

```
m = 9.59 bits/element  →  1.2 bytes/element
BitSet stores bits in long[] words (8 bytes = 64 bits each)
Actual JVM memory = ⌈m / 64⌉ × 8 + 32 bytes (object/array header)
```

> **Tại sao dùng `BitSet` thay vì `boolean[]`?**  
> `boolean[]` trong JVM tốn **1 byte/element** (không phải 1 bit), khiến bộ nhớ thực tế gấp **8 lần** lý thuyết. `BitSet` pack bits vào `long[]` words, footprint thực ≈ `m/8` bytes.

**API:**

```java
BloomFilter(int n, double targetFPR) // Tự tính m, k tối ưu
void insert(String key)               // Set k bits
boolean lookup(String key)            // Check k bits (true = maybe, false = definitely not)
double getTheoreticalFPR()            // FPR lý thuyết = (1 - e^(-kn/m))^k
double getFillRatio()                 // Tỷ lệ bits đã set
long estimateMemoryBytes()            // m / 8 (lý thuyết)
long actualJvmMemoryBytes()           // ⌈m/64⌉ × 8 + 32 (thực tế JVM)
```

**Tham khảo thuật toán:**
- Kirsch & Mitzenmacher, *"Less Hashing, Same Performance: Building a Better Bloom Filter"* (2006)
- FNV-1a Hash: Fowler–Noll–Vo hash function (32-bit variant)

---

## ⚡ Hệ thống Benchmark

### `BenchmarkRunner.java` — Orchestrator

Chương trình benchmark chính, thiết kế để đo lường chính xác và công bằng.

**Phương pháp đo lường:**

| Kỹ thuật | Mục đích |
|---|---|
| **Streaming JSON** | Parse JSON theo kiểu streaming (không load toàn bộ vào RAM) → tách biệt bộ nhớ cấu trúc dữ liệu khỏi bộ nhớ parser |
| **Multi-trial Insert** | 3 trial, lấy kết quả memory min (giảm nhiễu GC) |
| **Multi-trial Lookup** | 7 trial, lấy **median** thời gian (loại outlier) |
| **JIT Warmup** | 10 iteration warmup trước khi đo lookup |
| **Blackhole** | `volatile long BLACKHOLE` ngăn JIT loại bỏ dead code |
| **Target 2M ops** | Lookup lặp đủ ~2,000,000 operations để có số liệu ổn định |
| **Force GC** | `System.gc()` × 2 + `Thread.sleep(50ms)` giữa các lần đo |
| **Isolated JVM** | Mỗi (budget, size) chạy trong JVM riêng biệt (qua `run_benchmark.bat`) |

**Cách đo bộ nhớ:**

```
baseline  = Runtime.totalMemory() - Runtime.freeMemory()  [sau forceGC]
           ... build data structure ...
after     = Runtime.totalMemory() - Runtime.freeMemory()  [sau forceGC]
measured  = min(after - baseline) across trials
```

- **Hash Table:** Số đo thật đáng tin cậy (gồm `Node[]` + `Node` + `String`)
- **Bloom Filter:** Vì `BitSet` quá nhỏ (cỡ KB), số đo bị nhiễu GC ở N nhỏ → tham khảo `memory_model_kb` (footprint chính xác giải tích)

**Crossover = sự kiện OOM thật:** Hash Table OOM *trong lúc build cấu trúc* dưới cờ `-Xmx`, không phải khi parse JSON. Crossover point là quan sát thực nghiệm.

---

## 📊 Dataset

### Dataset chính: Username (10 kích thước)

| N | File | Kích thước |
|---|---|---|
| 1,000 | `username_n1000.json` | ~186 KB |
| 5,000 | `username_n5000.json` | ~263 KB |
| 10,000 | `username_n10000.json` | ~332 KB |
| 25,000 | `username_n25000.json` | ~539 KB |
| 50,000 | `username_n50000.json` | ~886 KB |
| 100,000 | `username_n100000.json` | ~1.5 MB |
| 250,000 | `username_n250000.json` | ~3.6 MB |
| 500,000 | `username_n500000.json` | ~7.0 MB |
| 1,000,000 | `username_n1000000.json` | ~13.7 MB |
| 2,000,000 | `username_n2000000.json` | ~27.2 MB |

**Đặc điểm Username:**
- String 7–18 ký tự (chữ thường + số + gạch ngang)
- 3 pattern: `prefix+noun+số` (`coolwolf4829`), random alphanumeric (`k7m2xq9p`), ID format (`u3795584`)
- Use-case thực tế: kiểm tra username đã đăng ký (GitHub, Discord)

**Cấu trúc mỗi file JSON:**

```json
{
  "dataset_type": "username",
  "n": 100000,
  "inserted": ["user1", "user2", ...],
  "negative_queries": ["ZZZQRY_0_...", ...],
  "mixed_queries": ["user1", "ZZZQRY_1_...", ...]
}
```

| Field | Số lượng | Mục đích |
|---|---|---|
| `inserted` | N phần tử | Load vào HT/BF |
| `negative_queries` | 5,000 | Đo FPR thực nghiệm (100% guaranteed negative) |
| `mixed_queries` | 5,000 (50/50) | Đo throughput tổng hợp |

### Dataset tham khảo (N = 10,000 mỗi loại)

| Loại | Use-case | Kích thước/elem |
|---|---|---|
| URL Blacklist | Web proxy spam filter | ~60–80 bytes |
| Integer Transaction ID | Payment idempotency check | 8 bytes |
| SHA-256 Hash | Password breach detection | 64 bytes |

---

## 📐 Tham số lý thuyết Bloom Filter

**Công thức Mitzenmacher (FPR target = 1%):**

```
m = ⌈-n · ln(p) / (ln 2)²⌉         [số bits]
k = round((m/n) · ln 2)             [số hash functions]
FPR = (1 - e^(-k·n/m))^k            [tỷ lệ FP lý thuyết]
```

| N | m (bits) | m (KB) | k | FPR lý thuyết |
|---|---|---|---|---|
| 1,000 | 9,586 | 1.2 | 7 | 1.0035% |
| 10,000 | 95,851 | 11.7 | 7 | 1.0039% |
| 100,000 | 958,506 | 117.0 | 7 | 1.0039% |
| 1,000,000 | 9,585,059 | 1,170.1 | 7 | 1.0039% |
| 2,000,000 | 19,170,117 | 2,340.1 | 7 | 1.0039% |

> **Nhận xét:** `k = 7` là tối ưu ở mọi kích thước. Số bits/phần tử luôn = **9.59** (hằng số, không phụ thuộc N).

### So sánh bộ nhớ lý thuyết

| N | Bloom Filter | Hash Table | HT/BF ratio | BF tiết kiệm |
|---|---|---|---|---|
| 1,000 | 1.2 KB | 97.7 KB | 83.5× | 98.8% |
| 100,000 | 117.0 KB | 9,765.6 KB | 83.5× | 98.8% |
| 1,000,000 | 1,170.1 KB | 97,656.2 KB | 83.5× | 98.8% |
| 2,000,000 | 2,340.1 KB | 195,312.5 KB | 83.5× | 98.8% |

---

## 📈 Kết quả thực nghiệm

### Crossover Point (OOM thực tế)

| Memory Budget | HT OOM tại N ≥ | BF vẫn hoạt động tới | Lý thuyết HT max |
|---|---|---|---|
| **16 MB** | 250,000 | 2,000,000+ | ~168K |
| **32 MB** | 500,000 | 2,000,000+ | ~320K |
| **64 MB** | 1,000,000 | 2,000,000+ | ~640K |
| **128 MB** | 2,000,000 | 2,000,000+ | ~1.28M |
| **256 MB** | > 2,000,000 | 2,000,000+ | ~2.68M |

### Hiệu năng tiêu biểu (Budget = 256 MB)

| Metric | Hash Table | Bloom Filter |
|---|---|---|
| **Throughput** (N nhỏ) | ~50–120M ops/sec | ~14–16M ops/sec |
| **Lookup time** | ~0.03–0.11 ms | ~0.22–0.35 ms |
| **Collision rate** | ~30% | N/A |
| **FPR thực nghiệm** | 0% (chính xác tuyệt đối) | 0.84%–1.38% (≈ lý thuyết 1%) |

### Kết luận chính

1. **Vùng an toàn (N nhỏ, RAM dồi dào):** Hash Table thắng tuyệt đối — throughput cao hơn 5–8×, chính xác 100%.
2. **Vùng crossover:** Khi N vượt `budget / 100 bytes`, Hash Table bắt đầu suy thoái do GC pressure, rồi OOM.
3. **Vùng ưu thế BF:** Bloom Filter duy trì throughput ổn định (~14M ops/sec) bất kể N, chỉ tốn ~1.2 bytes/elem.
4. **FPR thực nghiệm ≈ lý thuyết:** Dao động 0.84%–1.38%, xác nhận Double Hashing mô phỏng tốt k hash functions độc lập.

---

## 🚀 Hướng dẫn chạy

### Yêu cầu

- **Java JDK 17+** (đã test trên JDK 25 — Eclipse Adoptium)
- **Python 3.8+** (chỉ cần nếu muốn tái tạo dataset)

### 1. Sinh Dataset (tùy chọn — dataset đã có sẵn)

```bash
python generate_dataset.py
```

Tạo 10 file username (`1K → 2M`) + 4 file reference trong thư mục `datasets/`.

### 2. Chạy Benchmark đầy đủ (khuyến nghị)

```cmd
run_benchmark.bat
```

Script tự động:
1. Compile tất cả Java source → `out/`
2. Xóa CSV cũ trong `results/`
3. Chạy benchmark qua **5 memory budget** (16, 32, 64, 128, 256 MB) × **10 dataset sizes** (1K → 2M)
4. Mỗi (budget, size) chạy trong **JVM riêng biệt** với `-Xmx`

### 3. Chạy thủ công (một kích thước cụ thể)

```cmd
REM Compile
javac -d out src\*.java

REM Chạy N = 100,000 với budget 64 MB
java -cp out -Xmx64m BenchmarkRunner --budget 64 100000

REM Chạy nhiều kích thước
java -cp out -Xmx128m BenchmarkRunner --budget 128 1000 10000 100000

REM Chạy tất cả kích thước (mặc định)
java -cp out BenchmarkRunner
```

### Tham số dòng lệnh

| Tham số | Mô tả | Mặc định |
|---|---|---|
| `--budget <MB>` | Memory budget (để ghi vào CSV, không set `-Xmx`) | 256 |
| `<N₁> <N₂> ...` | Các kích thước dataset cần test | Tất cả 10 sizes |

> **Lưu ý:** `--budget` chỉ ghi nhận giá trị vào output CSV. Để **thực sự** giới hạn RAM, cần truyền `-Xmx` khi gọi `java` (script `run_benchmark.bat` đã làm điều này).

---

## 📂 Cấu trúc Output

### Console Output

```
======================================================================
  RBL BENCHMARK: Hash Table (Separate Chaining) vs Bloom Filter
  CSD201 — Summer 2026 | Nhom 2 SE20B02
======================================================================

──────────────────────────────────────────────────────────────────────
  BENCHMARK N = 100,000
──────────────────────────────────────────────────────────────────────

  [BF] Bloom Filter (k=7, m=958,506 bits):
  Data Structure    : BloomFilter
  N                 : 100,000
  Insert Time       : 15.31 ms
  Lookup Time       : 0.34 ms
  Throughput        : 14,775,414 ops/sec (±4.8%)
  Memory            : 118.2 KB measured (model 117.0 KB)
  FPR               : 0.9200% (theory=1.0039%)
  False Positives   : 46/5000
  BF Params         : 958,506 bits, k=7

  [HT] Hash Table Separate Chaining:
  Data Structure    : HashTable
  N                 : 100,000
  Insert Time       : 27.57 ms
  Lookup Time       : 0.33 ms
  Throughput        : 15,120,987 ops/sec (±22.7%)
  Memory            : 8,732.5 KB measured (model 9,765.6 KB)
  Collisions        : 31,130 (rate=31.13%)
  Max Chain         : 7 (avg=1.45)
```

### CSV Files (`results/`)

**`hashtable_results.csv`** — Cột:

```
memory_budget_mb, n, insert_time_ms, lookup_time_ms, throughput_ops_sec,
throughput_stdev_pct, memory_bytes, memory_kb, memory_model_kb,
collision_count, collision_rate, max_chain_len, avg_chain_len,
table_capacity, resize_count, is_oom
```

**`bloomfilter_results.csv`** — Cột:

```
memory_budget_mb, n, insert_time_ms, lookup_time_ms, throughput_ops_sec,
throughput_stdev_pct, memory_bytes, memory_kb, memory_model_kb,
m_bits, k_hashes, fpr_empirical, fpr_theoretical,
false_positive_count, negative_query_count, fill_ratio, is_oom
```

**`summary_comparison.csv`** — Bảng so sánh gộp cả hai cấu trúc.

---

## 📚 Tài liệu tham khảo

1. **Kirsch, A. & Mitzenmacher, M.** (2006). *Less Hashing, Same Performance: Building a Better Bloom Filter*. ESA 2006.
2. **Bloom, B. H.** (1970). *Space/Time Trade-offs in Hash Coding with Allowable Errors*. Communications of the ACM, 13(7), 422–426.
3. **Fowler–Noll–Vo hash function** — FNV-1a 32-bit variant. [www.isthe.com/chongo/tech/comp/fnv](http://www.isthe.com/chongo/tech/comp/fnv/)
4. **Cormen, T. H., Leiserson, C. E., Rivest, R. L., & Stein, C.** (2022). *Introduction to Algorithms* (4th ed.). MIT Press. — Chương 11: Hash Tables.

---

## 📄 License

Dự án này được thực hiện cho mục đích học thuật trong khuôn khổ môn CSD201 — FPT University.
