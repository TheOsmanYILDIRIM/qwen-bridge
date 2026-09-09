# 🌉 Qwen Bridge (Android App & Local API Proxy)

An Android application acting as a local OpenAI-compatible API bridge (`http://localhost:8787/v1`) for **Qwen AI**, with automated bot challenge & CAPTCHA overlay solver.

## 🚀 Key Features

1. **Local OpenAI API Proxy**:
   - Runs an embedded NanoHTTPD server on `localhost:8787`.
   - Compatible endpoints: `/v1/chat/completions`, `/v1/models`, `/v1/validate`, `/health`.
   - Real-time streaming (SSE) and OpenAI tool-calling translation.

2. **Automated Challenge & WAF Solver**:
   - Detects Alibaba Baxia WAF / Cloudflare Turnstile bot challenges on `chat.qwen.ai`.
   - Surfaces a floating interactive overlay on top of any active app using `TYPE_APPLICATION_OVERLAY`.
   - Automatically harvests and synchronizes session cookies (`cf_clearance`, `bx-ua`, `token`).

3. **Aggressive Anti-Kill Keep-Alive Architecture**:
   - **Foreground Service**: `foregroundServiceType="specialUse"` with persistent notification.
   - **MediaSession Trick**: Simulates active media session to bypass OEM aggressive task killers.
   - **Partial WakeLock**: Maintains CPU execution during long queries with screen locked.
   - **AlarmManager Watchdog & Boot Receiver**: Auto-restarts on system boot.
   - **OEM Setup Wizard**: Step-by-step guidance for Xiaomi (MIUI/HyperOS), Samsung (One UI), Huawei (EMUI).

4. **Jetpack Compose UI**:
   - Material 3 Dark theme.
   - Live server dashboard with start/stop switch and rapid clipboard copying.
   - Token manager with embedded web login extractor.
   - Live HTTP request telemetry and latency logs.

## 🛠️ Integration with Antigravity / OpenCode MCP

Set your Qwen MCP endpoint to localhost:
```bash
qwen_set_token --endpoint http://localhost:8787/v1
```
Or test via curl:
```bash
curl http://127.0.0.1:8787/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen3.8-max",
    "messages": [{"role": "user", "content": "Hello Qwen!"}]
  }'
```
