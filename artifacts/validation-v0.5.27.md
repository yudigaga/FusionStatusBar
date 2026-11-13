# Release verification v0.5.27

- Version `0.5.27` / `241`, R8 8.13.19, 3,896,421 bytes. Official certificate, non-debuggable, v3 signature and 16 KiB ZIP alignment passed.
- APK SHA-256: `864cba54d6b4746b5a20a0059dbf0dc514864f2faefaba9d4ae2a2a3b150aad3`.
- Original archive: `local_dirty_signed`, source `fe7bb9d230781985779e11e7151fc509acffd59f`, `sourceDirty=true`, `existing_build_outputs`. The existing signed package was not rebuilt or relabeled as a clean publication-commit build.
- Original Release reports recounted: 5 suites, 32 RELATED tests, zero failures/errors/skips. No full-suite rerun for this version. Full Release lint: zero errors, 142 warnings; original high-risk policy passed. No Debug archive.
- Fresh source check: all 796 original ZIP entries match their index; 792 restored and four temporary root logs/patches excluded from new source commits. All application files equal the build snapshot. Published 0.5.24/25 metadata, sponsor image and independent R8 tools retained.
- Publication changes outside the application: README/docs navigation and build/development guides, original report context notes, attributes and restoration of the independent release-script APK gate. Original source ZIP and archived records remain unchanged.
- Fresh tools: 62 regression tests passed. Final signed APK independently passed entry/public inheritance, compile-only API exclusion, scope/metadata and actual renaming checks; 531 module class/inner/synthetic mappings renamed.
- Actual DEX R8 marker and mapping match ID `3096445c046059544460f966f5f77f96dcf9922fa8acb76ddaaa9c516edfbb2d`; all four archived R8 artifact hashes/sizes match.
- JVM tests precede minification. Gradle was not rerun during publication. Phone installation, actual LSPosed/ART deoptimization, host animations and ROM acceptance were not validated. CI results are separate.
