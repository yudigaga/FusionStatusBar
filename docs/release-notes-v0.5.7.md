# FusionStatusBar 0.5.7

Version `0.5.7`, `versionCode 221`. Local Release APK signed with the official certificate.

## Changes

- The regular status bar continues to use its configured fused-icon size. Control Center's real and fake status-bar copies use an independent fit scale.
- The fake Control Center status bar now suppresses its duplicate native Wi-Fi and cellular signal slots alongside the fused icon, preventing overlapping glyphs on the right side.
- Control Center switch alpha uses the notification shade easing; the OEM-owned horizontal gesture path is unchanged.

## Build Scope

- Built with `:app:assembleRelease`; unit tests and full Debug/Release Lint were not run as requested. Gradle's `lintVitalRelease` ran during Release assembly.
- Official v3 signing and 16 KiB ZIP alignment were verified. APK identity, hash and source snapshot are recorded in `../artifacts/releases/v0.5.7/build-info.json`.
- The APK was not installed on a device; LSPosed callbacks and target-ROM rendering were not validated.
