from __future__ import annotations

import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import hot_rank_server as hot_server


class HotRankServerTest(unittest.TestCase):
    def test_extract_rank_rows_supports_direct_and_nested_data(self) -> None:
        direct = hot_server._extract_rank_rows(
            {"data": [{"sc": "SH600000", "rk": 1}]}
        )
        nested = hot_server._extract_rank_rows(
            {"data": {"list": [{"sc": "SZ000001", "rk": 2}]}}
        )
        self.assertEqual(direct[0]["sc"], "SH600000")
        self.assertEqual(nested[0]["sc"], "SZ000001")

    def test_security_code_mapping_supports_sh_sz_and_bj(self) -> None:
        self.assertEqual(
            hot_server._security_from_source_code("SH600000")["secid"],
            "1.600000",
        )
        self.assertEqual(
            hot_server._security_from_source_code("SZ000001")["secid"],
            "0.000001",
        )
        self.assertEqual(
            hot_server._security_from_source_code("BJ920001")["secid"],
            "0.920001",
        )
        self.assertIsNone(hot_server._security_from_source_code("USNVDA"))

    def test_build_surge_payload_preserves_rank_change_and_quote_order(self) -> None:
        original_rows = hot_server._load_upstream_rank_rows
        original_quotes = hot_server._quote_map
        hot_server._load_upstream_rank_rows = lambda rank_type, warnings: [
            {"sc": "SZ000001", "rk": "8", "hrc": "21"},
            {"sc": "SH600000", "rk": "15", "hrc": "9"},
        ]
        hot_server._quote_map = lambda securities, warnings: {
            "000001": {
                "f12": "000001",
                "f14": "平安银行",
                "f2": 12.34,
                "f3": 2.5,
                "f4": 0.30,
                "f6": 123456789,
                "f100": "银行",
            },
            "600000": {
                "f12": "600000",
                "f14": "浦发银行",
                "f2": 10.01,
                "f3": -1.2,
                "f4": -0.12,
                "f6": 76543210,
                "f100": "银行",
            },
        }
        try:
            payload = hot_server._build_hot_rank_payload("surge")
        finally:
            hot_server._load_upstream_rank_rows = original_rows
            hot_server._quote_map = original_quotes

        self.assertEqual(payload["type"], "surge")
        self.assertEqual(payload["items"][0]["code"], "000001")
        self.assertEqual(payload["items"][0]["currentRank"], 8)
        self.assertEqual(payload["items"][0]["rankChange"], 21)
        self.assertEqual(payload["items"][0]["rankChangeState"], "up")
        self.assertEqual(payload["items"][1]["name"], "浦发银行")
        self.assertEqual(payload["summary"]["quoteMatchCount"], 2)

    def test_limited_payload_does_not_change_full_cached_items(self) -> None:
        payload = {"items": [{"rank": index} for index in range(1, 11)]}
        limited = hot_server._limited_payload(payload, 3)
        self.assertEqual(limited["count"], 3)
        self.assertEqual(limited["fullCount"], 10)
        self.assertEqual(len(payload["items"]), 10)

    def test_hot_rank_routes_registered_once(self) -> None:
        for path in (
            hot_server.HOT_RANKING_PATH,
            hot_server.LEGACY_POPULARITY_PATH,
        ):
            routes = [
                route
                for route in hot_server.app.router.routes
                if getattr(route, "path", None) == path
                and "GET" in (getattr(route, "methods", None) or set())
            ]
            self.assertEqual(len(routes), 1, path)


if __name__ == "__main__":
    unittest.main()
