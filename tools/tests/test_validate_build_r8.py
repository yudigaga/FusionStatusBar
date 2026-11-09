import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile
import zlib


MODULE = Path(__file__).parents[1] / "validate-build.py"
SPEC = importlib.util.spec_from_file_location("validate_build_r8", MODULE)
validator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validator)

ENTRY = "com.xtjm.fusionstatusbar.FusionModule"
DESCRIPTOR = "Lcom/xtjm/fusionstatusbar/FusionModule;"
R8_VERSION = "8.13.17"
MAP_ID = "abc1234"


def uleb128(value):
    result = bytearray()
    while True:
        next_byte = value & 0x7f
        value >>= 7
        result.append(next_byte | (0x80 if value else 0))
        if not value:
            return bytes(result)


def minimal_dex(markers=(), classes=(DESCRIPTOR,), extra_strings=()):
    """Synthetic DEX with genuine string_ids, type_ids and class_defs tables."""
    strings = list(dict.fromkeys([*markers, *classes, *extra_strings]))
    string_off = 112 if strings else 0
    type_off = 112 + len(strings) * 4 if classes else 0
    class_off = 112 + len(strings) * 4 + len(classes) * 4 if classes else 0
    data_off = 112 + len(strings) * 4 + len(classes) * 36
    data = bytearray(data_off)
    for index, value in enumerate(strings):
        struct.pack_into("<I", data, string_off + index * 4, len(data))
        size = len(value.encode("utf-16-le", errors="surrogatepass")) // 2
        data.extend(uleb128(size))
        data.extend(value.encode("utf-8", errors="surrogatepass").replace(b"\0", b"\xc0\x80"))
        data.append(0)
    for index, descriptor in enumerate(classes):
        struct.pack_into("<I", data, type_off + index * 4, strings.index(descriptor))
        struct.pack_into("<8I", data, class_off + index * 32,
                         index, 1, 0xffffffff, 0, 0xffffffff, 0, 0, 0)
    data[:8] = b"dex\n039\0"
    struct.pack_into("<20I", data, 32, len(data), 112, 0x12345678, 0, 0, 0,
                     len(strings), string_off, len(classes), type_off, 0, 0, 0, 0, 0, 0,
                     len(classes), class_off, len(data) - data_off, data_off)
    data[12:32] = hashlib.sha1(data[32:]).digest()
    struct.pack_into("<I", data, 8, zlib.adler32(data[12:]))
    return bytes(data)


def marker(tool="R8", **changes):
    values = {"backend": "dex", "version": R8_VERSION,
              "compilation-mode": "release", "pg-map-id": MAP_ID}
    values.update(changes)
    return "~~" + tool + json.dumps(values, separators=(",", ":"))


class R8BuildGateTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.apk = self.root / "module.apk"
        self.mapping = self.root / "mapping"
        self.mapping.mkdir()
        self.mapping_text = (f"# compiler: R8\n# compiler_version: {R8_VERSION}\n"
                             f"# pg_map_id: {MAP_ID}\n{ENTRY} -> {ENTRY}:\n")
        (self.mapping / "mapping.txt").write_bytes(self.mapping_text.encode("utf-8"))
        self.write_apk()

    def write_apk(self, dex=None, entries=None, extras=None):
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("classes.dex", minimal_dex([marker()]) if dex is None else dex)
            archive.writestr("META-INF/xposed/java_init.list", ENTRY + "\n" if entries is None else entries)
            for path, value in (extras or {}).items():
                archive.writestr(path, value)

    def check(self):
        return validator.r8_artifacts(self.apk, self.mapping, [ENTRY])

    def project(self, non_r8_release=False):
        root = self.root / "project"
        (root / "app").mkdir(parents=True)
        (root / "tools").mkdir()
        (root / "app/build.gradle").write_text(
            'namespace "com.xtjm.fusionstatusbar"\ncompileSdk 36\n'
            'applicationId "io.github.yudigaga.fusionstatusbar"\n'
            'versionCode 239\nversionName "0.5.25"\ntargetSdk 35\n', encoding="utf-8")
        (root / "tools/lint-policy.json").write_text('{"checkedIds": [], "allowed": {}}', encoding="utf-8")
        resources = root / "app/src/main/resources/META-INF/xposed"
        resources.mkdir(parents=True)
        (resources / "scope.list").write_text("system\ncom.android.systemui\n", encoding="utf-8")
        (resources / "java_init.list").write_text(ENTRY + "\n", encoding="utf-8")
        for variant in ("release",):
            output = root / "app/build/outputs/apk" / variant
            output.mkdir(parents=True)
            (output / "output-metadata.json").write_text(json.dumps({
                "applicationId": "io.github.yudigaga.fusionstatusbar", "elements": [{
                    "versionName": "0.5.25", "versionCode": 239, "outputFile": "module.apk"}]}), encoding="utf-8")
            with zipfile.ZipFile(output / "module.apk", "w") as archive:
                archive.writestr("classes.dex", minimal_dex([marker(
                    "D8" if variant == "release" and non_r8_release else "R8")]))
                archive.writestr("META-INF/xposed/java_init.list", ENTRY + "\n")
                archive.writestr("META-INF/xposed/scope.list", "system\ncom.android.systemui\n")
                archive.writestr("META-INF/xposed/module.prop",
                                 "id=io.github.yudigaga.fusionstatusbar\nversion=0.5.25\nversionCode=239\n")
            config = root / f"app/build/generated/source/buildConfig/{variant}/com/xtjm/fusionstatusbar/BuildConfig.java"
            config.parent.mkdir(parents=True)
            config.write_text('APPLICATION_ID = "io.github.yudigaga.fusionstatusbar";\n'
                              'VERSION_NAME = "0.5.25";\nVERSION_CODE = 239;\n'
                              f'DEBUG = {"true" if variant == "debug" else "false"};\n', encoding="utf-8")
            mapping = root / "app/build/outputs/mapping" / variant
            mapping.mkdir(parents=True)
            (mapping / "mapping.txt").write_text(self.mapping_text, encoding="utf-8")
            tests = root / f"app/build/test-results/test{variant.capitalize()}UnitTest"
            tests.mkdir(parents=True)
            (tests / "TEST-fixture.xml").write_text(
                '<testsuite tests="1" failures="0" errors="0" skipped="0"/>', encoding="utf-8")
            reports = root / "app/build/reports"
            reports.mkdir(exist_ok=True)
            (reports / f"lint-results-{variant}.xml").write_text("<issues/>", encoding="utf-8")
        return root

    def test_full_build_validation_defaults_to_release_only(self):
        result = validator.validate_build(self.project(), "io.github.yudigaga.fusionstatusbar",
                                          "com.xtjm.fusionstatusbar")
        self.assertEqual({"release"}, set(result["variants"]))
        for variant in result["variants"].values():
            self.assertTrue(variant.get("minified"))
            self.assertEqual(MAP_ID, variant["mappingId"])

    def test_non_r8_release_prevents_a_full_validation_success(self):
        with self.assertRaises(validator.ValidationError):
            validator.validate_build(self.project(non_r8_release=True),
                                     "io.github.yudigaga.fusionstatusbar", "com.xtjm.fusionstatusbar")

    def test_valid_r8_dex_and_matching_mapping_produce_verifiable_artifact_metadata(self):
        (self.mapping / "configuration.txt").write_text("-keep class Entry", encoding="utf-8")
        (self.mapping / "usage.txt").write_text("RemovedClass", encoding="utf-8")
        result = self.check()
        self.assertTrue(result.get("minified"), "the actual APK must establish R8 processing")
        self.assertEqual("R8", result["engine"])
        self.assertEqual(R8_VERSION, result["r8Version"])
        self.assertEqual(MAP_ID, result["mappingId"])
        self.assertEqual(str((self.mapping / "mapping.txt").resolve()), result["mappingPath"])
        self.assertEqual(hashlib.sha256(self.mapping_text.encode()).hexdigest(), result["mappingSha256"])
        self.assertEqual({"mapping", "configuration", "usage"}, set(result["r8Artifacts"]))

    def test_dex_parser_uses_type_and_class_def_tables_and_handles_modified_utf8(self):
        result = validator.dex_metadata(minimal_dex([marker()], extra_strings=["\ud83d\ude00", "a\0b"]))
        self.assertEqual({DESCRIPTOR}, result["classes"])
        self.assertEqual(MAP_ID, result["markers"][0]["pg-map-id"])

    def test_d8_output_is_rejected_even_when_a_previous_r8_mapping_remains(self):
        self.write_apk(minimal_dex([marker("D8")]))
        with self.assertRaises(validator.ValidationError):
            self.check()

    def test_marker_in_an_asset_or_raw_non_dex_bytes_cannot_prove_r8(self):
        for dex in (minimal_dex(), b"not a dex " + marker().encode()):
            with self.subTest(dex=dex[:8]):
                self.write_apk(dex, extras={"assets/compiler.txt": marker()})
                with self.assertRaises(validator.ValidationError):
                    self.check()

    def test_entry_descriptor_in_string_pool_without_a_class_definition_is_rejected(self):
        self.write_apk(minimal_dex([marker()], classes=(), extra_strings=[DESCRIPTOR]))
        with self.assertRaises(validator.ValidationError):
            self.check()

    def test_packaged_entry_list_must_match_the_source_contract(self):
        self.write_apk(entries="different.Module\n")
        with self.assertRaises(validator.ValidationError):
            self.check()

    def test_missing_entry_list_or_empty_expected_entry_contract_is_rejected(self):
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("classes.dex", minimal_dex([marker()]))
        with self.assertRaises(validator.ValidationError):
            self.check()
        self.write_apk()
        with self.assertRaises(validator.ValidationError):
            validator.r8_artifacts(self.apk, self.mapping, [])

    def test_mapping_requires_an_r8_header_an_id_and_nonempty_content(self):
        for content in ("", "# compiler: D8\n# pg_map_id: abc1234\n", "# compiler: R8\n"):
            with self.subTest(content=content):
                (self.mapping / "mapping.txt").write_text(content, encoding="utf-8")
                with self.assertRaises(validator.ValidationError):
                    self.check()

    def test_missing_mapping_is_rejected(self):
        (self.mapping / "mapping.txt").unlink()
        with self.assertRaises(validator.ValidationError):
            self.check()

    def test_missing_mapping_compiler_version_is_rejected(self):
        without_version = self.mapping_text.replace(f"# compiler_version: {R8_VERSION}\n", "")
        (self.mapping / "mapping.txt").write_bytes(without_version.encode("utf-8"))
        with self.assertRaisesRegex(validator.ValidationError, "compiler_version"):
            self.check()

    def test_stale_mapping_id_and_different_compiler_versions_are_rejected(self):
        for changes in ({"pg-map-id": "stale"}, {"version": "1.0.0"}):
            with self.subTest(changes=changes):
                self.write_apk(minimal_dex([marker(**changes)]))
                with self.assertRaises(validator.ValidationError):
                    self.check()

    def test_invalid_marker_json_backend_or_missing_map_id_is_rejected(self):
        for value in ("~~R8{broken", marker(backend="cf"), marker(**{"pg-map-id": None})):
            with self.subTest(value=value):
                self.write_apk(minimal_dex([value]))
                with self.assertRaises(validator.ValidationError):
                    self.check()

    def test_entry_in_secondary_dex_is_accepted_without_an_apk_buildconfig_class(self):
        self.write_apk(minimal_dex([marker()], classes=()),
                       extras={"classes2.dex": minimal_dex(classes=(DESCRIPTOR,))})
        self.assertTrue(self.check().get("minified"))

    def test_conflicting_r8_markers_and_duplicate_mapping_headers_are_rejected(self):
        self.write_apk(minimal_dex([marker(), marker(**{"pg-map-id": "ambiguous"})]))
        with self.assertRaises(validator.ValidationError):
            self.check()
        self.write_apk()
        (self.mapping / "mapping.txt").write_text(self.mapping_text + "# pg_map_id: duplicate\n", encoding="utf-8")
        with self.assertRaises(validator.ValidationError):
            self.check()

    def test_dex_truncation_out_of_bounds_tables_and_invalid_type_indices_fail_closed(self):
        valid = minimal_dex([marker()])
        corruptions = [valid[:111], valid[:-1]]
        for offset, value in ((60, len(valid)), (56, 0xffffffff), (68, len(valid)),
                              (100, len(valid)), (struct.unpack_from("<I", valid, 68)[0], 0xffffffff),
                              (struct.unpack_from("<I", valid, 100)[0], 0xffffffff)):
            changed = bytearray(valid)
            struct.pack_into("<I", changed, offset, value)
            corruptions.append(bytes(changed))
        string_data = struct.unpack_from("<I", valid, 112)[0]
        for replacement in (b"\xff\xff\xff\xff\xff", b"\xff\xff\xff\xff\x10"):
            changed = bytearray(valid)
            changed[string_data:string_data + 5] = replacement
            corruptions.append(bytes(changed))
        for data in corruptions:
            with self.subTest(prefix=data[:12]):
                with self.assertRaises(validator.ValidationError):
                    validator.dex_metadata(data)


if __name__ == "__main__":
    unittest.main()
