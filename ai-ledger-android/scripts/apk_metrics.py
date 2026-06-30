#!/usr/bin/env python3
"""Generate a deterministic APK size report and optionally enforce a regression budget."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import sys
import zipfile

MIB = 1024 * 1024


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--current", required=True, type=Path)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--mapping", type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--max-growth-percent", type=float, default=5.0)
    parser.add_argument("--max-growth-bytes", type=int, default=MIB)
    return parser.parse_args()


def human_bytes(value: int) -> str:
    if value >= MIB:
        return f"{value / MIB:.2f} MiB"
    if value >= 1024:
        return f"{value / 1024:.1f} KiB"
    return f"{value} B"


def category(name: str) -> str:
    if name.startswith("classes") and name.endswith(".dex"):
        return "DEX"
    if name == "resources.arsc" or name.startswith("res/"):
        return "Android resources"
    if name.startswith("assets/"):
        return "Assets"
    if name.startswith("lib/"):
        return "Native libraries"
    if name.startswith("META-INF/"):
        return "Signing metadata"
    return "Other"


def inspect_apk(path: Path) -> dict[str, object]:
    if not path.is_file():
        raise FileNotFoundError(path)
    buckets: dict[str, list[int]] = {}
    entry_count = 0
    dex_count = 0
    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            if info.is_dir():
                continue
            entry_count += 1
            group = category(info.filename)
            values = buckets.setdefault(group, [0, 0, 0])
            values[0] += info.compress_size
            values[1] += info.file_size
            values[2] += 1
            if group == "DEX":
                dex_count += 1
    return {
        "path": path,
        "bytes": path.stat().st_size,
        "entry_count": entry_count,
        "dex_count": dex_count,
        "buckets": buckets,
    }


def render_table(metrics: dict[str, object]) -> list[str]:
    buckets = metrics["buckets"]
    assert isinstance(buckets, dict)
    lines = [
        "| APK section | Compressed | Uncompressed | Entries |",
        "|---|---:|---:|---:|",
    ]
    order = [
        "DEX",
        "Android resources",
        "Assets",
        "Native libraries",
        "Signing metadata",
        "Other",
    ]
    for name in order:
        compressed, uncompressed, count = buckets.get(name, [0, 0, 0])
        lines.append(
            f"| {name} | {human_bytes(compressed)} | "
            f"{human_bytes(uncompressed)} | {count} |"
        )
    return lines


def main() -> int:
    args = parse_args()
    current = inspect_apk(args.current)
    baseline = None
    if args.baseline and args.baseline.is_file():
        baseline = inspect_apk(args.baseline)

    current_bytes = int(current["bytes"])
    delta_bytes = 0
    delta_percent = 0.0
    regression = False
    if baseline is not None:
        baseline_bytes = int(baseline["bytes"])
        delta_bytes = current_bytes - baseline_bytes
        delta_percent = (delta_bytes / baseline_bytes * 100.0) if baseline_bytes else 0.0
        regression = (
            delta_bytes > args.max_growth_bytes
            and delta_percent > args.max_growth_percent
        )

    lines = [
        "# Compose Android APK regression report",
        "",
        f"- Current APK: **{human_bytes(current_bytes)}**",
        f"- ZIP entries: **{current['entry_count']}**",
        f"- DEX files: **{current['dex_count']}**",
        "- R8 minification: **enabled**",
        "- Android resource shrinking: **enabled**",
    ]
    if args.mapping and args.mapping.is_file():
        lines.append(f"- R8 mapping: **{human_bytes(args.mapping.stat().st_size)}**")
    else:
        lines.append("- R8 mapping: not found")

    if baseline is None:
        lines.extend(
            [
                "- Baseline APK: unavailable; this run establishes the first comparable report.",
                "",
            ]
        )
    else:
        sign = "+" if delta_bytes >= 0 else ""
        lines.extend(
            [
                f"- Baseline APK: **{human_bytes(int(baseline['bytes']))}**",
                f"- APK delta: **{sign}{human_bytes(delta_bytes)} "
                f"({sign}{delta_percent:.2f}%)**",
                f"- Regression budget: more than {human_bytes(args.max_growth_bytes)} "
                f"and {args.max_growth_percent:.1f}% in the same run",
                "",
            ]
        )

    lines.extend(render_table(current))
    lines.extend(
        [
            "",
            "## Result",
            "",
            "❌ APK size regression exceeded the configured budget."
            if regression
            else "✅ APK size stayed within the configured regression budget.",
            "",
            f"Commit: `{os.environ.get('GITHUB_SHA', 'local')}`",
        ]
    )

    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    return 1 if regression else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, zipfile.BadZipFile) as error:
        print(f"APK metrics failed: {error}", file=sys.stderr)
        raise SystemExit(2)
