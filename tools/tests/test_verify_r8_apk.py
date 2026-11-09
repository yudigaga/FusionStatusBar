import hashlib
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
import zlib


SCRIPT = Path(__file__).parents[1] / "verify-r8-apk.py"
ENTRY = "com.xtjm.fusionstatusbar.FusionModule"
BUSINESS = "com.xtjm.fusionstatusbar.Business"
BASE = "io.github.libxposed.api.XposedModule"
INIT = "META-INF/xposed/java_init.list"
SCOPE = "META-INF/xposed/scope.list"


def tiny_dex(classes, extra_types=()):
    """Build legal, method-free class tables, including map/checksum/signature."""
    descriptors = sorted({name for name, _, _ in classes}
                         | {base for _, _, base in classes if base}
                         | set(extra_types))
    strings = ["L" + name.replace(".", "/") + ";" for name in descriptors]
    order = sorted(range(len(strings)), key=strings.__getitem__)
    strings = [strings[index] for index in order]
    indices = {name[1:-1].replace("/", "."): index for index, name in enumerate(strings)}
    strings_off = 112
    types_off = strings_off + 4 * len(strings)
    classes_off = types_off + 4 * len(strings)
    data_off = classes_off + 32 * len(classes)
    data = bytearray(data_off)
    string_offsets = []
    for text in strings:
        string_offsets.append(len(data))
        length = len(text)
        while length >= 128:
            data.append((length & 127) | 128)
            length >>= 7
        data.append(length)
        data.extend(text.encode("ascii") + b"\0")
    while len(data) % 4:
        data.append(0)
    map_off = len(data)
    sections = [(0, 1, 0), (1, len(strings), strings_off),
                (2, len(strings), types_off)]
    if classes:
        sections.append((6, len(classes), classes_off))
    sections += [(0x2002, len(strings), data_off), (0x1000, 1, map_off)]
    data.extend(struct.pack("<I", len(sections)))
    for kind, count, offset in sections:
        data.extend(struct.pack("<HHII", kind, 0, count, offset))
    for index, offset in enumerate(string_offsets):
        struct.pack_into("<I", data, strings_off + 4 * index, offset)
        struct.pack_into("<I", data, types_off + 4 * index, index)
    for index, (name, flags, base) in enumerate(sorted(classes, key=lambda item: indices[item[0]])):
        struct.pack_into("<8I", data, classes_off + 32 * index, indices[name], flags,
                         indices[base] if base else 0xffffffff, 0, 0xffffffff, 0, 0, 0)
    data[:8] = b"dex\n035\0"
    for offset, value in ((32, len(data)), (36, 112), (40, 0x12345678),
                          (52, map_off), (56, len(strings)), (60, strings_off),
                          (64, len(strings)), (68, types_off),
                          (96, len(classes)), (100, classes_off if classes else 0),
                          (104, len(data) - data_off), (108, data_off)):
        struct.pack_into("<I", data, offset, value)
    data[12:32] = hashlib.sha1(data[32:]).digest()
    struct.pack_into("<I", data, 8, zlib.adler32(data[12:]) & 0xffffffff)
    return bytes(data)


class VerifyR8ApkTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root / "release.apk"
        self.mapping = self.root / "mapping.txt"
        self.output = self.root / "checks" / "r8.json"
        resources = self.root / "app/src/main/resources/META-INF/xposed"
        resources.mkdir(parents=True)
        (resources / "java_init.list").write_text(ENTRY + "\n", encoding="utf-8")
        (resources / "scope.list").write_text("system\ncom.android.systemui\n", encoding="utf-8")
        (self.root / "app/build.gradle").write_text(
            'android { namespace "com.xtjm.fusionstatusbar" }\n', encoding="utf-8")
        source = self.root / "app/src/main/java/com/xtjm/fusionstatusbar/Business.java"
        source.parent.mkdir(parents=True)
        source.write_text("package com.xtjm.fusionstatusbar; class Business {}\n", encoding="utf-8")
        self.write_mapping()
        self.write_apk()

    def write_mapping(self, business_target="a.b", entry_target=ENTRY, compiler="R8"):
        self.mapping.write_text(
            f"# compiler: {compiler}\n# compiler_version: 8.13.17\n"
            f"{ENTRY} -> {entry_target}:\n{BUSINESS} -> {business_target}:\n",
            encoding="utf-8")

    def write_apk(self, classes=None, init=None, scopes=None, second_dex=None, extra_types=()):
        if classes is None:
            classes = [(ENTRY, 1, BASE), ("a.b", 1, "java.lang.Object")]
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr(INIT, ENTRY + "\n" if init is None else init)
            archive.writestr(SCOPE, "system\ncom.android.systemui\n" if scopes is None else scopes)
            archive.writestr("classes.dex", tiny_dex(classes, extra_types))
            if second_dex is not None:
                archive.writestr("classes2.dex", tiny_dex(second_dex))

    def run_check(self, expected_code):
        completed = subprocess.run(
            [sys.executable, str(SCRIPT), "--project-root", str(self.root),
             "--apk", str(self.apk), "--mapping", str(self.mapping),
             "--output", str(self.output)], capture_output=True, text=True)
        self.assertEqual(expected_code, completed.returncode, completed.stdout + completed.stderr)
        result = json.loads(completed.stdout)
        self.assertEqual(result, json.loads(self.output.read_text(encoding="utf-8")))
        self.assertEqual(hashlib.sha256(self.apk.read_bytes()).hexdigest(), result["apkSha256"])
        self.assertEqual(hashlib.sha256(self.mapping.read_bytes()).hexdigest(), result["mappingSha256"])
        self.assertEqual("not_validated", result["deviceRuntime"])
        return result

    def assert_rejected(self, message):
        result = self.run_check(1)
        self.assertEqual("failed", result["status"])
        self.assertIn(message, result["error"])

    def test_accepts_actual_renaming_and_external_api_references(self):
        self.write_apk(extra_types=("io.github.libxposed.api.XposedInterface",))
        result = self.run_check(0)
        self.assertEqual("passed", result["status"])
        self.assertEqual([ENTRY], result["xposedEntryPoints"])
        self.assertEqual(1, result["renamedModuleClassCount"])
        self.assertEqual(2, result["dexClassDefinitionCount"])

    def test_rejects_entry_only_referenced_in_type_table(self):
        self.write_apk(classes=[("a.b", 1, "java.lang.Object")], extra_types=(ENTRY, BASE))
        self.assert_rejected("Entry class is not defined")

    def test_rejects_non_public_entry(self):
        self.write_apk(classes=[(ENTRY, 0, BASE), ("a.b", 1, "java.lang.Object")])
        self.assert_rejected("Entry class is not public")

    def test_rejects_wrong_superclass(self):
        self.write_apk(classes=[(ENTRY, 1, "java.lang.Object"), ("a.b", 1, "java.lang.Object")])
        self.assert_rejected("does not extend XposedModule")

    def test_accepts_inherited_entry_and_multidex(self):
        self.write_apk(classes=[("com.xtjm.fusionstatusbar.BaseModule", 1, BASE)],
                       second_dex=[(ENTRY, 1, "com.xtjm.fusionstatusbar.BaseModule"),
                                   ("a.b", 1, "java.lang.Object")])
        self.assertEqual(3, self.run_check(0)["dexClassDefinitionCount"])

    def test_rejects_compile_only_api_definition_in_secondary_dex(self):
        self.write_apk(second_dex=[("io.github.libxposed.api.XposedInterface", 1, "java.lang.Object")])
        self.assert_rejected("compileOnly libxposed API")

    def test_rejects_obfuscated_compile_only_api_definition(self):
        with self.mapping.open("a", encoding="utf-8") as stream:
            stream.write("io.github.libxposed.api.XposedInterface -> a.api:\n")
        self.write_apk(second_dex=[("a.api", 1, "java.lang.Object")])
        self.assert_rejected("compileOnly libxposed API")

    def test_rejects_changed_metadata(self):
        for changes in ({"init": "com.example.Other\n"}, {"scopes": "system\n"}):
            with self.subTest(changes=changes):
                self.write_apk(**changes)
                self.assert_rejected("metadata differs")

    def test_rejects_duplicate_metadata(self):
        self.write_apk(scopes="system\ncom.android.systemui\nsystem\n")
        self.assert_rejected("Duplicate metadata entry")

    def test_rejects_non_r8_mapping(self):
        self.write_mapping(compiler="ProGuard")
        self.assert_rejected("not an R8 mapping")

    def test_rejects_renamed_entry_even_if_original_definition_survives(self):
        self.write_mapping(entry_target="a.Entry")
        self.assert_rejected("Entry name changed in mapping")

    def test_rejects_mapping_without_actual_obfuscation(self):
        self.write_mapping(business_target=BUSINESS)
        self.write_apk(classes=[(ENTRY, 1, BASE), (BUSINESS, 1, "java.lang.Object")])
        self.assert_rejected("No module business class was actually renamed")

    def test_rejects_mapping_target_not_defined_in_dex(self):
        self.write_apk(classes=[(ENTRY, 1, BASE), ("a.unrelated", 1, "java.lang.Object")])
        self.assert_rejected("No module business class was actually renamed")

    def test_rejects_mapping_claim_when_original_class_still_exists(self):
        self.write_apk(classes=[(ENTRY, 1, BASE), (BUSINESS, 1, "java.lang.Object"),
                                ("a.b", 1, "java.lang.Object")])
        self.assert_rejected("No module business class was actually renamed")

    def test_rejects_only_synthetic_class_renaming(self):
        self.mapping.write_text(
            f"# compiler: R8\n# compiler_version: 8.13.17\n{ENTRY} -> {ENTRY}:\n"
            f"{BUSINESS}$$ExternalSyntheticLambda0 -> a.b:\n", encoding="utf-8")
        self.write_apk(classes=[(ENTRY, 1, BASE), (BUSINESS, 1, "java.lang.Object"),
                                ("a.b", 1, "java.lang.Object")])
        self.assert_rejected("No module business class was actually renamed")

    def test_rejects_duplicate_definitions_across_dex(self):
        self.write_apk(second_dex=[(ENTRY, 1, BASE)])
        self.assert_rejected("Duplicate DEX class definition")

    def test_rejects_truncated_dex_class_table(self):
        with zipfile.ZipFile(self.apk) as archive:
            members = {name: archive.read(name) for name in archive.namelist()}
        malformed = bytearray(members["classes.dex"])
        struct.pack_into("<I", malformed, 100, len(malformed) - 4)
        members["classes.dex"] = bytes(malformed)
        with zipfile.ZipFile(self.apk, "w") as archive:
            for name, content in members.items():
                archive.writestr(name, content)
        self.assert_rejected("DEX class table truncated")


if __name__ == "__main__":
    unittest.main()
