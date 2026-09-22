import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const submissionDuration = new Trend('submission_total_ms', true);
const tleCounts = new Counter('tle_count');
const wrongAnswerCounts = new Counter('wrong_answer_count');
const successCounts = new Counter('success_count');
const poolExhaustedCounts = new Counter('pool_exhausted_count');

export const options = {
    stages: [
        { duration: '30s', target: 5 },
        { duration: '30s', target: 20 },
        { duration: '30s', target: 35 },
        { duration: '30s', target: 50 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],
        submission_total_ms: ['p(95)<15000'],
    },
};

const BASE_URL = 'http://localhost:8080';

// Two Sum — array 10k phần tử [1..10000], target=19999, answer=[9998,9999]
const NUMS = Array.from({ length: 10000 }, (_, i) => i + 1);
const TARGET = 19999;

const CODE = `
import json, sys
data = json.loads(input())
nums, target = data[0], data[1]
seen = {}
for i, n in enumerate(nums):
    diff = target - n
    if diff in seen:
        print(json.dumps([seen[diff], i]))
        sys.exit(0)
    seen[n] = i
`.trim();

export default function () {
    const start = Date.now();

    const submitRes = http.post(
        `${BASE_URL}/api/v1/submission`,
        JSON.stringify({ problemId: 1, codeSubmit: CODE, language: 'PYTHON' }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    check(submitRes, { 'submit ok': (r) => r.status === 200 });

    const id = submitRes.json('id');
    if (!id) {
        poolExhaustedCounts.add(1);
        return;
    }

    // Poll until DONE (max 15s)
    let submitStatus = '';
    let buildStatus = '';
    for (let i = 0; i < 30; i++) {
        sleep(0.5);
        const pollRes = http.get(`${BASE_URL}/api/v1/submission/${id}`);
        submitStatus = pollRes.json('submitStatus');
        buildStatus = pollRes.json('buildStatus');
        if (submitStatus === 'DONE') break;
    }

    submissionDuration.add(Date.now() - start);

    check({ submitStatus, buildStatus }, {
        'completed': (s) => s.submitStatus === 'DONE',
        'accepted':  (s) => s.buildStatus === 'SUCCESS',
    });

    if (buildStatus === 'TIME_LIMIT_EXCEED') tleCounts.add(1);
    if (buildStatus === 'WRONG_ANSWER') wrongAnswerCounts.add(1);
    if (buildStatus === 'SUCCESS') successCounts.add(1);
}
