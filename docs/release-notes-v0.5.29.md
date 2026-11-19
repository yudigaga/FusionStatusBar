# FusionStatusBar 0.5.29

Version `0.5.29`, Android version code `243`.

## Changes

- Added reference-matched icons to the 19 status-icon visibility rows in both Miuix and Android View settings.
- Restored the host's captured native blur scale ratio when using the Highlight Glass compatibility fallback, and replayed it when leaving that fallback.
- Made ringer-mode `volume` icon recovery preflight its reflected state fields and roll back partial state and view changes after failure.

## Validation Scope

- Release APK is built with R8 and signed using the existing official certificate.
- Unit tests and full lint were not run as requested; `lintVitalRelease` runs as part of Release assembly.
- Device installation, LSPosed runtime, rendered status-bar screenshots, and compositor blur were not validated.
- The Highlight Glass compatibility path does not claim native MiGlass blur; it applies a Canvas surface and edge highlight.
