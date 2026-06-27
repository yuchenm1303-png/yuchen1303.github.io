from __future__ import annotations

from copy import deepcopy
from datetime import datetime, timezone
from threading import Lock
from time import monotonic
from typing import Any

import httpx
from fastapi import Query

import market_home_server as market_home


app = market_home.app
legacy = market_home.legacy

STOCK_KLINE_PATH = "/api/stock/a-share/kline"
CRAWL_KLINE_PATH = "/api/stock/crawl/a-share/kline"
KLINE_CACHE_VERSION = "v2-extended-history"
KLINE_FRESH_SECONDS = 10 * 60.0
KLINE_STALE_SECONDS = 24 * 60 * 60.0
KLINE_MAX_LIMIT = 800
KLINE_DEFAULT_LIMITS = {
    "daily": 600,
    "weekly": 320,
    "monthly": 180,
}
INDEX_SECURITIES = {
    item["code"]: deepcopy(item) for item in market_home._INDEX_SECURITIES
}

_LOCKS_GUARD = Lock()
_KLINE_LOCKS: dict[str, Lock] = {}


def _lock_for(key: str) -> Lock:
    with _LOCKS_GUARD:
        return _KLINE_LOCKS.setdefault(key, Lock())


def _canonical_period(period: str) -> str:
    canonical, _ = legacy._normalize_period(period)
    return canonical


def _effective_limit(period: str, requested: int | None) -> int:
    canonical = _canonical_period(period)
    default = KLINE_DEFAULT_LIMITS[canonical]
    if requested is None:
        return default
    return min(max(int(requested), default), KLINE_MAX_LIMIT)


def _resolve_instrument(
    query: str,
    instrument: str,
) -> dict[str, str]:
    kind = instrument.strip().lower()
    keyword = query.strip()
    if kind == "index":
        digits = "".join(char for char in keyword if char.isdigit())
        security = INDEX_SECURITIES.get(digits)
        if security is None:
            raise ValueError(f"unsupported index: {query}")
        return {**security, "instrument": "index"}
    if kind == "sector":
        code = keyword.upper()
        if not (code.startswith("BK") and code[2:].isdigit()):
            raise ValueError(f"unsupported sector code: {query}")
        return {
            "code": code,
            "name": code,
            "secid": f"90.{code}",
            "instrument": "sector",
        }
    security = legacy._resolve_security(market_home._get_shared_client(), keyword)
    return {**security, "instrument": "stock"}


def _load_eastmoney_non_stock_kline(
    security: dict[str, str],
    period: str,
    limit: int,
    warnings: list[str],
) -> list[dict[str, Any]]:
    canonical, klt = legacy._normalize_period(period)
    params = {
        "secid": security["secid"],
        "klt": klt,
        "fqt": "0",
        "lmt": str(limit),
        "beg": "0",
        "end": "20500101",
        "iscca": "1",
        "fields1": "f1,f2,f3,f4,f5,f6",
        "fields2": "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61",
    }
    last_error = ""
    for url in legacy.EASTMONEY_KLINE_URLS:
        try:
            raw = legacy._eastmoney_get(market_home._get_shared_client(), url, params)
            rows = legacy._parse_eastmoney_kline_rows(raw, limit)
            if rows:
                warnings.append(
                    f"{canonical}_kline: eastmoney_non_stock source={url.split('/')[2]}"
                )
                return rows
            last_error = f"{url}: empty klines"
        except (httpx.HTTPError, ValueError) as exc:
            last_error = f"{type(exc).__name__}: {exc}"
    raise ValueError(last_error or f"{canonical} kline unavailable")


def _build_kline_payload(
    query: str,
    period: str,
    limit: int,
    instrument: str,
) -> dict[str, Any]:
    started_at = monotonic()
    canonical = _canonical_period(period)
    security = _resolve_instrument(query, instrument)
    warnings: list[str] = []
    if security["instrument"] == "stock":
        points = legacy._load_kline(
            market_home._get_shared_client(),
            security,
            warnings,
            period=canonical,
            limit=limit,
        )
    else:
        points = _load_eastmoney_non_stock_kline(
            security,
            canonical,
            limit,
            warnings,
        )
    if len(points) < 2:
        raise ValueError(f"{canonical} kline returned insufficient real points")
    return {
        "provider": "eastmoney_extended_kline",
        "status": "ok",
        "instrument": security["instrument"],
        "query": query,
        "code": security["code"],
        "name": security["name"],
        "secid": security["secid"],
        "period": canonical,
        "requestedLimit": limit,
        "count": len(points),
        "kLinePoints": points,
        "dataSourceLabel": (
            f"真实历史K线 · {security['instrument']} · {security['code']} · "
            f"{len(points)}根"
        ),
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "cacheHit": False,
        "cacheAgeMs": 0,
        "totalLatencyMs": int((monotonic() - started_at) * 1000),
        "warnings": warnings,
    }


def _with_cache_label(
    payload: dict[str, Any],
    age_seconds: float,
    stale: bool,
) -> dict[str, Any]:
    cached = deepcopy(payload)
    cached["cacheHit"] = True
    cached["cacheAgeMs"] = max(int(age_seconds * 1000), 0)
    if stale:
        cached["status"] = "stale"
    cached["warnings"] = list(cached.get("warnings") or []) + [
        f"extended_kline_cache: {'stale' if stale else 'hit'} age={age_seconds:.2f}s"
    ]
    return cached


def _load_kline_cached(
    query: str,
    period: str,
    limit: int,
    instrument: str,
) -> dict[str, Any]:
    canonical = _canonical_period(period)
    key = legacy._cache_key(
        "extended-kline",
        f"{instrument}:{query}",
        f"{KLINE_CACHE_VERSION}:{canonical}:{limit}",
    )
    fresh = legacy._cache_get_seconds(key, KLINE_FRESH_SECONDS)
    if fresh is not None:
        payload, age = fresh
        return _with_cache_label(payload, age, stale=False)

    with _lock_for(key):
        fresh = legacy._cache_get_seconds(key, KLINE_FRESH_SECONDS)
        if fresh is not None:
            payload, age = fresh
            return _with_cache_label(payload, age, stale=False)
        try:
            payload = _build_kline_payload(query, canonical, limit, instrument)
        except Exception:
            stale = legacy._cache_get_seconds(key, KLINE_STALE_SECONDS)
            if stale is not None:
                payload, age = stale
                return _with_cache_label(payload, age, stale=True)
            raise
        legacy._cache_put(key, payload)
        return payload


def _remove_get_routes(paths: set[str]) -> None:
    app.router.routes[:] = [
        route
        for route in app.router.routes
        if not (
            getattr(route, "path", None) in paths
            and "GET" in (getattr(route, "methods", None) or set())
        )
    ]


_remove_get_routes({STOCK_KLINE_PATH, CRAWL_KLINE_PATH})


@app.get(STOCK_KLINE_PATH)
@app.get(CRAWL_KLINE_PATH)
def a_share_extended_kline(
    query: str = Query(..., min_length=1, max_length=32),
    period: str = Query("daily"),
    limit: int | None = Query(None, ge=20, le=KLINE_MAX_LIMIT),
    instrument: str = Query("stock", pattern="^(stock|index|sector)$"),
) -> dict[str, Any]:
    canonical = _canonical_period(period)
    effective_limit = _effective_limit(canonical, limit)
    return _load_kline_cached(query, canonical, effective_limit, instrument)
