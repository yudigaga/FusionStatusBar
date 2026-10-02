#!/usr/bin/env python3
"""Collect reproducible cold-start or inline-editor evidence with stdlib only.

This intentionally keeps UIAutomator, focus checks and resource checkpoints in
the capture. Their observer overhead is documented in the result. A settled UI
snapshot cannot establish the absence of a transient startup flash, and memory
checkpoints alone cannot establish the absence of a leak.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from datetime import datetime, timezone
import xml.etree.ElementTree as ET


PACKAGE = "com.xtjm.fusionstatusbar"
ACTIVITY = PACKAGE + "/.MainActivity"
ROOT = Path(__file__).resolve().parents[1]
BOUNDS = re.compile(r"^\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$")


class HarnessError(RuntimeError):
    pass


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def parse_ui(text):
    """Reject stale/non-XML captures; return the complete element tree."""
    try:
        root = ET.fromstring(text)
    except ET.ParseError as error:
        raise HarnessError("UI hierarchy is not valid XML") from error
    if root.tag != "hierarchy" or not list(root.iter("node")):
        raise HarnessError("UI hierarchy has no nodes")
    return root


def find_node(root, value, attr="content-desc", clickable=None):
    matches = [n for n in root.iter("node") if n.get(attr) == value
               and (clickable is None or n.get("clickable") == str(clickable).lower())]
    if len(matches) != 1:
        raise HarnessError(f"Expected one UI node {attr}={value!r}, found {len(matches)}")
    return matches[0]


def node_center(node):
    match = BOUNDS.fullmatch(node.get("bounds", ""))
    if match is None or node.get("enabled") != "true":
        raise HarnessError("UI node is disabled or its bounds cannot be parsed")
    left, top, right, bottom = map(int, match.groups())
    if left < 0 or top < 0 or right <= left or bottom <= top:
        raise HarnessError("UI node is outside the screen or has empty bounds")
    return [(left + right) // 2, (top + bottom) // 2]


def verify_overview(root):
    overview = find_node(root, "概述", clickable=True)
    selected = overview.get("selected") == "true"
    nodes = list(root.iter("node"))
    labels = [n.get("text", "") for n in nodes]
    loading = any("正在加载设置" in n.get("text", "")
                  or "正在加载设置" in n.get("content-desc", "") for n in nodes)
    activation = [t for t in labels if t in ("已激活", "未检测到激活")
                  or "系统界面已连接" in t or "本次开机尚未收到系统界面连接记录" in t]
    if not selected or loading or not activation:
        raise HarnessError("Cold-start settled UI is not the selected overview with activation status")
    return {"overviewSelected": selected, "startupPlaceholderVisible": loading,
            "activationLabels": activation, "settledSnapshotOnly": True}


def selector_current_row(root):
    selector = find_node(root, "卡片样式")
    rows = [n for n in selector.iter("node")
            if n.get("content-desc") == "当前选择" and n.get("clickable") == "true"]
    if len(rows) != 1:
        raise HarnessError("Card-style selector does not expose exactly one current-value row")
    return selector, rows[0]


def verify_editor(root, expanded):
    nodes = list(root.iter("node"))
    if any(n.get("class") == "android.widget.Spinner" for n in nodes):
        raise HarnessError("Editor snapshot contains a legacy Spinner")
    selector, current = selector_current_row(root)
    if selector.get("enabled") != "true":
        raise HarnessError("Card-style selector is locked; this run must not alter the lock")
    options = [n for n in selector.iter("node") if n.get("clickable") == "true"
               and n.get("content-desc") not in (None, "", "卡片样式", "当前选择")]
    selected_value = [n.get("text", "") for n in current.iter("node")
                      if n.get("text") and n.get("text") not in ("⌄", "⌃")]
    result = {"expanded": expanded, "hasSpinner": False, "selectorEnabled": True,
              "currentValueLabels": selected_value, "currentRowCenter": node_center(current)}
    if expanded:
        minor = find_node(selector, "微圆角", clickable=True)
        custom = find_node(selector, "自定义", clickable=True)
        marks = [n for n in selector.iter("node") if n.get("content-desc") == "已选择"]
        selected_options = [n for n in options if n.get("selected") == "true"]
        if minor.get("enabled") != "true" or custom.get("enabled") != "false":
            raise HarnessError("Expected enabled 微圆角 and disabled 自定义")
        if len(marks) != 1 or len(selected_options) != 1:
            raise HarnessError("Expected exactly one selected option and selection mark")
        result.update({"customDisabled": True, "minorRadiusEnabled": True,
                       "selectionMarkCount": len(marks),
                       "selectedOption": selected_options[0].get("content-desc")})
    elif options:
        raise HarnessError("Card-style options are still visible after collapse")
    return result


def parse_preferences(text):
    try:
        root = ET.fromstring(text)
        result = {}
        for n in root:
            if n.tag == "string":
                result[n.get("name", "")] = n.text or ""
            else:
                value = n.get("value", "")
                result[n.get("name", "")] = int(value) if n.tag in ("int", "long") else value
        return result
    except (ET.ParseError, ValueError) as error:
        raise HarnessError("Activation receipt cannot be parsed") from error


def parse_gfxinfo(text):
    result = {}
    for key, pattern in {
        "renderedFrames": r"Total frames rendered:\s*(\d+)",
        "jankyFrames": r"Janky frames:\s*(\d+)",
        "jankyPercent": r"Janky frames:\s*\d+\s*\(([\d.]+)%\)",
        "p50Ms": r"50th percentile:\s*(\d+)ms",
        "p90Ms": r"90th percentile:\s*(\d+)ms",
        "p95Ms": r"95th percentile:\s*(\d+)ms",
        "p99Ms": r"99th percentile:\s*(\d+)ms",
    }.items():
        match = re.search(pattern, text)
        if match:
            result[key] = float(match.group(1)) if key == "jankyPercent" else int(match.group(1))
    return result


def parse_memory_objects(text):
    result = {}
    for name in ("Views", "ViewRootImpl", "AppContexts", "Activities"):
        match = re.search(r"\b" + name + r":\s*(\d+)\b", text)
        result[name] = int(match.group(1)) if match else None
    return result


class Adb:
    def __init__(self, executable, serial, raw_dir, journal):
        self.executable = executable
        self.serial = serial
        self.raw_dir = raw_dir
        self.journal = journal
        self.counter = 0

    def run(self, *arguments, label="adb", allowed=(0,), timeout=90):
        self.counter += 1
        safe = re.sub(r"[^A-Za-z0-9._-]", "_", label)
        stem = f"{self.counter:05d}-{safe}"
        command = [self.executable, "-s", self.serial, *map(str, arguments)]
        started = utc_now()
        monotonic = time.monotonic()
        try:
            completed = subprocess.run(command, shell=False, stdout=subprocess.PIPE,
                                       stderr=subprocess.PIPE, timeout=timeout, check=False)
        except (OSError, subprocess.TimeoutExpired) as error:
            with self.journal.open("a", encoding="utf-8") as stream:
                stream.write(json.dumps({"command": command, "startedUtc": started,
                                         "error": str(error)}, ensure_ascii=False) + "\n")
            raise HarnessError(f"ADB command could not complete: {arguments}") from error
        stdout = completed.stdout.decode("utf-8", errors="replace")
        stderr = completed.stderr.decode("utf-8", errors="replace")
        stdout_file = self.raw_dir / (stem + ".stdout.txt")
        stderr_file = self.raw_dir / (stem + ".stderr.txt")
        stdout_file.write_bytes(completed.stdout)
        stderr_file.write_bytes(completed.stderr)
        row = {"command": command, "startedUtc": started, "endedUtc": utc_now(),
               "elapsedSeconds": round(time.monotonic() - monotonic, 6),
               "exitCode": completed.returncode, "allowedExitCodes": list(allowed),
               "stdout": str(stdout_file.relative_to(self.raw_dir.parent)),
               "stderr": str(stderr_file.relative_to(self.raw_dir.parent))}
        with self.journal.open("a", encoding="utf-8") as stream:
            stream.write(json.dumps(row, ensure_ascii=False) + "\n")
        if completed.returncode not in allowed:
            raise HarnessError(f"ADB failed ({completed.returncode}): {arguments}; {stderr.strip()}")
        return stdout, row, completed.returncode

    def shell(self, *arguments, **kwargs):
        return self.run("shell", *arguments, **kwargs)


class FlowHarness:
    def __init__(self, args):
        self.args = args
        self.output = ROOT / "build" / "perf"
        self.prefix = args.output_name
        self.raw_dir = self.output / (self.prefix + ".raw")
        self.remote_xml = "/sdcard/" + self.prefix + ".xml"
        self.cycles = []
        self.checkpoints = []
        self.traces = []
        self.result = {"scenario": args.scenario, "serial": args.serial,
                       "iterationsRequested": args.iterations, "completedCycles": 0,
                       "measurementStartedUtc": None, "measurementEndedUtc": None,
                       "cycles": self.cycles, "resourceCheckpoints": self.checkpoints,
                       "traces": self.traces, "traceSampling": "one independent trace per cycle",
                       "observerOverhead": "focus dumps, UIAutomator, gfxinfo and periodic resource snapshots are included",
                       "startupFlashAbsenceVerified": False, "memoryLeakAbsenceVerified": False,
                       "framestatsCoverage": "renderer_retained_history_may_cover_only_recent_frames",
                       "inlineResizeInterpretation": "inline expansion may legitimately resize its sheet; no zero-resize threshold",
                       "status": "preparing"}
        self.metadata = {}
        self.adb = None
        self.created_output = False

    def remote_trace(self, index):
        return f"/sdcard/{self.prefix}.cycle-{index:02d}.trace"

    def filename(self, suffix):
        return self.output / (self.prefix + "." + suffix)

    def write_json(self, suffix, value):
        self.filename(suffix).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    def save_text(self, suffix, value):
        self.filename(suffix).write_text(value, encoding="utf-8")

    def ui(self, label):
        # Remove our own previous dump so a failed capture cannot reuse stale XML.
        self.adb.shell("rm", "-f", self.remote_xml, label=label + "-remove-old-ui")
        for attempt in range(3):
            output, _, code = self.adb.shell("uiautomator", "dump", self.remote_xml,
                                           label=label + f"-ui-dump-{attempt}", allowed=(0, 1))
            if code == 0 and "dumped to:" in output:
                text, _, _ = self.adb.shell("cat", self.remote_xml, label=label + "-ui-cat")
                root = parse_ui(text)
                self.save_text(label + ".xml", text)
                return root
            time.sleep(0.5)
        raise HarnessError("UIAutomator did not produce a fresh hierarchy")

    def focus(self, label):
        text, row, _ = self.adb.shell("dumpsys", "window", label=label)
        match = re.search(r"mCurrentFocus=Window\{([^}\r\n]+)\}", text)
        if match is None or PACKAGE + "/" not in match.group(1):
            raise HarnessError("The unlocked target app does not own the focused window")
        return {"window": match.group(1).split()[0], "raw": row["stdout"]}

    def require_focus(self, expected, label):
        value = self.focus(label)
        if value["window"] != expected:
            raise HarnessError(f"Unexpected focused window at {label}")
        return value

    def tap(self, center, label):
        self.adb.shell("input", "tap", *center, label=label)
        time.sleep(self.args.action_wait_ms / 1000)

    def back(self, label):
        self.adb.shell("input", "keyevent", "4", label=label)
        time.sleep(self.args.action_wait_ms / 1000)

    def package_metadata(self, package):
        text, row, _ = self.adb.shell("dumpsys", "package", package, label="package-" + package)
        name = re.search(r"(?m)^\s*versionName=(\S+)", text)
        code = re.search(r"(?m)^\s*versionCode=(\d+)", text)
        paths, _, _ = self.adb.shell("pm", "path", package, label="paths-" + package)
        hashes = {}
        for line in paths.splitlines():
            if line.startswith("package:"):
                path = line[8:]
                hash_text, _, _ = self.adb.shell("sha256sum", path, label="apk-hash-" + package)
                digest = re.match(r"([a-fA-F0-9]{64})\s", hash_text)
                if digest is None:
                    raise HarnessError("Installed APK hash is not available")
                hashes[path] = digest.group(1).lower()
        if not name or not code or not hashes:
            raise HarnessError(f"Installed package metadata is incomplete: {package}")
        return {"package": package, "versionName": name.group(1),
                "versionCode": int(code.group(1)), "apkSha256": hashes, "raw": row["stdout"]}

    def pid(self, package, label):
        value, row, _ = self.adb.shell("pidof", package, label=label, allowed=(0, 1))
        return {"pids": value.strip().split(), "raw": row["stdout"]}

    def private_file(self, path, label):
        output, row, code = self.adb.shell("run-as", PACKAGE, "cat", path,
                                          label=label, allowed=(0, 1))
        if code == 0:
            return {"path": path, "present": True,
                    "sha256": hashlib.sha256((self.output / row["stdout"]).read_bytes()).hexdigest(),
                    "raw": row["stdout"]}, output
        _, _, existence = self.adb.shell("run-as", PACKAGE, "test", "-e", path,
                                         label=label + "-exists", allowed=(0, 1))
        if existence == 0:
            raise HarnessError(f"Private file exists but cannot be read: {path}")
        return {"path": path, "present": False, "sha256": None, "raw": row["stdout"]}, None

    def config_snapshot(self, label):
        # Only committed settings are compared. Activation, editor selection and
        # preview cache are deliberately not treated as configuration mutations.
        paths = [f"/data/user_de/{self.user_id}/{PACKAGE}/files/fusion_config_v4.json",
                 f"/data/user_de/{self.user_id}/{PACKAGE}/shared_prefs/fusion_statusbar.xml",
                 f"/data/user/{self.user_id}/{PACKAGE}/shared_prefs/fusion_statusbar.xml"]
        return [self.private_file(path, label + f"-{index}")[0]
                for index, path in enumerate(paths)]

    def receipt(self, label):
        path = f"/data/user_de/{self.user_id}/{PACKAGE}/shared_prefs/fusion_activation.xml"
        file_info, text = self.private_file(path, label)
        values = parse_preferences(text) if text is not None else {}
        pid = self.pid("com.android.systemui", label + "-systemui-pid")
        session = str(values.get("runtime_session", ""))
        session_parts = session.split(":", 2)
        stored_pid = session_parts[1] if len(session_parts) == 3 else None
        app = self.metadata["app"]
        estimated_boot_ms = (float(self.metadata["deviceEpochSeconds"])
                             - float(self.metadata["deviceUptimeSeconds"])) * 1000
        boot_delta_ms = abs(float(values.get("boot_start_ms", 0)) - estimated_boot_ms)
        return {"file": file_info, "values": values, "systemUi": pid,
                "versionMatchesInstalled": values.get("module_version") == app["versionName"]
                    and values.get("module_code") == app["versionCode"],
                "receiptSystemUiPid": stored_pid,
                "pidMatchesCurrentSystemUi": stored_pid in pid["pids"] if stored_pid else False,
                "bootStartDeltaMs": round(boot_delta_ms, 1),
                "sameBootWithinTolerance": values.get("boot_start_ms", 0) > 0 and boot_delta_ms < 120000}

    def resource_checkpoint(self, cycle, label):
        captured = utc_now()
        memory, mem_row, _ = self.adb.shell("dumpsys", "meminfo", PACKAGE, label=label + "-meminfo")
        _, cpu_row, _ = self.adb.shell("dumpsys", "cpuinfo", label=label + "-cpuinfo")
        pid = self.pid(PACKAGE, label + "-app-pid")
        pss = re.search(r"TOTAL PSS:\s*(\d+)", memory)
        if pss is None:
            pss = re.search(r"(?m)^\s*TOTAL\s+(\d+)\s", memory)
        row = {"completedCycle": cycle, "capturedUtc": captured,
               "pssKb": int(pss.group(1)) if pss else None,
               "objects": parse_memory_objects(memory),
               "app": pid, "meminfo": mem_row["stdout"], "cpuinfo": cpu_row["stdout"]}
        self.checkpoints.append(row)
        return row

    def renderer_stats(self, label):
        text, row, _ = self.adb.shell("dumpsys", "gfxinfo", PACKAGE, label=label + "-gfxinfo")
        _, frame_row, _ = self.adb.shell("dumpsys", "gfxinfo", PACKAGE, "framestats", label=label + "-framestats")
        return {"gfxinfo": row["stdout"], "framestats": frame_row["stdout"], **parse_gfxinfo(text)}

    def start(self, label):
        text, row, _ = self.adb.shell("am", "start", "-W", "-n", ACTIVITY, label=label)
        if re.search(r"(?m)^Status:\s*ok\s*$", text) is None or "Error:" in text:
            raise HarnessError("am start -W did not report Status: ok")
        fields = {}
        for key in ("Status", "LaunchState", "Activity", "ThisTime", "TotalTime", "WaitTime", "Complete"):
            match = re.search(r"(?m)^" + key + r":\s*(.*)$", text)
            if match:
                fields[key] = match.group(1).strip()
        time.sleep(self.args.initial_wait_ms / 1000)
        return {"raw": row["stdout"], "fields": fields,
                "app": self.pid(PACKAGE, label + "-pid")}

    def prepare(self):
        self.output.mkdir(parents=True, exist_ok=True)
        if list(self.output.glob(self.prefix + ".*")):
            raise HarnessError("Refusing to overwrite this output prefix; choose a unique --output-name")
        self.raw_dir.mkdir()
        self.created_output = True
        self.adb = Adb(self.args.adb, self.args.serial, self.raw_dir, self.filename("commands.jsonl"))
        state, _, _ = self.adb.run("get-state", label="get-state")
        if state.strip() != "device":
            raise HarnessError("Selected serial is not online as an authorized device")
        boot, _, _ = self.adb.shell("getprop", "sys.boot_completed", label="boot-completed")
        if boot.strip() != "1":
            raise HarnessError("Selected Android device has not completed boot")
        user, _, _ = self.adb.shell("am", "get-current-user", label="current-user")
        if re.fullmatch(r"\d+", user.strip()) is None:
            raise HarnessError("Cannot determine Android user")
        self.user_id = int(user.strip())
        # Verify run-as once; do not report unreadable data as missing settings.
        identity, _, _ = self.adb.shell("run-as", PACKAGE, "id", label="run-as-identity")
        if "uid=" not in identity:
            raise HarnessError("Debug run-as is unavailable; config invariance cannot be verified")
        for path in [self.remote_xml, *[self.remote_trace(i) for i in range(1, self.args.iterations + 1)]]:
            _, _, code = self.adb.shell("test", "-e", path, label="remote-output-exists", allowed=(0, 1))
            if code == 0:
                raise HarnessError(f"Refusing to overwrite remote output: {path}")
        getprop, _, _ = self.adb.shell("getprop", label="getprop")
        display, _, _ = self.adb.shell("dumpsys", "display", label="display")
        power, _, _ = self.adb.shell("dumpsys", "power", label="power")
        self.save_text("getprop.txt", getprop)
        self.save_text("display.txt", display)
        self.save_text("power.txt", power)
        if "mWakefulness=Awake" not in power:
            raise HarnessError("Device must remain awake and unlocked; harness does not change sleep settings")
        sdk, _, _ = self.adb.shell("getprop", "ro.build.version.sdk", label="sdk")
        uptime, uptime_row, _ = self.adb.shell("cat", "/proc/uptime", label="uptime")
        device_time, _, _ = self.adb.shell("date", "+%s", label="device-time")
        self.metadata = {"capturedUtc": utc_now(), "serial": self.args.serial,
                         "androidUser": self.user_id, "sdk": sdk.strip(),
                         "app": self.package_metadata(PACKAGE),
                         "systemUi": self.package_metadata("com.android.systemui"),
                         "deviceUptimeSeconds": uptime.split()[0], "deviceEpochSeconds": device_time.strip(),
                         "uptimeRaw": uptime_row["stdout"], "physicalDeviceAllowed": self.args.allow_physical_device,
                         "deviceSettingsChanged": False, "traceBufferKb": self.args.trace_buffer_kb,
                         "traceBufferInterpretation": "atrace -b is a per-CPU KiB buffer; short per-cycle captures",
                         "initialWaitMs": self.args.initial_wait_ms, "actionWaitMs": self.args.action_wait_ms}
        refresh = {}
        for key in ("peak_refresh_rate", "min_refresh_rate", "user_refresh_rate", "refresh_rate_mode"):
            value, _, _ = self.adb.shell("settings", "get", "system", key, label="refresh-" + key)
            refresh[key] = value.strip()
        self.metadata["refreshSettings"] = refresh
        self.metadata["configBefore"] = self.config_snapshot("config-before")
        self.metadata["runtimeReceiptBefore"] = self.receipt("runtime-receipt-before")
        self.write_json("metadata.json", self.metadata)
        self.metadata["initialStartup"] = self.start("initial-start")
        self.metadata["editorProcessPids"] = self.metadata["initialStartup"]["app"]["pids"]
        self.focus("initial-focused-window")
        if self.args.scenario == "editor-choice":
            self.learn_editor()
            self.adb.shell("dumpsys", "gfxinfo", PACKAGE, "reset", label="reset-gfxinfo")
        self.resource_checkpoint(0, "checkpoint-before")
        self.write_json("metadata.json", self.metadata)

    def learn_editor(self):
        root = self.ui("learn-overview")
        self.tap(node_center(find_node(root, "控制中心", clickable=True)), "learn-control-center-nav")
        root = self.ui("learn-editor-page")
        nav = find_node(root, "控制中心", clickable=True)
        if nav.get("selected") != "true":
            raise HarnessError("Control-center navigation is not selected")
        self.tile_center = node_center(find_node(root, "Wi-Fi、示意预览", clickable=True))
        self.activity_window = self.focus("learn-activity-focus")["window"]
        self.tap(self.tile_center, "learn-wifi-tile")
        self.sheet_window = self.focus("learn-sheet-focus")["window"]
        if self.sheet_window == self.activity_window:
            raise HarnessError("Wi-Fi item tap did not open an editor sheet")
        root = self.ui("learn-editor-collapsed")
        collapsed = verify_editor(root, False)
        self.collapsed_center = collapsed["currentRowCenter"]
        self.tap(self.collapsed_center, "learn-expand-selector")
        self.require_focus(self.sheet_window, "learn-expanded-focus")
        root = self.ui("learn-editor-expanded")
        expanded = verify_editor(root, True)
        self.expanded_center = expanded["currentRowCenter"]
        self.tap(self.expanded_center, "learn-collapse-selector")
        self.require_focus(self.sheet_window, "learn-collapsed-focus")
        verify_editor(self.ui("learn-editor-recollapsed"), False)
        self.back("learn-close-sheet")
        self.require_focus(self.activity_window, "learn-returned-focus")
        self.metadata["learnedEditor"] = {"tileCenter": self.tile_center,
                                         "collapsedCurrentRowCenter": self.collapsed_center,
                                         "expandedCurrentRowCenter": self.expanded_center,
                                         "collapsedVerification": collapsed,
                                         "expandedVerification": expanded}
        self.write_json("metadata.json", self.metadata)

    def cold_cycle(self, index):
        label = f"cycle-{index:02d}"
        before = self.pid(PACKAGE, label + "-pid-before")
        self.adb.shell("am", "force-stop", PACKAGE, label=label + "-force-stop")
        stopped = self.pid(PACKAGE, label + "-pid-stopped")
        if stopped["pids"]:
            raise HarnessError("App process survived force-stop; cold launch is not valid")
        startup = self.start(label + "-startup")
        root = self.ui(label + "-settled-overview")
        verification = verify_overview(root)
        focus = self.focus(label + "-settled-focus")
        stats = self.renderer_stats(label)
        self.cycles.append({"cycle": index, "pidBeforeForceStop": before,
                            "pidAfterForceStop": stopped, "startup": startup,
                            "settledUi": verification, "focusedWindow": focus,
                            "renderer": stats, "completedUtc": utc_now()})

    def editor_cycle(self, index):
        label = f"cycle-{index:02d}"
        focus_rows = [self.require_focus(self.activity_window, label + "-before-open-focus")]
        self.tap(self.tile_center, label + "-open-editor")
        sheet = self.focus(label + "-after-open-focus")
        if sheet["window"] == self.activity_window:
            raise HarnessError("Item tap did not open a sheet")
        focus_rows.append(sheet)
        verify = index in (1, self.args.iterations)
        ui = {}
        if verify:
            ui["collapsed"] = verify_editor(self.ui(label + "-collapsed"), False)
            if ui["collapsed"]["currentRowCenter"] != self.collapsed_center:
                raise HarnessError("Learned collapsed selector coordinate changed")
        focus_rows.append(self.require_focus(sheet["window"], label + "-before-expand-focus"))
        self.tap(self.collapsed_center, label + "-expand-selector")
        focus_rows.append(self.require_focus(sheet["window"], label + "-after-expand-focus"))
        if verify:
            ui["expanded"] = verify_editor(self.ui(label + "-expanded"), True)
            if ui["expanded"]["currentRowCenter"] != self.expanded_center:
                raise HarnessError("Learned expanded selector coordinate changed")
        focus_rows.append(self.require_focus(sheet["window"], label + "-before-collapse-focus"))
        self.tap(self.expanded_center, label + "-collapse-selector")
        focus_rows.append(self.require_focus(sheet["window"], label + "-after-collapse-focus"))
        if verify:
            ui["recollapsed"] = verify_editor(self.ui(label + "-recollapsed"), False)
        self.back(label + "-close-editor")
        focus_rows.append(self.require_focus(self.activity_window, label + "-after-close-focus"))
        self.cycles.append({"cycle": index, "focusChecks": focus_rows,
                            "uiVerification": ui, "uiSnapshotValidated": verify,
                            "completedUtc": utc_now()})

    def collect_final(self):
        self.metadata["configAfter"] = self.config_snapshot("config-after")
        before = [(v["path"], v["present"], v["sha256"]) for v in self.metadata["configBefore"]]
        after = [(v["path"], v["present"], v["sha256"]) for v in self.metadata["configAfter"]]
        self.result["committedConfigUnchanged"] = before == after
        self.metadata["runtimeReceiptAfter"] = self.receipt("runtime-receipt-after")
        self.result["finalRenderer"] = self.renderer_stats("final")
        for command, suffix in ((["dumpsys", "gfxinfo", PACKAGE], "gfxinfo.txt"),
                                (["dumpsys", "gfxinfo", PACKAGE, "framestats"], "framestats.txt"),
                                (["dumpsys", "cpuinfo"], "cpuinfo.txt"),
                                (["dumpsys", "meminfo", PACKAGE], "meminfo.txt")):
            text, _, _ = self.adb.shell(*command, label="final-" + suffix)
            self.save_text(suffix, text)
        self.result["resourceSampling"] = "before/intermediate/after snapshots; no slow-frame attribution or leak verdict"
        self.result["coldRendererInterpretation"] = "cold-start restarts renderer; use per-cycle statistics rather than final-only aggregate"
        if not self.result["committedConfigUnchanged"]:
            raise HarnessError("Committed config hash changed during a no-edit flow")

    def stop_segment(self, index):
        remote = self.remote_trace(index)
        local = self.filename(f"cycle-{index:02d}.trace")
        self.adb.shell("atrace", "--async_stop", "-o", remote, label=f"cycle-{index:02d}-atrace-stop")
        self.adb.run("pull", remote, str(local), label=f"cycle-{index:02d}-atrace-pull")
        content = local.read_text(encoding="utf-8", errors="replace")
        reasons = []
        if not content.strip():
            reasons.append("empty_trace")
        if not re.search(r"(?m)^\s*[^#\n].+\[\d+\].*\d+\.\d+:\s", content):
            reasons.append("no_ftrace_events_retained")
        for retained, written in re.findall(r"entries-in-buffer/entries-written:\s*(\d+)\s*/\s*(\d+)", content):
            if int(written) > int(retained):
                reasons.append("buffer_events_overwritten")
                break
        if re.search(r"LOST\s+\d+\s+EVENTS|lost_events=([1-9]\d*)", content):
            reasons.append("lost_events_reported")
        gc_labels = re.findall(r"tracing_mark_write:\s*B\|(\d+)\|([^\r\n]*\bGC\b[^\r\n]*)", content)
        app_pids = set()
        if self.cycles and self.cycles[-1]["cycle"] == index and "startup" in self.cycles[-1]:
            app_pids.update(self.cycles[-1]["startup"]["app"]["pids"])
        elif self.args.scenario == "editor-choice":
            app_pids.update(self.metadata.get("editorProcessPids", []))
        app_gc = [label for pid, label in gc_labels if pid in app_pids]
        row = {"cycle": index, "file": local.name, "bytes": local.stat().st_size,
               "truncationSuspected": bool(reasons), "truncationReasons": reasons,
               "observedAppGcLabelCount": len(app_gc), "observedAppGcLabels": sorted(set(app_gc)),
               "gcInterpretation": "app PID trace labels only; zero is not proof that no GC occurred"}
        self.traces.append(row)
        if reasons:
            raise HarnessError(f"Cycle {index} trace completeness failed: {', '.join(reasons)}")

    def run(self):
        error = None
        try:
            self.prepare()
            self.result["measurementStartedUtc"] = utc_now()
            self.result["status"] = "running"
            checkpoints = {max(1, self.args.iterations // 3),
                           max(1, self.args.iterations * 2 // 3), self.args.iterations}
            for index in range(1, self.args.iterations + 1):
                # Short per-cycle traces avoid allocating huge per-CPU buffers
                # or retaining a several-minute capture with overwritten data.
                # Always stop even if async_start partially fails.
                cycle_error = None
                try:
                    self.adb.shell("atrace", "--async_start", "-b", str(self.args.trace_buffer_kb),
                                   "-a", PACKAGE, "gfx", "view", "wm", "am", "dalvik", "sched",
                                   label=f"cycle-{index:02d}-atrace-start")
                    if self.args.scenario == "cold-start":
                        self.cold_cycle(index)
                    else:
                        self.editor_cycle(index)
                except Exception as caught:
                    cycle_error = caught
                    self.result.setdefault("cycleErrors", []).append({"cycle": index, "flowError": str(caught)})
                finally:
                    try:
                        self.stop_segment(index)
                    except Exception as caught:
                        self.result.setdefault("cycleErrors", []).append({"cycle": index, "traceError": str(caught)})
                        if cycle_error is None:
                            cycle_error = caught
                if cycle_error is not None:
                    raise cycle_error
                self.result["completedCycles"] = len(self.cycles)
                if index in checkpoints:
                    self.resource_checkpoint(index, f"checkpoint-{index:02d}")
                self.write_json("json", self.result)
                print(f"{self.args.scenario}: completed {index}/{self.args.iterations}", flush=True)
        except Exception as caught:
            error = caught
            self.result["error"] = str(caught)
        finally:
            self.result["measurementEndedUtc"] = utc_now()
            if self.metadata:
                try:
                    self.collect_final()
                except Exception as caught:
                    self.result["finalEvidenceError"] = str(caught)
                    if error is None:
                        error = caught
            self.result["status"] = "complete" if error is None else "failed"
            self.result["completedCycles"] = len(self.cycles)
            self.result["metadata"] = self.prefix + ".metadata.json"
            if self.created_output:
                self.write_json("metadata.json", self.metadata)
                self.write_json("json", self.result)
        if error is not None:
            raise HarnessError(str(error)) from error
        return self.result


def arguments(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--scenario", required=True, choices=("cold-start", "editor-choice"))
    parser.add_argument("--iterations", type=int, default=30)
    parser.add_argument("--output-name", required=True)
    parser.add_argument("--allow-physical-device", action="store_true")
    default_adb = Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk" / "platform-tools" / "adb.exe"
    parser.add_argument("--adb", default=str(default_adb) if default_adb.is_file() else "adb")
    parser.add_argument("--initial-wait-ms", type=int, default=2500)
    parser.add_argument("--action-wait-ms", type=int, default=500)
    parser.add_argument("--trace-buffer-kb", type=int, default=32768)
    args = parser.parse_args(argv)
    if re.fullmatch(r"[A-Za-z0-9._:-]+", args.serial) is None:
        parser.error("Serial contains unsupported characters")
    if not args.allow_physical_device and re.fullmatch(r"emulator-\d+", args.serial) is None:
        parser.error("Physical devices require explicit --allow-physical-device")
    if re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}", args.output_name) is None:
        parser.error("Output name must be 1..64 ASCII letters/numbers/dot/underscore/dash")
    if not 3 <= args.iterations <= 50:
        parser.error("Iterations must be between 3 and 50")
    if not 250 <= args.action_wait_ms <= 5000 or not 1000 <= args.initial_wait_ms <= 10000:
        parser.error("Action wait must be 250..5000ms; initial wait 1000..10000ms")
    if not 32768 <= args.trace_buffer_kb <= 524288:
        parser.error("Trace buffer must be 32768..524288 KiB")
    return args


def main(argv=None):
    try:
        args = arguments(argv)
        result = FlowHarness(args).run()
        print(json.dumps({"status": result["status"], "completedCycles": result["completedCycles"],
                          "summary": str(ROOT / "build" / "perf" / (args.output_name + ".json"))},
                         ensure_ascii=False))
        return 0
    except HarnessError as error:
        print("Measurement failed: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
