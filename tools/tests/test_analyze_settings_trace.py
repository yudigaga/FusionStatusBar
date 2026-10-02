"""Failure-path checks for the standard-library atrace CLI."""

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


sys.dont_write_bytecode = True
TOOLS = Path(__file__).resolve().parents[1]
SCRIPT = TOOLS / "analyze-settings-trace.py"
FIXTURE = Path(__file__).resolve().parent / "fixtures" / "settings-opening.trace"


class SettingsTraceAnalysisTest(unittest.TestCase):
    def run_cli(self, contents=None, *arguments):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            trace = root / "input.trace"
            trace.write_text(contents if contents is not None else FIXTURE.read_text(encoding="utf-8"), encoding="utf-8")
            output = root / "nested" / "summary.json"
            process = subprocess.run(
                [sys.executable, str(SCRIPT), str(trace), "--pid", "123", "--output", str(output), *arguments],
                text=True, capture_output=True,
                env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"},
            )
            self.assertTrue(output.is_file(), process.stderr)
            return process.returncode, json.loads(output.read_text(encoding="utf-8"))

    def test_cross_thread_end_cannot_close_main_thread_begin(self):
        contents = FIXTURE.read_text(encoding="utf-8").replace(
            "main-123 ( 123) [000] .... 1.010000", "render-124 ( 123) [000] .... 1.010000",
        )
        code, summary = self.run_cli(contents, "--expected-openings", "1")
        self.assertEqual(1, code)
        self.assertEqual(1, summary["parser"]["crossThreadEndEvents"])
        self.assertEqual(1, summary["parser"]["unfinishedBeginEvents"])
        self.assertEqual(1, summary["parser"]["matchedSlices"])
        self.assertIsNone(summary["firstWindowEvents"][0]["enclosingDoFrame"])

    def test_absent_opening_does_not_pass_validation(self):
        contents = FIXTURE.read_text(encoding="utf-8").replace("#first=true", "#first=false")
        code, summary = self.run_cli(contents, "--expected-openings", "1")
        self.assertEqual(1, code)
        self.assertEqual("failed", summary["validation"]["status"])
        self.assertIn("no_first_window_events", summary["validation"]["failures"])
        self.assertIn("unexpected_opening_count", summary["validation"]["failures"])

    def test_threshold_red_green_both_save_summary(self):
        green, green_summary = self.run_cli(None, "--expected-openings", "1", "--max-opening-frame-ms", "10")
        red, red_summary = self.run_cli(None, "--expected-openings", "1", "--max-opening-frame-ms", "9.9")
        self.assertEqual(0, green)
        self.assertEqual("passed", green_summary["validation"]["status"])
        self.assertEqual(1, red)
        self.assertEqual("failed", red_summary["validation"]["status"])
        self.assertEqual(10.0, red_summary["validation"]["exceededOpeningDoFrames"][0]["durationMs"])
        self.assertEqual(10.0, red_summary["openingDoFrameStats"]["maxMs"])
        self.assertIn("not CPU", red_summary["durationKind"])

    def test_incomplete_trace_failures(self):
        fixture = FIXTURE.read_text(encoding="utf-8")
        cases = {
            "trace_header_missing": "\n".join(line for line in fixture.splitlines() if not line.startswith("#")),
            "trace_header_reports_overwritten_entries": fixture.replace("4/4", "4/9"),
            "no_target_events": fixture.replace("-123", "-999").replace("|123", "|999"),
            "unmatched_end_events": fixture + " main-123 ( 123) [000] .... 1.011000: tracing_mark_write: E|123\n",
            "unfinished_begin_events": fixture + " main-123 ( 123) [000] .... 1.011000: tracing_mark_write: B|123|unfinished\n",
        }
        for reason, contents in cases.items():
            with self.subTest(reason=reason):
                code, summary = self.run_cli(contents, "--expected-openings", "1")
                self.assertEqual(1, code)
                self.assertIn(reason, summary["validation"]["failures"])

    def test_default_reports_without_claiming_validation_passed(self):
        contents = FIXTURE.read_text(encoding="utf-8").replace("#first=true", "#first=false")
        code, summary = self.run_cli(contents)
        self.assertEqual(0, code)
        self.assertEqual("not_requested", summary["validation"]["status"])
        self.assertEqual(0, summary["validation"]["observedOpenings"])

    def test_unknown_tgid_remains_reported_without_fabricating_a_match(self):
        contents = FIXTURE.read_text(encoding="utf-8").replace("( 123)", "(-----)")
        code, summary = self.run_cli(contents, "--expected-openings", "1")
        self.assertEqual(0, code)
        self.assertEqual(4, summary["target"]["unknownTgidLinesForTargetTid"])
        self.assertEqual({}, summary["target"]["numericTgidObserved"])


if __name__ == "__main__":
    unittest.main()
