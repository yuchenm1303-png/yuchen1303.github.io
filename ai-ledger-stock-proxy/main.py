from __future__ import annotations

from copy import deepcopy
from datetime import datetime, timezone
from time import monotonic
from typing import Any
from urllib.parse import urlencode

import httpx
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware


app = FastAPI(title="AI Ledger A股行情爬虫教学代理", version="0.3.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

EASTMONEY_QUOTE_URL = "https://push2delay.eastmoney.com/api/qt/stock/get"
EASTMONEY_KLINE_URL = "https://push2his.eastmoney.com/api/qt/stock/kline/get"
EASTMONEY_TRENDS_URL = "https://push2his.eastmoney.com/api/qt/stock/trends2/get"
EASTMONEY_SEARCH_URL = "https://searchapi.eastmoney.com/api/suggest/get"
EASTMONEY_TOKEN = "44c9d251add88e27b65ed86506f6e5da"

FRESH_DETAIL_SECONDS = 30
STALE_DETAIL_SECONDS = 6 * 60 * 60

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
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": "https://quote.eastmoney.com/",
        "Accept": "application/json,text/plain,*/*",
    }
    response = client.get(url, params=params, headers=headers)
    response.raise_for_status()
    data = response.json()
    if not isinstance(data, dict):
        raise ValueError("东方财富返回的 JSON 不是对象")
    return data


def _search_security(client: httpx.Client, query: str) -> dict[str, str] | None:
    search_url = f"{EASTMONEY_SEARCH_URL}?{urlencode({'input': query, 'type': '14', 'token': EASTMONEY_TOKEN})}"
    data = _eastmoney_get(
        client,
        search_url,
        None,
    )
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


def _load_quote(client: httpx.Client, security: dict[str, str]) -> dict[str, Any]:
    fields = ",".join(
        [
            "f43",
            "f44",
            "f45",
            "f46",
            "f47",
            "f48",
            "f50",
            "f57",
            "f58",
            "f60",
            "f116",
            "f117",
            "f162",
            "f167",
            "f168",
            "f169",
            "f170",
        ]
    )
    raw = _eastmoney_get(client, EASTMONEY_QUOTE_URL, {"secid": security["secid"], "fields": fields})
    data = raw.get("data")
    if not data:
        raise HTTPException(status_code=502, detail=f"东方财富 quote 暂无数据：{security['secid']}")

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


def _load_daily_kline(client: httpx.Client, security: dict[str, str]) -> list[dict[str, Any]]:
    raw = _eastmoney_get(
        client,
        EASTMONEY_KLINE_URL,
        {
            "secid": security["secid"],
            "klt": "101",
            "fqt": "1",
            "lmt": "80",
            "end": "20500101",
            "fields1": "f1,f2,f3,f4,f5,f6",
            "fields2": "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61",
        },
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
    return rows


def _load_minute_points(client: httpx.Client, security: dict[str, str]) -> list[dict[str, Any]]:
    raw = _eastmoney_get(
        client,
        EASTMONEY_TRENDS_URL,
        {
            "secid": security["secid"],
            "fields1": "f1,f2,f3,f4,f5,f6,f7,f8",
            "fields2": "f51,f52,f53,f54,f55,f56,f57,f58",
            "iscr": "0",
            "ndays": "1",
        },
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


def _fundamentals_from_quote(quote: dict[str, Any]) -> list[dict[str, str]]:
    return [
        {"label": "市值", "value": _safe_str(quote.get("totalMarketValue"))},
        {"label": "流通市值", "value": _safe_str(quote.get("floatMarketValue"))},
        {"label": "市盈率", "value": _safe_str(quote.get("peTtm"))},
        {"label": "市净率", "value": _safe_str(quote.get("pb"))},
        {"label": "量比", "value": _safe_str(quote.get("volumeRatio"))},
        {"label": "换手", "value": _safe_str(quote.get("turnoverRate"))},
    ]


def _build_crawl_detail_payload(query: str) -> dict[str, Any]:
    warnings = [
        "crawl: eastmoney_public_json",
        "learning_mode: only public JSON endpoints are used",
    ]
    with httpx.Client(timeout=httpx.Timeout(10.0, connect=5.0)) as client:
        security = _resolve_security(client, query)
        quote = _load_quote(client, security)
        try:
            k_lines = _load_daily_kline(client, security)
        except (httpx.HTTPError, ValueError) as exc:
            k_lines = []
            warnings.append(f"daily_kline_failed: {type(exc).__name__}: {exc}")
        try:
            minute_points = _load_minute_points(client, security)
        except (httpx.HTTPError, ValueError) as exc:
            minute_points = []
            warnings.append(f"minute_points_failed: {type(exc).__name__}: {exc}")

    if not k_lines:
        warnings.append("daily_kline: empty")
    if not minute_points:
        warnings.append("minute_points: empty")

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
        "fundamentals": _fundamentals_from_quote(quote),
        "warnings": warnings,
        "aiSummary": (
            f"{name} 当前价 {quote['price']}，涨跌幅 {quote['changePercent']}。"
            "本接口为本地教学爬虫方案，直接请求东方财富公开 JSON，解析 quote、日K 和分时数据。"
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
