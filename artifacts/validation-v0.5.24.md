# R8 Release verification v0.5.24

- Version `0.5.24` / `238`, official certificate signed, non-debuggable; v3 signature and 16 KiB ZIP alignment passed.
- Fresh R8 archive built from clean source commit `e2ab818d4488cb4ee9a6d88a7a6dd707a463362a`: `official_signed`, `sourceDirty=false`, `built_in_this_run`.
- Debug and Release each passed 204 classes / 1572 complete unit tests with zero failures, errors or skips; both lint variants reported zero errors and 101 warnings.
- Python tools passed 45 tests, including 17 R8 APK contract tests.
- Final APK: 3,335,213 bytes, SHA-256 `c81924b0db367c26822c3032476eb1fbba59c02b49ff05b9e574e42886eb3789`; about 84.6 percent smaller than the original 21,701,878-byte non-R8 archive.
- R8 8.13.19 mapping, configuration, seeds, usage and post-signing structural verification are archived. Module entry/public inheritance, metadata, actual business-class renaming and compile-only API exclusion passed.
- SDK checks cover the public entry constructor and three callbacks, Manifest components/launcher alias, XML View constructors and stable configuration identifiers. The 80 resource entries match the non-R8 baseline byte-for-byte.
- JVM tests run before minification. Actual LSPosed/ROM callbacks, visual effects, hardware and service behavior were not device-validated.
