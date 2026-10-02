# Wallpaper workspace: device log follow-up

Source: `../LSPosed_20260930_215331.zip`. Times below are device log times
on 2026-09-30. Line numbers refer to the named ZIP member, not an extracted
or filtered copy. Only relevant diagnostic fields are retained here.

## Entry sequence

All entries in this table are from `full.log`.

| Time | Line | Observation |
| --- | --- | --- |
| 21:53:12.194 | 199713 | App PID 22866: `MainActivity onResume took 190ms`. This precedes workspace entry. |
| 21:53:14.488 | 216862 | App receives the touch-up preceding workspace setup. |
| 21:53:14.501 | 216868 | `WallpaperWorkspace phase=setup elapsedMs=7 format=-3 showWallpaper=true contentChildren=1 size=0x0`. |
| 21:53:14.507 | 216875 | `phase=pre_draw elapsedMs=14`, same flags, size `1440x3200`. |
| 21:53:14.512 | 216892 | WindowManager finds MainActivity as the wallpaper target. |
| 21:53:14.514 | 216898 | Snowmountain wallpaper engine receives `onVisibilityChanged, visible: true`. |
| 21:53:14.515 | 216901 | Wallpaper player reports `surfaceCreated: surface is valid`. |
| 21:53:14.519 | 216903-216905 | WindowManager shows the wallpaper surface and records wallpaper visibility. |
| 21:53:14.538 | 216910 | Wallpaper producer records `disconnect: api 1`; this alone does not establish a rendering failure. |
| 21:53:16.212 | 217187 | App RenderInspector: `QueueBuffer time out`, count 1, average/max 9ms. Not a measurement of 1.7 seconds blocked. |
| 21:53:19.520 | 217927-217929 | Wallpaper engine logs `MSG_Filament_PAUSE` and player pause. No evidence here that this is abnormal rather than animation lifecycle. |
| 21:53:23.299 | 218760 | Workspace back callback is unregistered during close. |
| 21:53:23.322 | 218789 | Wallpaper engine receives `visible: false`. |

The 14ms measurement is cumulative from `open()`, not 14ms plus 7ms.
`pre_draw` is before drawing/submission and cannot measure first presented
frame, wallpaper pixels, fade duration, or GPU composition latency.
`format=-3` is `PixelFormat.TRANSLUCENT`; one content child confirms the
new workspace container arrangement, not the absence of compositor residue.
Only one setup/pre-draw pair is retained, so this is not a performance
distribution or a comparison against a measured v0.3.118 baseline.

## Version reporting defect

The new workspace probes establish that the instrumented app code ran.
However, the SystemUI module log still prints `0.3.118 / 132` at 21:52:59.429
(`log/modules_2026-09-30T14:20:21.582119.log`, line 11740).

Local verification found an actual packaging inconsistency in the delivered
v0.3.119 artifact: Gradle/output metadata uses `0.3.119 / 133`, but
`FusionModule.MODULE_VERSION` and `META-INF/xposed/module.prop` still use
`0.3.118 / 132`. The latter was also read directly from the built APK.
Therefore the old printed version does NOT prove an outdated installation
or that SystemUI failed to restart. The precise loaded hook binary cannot
be distinguished using that version string alone. Version sources must be
synchronized before the next release; this analysis does not rebuild or
replace the APK already under test.

## Other performance evidence

SystemUI PID 8503 reports skipped frames at 21:53:03.785 (100),
21:53:09.627 (706), and several later points before entry. The 706-frame
record is at `full.log:151430`; it is NOT from the app PID 22866.

Between 21:53:00.029 and 21:53:31.661, SystemUI logs 2,070
`ResourcesManager: failed to preload asset path` headers. They name its
fabricated color overlay resources (`neutral`, `accent`, `dynamic`, etc.)
and accompanying idmap loading failures. Of these headers, 2,058 precede
workspace entry, none occur while the workspace is open, and 12 follow
close. The first sampled stack starts in
framework application resource loading, not the wallpaper editor. This is
a separate system resource issue to investigate, not proof of a module
conflict or an explanation for black wallpaper. Do not delete system
resource-cache files on the basis of these logs.

No `FATAL EXCEPTION` or `ANR in` markers were found in retained `full.log`.
No skipped-frame entry was found for app PID 22866. Absence of such records
does not establish smooth rendering or exclude failures outside retention.

## Next observation needed

Confirm the actual result of this run: wallpaper visibility, residual
settings-page content, and whether the slow reveal remains. A short screen
recording aligned to entry is sufficient; the current log does not contain
the final pixels.

If slow reveal or black wallpaper persists, compare the same installed
build with a temporary static system wallpaper, after SystemUI has settled.
Change only the wallpaper for this comparison, then restore the original.
This separates a live-wallpaper-specific interaction from general window
composition without assuming that either is already the cause. Capture
frame timing or a system trace if slow presentation persists with both.

No rendering/state-management code was changed for this log analysis.
