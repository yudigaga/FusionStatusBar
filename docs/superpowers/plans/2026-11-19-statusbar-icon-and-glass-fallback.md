# Status Bar Icon And Glass Fallback Implementation Plan

> **For agentic workers:** Native execution in the isolated candidate.

**Goal:** Add screenshot-matched visibility controls and glyphs for all 19 status-icon categories, activate the Canvas Highlight Glass compatibility path, and keep icon/material restoration reversible.

**Architecture:** Persist a backward-compatible visibility bitmask in `FusionConfig`, compose it with SystemUI's status-icon block-list flow, and map aggregate/multi-slot categories to their native policy slots. Route Highlight Glass through the existing Canvas surface and reflection layers when private blur is unverified.

**Tech Stack:** Android Java, LibXposed, Android Canvas/Drawable, Gradle Release with R8.

**Spec:** `docs/superpowers/specs/2026-11-19-statusbar-icon-and-glass-fallback-design.md`

## Global Constraints

- Existing settings without the new key use the reference's initial selections.
- Only the 19 reference categories are controlled.
- Do not invoke unverified MiGlass private APIs.
- Do not run tests; build and sign Release with the original certificate.
- Keep the existing `0.5.29 / 243` artifact intact; build this follow-up as `0.5.30 / 244`.
- Keep the existing `0.5.28 / 242` artifact intact; build this follow-up as `0.5.29 / 243`.

## Review Focus

- Unrelated `FusionConfig` edits must preserve the new preference.
- User-selected slots must be blocked by SystemUI's visibility policy, not by directly changing activity state.
- Material wrappers must restore OEM drawables when Highlight Glass is disabled.
- Volume priority must preflight required state fields and roll back state/view mutations after runtime failure.
- Restore the latest host blur scale ratio on fallback and replay configured strength when leaving fallback.

---

### Task 1: Status Bar Icon Visibility

**Files:**
- Modify `app/src/main/java/com/xtjm/fusionstatusbar/FusionConfig.java`
- Modify `app/src/main/java/com/xtjm/fusionstatusbar/RuntimeConfigDiff.java`
- Modify `app/src/main/java/com/xtjm/fusionstatusbar/SettingsPageController.java`
- Modify `app/src/main/java/com/xtjm/fusionstatusbar/MiuixControls.kt`
- Add matching status icon vector resources under `app/src/main/res/drawable/`
- Modify localized `ui_control_strings.xml` files
- Modify `app/src/main/java/com/xtjm/fusionstatusbar/SystemUiHooks.java`
- Modify `app/src/main/java/com/xtjm/fusionstatusbar/StatusBarHookInstaller.java`
- Modify `app/src/main/java/com/xtjm/fusionstatusbar/MainActivity.java`

- [ ] Add a compatible 19-bit visibility mask and persist it with the screenshot-matched initial selection.
- [x] Add the screenshot-aligned entry and grouped toggle sheet.
- [x] Add the 19 matching glyphs to both Miuix and Android View settings paths.
- [ ] Hook SystemUI's block-list source, including roaming flow refresh, and map aggregate/multi-slot categories.

### Task 2: Highlight Glass Compatibility

**Files:**
- Modify `app/src/main/java/com/xtjm/fusionstatusbar/ControlCenterMaterialPolicy.java`
- Modify `app/src/main/java/com/xtjm/fusionstatusbar/ControlCenterMaterials.java`
- Modify `app/src/main/java/com/xtjm/fusionstatusbar/ControlCenterGlassCapabilities.java`
- Modify `app/src/main/java/com/xtjm/fusionstatusbar/RuntimeApplicationPolicy.java`
- Update affected material-policy test expectations without executing tests.

- [x] Preserve the configured Highlight Glass surface and strength when private blur is unknown.
- [x] Activate the existing reversible Canvas surface and edge reflection; keep native background restoration intact.
- [x] Restore blur scale ratio on fallback and replay it when returning to native material.

### Task 3: Signed Release

**Files:**
- Modify `app/build.gradle`
- Add `docs/release-notes-v0.5.29.md`
- Add a new local build manifest under `validation/`

- [x] Build `0.5.29/243` Release with R8 and `lintVitalRelease`; do not run unit tests.
- [x] Align and sign with the existing official certificate; verify APK identity, certificate digest, and alignment.
- [x] Record APK hash and the unperformed test/device checks.

The local signed APK SHA-256 is `9D64D7000C2A627837B37807B68DF5C9DF3DF176FFE61A97639E1C73DBAD1D71`; build and verification details are in `validation/build-output-0.5.29.json`. Unit tests, full lint, device/LSPosed behavior, rendered screenshots, and compositor blur remain unverified.

### Task 4: Respect User-Hidden Slots And Camera Glyph

- [x] Do not restore slots hidden by the 19-bit visibility mask while the fused layout is active.
- [x] Replace the person silhouette in the camera row with a camera/webcam glyph.

### Task 5: Signed Follow-Up

- [x] Build `0.5.30/244` Release with R8 and `lintVitalRelease`; do not run unit tests.
- [x] Sign with the original certificate, verify identity and 16 KiB alignment, and archive the matching R8 mapping.
- [x] Record the APK hash and unperformed test/device checks.

The final local signed APK SHA-256 is `2EEAFAB021E40A293A9BC8D90462FF4DBBE8D5D7A3DC2D1E650B5F9EA5E1DD8E`; build and verification details are in `validation/build-output-0.5.30.json`. Unit tests, full lint, device/LSPosed behavior, rendered screenshots, and compositor blur remain unverified.
