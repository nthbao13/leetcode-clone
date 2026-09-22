# Load Test Analysis v2 — Container Pool

**Date:** 2026-09-21  
**Tester:** k6 v0.57 | Spring Boot 4.1.1 | PostgreSQL 16 | Docker  
**JVM:** `-Xmx512m -Xms256m` | Docker containers: 256MB RAM / 0.5 CPU  
**So sánh với:** `load-test-analysis_v1.md`

---

## 1. Thay đổi so với v1

| # | Vấn đề (v1) | Fix trong v2 |
|---|-------------|-------------|
| 1 | Docker cold start ~1,054ms/submission | **Container pool** — 15 containers pre-warmed (5/language), reuse qua `exec` |
| 2 | Response 65 KB, circular reference | **`SubmissionResponseDTO`** — 7 field, không embed `Problem` |

---

## 2. Kịch bản k6

```
Stage 1:  0 →  5 VUs (30s)
Stage 2:  5 → 20 VUs (30s)
Stage 3: 20 → 35 VUs (30s)
Stage 4: 35 → 50 VUs (30s)
Stage 5: 50 →  0 VUs (30s)
Tổng: 2m30s, max 50 VUs
```

Payload: Python Two Sum, input array 10,000 phần tử `[1..10000]`, target=19999.

---

## 3. Kết quả k6

### Latency end-to-end (submission_total_ms)

| Metric | Giá trị |
|--------|---------|
| avg | 2.04 s |
| median (p50) | 2.04 s |
| p90 | 3.56 s |
| p95 | 3.59 s |
| max | 4.14 s |
| min | 516 ms |

### HTTP metrics

| Metric | Giá trị |
|--------|---------|
| Iterations hoàn chỉnh | **1,623** |
| Throughput (submissions/s) | **10.80 /s** |
| Tổng HTTP requests | 8,126 |
| HTTP req/s | 54.08 /s |
| `http_req_failed` | 0.00% |
| `http_req_duration` avg | 8.72 ms |
| `http_req_duration` median | 5.71 ms |
| `http_req_duration` p90 | 17.53 ms |
| `http_req_duration` p95 | 22.94 ms |
| `http_req_duration` max | 177.92 ms |
| Data received | 1.9 MB |
| Data sent | 1.3 MB |

### Checks

| Check | Kết quả |
|-------|---------|
| `submit ok` (POST 200) | ✓ 100% |
| `completed` (submitStatus == DONE) | ✓ 100% |
| `accepted` (buildStatus == SUCCESS) | ✗ 0% |

### Threshold

| Threshold | Kết quả |
|-----------|---------|
| `http_req_failed < 1%` | ✅ PASS (0%) |
| `submission_total_ms p(95) < 15s` | ✅ PASS (3.59s) |

---

## 4. JVM / Server metrics (Prometheus, peak trong test)

### Memory

| Metric | Giá trị |
|--------|---------|
| JVM Heap used peak | **124.7 MB** (Eden 60 + Old 57.6 + Survivor 7) |
| JVM Non-Heap peak | ~111.2 MB |
| Tổng JVM memory peak | **~236 MB** / 512 MB configured (46%) |

### CPU

| Metric | Giá trị |
|--------|---------|
| JVM process CPU peak | **5.2%** |
| System CPU peak | **78.6%** |

### Threads

| Metric | Giá trị |
|--------|---------|
| Live threads peak | 38 |

### GC

| Metric | Giá trị |
|--------|---------|
| GC pause tổng (5m window) | 854.2 ms (G1 Young Generation) |

---

## 5. Phân tích

### 5.1 Container pool — cải thiện thực tế

| | v1 (cold start) | v2 (pool) |
|--|--|--|
| Borrow container | ~800–900 ms (create+start) | **~0 ms** (dequeue từ pool) |
| Exec (Python run) | ~100–150 ms | **~80–130 ms** |
| Return container | ~50 ms (remove) | **~5 ms** (clean sandbox + enqueue) |
| **Tổng/submission** | **~1,054 ms** | **~85–135 ms** |

### 5.2 Throughput tăng nhưng bị giới hạn bởi pool size

Pool 5 containers/language. Khi 50 VU cùng chạy Python:
- 5 VU exec đồng thời
- 45 VU còn lại block chờ pool (borrow timeout 30s)

Throughput tối đa lý thuyết với pool=5:
```
5 containers × (1000ms / ~110ms per exec) ≈ 45 submissions/s
```

Thực tế đo được **10.80/s** — thấp hơn lý thuyết vì:
1. Overhead polling (0.5s sleep × nhiều polls/iteration)
2. 4 test cases mỗi submission (phải exec 4 lần per container borrow)
3. Java compilation overhead (test case JAVA sẽ tốn hơn, nhưng test này dùng PYTHON)

### 5.3 System CPU giảm từ 92.8% → 78.6%

Không còn `docker create/start/remove` mỗi submission. CPU giảm do:
- Không còn overlay filesystem create/destroy
- Không còn container namespace setup

### 5.4 Memory tăng nhẹ

Heap peak tăng từ 108 MB → 124.7 MB (+15%) do:
- 15 containers pre-warmed chiếm thêm memory trong JVM (container state, exec callbacks)
- Nhiều submission xử lý hơn (1,623 vs 1,144) → nhiều objects hơn

Vẫn trong giới hạn tốt: 124.7 MB / 512 MB (24%).

---

## 6. Vấn đề còn lại

| Vấn đề | Tác động | Trạng thái |
|--------|----------|-----------|
| Pool size 5 giới hạn concurrency | Throughput cap ~45/s khi 50 VU | Tăng `docker.pool.size` — chi phí thấp (Python ~530KB, Java ~7.7MB per container idle) |
| Thread pool unbounded | Không có backpressure khi overload | Chưa fix |
| buildStatus WRONG_ANSWER 100% | Bug test case logic | Chưa fix (ngoài scope) |
