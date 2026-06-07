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

Start later:

```powershell
.\start-windows.ps1
```

`run-windows.ps1` is still available as a compatibility entry and forwards to
`start-windows.ps1`.

## Why This Works Without Activate

The scripts do not require manual `conda activate`.

The scripts do not require `python` or `pip` to exist in the system `PATH`.

All install, verification, and start commands run through:

```powershell
conda run -n showui python ...
```

The bootstrap script also finds `conda.exe` from common Miniconda/Anaconda
install paths if `Get-Command conda` cannot find it.

## Test Health

Open another PowerShell after the service starts:

```powershell
curl http://127.0.0.1:9100/health
```

Expected response includes:

```json
{
  "ok": true,
  "name": "showui-local-provider",
  "model": "showlab/ShowUI-2B",
  "coordinateSystem": "normalized_full_screenshot_0_1"
}
```

## Test A Screenshot

```powershell
.\test-provider.ps1 -ImagePath "C:\Users\邹羽宸\Pictures\qq.png" -Goal "Click the Contacts tab."
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

## Environment Defaults

`start-windows.ps1` sets these defaults before launching the server:

```text
SHOWUI_HOST=127.0.0.1
SHOWUI_PORT=9100
SHOWUI_MAX_PIXELS=602112
SHOWUI_MAX_NEW_TOKENS=64
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

## Common Problems

If conda is not found:

- Install Miniconda.
- Reopen PowerShell.
- Run `.\bootstrap-windows.ps1` again.

If `CUDA=False`:

- PyTorch may have been installed as the CPU-only build.
- Your NVIDIA driver may be too old or may not match the selected CUDA wheel.
- Upgrade the NVIDIA driver, or use the current Windows CUDA wheel recommended
  by the PyTorch official selector.

Default CUDA 12.1 install used by the bootstrap script:

```powershell
conda run -n showui python -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121
```

If VRAM is insufficient:

```powershell
$env:SHOWUI_MAX_PIXELS="401408"
```

For a more conservative setting:

```powershell
$env:SHOWUI_MAX_PIXELS="301056"
```

Then run:

```powershell
.\start-windows.ps1
```

First startup can be slow because the model `showlab/ShowUI-2B` may need to be
downloaded.

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
