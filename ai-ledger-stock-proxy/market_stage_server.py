from __future__ import annotations

from datetime import datetime, timezone
from time import monotonic
from typing import Any, Callable

from fastapi import Response

import main as legacy
import market_home_server as home


app = home.app

INDICES_PATH = "/api/stock/a-share/market/indices"
BREADTH_PATH = "/api/stock/a-share/market/breadth"
DISCOVERY_PATH = "/api/stock/a-share/market/discovery"
STAGE_VERSION = "v4-priority-stages"
INDICES_REFRESH_SECONDS = 6.0
BREADTH_REFRESH_SECONDS = 10.0
DISCOVERY_REFRESH_SECONDS = 22.0


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


def _cache_is_fresh(
    kind: str,
    query: str,
    mode: str,
    max_age_seconds: float,
) -> bool:
    return legacy._cache_get(
        legacy._cache_key(kind, query, mode),
        max_age_seconds,
    ) is not None


def _cached_stage_module(
    kind: str,
    query: str,
    mode: str,
    name: str,
    fresh_seconds: float,
    builder: Callable[[], dict[str, Any]] | None = None,
) -> dict[str, Any]:
    key = legacy._cache_key(kind, query, mode)
    fresh = legacy._cache_get(key, fresh_seconds)
    if fresh is not None:
        payload, age = fresh
        return home._mark_cached_module(payload, age, stale=False)

    if builder is not None:
        try:
            payload = builder()
        except Exception:
            stale = legacy._cache_get(key, legacy.STALE_CACHE_SECONDS)
            if stale is not None:
                payload, age = stale
                return home._mark_cached_module(payload, age, stale=True)
            raise
        if legacy._payload_has_real_items(payload):
            legacy._cache_put(key, payload)
            return payload

    stale = legacy._cache_get(key, legacy.STALE_CACHE_SECONDS)
    if stale is not None:
        payload, age = stale
        return home._mark_cached_module(payload, age, stale=True)
    return home._module_unavailable(name, "waiting_for_background_refresh")


def _breadth_refresh_due() -> bool:
    return not _cache_is_fresh(
        "market",
        "breadth",
        "v1",
        BREADTH_REFRESH_SECONDS,
    )


def _discovery_refresh_due() -> bool:
    if not _cache_is_fresh(
        "sectors",
        "industry",
        "20",
        DISCOVERY_REFRESH_SECONDS,
    ):
        return True
    return any(
        not _cache_is_fresh(
            "ranking",
            query,
            "20",
            DISCOVERY_REFRESH_SECONDS,
        )
        for query, _, _ in home._RANKING_SPECS.values()
    )


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
        "refreshState": home._refresh_state_name(),
        "buildLatencyMs": elapsed_ms,
        "warnings": result_warnings + [f"market_stage_{stage}_build_ms={elapsed_ms}"],
        **modules,
    }


def _cached_indices() -> dict[str, Any]:
    return _cached_stage_module(
        "market",
        "indices",
        "full-parallel",
        "indices",
        INDICES_REFRESH_SECONDS,
        builder=home._load_indices_parallel,
    )


def _cached_breadth() -> dict[str, Any]:
    return _cached_stage_module(
        "market",
        "breadth",
        "v1",
        "marketBreadth",
        BREADTH_REFRESH_SECONDS,
    )


def _cached_discovery_modules() -> dict[str, dict[str, Any]]:
    modules: dict[str, dict[str, Any]] = {}
    for module_name, (query, _, _) in home._RANKING_SPECS.items():
        modules[module_name] = _cached_stage_module(
            "ranking",
            query,
            "20",
            module_name,
            DISCOVERY_REFRESH_SECONDS,
        )
    modules["sectorHotRanking"] = _cached_stage_module(
        "sectors",
        "industry",
        "20",
        "sectorHotRanking",
        DISCOVERY_REFRESH_SECONDS,
    )
    return modules


def _start_background_if_due(refresh_due: bool) -> tuple[bool, str]:
    if not refresh_due:
        return False, "market_stage: deferred_modules_fresh"
    started = home._start_market_home_background_refresh()
    return (
        started,
        "market_stage: background_refresh_started"
        if started
        else "market_stage: background_refresh_reused_or_cooling",
    )


_remove_get_routes({INDICES_PATH, BREADTH_PATH, DISCOVERY_PATH})


@app.get(INDICES_PATH)
def a_share_market_indices(response: Response) -> dict[str, Any]:
    started_at = monotonic()
    modules = {"indices": _cached_indices()}
    _, refresh_warning = _start_background_if_due(
        _breadth_refresh_due() or _discovery_refresh_due()
    )
    payload = _stage_payload("indices", modules, started_at, [refresh_warning])
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
    _, refresh_warning = _start_background_if_due(_breadth_refresh_due())
    payload = _stage_payload("breadth", modules, started_at, [refresh_warning])
    response.headers["X-Market-Stage"] = "breadth"
    response.headers["X-Market-Stage-Version"] = STAGE_VERSION
    response.headers["Server-Timing"] = f"market-breadth;dur={payload['buildLatencyMs']}"
    return payload


@app.get(DISCOVERY_PATH)
def a_share_market_discovery(response: Response) -> dict[str, Any]:
    started_at = monotonic()
    modules = _cached_discovery_modules()
    _, refresh_warning = _start_background_if_due(_discovery_refresh_due())
    payload = _stage_payload("discovery", modules, started_at, [refresh_warning])
    response.headers["X-Market-Stage"] = "discovery"
    response.headers["X-Market-Stage-Version"] = STAGE_VERSION
    response.headers["Server-Timing"] = f"market-discovery;dur={payload['buildLatencyMs']}"
    return payload
