# Control Center Global Materials

The global container background selector now includes crystal glass, light, graphite,
ice blue and mint in addition to original, transparent, frosted and dark. Existing
numeric values are unchanged: original=0, transparent=1, frosted=2 and dark=3.
Global border has an independent switch and 1-4 dp width, with a dark stroke on the
light surface and a light stroke on the others. It follows each container outline
and is drawn above the material without changing native backgrounds, foregrounds,
touch targets or slider progress.

Background blur still controls the whole panel. Card and tile blur strengths remain
independent percentages for their respective containers. The selected style supplies
the container color; the strength controls its opacity and native blur radius. The
transparent style disables container blur, so its card and tile sliders are paused
in the editor while retaining their values. Changing styles restores those sliders.
The global border is independent of all three blur strengths.

Group background and border controls are removed. The old fields remain readable in
saved layout versions 1-6 but no longer affect rendering; all group containers now
follow the global background and border. With the original style, groups use the
same default frosted color. Other layout data, including member icon size, remains
unchanged. Previous full backups and presets without the new global border fields
use border-off and 1 dp defaults.

Local tests cover settings changes and undo, serialization, old backups, native
material restoration, independent blur values, blank and light surfaces, global
border lifecycle and visual previews at 360/440 px. The release report and APK are
under artifacts/releases/v0.3.154. Local tests cannot establish HyperOS rendering
or SystemUI injection on a physical device.
