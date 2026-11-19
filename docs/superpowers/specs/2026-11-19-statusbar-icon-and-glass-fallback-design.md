# Status Bar Icon And Glass Fallback

## Intent

Let the user customize visibility for the 19 SystemUI status-icon categories in
the reference image, and make Highlight Glass visibly apply a module-owned
compatibility surface when HyperOS private blur support is unknown.

## Chosen Approach

Add a backward-compatible 19-bit visibility mask and a “图标显示自定义”
settings entry on the Status Bar page. Compose module visibility choices with
SystemUI's existing hidden-slot list and refresh its state flow when settings
change. The initial mask matches the reference: every category enabled except
privacy indicators and alarm. Keep SystemUI responsible for current state and
artwork. `roaming` and `privacy` remain aggregate policy entries; headset and
speaker categories map to their multiple native slots.
Render a matching left-side glyph for each setting row in both the Miuix and
Android View fallback settings paths.
The fused-layout native-slot restoration must not remove slots hidden by this
module's mask.

When Highlight Glass is selected, retain the original drawable for restoration
but use the existing translucent Canvas surface and edge reflection instead of
mapping the style to `DEFAULT`. Do not call unverified MiGlass private APIs.
Restore captured native blur scale ratios when entering the fallback, and replay
the selected strength when returning to a native material path. Make volume
icon recovery transactional so an incompatible SystemUI state object cannot
leave a partially cleared `hiddenBySpace` flag.

## Alternatives

- Changing `setIconVisibility` alone does not cover roaming markers, privacy
  policy, multi-slot device categories, or SystemUI's hidden-slot list.
- Forcing a full native MiGlass ABI is unsupported by the available API evidence.

## Constraints

- Missing saved preferences use the reference's initial toggle selections.
- Only the 19 categories in the reference image are configurable.
- Build Release with the existing official certificate; do not run tests.
