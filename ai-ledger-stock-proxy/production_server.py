from __future__ import annotations

import asyncio
import json
import logging
import os
from datetime import datetime, timezone
from time import monotonic
from typing import Any

from fastapi import Query
from fastapi.responses import Response

import fast_stock_server as stock_server
import discussion_server  # noqa: F401  注册个股讨论与评论路由
import discussion_post_server  # noqa: F401  注册帖子正文按需路由
import hot_rank_server  # noqa: F401  注册实时热点榜路由
import index_detail_server  # noqa: F401  注册指数详情路由
import index_compact_server  # noqa: F401  注册功能页三大指数独立报价与分时路由
import market_breadth_server
import market_home_server
import market_kline_server  # noqa: F401  注册扩展历史K线路由
import sector_detail_server  # noqa: F401  注册板块详情与成分股路由


MARKET_STAGE_IMPORT_ERROR = ""
try:
    import market_stage_server  # type: ignore  # noqa: F401
except Exception as exc:  # 生产入口必须优先保证核心行情服务可启动
    market_stage_server = None  # type: ignore[assignment]
    MARKET_STAGE_IMPORT_ERROR = f"{type(exc).__name__}: {exc}"


app = stock_server.app
LOGGER = logging.getLogger("ai-ledger-stock-proxy.production")
_PROCESS_STARTED_AT = monotonic()
_PROCESS_STARTED_ISO = datetime.now(timezone.utc).isoformat()
_SERVICE_VERSION = "0.9.12-single-encode-fast-slow"
_HOT_TICK_INTERVAL_SECONDS = 0.9
_HOT_TICK_MIN_AGE_SECONDS = 0.72
_HOT_SYMBOL_TTL_SECONDS = 30.0
_REALTIME_PATH = "/api/stock/a-share/realtime"
_DETAIL_PATH = "/api/stock/a-share/detail"
_CRAWL_DETAIL_PATH = "/api/stock/crawl/a-share/detail"
_STOCK_FULL_PATH = "/api/stock/a-share/stock/full"
_STAGE_PATHS = (
    "/api/stock/a-share/market/indices",
    "/api/stock/a-share/market/breadth",
    "/api/stock/a-share/market/discovery",
)
_hot_tick_task: asyncio.Task[None] | None = None
_hot_tick_semaphore = asyncio.Semaphore(4)


def _disable_index_compact_startup_warmup() -> None:
    """生产冷启动不主动抓三大指数，避免和首个个股请求争抢单实例资源。"""
    warm_handler = getattr(index_compact_server, "_warm_tools_index_hero", None)
    stop_handler = getattr(index_compact_server, "_stop_tools_index_hero_warmup", None)
    if warm_handler is not None:
        app.router.on_startup[:] = [
            handler for handler in app.router.on_startup if handler is not warm_handler
        ]
    if stop_handler is not None:
        app.router.on_shutdown[:] = [
            handler for handler in app.router.on_shutdown if handler is not stop_handler
        ]


_disable_index_compact_startup_warmup()


def _remove_get_routes(paths: set[str]) -> None:
    app.router.routes[:] = [
        route
        for route in app.router.routes
        if not (
            getattr(route, "path", None) in paths
            and "GET" in (getattr(route, "methods", None) or set())
        )
    ]


def _fallback_market_stage(stage: str) -> dict[str, Any]:
    payload = market_home_server._load_market_home_cached()
    fallback = dict(payload)
    fallback["stage"] = stage
    fallback["stageVersion"] = "fallback-market-home"
    fallback["warnings"] = list(fallback.get("warnings") or []) + [
        "market_stage: degraded_to_market_home",
        f"market_stage_import_error: {MARKET_STAGE_IMPORT_ERROR}",
    ]
    return fallback


if market_stage_server is None:
    _remove_get_routes(set(_STAGE_PATHS))

    @app.get(_STAGE_PATHS[0])
    def fallback_market_indices() -> dict[str, Any]:
        return _fallback_market_stage("indices")

    @app.get(_STAGE_PATHS[1])
    def fallback_market_breadth() -> dict[str, Any]:
        return _fallback_market_stage("breadth")

    @app.get(_STAGE_PATHS[2])
    def fallback_market_discovery() -> dict[str, Any]:
        return _fallback_market_stage("discovery")

    LOGGER.error(
        "market stage module unavailable; core stock service started with fallback routes: %s",
        MARKET_STAGE_IMPORT_ERROR,
    )


def _safe_runtime_diagnostics() -> dict[str, Any]:
    try:
        diagnostics = stock_server.runtime.diagnostics()
        return diagnostics if isinstance(diagnostics, dict) else {}
    except Exception as exc:
        return {"status": "unavailable", "error": f"{type(exc).__name__}: {exc}"}


def _should_refresh_hot_ticks(code: str, now: float | None = None) -> bool:
    key = f"ticks:{code}"
    if key in stock_server.runtime.inflight:
        return False
    entry = stock_server.runtime.cache.get(key)
    if entry is None:
        return True
    age_seconds = max((now if now is not None else monotonic()) - entry.stored_at, 0.0)
    return age_seconds >= _HOT_TICK_MIN_AGE_SECONDS


async def _refresh_hot_trade_ticks(code: str) -> None:
    async with _hot_tick_semaphore:
        now = monotonic()
        if not _should_refresh_hot_ticks(code, now):
            return
        security = {
            "code": code,
            "name": code,
            "secid": stock_server.detail._secid(code),
            "resolveSource": "hot-tick-code",
        }
        await stock_server.runtime.ticks(security, force=True)


async def _hot_trade_tick_loop() -> None:
    backoff = _HOT_TICK_INTERVAL_SECONDS
    while True:
        try:
            await asyncio.sleep(backoff)
            now = monotonic()
            expired_codes = [
                code
                for code, last_seen in stock_server.runtime.hot_symbols.items()
                if now - last_seen > _HOT_SYMBOL_TTL_SECONDS
            ]
            for code in expired_codes:
                stock_server.runtime.hot_symbols.pop(code, None)
            active_codes = list(stock_server.runtime.hot_symbols)
            if not active_codes:
                backoff = _HOT_TICK_INTERVAL_SECONDS
                continue
            if not stock_server.runtime._market_is_open():
                backoff = 5.0
                continue

            outcomes = await asyncio.gather(
                *(_refresh_hot_trade_ticks(code) for code in active_codes),
                return_exceptions=True,
            )
            failures = [item for item in outcomes if isinstance(item, Exception)]
            if failures:
                LOGGER.warning(
                    "hot trade tick refresh partial failure: %d/%d",
                    len(failures),
                    len(active_codes),
                )
                backoff = min(4.0, _HOT_TICK_INTERVAL_SECONDS + len(failures))
            else:
                backoff = _HOT_TICK_INTERVAL_SECONDS
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            LOGGER.warning(
                "hot trade tick loop failed: %s: %s",
                type(exc).__name__,
                exc,
            )
            backoff = min(max(backoff * 2.0, 1.8), 5.0)


@app.on_event("startup")
async def _start_hot_trade_tick_loop() -> None:
    global _hot_tick_task
    if _hot_tick_task is None or _hot_tick_task.done():
        _hot_tick_task = asyncio.create_task(
            _hot_trade_tick_loop(),
            name="a-share-hot-trade-ticks",
        )


@app.on_event("shutdown")
async def _stop_hot_trade_tick_loop() -> None:
    global _hot_tick_task
    if _hot_tick_task is None:
        return
    _hot_tick_task.cancel()
    await asyncio.gather(_hot_tick_task, return_exceptions=True)
    _hot_tick_task = None


def _json_response_once(payload: dict[str, Any], *, path_label: str | None = None) -> Response:
    """最终出口只序列化一次；准确字节数放响应头，避免为统计重复遍历大 JSON。"""
    body = dict(payload)
    body.pop("payloadBytes", None)
    content = json.dumps(
        body,
        ensure_ascii=False,
        separators=(",", ":"),
        allow_nan=False,
    ).encode("utf-8")
    response = Response(content=content, media_type="application/json")
    latency = int(body.get("totalLatencyMs") or 0)
    response.headers["Server-Timing"] = f"stock;dur={latency}"
    response.headers["X-AI-Ledger-Stock-Path"] = path_label or str(
        body.get("provider") or "production-stock"
    )
    response.headers["X-AI-Ledger-Payload-Bytes"] = str(len(content))
    return response


def _unavailable_slow_module(name: str) -> dict[str, Any]:
    return stock_server.legacy._module_payload(
        status="unavailable",
        source="production_fast_slow_gate",
        source_url_type="no verified upstream; zero-network response",
        warnings=[f"{name}: unavailable; upstream request skipped"],
    )


def _fast_empty_slow_payload(query: str) -> dict[str, Any]:
    normalized = query.strip()
    code = stock_server.detail._code_from_query(normalized) or normalized
    return {
        "status": "partial",
        "provider": "production_zero_network_slow_path",
        "source": "production_fast_slow_gate",
        "sourceUrlType": "zero-network slow module capability response",
        "query": normalized,
        "code": code,
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "cacheAgeMs": 0,
        "isDerived": False,
        "warnings": [
            "slow modules have no verified real source; upstream resolution and duplicate quote fetch skipped"
        ],
        "profile": _unavailable_slow_module("profile"),
        "financialsSummary": _unavailable_slow_module("financials"),
        "capitalSummary": _unavailable_slow_module("capital_stock"),
        "popularity": _unavailable_slow_module("popularity"),
        "announcements": _unavailable_slow_module("announcements"),
        "news": _unavailable_slow_module("stock_news"),
        "research": _unavailable_slow_module("research"),
        "performanceForecast": _unavailable_slow_module("performance_forecast"),
        "shareholders": _unavailable_slow_module("shareholders"),
        "unlocks": _unavailable_slow_module("unlocks"),
        "dividends": _unavailable_slow_module("dividends"),
    }


_remove_get_routes(
    {
        "/health",
        _REALTIME_PATH,
        _DETAIL_PATH,
        _CRAWL_DETAIL_PATH,
        _STOCK_FULL_PATH,
    }
)


@app.get(_REALTIME_PATH, response_class=Response)
async def a_share_realtime_production(
    query: str = Query(...),
    ndays: int = Query(1, ge=1, le=5),
    force_auction: bool = Query(False, alias="forceAuction"),
    since_minute_key: str = Query("", alias="sinceMinuteKey", max_length=64),
    since_trade_key: str = Query("", alias="sinceTradeKey", max_length=128),
    compact: bool = Query(True),
) -> Response:
    if ndays not in {1, 5}:
        raise stock_server.detail.HTTPException(status_code=400, detail="ndays must be 1 or 5")
    payload = await stock_server.fast_realtime_payload(
        query,
        ndays,
        force_auction=force_auction,
        since_minute_key=since_minute_key,
        since_trade_key=since_trade_key,
        compact=compact,
    )
    return _json_response_once(payload)


@app.get(_DETAIL_PATH, response_class=Response)
@app.get(_CRAWL_DETAIL_PATH, response_class=Response)
async def a_share_detail_production(
    query: str = Query(...),
    mode: str = Query("lite"),
    include_market: bool = Query(False, alias="includeMarket"),
) -> Response:
    started = monotonic()
    payload = await stock_server.fast_detail_payload(query, mode, include_market)
    payload["totalLatencyMs"] = int((monotonic() - started) * 1000)
    payload.setdefault("updatedAt", datetime.now(timezone.utc).isoformat())
    return _json_response_once(payload)


@app.get(_STOCK_FULL_PATH, response_class=Response)
def a_share_stock_full_production(query: str = Query(...)) -> Response:
    return _json_response_once(
        _fast_empty_slow_payload(query),
        path_label="production_zero_network_slow_path",
    )


@app.get("/health")
def health() -> dict[str, Any]:
    commit = os.getenv("RENDER_GIT_COMMIT") or os.getenv("GIT_COMMIT") or "unknown"
    stage_available = market_stage_server is not None
    return {
        "ok": True,
        "status": "ok",
        "service": "ai-ledger-stock-proxy",
        "version": _SERVICE_VERSION,
        "commit": commit,
        "startedAt": _PROCESS_STARTED_ISO,
        "uptimeSeconds": round(monotonic() - _PROCESS_STARTED_AT, 3),
        "dataSource": "eastmoney public json",
        "cacheSize": len(stock_server.legacy._cache),
        "marketHome": market_home_server.market_home_diagnostics(),
        "marketBreadth": {
            **market_breadth_server.diagnostics(),
            "cacheVersion": market_breadth_server.MARKET_BREADTH_CACHE_VERSION,
            "freshSeconds": market_breadth_server.MARKET_BREADTH_FRESH_SECONDS,
            "minimumCoverageRate": market_breadth_server.MARKET_BREADTH_MIN_COVERAGE,
            "minimumRows": market_breadth_server.MARKET_BREADTH_MIN_ROWS,
        },
        "marketStages": {
            "available": stage_available,
            "degraded": not stage_available,
            "version": (
                getattr(market_stage_server, "STAGE_VERSION", "fallback-market-home")
                if stage_available
                else "fallback-market-home"
            ),
            "paths": list(_STAGE_PATHS),
            "importError": MARKET_STAGE_IMPORT_ERROR,
        },
        "indexCompact": {
            "path": index_compact_server.INDEX_COMPACT_PATH,
            "batchPath": index_compact_server.INDEX_COMPACT_BATCH_PATH,
            "quotesPath": index_compact_server.INDEX_COMPACT_QUOTES_PATH,
            "trendPath": index_compact_server.INDEX_COMPACT_TREND_PATH,
            "version": index_compact_server.INDEX_COMPACT_CACHE_VERSION,
            "batchCodes": list(index_compact_server.INDEX_COMPACT_BATCH_CODES),
            "startupWarmup": False,
        },
        "responsePipeline": {
            "singleEncode": True,
            "payloadBytesHeader": True,
            "slowPathZeroNetwork": True,
        },
        "realtime": {
            **_safe_runtime_diagnostics(),
            "tradeTickWindowLimit": stock_server.REALTIME_TICK_WINDOW,
            "hotTradeTickWorker": {
                "running": _hot_tick_task is not None and not _hot_tick_task.done(),
                "intervalMs": int(_HOT_TICK_INTERVAL_SECONDS * 1000),
                "minRefreshAgeMs": int(_HOT_TICK_MIN_AGE_SECONDS * 1000),
                "activeSymbolTtlMs": int(_HOT_SYMBOL_TTL_SECONDS * 1000),
            },
        },
    }
