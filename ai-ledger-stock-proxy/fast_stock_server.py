from __future__ import annotations

import asyncio
import json
import logging
from datetime import datetime, timezone
from time import monotonic
from typing import Any

from fastapi import Query
from fastapi.responses import Response

import stock_detail_server as detail


app = detail.app
legacy = detail.legacy
runtime = detail.runtime
LOGGER = logging.getLogger("ai-ledger-stock-proxy.fast-stock")

REALTIME_PATH = "/api/stock/a-share/realtime"
DETAIL_PATH = "/api/stock/a-share/detail"
CRAWL_DETAIL_PATH = "/api/stock/crawl/a-share/detail"
REALTIME_AUCTION_GRACE_SECONDS = 0.045


def _consume_background(task: asyncio.Task[Any]) -> None:
    try:
        task.result()
    except asyncio.CancelledError:
        pass
    except Exception as exc:
        LOGGER.warning(
            "background auction refresh failed: %s: %s",
            type(exc).__name__,
            exc,
        )


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
    return str(point.get("time") or point.get("timestamp") or "").strip()


def _latest_cursor(points: list[dict[str, Any]], key_fn) -> str:
    for point in reversed(points):
        key = key_fn(point)
        if key:
            return key
    return ""


def _cursor_date(value: str) -> str:
    text = value.strip()
    if len(text) >= 10 and text[4:5] == "-" and text[7:8] == "-":
        return text[:10]
    return ""


def _apply_incremental_payload(
    payload: dict[str, Any],
    *,
    ndays: int,
    since_minute_key: str,
    since_trade_key: str,
    compact: bool,
) -> dict[str, Any]:
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
        minute_delta = [
            point for point in minute_points if _minute_key(point) >= since_minute_key
        ]
        payload.pop("minutePoints", None)
        payload["minuteDelta"] = minute_delta
        payload["minuteIsSnapshot"] = False
    else:
        payload["minuteIsSnapshot"] = True

    if since_trade_key:
        tick_delta = [
            point for point in trade_ticks if _trade_key(point) >= since_trade_key
        ]
        payload.pop("tradeTicks", None)
        payload["newTradeTicks"] = tick_delta
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
    security = _security_for_query(query)
    if security is None:
        security = await runtime.resolve(query)

    core_task = asyncio.create_task(runtime.realtime(query, ndays))
    auction_task = (
        asyncio.create_task(detail.load_auction(security, force=force_auction))
        if ndays == 1
        else None
    )
    try:
        payload = await core_task
    except Exception:
        if auction_task is not None and not auction_task.done():
            auction_task.cancel()
            await asyncio.gather(auction_task, return_exceptions=True)
        raise

    if auction_task is None:
        auction = detail._unavailable_auction(
            "auction: only available for ndays=1",
            "not-applicable",
        )
        finalized = detail._finalize_payload(payload, auction, started)
        return _apply_incremental_payload(
            finalized,
            ndays=ndays,
            since_minute_key=since_minute_key,
            since_trade_key=since_trade_key,
            compact=compact,
        )

    try:
        auction_result, refresh_mode = await asyncio.wait_for(
            asyncio.shield(auction_task),
            timeout=REALTIME_AUCTION_GRACE_SECONDS,
        )
    except asyncio.TimeoutError:
        auction_task.add_done_callback(_consume_background)
        auction = detail._fallback_auction_from_realtime(
            payload,
            "auction: refreshing in background; core quote returned immediately",
            "background-refresh",
        )
        finalized = detail._finalize_payload(payload, auction, started)
    except Exception as exc:
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
    return result


async def fast_detail_payload(
    query: str,
    mode: str,
    include_market: bool = False,
) -> dict[str, Any]:
    normalized_mode = "full" if mode == "full" else "lite"
    if normalized_mode == "lite" and not include_market:
        payload = await runtime.realtime(query, 1)
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
    since_minute_key: str = Query("", alias="sinceMinuteKey", max_length=32),
    since_trade_key: str = Query("", alias="sinceTradeKey", max_length=24),
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
