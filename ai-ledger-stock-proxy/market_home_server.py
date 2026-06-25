from __future__ import annotations

import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
from copy import deepcopy
from datetime import datetime, timezone
from threading import Lock, Thread
from time import monotonic
from typing import Any, Callable

import httpx

import main as legacy

app = legacy.app

LOGGER = logging.getLogger("ai-ledger-stock-proxy.market-home")
MARKET_HOME_PATH = "/api/stock/a-share/market/home"
MARKET_HOME_CACHE_VERSION = "v3-light-full"
LIGHT_HOME_BUDGET_SECONDS = 5.5
FULL_REFRESH_WORKERS = 4
EASTMONEY_ULIST_URLS = [
    "https://push2.eastmoney.com/api/qt/ulist.np/get",
    "https://push2delay.eastmoney.com/api/qt/ulist.np/get",
    "https://push2his.eastmoney.com/api/qt/ulist.np/get",
]

_market_home_lock = Lock()
_market_home_refresh_lock = Lock()
_client_lock = Lock()
_shared_client: httpx.Client | None = None
_home_refresh_running = False

_INDEX_SECURITIES = [
    {"name": "上证指数", "code": "000001", "secid": "1.000001"},
    {"name": "深证成指", "code": "399001", "secid": "0.399001"},
    {"name": "创业板指", "code": "399006", "secid": "0.399006"},
    {"name": "沪深300", "code": "000300", "secid": "1.000300"},
    {"name": "科创50", "code": "000688", "secid": "1.000688"},
    {"name": "中证A500", "code": "000510", "secid": "1.000510"},
    {"name": "上证50", "code": "000016", "secid": "1.000016"},
    {"name": "中证500", "code": "000905", "secid": "1.000905"},
    {"name": "中证1000", "code": "000852", "secid": "1.000852"},
    {"name": "北证50", "code": "899050", "secid": "0.899050"},
]

_REAL_STATUSES = {"ok", "partial", "stale"}
_CRITICAL_MODULES = {"indices", "marketBreadth", "gainers", "losers", "sectorHotRanking"}


def _remove_legacy_market_home_route() -> None:
    app.router.routes[:] = [
        route
        for route in app.router.routes
        if not (
            getattr(route, "path", None) == MARKET_HOME_PATH
            and "GET" in (getattr(route, "methods", None) or set())
        )
    ]


def _module_unavailable(name: str, warning: str) -> dict[str, Any]:
    return legacy._module_payload(
        status="unavailable",
        source="eastmoney_public_json",
        source_url_type="isolated market-home module",
        warnings=[f"{name}: {warning}"],
    )


def _get_shared_client() -> httpx.Client:
    global _shared_client
    with _client_lock:
        if _shared_client is None:
            _shared_client = httpx.Client(
                timeout=httpx.Timeout(3.6, connect=1.2),
                limits=httpx.Limits(
                    max_connections=20,
                    max_keepalive_connections=10,
                    keepalive_expiry=20.0,
                ),
                headers={
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    "AppleWebKit/537.36 Chrome/125 Safari/537.36",
                    "Referer": "https://quote.eastmoney.com/",
                    "Accept": "application/json, text/plain, */*",
                    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
                },
            )
        return _shared_client


@app.on_event("shutdown")
def _close_market_home_client() -> None:
    global _shared_client
    with _client_lock:
        if _shared_client is not None:
            _shared_client.close()
            _shared_client = None


def _mark_cached_module(
    payload: dict[str, Any],
    age_seconds: int,
    stale: bool,
) -> dict[str, Any]:
    cached = deepcopy(payload)
    cached["cacheAgeMs"] = max(age_seconds, 0) * 1000
    if stale and str(cached.get("status", "")).lower() in {"ok", "partial"}:
        cached["status"] = "stale"
    cache_label = "stale" if stale else "hit"
    cached["warnings"] = list(cached.get("warnings") or []) + [
        f"module_cache: {cache_label} age={age_seconds}s"
    ]
    return cached


def _cached_module(
    kind: str,
    query: str,
    mode: str,
    builder: Callable[[], dict[str, Any]],
) -> dict[str, Any]:
    key = legacy._cache_key(kind, query, mode)
    fresh = legacy._cache_get(key, legacy.FAST_CACHE_SECONDS)
    if fresh is not None:
        payload, age = fresh
        return _mark_cached_module(payload, age, stale=False)

    try:
        payload = builder()
    except Exception:
        stale = legacy._cache_get(key, legacy.STALE_CACHE_SECONDS)
        if stale is not None:
            payload, age = stale
            return _mark_cached_module(payload, age, stale=True)
        raise

    if not legacy._payload_has_real_items(payload):
        stale = legacy._cache_get(key, legacy.STALE_CACHE_SECONDS)
        if stale is not None:
            stale_payload, age = stale
            cached = _mark_cached_module(stale_payload, age, stale=True)
            cached["warnings"] = list(cached.get("warnings") or []) + [
                "module_cache: stale_used_because_builder_returned_no_real_items"
            ]
            return cached
        return payload

    legacy._cache_put(key, payload)
    return payload


def _load_indices_parallel() -> dict[str, Any]:
    started_at = monotonic()
    warnings: list[str] = []
    secids = ",".join(index["secid"] for index in _INDEX_SECURITIES)
    params = {
        "secids": secids,
        "fields": "f12,f14,f2,f3,f4,f5,f6,f15,f16,f17,f18",
        "fltt": "2",
    }
    raw = legacy._eastmoney_get_first(
        _get_shared_client(),
        EASTMONEY_ULIST_URLS,
        params,
        "indices_batch",
        warnings,
    )
    diff = list((raw.get("data") or {}).get("diff") or [])
    by_code = {str(item.get("f12") or ""): item for item in diff}
    items: list[dict[str, Any]] = []
    for index in _INDEX_SECURITIES:
        item = by_code.get(index["code"])
        if not item:
            warnings.append(f"index_{index['code']}_missing_from_batch")
            continue
        items.append(
            {
                "code": index["code"],
                "name": legacy._safe_str(item.get("f14"), index["name"]),
                "price": legacy._format_price(item.get("f2")),
                "changeAmount": legacy._format_signed(item.get("f4")),
                "changePercent": legacy._format_percent(item.get("f3")),
                "open": legacy._format_price(item.get("f17")),
                "high": legacy._format_price(item.get("f15")),
                "low": legacy._format_price(item.get("f16")),
                "previousClose": legacy._safe_float(item.get("f18")),
                "amount": legacy._format_cn_money(item.get("f6")),
                "volume": legacy._format_lots(item.get("f5")),
                "updatedAt": datetime.now(timezone.utc).isoformat(),
            }
        )
    order = {
        item["code"]: position
        for position, item in enumerate(_INDEX_SECURITIES)
    }
    items.sort(key=lambda item: order.get(str(item.get("code", "")), len(order)))
    status = (
        "ok"
        if len(items) == len(_INDEX_SECURITIES)
        else "partial"
        if items
        else "unavailable"
    )
    warnings.append(
        f"indices_parallel_build_ms={int((monotonic() - started_at) * 1000)}"
    )
    return legacy._module_payload(
        status=status,
        source="eastmoney_quote",
        source_url_type="qt/ulist.np/get batch",
        items=items,
        warnings=warnings,
    )


def _sentiment_from_breadth(breadth: dict[str, Any]) -> dict[str, Any]:
    breadth_status = str(breadth.get("status", "unavailable")).lower()
    raw_items = breadth.get("items")
    if breadth_status not in _REAL_STATUSES or not isinstance(raw_items, dict):
        return _module_unavailable("sentiment", "market breadth unavailable")

    items = dict(raw_items)
    red_rate = legacy._safe_float(items.get("redRate"))
    limit_score = min(
        legacy._safe_float(items.get("limitUpCount")) * 1.5,
        25.0,
    )
    temperature = max(0.0, min(100.0, red_rate * 0.75 + limit_score))
    items.update(
        {
            "sentimentTemperature": round(temperature, 2),
            "sentimentLevel": (
                "hot"
                if temperature >= 70
                else "warm"
                if temperature >= 55
                else "cold"
                if temperature < 35
                else "neutral"
            ),
            "formula": "redRate * 0.75 + min(limitUpCount * 1.5, 25)",
        }
    )
    return legacy._module_payload(
        status=breadth_status,
        source=str(breadth.get("source") or "eastmoney_clist"),
        source_url_type="derived once from shared market breadth",
        items=items,
        is_derived=True,
        warnings=list(breadth.get("warnings") or [])
        + ["sentiment: derived_from_shared_real_breadth"],
        cache_age_ms=int(legacy._safe_float(breadth.get("cacheAgeMs"))),
    )


def _load_market_module(
    name: str,
    builder: Callable[[], dict[str, Any]],
) -> dict[str, Any]:
    try:
        payload = builder()
        if not isinstance(payload, dict):
            raise ValueError("module payload is not an object")
        return payload
    except Exception as exc:
        return _module_unavailable(name, f"{type(exc).__name__}: {exc}")


def _cached_or_unavailable(
    kind: str,
    query: str,
    mode: str,
    name: str,
) -> dict[str, Any]:
    cached = legacy._cache_get(
        legacy._cache_key(kind, query, mode),
        legacy.STALE_CACHE_SECONDS,
    )
    if cached is not None:
        payload, age = cached
        return _mark_cached_module(payload, age, stale=True)
    return _module_unavailable(name, "waiting_for_background_full_refresh")


def _ranking_builder(type_name: str) -> Callable[[], dict[str, Any]]:
    return lambda: _cached_module(
        "ranking",
        type_name,
        "20",
        lambda: legacy._load_ranking(type_name, 20),
    )


def _full_module_builders() -> dict[str, Callable[[], dict[str, Any]]]:
    return {
        "indices": lambda: _cached_module(
            "market",
            "indices",
            "full-parallel",
            _load_indices_parallel,
        ),
        "marketBreadth": lambda: _cached_module(
            "market",
            "breadth",
            "v1",
            legacy._load_market_breadth,
        ),
        "gainers": _ranking_builder("gainers"),
        "losers": _ranking_builder("losers"),
        "amountRanking": _ranking_builder("amount"),
        "turnoverRanking": _ranking_builder("turnover"),
        "volumeRatioRanking": _ranking_builder("volume_ratio"),
        "speedRanking": _ranking_builder("speed"),
        "mainInflowRanking": _ranking_builder("main_inflow"),
        "mainOutflowRanking": _ranking_builder("main_outflow"),
        "sectorHotRanking": lambda: _cached_module(
            "sectors",
            "industry",
            "20",
            lambda: legacy._load_sectors("industry", 20),
        ),
    }


def _assemble_market_home(
    modules: dict[str, dict[str, Any]],
    started_at: float,
    build_kind: str,
    warnings: list[str] | None = None,
) -> dict[str, Any]:
    result_warnings = list(warnings or [])
    breadth = modules.get("marketBreadth") or _module_unavailable(
        "marketBreadth",
        "module missing",
    )
    modules["sentiment"] = _sentiment_from_breadth(breadth)
    modules["popularityRanking"] = legacy._load_ranking("popularity", 50)
    modules["limitUpSummary"] = legacy._unavailable_module("limit_up_summary")
    modules["marketNews"] = legacy._unavailable_module("market_news")

    critical_real = sum(
        1
        for name in _CRITICAL_MODULES
        if str((modules.get(name) or {}).get("status", "")).lower()
        in _REAL_STATUSES
    )
    status = (
        "ok"
        if critical_real == len(_CRITICAL_MODULES)
        else "partial"
        if critical_real > 0
        else "unavailable"
    )
    for name, module in modules.items():
        for warning in module.get("warnings") or []:
            result_warnings.append(f"{name}: {warning}")

    return {
        "status": status,
        "source": "eastmoney_public_json",
        "sourceUrlType": (
            "lightweight market endpoint"
            if build_kind == "light"
            else "background full market endpoint"
        ),
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "cacheAgeMs": 0,
        "isDerived": False,
        "warnings": result_warnings
        + [
            f"market_home_{build_kind}_build_ms="
            f"{int((monotonic() - started_at) * 1000)}"
        ],
        **modules,
    }


def _build_market_home_light() -> dict[str, Any]:
    started_at = monotonic()
    modules: dict[str, dict[str, Any]] = {}
    warnings: list[str] = []
    priority_builders: list[tuple[str, Callable[[], dict[str, Any]]]] = [
        (
            "indices",
            lambda: _cached_module(
                "market",
                "indices",
                "full-parallel",
                _load_indices_parallel,
            ),
        ),
        (
            "marketBreadth",
            lambda: _cached_module(
                "market",
                "breadth",
                "v1",
                legacy._load_market_breadth,
            ),
        ),
    ]
    for name, builder in priority_builders:
        if monotonic() - started_at > LIGHT_HOME_BUDGET_SECONDS:
            modules[name] = _cached_or_unavailable(
                "market",
                "indices" if name == "indices" else "breadth",
                "full-parallel" if name == "indices" else "v1",
                name,
            )
            warnings.append(f"{name}: skipped_light_budget_exhausted")
            continue
        modules[name] = _load_market_module(name, builder)

    modules["gainers"] = _cached_or_unavailable(
        "ranking", "gainers", "20", "gainers"
    )
    modules["losers"] = _cached_or_unavailable(
        "ranking", "losers", "20", "losers"
    )
    modules["amountRanking"] = _cached_or_unavailable(
        "ranking", "amount", "20", "amountRanking"
    )
    modules["turnoverRanking"] = _cached_or_unavailable(
        "ranking", "turnover", "20", "turnoverRanking"
    )
    modules["volumeRatioRanking"] = _cached_or_unavailable(
        "ranking", "volume_ratio", "20", "volumeRatioRanking"
    )
    modules["speedRanking"] = _cached_or_unavailable(
        "ranking", "speed", "20", "speedRanking"
    )
    modules["mainInflowRanking"] = _cached_or_unavailable(
        "ranking", "main_inflow", "20", "mainInflowRanking"
    )
    modules["mainOutflowRanking"] = _cached_or_unavailable(
        "ranking", "main_outflow", "20", "mainOutflowRanking"
    )
    modules["sectorHotRanking"] = _cached_or_unavailable(
        "sectors", "industry", "20", "sectorHotRanking"
    )
    warnings.append("market_home: full_background_refresh_required")
    return _assemble_market_home(modules, started_at, "light", warnings)


def _build_market_home_full() -> dict[str, Any]:
    started_at = monotonic()
    modules: dict[str, dict[str, Any]] = {}
    builders = _full_module_builders()
    with ThreadPoolExecutor(
        max_workers=FULL_REFRESH_WORKERS,
        thread_name_prefix="market-home-full",
    ) as executor:
        futures = {
            executor.submit(_load_market_module, name, builder): name
            for name, builder in builders.items()
        }
        for future in as_completed(futures):
            name = futures[future]
            try:
                modules[name] = future.result()
            except Exception as exc:
                modules[name] = _module_unavailable(
                    name,
                    f"background_future_failed: {type(exc).__name__}: {exc}",
                )

    return _assemble_market_home(
        modules,
        started_at,
        "full",
        [f"market_home: full_refresh_workers={FULL_REFRESH_WORKERS}"],
    )


def _home_cache_key() -> str:
    return legacy._cache_key(
        "market",
        "home",
        MARKET_HOME_CACHE_VERSION,
    )


def _refresh_market_home_background() -> None:
    global _home_refresh_running
    try:
        payload = _build_market_home_full()
        if str(payload.get("status", "")).lower() in _REAL_STATUSES:
            legacy._cache_put(_home_cache_key(), payload)
        else:
            LOGGER.warning(
                "full market-home refresh produced no real critical module; "
                "keeping previous home cache"
            )
    except Exception:
        LOGGER.exception("full market-home background refresh failed")
    finally:
        with _market_home_refresh_lock:
            _home_refresh_running = False


def _start_market_home_background_refresh() -> bool:
    global _home_refresh_running
    with _market_home_refresh_lock:
        if _home_refresh_running:
            return False
        _home_refresh_running = True
    Thread(
        target=_refresh_market_home_background,
        name="market-home-refresh",
        daemon=True,
    ).start()
    return True


def _with_home_cache_label(
    payload: dict[str, Any],
    age_seconds: int,
    label: str,
) -> dict[str, Any]:
    cached = deepcopy(payload)
    cached["cacheAgeMs"] = max(age_seconds, 0) * 1000
    if label == "stale" and str(cached.get("status", "")).lower() in {
        "ok",
        "partial",
    }:
        cached["status"] = "stale"
    cached["warnings"] = list(cached.get("warnings") or []) + [
        f"market_home_cache: {label} age={age_seconds}s"
    ]
    return cached


def _load_market_home_cached() -> dict[str, Any]:
    key = _home_cache_key()
    fresh = legacy._cache_get(key, legacy.FAST_CACHE_SECONDS)
    if fresh is not None:
        payload, age = fresh
        cached = _with_home_cache_label(payload, age, "hit")
        if payload.get("sourceUrlType") == "lightweight market endpoint":
            if _start_market_home_background_refresh():
                cached["warnings"].append(
                    "market_home_cache: full_background_refresh_started"
                )
        return cached

    stale = legacy._cache_get(key, legacy.STALE_CACHE_SECONDS)
    if stale is not None:
        payload, age = stale
        started = _start_market_home_background_refresh()
        cached = _with_home_cache_label(payload, age, "stale")
        if started:
            cached["warnings"].append(
                "market_home_cache: full_background_refresh_started"
            )
        return cached

    payload = _build_market_home_light()
    if str(payload.get("status", "")).lower() in _REAL_STATUSES:
        legacy._cache_put(key, payload)
    started = _start_market_home_background_refresh()
    response = deepcopy(payload)
    response["warnings"] = list(response.get("warnings") or []) + [
        "market_home_cache: cold_lightweight_response",
        (
            "market_home_cache: full_background_refresh_started"
            if started
            else "market_home_cache: full_background_refresh_already_running"
        ),
    ]
    return response


_remove_legacy_market_home_route()


@app.get(MARKET_HOME_PATH)
def a_share_market_home_parallel() -> dict[str, Any]:
    # Only the lightweight cold build is serialized. The full module refresh runs
    # in one guarded background worker and writes its result through the shared cache.
    with _market_home_lock:
        return _load_market_home_cached()
