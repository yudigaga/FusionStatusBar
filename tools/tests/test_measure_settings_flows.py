"""Host-only contract tests; never execute adb or connect to a device."""

import importlib.util
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch


MODULE_FILE = Path(__file__).resolve().parents[1] / "measure-settings-flows.py"
SPEC = importlib.util.spec_from_file_location("measure_settings_flows", MODULE_FILE)
flows = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(flows)


def selector_xml(expanded):
    options = """
      <node content-desc="跟随默认" enabled="true" clickable="true" selected="true" bounds="[0,50][100,100]">
        <node content-desc="已选择" text="✓"/>
      </node>
      <node content-desc="微圆角" enabled="true" clickable="true" selected="false" bounds="[0,100][100,150]"/>
      <node content-desc="自定义" enabled="false" clickable="true" selected="false" bounds="[0,150][100,200]"/>
    """ if expanded else ""
    return """<hierarchy><node content-desc="卡片样式" enabled="true" clickable="true" bounds="[0,0][100,500]">
      <node content-desc="当前选择" enabled="true" clickable="true" bounds="[0,0][100,50]">
        <node text="跟随默认"/><node text="⌄"/>
      </node>""" + options + "</node></hierarchy>"


class ParsingTests(unittest.TestCase):
    def test_expanded_selector_requires_disabled_custom_and_one_mark(self):
        root = flows.parse_ui(selector_xml(True))
        result = flows.verify_editor(root, True)
        self.assertTrue(result["customDisabled"])
        self.assertEqual(result["selectionMarkCount"], 1)
        self.assertEqual(result["currentRowCenter"], [50, 25])
        custom = flows.find_node(root, "自定义", clickable=True)
        custom.set("enabled", "true")
        with self.assertRaises(flows.HarnessError):
            flows.verify_editor(root, True)

    def test_collapsed_snapshot_rejects_visible_options_and_spinner(self):
        self.assertFalse(flows.verify_editor(flows.parse_ui(selector_xml(False)), False)["expanded"])
        with self.assertRaises(flows.HarnessError):
            flows.verify_editor(flows.parse_ui(selector_xml(True)), False)
        root = flows.parse_ui(selector_xml(False))
        flows.ET.SubElement(root, "node", {"class": "android.widget.Spinner"})
        with self.assertRaises(flows.HarnessError):
            flows.verify_editor(root, False)

    def test_overview_requires_selected_nav_and_no_loading_placeholder(self):
        text = '<hierarchy><node content-desc="概述" clickable="true" selected="true"/><node text="已激活"/><node text="系统界面已连接 · 12:00"/></hierarchy>'
        root = flows.parse_ui(text)
        self.assertTrue(flows.verify_overview(root)["overviewSelected"])
        flows.ET.SubElement(root, "node", {"text": "正在加载设置"})
        with self.assertRaises(flows.HarnessError):
            flows.verify_overview(root)

    def test_receipt_and_metrics_parse_without_claiming_more_coverage(self):
        values = flows.parse_preferences('<map><string name="runtime_session">1000:4321:token</string><string name="module_version">0.3.144</string><int name="module_code" value="158"/></map>')
        self.assertEqual(values["module_code"], 158)
        stats = flows.parse_gfxinfo("Total frames rendered: 10\nJanky frames: 2 (20.0%)\n99th percentile: 90ms")
        self.assertEqual(stats, {"renderedFrames": 10, "jankyFrames": 2, "jankyPercent": 20.0, "p99Ms": 90})
        objects = flows.parse_memory_objects(" Objects\n Views: 1200 ViewRootImpl: 3\n AppContexts: 5 Activities: 1\n")
        self.assertEqual(objects, {"Views": 1200, "ViewRootImpl": 3, "AppContexts": 5, "Activities": 1})
        with self.assertRaises(flows.HarnessError):
            flows.parse_ui("old unavailable output")


class HarnessContracts(unittest.TestCase):
    def args(self):
        return SimpleNamespace(serial="b7015e9b", scenario="editor-choice", output_name="unit",
                               iterations=3, action_wait_ms=500, initial_wait_ms=2500,
                               trace_buffer_kb=32768, allow_physical_device=True, adb="adb")

    def test_adb_never_uses_host_shell_and_checks_return_code(self):
        with tempfile.TemporaryDirectory() as directory:
            raw = Path(directory) / "unit.raw"
            raw.mkdir()
            adb = flows.Adb("adb", "b7015e9b", raw, Path(directory) / "unit.commands.jsonl")
            with patch.object(flows.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, b"ok\n", b"")) as mock:
                self.assertEqual(adb.shell("getprop", "x")[0], "ok\n")
                self.assertEqual(mock.call_args.args[0], ["adb", "-s", "b7015e9b", "shell", "getprop", "x"])
                self.assertIs(mock.call_args.kwargs["shell"], False)
            with patch.object(flows.subprocess, "run", return_value=subprocess.CompletedProcess([], 2, b"", b"denied")):
                with self.assertRaises(flows.HarnessError):
                    adb.shell("bad")

    def test_existing_output_is_not_overwritten_on_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            harness = flows.FlowHarness(self.args())
            harness.output = Path(directory)
            harness.raw_dir = harness.output / "unit.raw"
            harness.raw_dir.mkdir()
            summary = harness.output / "unit.json"
            summary.write_text("existing evidence", encoding="utf-8")
            with self.assertRaises(flows.HarnessError):
                harness.run()
            self.assertEqual(summary.read_text(encoding="utf-8"), "existing evidence")

    def test_trace_is_stopped_and_failure_summary_retains_complete_cycles(self):
        class FakeAdb:
            def __init__(self): self.commands = []
            def shell(self, *args, **kwargs): self.commands.append(args); return "", {}, 0
            def run(self, *args, **kwargs): self.commands.append(args); return "", {}, 0
        with tempfile.TemporaryDirectory() as directory:
            harness = flows.FlowHarness(self.args())
            harness.output = Path(directory)
            harness.raw_dir = harness.output / "unit.raw"
            harness.raw_dir.mkdir()
            harness.created_output = True
            harness.adb = FakeAdb()
            def fail_cycle(index):
                if index == 2: raise flows.HarnessError("focus changed")
                harness.cycles.append({"cycle": index})
            def write_fake_trace(*args, **kwargs):
                harness.adb.commands.append(args)
                if args[0] == "pull":
                    Path(args[2]).write_text("<...>-123 [001] d..2 123.000001: tracing_mark_write: B|123|draw\n", encoding="utf-8")
                return "", {}, 0
            harness.adb.run = write_fake_trace
            with patch.object(harness, "prepare"), patch.object(harness, "editor_cycle", side_effect=fail_cycle), patch.object(harness, "resource_checkpoint"):
                with self.assertRaises(flows.HarnessError):
                    harness.run()
            self.assertEqual(harness.result["completedCycles"], 1)
            self.assertEqual(harness.result["status"], "failed")
            self.assertTrue(any(c[:2] == ("atrace", "--async_stop") for c in harness.adb.commands))
            self.assertTrue((harness.output / "unit.json").is_file())


if __name__ == "__main__":
    unittest.main()
