from __future__ import annotations

import argparse
import asyncio
import statistics
from time import perf_counter

import httpx


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    index = min(int((len(ordered) - 1) * fraction), len(ordered) - 1)
    return ordered[index]


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--query", default="600396")
    parser.add_argument("--requests", type=int, default=100)
    parser.add_argument("--concurrency", type=int, default=10)
    args = parser.parse_args()

    limits = httpx.Limits(max_connections=args.concurrency, max_keepalive_connections=args.concurrency)
    async with httpx.AsyncClient(base_url=args.base_url, timeout=10.0, limits=limits) as client:
        before = (await client.get("/api/stock/a-share/realtime/diagnostics")).json()
        await client.get("/api/stock/a-share/realtime", params={"query": args.query, "ndays": 1})
        semaphore = asyncio.Semaphore(args.concurrency)

        async def one() -> tuple[float, bool, int]:
            async with semaphore:
                started = perf_counter()
                response = await client.get("/api/stock/a-share/realtime", params={"query": args.query, "ndays": 1})
                elapsed = (perf_counter() - started) * 1000
                response.raise_for_status()
                payload = response.json()
                return elapsed, bool(payload.get("cacheHit")), int(payload.get("upstreamLatencyMs") or 0)

        results = await asyncio.gather(*(one() for _ in range(args.requests)))
        after = (await client.get("/api/stock/a-share/realtime/diagnostics")).json()

    latencies = [item[0] for item in results]
    hits = sum(item[1] for item in results)
    before_upstream = int((before.get("metrics") or {}).get("upstreamRequests") or 0)
    after_upstream = int((after.get("metrics") or {}).get("upstreamRequests") or 0)
    print(f"requests={len(results)} concurrency={args.concurrency}")
    print(f"p50_ms={percentile(latencies, 0.50):.2f}")
    print(f"p95_ms={percentile(latencies, 0.95):.2f}")
    print(f"p99_ms={percentile(latencies, 0.99):.2f}")
    print(f"mean_ms={statistics.fmean(latencies):.2f}")
    print(f"cache_hit_rate={hits / len(results):.2%}")
    print(f"upstream_requests_delta={after_upstream - before_upstream}")
    print(f"singleflight_waits={(after.get('metrics') or {}).get('singleflightWaits', 0)}")


if __name__ == "__main__":
    asyncio.run(main())
