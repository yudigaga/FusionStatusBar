# FusionStatusBar 0.3.196: System-Server Scope Fix

Version `0.3.196`, code `210`.

## Confirmed Failure

The supplied `LSPosed_20261013_183842.zip` contains a live scope database dump. `db_scope.txt` lines 448-450 list `android` for `io.github.yudigaga.fusionstatusbar`, but no `system` entry. The Mi Ke module has separate `system` and `android` entries (lines 343-345 and 393-395).

The logs confirm FusionStatusBar 0.3.195/code 209 in SystemUI. Other modules emit `(system)` and `onSystemServerStarting` diagnostics during boot, but FusionStatusBar has no `(system)` diagnostics and no `system start confirmation` installation, failure, configuration or gate-execution marker. Therefore its system-server callback did not run; these logs do not show an installed-but-ineffective confirmation gate.

The 0.3.195 APK incorrectly declared only `android` for the new framework feature. On the user's LSPosed build, `system` selects system_server independently of the Android application scope. The workspace Mi Ke source declares both entries in `references/customiuizer/app/src/main/resources/META-INF/xposed/scope.list`.

## Fix

Add `system` to the recommended Xposed scopes and retain `android`. The existing MIUI LocalService interceptor and its configuration protocol are unchanged. Broadcasts still target the real package `android`; the LSPosed synthetic scope `system` must not replace that package name.

The Miscellaneous switch description now explicitly names `system / system_server`. Build validation requires `system` in source metadata and verifies that both APK variants package exactly the declared scope set. Regression checks fail against the old `android`-only scope and pass after the correction.

## Upgrade

Install the original-signed APK, open FusionStatusBar's scope selection in LSPosed, and explicitly enable **System** (`system / system_server`). Keep the existing scopes for other features. Recommended scope metadata may not automatically add a new selection to an already enabled module.

After enabling System, reboot and open FusionStatusBar once to publish the saved Miscellaneous setting. Keep Remove chain start popup enabled. The next boot log should contain `system start confirmation hooks version=0.3.196 installed=...` and a configuration line with `enabled=true`. `gate bypass active` establishes that the interceptor ran; successful target launch must still be checked separately.

## Verification Limits

Debug and Release each passed 1116 tests with no failures or errors. All 28 build-tool regression tests passed, including rejection of the old Android-only scope. The rebuilt APK scope metadata is checked directly against the source list.

No connected Android device was available. Local tests, lint, APK metadata, original signing certificate and ZIP alignment are recorded in `artifacts/releases/v0.3.196/`. The supplied failure is explained by absent system-server injection; the target ROM's actual launch after this correction remains unverified.
