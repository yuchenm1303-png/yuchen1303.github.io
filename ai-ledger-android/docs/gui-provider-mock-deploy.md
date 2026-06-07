# GUI Provider Mock Cloud Deploy

This mock provider is a standalone HTTP service for validating the AI Ledger
backend `external_http` GUI provider path. It returns compact action JSON such
as `{ s, a, x, y, b, t, r, q, c, e }` and uses full-screen normalized
coordinates in the `0..1` range.

Long-term backend configuration should use a real cloud HTTPS URL. Do not set
`AGENT_GUI_PROVIDER_URL` to `127.0.0.1`, `localhost`, a private LAN address, or
a short-lived local tunnel URL unless you are only doing temporary validation.

## Local Test

Run from `ai-ledger-android/`:

```bash
npm run gui-provider:test
```

Expected result:

```text
GUI provider mock tests passed
```

The test starts the provider, checks `GET /`, verifies that `goal=进入联系人`
returns `tap_xy`, and verifies that high-risk goals such as `发送验证码` and
`转账付款` return `need_user_help`.

## Local Start

Linux/macOS:

```bash
GUI_PROVIDER_API_KEY=test-key GUI_PROVIDER_PORT=9100 npm run gui-provider:mock
```

Windows PowerShell:

```powershell
$env:GUI_PROVIDER_API_KEY="test-key"
$env:GUI_PROVIDER_PORT="9100"
npm run gui-provider:mock
```

Health check:

```bash
curl http://127.0.0.1:9100
```

## Docker Build

Run from the repository root:

```bash
docker build -f ai-ledger-android/gui-provider-mock.Dockerfile -t ai-ledger-gui-provider-mock ai-ledger-android
```

## Docker Run

```bash
docker run --rm \
  -p 9100:9100 \
  -e GUI_PROVIDER_API_KEY=test-key \
  -e GUI_PROVIDER_PORT=9100 \
  ai-ledger-gui-provider-mock
```

Health check:

```bash
curl http://127.0.0.1:9100
```

POST validation request:

```bash
curl -s http://127.0.0.1:9100 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer test-key" \
  -d '{
    "goal": "进入联系人",
    "screen": {
      "currentApp": "微信",
      "screenshot": { "width": 1080, "height": 2400 },
      "texts": ["微信", "通讯录", "发现", "我"],
      "clickableNodes": [
        { "text": "微信", "bounds": { "left": 0, "top": 2260, "right": 270, "bottom": 2340 }, "clickable": true },
        { "text": "通讯录", "bounds": { "left": 270, "top": 2260, "right": 540, "bottom": 2340 }, "clickable": true },
        { "text": "发现", "bounds": { "left": 540, "top": 2260, "right": 810, "bottom": 2340 }, "clickable": true },
        { "text": "我", "bounds": { "left": 810, "top": 2260, "right": 1080, "bottom": 2340 }, "clickable": true }
      ]
    }
  }'
```

Expected compact response:

```json
{"s":"p","a":"tap_xy","x":0.375,"y":0.9583,"b":[0.25,0.9417,0.5,0.975],"t":"通讯录","r":"low","q":false}
```

High-risk validation request:

```bash
curl -s http://127.0.0.1:9100 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer test-key" \
  -d '{"goal":"转账付款","screen":{"currentApp":"微信","screenshot":{"width":1080,"height":2400},"clickableNodes":[{"text":"转账","bounds":"[100,100][300,200]","clickable":true}]}}'
```

Expected behavior: response action is `need_user_help`, with no `x`, `y`, or
`b` coordinates.

## Render Deployment

Render is the simplest path when you want a stable HTTPS URL.

1. Push this repository branch to GitHub.
2. In Render, create a new `Web Service`.
3. Select the GitHub repository `yuchenm1303-png/yuchen1303.github.io`.
4. Select branch `dev-update-1`.
5. Choose `Docker` runtime.
6. Set Dockerfile path:

```text
ai-ledger-android/gui-provider-mock.Dockerfile
```

7. Set Docker build context:

```text
ai-ledger-android
```

8. Add environment variables:

```text
GUI_PROVIDER_API_KEY=test-key
GUI_PROVIDER_PORT=9100
```

Render may inject `PORT`; the mock provider supports both `PORT` and
`GUI_PROVIDER_PORT`, so this is fine.

After deploy, Render gives an HTTPS URL like:

```text
https://your-service-name.onrender.com
```

Use that URL as `AGENT_GUI_PROVIDER_URL`.

## Railway Deployment

Railway can also deploy this Dockerfile and give a public HTTPS domain.

1. Create a new Railway project from GitHub.
2. Select `yuchenm1303-png/yuchen1303.github.io` and branch `dev-update-1`.
3. Configure Dockerfile path:

```text
ai-ledger-android/gui-provider-mock.Dockerfile
```

4. Configure build context:

```text
ai-ledger-android
```

5. Add variables:

```text
GUI_PROVIDER_API_KEY=test-key
GUI_PROVIDER_PORT=9100
```

6. Generate or enable the public Railway domain.

Use the generated HTTPS domain as `AGENT_GUI_PROVIDER_URL`.

## Google Cloud Run Deployment

Cloud Run is a good option if you already use Google Cloud.

Build and deploy from the repository root:

```bash
gcloud builds submit ai-ledger-android \
  --tag gcr.io/PROJECT_ID/ai-ledger-gui-provider-mock \
  --dockerfile ai-ledger-android/gui-provider-mock.Dockerfile

gcloud run deploy ai-ledger-gui-provider-mock \
  --image gcr.io/PROJECT_ID/ai-ledger-gui-provider-mock \
  --platform managed \
  --region asia-east1 \
  --allow-unauthenticated \
  --set-env-vars GUI_PROVIDER_API_KEY=test-key
```

Cloud Run provides `PORT` automatically. The mock provider reads `PORT`, so no
extra port variable is required.

## Cloudflare Tunnel / ngrok

Cloudflare Tunnel and ngrok are only recommended for temporary validation.

Examples:

```bash
cloudflared tunnel --url http://localhost:9100
ngrok http 9100
```

These commands expose the machine that is currently running the mock provider.
If they run inside a temporary Codex environment, the public URL points to that
temporary environment, not your computer or a durable server. The URL can change
or stop working when the tunnel process exits.

For long-term Alibaba Cloud backend configuration, deploy to Render, Railway,
Cloud Run, Alibaba Cloud ECS, Alibaba Cloud Function Compute, or another stable
cloud service with HTTPS.

## Alibaba Cloud Backend Variables

After cloud deployment, configure the backend with:

```text
AGENT_GUI_PROVIDER=external_http
AGENT_GUI_PROVIDER_URL=https://your-public-provider-url
AGENT_GUI_PROVIDER_API_KEY=test-key
AGENT_GUI_PROVIDER_TIMEOUT_MS=2500
AGENT_GUI_PROVIDER_FALLBACK_TO_QWEN=true
AGENT_ACTION_BATCH_MAX=1
```

`AGENT_GUI_PROVIDER_URL` must be the public HTTPS URL from the cloud platform.
Do not use `http://127.0.0.1:9100` for Alibaba Cloud backend deployment.
