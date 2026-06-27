from __future__ import annotations

import asyncio
import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
from copy import deepcopy
from datetime import datetime, timezone
from threading import Lock
from time import monotonic
from typing import Any, Callable

import httpx
from fastapi import HTTPException, Query

import market_home_server as market_home
from realtime_runtime import CN_TZ, QUOTE_URLS, TENCENT_FIVE_DAY_URL, TRENDS_URLS


app = market_home.app
legacy = market_home.legacy
LOGGER = logging.getLogger("ai-ledger-stock-proxy.index-detail")

INDEX_DETAIL_PATH = "/api/stock/a-share/index/detail"
INDEX_DETAIL_CACHE_VERSION = "v2-true-five-day"
INDEX_DETAIL_FRESH_SECONDS = 1.0
INDEX_DETAIL_STALE_SECONDS = 6 * 60 * 60.0
INDEX_DETAIL_WORKERS = 5

_INDEX_SECURITIES = tuple(deepcopy(market_home._INDEX_SECURITIES))
_INDEX_BY_CODE = {item["code"]: item for item in _INDEX_SECURITIES}
_INDEX_ALIAS_TO_CODE = {
    "上证": "000001",
    "上证指数": "000001",
    "沪指": "000001",
    "深证": "399001",
    "深证成指": "399001",
    "深成指": "399001",
    "创业板": "399006",
    "创业板指": "399006",
    "沪深300": "000300",
    "hs300": "000300",
    "科创50": "000688",
    "中证a500": "000510",
    "a500": "000510",
    "上证50": "000016",
    "中证500": "000905",
    "中证1000": "000852",
    "北证50": "899050",
}
_INDEX_DETAIL_LOCK = Lock()


def _resolve_index(query: str) -> dict[str, str]:
    keyword = query.strip()
    if not keyword:
        raise HTTPException(status_code=400, detail="指数代码或名称不能为空")
    digits = "".join(char for char in keyword if char.isdigit())
    if len(digits) == 6 and digits in _INDEX_BY_CODE:
        return deepcopy(_INDEX_BY_CODE[digits])
    normalized = keyword.replace(" ", "").lower()
    code = _INDEX_ALIAS_TO_CODE.get(normalized)
    if code:
        return deepcopy(_INDEX_BY_CODE[code])
    for item in _INDEX_SECURITIES:
        if normalized == str(item["name"]).replace(" ", "").lower():
            return deepcopy(item)
    raise HTTPException(status_code=404, detail=f"暂不支持该指数：{keyword}")


def _load_index_quote(security: dict[str, str], warnings: list[str]) -> dict[str, Any]:
    raw = legacy._eastmoney_get_first(
        market_home._get_shared_client(),
        QUOTE_URLS,
        {
            "secid": security["secid"],
            "fields": legacy._quote_fields(),
        },
        "index_quote",
        warnings,
    )
    data = raw.get("data") or {}
    if not data:
        raise ValueError("index quote data is empty")
    quote = legacy._quote_from_raw(data, security)
    quote["market"] = "指数"
    quote["volume"] = legacy._format_lots(data.get("f47"))
    quote["amount"] = legacy._format_cn_money(data.get("f48"))
    return quote


def _parse_index_minutes(raw: dict[str, Any], ndays: int) -> list[dict[str, Any]]:
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
        amount = max(legacy._safe_float(parts[6]), 0.0)
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
                "open": legacy._safe_float(parts[1], price),
                "price": price,
                "high": legacy._safe_float(parts[3], price),
                "low": legacy._safe_float(parts[4], price),
                "volume": volume,
                "amount": amount,
                "average": legacy._safe_float(parts[7], price),
                "volumeRatio": 0.0,
            }
        )
    if not rows:
        raise ValueError(f"index {ndays}d minute trends are empty")
    max_volume = max(volumes or [1.0])
    for row in rows:
        row["volumeRatio"] = min(max(row["volume"] / max_volume, 0.02), 1.0)
    rows.sort(key=lambda row: int(row["timestamp"]))
    return rows


def _load_eastmoney_index_minutes(
    security: dict[str, str],
    ndays: int,
    warnings: list[str],
) -> list[dict[str, Any]]:
    raw = legacy._eastmoney_get_first(
        market_home._get_shared_client(),
        TRENDS_URLS,
        {
            "secid": security["secid"],
            "fields1": "f1,f2,f3,f4,f5,f6,f7,f8",
            "fields2": "f51,f52,f53,f54,f55,f56,f57,f58",
            "iscr": "0",
            "ndays": str(ndays),
        },
        f"index_minute_{ndays}d",
        warnings,
    )
    return _parse_index_minutes(raw, ndays)


def _load_index_minutes(
    security: dict[str, str],
    ndays: int,
    warnings: list[str],
) -> list[dict[str, Any]]:
    return _load_eastmoney_index_minutes(security, ndays, warnings)


def _tencent_symbol(security: dict[str, str]) -> str:
    prefix = "sh" if security["secid"].startswith("1.") else "sz"
    return f"{prefix}{security['code']}"


def _parse_tencent_five_day(
    payload: dict[str, Any],
    security: dict[str, str],
) -> list[dict[str, Any]]:
    symbol = _tencent_symbol(security)
    days = ((((payload.get("data") or {}).get(symbol) or {}).get("data")) or [])
    rows: list[dict[str, Any]] = []
    volumes: list[float] = []
    for day in days:
        raw_date = str(day.get("date") or "")
        if len(raw_date) != 8:
            continue
        date_text = f"{raw_date[:4]}-{raw_date[4:6]}-{raw_date[6:]}"
        previous_cumulative_volume = 0.0
        previous_cumulative_amount = 0.0
        for item in day.get("data") or []:
            parts = str(item).split()
            if len(parts) < 4 or len(parts[0]) != 4:
                continue
            time_text = f"{parts[0][:2]}:{parts[0][2:]}"
            price = legacy._safe_float(parts[1])
            if price <= 0:
                continue
            cumulative_volume = max(legacy._safe_float(parts[2]), 0.0)
            cumulative_amount = max(legacy._safe_float(parts[3]), 0.0)
            volume = max(cumulative_volume - previous_cumulative_volume, 0.0)
            amount = max(cumulative_amount - previous_cumulative_amount, 0.0)
            previous_cumulative_volume = cumulative_volume
            previous_cumulative_amount = cumulative_amount
            average = (
                cumulative_amount / cumulative_volume / 100.0
                if cumulative_volume > 0
                else price
            )
            volumes.append(volume)
            rows.append(
                {
                    "date": date_text,
                    "time": time_text,
                    "timestamp": int(
                        datetime.strptime(
                            f"{date_text} {time_text}",
                            "%Y-%m-%d %H:%M",
                        ).replace(tzinfo=CN_TZ).timestamp()
                        * 1000
                    ),
                    "open": price,
                    "price": price,
                    "high": price,
                    "low": price,
                    "volume": volume,
                    "amount": amount,
                    "average": average,
                    "volumeRatio": 0.0,
                }
            )
    if not rows:
        raise ValueError("Tencent index five-day data is empty")
    max_volume = max(volumes or [1.0])
    for row in rows:
        row["volumeRatio"] = min(max(row["volume"] / max_volume, 0.02), 1.0)
    rows.sort(key=lambda row: int(row["timestamp"]))
    return rows


def _load_tencent_index_five_day(
    security: dict[str, str],
) -> list[dict[str, Any]]:
    client = market_home._get_shared_client()
    response = client.get(
        TENCENT_FIVE_DAY_URL,
        params={"code": _tencent_symbol(security)},
        headers={"Referer": "https://gu.qq.com/"},
        timeout=httpx.Timeout(4.0, connect=1.2),
    )
    response.raise_for_status()
    payload = response.json()
    if not isinstance(payload, dict):
        raise ValueError("Tencent index five-day response is not an object")
    return _parse_tencent_five_day(payload, security)


def _trading_dates(points: list[dict[str, Any]]) -> list[str]:
    return sorted({str(point.get("date") or "") for point in points if point.get("date")})


def _load_index_five_day(
    security: dict[str, str],
    warnings: list[str],
) -> dict[str, Any]:
    eastmoney_points: list[dict[str, Any]] = []
    try:
        eastmoney_points = _load_eastmoney_index_minutes(security, 5, warnings)
    except Exception as exc:
        warnings.append(f"index_five_day_eastmoney_failed: {type(exc).__name__}: {exc}")
    eastmoney_dates = _trading_dates(eastmoney_points)
    if len(eastmoney_dates) >= 5:
        return {
            "points": eastmoney_points,
            "source": "eastmoney_trends2",
            "sourceUrlType": "qt/stock/trends2/get ndays=5",
            "tradingDayCount": len(eastmoney_dates),
            "tradingDates": eastmoney_dates[-5:],
            "status": "ok",
        }
    if eastmoney_points:
        warnings.append(
            f"index_five_day_eastmoney_incomplete: trading_days={len(eastmoney_dates)}"
        )

    try:
        tencent_points = _load_tencent_index_five_day(security)
        tencent_dates = _trading_dates(tencent_points)
        if len(tencent_dates) >= 2:
            return {
                "points": tencent_points,
                "source": "tencent_five_day",
                "sourceUrlType": "appstock/app/day/query",
                "tradingDayCount": len(tencent_dates),
                "tradingDates": tencent_dates[-5:],
                "status": "ok" if len(tencent_dates) >= 5 else "partial",
            }
        warnings.append(
            f"index_five_day_tencent_incomplete: trading_days={len(tencent_dates)}"
        )
    except Exception as exc:
        warnings.append(f"index_five_day_tencent_failed: {type(exc).__name__}: {exc}")

    if len(eastmoney_dates) >= 2:
        return {
            "points": eastmoney_points,
            "source": "eastmoney_trends2",
            "sourceUrlType": "qt/stock/trends2/get ndays=5 incomplete",
            "tradingDayCount": len(eastmoney_dates),
            "tradingDates": eastmoney_dates,
            "status": "partial",
        }
    raise ValueError("true index five-day minute data unavailable")


def _load_index_kline(
    security: dict[str, str],
    warnings: list[str],
    limit: int = 120,
) -> list[dict[str, Any]]:
    params = {
        "secid": security["secid"],
        "klt": "101",
        "fqt": "0",
        "lmt": str(limit),
        "beg": "0",
        "end": "20500101",
        "iscca": "1",
        "fields1": "f1,f2,f3,f4,f5,f6",
        "fields2": "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61",
    }
    last_error: str | None = None
    for url in legacy.EASTMONEY_KLINE_URLS:
        try:
            rows = legacy._parse_eastmoney_kline_rows(
                legacy._eastmoney_get(market_home._get_shared_client(), url, params),
                limit,
            )
            if rows:
                return rows
            last_error = f"{url}: empty klines"
        except (httpx.HTTPError, ValueError) as exc:
            last_error = f"{type(exc).__name__}: {exc}"
    warnings.append(f"index_daily_kline_failed: {last_error}")
    try:
        return legacy._load_tencent_history_kline(
            market_home._get_shared_client(),
            security,
            warnings,
            "daily",
            limit,
        )
    except (httpx.HTTPError, ValueError, KeyError) as exc:
        warnings.append(f"index_daily_kline_tencent_failed: {type(exc).__name__}: {exc}")
        return []


def _load_market_context(code: str) -> dict[str, Any]:
    home = market_home._load_market_home_cached()
    indices_module = home.get("indices") or {}
    related = [
        item
        for item in list(indices_module.get("items") or [])
        if str(item.get("code") or "") != code
    ]
    return {
        "marketBreadth": deepcopy((home.get("marketBreadth") or {}).get("items") or {}),
        "marketBreadthMeta": {
            key: deepcopy((home.get("marketBreadth") or {}).get(key))
            for key in ("status", "source", "updatedAt", "cacheAgeMs", "warnings")
        },
        "sentiment": deepcopy((home.get("sentiment") or {}).get("items") or {}),
        "sentimentMeta": {
            key: deepcopy((home.get("sentiment") or {}).get(key))
            for key in ("status", "source", "updatedAt", "cacheAgeMs", "warnings")
        },
        "relatedIndices": related,
    }


def _build_index_detail(security: dict[str, str]) -> dict[str, Any]:
    started_at = monotonic()
    warnings: list[str] = []
    results: dict[str, Any] = {}
    tasks: dict[str, Callable[[], Any]] = {
        "quote": lambda: _load_index_quote(security, warnings),
        "minutePoints": lambda: _load_index_minutes(security, 1, warnings),
        "fiveDayBundle": lambda: _load_index_five_day(security, warnings),
        "kLinePoints": lambda: _load_index_kline(security, warnings),
        "context": lambda: _load_market_context(security["code"]),
    }
    with ThreadPoolExecutor(max_workers=INDEX_DETAIL_WORKERS) as executor:
        future_to_name = {
            executor.submit(builder): name for name, builder in tasks.items()
        }
        for future in as_completed(future_to_name):
            name = future_to_name[future]
            try:
                results[name] = future.result()
            except Exception as exc:
                warnings.append(f"{name}: {type(exc).__name__}: {exc}")
                results[name] = None

    quote = results.get("quote")
    if not quote:
        raise ValueError("index quote unavailable")
    context = results.get("context") or {}
    five_day_bundle = results.get("fiveDayBundle") or {}
    minute_points = list(results.get("minutePoints") or [])
    five_day_points = list(five_day_bundle.get("points") or [])
    kline_points = list(results.get("kLinePoints") or [])
    complete_modules = sum(bool(value) for value in (minute_points, five_day_points, kline_points))
    status = "ok" if complete_modules == 3 and five_day_bundle.get("status") == "ok" else "partial"
    now_iso = datetime.now(timezone.utc).isoformat()
    return {
        "provider": "eastmoney_index_detail",
        "status": status,
        "code": security["code"],
        "name": security["name"],
        "secid": security["secid"],
        "quote": quote,
        "minutePoints": minute_points,
        "fiveDayPoints": five_day_points,
        "fiveDayMeta": {
            "status": five_day_bundle.get("status", "unavailable"),
            "source": five_day_bundle.get("source", ""),
            "sourceUrlType": five_day_bundle.get("sourceUrlType", ""),
            "tradingDayCount": int(five_day_bundle.get("tradingDayCount") or 0),
            "tradingDates": list(five_day_bundle.get("tradingDates") or []),
        },
        "kLinePoints": kline_points,
        "marketBreadth": context.get("marketBreadth") or {},
        "marketBreadthMeta": context.get("marketBreadthMeta") or {},
        "sentiment": context.get("sentiment") or {},
        "sentimentMeta": context.get("sentimentMeta") or {},
        "relatedIndices": list(context.get("relatedIndices") or []),
        "dataSourceLabel": f"指数真实行情 · {security['name']} · 东方财富公开 JSON",
        "updatedAt": now_iso,
        "cacheHit": False,
        "cacheAgeMs": 0,
        "totalLatencyMs": int((monotonic() - started_at) * 1000),
        "warnings": warnings,
    }


def _with_index_cache_label(
    payload: dict[str, Any],
    age_seconds: float,
    stale: bool,
) -> dict[str, Any]:
    cached = deepcopy(payload)
    cached["cacheHit"] = True
    cached["cacheAgeMs"] = max(int(age_seconds * 1000), 0)
    if stale and cached.get("status") in {"ok", "partial"}:
        cached["status"] = "stale"
    label = "stale" if stale else "hit"
    cached["warnings"] = list(cached.get("warnings") or []) + [
        f"index_detail_cache: {label} age={age_seconds:.2f}s"
    ]
    return cached


def _load_index_detail_cached(security: dict[str, str]) -> dict[str, Any]:
    key = legacy._cache_key(
        "index-detail",
        security["code"],
        INDEX_DETAIL_CACHE_VERSION,
    )
    fresh = legacy._cache_get_seconds(key, INDEX_DETAIL_FRESH_SECONDS)
    if fresh is not None:
        payload, age = fresh
        return _with_index_cache_label(payload, age, stale=False)

    with _INDEX_DETAIL_LOCK:
        fresh = legacy._cache_get_seconds(key, INDEX_DETAIL_FRESH_SECONDS)
        if fresh is not None:
            payload, age = fresh
            return _with_index_cache_label(payload, age, stale=False)
        try:
            payload = _build_index_detail(security)
        except Exception:
            stale = legacy._cache_get_seconds(key, INDEX_DETAIL_STALE_SECONDS)
            if stale is not None:
                cached_payload, age = stale
                return _with_index_cache_label(cached_payload, age, stale=True)
            raise
        legacy._cache_put(key, payload)
        return payload


@app.get(INDEX_DETAIL_PATH)
async def a_share_index_detail(
    query: str = Query(..., min_length=1, max_length=32),
) -> dict[str, Any]:
    security = _resolve_index(query)
    try:
        return await asyncio.to_thread(_load_index_detail_cached, security)
    except HTTPException:
        raise
    except Exception as exc:
        LOGGER.exception("index detail failed for %s", security["code"])
        raise HTTPException(
            status_code=502,
            detail=f"指数详情暂不可用：{type(exc).__name__}: {exc}",
        ) from exc
