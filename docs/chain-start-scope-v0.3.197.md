# FusionStatusBar 0.3.197

Version `0.3.197`, code `211`.

This is a scope cleanup after the `LSPosed_20261013_183842.zip` diagnosis. The module no longer declares the unused LSPosed `android` scope. The required `system` scope remains, along with the existing SystemUI, plugin, launcher and Security Center scopes.

The chain-start implementation and the configuration broadcast target are unchanged. `android` still appears as a package name in the system-server configuration exchange because that is the actual Android system package; it is not an LSPosed scope selection.

Install the original-signed APK, remove the old `android` selection if it remains visible in LSPosed, keep `system` enabled, and reboot. The Miscellaneous setting stays enabled. The next log should show `system start confirmation hooks version=0.3.197 installed=...`; a later `gate bypass active` line confirms the hook ran.

Debug and Release are rebuilt from this version. Build validation now checks that the packaged scope list exactly matches source and that `system` is present.
