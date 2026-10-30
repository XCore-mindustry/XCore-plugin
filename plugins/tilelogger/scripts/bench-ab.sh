#!/usr/bin/env bash
# A/B benchmark of the native core: the current harness and identical flags against base and working-tree
# headers, interleaved per round, one process per scenario group. See README "Benchmarks".
set -euo pipefail

if [[ $# -lt 1 ]]; then
    echo "usage: $0 <base-ref> [rounds] [-- bench options such as --n 1000000 --reps 5]" >&2
    exit 2
fi
base=$1
shift
rounds=3
if [[ $# -gt 0 && $1 != -- ]]; then
    rounds=$1
    shift
fi
[[ ${1:-} == -- ]] && shift

root=$(git rev-parse --show-toplevel)
out=$root/build/bench
worktree=$out/base-tree
cxx=${CXX:-c++}
flags=(-std=c++20 -O3 -DNDEBUG -pthread)  # matches the Gradle release variant
mkdir -p "$out/data"

git -C "$root" worktree remove --force "$worktree" 2>/dev/null || true
git -C "$root" worktree add --quiet --detach "$worktree" "$base"
trap 'git -C "$root" worktree remove --force "$worktree" 2>/dev/null || true' EXIT
for side in base head; do
    tree=$root
    [[ $side == base ]] && tree=$worktree
    "$cxx" "${flags[@]}" -I "$tree/native/src/main/headers" "$root/native/src/bench/Bench.cpp" -o "$out/bench-$side"
done

head=$(git -C "$root" rev-parse --short HEAD)
git -C "$root" diff --quiet HEAD -- native/src/main || head+="+dirty"
results=$out/results-$(date +%Y%m%d-%H%M%S).tsv
{
    echo "# base=$(git -C "$root" rev-parse --short "$base") head=$head rounds=$rounds args=$*"
    echo "# cxx=$("$cxx" --version | head -1) flags=${flags[*]} prefix=${BENCH_PREFIX:-none}"
    echo "# host=$(uname -srm) data-fs=$(findmnt -n -o FSTYPE -T "$out/data" 2>/dev/null || echo unknown)"
} >"$results"

for round in $(seq "$rounds"); do
    sides=(base head)
    ((round % 2)) || sides=(head base)
    for group in ingest last page plan commit load journal; do
        for side in "${sides[@]}"; do
            echo "round $round/$rounds: $group ($side)" >&2
            # BENCH_PREFIX is split on purpose, e.g. BENCH_PREFIX="taskset -c 3".
            ${BENCH_PREFIX:-} "$out/bench-$side" --only "$group." --dir "$out/data" "$@" |
                sed "s/^/$side\t$round\t/" >>"$results"
        done
    done
done

python3 - "$results" <<'PY'
import collections, statistics, sys

path = sys.argv[1]
timing = collections.defaultdict(lambda: collections.defaultdict(list))
digests = collections.defaultdict(lambda: collections.defaultdict(set))
sizes = collections.defaultdict(dict)
metrics = collections.defaultdict(lambda: collections.defaultdict(list))
errors, warnings = [], set()
for line in open(path):
    if line.startswith("#"):
        print(line.rstrip())
        continue
    side, _, kind, *rest = line.rstrip("\n").split("\t")
    if kind == "result":
        name, n, _, p50, p99, top, outcome, digest = rest
        timing[(name, n)][side].append((int(p50), int(p99), int(top)))
        digests[(name, n)][side].add(f"{digest}/{outcome}")
    elif kind == "memory":
        name, n, live, peak, _, _ = rest
        sizes[("memory " + name, n)][side] = (int(live), int(peak))
    elif kind == "archive":
        name, n, size, _ = rest
        sizes[("archive " + name, n)][side] = (int(size), None)
    elif kind == "metric":
        name, n, key, value = rest
        metrics[(name, n, key)][side].append(float(value))
    elif kind == "warning":
        warnings.add(" ".join(rest))
    elif kind == "error":
        errors.append(f"{side}: {rest}")

def ratio(a, b):
    return f"{b / a:.3f}" if a else "-"

print("\nscenario\tn\tbase p50\thead p50\thead/base\tbase p99\thead p99\tbase max\thead max\tresult")
for (name, n), sides in timing.items():
    med = {s: [statistics.median(v[i] for v in sides.get(s, [(0, 0, 0)])) for i in range(3)]
           for s in ("base", "head")}
    b, h = med["base"], med["head"]
    found = digests[(name, n)]
    if any(len(found[s]) != 1 for s in ("base", "head")):
        verdict = "UNSTABLE " + str(dict(found))
    else:
        verdict = "same" if found["base"] == found["head"] else "DIFFERENT " + str(dict(found))
    print(f"{name}\t{n}\t{b[0]:.0f}\t{h[0]:.0f}\t{ratio(b[0], h[0])}\t{b[1]:.0f}\t{h[1]:.0f}\t{b[2]:.0f}\t{h[2]:.0f}\t"
          + verdict)
print("\nbytes\tn\tbase live\thead live\thead/base\tbase peak\thead peak")
for (name, n), sides in sizes.items():
    b, h = sides.get("base", (0, None)), sides.get("head", (0, None))
    print(f"{name}\t{n}\t{b[0]}\t{h[0]}\t{ratio(b[0], h[0])}\t{b[1] or '-'}\t{h[1] or '-'}")
if metrics:
    print("\nmetric (median of runs)\tn\tbase\thead\thead/base")
    for (name, n, key), sides in metrics.items():
        b, h = (statistics.median(sides[s]) if sides[s] else 0 for s in ("base", "head"))
        print(f"{name} {key}\t{n}\t{b:g}\t{h:g}\t{ratio(b, h)}")
for warning in sorted(warnings):
    print("WARNING", warning)
for error in errors:
    print("ERROR", error)
print(f"\nraw: {path}")
PY
