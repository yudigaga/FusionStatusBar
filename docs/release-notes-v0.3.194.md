# FusionStatusBar 0.3.194

Based on the local v0.3.193 release source, commit `aa8c639`. Version code: `208`.

## Changes

- Add a Miscellaneous tab before Settings. Its opt-in Remove chain start popup setting automatically accepts the native Security Center confirmation button.
- Add a floating frosted navigation bar with live page-background blur, a rounded outline and blue selected icons. Settings contains a persistent appearance switch; the floating style is enabled by default and disabling it restores the regular bar.
- Reserve the measured navigation height in both the desktop Compose list and the other pages. All six destinations remain available at narrow widths and larger font sizes.

## Hook And Configuration

The requested HyperCeiler feature is the Security Center popup rule, scoped to `com.miui.securitycenter`. It observes `TextView.setText(CharSequence)` after the native setter and identifies the localized `button_text_accept` label. Acceptance invokes the original click listener. The implementation is independently written for LibXposed API 102, preserves the native setter, and leaves other system startup policy checks intact. See [source research and exact upstream references](hyperceiler-chain-start-research.md).

The existing four LSPosed scopes remain sufficient. Enable Security Center and restart that process or reboot after installing the new module. The miscellaneous setting defaults to off. The appearance setting takes effect immediately inside the app. Miscellaneous configuration is stored atomically in device-protected storage and exposed only to the module and Security Center; only the module can publish changes.

Miscellaneous configuration is separate from the existing status bar/control center backup format. Security Center keeps its last successfully read setting in device-protected storage and reads current changes through the module Provider. Keep the module app enabled when updating this setting; an unavailable Provider retains the last known value. The existing SystemUI/launcher configuration exchange remains unchanged.

## Validation

Debug and Release each passed 1116 tests with no failures, errors or skips. Both lint variants reported zero errors and 94 warnings, and the repository's high-risk policy passed. The 25 build-tool regression tests also passed. OEM resource reflection and programmatically constructed capture views account for new advisory warnings; they do not widen the accepted high-risk policy.

Rendering checks cover six destinations at 320dp and 1.5 font scale in both themes, persistent appearance switching, background boundary pixel mixing, unblurred foreground controls, and unavailable-capture fallback. Local compilation, tests, lint and signed APK verification are recorded in `artifacts/releases/v0.3.194/release.json` and `validation.md`. Robolectric checks cannot establish compatibility with a specific proprietary Security Center version.

No device installation or real chained-launch acceptance has been performed. Device verification must confirm that an application requesting another application opens the intended destination with the setting enabled and restores the native confirmation when disabled.
