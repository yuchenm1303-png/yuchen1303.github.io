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

import market_home_server as market_home
from realtime_runtime import CN_TZ


app = market_home.app
legacy = market_home.legacy
LOGGER = logging.getLogger("ai-ledger-stock-proxy.index-compact")

INDEX_COMPACT_PATH = "/api/stock/a-share/index/compact"
INDEX_COMPACT_BATCH_PATH = "/api/stock/a-share/index/compact/batch"
INDEX_COMPACT_QUOTES_PATH = "/api/stock/a-share/index/compact/quotes"
INDEX_COMPACT_TREND_PATH = "/api/stock/a-share/index/compact/trend"
INDEX_COMPACT_CACHE_VERSION = "v5-priority-four-lane"
INDEX_COMPACT_FRESH_SECONDS = 10.0
INDEX_COMPACT_STALE_SECONDS = 6 * 60 * 60.0
INDEX_COMPACT_BATCH_CODES = ("000001", "399001", "399006")

_HERO_SECURITIES = (
    {"code": "000001", "name": "上证指数", "secid": "1.000001"},
    {"code": "399001", "name": "深证成指", "secid": "0.399001"},
    {"code": "399006", "name": "创业板指", "secid": "0.399006"},
)
_HERO_BY_CODE = {item["code"]: item for item in _HERO_SECURITIES}
_HERO_ALIAS_TO_CODE = {
    "上证": "000001",
    "上证指数": "000001",
    "沪指": "000001",
    "深证": "399001",
    "深证成指": "399001",
    "深成指": "399001",
    "创业板": "399006",
    "创业板指": "399006",
}
_HERO_ULIST_URLS = (
    "https://push2delay.eastmoney.com/api/qt/ulist.np/get",
    "https://push2his.eastmoney.com/api/qt/ulist.np/get",
    "https://push2.eastmoney.com/api/qt/ulist.np/get",
)
_HERO_TRENDS_URLS = (
    "https://push2delay.eastmoney.com/api/qt/stock/trends2/get",
    "https://push2his.eastmoney.com/api/qt/stock/trends2/get",
    "https://push2.eastmoney.com/api/qt/stock/trends2/get",
)
_QUOTES_CACHE_KEY = legacy._cache_key(
    "tools-index-quotes",
    "000001,399001,399006",
    INDEX_COMPACT_CACHE_VERSION,
)
_BATCH_CACHE_KEY = legacy._cache_key(
    "tools-index-hero",
    "000001,399001,399006",
    INDEX_COMPACT_CACHE_VERSION,
)
_QUOTES_LOCK = Lock()
_BATCH_LOCK = Lock()
_TREND_LOCKS_GUARD = Lock()
_TREND_LOCKS: dict[str, Lock] = {}
_warm_task: asyncio.Task[None] | None = None


def _resolve_index(query: str) -> dict[str, str]:
    keyword = query.strip()
    if not keyword:
        raise HTTPException(status_code=400, detail="指数代码或名称不能为空")
    digits = "".join(char for char in keyword if char.isdigit())
    if len(digits) == 6 and digits in _HERO_BY_CODE:
        return deepcopy(_HERO_BY_CODE[digits])
    code = _HERO_ALIAS_TO_CODE.get(keyword.replace(" ", "").lower())
    if code:
        return deepcopy(_HERO_BY_CODE[code])
    raise HTTPException(status_code=404, detail=f"功能页暂不支持该指数：{keyword}")


def _trend_lock(code: str) -> Lock:
    with _TREND_LOCKS_GUARD:
        lock = _TREND_LOCKS.get(code)
        if lock is None:
            lock = Lock()
            _TREND_LOCKS[code] = lock
        return lock


def _load_quotes_batch() -> tuple[dict[str, dict[str, Any]], list[str]]:
    warnings: list[str] = []
    raw = legacy._eastmoney_get_first(
        market_home._get_shared_client(),
        list(_HERO_ULIST_URLS),
        {
            "secids": ",".join(item["secid"] for item in _HERO_SECURITIES),
            "fields": "f12,f14,f2,f3,f4,f5,f6,f15,f16,f17,f18",
            "fltt": "2",
        },
        "tools_index_quotes_batch",
        warnings,
    )
    diff = list((raw.get("data") or {}).get("diff") or [])
    by_code = {str(item.get("f12") or ""): item for item in diff}
    quotes: dict[str, dict[str, Any]] = {}
    now_iso = datetime.now(timezone.utc).isoformat()
    for security in _HERO_SECURITIES:
        raw_item = by_code.get(security["code"])
        if not raw_item:
            warnings.append(f"tools_index_quote_{security['code']}_missing")
            continue
        quotes[security["code"]] = {
            "code": security["code"],
            "name": legacy._safe_str(raw_item.get("f14"), security["name"]),
            "price": legacy._format_price(raw_item.get("f2")),
            "changeAmount": legacy._format_signed(raw_item.get("f4")),
            "changePercent": legacy._format_percent(raw_item.get("f3")),
            "open": legacy._format_price(raw_item.get("f17")),
            "high": legacy._format_price(raw_item.get("f15")),
            "low": legacy._format_price(raw_item.get("f16")),
            "previousClose": legacy._safe_float(raw_item.get("f18")),
            "amount": legacy._format_cn_money(raw_item.get("f6")),
            "volume": legacy._format_lots(raw_item.get("f5")),
            "updatedAt": now_iso,
        }
    if not quotes:
        raise ValueError("tools index batch quotes are empty")
    return quotes, warnings


def _build_quotes_payload() -> dict[str, Any]:
    started_at = monotonic()
    quotes, warnings = _load_quotes_batch()
    items = [deepcopy(quotes[code]) for code in INDEX_COMPACT_BATCH_CODES if code in quotes]
    return {
        "provider": "eastmoney_tools_index_quotes",
        "status": "ok" if len(items) == len(INDEX_COMPACT_BATCH_CODES) else "partial",
        "priority": "highest",
        "items": items,
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "cacheHit": False,
        "cacheAgeMs": 0,
        "latencyMs": int((monotonic() - started_at) * 1000),
        "warnings": warnings,
    }


def _parse_minutes(raw: dict[str, Any]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    volumes: list[float] = []
    for item in ((raw.get("data") or {}).get("trends") or []):
        parts = str(item).split(",")
        if len(parts) < 8 or " " not in parts[0]:
            continue
        date_text, time_text = parts[0].split(" ", 1)
        price = legacy._safe_float(parts[2])
        if price <= 0:
            continue
        volume = max(legacy._safe_float(parts[5]), 0.0)
        volumes.append(volume)
        rows.append(
            {
                "date": date_text,
                "time": time_text[:5],
                "timestamp": int(
                    datetime.strptime(
                        f"{date_text} {time_text[:5]}",
                        "%Y-%m-%d %H:%M",
                    ).replace(tzinfo=CN_TZ).timestamp()
                    * 1000
                ),
                "price": price,
                "average": legacy._safe_float(parts[7], price),
                "volume": volume,
                "volumeRatio": 0.0,
            }
        )
    if not rows:
        raise ValueError("tools index minute trends are empty")
    max_volume = max(volumes or [1.0])
    for row in rows:
        row["volumeRatio"] = min(max(row["volume"] / max_volume, 0.02), 1.0)
    rows.sort(key=lambda row: int(row["timestamp"]))
    return rows


def _build_trend(security: dict[str, str]) -> dict[str, Any]:
    warnings: list[str] = []
    started_at = monotonic()
    raw = legacy._eastmoney_get_first(
        market_home._get_shared_client(),
        list(_HERO_TRENDS_URLS),
        {
            "secid": security["secid"],
            "fields1": "f1,f2,f3,f4,f5,f6,f7,f8",
            "fields2": "f51,f52,f53,f54,f55,f56,f57,f58",
            "iscr": "0",
            "ndays": "1",
        },
        f"tools_index_minute_{security['code']}",
        warnings,
    )
    return {
        "provider": "eastmoney_tools_index_trend",
        "status": "ok",
        "code": security["code"],
        "name": security["name"],
        "minutePoints": _parse_minutes(raw),
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "cacheHit": False,
        "cacheAgeMs": 0,
        "latencyMs": int((monotonic() - started_at) * 1000),
        "warnings": warnings,
    }


def _with_cache_label(payload: dict[str, Any], age_seconds: float, stale: bool) -> dict[str, Any]:
    cached = deepcopy(payload)
    cached["cacheHit"] = True
    cached["cacheAgeMs"] = max(int(age_seconds * 1000), 0)
    if stale and cached.get("status") in {"ok", "partial"}:
        cached["status"] = "stale"
    cached["warnings"] = list(cached.get("warnings") or []) + [
        f"tools_index_cache: {'stale' if stale else 'hit'} age={age_seconds:.2f}s"
    ]
    return cached


def _load_quotes_cached() -> dict[str, Any]:
    fresh = legacy._cache_get_seconds(_QUOTES_CACHE_KEY, INDEX_COMPACT_FRESH_SECONDS)
    if fresh is not None:
        payload, age = fresh
        return _with_cache_label(payload, age, stale=False)
    with _QUOTES_LOCK:
        fresh = legacy._cache_get_seconds(_QUOTES_CACHE_KEY, INDEX_COMPACT_FRESH_SECONDS)
        if fresh is not None:
            payload, age = fresh
            return _with_cache_label(payload, age, stale=False)
        stale = legacy._cache_get_seconds(_QUOTES_CACHE_KEY, INDEX_COMPACT_STALE_SECONDS)
        try:
            payload = _build_quotes_payload()
        except Exception:
            if stale is not None:
                old, age = stale
                return _with_cache_label(old, age, stale=True)
            raise
        legacy._cache_put(_QUOTES_CACHE_KEY, payload)
        return payload


def _load_trend_cached(security: dict[str, str]) -> dict[str, Any]:
    key = legacy._cache_key(
        "tools-index-trend",
        security["code"],
        INDEX_COMPACT_CACHE_VERSION,
    )
    fresh = legacy._cache_get_seconds(key, INDEX_COMPACT_FRESH_SECONDS)
    if fresh is not None:
        payload, age = fresh
        return _with_cache_label(payload, age, stale=False)
    with _trend_lock(security["code"]):
        fresh = legacy._cache_get_seconds(key, INDEX_COMPACT_FRESH_SECONDS)
        if fresh is not None:
            payload, age = fresh
            return _with_cache_label(payload, age, stale=False)
        stale = legacy._cache_get_seconds(key, INDEX_COMPACT_STALE_SECONDS)
        try:
            payload = _build_trend(security)
        except Exception:
            if stale is not None:
                old, age = stale
                return _with_cache_label(old, age, stale=True)
            raise
        legacy._cache_put(key, payload)
        return payload


def _build_batch() -> dict[str, Any]:
    started_at = monotonic()
    results: dict[str, Any] = {}
    tasks: dict[str, Callable[[], Any]] = {
        "quotes": _load_quotes_cached,
        **{
            f"trend:{security['code']}": lambda security=security: _load_trend_cached(security)
            for security in _HERO_SECURITIES
        },
    }
    errors: dict[str, str] = {}
    with ThreadPoolExecutor(max_workers=4, thread_name_prefix="tools-index-hero") as executor:
        futures = {executor.submit(builder): name for name, builder in tasks.items()}
        for future in as_completed(futures):
            name = futures[future]
            try:
                results[name] = future.result()
            except Exception as exc:
                errors[name] = f"{type(exc).__name__}: {exc}"

    quote_payload = dict(results.get("quotes") or {})
    quotes = {
        str(item.get("code") or ""): item
        for item in list(quote_payload.get("items") or [])
    }
    if not quotes:
        raise ValueError(errors.get("quotes") or "tools index quotes unavailable")

    items: list[dict[str, Any]] = []
    for security in _HERO_SECURITIES:
        trend = dict(results.get(f"trend:{security['code']}") or {})
        quote = deepcopy(quotes.get(security["code"]) or {})
        warnings = list(quote_payload.get("warnings") or []) + list(trend.get("warnings") or [])
        trend_error = errors.get(f"trend:{security['code']}")
        if trend_error:
            warnings.append(f"minutePoints: {trend_error}")
        minute_points = list(trend.get("minutePoints") or [])
        items.append(
            {
                "provider": "eastmoney_tools_index_hero",
                "status": "ok" if quote and minute_points else "partial",
                "compact": True,
                "code": security["code"],
                "name": security["name"],
                "secid": security["secid"],
                "quote": quote,
                "minutePoints": minute_points,
                "updatedAt": str(quote.get("updatedAt") or trend.get("updatedAt") or ""),
                "warnings": warnings,
            }
        )

    loaded_count = sum(bool(item.get("quote")) for item in items)
    complete_count = sum(
        bool(item.get("quote")) and bool(item.get("minutePoints")) for item in items
    )
    return {
        "provider": "eastmoney_tools_index_hero_batch",
        "status": "ok" if complete_count == len(items) else "partial",
        "firstBatch": True,
        "dedicated": True,
        "splitPriority": True,
        "items": items,
        "loadedCount": loaded_count,
        "completeCount": complete_count,
        "requestedCount": len(items),
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "totalLatencyMs": int((monotonic() - started_at) * 1000),
        "warnings": list(errors.values()),
    }


def _merge_with_stale(current: dict[str, Any], stale: dict[str, Any]) -> dict[str, Any]:
    current_by_code = {
        str(item.get("code") or ""): deepcopy(item)
        for item in list(current.get("items") or [])
    }
    stale_by_code = {
        str(item.get("code") or ""): deepcopy(item)
        for item in list(stale.get("items") or [])
    }
    merged_items: list[dict[str, Any]] = []
    any_stale_used = False
    for security in _HERO_SECURITIES:
        code = security["code"]
        item = current_by_code.get(code) or {
            "code": code,
            "name": security["name"],
            "quote": {},
            "minutePoints": [],
            "warnings": [],
        }
        old = stale_by_code.get(code) or {}
        item_stale_used = False
        if not item.get("quote") and old.get("quote"):
            item["quote"] = deepcopy(old["quote"])
            item_stale_used = True
        if not item.get("minutePoints") and old.get("minutePoints"):
            item["minutePoints"] = deepcopy(old["minutePoints"])
            item_stale_used = True
        if item_stale_used:
            any_stale_used = True
            item["warnings"] = list(item.get("warnings") or []) + [
                "tools_index_hero: preserved_previous_success"
            ]
        item["status"] = (
            "stale"
            if item_stale_used and item.get("quote") and item.get("minutePoints")
            else "ok"
            if item.get("quote") and item.get("minutePoints")
            else "partial"
        )
        merged_items.append(item)

    merged = deepcopy(current)
    merged["items"] = merged_items
    merged["loadedCount"] = sum(bool(item.get("quote")) for item in merged_items)
    merged["completeCount"] = sum(
        bool(item.get("quote")) and bool(item.get("minutePoints"))
        for item in merged_items
    )
    if merged["completeCount"] == len(merged_items):
        merged["status"] = "stale" if any_stale_used else "ok"
    return merged


def _load_batch_cached() -> dict[str, Any]:
    fresh = legacy._cache_get_seconds(_BATCH_CACHE_KEY, INDEX_COMPACT_FRESH_SECONDS)
    if fresh is not None:
        payload, age = fresh
        return _with_cache_label(payload, age, stale=False)
    with _BATCH_LOCK:
        fresh = legacy._cache_get_seconds(_BATCH_CACHE_KEY, INDEX_COMPACT_FRESH_SECONDS)
        if fresh is not None:
            payload, age = fresh
            return _with_cache_label(payload, age, stale=False)
        stale = legacy._cache_get_seconds(_BATCH_CACHE_KEY, INDEX_COMPACT_STALE_SECONDS)
        try:
            payload = _build_batch()
        except Exception:
            if stale is not None:
                old, age = stale
                return _with_cache_label(old, age, stale=True)
            raise
        if stale is not None and int(payload.get("completeCount") or 0) < len(_HERO_SECURITIES):
            old, _ = stale
            payload = _merge_with_stale(payload, old)
        legacy._cache_put(_BATCH_CACHE_KEY, payload)
        return payload


@app.on_event("startup")
async def _warm_tools_index_hero() -> None:
    global _warm_task
    if _warm_task is None or _warm_task.done():
        _warm_task = asyncio.create_task(
            asyncio.to_thread(_load_batch_cached),
            name="tools-index-hero-warmup",
        )


@app.on_event("shutdown")
async def _stop_tools_index_hero_warmup() -> None:
    global _warm_task
    if _warm_task is None:
        return
    if not _warm_task.done():
        _warm_task.cancel()
        await asyncio.gather(_warm_task, return_exceptions=True)
    _warm_task = None


@app.get(INDEX_COMPACT_QUOTES_PATH)
async def a_share_index_compact_quotes() -> dict[str, Any]:
    try:
        return await asyncio.to_thread(_load_quotes_cached)
    except Exception as exc:
        LOGGER.exception("tools index quotes failed")
        raise HTTPException(
            status_code=502,
            detail=f"三大指数报价暂不可用：{type(exc).__name__}: {exc}",
        ) from exc


@app.get(INDEX_COMPACT_TREND_PATH)
async def a_share_index_compact_trend(
    query: str = Query(..., min_length=1, max_length=32),
) -> dict[str, Any]:
    security = _resolve_index(query)
    try:
        return await asyncio.to_thread(_load_trend_cached, security)
    except HTTPException:
        raise
    except Exception as exc:
        LOGGER.exception("tools index trend failed for %s", security["code"])
        raise HTTPException(
            status_code=502,
            detail=f"指数分时暂不可用：{type(exc).__name__}: {exc}",
        ) from exc


@app.get(INDEX_COMPACT_BATCH_PATH)
async def a_share_index_compact_batch() -> dict[str, Any]:
    try:
        return await asyncio.to_thread(_load_batch_cached)
    except Exception as exc:
        LOGGER.exception("tools index hero batch failed")
        raise HTTPException(
            status_code=502,
            detail=f"三大指数首批行情暂不可用：{type(exc).__name__}: {exc}",
        ) from exc


@app.get(INDEX_COMPACT_PATH)
async def a_share_index_compact(
    query: str = Query(..., min_length=1, max_length=32),
) -> dict[str, Any]:
    security = _resolve_index(query)
    try:
        payload = await asyncio.to_thread(_load_batch_cached)
        for item in list(payload.get("items") or []):
            if str(item.get("code") or "") == security["code"]:
                single = deepcopy(item)
                single["batchCacheHit"] = bool(payload.get("cacheHit"))
                single["batchCacheAgeMs"] = int(payload.get("cacheAgeMs") or 0)
                return single
        raise ValueError(f"batch response missing {security['code']}")
    except HTTPException:
        raise
    except Exception as exc:
        LOGGER.exception("tools index hero single failed for %s", security["code"])
        raise HTTPException(
            status_code=502,
            detail=f"指数轻量行情暂不可用：{type(exc).__name__}: {exc}",
        ) from exc
