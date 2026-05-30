from __future__ import annotations

from datetime import datetime, timedelta
from typing import Any

import akshare as ak
import pandas as pd
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(title="AI Ledger A股行情代理", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

_spot_cache: tuple[datetime, pd.DataFrame] | None = None


def _now() -> datetime:
    return datetime.now()


def _safe_float(value: Any, default: float = 0.0) -> float:
    try:
        if pd.isna(value):
            return default
        return float(value)
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


def _format_signed(value: Any) -> str:
    number = _safe_float(value, 0.0)
    return f"{number:+.2f}"


def _format_percent(value: Any, signed: bool = False) -> str:
    number = _safe_float(value, 0.0)
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


def _get_spot_df() -> pd.DataFrame:
    global _spot_cache
    now = _now()
    if _spot_cache is not None:
        created_at, df = _spot_cache
        if now - created_at < timedelta(seconds=8):
            return df
    df = ak.stock_zh_a_spot_em()
    _spot_cache = (now, df)
    return df


def _resolve_stock(query: str) -> dict[str, Any]:
    keyword = query.strip()
    if not keyword:
        raise HTTPException(status_code=400, detail="query 不能为空")

    df = _get_spot_df()
    if "代码" not in df.columns or "名称" not in df.columns:
        raise HTTPException(status_code=502, detail="行情源缺少股票代码或名称字段")

    code_part = "".join(ch for ch in keyword if ch.isdigit())
    if len(code_part) == 6:
        hit = df[df["代码"].astype(str) == code_part]
    else:
        code_hit = df[df["代码"].astype(str).str.contains(keyword, case=False, na=False)]
        name_hit = df[df["名称"].astype(str).str.contains(keyword, case=False, na=False)]
        hit = pd.concat([code_hit, name_hit]).drop_duplicates(subset=["代码"])

    if hit.empty:
        raise HTTPException(status_code=404, detail=f"没有找到股票：{keyword}")

    row = hit.iloc[0]
    return {"code": _safe_str(row.get("代码")), "name": _safe_str(row.get("名称")), "row": row}


def _quote_from_row(code: str, name: str, row: pd.Series) -> dict[str, Any]:
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


def _load_daily_kline(code: str) -> list[dict[str, Any]]:
    end_date = datetime.now().strftime("%Y%m%d")
    start_date = (datetime.now() - timedelta(days=150)).strftime("%Y%m%d")
    try:
        df = ak.stock_zh_a_hist(symbol=code, period="daily", start_date=start_date, end_date=end_date, adjust="qfq")
    except Exception:
        return []
    if df.empty:
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
    # AKShare 分钟接口在不同版本中字段可能变化；失败时用最近 K 线生成可视化轮廓，保证 App 不崩。
    try:
        end = datetime.now().strftime("%Y-%m-%d 15:00:00")
        start = datetime.now().strftime("%Y-%m-%d 09:30:00")
        df = ak.stock_zh_a_hist_min_em(symbol=code, start_date=start, end_date=end, period="1", adjust="")
        if not df.empty:
            close_col = "收盘" if "收盘" in df.columns else df.columns[2]
            volume_col = "成交量" if "成交量" in df.columns else None
            max_volume = max((_safe_float(v) for v in df[volume_col]), default=1.0) if volume_col else 1.0
            points = []
            closes: list[float] = []
            for _, row in df.tail(120).iterrows():
                price = _safe_float(row.get(close_col))
                closes.append(price)
                points.append(
                    {
                        "time": _safe_str(row.iloc[0])[-8:-3],
                        "price": price,
                        "average": sum(closes) / max(len(closes), 1),
                        "volumeRatio": min(max((_safe_float(row.get(volume_col)) if volume_col else 1.0) / max_volume, 0.02), 1.0),
                    }
                )
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
        points.append(
            {
                "time": {0: "09:30", 5: "11:30", 6: "13:00", 11: "15:00"}.get(index, ""),
                "price": price,
                "average": sum(closes) / max(len(closes), 1),
                "volumeRatio": min(max(item["volume"] / max_volume, 0.05), 1.0),
            }
        )
    return points


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "dataSource": "AKShare + optional future Tushare"}


@app.get("/api/stock/a-share/detail")
def a_share_detail(query: str = Query(..., description="股票代码或名称，例如 600519 / 贵州茅台")) -> dict[str, Any]:
    resolved = _resolve_stock(query)
    code = resolved["code"]
    name = resolved["name"]
    row = resolved["row"]
    quote = _quote_from_row(code, name, row)
    k_lines = _load_daily_kline(code)
    minute_points = _load_minute_points(code, k_lines)
    return {
        "dataSourceLabel": f"AKShare 免费行情代理 · {code}",
        "quote": quote,
        "kLinePoints": k_lines,
        "minutePoints": minute_points,
        "aiSummary": f"{name} 当前价 {quote['price']}，涨跌幅 {quote['changePercent']}。报价、日K和分时轮廓来自 AKShare 代理；后续可继续扩展盘口、资金流、龙虎榜和公告。",
    }
