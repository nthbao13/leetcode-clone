const BASE = '/api/v1';

// --- Stubs shown in editor (LeetCode style) ---
const STUBS = {
  PYTHON: `from typing import List

class Solution:
    def twoSum(self, nums: List[int], target: int) -> List[int]:
        `,
  JAVA: `class Solution {
    public int[] twoSum(int[] nums, int target) {

    }
}`,
  CPP: `class Solution {
public:
    vector<int> twoSum(vector<int>& nums, int target) {

    }
};`,
};

// --- Boilerplate that wraps the user's stub before sending to backend ---
const WRAP = {
  PYTHON: (stub) => `import json, sys
${stub}

data = json.loads(input())
sol = Solution()
print(json.dumps(sol.twoSum(data[0], data[1])))
`,

  JAVA: (stub) => `import java.util.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

${stub.replace('class Solution', 'public class Solution')}

class __Runner {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        Gson gson = new Gson();
        JsonArray data = gson.fromJson(sc.nextLine(), JsonArray.class);
        int[] nums = gson.fromJson(data.get(0), int[].class);
        int target = data.get(1).getAsInt();
        int[] result = new Solution().twoSum(nums, target);
        System.out.println(gson.toJson(result));
    }
}`,

  CPP: (stub) => `#include <bits/stdc++.h>
#include <nlohmann/json.hpp>
using namespace std;
using json = nlohmann::json;

${stub}

int main() {
    string line; getline(cin, line);
    auto data = json::parse(line);
    vector<int> nums = data[0].get<vector<int>>();
    int target = data[1].get<int>();
    Solution sol;
    auto res = sol.twoSum(nums, target);
    cout << json(res).dump() << endl;
}`,
};

// --- Monaco setup ---
let editor;

require(['vs/editor/editor.main'], () => {
  editor = monaco.editor.create(document.getElementById('monaco-editor'), {
    value: STUBS.PYTHON,
    language: 'python',
    theme: 'vs-dark',
    fontSize: 14,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    automaticLayout: true,
    tabSize: 4,
    insertSpaces: true,
    lineNumbers: 'on',
    fontFamily: "'Fira Code', 'Menlo', 'Consolas', monospace",
    fontLigatures: true,
  });
});

const MONACO_LANG = { PYTHON: 'python', JAVA: 'java', CPP: 'cpp' };

document.getElementById('language').addEventListener('change', function () {
  const lang = this.value;
  if (!editor) return;
  monaco.editor.setModelLanguage(editor.getModel(), MONACO_LANG[lang]);
  editor.setValue(STUBS[lang]);
  setResult('placeholder');
});

// --- Submit ---
document.getElementById('btn-submit').addEventListener('click', submit);

async function submit() {
  if (!editor) return;
  const language = document.getElementById('language').value;
  const stub     = editor.getValue();
  const code     = WRAP[language](stub);

  setResult('pending', 'Submitting…');
  const btn = document.getElementById('btn-submit');
  btn.disabled = true;

  try {
    const res = await fetch(`${BASE}/submission`, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({ problemId: 1, codeSubmit: code, language }),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const { id } = await res.json();
    if (!id) throw new Error('No ID returned');

    setResult('pending', 'Running…');
    await poll(id);
  } catch (e) {
    setResult('error', `Error: ${e.message}`);
  } finally {
    btn.disabled = false;
  }
}

async function poll(id) {
  for (let i = 0; i < 60; i++) {
    await sleep(500);
    const data = await fetch(`${BASE}/submission/${id}`).then(r => r.json());
    if (data.submitStatus === 'DONE') { showResult(data); return; }
  }
  setResult('error', 'Timed out (30s)');
}

function showResult({ buildStatus, output, errorOutput, runtimeMs }) {
  if      (buildStatus === 'SUCCESS')             { localStorage.setItem('solved_1', '1'); setResult('accepted', '✓ Accepted', `Runtime: ${runtimeMs} ms`); }
  else if (buildStatus === 'WRONG_ANSWER')        setResult('wrong',    '✗ Wrong Answer', `Output: ${output ?? '(empty)'}`);
  else if (buildStatus === 'TIME_LIMIT_EXCEED')   setResult('error',    '⏱ Time Limit Exceeded');
  else if (buildStatus === 'COMPILE_ERROR')       setResult('error',    '✗ Compile Error', errorOutput);
  else if (buildStatus === 'MEMORY_LIMIT_EXCEED') setResult('error',    '✗ Memory Limit Exceeded');
  else                                            setResult('error',    `${buildStatus}`);
}

function setResult(type, main = '', detail = '') {
  const el = document.getElementById('result-area');
  if (type === 'placeholder') {
    el.innerHTML = `<span class="result-placeholder">Submit your code to see the result</span>`;
    return;
  }
  const cls = { pending: 'result-pending', accepted: 'result-accepted', wrong: 'result-wrong', error: 'result-error' };
  const spin = type === 'pending' ? '<span class="spinner"></span>' : '';
  el.innerHTML = `<div class="${cls[type] ?? ''}">${spin}${main}</div>`
    + (detail ? `<div class="result-detail">${esc(detail)}</div>` : '');
}

const esc   = s => String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
const sleep = ms => new Promise(r => setTimeout(r, ms));
