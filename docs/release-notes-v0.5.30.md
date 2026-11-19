# FusionStatusBar 0.5.30

Version `0.5.30`, Android version code `244`.

## Changes

- Kept disabled status-icon categories blocked after fused-layout restoration, including alarm and camera slots.
- Replaced the camera setting's person silhouette with a camera/webcam glyph.
- Includes the 19 screenshot-matched icon visibility rows and reversible Highlight Glass / volume-icon restoration fixes from the preceding local candidate.

## Validation Scope

- Release APK is built with R8 and signed using the existing official certificate.
- Unit tests and full lint are not run as requested; `lintVitalRelease` runs as part of Release assembly.
- Device installation, LSPosed runtime, rendered status-bar screenshots, and compositor blur are not validated.
