# Load Test Analysis — Leetcode Clone

**Date:** 2026-09-21  
**Tester:** k6 v0.57 | Spring Boot 4.1.1 | PostgreSQL 16 | Docker

---

## 1. Môi trường

| Component | Chi tiết |
|-----------|----------|
| App | Spring Boot 4.1.1, chạy từ IntelliJ (dev mode) |
| DB | PostgreSQL 16 (Docker), port 5432 |
| Monitoring | Prometheus 9090, Grafana 3000 |
| Machine | macOS (Darwin 25.5.0, Apple Silicon) |

---

## 2. Kịch bản k6

File: `load-test/script.js`

```
Stage 1:  0 → 1 VU   (30s)
Stage 2:  1 → 5 VUs  (30s)
Stage 3:  5 → 10 VUs (30s)
Stage 4: 10 → 20 VUs (30s)
Stage 5: 20 → 0 VUs  (30s)
Tổng: 2m30s
```

Mỗi iteration: POST `/api/v1/submission` (Python, Two Sum) → poll GET `/api/v1/submission/{id}` mỗi 0.5s tối đa 30 lần cho đến khi `submitStatus == DONE`.

---

## 3. Kết quả k6

### HTTP metrics

| Metric | Giá trị |
|--------|---------|
| Tổng iterations | 69,301 |
| Throughput | **461 req/s** |
| `http_req_failed` | **0.00%** |
| `checks_rate` | 100% (chỉ check `submit ok`) |
| `http_req_duration` avg | 15.22 ms |
| `http_req_duration` p90 | 34.88 ms |
| `http_req_duration` p95 | 45.61 ms |
| `http_req_duration` p99 | **70.30 ms** |
| Data received | 4.5 GB (65 KB/response) |
| Data sent | 30 MB |

### Threshold

| Threshold | Kết quả |
|-----------|---------|
| `http_req_failed < 1%` | PASS |
| `submission_total_ms p95 < 15s` | PASS — nhưng **không có ý nghĩa** (xem mục 5) |

---

## 4. Số liệu Spring Boot (Prometheus)

### Trong lúc test (peak)

| Metric | Giá trị |
|--------|---------|
| JVM Heap used (peak) | ~949 MB / 4096 MB (23%) |
| Live threads (peak) | **49** |
| GC overhead (peak) | **0.471%** |
| HTTP p99 latency (server-side) | < 70 ms |

### Sau test

| Metric | Giá trị |
|--------|---------|
| JVM Heap hiện tại | 949 MB |
| Live threads | 39 |
| GC overhead | 0.099% |
| DB connections (idle) | 10 |

---

## 5. Vấn đề phát hiện

### 5.1 Submissions bị kẹt PENDING

**Tình trạng DB sau 2 lần chạy k6:**

| submit_status | build_status | Số lượng |
|---------------|--------------|---------|
| PENDING | — | **221,290** |
| DONE | WRONG_ANSWER | 16,569 |
| IN_PROGRESS | — | 8 |

**221,290 submissions không bao giờ được xử lý.**

Root cause: throughput mismatch giữa incoming rate và processing rate.

| | Rate |
|--|--|
| k6 gửi vào | **~462 submissions/giây** |
| App xử lý được | **~3–8 submissions/giây** |

### 5.2 Docker container cold start quá chậm

Mỗi submission hiện tại tạo mới 1 container, chạy xong rồi xóa:

```
createContainer() → startContainer() → copyCode() → exec() → removeContainer()
```

**Đo thực tế:**

| Cách | Thời gian |
|------|-----------|
| Cold start (create → start → exec → remove) | **~1,054 ms** |
| Warm container (exec trực tiếp) | **~60–90 ms** |

Chênh lệch **~12–15 lần**. Với 8 async threads mặc định:

```
Capacity = 8 threads × (1 submission/giây) = ~8 submissions/giây
Incoming  = 462 submissions/giây
```

→ Queue tích lũy **~454 submissions/giây** không được xử lý.

### 5.3 Response GET `/api/v1/submission/{id}` thiếu field `submitStatus`

k6 poll bằng cách check `submitStatus == "DONE"`, nhưng endpoint GET không trả về field này trong response JSON. Kết quả:

- Poll chạy hết 30 lần (15 giây) không tìm thấy DONE
- Custom metric `submission_total_ms` luôn bằng 0 — **không đo được latency thực tế end-to-end**
- Check `completed` và `accepted` trong k6 không bao giờ chạy

**Ví dụ response GET `/api/v1/submission/237872`:**
```json
{
  "id": 237872,
  "buildStatus": null,
  "language": "PYTHON",
  "output": null,
  "errorOutput": null
  // submitStatus: KHÔNG CÓ
}
```

### 5.4 Response POST `/api/v1/submission` quá nặng

Mỗi response POST embed toàn bộ `Problem` object (bao gồm cả test cases) vào body — **65 KB/response**.

- Với 462 req/s → **~30 MB/s bandwidth** chỉ cho response
- k6 nhận 4.5 GB data trong 2m30s
- jq và Python json parser đều fail khi parse (response quá deep/lớn)

Cần tách riêng: response POST chỉ trả về `{ id, submitStatus }`.

### 5.5 Không có thread pool config

`@EnableAsync` không khai báo custom executor → Spring dùng default: **8 core threads, unbounded queue**. Không có backpressure — app nhận mọi submission rồi để queue phình ra vô hạn.

---

## 6. Tóm tắt vấn đề theo mức độ ưu tiên

| # | Vấn đề | Tác động | Fix |
|---|--------|----------|-----|
| 1 | Docker cold start ~1s/submission | Processing rate chỉ ~8/s vs 462/s incoming | Container pool |
| 2 | Không có thread pool limit | Queue unbounded, 221k submissions tồn đọng | `ThreadPoolTaskExecutor` + reject policy |
| 3 | GET response thiếu `submitStatus` | Client không poll được kết quả | Thêm field vào response |
| 4 | POST response 65 KB (embed cả Problem) | 30 MB/s bandwidth lãng phí | Trả về DTO nhẹ `{ id, submitStatus }` |

---

## 7. Hướng fix đề xuất

### Container Pool

Giữ sẵn N container đã start (per language), mỗi submission chỉ cần `exec` vào container có sẵn, xong clean `/sandbox` rồi trả về pool.

```
Thời gian dự kiến sau fix:
  exec + clean sandbox ≈ 60–90 ms + ~10 ms = ~100 ms/submission
  Với pool 5 containers × 3 languages = 15 containers
  Throughput ≈ 5 × (1000ms/100ms) = ~50 submissions/giây
```

Đủ cho demo, không cần k8s.
