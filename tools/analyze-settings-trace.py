#!/usr/bin/env python3
"""Summarize an Android atrace settings interaction trace.

The duration values reported here are timestamp deltas from atrace.  They are
wall-clock durations, not CPU time.  The parser intentionally keeps B/E stacks
per trace TID and reports unmatched/unfinished events instead of silently
discarding them.

Without validation options the command only reports metrics.  Supplying
--expected-openings or --max-opening-frame-ms enables completeness validation
and exits nonzero on failure, after saving the JSON output.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import statistics
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable


LINE_RE = re.compile(
    r"^\s*(?P<comm>.+)-(?P<tid>\d+)\s+\((?P<tgid>[^)]*)\)"
    r"\s+\[\d+\]\s+\S+\s+(?P<timestamp>\d+\.\d+):\s+"
    r"tracing_mark_write:\s+(?P<kind>[BESF])\|(?P<marker>[^|\r\n]*)"
    r"(?:\|(?P<label>[^\r\n]*))?"
)
HEADER_ENTRIES_RE = re.compile(r"# entries-in-buffer/entries-written:\s*(\d+)\s*/\s*(\d+)")


def percentile(values: list[float], fraction: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, math.ceil(fraction * len(ordered)) - 1))
    return ordered[index]


def stats(values: Iterable[float]) -> dict[str, Any]:
    values = list(values)
    if not values:
        return {"count": 0}
    return {
        "count": len(values),
        "meanMs": round(statistics.fmean(values), 3),
        "p50Ms": round(percentile(values, 0.50), 3),
        "p95Ms": round(percentile(values, 0.95), 3),
        "maxMs": round(max(values), 3),
        "minMs": round(min(values), 3),
    }


def is_target_event(match: re.Match[str], target_pid: str, target_tgid: str) -> tuple[bool, bool]:
    """Return (matches source TID and marker pid, known TGID matches).

    Standard atrace output often prints ``(-----)`` in the TGID column.  Such a
    line has no numeric TGID to validate; it remains usable when both the source
    TID and trace marker PID identify the requested process.
    """

    source_tid = match.group("tid")
    marker_pid = match.group("marker").strip()
    source_tgid = match.group("tgid").strip()
    tid_and_marker = source_tid == target_pid and marker_pid == target_pid
    tgid_known = source_tgid.isdigit()
    tgid_matches = tgid_known and source_tgid == target_tgid
    return tid_and_marker, (not tgid_known or tgid_matches)


def parse_trace(path: Path, target_pid: str, target_tgid: str) -> dict[str, Any]:
    stacks: dict[str, list[dict[str, Any]]] = defaultdict(list)
    slices: list[dict[str, Any]] = []
    counters = Counter()
    tgid_values = Counter()
    target_tgid_unknown = 0
    first_seen: float | None = None
    last_seen: float | None = None
    header = {
        "tracerLine": False,
        "entriesLine": False,
        "columnsLine": False,
    }
    header_entries: tuple[int, int] | None = None

    with path.open("r", encoding="utf-8", errors="replace") as stream:
        for raw_line in stream:
            if raw_line.startswith("# tracer:"):
                header["tracerLine"] = True
            entries_match = HEADER_ENTRIES_RE.match(raw_line)
            if entries_match:
                header["entriesLine"] = True
                header_entries = (int(entries_match[1]), int(entries_match[2]))
            if "TIMESTAMP  FUNCTION" in raw_line:
                header["columnsLine"] = True
            match = LINE_RE.match(raw_line.rstrip("\n"))
            if not match:
                continue
            counters["traceEvents"] += 1
            timestamp = float(match.group("timestamp"))
            first_seen = timestamp if first_seen is None else min(first_seen, timestamp)
            last_seen = timestamp if last_seen is None else max(last_seen, timestamp)
            marker = match.group("marker").strip()
            source_tid = match.group("tid")
            source_tgid = match.group("tgid").strip()
            if source_tgid.isdigit():
                tgid_values[source_tgid] += 1

            target_tid_marker, tgid_ok = is_target_event(match, target_pid, target_tgid)
            if source_tid == target_pid:
                counters["sourceTidEvents"] += 1
                if not source_tgid.isdigit():
                    target_tgid_unknown += 1
                if marker != target_pid:
                    counters["sourceTidMarkerMismatch"] += 1
            if marker == target_pid:
                counters["markerPidEvents"] += 1
                if source_tid != target_pid:
                    counters["markerPidSourceTidMismatch"] += 1
                    if match.group("kind") == "E":
                        counters["crossThreadEndEvents"] += 1
            if not target_tid_marker:
                continue
            if not tgid_ok:
                counters["targetNumericTgidMismatch"] += 1
                continue
            counters["targetEvents"] += 1
            kind = match.group("kind")
            label = match.group("label") or ""
            # The trace marker PID is part of the key as well as the source TID;
            # this prevents a cross-process E marker from closing this stack.
            key = f"{source_tid}:{marker}"
            if kind == "B":
                stacks[key].append({"label": label, "start": timestamp, "tid": source_tid})
                counters["beginEvents"] += 1
                continue
            if kind != "E":
                counters[f"{kind}Events"] += 1
                continue
            if not stacks[key]:
                counters["unmatchedEndEvents"] += 1
                continue
            begin = stacks[key].pop()
            duration = max(0.0, (timestamp - begin["start"]) * 1000.0)
            slices.append(
                {
                    "tid": int(source_tid),
                    "label": begin["label"],
                    "startMs": round(begin["start"] * 1000.0, 3),
                    "endMs": round(timestamp * 1000.0, 3),
                    "durationMs": round(duration, 3),
                }
            )
            counters["matchedSlices"] += 1

    unfinished = sum(len(stack) for stack in stacks.values())
    counters["unfinishedBeginEvents"] = unfinished
    counters["stackKeysWithUnfinishedBegins"] = sum(bool(stack) for stack in stacks.values())
    for name in ["unmatchedEndEvents", "beginEvents", "matchedSlices", "sourceTidMarkerMismatch", "targetNumericTgidMismatch", "targetEvents", "crossThreadEndEvents"]:
        counters[name] += 0

    slices.sort(key=lambda item: (item["startMs"], item["endMs"]))
    labels = Counter(item["label"] for item in slices)

    def matching(prefix: str) -> list[dict[str, Any]]:
        return [item for item in slices if item["label"].startswith(prefix)]

    frame_slices = [
        item for item in matching("Choreographer#doFrame")
        if "resynced to" not in item["label"]
    ]
    opening_slices = matching("VRI[MainActivity]-relayoutWindow#first=true")
    # Add a small frame context around each first-window event.  The enclosing
    # frame is preferred; adjacent frames make resync effects visible when the
    # event is split across trace nesting.
    for opening in opening_slices:
        containing = [
            frame
            for frame in frame_slices
            if frame["startMs"] <= opening["startMs"] <= frame["endMs"]
            and opening["endMs"] <= frame["endMs"]
        ]
        containing.sort(key=lambda frame: frame["durationMs"])
        opening["enclosingDoFrame"] = containing[0] if containing else None
        nearest = sorted(
            frame_slices,
            key=lambda frame: abs(frame["startMs"] - opening["startMs"]),
        )[:3]
        opening["nearbyDoFrames"] = nearest
        frame = opening["enclosingDoFrame"]
        if frame is not None:
            inside = [
                item for item in slices
                if frame["startMs"] <= item["startMs"]
                and item["endMs"] <= frame["endMs"]
            ]
            opening["enclosingFrameStages"] = {
                label: {
                    "count": sum(item["label"] == label for item in inside),
                    "sumDurationMs": round(sum(
                        item["durationMs"] for item in inside
                        if item["label"] == label
                    ), 3),
                }
                for label in ["measure", "layout", "relayoutWindow", "Record View#draw()", "syncAndDrawFrame"]
            }

    selected_prefixes = [
        "Choreographer#doFrame",
        "traversal",
        "measure",
        "layout",
        "draw-VRI[MainActivity]",
        "Record View#draw()",
        "syncAndDrawFrame",
        "postAndWait",
        "relayoutWindow",
        "VRI[MainActivity]-relayoutWindow#first=true",
        "computePalette",
    ]
    selected = {
        prefix: stats(
            item["durationMs"]
            for item in slices
            if item["label"].startswith(prefix)
        )
        for prefix in selected_prefixes
    }
    selected["Choreographer#doFrame"] = stats(item["durationMs"] for item in frame_slices)
    selected["Choreographer#doFrame - resynced"] = stats(
        item["durationMs"] for item in matching("Choreographer#doFrame - resynced")
    )
    opening_frame_starts = {
        event["enclosingDoFrame"]["startMs"] for event in opening_slices
        if event["enclosingDoFrame"] is not None
    }
    gc_labels = {
        label: count for label, count in labels.items()
        if re.search(r"(?:\bGC\b|garbage.?collect)", label, re.IGNORECASE)
    }

    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)

    return {
        "format": "xtjm-settings-trace-analysis-v1",
        "trace": str(path),
        "traceSha256": digest.hexdigest(),
        "durationUnit": "ms",
        "durationKind": "wall-clock timestamp delta; not CPU time",
        "target": {
            "pid": int(target_pid),
            "tgid": int(target_tgid),
            "matching": "source trace TID and B/E marker PID must both match",
            "numericTgidObserved": dict(tgid_values),
            "unknownTgidLinesForTargetTid": target_tgid_unknown,
        },
        "header": {
            **header,
            "complete": all(header.values()),
            "entriesInBuffer": header_entries[0] if header_entries is not None else None,
            "entriesWritten": header_entries[1] if header_entries is not None else None,
            "overwrittenEntries": max(0, header_entries[1] - header_entries[0]) if header_entries is not None else None,
        },
        "traceTimeSeconds": None if first_seen is None else round(last_seen - first_seen, 6),
        "parser": dict(counters),
        "eventCounts": dict(labels),
        "selectedEventStats": selected,
        "openingDoFrameStats": stats(
            item["durationMs"] for item in frame_slices
            if item["startMs"] in opening_frame_starts
        ),
        "otherDoFrameStats": stats(
            item["durationMs"] for item in frame_slices
            if item["startMs"] not in opening_frame_starts
        ),
        "observedGcLabels": gc_labels,
        "firstWindowEvents": opening_slices,
        "notes": [
            "GC label counts only cover the selected TID/TGID stream; zero observed labels does not prove that no GC occurred.",
            "A duration includes waits and scheduler/renderer/service time visible in the trace; it is not on-CPU duration.",
            "Opening doFrame duration is a main-thread trace span, not a measured display-present frame duration or a gfxinfo jank rate.",
            "TGID is unknown when atrace prints (-----); those events remain included only when source TID and marker PID match.",
        ],
    }


def validate_analysis(
    result: dict[str, Any], expected_openings: int | None, max_opening_frame_ms: float | None,
) -> dict[str, Any]:
    """Apply optional completeness/diagnostic thresholds without hiding metrics."""

    enabled = expected_openings is not None or max_opening_frame_ms is not None
    failures: list[str] = []
    opening_events = result["firstWindowEvents"]
    frames = [item["enclosingDoFrame"] for item in opening_events if item["enclosingDoFrame"] is not None]
    exceeded = []
    if enabled:
        if not result["header"]["complete"]:
            failures.append("trace_header_missing")
        if result["header"]["overwrittenEntries"]:
            failures.append("trace_header_reports_overwritten_entries")
        if not result["parser"]["targetEvents"]:
            failures.append("no_target_events")
        if not opening_events:
            failures.append("no_first_window_events")
        if result["parser"]["unmatchedEndEvents"]:
            failures.append("unmatched_end_events")
        if result["parser"]["unfinishedBeginEvents"]:
            failures.append("unfinished_begin_events")
        if expected_openings is not None and len(opening_events) != expected_openings:
            failures.append("unexpected_opening_count")
        if len(frames) != len(opening_events):
            failures.append("opening_without_enclosing_do_frame")
        if max_opening_frame_ms is not None:
            exceeded = [
                {"startMs": item["startMs"], "durationMs": item["durationMs"]}
                for item in frames if item["durationMs"] > max_opening_frame_ms
            ]
            if exceeded:
                failures.append("opening_do_frame_wall_duration_exceeded")
    return {
        "status": "failed" if failures else "passed" if enabled else "not_requested",
        "exitCode": 1 if failures else 0,
        "expectedOpenings": expected_openings,
        "observedOpenings": len(opening_events),
        "maxOpeningFrameWallMs": max_opening_frame_ms,
        "failures": failures,
        "exceededOpeningDoFrames": exceeded,
        "scope": "trace completeness and opening doFrame wall-clock diagnostic; not CPU or display-present frame validation",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("trace", type=Path, help="atrace text file")
    parser.add_argument("--pid", type=int, required=True, help="target main-thread trace TID/marker PID")
    parser.add_argument("--tgid", type=int, help="target TGID (defaults to --pid)")
    parser.add_argument("--output", type=Path, help="write JSON to this path")
    parser.add_argument("--expected-openings", type=int, help="require this many first-window events and a complete trace")
    parser.add_argument("--max-opening-frame-ms", type=float, help="fail if an enclosing opening doFrame wall duration exceeds this diagnostic limit")
    args = parser.parse_args()
    if args.expected_openings is not None and args.expected_openings < 1:
        parser.error("--expected-openings must be at least 1")
    if args.max_opening_frame_ms is not None and (
        not math.isfinite(args.max_opening_frame_ms) or args.max_opening_frame_ms <= 0
    ):
        parser.error("--max-opening-frame-ms must be a positive finite number")
    result = parse_trace(args.trace, str(args.pid), str(args.tgid if args.tgid is not None else args.pid))
    result["validation"] = validate_analysis(result, args.expected_openings, args.max_opening_frame_ms)
    encoded = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded, encoding="utf-8")
    else:
        print(encoded, end="")
    return result["validation"]["exitCode"]


if __name__ == "__main__":
    raise SystemExit(main())
