from __future__ import annotations

import asyncio
import sys
import unittest
from pathlib import Path


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


if __name__ == "__main__":
    unittest.main()
