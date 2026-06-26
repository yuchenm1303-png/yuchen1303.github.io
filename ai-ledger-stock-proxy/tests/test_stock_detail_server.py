from __future__ import annotations

import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from stock_detail_server import (
    AUCTION_PATH,
    REALTIME_PATH,
    _auction_loader,
    app,
    merge_auction_into_minute_points,
    parse_auction_trends,
    runtime,
)


class StockDetailServerTest(unittest.IsolatedAsyncioTestCase):
    def test_parse_real_open_and_close_auction_points(self) -> None:
        payload = {
            "data": {
                "trends": [
                    "2026-06-25 09:15,25.31,25.31,25.31,25.31,100,253100,25.31",
                    "2026-06-25 09:20,25.31,26.80,26.80,25.31,500,1320000,26.80",
                    "2026-06-25 09:25,25.31,26.29,27.84,25.31,1200,3154800,26.29",
                    "2026-06-25 09:30,26.29,26.48,26.60,26.20,3000,7920000,26.36",
                    "2026-06-25 14:57,26.74,27.01,27.01,26.74,2227,6014927,27.44",
                    "2026-06-25 15:00,27.01,26.88,27.01,26.88,63600,170956800,27.44",
                ]
            }
        }

        parsed = parse_auction_trends(payload)

        self.assertEqual([point["time"] for point in parsed["openPoints"]], ["09:15", "09:20", "09:25"])
        self.assertEqual([point["time"] for point in parsed["closePoints"]], ["14:57", "15:00"])
        self.assertEqual(parsed["openPoints"][1]["price"], 26.80)
        self.assertEqual(parsed["openPoints"][1]["priceSource"], "f58_latest")
        self.assertEqual(parsed["closePoints"][0]["price"], 27.01)
        self.assertEqual(parsed["closePoints"][0]["priceSource"], "f53_close")
        self.assertTrue(all(point["isAuction"] for point in parsed["openPoints"] + parsed["closePoints"]))
        self.assertTrue(all(not point["isDerived"] for point in parsed["openPoints"] + parsed["closePoints"]))

    async def test_loader_uses_real_premarket_endpoint_mode(self) -> None:
        captured: dict[str, str] = {}

        async def fake_get_json(urls, params):
            captured.update(params)
            return (
                {
                    "data": {
                        "trends": [
                            "2026-06-25 09:25,25.31,26.29,27.84,25.31,1200,3154800,26.29",
                            "2026-06-25 15:00,27.01,26.88,27.01,26.88,63600,170956800,27.44",
                        ]
                    }
                },
                "push2.eastmoney.com",
                12,
            )

        original_get_json = runtime._get_json
        runtime._get_json = fake_get_json
        try:
            parsed, host, latency, source_timestamp = await _auction_loader(
                {"code": "600667", "secid": "1.600667"}
            )
        finally:
            runtime._get_json = original_get_json

        self.assertEqual(captured["iscr"], "1")
        self.assertEqual(captured["iscca"], "0")
        self.assertEqual(captured["ndays"], "1")
        self.assertIn("f58", captured["fields2"])
        self.assertEqual(host, "push2.eastmoney.com")
        self.assertEqual(latency, 12)
        self.assertTrue(source_timestamp)
        self.assertEqual(len(parsed["openPoints"]), 1)
        self.assertEqual(len(parsed["closePoints"]), 1)

    def test_merge_inserts_open_auction_and_replaces_same_timestamp_close_point(self) -> None:
        continuous = [
            {
                "date": "2026-06-25",
                "time": "09:30",
                "timestamp": 1750815000000,
                "price": 26.48,
                "average": 26.36,
                "volume": 3000.0,
                "volumeRatio": 0.5,
            },
            {
                "date": "2026-06-25",
                "time": "15:00",
                "timestamp": 1750834800000,
                "price": 26.90,
                "average": 27.44,
                "volume": 60000.0,
                "volumeRatio": 1.0,
            },
        ]
        open_points = [
            {
                "date": "2026-06-25",
                "time": "09:25",
                "timestamp": 1750814700000,
                "price": 26.29,
                "average": 26.29,
                "volume": 1200.0,
                "volumeRatio": 0.02,
                "sessionPhase": "openAuction",
                "isAuction": True,
                "isDerived": False,
            }
        ]
        close_points = [
            {
                "date": "2026-06-25",
                "time": "15:00",
                "timestamp": 1750834800000,
                "price": 26.88,
                "average": 26.88,
                "volume": 63600.0,
                "volumeRatio": 1.0,
                "sessionPhase": "closeAuction",
                "isAuction": True,
                "isDerived": False,
            }
        ]

        merged = merge_auction_into_minute_points(continuous, open_points, close_points)

        self.assertEqual([point["time"] for point in merged], ["09:25", "09:30", "15:00"])
        self.assertEqual(merged[-1]["price"], 26.88)
        self.assertEqual(merged[0]["sessionPhase"], "openAuction")
        self.assertEqual(merged[1]["sessionPhase"], "continuous")
        self.assertEqual(merged[-1]["sessionPhase"], "closeAuction")

    def test_empty_upstream_never_generates_auction_points(self) -> None:
        parsed = parse_auction_trends({"data": {"trends": []}})

        self.assertEqual(parsed["openPoints"], [])
        self.assertEqual(parsed["closePoints"], [])

    def test_realtime_and_auction_routes_are_registered_once(self) -> None:
        realtime_routes = [
            route for route in app.router.routes
            if getattr(route, "path", None) == REALTIME_PATH
            and "GET" in (getattr(route, "methods", None) or set())
        ]
        auction_routes = [
            route for route in app.router.routes
            if getattr(route, "path", None) == AUCTION_PATH
            and "GET" in (getattr(route, "methods", None) or set())
        ]

        self.assertEqual(len(realtime_routes), 1)
        self.assertEqual(len(auction_routes), 1)


if __name__ == "__main__":
    unittest.main()
