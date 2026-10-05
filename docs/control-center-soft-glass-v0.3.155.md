# Control Center Soft Glass and Material Regression

The global container selector includes soft glass ("柔光玻璃"). It keeps the
device's native per-container blur, uses a translucent cool surface, and draws
a light-sensitive rim that follows the container outline. The reflection is
limited to the edge, so text, icons and slider fills are not washed out. The
editor preview uses the same rim and restores its previous pixels when another
style is selected. This is an independent implementation informed by the
HyperLight visual reference; no HyperLight code or assets are bundled.

The 0.3.154 device capture showed 15 correctly placed items but pale cards,
square borders inside large cards and groups, broken border segments on small
tiles, and slider fills nearly indistinguishable from their tracks. LSPosed
confirmed module injection, 38 control-center hooks and the 15-item mount;
it did not record the selected surface style or a material exception.

Global borders and glass reflections now target outer containers. Card and
group icon backgrounds keep their OEM colors, including when a native blend
request or configuration refresh arrives before the icon is attached to its
card. Standalone compact tiles still receive the global surface. Compound drawables are tinted only
when they identify a background layer, leaving unlabelled slider fill layers
untouched. Background, card and tile blur strengths retain their existing
roles; the transparent style continues to disable container blur.

Pixel tests cover nested icons, compact round tiles, glass preview and runtime
restoration. Material tests cover unnamed progress layers and native blend
requests and configuration refreshes before and after attachment. The editor
uses a bundled backdrop, while SystemUI samples the current wallpaper through native blur, so exact
card colors can vary with the wallpaper. Physical-device rendering still
requires validation with this APK; local tests cannot prove the OEM result.
