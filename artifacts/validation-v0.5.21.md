# Local verification v0.5.21

- Official certificate signed Release APK, version `0.5.21` / `235`, non-debuggable; v3 signature and 16 KiB ZIP alignment verified.
- Build-time source snapshot: 665 files, SHA-256 checked against the workspace, index and ZIP before release documentation changes.
- Archived complete unit test suites: Debug and Release each contain 194 classes and 1500 tests, with zero failures, errors or skips.
- Complete Debug and Release lint: zero errors and 99 warnings each; high-risk policies passed. Both variants assembled successfully.
- The 28 Python tool regression tests passed during release preparation. The current build metadata and report validator also passed.
- Device installation, actual LSPosed callbacks, target ROM glass rendering, tile availability and click behavior remain unvalidated.
- Original `release.json` is preserved as `release-v0.5.21.json`, including `local_dirty_signed`, `sourceDirty=true` and `existing_build_outputs`. Publishing does not modify the signed APK or imply a clean-source rebuild.
