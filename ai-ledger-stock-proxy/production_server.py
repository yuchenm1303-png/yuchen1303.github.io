from __future__ import annotations

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
_PROCESS_STARTED_AT = monotonic()
_PROCESS_STARTED_ISO = datetime.now(timezone.utc).isoformat()
_SERVICE_VERSION = "0.9.0-nonblocking-market-home"


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
        "realtime": _safe_runtime_diagnostics(),
    }
