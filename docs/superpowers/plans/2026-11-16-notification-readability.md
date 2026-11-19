# Fusion notification readability implementation plan

> For agentic workers: use executing-plans with test-driven-development. The supplied delegation is the authorized design and execution scope; no commits or automatic integration.

**Goal:** Preserve notification content semantics, make uncertain blur reversible, and reduce measured redundant notification geometry work.

**Architecture:** Keep notification content entirely host-owned. Keep existing Canvas soft edges and bounded background drawing. Use native background fallback for automatic notification glass when OEM blur cannot be established; do not infer private blur from public window capability. Route geometry changes through immediate invalidation only after measuring their current cost.

**Tech stack:** Android Java/Kotlin, Xposed, Robolectric, Gradle Release/R8, Python tools.

**Spec:** The creating chat's 2026-11-16 Fusion delegation, reproduced in `../../2026-11-16-notification-readability.md` with implementation results and acceptance boundaries.

**Authority update:** The user subsequently assigned ALL tests, lint, compilation and builds to 小智's GPT-6 Astra ultra. Local work is limited to static analysis, candidate edits, test source and static review. No local TDD execution or build is allowed; all run/measure steps below belong to that external executor. The earlier a-red failed before business tests at Gradle connection; a-red-unsandboxed remains unapproved. Do not execute, approve, dismiss or replace it.

## Global constraints

- Source is 0.5.27 / 241 at fe7bb9d230781985779e11e7151fc509acffd59f plus all relevant uncommitted source. Reference 000ef04a is comparison only.
- Edit only this independent work copy; source directory, git metadata, models, devices and cloud machines stay untouched. No commits, pushes, releases, upload or automatic migration.
- Preserve CLEAR, strength 0, manual soft glass, independent notification/heads-up switches, soft inward edge, bounded internal reflection, native alpha, theme restore, shader caches and Grid dirty logic.
- Unknown custom content stays untouched. No global neutralForeground change or blanket recoloring.
- All verification is local; device visual and Perfetto acceptance remain pending. No new permissions or dependencies.

## Review focus

1. Stateful disabled/pressed colors and app RemoteViews/media icons must retain host identity, colors and actions.
2. Host rebind and theme changes including same-uiMode updates must not restore stale text values.
3. Missing, failed or merely requested OEM blur must not be reported as proven availability.
4. Bounds fast path must preserve actual height, RTL width, expand clip, native alpha and heads-up transitions immediately.
5. Turning off after theme/reuse must restore the latest host drawable without self-reference.

## Task A: notification content ownership

Files: NotificationSurfaceHooks.java; NotificationContentSemanticsTest.java; ControlCenterAdaptiveGlassConsumersTest.java.

- [x] Add real applyRow test source for native body/emphasis/stateful colors, RemoteViews/media content, rebind/theme/disable. Not compiled or run.
- [ ] Run `:app:testReleaseUnitTest --tests '*NotificationContentSemanticsTest'` and save actual RED results.
- [x] Remove notification use of generic applyContent; keep menu/control-center content styling contract explicit.
- [ ] Run tests again and related notification regressions; require zero failures for GREEN.

## Task B: reversible notification glass fallback

Files: SystemSurfaceStyle.java; focused notification blur regression tests; NotificationSurfaceHooks.java only if reporting requires it.

Interface: NotificationSurfaceHooks.applyRow owns background policy, SystemSurfaceStyle owns reversible material/native requests. Geometry callers must not trigger a rebind.

- [x] Statically characterize native background and OEM request behavior; add fallback/lifecycle test source. No RED/GREEN execution.
- [x] Implement a notification-only static candidate: unknown uses the full host background at positive strengths, preserving CLEAR/0/manual modes. No compositor availability inferred from a setter.
- [ ] Separate backend installation, request, availability and fallback diagnostics where evidence exists. Record any backend or menu behavior left pending.
- [ ] Verify RED/GREEN, restore, theme, strength, bounds, native request and shader regressions.

## Task C: measured geometry work

Files: NotificationSurfaceHooks.java; NotificationGeometryDispatchTest.java.

- [ ] Count complete applyRow work, child enumeration, background assignments and OEM setter replay through real hook callbacks in local fixtures before edits.
- [ ] Add failing expectations for repeated geometry callbacks; keep pixels immediate.
- [x] Route only identified geometry methods to bounds invalidation as an unmeasured candidate. Escalate for missing/replaced ownership or semantic hooks. Preserve dynamic heads-up/config updates.
- [ ] Compare counts; no claims about actual ROM jank without Release/Perfetto samples.

## Validation and delivery

- [ ] Serialize heavy work after process inspection. Run fresh Release unit tests, related full regression, Release lint, R8 Release assembly, Python tool tests as feasible; record commands, exit codes, executed vs cached and limitations.
- [ ] Provide 48-cell synthetic background/visual checks with explicit local-vs-device boundary, plus all additional device scenarios in the report.
- [ ] Review final diff independently, verify source remains byte-identical, export patch from baseline/work and per-file hashes, and document guarded manual migration. Do not integrate.

## Execution ledger

- Pre-flight: A and C share NotificationSurfaceHooks; edit sequentially. B owns SystemSurfaceStyle; coordinate its notification policy with A before C verification. No git-based skill scripts because the authorized isolation deliberately has no repository metadata and no commits are allowed.
- Static review found external-setBackground-before-disable and internal-drawable-becomes-null edges. Candidate and test source now cover both. The private domain remains UNKNOWN; public-window listeners and global control-center fallback are deferred.
- No tests/builds run after the authority update. No local completion or performance claims are permitted. Freeze includes source hashes and review artifacts only; await external results.
