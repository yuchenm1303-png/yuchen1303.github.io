# Local ShowUI GUI Provider

This tool starts a local HTTP GUI provider for AI Ledger. It converts a mobile
screenshot plus a target goal into a compact `tap_xy` action that the Alibaba
Cloud backend can call through `AGENT_GUI_PROVIDER=external_http`.

It is intentionally not wired into the Android project. It is a local Windows
tool for testing `showlab/ShowUI-2B` on your own NVIDIA GPU.

## Why Local

Free hosted CPU inference is usually too slow for GUI grounding, and hosted GPU
inference costs money. A local RTX 3070 Ti Laptop GPU with 8GB VRAM is a good
first test path if image size is kept conservative.

## Requirements

- Windows
- NVIDIA GPU, 8GB VRAM or more recommended
- Recent NVIDIA driver
- Python 3.10
- Conda recommended
- CUDA PyTorch installed from the official PyTorch selector

`requirements.txt` deliberately does not include `torch`, because Windows CUDA
PyTorch should be installed with the wheel that matches your driver/CUDA setup.

## Install

```powershell
cd ai-ledger-android\tools\showui-local-provider
conda create -n showui python=3.10 -y
conda activate showui
```

Install PyTorch CUDA from the official selector:

```text
https://pytorch.org/get-started/locally/
```

Common CUDA 12.1 example:

```powershell
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121
```

If your driver supports a newer CUDA wheel, you can choose `cu126` or `cu128`
from the PyTorch site.

Then install the provider dependencies:

```powershell
pip install -r requirements.txt
```

## Start

```powershell
.\run-windows.ps1
```

Default service address:

```text
http://127.0.0.1:9100
```

Default environment variables:

```text
SHOWUI_MODEL_ID=showlab/ShowUI-2B
SHOWUI_HOST=127.0.0.1
SHOWUI_PORT=9100
SHOWUI_MAX_PIXELS=602112
SHOWUI_MIN_PIXELS=200704
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

## Check CUDA

```powershell
python -c "import torch; print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU')"
```

Expected for local GPU inference:

```text
True
NVIDIA GeForce RTX 3070 Ti Laptop GPU
```

If CUDA is not available, check the NVIDIA driver and make sure PyTorch was not
installed as the CPU-only build.

## Test With A Screenshot

Start the server first, then run:

```powershell
.\test-provider.ps1 -ImagePath "C:\Users\you\Pictures\qq.png" -Goal "Click the Contacts tab."
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

`x` and `y` are always clamped normalized full-screen coordinates in the `0..1`
range. If ShowUI returns pixel coordinates, the provider converts them using
the screenshot width and height.

## HTTP API

Health:

```http
GET /health
```

Example response:

```json
{
  "ok": true,
  "name": "showui-local-provider",
  "model": "showlab/ShowUI-2B",
  "device": "cuda",
  "coordinateSystem": "normalized_full_screenshot_0_1"
}
```

Planning:

```http
POST /
```

Backend-compatible payload:

```json
{
  "goal": "Click the Contacts tab.",
  "screen": {
    "screenshot": {
      "base64": "...",
      "mimeType": "image/jpeg",
      "width": 1080,
      "height": 2400,
      "displayWidth": 1080,
      "displayHeight": 2400
    },
    "texts": [],
    "clickableNodes": []
  },
  "supportedSteps": []
}
```

Simple test payload:

```json
{
  "goal": "Click the Contacts tab.",
  "imageBase64": "..."
}
```

## OOM Tips

8GB VRAM can be tight. If you hit out-of-memory errors, lower the image token
budget:

```powershell
$env:SHOWUI_MAX_PIXELS="401408"
$env:SHOWUI_MAX_NEW_TOKENS="32"
```

For a more conservative setting:

```powershell
$env:SHOWUI_MAX_PIXELS="301056"
```

Restart `server.py` after changing these values.

## Temporary Alibaba Cloud Access

The Alibaba Cloud backend cannot call `127.0.0.1` on your laptop directly. For
temporary testing, expose the local service with Cloudflare Tunnel or ngrok:

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

Important notes:

- Your laptop must stay powered on.
- `server.py` must keep running.
- The tunnel process must keep running.
- Free tunnel URLs may change. If the URL changes, update
  `AGENT_GUI_PROVIDER_URL` in Alibaba Cloud.
- This is a test setup, not a long-term production deployment.
