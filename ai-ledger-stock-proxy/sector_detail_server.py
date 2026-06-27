from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from copy import deepcopy
from datetime import datetime, timezone
from threading import Lock
from time import monotonic
from typing import Any, Callable

from fastapi import HTTPException, Query

import market_home_server as market_home
from realtime_runtime import CN_TZ, QUOTE_URLS, TRENDS_URLS


app = market_home.app
legacy = market_home.legacy

SECTOR_DETAIL_PATH = "/api/stock/a-share/sector/detail"
SECTOR_CONSTITUENTS_PATH = "/api/stock/a-share/sector/constituents"
LEGACY_CONSTITUENTS_PATH = "/api/stock/a-share/sectors/{sectorCode}/constituents"
SECTOR_CACHE_VERSION = "v1-real-sector-detail"
SECTOR_LIVE_FRESH_SECONDS = 5.0
SECTOR_LIVE_STALE_SECONDS = 6 * 60 * 60.0
SECTOR_CONSTITUENTS_FRESH_SECONDS = 60.0
SECTOR_CONSTITUENTS_STALE_SECONDS = 6 * 60 * 60.0
SECTOR_CATALOG_FRESH_SECONDS = 6 * 60 * 60.0
SECTOR_CATALOG_STALE_SECONDS = 24 * 60 * 60.0
SECTOR_MAX_CONSTITUENTS = 200

_LOCKS_GUARD = Lock()
_CACHE_LOCKS: dict[str, Lock] = {}


def _lock_for(key: str) -> Lock:
    with _LOCKS_GUARD:
        return _CACHE_LOCKS.setdefault(key, Lock())


def _is_sector_code(value: str) -> bool:
    code = value.strip().upper()
    return code.startswith("BK") and code[2:].isdigit()


def _home_sector_items() -> list[dict[str, Any]]:
    try:
        home = market_home._load_market_home_cached()
        return list(((home.get("sectorHotRanking") or {}).get("items")) or [])
    except Exception:
        return []


def _metadata_from_item(item: dict[str, Any], type_name: str | None = None) -> dict[str, Any]:
    return {
        "code": str(item.get("sectorCode") or item.get("f12") or "").strip().upper(),
        "name": str(item.get("sectorName") or item.get("f14") or "").strip(),
        "type": str(item.get("type") or type_name or "industry"),
        "changePercent": str(item.get("changePercent") or legacy._format_percent(item.get("f3"))),
        "upCount": item.get("upCount", item.get("f104")),
        "downCount": item.get("downCount", item.get("f105")),
        "flatCount": item.get("flatCount", item.get("f106")),
        "leaderName": str(item.get("leaderName") or item.get("f128") or "").strip(),
        "leaderChangePercent": str(
            item.get("leaderChangePercent") or legacy._format_percent(item.get("f136"))
        ),
        "amount": str(item.get("amount") or legacy._format_cn_money(item.get("f6"))),
        "turnoverRate": str(
            item.get("turnoverRate")
            or legacy._format_percent(item.get("f8"), signed=False)
        ),
        "mainInflow": str(item.get("mainInflow") or legacy._format_cn_money(item.get("f62"))),
    }


def _load_sector_catalog() -> dict[str, Any]:
    warnings: list[str] = []
    items: list[dict[str, Any]] = []
    for type_name, fs in legacy.SECTOR_FS.items():
        try:
            rows = legacy._clist_items(
                market_home._get_shared_client(),
                fs,
                "f12",
                500,
                warnings,
                f"sector_catalog_{type_name}",
                strict=True,
            )
        except Exception as exc:
            warnings.append(
                f"sector_catalog_{type_name}: {type(exc).__name__}: {exc}"
            )
            continue
        items.extend(_metadata_from_item(row, type_name) for row in rows)
    by_code = {item["code"]: item for item in items if item["code"]}
    return {
        "status": "ok" if by_code else "empty",
        "items": list(by_code.values()),
        "warnings": warnings,
        "updatedAt": datetime.now(timezone.utc).isoformat(),
    }


def _cached_payload(
    key: str,
    fresh_seconds: float,
    stale_seconds: float,
    loader: Callable[[], dict[str, Any]],
) -> dict[str, Any]:
    fresh = legacy._cache_get_seconds(key, fresh_seconds)
    if fresh is not None:
        payload, age = fresh
        cached = deepcopy(payload)
        cached["cacheHit"] = True
        cached["cacheAgeMs"] = max(int(age * 1000), 0)
        return cached
    with _lock_for(key):
        fresh = legacy._cache_get_seconds(key, fresh_seconds)
        if fresh is not None:
            payload, age = fresh
            cached = deepcopy(payload)
            cached["cacheHit"] = True
            cached["cacheAgeMs"] = max(int(age * 1000), 0)
            return cached
        try:
            payload = loader()
        except Exception:
            stale = legacy._cache_get_seconds(key, stale_seconds)
            if stale is not None:
                payload, age = stale
                cached = deepcopy(payload)
                cached["status"] = "stale"
                cached["cacheHit"] = True
                cached["cacheAgeMs"] = max(int(age * 1000), 0)
                cached["warnings"] = list(cached.get("warnings") or []) + [
                    f"sector_cache: stale age={age:.2f}s"
                ]
                return cached
            raise
        payload.setdefault("cacheHit", False)
        payload.setdefault("cacheAgeMs", 0)
        legacy._cache_put(key, payload)
        return payload


def _sector_catalog_cached() -> dict[str, Any]:
    key = legacy._cache_key("sector", "catalog", SECTOR_CACHE_VERSION)
    return _cached_payload(
        key,
        SECTOR_CATALOG_FRESH_SECONDS,
        SECTOR_CATALOG_STALE_SECONDS,
        _load_sector_catalog,
    )


def _resolve_sector(query: str) -> dict[str, Any]:
    keyword = query.strip()
    if not keyword:
        raise HTTPException(status_code=400, detail="板块代码或名称不能为空")
    normalized = keyword.upper()
    home_items = _home_sector_items()
    if _is_sector_code(normalized):
        matched = next(
            (
                _metadata_from_item(item)
                for item in home_items
                if str(item.get("sectorCode") or "").upper() == normalized
            ),
            None,
        )
        if matched is not None:
            return matched
        catalog = _sector_catalog_cached()
        catalog_match = next(
            (
                deepcopy(item)
                for item in catalog.get("items") or []
                if str(item.get("code") or "").upper() == normalized
            ),
            None,
        )
        return catalog_match or {
            "code": normalized,
            "name": normalized,
            "type": "industry",
            "changePercent": "--",
            "upCount": None,
            "downCount": None,
            "flatCount": None,
            "leaderName": "",
            "leaderChangePercent": "--",
            "amount": "--",
            "turnoverRate": "--",
            "mainInflow": "--",
        }

    compact = keyword.replace(" ", "").lower()
    for item in home_items:
        metadata = _metadata_from_item(item)
        if metadata["name"].replace(" ", "").lower() == compact:
            return metadata
    catalog = _sector_catalog_cached()
    for item in catalog.get("items") or []:
        if str(item.get("name") or "").replace(" ", "").lower() == compact:
            return deepcopy(item)
    raise HTTPException(status_code=404, detail=f"未找到板块：{query}")


def _load_sector_quote(metadata: dict[str, Any], warnings: list[str]) -> dict[str, Any]:
    code = metadata["code"]
    raw = legacy._eastmoney_get_first(
        market_home._get_shared_client(),
        QUOTE_URLS,
        {
            "secid": f"90.{code}",
            "fields": "f43,f44,f45,f46,f47,f48,f57,f58,f60,f169,f170",
        },
        "sector_quote",
        warnings,
    )
    data = raw.get("data") or {}
    price = legacy._scaled(data.get("f43"), -1.0)
    if price <= 0:
        raise ValueError("sector quote returned invalid price")
    name = legacy._safe_str(data.get("f58"), metadata.get("name") or code)
    return {
        "code": code,
        "name": name,
        "market": "行业板块" if metadata.get("type") == "industry" else "市场板块",
        "price": legacy._format_price(price),
        "changeAmount": legacy._format_signed(legacy._scaled(data.get("f169"))),
        "changePercent": legacy._format_percent(legacy._scaled(data.get("f170"))),
        "open": legacy._format_price(legacy._scaled(data.get("f46"), -1.0)),
        "high": legacy._format_price(legacy._scaled(data.get("f44"), -1.0)),
        "low": legacy._format_price(legacy._scaled(data.get("f45"), -1.0)),
        "previousClose": legacy._scaled(data.get("f60")),
        "amount": legacy._format_cn_money(data.get("f48")),
        "volume": legacy._format_lots(data.get("f47")),
    }


def _parse_sector_minutes(raw: dict[str, Any]) -> list[dict[str, Any]]:
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
                        f"{date_text} {time_text[:5]}", "%Y-%m-%d %H:%M"
                    ).replace(tzinfo=CN_TZ).timestamp()
                    * 1000
                ),
                "open": legacy._safe_float(parts[1], price),
                "price": price,
                "high": legacy._safe_float(parts[3], price),
                "low": legacy._safe_float(parts[4], price),
                "volume": volume,
                "amount": max(legacy._safe_float(parts[6]), 0.0),
                "average": legacy._safe_float(parts[7], price),
                "volumeRatio": 0.0,
            }
        )
    if not rows:
        raise ValueError("sector minute trends are empty")
    max_volume = max(volumes or [1.0])
    for row in rows:
        row["volumeRatio"] = min(max(row["volume"] / max_volume, 0.02), 1.0)
    rows.sort(key=lambda row: int(row["timestamp"]))
    return rows


def _load_sector_minutes(metadata: dict[str, Any], warnings: list[str]) -> list[dict[str, Any]]:
    raw = legacy._eastmoney_get_first(
        market_home._get_shared_client(),
        TRENDS_URLS,
        {
            "secid": f"90.{metadata['code']}",
            "fields1": "f1,f2,f3,f4,f5,f6,f7,f8",
            "fields2": "f51,f52,f53,f54,f55,f56,f57,f58",
            "iscr": "0",
            "ndays": "1",
        },
        "sector_minute",
        warnings,
    )
    return _parse_sector_minutes(raw)


def _related_sectors(code: str) -> list[dict[str, Any]]:
    return [
        _metadata_from_item(item)
        for item in _home_sector_items()
        if str(item.get("sectorCode") or "").upper() != code
    ][:8]


def _build_sector_detail(metadata: dict[str, Any]) -> dict[str, Any]:
    started_at = monotonic()
    warnings: list[str] = []
    with ThreadPoolExecutor(max_workers=2, thread_name_prefix="sector-detail") as executor:
        quote_future = executor.submit(_load_sector_quote, metadata, warnings)
        minute_future = executor.submit(_load_sector_minutes, metadata, warnings)
        quote = quote_future.result()
        try:
            minute_points = minute_future.result()
        except Exception as exc:
            warnings.append(f"sector_minute: {type(exc).__name__}: {exc}")
            minute_points = []
    resolved = {**metadata, "name": quote["name"]}
    return {
        "provider": "eastmoney_sector_detail",
        "status": "ok" if minute_points else "partial",
        "code": resolved["code"],
        "name": resolved["name"],
        "type": resolved.get("type") or "industry",
        "secid": f"90.{resolved['code']}",
        "quote": quote,
        "minutePoints": minute_points,
        "breadth": {
            "upCount": resolved.get("upCount"),
            "downCount": resolved.get("downCount"),
            "flatCount": resolved.get("flatCount"),
            "redRate": (
                round(
                    legacy._safe_float(resolved.get("upCount"))
                    / max(
                        legacy._safe_float(resolved.get("upCount"))
                        + legacy._safe_float(resolved.get("downCount")),
                        1.0,
                    )
                    * 100,
                    2,
                )
                if resolved.get("upCount") is not None
                else None
            ),
            "leaderName": resolved.get("leaderName") or "",
            "leaderChangePercent": resolved.get("leaderChangePercent") or "--",
            "amount": resolved.get("amount") or quote.get("amount") or "--",
            "turnoverRate": resolved.get("turnoverRate") or "--",
            "mainInflow": resolved.get("mainInflow") or "--",
        },
        "relatedSectors": _related_sectors(resolved["code"]),
        "dataSourceLabel": f"真实板块行情 · {resolved['name']} · 东方财富公开 JSON",
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "cacheHit": False,
        "cacheAgeMs": 0,
        "totalLatencyMs": int((monotonic() - started_at) * 1000),
        "warnings": warnings,
    }


def _load_sector_detail_cached(metadata: dict[str, Any]) -> dict[str, Any]:
    key = legacy._cache_key(
        "sector-detail",
        metadata["code"],
        SECTOR_CACHE_VERSION,
    )
    return _cached_payload(
        key,
        SECTOR_LIVE_FRESH_SECONDS,
        SECTOR_LIVE_STALE_SECONDS,
        lambda: _build_sector_detail(metadata),
    )


def _constituent_item(item: dict[str, Any], rank: int) -> dict[str, Any]:
    change = legacy._format_percent(item.get("f3"))
    return {
        "rank": rank,
        "code": legacy._safe_str(item.get("f12"), ""),
        "name": legacy._safe_str(item.get("f14"), ""),
        "price": legacy._format_price(item.get("f2")),
        "changePercent": change,
        "isRising": not change.startswith("-"),
        "amount": legacy._format_cn_money(item.get("f6")),
        "turnoverRate": legacy._format_percent(item.get("f8"), signed=False),
        "volumeRatio": legacy._format_price(item.get("f10")),
        "mainInflow": legacy._format_cn_money(item.get("f62")),
        "totalMarketValue": legacy._format_cn_money(item.get("f20")),
        "floatMarketValue": legacy._format_cn_money(item.get("f21")),
    }


def _build_constituents(metadata: dict[str, Any]) -> dict[str, Any]:
    warnings: list[str] = []
    rows = legacy._clist_items(
        market_home._get_shared_client(),
        f"b:{metadata['code']}",
        "f3",
        SECTOR_MAX_CONSTITUENTS,
        warnings,
        f"sector_constituents_{metadata['code']}",
        strict=True,
    )
    items = [_constituent_item(item, index + 1) for index, item in enumerate(rows)]
    return {
        "provider": "eastmoney_sector_constituents",
        "status": "ok" if items else "empty",
        "code": metadata["code"],
        "name": metadata["name"],
        "total": len(items),
        "items": items,
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "cacheHit": False,
        "cacheAgeMs": 0,
        "warnings": warnings,
    }


def _load_constituents_cached(metadata: dict[str, Any]) -> dict[str, Any]:
    key = legacy._cache_key(
        "sector-constituents",
        metadata["code"],
        SECTOR_CACHE_VERSION,
    )
    return _cached_payload(
        key,
        SECTOR_CONSTITUENTS_FRESH_SECONDS,
        SECTOR_CONSTITUENTS_STALE_SECONDS,
        lambda: _build_constituents(metadata),
    )


def _paged_constituents(
    metadata: dict[str, Any],
    page: int,
    page_size: int,
) -> dict[str, Any]:
    payload = _load_constituents_cached(metadata)
    items = list(payload.get("items") or [])
    start = (page - 1) * page_size
    result = deepcopy(payload)
    result["page"] = page
    result["pageSize"] = page_size
    result["total"] = len(items)
    result["hasMore"] = start + page_size < len(items)
    result["items"] = items[start : start + page_size]
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


_remove_get_routes({LEGACY_CONSTITUENTS_PATH})


@app.get(SECTOR_DETAIL_PATH)
def a_share_sector_detail(
    query: str = Query(..., min_length=1, max_length=64),
) -> dict[str, Any]:
    metadata = _resolve_sector(query)
    try:
        return _load_sector_detail_cached(metadata)
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(
            status_code=502,
            detail=f"板块详情暂不可用：{type(exc).__name__}: {exc}",
        ) from exc


@app.get(SECTOR_CONSTITUENTS_PATH)
def a_share_sector_constituents(
    query: str = Query(..., min_length=1, max_length=64),
    page: int = Query(1, ge=1),
    pageSize: int = Query(20, ge=1, le=50),
) -> dict[str, Any]:
    metadata = _resolve_sector(query)
    try:
        return _paged_constituents(metadata, page, pageSize)
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(
            status_code=502,
            detail=f"板块成分股暂不可用：{type(exc).__name__}: {exc}",
        ) from exc


@app.get(LEGACY_CONSTITUENTS_PATH)
def a_share_sector_constituents_legacy(
    sectorCode: str,
    page: int = Query(1, ge=1),
    pageSize: int = Query(20, ge=1, le=50),
) -> dict[str, Any]:
    metadata = _resolve_sector(sectorCode)
    return _paged_constituents(metadata, page, pageSize)
