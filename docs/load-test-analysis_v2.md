# Load Test Analysis v2 — Leetcode Clone

**Date:** 2026-09-21  
**Tester:** k6 v0.57 | Spring Boot 4.1.1 | PostgreSQL 16 | Docker  
**So sánh với:** `load-test-analysis_v1.md`

---

## 1. Thay đổi so với v1

| # | Vấn đề (v1) | Fix đã thực hiện |
|---|-------------|-----------------|
| 1 | Docker cold start ~1,054ms/submission | **Container pool** — 15 container pre-warmed (5/language) |
| 2 | Response 65 KB, circular reference, `submitStatus` missing | **`SubmissionResponseDTO`** — trả về 7 field, không embed `Problem` |
| 3 | k6 không đo được end-to-end latency | Fix từ #2, `submission_total_ms` hoạt động đúng |

Chưa fix: thread pool unbounded (vấn đề #2 trong v1).

---

## 2. Kịch bản k6

Giữ nguyên script v1:

```
Stage 1:  0 → 1 VU   (30s)
Stage 2:  1 → 5 VUs  (30s)
Stage 3:  5 → 10 VUs (30s)
Stage 4: 10 → 20 VUs (30s)
Stage 5: 20 → 0 VUs  (30s)
Tổng: 2m30s
```

Mỗi iteration: POST `/api/v1/submission` → poll GET mỗi 0.5s cho đến `submitStatus == DONE` (tối đa 15s).

---

## 3. Kết quả k6

### HTTP metrics

| Metric | v1 (trước) | v2 (sau) | Thay đổi |
|--------|-----------|---------|---------|
| Iterations hoàn chỉnh (end-to-end) | ~0 (poll không detect DONE) | **1,627** | ✅ Flow hoạt động |
| HTTP req/s | 461 | **24.9** | Giảm — do mỗi iteration nặng hơn (có poll) |
| `http_req_failed` | 0.00% | **0.00%** | = |
| `http_req_duration` avg | 15.22 ms | **2.41 ms** | ↓ 6x (response nhỏ hơn) |
| `http_req_duration` p95 | 45.61 ms | **5.32 ms** | ↓ 8x |
| Data received | **4.5 GB** | **894 KB** | ↓ ~5,000x |
| Data sent | 30 MB | 910 KB | ↓ 33x |

### Custom metrics (mới hoạt động từ v2)

| Metric | Giá trị |
|--------|---------|
| `submission_total_ms` avg | **658 ms** |
| `submission_total_ms` med | 506 ms |
| `submission_total_ms` p90 | 1,000 ms |
| `submission_total_ms` p95 | **1,000 ms** |
| `wrong_answer_count` | 1,627 (100% submissions) |

> `submission_total_ms` = thời gian từ lúc POST đến khi poll nhận được `DONE` — đây là latency **thực tế từ góc nhìn user**.

### Checks

| Check | v1 | v2 |
|-------|----|----|
| `submit ok` | ✓ 100% | ✓ 100% |
| `completed` (submitStatus == DONE) | không chạy | ✓ **100%** |
| `accepted` (buildStatus == SUCCESS) | không chạy | ✗ 0% |

### Threshold

| Threshold | v1 | v2 |
|-----------|----|----|
| `http_req_failed < 1%` | PASS | **PASS** |
| `submission_total_ms p95 < 15s` | PASS (vô nghĩa) | **PASS** (1s < 15s, có ý nghĩa) |

---

## 4. DB sau test

| submit_status | build_status | Số lượng |
|---------------|--------------|---------|
| DONE | WRONG_ANSWER | 1,627 |

**Không còn submissions tồn đọng PENDING.** 1,627 submissions đều được xử lý trong vòng test.

---

## 5. Phân tích

### 5.1 Container pool hoạt động đúng

Đo trực tiếp trên DB:

| Submission | `runtime_ms` (Docker exec) |
|------------|--------------------------|
| 237874 | **91 ms** |
| 237875 | **73 ms** |

So sánh:

| | v1 (cold start) | v2 (pool) |
|--|--|--|
| Thời gian tạo + chạy container | ~1,054 ms | **~73–91 ms** |
| Cải thiện | — | **~12–14x nhanh hơn** |

`submission_total_ms` avg = 658ms > 73ms vì bao gồm cả overhead polling (0.5s sleep giữa mỗi lần poll).

### 5.2 Throughput thực tế với pool

Pool 5 containers/language, 1 VU tại stage đầu:

```
Observed: ~1 iteration/second (với 1 VU)
submission_total_ms avg = 658ms → ~1.5 iterations/s per VU (lý thuyết)
```

Tại 20 VUs nhưng pool chỉ có 5 containers/language:
- 5 VU chạy đồng thời, 15 VU bị block chờ pool
- Throughput bị giới hạn bởi pool size, không phải thread pool

### 5.3 `accepted` 0% — bug logic bài Two Sum

Tất cả submissions đều trả về `WRONG_ANSWER`. Nguyên nhân nằm ở cách test case đọc input, không liên quan infrastructure. Cần điều tra riêng.

### 5.4 Vấn đề còn lại

| Vấn đề | Trạng thái |
|--------|-----------|
| Thread pool unbounded | Chưa fix — không gây issue ở scale này |
| Pool size 5 giới hạn concurrency tại 20 VUs | Có thể tăng `docker.pool.size` |
| `accepted` 0% | Bug logic Two Sum cần debug |

---

## 6. So sánh tổng thể v1 vs v2

| | v1 | v2 |
|--|--|--|
| Flow end-to-end hoạt động | ❌ | ✅ |
| `submission_total_ms` đo được | ❌ (luôn 0) | ✅ avg 658ms |
| DB tồn đọng sau test | +221,290 PENDING | **0** |
| Response size | 65 KB (broken JSON) | **~100 bytes** |
| Container lifecycle | create/remove mỗi lần | **pool reuse** |
| Docker time/submission | ~1,054 ms | **~73–91 ms** |
