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


def complete_home(status: str = "ok"):
    payload = {
        "status": status,
        "sourceUrlType": "shared-universe test market endpoint",
        "warnings": [],
    }
    for name in market_home._REFRESHABLE_MODULES:
        payload[name] = module({"upCount": 1}) if name == "marketBreadth" else module()
    return payload


class MarketHomeServerTest(unittest.TestCase):
    def tearDown(self) -> None:
        market_home._home_refresh_running = False
        market_home._universe_cache = None

    def test_fast_builder_uses_one_real_universe_for_all_rankings(self) -> None:
        rows = [
            {"f12": "000001", "f14": "甲", "f2": 10, "f3": 1, "f6": 100, "f8": 2, "f10": 1.1, "f22": 0.2, "f62": 20},
            {"f12": "000002", "f14": "乙", "f2": 20, "f3": 5, "f6": 300, "f8": 6, "f10": 2.2, "f22": 0.8, "f62": 80},
            {"f12": "000003", "f14": "丙", "f2": 30, "f3": -4, "f6": 200, "f8": 4, "f10": 1.5, "f22": -0.5, "f62": -60},
        ]

        def direct_cache(kind, query, mode, builder):
            return builder()

        with (
            patch.object(market_home, "_cached_module", side_effect=direct_cache),
            patch.object(market_home, "_load_indices_parallel", return_value=module()),
            patch.object(market_home, "_load_sector_hot_shared", return_value=module()),
            patch.object(
                market_home,
                "_load_market_universe_cached",
                return_value=(rows, [], 0, False),
            ) as universe_loader,
            patch.object(market_home.legacy, "_cache_put"),
            patch.object(
                market_home.legacy,
                "_load_ranking",
                return_value=module([], status="unavailable"),
            ),
            patch.object(
                market_home.legacy,
                "_unavailable_module",
                side_effect=lambda name: module([], status="unavailable"),
            ),
        ):
            payload = market_home._build_market_home_fast("test")

        universe_loader.assert_called_once_with()
        self.assertEqual(payload["gainers"]["items"][0]["code"], "000002")
        self.assertEqual(payload["losers"]["items"][0]["code"], "000003")
        self.assertEqual(payload["amountRanking"]["items"][0]["code"], "000002")
        self.assertEqual(payload["mainInflowRanking"]["items"][0]["code"], "000002")
        self.assertEqual(payload["mainOutflowRanking"]["items"][0]["code"], "000003")
        self.assertEqual(payload["marketBreadth"]["items"]["upCount"], 2)
        self.assertEqual(payload["marketBreadth"]["items"]["downCount"], 1)

    def test_cold_partial_response_starts_background_completion(self) -> None:
        partial = complete_home(status="partial")
        partial["sectorHotRanking"] = module([], status="unavailable")
        with (
            patch.object(market_home.legacy, "_cache_get", return_value=None),
            patch.object(market_home.legacy, "_cache_put") as cache_put,
            patch.object(market_home, "_build_market_home_fast", return_value=partial),
            patch.object(
                market_home,
                "_start_market_home_background_refresh",
                return_value=True,
            ) as start_refresh,
        ):
            payload = market_home._load_market_home_cached()

        cache_put.assert_called_once_with(market_home._home_cache_key(), partial)
        start_refresh.assert_called_once_with()
        self.assertIn(
            "market_home_cache: background_completion_started",
            payload["warnings"],
        )

    def test_fresh_complete_cache_does_not_start_background(self) -> None:
        home = complete_home()
        with (
            patch.object(market_home.legacy, "_cache_get", return_value=(home, 1)),
            patch.object(market_home, "_start_market_home_background_refresh") as start_refresh,
        ):
            payload = market_home._load_market_home_cached()

        start_refresh.assert_not_called()
        self.assertIn("market_home_cache: hit age=1s", payload["warnings"])

    def test_background_refresh_replaces_home_cache(self) -> None:
        full = complete_home()
        market_home._home_refresh_running = True
        with (
            patch.object(market_home, "_build_market_home_fast", return_value=full) as build,
            patch.object(market_home.legacy, "_cache_put") as cache_put,
        ):
            market_home._refresh_market_home_background()

        build.assert_called_once_with("background")
        cache_put.assert_called_once_with(market_home._home_cache_key(), full)
        self.assertFalse(market_home._home_refresh_running)

    def test_unavailable_background_does_not_overwrite_home_cache(self) -> None:
        unavailable = {
            "status": "unavailable",
            "sourceUrlType": "shared-universe background market endpoint",
            "warnings": [],
        }
        market_home._home_refresh_running = True
        with (
            patch.object(market_home, "_build_market_home_fast", return_value=unavailable),
            patch.object(market_home.legacy, "_cache_put") as cache_put,
        ):
            market_home._refresh_market_home_background()

        cache_put.assert_not_called()
        self.assertFalse(market_home._home_refresh_running)


if __name__ == "__main__":
    unittest.main()
