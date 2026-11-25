import re
import unittest
from pathlib import Path


class ModuleLogSinkTest(unittest.TestCase):
    """New module code must not bypass the two process-local diagnostic sinks."""

    def test_production_sources_do_not_write_diagnostics_outside_the_gate(self):
        package = Path(__file__).resolve().parents[2] / "app/src/main/java/com/xtjm/fusionstatusbar"
        android_sink = re.compile(
            r"\b(?:Log|Slog|EventLog|Timber)\s*\.\s*(?:[vdiwe]|wtf|println|writeEvent)\s*\("
        )
        other_sink = re.compile(
            r"\b(?:System\s*\.\s*(?:out|err)\b|printStackTrace\s*\(|XposedBridge\s*\.\s*log\s*\()"
        )
        framework_sink = re.compile(r"\b(?:module|reporter|xposedModule)\s*\.\s*log\s*\(")
        violations = []
        for path in sorted(package.rglob("*")):
            if path.suffix not in (".java", ".kt"):
                continue
            for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                if (path.name != "ModuleLog.java" and android_sink.search(line)
                        or path.name != "FusionModule.java" and framework_sink.search(line)
                        or other_sink.search(line)):
                    violations.append(f"{path.name}:{number}: {line.strip()}")
        self.assertTrue(package.is_dir())
        self.assertEqual([], violations)


if __name__ == "__main__":
    unittest.main()
