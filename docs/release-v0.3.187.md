# FusionStatusBar v0.3.187

本版修正 HyperOS 移动信号等级读取错误。

## 更新内容

- HyperOS 上优先读取 `SignalStrength.getMiuiLevel()`，与系统原生 `MiuiNetworkController` 使用同一等级字段。
- 项目只适配 MIUI/HyperOS；读取不到 MIUI 字段时标记为不可用，不使用 Android 通用等级冒充 MIUI 格数。
- 日志证据显示同一时段通用等级为 1，而 HyperOS 原生 `miuiLevel` 为 3/4；这正是之前预览与实机格数不一致的原因。
- Wi-Fi 图标继续显示 Wi-Fi 强度，双排点阵继续显示移动信号；Wi-Fi 不参与移动等级计算。

## 构建验证

- Debug 与 Release 各 1006 项单元测试通过，0 失败、0 错误、0 跳过。
- 两套 lint 结果、正式 APK 摘要、签名证书、ZIP 对齐和源码快照见同目录 `release.json` 与 `validation.md`。
- 本次基于包含未提交修复的工作区构建；发布清单保留源码快照并标记 `sourceDirty=true`。
- 正式 APK SHA-256：`05a3fa5e9f3c75a6dbca9615c3be709a28099d3e3d5b6c2154cb295fc4e8e90e`。
- 发布证书 SHA-256：`ccfaf42f8f14bb36973aeb971572fcc956a256196f68ff088340dfa7eca96fe2`。

## 实机状态

本次没有连接 Android 设备。新 APK 需要在目标 HyperOS 设备上重启 SystemUI 后验证双排信号格数。
