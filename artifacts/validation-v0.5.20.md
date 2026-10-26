# Local verification v0.5.20

- Official certificate signed Release APK, version `0.5.20` / `234`, non-debuggable; v3 signature and 16 KiB ZIP alignment verified.
- Build-time source snapshot: 655 files, SHA-256 checked against the workspace, index and ZIP before release documentation changes.
- Archived targeted unit tests: Debug and Release each contain 26 classes and 242 tests, with zero failures, errors or skips. These are targeted regressions, not the full suites.
- Complete Debug and Release lint: zero errors, 99 and 98 warnings respectively; high-risk policies passed. Both variants assembled successfully.
- The 28 Python tool regression tests passed during release preparation. Feature/APK checks and independent signature/alignment evidence are preserved with the archive.
- MaterialKolor license and notice files match the packaged APK entries. Dependency declaration, lockfile and license texts accompany the source publication.
- The original feature report contains damaged Chinese label keys; it is preserved unchanged. `publication-feature-check.json` records an independent check of all 12 expected labels directly in the APK resources, plus the three packaged license files.
- Full unit test suites, device installation, actual LSPosed callbacks, haptic feel and target ROM behavior remain unvalidated.
- Original `release.json` is preserved as `release-v0.5.20.json`, including `local_dirty_signed`, `sourceDirty=true`, `existing_build_outputs` and historical local distribution state. Publishing does not modify the signed APK or imply a clean-source rebuild.
