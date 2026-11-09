# Release verification v0.5.25

- Version `0.5.25` / `239`, R8 8.13.19, 3,875,941 bytes. Official certificate, non-debuggable, v3 signature and 16 KiB ZIP alignment passed.
- APK SHA-256: `ce5d19fd99bf16aba7b02a807d5bbde248b4143fd8e87a3b627884b528d1989c`.
- Original archive: `local_dirty_signed`, source `fe7bb9d230781985779e11e7151fc509acffd59f`, `sourceDirty=true`, `existing_build_outputs`. No rebuild or source-provenance relabeling was performed during publication.
- Original Release reports recounted: 208 suites, 1598 tests, zero failures/errors/skips. Release lint: zero errors, 145 warnings; original high-risk policy passed. No Debug archive exists for this version.
- Fresh publication checks: all 782 original ZIP entries match their source index; 778 files restored with four root temporary logs/patches excluded from the publication checkout. All application files equal the build snapshot. README, attributes and release-script integration are deliberate publication changes; prior 0.5.24 history and independent R8 tools are retained.
- Fresh Python regression: 62 tests passed. Final signed APK independently passed public entry/inheritance, compile-only API exclusion, metadata/scope and actual business-class renaming checks; 524 module class/inner/synthetic mappings renamed.
- Actual DEX R8 marker and mapping correspond to ID `da091bfd56f74e0235610b9cbfe7be6897295d9b009a39f1c2746720c05b9e59`. Mapping SHA-256: `e248d14b7d52e619a0b27ae2290abbd4ed90d52195b8d217ffdfb63749140064`; all four R8 artifact hashes and sizes match the original record.
- Release-only pipeline retains the previous independent archive APK gate in addition to the new marker/mapping validation. Original archived reports and source ZIP remain unchanged.
- JVM tests precede minification. Gradle was not rerun during publication. Phone installation, actual language switching/persistence, LSPosed callbacks, ROM, hardware and service behavior were not validated. Remote CI results are reported separately.
