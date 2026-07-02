from __future__ import annotations

import unittest
from unittest.mock import Mock, patch

from fastapi import Response

import index_compact_server as compact
import market_stage_server as stage


class MarketStageServerTest(unittest.TestCase):
    def test_indices_refresh_uses_stage_specific_ttl_and_caches_real_payload(self) -> None:
        payload = {
            "status": "ok",
            "source": "test",
            "sourceUrlType": "test",
            "items": [{"code": "000001", "name": "上证指数", "price": "3000"}],
            "warnings": [],
        }
        cache_put = Mock()
        with (
            patch.object(stage.legacy, "_cache_get", return_value=None),
            patch.object(stage.legacy, "_cache_put", cache_put),
            patch.object(stage.legacy, "_payload_has_real_items", return_value=True),
            patch.object(stage.home, "_load_indices_parallel", return_value=payload),
        ):
            result = stage._cached_indices()

        self.assertEqual(result, payload)
        cache_put.assert_called_once()
        self.assertEqual(stage.INDICES_REFRESH_SECONDS, 8.0)

    def test_fresh_stage_cache_does_not_call_builder(self) -> None:
        payload = {
            "status": "ok",
            "source": "test",
            "sourceUrlType": "test",
            "items": [{"code": "000001"}],
            "warnings": [],
        }
        builder = Mock(side_effect=AssertionError("fresh cache must skip builder"))
        with patch.object(stage.legacy, "_cache_get", return_value=(payload, 2)):
            result = stage._cached_stage_module(
                "market",
                "indices",
                "full-parallel",
                "indices",
                stage.INDICES_REFRESH_SECONDS,
                builder=builder,
            )
        self.assertEqual(result["status"], "ok")
        self.assertEqual(result["cacheAgeMs"], 2000)
        builder.assert_not_called()

    def test_discovery_health_ignores_unimplemented_optional_modules(self) -> None:
        module = {
            "status": "ok",
            "source": "test",
            "sourceUrlType": "test",
            "items": [{"code": "600000", "name": "测试"}],
            "warnings": [],
            "cacheAgeMs": 0,
        }
        with patch.object(stage, "_cached_stage_module", return_value=module):
            payload = stage.a_share_market_discovery(Response())

        self.assertEqual(payload["status"], "ok")
        self.assertNotIn("popularityRanking", payload)
        self.assertNotIn("limitUpSummary", payload)
        self.assertNotIn("marketNews", payload)
        self.assertIn("sectorHotRanking", payload)

    def test_indices_and_discovery_are_read_only(self) -> None:
        module = {
            "status": "ok",
            "source": "test",
            "sourceUrlType": "test",
            "items": [{"code": "000001"}],
            "warnings": [],
            "cacheAgeMs": 0,
        }
        with (
            patch.object(stage, "_cached_indices", return_value=module),
            patch.object(stage, "_cached_discovery_modules", return_value={"gainers": module}),
            patch.object(stage.home, "_start_market_home_background_refresh") as start,
        ):
            indices = stage.a_share_market_indices(Response())
            discovery = stage.a_share_market_discovery(Response())

        start.assert_not_called()
        self.assertIn("market_stage: indices_read_only", indices["warnings"])
        self.assertIn("market_stage: discovery_read_only", discovery["warnings"])

    def test_only_breadth_can_start_full_market_refresh(self) -> None:
        breadth = {
            "status": "ok",
            "source": "test",
            "sourceUrlType": "test",
            "items": {"upCount": 1, "downCount": 1},
            "warnings": [],
            "cacheAgeMs": 0,
        }
        with (
            patch.object(stage, "_cached_breadth", return_value=breadth),
            patch.object(stage, "_market_refresh_due", return_value=True),
            patch.object(
                stage.home,
                "_start_market_home_background_refresh",
                return_value=True,
            ) as start,
        ):
            payload = stage.a_share_market_breadth(Response())

        start.assert_called_once_with()
        self.assertIn("market_stage: background_refresh_started", payload["warnings"])

    def test_full_market_refresh_window_is_not_aggressive(self) -> None:
        self.assertGreaterEqual(stage.MARKET_REFRESH_SECONDS, 30.0)

    def test_tools_index_chain_is_independent_from_index_detail(self) -> None:
        self.assertFalse(hasattr(compact, "detail"))
        self.assertEqual(
            compact.INDEX_COMPACT_BATCH_CODES,
            ("000001", "399001", "399006"),
        )
        self.assertEqual(
            compact.INDEX_COMPACT_QUOTES_PATH,
            "/api/stock/a-share/index/compact/quotes",
        )
        self.assertEqual(
            compact.INDEX_COMPACT_TREND_PATH,
            "/api/stock/a-share/index/compact/trend",
        )

    def test_tools_index_batch_uses_one_quotes_lane_and_three_independent_trends(self) -> None:
        quotes_payload = {
            "status": "ok",
            "items": [
                {"code": "000001", "name": "上证指数", "price": "3000.00"},
                {"code": "399001", "name": "深证成指", "price": "9500.00"},
                {"code": "399006", "name": "创业板指", "price": "1900.00"},
            ],
            "warnings": [],
        }

        def trend_result(security: dict[str, str]) -> dict[str, object]:
            return {
                "status": "ok",
                "code": security["code"],
                "name": security["name"],
                "minutePoints": [
                    {"time": "09:30", "price": 100.0, "average": 100.0},
                    {"time": "09:31", "price": 101.0, "average": 100.5},
                ],
                "warnings": [],
            }

        with (
            patch.object(compact, "_load_quotes_cached", return_value=quotes_payload) as quote_loader,
            patch.object(compact, "_load_trend_cached", side_effect=trend_result) as trend_loader,
        ):
            payload = compact._build_batch()

        quote_loader.assert_called_once_with()
        self.assertEqual(trend_loader.call_count, 3)
        self.assertTrue(payload["dedicated"])
        self.assertTrue(payload["splitPriority"])
        self.assertEqual(payload["loadedCount"], 3)
        self.assertEqual(payload["completeCount"], 3)
        self.assertEqual(payload["status"], "ok")
        self.assertEqual(
            [item["code"] for item in payload["items"]],
            ["000001", "399001", "399006"],
        )

    def test_tools_index_partial_refresh_preserves_previous_real_curve(self) -> None:
        current = {
            "status": "partial",
            "items": [
                {
                    "code": "000001",
                    "name": "上证指数",
                    "quote": {"price": "3010.00"},
                    "minutePoints": [],
                    "warnings": [],
                }
            ],
        }
        stale = {
            "status": "ok",
            "items": [
                {
                    "code": "000001",
                    "name": "上证指数",
                    "quote": {"price": "3000.00"},
                    "minutePoints": [{"time": "09:30", "price": 3000.0}],
                    "warnings": [],
                },
                {
                    "code": "399001",
                    "name": "深证成指",
                    "quote": {"price": "9500.00"},
                    "minutePoints": [{"time": "09:30", "price": 9500.0}],
                    "warnings": [],
                },
                {
                    "code": "399006",
                    "name": "创业板指",
                    "quote": {"price": "1900.00"},
                    "minutePoints": [{"time": "09:30", "price": 1900.0}],
                    "warnings": [],
                },
            ],
        }

        merged = compact._merge_with_stale(current, stale)

        self.assertEqual(merged["items"][0]["quote"]["price"], "3010.00")
        self.assertTrue(merged["items"][0]["minutePoints"])
        self.assertEqual(merged["loadedCount"], 3)
        self.assertEqual(merged["completeCount"], 3)


if __name__ == "__main__":
    unittest.main()
