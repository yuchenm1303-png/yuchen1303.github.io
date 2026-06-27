from __future__ import annotations

import importlib.util
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi import FastAPI


MODULE_PATH = Path(__file__).with_name("market_home_server.py")


def _fake_legacy() -> types.ModuleType:
    legacy = types.ModuleType("main")
    legacy.app = FastAPI()
    legacy.FAST_CACHE_SECONDS = 18
    legacy.STALE_CACHE_SECONDS = 6 * 60 * 60
    legacy.A_STOCK_FS = "a-share"
    legacy.SECTOR_FS = {"industry": "industry"}
    legacy._cache = {}
    legacy._cache_key = lambda *parts: ":".join(parts)
    legacy._cache_get = lambda key, max_age: None
    legacy._cache_put = lambda key, value: legacy._cache.__setitem__(key, value)
    legacy._payload_has_real_items = lambda payload: bool(payload.get("items"))
    legacy._module_payload = lambda status, source, source_url_type, items=None, is_derived=False, warnings=None, cache_age_ms=0: {
        "status": status,
        "source": source,
        "sourceUrlType": source_url_type,
        "items": [] if items is None else items,
        "isDerived": is_derived,
        "warnings": warnings or [],
        "cacheAgeMs": cache_age_ms,
    }
    legacy._safe_str = lambda value, default="": str(value) if value not in (None, "") else default
    legacy._safe_float = lambda value, default=0.0: float(value or default)
    legacy._format_price = str
    legacy._format_signed = str
    legacy._format_percent = lambda value, signed=True: str(value)
    legacy._format_cn_money = str
    legacy._format_lots = str
    legacy._ranking_item = lambda item, rank: {
        "rank": rank,
        "code": item.get("f12"),
        "name": item.get("f14"),
        "price": item.get("f2"),
        "changePercent": item.get("f3"),
    }
    legacy._load_ranking = lambda *args: legacy._module_payload(
        "unavailable", "none", "none"
    )
    legacy._unavailable_module = lambda name: legacy._module_payload(
        "unavailable", "none", "none", warnings=[name]
    )
    legacy._eastmoney_get_first = lambda *args, **kwargs: {"data": {"diff": []}}
    legacy._clist_items = lambda *args, **kwargs: []
    return legacy


def _load_module():
    legacy = _fake_legacy()
    previous_main = sys.modules.get("main")
    previous_module = sys.modules.get("market_home_server_under_test")
    sys.modules["main"] = legacy
    try:
        spec = importlib.util.spec_from_file_location(
            "market_home_server_under_test", MODULE_PATH
        )
        assert spec and spec.loader
        module = importlib.util.module_from_spec(spec)
        sys.modules["market_home_server_under_test"] = module
        spec.loader.exec_module(module)
        return module
    finally:
        if previous_main is None:
            sys.modules.pop("main", None)
        else:
            sys.modules["main"] = previous_main
        if previous_module is None:
            sys.modules.pop("market_home_server_under_test", None)
        else:
            sys.modules["market_home_server_under_test"] = previous_module


class MarketHomeRuntimeTest(unittest.TestCase):
    def setUp(self) -> None:
        self.module = _load_module()

    def test_cold_request_never_runs_synchronous_crawl(self) -> None:
        with (
            patch.object(self.module.legacy, "_cache_get", return_value=None),
            patch.object(
                self.module,
                "_start_market_home_background_refresh",
                return_value=True,
            ),
            patch.object(
                self.module,
                "_build_market_home_fast",
                side_effect=AssertionError("synchronous crawl must not run"),
            ),
        ):
            payload = self.module._load_market_home_cached()
        self.assertEqual(payload["status"], "warming")
        self.assertTrue(
            any("without synchronous crawl" in item for item in payload["warnings"])
        )

    def test_only_one_refresh_can_start(self) -> None:
        self.module._home_refresh_running = True
        self.assertFalse(self.module._start_market_home_background_refresh())

    def test_ranking_uses_correct_top_k(self) -> None:
        rows = [
            {"f12": str(index), "f14": f"n{index}", "f2": index, "f3": index}
            for index in range(100)
        ]
        payload = self.module._ranking_from_rows(
            "gainers", rows, cache_age_ms=0, stale=False, limit=5
        )
        self.assertEqual(
            [int(item["code"]) for item in payload["items"]],
            [99, 98, 97, 96, 95],
        )


if __name__ == "__main__":
    unittest.main()
