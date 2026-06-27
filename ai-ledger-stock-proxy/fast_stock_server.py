from __future__ import annotations

import asyncio
import json
import logging
from datetime import datetime, time, timezone
from time import monotonic, time_ns
from typing import Any, Callable

from fastapi import Query
from fastapi.responses import Response
from starlette.middleware.gzip import GZipMiddleware

import stock_detail_server as detail


app = detail.app
legacy = detail.legacy
runtime = detail.runtime
LOGGER = logging.getLogger("ai-ledger-stock-proxy.fast-stock")

REALTIME_PATH = "/api/stock/a-share/realtime"
DETAIL_PATH = "/api/stock/a-share/detail"
CRAWL_DETAIL_PATH = "/api/stock/crawl/a-share/detail"
REALTIME_AUCTION_GRACE_SECONDS = 0.045
DEFAULT_PREWARM_CODE = "600396"

if not any(getattr(item, "cls", None) is GZipMiddleware for item in app.user_middleware):
    app.add_middleware(GZipMiddleware, minimum_size=1024, compresslevel=4)


def _consume_background(task: asyncio.Task[Any]) -> None:
    try:
        task.result()
    except asyncio.CancelledError:
        pass
    except Exception as exc:
        LOGGER.warning(
            "background stock refresh failed: %s: %s",
            type(exc).__name__,
            exc,
        )


def _schedule(coro: Any, name: str) -> asyncio.Task[Any]:
    task = asyncio.create_task(coro, name=name)
    task.add_done_callback(_consume_background)
    return task


def _security_for_query(query: str) -> dict[str, str] | None:
    code = detail._code_from_query(query)
    if not code:
        return None
    return {
        "code": code,
        "name": code,
        "secid": detail._secid(code),
        "resolveSource": "direct-code",
    }


def _minute_key(point: dict[str, Any]) -> str:
    date = str(point.get("date") or point.get("tradeDate") or "").strip()
    time_text = str(point.get("time") or point.get("minute") or "").strip()
    if date and time_text and date not in time_text:
        return f"{date} {time_text}"
    return time_text or date


def _trade_key(point: dict[str, Any]) -> str:
    return "|".join(
        (
            str(point.get("time") or point.get("timestamp") or "").strip(),
            str(point.get("price") or "").strip(),
            str(point.get("volume") or "").strip(),
            str(point.get("direction") or "").strip(),
        )
    )


def _latest_cursor(points: list[dict[str, Any]], key_fn: Callable[[dict[str, Any]], str]) -> str:
    for point in reversed(points):
        key = key_fn(point)
        if key:
            return key
    return ""


def _cursor_date(value: str) -> str:
    text_value = value.strip()
    if len(text_value) >= 10 and text_value[4:5] == "-" and text_value[7:8] == "-":
        return text_value[:10]
    return ""


def _cursor_time(value: str) -> str:
    return value.split("|", 1)[0].strip()


def _delta_from_cursor(
    points: list[dict[str, Any]],
    cursor: str,
    key_fn: Callable[[dict[str, Any]], str],
) -> list[dict[str, Any]]:
    if not cursor:
        return points
    for index in range(len(points) - 1, -1, -1):
        if key_fn(points[index]) == cursor:
            return points[index:]
    cursor_time = _cursor_time(cursor)
    return [point for point in points if _cursor_time(key_fn(point)) >= cursor_time]


def _point_phase(point: dict[str, Any]) -> str:
    explicit = str(
        point.get("phase")
        or point.get("sessionPhase")
        or point.get("auctionPhase")
        or ""
    ).strip()
    if explicit:
        return explicit
    return detail._phase_for_time(str(point.get("time") or "")[:5])


def _is_auction_point(point: dict[str, Any]) -> bool:
    phase = _point_phase(point).lower()
    return phase != "continuous" and phase not in {"", "none", "null"}


def _merge_delta_with_auction_snapshot(
    delta: list[dict[str, Any]],
    minute_points: list[dict[str, Any]],
    ndays: int,
) -> list[dict[str, Any]]:
    if ndays != 1:
        return delta
    repaired: dict[str, dict[str, Any]] = {}
    for point in minute_points:
        if _is_auction_point(point):
            repaired[_minute_key(point)] = point
    for point in delta:
        repaired[_minute_key(point)] = point
    return sorted(
        repaired.values(),
        key=lambda point: (
            int(point.get("timestamp") or 0),
            _minute_key(point),
        ),
    )


def _normalize_minute_contract(payload: dict[str, Any]) -> None:
    points = list(payload.get("minutePoints") or [])
    continuous_max_volume = 0.0

    for point in points:
        phase = _point_phase(point) or "continuous"
        is_auction = phase.lower() != "continuous"
        raw_volume = max(detail._safe_float(point.get("volume")), 0.0)

        point["phase"] = phase
        point.setdefault("sessionPhase", phase)
        point.setdefault("unmatchedVolume", None)
        point.setdefault("unmatchedDirection", "unavailable")

        if is_auction:
            matched_volume = point.get("matchedVolume")
            if matched_volume is None:
                point["matchedVolume"] = raw_volume
            point["volume"] = 0.0
            point["volumeRatio"] = 0.0
        else:
            point.setdefault("matchedVolume", None)
            continuous_max_volume = max(continuous_max_volume, raw_volume)

    if continuous_max_volume > 0:
        for point in points:
            if _is_auction_point(point):
                continue
            volume = max(detail._safe_float(point.get("volume")), 0.0)
            point["volumeRatio"] = min(max(volume / continuous_max_volume, 0.02), 1.0)

    payload["minutePoints"] = points


def _apply_incremental_payload(
    payload: dict[str, Any],
    *,
    ndays: int,
    since_minute_key: str,
    since_trade_key: str,
    compact: bool,
) -> dict[str, Any]:
    _normalize_minute_contract(payload)
    minute_points = list(payload.get("minutePoints") or [])
    trade_ticks = list(payload.get("tradeTicks") or [])
    minute_cursor = _latest_cursor(minute_points, _minute_key)
    trade_cursor = _latest_cursor(trade_ticks, _trade_key)
    payload["minuteCursor"] = minute_cursor
    payload["tradeCursor"] = trade_cursor

    if not compact or (not since_minute_key and not since_trade_key):
        payload["isDelta"] = False
        payload["minuteIsSnapshot"] = True
        payload["ticksAreSnapshot"] = True
        payload["payloadBytes"] = len(
            json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        )
        return payload

    latest_minute_date = _cursor_date(minute_cursor)
    requested_minute_date = _cursor_date(since_minute_key)
    minute_reset = bool(
        ndays == 1
        and requested_minute_date
        and latest_minute_date
        and requested_minute_date != latest_minute_date
    )

    if since_minute_key and not minute_reset:
        minute_delta = _delta_from_cursor(
            minute_points,
            since_minute_key,
            _minute_key,
        )
        minute_delta = _merge_delta_with_auction_snapshot(
            minute_delta,
            minute_points,
            ndays,
        )
        payload.pop("minutePoints", None)
        payload["minuteDelta"] = minute_delta
        payload["minuteIsSnapshot"] = False
        if ndays == 1:
            payload["auctionPointsIncluded"] = True
    else:
        payload["minuteIsSnapshot"] = True

    if since_trade_key:
        payload.pop("tradeTicks", None)
        payload["newTradeTicks"] = _delta_from_cursor(
            trade_ticks,
            since_trade_key,
            _trade_key,
        )
        payload["ticksAreSnapshot"] = False
    else:
        payload["ticksAreSnapshot"] = True

    if minute_reset:
        payload["minuteReset"] = True

    auction = payload.pop("auction", None)
    if isinstance(auction, dict):
        payload["auctionMeta"] = {
            "status": auction.get("status"),
            "updatedAt": auction.get("updatedAt"),
            "sourceTimestamp": auction.get("sourceTimestamp"),
            "cacheAgeMs": auction.get("cacheAgeMs"),
            "refreshMode": auction.get("refreshMode"),
        }

    payload["isDelta"] = True
    payload["payloadBytes"] = len(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    )
    return payload


def _cache_result_from_entry(
    entry: Any,
    *,
    age_seconds: float,
    stale: bool,
) -> detail.CacheResult:
    return detail.CacheResult(
        value=entry.value,
        cache_hit=True,
        cache_age_ms=max(int(age_seconds * 1000), 0),
        stale=stale,
        waited=False,
        source_timestamp=entry.source_timestamp,
        source_host=entry.source_host,
        upstream_latency_ms=0,
        updated_at=entry.updated_at,
    )


def _cached_ticks(security: dict[str, str]) -> tuple[list[dict[str, Any]], detail.CacheResult | None]:
    entry = runtime.cache.get(f"ticks:{security['code']}")
    if entry is None:
        return [], None
    age = max(monotonic() - entry.stored_at, 0.0)
    market_open = runtime._market_is_open()
    max_age = 30.0 if market_open else 12 * 60 * 60.0
    if age > max_age:
        return [], None
    result = _cache_result_from_entry(entry, age_seconds=age, stale=market_open and age > 1.0)
    return list(entry.value or [])[-20:], result


def _ensure_ticks_refresh(security: dict[str, str], cached: detail.CacheResult | None) -> None:
    market_open = runtime._market_is_open()
    should_refresh = cached is None or (market_open and cached.cache_age_ms > 900)
    key = f"ticks:{security['code']}"
    if should_refresh and key not in runtime.inflight:
        _schedule(runtime.ticks(security), f"ticks-prefetch:{security['code']}")


async def _fast_core_realtime(
    query: str,
    ndays: int,
    *,
    mark_hot: bool = True,
) -> dict[str, Any]:
    started = monotonic()
    security = _security_for_query(query) or await runtime.resolve(query)
    if mark_hot:
        runtime.hot_symbols[security["code"]] = monotonic()

    quote_task = asyncio.create_task(runtime.quote_raw(security))
    minute_task = asyncio.create_task(runtime.minute(security, ndays))
    quote_result, minute_result = await asyncio.gather(
        quote_task,
        minute_task,
        return_exceptions=True,
    )
    if isinstance(quote_result, Exception):
        raise detail.HTTPException(status_code=502, detail=f"realtime quote failed: {quote_result}")
    if isinstance(minute_result, Exception):
        raise detail.HTTPException(status_code=502, detail=f"realtime minute failed: {minute_result}")

    quote = runtime.parse_quote(quote_result.value, security)
    sell, buy, depth_meta = await runtime.depth(security, quote_result, quote)
    ticks, ticks_result = _cached_ticks(security)
    _ensure_ticks_refresh(security, ticks_result)

    warnings = list(depth_meta.get("depthWarnings") or [])
    if quote_result.stale:
        warnings.append("quote: stale_cache")
    if minute_result.stale:
        warnings.append(f"minute:{ndays}d: stale_cache")
    if ndays == 5 and "gtimg" in minute_result.source_host:
        warnings.append("minute:5d eastmoney_incomplete_using_tencent_real_history")
    if quote_result.waited or minute_result.waited:
        warnings.append("singleflight: waited")
    if ticks_result is None:
        warnings.append("trade_ticks: background_prefetch")
    elif ticks_result.stale:
        warnings.append("trade_ticks: stale_cache_refreshing")

    cache_results = [quote_result, minute_result] + ([ticks_result] if ticks_result else [])
    total_latency_ms = int((monotonic() - started) * 1000)
    payload: dict[str, Any] = {
        "provider": "crawl_eastmoney_fast_core",
        "query": query,
        "code": security["code"],
        "ndays": ndays,
        "sequence": time_ns(),
        "updatedAt": max(result.updated_at for result in cache_results if result),
        "sourceTimestamp": max(result.source_timestamp for result in cache_results if result),
        "cacheHit": all(result.cache_hit for result in cache_results if result),
        "cacheAgeMs": max(result.cache_age_ms for result in cache_results if result),
        "upstreamLatencyMs": max(result.upstream_latency_ms for result in cache_results if result),
        "totalLatencyMs": total_latency_ms,
        "sourceHost": quote_result.source_host,
        "quote": quote,
        "minutePoints": minute_result.value,
        "sellLevels": sell,
        "buyLevels": buy,
        **depth_meta,
        "tradeTicks": ticks,
        "tradeTicksIsDerived": False,
        "warnings": warnings,
    }
    _normalize_minute_contract(payload)
    if payload["minutePoints"] and not any(
        float(point.get("volume") or 0) > 0 for point in payload["minutePoints"]
    ):
        payload["warnings"].append("minute_volume: upstream returned no positive volume")
    payload["payloadBytes"] = len(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    )
    return payload


def _auction_window_active(now_cn: datetime | None = None) -> bool:
    value = now_cn or datetime.now(detail.CN_TZ)
    if value.weekday() >= 5:
        return False
    current = value.time()
    return (
        time(9, 14, 30) <= current <= time(9, 26, 0)
        or time(14, 56, 30) <= current <= time(15, 1, 0)
    )


def _cached_auction_result(
    security: dict[str, str],
) -> tuple[detail.CacheResult, str] | None:
    trade_date = runtime._latest_trade_date(security["code"])
    entry = runtime.cache.get(f"auction:v2:{trade_date}:{security['code']}")
    if entry is None:
        return None
    age = max(monotonic() - entry.stored_at, 0.0)
    result = _cache_result_from_entry(
        entry,
        age_seconds=age,
        stale=_auction_window_active() and age > 2.0,
    )
    return result, "stable-cache"


def _auction_payload_from_result(
    payload: dict[str, Any],
    auction_result: detail.CacheResult,
    refresh_mode: str,
) -> dict[str, Any]:
    parsed = auction_result.value
    open_points = list(parsed.get("openPoints") or [])
    close_points = list(parsed.get("closePoints") or [])
    standard_close = detail._real_close_points_from_minute(
        list(payload.get("minutePoints") or [])
    )
    source = str(parsed.get("source") or detail.EASTMONEY_AUCTION_SOURCE)
    source_url_type = str(
        parsed.get("sourceUrlType") or detail.EASTMONEY_AUCTION_SOURCE_URL_TYPE
    )
    if not close_points and standard_close:
        close_points = standard_close
        source = f"{source},{detail.EASTMONEY_AUCTION_SOURCE}"
        source_url_type = (
            f"{source_url_type}; {detail.STANDARD_CLOSE_SOURCE_URL_TYPE}"
        )
    return detail._build_auction_payload(
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


async def fast_realtime_payload(
    query: str,
    ndays: int,
    *,
    force_auction: bool = False,
    since_minute_key: str = "",
    since_trade_key: str = "",
    compact: bool = True,
) -> dict[str, Any]:
    started = monotonic()
    security = _security_for_query(query) or await runtime.resolve(query)
    core_task = asyncio.create_task(_fast_core_realtime(query, ndays))

    cached_auction = _cached_auction_result(security) if ndays == 1 else None
    should_refresh_auction = bool(
        ndays == 1
        and (force_auction or _auction_window_active() or cached_auction is None)
    )
    auction_task = (
        asyncio.create_task(detail.load_auction(security, force=force_auction))
        if should_refresh_auction
        else None
    )

    try:
        payload = await core_task
    except Exception:
        if auction_task is not None and not auction_task.done():
            auction_task.cancel()
            await asyncio.gather(auction_task, return_exceptions=True)
        raise

    if ndays != 1:
        finalized = detail._finalize_payload(
            payload,
            detail._unavailable_auction(
                "auction: only available for ndays=1",
                "not-applicable",
            ),
            started,
        )
    elif auction_task is None and cached_auction is not None:
        cached_result, refresh_mode = cached_auction
        finalized = detail._finalize_payload(
            payload,
            _auction_payload_from_result(payload, cached_result, refresh_mode),
            started,
            cached_result,
        )
    else:
        try:
            auction_result, refresh_mode = await asyncio.wait_for(
                asyncio.shield(auction_task),
                timeout=REALTIME_AUCTION_GRACE_SECONDS,
            )
        except asyncio.TimeoutError:
            assert auction_task is not None
            auction_task.add_done_callback(_consume_background)
            if cached_auction is not None:
                cached_result, _ = cached_auction
                auction = _auction_payload_from_result(
                    payload,
                    cached_result,
                    "cache-while-refresh",
                )
                finalized = detail._finalize_payload(payload, auction, started, cached_result)
            else:
                auction = detail._fallback_auction_from_realtime(
                    payload,
                    "auction: refreshing in background; core quote returned immediately",
                    "background-refresh",
                )
                finalized = detail._finalize_payload(payload, auction, started)
        except Exception as exc:
            if cached_auction is not None:
                cached_result, _ = cached_auction
                auction = _auction_payload_from_result(
                    payload,
                    cached_result,
                    "cache-after-error",
                )
                finalized = detail._finalize_payload(payload, auction, started, cached_result)
            else:
                auction = detail._fallback_auction_from_realtime(
                    payload,
                    f"auction: unavailable {type(exc).__name__}: {exc}",
                    "error-fallback",
                )
                finalized = detail._finalize_payload(payload, auction, started)
        else:
            auction = _auction_payload_from_result(payload, auction_result, refresh_mode)
            finalized = detail._finalize_payload(payload, auction, started, auction_result)

    return _apply_incremental_payload(
        finalized,
        ndays=ndays,
        since_minute_key=since_minute_key,
        since_trade_key=since_trade_key,
        compact=compact,
    )


def _fast_lite_detail(payload: dict[str, Any], query: str) -> dict[str, Any]:
    quote = dict(payload.get("quote") or {})
    name = str(quote.get("name") or quote.get("code") or query)
    code = str(quote.get("code") or query)
    price = str(quote.get("price") or "--")
    change_percent = str(quote.get("changePercent") or "--")
    result = dict(payload)
    result.update(
        {
            "provider": "async_realtime_fast_path",
            "mode": "lite",
            "dataSourceLabel": f"A股异步实时快路径 · {code}",
            "kLinePoints": [],
            "moneyFlow": {
                "mainInflow": "--",
                "superLargeOrder": "--",
                "largeOrder": "--",
                "mediumOrder": "--",
                "smallOrder": "--",
            },
            "fundamentals": legacy._fundamentals_from_quote(quote),
            "indices": [],
            "watchlist": [],
            "marketBoards": [],
            "aiSummary": f"{name} 当前价 {price}，涨跌幅 {change_percent}。",
        }
    )
    _normalize_minute_contract(result)
    return result


async def fast_detail_payload(
    query: str,
    mode: str,
    include_market: bool = False,
) -> dict[str, Any]:
    normalized_mode = "full" if mode == "full" else "lite"
    if normalized_mode == "lite" and not include_market:
        payload = await fast_realtime_payload(
            query,
            1,
            compact=False,
        )
        return _fast_lite_detail(payload, query)

    return await asyncio.to_thread(
        legacy._cached_response,
        "detail_market" if include_market else "detail",
        query,
        normalized_mode,
        lambda: legacy._build_detail_payload(
            query,
            normalized_mode,
            include_market,
        ),
    )


def _timed_json_response(payload: dict[str, Any]) -> Response:
    response = legacy._fast_json_response(payload)
    latency = int(payload.get("totalLatencyMs") or 0)
    response.headers["Server-Timing"] = f"stock;dur={latency}"
    response.headers["X-AI-Ledger-Stock-Path"] = str(
        payload.get("provider") or "fast-stock"
    )
    response.headers["X-AI-Ledger-Payload-Bytes"] = str(
        int(payload.get("payloadBytes") or 0)
    )
    return response


async def _background_prewarm() -> None:
    await asyncio.sleep(0.15)
    try:
        await _fast_core_realtime(DEFAULT_PREWARM_CODE, 1, mark_hot=False)
    except Exception as exc:
        LOGGER.warning("default stock prewarm failed: %s: %s", type(exc).__name__, exc)
    await asyncio.sleep(0.75)
    try:
        detail.market_home._start_market_home_background_refresh()
    except Exception as exc:
        LOGGER.warning("market home prewarm failed: %s: %s", type(exc).__name__, exc)


@app.on_event("startup")
async def _start_background_prewarm() -> None:
    _schedule(_background_prewarm(), "stock-background-prewarm")


detail._remove_get_routes(
    {
        REALTIME_PATH,
        DETAIL_PATH,
        CRAWL_DETAIL_PATH,
    }
)


@app.get(REALTIME_PATH, response_class=Response)
async def a_share_realtime_fast(
    query: str = Query(...),
    ndays: int = Query(1, ge=1, le=5),
    force_auction: bool = Query(False, alias="forceAuction"),
    since_minute_key: str = Query("", alias="sinceMinuteKey", max_length=64),
    since_trade_key: str = Query("", alias="sinceTradeKey", max_length=128),
    compact: bool = Query(True),
) -> Response:
    if ndays not in {1, 5}:
        raise detail.HTTPException(status_code=400, detail="ndays must be 1 or 5")
    return _timed_json_response(
        await fast_realtime_payload(
            query,
            ndays,
            force_auction=force_auction,
            since_minute_key=since_minute_key,
            since_trade_key=since_trade_key,
            compact=compact,
        )
    )


@app.get(DETAIL_PATH, response_class=Response)
@app.get(CRAWL_DETAIL_PATH, response_class=Response)
async def a_share_detail_fast(
    query: str = Query(...),
    mode: str = Query("lite"),
    include_market: bool = Query(False, alias="includeMarket"),
) -> Response:
    started = monotonic()
    payload = await fast_detail_payload(query, mode, include_market)
    payload["totalLatencyMs"] = int((monotonic() - started) * 1000)
    payload.setdefault("updatedAt", datetime.now(timezone.utc).isoformat())
    payload["payloadBytes"] = len(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    )
    return _timed_json_response(payload)
