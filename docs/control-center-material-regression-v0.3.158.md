# Control Center Material Regression v0.3.158

This release fixes the HyperOS material regression introduced after 0.3.153.

- Recognized panel roots now process unlabelled full-size `GradientDrawable`
  layers used by `QSCardItemView#big_tile`.
- Slider progress and preserved icon layers remain excluded from that fallback.
- Registered panel backgrounds suppress native color filters and tints while a
  global surface is active, then restore the latest native values when the
  surface is disabled.
- The abstract `Drawable#setColorFilter` hook is not installed; concrete
  drawable implementations are hooked instead.
- Temporary `[DEBUG-glass]` instrumentation was removed before release.

Validation includes focused material, border, soft-glass, runtime-grid, and
landscape tests, followed by a HyperOS device restart and control-center hook
inspection on `M2105K81AC`.
