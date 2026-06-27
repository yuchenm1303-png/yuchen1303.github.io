from __future__ import annotations

import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import index_detail_server as index_server


class IndexDetailServerTest(unittest.TestCase):
    def test_resolve_index_by_code_and_alias(self) -> None:
        self.assertEqual(index_server._resolve_index("000001")["secid"], "1.000001")
        self.assertEqual(index_server._resolve_index("创业板指")["code"], "399006")
        self.assertEqual(index_server._resolve_index("A500")["code"], "000510")

    def test_parse_index_minutes_keeps_volume_and_amount(self) -> None:
        raw = {
            "data": {
                "trends": [
                    "2026-06-27 09:30,4000.00,4001.20,4002.00,3999.00,120,480000,4000.80",
                    "2026-06-27 09:31,4001.20,4003.50,4004.00,4001.00,240,960000,4002.10",
                ]
            }
        }
        points = index_server._parse_index_minutes(raw, 1)
        self.assertEqual(len(points), 2)
        self.assertEqual(points[0]["volume"], 120)
        self.assertEqual(points[0]["amount"], 480000)
        self.assertEqual(points[0]["volumeRatio"], 0.5)
        self.assertEqual(points[1]["volumeRatio"], 1.0)
        self.assertGreater(points[0]["timestamp"], 0)

    def test_build_index_detail_combines_shared_modules(self) -> None:
        original_quote = index_server._load_index_quote
        original_minutes = index_server._load_index_minutes
        original_kline = index_server._load_index_kline
        original_context = index_server._load_market_context

        index_server._load_index_quote = lambda security, warnings: {
            "name": security["name"],
            "code": security["code"],
            "market": "指数",
            "price": "4027.26",
            "changeAmount": "-93.12",
            "changePercent": "-2.26%",
            "previousClose": 4120.38,
            "open": "4100.00",
            "high": "4122.00",
            "low": "4010.00",
            "amount": "922.23亿",
            "volume": "1.20亿手",
        }
        index_server._load_index_minutes = lambda security, ndays, warnings: [
            {
                "date": "2026-06-27",
                "time": "09:30",
                "timestamp": 1,
                "price": 4027.26,
                "average": 4030.00,
                "volume": 100,
                "amount": 400000,
                "volumeRatio": 1.0,
            }
        ]
        index_server._load_index_kline = lambda security, warnings: [
            {
                "date": "2026-06-26",
                "open": 4100.0,
                "close": 4027.26,
                "high": 4122.0,
                "low": 4010.0,
                "volume": 1000,
                "amount": 4000000,
                "amplitude": "2.72",
                "changePercent": "-2.26",
                "changeAmount": "-93.12",
                "turnoverRate": "--",
            }
        ]
        index_server._load_market_context = lambda code: {
            "marketBreadth": {"upCount": 18, "downCount": 81},
            "marketBreadthMeta": {"status": "ok", "source": "test"},
            "sentiment": {"sentimentTemperature": 15},
            "sentimentMeta": {"status": "ok", "source": "test"},
            "relatedIndices": [{"code": "399001", "name": "深证成指"}],
        }
        try:
            payload = index_server._build_index_detail(
                {"name": "上证指数", "code": "000001", "secid": "1.000001"}
            )
        finally:
            index_server._load_index_quote = original_quote
            index_server._load_index_minutes = original_minutes
            index_server._load_index_kline = original_kline
            index_server._load_market_context = original_context

        self.assertEqual(payload["status"], "ok")
        self.assertEqual(payload["quote"]["code"], "000001")
        self.assertEqual(len(payload["minutePoints"]), 1)
        self.assertEqual(len(payload["fiveDayPoints"]), 1)
        self.assertEqual(len(payload["kLinePoints"]), 1)
        self.assertEqual(payload["marketBreadth"]["downCount"], 81)
        self.assertEqual(payload["relatedIndices"][0]["code"], "399001")

    def test_index_detail_route_registered_once(self) -> None:
        routes = [
            route
            for route in index_server.app.router.routes
            if getattr(route, "path", None) == index_server.INDEX_DETAIL_PATH
            and "GET" in (getattr(route, "methods", None) or set())
        ]
        self.assertEqual(len(routes), 1)


if __name__ == "__main__":
    unittest.main()
