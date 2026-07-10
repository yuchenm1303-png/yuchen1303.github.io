from __future__ import annotations

import math
from concurrent.futures import ThreadPoolExecutor, as_completed
from copy import deepcopy
from datetime import datetime, timezone
from threading import Lock, Thread
from time import monotonic
from typing import Any

import main as legacy
import market_home_server as home


MARKET_BREADTH_CACHE_VERSION = "v3-full-universe-swr"
MARKET_BREADTH_FRESH_SECONDS = 45.0
MARKET_BREADTH_STALE_SECONDS = 6 * 60 * 60.0
MARKET_BREADTH_PAGE_SIZE = 100
MARKET_BREADTH_MAX_PAGES = 70
MARKET_BREADTH_WORKERS = 10
MARKET_BREADTH_MIN_COVERAGE = 0.95
MARKET_BREADTH_MIN_ROWS = 3000
MARKET_BREADTH_REFRESH_BACKOFF_BASE_SECONDS = 3.0
MARKET_BREADTH_REFRESH_BACKOFF_MAX_SECONDS = 60.0

_CACHE_KEY = legacy._cache_key(
    "market",
    "breadth",
    MARKET_BREADTH_CACHE_VERSION,
)
_BUILD_LOCK = Lock()
_REFRESH_STATE_LOCK = Lock()
_DIAGNOSTICS_LOCK = Lock()
_PAGE_EXECUTOR = ThreadPoolExecutor(
    max_workers=MARKET_BREADTH_WORKERS,
    thread_name_prefix="market-breadth-page",
)
_refresh_running = False
_refresh_failures = 0
_refresh_retry_after = 0.0
_last_diagnostics: dict[str, Any] = {
    "version": MARKET_BREADTH_CACHE_VERSION,
    "state": "idle",
    "reportedTotal": 0,
    "sampleCount": 0,
    "coverageRate": 0.0,
    "pageCount": 0,
    "latencyMs": 0,
    "lastError": "",
    "updatedAt": "",
    "refreshRunning": False,
    "refreshFailures": 0,
    "retryAfterMs": 0,
}


def cache_is_fresh(max_age_seconds: float = MARKET_BREADTH_FRESH_SECONDS) -> bool:
    return legacy._cache_get(_CACHE_KEY, max_age_seconds) is not None


def diagnostics() -> dict[str, Any]:
    with _DIAGNOSTICS_LOCK:
        snapshot = deepcopy(_last_diagnostics)
    with _REFRESH_STATE_LOCK:
        snapshot["refreshRunning"] = _refresh_running
        snapshot["refreshFailures"] = _refresh_failures
        snapshot["retryAfterMs"] = max(int((_refresh_retry_after - monotonic()) * 1000), 0)
    return snapshot


def _set_diagnostics(**values: Any) -> None:
    with _DIAGNOSTICS_LOCK:
        _last_diagnostics.update(values)


def _request_page(page: int) -> tuple[int, list[dict[str, Any]], list[str]]:
    warnings: list[str] = []
    raw = legacy._eastmoney_get_first(
        home._get_shared_client(),
        legacy.EASTMONEY_CLIST_URLS,
        {
            "pn": str(page),
            "pz": str(MARKET_BREADTH_PAGE_SIZE),
            "po": "1",
            "np": "1",
            "fltt": "2",
            "invt": "2",
            "fid": "f12",
            "fs": legacy.A_STOCK_FS,
            "fields": "f12,f13,f14,f3,f6",
        },
        f"market_breadth_page_{page}",
        warnings,
    )
    data = raw.get("data") or {}
    total = max(int(legacy._safe_float(data.get("total"), 0.0)), 0)
    rows = [item for item in list(data.get("diff") or []) if isinstance(item, dict)]
    return total, rows, warnings


def _load_full_market_universe() -> tuple[list[dict[str, Any]], int, int, list[str]]:
    first_total, first_rows, first_warnings = _request_page(1)
    if not first_rows:
        raise ValueError("market breadth first page returned no rows")

    reported_total = max(first_total, len(first_rows))
    page_count = min(
        max(math.ceil(reported_total / MARKET_BREADTH_PAGE_SIZE), 1),
        MARKET_BREADTH_MAX_PAGES,
    )
    rows_by_page: dict[int, list[dict[str, Any]]] = {1: first_rows}
    warnings = list(first_warnings)
    failed_pages: list[int] = []

    if page_count > 1:
        futures = {
            _PAGE_EXECUTOR.submit(_request_page, page): page
            for page in range(2, page_count + 1)
        }
        for future in as_completed(futures):
            page = futures[future]
            try:
                total, page_rows, page_warnings = future.result()
                if total > reported_total:
                    reported_total = total
                if page_rows:
                    rows_by_page[page] = page_rows
                else:
                    failed_pages.append(page)
                warnings.extend(page_warnings)
            except Exception as exc:
                failed_pages.append(page)
                warnings.append(
                    f"market_breadth_page_{page}_failed: {type(exc).__name__}: {exc}"
                )

    # 并发阶段偶发失败时只重试缺页，不重新抓取成功页。
    retry_futures = {
        _PAGE_EXECUTOR.submit(_request_page, page): page
        for page in failed_pages
    }
    for future in as_completed(retry_futures):
        page = retry_futures[future]
        try:
            total, page_rows, page_warnings = future.result()
            if total > reported_total:
                reported_total = total
            if page_rows:
                rows_by_page[page] = page_rows
            warnings.extend(page_warnings)
        except Exception as exc:
            warnings.append(
                f"market_breadth_page_{page}_retry_failed: {type(exc).__name__}: {exc}"
            )

    by_security: dict[str, dict[str, Any]] = {}
    for page in sorted(rows_by_page):
        for item in rows_by_page[page]:
            code = str(item.get("f12") or "").strip()
            market = str(item.get("f13") or "").strip()
            if len(code) != 6:
                continue
            by_security[f"{market}:{code}"] = item

    rows = list(by_security.values())
    coverage = len(rows) / max(reported_total, 1)
    if len(rows) < MARKET_BREADTH_MIN_ROWS or coverage < MARKET_BREADTH_MIN_COVERAGE:
        raise ValueError(
            "market breadth universe incomplete: "
            f"rows={len(rows)} total={reported_total} coverage={coverage:.4f} "
            f"pages={len(rows_by_page)}/{page_count}"
        )

    warnings.append(
        "market_breadth_universe: "
        f"rows={len(rows)} total={reported_total} coverage={coverage:.4f} "
        f"pages={len(rows_by_page)}/{page_count}"
    )
    return rows, reported_total, page_count, warnings[-32:]


def _is_beijing(code: str) -> bool:
    return code.startswith(("4", "8", "92"))


def _limit_threshold(code: str, name: str) -> float:
    upper_name = name.upper()
    if "ST" in upper_name:
        return 4.8
    if code.startswith(("300", "301", "688", "689")):
        return 19.8
    if _is_beijing(code):
        return 29.8
    return 9.8


def _median(values: list[float]) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    middle = len(ordered) // 2
    if len(ordered) % 2:
        return ordered[middle]
    return (ordered[middle - 1] + ordered[middle]) / 2.0


def _build_market_breadth() -> dict[str, Any]:
    started_at = monotonic()
    _set_diagnostics(state="building", lastError="")
    rows, reported_total, page_count, warnings = _load_full_market_universe()

    up = down = flat = limit_up = limit_down = 0
    amount_total = 0.0
    amount_shsz = 0.0
    amount_bj = 0.0
    changes: list[float] = []

    for item in rows:
        code = str(item.get("f12") or "").strip()
        name = str(item.get("f14") or "").strip()
        change = legacy._safe_float(item.get("f3"))
        amount = max(legacy._safe_float(item.get("f6")), 0.0)
        changes.append(change)
        amount_total += amount
        if _is_beijing(code):
            amount_bj += amount
        else:
            amount_shsz += amount

        if change > 0:
            up += 1
        elif change < 0:
            down += 1
        else:
            flat += 1

        threshold = _limit_threshold(code, name)
        if change >= threshold:
            limit_up += 1
        if change <= -threshold:
            limit_down += 1

    sample_count = len(rows)
    coverage_rate = sample_count / max(reported_total, 1)
    latency_ms = int((monotonic() - started_at) * 1000)
    updated_at = datetime.now(timezone.utc).isoformat()
    items = {
        "upCount": up,
        "downCount": down,
        "flatCount": flat,
        "limitUpCount": limit_up,
        "limitDownCount": limit_down,
        "brokenBoardCount": None,
        "brokenBoardRate": None,
        "maxConsecutiveBoards": None,
        "redRate": round(up / max(sample_count, 1) * 100, 2),
        "medianChangePercent": round(_median(changes), 2),
        "marketAmount": legacy._format_cn_money(amount_total),
        "shszAmount": legacy._format_cn_money(amount_shsz),
        "bjAmount": legacy._format_cn_money(amount_bj),
        "moneyMakingEffect": round(up / max(up + down, 1) * 100, 2),
        "sampleCount": sample_count,
        "reportedTotal": reported_total,
        "coverageRate": round(coverage_rate * 100, 2),
        "pageCount": page_count,
        "updatedAt": updated_at,
    }
    payload = legacy._module_payload(
        status="ok",
        source="eastmoney_clist_paginated",
        source_url_type="qt/clist/get full A-share universe pagination",
        items=items,
        warnings=warnings
        + [
            "market_breadth: full_universe_only",
            f"market_breadth_build_ms={latency_ms}",
        ],
        cache_age_ms=0,
    )
    _set_diagnostics(
        state="ok",
        reportedTotal=reported_total,
        sampleCount=sample_count,
        coverageRate=round(coverage_rate * 100, 2),
        pageCount=page_count,
        latencyMs=latency_ms,
        lastError="",
        updatedAt=updated_at,
    )
    return payload


def _refresh_backoff_seconds(failure_count: int) -> float:
    exponent = max(failure_count - 1, 0)
    return min(
        MARKET_BREADTH_REFRESH_BACKOFF_BASE_SECONDS * (2**exponent),
        MARKET_BREADTH_REFRESH_BACKOFF_MAX_SECONDS,
    )


def _background_refresh_worker() -> None:
    global _refresh_running, _refresh_failures, _refresh_retry_after
    try:
        with _BUILD_LOCK:
            fresh = legacy._cache_get(_CACHE_KEY, MARKET_BREADTH_FRESH_SECONDS)
            if fresh is not None:
                return
            payload = _build_market_breadth()
            legacy._cache_put(_CACHE_KEY, payload)
        with _REFRESH_STATE_LOCK:
            _refresh_failures = 0
            _refresh_retry_after = 0.0
    except Exception as exc:
        with _REFRESH_STATE_LOCK:
            _refresh_failures += 1
            _refresh_retry_after = monotonic() + _refresh_backoff_seconds(_refresh_failures)
        _set_diagnostics(
            state="error",
            lastError=f"{type(exc).__name__}: {exc}",
        )
    finally:
        with _REFRESH_STATE_LOCK:
            _refresh_running = False


def ensure_background_refresh(force: bool = False) -> bool:
    global _refresh_running
    now = monotonic()
    with _REFRESH_STATE_LOCK:
        if _refresh_running:
            return False
        if not force and now < _refresh_retry_after:
            return False
        _refresh_running = True
    _set_diagnostics(state="refreshing")
    Thread(
        target=_background_refresh_worker,
        name="market-breadth-refresh",
        daemon=True,
    ).start()
    return True


def load_market_breadth_cached(force: bool = False) -> dict[str, Any]:
    if not force:
        fresh = legacy._cache_get(_CACHE_KEY, MARKET_BREADTH_FRESH_SECONDS)
        if fresh is not None:
            payload, age = fresh
            return home._mark_cached_module(payload, age, stale=False)

    stale = legacy._cache_get(_CACHE_KEY, MARKET_BREADTH_STALE_SECONDS)
    refresh_started = ensure_background_refresh(force=force)
    refresh_warning = (
        "market_breadth_refresh: started"
        if refresh_started
        else "market_breadth_refresh: reused_or_backing_off"
    )

    if stale is not None:
        old, age = stale
        cached = home._mark_cached_module(old, age, stale=True)
        cached["warnings"] = list(cached.get("warnings") or []) + [refresh_warning]
        return cached

    unavailable = home._module_unavailable(
        "marketBreadth",
        "warming_in_background",
    )
    unavailable["warnings"] = list(unavailable.get("warnings") or []) + [refresh_warning]
    return unavailable
