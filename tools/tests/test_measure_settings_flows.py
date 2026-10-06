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
    options = "".join(
        '<node content-desc="{label}" enabled="true" clickable="true" selected="{selected}" bounds="[0,100][100,150]">{mark}</node>'.format(
            label=label, selected="true" if label == "原有背景" else "false",
            mark='<node content-desc="已选择" text="✓"/>' if label == "原有背景" else "")
        for label in flows.SURFACE_CHOICES) if expanded else ""
    return """<hierarchy><node text="布局与外观"/><node text="完成" enabled="true" clickable="true" bounds="[100,0][200,50]"/>
      <node content-desc="全局容器背景" enabled="true" clickable="true" bounds="[0,0][100,500]">
      <node content-desc="当前选择" enabled="true" clickable="true" bounds="[0,0][100,50]">
        <node text="原有背景"/><node text="⌄"/>
      </node>""" + options + "</node></hierarchy>"


class ParsingTests(unittest.TestCase):
    def test_expanded_surface_selector_requires_all_options_and_one_selected_mark(self):
        root = flows.parse_ui(selector_xml(True))
        result = flows.verify_appearance_dialog(root, True)
        self.assertEqual(result["title"], "布局与外观")
        self.assertEqual(result["choice"]["selector"], "全局容器背景")
        self.assertEqual(result["choice"]["selectedOption"], "原有背景")
        self.assertEqual(set(result["choice"]["optionLabels"]), set(flows.SURFACE_CHOICES))
        self.assertEqual(result["finishCenter"], [150, 25])
        self.assertEqual(result["choice"]["currentRowCenter"], [50, 25])
        selector = flows.find_node(root, "全局容器背景")
        option = flows.find_node(selector, "透明", clickable=True)
        option.set("selected", "true")
        with self.assertRaises(flows.HarnessError):
            flows.verify_appearance_dialog(root, True)

    def test_collapsed_surface_selector_rejects_visible_options_and_spinner(self):
        self.assertFalse(flows.verify_appearance_dialog(
            flows.parse_ui(selector_xml(False)), False)["choice"]["expanded"])
        with self.assertRaises(flows.HarnessError):
            flows.verify_appearance_dialog(flows.parse_ui(selector_xml(True)), False)
        root = flows.parse_ui(selector_xml(False))
        flows.ET.SubElement(root, "node", {"class": "android.widget.Spinner"})
        with self.assertRaises(flows.HarnessError):
            flows.verify_appearance_dialog(root, False)

    def test_appearance_dialog_requires_one_clickable_entry_control_and_selector_row(self):
        root = flows.parse_ui(selector_xml(False))
        with self.assertRaises(flows.HarnessError):
            flows.verify_appearance_dialog(root, True)
        root = flows.parse_ui(selector_xml(False))
        selector = flows.find_node(root, "全局容器背景")
        selector.remove(selector.find("node"))
        with self.assertRaises(flows.HarnessError):
            flows.verify_appearance_dialog(root, False)

    def test_expanded_surface_selector_requires_exactly_one_mark(self):
        root = flows.parse_ui(selector_xml(True))
        selector = flows.find_node(root, "全局容器背景")
        flows.ET.SubElement(selector, "node", {"content-desc": "已选择"})
        with self.assertRaises(flows.HarnessError):
            flows.verify_appearance_dialog(root, True)

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

    def test_public_release_defaults_to_application_id_and_namespace_activity(self):
        parsed = flows.arguments([
            "--serial", "b7015e9b", "--scenario", "cold-start", "--output-name", "public",
            "--allow-physical-device",
        ])
        self.assertEqual(parsed.package, "io.github.yudigaga.fusionstatusbar")
        self.assertEqual(parsed.activity, "com.xtjm.fusionstatusbar.MainActivity")
        harness = flows.FlowHarness(parsed)
        self.assertEqual(harness.activity,
                         "io.github.yudigaga.fusionstatusbar/com.xtjm.fusionstatusbar.MainActivity")

    def test_release_without_run_as_marks_private_evidence_unavailable(self):
        harness = flows.FlowHarness(self.args())
        harness.private_evidence_available = False
        info, content = harness.private_file(
            "/data/user_de/0/io.github.yudigaga.fusionstatusbar/files/config", "private")
        self.assertIsNone(content)
        self.assertFalse(info["available"])
        self.assertEqual(info["reason"], "run_as_unavailable")

    def test_release_final_sampling_preserves_unknown_config_and_runtime_verdicts(self):
        class FakeAdb:
            def __init__(self): self.commands = []
            def shell(self, *args, **kwargs):
                self.commands.append(args)
                return "", {}, 0
        harness = flows.FlowHarness(self.args())
        harness.adb = FakeAdb()
        harness.user_id = 0
        harness.metadata = {"configBefore": harness.config_snapshot("before")}
        with patch.object(harness, "pid", return_value={"pids": ["4321"]}), \
                patch.object(harness, "renderer_stats", return_value={}), \
                patch.object(harness, "save_text"):
            harness.collect_final()
        self.assertIsNone(harness.result["committedConfigUnchanged"])
        self.assertEqual(harness.result["configInvarianceEvidence"], "not_verified_run_as_unavailable")
        receipt = harness.metadata["runtimeReceiptAfter"]
        self.assertEqual(receipt["availability"], "unavailable")
        self.assertIsNone(receipt["versionMatchesInstalled"])
        self.assertIsNone(receipt["pidMatchesCurrentSystemUi"])
        self.assertTrue(harness.adb.commands)
        self.assertTrue(all(command[0] == "dumpsys" for command in harness.adb.commands))

    def test_prepare_continues_when_release_run_as_is_denied(self):
        class FakeAdb:
            def __init__(self): self.commands = []
            def run(self, *args, **kwargs):
                self.commands.append(args)
                return "device\n", {"stdout": "state.txt", "stderr": "state-error.txt"}, 0
            def shell(self, *args, **kwargs):
                self.commands.append(args)
                row = {"stdout": "capture.txt", "stderr": "capture-error.txt"}
                if args[:1] == ("run-as",): return "", row, 1
                if args[:2] == ("getprop", "sys.boot_completed"): return "1\n", row, 0
                if args[:2] == ("am", "get-current-user"): return "0\n", row, 0
                if args[:1] == ("test",): return "", row, 1
                if args[:2] == ("dumpsys", "power"): return "mWakefulness=Awake\n", row, 0
                if args[:2] == ("cat", "/proc/uptime"): return "1000.0 0.0\n", row, 0
                if args[:2] == ("date", "+%s"): return "2000\n", row, 0
                return "", row, 0
        with tempfile.TemporaryDirectory() as directory:
            args = self.args()
            args.scenario = "cold-start"
            harness = flows.FlowHarness(args)
            harness.output = Path(directory)
            harness.raw_dir = harness.output / "unit.raw"
            fake = FakeAdb()
            package = {"versionName": "0.3.174", "versionCode": 188}
            with patch.object(flows, "Adb", return_value=fake), \
                    patch.object(harness, "package_metadata", return_value=package), \
                    patch.object(harness, "pid", return_value={"pids": ["4321"]}), \
                    patch.object(harness, "start", return_value={"app": {"pids": ["1234"]}}), \
                    patch.object(harness, "focus"), patch.object(harness, "resource_checkpoint"):
                harness.prepare()
            self.assertFalse(harness.result["privateEvidenceAvailable"])
            self.assertEqual(harness.metadata["privateEvidence"]["identityExitCode"], 1)
            self.assertEqual(harness.metadata["runtimeReceiptBefore"]["availability"], "unavailable")
            self.assertEqual([command for command in fake.commands if command[0] == "run-as"],
                             [("run-as", "io.github.yudigaga.fusionstatusbar", "id")])

    def test_explicit_package_and_activity_control_launch_and_process_sampling(self):
        class FakeAdb:
            def __init__(self): self.commands = []
            def shell(self, *args, **kwargs):
                self.commands.append(args)
                if args[:2] == ("am", "start"):
                    return "Status: ok\nTotalTime: 1200\n", {"stdout": "start.txt"}, 0
                return "1234\n", {"stdout": "pid.txt"}, 0
        args = self.args()
        args.package = "com.xtjm.fusionstatusbar"
        args.activity = ".MainActivity"
        harness = flows.FlowHarness(args)
        harness.adb = FakeAdb()
        with patch.object(flows.time, "sleep"):
            result = harness.start("launch")
        self.assertEqual(harness.adb.commands, [
            ("am", "start", "-W", "-n", "com.xtjm.fusionstatusbar/.MainActivity"),
            ("pidof", "com.xtjm.fusionstatusbar"),
        ])
        self.assertEqual(result["app"]["pids"], ["1234"])

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
