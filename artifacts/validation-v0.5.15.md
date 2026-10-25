# Local verification v0.5.15

- Official certificate signed Release APK, version `0.5.15` / `229`, non-debuggable; v3 signature and 16 KiB ZIP alignment verified.
- Build-time source snapshot: 611 files, SHA-256 checked against the workspace, index and ZIP before release documentation changes.
- Archived targeted Debug tests: 52 classes, 339 tests, zero failures, errors or skips. These are targeted regressions, not the full Debug suite.
- Complete Release lint: zero errors, 98 warnings; high-risk policy passed. Release assembly passed.
- The 28 Python tool regression tests passed during release preparation.
- Full Debug unit tests, Release unit tests and Debug lint were not run for this build. Device installation, LSPosed runtime stability, target ROM visuals, gesture timing and frame-rate improvements remain unvalidated.
- Original `build-info.json` is preserved as `release-v0.5.15.json`, including `local_dirty_signed_candidate` and `sourceDirty=true`. Publishing does not alter the signed APK or imply a clean-source rebuild.
