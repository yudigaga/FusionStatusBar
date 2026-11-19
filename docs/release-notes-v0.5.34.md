# FusionStatusBar 0.5.34

版本 `0.5.34`，Android 构建号 `248`，使用原正式证书签名并启用 R8。相对已发布的 `0.5.27`，本版源码快照包含以下累计调整。

## 更新内容

- 新增 19 类状态栏图标显示设置，分为系统状态和连接设备。配置与 SystemUI 原生 hidden-slot 策略合并，覆盖隐私、漫游、耳机及多槽位设备图标；默认 17 类允许显示，隐私和闹钟保持关闭。音量图标仅在宿主因空间不足而隐藏且存在可用空间时尝试恢复。模块不会创建宿主未提供的图标。
- 高光玻璃在 OEM 私有模糊能力未知时使用可逆 Canvas 表面和边缘反光兼容路径，并保存/恢复宿主背景与模糊比例。此路径不代表原生 MiGlass 或合成器模糊可用。
- 调整通知内容和背景处理，保留通知正文、语义颜色、媒体、RemoteViews 与动作由宿主绘制；同时包含通知时钟和双排网速的布局、刷新及恢复逻辑改动。

本版不新增 LSPosed 作用域。应用配置 schema 更新为 `7`；旧配置缺少状态栏图标掩码时使用兼容默认值。状态栏图标效果依赖宿主实际槽位、空间和 ROM 实现。

## 升级

使用原正式证书覆盖安装。沿用 `system`、`com.android.systemui`、`miui.systemui.plugin`、`com.miui.home`、`com.miui.securitycenter` 五个已有作用域。更新后重启 SystemUI 载入新 Hook；首次安装或补选 System 作用域仍需整机重启。不同厂商界面和通知模板可能存在差异。

## 构建与验证范围

- Release `assembleRelease` 构建成功，实际执行 R8 `8.13.19`；`lintVitalRelease` 通过，报告为 `No issues found.`。完整 `lintRelease` 未运行。
- **0.5.34 没有单元测试执行记录，本次未运行单测。** 随包的 `validation-v0.5.33.json` 是 0.5.33 的历史记录：1852 项中 26 项失败，报告称与 0.5.32 基线一致；这不是 0.5.34 的测试结果，也不证明本版通过。
- 本次发布复核签名包、R8 DEX 标记和 mapping 哈希。未进行手机安装、真实 LSPosed 注入、通知与状态栏视觉、SystemUI 行为、ROM、性能或原生模糊验收。

| 项目 | 结果 |
|---|---|
| APK | `fusion-statusbar-v0.5.34.apk`，`3,954,605` 字节，非 debuggable |
| SHA-256 | `937fda3d49a5973d2a5bafd3c6920194aa362f91a4c61a761a620cff748f86ae` |
| 正式证书 SHA-256 | `ccfaf42f8f14bb36973aeb971572fcc956a256196f68ff088340dfa7eca96fe2` |
| 签名与对齐 | APK Signature Scheme v3；16 KiB ZIP 对齐通过 |
| R8 | `8.13.19`，DEX 标记与映射一致 |
| Mapping ID | `367faca61624c7339f2ca4891094c20b022896b25fe7a976d25809a291765be0` |
| Mapping SHA-256 | `9dce7d0de7af2dce0da1c07b42393cd62ba407d1d8c5b7a53fd089621a2c965f` |

## 来源记录

原始构建记录为 `local_snapshot_signed`，`sourceDirectory=fusion-feedback-20261118-05`，`sourceCommit`、`sourceRef`、`sourceDirty` 和 `sourcePatchSha256` 均为空。发布提交用于整理并归档该快照，**不是**原构建的 Git 提交，也不改变 APK 的来源声明。原始 `source.zip` 有 749 项，文件与 `source-files.json` 的路径和 SHA-256 完全匹配；归档同时保留这两份原始来源文件。

Release 附带原始源码 ZIP、APK 校验文件和验证 ZIP。验证 ZIP 包含签名与 Manifest 记录、R8 mapping/configuration/seeds/usage、构建日志、lintVitalRelease 结果、原始 release.json/snapshot/source index，以及 0.5.33 历史验证记录。测试、实机和 ROM 的缺口不会由这些静态产物补足。

- [构建记录](../artifacts/release-v0.5.34.json) · [验证摘要](../artifacts/validation-v0.5.34.md)
- [通知呈现候选记录](2026-11-16-notification-readability.md) · [时钟修订记录](2026-11-17-notification-clock-r3-report.md) · [通知/网速修订记录](2026-11-17-notification-network-r2-report.md)
- [文档导航](README.md)
