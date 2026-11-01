#!/usr/bin/env python3
"""Regression checks for false-success cases in the A/B report, without timing assertions."""
import runpy
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

CHECKER = Path(__file__).with_name("compare-bench.py")
MODEL = runpy.run_path(str(CHECKER))


def complete_run():
    rows = []
    for side in ("base", "head"):
        rows.append(f"{side}\t1\tmeta\tharness=2\tmemory_mode=0")
        for name in sorted(MODEL["SCENARIOS"]):
            sampling = "batch_mean_ns_per_record" if name.startswith("ingest.") else "operation_ns"
            rows.append(f"{side}\t1\tresult\t{name}\t10000\t1\t{sampling}\t10\t20\t30\tok\tabcd")
            if name.startswith("journal."):
                for key in ("admitted", "producer_seconds", "producer_records_per_s", "bytes_per_record"):
                    rows.append(f"{side}\t1\tmetric\t{name}\t10000\t{key}\t1")
        rows.append(f"{side}\t1\tmeta\tharness=2\tmemory_mode=1")
        for name in sorted(MODEL["MEMORY"]):
            rows.append(f"{side}\t1\tmemory\t{name}\t10000\t100\t200\t0.01\t-")
        for name in ("load.plain", "load.rollback"):
            rows.append(f"{side}\t1\tarchive\t{name}\t10000\t1000\t0.1")
    return rows


class ComparisonTest(unittest.TestCase):
    def run_rows(self, rows, rounds=1):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "results.tsv"
            path.write_text("\n".join(rows) + "\n")
            return subprocess.run([sys.executable, str(CHECKER), str(path), str(rounds)],
                                  capture_output=True, text=True)

    def test_complete_run_and_timing_regression_are_advisory(self):
        rows = complete_run()
        rows = [r.replace("\t10\t20\t30\t", "\t100\t200\t300\t")
                if r.startswith("head\t1\tresult") else r for r in rows]
        self.assertEqual(0, self.run_rows(rows).returncode)

    def test_different_digest_fails(self):
        rows = [r.replace("abcd", "ffff") if r.startswith("head\t1\tresult\tlast.miss\t") else r
                for r in complete_run()]
        result = self.run_rows(rows)
        self.assertEqual(1, result.returncode)
        self.assertIn("DIFFERENT", result.stdout)

    def test_missing_scenario_or_round_fails(self):
        rows = [r for r in complete_run() if not r.startswith("head\t1\tresult\tlast.miss\t")]
        self.assertEqual(1, self.run_rows(rows).returncode)
        self.assertEqual(1, self.run_rows(complete_run(), rounds=2).returncode)

    def test_repeated_rounds_must_remain_deterministic(self):
        rows = complete_run()
        second = [r.replace("\t1\t", "\t2\t", 1) for r in rows]
        self.assertEqual(0, self.run_rows(rows + second, rounds=2).returncode)
        second = [r.replace("abcd", "ffff") if "\tresult\tlast.miss\t" in r else r for r in second]
        self.assertEqual(1, self.run_rows(rows + second, rounds=2).returncode)

    def test_error_empty_and_duplicate_results_fail(self):
        rows = complete_run()
        result_row = next(r for r in rows if "\tresult\t" in r)
        for bad in (rows + ["base\t1\terror\tload.plain\tcorrupt archive"],
                    rows + [result_row], [],
                    [r.replace("\t1\toperation_ns\t", "\t0\toperation_ns\t") for r in rows]):
            with self.subTest(rows=len(bad)):
                self.assertEqual(1, self.run_rows(bad).returncode)

    def test_mixed_versions_and_instrumented_timings_fail(self):
        rows = complete_run()
        for source, target in (("harness=2", "harness=3"), ("memory_mode=0", "memory_mode=1")):
            bad = [r.replace(source, target) if r.startswith("head\t") else r for r in rows]
            self.assertEqual(1, self.run_rows(bad).returncode)

    def test_failed_or_incomplete_journal_fails(self):
        rows = complete_run()
        bad = [r.replace("\tok\tabcd", "\tstalled\tabcd")
               if "\tresult\tjournal.sustained\t" in r else r for r in rows]
        self.assertEqual(1, self.run_rows(bad).returncode)
        bad = [r for r in rows if "\tmetric\tjournal.sustained\t10000\tadmitted\t" not in r]
        self.assertEqual(1, self.run_rows(bad).returncode)

    def test_verified_burst_can_have_different_admitted_workloads(self):
        rows = [r.replace("\tok\tabcd", "\toverflow\tffff")
                if r.startswith("head\t1\tresult\tjournal.burst.") else r for r in complete_run()]
        result = self.run_rows(rows)
        self.assertEqual(0, result.returncode)
        self.assertIn("roundtrip verified; variable workload", result.stdout)

    def test_runner_preserves_and_reports_a_failed_process(self):
        with tempfile.TemporaryDirectory() as directory:
            compiler = Path(directory) / "fake-cxx"
            compiler.write_text(
                "#!/usr/bin/env python3\nimport pathlib, sys\n"
                "if '--version' in sys.argv:\n    print('test compiler')\n"
                "else:\n"
                "    p = pathlib.Path(sys.argv[sys.argv.index('-o') + 1])\n"
                "    p.write_text(\"#!/bin/sh\\nprintf 'error\\\\tbench\\\\tforced failure\\\\n'\\nexit 1\\n\")\n"
                "    p.chmod(0o755)\n")
            compiler.chmod(0o755)
            env = dict(os.environ, CXX=str(compiler), BENCH_PREFIX="", BENCH_DATA_DIR="")
            result = subprocess.run([str(CHECKER.with_name("bench-ab.sh")), "HEAD", "1"],
                                    cwd=CHECKER.parent.parent, env=env, capture_output=True, text=True)
            self.assertEqual(1, result.returncode)
            self.assertIn("raw output:", result.stderr)
            self.assertIn("forced failure", result.stderr)


if __name__ == "__main__":
    unittest.main()
