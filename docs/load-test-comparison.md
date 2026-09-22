# Load Test Comparison: v1 vs v2

**Date:** 2026-09-21  
**Test setup:** 50 VUs | 2m30s | Python Two Sum | 10,000-element input  
**JVM:** `-Xmx512m -Xms256m` | Docker containers: 256MB / 0.5 CPU

---

## Thay đổi giữa v1 và v2

| | v1 | v2 |
|--|--|--|
| Container lifecycle | create → start → exec → remove (mỗi submission) | pool pre-warmed, borrow → exec → release |
| Response DTO | Full entity (65 KB, circular ref) | DTO nhẹ 7 field (~100 bytes) |

---

## Latency end-to-end (submission_total_ms)

| Metric | v1 (cold start) | v2 (pool) | Cải thiện |
|--------|-----------------|-----------|-----------|
| avg | 2.92 s | **2.04 s** | ↓ 30% |
| p50 (median) | 2.57 s | **2.04 s** | ↓ 21% |
| p90 | 5.59 s | **3.56 s** | ↓ 36% |
| p95 | 6.10 s | **3.59 s** | ↓ 41% |
| max | 7.13 s | **4.14 s** | ↓ 42% |
| min | 518 ms | **516 ms** | ≈ |

---

## Throughput

| Metric | v1 | v2 | Cải thiện |
|--------|----|----|-----------|
| Submissions hoàn chỉnh | 1,144 | **1,623** | ↑ 42% |
| Throughput (submissions/s) | 7.62 | **10.80** | ↑ 42% |
| HTTP req/s | 51.09 | **54.08** | ↑ 6% |

---

## HTTP request latency (API calls)

| Metric | v1 | v2 | Cải thiện |
|--------|----|----|-----------|
| avg | 10.16 ms | **8.72 ms** | ↓ 14% |
| median | 6.73 ms | **5.71 ms** | ↓ 15% |
| p90 | 19.88 ms | **17.53 ms** | ↓ 12% |
| p95 | 28.22 ms | **22.94 ms** | ↓ 19% |
| max | 114.91 ms | 177.92 ms | ↑ (outlier) |

---

## JVM & System resources

| Metric | v1 | v2 | Ghi chú |
|--------|----|----|---------|
| JVM heap peak | 108 MB | **124.7 MB** | +15% — nhiều submissions hơn |
| JVM nonheap | ~111 MB | ~111 MB | = |
| Tổng JVM memory | ~219 MB | ~236 MB | Trong giới hạn (512 MB max) |
| JVM process CPU peak | 4.6% | 5.2% | ≈ |
| **System CPU peak** | **92.8%** | **78.6%** | ↓ 15% — loại bỏ create/remove overhead |
| Live threads peak | 41 | 38 | ≈ |
| GC pause (5m) | 680 ms | 854 ms | +25% — nhiều objects hơn |

---

## Reliability & correctness

| Metric | v1 | v2 |
|--------|----|----|
| `http_req_failed` | 0.00% | 0.00% |
| `completed` (DONE) | ✓ 100% | ✓ 100% |
| DB submissions tồn đọng | 0 | 0 |
| p95 threshold (< 15s) | ✅ 6.1s | ✅ 3.59s |

---

## Tóm tắt

Container pool cải thiện **p95 latency 41%** (6.1s → 3.59s) và **throughput 42%** (7.62 → 10.80 sub/s) trong cùng điều kiện 50 VU.

Bottleneck mới sau v2 là pool size (5 containers/language): khi 50 VU cùng request Python, 45 VU block chờ 5 container available. Tăng `docker.pool.size` sẽ tiếp tục cải thiện throughput — chi phí là memory cho các containers pre-warmed thêm (Python/C++: ~530 KB/container idle, Java: ~7.7 MB/container idle do JVM overhead).
