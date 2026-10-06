# FusionStatusBar v0.3.184

本版修正 Wi-Fi 已连接时融合图标双排信号错误使用 Wi-Fi 信号强度的问题。

## 更新内容

- Wi-Fi 图标显示与蜂窝信号显示相互独立。即使当前网络使用 Wi-Fi，两排信号仍取自各 SIM 卡的 Telephony 信号级别。
- 设备没有可用订阅编号时，单排回退信号继续使用蜂窝信号值，不再以 Wi-Fi RSSI 代替。

## 构建验证

- Debug 与 Release 各 1002 项单元测试通过，0 失败、0 错误、0 跳过。
- 两套 lint 结果、正式 APK 摘要、签名证书、ZIP 对齐和源码快照见同目录 `release.json` 与 `validation.md`。
- 本次基于包含未提交修复的工作区构建；发布清单保留源码快照并标记 `sourceDirty=true`。

## 实机状态

本次没有连接 Android 设备。安装、LSPosed 注入以及设备上 Wi-Fi 连网时双卡信号显示仍待实机确认。
