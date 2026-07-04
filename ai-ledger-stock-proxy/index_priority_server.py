from __future__ import annotations

import logging
from copy import deepcopy
from datetime import datetime, timezone
from threading import Lock
from time import monotonic
from typing import Any

import httpx
from fastapi import Response

import market_home_server as market_home


app = market_home.app
legacy = market_home.legacy
LOGGER = logging.getLogger("ai-ledger-stock-proxy.index-priority")

INDEX_PRIORITY_QUOTES_PATH = "/api/stock/a-share/index/priority/quotes"
INDEX_PRIORITY_CACHE_VERSION = "v1-dedicated-ulist-quotes"
INDEX_PRIORITY_FRESH_SECONDS = 3.0
INDEX_PRIORITY_STALE_SECONDS = 6 * 60 * 60.0
_INDEX_SECURITIES = (
    {"code": "000001", "name": "上证指数", "secid": "1.000001"},
    {"code": "399001", "name": "深证成指", "secid": "0.399001"},
    {"code": "399006", "name": "创业板指", "secid": "0.399006"},
)
_INDEX_ULIST_URLS = (
    "https://push2.eastmoney.com/api/qt/ulist.np/get",
    "https://push2delay.eastmoney.com/api/qt/ulist.np/get",
    "https://push2his.eastmoney.com/api/qt/ulist.np/get",
)
_CACHE_KEY = legacy._cache_key(
    "index-priority-quotes",
    "000001,399001,399006",
    INDEX_PRIORITY_CACHE_VERSION,
)
_client_lock = Lock()
_cache_lock = Lock()
_priority_client: httpx.Client | None = None


def _remove_get_routes(paths: set[str]) -> None:
    app.router.routes[:] = [
        route
        for route in app.router.routes
        if not (
            getattr(route, "path", None) in paths
            and "GET" in (getattr(route, "methods", None) or set())
        )
    ]


def _get_priority_client() -> httpx.Client:
    global _priority_client
    with _client_lock:
        if _priority_client is None:
            _priority_client = httpx.Client(
                timeout=httpx.Timeout(1.25, connect=0.35, pool=0.20),
                limits=httpx.Limits(
                    max_connections=4,
                    max_keepalive_connections=4,
                    keepalive_expiry=60.0,
                ),
                headers={
                    "User-Agent": (
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        "AppleWebKit/537.36 Chrome/125 Safari/537.36"
                    ),
                    "Referer": "https://quote.eastmoney.com/",
                    "Origin": "https://quote.eastmoney.com",
                    "Accept": "application/json, text/plain, */*",
                    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
                    "Connection": "keep-alive",
                },
            )
        return _priority_client


@app.on_event("shutdown")
def _close_index_priority_client() -> None:
    global _priority_client
    with _client_lock:
        if _priority_client is not None:
            _priority_client.close()
            _priority_client = None


def _with_cache_label(payload: dict[str, Any], age_seconds: float, stale: bool) -> dict[str, Any]:
    cached = deepcopy(payload)
    cached["cacheHit"] = True
    cached["cacheAgeMs"] = max(int(age_seconds * 1000), 0)
    if stale and cached.get("status") in {"ok", "partial"}:
        cached["status"] = "stale"
    cached["warnings"] = list(cached.get("warnings") or []) + [
        f"index_priority_cache: {'stale' if stale else 'hit'} age={age_seconds:.2f}s"
    ]
    return cached


def _build_priority_quotes() -> dict[str, Any]:
    started_at = monotonic()
    warnings: list[str] = []
    raw = legacy._eastmoney_get_first(
        _get_priority_client(),
        list(_INDEX_ULIST_URLS),
        {
            "secids": ",".join(item["secid"] for item in _INDEX_SECURITIES),
            "fields": "f12,f14,f2,f3,f4,f5,f6,f15,f16,f17,f18",
            "fltt": "2",
        },
        "index_priority_quotes",
        warnings,
    )
    diff = list((raw.get("data") or {}).get("diff") or [])
    by_code = {str(item.get("f12") or ""): item for item in diff}
    now_iso = datetime.now(timezone.utc).isoformat()
    items: list[dict[str, Any]] = []
    for security in _INDEX_SECURITIES:
        item = by_code.get(security["code"])
        if not item:
            warnings.append(f"index_priority_{security['code']}_missing")
            continue
        items.append(
            {
                "code": security["code"],
                "name": legacy._safe_str(item.get("f14"), security["name"]),
                "price": legacy._format_price(item.get("f2")),
                "changeAmount": legacy._format_signed(item.get("f4")),
                "changePercent": legacy._format_percent(item.get("f3")),
                "open": legacy._format_price(item.get("f17")),
                "high": legacy._format_price(item.get("f15")),
                "low": legacy._format_price(item.get("f16")),
                "previousClose": legacy._safe_float(item.get("f18")),
                "amount": legacy._format_cn_money(item.get("f6")),
                "volume": legacy._format_lots(item.get("f5")),
                "updatedAt": now_iso,
            }
        )
    if not items:
        raise ValueError("index priority quotes returned no real items")
    latency_ms = int((monotonic() - started_at) * 1000)
    return {
        "provider": "eastmoney_index_priority_quotes",
        "status": "ok" if len(items) == len(_INDEX_SECURITIES) else "partial",
        "priority": "highest",
        "dedicated": True,
        "items": items,
        "updatedAt": now_iso,
        "cacheHit": False,
        "cacheAgeMs": 0,
        "latencyMs": latency_ms,
        "warnings": warnings + [f"index_priority_quotes_build_ms={latency_ms}"],
    }


def load_index_priority_quotes_cached(force: bool = False) -> dict[str, Any]:
    if not force:
        fresh = legacy._cache_get_seconds(_CACHE_KEY, INDEX_PRIORITY_FRESH_SECONDS)
        if fresh is not None:
            payload, age = fresh
            return _with_cache_label(payload, age, stale=False)

    with _cache_lock:
        if not force:
            fresh = legacy._cache_get_seconds(_CACHE_KEY, INDEX_PRIORITY_FRESH_SECONDS)
            if fresh is not None:
                payload, age = fresh
                return _with_cache_label(payload, age, stale=False)
        stale = legacy._cache_get_seconds(_CACHE_KEY, INDEX_PRIORITY_STALE_SECONDS)
        try:
            payload = _build_priority_quotes()
        except Exception:
            if stale is not None:
                old, age = stale
                return _with_cache_label(old, age, stale=True)
            raise
        legacy._cache_put(_CACHE_KEY, payload)
        return payload


_remove_get_routes({INDEX_PRIORITY_QUOTES_PATH})


@app.get(INDEX_PRIORITY_QUOTES_PATH, response_class=Response)
async def a_share_index_priority_quotes(response: Response) -> Response:
    try:
        payload = load_index_priority_quotes_cached()
    except Exception as exc:
        LOGGER.exception("index priority quotes failed")
        raise market_home.legacy.HTTPException(
            status_code=502,
            detail=f"三大指数最高优先级报价暂不可用：{type(exc).__name__}: {exc}",
        ) from exc
    response = legacy._fast_json_response(payload)
    response.headers["X-AI-Ledger-Stock-Priority"] = "index-quotes-highest"
    response.headers["X-AI-Ledger-Stock-Path"] = "index-priority-quotes"
    response.headers["Server-Timing"] = f"index-priority;dur={int(payload.get('latencyMs') or 0)}"
    return response
