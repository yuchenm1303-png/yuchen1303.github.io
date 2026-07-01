from __future__ import annotations

import asyncio
import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
from copy import deepcopy
from datetime import datetime, timezone
from threading import Lock
from time import monotonic
from typing import Any, Callable

from fastapi import HTTPException, Query

import index_detail_server as detail


app = detail.app
legacy = detail.legacy
LOGGER = logging.getLogger("ai-ledger-stock-proxy.index-compact")

INDEX_COMPACT_PATH = "/api/stock/a-share/index/compact"
INDEX_COMPACT_CACHE_VERSION = "v1-quote-minute"
INDEX_COMPACT_FRESH_SECONDS = 8.0
INDEX_COMPACT_STALE_SECONDS = 6 * 60 * 60.0
_INDEX_COMPACT_LOCK = Lock()


def _build_index_compact(security: dict[str, str]) -> dict[str, Any]:
    started_at = monotonic()
    warnings: list[str] = []
    results: dict[str, Any] = {}
    tasks: dict[str, Callable[[], Any]] = {
        "quote": lambda: detail._load_index_quote(security, warnings),
        "minutePoints": lambda: detail._load_index_minutes(security, 1, warnings),
    }
    with ThreadPoolExecutor(
        max_workers=2,
        thread_name_prefix="index-compact",
    ) as executor:
        futures = {executor.submit(builder): name for name, builder in tasks.items()}
        for future in as_completed(futures):
            name = futures[future]
            try:
                results[name] = future.result()
            except Exception as exc:
                warnings.append(f"{name}: {type(exc).__name__}: {exc}")
                results[name] = None

    quote = results.get("quote")
    if not quote:
        raise ValueError("index compact quote unavailable")
    minute_points = list(results.get("minutePoints") or [])
    status = "ok" if minute_points else "partial"
    return {
        "provider": "eastmoney_index_compact",
        "status": status,
        "compact": True,
        "code": security["code"],
        "name": security["name"],
        "secid": security["secid"],
        "quote": quote,
        "minutePoints": minute_points,
        "dataSourceLabel": f"指数真实行情 · {security['name']} · 东方财富公开 JSON",
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "cacheHit": False,
        "cacheAgeMs": 0,
        "totalLatencyMs": int((monotonic() - started_at) * 1000),
        "warnings": warnings,
    }


def _with_compact_cache_label(
    payload: dict[str, Any],
    age_seconds: float,
    stale: bool,
) -> dict[str, Any]:
    cached = deepcopy(payload)
    cached["cacheHit"] = True
    cached["cacheAgeMs"] = max(int(age_seconds * 1000), 0)
    if stale and cached.get("status") in {"ok", "partial"}:
        cached["status"] = "stale"
    cached["warnings"] = list(cached.get("warnings") or []) + [
        f"index_compact_cache: {'stale' if stale else 'hit'} age={age_seconds:.2f}s"
    ]
    return cached


def _load_index_compact_cached(security: dict[str, str]) -> dict[str, Any]:
    key = legacy._cache_key(
        "index-compact",
        security["code"],
        INDEX_COMPACT_CACHE_VERSION,
    )
    fresh = legacy._cache_get_seconds(key, INDEX_COMPACT_FRESH_SECONDS)
    if fresh is not None:
        payload, age = fresh
        return _with_compact_cache_label(payload, age, stale=False)

    with _INDEX_COMPACT_LOCK:
        fresh = legacy._cache_get_seconds(key, INDEX_COMPACT_FRESH_SECONDS)
        if fresh is not None:
            payload, age = fresh
            return _with_compact_cache_label(payload, age, stale=False)
        try:
            payload = _build_index_compact(security)
        except Exception:
            stale = legacy._cache_get_seconds(key, INDEX_COMPACT_STALE_SECONDS)
            if stale is not None:
                cached_payload, age = stale
                return _with_compact_cache_label(cached_payload, age, stale=True)
            raise
        legacy._cache_put(key, payload)
        return payload


@app.get(INDEX_COMPACT_PATH)
async def a_share_index_compact(
    query: str = Query(..., min_length=1, max_length=32),
) -> dict[str, Any]:
    security = detail._resolve_index(query)
    try:
        return await asyncio.to_thread(_load_index_compact_cached, security)
    except HTTPException:
        raise
    except Exception as exc:
        LOGGER.exception("index compact failed for %s", security["code"])
        raise HTTPException(
            status_code=502,
            detail=f"指数轻量行情暂不可用：{type(exc).__name__}: {exc}",
        ) from exc
