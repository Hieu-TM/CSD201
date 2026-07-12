# BẢN THẢO NGHIÊN CỨU (RESEARCH DRAFT)

**Tên đề tài:** Phân tích Điểm giao cắt hiệu năng (Performance Crossover Point) giữa Hash Table và Bloom Filter dưới ràng buộc bộ nhớ khắt khe

**Môn học:** Data Structures & Algorithms (CSD201) - Summer 2026
**Nhóm thực hiện:** Nhóm 2 - SE20B02

---

## 1. Tóm tắt (Abstract)
Trong bài toán kiểm tra sự tồn tại của dữ liệu (Membership queries), Hash Table và Bloom Filter đại diện cho sự đánh đổi kinh điển giữa **Không gian (Space)** và **Thời gian/Độ chính xác (Time/Accuracy)**. Báo cáo này trình bày kết quả đo lường hiệu năng thực nghiệm (Memory-constrained Benchmark) của cả hai cấu trúc dữ liệu dưới các ngưỡng RAM cứng (16MB đến 256MB). Mục tiêu không phải để chứng minh cấu trúc nào tốt hơn, mà để tìm ra **"Điểm giao cắt" (Crossover Point)** – thời điểm Hash Table suy thoái hiệu năng do thiếu hụt bộ nhớ và Bloom Filter chứng minh được sự ưu việt về thông lượng (Throughput) cùng mức sử dụng RAM siêu nhỏ.

## 2. Câu hỏi nghiên cứu (Research Question)
> "Cho trước một ngân sách bộ nhớ cố định $M$, tại ngưỡng quy mô dữ liệu $N$ nào thì Hash Table bắt đầu suy thoái nghiêm trọng về hiệu năng (do cạn kiệt RAM và áp lực Garbage Collector), và Bloom Filter với cấu hình $k, m$ cụ thể chứng minh được tính ưu việt về Throughput, với sự đánh đổi về tỷ lệ False Positive $p$ thực tế so với lý thuyết là bao nhiêu?"

## 3. Phương pháp nghiên cứu
- **Ngôn ngữ & Môi trường:** Java (chuẩn CSD201), chạy giả lập giới hạn RAM bằng tham số JVM `-Xmx`.
- **Cấu trúc cài đặt:**
  - `HashTableSC`: Cài đặt thủ công bằng mảng và danh sách liên kết (Separate Chaining), kích thước mảng là số nguyên tố, Load Factor = 0.75.
  - `BloomFilter`: Dùng `BitSet` với kỹ thuật Double Hashing để sinh $k=7$ hàm băm, nhắm mục tiêu False Positive Rate (FPR) lý thuyết $p = 1\%$.
- **Tập dữ liệu (Dataset):** Các tệp JSON định dạng `username_n{N}`. Scale $N$ từ 1,000 đến 2,000,000 phần tử. Các mức Memory Budget: 16MB, 32MB, 64MB, 128MB, 256MB.

---

## 4. Kết quả Thực nghiệm & Phân tích Insight

### 4.1. Vùng an toàn (Sparse Data) - Sự thống trị của Hash Table
Khi dữ liệu ít ($N$ nhỏ) và RAM dồi dào (vd: ở mức 256MB):
- **Thông lượng (Throughput):** Hash Table đạt thông lượng cực cao, dao động từ **15 - 21 triệu ops/sec**. Thời gian lookup trung bình chỉ ở mức **~0.25 ms**.
- **Tính chính xác:** 100% không có sai số (Không có False Negatives và False Positives).
- **Kết luận:** Trong "vùng an toàn", chi phí tra cứu $O(1)$ amortized của Hash Table cùng với tính locality của bộ nhớ đệm (Cache locality) đánh bại chi phí tính toán $k=7$ hàm băm của Bloom Filter (Throughput của Bloom Filter thấp hơn, ở mức ~7 - 12 triệu ops/sec).

### 4.2. Khám phá Điểm Giao Cắt (The Crossover Point)
Khi scale $N$ dần lên và chạm vào giới hạn bộ nhớ $M$, chi phí không gian của Hash Table trở thành gánh nặng. Đo lường thực tế chỉ ra Hash Table tiêu tốn **~100 bytes / phần tử**, trong khi Bloom Filter chỉ tiêu tốn **~1.2 bytes / phần tử** (Tiết kiệm gấp **~83.5 lần**).

Dựa trên dữ liệu, ta xác định được điểm sụp đổ (OOM Point) của Hash Table tại từng ngưỡng bộ nhớ:
- **Ngân sách 16 MB:** Hash Table sụp đổ (OOM) ở $N \ge 250,000$ (Điểm tới hạn lý thuyết $N \approx 160K$).
- **Ngân sách 32 MB:** Hash Table sụp đổ ở $N \ge 500,000$ (Điểm tới hạn lý thuyết $N \approx 320K$).
- **Ngân sách 64 MB:** Hash Table sụp đổ ở $N \ge 1,000,000$ (Điểm tới hạn lý thuyết $N \approx 640K$).
- **Ngân sách 128 MB:** Hash Table sụp đổ ở $N \ge 2,000,000$ (Điểm tới hạn lý thuyết $N \approx 1.28M$).

> [!WARNING]
> **Hiện tượng suy thoái (Bottleneck):**
> Ngay trước khi bị crash `OutOfMemoryError`, thông lượng của Hash Table giảm sút nghiêm trọng. Nguyên nhân là do Java Garbage Collector (GC) liên tục giành quyền chạy để giải phóng bộ nhớ (Stop-the-world pauses), khiến ứng dụng bị treo. Ngay tại lúc Hash Table "ngắc ngoải", Bloom Filter cắt ngang đường hiệu năng và tiếp tục duy trì Throughput ổn định ở mức hàng triệu ops/sec do lượng RAM chiếm dụng của nó cực kỳ khiêm tốn (Ví dụ ở $N=2,000,000$, Bloom Filter chỉ tốn **2.3 MB**).

### 4.3. Xác minh Toán học (Empirical vs Theoretical FPR)
Theo công thức của Mitzenmacher, với cấu hình số lượng bits $m$ và $k$ hash functions được tính toán động dựa trên $N$, FPR lý thuyết kỳ vọng là $p \approx 1.0\%$.
- **Kết quả thực nghiệm đo được:** `fpr_empirical` dao động cực kỳ ổn định trong khoảng **$0.84\% - 1.38\%$**.
- Tỷ lệ này hoàn toàn khớp với dự đoán lý thuyết, chứng minh tính đúng đắn của việc áp dụng Double Hashing để mô phỏng $k$ hàm băm độc lập. Không có hiện tượng False Negative xảy ra.

---

## 5. Kết luận (Conclusion & Trade-off)
Bài nghiên cứu đã trả lời thỏa đáng Câu hỏi Nghiên cứu (RQ):
1. **Hash Table** phù hợp khi tài nguyên bộ nhớ dồi dào, cần kết quả truy vấn chính xác 100% hoặc có nhu cầu lưu giữ giá trị thực thể (Value mapping).
2. **Bloom Filter** trở thành cứu cánh bắt buộc khi số lượng phần tử vượt qua **Cross-over point** của bộ nhớ hệ thống. Với sự đánh đổi chấp nhận được (FPR ~ 1%), nó cung cấp Throughput ổn định và khả năng nén bộ nhớ tuyệt vời (~83x).

**Ứng dụng:** Bloom Filter là màng lọc lý tưởng ở lớp ngoài cùng của hệ thống (như CDN, Database Caching) để chặn các truy vấn "chắc chắn không tồn tại" (True Negatives) trước khi chúng kịp tấn công vào cấu trúc dữ liệu nặng hoặc I/O Disk.
