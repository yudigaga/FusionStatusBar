# FusionStatusBar 0.5.28

Version `0.5.28`, Android version code `242`.

## Changes

- Added a status-bar icon visibility customization sheet with the 19 SystemUI categories from the reference, grouped into system status and connected devices.
- Composed the choices with SystemUI's native hidden-slot policy, including roaming flow, privacy policy, and the multi-slot headset and speaker categories.
- Prioritized an active ringer-mode `volume` icon only when the visible phone or keyguard icon container marked it hidden by space and a non-overlapping gap remains.
- Changed unknown Highlight Glass support to use a reversible Canvas compatibility tint and reflection while leaving OEM blur requests untouched.

## Validation Scope

- Release APK is built with R8 and signed using the existing official certificate.
- Unit tests were not run as requested.
- Device installation, LSPosed runtime after this build, rendered status-bar screenshots, and compositor blur were not validated.
- The Highlight Glass compatibility path does not claim native MiGlass blur; it applies a Canvas surface and edge highlight.
