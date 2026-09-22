# Load Test Analysis v1 — Cold Start

**Date:** 2026-09-21  
**Tester:** k6 v0.57 | Spring Boot 4.1.1 | PostgreSQL 16 | Docker  
**JVM:** `-Xmx512m -Xms256m` | Docker containers: 256MB RAM / 0.5 CPU

---

## 1. Môi trường

| Component | Chi tiết |
|-----------|----------|
| App | Spring Boot 4.1.1, chạy local (IntelliJ) |
| DB | PostgreSQL 16 (Docker), port 5432 |
| Monitoring | Prometheus 9090, Grafana 3000 |
| Machine | macOS Darwin 25.5.0, Apple Silicon |
| JVM limits | `-Xmx512m -Xms256m` |
| Docker container limits | 256 MB RAM, 0.5 CPU (quota=50000/period=100000) |

---

## 2. Kịch bản k6

File: `load-test/script.js`

```
Stage 1:  0 →  5 VUs (30s)
Stage 2:  5 → 20 VUs (30s)
Stage 3: 20 → 35 VUs (30s)
Stage 4: 35 → 50 VUs (30s)
Stage 5: 50 →  0 VUs (30s)
Tổng: 2m30s, max 50 VUs
```

Payload: Python Two Sum, input array 10,000 phần tử `[1..10000]`, target=19999.

Mỗi iteration:
1. POST `/api/v1/submission` → nhận `id`
2. Poll GET `/api/v1/submission/{id}` mỗi 0.5s, tối đa 30 lần → đợi `submitStatus == DONE`

---

## 3. Kết quả k6

### Latency end-to-end (submission_total_ms)

> Đo từ lúc POST cho đến khi GET trả về `DONE`.

| Metric | Giá trị |
|--------|---------|
| avg | 2.92 s |
| median (p50) | 2.57 s |
| p90 | 5.59 s |
| p95 | 6.10 s |
| max | 7.13 s |
| min | 518 ms |

### HTTP metrics

| Metric | Giá trị |
|--------|---------|
| Iterations hoàn chỉnh | **1,144** |
| Throughput (submissions/s) | **7.62 /s** |
| Tổng HTTP requests | 7,674 |
| HTTP req/s | 51.09 /s |
| `http_req_failed` | 0.00% |
| `http_req_duration` avg | 10.16 ms |
| `http_req_duration` median | 6.73 ms |
| `http_req_duration` p90 | 19.88 ms |
| `http_req_duration` p95 | 28.22 ms |
| `http_req_duration` max | 114.91 ms |
| Data received | 1.8 MB |
| Data sent | 1.1 MB |

### Checks

| Check | Kết quả |
|-------|---------|
| `submit ok` (POST 200) | ✓ 100% |
| `completed` (submitStatus == DONE) | ✓ 100% |
| `accepted` (buildStatus == SUCCESS) | ✗ 0% |

*0% accepted do bug logic Two Sum test case — không liên quan infrastructure.*

### Threshold

| Threshold | Kết quả |
|-----------|---------|
| `http_req_failed < 1%` | ✅ PASS (0%) |
| `submission_total_ms p(95) < 15s` | ✅ PASS (6.1s) |

---

## 4. JVM / Server metrics (Prometheus, peak trong test)

### Memory

| Metric | Giá trị |
|--------|---------|
| JVM Heap used peak | **108 MB** (Eden 44 + Old 58 + Survivor 6) |
| JVM Heap committed peak | ~132 MB |
| JVM Non-Heap peak | ~111 MB (Metaspace 87 + CodeCache 11 + CompressedClass 13) |
| Tổng JVM memory peak | **~219 MB** / 512 MB configured (43%) |

### CPU

| Metric | Giá trị |
|--------|---------|
| JVM process CPU peak | **4.6%** |
| System CPU peak | **92.8%** |

> System CPU 92.8% — bottleneck chính là Docker: mỗi submission tạo + xóa 1 container. `docker create/start/remove` tốn CPU nặng ở OS level.

### Threads

| Metric | Giá trị |
|--------|---------|
| Live threads peak | 41 |
| Peak threads (all-time) | 43 |

### GC

| Metric | Giá trị |
|--------|---------|
| GC pause tổng (5m window) | 680.3 ms (G1 Young Generation) |
| Loại | G1 Evacuation Pause (minor GC) |

---

## 5. Phân tích bottleneck

### 5.1 Docker cold start là bottleneck chính

Mỗi submission thực hiện full lifecycle:

```
createContainer() → startContainer() → copyCode() → compile/exec() → removeContainer()
```

| Bước | Thời gian |
|------|-----------|
| createContainer + startContainer | ~800–900 ms |
| exec (compile + run) | ~100–150 ms |
| removeContainer | ~50 ms |
| **Tổng** | **~1,054 ms** |

Với 8 async threads mặc định (`@EnableAsync` default):
```
Max throughput = 8 threads × (1000ms / 1054ms) ≈ 7.6 submissions/s
```

Kết quả đo: **7.62 /s** — khớp hoàn toàn với tính toán lý thuyết.

### 5.2 System CPU 92.8% — Docker overhead

Process CPU của JVM chỉ 4.6%, nhưng system CPU lên 92.8%. Docker container lifecycle (create/rm) tốn nhiều system call và overlay filesystem I/O. Đây là chi phí ẩn của cold start architecture.

### 5.3 Memory hiệu quả trong giới hạn

JVM heap chỉ dùng 108/512 MB (21%) — memory không phải bottleneck. Limit `-Xmx512m` đủ rộng.

### 5.4 Latency phân bố

p95 = 6.1s với 50 VUs. Tại peak (50 VU), mỗi submission phải chờ queue:
- Pool 8 threads → queue depth ~ 50/8 ≈ 6 submissions chờ
- Mỗi submission mất ~1s → chờ ~6s → p95 ≈ 6s ✓

---

## 6. Vấn đề còn lại

| Vấn đề | Tác động | Trạng thái |
|--------|----------|-----------|
| Docker cold start ~1,054ms | Throughput cap 7.6/s, p95=6.1s | **→ Fix trong v2 (container pool)** |
| System CPU 92.8% | OS bị stress, không scale được | **→ Fix trong v2** |
| Thread pool unbounded | Queue không có backpressure | Chưa fix |
| buildStatus WRONG_ANSWER 100% | Bug test case logic | Chưa fix (ngoài scope) |
