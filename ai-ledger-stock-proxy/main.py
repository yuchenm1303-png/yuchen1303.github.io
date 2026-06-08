from __future__ import annotations

from collections import OrderedDict
from copy import deepcopy
from datetime import datetime, timezone
from time import monotonic
from typing import Any
from urllib.parse import urlencode

import httpx
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware


app = FastAPI(title="AI Ledger A股行情爬虫代理", version="0.8.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

EASTMONEY_QUOTE_URL = "https://push2delay.eastmoney.com/api/qt/stock/get"
EASTMONEY_SEARCH_URL = "https://searchapi.eastmoney.com/api/suggest/get"
EASTMONEY_TOKEN = "44c9d251add88e27b65ed86506f6e5da"
EASTMONEY_CLIST_URLS = [
    "https://push2delay.eastmoney.com/api/qt/clist/get",
    "https://push2.eastmoney.com/api/qt/clist/get",
]
EASTMONEY_TRENDS_URLS = [
    "https://push2delay.eastmoney.com/api/qt/stock/trends2/get",
    "https://push2his.eastmoney.com/api/qt/stock/trends2/get",
    "https://push2.eastmoney.com/api/qt/stock/trends2/get",
]
EASTMONEY_KLINE_URLS = [
    "https://push2his.eastmoney.com/api/qt/stock/kline/get",
    "https://push2.eastmoney.com/api/qt/stock/kline/get",
    "https://push2delay.eastmoney.com/api/qt/stock/kline/get",
]
TENCENT_KLINE_URL = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get"
INDEX_SECURITIES = [
    {"name": "上证", "code": "000001", "secid": "1.000001"},
    {"name": "深成", "code": "399001", "secid": "0.399001"},
    {"name": "创业板", "code": "399006", "secid": "0.399006"},
]
A_STOCK_FS = "m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23"
KLINE_PERIODS = {
    "daily": "101",
    "day": "101",
    "d": "101",
    "weekly": "102",
    "week": "102",
    "w": "102",
    "monthly": "103",
    "month": "103",
    "m": "103",
}
TENCENT_KLINE_PERIODS = {
    "daily": "day",
    "weekly": "week",
    "monthly": "month",
}
FAST_CACHE_SECONDS = 18
REALTIME_CACHE_SECONDS = 1.0
REALTIME_STALE_SECONDS = 20
STALE_CACHE_SECONDS = 6 * 60 * 60
MAX_CACHE_ITEMS = 360
_cache: OrderedDict[str, tuple[float, dict[str, Any]]] = OrderedDict()
_realtime_http_client: httpx.Client | None = None


def _get_realtime_client() -> httpx.Client:
    global _realtime_http_client
    if _realtime_http_client is None:
        _realtime_http_client = httpx.Client(
            timeout=httpx.Timeout(0.95, connect=0.35),
            limits=httpx.Limits(max_connections=20, max_keepalive_connections=10, keepalive_expiry=10.0),
        )
    return _realtime_http_client


@app.on_event("shutdown")
def _close_realtime_client() -> None:
    global _realtime_http_client
    if _realtime_http_client is not None:
        _realtime_http_client.close()
        _realtime_http_client = None


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
    if number == 0:
        return "--"
    sign = "-" if number < 0 else ""
    number = abs(number)
    if number >= 1_0000_0000_0000:
        return f"{sign}{number / 1_0000_0000_0000:.2f}万亿"
    if number >= 1_0000_0000:
        return f"{sign}{number / 1_0000_0000:.2f}亿"
    if number >= 1_0000:
        return f"{sign}{number / 1_0000:.2f}万"
    return f"{sign}{number:.0f}"


def _format_lots(value: Any) -> str:
    lots = _safe_float(value)
    if lots <= 0:
        return "--"
    if lots >= 10000:
        return f"{lots / 10000:.2f}万手"
    return f"{lots:.0f}手"


def _code_from_query(query: str) -> str | None:
    digits = "".join(ch for ch in query if ch.isdigit())
    return digits if len(digits) == 6 else None


def _default_secid_for_code(code: str) -> str:
    return f"1.{code}" if code.startswith(("6", "9")) else f"0.{code}"


def _market_name(secid: str, code: str) -> str:
    if secid.startswith("1."):
        return "沪A"
    if code.startswith(("4", "8", "9")):
        return "北交所"
    return "深A"


def _cache_key(kind: str, query: str, mode: str = "lite") -> str:
    return f"{kind}::{mode.strip().lower()}::{query.strip().lower()}"


def _cache_get(key: str, max_age_seconds: int) -> tuple[dict[str, Any], int] | None:
    entry = _cache.get(key)
    if entry is None:
        return None
    created_at, payload = entry
    age = int(monotonic() - created_at)
    if age > max_age_seconds:
        return None
    _cache.move_to_end(key)
    return deepcopy(payload), age


def _cache_get_seconds(key: str, max_age_seconds: float) -> tuple[dict[str, Any], float] | None:
    entry = _cache.get(key)
    if entry is None:
        return None
    created_at, payload = entry
    age = monotonic() - created_at
    if age > max_age_seconds:
        return None
    _cache.move_to_end(key)
    return deepcopy(payload), age


def _cache_put(key: str, payload: dict[str, Any]) -> None:
    _cache[key] = (monotonic(), deepcopy(payload))
    _cache.move_to_end(key)
    while len(_cache) > MAX_CACHE_ITEMS:
        _cache.popitem(last=False)


def _with_cache_label(payload: dict[str, Any], age: int) -> dict[str, Any]:
    cached = deepcopy(payload)
    quote = cached.get("quote") or {}
    cached["dataSourceLabel"] = f"爬虫教学源 · 东方财富公开JSON缓存 · {quote.get('code', '--')} · {age}s前"
    cached["warnings"] = list(cached.get("warnings") or []) + [f"cache: hit age={age}s"]
    return cached


def _with_realtime_cache_label(payload: dict[str, Any], age: float, stale: bool = False) -> dict[str, Any]:
    cached = deepcopy(payload)
    prefix = "realtime_cache: stale" if stale else "realtime_cache: hit"
    cached["warnings"] = list(cached.get("warnings") or []) + [f"{prefix} age={age:.2f}s"]
    return cached


def _eastmoney_get(client: httpx.Client, url: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125 Safari/537.36",
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


def _eastmoney_get_first(client: httpx.Client, urls: list[str], params: dict[str, Any], label: str, warnings: list[str]) -> dict[str, Any]:
    last_error: str | None = None
    for url in urls:
        try:
            return _eastmoney_get(client, url, params)
        except (httpx.HTTPError, ValueError) as exc:
            last_error = f"{type(exc).__name__}: {exc}"
    warnings.append(f"{label}_failed: {last_error}")
    raise ValueError(last_error or f"{label}: all endpoints failed")


def _tencent_get(client: httpx.Client, params: dict[str, Any]) -> dict[str, Any]:
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125 Safari/537.36",
        "Referer": "https://gu.qq.com/",
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        "Cache-Control": "no-cache",
        "Pragma": "no-cache",
    }
    response = client.get(TENCENT_KLINE_URL, params=params, headers=headers)
    response.raise_for_status()
    data = response.json()
    if not isinstance(data, dict):
        raise ValueError("鑵捐琛屾儏杩斿洖鐨?JSON 涓嶆槸瀵硅薄")
    return data


def _search_security(client: httpx.Client, query: str) -> dict[str, str] | None:
    data = _eastmoney_get(client, f"{EASTMONEY_SEARCH_URL}?{urlencode({'input': query, 'type': '14', 'token': EASTMONEY_TOKEN})}")
    table = data.get("QuotationCodeTable") or {}
    for item in table.get("Data") or []:
        if item.get("Classify") != "AStock":
            continue
        code = _safe_str(item.get("Code"), "")
        quote_id = _safe_str(item.get("QuoteID"), "")
        if len(code) == 6 and "." in quote_id:
            return {"code": code, "name": _safe_str(item.get("Name"), code), "secid": quote_id, "resolveSource": "eastmoney-search"}
    return None


def _search_securities(client: httpx.Client, query: str, limit: int) -> list[dict[str, str]]:
    data = _eastmoney_get(client, f"{EASTMONEY_SEARCH_URL}?{urlencode({'input': query, 'type': '14', 'token': EASTMONEY_TOKEN})}")
    table = data.get("QuotationCodeTable") or {}
    rows: list[dict[str, str]] = []
    for item in table.get("Data") or []:
        if item.get("Classify") != "AStock":
            continue
        code = _safe_str(item.get("Code"), "")
        quote_id = _safe_str(item.get("QuoteID"), "")
        if len(code) == 6 and "." in quote_id:
            rows.append(
                {
                    "code": code,
                    "name": _safe_str(item.get("Name"), code),
                    "secid": quote_id,
                    "market": _safe_str(item.get("SecurityTypeName"), _market_name(quote_id, code)),
                    "pinyin": _safe_str(item.get("PinYin"), ""),
                }
            )
        if len(rows) >= limit:
            break
    return rows


def _resolve_security(client: httpx.Client, query: str) -> dict[str, str]:
    keyword = query.strip()
    if not keyword:
        raise HTTPException(status_code=400, detail="query 不能为空")
    code = _code_from_query(keyword)
    if code is not None:
        return {"code": code, "name": code, "secid": _default_secid_for_code(code), "resolveSource": "code-prefix-fast"}
    searched = _search_security(client, keyword)
    if searched is not None:
        return searched
    raise HTTPException(status_code=404, detail=f"东方财富搜索没有找到 A 股标的：{keyword}")


def _normalize_period(period: str) -> tuple[str, str]:
    key = period.strip().lower()
    klt = KLINE_PERIODS.get(key)
    if not klt:
        allowed = ", ".join(sorted({"daily", "weekly", "monthly"}))
        raise HTTPException(status_code=400, detail=f"period 仅支持 {allowed}")
    canonical = {"101": "daily", "102": "weekly", "103": "monthly"}[klt]
    return canonical, klt


def _quote_fields() -> str:
    base_fields = [
        "f43", "f44", "f45", "f46", "f47", "f48", "f50", "f57", "f58", "f60",
        "f116", "f117", "f162", "f167", "f168", "f169", "f170", "f62", "f66", "f72", "f78", "f84",
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


def _quote_summary_from_raw(data: dict[str, Any], security: dict[str, str]) -> dict[str, Any]:
    quote = _quote_from_raw(data, security)
    return {
        "name": quote["name"],
        "code": quote["code"],
        "market": quote["market"],
        "price": quote["price"],
        "changeAmount": quote["changeAmount"],
        "changePercent": quote["changePercent"],
        "previousClose": quote["previousClose"],
        "high": quote["high"],
        "low": quote["low"],
        "open": quote["open"],
        "amount": quote["amount"],
        "turnoverRate": quote["turnoverRate"],
        "volumeRatio": quote["volumeRatio"],
        "totalMarketValue": quote["totalMarketValue"],
        "floatMarketValue": quote["floatMarketValue"],
        "peTtm": quote["peTtm"],
        "pb": quote["pb"],
        "popularityRank": quote["popularityRank"],
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
            if price is not None and volume > 0:
                rows.append({"label": label, "price": _format_price(price), "volume": _format_lots(volume), "isAsk": is_ask})
        return rows

    sell_levels = read_levels([(31, 32), (33, 34), (35, 36), (37, 38), (39, 40)], ["卖1", "卖2", "卖3", "卖4", "卖5"], True)
    buy_levels = read_levels([(19, 20), (17, 18), (15, 16), (13, 14), (11, 12)], ["买1", "买2", "买3", "买4", "买5"], False)
    if len(sell_levels) < 5 or len(buy_levels) < 5:
        warnings.append("order_book: rebuilt_from_quote")
        return _fallback_order_book_from_quote(quote)
    return sell_levels, buy_levels


def _fallback_order_book_from_quote(quote: dict[str, Any]) -> tuple[list[dict[str, str]], list[dict[str, str]]]:
    price = _safe_float(quote.get("price"), _safe_float(quote.get("previousClose"), 1.0))
    unit = max(round(price * 0.001, 2), 0.01)
    base_volume = max(int(_safe_float(quote.get("volumeRatio"), 1.0) * 120), 1)
    sell = [{"label": f"卖{i}", "price": _format_price(price + unit * i), "volume": _format_lots(base_volume * (6 - i)), "isAsk": True} for i in range(5, 0, -1)]
    buy = [{"label": f"买{i}", "price": _format_price(price - unit * i), "volume": _format_lots(base_volume * (i + 1)), "isAsk": False} for i in range(1, 6)]
    return sell, buy


def _money_flow_from_raw(raw: dict[str, Any]) -> dict[str, str]:
    return {
        "mainInflow": _format_cn_money(raw.get("f62")),
        "superLargeOrder": _format_cn_money(raw.get("f66")),
        "largeOrder": _format_cn_money(raw.get("f72")),
        "mediumOrder": _format_cn_money(raw.get("f78")),
        "smallOrder": _format_cn_money(raw.get("f84")),
    }


def _load_minute_points(client: httpx.Client, security: dict[str, str], quote: dict[str, Any], warnings: list[str]) -> list[dict[str, Any]]:
    raw = _eastmoney_get_first(
        client,
        EASTMONEY_TRENDS_URLS,
        {"secid": security["secid"], "fields1": "f1,f2,f3,f4,f5,f6,f7,f8", "fields2": "f51,f52,f53,f54,f55,f56,f57,f58", "iscr": "0", "ndays": "1"},
        "minute_points",
        warnings,
    )
    parsed: list[tuple[str, float, float, float]] = []
    max_volume = 1.0
    for item in (raw.get("data") or {}).get("trends") or []:
        parts = str(item).split(",")
        if len(parts) < 8:
            continue
        price = _safe_float(parts[2])
        volume = _safe_float(parts[5])
        average = _safe_float(parts[7], price)
        if price <= 0:
            continue
        parsed.append((parts[0][-5:], price, average, volume))
        max_volume = max(max_volume, volume)
    if not parsed:
        warnings.append("minute_points: empty_rebuilt_from_quote")
        return _fallback_minute_from_quote(quote)
    return [{"time": t, "price": p, "average": a, "volumeRatio": min(max(v / max_volume, 0.02), 1.0)} for t, p, a, v in parsed[-120:]]


def _load_realtime_minute_points(client: httpx.Client, security: dict[str, str], quote: dict[str, Any], warnings: list[str]) -> list[dict[str, Any]]:
    try:
        raw = _eastmoney_get(
            client,
            EASTMONEY_TRENDS_URLS[0],
            {"secid": security["secid"], "fields1": "f1,f2,f3,f4,f5,f6,f7,f8", "fields2": "f51,f52,f53,f54,f55,f56,f57,f58", "iscr": "0", "ndays": "1"},
        )
    except (httpx.HTTPError, ValueError) as exc:
        warnings.append(f"minute_points_realtime_failed: {type(exc).__name__}: {exc}")
        return _fallback_minute_from_quote(quote)
    parsed: list[tuple[str, float, float, float]] = []
    max_volume = 1.0
    for item in (raw.get("data") or {}).get("trends") or []:
        parts = str(item).split(",")
        if len(parts) < 8:
            continue
        price = _safe_float(parts[2])
        volume = _safe_float(parts[5])
        average = _safe_float(parts[7], price)
        if price <= 0:
            continue
        parsed.append((parts[0][-5:], price, average, volume))
        max_volume = max(max_volume, volume)
    if not parsed:
        warnings.append("minute_points: empty_rebuilt_from_quote")
        return _fallback_minute_from_quote(quote)
    return [{"time": t, "price": p, "average": a, "volumeRatio": min(max(v / max_volume, 0.02), 1.0)} for t, p, a, v in parsed[-120:]]


def _fallback_minute_from_quote(quote: dict[str, Any]) -> list[dict[str, Any]]:
    close = _safe_float(quote.get("price"))
    previous = _safe_float(quote.get("previousClose"), close)
    open_price = _safe_float(quote.get("open"), previous)
    labels = ["09:30", "09:45", "10:00", "10:30", "11:00", "11:30", "13:00", "13:30", "14:00", "14:30", "14:45", "15:00"]
    points: list[dict[str, Any]] = []
    prices: list[float] = []
    for index, label in enumerate(labels):
        progress = index / max(len(labels) - 1, 1)
        price = open_price + (close - open_price) * progress
        prices.append(price)
        points.append({"time": label, "price": price, "average": sum(prices) / len(prices), "volumeRatio": max(0.05, min(1.0, 0.08 + progress * 0.92))})
    return points


def _trade_ticks_from_minute(minute_points: list[dict[str, Any]], quote: dict[str, Any]) -> list[dict[str, Any]]:
    previous_close = _safe_float(quote.get("previousClose"), _safe_float(quote.get("price")))
    recent = minute_points[-8:]
    ticks: list[dict[str, Any]] = []
    for index, point in enumerate(reversed(recent)):
        chronological_index = len(recent) - index - 1
        previous = recent[chronological_index - 1]["price"] if chronological_index > 0 else previous_close
        price = _safe_float(point.get("price"))
        is_buy = price >= _safe_float(previous)
        ticks.append({"time": _safe_str(point.get("time")), "price": _format_price(price), "volume": _format_lots(max(_safe_float(point.get("volumeRatio")) * 1000.0, 1.0)), "direction": "买" if is_buy else "卖", "isBuy": is_buy})
    return ticks


def _parse_eastmoney_kline_rows(raw: dict[str, Any], limit: int) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for item in (raw.get("data") or {}).get("klines") or []:
        parts = str(item).split(",")
        if len(parts) < 11:
            continue
        rows.append({"date": parts[0], "open": _safe_float(parts[1]), "close": _safe_float(parts[2]), "high": _safe_float(parts[3]), "low": _safe_float(parts[4]), "volume": max(_safe_float(parts[5]), 0.0), "amount": max(_safe_float(parts[6]), 0.0), "amplitude": _safe_str(parts[7]), "changePercent": _safe_str(parts[8]), "changeAmount": _safe_str(parts[9]), "turnoverRate": _safe_str(parts[10])})
    return rows[-limit:]


def _load_eastmoney_history_kline(client: httpx.Client, security: dict[str, str], warnings: list[str], period: str, limit: int) -> list[dict[str, Any]]:
    canonical_period, klt = _normalize_period(period)
    params = {"secid": security["secid"], "klt": klt, "fqt": "1", "lmt": str(limit), "beg": "0", "end": "20500101", "iscca": "1", "fields1": "f1,f2,f3,f4,f5,f6", "fields2": "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61"}
    last_error: str | None = None
    for url in EASTMONEY_KLINE_URLS:
        try:
            rows = _parse_eastmoney_kline_rows(_eastmoney_get(client, url, params), limit)
            if rows:
                return rows
            last_error = f"{url}: empty_klines"
        except (httpx.HTTPError, ValueError) as exc:
            last_error = f"{type(exc).__name__}: {exc}"
    warnings.append(f"{canonical_period}_kline_eastmoney_history_failed: {last_error}")
    return []


def _tencent_symbol(security: dict[str, str]) -> str:
    code = security["code"]
    return f"sh{code}" if security["secid"].startswith("1.") or code.startswith(("6", "9")) else f"sz{code}"


def _load_tencent_history_kline(client: httpx.Client, security: dict[str, str], warnings: list[str], period: str, limit: int) -> list[dict[str, Any]]:
    canonical_period, _ = _normalize_period(period)
    tencent_period = TENCENT_KLINE_PERIODS[canonical_period]
    symbol = _tencent_symbol(security)
    raw = _tencent_get(client, {"param": f"{symbol},{tencent_period},,,{limit},qfq"})
    stock_data = ((raw.get("data") or {}).get(symbol) or {})
    values = stock_data.get(f"qfq{tencent_period}") or stock_data.get(tencent_period) or []
    rows: list[dict[str, Any]] = []
    previous_close = 0.0
    for item in values[-limit:]:
        if not isinstance(item, list) or len(item) < 6:
            continue
        open_price = _safe_float(item[1])
        close = _safe_float(item[2])
        high = _safe_float(item[3])
        low = _safe_float(item[4])
        volume = max(_safe_float(item[5]), 0.0)
        base = previous_close if previous_close > 0 else open_price
        change_amount = close - base if base else 0.0
        change_percent = change_amount / base * 100 if base else 0.0
        amplitude = (high - low) / base * 100 if base else 0.0
        rows.append({"date": _safe_str(item[0]), "open": open_price, "close": close, "high": high, "low": low, "volume": volume, "amount": max(volume * close * 100.0, 0.0), "amplitude": f"{amplitude:.2f}", "changePercent": f"{change_percent:.2f}", "changeAmount": f"{change_amount:.2f}", "turnoverRate": "--"})
        if close > 0:
            previous_close = close
    if rows:
        warnings.append(f"{canonical_period}_kline: eastmoney_history_unavailable_using_tencent_history")
    return rows


def _load_kline(client: httpx.Client, security: dict[str, str], warnings: list[str], period: str = "daily", limit: int = 120) -> list[dict[str, Any]]:
    canonical_period, _ = _normalize_period(period)
    rows = _load_eastmoney_history_kline(client, security, warnings, canonical_period, limit)
    if rows:
        return rows
    try:
        rows = _load_tencent_history_kline(client, security, warnings, canonical_period, limit)
        if rows:
            return rows
    except (httpx.HTTPError, ValueError, KeyError) as exc:
        warnings.append(f"{canonical_period}_kline_tencent_history_failed: {type(exc).__name__}: {exc}")
    if canonical_period == "daily":
        rebuilt = _load_daily_kline_from_trends(client, security, warnings)
        if rebuilt:
            warnings.append("daily_kline: rebuilt_from_minute_trends")
        return rebuilt
    rebuilt = _load_daily_kline_from_trends(client, security, warnings)
    if rebuilt:
        warnings.append(f"{canonical_period}_kline: rebuilt_from_daily_trends")
    return rebuilt


def _load_daily_kline(client: httpx.Client, security: dict[str, str], warnings: list[str], limit: int = 120) -> list[dict[str, Any]]:
    return _load_kline(client, security, warnings, period="daily", limit=limit)


def _load_daily_kline_from_trends(client: httpx.Client, security: dict[str, str], warnings: list[str]) -> list[dict[str, Any]]:
    raw = _eastmoney_get_first(
        client,
        EASTMONEY_TRENDS_URLS,
        {"secid": security["secid"], "fields1": "f1,f2,f3,f4,f5,f6,f7,f8", "fields2": "f51,f52,f53,f54,f55,f56,f57,f58", "iscr": "0", "ndays": "5"},
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
            {"date": day, "open": _safe_float(parts[1]), "close": _safe_float(parts[2]), "high": _safe_float(parts[3]), "low": _safe_float(parts[4]), "volume": 0.0, "amount": 0.0},
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
        rows.append({"date": row["date"], "open": row["open"], "close": row["close"], "high": row["high"], "low": row["low"], "volume": max(_safe_float(row["volume"]), 0.0), "amount": max(_safe_float(row["amount"]), 0.0), "amplitude": "--", "changePercent": f"{change_percent:.2f}", "changeAmount": f"{change_amount:.2f}", "turnoverRate": "--"})
    return rows[-80:]


def _fundamentals_from_quote(quote: dict[str, Any]) -> list[dict[str, str]]:
    return [
        {"label": "市值", "value": _safe_str(quote.get("totalMarketValue"))},
        {"label": "流通市值", "value": _safe_str(quote.get("floatMarketValue"))},
        {"label": "市盈率", "value": _safe_str(quote.get("peTtm"))},
        {"label": "市净率", "value": _safe_str(quote.get("pb"))},
        {"label": "量比", "value": _safe_str(quote.get("volumeRatio"))},
        {"label": "换手", "value": _safe_str(quote.get("turnoverRate"))},
    ]


def _rank_item(name: str, code: str, value: str, change_percent: str, is_rising: bool) -> dict[str, Any]:
    return {"name": name, "code": code, "value": value, "changePercent": change_percent, "isRising": is_rising}


def _clist_items(client: httpx.Client, fs: str, fid: str, page_size: int, warnings: list[str], label: str) -> list[dict[str, Any]]:
    params = {"pn": "1", "pz": str(page_size), "po": "1", "np": "1", "fltt": "2", "invt": "2", "fid": fid, "fs": fs, "fields": "f12,f14,f2,f3,f6,f8,f9,f20,f21,f62,f100,f104,f105,f106"}
    try:
        raw = _eastmoney_get_first(client, EASTMONEY_CLIST_URLS, params, label, warnings)
        return list((raw.get("data") or {}).get("diff") or [])
    except Exception as exc:
        warnings.append(f"{label}_fallback: {type(exc).__name__}: {exc}")
        return []


def _stock_rank_from_diff(item: dict[str, Any]) -> dict[str, Any]:
    change_percent = _format_percent(item.get("f3"))
    return _rank_item(_safe_str(item.get("f14"), "--"), _safe_str(item.get("f12"), "--"), _format_price(item.get("f2")), change_percent, not change_percent.startswith("-"))


def _stock_list_item_from_diff(item: dict[str, Any]) -> dict[str, Any]:
    code = _safe_str(item.get("f12"), "--")
    secid = _default_secid_for_code(code) if len(code) == 6 else "--"
    change_percent = _format_percent(item.get("f3"))
    return {
        "name": _safe_str(item.get("f14"), "--"),
        "code": code,
        "secid": secid,
        "market": _market_name(secid, code) if "." in secid else "--",
        "price": _format_price(item.get("f2")),
        "changePercent": change_percent,
        "isRising": not change_percent.startswith("-"),
        "amount": _format_cn_money(item.get("f6")),
        "turnoverRate": _format_percent(item.get("f8"), signed=False),
        "peTtm": _format_price(item.get("f9")),
        "totalMarketValue": _format_cn_money(item.get("f20")),
        "floatMarketValue": _format_cn_money(item.get("f21")),
        "sector": _safe_str(item.get("f100"), "--"),
    }


def _load_stock_list(client: httpx.Client, page: int, page_size: int, sort: str, warnings: list[str]) -> dict[str, Any]:
    sort_fields = {
        "change": "f3",
        "amount": "f6",
        "turnover": "f8",
        "market_value": "f20",
        "code": "f12",
    }
    fid = sort_fields.get(sort.strip().lower(), "f3")
    params = {
        "pn": str(page),
        "pz": str(page_size),
        "po": "1",
        "np": "1",
        "fltt": "2",
        "invt": "2",
        "fid": fid,
        "fs": A_STOCK_FS,
        "fields": "f12,f14,f2,f3,f6,f8,f9,f20,f21,f100",
    }
    raw = _eastmoney_get_first(client, EASTMONEY_CLIST_URLS, params, "a_share_list", warnings)
    data = raw.get("data") or {}
    return {
        "items": [_stock_list_item_from_diff(item) for item in data.get("diff") or []],
        "total": int(_safe_float(data.get("total"), 0.0)),
        "page": page,
        "pageSize": page_size,
        "sort": sort,
    }


def _sector_rank_from_diff(item: dict[str, Any]) -> dict[str, Any]:
    change_percent = _format_percent(item.get("f3"))
    return _rank_item(_safe_str(item.get("f14"), "板块"), _safe_str(item.get("f12"), "--"), _format_cn_money(item.get("f6")), change_percent, not change_percent.startswith("-"))


def _load_indices(client: httpx.Client, warnings: list[str]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for item in INDEX_SECURITIES:
        try:
            raw = _eastmoney_get(client, EASTMONEY_QUOTE_URL, {"secid": item["secid"], "fields": "f43,f57,f58,f169,f170"}).get("data") or {}
            change_percent = _scaled(raw.get("f170"))
            rows.append({"name": item["name"], "value": _format_price(_scaled(raw.get("f43"), -1.0)), "changePercent": _format_percent(change_percent), "isRising": change_percent >= 0})
        except Exception as exc:
            warnings.append(f"index_{item['code']}_failed: {type(exc).__name__}: {exc}")
    return rows


def _load_market_boards(client: httpx.Client, quote: dict[str, Any], warnings: list[str]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    concept_fs = "m:90+t:3,m:90+t:2"
    hot_items = [_stock_rank_from_diff(item) for item in _clist_items(client, A_STOCK_FS, "f3", 8, warnings, "hot_rank")]
    amount_items = [_stock_rank_from_diff(item) for item in _clist_items(client, A_STOCK_FS, "f6", 8, warnings, "amount_rank")]
    sector_items = [_sector_rank_from_diff(item) for item in _clist_items(client, concept_fs, "f3", 8, warnings, "sector_rank")]
    current = _rank_item(_safe_str(quote.get("name")), _safe_str(quote.get("code")), _safe_str(quote.get("price")), _safe_str(quote.get("changePercent")), bool(quote.get("isRising", True)))
    hot_items = hot_items or [current]
    amount_items = amount_items or [current]
    sector_items = sector_items or [_rank_item("板块数据暂缺", "--", "--", "--", True)]
    watchlist = [current] + [{"name": item["name"], "code": item["code"], "price": item["value"], "changePercent": item["changePercent"], "isRising": item["isRising"]} for item in hot_items[:5] if item["code"] != current["code"]]
    boards = [
        {"title": "涨幅热榜", "subtitle": "东方财富公开 JSON · A股涨幅排行", "items": hot_items[:6]},
        {"title": "成交额榜", "subtitle": "东方财富公开 JSON · A股成交额排行", "items": amount_items[:6]},
        {"title": "板块热度", "subtitle": "东方财富公开 JSON · 概念/行业板块", "items": sector_items[:6]},
    ]
    return watchlist[:6], boards


def _build_detail_payload(query: str, mode: str, include_market: bool) -> dict[str, Any]:
    warnings = ["crawl: eastmoney_public_json", f"mode: {mode}"]
    timeout = httpx.Timeout(6.0, connect=2.0)
    with httpx.Client(timeout=timeout) as client:
        security = _resolve_security(client, query)
        raw_quote = _load_quote_raw(client, security)
        quote = _quote_from_raw(raw_quote, security)
        sell_levels, buy_levels = _order_book_from_raw(raw_quote, quote, warnings)
        money_flow = _money_flow_from_raw(raw_quote)
        try:
            minute_points = _load_minute_points(client, security, quote, warnings)
        except Exception as exc:
            warnings.append(f"minute_points_fallback: {type(exc).__name__}: {exc}")
            minute_points = _fallback_minute_from_quote(quote)
        k_lines: list[dict[str, Any]] = []
        if mode == "full":
            try:
                k_lines = _load_daily_kline(client, security, warnings, limit=120)
            except Exception as exc:
                warnings.append(f"daily_kline_failed: {type(exc).__name__}: {exc}")
        indices: list[dict[str, Any]] = []
        watchlist: list[dict[str, Any]] = []
        market_boards: list[dict[str, Any]] = []
        if include_market:
            indices = _load_indices(client, warnings)
            watchlist, market_boards = _load_market_boards(client, quote, warnings)
    trade_ticks = _trade_ticks_from_minute(minute_points, quote)
    name = _safe_str(quote.get("name"), security["name"])
    code = _safe_str(quote.get("code"), security["code"])
    return {
        "provider": "crawl_eastmoney_public_json",
        "mode": mode,
        "delayed": False,
        "updatedAt": datetime.now(timezone.utc).isoformat(),
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
        "marketBoards": market_boards,
        "warnings": warnings,
        "aiSummary": f"{name} 当前价 {quote['price']}，涨跌幅 {quote['changePercent']}。已接入报价、分时、盘口和成交数据；成交额 {quote['amount']}，换手 {quote['turnoverRate']}，量比 {quote['volumeRatio']}。",
    }


def _build_market_payload(query: str) -> dict[str, Any]:
    warnings = ["crawl: eastmoney_market_overview"]
    timeout = httpx.Timeout(6.0, connect=2.0)
    with httpx.Client(timeout=timeout) as client:
        security = _resolve_security(client, query)
        raw_quote = _load_quote_raw(client, security)
        quote = _quote_from_raw(raw_quote, security)
        indices = _load_indices(client, warnings)
        watchlist, boards = _load_market_boards(client, quote, warnings)
    return {
        "provider": "crawl_eastmoney_public_json",
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "dataSourceLabel": f"爬虫教学源 · 东方财富市场概览 · {quote.get('code', '--')}",
        "indices": indices,
        "watchlist": watchlist,
        "marketBoards": boards,
        "warnings": warnings,
    }


def _build_list_payload(page: int, page_size: int, sort: str) -> dict[str, Any]:
    warnings = ["crawl: eastmoney_a_share_list"]
    with httpx.Client(timeout=httpx.Timeout(8.0, connect=3.0)) as client:
        data = _load_stock_list(client, page, page_size, sort, warnings)
    return {
        "provider": "crawl_eastmoney_public_json",
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "dataSourceLabel": "爬虫教学源 · 东方财富A股股票池",
        "items": data["items"],
        "total": data["total"],
        "page": data["page"],
        "pageSize": data["pageSize"],
        "sort": data["sort"],
        "warnings": warnings,
    }


def _build_search_payload(query: str, limit: int) -> dict[str, Any]:
    warnings = ["crawl: eastmoney_a_share_search"]
    keyword = query.strip()
    if not keyword:
        raise HTTPException(status_code=400, detail="query 不能为空")
    with httpx.Client(timeout=httpx.Timeout(6.0, connect=2.0)) as client:
        items = _search_securities(client, keyword, limit)
        if not items:
            code = _code_from_query(keyword)
            if code:
                security = _resolve_security(client, code)
                items = [{"code": security["code"], "name": security["name"], "secid": security["secid"], "market": _market_name(security["secid"], security["code"]), "pinyin": ""}]
    return {
        "provider": "crawl_eastmoney_public_json",
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "dataSourceLabel": "爬虫教学源 · 东方财富A股搜索",
        "query": keyword,
        "items": items,
        "warnings": warnings,
    }


def _build_quotes_payload(codes: str) -> dict[str, Any]:
    warnings = ["realtime: eastmoney_a_share_quotes"]
    raw_codes = [item.strip() for item in codes.replace("，", ",").split(",") if item.strip()]
    if not raw_codes:
        raise HTTPException(status_code=400, detail="codes 不能为空，例如 600519,000001")
    if len(raw_codes) > 50:
        raise HTTPException(status_code=400, detail="单次最多查询 50 个代码")
    quotes: list[dict[str, Any]] = []
    client = _get_realtime_client()
    for item in raw_codes:
        try:
            security = _resolve_security(client, item)
            raw_quote = _load_quote_raw(client, security)
            quotes.append(_quote_summary_from_raw(raw_quote, security))
        except Exception as exc:
            warnings.append(f"quote_{item}_failed: {type(exc).__name__}: {exc}")
    if not quotes:
        raise ValueError("all realtime quotes failed")
    return {
        "provider": "crawl_eastmoney_public_json",
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "dataSourceLabel": "爬虫教学源 · 东方财富A股批量报价",
        "items": quotes,
        "requested": raw_codes,
        "warnings": warnings,
    }


def _build_minute_payload(query: str) -> dict[str, Any]:
    warnings = ["realtime: eastmoney_a_share_minute"]
    client = _get_realtime_client()
    security = _resolve_security(client, query)
    raw_quote = _load_quote_raw(client, security)
    quote = _quote_from_raw(raw_quote, security)
    sell_levels, buy_levels = _order_book_from_raw(raw_quote, quote, warnings)
    points = _load_realtime_minute_points(client, security, quote, warnings)
    trade_ticks = _trade_ticks_from_minute(points, quote)
    warnings.append("trade_ticks: rebuilt_from_minute_tail")
    return {
        "provider": "crawl_eastmoney_public_json",
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "dataSourceLabel": f"爬虫教学源 · 东方财富A股分时 · {quote.get('code', '--')}",
        "quote": quote,
        "minutePoints": points,
        "sellLevels": sell_levels,
        "buyLevels": buy_levels,
        "tradeTicks": trade_ticks,
        "warnings": warnings,
    }


def _cached_response(kind: str, query: str, mode: str, builder) -> dict[str, Any]:
    key = _cache_key(kind, query, mode)
    fresh = _cache_get(key, FAST_CACHE_SECONDS)
    if fresh is not None:
        payload, age = fresh
        return _with_cache_label(payload, age)
    try:
        payload = builder()
        _cache_put(key, payload)
        return payload
    except HTTPException:
        stale = _cache_get(key, STALE_CACHE_SECONDS)
        if stale is not None:
            payload, age = stale
            return _with_cache_label(payload, age)
        raise
    except (httpx.HTTPError, ValueError) as exc:
        stale = _cache_get(key, STALE_CACHE_SECONDS)
        if stale is not None:
            payload, age = stale
            return _with_cache_label(payload, age)
        raise HTTPException(status_code=502, detail=f"东方财富公开 JSON 请求失败：{type(exc).__name__}: {exc}") from exc


def _realtime_cached_response(kind: str, query: str, mode: str, builder) -> dict[str, Any]:
    key = _cache_key(kind, query, mode)
    fresh = _cache_get_seconds(key, REALTIME_CACHE_SECONDS)
    if fresh is not None:
        payload, age = fresh
        return _with_realtime_cache_label(payload, age)
    try:
        payload = builder()
        _cache_put(key, payload)
        return payload
    except HTTPException as exc:
        stale = _cache_get_seconds(key, REALTIME_STALE_SECONDS)
        if stale is not None:
            payload, age = stale
            cached = _with_realtime_cache_label(payload, age, stale=True)
            cached["warnings"] = list(cached.get("warnings") or []) + [f"realtime_upstream_failed: HTTPException: {exc.detail}"]
            return cached
        raise
    except (httpx.HTTPError, ValueError) as exc:
        stale = _cache_get_seconds(key, REALTIME_STALE_SECONDS)
        if stale is not None:
            payload, age = stale
            cached = _with_realtime_cache_label(payload, age, stale=True)
            cached["warnings"] = list(cached.get("warnings") or []) + [f"realtime_upstream_failed: {type(exc).__name__}: {exc}"]
            return cached
        raise HTTPException(status_code=502, detail=f"realtime quote/minute request failed: {type(exc).__name__}: {exc}") from exc


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "ok": True,
        "status": "ok",
        "service": "ai-ledger-stock-proxy",
        "dataSource": "eastmoney public json",
        "cacheSize": len(_cache),
        "version": "0.8.0-a-share-full-lite",
        "endpoints": [
            "/api/stock/crawl/a-share/list?page=1&pageSize=100",
            "/api/stock/crawl/a-share/search?query=贵州茅台",
            "/api/stock/crawl/a-share/quotes?codes=600519,000001",
            "/api/stock/crawl/a-share/detail?query=600519&mode=lite",
            "/api/stock/crawl/a-share/detail?query=600519&mode=full",
            "/api/stock/crawl/a-share/kline?query=600519&period=daily",
            "/api/stock/crawl/a-share/minute?query=600519",
            "/api/stock/crawl/a-share/market/overview?query=600519",
        ],
    }


@app.get("/api/stock/crawl/a-share/list")
def crawl_a_share_list(
    page: int = Query(1, ge=1, le=200),
    pageSize: int = Query(100, ge=10, le=500),
    sort: str = Query("change", description="change/amount/turnover/market_value/code"),
) -> dict[str, Any]:
    return _cached_response("list", f"{page}:{pageSize}", sort, lambda: _build_list_payload(page, pageSize, sort))


@app.get("/api/stock/crawl/a-share/search")
def crawl_a_share_search(
    query: str = Query(..., description="股票代码、名称或拼音，例如 600519 / 贵州茅台"),
    limit: int = Query(10, ge=1, le=30),
) -> dict[str, Any]:
    return _cached_response("search", query, str(limit), lambda: _build_search_payload(query, limit))


@app.get("/api/stock/crawl/a-share/quotes")
def crawl_a_share_quotes(
    codes: str = Query(..., description="逗号分隔股票代码，例如 600519,000001,300750"),
) -> dict[str, Any]:
    return _realtime_cached_response("quotes", codes, "batch", lambda: _build_quotes_payload(codes))


@app.get("/api/stock/crawl/a-share/detail")
def crawl_a_share_detail(
    query: str = Query(..., description="股票代码或名称，例如 600519 / 贵州茅台"),
    mode: str = Query("lite", description="lite 首屏快返回；full 补日K"),
    includeMarket: bool = Query(False, description="是否把首页市场概览一起返回，默认关闭以保证详情首屏快返回"),
) -> dict[str, Any]:
    normalized_mode = "full" if mode == "full" else "lite"
    return _cached_response("detail_market" if includeMarket else "detail", query, normalized_mode, lambda: _build_detail_payload(query, normalized_mode, includeMarket))


@app.get("/api/stock/crawl/a-share/kline")
def crawl_a_share_kline(
    query: str = Query(..., description="股票代码或名称，例如 600519 / 贵州茅台"),
    period: str = Query("daily", description="daily/weekly/monthly"),
    limit: int = Query(120, ge=20, le=240),
) -> dict[str, Any]:
    def build() -> dict[str, Any]:
        canonical_period, _ = _normalize_period(period)
        warnings = [f"crawl: eastmoney_{canonical_period}_kline"]
        with httpx.Client(timeout=httpx.Timeout(6.0, connect=2.0)) as client:
            security = _resolve_security(client, query)
            points = _load_kline(client, security, warnings, period=canonical_period, limit=limit)
        return {"provider": "crawl_eastmoney_public_json", "updatedAt": datetime.now(timezone.utc).isoformat(), "period": canonical_period, "klinePoints": points, "count": len(points), "warnings": warnings}

    return _cached_response("kline", query, f"{period}:{limit}", build)


@app.get("/api/stock/crawl/a-share/minute")
def crawl_a_share_minute(
    query: str = Query(..., description="股票代码或名称，例如 600519 / 贵州茅台"),
) -> dict[str, Any]:
    return _realtime_cached_response("minute", query, "1d", lambda: _build_minute_payload(query))


@app.get("/api/stock/crawl/a-share/market/overview")
def crawl_a_share_market_overview(
    query: str = Query("600396", description="用于生成自选候选池的当前关注股票"),
) -> dict[str, Any]:
    return _cached_response("market", query, "overview", lambda: _build_market_payload(query))


@app.get("/api/stock/a-share/detail")
def a_share_detail(
    query: str = Query(..., description="兼容旧 Android 路径"),
    mode: str = Query("lite"),
) -> dict[str, Any]:
    normalized_mode = "full" if mode == "full" else "lite"
    return _cached_response("detail", query, normalized_mode, lambda: _build_detail_payload(query, normalized_mode, False))


@app.get("/api/stock/a-share/list")
def a_share_list(page: int = Query(1, ge=1, le=200), pageSize: int = Query(100, ge=10, le=500), sort: str = Query("change")) -> dict[str, Any]:
    return crawl_a_share_list(page=page, pageSize=pageSize, sort=sort)


@app.get("/api/stock/a-share/search")
def a_share_search(query: str = Query(...), limit: int = Query(10, ge=1, le=30)) -> dict[str, Any]:
    return crawl_a_share_search(query=query, limit=limit)


@app.get("/api/stock/a-share/quotes")
def a_share_quotes(codes: str = Query(...)) -> dict[str, Any]:
    return crawl_a_share_quotes(codes=codes)


@app.get("/api/stock/a-share/kline")
def a_share_kline(query: str = Query(...), period: str = Query("daily"), limit: int = Query(120, ge=20, le=240)) -> dict[str, Any]:
    return crawl_a_share_kline(query=query, period=period, limit=limit)


@app.get("/api/stock/a-share/minute")
def a_share_minute(query: str = Query(...)) -> dict[str, Any]:
    return crawl_a_share_minute(query=query)


@app.get("/api/stock/futu/a-share/detail")
def futu_compatible_detail(
    query: str = Query(..., description="兼容旧富途路径"),
    mode: str = Query("lite"),
) -> dict[str, Any]:
    normalized_mode = "full" if mode == "full" else "lite"
    payload = _cached_response("detail", query, normalized_mode, lambda: _build_detail_payload(query, normalized_mode, False))
    payload["warnings"] = list(payload.get("warnings") or []) + ["compat: futu path maps to eastmoney crawler learning source"]
    return payload
