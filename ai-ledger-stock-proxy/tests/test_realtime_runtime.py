from __future__ import annotations

import asyncio
import sys
import unittest
from pathlib import Path
from time import monotonic


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from realtime_runtime import CacheEntry, CacheResult, RealtimeRuntime


class RealtimeRuntimeTest(unittest.IsolatedAsyncioTestCase):
    async def asyncTearDown(self) -> None:
        runtime = getattr(self, "runtime", None)
        if runtime is not None:
            await runtime.close()

    async def test_singleflight_coalesces_concurrent_cache_misses(self) -> None:
        self.runtime = RealtimeRuntime()
        calls = 0

        async def loader():
            nonlocal calls
            calls += 1
            await asyncio.sleep(0.03)
            return {"value": 1}, "push2.eastmoney.com", 30, "2026-06-25T09:30:00+08:00"

        results = await asyncio.gather(
            *(self.runtime._cached("quote:600396", 1.0, 30.0, loader) for _ in range(10))
        )

        self.assertEqual(calls, 1)
        self.assertEqual(sum(result.waited for result in results), 9)
        self.assertTrue(all(result.value == {"value": 1} for result in results))

    async def test_five_day_merge_uses_timestamp_and_keeps_latest_five_dates(self) -> None:
        self.runtime = RealtimeRuntime()
        old_points = [
            {"date": f"2026-06-{day:02d}", "time": "09:30", "timestamp": day * 1000, "price": float(day)}
            for day in range(18, 25)
        ]
        self.runtime.cache["minute:5d:600396"] = CacheEntry(
            old_points, 1.0, "updated", "source", "host", 10
        )
        today = CacheResult(
            value=[{"date": "2026-06-25", "time": "09:30", "timestamp": 25000, "price": 20.0}],
            cache_hit=False,
            cache_age_ms=0,
            stale=False,
            waited=False,
            source_timestamp="source2",
            source_host="host2",
            upstream_latency_ms=12,
            updated_at="updated2",
        )

        await self.runtime._merge_today_into_five_day("600396", today)
        values = self.runtime.cache["minute:5d:600396"].value

        self.assertEqual(sorted({point["date"] for point in values}), [
            "2026-06-21", "2026-06-22", "2026-06-23", "2026-06-24", "2026-06-25"
        ])
        self.assertEqual(len({point["timestamp"] for point in values}), len(values))

    async def test_push2delay_is_never_the_initial_primary_source(self) -> None:
        self.runtime = RealtimeRuntime()
        ordered = self.runtime._source_order([
            "https://push2delay.eastmoney.com/api",
            "https://push2.eastmoney.com/api",
            "https://push2his.eastmoney.com/api",
        ])
        self.assertIn("push2.eastmoney.com", ordered[0])
        self.assertGreater(next(index for index, url in enumerate(ordered) if "delay" in url), 0)

    async def test_stale_while_revalidate_returns_old_value_immediately(self) -> None:
        self.runtime = RealtimeRuntime()
        self.runtime.cache["quote:600396"] = CacheEntry(
            {"price": "15.77"},
            asyncio.get_running_loop().time() - 2.0,
            "updated",
            "source",
            "host",
            10,
        )

        async def failing_loader():
            raise ValueError("upstream unavailable")

        result = await self.runtime._cached("quote:600396", 1.0, 30.0, failing_loader)

        self.assertEqual(result.value, {"price": "15.77"})
        self.assertTrue(result.cache_hit)
        self.assertTrue(result.stale)
        await asyncio.sleep(0)

    def test_depth_empty_never_builds_fake_levels(self) -> None:
        self.runtime = RealtimeRuntime()
        sell, buy, meta = self.runtime.parse_depth({"f43": 1000}, {"price": "10.00"})

        self.assertEqual(sell, [])
        self.assertEqual(buy, [])
        self.assertEqual(meta["depthStatus"], "empty")
        self.assertFalse(meta["depthIsDerived"])

    def test_depth_partial_does_not_fill_missing_levels(self) -> None:
        self.runtime = RealtimeRuntime()
        raw = {"f43": 1000, "f39": 1005, "f40": 300, "f19": 995, "f20": 200}
        sell, buy, meta = self.runtime.parse_depth(raw, {"price": "10.00"})

        self.assertEqual([row["label"] for row in sell], ["卖1"])
        self.assertEqual([row["label"] for row in buy], ["买1"])
        self.assertEqual(meta["depthStatus"], "partial")

    def test_depth_sorting_and_limit_price_filters(self) -> None:
        self.runtime = RealtimeRuntime()
        raw = {
            "f43": 1000,
            "f51": 1020,
            "f52": 980,
            "f31": 1005, "f32": 100,
            "f33": 1025, "f34": 100,
            "f35": 1010, "f36": 100,
            "f19": 995, "f20": 100,
            "f17": 975, "f18": 100,
            "f15": 990, "f16": 100,
        }
        sell, buy, meta = self.runtime.parse_depth(raw, {"price": "10.00"})

        self.assertEqual([row["price"] for row in sell], ["10.05", "10.10"])
        self.assertEqual([row["price"] for row in buy], ["9.95", "9.90"])
        self.assertTrue(any("above_limit_up" in item for item in meta["depthWarnings"]))
        self.assertTrue(any("below_limit_down" in item for item in meta["depthWarnings"]))
        self.assertFalse(meta["depthIsDerived"])

    async def test_depth_stale_cache_is_only_previous_true_depth_and_code_isolated(self) -> None:
        self.runtime = RealtimeRuntime()
        self.runtime.cache["depth:600396"] = CacheEntry(
            {
                "sellLevels": [{"label": "卖1", "price": "10.05", "volume": "100", "isAsk": True}],
                "buyLevels": [{"label": "买1", "price": "9.95", "volume": "200", "isAsk": False}],
                "depthMeta": {"depthStatus": "partial", "depthSource": "eastmoney_push2", "depthIsDerived": False, "depthWarnings": []},
            },
            monotonic() - 2.0,
            "updated",
            "source",
            "push2.eastmoney.com",
            10,
        )
        empty_quote = CacheResult({}, False, 0, False, False, "source", "push2.eastmoney.com", 10, "updated")

        sell, buy, meta = await self.runtime.depth({"code": "600396"}, empty_quote, {"price": "10.00"})
        other_sell, other_buy, other_meta = await self.runtime.depth({"code": "000001"}, empty_quote, {"price": "10.00"})

        self.assertEqual(meta["depthStatus"], "stale")
        self.assertEqual(sell[0]["price"], "10.05")
        self.assertEqual(other_sell, [])
        self.assertEqual(other_buy, [])
        self.assertEqual(other_meta["depthStatus"], "unavailable")
        self.assertFalse(meta["depthIsDerived"])


if __name__ == "__main__":
    unittest.main()
