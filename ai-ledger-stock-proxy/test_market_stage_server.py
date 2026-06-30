from __future__ import annotations

import unittest
from unittest.mock import Mock, patch

from fastapi import Response

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
        self.assertEqual(stage.INDICES_REFRESH_SECONDS, 6.0)

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
                6.0,
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
        with (
            patch.object(stage, "_cached_stage_module", return_value=module),
            patch.object(stage, "_discovery_refresh_due", return_value=False),
        ):
            payload = stage.a_share_market_discovery(Response())

        self.assertEqual(payload["status"], "ok")
        self.assertNotIn("popularityRanking", payload)
        self.assertNotIn("limitUpSummary", payload)
        self.assertNotIn("marketNews", payload)
        self.assertIn("sectorHotRanking", payload)

    def test_only_one_background_refresh_path_is_used(self) -> None:
        with patch.object(
            stage.home,
            "_start_market_home_background_refresh",
            return_value=False,
        ) as start:
            started, warning = stage._start_background_if_due(True)
        self.assertFalse(started)
        self.assertIn("reused_or_cooling", warning)
        start.assert_called_once_with()


if __name__ == "__main__":
    unittest.main()
