from __future__ import annotations

import sys
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import market_home_server as market_home


def module(items=None, status: str = "ok"):
    return {
        "status": status,
        "source": "test",
        "sourceUrlType": "test",
        "updatedAt": "2026-06-25T00:00:00+00:00",
        "cacheAgeMs": 0,
        "isDerived": False,
        "warnings": [],
        "items": [{"value": 1}] if items is None else items,
    }


class MarketHomeServerTest(unittest.TestCase):
    def tearDown(self) -> None:
        market_home._home_refresh_running = False

    def test_cold_light_response_starts_full_background_refresh(self) -> None:
        light = {
            "status": "partial",
            "sourceUrlType": "lightweight market endpoint",
            "warnings": [],
            "indices": module(),
            "marketBreadth": module({"upCount": 1}),
        }
        with (
            patch.object(market_home.legacy, "_cache_get", return_value=None),
            patch.object(market_home.legacy, "_cache_put") as cache_put,
            patch.object(market_home, "_build_market_home_light", return_value=light),
            patch.object(
                market_home,
                "_start_market_home_background_refresh",
                return_value=True,
            ) as start_refresh,
        ):
            payload = market_home._load_market_home_cached()

        cache_put.assert_called_once_with(market_home._home_cache_key(), light)
        start_refresh.assert_called_once_with()
        self.assertIn(
            "market_home_cache: full_background_refresh_started",
            payload["warnings"],
        )

    def test_background_refresh_uses_full_builder_and_replaces_home_cache(self) -> None:
        full = {
            "status": "ok",
            "sourceUrlType": "background full market endpoint",
            "warnings": [],
            "gainers": module(),
        }
        market_home._home_refresh_running = True
        with (
            patch.object(market_home, "_build_market_home_full", return_value=full) as build,
            patch.object(market_home.legacy, "_cache_put") as cache_put,
        ):
            market_home._refresh_market_home_background()

        build.assert_called_once_with()
        cache_put.assert_called_once_with(market_home._home_cache_key(), full)
        self.assertFalse(market_home._home_refresh_running)

    def test_full_builder_loads_all_rankings_and_sector(self) -> None:
        ranking_calls: list[str] = []

        def load_ranking(type_name: str, limit: int):
            ranking_calls.append(type_name)
            if type_name == "popularity":
                return module([], status="unavailable")
            return module([{"type": type_name, "limit": limit}])

        def direct_cache(kind, query, mode, builder):
            return builder()

        with (
            patch.object(market_home, "_cached_module", side_effect=direct_cache),
            patch.object(market_home, "_load_indices_parallel", return_value=module()),
            patch.object(
                market_home.legacy,
                "_load_market_breadth",
                return_value=module({"redRate": 50.0, "limitUpCount": 2}),
            ),
            patch.object(market_home.legacy, "_load_ranking", side_effect=load_ranking),
            patch.object(market_home.legacy, "_load_sectors", return_value=module()),
            patch.object(
                market_home.legacy,
                "_unavailable_module",
                side_effect=lambda name: module([], status="unavailable"),
            ),
        ):
            payload = market_home._build_market_home_full()

        self.assertEqual(payload["sourceUrlType"], "background full market endpoint")
        self.assertEqual(payload["gainers"]["status"], "ok")
        self.assertEqual(payload["mainInflowRanking"]["status"], "ok")
        self.assertEqual(payload["sectorHotRanking"]["status"], "ok")
        self.assertTrue(
            {
                "gainers",
                "losers",
                "amount",
                "turnover",
                "volume_ratio",
                "speed",
                "main_inflow",
                "main_outflow",
            }.issubset(set(ranking_calls))
        )

    def test_unavailable_full_refresh_does_not_overwrite_light_cache(self) -> None:
        unavailable = {
            "status": "unavailable",
            "sourceUrlType": "background full market endpoint",
            "warnings": [],
        }
        market_home._home_refresh_running = True
        with (
            patch.object(market_home, "_build_market_home_full", return_value=unavailable),
            patch.object(market_home.legacy, "_cache_put") as cache_put,
        ):
            market_home._refresh_market_home_background()

        cache_put.assert_not_called()
        self.assertFalse(market_home._home_refresh_running)


if __name__ == "__main__":
    unittest.main()
