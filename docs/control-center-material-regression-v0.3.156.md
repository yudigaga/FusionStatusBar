# Control Center Material Regression Fix

Version 0.3.154 added global drawable recoloring, recursive material registration
and global borders. The 0.3.155 soft-glass color was a light translucent tint,
so the current wallpaper and lower blur strengths could wash the cards toward
gray. Runtime border views were also sibling children of OEM tiles; their draw
order could fall below elevated MIUI holders. Slider progress views were skipped
during tree discovery, but later drawable and native-blur hooks could still tint
them.

Soft glass now uses a dark cool tint and keeps its surface opacity separate from
background, card and tile blur strengths. Those strengths still control panel and
native backdrop blur. The reflection remains limited to a brighter outline.
Slider progress subtrees keep OEM colors and fill while their parent tracks retain
the global glass material. Runtime tile borders draw above the highest current
native tile Z value, including on pairs, without replacing native tile backgrounds.

The 0.3.153 material code adjusted blur and background alpha but did not install
the global border layer or recursively tint every drawable surface. The report
and captures from 0.3.155 confirmed the later border/material regression on an
Android 16 tablet. HyperLight's public repository currently contains its README
files and icon only. Its README describes configurable high-gloss material and
small/medium/large outline settings, but there is no renderer source in that
repository to reuse.

Local regression coverage checks low-blur dark glass, readable slider fills,
late progress drawable updates, nested icon preservation, continuous borders,
elevated OEM holders, pair coverage and touch passthrough. Release validation
records local test and lint results; the connected-device screenshot check is
reported separately because HyperOS rendering remains device-specific.
