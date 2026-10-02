# Control-center icon regression fixture

`miui-themed-empty-service-icon.png` is an unchanged copy of the 96 x 96 PNG
exported during Android 16 device validation of version 0.3.146 on 2026-10-02.
Source: `artifacts/validation/v0.3.146/20261002-icon-ui/service-icons/icon-21.png`.
The accompanying `icons.json` identifies it as the discovered TileService icon
for `com.miui.mishare.connectivity/.tile.MiShareTileService` (小米互传).

The device also returned the same empty themed plate for 屏幕录制 (`icon-29.png`)
and 投屏 (`icon-44.png`). It has a white interior with a gray edge shadow but
no service logo. This is failure evidence, not an icon asset for the app.

SHA-256: `7cce1294c3f3f325e5aff2eaafb03f006e9d28cca0c850839b8aeca73716161c`.
The test loads the original PNG and runs it through the real card renderer;
it must fall back to an identifiable local symbol without changing the source.
