# Control Center Shape and Active Fill v0.3.159

This release corrects the remaining runtime presentation issues reported on
the HyperOS device:

- Circle placements use centered square bounds even when a grid column and row
  have different physical sizes.
- Native tile and card holders are clipped to the configured slot path, so an
  OEM rectangular background cannot leak outside rounded cards.
- Active circular tiles receive a temporary surface layer above the native
  background and below their icon/content. The native background is restored
  when the tile turns off.
- Active detection accepts the normal, customize, and tile-state holder fields.
