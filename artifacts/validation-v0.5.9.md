# Local verification v0.5.9

- Built locally with `:app:assembleRelease`; official v3 signature and 16 KiB ZIP alignment passed.
- APK metadata is `io.github.yudigaga.fusionstatusbar`, version `0.5.9` / `223`; final APK SHA-256 is recorded in `release-v0.5.9.json`.
- The build record states that Debug/Release unit tests and full lint were not run. `lintVitalRelease` ran as part of Release assembly.
- The 28 Python tool regression tests passed during release preparation. They do not replace Android unit tests or full lint.
- The build-time source archive contains 570 indexed files. `source.zip`, its index, and the tracked patch match their build records.
- Device installation, LSPosed injection, target ROM rendering, and runtime performance were not validated.
