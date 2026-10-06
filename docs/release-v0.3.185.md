# FusionStatusBar v0.3.185

本版让 Wi-Fi 图标显示 Wi-Fi 信号强度，同时保留双排蜂窝信号显示。

## 更新内容

- Wi-Fi RSSI 进入运行时快照，并按强度点亮 Wi-Fi 图标弧线；RSSI 单独变化时也会触发状态刷新。
- 双排信号点阵只使用各 SIM 卡的 Telephony 信号强度；Wi-Fi 信号不会代替蜂窝信号。
- Wi-Fi 图标的无障碍描述包含当前 Wi-Fi 信号级别。

## 构建验证

- Debug 与 Release 各 1005 项单元测试通过，0 失败、0 错误、0 跳过。
- 两套 lint 结果、正式 APK 摘要、签名证书、ZIP 对齐和源码快照见同目录 `release.json` 与 `validation.md`。
- 本次基于包含未提交修复的工作区构建；发布清单保留源码快照并标记 `sourceDirty=true`。

## 实机状态

本次没有连接 Android 设备。安装、LSPosed 注入以及 Wi-Fi RSSI 弧线和双 SIM 信号显示仍待实机确认。
