# 手机管家首页提醒显示逆向与实现报告

> 以下为原始本地分析与实现记录，所述授权、路径和验证范围对应当时任务。`analysis/` 下的样本、脚本和日志未随远端仓库发布；本次正式交付及验收范围见 [0.5.27 更新说明](release-notes-v0.5.27.md)。

分析日期：2026-11-13。报告类型：普通 APK 逆向（flavor = null）。

已在模块“杂项 → 扫描与确认”增加默认关闭的“隐藏首页风险提醒”。开启后，普通手机首页的危险/风险结论文案隐藏，病毒扫描与优化加速卡片使用宿主普通文字颜色、普通说明并隐藏红点。关闭时恢复最新的原生显示。本次仅改变首页呈现，扫描、优化、真实评分和风险数据仍由宿主执行与维护。

## 范围与样本

授权、范围和离线限制见工作区 `analysis/security-home-reminders-20261113/scope.md`。用户提供截图并明确要求修改这三处显示；没有设备安装或远程发布授权。

| 属性 | 值 |
|---|---|
| APK | 手机管家_13.8.0-261020.0.1.apk |
| 包名 / versionCode | com.miui.securitycenter / 40001380 |
| SHA256 | e53a09e38772327e059f74cdaf02e8b3674f9f001e3eea29f10f77fda25448c6 |
| 工具 | 已有 JADX 1.5.5 / apktool 3.0.2 产物；smali 为精确签名依据 |
| 模块版本 | 0.5.26 / 240 |

## Evidence → Finding → Path

证据根目录：工作区 `analysis/security-center-13.8.0-20261109/apktool`。本次声明/字段/资源匹配与文件 SHA256 记录在 `analysis/security-home-reminders-20261113/host-contract.json`；可从工作区执行 `py analysis/security-home-reminders-20261113/verify-host-contract.py` 重现。该验证只证明当前本地样本的静态合同。

| E-id | source_ref | repro_command | content_hash |
|---|---|---|---|
| E-01 | smali_classes3/com/miui/securityscan/ui/main/MainContentFrame.smali:1520；:880 | 上述 verify-host-contract.py | 见 host-contract.json |
| E-02 | smali_classes2/com/miui/common/card/models/FuncGrid6CardModel$FuncGrid6ViewHolder.smali:1331、:1702、:2293、:2421 | 上述 verify-host-contract.py | 见 host-contract.json |
| E-03 | 同目录 FuncGrid6CardModel$DisplayViewHolder.smali:22–36；res/values/public.xml | 上述 verify-host-contract.py | 见 host-contract.json |
| E-04 | 本次 settings-red.log、render-red.log、focused-green.log | 下节 Gradle 命令 | n/a；可重跑测试 |

| F-id | severity | evidence_ids | confidence | location | status |
|---|---|---|---|---|---|
| F-01 | n/a_re | E-01 | high | MainContentFrame.setStatusTopText(String)、n() | 静态已核实，运行时未验证 |
| F-02 | n/a_re | E-02、E-03 | high | refreshSecurityScan / refreshOptimizemanage | 静态已核实，运行时未验证 |
| F-03 | n/a_re | E-02、E-04 | high | fillData 前清理与可逆快照 | 本地回归通过，实机未验证 |

P-01（path_type=callflow）：ScoreManager → gk.t.c(Context) → MainFragment → MainContentFrame.setStatusTopText → 字段 e:TextView。本模块只在最后一层处理可见性。仅匹配宿主 `examination_score_60_0`（0x7f1208c4）与 `examination_score_80_60`（0x7f1208c6），保留扫描进度和具体危险应用提示。`n()` 会通过 gk.w.e 强制可见，因此也在其原生执行后重新应用隐藏；原生退出动画设置的 GONE 不被旧快照覆盖。

P-02（path_type=callflow）：sj.j 的同步/异步回调 → FuncGrid6ViewHolder 的两个 private refresh 方法 → DisplayViewHolder 的 titleTextView、summaryTextView、redPointView。本模块执行原方法后才更新这三个 View。普通样式来自宿主 `six_pices_card_item_title_textcolor`（0x7f060eb2）、`six_pices_card_item_summary_textcolor`（0x7f060eb1）；说明来自 `menu_summary_antivirus`（智能查杀病毒）与 `menu_summary_optimize_manage`（让手机更流畅）。实际扫描时间、风险数量和内存计算输入不变。

P-03（path_type=callflow）：RecyclerView fillData → 重建 DisplayViewHolder → refresh。fillData 执行前按根 View 恢复并移除旧快照，防止同一 View 重用后被旧 Holder 覆盖；其他卡片不变。弱映射的值只保存字段元数据、文字、颜色和可见性，不持有 View。

## 实现与配置

- `SecurityCenterHomeHooks` 安装五个精确方法 Hook，并对已确认的调用类去优化；`SecurityCenterHooks` 记录实际安装数量。
- 独立配置键 `security_center_hide_home_warnings`，默认 false；与 `security_center_score_666` 独立，沿用现有版本准入、原子保存、宿主缓存和配置监听。
- 开关覆盖简体中文、繁体中文、英语、日语、韩语、俄语、西班牙语；宿主卡片文字与配色跟随宿主自身资源。
- 原方法参数、返回和异常保持；不修改 ScoreManager、扫描日期、引擎或实际优化状态。橙色球体背景沿用原生状态。

## 验证与复现

从模块项目目录使用 JDK 21 执行：

```powershell
.\gradlew.bat :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease -I E:\vs\claude\xtjm\analysis\security-center-13.8.0-20261109\low-memory.init.gradle '-Dorg.gradle.jvmargs=-Xmx1024m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process' --no-daemon --max-workers=1 --console=plain
```

先验证设置测试 11 项中 4 项在缺少配置键/入口时失败，再验证渲染测试 7 项中 6 项在空实现时断言失败。实现后的定向 30 项测试通过。完整 Release 测试、lint、R8 版本、签名、APK 哈希与匹配 mapping 以 `artifacts/releases/v0.5.26/release.json` 为最终归档证据。

时间线：2026-11-13 先定位当前宿主的文案、卡片与动画调用链；编写并运行 RED；加入设置翻译与显示实现；完成 GREEN 与两路独立源码/smali 审查；最后执行完整 Release 验证与本地签名归档。

## 实机验证边界

ADB 设备列表为空，未安装 APK、未运行 LSPosed 或实际动画。Robolectric 与模拟 Chain 覆盖原生执行、独立开关、保存重建、普通显示、刷新恢复、View 重用、扫描进度和 GONE；不能证明设备上的 ART 去优化及实际界面效果。顶部仅覆盖用户截图对应的普通 MainContentFrame，Lite、Pad、Fold 及结果页不在本次顶部适配范围。

安装后在 LSPosed 保持手机管家作用域启用，模块“杂项 → 扫描与确认 → 隐藏首页风险提醒”开启，再重启手机管家进程以载入新 Hook。设备验收应包含进出首页、手动病毒扫描、优化、深浅色、关闭开关恢复，以及保留 666 选项时的独立效果。
