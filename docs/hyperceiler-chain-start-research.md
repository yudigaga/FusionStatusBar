# HyperCeiler Chain Start Popup Research

Investigated on 2026-10-13 against the official [ReChronoRain/HyperCeiler repository](https://github.com/ReChronoRain/HyperCeiler). The `main` head returned by the GitHub API was [`5e4686069dd7ab1f3697e256d5fc7d68fb73e317`](https://github.com/ReChronoRain/HyperCeiler/commit/5e4686069dd7ab1f3697e256d5fc7d68fb73e317), committed on 2026-10-03. All HyperCeiler source links below are pinned to that commit.

This is source research. It does not establish that a hook works on the user's current Security Center build, or that an actual chained launch was allowed on a device.

## Exact Feature Requested

The Chinese title `移除链式启动弹窗` maps to resource `security_center_remove_open_app_confirmation_popup`; its description identifies the `XXX 想要打开 XXX` popup. A separate title, `绕过链式启动检测器`, describes allowing **all** chained start requests. These are different settings and implementations, not alternative names for one feature. [Chinese titles and descriptions](https://github.com/ReChronoRain/HyperCeiler/blob/5e4686069dd7ab1f3697e256d5fc7d68fb73e317/library/core/src/main/res/values-zh-rCN/strings_app.xml#L499-L500), [popup title](https://github.com/ReChronoRain/HyperCeiler/blob/5e4686069dd7ab1f3697e256d5fc7d68fb73e317/library/core/src/main/res/values-zh-rCN/strings_app.xml#L1538-L1539).

| Feature | Target process | Upstream rule | Behavior |
| --- | --- | --- | --- |
| Remove chain start popup | `com.miui.securitycenter` | `RemoveOpenAppConfirmationPopup` | Invoke the accept button's existing click action after its label is assigned. |
| Bypass wake path checker | `system_server` | `BypassWakePathChecker` | Skip two framework policy checks and force an allow result. |

The popup rule is registered by `SecurityCenter`, whose target package is exactly `com.miui.securitycenter`. It is enabled by `security_center_remove_open_app_confirmation_popup`, with an XML default of `false`. [Scope annotation](https://github.com/ReChronoRain/HyperCeiler/blob/5e4686069dd7ab1f3697e256d5fc7d68fb73e317/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/app/SecurityCenter.java#L79-L80), [rule registration](https://github.com/ReChronoRain/HyperCeiler/blob/5e4686069dd7ab1f3697e256d5fc7d68fb73e317/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/app/SecurityCenter.java#L136-L137), [preference](https://github.com/ReChronoRain/HyperCeiler/blob/5e4686069dd7ab1f3697e256d5fc7d68fb73e317/library/core/src/main/res/xml/security_center_other.xml#L143-L147).

## Popup Hook Point And Semantics

The exact upstream hook point is the Android framework method:

```java
android.widget.TextView.setText(java.lang.CharSequence) : void
```

It is an **after** hook. The original setter executes normally. The hook resolves the string resource `button_text_accept` from the `TextView` context's package, compares that localized string with the setter's first argument, and calls `TextView.performClick()` on a match. It does not replace a boolean policy method, set a return result, hook a Xiaomi Activity by class name, or simply hide a window. [Complete upstream rule](https://github.com/ReChronoRain/HyperCeiler/blob/5e4686069dd7ab1f3697e256d5fc7d68fb73e317/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/rules/securitycenter/RemoveOpenAppConfirmationPopup.kt#L28-L49).

The desired semantic is automatic **acceptance**, through the existing button listener. AOSP's `performClick()` invokes the registered `OnClickListener` and returns `true` when a listener exists; it returns `false` if none exists. Therefore an upstream hook callback executing is not proof that a chained launch proceeded: the listener could be absent when `setText` runs, or the host listener could apply additional logic. HyperCeiler ignores this return value. [AOSP View click behavior, Android 15](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/view/View.java#L8019-L8038), [AOSP setter signature](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/widget/TextView.java#L7219-L7221).

The upstream rule is broad within its process: it does not verify the view ID, dialog owner, Activity class, attachment state, visibility, enabled state, or an installed click listener. It also directly calls `getString()` on the result of `getIdentifier()` without handling a missing resource. Those statements describe the source, not successful or failed device behavior. [Rule body](https://github.com/ReChronoRain/HyperCeiler/blob/5e4686069dd7ab1f3697e256d5fc7d68fb73e317/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/rules/securitycenter/RemoveOpenAppConfirmationPopup.kt#L31-L45).

## Version And Scope Boundaries

The popup rule and its registration contain no per-feature Android SDK, HyperOS, MIUI, or Security Center version branch. The package registration has only the package constraint, and the hook targets a stable framework setter with a localized string marker. This provides a compatibility strategy, not a verified ROM compatibility matrix. The feature requires `button_text_accept` to exist and the corresponding button to receive text through this overload. [Registration](https://github.com/ReChronoRain/HyperCeiler/blob/5e4686069dd7ab1f3697e256d5fc7d68fb73e317/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/app/SecurityCenter.java#L79-L137), [rule](https://github.com/ReChronoRain/HyperCeiler/blob/5e4686069dd7ab1f3697e256d5fc7d68fb73e317/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/rules/securitycenter/RemoveOpenAppConfirmationPopup.kt#L28-L49).

The source history shows the hook was migrated to EzHookTool and LibXposed API 102 on 2026-07-19, with the same text-match-and-click behavior. The January API 100 implementation also used `TextView.setText(CharSequence)` after interception; its different hook DSL is not a different ROM hook point. [API 102 migration](https://github.com/ReChronoRain/HyperCeiler/commit/fe998bf97c45a91d5aae72fd5f815eb3fcbe5397), [earlier popup rule](https://github.com/ReChronoRain/HyperCeiler/blob/ab88528abf965fd7415a5e34a050140c10e305de/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/rules/securitycenter/RemoveOpenAppConfirmationPopup.kt#L28-L52).

For FusionStatusBar's requested popup option, `com.miui.securitycenter` is the relevant Xposed scope. Adding an `android`/system-framework scope is not required for this exact upstream feature.

## Separate Framework Bypass

`BypassWakePathChecker` targets `com.miui.server.WakePathChecker` and forces `true` before the original methods run:

```java
isAllowedByWakePathRule(String, String, String, String, int, int, int, int)
checkAllowStartActivity(String, String, int, int, android.content.Intent)
```

Both methods receive an explicit `true` result in the upstream code. This is an allow-policy bypass; its scope is wider than accepting one popup. The source does not expose the proprietary method declarations, so the upstream expected boolean return should still be verified reflectively before applying this different approach. [Framework hook](https://github.com/ReChronoRain/HyperCeiler/blob/5e4686069dd7ab1f3697e256d5fc7d68fb73e317/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/rules/systemframework/others/BypassWakePathChecker.java#L41-L83).

The framework feature is registered for SDK <=35 and SDK >=36 through two loader classes; both use the same `BypassWakePathChecker`. The label `system` is HyperCeiler's internal system-server target, constructed from `SystemServerStartingParam`, rather than an ordinary app package. [SDK <=35 loader](https://github.com/ReChronoRain/HyperCeiler/blob/5e4686069dd7ab1f3697e256d5fc7d68fb73e317/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/app/SystemFramework/SystemFrameworkV.java#L93-L163), [SDK >=36 loader](https://github.com/ReChronoRain/HyperCeiler/blob/5e4686069dd7ab1f3697e256d5fc7d68fb73e317/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/app/SystemFramework/SystemFrameworkB.java#L91-L171), [system-server parameter mapping](https://github.com/ReChronoRain/HyperCeiler/blob/5e4686069dd7ab1f3697e256d5fc7d68fb73e317/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/base/PackageTarget.java#L66-L69), [internal target constant](https://github.com/ReChronoRain/HyperCeiler/blob/5e4686069dd7ab1f3697e256d5fc7d68fb73e317/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/base/BaseLoad.java#L52).

## FusionStatusBar Adaptation

Implement the popup behavior independently with this project's LibXposed API 102 interceptor and existing `com.miui.securitycenter` package-ready dispatch. Preserve the setter's original result and make the interception conditional on an opt-in miscellaneous setting. The following guards address risks apparent in the upstream implementation:

1. Require the exact Security Center context package and a nonzero, resolvable `button_text_accept` string resource; missing OEM markers must leave native behavior intact.
2. Match the localized label, then require an actual clickable, enabled confirmation control. Matching a string on an arbitrary informational `TextView` is insufficient.
3. If the button is still being built, wait until it is attached and its listener is ready. Keep retries bounded, suppress duplicate work per view, and recheck the setting, label, and view state before clicking.
4. Restrict acceptance to a known chain-start dialog owner if the host can identify it reliably. HyperCeiler supplies no proprietary Activity class or ID for this feature, so any additional owner marker must be independently supported by runtime evidence.
5. Keep native behavior for unknown owners and missing resources, and protect host exceptions using the repo's existing hook exception policy.

These are adaptation recommendations, not claims that HyperCeiler already performs those checks. Any hook that only dismisses the dialog or changes `setText`'s return does not preserve automatic acceptance. Any implementation that instead globally forces the framework methods to return `true` changes the requested feature's scope.

Tests should verify opt-out behavior, localized resource matching, missing-marker fallback, rejection of unrelated views, one acceptance per popup, and cancellation before a queued click. Device verification must then trigger a real `XXX wants to open XXX` chain-start popup, confirm that the destination opens, and verify that disabling the option restores the confirmation. A build or hook installation alone is insufficient evidence.

## License

HyperCeiler's current root license and the popup hook's header are **AGPL version 3**, not a permissive license and not merely GPL-3.0. The separate `MIT_License.md` records third-party attribution and does not override the popup file's AGPL header. [Root license](https://github.com/ReChronoRain/HyperCeiler/blob/5e4686069dd7ab1f3697e256d5fc7d68fb73e317/LICENSE), [popup header](https://github.com/ReChronoRain/HyperCeiler/blob/5e4686069dd7ab1f3697e256d5fc7d68fb73e317/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/rules/securitycenter/RemoveOpenAppConfirmationPopup.kt#L1-L18), [third-party MIT notice](https://github.com/ReChronoRain/HyperCeiler/blob/5e4686069dd7ab1f3697e256d5fc7d68fb73e317/MIT_License.md).

This research records interface names, observed behavior, and source links; it does not import HyperCeiler code or dependencies. Copying or translating its implementation requires a deliberate review of AGPL obligations and the receiving project's license. The local FusionStatusBar checkout has third-party licenses but no project-wide license declaration, so direct-copy compatibility cannot be asserted from the checkout. An independently written implementation using the documented hook facts avoids importing the upstream implementation; this note is not a legal determination of derivative-work status.
