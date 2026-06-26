from __future__ import annotations

import asyncio
import json
import logging
from copy import deepcopy
from datetime import datetime, time, timezone
from time import monotonic
from typing import Any
from zoneinfo import ZoneInfo

from fastapi import HTTPException, Query
from fastapi.responses import Response

import market_home_server as market_home
from realtime_runtime import CacheResult, TRENDS_URLS


app = market_home.app
legacy = market_home.legacy
runtime = legacy.realtime_runtime

LOGGER = logging.getLogger("ai-ledger-stock-proxy.stock-detail")
CN_TZ = ZoneInfo("Asia/Shanghai")
REALTIME_PATH = "/api/stock/a-share/realtime"
AUCTION_PATH = "/api/stock/a-share/auction"
EASTMONEY_AUCTION_SOURCE = "eastmoney_trends2_pre_market"
EASTMONEY_AUCTION_SOURCE_URL_TYPE = "qt/stock/trends2/get iscr=1 iscca=0"
TDX_AUCTION_SOURCE = "eltdx_7709_call_auction"
TDX_AUCTION_SOURCE_URL_TYPE = "tdx 7709 command 0x056a"
STANDARD_CLOSE_SOURCE_URL_TYPE = "qt/stock/trends2/get standard close-auction minute points"
OPEN_AUCTION_START = 9 * 60 + 15
OPEN_AUCTION_END = 9 * 60 + 25
CLOSE_AUCTION_START = 14 * 60 + 57
CLOSE_AUCTION_END = 15 * 60
TDX_TIMEOUT_SECONDS = 2.0
TDX_ASYNC_BUDGET_SECONDS = 2.2
AUCTION_TOTAL_BUDGET_SECONDS = 2.6


def _safe_float(value: Any, default: float = 0.0) -> float:
    try:
        if value is None:
            return default
        text_value = str(value).replace(",", "").replace("%", "").strip()
        if text_value in {"", "-", "--", "None", "null"}:
            return default
        return float(text_value)
    except (TypeError, ValueError):
        return default


def _secid(code: str) -> str:
    return f"1.{code}" if code.startswith(("6", "9")) else f"0.{code}"


def _tdx_code(code: str) -> str:
    if code.startswith("6"):
        return f"sh{code}"
    if code.startswith(("4", "8", "9")):
        return f"bj{code}"
    return f"sz{code}"


def _code_from_query(query: str) -> str | None:
    digits = "".join(char for char in query if char.isdigit())
    return digits if len(digits) == 6 else None


def _minute_of_day(value: str) -> int | None:
    text_value = str(value).strip()
    if " " in text_value:
        text_value = text_value.rsplit(" ", 1)[-1]
    pieces = text_value.split(":")
    if len(pieces) < 2:
        return None
    try:
        return int(pieces[0]) * 60 + int(pieces[1])
    except ValueError:
        return None


def _phase_for_time(value: str) -> str:
    minute = _minute_of_day(value)
    if minute is None:
        return "continuous"
    if OPEN_AUCTION_START <= minute <= OPEN_AUCTION_END:
        return "openAuction"
    if CLOSE_AUCTION_START <= minute <= CLOSE_AUCTION_END:
        return "closeAuction"
    return "continuous"


def _epoch_ms(date_text: str, time_text: str) -> int:
    pieces = time_text.split(":")
    pattern = "%Y-%m-%d %H:%M:%S" if len(pieces) >= 3 else "%Y-%m-%d %H:%M"
    value = datetime.strptime(f"{date_text} {time_text}", pattern)
    return int(value.replace(tzinfo=CN_TZ).timestamp() * 1000)


def _auction_cache_policy(
    now_cn: datetime | None = None,
    *,
    force: bool = False,
) -> tuple[float, float, bool, str]:
    if force:
        return 0.0, 2.0, False, "forced-live"
    value = now_cn or datetime.now(CN_TZ)
    if value.weekday() >= 5:
        return 300.0, 12 * 60 * 60.0, True, "market-closed"
    current = value.time()
    in_open_auction = time(9, 14, 30) <= current <= time(9, 26, 0)
    in_close_auction = time(14, 56, 30) <= current <= time(15, 1, 0)
    if in_open_auction or in_close_auction:
        return 0.35, 2.0, False, "live-auction"
    if time(9, 0, 0) <= current <= time(15, 5, 0):
        return 20.0, 120.0, False, "trading-session"
    return 300.0, 12 * 60 * 60.0, True, "market-closed"


def _point_price(parts: list[str]) -> tuple[float, str]:
    matched_price = _safe_float(parts[2]) if len(parts) > 2 else 0.0
    open_price = _safe_float(parts[1]) if len(parts) > 1 else 0.0
    average_price = _safe_float(parts[7]) if len(parts) > 7 else 0.0
    candidates = (
        (matched_price, "f53_matched_price"),
        (open_price, "f52_open"),
        (average_price, "f58_average_fallback"),
    )
    for value, source in candidates:
        if value > 0:
            return value, source
    return 0.0, "unavailable"


def _unmatched_direction(raw_value: int) -> str:
    if raw_value > 0:
        return "buy"
    if raw_value < 0:
        return "sell"
    return "balanced"


def _recalculate_volume_ratios(points: list[dict[str, Any]]) -> None:
    max_volume = max(
        (
            _safe_float(point.get("matchedVolume"), _safe_float(point.get("volume")))
            + _safe_float(point.get("unmatchedVolume"))
            for point in points
        ),
        default=0.0,
    )
    for point in points:
        total = _safe_float(point.get("matchedVolume"), _safe_float(point.get("volume"))) + _safe_float(
            point.get("unmatchedVolume")
        )
        point["volumeRatio"] = (
            min(max(total / max_volume, 0.02), 1.0) if max_volume > 0 else 0.02
        )


def parse_eastmoney_auction_trends(
    payload: dict[str, Any],
    expected_trade_date: str | None = None,
) -> dict[str, Any]:
    trends = ((payload.get("data") or {}).get("trends") or [])
    parsed_by_timestamp: dict[int, dict[str, Any]] = {}
    warnings: list[str] = []
    for item in trends:
        parts = str(item).split(",")
        if len(parts) < 8 or " " not in parts[0]:
            continue
        date_text, time_text = parts[0].split(" ", 1)
        time_text = time_text[:5]
        phase = _phase_for_time(time_text)
        if phase == "continuous":
            continue
        price, price_source = _point_price(parts)
        if price <= 0:
            warnings.append(f"auction:{phase}:{time_text}: missing_matched_price")
            continue
        timestamp = _epoch_ms(date_text, time_text)
        average = _safe_float(parts[7], price)
        matched_volume = max(_safe_float(parts[5]), 0.0)
        parsed_by_timestamp[timestamp] = {
            "date": date_text,
            "time": time_text,
            "timestamp": timestamp,
            "price": price,
            "average": average if average > 0 else price,
            "open": _safe_float(parts[1]),
            "high": _safe_float(parts[3]),
            "low": _safe_float(parts[4]),
            "volume": matched_volume,
            "amount": max(_safe_float(parts[6]), 0.0),
            "matchedVolume": matched_volume,
            "unmatchedVolume": None,
            "unmatchedSignedRaw": None,
            "unmatchedDirection": "unavailable",
            "matchedVolumeSource": "eastmoney_trends2_f56",
            "unmatchedVolumeSource": "unavailable",
            "volumeIsDerived": False,
            "volumeRatio": 0.0,
            "sessionPhase": phase,
            "isAuction": True,
            "isDerived": False,
            "priceSource": price_source,
            "auctionSource": EASTMONEY_AUCTION_SOURCE,
        }

    parsed = sorted(parsed_by_timestamp.values(), key=lambda point: int(point["timestamp"]))
    if parsed:
        latest_date = max(str(point["date"]) for point in parsed)
        if expected_trade_date and latest_date != expected_trade_date:
            warnings.append(
                f"auction: trade_date_mismatch expected={expected_trade_date} actual={latest_date}"
            )
            parsed = []
        else:
            parsed = [point for point in parsed if point["date"] == latest_date]

    _recalculate_volume_ratios(parsed)
    open_points = [point for point in parsed if point["sessionPhase"] == "openAuction"]
    close_points = [point for point in parsed if point["sessionPhase"] == "closeAuction"]
    return {
        "openPoints": open_points,
        "closePoints": close_points,
        "tradeDate": str(parsed[-1]["date"]) if parsed else expected_trade_date or "",
        "source": EASTMONEY_AUCTION_SOURCE,
        "sourceUrlType": EASTMONEY_AUCTION_SOURCE_URL_TYPE,
        "warnings": warnings,
    }


def parse_tdx_auction_series(
    series: Any,
    trade_date: str,
) -> dict[str, Any]:
    parsed_by_timestamp: dict[int, dict[str, Any]] = {}
    warnings: list[str] = []
    for raw in getattr(series, "points", ()) or ():
        time_label = str(getattr(raw, "time_label", "") or "")
        phase = _phase_for_time(time_label)
        if phase == "continuous":
            continue
        price = _safe_float(getattr(raw, "price", None))
        if price <= 0:
            warnings.append(f"tdx_auction:{phase}:{time_label}: missing_price")
            continue
        matched_volume = max(int(getattr(raw, "matched_volume", 0) or 0), 0)
        unmatched_signed = int(getattr(raw, "unmatched_signed_raw", 0) or 0)
        unmatched_volume = abs(int(getattr(raw, "unmatched_volume", abs(unmatched_signed)) or 0))
        timestamp = _epoch_ms(trade_date, time_label)
        parsed_by_timestamp[timestamp] = {
            "date": trade_date,
            "time": time_label,
            "timestamp": timestamp,
            "price": price,
            "average": price,
            "open": price,
            "high": price,
            "low": price,
            "volume": float(matched_volume),
            "amount": price * matched_volume * 100.0,
            "matchedVolume": float(matched_volume),
            "unmatchedVolume": float(unmatched_volume),
            "unmatchedSignedRaw": unmatched_signed,
            "unmatchedDirection": _unmatched_direction(unmatched_signed),
            "matchedVolumeSource": "tdx_7709_0x056a_raw",
            "unmatchedVolumeSource": "tdx_7709_0x056a_raw",
            "volumeIsDerived": False,
            "volumeRatio": 0.0,
            "sessionPhase": phase,
            "isAuction": True,
            "isDerived": False,
            "priceSource": "tdx_7709_0x056a_price",
            "auctionSource": TDX_AUCTION_SOURCE,
        }

    parsed = sorted(parsed_by_timestamp.values(), key=lambda point: int(point["timestamp"]))
    _recalculate_volume_ratios(parsed)
    return {
        "openPoints": [point for point in parsed if point["sessionPhase"] == "openAuction"],
        "closePoints": [point for point in parsed if point["sessionPhase"] == "closeAuction"],
        "tradeDate": trade_date,
        "source": TDX_AUCTION_SOURCE,
        "sourceUrlType": TDX_AUCTION_SOURCE_URL_TYPE,
        "warnings": warnings,
    }


def _load_tdx_auction_series_sync(code: str, trade_date: str) -> tuple[dict[str, Any], int]:
    from eltdx import TdxClient

    started = monotonic()
    with TdxClient(
        timeout=TDX_TIMEOUT_SECONDS,
        pool_size=1,
        probe_hosts=False,
        heartbeat_interval=None,
    ) as client:
        series = client.auctions.series(_tdx_code(code))
    latency = int((monotonic() - started) * 1000)
    return parse_tdx_auction_series(series, trade_date), latency


def _merge_auction_sources(
    eastmoney: dict[str, Any] | None,
    tdx: dict[str, Any] | None,
    warnings: list[str],
) -> dict[str, Any]:
    eastmoney = eastmoney or {}
    tdx = tdx or {}
    tdx_open = list(tdx.get("openPoints") or [])
    tdx_close = list(tdx.get("closePoints") or [])
    east_open = list(eastmoney.get("openPoints") or [])
    east_close = list(eastmoney.get("closePoints") or [])

    open_points = tdx_open or east_open
    close_points = tdx_close or east_close
    all_points = open_points + close_points
    _recalculate_volume_ratios(all_points)

    used_sources: list[str] = []
    used_types: list[str] = []
    if tdx_open or tdx_close:
        used_sources.append(TDX_AUCTION_SOURCE)
        used_types.append(TDX_AUCTION_SOURCE_URL_TYPE)
    if (not tdx_open and east_open) or (not tdx_close and east_close):
        used_sources.append(EASTMONEY_AUCTION_SOURCE)
        used_types.append(EASTMONEY_AUCTION_SOURCE_URL_TYPE)

    if not all_points:
        raise ValueError("all auction sources returned no usable points")

    trade_date = str(
        tdx.get("tradeDate")
        or eastmoney.get("tradeDate")
        or all_points[-1].get("date")
        or ""
    )
    return {
        "openPoints": open_points,
        "closePoints": close_points,
        "tradeDate": trade_date,
        "source": ",".join(dict.fromkeys(used_sources)) or EASTMONEY_AUCTION_SOURCE,
        "sourceUrlType": "; ".join(dict.fromkeys(used_types)) or EASTMONEY_AUCTION_SOURCE_URL_TYPE,
        "warnings": warnings
        + list(eastmoney.get("warnings") or [])
        + list(tdx.get("warnings") or []),
    }


def _phase_payload(
    phase: str,
    points: list[dict[str, Any]],
    source: str,
    source_url_type: str,
) -> dict[str, Any]:
    is_open = phase == "open"
    matched_count = sum(point.get("matchedVolume") is not None for point in points)
    unmatched_count = sum(point.get("unmatchedVolume") is not None for point in points)
    volume_status = (
        "ok"
        if matched_count > 0 and unmatched_count > 0
        else "partial"
        if matched_count > 0
        else "unavailable"
    )
    return {
        "status": "ok" if points else "unavailable",
        "phase": phase,
        "startTime": "09:15" if is_open else "14:57",
        "endTime": "09:25" if is_open else "15:00",
        "source": source,
        "sourceUrlType": source_url_type,
        "isDerived": False,
        "pointCount": len(points),
        "volumePointCount": max(matched_count, unmatched_count),
        "matchedVolumePointCount": matched_count,
        "unmatchedVolumePointCount": unmatched_count,
        "volumeStatus": volume_status,
        "volumeUnit": "hand",
        "latestPointTime": str(points[-1].get("time") or "") if points else "",
        "points": points,
    }


def _build_auction_payload(
    open_points: list[dict[str, Any]],
    close_points: list[dict[str, Any]],
    *,
    source: str,
    source_url_type: str,
    updated_at: str,
    source_timestamp: str,
    trade_date: str,
    cache_hit: bool,
    cache_age_ms: int,
    stale: bool,
    refresh_mode: str,
    warnings: list[str] | None = None,
) -> dict[str, Any]:
    if open_points and close_points:
        status = "stale" if stale else "ok"
    elif open_points or close_points:
        status = "stale" if stale else "partial"
    else:
        status = "unavailable"
    warning_list = list(warnings or [])
    if not open_points:
        warning_list.append("auction: opening_points_unavailable")
    if not close_points:
        warning_list.append("auction: closing_points_unavailable")
    return {
        "status": status,
        "source": source,
        "sourceUrlType": source_url_type,
        "updatedAt": updated_at,
        "sourceTimestamp": source_timestamp,
        "tradeDate": trade_date,
        "cacheHit": cache_hit,
        "cacheAgeMs": cache_age_ms,
        "stale": stale,
        "refreshMode": refresh_mode,
        "isDerived": False,
        "warnings": warning_list,
        "open": _phase_payload("open", open_points, source, source_url_type),
        "close": _phase_payload("close", close_points, source, source_url_type),
    }


async def _load_eastmoney_auction(
    security: dict[str, str],
    trade_date: str,
) -> tuple[dict[str, Any], str, int]:
    payload, host, latency = await runtime._get_json(
        TRENDS_URLS,
        {
            "secid": security["secid"],
            "fields1": "f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11,f12,f13",
            "fields2": "f51,f52,f53,f54,f55,f56,f57,f58",
            "ndays": "1",
            "iscr": "1",
            "iscca": "0",
        },
    )
    return parse_eastmoney_auction_trends(payload, trade_date), host, latency


async def _auction_loader(
    security: dict[str, str],
    expected_trade_date: str | None = None,
) -> tuple[Any, str, int, str]:
    trade_date = expected_trade_date or runtime._latest_trade_date(security["code"])
    east_task = asyncio.create_task(_load_eastmoney_auction(security, trade_date))
    tdx_task = asyncio.create_task(
        asyncio.to_thread(_load_tdx_auction_series_sync, security["code"], trade_date)
    )

    east_result: dict[str, Any] | None = None
    tdx_result: dict[str, Any] | None = None
    hosts: list[str] = []
    latencies: list[int] = []
    warnings: list[str] = []

    try:
        east_result, east_host, east_latency = await east_task
        hosts.append(east_host)
        latencies.append(east_latency)
    except Exception as exc:
        warnings.append(f"auction:eastmoney_unavailable {type(exc).__name__}: {exc}")

    try:
        tdx_result, tdx_latency = await asyncio.wait_for(
            asyncio.shield(tdx_task), timeout=TDX_ASYNC_BUDGET_SECONDS
        )
        hosts.append("tdx-7709")
        latencies.append(tdx_latency)
    except asyncio.TimeoutError:
        tdx_task.cancel()
        await asyncio.gather(tdx_task, return_exceptions=True)
        warnings.append(f"auction:tdx_budget_exceeded {TDX_ASYNC_BUDGET_SECONDS:.1f}s")
    except Exception as exc:
        warnings.append(f"auction:tdx_unavailable {type(exc).__name__}: {exc}")

    parsed = _merge_auction_sources(east_result, tdx_result, warnings)
    points = list(parsed.get("openPoints") or []) + list(parsed.get("closePoints") or [])
    source_timestamp = (
        datetime.fromtimestamp(points[-1]["timestamp"] / 1000, tz=CN_TZ)
        .astimezone(timezone.utc)
        .isoformat()
        if points
        else datetime.now(timezone.utc).isoformat()
    )
    return parsed, ",".join(dict.fromkeys(hosts)) or "auction-upstream", max(latencies or [0]), source_timestamp


async def load_auction(
    security: dict[str, str],
    *,
    force: bool = False,
) -> tuple[CacheResult, str]:
    trade_date = runtime._latest_trade_date(security["code"])
    fresh_seconds, stale_seconds, allow_stale, refresh_mode = _auction_cache_policy(
        force=force
    )
    result = await runtime._cached(
        f"auction:v2:{trade_date}:{security['code']}",
        fresh_seconds=fresh_seconds,
        stale_seconds=stale_seconds,
        loader=lambda: _auction_loader(security, trade_date),
        allow_stale_while_revalidate=allow_stale,
    )
    return result, refresh_mode


def _real_close_points_from_minute(points: list[dict[str, Any]]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for raw in points:
        time_text = str(raw.get("time") or "")[:5]
        if _phase_for_time(time_text) != "closeAuction":
            continue
        point = deepcopy(raw)
        matched_volume = max(_safe_float(point.get("volume")), 0.0)
        point["sessionPhase"] = "closeAuction"
        point["isAuction"] = True
        point["isDerived"] = False
        point["priceSource"] = "standard_trends_f53_price"
        point["matchedVolume"] = matched_volume
        point["unmatchedVolume"] = None
        point["unmatchedSignedRaw"] = None
        point["unmatchedDirection"] = "unavailable"
        point["matchedVolumeSource"] = "standard_trends_volume"
        point["unmatchedVolumeSource"] = "unavailable"
        point["volumeIsDerived"] = False
        result.append(point)
    _recalculate_volume_ratios(result)
    return sorted(result, key=lambda point: int(point.get("timestamp") or 0))


def merge_auction_into_minute_points(
    minute_points: list[dict[str, Any]],
    open_points: list[dict[str, Any]],
    close_points: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    merged: dict[int, dict[str, Any]] = {}
    for raw in minute_points:
        point = deepcopy(raw)
        phase = _phase_for_time(str(point.get("time") or "")[:5])
        point["sessionPhase"] = phase
        point["isAuction"] = phase != "continuous"
        point.setdefault("isDerived", False)
        timestamp = int(point.get("timestamp") or 0)
        if timestamp > 0:
            merged[timestamp] = point
    for raw in open_points + close_points:
        timestamp = int(raw.get("timestamp") or 0)
        if timestamp > 0:
            merged[timestamp] = deepcopy(raw)
    values = sorted(merged.values(), key=lambda point: int(point.get("timestamp") or 0))
    max_volume = max((_safe_float(point.get("volume")) for point in values), default=0.0)
    if max_volume > 0:
        for point in values:
            if not point.get("isAuction"):
                point["volumeRatio"] = min(
                    max(_safe_float(point.get("volume")) / max_volume, 0.02),
                    1.0,
                )
    return values


def _unavailable_auction(reason: str, refresh_mode: str = "unavailable") -> dict[str, Any]:
    now = datetime.now(timezone.utc).isoformat()
    return _build_auction_payload(
        [],
        [],
        source=f"{TDX_AUCTION_SOURCE},{EASTMONEY_AUCTION_SOURCE}",
        source_url_type=f"{TDX_AUCTION_SOURCE_URL_TYPE}; {EASTMONEY_AUCTION_SOURCE_URL_TYPE}",
        updated_at=now,
        source_timestamp=now,
        trade_date="",
        cache_hit=False,
        cache_age_ms=0,
        stale=False,
        refresh_mode=refresh_mode,
        warnings=[reason],
    )


def _fallback_auction_from_realtime(
    payload: dict[str, Any],
    reason: str,
    refresh_mode: str,
) -> dict[str, Any]:
    close_points = _real_close_points_from_minute(list(payload.get("minutePoints") or []))
    now = datetime.now(timezone.utc).isoformat()
    trade_date = str(close_points[-1].get("date") or "") if close_points else ""
    return _build_auction_payload(
        [],
        close_points,
        source=EASTMONEY_AUCTION_SOURCE,
        source_url_type=(
            f"{EASTMONEY_AUCTION_SOURCE_URL_TYPE}; {STANDARD_CLOSE_SOURCE_URL_TYPE}"
            if close_points
            else EASTMONEY_AUCTION_SOURCE_URL_TYPE
        ),
        updated_at=now,
        source_timestamp=str(payload.get("sourceTimestamp") or now),
        trade_date=trade_date,
        cache_hit=bool(payload.get("cacheHit")),
        cache_age_ms=int(payload.get("cacheAgeMs") or 0),
        stale=False,
        refresh_mode=refresh_mode,
        warnings=[reason],
    )


def _finalize_payload(
    payload: dict[str, Any],
    auction: dict[str, Any],
    started: float,
    auction_result: CacheResult | None = None,
) -> dict[str, Any]:
    open_points = list((auction.get("open") or {}).get("points") or [])
    close_points = list((auction.get("close") or {}).get("points") or [])
    payload["minutePoints"] = merge_auction_into_minute_points(
        list(payload.get("minutePoints") or []),
        open_points,
        close_points,
    )
    payload["auction"] = auction
    payload["warnings"] = list(payload.get("warnings") or []) + list(
        auction.get("warnings") or []
    )
    if auction_result is not None:
        payload["auctionSourceHost"] = auction_result.source_host
        payload["auctionUpstreamLatencyMs"] = auction_result.upstream_latency_ms
    payload["totalLatencyMs"] = int((monotonic() - started) * 1000)
    payload["payloadBytes"] = len(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    )
    return payload


async def _cancel_task(task: asyncio.Task[Any] | None) -> None:
    if task is None or task.done():
        return
    task.cancel()
    await asyncio.gather(task, return_exceptions=True)


async def _enriched_realtime(
    query: str,
    ndays: int,
    *,
    force_auction: bool = False,
) -> dict[str, Any]:
    started = monotonic()
    code = _code_from_query(query)
    security = (
        {"code": code, "name": code, "secid": _secid(code), "resolveSource": "direct-code"}
        if code
        else await runtime.resolve(query)
    )
    realtime_task = asyncio.create_task(runtime.realtime(query, ndays))
    auction_task = (
        asyncio.create_task(load_auction(security, force=force_auction))
        if ndays == 1
        else None
    )
    try:
        payload = await realtime_task
    except Exception:
        await _cancel_task(auction_task)
        raise
    if auction_task is None:
        return _finalize_payload(
            payload,
            _unavailable_auction("auction: only available for ndays=1"),
            started,
        )

    try:
        auction_result, refresh_mode = await asyncio.wait_for(
            asyncio.shield(auction_task), timeout=AUCTION_TOTAL_BUDGET_SECONDS
        )
    except asyncio.TimeoutError:
        await _cancel_task(auction_task)
        return _finalize_payload(
            payload,
            _fallback_auction_from_realtime(
                payload,
                f"auction: upstream budget exceeded {AUCTION_TOTAL_BUDGET_SECONDS:.1f}s",
                "timeout-fallback",
            ),
            started,
        )
    except Exception as exc:
        return _finalize_payload(
            payload,
            _fallback_auction_from_realtime(
                payload,
                f"auction: unavailable {type(exc).__name__}: {exc}",
                "error-fallback",
            ),
            started,
        )

    parsed = auction_result.value
    open_points = list(parsed.get("openPoints") or [])
    close_points = list(parsed.get("closePoints") or [])
    standard_close = _real_close_points_from_minute(list(payload.get("minutePoints") or []))
    source = str(parsed.get("source") or EASTMONEY_AUCTION_SOURCE)
    source_url_type = str(parsed.get("sourceUrlType") or EASTMONEY_AUCTION_SOURCE_URL_TYPE)
    if not close_points and standard_close:
        close_points = standard_close
        source = f"{source},{EASTMONEY_AUCTION_SOURCE}"
        source_url_type = f"{source_url_type}; {STANDARD_CLOSE_SOURCE_URL_TYPE}"

    auction = _build_auction_payload(
        open_points,
        close_points,
        source=source,
        source_url_type=source_url_type,
        updated_at=auction_result.updated_at,
        source_timestamp=auction_result.source_timestamp,
        trade_date=str(parsed.get("tradeDate") or ""),
        cache_hit=auction_result.cache_hit,
        cache_age_ms=auction_result.cache_age_ms,
        stale=auction_result.stale,
        refresh_mode=refresh_mode,
        warnings=list(parsed.get("warnings") or []),
    )
    return _finalize_payload(payload, auction, started, auction_result)


def _remove_get_routes(paths: set[str]) -> None:
    app.router.routes[:] = [
        route
        for route in app.router.routes
        if not (
            getattr(route, "path", None) in paths
            and "GET" in (getattr(route, "methods", None) or set())
        )
    ]


_remove_get_routes({REALTIME_PATH, AUCTION_PATH})


@app.get(REALTIME_PATH, response_class=Response)
async def a_share_realtime_with_auction(
    query: str = Query(...),
    ndays: int = Query(1, ge=1, le=5),
    force_auction: bool = Query(False, alias="forceAuction"),
) -> Response:
    if ndays not in {1, 5}:
        raise HTTPException(status_code=400, detail="ndays must be 1 or 5")
    return legacy._fast_json_response(
        await _enriched_realtime(query, ndays, force_auction=force_auction)
    )


@app.get(AUCTION_PATH, response_class=Response)
async def a_share_auction_real(
    query: str = Query(...),
    force: bool = Query(False),
) -> Response:
    payload = await _enriched_realtime(query, 1, force_auction=force)
    return legacy._fast_json_response(payload["auction"])
