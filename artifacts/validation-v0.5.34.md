# Release verification v0.5.34

- Version `0.5.34` / `248`; APK SHA-256 `937fda3d49a5973d2a5bafd3c6920194aa362f91a4c61a761a620cff748f86ae`, size 3,954,605 bytes.
- Top-level and archived APKs match byte-for-byte. APK Signature Scheme v3 validates with the existing release certificate (`ccfaf42f8f14bb36973aeb971572fcc956a256196f68ff088340dfa7eca96fe2`); 16 KiB ZIP alignment passes. APK is non-debuggable, min SDK 33, target SDK 35.
- The archived build log records successful `assembleRelease` with R8 `8.13.19`; `lintVitalRelease` reports no issues. Full `lintRelease` was not run.
- No 0.5.34 unit test run is recorded or claimed. `validation-v0.5.33.json` is historical only: 1,852 tests, 26 failures, reported as matching the 0.5.32 baseline. It is not evidence for this version.
- Publication tooling check: 62 Python regression tests passed; the signed APK also passed the independent R8 structure verifier. These checks do not substitute for app unit tests or full lint.
- Independent R8 structural verification matched the DEX marker and mapping ID `367faca61624c7339f2ca4891094c20b022896b25fe7a976d25809a291765be0`. Mapping SHA-256 is `9dce7d0de7af2dce0da1c07b42393cd62ba407d1d8c5b7a53fd089621a2c965f`.
- The original source archive contains 749 files; every indexed path and SHA-256 matches `source-files.json`. Build provenance remains `local_snapshot_signed`, `sourceDirectory=fusion-feedback-20261118-05`, and null Git commit/ref/dirty/patch fields. The publication commit is not represented as the build commit.
- No phone installation, LSPosed callbacks, SystemUI runtime, ROM, rendered UI, native MiGlass compositor behavior, or performance validation was performed. Remote CI results are separate.
