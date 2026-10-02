# Canvas Icon Rendering

Version: 0.3.129 / 143. Date: 2026-10-01.

## Evidence

The user supplied a canvas screenshot with white plate-like icons and dark glyphs
that were barely visible, and clarified that this predated the blur sliders and
appeared when tiles were added. This investigation concerns the existing icon
pipeline, not a claim that the sliders introduced the issue. No new device log or
original cached icon bitmaps were supplied.

Four native-graphics regression fixtures failed before the fix: a cached neutral
plate rendered as a glyph, a black tile glyph was unreadable on a dark surface,
wrapped native artwork retained its background layer, and an active title remained
dark when its backing was transparent. The fixtures exercise the actual capture
and preview code; they do not identify every OEM drawable in the supplied screenshot.

## Changes

- Preview artwork is classified at bind time. Empty images and nearly uniform,
  dense neutral plates fall back to the packaged spec icon. Unknown specs retain
  the generic fallback. This cannot reconstruct lost glyph details.
- Sparse monochrome captured/application icons adapt to the visible surface.
  Colored icons and detailed opaque application artwork retain their pixels.
  The source bitmap is neither recolored nor recycled.
- Native capture traverses current state and standard DrawableWrapper layers
  before selecting the foreground LayerDrawable child. Traversal is bounded;
  source bounds and live tile state remain unchanged.
- A missing or invalid captured icon no longer prevents a usable application
  TileService icon from being used. Captured labels, state, shape, and level remain.
- Card labels, paired icons, and group members use the same backdrop-aware preview
  contrast path. The existing draft, layout, push, and live SystemUI icon paths are
  not replaced. Existing cached layouts need not be recreated.

## Verification

`:app:testDebugUnitTest --tests '*ControlCenterCanvasIconTest'` initially produced
four failures. The final full test, lint, and debug assembly run passed: 300 tests,
zero failures/errors/skips, lint zero errors and 57 warnings. New coverage includes
old cache fallback, bitmap preservation, paired/grouped tiles, and screenshots at
360/440 widths with transparent/opaque materials.

Inspected `app/build/reports/control-center-icons/canvas-360-0.png` and
`canvas-440-100.png`. The APK passed v2 signature verification and reports the
expected version in package and Xposed metadata.

APK: `app/build/outputs/apk/debug/fusion-statusbar-v0.3.129-debug.apk`.

No phone installation or HyperOS rendering verification was performed. Reopen the
canvas after installation to check existing and newly added system/application
tiles. Refresh the native capture after restarting SystemUI to exercise the updated
sampler; this is separate from rebuilding or overwriting the editable layout.
