# FusionStatusBar 0.5.8

Version `0.5.8`, `versionCode 222`. Local Release APK signed with the official certificate.

## Changes

- Include the fake Control Center status-bar battery area in native status-icon alignment. This host uses `system_icon_area`, not `system_icons`, so its copied status icons previously missed the spacing adjustment beside the fused icon.
- Add one-time geometry logging for each aligned container, including available width, required width, host type, and final icon/status positions.
- Preserve the separate size policy: the regular status bar uses its existing icon-size setting; Control Center real and fake copies use the independent fit scale.

## Build Scope

- Built with `:app:assembleRelease`; unit tests and full Debug/Release Lint were not run as requested. Gradle's `lintVitalRelease` ran during Release assembly.
- Official v3 signing and 16 KiB ZIP alignment were verified. APK identity, hash, source snapshot, and build scope are recorded in `../artifacts/releases/v0.5.8/build-info.json`.
- The APK was not installed on a device; target-ROM runtime rendering remains unverified.
