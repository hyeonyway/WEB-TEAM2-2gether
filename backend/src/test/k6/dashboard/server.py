#!/usr/bin/env python3
"""로컬 k6 부하테스트 대시보드. 표준 라이브러리만 사용, 별도 설치 불필요.

    python3 backend/src/test/k6/dashboard/server.py [--port 8787]

브라우저에서 http://127.0.0.1:8787 접속.
"""
import argparse
import json
import shutil
import subprocess
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

K6_DIR = Path(__file__).resolve().parent.parent
RESULT_DIR = K6_DIR / "result"

SCENARIOS = {
    "pure-throughput": {
        "script": "scenarios/pure-throughput.js",
        "needs_sse": True,
        "fields": [
            ("SSE_VUS", "SSE 동시접속 (250/500/1000)", "250"),
            ("STAGE_DURATION", "QPS 계단 하나당 유지시간", "2m"),
            ("QPS_STAGES", "QPS 계단 (콤마구분)", "50,100,150,200,300,400"),
            ("LOAD_TEST_USER_COUNT", "테스트 유저 수 (기본=SSE_VUS)", ""),
            ("SSE_RAMP_UP", "SSE 램프업 시간", "30s"),
        ],
    },
    "hot-auction-pattern": {
        "script": "scenarios/hot-auction-pattern.js",
        "needs_sse": True,
        "fields": [
            ("SSE_USERS", "SSE 동시접속 유저 수", "500"),
            ("HOT_AUCTION_COUNT", "핫 경매 개수", "3"),
            ("HOT_AUCTION_RATE", "핫 경매 1개당 초당 입찰", "14"),
            ("COLD_AUCTION_RATE_PER_AUCTION", "일반 경매 1개당 초당 입찰", "0.09"),
            ("DURATION", "본 구간 유지시간", "5m"),
            ("AUCTION_IDS", "대상 경매 ID (콤마구분, 비우면 자동조회)", ""),
            ("HOT_AUCTION_IDS", "그중 핫 경매로 쓸 ID (콤마구분)", ""),
        ],
    },
    "bid-only-load": {
        "script": "scenarios/bid-only-load.js",
        "needs_sse": False,
        "fields": [
            ("LOAD_TEST_USER_COUNT", "테스트 유저 수", "500"),
            ("STAGE_DURATION", "QPS 계단 하나당 유지시간", "2m"),
            ("QPS_STAGES", "QPS 계단 (콤마구분)", "50,100,150,200,300,400"),
            ("HOT_AUCTION_ID", "특정 경매에 집중 (비우면 분산)", ""),
            ("REST_DURATION", "계단 사이 휴식시간 (비우면 없음)", ""),
            ("REST_TARGET", "휴식구간 target QPS", "0"),
            ("AUCTION_IDS", "대상 경매 ID (콤마구분, 비우면 자동조회)", ""),
        ],
    },
}

ADVANCED_FIELDS = [
    ("LOGIN_BATCH_SIZE", "로그인 배치 크기", "25"),
    ("PRE_ALLOCATED_VUS", "사전할당 VU", "200"),
    ("MAX_VUS", "최대 VU", "1000"),
    ("SETUP_TIMEOUT", "setup() 타임아웃", "15m"),
    ("LOAD_TEST_PASSWORD", "테스트 계정 공통 비밀번호", "K6LoadTest123!"),
]

RUNS_LOCK = threading.Lock()
RUNS = {}
ACTIVE_RUN_ID = None


def build_command(scenario, base_url, params):
    config = SCENARIOS[scenario]
    if config["needs_sse"]:
        binary = K6_DIR / "sse" / "k6-sse"
        if not binary.exists():
            raise RuntimeError(f"SSE k6 바이너리 없음: {binary} (backend/src/test/k6/sse/README.md 참고해서 빌드)")
        binary = str(binary)
    else:
        binary = shutil.which("k6")
        if not binary:
            raise RuntimeError("k6가 설치돼 있지 않습니다: brew install k6")

    cmd = [binary, "run"]
    cmd += ["-e", f"BASE_URL={base_url}"]
    for key, value in params.items():
        if value != "":
            cmd += ["-e", f"{key}={value}"]
    cmd += [config["script"]]
    return cmd


def run_job(run_id, cmd, result_file):
    RESULT_DIR.mkdir(exist_ok=True)
    log_path = RESULT_DIR / f"{run_id}.log"
    returncode = None
    try:
        with open(log_path, "w") as log_file:
            log_file.write(f"$ {' '.join(cmd)}\n\n")
            log_file.flush()
            proc = subprocess.Popen(cmd, cwd=K6_DIR, stdout=log_file, stderr=subprocess.STDOUT)
            with RUNS_LOCK:
                RUNS[run_id]["proc"] = proc
            proc.wait()
            returncode = proc.returncode
    except Exception as exception:
        with open(log_path, "a") as log_file:
            log_file.write(f"\n[dashboard] 실행 실패: {exception}\n")
        returncode = -1
    finally:
        with RUNS_LOCK:
            RUNS[run_id]["returncode"] = returncode
            RUNS[run_id]["finished_at"] = time.time()
            RUNS[run_id]["result_file"] = str(result_file) if result_file else None
            global ACTIVE_RUN_ID
            if ACTIVE_RUN_ID == run_id:
                ACTIVE_RUN_ID = None


def start_run(scenario, base_url, params):
    global ACTIVE_RUN_ID
    with RUNS_LOCK:
        if ACTIVE_RUN_ID is not None:
            raise RuntimeError("이미 실행 중인 테스트가 있습니다. 먼저 중지하거나 끝날 때까지 기다리세요.")
        run_id = uuid.uuid4().hex[:12]
        RUNS[run_id] = {"proc": None, "returncode": None, "started_at": time.time(), "finished_at": None,
                         "scenario": scenario, "result_file": None}
        ACTIVE_RUN_ID = run_id

    timestamp = time.strftime("%Y%m%d-%H%M%S")
    result_file = f"result/dashboard-{scenario}-{timestamp}.json"
    params = dict(params)
    params["K6_RESULT_FILE"] = result_file
    cmd = build_command(scenario, base_url, params)
    thread = threading.Thread(target=run_job, args=(run_id, cmd, RESULT_DIR / f"dashboard-{scenario}-{timestamp}.json"), daemon=True)
    thread.start()
    return run_id


def stop_run(run_id):
    # proc가 아직 None인 시작 직후 찰나에 중지를 누르면 여기서도 같은 공백에 걸릴 수
    # 있다 — 몇 번 짧게 재시도해서 실제로 Popen된 뒤의 proc를 잡는다.
    for _ in range(20):
        with RUNS_LOCK:
            run = RUNS.get(run_id)
            proc = run["proc"] if run else None
            finished = run is not None and run["returncode"] is not None
        if finished or not run:
            return
        if proc is not None:
            if proc.poll() is None:
                proc.terminate()
            return
        time.sleep(0.1)


def read_log_tail(run_id, max_chars=12000):
    log_path = RESULT_DIR / f"{run_id}.log"
    if not log_path.exists():
        return ""
    data = log_path.read_text(errors="replace")
    return data[-max_chars:]


PAGE = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>k6 부하테스트 대시보드</title>
<style>
  body { font-family: -apple-system, sans-serif; max-width: 900px; margin: 2rem auto; padding: 0 1rem; color: #1a1a1a; }
  h1 { font-size: 1.3rem; }
  fieldset { border: 1px solid #ddd; border-radius: 8px; margin-bottom: 1rem; }
  legend { padding: 0 0.5rem; font-weight: 600; }
  label { display: block; margin: 0.5rem 0 0.2rem; font-size: 0.9rem; color: #444; }
  input, select, textarea { width: 100%; box-sizing: border-box; padding: 0.4rem; font-size: 0.95rem; }
  .scenario-fields { display: none; }
  .scenario-fields.active { display: block; }
  button { padding: 0.6rem 1.2rem; font-size: 1rem; cursor: pointer; margin-right: 0.5rem; }
  #log { background: #111; color: #ddd; padding: 1rem; border-radius: 8px; height: 350px; overflow-y: auto;
         white-space: pre-wrap; font-family: ui-monospace, monospace; font-size: 0.8rem; }
  #status { margin: 0.5rem 0; font-weight: 600; }
  .running { color: #b45309; }
  .done-ok { color: #15803d; }
  .done-fail { color: #b91c1c; }
</style>
</head>
<body>
<h1>k6 부하테스트 대시보드</h1>

<label>시나리오</label>
<select id="scenario"></select>

<label>BASE_URL</label>
<input id="base_url" value="https://api.dbidding.shop">

<fieldset><legend>시나리오 파라미터</legend><div id="scenario-panels"></div></fieldset>

<fieldset><legend>고급 옵션</legend><div id="advanced-panel"></div></fieldset>

<label>추가 환경변수 (한 줄에 KEY=VALUE)</label>
<textarea id="extra" rows="3" placeholder="SSE_DURATION=10m"></textarea>

<p>
  <button id="run-btn" onclick="runTest()">실행</button>
  <button id="stop-btn" onclick="stopTest()" disabled>중지</button>
</p>

<div id="status"></div>
<div id="log"></div>

<script>
const SCENARIOS = __SCENARIOS_JSON__;
const ADVANCED = __ADVANCED_JSON__;
let currentRunId = null;
let pollTimer = null;

function el(tag, attrs, children) {
  const e = document.createElement(tag);
  Object.entries(attrs || {}).forEach(([k, v]) => e.setAttribute(k, v));
  (children || []).forEach(c => e.appendChild(c));
  return e;
}

function buildFieldRow(key, label, def) {
  const wrap = document.createElement('div');
  wrap.appendChild(el('label', {for: 'f_' + key}, [document.createTextNode(label)]));
  wrap.appendChild(el('input', {id: 'f_' + key, 'data-key': key, value: def}));
  return wrap;
}

function init() {
  const sel = document.getElementById('scenario');
  Object.keys(SCENARIOS).forEach(name => sel.appendChild(el('option', {value: name}, [document.createTextNode(name)])));
  sel.addEventListener('change', renderScenarioPanel);

  const panels = document.getElementById('scenario-panels');
  Object.entries(SCENARIOS).forEach(([name, config]) => {
    const panel = el('div', {class: 'scenario-fields', 'data-scenario': name});
    config.fields.forEach(([key, label, def]) => panel.appendChild(buildFieldRow(key, label, def)));
    panels.appendChild(panel);
  });

  const advPanel = document.getElementById('advanced-panel');
  ADVANCED.forEach(([key, label, def]) => advPanel.appendChild(buildFieldRow(key, label, def)));

  renderScenarioPanel();
}

function renderScenarioPanel() {
  const name = document.getElementById('scenario').value;
  document.querySelectorAll('.scenario-fields').forEach(p => p.classList.toggle('active', p.dataset.scenario === name));
}

function collectParams(scope) {
  const params = {};
  scope.querySelectorAll('input[data-key]').forEach(input => { params[input.dataset.key] = input.value.trim(); });
  return params;
}

async function runTest() {
  const scenario = document.getElementById('scenario').value;
  const baseUrl = document.getElementById('base_url').value.trim();
  const activePanel = document.querySelector('.scenario-fields.active');
  const params = Object.assign({}, collectParams(activePanel), collectParams(document.getElementById('advanced-panel')));
  document.getElementById('extra').value.split('\\n').map(l => l.trim()).filter(Boolean).forEach(line => {
    const idx = line.indexOf('=');
    if (idx > 0) params[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
  });

  const res = await fetch('/api/run', {
    method: 'POST', headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({scenario, base_url: baseUrl, params}),
  });
  const data = await res.json();
  if (!res.ok) { alert(data.error || '실행 실패'); return; }
  currentRunId = data.run_id;
  document.getElementById('run-btn').disabled = true;
  document.getElementById('stop-btn').disabled = false;
  document.getElementById('log').textContent = '';
  poll();
}

async function stopTest() {
  if (!currentRunId) return;
  await fetch('/api/stop/' + currentRunId, {method: 'POST'});
}

async function poll() {
  if (!currentRunId) return;
  const res = await fetch('/api/status/' + currentRunId);
  const data = await res.json();
  document.getElementById('log').textContent = data.log;
  document.getElementById('log').scrollTop = 1e9;
  const statusEl = document.getElementById('status');
  if (data.running) {
    statusEl.textContent = '실행 중... (' + Math.round(data.elapsed) + 's)';
    statusEl.className = 'running';
    pollTimer = setTimeout(poll, 1500);
  } else {
    const ok = data.returncode === 0;
    statusEl.textContent = (ok ? '완료' : '종료(코드 ' + data.returncode + ')') +
      (data.result_file ? ' — 결과: ' + data.result_file : '');
    statusEl.className = ok ? 'done-ok' : 'done-fail';
    document.getElementById('run-btn').disabled = false;
    document.getElementById('stop-btn').disabled = true;
  }
}

init();
</script>
</body>
</html>
"""


class Handler(BaseHTTPRequestHandler):
    def _json(self, obj, status=200):
        body = json.dumps(obj).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/":
            scenarios_json = json.dumps({name: {"fields": cfg["fields"]} for name, cfg in SCENARIOS.items()})
            body = PAGE.replace("__SCENARIOS_JSON__", scenarios_json).replace("__ADVANCED_JSON__", json.dumps(ADVANCED_FIELDS)).encode()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        if self.path.startswith("/api/status/"):
            run_id = self.path.rsplit("/", 1)[-1]
            with RUNS_LOCK:
                run = RUNS.get(run_id)
            if not run:
                self._json({"error": "unknown run"}, 404)
                return
            # proc는 백그라운드 스레드가 subprocess.Popen()까지 실행해야 채워지는데,
            # start_run()이 run_id를 반환한 직후 프론트가 바로 첫 폴링을 날리면 그 사이
            # (아직 None)에 걸릴 수 있다 — 그 순간 "proc is not None"으로 판정하면 아직
            # 시작도 안 한 걸 "이미 끝남"으로 잘못 읽는다(#560). returncode는 run_job()의
            # finally에서 실제 종료 후에만 채워지므로, 이걸로 판정해야 시작 전 공백까지
            # 정확히 "아직 실행 중"으로 잡힌다.
            running = run["returncode"] is None
            elapsed = (run["finished_at"] or time.time()) - run["started_at"]
            self._json({
                "running": running, "returncode": run["returncode"], "elapsed": elapsed,
                "result_file": run["result_file"], "log": read_log_tail(run_id),
            })
            return
        self.send_error(404)

    def do_POST(self):
        if self.path == "/api/run":
            length = int(self.headers.get("Content-Length", 0))
            body = json.loads(self.rfile.read(length) or b"{}")
            scenario = body.get("scenario")
            base_url = body.get("base_url") or "http://localhost:8080"
            params = body.get("params") or {}
            if scenario not in SCENARIOS:
                self._json({"error": "알 수 없는 시나리오"}, 400)
                return
            try:
                run_id = start_run(scenario, base_url, params)
            except RuntimeError as exception:
                self._json({"error": str(exception)}, 400)
                return
            self._json({"run_id": run_id})
            return
        if self.path.startswith("/api/stop/"):
            run_id = self.path.rsplit("/", 1)[-1]
            stop_run(run_id)
            self._json({"ok": True})
            return
        self.send_error(404)

    def log_message(self, format, *args):
        pass


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8787)
    args = parser.parse_args()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"k6 dashboard: http://127.0.0.1:{args.port}")
    server.serve_forever()


if __name__ == "__main__":
    main()
