from __future__ import annotations

from concurrent.futures import Future, ThreadPoolExecutor, wait
from copy import deepcopy
from datetime import datetime, timezone
from threading import Lock
from time import monotonic
from typing import Any, Callable

import httpx

import main as legacy

app = legacy.app

MARKET_HOME_PATH = "/api/stock/a-share/market/home"
MARKET_HOME_CACHE_VERSION = "v2-parallel"
MARKET_HOME_WORKERS = 10
MARKET_HOME_BUDGET_SECONDS = 8.5
INDEX_BUDGET_SECONDS = 5.5
EASTMONEY_ULIST_URL = "https://push2.eastmoney.com/api/qt/ulist.np/get"

_market_home_lock = Lock()
_client_lock = Lock()
_shared_client: httpx.Client | None = None

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
                limits=httpx.Limits(max_connections=20, max_keepalive_connections=10, keepalive_expiry=20.0),
                headers={
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125 Safari/537.36",
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


def _mark_cached_module(payload: dict[str, Any], age_seconds: int, stale: bool) -> dict[str, Any]:
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
    legacy._cache_put(key, payload)
    return payload


def _load_indices_parallel() -> dict[str, Any]:
    started_at = monotonic()
    warnings: list[str] = []
    secids = ",".join(index["secid"] for index in _INDEX_SECURITIES)
    raw = legacy._eastmoney_get(
        _get_shared_client(),
        EASTMONEY_ULIST_URL,
        {
            "secids": secids,
            "fields": "f12,f14,f2,f3,f4,f5,f6,f15,f16,f17,f18",
            "fltt": "2",
        },
    )
    diff = list((raw.get("data") or {}).get("diff") or [])
    by_code = {str(item.get("f12") or ""): item for item in diff}
    items: list[dict[str, Any]] = []
    for index in _INDEX_SECURITIES:
        item = by_code.get(index["code"])
        if not item:
            warnings.append(f"index_{index['code']}_missing_from_batch")
            continue
        items.append({
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
        })
    order = {item["code"]: position for position, item in enumerate(_INDEX_SECURITIES)}
    items.sort(key=lambda item: order.get(str(item.get("code", "")), len(order)))
    status = "ok" if len(items) == len(_INDEX_SECURITIES) else ("partial" if items else "unavailable")
    warnings.append(f"indices_parallel_build_ms={int((monotonic() - started_at) * 1000)}")
    return legacy._module_payload(
        status=status,
        source="eastmoney_quote",
        source_url_type="qt/stock/get controlled parallel",
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
    limit_score = min(legacy._safe_float(items.get("limitUpCount")) * 1.5, 25.0)
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


def _load_market_module(name: str, builder: Callable[[], dict[str, Any]]) -> dict[str, Any]:
    try:
        payload = builder()
        if not isinstance(payload, dict):
            raise ValueError("module payload is not an object")
        return payload
    except Exception as exc:
        return _module_unavailable(name, f"{type(exc).__name__}: {exc}")


def _build_market_home_parallel() -> dict[str, Any]:
    started_at = monotonic()
    module_builders: dict[str, Callable[[], dict[str, Any]]] = {
        "indices": lambda: _cached_module(
            "market", "indices", "full-parallel", _load_indices_parallel
        ),
        "marketBreadth": lambda: _cached_module(
            "market", "breadth", "v1", legacy._load_market_breadth
        ),
        "gainers": lambda: _cached_module(
            "ranking", "gainers", "20", lambda: legacy._load_ranking("gainers", 20)
        ),
        "losers": lambda: _cached_module(
            "ranking", "losers", "20", lambda: legacy._load_ranking("losers", 20)
        ),
        "amountRanking": lambda: _cached_module(
            "ranking", "amount", "20", lambda: legacy._load_ranking("amount", 20)
        ),
        "turnoverRanking": lambda: _cached_module(
            "ranking", "turnover", "20", lambda: legacy._load_ranking("turnover", 20)
        ),
        "volumeRatioRanking": lambda: _cached_module(
            "ranking", "volume_ratio", "20", lambda: legacy._load_ranking("volume_ratio", 20)
        ),
        "speedRanking": lambda: _cached_module(
            "ranking", "speed", "20", lambda: legacy._load_ranking("speed", 20)
        ),
        "mainInflowRanking": lambda: _cached_module(
            "ranking", "main_inflow", "20", lambda: legacy._load_ranking("main_inflow", 20)
        ),
        "mainOutflowRanking": lambda: _cached_module(
            "ranking", "main_outflow", "20", lambda: legacy._load_ranking("main_outflow", 20)
        ),
        "sectorHotRanking": lambda: _cached_module(
            "sectors", "industry", "20", lambda: legacy._load_sectors("industry", 20)
        ),
    }

    executor = ThreadPoolExecutor(max_workers=MARKET_HOME_WORKERS, thread_name_prefix="market-home")
    futures: dict[Future[dict[str, Any]], str] = {
        executor.submit(_load_market_module, name, builder): name
        for name, builder in module_builders.items()
    }
    done, pending = wait(futures, timeout=MARKET_HOME_BUDGET_SECONDS)
    modules: dict[str, dict[str, Any]] = {}
    warnings: list[str] = []

    for future in done:
        name = futures[future]
        try:
            modules[name] = future.result()
        except Exception as exc:
            modules[name] = _module_unavailable(name, f"{type(exc).__name__}: {exc}")

    for future in pending:
        name = futures[future]
        future.cancel()
        modules[name] = _module_unavailable(name, "module timeout")
        warnings.append(f"{name}: timeout")

    executor.shutdown(wait=False, cancel_futures=True)
    breadth = modules.get("marketBreadth") or _module_unavailable(
        "marketBreadth", "module missing"
    )
    modules["sentiment"] = _sentiment_from_breadth(breadth)
    modules["popularityRanking"] = legacy._load_ranking("popularity", 50)
    modules["limitUpSummary"] = legacy._unavailable_module("limit_up_summary")
    modules["marketNews"] = legacy._unavailable_module("market_news")

    critical_names = {"indices", "marketBreadth", "gainers", "losers", "sectorHotRanking"}
    critical_real = sum(
        1
        for name in critical_names
        if str((modules.get(name) or {}).get("status", "")).lower() in _REAL_STATUSES
    )
    if critical_real == 0:
        raise ValueError("all critical market-home modules unavailable")

    status = "ok" if critical_real == len(critical_names) else "partial"
    for name, module in modules.items():
        for warning in module.get("warnings") or []:
            warnings.append(f"{name}: {warning}")

    return {
        "status": status,
        "source": "eastmoney_public_json",
        "sourceUrlType": "controlled parallel market endpoint",
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "cacheAgeMs": 0,
        "isDerived": False,
        "warnings": warnings
        + [f"market_home_parallel_build_ms={int((monotonic() - started_at) * 1000)}"],
        **modules,
    }


def _load_market_home_cached() -> dict[str, Any]:
    key = legacy._cache_key("market", "home", MARKET_HOME_CACHE_VERSION)
    fresh = legacy._cache_get(key, legacy.FAST_CACHE_SECONDS)
    if fresh is not None:
        payload, age = fresh
        cached = deepcopy(payload)
        cached["cacheAgeMs"] = age * 1000
        cached["warnings"] = list(cached.get("warnings") or []) + [
            f"market_home_cache: hit age={age}s"
        ]
        return cached

    try:
        payload = _build_market_home_parallel()
    except Exception:
        stale = legacy._cache_get(key, legacy.STALE_CACHE_SECONDS)
        if stale is not None:
            payload, age = stale
            cached = deepcopy(payload)
            cached["status"] = "stale"
            cached["cacheAgeMs"] = age * 1000
            cached["warnings"] = list(cached.get("warnings") or []) + [
                f"market_home_cache: stale age={age}s"
            ]
            return cached
        raise

    legacy._cache_put(key, payload)
    return payload


_remove_legacy_market_home_route()


@app.get(MARKET_HOME_PATH)
def a_share_market_home_parallel() -> dict[str, Any]:
    # A single in-process build prevents cold-start request stampedes. Cached calls
    # only hold this lock for a few microseconds.
    with _market_home_lock:
        return _load_market_home_cached()
