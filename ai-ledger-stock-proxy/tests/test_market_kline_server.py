from __future__ import annotations

import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import market_kline_server as kline_server


class MarketKlineServerTest(unittest.TestCase):
    def test_period_defaults_expand_legacy_160_request(self) -> None:
        self.assertEqual(kline_server._effective_limit("daily", 160), 600)
        self.assertEqual(kline_server._effective_limit("weekly", 160), 320)
        self.assertEqual(kline_server._effective_limit("monthly", 160), 180)

    def test_requested_limit_cannot_exceed_maximum(self) -> None:
        self.assertEqual(kline_server._effective_limit("daily", 800), 800)
        self.assertEqual(kline_server._effective_limit("daily", 900), 800)

    def test_resolve_index_and_sector_instruments(self) -> None:
        index = kline_server._resolve_instrument("000001", "index")
        self.assertEqual(index["secid"], "1.000001")
        self.assertEqual(index["instrument"], "index")

        sector = kline_server._resolve_instrument("bk0428", "sector")
        self.assertEqual(sector["code"], "BK0428")
        self.assertEqual(sector["secid"], "90.BK0428")
        self.assertEqual(sector["instrument"], "sector")

    def test_kline_routes_registered_once(self) -> None:
        for path in (kline_server.STOCK_KLINE_PATH, kline_server.CRAWL_KLINE_PATH):
            routes = [
                route
                for route in kline_server.app.router.routes
                if getattr(route, "path", None) == path
                and "GET" in (getattr(route, "methods", None) or set())
            ]
            self.assertEqual(len(routes), 1, path)


if __name__ == "__main__":
    unittest.main()
