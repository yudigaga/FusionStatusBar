# Home warning display Implementation Plan

> **For agentic workers:** Use inline execution with parallel read-only host analysis and one final independent review. Steps use checkbox syntax for tracking.

**Goal:** Add a reversible, default-off setting for the three Phone Manager home warning displays identified by the user.

**Architecture:** Run original home binding methods, then alter only the verified home views. Keep native display snapshots for restoration and native scan data untouched. Reuse the existing version guard, configuration runtime and multilingual settings loop.

**Tech Stack:** Java, Android Views, libxposed 102, Robolectric, Kotlin Compose settings tests, Gradle Release/R8.

**Spec:** `docs/superpowers/specs/2026-11-13-home-warning-display-design.md`

## Global Constraints

- Exact supported host: `com.miui.securitycenter`, versionCode `40001380`.
- Independent key: `security_center_hide_home_warnings`; defaults off.
- Preserve all native scan/risk data and the existing cosmetic 666 option.
- Seven language resource sets; no added dependencies.
- Release tests/lint/assemble only; archive matching R8 mapping and official signature.
- Preserve existing workspace changes; no commits, remote publication or device installation.

## Review Focus

- Disabling must restore the most recent native UI, including after refreshes while enabled.
- Recycled cards must not transfer warning suppression to unrelated cards.
- Host scan inputs, return values, exceptions and real scores must remain unchanged.
- Missing or changed view fields must leave the native screen usable.
- Dark/light theme colors and host language must follow the current host resources.

### Task 1: Trace and setting

- [x] Record exact host render methods, fields and resource IDs from existing decompilation.
- [x] Write and run failing config/UI tests for default-off, independence, persistence and recreation.
- [x] Add enum metadata and seven translated resource sets; pass focused Release tests.

### Task 2: Home render hooks

- [x] Write and run failing render tests for native execution, normal appearance, restoration, rebind, unrelated cards and failure behavior.
- [x] Add minimal hooks at verified render boundaries and register with SecurityCenterHooks.
- [x] Pass focused Release tests; independently review the connected code and static host contract. 30 tests passed; two independent source/smali reviews found no blocking issue.

### Task 3: Release validation

- [x] Write reverse-engineering report with evidence and runtime limitations.
- [x] Run complete Release tests, lint, language validation, Python tooling tests and R8 assemble. 1608 tests / 209 suites, zero failures/errors/skips; lint 0 errors / 142 warnings, high-risk policy passed; seven language sets each 852 strings; 45 Python tests; R8 8.13.19.
- [x] Sign and archive local 0.5.26 (240), matching mapping, verify signature/alignment/hash and copy APK to workspace root. Official v3 signature and 16 KiB alignment passed; R8 component verification passed for five classes/eight external methods. Final identities are in release.json and analysis/security-home-reminders-20261113/final-verification.json. These completion notes are the only post-archive documentation changes.
- [x] Prepare final handoff with setting location, artifact and device validation boundary. No installation, real LSPosed execution or remote publication performed.
