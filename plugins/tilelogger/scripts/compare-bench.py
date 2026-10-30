#!/usr/bin/env python3
"""Compare a complete A/B run. Timing differences are advisory; invalid results fail."""
import collections
import math
import statistics
import sys
from pathlib import Path

SCENARIOS = {
    "ingest.history", "ingest.logger.repeated", "ingest.logger.unique",
    "last.recent", "last.deep", "last.miss", "page.hot-zone", "page.window.active",
    "page.window.absent", "page.before.1", "page.before.50", "page.before.99",
    "plan.miss-1x1", "plan.hot-zone", "plan.wide-other-player", "plan.wide-all",
    "commit.fresh", "commit.prior", "load.plain", "load.rollback",
    "journal.sustained", "journal.burst.small", "journal.burst.large",
}
MEMORY = {s for s in SCENARIOS if s.startswith(("ingest.", "load."))}


def compare(path, rounds):
    results, memory, archives = {}, {}, {}
    metrics = collections.defaultdict(lambda: collections.defaultdict(list))
    mode, versions, warnings, errors = {}, set(), set(), []
    metric_rows = set()
    for line in Path(path).read_text().splitlines():
        if line.startswith("#"):
            print(line)
            continue
        side, round_text, kind, *fields = line.split("\t")
        round_id = int(round_text)
        if side not in ("base", "head") or not 1 <= round_id <= rounds:
            raise ValueError("Unexpected side or repetition")
        if kind == "meta":
            meta = dict(f.split("=", 1) for f in fields)
            if "harness" in meta:
                versions.add(meta["harness"])
                mode[side, round_id] = int(meta["memory_mode"])
            continue
        if kind == "warning":
            warnings.add(" ".join(fields))
            continue
        if kind == "error":
            errors.append(f"{side}: {' '.join(fields)}")
            continue
        if kind == "result":
            name, n, ops, sampling, p50, p99, maximum, outcome, digest = fields
            key = side, round_id, name
            if key in results or name not in SCENARIOS or mode.get((side, round_id)) != 0:
                raise ValueError(f"Duplicate, unknown or instrumented timing result: {key}")
            if int(ops) <= 0 or sampling not in ("operation_ns", "batch_mean_ns_per_record"):
                raise ValueError(f"Empty or invalid timing sample: {name}")
            allowed = {"ok", "overflow"} if name.startswith("journal.") else (
                {"ok", "rejected"} if name.startswith("plan.") else {"ok"})
            if not set(outcome.split("+")) <= allowed:
                raise ValueError(f"Failed scenario: {name}: {outcome}")
            times = tuple(int(t) for t in (p50, p99, maximum))
            if not 0 < times[0] <= times[1] <= times[2]:
                raise ValueError(f"Invalid timing values: {name}")
            results[key] = (n, sampling, times, outcome, digest)
        elif kind == "memory":
            name, n, live, peak, _, _ = fields
            key = side, round_id, name
            if key in memory or name not in MEMORY or mode.get((side, round_id)) != 1:
                raise ValueError(f"Duplicate or uninstrumented memory result: {key}")
            memory[key] = (n, int(live), int(peak))
        elif kind == "archive":
            name, n, size, _ = fields
            archives.setdefault((side, name, n), set()).add(int(size))
        elif kind == "metric":
            name, n, key, value = fields
            row = side, round_id, name, key
            if name not in SCENARIOS or row in metric_rows:
                raise ValueError(f"Duplicate or unknown metric: {row}")
            metric_rows.add(row)
            number = float(value)
            if not math.isfinite(number) or number < 0:
                raise ValueError(f"Invalid metric: {name}/{key}")
            metrics[name, n, key][side].append(number)
        else:
            raise ValueError(f"Unknown output row: {kind}")

    if len(versions) != 1 or errors:
        raise ValueError(f"Missing/mixed harness versions or scenario errors: {versions}, {errors}")
    expected = {(s, r, name) for s in ("base", "head") for r in range(1, rounds + 1)
                for name in SCENARIOS}
    if results.keys() != expected:
        raise ValueError(f"Missing timing results: {sorted(expected - results.keys())}")
    expected_memory = {k for k in expected if k[2] in MEMORY}
    if memory.keys() != expected_memory:
        raise ValueError(f"Missing memory results: {sorted(expected_memory - memory.keys())}")
    expected_metrics = {(s, r, name, key) for s, r, name in expected if name.startswith("journal.")
                        for key in ("admitted", "producer_seconds", "producer_records_per_s", "bytes_per_record")}
    if not expected_metrics <= metric_rows:
        raise ValueError(f"Missing journal metrics: {sorted(expected_metrics - metric_rows)}")
    if len({v[0] for v in results.values()} | {v[0] for v in memory.values()}) != 1:
        raise ValueError("Mixed fixture sizes")

    print("\nscenario\tsampling\tbase p50\thead p50\thead/base\tbase p99\thead p99\tverification")
    for name in sorted(SCENARIOS):
        sides = {s: [results[s, r, name] for r in range(1, rounds + 1)] for s in ("base", "head")}
        if len({v[1] for values in sides.values() for v in values}) != 1:
            raise ValueError(f"Mixed sampling methods: {name}")
        found = {(v[3], v[4]) for values in sides.values() for v in values}
        variable = name.startswith("journal.burst.") or (
            name == "journal.sustained" and any(v[3] != "ok" for values in sides.values() for v in values))
        if not variable and len(found) != 1:
            errors.append(f"DIFFERENT results: {name}: {found}")
        med = {s: [statistics.median(v[2][i] for v in values) for i in range(3)]
               for s, values in sides.items()}
        b, h = med["base"], med["head"]
        verdict = "roundtrip verified; variable workload" if variable else "same" if len(found) == 1 else "DIFFERENT"
        print(f"{name}\t{sides['base'][0][1]}\t{b[0]:g}\t{h[0]:g}\t{h[0]/b[0]:.3f}\t{b[1]:g}\t{h[1]:g}\t{verdict}")

    print("\nrequested bytes\tbase live\thead live\tbase peak\thead peak")
    for name in sorted(MEMORY):
        sides = {s: [memory[s, r, name][1:] for r in range(1, rounds + 1)] for s in ("base", "head")}
        if any(len(set(v)) != 1 for v in sides.values()):
            errors.append(f"UNSTABLE memory: {name}")
        b, h = (sides[s][0] for s in ("base", "head"))
        print(f"{name}\t{b[0]}\t{h[0]}\t{b[1]}\t{h[1]}")

    for values in archives.values():
        if len(values) != 1:
            errors.append("UNSTABLE archive size within a side")
    print("\narchive bytes (changes require explanation)")
    for name in ("load.plain", "load.rollback"):
        values = {s: [v for (side, scenario, _), v in archives.items() if side == s and scenario == name]
                  for s in ("base", "head")}
        if any(len(v) != 1 for v in values.values()):
            raise ValueError(f"Missing archive size: {name}")
        print(name, values)

    print("\nmetric (median of runs)\tbase\thead")
    for (name, _, key), sides in sorted(metrics.items()):
        if any(len(sides[s]) != rounds for s in ("base", "head")):
            print(f"{name} {key}\tpartial/not comparable")
            continue
        print(f"{name} {key}\t{statistics.median(sides['base']):g}\t{statistics.median(sides['head']):g}")
    for warning in sorted(warnings):
        print("WARNING", warning)
    for error in errors:
        print("ERROR", error)
    print(f"\nraw: {path}")
    return 1 if errors else 0


if __name__ == "__main__":
    try:
        if len(sys.argv) != 3 or int(sys.argv[2]) < 1:
            raise ValueError("usage: compare-bench.py results.tsv positive-round-count")
        sys.exit(compare(sys.argv[1], int(sys.argv[2])))
    except (ValueError, KeyError, OSError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        sys.exit(1)
