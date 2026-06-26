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
        async def fake_realtime(query: str, ndays: int):
            return {
                "provider": "test",
                "quote": {"code": query, "price": "10.00"},
                "minutePoints": [],
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

        original_realtime = fast_server.runtime.realtime
        original_auction = fast_server.detail.load_auction
        fast_server.runtime.realtime = fake_realtime
        fast_server.detail.load_auction = fake_auction
        try:
            started = monotonic()
            payload = await fast_server.fast_realtime_payload("600667", 1)
            elapsed = monotonic() - started
            await asyncio.sleep(0.27)
        finally:
            fast_server.runtime.realtime = original_realtime
            fast_server.detail.load_auction = original_auction

        self.assertLess(elapsed, 0.15)
        self.assertEqual(payload["auction"]["refreshMode"], "background-refresh")
        self.assertTrue(
            any("refreshing in background" in item for item in payload["warnings"])
        )

    async def test_lite_detail_reuses_async_realtime_runtime(self) -> None:
        calls = 0

        async def fake_realtime(query: str, ndays: int):
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

        original_realtime = fast_server.runtime.realtime
        fast_server.runtime.realtime = fake_realtime
        try:
            payload = await fast_server.fast_detail_payload("600667", "lite")
        finally:
            fast_server.runtime.realtime = original_realtime

        self.assertEqual(calls, 1)
        self.assertEqual(payload["provider"], "async_realtime_fast_path")
        self.assertEqual(payload["quote"]["code"], "600667")
        self.assertEqual(len(payload["minutePoints"]), 1)
        self.assertEqual(payload["kLinePoints"], [])

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
