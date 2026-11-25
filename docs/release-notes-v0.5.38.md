# FusionStatusBar 0.5.38

Android 构建号 `252`。包含自 0.5.36 以来的模块日志、状态栏时钟及高光玻璃调整。

- 「设置 → 模块维护」新增模块日志开关，默认开启。它控制 Fusion 写入的日志；已保存的历史日志不会被删除，系统和其他模块的日志也不受影响。
- 自定义状态栏时钟只在可见时刷新。含秒格式对齐整秒，其余对齐整分钟；关闭自定义恢复原生文本和字号。
- 通知高光玻璃按日夜调整原生模板文字对比度，关闭样式后恢复颜色；同时调整控制中心玻璃反光。

使用原正式证书覆盖更新，沿用原有五个 LSPosed 作用域。更新后重启 SystemUI；首次安装或补选 System 作用域时整机重启。

正式 APK、SHA-256、签名、R8 和测试记录见 [GitHub Release](https://github.com/yudigaga/FusionStatusBar/releases/tag/v0.5.38)。未做真机安装、LSPosed 注入或目标 ROM 的显示与性能验收。

[LSPosed 下载](https://github.com/Xposed-Modules-Repo/io.github.yudigaga.fusionstatusbar/releases/tag/252-0.5.38) · [构建与发布](building.md) · [文档导航](README.md)
