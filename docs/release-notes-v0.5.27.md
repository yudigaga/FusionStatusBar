# FusionStatusBar 0.5.27

版本 `0.5.27`，Android 构建号 `241`，沿用原正式证书和 R8。以下为相对远端 `0.5.25` 的累计更新，包含本地 `0.5.26` 的首页提醒功能及本版的体检文字补充。

## 更新内容

- 新增“杂项 → 扫描与确认 → 隐藏首页风险提醒”，默认关闭。开启后隐藏普通手机管家首页的危险/风险结论文案；病毒扫描、优化加速卡片采用宿主普通文字颜色和说明，并隐藏红点。
- 同一开关继续覆盖顶部与下方的“正在体检”提示，在文字刷新、显隐及显入动画之后保持显示过滤。下方实际体检结果继续显示；关闭开关恢复模块自身改变的原生呈现，保留宿主原本的 `GONE` 状态。
- 开关与“666”选项独立，沿用配置保存、宿主缓存和监听；七种应用语言同步设置文案。扫描、优化、真实评分和风险数据不作修改，橙色球体背景沿用原生状态。

新增显示适配已核对 `com.miui.securitycenter` **13.8.0-261020.0.1 / versionCode 40001380** 的普通手机版首页；代码按包名和 versionCode 准入，未知版本不套用这组规则。Lite、Pad、Fold 及结果页不属于此次新增适配范围。

## 升级与使用

1. 使用原正式证书覆盖安装，无新增 LSPosed 作用域。
2. 保持手机管家 `com.miui.securitycenter` 作用域开启，在模块中启用上述开关。
3. 重启手机管家进程或手机，加载本版新 Hook；仅重启 SystemUI 不会重载手机管家。

首次启用或补选 System 作用域的整机重启要求，以及其他手机管家扩展的硬件限制，继续参照 [0.5.24 说明](release-notes-v0.5.24.md)。旧包名迁移见 [0.3.197 说明](release-notes-v0.3.197.md)。

## 构建与验证

本版原始归档包含 **5 个测试类、32 项相关 Release 测试**，失败、错误和跳过均为 `0`；完整 Release lint 为 **0 错误、142 警告**，高风险策略通过，并完成 R8 Release 构建。本版未重复全量测试，不将本地 `0.5.26` 的 `1608` 项全量结果算作 `0.5.27` 已复跑。

本次发布重新核对归档测试、lint、源码索引与所有 R8 文件哈希，通过 **62 项 Python 工具测试**及对最终签名 APK 的独立结构检查。实际 DEX 的 R8 标记、编译器版本与 mapping ID 一致，入口公开/继承、元数据、compile-only API 打包边界及真实业务类改名检查通过。发布期间未重新运行 Gradle或构建 Debug。

| 项目 | 结果 |
|---|---|
| APK | `fusion-statusbar-v0.5.27.apk`，`3,896,421` 字节，非 debuggable |
| SHA-256 | `864cba54d6b4746b5a20a0059dbf0dc514864f2faefaba9d4ae2a2a3b150aad3` |
| 正式证书 SHA-256 | `ccfaf42f8f14bb36973aeb971572fcc956a256196f68ff088340dfa7eca96fe2` |
| 签名与 ZIP 对齐 | v3 通过，`16 KiB` 对齐通过 |
| R8 | `8.13.19`，未启用资源压缩 |
| Mapping ID | `3096445c046059544460f966f5f77f96dcf9922fa8acb76ddaaa9c516edfbb2d` |
| Mapping SHA-256 | `7aa6adfdff42f2071cf367479b30e194d5bbf1ce7e9f9614174f31b19353f258` |

JVM/Robolectric 测试执行在混淆前类上。未进行手机安装、真实 LSPosed/ART 去优化、宿主动画或 ROM 验收；原生扫描继续执行属于源码及模拟 Chain 检查结论，仍需设备确认。远端 CI 以发布提交对应的 GitHub Actions 结论为准。

## 来源与附件

本次使用已有签名构建，保留原记录 `local_dirty_signed`、`sourceDirty=true`、`validationInput=existing_build_outputs`，基线提交为 `fe7bb9d230781985779e11e7151fc509acffd59f`。未将原包改记为新发布提交的干净重构建。

发布源码在已发布主仓 `0b05743` 之上恢复原始快照的应用文件，保留上一版验包工具、发布历史和赞赏码。额外整理首页与文档导航、迁出构建/架构细节，并接回已有独立 R8 验包调用。原开发目录与构建归档均未覆盖。

正式发布附带 APK、SHA-256 校验文件、原始 `source.zip` 和验证 ZIP。原始 ZIP 的 `796` 项文件与索引一致；其中四个根目录临时日志/补丁仅保留在原快照，不作为本次新源码提交。验证 ZIP 包含原测试、lint、mapping、configuration、seeds、usage、原始构建元数据及本轮工具测试、独立验包记录。

- [原始构建记录](../artifacts/release-v0.5.27.json) · [本次验证摘要](../artifacts/validation-v0.5.27.md)
- [首页提醒实现记录](2026-11-13_reverse-security-home-reminders-report.md) · [体检提示补充记录](2026-11-13_security-home-scanning-report.md)
- [构建约定](building.md) · [文档导航](README.md)
