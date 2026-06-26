from __future__ import annotations

import sys
import unittest
from datetime import datetime
from pathlib import Path
from types import SimpleNamespace
from zoneinfo import ZoneInfo


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import stock_detail_server as detail_server
from stock_detail_server import (
    AUCTION_PATH,
    REALTIME_PATH,
    _auction_cache_policy,
    _auction_loader,
    app,
    merge_auction_into_minute_points,
    parse_eastmoney_auction_trends,
    parse_tdx_auction_series,
    runtime,
)


CN_TZ = ZoneInfo("Asia/Shanghai")


class StockDetailServerTest(unittest.IsolatedAsyncioTestCase):
    def test_parse_eastmoney_price_and_matched_volume(self) -> None:
        payload = {
            "data": {
                "trends": [
                    "2026-06-25 09:15,25.31,25.40,25.40,25.31,100,253100,25.31",
                    "2026-06-25 09:20,25.31,26.80,26.80,25.31,500,1320000,25.92",
                    "2026-06-25 09:25,25.31,26.29,27.84,25.31,1200,3154800,26.01",
                    "2026-06-25 09:30,26.29,26.48,26.60,26.20,3000,7920000,26.36",
                    "2026-06-25 14:57,26.74,27.01,27.01,26.74,2227,6014927,27.44",
                    "2026-06-25 15:00,27.01,26.88,27.01,26.88,63600,170956800,27.40",
                ]
            }
        }

        parsed = parse_eastmoney_auction_trends(
            payload,
            expected_trade_date="2026-06-25",
        )

        self.assertEqual(
            [point["time"] for point in parsed["openPoints"]],
            ["09:15", "09:20", "09:25"],
        )
        self.assertEqual(
            [point["time"] for point in parsed["closePoints"]],
            ["14:57", "15:00"],
        )
        self.assertEqual(parsed["openPoints"][1]["price"], 26.80)
        self.assertEqual(parsed["openPoints"][1]["matchedVolume"], 500.0)
        self.assertIsNone(parsed["openPoints"][1]["unmatchedVolume"])
        self.assertEqual(
            parsed["openPoints"][1]["matchedVolumeSource"],
            "eastmoney_trends2_f56",
        )
        self.assertEqual(parsed["openPoints"][1]["priceSource"], "f53_matched_price")

    def test_parse_tdx_exact_matched_and_unmatched_volume(self) -> None:
        series = SimpleNamespace(
            points=(
                SimpleNamespace(
                    time_label="09:15:03",
                    price=25.40,
                    matched_volume=1200,
                    unmatched_signed_raw=800,
                    unmatched_volume=800,
                ),
                SimpleNamespace(
                    time_label="09:20:08",
                    price=26.80,
                    matched_volume=5600,
                    unmatched_signed_raw=-1300,
                    unmatched_volume=1300,
                ),
                SimpleNamespace(
                    time_label="14:57:01",
                    price=27.01,
                    matched_volume=2227,
                    unmatched_signed_raw=0,
                    unmatched_volume=0,
                ),
            )
        )

        parsed = parse_tdx_auction_series(series, "2026-06-25")

        self.assertEqual(len(parsed["openPoints"]), 2)
        self.assertEqual(len(parsed["closePoints"]), 1)
        first = parsed["openPoints"][0]
        second = parsed["openPoints"][1]
        self.assertEqual(first["matchedVolume"], 1200.0)
        self.assertEqual(first["unmatchedVolume"], 800.0)
        self.assertEqual(first["unmatchedDirection"], "buy")
        self.assertEqual(second["unmatchedDirection"], "sell")
        self.assertEqual(first["matchedVolumeSource"], "tdx_7709_0x056a_raw")
        self.assertFalse(first["volumeIsDerived"])
        self.assertFalse(first["isDerived"])

    def test_old_trade_date_is_rejected(self) -> None:
        parsed = parse_eastmoney_auction_trends(
            {
                "data": {
                    "trends": [
                        "2026-06-24 09:25,25.31,26.29,27.84,25.31,1200,3154800,26.01"
                    ]
                }
            },
            expected_trade_date="2026-06-25",
        )

        self.assertEqual(parsed["openPoints"], [])
        self.assertEqual(parsed["closePoints"], [])
        self.assertTrue(any("trade_date_mismatch" in item for item in parsed["warnings"]))

    def test_live_auction_cache_waits_for_current_refresh(self) -> None:
        fresh, stale, allow_stale, mode = _auction_cache_policy(
            datetime(2026, 6, 25, 9, 20, tzinfo=CN_TZ)
        )

        self.assertLessEqual(fresh, 0.35)
        self.assertLessEqual(stale, 2.0)
        self.assertFalse(allow_stale)
        self.assertEqual(mode, "live-auction")

    def test_closed_market_cache_can_reuse_stable_snapshot(self) -> None:
        fresh, stale, allow_stale, mode = _auction_cache_policy(
            datetime(2026, 6, 25, 16, 0, tzinfo=CN_TZ)
        )

        self.assertGreaterEqual(fresh, 300.0)
        self.assertGreaterEqual(stale, 6 * 60 * 60.0)
        self.assertTrue(allow_stale)
        self.assertEqual(mode, "market-closed")

    async def test_loader_prefers_tdx_volume_series_and_keeps_eastmoney_fallback(self) -> None:
        captured: dict[str, str] = {}

        async def fake_get_json(urls, params):
            captured.update(params)
            return (
                {
                    "data": {
                        "trends": [
                            "2026-06-25 09:25,25.31,26.29,27.84,25.31,1200,3154800,26.01",
                            "2026-06-25 15:00,27.01,26.88,27.01,26.88,63600,170956800,27.40",
                        ]
                    }
                },
                "push2.eastmoney.com",
                12,
            )

        def fake_tdx(code: str, trade_date: str):
            parsed = parse_tdx_auction_series(
                SimpleNamespace(
                    points=(
                        SimpleNamespace(
                            time_label="09:25:00",
                            price=26.29,
                            matched_volume=7000,
                            unmatched_signed_raw=900,
                            unmatched_volume=900,
                        ),
                        SimpleNamespace(
                            time_label="15:00:00",
                            price=26.88,
                            matched_volume=63600,
                            unmatched_signed_raw=-120,
                            unmatched_volume=120,
                        ),
                    )
                ),
                trade_date,
            )
            return parsed, 18

        original_get_json = runtime._get_json
        original_tdx = detail_server._load_tdx_auction_series_sync
        runtime._get_json = fake_get_json
        detail_server._load_tdx_auction_series_sync = fake_tdx
        try:
            parsed, host, latency, source_timestamp = await _auction_loader(
                {"code": "600667", "secid": "1.600667"},
                "2026-06-25",
            )
        finally:
            runtime._get_json = original_get_json
            detail_server._load_tdx_auction_series_sync = original_tdx

        self.assertEqual(captured["iscr"], "1")
        self.assertEqual(captured["iscca"], "0")
        self.assertEqual(captured["ndays"], "1")
        self.assertIn("f53", captured["fields2"])
        self.assertIn("push2.eastmoney.com", host)
        self.assertIn("tdx-7709", host)
        self.assertEqual(latency, 18)
        self.assertTrue(source_timestamp)
        self.assertEqual(parsed["openPoints"][0]["matchedVolume"], 7000.0)
        self.assertEqual(parsed["openPoints"][0]["unmatchedVolume"], 900.0)
        self.assertIn("eltdx_7709_call_auction", parsed["source"])

    def test_merge_inserts_second_level_auction_points(self) -> None:
        continuous = [
            {
                "date": "2026-06-25",
                "time": "09:30",
                "timestamp": 1750815000000,
                "price": 26.48,
                "average": 26.36,
                "volume": 3000.0,
                "volumeRatio": 0.5,
            }
        ]
        open_points = [
            {
                "date": "2026-06-25",
                "time": "09:25:03",
                "timestamp": 1750814703000,
                "price": 26.29,
                "average": 26.29,
                "volume": 7000.0,
                "matchedVolume": 7000.0,
                "unmatchedVolume": 900.0,
                "unmatchedDirection": "buy",
                "volumeRatio": 1.0,
                "sessionPhase": "openAuction",
                "isAuction": True,
                "isDerived": False,
            }
        ]

        merged = merge_auction_into_minute_points(continuous, open_points, [])

        self.assertEqual([point["time"] for point in merged], ["09:25:03", "09:30"])
        self.assertEqual(merged[0]["unmatchedVolume"], 900.0)
        self.assertEqual(merged[0]["sessionPhase"], "openAuction")
        self.assertEqual(merged[1]["sessionPhase"], "continuous")

    def test_empty_upstream_never_generates_points(self) -> None:
        parsed = parse_eastmoney_auction_trends({"data": {"trends": []}})

        self.assertEqual(parsed["openPoints"], [])
        self.assertEqual(parsed["closePoints"], [])

    def test_realtime_and_auction_routes_are_registered_once(self) -> None:
        realtime_routes = [
            route
            for route in app.router.routes
            if getattr(route, "path", None) == REALTIME_PATH
            and "GET" in (getattr(route, "methods", None) or set())
        ]
        auction_routes = [
            route
            for route in app.router.routes
            if getattr(route, "path", None) == AUCTION_PATH
            and "GET" in (getattr(route, "methods", None) or set())
        ]

        self.assertEqual(len(realtime_routes), 1)
        self.assertEqual(len(auction_routes), 1)


if __name__ == "__main__":
    unittest.main()
