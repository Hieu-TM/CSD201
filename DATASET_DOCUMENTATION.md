# RBL Dataset Documentation
## Project: Phân tích Điểm giao cắt hiệu năng Hash Table vs Bloom Filter
### CSD201 — Summer 2026 | Nhóm 2 SE20B02

---

## 1. Tổng quan Dataset

| Thuộc tính | Giá trị |
|---|---|
| Số loại dataset | 4 (Username, URL, Integer ID, SHA-256 Hash) |
| Dataset dùng cho benchmark | Username (10 kích thước) |
| Benchmark sizes (N) | 1K, 5K, 10K, 25K, 50K, 100K, 250K, 500K, 1M, 2M |
| Query set per N | 5,000 (mixed) + 5,000 (pure negative) |
| Seed ngẫu nhiên | 42 (reproducible) |

---

## 2. Bốn loại Dataset

### 2.1 USERNAME (Dataset chính cho benchmark)
- **Use-case thực tế:** Kiểm tra username đã đăng ký chưa (GitHub, Discord)
- **Cấu trúc:** String 7–18 ký tự (chữ thường + số + gạch ngang)
- **Kích thước trung bình mỗi phần tử:** ~12 bytes
- **Pattern sinh dữ liệu:**
  - `prefix + noun + số` (ví dụ: `coolwolf4829`)
  - Chuỗi alphanumeric ngẫu nhiên (ví dụ: `k7m2xq9p`)
  - Dạng ID: `u3795584`
- **Files:** `username_n{N}.json` với N ∈ {1000, 5000, ..., 2000000}
- **Lý do chọn:** String length phù hợp, entropy vừa đủ, phổ biến nhất

### 2.2 URL BLACKLIST
- **Use-case:** Web proxy filter spam/malware (như Squid + Bloom Filter)
- **Cấu trúc:** `https://{domain}/{path1}/{path2}?param=value`
- **Kích thước trung bình:** ~60–80 bytes
- **File tham khảo:** `reference_url_n10k.json`

### 2.3 INTEGER TRANSACTION ID
- **Use-case:** Idempotency check trong payment system (đã xử lý giao dịch chưa?)
- **Cấu trúc:** Integer 64-bit, phân bố hỗn hợp (clustered + sparse)
- **Kích thước:** 8 bytes fixed
- **File tham khảo:** `reference_integer_id_n10k.json`

### 2.4 SHA-256 HASH
- **Use-case:** Password breach detection (kiểu HaveIBeenPwned)
- **Cấu trúc:** Hex string 64 ký tự (256 bits entropy)
- **Kích thước:** 64 bytes
- **File tham khảo:** `reference_sha256_n10k.json`

---

## 3. Cấu trúc file JSON

```json
{
  "dataset_type": "username",
  "n": 100000,
  "inserted": ["user1", "user2", ...],
  "negative_queries": ["ZZZQRY_0_...", ...],
  "mixed_queries": ["user1", "ZZZQRY_1_...", ...]
}
```

| Field | Mô tả | Dùng để |
|---|---|---|
| `inserted` | N phần tử đã được insert | Load vào HT/BF |
| `negative_queries` | 5000 queries CHẮC CHẮN không có trong set | Đo FPR thực nghiệm của BF |
| `mixed_queries` | 50% positive + 50% negative | Đo throughput tổng hợp |

---

## 4. Tham số lý thuyết Bloom Filter (Mitzenmacher)

**Công thức:**
```
m = ceil(-n · ln(p) / (ln 2)²)     [số bits]
k = round((m/n) · ln(2))           [số hash functions]
FPR = (1 - e^(-k·n/m))^k           [tỷ lệ FP lý thuyết]
```

Với FPR target = 1%:

| N | m (bits) | m (KB) | k | FPR lý thuyết |
|---|---|---|---|---|
| 1,000 | 9,586 | 1.2 | 7 | 1.0035% |
| 5,000 | 47,926 | 5.9 | 7 | 1.0039% |
| 10,000 | 95,851 | 11.7 | 7 | 1.0039% |
| 25,000 | 239,627 | 29.3 | 7 | 1.0039% |
| 50,000 | 479,253 | 58.5 | 7 | 1.0039% |
| 100,000 | 958,506 | 117.0 | 7 | 1.0039% |
| 250,000 | 2,396,265 | 292.5 | 7 | 1.0039% |
| 500,000 | 4,792,530 | 585.0 | 7 | 1.0039% |
| 1,000,000 | 9,585,059 | 1,170.1 | 7 | 1.0039% |
| 2,000,000 | 19,170,117 | 2,340.1 | 7 | 1.0039% |

**Nhận xét:** k = 7 hash functions là tối ưu ở mọi kích thước với FPR=1%. Số bits/phần tử luôn = **9.59** (không đổi theo N).

---

## 5. Phân tích bộ nhớ: HT vs BF

> **Cách đo bộ nhớ (cập nhật 2026-07-12):** benchmark báo **bộ nhớ đo thật** (`memory_kb`) cho cả hai
> cấu trúc, dùng cùng một giao thức: `BenchmarkRunner` build cấu trúc bằng **streaming** (mỗi cấu trúc
> giữ tham chiếu chuỗi của riêng nó), rồi lấy chênh lệch heap của JVM (`Runtime.totalMemory - freeMemory`)
> giữa hai mốc `forceGC()` bao quanh pha build, **lấy min qua nhiều trial** để giảm nhiễu GC. Con số mô
> hình lý thuyết (`memory_model_kb`) chỉ để **đối chứng**, không còn ghi đè số đo (trước đây HT báo
> `max(measured, model)` với `model` cố định ~100 bytes/phần tử → luôn thắng, khiến "crossover" trở thành
> tiền định thay vì quan sát thực nghiệm).
>
> - **Hash Table:** số **đo thật** là con số đáng tin; nó gồm cả `Node[]` + `Node` + `String` mà HT giữ
>   sống. `memory_model_kb` (~100 bytes/phần tử) chỉ để so sánh, và ở N lớn measured ≈ model xác nhận mô
>   hình hợp lý.
> - **Bloom Filter:** vì `BitSet` quá nhỏ (cỡ KB < nhiễu GC), số **đo thật nhiễu mạnh ở N nhỏ**; với BF
>   hãy đọc `memory_model_kb` = footprint `BitSet` **chính xác giải tích** (đóng gói word 64-bit), còn số
>   đo chỉ để xác nhận bậc độ lớn. `BloomFilter` dùng `BitSet` (không phải `boolean[]` vốn tốn 1 byte/bit,
>   gấp 8×) nên footprint thực ≈ lý thuyết m/8.
> - **Crossover = sự kiện OOM thật:** HT nay OOM *trong lúc build cấu trúc* dưới cờ `-Xmx`, không phải
>   trong lúc parse JSON. Vì vậy ngưỡng crossover là quan sát thực nghiệm, độc lập với con số bộ nhớ báo
>   cáo.
>
> Bảng dưới là **con số lý thuyết** (dùng cho tính toán/thiết kế), HT ≈ 100 bytes/phần tử và BF ≈ 1.2
> bytes/phần tử — đối chiếu với cột `memory_model_kb` trong `results/*.csv`.

| N | BF (KB) | HT (KB) | HT/BF ratio | BF tiết kiệm |
|---|---|---|---|---|
| 1,000 | 1.2 | 97.7 | 83.5× | 98.8% |
| 100,000 | 117.0 | 9,765.6 | 83.5× | 98.8% |
| 1,000,000 | 1,170.1 | 97,656.2 | 83.5× | 98.8% |
| 2,000,000 | 2,340.1 | 195,312.5 | 83.5× | 98.8% |

**Kết luận:** BF tiết kiệm ~98.8% RAM so với HT ở mọi kích thước (tỷ lệ nén ~83.5×).

---

## 6. Memory Budget Scenarios

**Crossover sẽ xảy ra khi HT vượt quá memory budget:**

| Budget | HT chứa tối đa | BF chứa tối đa | BF/HT |
|---|---|---|---|
| 4 MB | ~42K phần tử | ~3.5M phần tử | 83.3× |
| 16 MB | ~168K phần tử | ~14M phần tử | 83.3× |
| 64 MB | ~671K phần tử | ~56M phần tử | 83.3× |
| 256 MB | ~2.68M phần tử | ~224M phần tử | 83.3× |

→ **Crossover Point dự kiến với budget 16MB: N ≈ 168,000** (ở đó HT bắt đầu OOM; BF vẫn vận hành bình thường tới hàng chục triệu phần tử). Con số thực nghiệm đo được sẽ cao hơn ngưỡng lý thuyết này một chút vì bản thân việc nạp JSON và overhead JVM cũng chiếm một phần ngân sách bộ nhớ.

---

## 7. Lý do chọn dataset này

| Tiêu chí | Username dataset | Lý do |
|---|---|---|
| **Tính thực tế** | ✅ Cao | Membership query phổ biến nhất |
| **Kiểm soát được** | ✅ 100% | Tự generate, biết ground truth |
| **Pure negatives** | ✅ Đảm bảo | Pattern `ZZZQRY_*` không thể trùng |
| **Reproducible** | ✅ Seed=42 | Cùng kết quả mọi lần chạy |
| **Scalable** | ✅ 1K→2M | Bao phủ toàn bộ vùng crossover |
| **FP measurement** | ✅ Chính xác | Tập negatives sạch 100% |
