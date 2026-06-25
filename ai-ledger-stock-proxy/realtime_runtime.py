from __future__ import annotations

import asyncio
import json
import logging
from collections import OrderedDict
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from time import monotonic
from typing import Any, Awaitable, Callable
from urllib.parse import urlparse
from zoneinfo import ZoneInfo

import httpx
from fastapi import HTTPException


LOGGER = logging.getLogger("ai-ledger-stock-proxy.realtime")
CN_TZ = ZoneInfo("Asia/Shanghai")
QUOTE_URLS = [
    "https://push2.eastmoney.com/api/qt/stock/get",
    "https://push2delay.eastmoney.com/api/qt/stock/get",
    "https://push2his.eastmoney.com/api/qt/stock/get",
]
TRENDS_URLS = [
    "https://push2.eastmoney.com/api/qt/stock/trends2/get",
    "https://push2delay.eastmoney.com/api/qt/stock/trends2/get",
    "https://push2his.eastmoney.com/api/qt/stock/trends2/get",
]
DETAIL_URLS = [
    "https://push2.eastmoney.com/api/qt/stock/details/get",
    "https://push2delay.eastmoney.com/api/qt/stock/details/get",
    "https://push2his.eastmoney.com/api/qt/stock/details/get",
]
TENCENT_FIVE_DAY_URL = "https://web.ifzq.gtimg.cn/appstock/app/day/query"
SEARCH_URL = "https://searchapi.eastmoney.com/api/suggest/get"
SEARCH_TOKEN = "44c9d251add88e27b65ed86506f6e5da"


def _safe_float(value: Any, default: float = 0.0) -> float:
    try:
        if value is None:
            return default
        text = str(value).replace(",", "").replace("%", "").strip()
        if text in {"", "-", "--", "None", "null"}:
            return default
        return float(text)
    except (TypeError, ValueError):
        return default


def _scaled(value: Any, default: float = 0.0) -> float:
    return _safe_float(value, default * 100.0) / 100.0


def _price(value: Any) -> str:
    number = _safe_float(value, -1.0)
    return "--" if number < 0 else f"{number:.2f}"


def _money(value: Any) -> str:
    number = _safe_float(value)
    if number == 0:
        return "--"
    sign = "-" if number < 0 else ""
    number = abs(number)
    if number >= 1_0000_0000_0000:
        return f"{sign}{number / 1_0000_0000_0000:.2f}\u4e07\u4ebf"
    if number >= 1_0000_0000:
        return f"{sign}{number / 1_0000_0000:.2f}\u4ebf"
    if number >= 1_0000:
        return f"{sign}{number / 1_0000:.2f}\u4e07"
    return f"{sign}{number:.0f}"


def _lots(value: Any) -> str:
    number = _safe_float(value)
    if number <= 0:
        return "--"
    return f"{number / 10000:.2f}\u4e07\u624b" if number >= 10000 else f"{number:.0f}"


def _code_from_query(query: str) -> str | None:
    digits = "".join(char for char in query if char.isdigit())
    return digits if len(digits) == 6 else None


def _secid(code: str) -> str:
    return f"1.{code}" if code.startswith(("6", "9")) else f"0.{code}"


def _market(secid: str, code: str) -> str:
    if secid.startswith("1."):
        return "\u6caaa"
    if code.startswith(("4", "8", "9")):
        return "\u5317\u4ea4\u6240"
    return "\u6df1a"


def _epoch_ms(date_text: str, time_text: str) -> int:
    try:
        value = datetime.strptime(f"{date_text} {time_text}", "%Y-%m-%d %H:%M:%S")
    except ValueError:
        value = datetime.strptime(f"{date_text} {time_text}", "%Y-%m-%d %H:%M")
    return int(value.replace(tzinfo=CN_TZ).timestamp() * 1000)


@dataclass
class CacheEntry:
    value: Any
    stored_at: float
    updated_at: str
    source_timestamp: str
    source_host: str
    upstream_latency_ms: int


@dataclass
class CacheResult:
    value: Any
    cache_hit: bool
    cache_age_ms: int
    stale: bool
    waited: bool
    source_timestamp: str
    source_host: str
    upstream_latency_ms: int
    updated_at: str


@dataclass
class SourceStat:
    requests: int = 0
    successes: int = 0
    consecutive_failures: int = 0
    last_latency_ms: int = 10_000
    last_success_at: str = ""


class RealtimeRuntime:
    def __init__(self) -> None:
        self.client: httpx.AsyncClient | None = None
        self.cache: OrderedDict[str, CacheEntry] = OrderedDict()
        self.inflight: dict[str, asyncio.Task[CacheEntry]] = {}
        self.inflight_lock = asyncio.Lock()
        self.cache_lock = asyncio.Lock()
        self.source_stats: dict[str, SourceStat] = {}
        self.refresh_counts: dict[str, int] = {}
        self.hot_symbols: dict[str, float] = {}
        self.hot_task: asyncio.Task[None] | None = None
        self.hot_semaphore = asyncio.Semaphore(8)
        self.metrics = {
            "upstreamRequests": 0,
            "cacheHits": 0,
            "cacheMisses": 0,
            "singleflightWaits": 0,
            "staleReturns": 0,
            "refreshFailures": 0,
        }

    async def start(self) -> None:
        if self.client is None:
            self.client = httpx.AsyncClient(
                timeout=httpx.Timeout(1.2, connect=0.6),
                limits=httpx.Limits(max_connections=40, max_keepalive_connections=20, keepalive_expiry=20.0),
                headers={
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125 Safari/537.36",
                    "Referer": "https://quote.eastmoney.com/",
                    "Accept": "application/json, text/plain, */*",
                    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
                },
            )
        if self.hot_task is None or self.hot_task.done():
            self.hot_task = asyncio.create_task(self._hot_loop(), name="a-share-hot-refresh")

    async def close(self) -> None:
        if self.hot_task is not None:
            self.hot_task.cancel()
            await asyncio.gather(self.hot_task, return_exceptions=True)
            self.hot_task = None
        async with self.inflight_lock:
            tasks = list(self.inflight.values())
            self.inflight.clear()
        for task in tasks:
            task.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)
        if self.client is not None:
            await self.client.aclose()
            self.client = None

    async def _ensure_started(self) -> httpx.AsyncClient:
        await self.start()
        assert self.client is not None
        return self.client

    def _source_order(self, urls: list[str]) -> list[str]:
        def score(url: str) -> tuple[int, int, int]:
            host = urlparse(url).netloc
            stat = self.source_stats.get(host, SourceStat())
            fallback_penalty = 0 if host == "push2.eastmoney.com" else 1
            health_penalty = min(stat.consecutive_failures, 4)
            latency = stat.last_latency_ms if stat.successes else 500
            return health_penalty, fallback_penalty, latency

        return sorted(urls, key=score)

    async def _get_json(self, urls: list[str], params: dict[str, Any]) -> tuple[dict[str, Any], str, int]:
        client = await self._ensure_started()
        last_error: Exception | None = None
        for url in self._source_order(urls):
            host = urlparse(url).netloc
            stat = self.source_stats.setdefault(host, SourceStat())
            started = monotonic()
            stat.requests += 1
            self.metrics["upstreamRequests"] += 1
            try:
                response = await client.get(url, params=params)
                response.raise_for_status()
                payload = response.json()
                if not isinstance(payload, dict):
                    raise ValueError("upstream JSON is not an object")
                latency = int((monotonic() - started) * 1000)
                stat.successes += 1
                stat.consecutive_failures = 0
                stat.last_latency_ms = latency
                stat.last_success_at = datetime.now(timezone.utc).isoformat()
                return payload, host, latency
            except (httpx.HTTPError, ValueError) as exc:
                last_error = exc
                stat.consecutive_failures += 1
                stat.last_latency_ms = int((monotonic() - started) * 1000)
        raise ValueError(f"all upstream sources failed: {type(last_error).__name__}: {last_error}")

    async def _load_entry(self, loader: Callable[[], Awaitable[tuple[Any, str, int, str]]]) -> CacheEntry:
        value, host, latency, source_timestamp = await loader()
        return CacheEntry(
            value=value,
            stored_at=monotonic(),
            updated_at=datetime.now(timezone.utc).isoformat(),
            source_timestamp=source_timestamp,
            source_host=host,
            upstream_latency_ms=latency,
        )

    async def _refresh(self, key: str, loader: Callable[[], Awaitable[tuple[Any, str, int, str]]]) -> CacheEntry:
        try:
            self.refresh_counts[key] = self.refresh_counts.get(key, 0) + 1
            entry = await self._load_entry(loader)
            async with self.cache_lock:
                self.cache[key] = entry
                self.cache.move_to_end(key)
                while len(self.cache) > 1200:
                    self.cache.popitem(last=False)
            return entry
        finally:
            async with self.inflight_lock:
                current = self.inflight.get(key)
                if current is asyncio.current_task():
                    self.inflight.pop(key, None)

    async def _singleflight(self, key: str, loader: Callable[[], Awaitable[tuple[Any, str, int, str]]]) -> tuple[CacheEntry, bool]:
        async with self.inflight_lock:
            task = self.inflight.get(key)
            waited = task is not None
            if task is None:
                task = asyncio.create_task(self._refresh(key, loader), name=f"refresh:{key}")
                self.inflight[key] = task
            else:
                self.metrics["singleflightWaits"] += 1
        return await asyncio.shield(task), waited

    async def _cached(
        self,
        key: str,
        fresh_seconds: float,
        stale_seconds: float,
        loader: Callable[[], Awaitable[tuple[Any, str, int, str]]],
        allow_stale_while_revalidate: bool = True,
    ) -> CacheResult:
        entry = self.cache.get(key)
        age = monotonic() - entry.stored_at if entry else float("inf")
        if entry and age <= fresh_seconds:
            self.metrics["cacheHits"] += 1
            return self._result(entry, True, age, False, False, 0)
        if entry and age <= stale_seconds and allow_stale_while_revalidate:
            self.metrics["cacheHits"] += 1
            self.metrics["staleReturns"] += 1
            async with self.inflight_lock:
                if key not in self.inflight:
                    task = asyncio.create_task(self._refresh(key, loader), name=f"refresh:{key}")
                    self.inflight[key] = task
                    task.add_done_callback(self._background_done)
            return self._result(entry, True, age, True, False, 0)
        self.metrics["cacheMisses"] += 1
        try:
            loaded, waited = await self._singleflight(key, loader)
            return self._result(loaded, False, 0.0, False, waited, loaded.upstream_latency_ms)
        except Exception:
            if entry and age <= stale_seconds:
                self.metrics["staleReturns"] += 1
                return self._result(entry, True, age, True, False, 0)
            raise

    def _background_done(self, task: asyncio.Task[CacheEntry]) -> None:
        try:
            task.result()
        except Exception as exc:
            self.metrics["refreshFailures"] += 1
            LOGGER.warning("background refresh failed: %s: %s", type(exc).__name__, exc)

    @staticmethod
    def _result(entry: CacheEntry, hit: bool, age: float, stale: bool, waited: bool, upstream_ms: int) -> CacheResult:
        return CacheResult(
            value=entry.value,
            cache_hit=hit,
            cache_age_ms=max(int(age * 1000), 0),
            stale=stale,
            waited=waited,
            source_timestamp=entry.source_timestamp,
            source_host=entry.source_host,
            upstream_latency_ms=upstream_ms,
            updated_at=entry.updated_at,
        )

    async def resolve(self, query: str) -> dict[str, str]:
        keyword = query.strip()
        if not keyword:
            raise HTTPException(status_code=400, detail="query cannot be empty")
        code = _code_from_query(keyword)
        if code:
            return {"code": code, "name": code, "secid": _secid(code), "resolveSource": "code-prefix-fast"}

        async def loader() -> tuple[Any, str, int, str]:
            client = await self._ensure_started()
            started = monotonic()
            response = await client.get(SEARCH_URL, params={"input": keyword, "type": "14", "token": SEARCH_TOKEN})
            response.raise_for_status()
            payload = response.json()
            for item in ((payload.get("QuotationCodeTable") or {}).get("Data") or []):
                if item.get("Classify") != "AStock":
                    continue
                item_code = str(item.get("Code") or "")
                quote_id = str(item.get("QuoteID") or "")
                if len(item_code) == 6 and "." in quote_id:
                    value = {"code": item_code, "name": str(item.get("Name") or item_code), "secid": quote_id, "resolveSource": "eastmoney-search-cache"}
                    return value, urlparse(SEARCH_URL).netloc, int((monotonic() - started) * 1000), datetime.now(timezone.utc).isoformat()
            raise HTTPException(status_code=404, detail=f"A-share security not found: {keyword}")

        result = await self._cached(f"resolve:{keyword.lower()}", 86400, 7 * 86400, loader, False)
        return result.value

    @staticmethod
    def _quote_fields() -> str:
        base = [
            "f43", "f44", "f45", "f46", "f47", "f48", "f50", "f57", "f58", "f60", "f86",
            "f51", "f52", "f116", "f117", "f162", "f167", "f168", "f169", "f170", "f530",
        ]
        return ",".join(base + [f"f{i}" for i in range(11, 41)])

    async def _quote_loader(self, security: dict[str, str]) -> tuple[Any, str, int, str]:
        payload, host, latency = await self._get_json(QUOTE_URLS, {"secid": security["secid"], "fields": self._quote_fields()})
        raw = payload.get("data")
        if not raw:
            raise ValueError("quote data is empty")
        source_timestamp = datetime.fromtimestamp(_safe_float(raw.get("f86"), datetime.now().timestamp()), tz=timezone.utc).isoformat()
        return raw, host, latency, source_timestamp

    async def quote_raw(self, security: dict[str, str], force: bool = False) -> CacheResult:
        fresh = 0.0 if force else 1.0
        return await self._cached(f"quote:{security['code']}", fresh, 30.0, lambda: self._quote_loader(security), not force)

    @staticmethod
    def parse_quote(raw: dict[str, Any], security: dict[str, str]) -> dict[str, Any]:
        code = str(raw.get("f57") or security["code"])
        name = str(raw.get("f58") or security["name"])
        change_amount = _scaled(raw.get("f169"))
        change_percent = _scaled(raw.get("f170"))
        return {
            "name": name,
            "code": code,
            "market": _market(security["secid"], code),
            "price": _price(_scaled(raw.get("f43"), -1.0)),
            "changeAmount": f"{change_amount:+.2f}",
            "changePercent": f"{change_percent:+.2f}%",
            "previousClose": _scaled(raw.get("f60")),
            "open": _price(_scaled(raw.get("f46"), -1.0)),
            "high": _price(_scaled(raw.get("f44"), -1.0)),
            "low": _price(_scaled(raw.get("f45"), -1.0)),
            "amount": _money(raw.get("f48")),
            "turnoverRate": f"{_scaled(raw.get('f168')):.2f}%",
            "volumeRatio": _price(_scaled(raw.get("f50"), -1.0)),
            "totalMarketValue": _money(raw.get("f116")),
            "floatMarketValue": _money(raw.get("f117")),
            "peTtm": _price(_scaled(raw.get("f162"), -1.0)),
            "pb": _price(_scaled(raw.get("f167"), -1.0)),
            "popularityRank": "--",
            "isRising": change_amount >= 0,
        }

    @staticmethod
    def parse_depth(raw: dict[str, Any], quote: dict[str, Any]) -> tuple[list[dict[str, Any]], list[dict[str, Any]], dict[str, Any]]:
        current = _safe_float(quote.get("price"))
        limit_up = _scaled(raw.get("f51"), -1.0)
        limit_down = _scaled(raw.get("f52"), -1.0)
        warnings: list[str] = []

        def rows(pairs: list[tuple[int, int]], labels: list[str], is_ask: bool) -> list[dict[str, Any]]:
            result: list[dict[str, Any]] = []
            for (price_field, volume_field), label in zip(pairs, labels):
                value = _scaled(raw.get(f"f{price_field}"), -1.0)
                volume = _safe_float(raw.get(f"f{volume_field}"))
                if value <= 0 or volume <= 0:
                    continue
                if current > 0 and abs(value - current) / current > 0.35:
                    warnings.append(f"depth: level_{label}_price_out_of_range")
                    continue
                if is_ask and limit_up > 0 and value > limit_up + 0.0001:
                    warnings.append(f"depth: level_{label}_above_limit_up")
                    continue
                if not is_ask and limit_down > 0 and value < limit_down - 0.0001:
                    warnings.append(f"depth: level_{label}_below_limit_down")
                    continue
                result.append({"label": label, "price": _price(value), "volume": _lots(volume), "isAsk": is_ask})
            return result

        sell = rows([(39, 40), (37, 38), (35, 36), (33, 34), (31, 32)], [f"\u5356{i}" for i in range(1, 6)], True)
        buy = rows([(19, 20), (17, 18), (15, 16), (13, 14), (11, 12)], [f"\u4e70{i}" for i in range(1, 6)], False)
        if any(_safe_float(sell[index]["price"]) > _safe_float(sell[index + 1]["price"]) for index in range(len(sell) - 1)):
            warnings.append("depth: ask_levels_not_ascending")
            sell = sorted(sell, key=lambda row: _safe_float(row["price"]))
        if any(_safe_float(buy[index]["price"]) < _safe_float(buy[index + 1]["price"]) for index in range(len(buy) - 1)):
            warnings.append("depth: bid_levels_not_descending")
            buy = sorted(buy, key=lambda row: _safe_float(row["price"]), reverse=True)
        if sell and buy and _safe_float(sell[0]["price"]) < _safe_float(buy[0]["price"]):
            warnings.append("depth: crossed_book_rejected")
            sell, buy = [], []
        status = "ok" if len(sell) == 5 and len(buy) == 5 else ("partial" if sell or buy else "empty")
        meta = {
            "depthStatus": status,
            "depthSource": "eastmoney_push2",
            "depthIsDerived": False,
            "depthWarnings": warnings,
        }
        return sell, buy, meta

    async def depth(self, security: dict[str, str], quote_result: CacheResult, quote: dict[str, Any]) -> tuple[list[dict[str, Any]], list[dict[str, Any]], dict[str, Any]]:
        key = f"depth:{security['code']}"
        sell, buy, meta = self.parse_depth(quote_result.value, quote)
        now_iso = datetime.now(timezone.utc).isoformat()
        meta.update({
            "depthUpdatedAt": now_iso,
            "depthSourceTimestamp": quote_result.source_timestamp,
            "depthCacheAgeMs": 0,
        })
        if sell or buy:
            self.cache[key] = CacheEntry(
                {"sellLevels": sell, "buyLevels": buy, "depthMeta": meta},
                monotonic(),
                quote_result.updated_at,
                quote_result.source_timestamp,
                quote_result.source_host,
                quote_result.upstream_latency_ms,
            )
            return sell, buy, meta
        old = self.cache.get(key)
        if old is not None:
            old_meta = dict(old.value.get("depthMeta") or {})
            old_meta.update({
                "depthStatus": "stale",
                "depthIsDerived": False,
                "depthCacheAgeMs": max(int((monotonic() - old.stored_at) * 1000), 0),
                "depthWarnings": list(old_meta.get("depthWarnings") or []) + ["depth: stale_true_cache"],
            })
            return old.value["sellLevels"], old.value["buyLevels"], old_meta
        meta["depthStatus"] = "unavailable"
        meta["depthWarnings"] = list(meta.get("depthWarnings") or []) + ["depth: unavailable_no_true_cache"]
        return sell, buy, meta

    async def _minute_loader(self, security: dict[str, str], ndays: int) -> tuple[Any, str, int, str]:
        payload, host, latency = await self._get_json(
            TRENDS_URLS,
            {
                "secid": security["secid"],
                "fields1": "f1,f2,f3,f4,f5,f6,f7,f8",
                "fields2": "f51,f52,f53,f54,f55,f56,f57,f58",
                "iscr": "0",
                "ndays": str(ndays),
            },
        )
        trends = ((payload.get("data") or {}).get("trends") or [])
        points: list[dict[str, Any]] = []
        volumes: list[float] = []
        for item in trends:
            parts = str(item).split(",")
            if len(parts) < 8 or " " not in parts[0]:
                continue
            date_text, time_text = parts[0].split(" ", 1)
            price = _safe_float(parts[2])
            if price <= 0:
                continue
            volume = max(_safe_float(parts[5]), 0.0)
            volumes.append(volume)
            points.append(
                {
                    "date": date_text,
                    "time": time_text[:5],
                    "timestamp": _epoch_ms(date_text, time_text),
                    "price": price,
                    "average": _safe_float(parts[7], price),
                    "volume": volume,
                    "volumeRatio": 0.0,
                }
            )
        if not points:
            raise ValueError(f"{ndays}d minute trends are empty")
        max_volume = max(volumes or [1.0])
        for point in points:
            point["volumeRatio"] = min(max(point["volume"] / max_volume, 0.02), 1.0)
        unique_dates = {point["date"] for point in points}
        if ndays == 5 and len(unique_dates) < 2:
            return await self._tencent_five_day_loader(security)
        source_timestamp = datetime.fromtimestamp(points[-1]["timestamp"] / 1000, tz=CN_TZ).astimezone(timezone.utc).isoformat()
        return points, host, latency, source_timestamp

    async def _tencent_five_day_loader(self, security: dict[str, str]) -> tuple[Any, str, int, str]:
        symbol = ("sh" if security["secid"].startswith("1.") else "sz") + security["code"]
        client = await self._ensure_started()
        host = urlparse(TENCENT_FIVE_DAY_URL).netloc
        stat = self.source_stats.setdefault(host, SourceStat())
        started = monotonic()
        stat.requests += 1
        self.metrics["upstreamRequests"] += 1
        try:
            response = await client.get(
                TENCENT_FIVE_DAY_URL,
                params={"code": symbol},
                headers={"Referer": "https://gu.qq.com/"},
                timeout=httpx.Timeout(4.0, connect=2.0),
            )
            response.raise_for_status()
            payload = response.json()
            latency = int((monotonic() - started) * 1000)
            stat.successes += 1
            stat.consecutive_failures = 0
            stat.last_latency_ms = latency
            stat.last_success_at = datetime.now(timezone.utc).isoformat()
        except (httpx.HTTPError, ValueError) as exc:
            stat.consecutive_failures += 1
            stat.last_latency_ms = int((monotonic() - started) * 1000)
            raise ValueError(f"Tencent 5d minute request failed: {type(exc).__name__}: {exc}") from exc
        days = ((((payload.get("data") or {}).get(symbol) or {}).get("data")) or [])
        points: list[dict[str, Any]] = []
        volumes: list[float] = []
        for day in days:
            raw_date = str(day.get("date") or "")
            if len(raw_date) != 8:
                continue
            date_text = f"{raw_date[:4]}-{raw_date[4:6]}-{raw_date[6:]}"
            previous_cumulative_volume = 0.0
            for item in day.get("data") or []:
                parts = str(item).split()
                if len(parts) < 4 or len(parts[0]) != 4:
                    continue
                time_text = f"{parts[0][:2]}:{parts[0][2:]}"
                price = _safe_float(parts[1])
                cumulative_volume = max(_safe_float(parts[2]), 0.0)
                cumulative_amount = max(_safe_float(parts[3]), 0.0)
                volume = max(cumulative_volume - previous_cumulative_volume, 0.0)
                previous_cumulative_volume = cumulative_volume
                average = cumulative_amount / cumulative_volume / 100.0 if cumulative_volume > 0 else price
                volumes.append(volume)
                points.append(
                    {
                        "date": date_text,
                        "time": time_text,
                        "timestamp": _epoch_ms(date_text, time_text),
                        "price": price,
                        "average": average,
                        "volume": volume,
                        "volumeRatio": 0.0,
                    }
                )
        unique_dates = {point["date"] for point in points}
        if len(unique_dates) < 2:
            raise ValueError("Tencent 5d minute data did not contain multiple trading dates")
        max_volume = max(volumes or [1.0])
        for point in points:
            point["volumeRatio"] = min(max(point["volume"] / max_volume, 0.02), 1.0)
        points.sort(key=lambda point: int(point["timestamp"]))
        source_timestamp = datetime.fromtimestamp(points[-1]["timestamp"] / 1000, tz=CN_TZ).astimezone(timezone.utc).isoformat()
        return points, host, latency, source_timestamp

    async def minute(self, security: dict[str, str], ndays: int, force: bool = False) -> CacheResult:
        key = f"minute:{ndays}d:{security['code']}"
        fresh_seconds = 0.0 if force else (1.0 if ndays == 1 else 60.0)
        stale_seconds = 30.0 if ndays == 1 else 6 * 3600.0
        return await self._cached(key, fresh_seconds, stale_seconds, lambda: self._minute_loader(security, ndays), not force)

    async def _ticks_loader(self, security: dict[str, str]) -> tuple[Any, str, int, str]:
        payload, host, latency = await self._get_json(
            DETAIL_URLS,
            {"secid": security["secid"], "pos": "-100", "fields1": "f1,f2,f3,f4,f5", "fields2": "f51,f52,f53,f54,f55"},
        )
        details = ((payload.get("data") or {}).get("details") or [])
        date_text = self._latest_trade_date(security["code"])
        ticks: list[dict[str, Any]] = []
        seen: set[tuple[int, str, str]] = set()
        for item in details[-200:]:
            parts = str(item).split(",")
            if len(parts) < 3:
                continue
            time_text = parts[0]
            timestamp = _epoch_ms(date_text, time_text)
            price = _price(parts[1])
            volume = _lots(parts[2])
            side = parts[4] if len(parts) > 4 else "0"
            is_buy = side == "2"
            key = (timestamp, price, volume)
            if key in seen:
                continue
            seen.add(key)
            ticks.append({"time": time_text, "timestamp": timestamp, "price": price, "volume": volume, "direction": "\u4e70" if is_buy else "\u5356", "isBuy": is_buy})
        if not ticks:
            raise ValueError("trade details are empty")
        source_timestamp = datetime.fromtimestamp(ticks[-1]["timestamp"] / 1000, tz=CN_TZ).astimezone(timezone.utc).isoformat()
        return ticks[-200:], host, latency, source_timestamp

    def _latest_trade_date(self, code: str) -> str:
        quote_entry = self.cache.get(f"quote:{code}")
        if quote_entry is not None:
            try:
                return datetime.fromisoformat(quote_entry.source_timestamp).astimezone(CN_TZ).strftime("%Y-%m-%d")
            except ValueError:
                pass
        value = datetime.now(CN_TZ)
        if value.hour < 9:
            value -= timedelta(days=1)
        while value.weekday() >= 5:
            value -= timedelta(days=1)
        return value.strftime("%Y-%m-%d")

    async def ticks(self, security: dict[str, str], force: bool = False) -> CacheResult:
        fresh = 0.0 if force else 1.0
        return await self._cached(f"ticks:{security['code']}", fresh, 30.0, lambda: self._ticks_loader(security), not force)

    @staticmethod
    def derived_ticks(points: list[dict[str, Any]], quote: dict[str, Any]) -> list[dict[str, Any]]:
        previous = _safe_float(quote.get("previousClose"), _safe_float(quote.get("price")))
        ticks: list[dict[str, Any]] = []
        for point in points[-20:]:
            price = _safe_float(point.get("price"))
            is_buy = price >= previous
            ticks.append(
                {
                    "time": f"{point.get('time', '--')}:00" if len(str(point.get("time", ""))) == 5 else str(point.get("time", "--")),
                    "timestamp": int(point.get("timestamp") or 0),
                    "price": _price(price),
                    "volume": _lots(max(_safe_float(point.get("volume")), 1.0)),
                    "direction": "\u4e70" if is_buy else "\u5356",
                    "isBuy": is_buy,
                }
            )
            previous = price
        return ticks

    async def realtime(self, query: str, ndays: int = 1, mark_hot: bool = True) -> dict[str, Any]:
        started = monotonic()
        security = await self.resolve(query)
        if mark_hot:
            self.hot_symbols[security["code"]] = monotonic()
        quote_task = asyncio.create_task(self.quote_raw(security))
        minute_task = asyncio.create_task(self.minute(security, ndays))
        ticks_task = asyncio.create_task(self.ticks(security))
        quote_result, minute_result = await asyncio.gather(quote_task, minute_task, return_exceptions=True)
        if isinstance(quote_result, Exception):
            raise HTTPException(status_code=502, detail=f"realtime quote failed: {quote_result}")
        if isinstance(minute_result, Exception):
            raise HTTPException(status_code=502, detail=f"realtime minute failed: {minute_result}")
        quote = self.parse_quote(quote_result.value, security)
        sell, buy, depth_meta = await self.depth(security, quote_result, quote)
        warnings = list(depth_meta.get("depthWarnings") or [])
        if quote_result.stale:
            warnings.append("quote: stale_cache")
        if minute_result.stale:
            warnings.append(f"minute:{ndays}d: stale_cache")
        if ndays == 5 and "gtimg" in minute_result.source_host:
            warnings.append("minute:5d eastmoney_incomplete_using_tencent_real_history")
        if quote_result.waited or minute_result.waited:
            warnings.append("singleflight: waited")
        try:
            ticks_outcome = await asyncio.wait_for(asyncio.shield(ticks_task), timeout=0.12)
        except asyncio.TimeoutError:
            ticks_outcome = TimeoutError("trade ticks budget exceeded 120ms")
            ticks_task.cancel()
        if isinstance(ticks_outcome, Exception):
            ticks = []
            warnings.append(f"trade_ticks: unavailable {type(ticks_outcome).__name__}: {ticks_outcome}")
            ticks_result: CacheResult | None = None
        else:
            ticks_result = ticks_outcome
            ticks = ticks_result.value[-20:]
            if ticks_result.stale:
                warnings.append("trade_ticks: stale_cache")
        cache_results = [quote_result, minute_result] + ([ticks_result] if ticks_result else [])
        total_latency_ms = int((monotonic() - started) * 1000)
        payload: dict[str, Any] = {
            "provider": "crawl_eastmoney",
            "query": query,
            "code": security["code"],
            "ndays": ndays,
            "updatedAt": max(result.updated_at for result in cache_results if result),
            "sourceTimestamp": max(result.source_timestamp for result in cache_results if result),
            "cacheHit": all(result.cache_hit for result in cache_results if result),
            "cacheAgeMs": max(result.cache_age_ms for result in cache_results if result),
            "upstreamLatencyMs": max(result.upstream_latency_ms for result in cache_results if result),
            "totalLatencyMs": total_latency_ms,
            "sourceHost": quote_result.source_host,
            "quote": quote,
            "minutePoints": minute_result.value,
            "sellLevels": sell,
            "buyLevels": buy,
            **depth_meta,
            "tradeTicks": ticks,
            "warnings": warnings,
        }
        payload["payloadBytes"] = len(json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
        LOGGER.info(
            "realtime code=%s cache=%s waited=%s upstream=%d total=%d stale=%s hot=%d",
            security["code"], payload["cacheHit"], any(result.waited for result in cache_results if result),
            payload["upstreamLatencyMs"], total_latency_ms, any(result.stale for result in cache_results if result), len(self.hot_symbols),
        )
        return payload

    async def quotes(self, codes: str) -> dict[str, Any]:
        started = monotonic()
        requested = [item.strip() for item in codes.replace("\uff0c", ",").split(",") if item.strip()]
        if not requested:
            raise HTTPException(status_code=400, detail="codes cannot be empty")
        if len(requested) > 50:
            raise HTTPException(status_code=400, detail="at most 50 codes per request")
        semaphore = asyncio.Semaphore(10)

        async def one(item: str) -> tuple[dict[str, Any] | None, CacheResult | None, str | None]:
            try:
                async with semaphore:
                    security = await self.resolve(item)
                    result = await self.quote_raw(security)
                    self.hot_symbols[security["code"]] = monotonic()
                    return self.parse_quote(result.value, security), result, None
            except Exception as exc:
                return None, None, f"quote_{item}_failed: {type(exc).__name__}: {exc}"

        outcomes = await asyncio.gather(*(one(item) for item in requested))
        items = [quote for quote, _, _ in outcomes if quote]
        results = [result for _, result, _ in outcomes if result]
        warnings = [warning for _, _, warning in outcomes if warning]
        if not items:
            raise HTTPException(status_code=502, detail="all realtime quotes failed")
        total_latency_ms = int((monotonic() - started) * 1000)
        payload = {
            "provider": "crawl_eastmoney",
            "updatedAt": max(result.updated_at for result in results),
            "sourceTimestamp": max(result.source_timestamp for result in results),
            "cacheHit": all(result.cache_hit for result in results),
            "cacheAgeMs": max(result.cache_age_ms for result in results),
            "upstreamLatencyMs": max(result.upstream_latency_ms for result in results),
            "totalLatencyMs": total_latency_ms,
            "sourceHost": ",".join(sorted({result.source_host for result in results})),
            "items": items,
            "requested": requested,
            "warnings": warnings,
        }
        payload["payloadBytes"] = len(json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
        return payload

    async def minute_compat(self, query: str) -> dict[str, Any]:
        payload = await self.realtime(query, 1)
        return payload

    async def _refresh_hot_symbol(self, code: str) -> None:
        async with self.hot_semaphore:
            security = {"code": code, "name": code, "secid": _secid(code), "resolveSource": "hot-code"}
            tasks = [self.quote_raw(security, force=True), self.minute(security, 1, force=True)]
            results = await asyncio.gather(*tasks, return_exceptions=True)
            if isinstance(results[1], CacheResult):
                await self._merge_today_into_five_day(code, results[1])

    async def _merge_today_into_five_day(self, code: str, today: CacheResult) -> None:
        key = f"minute:5d:{code}"
        existing = self.cache.get(key)
        if existing is None:
            return
        merged = {int(point["timestamp"]): point for point in existing.value}
        for point in today.value:
            merged[int(point["timestamp"])] = point
        values = sorted(merged.values(), key=lambda point: int(point["timestamp"]))
        dates = sorted({point["date"] for point in values})[-5:]
        values = [point for point in values if point["date"] in dates]
        self.cache[key] = CacheEntry(values, monotonic(), today.updated_at, today.source_timestamp, today.source_host, today.upstream_latency_ms)

    @staticmethod
    def _market_is_open() -> bool:
        now = datetime.now(CN_TZ)
        if now.weekday() >= 5:
            return False
        minute = now.hour * 60 + now.minute
        return 9 * 60 + 15 <= minute <= 11 * 60 + 35 or 12 * 60 + 55 <= minute <= 15 * 60 + 5

    async def _hot_loop(self) -> None:
        backoff = 1.0
        while True:
            try:
                await asyncio.sleep(backoff)
                now = monotonic()
                expired = [code for code, last_seen in self.hot_symbols.items() if now - last_seen > 30.0]
                for code in expired:
                    self.hot_symbols.pop(code, None)
                if not self.hot_symbols:
                    backoff = 1.0
                    continue
                if not self._market_is_open():
                    backoff = 5.0
                    continue
                outcomes = await asyncio.gather(*(self._refresh_hot_symbol(code) for code in list(self.hot_symbols)), return_exceptions=True)
                failed = sum(isinstance(item, Exception) for item in outcomes)
                backoff = min(8.0, 2.0 ** min(failed, 3)) if failed else 1.0
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                LOGGER.warning("hot refresh loop failed: %s: %s", type(exc).__name__, exc)
                backoff = min(backoff * 2, 8.0)

    def diagnostics(self) -> dict[str, Any]:
        sources = {}
        for host, stat in self.source_stats.items():
            sources[host] = {
                "requests": stat.requests,
                "successRate": round(stat.successes / stat.requests, 4) if stat.requests else 0.0,
                "consecutiveFailures": stat.consecutive_failures,
                "lastLatencyMs": stat.last_latency_ms,
                "lastSuccessAt": stat.last_success_at,
            }
        return {
            "cacheEntries": len(self.cache),
            "inflight": len(self.inflight),
            "hotSymbols": sorted(self.hot_symbols),
            "metrics": dict(self.metrics),
            "refreshCounts": dict(self.refresh_counts),
            "sources": sources,
            "redis": "not_required_for_single_process; recommended_for_multi-worker_or_multi-instance",
        }
