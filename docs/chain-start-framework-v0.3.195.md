# FusionStatusBar 0.3.195: Framework Startup Confirmation

Version `0.3.195`, code `209`, based on the local 0.3.194 changes.

Correction from the next device log: this version omitted the separate LSPosed `system` scope, so selecting only `android` did not load the system-server interceptor. Use [0.3.196 scope correction and upgrade instructions](chain-start-scope-v0.3.196.md). The historical scope instructions below were incomplete for the user's LSPosed build.

## Final Behavior

The Miscellaneous setting now installs a system-server interceptor for primitive-boolean overloads of `com.miui.server.SecurityManagerService$LocalService.checkAllowStartActivity`. With the setting enabled, the interceptor returns `true` without calling the native confirmation gate. With it disabled, the original method executes and its result is preserved.

This directly allows inter-app activity starts through that gate. It does not click either the one-time or permanent-allow buttons. The Security Center popup interception has been removed entirely. The existing Security Center sidebar feature remains separate.

## Primary Source

The workspace's Mi Ke checkout (`references/customiuizer`, commit `dfd3a4de5201d058357c692250e35b1cb74edfb6`) registers `RemoveActStartConfirmHook` in `onSystemServerStarting` and targets this exact local-service method. Sources:

- [Mi Ke framework hook](https://github.com/MonwF/customiuizer/blob/dfd3a4de5201d058357c692250e35b1cb74edfb6/app/src/main/java/name/monwf/customiuizer/mods/System.java#L2707).
- [System-server registration](https://github.com/MonwF/customiuizer/blob/dfd3a4de5201d058357c692250e35b1cb74edfb6/app/src/main/java/name/monwf/customiuizer/MainModule.java#L119).
- [GPL-3.0 license](https://github.com/MonwF/customiuizer/blob/dfd3a4de5201d058357c692250e35b1cb74edfb6/LICENSE).

The adaptation is independently implemented using interface names and behavior established by that source; no upstream implementation or dependency is imported. Unknown return types and unavailable classes leave the native behavior intact.

## Supplied Failure Evidence

`LSPosed_20261013_172029.zip` records module version 0.3.194/code 208 and four popup hook installations in Security Center at 17:19:14, preceding the supplied screenshot. Device properties identify Android 16/API 36 and HyperOS 3 (`OS3.0.318FinalZ`). The screenshot still shows the app-open confirmation, so injection cannot be treated as successful suppression. The log archive does not establish whether the old button selector or configuration reader executed.

## Configuration And Upgrade

Enable the module for LSPosed's **System Framework** (`android`) scope and reboot after installing 0.3.195. The existing Miscellaneous switch and its saved value are preserved. The APK now recommends five scopes; the other four retain their existing responsibilities.

The settings app owns its device-protected miscellaneous snapshot. System Framework may read that snapshot through the authenticated Provider; only the module may write it. Explicit broadcasts target `android`, and the system-server receiver requires the module's signature permission. Startup reads use a background worker, retry while system services initialize, and fall back to the system-owned atomic file `/data/system/fusion_statusbar_misc_v1.json` when the Provider is unavailable. Activity-start interception uses the in-memory setting and does not perform Binder calls.

Opening settings or saving the switch publishes its current value. New LSPosed diagnostics distinguish hook installation, cache/Provider/broadcast configuration loading, and execution of the enabled startup gate.

## Validation

Tests exercise native allow/deny preservation, bypass without executing the native gate, rejection of unrelated methods or incompatible return types, configuration persistence and disabling, malformed/future/wrong-destination updates, Provider caller boundaries, and module-only publication. The generated archive contains Debug/Release test and lint results, APK identity/signature checks and the exact source snapshot.

Debug and Release each passed 1116 tests with zero failures, errors or skips. Both lint variants reported zero errors and 97 warnings, with the high-risk policy passed. New framework-reflection warnings are expected for system-server context capture; each unsupported reflection path is protected and the OEM service constructor provides an additional context source. APK metadata was checked to contain the new `android` scope. The removed Security Center popup class is absent from both Java compilation outputs.

No connected device was available during this fix. No device installation, framework injection or actual SearchEVO-to-target launch has been validated for 0.3.195. Confirm suppression by repeating the user's original inter-app start after enabling System Framework and rebooting.
