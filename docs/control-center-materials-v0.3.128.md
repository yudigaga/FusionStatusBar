# Control Center Materials

Version: 0.3.128 / 142. Date: 2026-10-01.

## Settings

The existing layout and appearance panel now contains three independent 0-100%
sliders: background blur, card blur, and tile blur. Each has transparent and blur
endpoints. Existing configurations default to 100%; reset restores these defaults.
The preferences and Bundle transport preserve all three fields across config
copies. No layout-plan schema migration is required.

Slider changes update only the editor draft. Explicit push publishes the settings
through the existing configuration and SystemUI notification path.

## Runtime

- Background strength scales the native window controller's blur output, retaining
  the native open/close progress. It does not replace the expansion animation.
- Card strength covers native large cards, media, slider tracks, device entries,
  compact cards, and module-owned group containers.
- Tile strength covers small quick-setting surfaces, including group members.
- Only material backgrounds, blend-color alpha, and native blur settings change.
  View alpha, transforms, foreground text/icons, and slider fill are not scaled.
- Native surfaces at 100% retain OEM requests. Intermediate strengths request
  per-surface background sampling where reversible HyperOS APIs are available.
  Module-owned group containers additionally request their own backdrop at 100%.
  Their requested radius ranges from 0 to 275 native units.
- Disabling customization restores tracked native values. Native EDIT mode passes
  through native requests. Repeated refreshes do not compound alpha or re-enable
  disabled module-owned surfaces. Requests received before blur initialization
  and later OEM theme updates retain their original restorable values.

The implementation probes vendor View blur methods and the plugin window blur
controller. Logs include `control-center native material APIs=N/5`. Unsupported
per-surface APIs retain available material controls and log a warning. API presence
alone does not prove that the OEM compositor renders a particular blur effect.

## Preview And Device Checks

The editor continues to use the bundled dark, preblurred background. It does not
read the system wallpaper. Its preview adjusts material opacity; it is not a live
SystemUI backdrop sample and cannot reproduce every native blur or tint layer.

After installing the APK, restart SystemUI or reboot, enable customization, and
push the draft. Check each slider independently at 0%, 50%, and 100%, including
native cards, tiles, groups, open/close animation, and native EDIT scrolling.
Check disabling customization restores native materials. No device installation
or Xiaomi/HyperOS compositor validation was performed for this release.

## Verification

`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` completed successfully.
292 tests passed with zero failures, errors, or skips. Lint reported zero errors
and 57 warnings. The final APK passed v2 signature verification; package metadata
reports versionName 0.3.128 and versionCode 142.

Coverage includes independent draft sliders, explicit-push persistence, defaults,
serialization, layer targeting, non-compounding alpha, native parameter restoration,
repeated disabled refreshes, and foreground pixel preservation. The two additional
restoration tests failed before their fixes and passed in the final full run.
Native blur tests use simulated OEM setter receivers, not a hardware compositor.
The 300-pixel settings screenshot was inspected for the new controls.

APK: `app/build/outputs/apk/debug/fusion-statusbar-v0.3.128-debug.apk`.
