from __future__ import annotations

import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import sector_detail_server as sector_server


class SectorDetailServerTest(unittest.TestCase):
    def test_parse_sector_minutes_keeps_real_date_and_volume(self) -> None:
        raw = {
            "data": {
                "trends": [
                    "2026-06-27 09:30,1200.00,1201.20,1202.00,1199.00,100,12012000,1200.80",
                    "2026-06-27 09:31,1201.20,1203.50,1204.00,1201.00,250,30087500,1202.10",
                ]
            }
        }
        points = sector_server._parse_sector_minutes(raw)
        self.assertEqual(len(points), 2)
        self.assertEqual(points[0]["date"], "2026-06-27")
        self.assertEqual(points[0]["volume"], 100)
        self.assertEqual(points[0]["volumeRatio"], 0.4)
        self.assertEqual(points[1]["volumeRatio"], 1.0)
        self.assertGreater(points[1]["timestamp"], points[0]["timestamp"])

    def test_paged_constituents_uses_cached_full_list(self) -> None:
        original = sector_server._load_constituents_cached
        sector_server._load_constituents_cached = lambda metadata: {
            "status": "ok",
            "items": [{"code": f"600{index:03d}"} for index in range(45)],
        }
        try:
            result = sector_server._paged_constituents(
                {"code": "BK0428", "name": "测试板块"},
                page=2,
                page_size=20,
            )
        finally:
            sector_server._load_constituents_cached = original
        self.assertEqual(result["page"], 2)
        self.assertEqual(result["pageSize"], 20)
        self.assertEqual(result["total"], 45)
        self.assertTrue(result["hasMore"])
        self.assertEqual(result["items"][0]["code"], "600020")

    def test_sector_routes_registered_once(self) -> None:
        for path in (
            sector_server.SECTOR_DETAIL_PATH,
            sector_server.SECTOR_CONSTITUENTS_PATH,
            sector_server.LEGACY_CONSTITUENTS_PATH,
        ):
            routes = [
                route
                for route in sector_server.app.router.routes
                if getattr(route, "path", None) == path
                and "GET" in (getattr(route, "methods", None) or set())
            ]
            self.assertEqual(len(routes), 1, path)


if __name__ == "__main__":
    unittest.main()
