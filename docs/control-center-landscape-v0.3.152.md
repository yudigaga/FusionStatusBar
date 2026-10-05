# Control Center Landscape Customization

The previous landscape fix retained the OEM two-panel containers but skipped the saved
custom grid. Wide Bluetooth cards, grouped tiles and custom placements therefore returned
to the native arrangement after rotation.

Landscape now projects the selected saved canvas into both OEM adapters. Native network
cards and right-hand components stay in the right panel; list tiles and mixed groups stay
in the left panel. Explicit component-side settings take precedence. Pairs and groups
remain atomic, with their member positions, labels, appearance and dimensions retained.
Only entirely empty rows introduced by the split are removed. The published canvas is
never rewritten, so portrait uses its original coordinates after rotation.

Both adapters resolve against live inventories from both native panel owners, even when
one adapter already contains a proxy. Proxies replace handled native tiles and components
while leaving header, footer and security items intact. An intentionally empty side does
not fall back to the native tiles. Releasing an old component binding cannot clear a
holder already rebound in the other panel. Landscape captures expose the full saved
canvas to the editor instead of importing only the first panel.

Validation covers the screenshot-shaped layout, both adapter application orders,
cross-panel groups, explicit component placement, hidden items, row compaction,
portrait/landscape transitions and disabling customization. Local unit tests and lint
are recorded in artifacts/validation/landscape-final/v0.3.152. No device installation, SystemUI injection
or HyperOS visual acceptance is claimed by the local checks.
