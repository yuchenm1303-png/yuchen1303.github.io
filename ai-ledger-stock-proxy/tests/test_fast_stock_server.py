from __future__ import annotations

import asyncio
import sys
import unittest
from pathlib import Path
from time import monotonic


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import fast_stock_server as fast_server


class FastStockServerTest(unittest.IsolatedAsyncioTestCase):
    async def test_realtime_does_not_wait_for_slow_auction_refresh(self) -> None:
        async def fake_core(query: str, ndays: int, mark_hot: bool = True):
            return {
                "provider": "test",
                "quote": {"code": query, "price": "10.00"},
                "minutePoints": [],
                "tradeTicks": [],
                "warnings": [],
                "updatedAt": "2026-06-27T00:00:00+00:00",
                "sourceTimestamp": "2026-06-27T00:00:00+00:00",
                "cacheHit": True,
                "cacheAgeMs": 0,
                "upstreamLatencyMs": 0,
                "totalLatencyMs": 0,
            }

        async def fake_auction(security, force=False):
            await asyncio.sleep(0.25)
            raise RuntimeError("slow auction")

        original_core = fast_server._fast_core_realtime
        original_auction = fast_server.detail.load_auction
        fast_server._fast_core_realtime = fake_core
        fast_server.detail.load_auction = fake_auction
        try:
            started = monotonic()
            payload = await fast_server.fast_realtime_payload("600667", 1)
            elapsed = monotonic() - started
            await asyncio.sleep(0.27)
        finally:
            fast_server._fast_core_realtime = original_core
            fast_server.detail.load_auction = original_auction

        self.assertLess(elapsed, 0.15)
        self.assertEqual(payload["auction"]["refreshMode"], "background-refresh")
        self.assertTrue(
            any("refreshing in background" in item for item in payload["warnings"])
        )

    async def test_lite_detail_reuses_fast_core_once(self) -> None:
        calls = 0

        async def fake_core(query: str, ndays: int, mark_hot: bool = True):
            nonlocal calls
            calls += 1
            return {
                "provider": "test",
                "quote": {
                    "name": "测试股票",
                    "code": "600667",
                    "price": "12.34",
                    "changePercent": "+1.23%",
                },
                "minutePoints": [{"time": "09:30", "price": 12.34}],
                "sellLevels": [],
                "buyLevels": [],
                "tradeTicks": [],
                "warnings": [],
            }

        original_core = fast_server._fast_core_realtime
        fast_server._fast_core_realtime = fake_core
        try:
            payload = await fast_server.fast_detail_payload("600667", "lite")
        finally:
            fast_server._fast_core_realtime = original_core

        self.assertEqual(calls, 1)
        self.assertEqual(payload["provider"], "async_realtime_fast_path")
        self.assertEqual(payload["quote"]["code"], "600667")
        self.assertEqual(len(payload["minutePoints"]), 1)
        self.assertEqual(payload["kLinePoints"], [])

    def test_incremental_payload_keeps_only_latest_changes(self) -> None:
        payload = {
            "quote": {"code": "600667", "price": "12.34"},
            "minutePoints": [
                {"date": "2026-06-27", "time": "09:30", "price": 12.10},
                {"date": "2026-06-27", "time": "09:31", "price": 12.20},
                {"date": "2026-06-27", "time": "09:32", "price": 12.34},
            ],
            "tradeTicks": [
                {"time": "09:31:58", "price": "12.20"},
                {"time": "09:32:01", "price": "12.34"},
            ],
            "auction": {
                "status": "ok",
                "updatedAt": "2026-06-27T01:32:00Z",
                "sourceTimestamp": "2026-06-27T01:32:00Z",
                "cacheAgeMs": 0,
                "refreshMode": "cache-hit",
                "open": {"points": [{"time": "09:20"}]},
                "close": {"points": []},
            },
        }
        result = fast_server._apply_incremental_payload(
            payload,
            ndays=1,
            since_minute_key="2026-06-27 09:31",
            since_trade_key="09:31:58",
            compact=True,
        )

        self.assertTrue(result["isDelta"])
        self.assertFalse(result["minuteIsSnapshot"])
        self.assertFalse(result["ticksAreSnapshot"])
        self.assertNotIn("minutePoints", result)
        self.assertNotIn("tradeTicks", result)
        self.assertNotIn("auction", result)
        self.assertEqual(len(result["minuteDelta"]), 2)
        self.assertEqual(len(result["newTradeTicks"]), 2)
        self.assertEqual(result["minuteCursor"], "2026-06-27 09:32")
        self.assertEqual(result["tradeCursor"], "09:32:01|12.34||")
        self.assertGreater(result["payloadBytes"], 0)

    def test_minute_contract_exposes_phase_and_auction_volume_fields(self) -> None:
        payload = {
            "minutePoints": [
                {
                    "date": "2026-06-27",
                    "time": "09:20",
                    "price": 12.10,
                    "volume": 88,
                    "sessionPhase": "openAuction",
                }
            ],
            "tradeTicks": [],
        }
        result = fast_server._apply_incremental_payload(
            payload,
            ndays=1,
            since_minute_key="",
            since_trade_key="",
            compact=False,
        )
        point = result["minutePoints"][0]
        self.assertEqual(point["phase"], "openAuction")
        self.assertEqual(point["matchedVolume"], 88)
        self.assertIsNone(point["unmatchedVolume"])
        self.assertEqual(point["unmatchedDirection"], "unavailable")

    def test_one_day_cursor_resets_on_trade_date_change(self) -> None:
        payload = {
            "minutePoints": [
                {"date": "2026-06-28", "time": "09:15", "price": 12.40},
                {"date": "2026-06-28", "time": "09:16", "price": 12.42},
            ],
            "tradeTicks": [],
        }
        result = fast_server._apply_incremental_payload(
            payload,
            ndays=1,
            since_minute_key="2026-06-27 15:00",
            since_trade_key="",
            compact=True,
        )
        self.assertTrue(result["minuteReset"])
        self.assertTrue(result["minuteIsSnapshot"])
        self.assertIn("minutePoints", result)
        self.assertNotIn("minuteDelta", result)

    def test_fast_routes_are_registered_once(self) -> None:
        for path in (
            fast_server.REALTIME_PATH,
            fast_server.DETAIL_PATH,
            fast_server.CRAWL_DETAIL_PATH,
        ):
            routes = [
                route
                for route in fast_server.app.router.routes
                if getattr(route, "path", None) == path
                and "GET" in (getattr(route, "methods", None) or set())
            ]
            self.assertEqual(len(routes), 1, path)


if __name__ == "__main__":
    unittest.main()
