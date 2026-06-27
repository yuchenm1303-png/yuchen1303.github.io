from __future__ import annotations

import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
from copy import deepcopy
from datetime import datetime, timezone
from threading import Event, Lock, Thread
from time import monotonic
from typing import Any, Callable

import httpx

import main as legacy

app = legacy.app

LOGGER = logging.getLogger("ai-ledger-stock-proxy.market-home")
MARKET_HOME_PATH = "/api/stock/a-share/market/home"
MARKET_HOME_CACHE_VERSION = "v5-coordinated-cold-start"
FULL_REFRESH_WORKERS = 3
MARKET_UNIVERSE_FRESH_SECONDS = 18.0
MARKET_UNIVERSE_STALE_SECONDS = 6 * 60 * 60.0
MARKET_HOME_COLD_WAIT_SECONDS = 10.0
EASTMONEY_ULIST_URLS = [
    "https://push2.eastmoney.com/api/qt/ulist.np/get",
    "https://push2delay.eastmoney.com/api/qt/ulist.np/get",
    "https://push2his.eastmoney.com/api/qt/ulist.np/get",
]

_market_home_lock = Lock()
_market_home_refresh_lock = Lock()
_client_lock = Lock()
_universe_lock = Lock()
_shared_client: httpx.Client | None = None
_home_refresh_running = False
_home_refresh_complete = Event()
_home_refresh_complete.set()
_universe_cache: tuple[float, list[dict[str, Any]], list[str]] | None = None

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
_CRITICAL_MODULES = {
    "indices",
    "marketBreadth",
    "gainers",
    "losers",
    "sectorHotRanking",
}
_REFRESHABLE_MODULES = {
    "indices",
    "marketBreadth",
    "gainers",
    "losers",
    "amountRanking",
    "turnoverRanking",
    "volumeRatioRanking",
    "speedRanking",
    "mainInflowRanking",
    "mainOutflowRanking",
    "sectorHotRanking",
}
_RANKING_SPECS = {
    "gainers": ("gainers", "f3", True),
    "losers": ("losers", "f3", False),
    "amountRanking": ("amount", "f6", True),
    "turnoverRanking": ("turnover", "f8", True),
    "volumeRatioRanking": ("volume_ratio", "f10", True),
    "speedRanking": ("speed", "f22", True),
    "mainInflowRanking": ("main_inflow", "f62", True),
    "mainOutflowRanking": ("main_outflow", "f62", False),
}


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
                timeout=httpx.Timeout(2.8, connect=0.8),
                limits=httpx.Limits(
                    max_connections=12,
                    max_keepalive_connections=8,
                    keepalive_expiry=30.0,
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
    return _module_unavailable(name, "waiting_for_real_refresh")


def _cache_module(kind: str, query: str, mode: str, payload: dict[str, Any]) -> None:
    if legacy._payload_has_real_items(payload):
        legacy._cache_put(legacy._cache_key(kind, query, mode), payload)


def _load_indices_parallel() -> dict[str, Any]:
    started_at = monotonic()
    warnings: list[str] = []
    secids = ",".join(index["secid"] for index in _INDEX_SECURITIES)
    raw = legacy._eastmoney_get_first(
        _get_shared_client(),
        EASTMONEY_ULIST_URLS,
        {
            "secids": secids,
            "fields": "f12,f14,f2,f3,f4,f5,f6,f15,f16,f17,f18",
            "fltt": "2",
        },
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
        f"indices_batch_build_ms={int((monotonic() - started_at) * 1000)}"
    )
    return legacy._module_payload(
        status=status,
        source="eastmoney_quote",
        source_url_type="qt/ulist.np/get batch",
        items=items,
        warnings=warnings,
    )


def _load_market_universe_cached() -> tuple[list[dict[str, Any]], list[str], int, bool]:
    global _universe_cache
    now = monotonic()
    with _universe_lock:
        if _universe_cache is not None:
            stored_at, rows, cached_warnings = _universe_cache
            age = now - stored_at
            if age <= MARKET_UNIVERSE_FRESH_SECONDS:
                return rows, list(cached_warnings) + [f"market_universe_cache: hit age={age:.2f}s"], int(age * 1000), False

        stale = _universe_cache
        warnings: list[str] = []
        try:
            rows = legacy._clist_items(
                _get_shared_client(),
                legacy.A_STOCK_FS,
                "f12",
                5000,
                warnings,
                "market_universe",
                po="1",
                strict=True,
            )
            if not rows:
                raise ValueError("market universe returned no real rows")
            _universe_cache = (monotonic(), rows, list(warnings))
            return rows, warnings, 0, False
        except Exception as exc:
            if stale is not None:
                stored_at, rows, cached_warnings = stale
                age = now - stored_at
                if age <= MARKET_UNIVERSE_STALE_SECONDS:
                    stale_warnings = list(cached_warnings) + [
                        f"market_universe_cache: stale age={age:.2f}s because {type(exc).__name__}: {exc}"
                    ]
                    return rows, stale_warnings, int(age * 1000), True
            raise


def _market_breadth_from_rows(
    rows: list[dict[str, Any]],
    warnings: list[str],
    cache_age_ms: int,
    stale: bool,
) -> dict[str, Any]:
    if not rows:
        return _module_unavailable("marketBreadth", "empty real market universe")
    changes = [legacy._safe_float(item.get("f3")) for item in rows]
    up = sum(1 for value in changes if value > 0)
    down = sum(1 for value in changes if value < 0)
    flat = sum(1 for value in changes if value == 0)
    amount = sum(legacy._safe_float(item.get("f6")) for item in rows)
    items = {
        "upCount": up,
        "downCount": down,
        "flatCount": flat,
        "limitUpCount": sum(1 for value in changes if value >= 9.8),
        "limitDownCount": sum(1 for value in changes if value <= -9.8),
        "brokenBoardCount": None,
        "brokenBoardRate": None,
        "maxConsecutiveBoards": None,
        "redRate": round(up / len(rows) * 100, 2),
        "medianChangePercent": sorted(changes)[len(changes) // 2],
        "marketAmount": legacy._format_cn_money(amount),
        "shszAmount": legacy._format_cn_money(amount),
        "bjAmount": None,
        "moneyMakingEffect": round(up / max(up + down, 1) * 100, 2),
        "updatedAt": datetime.now(timezone.utc).isoformat(),
    }
    return legacy._module_payload(
        status="stale" if stale else "ok",
        source="eastmoney_clist",
        source_url_type="single shared real A-share universe",
        items=items,
        warnings=list(warnings) + ["market_breadth: derived_from_single_shared_universe"],
        cache_age_ms=cache_age_ms,
    )


def _ranking_from_rows(
    module_name: str,
    rows: list[dict[str, Any]],
    cache_age_ms: int,
    stale: bool,
    limit: int = 20,
) -> dict[str, Any]:
    query, field, descending = _RANKING_SPECS[module_name]
    valid_rows = [
        item
        for item in rows
        if str(item.get("f12") or "").strip()
        and str(item.get("f14") or "").strip()
    ]
    ordered = sorted(
        valid_rows,
        key=lambda item: legacy._safe_float(item.get(field)),
        reverse=descending,
    )
    items = [
        legacy._ranking_item(item, index + 1)
        for index, item in enumerate(ordered[:limit])
    ]
    return legacy._module_payload(
        status="stale" if stale and items else "ok" if items else "empty",
        source="eastmoney_clist",
        source_url_type=f"single shared real A-share universe sorted by {field}",
        items=items,
        warnings=[f"ranking_{query}: derived_from_single_shared_universe"],
        cache_age_ms=cache_age_ms,
    )


def _load_sector_hot_shared() -> dict[str, Any]:
    warnings: list[str] = []
    rows = legacy._clist_items(
        _get_shared_client(),
        legacy.SECTOR_FS["industry"],
        "f3",
        20,
        warnings,
        "sectors_industry",
        po="1",
        strict=True,
    )
    items = [
        {
            "sectorCode": legacy._safe_str(item.get("f12"), ""),
            "sectorName": legacy._safe_str(item.get("f14"), ""),
            "type": "industry",
            "changePercent": legacy._format_percent(item.get("f3")),
            "upCount": item.get("f104"),
            "downCount": item.get("f105"),
            "flatCount": item.get("f106"),
            "leaderName": legacy._safe_str(item.get("f128"), ""),
            "leaderChangePercent": legacy._format_percent(item.get("f136")),
            "amount": legacy._format_cn_money(item.get("f6")),
            "turnoverRate": legacy._format_percent(item.get("f8"), signed=False),
            "mainInflow": legacy._format_cn_money(item.get("f62")),
            "heatRank": index + 1,
            "updatedAt": datetime.now(timezone.utc).isoformat(),
        }
        for index, item in enumerate(rows[:20])
    ]
    return legacy._module_payload(
        status="ok" if items else "empty",
        source="eastmoney_clist",
        source_url_type="shared qt/clist/get industry sectors",
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
        "sourceUrlType": f"shared-universe {build_kind} market endpoint",
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


def _build_market_home_fast(build_kind: str = "fast") -> dict[str, Any]:
    started_at = monotonic()
    modules: dict[str, dict[str, Any]] = {}
    warnings: list[str] = [
        f"market_home: shared_universe_workers={FULL_REFRESH_WORKERS}"
    ]
    universe_result: tuple[list[dict[str, Any]], list[str], int, bool] | None = None

    builders: dict[str, Callable[[], Any]] = {
        "indices": lambda: _cached_module(
            "market",
            "indices",
            "full-parallel",
            _load_indices_parallel,
        ),
        "marketUniverse": _load_market_universe_cached,
        "sectorHotRanking": lambda: _cached_module(
            "sectors",
            "industry",
            "20",
            _load_sector_hot_shared,
        ),
    }
    with ThreadPoolExecutor(
        max_workers=FULL_REFRESH_WORKERS,
        thread_name_prefix="market-home-shared",
    ) as executor:
        futures = {
            executor.submit(builder): name
            for name, builder in builders.items()
        }
        for future in as_completed(futures):
            name = futures[future]
            try:
                value = future.result()
                if name == "marketUniverse":
                    universe_result = value
                else:
                    modules[name] = value
            except Exception as exc:
                if name == "marketUniverse":
                    warnings.append(
                        f"marketUniverse: {type(exc).__name__}: {exc}"
                    )
                else:
                    modules[name] = _module_unavailable(
                        name,
                        f"{type(exc).__name__}: {exc}",
                    )

    if universe_result is not None:
        rows, universe_warnings, cache_age_ms, stale = universe_result
        breadth = _market_breadth_from_rows(
            rows,
            universe_warnings,
            cache_age_ms,
            stale,
        )
        modules["marketBreadth"] = breadth
        _cache_module("market", "breadth", "v1", breadth)
        for module_name, (query, _, _) in _RANKING_SPECS.items():
            payload = _ranking_from_rows(
                module_name,
                rows,
                cache_age_ms,
                stale,
            )
            modules[module_name] = payload
            _cache_module("ranking", query, "20", payload)
    else:
        modules["marketBreadth"] = _cached_or_unavailable(
            "market",
            "breadth",
            "v1",
            "marketBreadth",
        )
        for module_name, (query, _, _) in _RANKING_SPECS.items():
            modules[module_name] = _cached_or_unavailable(
                "ranking",
                query,
                "20",
                module_name,
            )

    modules.setdefault(
        "indices",
        _cached_or_unavailable(
            "market",
            "indices",
            "full-parallel",
            "indices",
        ),
    )
    modules.setdefault(
        "sectorHotRanking",
        _cached_or_unavailable(
            "sectors",
            "industry",
            "20",
            "sectorHotRanking",
        ),
    )
    return _assemble_market_home(modules, started_at, build_kind, warnings)


def _home_cache_key() -> str:
    return legacy._cache_key("market", "home", MARKET_HOME_CACHE_VERSION)


def _home_needs_background_refresh(payload: dict[str, Any]) -> bool:
    return any(
        str((payload.get(name) or {}).get("status", "")).lower()
        not in _REAL_STATUSES
        for name in _REFRESHABLE_MODULES
    )


def _cached_home_after_wait(key: str) -> dict[str, Any] | None:
    fresh = legacy._cache_get(key, legacy.FAST_CACHE_SECONDS)
    if fresh is not None:
        payload, age = fresh
        return _with_home_cache_label(payload, age, "cold-wait-hit")
    stale = legacy._cache_get(key, legacy.STALE_CACHE_SECONDS)
    if stale is not None:
        payload, age = stale
        return _with_home_cache_label(payload, age, "cold-wait-stale")
    return None


def _warming_market_home() -> dict[str, Any]:
    started_at = monotonic()
    modules: dict[str, dict[str, Any]] = {
        "indices": _cached_or_unavailable(
            "market", "indices", "full-parallel", "indices"
        ),
        "marketBreadth": _cached_or_unavailable(
            "market", "breadth", "v1", "marketBreadth"
        ),
        "sectorHotRanking": _cached_or_unavailable(
            "sectors", "industry", "20", "sectorHotRanking"
        ),
    }
    for module_name, (query, _, _) in _RANKING_SPECS.items():
        modules[module_name] = _cached_or_unavailable(
            "ranking", query, "20", module_name
        )
    return _assemble_market_home(
        modules,
        started_at,
        "warming",
        [
            "market_home: startup refresh is still running; "
            "returned currently available real caches without starting duplicate crawl"
        ],
    )


def _refresh_market_home_background() -> None:
    global _home_refresh_running
    try:
        payload = _build_market_home_fast("background")
        if str(payload.get("status", "")).lower() in _REAL_STATUSES:
            legacy._cache_put(_home_cache_key(), payload)
        else:
            LOGGER.warning(
                "market-home background refresh produced no real critical module; "
                "keeping previous home cache"
            )
    except Exception:
        LOGGER.exception("market-home background refresh failed")
    finally:
        with _market_home_refresh_lock:
            _home_refresh_running = False
            _home_refresh_complete.set()


def _start_market_home_background_refresh() -> bool:
    global _home_refresh_running
    with _market_home_refresh_lock:
        if _home_refresh_running:
            return False
        _home_refresh_running = True
        _home_refresh_complete.clear()
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
    if label in {"stale", "cold-wait-stale"} and str(cached.get("status", "")).lower() in {
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
        if _home_needs_background_refresh(payload):
            if _start_market_home_background_refresh():
                cached["warnings"].append(
                    "market_home_cache: background_completion_started"
                )
        return cached

    stale = legacy._cache_get(key, legacy.STALE_CACHE_SECONDS)
    if stale is not None:
        payload, age = stale
        started = _start_market_home_background_refresh()
        cached = _with_home_cache_label(payload, age, "stale")
        if started:
            cached["warnings"].append(
                "market_home_cache: background_refresh_started"
            )
        return cached

    with _market_home_refresh_lock:
        refresh_running = _home_refresh_running
    if refresh_running:
        completed = _home_refresh_complete.wait(MARKET_HOME_COLD_WAIT_SECONDS)
        cached = _cached_home_after_wait(key)
        if cached is not None:
            cached["warnings"].append(
                "market_home_cache: reused startup refresh result"
            )
            return cached
        if not completed:
            return _warming_market_home()

    payload = _build_market_home_fast("cold")
    if str(payload.get("status", "")).lower() in _REAL_STATUSES:
        legacy._cache_put(key, payload)
    if _home_needs_background_refresh(payload):
        started = _start_market_home_background_refresh()
        payload = deepcopy(payload)
        payload["warnings"] = list(payload.get("warnings") or []) + [
            (
                "market_home_cache: background_completion_started"
                if started
                else "market_home_cache: background_completion_already_running"
            )
        ]
    return payload


@app.on_event("startup")
def _prewarm_market_home_on_startup() -> None:
    _start_market_home_background_refresh()


_remove_legacy_market_home_route()


@app.get(MARKET_HOME_PATH)
def a_share_market_home_parallel() -> dict[str, Any]:
    with _market_home_lock:
        return _load_market_home_cached()
