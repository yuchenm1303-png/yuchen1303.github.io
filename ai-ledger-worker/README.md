# AI Ledger Worker

Cloudflare Worker for AI Ledger cloud understanding and structured command output.

## API

- `GET /health` returns:

```json
{
  "ok": true,
  "version": "ai-ledger-worker-command-protocol-v1"
}
```

- `POST /` accepts chat messages, ledger context, command protocol metadata, navigation context, and web search flags from the frontend.

The Worker always returns JSON with:

```json
{
  "reply": "给用户看的简短回复",
  "action": "chat | draft | mobile_command",
  "records": [],
  "mobileCommand": null,
  "source": "gemini_structured"
}
```

## Gemini Secret

Do not commit Gemini API keys. Configure the key as a Cloudflare Worker secret:

```bash
wrangler secret put GEMINI_API_KEY
```

The model name is configured through `GEMINI_MODEL` in `wrangler.toml`.

## Web Search

The Worker currently has no real search provider integration. When the request asks for forced search, it returns a JSON response that explicitly says the cloud side has not connected a real search source instead of inventing search results.
