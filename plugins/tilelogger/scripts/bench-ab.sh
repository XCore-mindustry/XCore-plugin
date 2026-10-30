#!/usr/bin/env bash
# Same harness and compiler against base and working-tree headers; isolated files per invocation.
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
[[ $rounds =~ ^[1-9][0-9]*$ ]] || { echo "rounds must be positive" >&2; exit 2; }
args=("$@")
for ((i=0; i<${#args[@]}; i+=2)); do
    case ${args[i]} in
        --n|--reps|--seed|--players|--rate|--seconds) ;;
        *) echo "unsupported option: ${args[i]} (use BENCH_DATA_DIR to select storage)" >&2; exit 2 ;;
    esac
    [[ $((i+1)) -lt ${#args[@]} && ${args[i+1]} =~ ^[0-9]+$ ]] || {
        echo "missing numeric option value" >&2; exit 2;
    }
done

root=$(git rev-parse --show-toplevel)
base=$(git -C "$root" rev-parse --verify --end-of-options "$base^{commit}")
mkdir -p "$root/build/bench"
out=$(mktemp -d "$root/build/bench/run-XXXXXX")
worktree=$out/base-tree
data=$out/data
if [[ -n ${BENCH_DATA_DIR:-} ]]; then
    mkdir -p "$BENCH_DATA_DIR"
    data=$(mktemp -d "$BENCH_DATA_DIR/tilelogger-bench-XXXXXX")
fi
cxx=${CXX:-c++}
flags=(-std=c++20 -O3 -DNDEBUG -pthread)  # matches the Gradle release variant
mkdir -p "$data"
read -r -a prefix <<< "${BENCH_PREFIX:-}"

trap 'git -C "$root" worktree remove "$worktree" 2>/dev/null || true; rmdir "$data" 2>/dev/null || true' EXIT
git -C "$root" worktree add --quiet --detach "$worktree" "$base"
for side in base head; do
    tree=$root
    [[ $side == base ]] && tree=$worktree
    "$cxx" "${flags[@]}" -I "$tree/native/src/main/headers" "$root/native/src/bench/Bench.cpp" -o "$out/bench-$side"
    "$cxx" "${flags[@]}" -DTILELOGGER_BENCH_MEMORY -I "$tree/native/src/main/headers" \
        "$root/native/src/bench/Bench.cpp" -o "$out/memory-$side"
done

head=$(git -C "$root" rev-parse HEAD)
[[ -z $(git -C "$root" status --porcelain -- native/src/main) ]] || head+="+dirty"
results=$out/results.tsv
{
    echo "# base=$(git -C "$root" rev-parse --short "$base") head=$head rounds=$rounds args=$*"
    echo "# cxx=$("$cxx" --version | head -1) flags=${flags[*]} prefix=${BENCH_PREFIX:-none}"
    echo "# host=$(uname -srm) data=$data fs=$(findmnt -n -o FSTYPE -T "$data" 2>/dev/null || echo unknown)"
    echo "# harness-sha256=$(sha256sum "$root/native/src/bench/Bench.cpp" | cut -d ' ' -f 1)"
} >"$results"

run_case() {
    local binary=$1
    if ! "${prefix[@]}" "$binary" --only "$group." --dir "$data" "${args[@]}" |
            sed "s/^/$side\t$round\t/" >>"$results"; then
        echo "ERROR: $side/$group round $round failed; raw output: $results" >&2
        tail -n 3 "$results" >&2
        return 1
    fi
}

for ((round=1; round<=rounds; round++)); do
    sides=(base head)
    ((round % 2)) || sides=(head base)
    for group in ingest last page plan commit load journal; do
        for side in "${sides[@]}"; do
            echo "round $round/$rounds: $group ($side)" >&2
            run_case "$out/bench-$side"
            if [[ $group == ingest || $group == load ]]; then
                run_case "$out/memory-$side"
            fi
        done
    done
done

python3 "$root/scripts/compare-bench.py" "$results" "$rounds"
