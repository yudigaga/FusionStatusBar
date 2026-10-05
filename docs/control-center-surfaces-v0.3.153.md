# Member Icon Size and Global Container Backgrounds

Group member properties now contain an independent 25%-200% icon size slider and numeric
input. Scaling targets the nested glyph when available, preserving the background circle,
member slot, label space and click target. Native and preview rendering share the setting.
Arrangement changes and member mutations keep the scale attached to its tile specification.
Layout format 6 stores it; formats 1-5 remain readable, with old group members at 100%.

The Control Center appearance dialog now has a global container background selector:
original, transparent, frosted and dark. It applies to regular tiles, network/media/device
cards, slider tracks and group containers in both orientations. Original restores local
group appearance and the latest native colors. Slider fills, icon artwork and animation
alpha remain separate from container materials. Dark neutral labels adapt for contrast.
Frosted requests the existing native HyperOS backdrop APIs when available.

The setting lives in the editor draft until pushed. Undo, publication, persistence, full
backup and presets retain it. Complete old backups without the new optional field default
to original; missing unrelated fields remain invalid. Existing blur strengths stay active.

Local tests cover icon pixel growth, stable member geometry, bounds and labels, v5 migration,
invalid scale rejection, configuration copies and persistence, native color/theme updates,
repeated material restoration, slider fills, preview pixels, undo and old/new backups.
Preview images at 360/440 pixels are generated under app/build/reports/surface-previews.
The release gate records test/lint/signature/version checks in artifacts/releases/v0.3.153.
Device installation and SystemUI/HyperOS acceptance are not performed by this local gate.
