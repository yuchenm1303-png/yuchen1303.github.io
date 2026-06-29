from __future__ import annotations

import asyncio
import logging
import os
from datetime import datetime, timezone
from time import monotonic
from typing import Any

import fast_stock_server as stock_server
import discussion_server  # noqa: F401  注册个股讨论与评论路由
import discussion_post_server  # noqa: F401  注册帖子正文按需路由
import hot_rank_server  # noqa: F401  注册实时热点榜路由
import index_detail_server  # noqa: F401  注册指数详情路由
import market_home_server
import market_kline_server  # noqa: F401  注册扩展历史K线路由
import sector_detail_server  # noqa: F401  注册板块详情与成分股路由


app = stock_server.app
LOGGER = logging.getLogger("ai-ledger-stock-proxy.production")
_PROCESS_STARTED_AT = monotonic()
_PROCESS_STARTED_ISO = datetime.now(timezone.utc).isoformat()
_SERVICE_VERSION = "0.9.2-refresh-coordination"
_HOT_TICK_INTERVAL_SECONDS = 0.9
_HOT_TICK_MIN_AGE_SECONDS = 0.72
_HOT_SYMBOL_TTL_SECONDS = 30.0
_hot_tick_task: asyncio.Task[None] | None = None
_hot_tick_semaphore = asyncio.Semaphore(4)


def _remove_get_routes(paths: set[str]) -> None:
    app.router.routes[:] = [
        route
        for route in app.router.routes
        if not (
            getattr(route, "path", None) in paths
            and "GET" in (getattr(route, "methods", None) or set())
        )
    ]


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


_remove_get_routes({"/health"})


@app.get("/health")
def health() -> dict[str, Any]:
    commit = os.getenv("RENDER_GIT_COMMIT") or os.getenv("GIT_COMMIT") or "unknown"
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
        "realtime": {
            **_safe_runtime_diagnostics(),
            "hotTradeTickWorker": {
                "running": _hot_tick_task is not None and not _hot_tick_task.done(),
                "intervalMs": int(_HOT_TICK_INTERVAL_SECONDS * 1000),
                "minRefreshAgeMs": int(_HOT_TICK_MIN_AGE_SECONDS * 1000),
                "activeSymbolTtlMs": int(_HOT_SYMBOL_TTL_SECONDS * 1000),
            },
        },
    }
