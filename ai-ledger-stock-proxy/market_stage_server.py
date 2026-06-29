from __future__ import annotations

from datetime import datetime, timezone
from time import monotonic
from typing import Any

from fastapi import Response

import main as legacy
import market_home_server as home


app = home.app

INDICES_PATH = "/api/stock/a-share/market/indices"
BREADTH_PATH = "/api/stock/a-share/market/breadth"
DISCOVERY_PATH = "/api/stock/a-share/market/discovery"
STAGE_VERSION = "v1-priority-stages"


def _remove_get_routes(paths: set[str]) -> None:
    app.router.routes[:] = [
        route
        for route in app.router.routes
        if not (
            getattr(route, "path", None) in paths
            and "GET" in (getattr(route, "methods", None) or set())
        )
    ]


def _module_is_real(module: dict[str, Any] | None) -> bool:
    return str((module or {}).get("status", "")).lower() in home._REAL_STATUSES


def _module_cache_age_ms(module: dict[str, Any] | None) -> int:
    try:
        return max(int(float((module or {}).get("cacheAgeMs") or 0)), 0)
    except (TypeError, ValueError):
        return 0


def _stage_payload(
    stage: str,
    modules: dict[str, dict[str, Any]],
    started_at: float,
    warnings: list[str] | None = None,
) -> dict[str, Any]:
    real_count = sum(1 for module in modules.values() if _module_is_real(module))
    status = (
        "ok"
        if real_count == len(modules) and modules
        else "partial"
        if real_count > 0
        else "warming"
    )
    refresh_state = home._refresh_state_name()
    result_warnings = list(warnings or [])
    for name, module in modules.items():
        for warning in module.get("warnings") or []:
            result_warnings.append(f"{name}: {warning}")
    if status == "warming":
        result_warnings.append(f"market_stage:{stage}: waiting_for_background_refresh")
    elapsed_ms = int((monotonic() - started_at) * 1000)
    return {
        "status": status,
        "stage": stage,
        "stageVersion": STAGE_VERSION,
        "source": "eastmoney_public_json",
        "sourceUrlType": f"priority market stage {stage}",
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "cacheAgeMs": max((_module_cache_age_ms(module) for module in modules.values()), default=0),
        "isDerived": False,
        "refreshState": refresh_state,
        "buildLatencyMs": elapsed_ms,
        "warnings": result_warnings + [f"market_stage_{stage}_build_ms={elapsed_ms}"],
        **modules,
    }


def _cached_indices() -> dict[str, Any]:
    try:
        return home._cached_module(
            "market",
            "indices",
            "full-parallel",
            home._load_indices_parallel,
        )
    except Exception as exc:
        return home._cached_or_unavailable(
            "market",
            "indices",
            "full-parallel",
            f"indices:{type(exc).__name__}",
        )


def _cached_breadth() -> dict[str, Any]:
    return home._cached_or_unavailable(
        "market",
        "breadth",
        "v1",
        "marketBreadth",
    )


def _cached_discovery_modules() -> dict[str, dict[str, Any]]:
    modules: dict[str, dict[str, Any]] = {}
    for module_name, (query, _, _) in home._RANKING_SPECS.items():
        modules[module_name] = home._cached_or_unavailable(
            "ranking",
            query,
            "20",
            module_name,
        )
    modules["sectorHotRanking"] = home._cached_or_unavailable(
        "sectors",
        "industry",
        "20",
        "sectorHotRanking",
    )
    modules["popularityRanking"] = legacy._unavailable_module("popularity_ranking")
    modules["limitUpSummary"] = legacy._unavailable_module("limit_up_summary")
    modules["marketNews"] = legacy._unavailable_module("market_news")
    return modules


def _ensure_background_refresh(modules: dict[str, dict[str, Any]]) -> bool:
    if all(_module_is_real(module) for module in modules.values()):
        return False
    return home._start_market_home_background_refresh()


_remove_get_routes({INDICES_PATH, BREADTH_PATH, DISCOVERY_PATH})


@app.get(INDICES_PATH)
def a_share_market_indices(response: Response) -> dict[str, Any]:
    started_at = monotonic()
    modules = {"indices": _cached_indices()}
    background_started = home._start_market_home_background_refresh()
    payload = _stage_payload(
        "indices",
        modules,
        started_at,
        [
            "market_stage: full_background_refresh_started"
            if background_started
            else "market_stage: full_background_refresh_reused"
        ],
    )
    response.headers["X-Market-Stage"] = "indices"
    response.headers["X-Market-Stage-Version"] = STAGE_VERSION
    response.headers["Server-Timing"] = f"market-indices;dur={payload['buildLatencyMs']}"
    return payload


@app.get(BREADTH_PATH)
def a_share_market_breadth(response: Response) -> dict[str, Any]:
    started_at = monotonic()
    breadth = _cached_breadth()
    modules = {
        "marketBreadth": breadth,
        "sentiment": home._sentiment_from_breadth(breadth),
    }
    background_started = _ensure_background_refresh(modules)
    payload = _stage_payload(
        "breadth",
        modules,
        started_at,
        [
            "market_stage: background_refresh_started"
            if background_started
            else "market_stage: background_refresh_reused_or_ready"
        ],
    )
    response.headers["X-Market-Stage"] = "breadth"
    response.headers["X-Market-Stage-Version"] = STAGE_VERSION
    response.headers["Server-Timing"] = f"market-breadth;dur={payload['buildLatencyMs']}"
    return payload


@app.get(DISCOVERY_PATH)
def a_share_market_discovery(response: Response) -> dict[str, Any]:
    started_at = monotonic()
    modules = _cached_discovery_modules()
    refreshable = {
        name: module
        for name, module in modules.items()
        if name in home._REFRESHABLE_MODULES
    }
    background_started = _ensure_background_refresh(refreshable)
    payload = _stage_payload(
        "discovery",
        modules,
        started_at,
        [
            "market_stage: background_refresh_started"
            if background_started
            else "market_stage: background_refresh_reused_or_ready"
        ],
    )
    response.headers["X-Market-Stage"] = "discovery"
    response.headers["X-Market-Stage-Version"] = STAGE_VERSION
    response.headers["Server-Timing"] = f"market-discovery;dur={payload['buildLatencyMs']}"
    return payload
