import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


MODULE = Path(__file__).parents[1] / "validate-build.py"
SPEC = importlib.util.spec_from_file_location("validate_build", MODULE)
validate_build = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validate_build)


class ValidateBuildTests(unittest.TestCase):
    def test_source_identity_reads_literal_android_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "app").mkdir()
            (root / "app/build.gradle").write_text(
                'namespace "com.example"\ncompileSdk 35\ndefaultConfig {\n'
                'applicationId "io.example"\nminSdk 33\ntargetSdk 35\n'
                'versionCode 9\nversionName "1.2.3"\n}\n', encoding="utf-8")
            self.assertEqual(validate_build.source_identity(root), {
                "namespace": "com.example", "applicationId": "io.example", "versionName": "1.2.3",
                "versionCode": 9, "compileSdk": 35, "targetSdk": 35,
            })

    def test_test_totals_rejects_skipped_tests(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "TEST-fixture.xml"
            report.write_text('<testsuite tests="2" failures="0" errors="0" skipped="1"/>', encoding="utf-8")
            with self.assertRaises(validate_build.ValidationError):
                validate_build.test_totals(Path(directory))

    def test_lint_totals_allows_only_policy_entries(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = root / "lint.xml"
            report.write_text('<issues><issue id="StaticFieldLeak" severity="Warning">'
                              '<location file="app/src/main/java/X.java"/></issue></issues>', encoding="utf-8")
            policy = {"checkedIds": ["StaticFieldLeak"], "allowed": {"StaticFieldLeak:app/src/main/java/X.java": 1}}
            result = validate_build.lint_totals(root, report, policy)
            self.assertEqual(result["warnings"], 1)

    def test_validate_build_rejects_wrong_public_package(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "app").mkdir()
            (root / "app/build.gradle").write_text(
                'namespace "com.example"\ncompileSdk 35\napplicationId "com.example"\n'
                'versionCode 1\nversionName "1.0"\ntargetSdk 35\n', encoding="utf-8")
            (root / "tools").mkdir()
            (root / "tools/lint-policy.json").write_text(json.dumps({"checkedIds": [], "allowed": {}}), encoding="utf-8")
            with self.assertRaises(validate_build.ValidationError):
                validate_build.validate_build(root, "io.example", "com.example")


if __name__ == "__main__":
    unittest.main()
