from __future__ import annotations

from copy import deepcopy
from datetime import datetime, timedelta
from typing import Any
import json
from urllib.parse import quote
from urllib.request import Request, urlopen

import akshare as ak
import pandas as pd
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(title="AI Ledger A股行情代理", version="0.1.3")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

_spot_cache: tuple[datetime, pd.DataFrame] | None = None
_detail_cache: dict[str, tuple[datetime, dict[str, Any]]] = {}
_last_spot_error: str | None = None

FRESH_DETAIL_SECONDS = 45
STALE_DETAIL_SECONDS = 6 * 60 * 60


def _now() -> datetime:
    return datetime.now()


def _safe_float(value: Any, default: float = 0.0) -> float:
    try:
        if value is None or pd.isna(value):
            return default
        text = str(value).replace(",", "").replace("%", "").strip()
        if text in {"", "--", "-"}:
            return default
        return float(text)
    except Exception:
        return default


def _safe_str(value: Any, default: str = "--") -> str:
    if value is None:
        return default
    try:
        if pd.isna(value):
            return default
    except Exception:
        pass
    text = str(value).strip()
    return text if text else default


def _format_price(value: Any) -> str:
    number = _safe_float(value, -1.0)
    if number < 0:
        return "--"
    return f"{number:.2f}"


def _format_scaled_price(value: Any) -> str:
    number = _safe_float(value, -1.0)
    if number <= 0 or number > 100000000:
        return "--"
    return f"{number / 100.0:.2f}"


def _format_signed(value: Any) -> str:
    number = _safe_float(value, 0.0)
    return f"{number:+.2f}"


def _format_signed_scaled(value: Any) -> str:
    number = _safe_float(value, 0.0) / 100.0
    return f"{number:+.2f}"


def _format_percent(value: Any, signed: bool = False) -> str:
    number = _safe_float(value, 0.0)
    return f"{number:+.2f}%" if signed else f"{number:.2f}%"


def _format_scaled_percent(value: Any, signed: bool = False) -> str:
    number = _safe_float(value, 0.0) / 100.0
    return f"{number:+.2f}%" if signed else f"{number:.2f}%"


def _format_cn_money(value: Any) -> str:
    number = _safe_float(value, 0.0)
    if number <= 0:
        return "--"
    if number >= 1_0000_0000_0000:
        return f"{number / 1_0000_0000_0000:.2f}万亿"
    if number >= 1_0000_0000:
        return f"{number / 1_0000_0000:.2f}亿"
    if number >= 1_0000:
        return f"{number / 1_0000:.2f}万"
    return f"{number:.0f}"


def _market_name(code: str) -> str:
    if code.startswith(("6", "9")):
        return "沪A"
    if code.startswith(("4", "8")):
        return "北交所"
    return "深A"


def _secid_for_code(code: str) -> str:
    return f"1.{code}" if code.startswith(("6", "9")) else f"0.{code}"


def _normalize_code(value: Any) -> str:
    text = _safe_str(value, "")
    digits = "".join(ch for ch in text if ch.isdigit())
    if len(digits) <= 6:
        return digits.zfill(6)
    return digits[-6:]


def _query_key(query: str) -> str:
    return query.strip().lower()


def _code_from_query(query: str) -> str | None:
    digits = "".join(ch for ch in query if ch.isdigit())
    return digits if len(digits) == 6 else None


def _cache_get(key: str | None, max_age_seconds: int) -> tuple[dict[str, Any], int] | None:
    if not key:
        return None
    entry = _detail_cache.get(_query_key(key))
    if entry is None:
        return None
    created_at, payload = entry
    age = int((_now() - created_at).total_seconds())
    if age > max_age_seconds:
        return None
    return deepcopy(payload), age


def _cache_put(payload: dict[str, Any], *keys: str | None) -> None:
    created_at = _now()
    for key in keys:
        if key:
            _detail_cache[_query_key(key)] = (created_at, deepcopy(payload))


def _as_cached_payload(payload: dict[str, Any], age: int, reason: str | None = None) -> dict[str, Any]:
    cached = deepcopy(payload)
    quote_data = cached.get("quote") or {}
    code = quote_data.get("code") or "--"
    cached["dataSourceLabel"] = f"AKShare 缓存行情 · {code} · {age}s前"
    warnings = list(cached.get("warnings") or [])
    warnings.append(f"cache: hit, age={age}s")
    if reason:
        warnings.append(f"realtime_failed: {reason}")
    cached["warnings"] = warnings
    return cached


def _http_json(url: str, timeout: int = 10) -> dict[str, Any]:
    request = Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0",
            "Referer": "https://quote.eastmoney.com/",
            "Accept": "application/json,text/plain,*/*",
        },
    )
    with urlopen(request, timeout=timeout) as response:
        raw = response.read().decode("utf-8", errors="ignore")
    return json.loads(raw)


def _get_spot_df() -> pd.DataFrame:
    global _spot_cache, _last_spot_error
    now = _now()
    if _spot_cache is not None:
        created_at, df = _spot_cache
        if now - created_at < timedelta(seconds=30):
            return df
    try:
        df = ak.stock_zh_a_spot_em()
        if df is None or df.empty:
            raise RuntimeError("AKShare stock_zh_a_spot_em 返回空数据")
        if "代码" in df.columns:
            df = df.copy()
            df["代码"] = df["代码"].map(_normalize_code)
        _spot_cache = (now, df)
        _last_spot_error = None
        return df
    except Exception as exc:
        _last_spot_error = f"{type(exc).__name__}: {exc}"
        if _spot_cache is not None:
            return _spot_cache[1]
        raise HTTPException(status_code=502, detail=f"AKShare 实时行情列表暂不可用：{_last_spot_error}") from exc


def _resolve_stock(query: str) -> dict[str, Any]:
    keyword = query.strip()
    if not keyword:
        raise HTTPException(status_code=400, detail="query 不能为空")

    code_part = _code_from_query(keyword)
    has_direct_code = code_part is not None

    try:
        df = _get_spot_df()
    except HTTPException:
        if has_direct_code and code_part:
            return {"code": code_part, "name": code_part, "row": None, "resolveSource": "code-only"}
        raise

    if "代码" not in df.columns or "名称" not in df.columns:
        raise HTTPException(status_code=502, detail="AKShare 行情源缺少股票代码或名称字段")

    if has_direct_code and code_part:
        hit = df[df["代码"].astype(str) == code_part]
    else:
        code_hit = df[df["代码"].astype(str).str.contains(keyword, case=False, na=False)]
        name_hit = df[df["名称"].astype(str).str.contains(keyword, case=False, na=False)]
        hit = pd.concat([code_hit, name_hit]).drop_duplicates(subset=["代码"])

    if hit.empty:
        if has_direct_code and code_part:
            return {"code": code_part, "name": code_part, "row": None, "resolveSource": "code-only"}
        raise HTTPException(status_code=404, detail=f"没有找到股票：{keyword}")

    row = hit.iloc[0]
    return {"code": _normalize_code(row.get("代码")), "name": _safe_str(row.get("名称")), "row": row, "resolveSource": "akshare-spot"}


def _quote_from_spot_row(code: str, name: str, row: pd.Series) -> dict[str, Any]:
    change_percent = _safe_float(row.get("涨跌幅"), 0.0)
    previous_close = _safe_float(row.get("昨收"), 0.0)
    return {
        "name": name,
        "code": code,
        "market": _market_name(code),
        "price": _format_price(row.get("最新价")),
        "changeAmount": _format_signed(row.get("涨跌额")),
        "changePercent": _format_percent(row.get("涨跌幅"), signed=True),
        "isRising": change_percent >= 0,
        "previousClose": previous_close,
        "high": _format_price(row.get("最高")),
        "low": _format_price(row.get("最低")),
        "open": _format_price(row.get("今开")),
        "totalMarketValue": _format_cn_money(row.get("总市值")),
        "floatMarketValue": _format_cn_money(row.get("流通市值")),
        "volumeRatio": _format_price(row.get("量比")),
        "turnoverRate": _format_percent(row.get("换手率")),
        "peTtm": _format_price(row.get("市盈率-动态")),
        "pb": _format_price(row.get("市净率")),
        "amount": _format_cn_money(row.get("成交额")),
        "popularityRank": "--",
    }


def _quote_from_kline(code: str, name: str, k_lines: list[dict[str, Any]]) -> dict[str, Any]:
    latest = k_lines[-1] if k_lines else {}
    previous = k_lines[-2] if len(k_lines) >= 2 else latest
    close = _safe_float(latest.get("close"), 0.0)
    prev_close = _safe_float(previous.get("close"), close)
    change_amount = close - prev_close
    change_percent = change_amount / prev_close * 100 if prev_close else 0.0
    return {
        "name": name,
        "code": code,
        "market": _market_name(code),
        "price": _format_price(close),
        "changeAmount": _format_signed(change_amount),
        "changePercent": _format_percent(change_percent, signed=True),
        "isRising": change_amount >= 0,
        "previousClose": prev_close,
        "high": _format_price(latest.get("high")),
        "low": _format_price(latest.get("low")),
        "open": _format_price(latest.get("open")),
        "totalMarketValue": "--",
        "floatMarketValue": "--",
        "volumeRatio": "--",
        "turnoverRate": "--",
        "peTtm": "--",
        "pb": "--",
        "amount": _format_cn_money(_safe_float(latest.get("amount"), 0.0) * 100000000.0),
        "popularityRank": "--",
    }


def _load_daily_kline(code: str) -> list[dict[str, Any]]:
    end_date = datetime.now().strftime("%Y%m%d")
    start_date = (datetime.now() - timedelta(days=180)).strftime("%Y%m%d")
    try:
        df = ak.stock_zh_a_hist(symbol=code, period="daily", start_date=start_date, end_date=end_date, adjust="qfq")
    except Exception:
        return []
    if df is None or df.empty:
        return []
    rows = []
    for _, row in df.tail(80).iterrows():
        rows.append(
            {
                "date": _safe_str(row.get("日期"))[-5:],
                "open": _safe_float(row.get("开盘")),
                "close": _safe_float(row.get("收盘")),
                "high": _safe_float(row.get("最高")),
                "low": _safe_float(row.get("最低")),
                "volume": max(_safe_float(row.get("成交量")) / 10000.0, 0.01),
                "amount": max(_safe_float(row.get("成交额")) / 100000000.0, 0.01),
                "changePercent": _format_percent(row.get("涨跌幅"), signed=True),
            }
        )
    return rows


def _load_minute_points(code: str, k_lines: list[dict[str, Any]]) -> list[dict[str, Any]]:
    try:
        end = datetime.now().strftime("%Y-%m-%d 15:00:00")
        start = datetime.now().strftime("%Y-%m-%d 09:30:00")
        df = ak.stock_zh_a_hist_min_em(symbol=code, start_date=start, end_date=end, period="1", adjust="")
        if df is not None and not df.empty:
            close_col = "收盘" if "收盘" in df.columns else df.columns[min(2, len(df.columns) - 1)]
            volume_col = "成交量" if "成交量" in df.columns else None
            max_volume = max((_safe_float(v) for v in df[volume_col]), default=1.0) if volume_col else 1.0
            points = []
            closes: list[float] = []
            for _, row in df.tail(120).iterrows():
                price = _safe_float(row.get(close_col))
                closes.append(price)
                points.append({"time": _safe_str(row.iloc[0])[-8:-3], "price": price, "average": sum(closes) / max(len(closes), 1), "volumeRatio": min(max((_safe_float(row.get(volume_col)) if volume_col else 1.0) / max_volume, 0.02), 1.0)})
            if points:
                return points
    except Exception:
        pass

    recent = k_lines[-12:]
    if not recent:
        return []
    max_volume = max(item["volume"] for item in recent) or 1.0
    points = []
    closes: list[float] = []
    for index, item in enumerate(recent):
        price = _safe_float(item.get("close"))
        closes.append(price)
        points.append({"time": {0: "09:30", 5: "11:30", 6: "13:00", 11: "15:00"}.get(index, ""), "price": price, "average": sum(closes) / max(len(closes), 1), "volumeRatio": min(max(item["volume"] / max_volume, 0.05), 1.0)})
    return points


def _em_quote(code: str) -> dict[str, Any] | None:
    try:
        url = f"https://push2.eastmoney.com/api/qt/stock/get?secid={_secid_for_code(code)}&fields=f43,f44,f45,f46,f48,f50,f57,f58,f60,f116,f117,f162,f168,f169,f170"
        data = _http_json(url, timeout=8).get("data")
        if not data:
            return None
        change_raw = _safe_float(data.get("f169"), 0.0)
        return {
            "name": _safe_str(data.get("f58"), code),
            "code": _safe_str(data.get("f57"), code),
            "market": _market_name(code),
            "price": _format_scaled_price(data.get("f43")),
            "changeAmount": _format_signed_scaled(data.get("f169")),
            "changePercent": _format_scaled_percent(data.get("f170"), signed=True),
            "isRising": change_raw >= 0,
            "previousClose": _safe_float(data.get("f60"), 0.0) / 100.0,
            "high": _format_scaled_price(data.get("f44")),
            "low": _format_scaled_price(data.get("f45")),
            "open": _format_scaled_price(data.get("f46")),
            "totalMarketValue": _format_cn_money(data.get("f116")),
            "floatMarketValue": _format_cn_money(data.get("f117")),
            "volumeRatio": _format_scaled_price(data.get("f50")),
            "turnoverRate": _format_scaled_percent(data.get("f168")),
            "peTtm": _format_scaled_price(data.get("f162")),
            "pb": "--",
            "amount": _format_cn_money(data.get("f48")),
            "popularityRank": "--",
        }
    except Exception:
        return None


def _em_daily_kline(code: str) -> list[dict[str, Any]]:
    try:
        url = f"https://push2his.eastmoney.com/api/qt/stock/kline/get?secid={_secid_for_code(code)}&klt=101&fqt=1&lmt=80&end=20500101&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61"
        data = _http_json(url, timeout=10).get("data") or {}
        klines = data.get("klines") or []
        rows = []
        for item in klines:
            parts = str(item).split(",")
            if len(parts) >= 9:
                rows.append({"date": parts[0][-5:], "open": _safe_float(parts[1]), "close": _safe_float(parts[2]), "high": _safe_float(parts[3]), "low": _safe_float(parts[4]), "volume": max(_safe_float(parts[5]) / 10000.0, 0.01), "amount": max(_safe_float(parts[6]) / 100000000.0, 0.01), "changePercent": _format_percent(parts[8], signed=True)})
        return rows
    except Exception:
        return []


def _em_minute_points(code: str) -> list[dict[str, Any]]:
    try:
        url = f"https://push2his.eastmoney.com/api/qt/stock/trends2/get?secid={_secid_for_code(code)}&fields1=f1,f2,f3,f4,f5,f6,f7,f8&fields2=f51,f52,f53,f54,f55,f56,f57,f58&iscr=0&ndays=1"
        data = _http_json(url, timeout=8).get("data") or {}
        trends = data.get("trends") or []
        points = []
        closes: list[float] = []
        max_volume = 1.0
        parsed = []
        for item in trends[-120:]:
            parts = str(item).split(",")
            if len(parts) >= 6:
                price = _safe_float(parts[2])
                volume = _safe_float(parts[5], 1.0)
                parsed.append((parts[0][-5:], price, volume))
                max_volume = max(max_volume, volume)
        for time_text, price, volume in parsed:
            closes.append(price)
            points.append({"time": time_text, "price": price, "average": sum(closes) / max(len(closes), 1), "volumeRatio": min(max(volume / max_volume, 0.02), 1.0)})
        return points
    except Exception:
        return []


def _build_detail_payload(query: str) -> dict[str, Any]:
    warnings = []
    resolved = _resolve_stock(query)
    code = resolved["code"]
    name = resolved["name"]
    row = resolved.get("row")

    k_lines = _load_daily_kline(code)
    if not k_lines:
        em_k_lines = _em_daily_kline(code)
        if em_k_lines:
            k_lines = em_k_lines
            warnings.append("ak_daily_empty: fast_public_kline_fallback")

    if row is not None:
        quote_data = _quote_from_spot_row(code, name, row)
    else:
        quote_data = _em_quote(code)
        if quote_data is not None:
            name = quote_data.get("name", name)
            warnings.append("ak_spot_empty: fast_public_quote_fallback")
        elif k_lines:
            quote_data = _quote_from_kline(code, name, k_lines)
        else:
            raise HTTPException(status_code=502, detail=f"AKShare 暂未返回 {query} 的报价或 K 线数据")

    minute_points = _load_minute_points(code, k_lines)
    if not minute_points:
        minute_points = _em_minute_points(code)
        if minute_points:
            warnings.append("ak_minute_empty: fast_public_minute_fallback")

    if _last_spot_error:
        warnings.append(f"spot: {_last_spot_error}")
    if not k_lines:
        warnings.append("daily_kline: empty")
    if not minute_points:
        warnings.append("minute_points: empty")

    using_fallback = any("fallback" in item for item in warnings)
    label = f"AKShare主源 + 快速公开源兜底 · {code}" if using_fallback else f"AKShare 免费行情代理 · {code}"
    return {
        "dataSourceLabel": label,
        "quote": quote_data,
        "kLinePoints": k_lines,
        "minutePoints": minute_points,
        "warnings": warnings,
        "aiSummary": f"{name} 当前价 {quote_data['price']}，涨跌幅 {quote_data['changePercent']}。AKShare 为主源；若免费源暂时失败，会使用缓存或快速公开源补齐首屏行情。",
    }


@app.get("/health")
def health() -> dict[str, Any]:
    return {"status": "ok", "dataSource": "AKShare primary + cache + fast fallback", "cacheSize": len(_detail_cache)}


@app.get("/api/stock/a-share/detail")
def a_share_detail(query: str = Query(..., description="股票代码或名称，例如 600519 / 贵州茅台")) -> dict[str, Any]:
    key = _query_key(query)
    code_key = _code_from_query(query)

    fresh = _cache_get(key, FRESH_DETAIL_SECONDS) or _cache_get(code_key, FRESH_DETAIL_SECONDS)
    if fresh is not None:
        payload, age = fresh
        return _as_cached_payload(payload, age)

    try:
        payload = _build_detail_payload(query)
        quote_data = payload.get("quote") or {}
        code = _safe_str(quote_data.get("code"), code_key or "")
        name = _safe_str(quote_data.get("name"), "")
        _cache_put(payload, key, code, name)
        return payload
    except HTTPException as exc:
        stale = _cache_get(key, STALE_DETAIL_SECONDS) or _cache_get(code_key, STALE_DETAIL_SECONDS)
        if stale is not None:
            payload, age = stale
            return _as_cached_payload(payload, age, reason=str(exc.detail))
        raise exc
    except Exception as exc:
        stale = _cache_get(key, STALE_DETAIL_SECONDS) or _cache_get(code_key, STALE_DETAIL_SECONDS)
        if stale is not None:
            payload, age = stale
            return _as_cached_payload(payload, age, reason=f"{type(exc).__name__}: {exc}")
        raise HTTPException(status_code=502, detail=f"行情代理异常：{type(exc).__name__}: {exc}") from exc
