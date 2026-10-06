"""Validate both Android build variants before local or CI archiving."""

import argparse
import hashlib
import json
import re
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile


class ValidationError(ValueError):
    pass


def source_identity(project):
    source = (project / "app/build.gradle").read_text(encoding="utf-8")
    identity = {}
    for key in ("namespace", "applicationId", "versionName", "versionCode", "compileSdk", "targetSdk"):
        pattern = rf'\b{key}\s+(?:"([^"]+)"|(\d+))'
        matches = re.findall(pattern, source)
        if len(matches) != 1:
            raise ValidationError(f"Expected one literal {key} in app/build.gradle")
        text, number = matches[0]
        identity[key] = text or int(number)
    return identity


def test_totals(directory):
    files = sorted(directory.glob("TEST-*.xml"))
    if not files:
        raise ValidationError(f"No test reports: {directory}")
    totals = dict(total=0, failures=0, errors=0, skipped=0)
    for file in files:
        suite = ET.parse(file).getroot()
        if suite.tag != "testsuite":
            raise ValidationError(f"Unexpected test report format: {file}")
        for target, attribute in (("total", "tests"), ("failures", "failures"), ("errors", "errors"), ("skipped", "skipped")):
            value = int(suite.attrib[attribute])
            if value < 0:
                raise ValidationError(f"Negative test count: {file}")
            totals[target] += value
    if totals["total"] == 0 or any(totals[key] for key in ("failures", "errors", "skipped")):
        raise ValidationError(f"Unit-test gate failed: {totals}")
    return totals


def lint_totals(project, report, policy):
    issues = ET.parse(report).getroot().findall("issue")
    errors = sum(issue.get("severity") in ("Error", "Fatal") for issue in issues)
    if errors or any(issue.get("id") == "ObsoleteLintCustomCheck" for issue in issues):
        raise ValidationError(f"Lint gate failed: {report}")
    counts = {}
    project_prefix = str(project.resolve()).replace("\\", "/").rstrip("/") + "/"
    for issue in issues:
        issue_id = issue.get("id")
        if issue_id not in policy["checkedIds"]:
            continue
        location = issue.find("location")
        file = "" if location is None else location.get("file", "").replace("\\", "/")
        if file.lower().startswith(project_prefix.lower()):
            file = file[len(project_prefix):]
        key = f"{issue_id}:{file}"
        counts[key] = counts.get(key, 0) + 1
        if counts[key] > policy["allowed"].get(key, 0):
            raise ValidationError(f"New high-risk lint finding requires review: {key}")
    return dict(errors=errors, warnings=sum(issue.get("severity") == "Warning" for issue in issues),
                customChecksCompatible=True, highRiskPolicy="passed")


def validate_variant(project, variant, identity, policy):
    output = project / "app/build/outputs/apk" / variant
    metadata = json.loads((output / "output-metadata.json").read_text(encoding="utf-8"))
    if metadata.get("applicationId") != identity["applicationId"]:
        raise ValidationError(f"APK metadata identity mismatch: {variant}")
    elements = metadata.get("elements", [])
    if len(elements) != 1:
        raise ValidationError(f"Expected one APK: {variant}")
    element = elements[0]
    for field in ("versionName", "versionCode"):
        if element.get(field) != identity[field]:
            raise ValidationError(f"APK metadata {field} mismatch: {variant}")
    apk = (output / element["outputFile"]).resolve()
    if apk.parent != output.resolve() or apk.suffix != ".apk" or not apk.is_file():
        raise ValidationError(f"Invalid APK output path: {variant}")
    build_config = (project / f"app/build/generated/source/buildConfig/{variant}" /
                    identity["namespace"].replace(".", "/") / "BuildConfig.java").read_text(encoding="utf-8")
    constants = {
        "APPLICATION_ID": f'"{identity["applicationId"]}"',
        "VERSION_NAME": f'"{identity["versionName"]}"',
        "VERSION_CODE": str(identity["versionCode"]),
        "DEBUG": "true" if variant == "debug" else "false",
    }
    for field, value in constants.items():
        pattern = (rf"\b{field}\s*=\s*(?:{re.escape(value)}|Boolean\.parseBoolean\(\"{value}\"\))\s*;"
                   if field == "DEBUG" else rf"\b{field}\s*=\s*{re.escape(value)}\s*;")
        if not re.search(pattern, build_config):
            raise ValidationError(f"Hook BuildConfig {field} mismatch: {variant}")
    with zipfile.ZipFile(apk) as archive:
        module = archive.read("META-INF/xposed/module.prop").decode("utf-8")
    properties = dict(line.split("=", 1) for line in module.splitlines()
                      if "=" in line and not line.lstrip().startswith("#"))
    for field, value in (("id", identity["applicationId"]), ("version", identity["versionName"]),
                         ("versionCode", str(identity["versionCode"]))):
        if properties.get(field) != value:
            raise ValidationError(f"Xposed {field} mismatch: {variant}")
    name = variant.capitalize()
    tests = test_totals(project / f"app/build/test-results/test{name}UnitTest")
    lint = lint_totals(project, project / f"app/build/reports/lint-results-{variant}.xml", policy)
    return dict(apk=str(apk), apkSha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                apkSize=apk.stat().st_size, tests=tests, lint=lint, debuggable=variant == "debug")


def validate_build(project, expected_package, expected_namespace):
    identity = source_identity(project)
    if identity["applicationId"] != expected_package or identity["namespace"] != expected_namespace:
        raise ValidationError("Source package or namespace differs from the selected release identity")
    policy = json.loads((project / "tools/lint-policy.json").read_text(encoding="utf-8"))
    return dict(identity=identity, variants={variant: validate_variant(project, variant, identity, policy)
                                             for variant in ("debug", "release")})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-root", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--package", default="io.github.yudigaga.fusionstatusbar")
    parser.add_argument("--namespace", default="com.xtjm.fusionstatusbar")
    parser.add_argument("--identity-only", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = source_identity(args.project_root) if args.identity_only else validate_build(
        args.project_root, args.package, args.namespace)
    content = json.dumps(result, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        args.output.write_text(content, encoding="utf-8")
    else:
        print(content, end="")


if __name__ == "__main__":
    main()
