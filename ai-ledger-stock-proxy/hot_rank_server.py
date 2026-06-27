from __future__ import annotations

from copy import deepcopy
from datetime import datetime, timezone
from threading import Lock
from time import monotonic
from typing import Any

import httpx
from fastapi import HTTPException, Query

import market_home_server as market_home


app = market_home.app
legacy = market_home.legacy

HOT_RANKING_PATH = "/api/stock/a-share/hot/ranking"
LEGACY_POPULARITY_PATH = "/api/stock/a-share/rankings/popularity"
HOT_RANK_CACHE_VERSION = "v1-eastmoney-stockrank"
HOT_RANK_FRESH_SECONDS = 120.0
HOT_RANK_STALE_SECONDS = 6 * 60 * 60.0
HOT_RANK_MAX_ITEMS = 100
HOT_RANK_PAGE_URL = "https://guba.eastmoney.com/rank/"
HOT_RANK_ENDPOINTS = {
    "popularity": "https://emappdata.eastmoney.com/stockrank/getAllCurrentList",
    "surge": "https://emappdata.eastmoney.com/stockrank/getAllHisRcList",
}
HOT_RANK_LABELS = {
    "popularity": "人气榜",
    "surge": "飙升榜",
}
HOT_RANK_PAYLOAD = {
    "appId": "appId01",
    "globalId": "786e4c21-70dc-435a-93bb-38",
    "marketType": "",
    "pageNo": 1,
    "pageSize": HOT_RANK_MAX_ITEMS,
}

_LOCKS_GUARD = Lock()
_HOT_LOCKS: dict[str, Lock] = {}


def _lock_for(key: str) -> Lock:
    with _LOCKS_GUARD:
        return _HOT_LOCKS.setdefault(key, Lock())


def _int_or_none(value: Any) -> int | None:
    try:
        if value is None:
            return None
        text = str(value).strip()
        if text in {"", "-", "--", "None", "null"}:
            return None
        return int(float(text))
    except (TypeError, ValueError):
        return None


def _normalize_rank_type(value: str) -> str:
    normalized = value.strip().lower()
    if normalized not in HOT_RANK_ENDPOINTS:
        raise HTTPException(
            status_code=400,
            detail="type must be popularity or surge",
        )
    return normalized


def _security_from_source_code(source_code: Any) -> dict[str, str] | None:
    raw = str(source_code or "").strip().upper()
    if len(raw) < 8:
        return None
    prefix = raw[:2]
    code = "".join(char for char in raw[2:] if char.isdigit())
    if prefix not in {"SH", "SZ", "BJ"} or len(code) != 6:
        return None
    market_id = "1" if prefix == "SH" else "0"
    market_name = {"SH": "沪市", "SZ": "深市", "BJ": "北交所"}[prefix]
    return {
        "sourceCode": raw,
        "code": code,
        "secid": f"{market_id}.{code}",
        "market": market_name,
    }


def _extract_rank_rows(raw: dict[str, Any]) -> list[dict[str, Any]]:
    data: Any = raw.get("data")
    if isinstance(data, list):
        return [item for item in data if isinstance(item, dict)]
    if isinstance(data, dict):
        for key in ("list", "items", "data", "result"):
            rows = data.get(key)
            if isinstance(rows, list):
                return [item for item in rows if isinstance(item, dict)]
    result = raw.get("result")
    if isinstance(result, dict):
        for key in ("list", "items", "data"):
            rows = result.get(key)
            if isinstance(rows, list):
                return [item for item in rows if isinstance(item, dict)]
    return []


def _load_upstream_rank_rows(rank_type: str, warnings: list[str]) -> list[dict[str, Any]]:
    endpoint = HOT_RANK_ENDPOINTS[rank_type]
    client = market_home._get_shared_client()
    response = client.post(
        endpoint,
        json=HOT_RANK_PAYLOAD,
        headers={
            "Accept": "application/json, text/plain, */*",
            "Content-Type": "application/json;charset=UTF-8",
            "Origin": "https://guba.eastmoney.com",
            "Referer": HOT_RANK_PAGE_URL,
        },
        timeout=5.5,
    )
    response.raise_for_status()
    raw = response.json()
    if not isinstance(raw, dict):
        raise ValueError("hot rank upstream response is not an object")
    rows = _extract_rank_rows(raw)
    if not rows:
        raise ValueError("hot rank upstream returned no real rows")
    warnings.append(
        f"hot_rank_{rank_type}: upstream_rows={len(rows)} endpoint={endpoint.split('/')[-1]}"
    )
    return rows


def _quote_map(securities: list[dict[str, str]], warnings: list[str]) -> dict[str, dict[str, Any]]:
    if not securities:
        return {}
    secids = ",".join(item["secid"] for item in securities)
    raw = legacy._eastmoney_get_first(
        market_home._get_shared_client(),
        market_home.EASTMONEY_ULIST_URLS,
        {
            "ut": "f057cbcbce2a86e2866ab8877db1d059",
            "fltt": "2",
            "invt": "2",
            "fields": "f2,f3,f4,f6,f12,f14,f100",
            "secids": secids,
        },
        "hot_rank_quotes",
        warnings,
    )
    rows = list((raw.get("data") or {}).get("diff") or [])
    result: dict[str, dict[str, Any]] = {}
    for row in rows:
        if not isinstance(row, dict):
            continue
        code = str(row.get("f12") or "").strip()
        if code:
            result[code] = row
    warnings.append(f"hot_rank_quotes: matched={len(result)}/{len(securities)}")
    return result


def _rank_change_state(value: int | None) -> str:
    if value is None:
        return "unknown"
    if value > 0:
        return "up"
    if value < 0:
        return "down"
    return "flat"


def _build_hot_rank_payload(rank_type: str) -> dict[str, Any]:
    started_at = monotonic()
    warnings: list[str] = []
    raw_rows = _load_upstream_rank_rows(rank_type, warnings)

    normalized_rows: list[tuple[dict[str, Any], dict[str, str]]] = []
    seen: set[str] = set()
    for row in raw_rows:
        security = _security_from_source_code(row.get("sc"))
        if security is None or security["sourceCode"] in seen:
            continue
        seen.add(security["sourceCode"])
        normalized_rows.append((row, security))
        if len(normalized_rows) >= HOT_RANK_MAX_ITEMS:
            break
    if not normalized_rows:
        raise ValueError("hot rank returned no supported A-share security codes")

    quotes = _quote_map([security for _, security in normalized_rows], warnings)
    items: list[dict[str, Any]] = []
    quote_matches = 0
    for list_rank, (row, security) in enumerate(normalized_rows, start=1):
        quote = quotes.get(security["code"]) or {}
        if quote:
            quote_matches += 1
        current_rank = _int_or_none(row.get("rk")) or list_rank
        rank_change = _int_or_none(row.get("hrc"))
        change_percent = legacy._format_percent(quote.get("f3")) if quote else "--"
        items.append(
            {
                "rank": list_rank,
                "currentRank": current_rank,
                "rankChange": rank_change,
                "rankChangeState": _rank_change_state(rank_change),
                "sourceSecurityCode": security["sourceCode"],
                "code": security["code"],
                "market": security["market"],
                "name": legacy._safe_str(quote.get("f14"), security["code"]),
                "price": legacy._format_price(quote.get("f2")) if quote else "--",
                "changeAmount": legacy._format_signed(quote.get("f4")) if quote else "--",
                "changePercent": change_percent,
                "amount": legacy._format_cn_money(quote.get("f6")) if quote else "--",
                "industry": legacy._safe_str(quote.get("f100"), "") if quote else "",
                "isRising": not str(change_percent).startswith("-"),
            }
        )

    rising_count = sum(1 for item in items if str(item["changePercent"]).startswith("+"))
    falling_count = sum(1 for item in items if str(item["changePercent"]).startswith("-"))
    status = "ok" if quote_matches == len(items) else "partial"
    updated_at = datetime.now(timezone.utc).isoformat()
    return {
        "provider": "eastmoney_stockrank",
        "status": status,
        "type": rank_type,
        "title": HOT_RANK_LABELS[rank_type],
        "source": "eastmoney_guba_stockrank",
        "sourceUrlType": (
            "emappdata stockrank POST + push2 batch quote"
        ),
        "sourcePageUrl": HOT_RANK_PAGE_URL,
        "upstreamUpdateIntervalSeconds": 600,
        "dataSourceLabel": (
            f"东方财富个股{HOT_RANK_LABELS[rank_type]} · 官方说明约10分钟更新"
        ),
        "total": len(items),
        "summary": {
            "risingCount": rising_count,
            "fallingCount": falling_count,
            "flatOrUnavailableCount": max(len(items) - rising_count - falling_count, 0),
            "quoteMatchCount": quote_matches,
        },
        "items": items,
        "updatedAt": updated_at,
        "cacheHit": False,
        "cacheAgeMs": 0,
        "totalLatencyMs": int((monotonic() - started_at) * 1000),
        "warnings": warnings,
    }


def _with_cache_label(
    payload: dict[str, Any],
    age_seconds: float,
    *,
    stale: bool,
) -> dict[str, Any]:
    cached = deepcopy(payload)
    cached["cacheHit"] = True
    cached["cacheAgeMs"] = max(int(age_seconds * 1000), 0)
    if stale and str(cached.get("status", "")).lower() in {"ok", "partial"}:
        cached["status"] = "stale"
    cached["warnings"] = list(cached.get("warnings") or []) + [
        f"hot_rank_cache: {'stale' if stale else 'hit'} age={age_seconds:.2f}s"
    ]
    return cached


def _load_hot_rank_cached(rank_type: str) -> dict[str, Any]:
    key = legacy._cache_key(
        "hot-rank",
        rank_type,
        HOT_RANK_CACHE_VERSION,
    )
    fresh = legacy._cache_get_seconds(key, HOT_RANK_FRESH_SECONDS)
    if fresh is not None:
        payload, age = fresh
        return _with_cache_label(payload, age, stale=False)

    with _lock_for(key):
        fresh = legacy._cache_get_seconds(key, HOT_RANK_FRESH_SECONDS)
        if fresh is not None:
            payload, age = fresh
            return _with_cache_label(payload, age, stale=False)
        try:
            payload = _build_hot_rank_payload(rank_type)
        except Exception:
            stale = legacy._cache_get_seconds(key, HOT_RANK_STALE_SECONDS)
            if stale is not None:
                payload, age = stale
                return _with_cache_label(payload, age, stale=True)
            raise
        legacy._cache_put(key, payload)
        return payload


def _limited_payload(payload: dict[str, Any], limit: int) -> dict[str, Any]:
    result = deepcopy(payload)
    items = list(result.get("items") or [])
    result["fullCount"] = len(items)
    result["items"] = items[:limit]
    result["count"] = len(result["items"])
    return result


def _remove_get_routes(paths: set[str]) -> None:
    app.router.routes[:] = [
        route
        for route in app.router.routes
        if not (
            getattr(route, "path", None) in paths
            and "GET" in (getattr(route, "methods", None) or set())
        )
    ]


_remove_get_routes({LEGACY_POPULARITY_PATH})


@app.get(HOT_RANKING_PATH)
def a_share_hot_ranking(
    type: str = Query("popularity", pattern="^(popularity|surge)$"),
    limit: int = Query(50, ge=1, le=HOT_RANK_MAX_ITEMS),
) -> dict[str, Any]:
    rank_type = _normalize_rank_type(type)
    try:
        return _limited_payload(_load_hot_rank_cached(rank_type), limit)
    except HTTPException:
        raise
    except (httpx.HTTPError, ValueError, TypeError) as exc:
        raise HTTPException(
            status_code=502,
            detail=f"实时热点榜暂不可用：{type(exc).__name__}: {exc}",
        ) from exc


@app.get(LEGACY_POPULARITY_PATH)
def a_share_popularity_ranking_real(
    limit: int = Query(50, ge=1, le=HOT_RANK_MAX_ITEMS),
) -> dict[str, Any]:
    try:
        return _limited_payload(_load_hot_rank_cached("popularity"), limit)
    except (httpx.HTTPError, ValueError, TypeError) as exc:
        raise HTTPException(
            status_code=502,
            detail=f"个股人气榜暂不可用：{type(exc).__name__}: {exc}",
        ) from exc
