"""Validate the R8 Release APK before local or CI archiving."""

import argparse
import hashlib
import json
import re
import struct
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile


class ValidationError(ValueError):
    pass


def dex_metadata(data):
    """Read only bounded DEX tables; a descriptor in string_data is not a class definition."""
    if len(data) < 112 or not re.fullmatch(rb"dex\n(?:035|037|038|039|040)\x00", data[:8]):
        raise ValidationError("Unsupported or truncated DEX header")

    def uint(offset):
        return struct.unpack_from("<I", data, offset)[0]

    if uint(32) != len(data) or uint(36) != 112 or uint(40) != 0x12345678:
        raise ValidationError("DEX size, header size or endian tag mismatch")

    def bounded(offset, count, width, alignment=1):
        if (offset < 112 or offset % alignment or offset > len(data)
                or count > (len(data) - offset) // width):
            raise ValidationError("DEX table or data offset is out of bounds")

    for size_field, offset_field, width in ((56, 60, 4), (64, 68, 4), (72, 76, 12),
                                           (80, 84, 8), (88, 92, 8), (96, 100, 32)):
        count, offset = uint(size_field), uint(offset_field)
        if count:
            bounded(offset, count, width, 4)
        elif offset:
            raise ValidationError("Empty DEX table has a nonzero offset")
    data_size, data_off = uint(104), uint(108)
    if data_size:
        bounded(data_off, data_size, 1)
    elif data_off:
        raise ValidationError("Empty DEX data has a nonzero offset")
    data_end = data_off + data_size
    map_off = uint(52)
    if map_off:
        bounded(map_off, 1, 4, 4)
        bounded(map_off + 4, uint(map_off), 12, 4)

    def dex_string(offset):
        if not data_size or offset < data_off or offset >= data_end:
            raise ValidationError("DEX string is outside the data section")
        size = 0
        for index in range(5):
            if offset >= data_end:
                raise ValidationError("Truncated DEX string length")
            byte = data[offset]
            offset += 1
            if index == 4 and byte > 15:
                raise ValidationError("Overflowing DEX string length")
            size |= (byte & 127) << (index * 7)
            if not byte & 128:
                break
        else:
            raise ValidationError("Unterminated DEX string length")
        end = data.find(b"\0", offset, data_end)
        if end < 0:
            raise ValidationError("Unterminated DEX string")
        try:
            value = data[offset:end].replace(b"\xc0\x80", b"\0").decode("utf-8", errors="surrogatepass")
        except UnicodeDecodeError as error:
            raise ValidationError("Malformed DEX modified UTF-8 string") from error
        if len(value.encode("utf-16-le", errors="surrogatepass")) // 2 != size:
            raise ValidationError("DEX string UTF-16 length mismatch")
        return value

    strings = [dex_string(uint(uint(60) + index * 4)) for index in range(uint(56))]
    types = []
    for index in range(uint(64)):
        string_index = uint(uint(68) + index * 4)
        if string_index >= len(strings):
            raise ValidationError("DEX type descriptor index is out of bounds")
        types.append(strings[string_index])
    classes = set()
    for index in range(uint(96)):
        type_index = uint(uint(100) + index * 32)
        if type_index >= len(types):
            raise ValidationError("DEX class type index is out of bounds")
        descriptor = types[type_index]
        if not descriptor.startswith("L") or not descriptor.endswith(";") or descriptor in classes:
            raise ValidationError("Invalid or duplicate DEX class definition")
        classes.add(descriptor)
    markers = []
    for value in strings:
        if not value.startswith("~~R8{"):
            continue
        try:
            marker = json.loads(value[4:])
        except json.JSONDecodeError as error:
            raise ValidationError("Malformed R8 DEX marker") from error
        if (not isinstance(marker, dict) or marker.get("backend") != "dex"
                or not isinstance(marker.get("version"), str) or not marker["version"].strip()
                or not isinstance(marker.get("pg-map-id"), str) or not marker["pg-map-id"].strip()):
            raise ValidationError("R8 DEX marker lacks its backend, version or mapping ID")
        markers.append(marker)
    return dict(classes=classes, markers=markers)


def java_initializers(text):
    entries = [line.strip() for line in text.splitlines()
               if line.strip() and not line.lstrip().startswith("#")]
    if (not entries or len(entries) != len(set(entries))
            or any(not re.fullmatch(r"[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)+", entry)
                   for entry in entries)):
        raise ValidationError("Invalid Xposed Java entry list")
    return entries


def r8_artifacts(apk, mapping_directory, expected_entries):
    expected_entries = java_initializers("\n".join(expected_entries))
    classes, markers = set(), []
    try:
        with zipfile.ZipFile(apk) as archive:
            entries = java_initializers(archive.read("META-INF/xposed/java_init.list").decode("utf-8"))
            if entries != expected_entries:
                raise ValidationError("Packaged Xposed Java entries differ from source")
            dex_files = [item for item in archive.infolist()
                         if re.fullmatch(r"classes(?:[2-9]|[1-9][0-9]+)?\.dex", item.filename)]
            if (not any(item.filename == "classes.dex" for item in dex_files)
                    or len(dex_files) != len({item.filename for item in dex_files})):
                raise ValidationError("APK has missing or duplicate primary DEX files")
            for item in dex_files:
                parsed = dex_metadata(archive.read(item))
                if classes.intersection(parsed["classes"]):
                    raise ValidationError("APK contains duplicate class definitions across DEX files")
                classes.update(parsed["classes"])
                markers.extend(parsed["markers"])
    except (KeyError, UnicodeDecodeError, zipfile.BadZipFile, OSError) as error:
        raise ValidationError("APK R8 metadata or DEX cannot be read") from error
    for entry in expected_entries:
        if "L" + entry.replace(".", "/") + ";" not in classes:
            raise ValidationError(f"Xposed entry class is absent from DEX class_defs: {entry}")
    identities = {(marker["version"], marker["pg-map-id"]) for marker in markers}
    if len(identities) != 1:
        raise ValidationError("APK lacks an unambiguous R8 DEX marker")
    version, mapping_id = next(iter(identities))
    mapping_file = mapping_directory / "mapping.txt"
    try:
        mapping = mapping_file.read_text(encoding="utf-8-sig")
    except (OSError, UnicodeDecodeError) as error:
        raise ValidationError("R8 mapping.txt is missing or unreadable") from error

    def mapping_header(name, required=True):
        values = re.findall(rf"^#\s*{re.escape(name)}\s*:\s*(.*?)\s*$", mapping, re.MULTILINE)
        if len(values) > 1 or (required and (len(values) != 1 or not values[0])):
            raise ValidationError(f"R8 mapping header is missing or duplicated: {name}")
        return values[0] if values else None

    if mapping_header("compiler") != "R8" or mapping_header("pg_map_id") != mapping_id:
        raise ValidationError("R8 mapping compiler or pg_map_id differs from the APK marker")
    mapping_version = mapping_header("compiler_version")
    if mapping_version != version:
        raise ValidationError("R8 mapping compiler version differs from the APK marker")
    artifacts = {}
    for name in ("mapping", "configuration", "seeds", "usage"):
        path = mapping_directory / f"{name}.txt"
        if name != "mapping" and not path.exists():
            continue
        try:
            data = path.read_bytes()
        except OSError as error:
            raise ValidationError(f"R8 artifact is unreadable: {path}") from error
        artifacts[name] = dict(path=str(path.resolve()), sha256=hashlib.sha256(data).hexdigest(), size=len(data))
    return dict(minified=True, engine="R8", r8Version=version, mappingId=mapping_id,
                mappingPath=artifacts["mapping"]["path"], mappingSha256=artifacts["mapping"]["sha256"],
                r8Artifacts=artifacts, xposedEntryPoints=expected_entries)


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


def xposed_scopes(text):
    scopes = [line.strip() for line in text.splitlines()
              if line.strip() and not line.lstrip().startswith("#")]
    if len(scopes) != len(set(scopes)):
        raise ValidationError("Duplicate Xposed scope")
    return set(scopes)


def source_scopes(project):
    path = project / "app/src/main/resources/META-INF/xposed/scope.list"
    scopes = xposed_scopes(path.read_text(encoding="utf-8"))
    if "system" not in scopes:
        raise ValidationError("The system_server hook requires Xposed scope 'system'; 'android' is a separate target")
    return scopes


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
        scopes = xposed_scopes(archive.read("META-INF/xposed/scope.list").decode("utf-8"))
        if scopes != source_scopes(project):
            raise ValidationError(f"Packaged Xposed scopes differ from source: {variant}")
    properties = dict(line.split("=", 1) for line in module.splitlines()
                      if "=" in line and not line.lstrip().startswith("#"))
    for field, value in (("id", identity["applicationId"]), ("version", identity["versionName"]),
                         ("versionCode", str(identity["versionCode"]))):
        if properties.get(field) != value:
            raise ValidationError(f"Xposed {field} mismatch: {variant}")
    entry_list = project / "app/src/main/resources/META-INF/xposed/java_init.list"
    r8 = r8_artifacts(apk, project / "app/build/outputs/mapping" / variant,
                      java_initializers(entry_list.read_text(encoding="utf-8")))
    name = variant.capitalize()
    tests = test_totals(project / f"app/build/test-results/test{name}UnitTest")
    lint = lint_totals(project, project / f"app/build/reports/lint-results-{variant}.xml", policy)
    return dict(apk=str(apk), apkSha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                apkSize=apk.stat().st_size, tests=tests, lint=lint, debuggable=variant == "debug",
                xposedScopes=sorted(scopes), **r8)


def validate_build(project, expected_package, expected_namespace):
    identity = source_identity(project)
    if identity["applicationId"] != expected_package or identity["namespace"] != expected_namespace:
        raise ValidationError("Source package or namespace differs from the selected release identity")
    policy = json.loads((project / "tools/lint-policy.json").read_text(encoding="utf-8"))
    return dict(identity=identity, variants={variant: validate_variant(project, variant, identity, policy)
                                             for variant in ("release",)})


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
