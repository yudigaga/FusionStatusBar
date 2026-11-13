# 手机管家“正在体检”显示补充报告

> 以下为原始本地分析与实现记录，所述授权、路径和验证范围对应当时任务。`analysis/` 下的样本、脚本和日志未随远端仓库发布；本次正式交付及验收范围见 [0.5.27 更新说明](release-notes-v0.5.27.md)。

用户在 0.5.26 交付后补充“正在体检也要屏蔽”。0.5.27 / 241 沿用“杂项 → 扫描与确认 → 隐藏首页风险提醒”，覆盖顶部和下方两个位置的体检提示。关闭时恢复原生显示，体检方法仍继续执行。

## 范围

普通 APK 逆向，flavor=null。授权和离线范围见工作区 `analysis/security-home-scanning-20261113/scope.md`。宿主为 `com.miui.securitycenter` 13.8.0-261020.0.1 / 40001380，APK SHA256 为 `e53a09e38772327e059f74cdaf02e8b3674f9f001e3eea29f10f77fda25448c6`。不扩大到 Lite/Pad/Fold，不操作设备或发布仓库。

## Evidence → Finding → Path

证据根为 `analysis/security-center-13.8.0-20261109/apktool`，来自已有 JADX/apktool 产物。执行工作区命令 `py analysis/security-home-scanning-20261113/verify-host-contract.py` 可重现声明、资源及样本 SHA256 校验；文件哈希和行号保存在同目录 host-contract.json。

| E-id | source_ref | repro_command | content_hash |
|---|---|---|---|
| E-01 | res/values/public.xml:30197；res/values-zh-rCN/strings.xml:3437 | 上述 verify-host-contract.py | n/a；解析结果记录在 host-contract.json |
| E-02 | smali_classes3/com/miui/securityscan/MainFragment.smali:1460–1466、3425–3550 | 上述 verify-host-contract.py | 见 host-contract.json |
| E-03 | smali_classes3/com/miui/securityscan/ui/main/MainContentFrame.smali:1461、1510、1637–1687 | 上述 verify-host-contract.py | 见 host-contract.json |
| E-04 | analysis/security-home-scanning-20261113/scanning-red.log 与 release-build.log | 下节 Gradle 命令 | n/a；测试可重跑 |

F-01：severity=n/a_re，evidence_ids=E-01/E-02，confidence=high，location=MainFragment.K2/f3，status=静态已核实。`hints_scanning_text` 是 0x7f120e06，简中为“正在体检…”。该值在增量扫描写顶部，在完整扫描同时写顶部和下方。

F-02：severity=n/a_re，evidence_ids=E-03/E-04，confidence=high，location=MainContentFrame.f，status=静态已核实、本地回归验证。底部 f 对应 status_bar_bottom（0x7f0b0cc6），会被 setStatusBottomVisible 与 t() 的显入动画重新显示。三个入口均需在原方法后应用过滤。

P-01（path_type=callflow）：MainFragment.K2/f3 → setStatusTopText / setStatusBottomText → MainContentFrame.e/f。顶部追加体检资源过滤；底部仅匹配体检资源，不使用顶部危险/风险过滤集合，实际结果文案继续显示。

P-02（path_type=callflow）：MainFragment.Q1 → MainContentFrame.t → gk.w.e → f.setVisibility(VISIBLE) → 本模块重新隐藏体检文字。原生 GONE 得以保留；配置关闭恢复模块自身造成的 INVISIBLE。既有 MainFragment 去优化包含这些调用方。

原生扫描仍由 f3 调用 scanner.m.n（增量）或 scanner.k.v（完整）；本次没有对这些方法或数据施加 Hook。

## 验证

新增回归先在旧实现上执行：9 项中 1 项正确失败，定位到配置过滤未包含体检资源。测试覆盖中文/英文宿主文字、原方法参数/返回/执行、底部文字/显隐/动画、关闭恢复、GONE 和实际结果继续显示。七种语言的设置说明同步更新。

从模块项目目录执行本轮相关 Release 验证：

```powershell
.\gradlew.bat :app:testReleaseUnitTest --tests '*SecurityCenterHomeHooksTest' --tests '*SecurityCenterConfigTest' --tests '*SecurityCenterPrivacyHooksTest' --tests '*MiscSettingsUiTest' --tests '*AppLanguageResourcesTest' :app:lintRelease :app:assembleRelease -I E:\vs\claude\xtjm\analysis\security-center-13.8.0-20261109\low-memory.init.gradle '-Dorg.gradle.jvmargs=-Xmx1024m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process' --no-daemon --max-workers=1 --console=plain
```

本次为窄范围显示扩展，执行相关测试、完整 lint 和 R8 Release 构建；不将 0.5.26 已通过的 1608 项全量结果当作本版本复跑。当前测试计数、lint、正式证书签名、mapping 和 APK 哈希以 `artifacts/releases/v0.5.27/release.json` 为最终记录。

时间线：2026-11-13 用户补充需求 → 核对两个显示位置与显入动画 → 新回归 RED → 最小显示补点与七语言说明 → 相关 GREEN、独立审查、lint/R8 和本地签名校验。

## 限制与验收

未重复全量测试，未实机验证 LSPosed 安装、ART 去优化或实际动画。安装新包后保持手机管家作用域开启，启用原“隐藏首页风险提醒”并重启手机管家。验收需包含增量/完整体检、显入动画、关闭开关、实际结果以及 666 开关的独立效果。
