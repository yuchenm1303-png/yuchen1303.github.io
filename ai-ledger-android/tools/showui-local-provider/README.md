# Local ShowUI GUI Provider

This tool runs `showlab/ShowUI-2B` locally on Windows and exposes an HTTP GUI
provider for AI Ledger. It converts a screenshot plus a goal into compact
`tap_xy` JSON for the Alibaba Cloud backend.

It does not modify or depend on the Android app build. It is only a local test
tool under `ai-ledger-android/tools/showui-local-provider/`.

## Quick Start

Open PowerShell and enter the tool directory:

```powershell
cd "C:\Users\邹羽宸\OneDrive\文档\New project 2-cloud-dev-update-1\ai-ledger-android\tools\showui-local-provider"
```

First-time install:

```powershell
.\bootstrap-windows.ps1
```

Start service:

```powershell
.\start-windows.ps1
```

Stop service:

```powershell
.\stop-windows.ps1
```

Restart service:

```powershell
.\restart-windows.ps1
```

`run-windows.ps1` is still available as a compatibility entry and forwards to
`start-windows.ps1`.

## Health Check

Keep the service PowerShell window open. Open a second PowerShell window and run:

```powershell
curl http://127.0.0.1:9100/health
```

Expected response includes:

```json
{
  "ok": true,
  "name": "showui-local-provider",
  "model": "showlab/ShowUI-2B",
  "coordinateSystem": "normalized_full_screenshot_0_1",
  "modelLoaded": true,
  "cudaMemoryAllocatedMb": 1234.5,
  "cudaMemoryReservedMb": 1234.5
}
```

## Test A Screenshot

Run this in the second PowerShell window:

```powershell
.\test-provider.ps1 -ImagePath "C:\Users\邹羽宸\qq.png" -Goal "Click the Contacts tab."
```

Success criteria:

```json
{
  "s": "p",
  "a": "tap_xy",
  "x": 0.5,
  "y": 0.9
}
```

`x` and `y` are normalized full-screen coordinates in the `0..1` range.

## What The Scripts Do

`start-windows.ps1` automatically checks port `9100`. If an old local ShowUI
service is still listening, it calls `stop-windows.ps1` to kill only the process
listening on that port before starting the new service.

The scripts do not require manual `conda activate`.

The scripts do not require `python` or `pip` to exist in the system `PATH`.

All install, verification, and start commands run through:

```powershell
conda run -n showui python ...
```

`start-windows.ps1` prints the current directory, conda path, Python version,
PyTorch version, CUDA state, GPU name, `SHOWUI_MAX_PIXELS`,
`SHOWUI_MAX_NEW_TOKENS`, service URL, and health URL.

## Environment Defaults

`start-windows.ps1` sets conservative defaults before launching the server:

```text
SHOWUI_HOST=127.0.0.1
SHOWUI_PORT=9100
SHOWUI_MAX_PIXELS=301056
SHOWUI_MAX_NEW_TOKENS=32
```

Optional API key:

```powershell
$env:SHOWUI_PROVIDER_API_KEY="test-key"
```

If this key is set, `POST /` requires:

```text
Authorization: Bearer test-key
```

`GET /health` does not require authorization.

## If It Hangs

Check the `start-windows.ps1` window first. The server prints logs when it loads
the model, receives a request, starts inference, finishes inference, or fails to
parse coordinates.

The model is preloaded during server startup, before the first POST request. If
`/health` returns `modelLoaded=false`, POST requests will immediately return
`need_user_help` with `ShowUI model not loaded` instead of waiting forever.

Run:

```powershell
nvidia-smi
```

If the service is really running inference, you should usually see `python.exe`
using GPU memory. The first startup can be slow because the model
`showlab/ShowUI-2B` may need to be downloaded.

If VRAM is insufficient, lower image size and restart:

```powershell
$env:SHOWUI_MAX_PIXELS="200704"
$env:SHOWUI_MAX_NEW_TOKENS="32"
.\restart-windows.ps1
```

You can also try:

```powershell
$env:SHOWUI_MAX_PIXELS="301056"
$env:SHOWUI_MAX_NEW_TOKENS="32"
.\restart-windows.ps1
```

If `CUDA=False`:

- PyTorch may have been installed as the CPU-only build.
- Your NVIDIA driver may be too old or may not match the selected CUDA wheel.
- Upgrade the NVIDIA driver, or use the current Windows CUDA wheel recommended
  by the PyTorch official selector.

## Temporary Alibaba Cloud Access

The Alibaba Cloud backend cannot call `127.0.0.1` on your laptop directly. For
temporary testing, expose local port `9100` through Cloudflare Tunnel or ngrok:

```powershell
cloudflared tunnel --url http://127.0.0.1:9100
```

or:

```powershell
ngrok http 9100
```

Then configure the Alibaba Cloud backend:

```text
AGENT_GUI_PROVIDER=external_http
AGENT_GUI_PROVIDER_URL=https://your-public-tunnel-url
AGENT_GUI_PROVIDER_API_KEY=test-key
AGENT_GUI_PROVIDER_TIMEOUT_MS=8000
AGENT_GUI_PROVIDER_FALLBACK_TO_QWEN=false
AGENT_ACTION_BATCH_MAX=1
```

Use the same `AGENT_GUI_PROVIDER_API_KEY` as `SHOWUI_PROVIDER_API_KEY`. If you
do not set `SHOWUI_PROVIDER_API_KEY`, leave the backend API key empty for this
temporary test.

This is a test setup, not a long-term production deployment. Your laptop,
`server.py`, and the tunnel process must all stay running.
