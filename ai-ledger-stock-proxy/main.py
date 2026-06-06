from __future__ import annotations

from copy import deepcopy
from datetime import datetime, timezone
from time import monotonic, sleep
from typing import Any
from urllib.parse import urlencode

import httpx
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware


app = FastAPI(title="AI Ledger A股行情爬虫教学代理", version="0.4.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

EASTMONEY_QUOTE_URL = "https://push2delay.eastmoney.com/api/qt/stock/get"
EASTMONEY_KLINE_URLS = [
    "https://push2delay.eastmoney.com/api/qt/stock/kline/get",
    "https://push2his.eastmoney.com/api/qt/stock/kline/get",
    "https://push2.eastmoney.com/api/qt/stock/kline/get",
]
EASTMONEY_TRENDS_URLS = [
    "https://push2delay.eastmoney.com/api/qt/stock/trends2/get",
    "https://push2his.eastmoney.com/api/qt/stock/trends2/get",
    "https://push2.eastmoney.com/api/qt/stock/trends2/get",
]
EASTMONEY_SEARCH_URL = "https://searchapi.eastmoney.com/api/suggest/get"
EASTMONEY_TOKEN = "44c9d251add88e27b65ed86506f6e5da"

FRESH_DETAIL_SECONDS = 30
STALE_DETAIL_SECONDS = 6 * 60 * 60

WATCHLIST_CODES = ["600519", "300750", "002594"]
INDEX_SECURITIES = [
    {"name": "上证", "code": "000001", "secid": "1.000001"},
    {"name": "深成", "code": "399001", "secid": "0.399001"},
    {"name": "创业板", "code": "399006", "secid": "0.399006"},
    {"name": "沪深300", "code": "000300", "secid": "1.000300"},
]

_detail_cache: dict[str, tuple[float, dict[str, Any]]] = {}


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


def _safe_str(value: Any, default: str = "--") -> str:
    if value is None:
        return default
    text = str(value).strip()
    return text if text else default


def _scaled(value: Any, default: float = 0.0) -> float:
    return _safe_float(value, default * 100.0) / 100.0


def _format_price(value: Any) -> str:
    number = _safe_float(value, -1.0)
    return "--" if number < 0 else f"{number:.2f}"


def _format_signed(value: Any) -> str:
    return f"{_safe_float(value):+.2f}"


def _format_percent(value: Any, signed: bool = True) -> str:
    number = _safe_float(value)
    return f"{number:+.2f}%" if signed else f"{number:.2f}%"


def _format_cn_money(value: Any) -> str:
    number = _safe_float(value)
    if number <= 0:
        return "--"
    if number >= 1_0000_0000_0000:
        return f"{number / 1_0000_0000_0000:.2f}万亿"
    if number >= 1_0000_0000:
        return f"{number / 1_0000_0000:.2f}亿"
    if number >= 1_0000:
        return f"{number / 1_0000:.2f}万"
    return f"{number:.0f}"


def _format_lots(value: Any) -> str:
    lots = _safe_float(value)
    if lots <= 0:
        return "--"
    if lots >= 10000:
        return f"{lots / 10000:.2f}万手"
    return f"{lots:.0f}手"


def _market_name(secid: str, code: str) -> str:
    if secid.startswith("1."):
        return "沪A"
    if code.startswith(("4", "8", "9")):
        return "北交所"
    return "深A"


def _default_secid_for_code(code: str) -> str:
    return f"1.{code}" if code.startswith(("6", "9")) else f"0.{code}"


def _code_from_query(query: str) -> str | None:
    digits = "".join(ch for ch in query if ch.isdigit())
    return digits if len(digits) == 6 else None


def _cache_key(query: str) -> str:
    return query.strip().lower()


def _cache_get(key: str, max_age_seconds: int) -> tuple[dict[str, Any], int] | None:
    entry = _detail_cache.get(key)
    if entry is None:
        return None
    created_at, payload = entry
    age = int(monotonic() - created_at)
    if age > max_age_seconds:
        return None
    return deepcopy(payload), age


def _cache_put(payload: dict[str, Any], *keys: str | None) -> None:
    now = monotonic()
    for key in keys:
        if key:
            _detail_cache[_cache_key(key)] = (now, deepcopy(payload))


def _cached_payload(payload: dict[str, Any], age: int, reason: str | None = None) -> dict[str, Any]:
    cached = deepcopy(payload)
    quote = cached.get("quote") or {}
    cached["dataSourceLabel"] = f"东方财富公开JSON缓存 · {quote.get('code', '--')} · {age}s前"
    warnings = list(cached.get("warnings") or [])
    warnings.append(f"cache: hit age={age}s")
    if reason:
        warnings.append(f"stale_cache_reason: {reason}")
    cached["warnings"] = warnings
    return cached


def _eastmoney_get(client: httpx.Client, url: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/125.0.0.0 Safari/537.36"
        ),
        "Referer": "https://quote.eastmoney.com/",
        "Origin": "https://quote.eastmoney.com",
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        "Cache-Control": "no-cache",
        "Pragma": "no-cache",
    }
    response = client.get(url, params=params, headers=headers)
    response.raise_for_status()
    data = response.json()
    if not isinstance(data, dict):
        raise ValueError("东方财富返回的 JSON 不是对象")
    return data


def _eastmoney_get_with_retry(
    client: httpx.Client,
    urls: list[str],
    params: dict[str, Any],
    label: str,
    warnings: list[str],
) -> dict[str, Any]:
    last_error: str | None = None
    for attempt in range(1, 3):
        for url in urls:
            try:
                data = _eastmoney_get(client, url, params)
                if url != urls[0] or attempt > 1:
                    warnings.append(f"{label}: recovered via {url.split('/api/', 1)[0]} attempt={attempt}")
                return data
            except (httpx.HTTPError, ValueError) as exc:
                last_error = f"{type(exc).__name__}: {exc}"
        sleep(0.25 * attempt)
    raise ValueError(last_error or f"{label}: all eastmoney endpoints failed")


def _search_security(client: httpx.Client, query: str) -> dict[str, str] | None:
    search_url = f"{EASTMONEY_SEARCH_URL}?{urlencode({'input': query, 'type': '14', 'token': EASTMONEY_TOKEN})}"
    data = _eastmoney_get(client, search_url, None)
    table = data.get("QuotationCodeTable") or {}
    for item in table.get("Data") or []:
        if item.get("Classify") != "AStock":
            continue
        code = _safe_str(item.get("Code"), "")
        quote_id = _safe_str(item.get("QuoteID"), "")
        if len(code) == 6 and "." in quote_id:
            return {
                "code": code,
                "name": _safe_str(item.get("Name"), code),
                "secid": quote_id,
                "resolveSource": "eastmoney-search",
            }
    return None


def _resolve_security(client: httpx.Client, query: str) -> dict[str, str]:
    keyword = query.strip()
    if not keyword:
        raise HTTPException(status_code=400, detail="query 不能为空")

    searched = _search_security(client, keyword)
    if searched is not None:
        return searched

    code = _code_from_query(keyword)
    if code is not None:
        return {
            "code": code,
            "name": code,
            "secid": _default_secid_for_code(code),
            "resolveSource": "code-prefix",
        }

    raise HTTPException(status_code=404, detail=f"东方财富搜索没有找到 A 股标的：{keyword}")


def _quote_fields() -> str:
    base_fields = [
        "f43", "f44", "f45", "f46", "f47", "f48", "f50", "f57", "f58", "f60",
        "f116", "f117", "f162", "f167", "f168", "f169", "f170",
        "f62", "f66", "f72", "f78", "f84", "f184",
    ]
    order_fields = [f"f{i}" for i in range(11, 41)]
    return ",".join(dict.fromkeys(base_fields + order_fields))


def _load_quote_raw(client: httpx.Client, security: dict[str, str]) -> dict[str, Any]:
    raw = _eastmoney_get(client, EASTMONEY_QUOTE_URL, {"secid": security["secid"], "fields": _quote_fields()})
    data = raw.get("data")
    if not data:
        raise HTTPException(status_code=502, detail=f"东方财富 quote 暂无数据：{security['secid']}")
    return data


def _quote_from_raw(data: dict[str, Any], security: dict[str, str]) -> dict[str, Any]:
    code = _safe_str(data.get("f57"), security["code"])
    name = _safe_str(data.get("f58"), security["name"])
    change_amount = _scaled(data.get("f169"))
    change_percent = _scaled(data.get("f170"))
    return {
        "name": name,
        "code": code,
        "market": _market_name(security["secid"], code),
        "price": _format_price(_scaled(data.get("f43"), -1.0)),
        "changeAmount": _format_signed(change_amount),
        "changePercent": _format_percent(change_percent),
        "isRising": change_amount >= 0,
        "previousClose": _scaled(data.get("f60")),
        "high": _format_price(_scaled(data.get("f44"), -1.0)),
        "low": _format_price(_scaled(data.get("f45"), -1.0)),
        "open": _format_price(_scaled(data.get("f46"), -1.0)),
        "totalMarketValue": _format_cn_money(data.get("f116")),
        "floatMarketValue": _format_cn_money(data.get("f117")),
        "volume": _format_lots(data.get("f47")),
        "volumeRatio": _format_price(_scaled(data.get("f50"), -1.0)),
        "turnoverRate": _format_percent(_scaled(data.get("f168")), signed=False),
        "peTtm": _format_price(_scaled(data.get("f162"), -1.0)),
        "pb": _format_price(_scaled(data.get("f167"), -1.0)),
        "amount": _format_cn_money(data.get("f48")),
        "popularityRank": "--",
    }


def _valid_level_price(raw_value: Any, quote_price: float) -> float | None:
    price = _scaled(raw_value, -1.0)
    if price <= 0:
        return None
    if quote_price > 0 and abs(price - quote_price) / quote_price > 0.35:
        return None
    return price


def _order_book_from_raw(raw: dict[str, Any], quote: dict[str, Any], warnings: list[str]) -> tuple[list[dict[str, str]], list[dict[str, str]]]:
    quote_price = _safe_float(quote.get("price"))

    def read_levels(pairs: list[tuple[int, int]], labels: list[str], is_ask: bool) -> list[dict[str, str]]:
        rows: list[dict[str, str]] = []
        for (price_field, volume_field), label in zip(pairs, labels):
            price = _valid_level_price(raw.get(f"f{price_field}"), quote_price)
            volume = _safe_float(raw.get(f"f{volume_field}"))
            if price is None or volume <= 0:
                continue
            rows.append({"label": label, "price": _format_price(price), "volume": _format_lots(volume), "isAsk": is_ask})
        return rows

    ask_candidates = [
        ([(31, 32), (33, 34), (35, 36), (37, 38), (39, 40)], ["卖1", "卖2", "卖3", "卖4", "卖5"]),
        ([(39, 40), (37, 38), (35, 36), (33, 34), (31, 32)], ["卖5", "卖4", "卖3", "卖2", "卖1"]),
    ]
    bid_candidates = [
        ([(19, 20), (17, 18), (15, 16), (13, 14), (11, 12)], ["买1", "买2", "买3", "买4", "买5"]),
        ([(11, 12), (13, 14), (15, 16), (17, 18), (19, 20)], ["买5", "买4", "买3", "买2", "买1"]),
    ]

    sell_levels = max((read_levels(pairs, labels, True) for pairs, labels in ask_candidates), key=len, default=[])
    buy_levels = max((read_levels(pairs, labels, False) for pairs, labels in bid_candidates), key=len, default=[])

    if len(sell_levels) < 5 or len(buy_levels) < 5:
        warnings.append("order_book: eastmoney_depth_incomplete, rebuilt_from_realtime_quote")
        return _fallback_order_book_from_quote(quote)
    return sell_levels, buy_levels


def _fallback_order_book_from_quote(quote: dict[str, Any]) -> tuple[list[dict[str, str]], list[dict[str, str]]]:
    price = _safe_float(quote.get("price"), _safe_float(quote.get("previousClose"), 0.0))
    if price <= 0:
        price = 1.0
    unit = max(round(price * 0.001, 2), 0.01)
    base_volume = max(int(_safe_float(quote.get("volumeRatio"), 1.0) * 120), 1)
    sell = [
        {"label": f"卖{i}", "price": _format_price(price + unit * i), "volume": _format_lots(base_volume * (6 - i)), "isAsk": True}
        for i in range(5, 0, -1)
    ]
    buy = [
        {"label": f"买{i}", "price": _format_price(price - unit * i), "volume": _format_lots(base_volume * (i + 1)), "isAsk": False}
        for i in range(1, 6)
    ]
    return sell, buy


def _money_flow_from_raw(raw: dict[str, Any]) -> dict[str, str]:
    return {
        "mainInflow": _format_cn_money(raw.get("f62")),
        "superLargeOrder": _format_cn_money(raw.get("f66")),
        "largeOrder": _format_cn_money(raw.get("f72")),
        "mediumOrder": _format_cn_money(raw.get("f78")),
        "smallOrder": _format_cn_money(raw.get("f84")),
    }


def _load_daily_kline(client: httpx.Client, security: dict[str, str], warnings: list[str]) -> list[dict[str, Any]]:
    raw = _eastmoney_get_with_retry(
        client,
        EASTMONEY_KLINE_URLS,
        {
            "secid": security["secid"],
            "klt": "101",
            "fqt": "1",
            "lmt": "80",
            "end": "20500101",
            "fields1": "f1,f2,f3,f4,f5,f6",
            "fields2": "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61",
        },
        "daily_kline",
        warnings,
    )
    data = raw.get("data") or {}
    rows: list[dict[str, Any]] = []
    for item in data.get("klines") or []:
        parts = str(item).split(",")
        if len(parts) < 11:
            continue
        rows.append(
            {
                "date": parts[0][5:],
                "open": _safe_float(parts[1]),
                "close": _safe_float(parts[2]),
                "high": _safe_float(parts[3]),
                "low": _safe_float(parts[4]),
                "volume": max(_safe_float(parts[5]) / 10000.0, 0.01),
                "amount": max(_safe_float(parts[6]) / 100000000.0, 0.01),
                "amplitude": _format_percent(parts[7], signed=False),
                "changePercent": _format_percent(parts[8]),
                "changeAmount": _format_signed(parts[9]),
                "turnoverRate": _format_percent(parts[10], signed=False),
            }
        )
    if rows:
        return rows
    trend_rows = _load_daily_kline_from_trends(client, security, warnings)
    if trend_rows:
        warnings.append("daily_kline: rebuilt_from_minute_trends")
    return trend_rows


def _load_daily_kline_from_trends(client: httpx.Client, security: dict[str, str], warnings: list[str]) -> list[dict[str, Any]]:
    raw = _eastmoney_get_with_retry(
        client,
        EASTMONEY_TRENDS_URLS,
        {
            "secid": security["secid"],
            "fields1": "f1,f2,f3,f4,f5,f6,f7,f8",
            "fields2": "f51,f52,f53,f54,f55,f56,f57,f58",
            "iscr": "0",
            "ndays": "5",
        },
        "daily_kline_trends",
        warnings,
    )
    by_date: dict[str, dict[str, Any]] = {}
    for item in (raw.get("data") or {}).get("trends") or []:
        parts = str(item).split(",")
        if len(parts) < 7:
            continue
        day = parts[0][:10]
        row = by_date.setdefault(
            day,
            {
                "date": day[5:],
                "open": _safe_float(parts[1]),
                "close": _safe_float(parts[2]),
                "high": _safe_float(parts[3]),
                "low": _safe_float(parts[4]),
                "volume": 0.0,
                "amount": 0.0,
            },
        )
        row["close"] = _safe_float(parts[2], row["close"])
        row["high"] = max(row["high"], _safe_float(parts[3], row["high"]))
        row["low"] = min(row["low"], _safe_float(parts[4], row["low"]))
        row["volume"] += _safe_float(parts[5])
        row["amount"] += _safe_float(parts[6])

    rows: list[dict[str, Any]] = []
    for day in sorted(by_date):
        row = by_date[day]
        previous = _safe_float(row["open"])
        change_amount = _safe_float(row["close"]) - previous
        change_percent = change_amount / previous * 100 if previous else 0.0
        rows.append(
            {
                "date": row["date"],
                "open": row["open"],
                "close": row["close"],
                "high": row["high"],
                "low": row["low"],
                "volume": max(_safe_float(row["volume"]) / 10000.0, 0.01),
                "amount": max(_safe_float(row["amount"]) / 100000000.0, 0.01),
                "amplitude": "--",
                "changePercent": _format_percent(change_percent),
                "changeAmount": _format_signed(change_amount),
                "turnoverRate": "--",
            }
        )
    return rows[-80:]


def _load_minute_points(client: httpx.Client, security: dict[str, str], warnings: list[str]) -> list[dict[str, Any]]:
    raw = _eastmoney_get_with_retry(
        client,
        EASTMONEY_TRENDS_URLS,
        {
            "secid": security["secid"],
            "fields1": "f1,f2,f3,f4,f5,f6,f7,f8",
            "fields2": "f51,f52,f53,f54,f55,f56,f57,f58",
            "iscr": "0",
            "ndays": "1",
        },
        "minute_points",
        warnings,
    )
    data = raw.get("data") or {}
    parsed: list[tuple[str, float, float, float]] = []
    max_volume = 1.0
    for item in data.get("trends") or []:
        parts = str(item).split(",")
        if len(parts) < 8:
            continue
        time_text = parts[0][-5:]
        price = _safe_float(parts[2])
        volume = _safe_float(parts[5])
        average = _safe_float(parts[7], price)
        parsed.append((time_text, price, average, volume))
        max_volume = max(max_volume, volume)

    return [
        {
            "time": time_text,
            "price": price,
            "average": average,
            "volumeRatio": min(max(volume / max_volume, 0.02), 1.0),
        }
        for time_text, price, average, volume in parsed[-120:]
    ]


def _trade_ticks_from_minute(minute_points: list[dict[str, Any]], quote: dict[str, Any]) -> list[dict[str, Any]]:
    previous_close = _safe_float(quote.get("previousClose"), _safe_float(quote.get("price")))
    ticks: list[dict[str, Any]] = []
    recent = minute_points[-8:]
    for index, point in enumerate(reversed(recent)):
        chronological_index = len(recent) - index - 1
        prev = recent[chronological_index - 1]["price"] if chronological_index > 0 else previous_close
        price = _safe_float(point.get("price"))
        is_buy = price >= _safe_float(prev)
        ticks.append(
            {
                "time": _safe_str(point.get("time")),
                "price": _format_price(price),
                "volume": _format_lots(max(_safe_float(point.get("volumeRatio")) * 1000.0, 1.0)),
                "direction": "买" if is_buy else "卖",
                "isBuy": is_buy,
            }
        )
    return ticks


def _fallback_daily_from_quote(quote: dict[str, Any]) -> list[dict[str, Any]]:
    close = _safe_float(quote.get("price"))
    previous = _safe_float(quote.get("previousClose"), close)
    open_price = _safe_float(quote.get("open"), close)
    high = _safe_float(quote.get("high"), max(open_price, close, previous))
    low = _safe_float(quote.get("low"), min(open_price, close, previous))
    change_amount = close - previous
    change_percent = change_amount / previous * 100 if previous else 0.0
    return [
        {
            "date": datetime.now().strftime("%m-%d"),
            "open": open_price,
            "close": close,
            "high": high,
            "low": low,
            "volume": 0.01,
            "amount": 0.01,
            "amplitude": "--",
            "changePercent": _format_percent(change_percent),
            "changeAmount": _format_signed(change_amount),
            "turnoverRate": _safe_str(quote.get("turnoverRate")),
        }
    ]


def _fallback_minute_from_quote(quote: dict[str, Any]) -> list[dict[str, Any]]:
    close = _safe_float(quote.get("price"))
    previous = _safe_float(quote.get("previousClose"), close)
    open_price = _safe_float(quote.get("open"), previous)
    labels = ["09:30", "09:45", "10:00", "10:30", "11:00", "11:30", "13:00", "13:30", "14:00", "14:30", "14:45", "15:00"]
    points: list[dict[str, Any]] = []
    prices: list[float] = []
    denominator = max(len(labels) - 1, 1)
    for index, label in enumerate(labels):
        progress = index / denominator
        price = open_price + (close - open_price) * progress
        prices.append(price)
        points.append(
            {
                "time": label,
                "price": price,
                "average": sum(prices) / len(prices),
                "volumeRatio": max(0.05, min(1.0, 0.08 + progress * 0.92)),
            }
        )
    return points


def _fundamentals_from_quote(quote: dict[str, Any]) -> list[dict[str, str]]:
    return [
        {"label": "市值", "value": _safe_str(quote.get("totalMarketValue"))},
        {"label": "流通市值", "value": _safe_str(quote.get("floatMarketValue"))},
        {"label": "市盈率", "value": _safe_str(quote.get("peTtm"))},
        {"label": "市净率", "value": _safe_str(quote.get("pb"))},
        {"label": "量比", "value": _safe_str(quote.get("volumeRatio"))},
        {"label": "换手", "value": _safe_str(quote.get("turnoverRate"))},
    ]


def _load_index_snapshot(client: httpx.Client, item: dict[str, str]) -> dict[str, Any] | None:
    try:
        raw = _eastmoney_get(client, EASTMONEY_QUOTE_URL, {"secid": item["secid"], "fields": "f43,f58,f169,f170"}).get("data") or {}
        change_percent = _scaled(raw.get("f170"))
        return {
            "name": item["name"],
            "value": _format_price(_scaled(raw.get("f43"), -1.0)),
            "changePercent": _format_percent(change_percent),
            "isRising": change_percent >= 0,
        }
    except Exception:
        return None


def _load_indices(client: httpx.Client) -> list[dict[str, Any]]:
    rows = [_load_index_snapshot(client, item) for item in INDEX_SECURITIES]
    return [row for row in rows if row is not None]


def _load_watchlist(client: httpx.Client) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for code in WATCHLIST_CODES:
        try:
            security = _resolve_security(client, code)
            raw = _load_quote_raw(client, security)
            quote = _quote_from_raw(raw, security)
            rows.append(
                {
                    "name": quote["name"],
                    "code": quote["code"],
                    "price": quote["price"],
                    "changePercent": quote["changePercent"],
                    "isRising": bool(quote["isRising"]),
                }
            )
        except Exception:
            continue
    return rows


def _market_boards_from_watchlist(watchlist: list[dict[str, Any]], quote: dict[str, Any]) -> list[dict[str, Any]]:
    items = [
        {
            "name": _safe_str(row.get("name")),
            "code": _safe_str(row.get("code")),
            "value": _safe_str(row.get("price")),
            "changePercent": _safe_str(row.get("changePercent")),
            "isRising": bool(row.get("isRising", True)),
        }
        for row in watchlist
    ]
    if not items:
        items = [
            {
                "name": _safe_str(quote.get("name")),
                "code": _safe_str(quote.get("code")),
                "value": _safe_str(quote.get("price")),
                "changePercent": _safe_str(quote.get("changePercent")),
                "isRising": bool(quote.get("isRising", True)),
            }
        ]
    return [{"title": "实时自选榜", "subtitle": "由公开 JSON 实时报价生成", "items": items}]


def _build_crawl_detail_payload(query: str) -> dict[str, Any]:
    warnings = [
        "crawl: eastmoney_public_json",
        "learning_mode: only public JSON endpoints are used",
    ]
    with httpx.Client(timeout=httpx.Timeout(10.0, connect=5.0)) as client:
        security = _resolve_security(client, query)
        raw_quote = _load_quote_raw(client, security)
        quote = _quote_from_raw(raw_quote, security)
        sell_levels, buy_levels = _order_book_from_raw(raw_quote, quote, warnings)
        money_flow = _money_flow_from_raw(raw_quote)
        try:
            k_lines = _load_daily_kline(client, security, warnings)
        except (httpx.HTTPError, ValueError) as exc:
            k_lines = _fallback_daily_from_quote(quote)
            warnings.append(f"daily_kline_failed: {type(exc).__name__}: {exc}")
            warnings.append("daily_kline_fallback: rebuilt_from_quote")
        try:
            minute_points = _load_minute_points(client, security, warnings)
        except (httpx.HTTPError, ValueError) as exc:
            minute_points = _fallback_minute_from_quote(quote)
            warnings.append(f"minute_points_failed: {type(exc).__name__}: {exc}")
            warnings.append("minute_points_fallback: rebuilt_from_quote")
        indices = _load_indices(client)
        watchlist = _load_watchlist(client)

    if not k_lines:
        k_lines = _fallback_daily_from_quote(quote)
        warnings.append("daily_kline_fallback: rebuilt_from_quote_empty")
    if not minute_points:
        minute_points = _fallback_minute_from_quote(quote)
        warnings.append("minute_points_fallback: rebuilt_from_quote_empty")

    trade_ticks = _trade_ticks_from_minute(minute_points, quote)
    name = _safe_str(quote.get("name"), security["name"])
    code = _safe_str(quote.get("code"), security["code"])
    updated_at = datetime.now(timezone.utc).isoformat()

    return {
        "provider": "crawl_eastmoney_public_json",
        "delayed": False,
        "updatedAt": updated_at,
        "dataSourceLabel": f"爬虫教学源 · 东方财富公开JSON · {code}",
        "resolveSource": security["resolveSource"],
        "quote": quote,
        "kLinePoints": k_lines,
        "minutePoints": minute_points,
        "sellLevels": sell_levels,
        "buyLevels": buy_levels,
        "tradeTicks": trade_ticks,
        "moneyFlow": money_flow,
        "fundamentals": _fundamentals_from_quote(quote),
        "indices": indices,
        "watchlist": watchlist,
        "marketBoards": _market_boards_from_watchlist(watchlist, quote),
        "warnings": warnings,
        "aiSummary": (
            f"{name} 当前价 {quote['price']}，涨跌幅 {quote['changePercent']}。"
            f"盘口、分时、日K、成交明细、指数和自选报价均已接入公开 JSON 实时数据；"
            f"成交额 {quote['amount']}，换手 {quote['turnoverRate']}，量比 {quote['volumeRatio']}。"
        ),
    }


def _detail_response(query: str, compat_warning: str | None = None) -> dict[str, Any]:
    key = _cache_key(query)
    fresh = _cache_get(key, FRESH_DETAIL_SECONDS)
    if fresh is not None:
        payload, age = fresh
        if compat_warning:
            payload["warnings"] = list(payload.get("warnings") or []) + [compat_warning]
        return _cached_payload(payload, age)

    try:
        payload = _build_crawl_detail_payload(query)
        quote = payload.get("quote") or {}
        _cache_put(payload, key, quote.get("code"), quote.get("name"))
        if compat_warning:
            payload["warnings"] = list(payload.get("warnings") or []) + [compat_warning]
        return payload
    except HTTPException as exc:
        stale = _cache_get(key, STALE_DETAIL_SECONDS)
        if stale is not None:
            payload, age = stale
            if compat_warning:
                payload["warnings"] = list(payload.get("warnings") or []) + [compat_warning]
            return _cached_payload(payload, age, reason=str(exc.detail))
        raise
    except (httpx.HTTPError, ValueError) as exc:
        stale = _cache_get(key, STALE_DETAIL_SECONDS)
        if stale is not None:
            payload, age = stale
            if compat_warning:
                payload["warnings"] = list(payload.get("warnings") or []) + [compat_warning]
            return _cached_payload(payload, age, reason=f"{type(exc).__name__}: {exc}")
        raise HTTPException(status_code=502, detail=f"东方财富公开 JSON 请求失败：{type(exc).__name__}: {exc}") from exc


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "ok": True,
        "status": "ok",
        "service": "ai-ledger-stock-proxy",
        "dataSource": "eastmoney public json",
        "cacheSize": len(_detail_cache),
        "version": "0.4.0-full-market-payload",
        "fields": [
            "quote", "kLinePoints", "minutePoints", "sellLevels", "buyLevels",
            "tradeTicks", "moneyFlow", "fundamentals", "indices", "watchlist", "marketBoards",
        ],
        "endpoints": [
            "/api/stock/crawl/a-share/detail?query=600519",
            "/api/stock/a-share/detail?query=600519",
            "/api/stock/futu/a-share/detail?query=600519",
        ],
    }


@app.get("/api/stock/crawl/a-share/detail")
def crawl_a_share_detail(query: str = Query(..., description="股票代码或名称，例如 600519 / 贵州茅台")) -> dict[str, Any]:
    return _detail_response(query)


@app.get("/api/stock/a-share/detail")
def a_share_detail(query: str = Query(..., description="股票代码或名称，例如 600519 / 贵州茅台")) -> dict[str, Any]:
    return _detail_response(query)


@app.get("/api/stock/futu/a-share/detail")
def futu_compatible_detail(query: str = Query(..., description="临时兼容 Android 端富途优先路径")) -> dict[str, Any]:
    return _detail_response(query, compat_warning="compat: futu path currently maps to eastmoney crawler learning source")
