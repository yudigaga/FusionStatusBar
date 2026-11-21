# FusionStatusBar 0.5.36

Android 构建号 `250`。从当前发布基线合入闹钟图标淡出和隐私提示退出两项网速布局改动，保留控制中心模糊恢复修复。

- 只有在原生闹钟图标因空间不足而完全淡出后，双排网速才回收其释放的空间；模块不移动原生图标。
- 隐私提示退出时，双排网速使用原生图标动画结束后的位置恢复布局，无需重新触发布局根节点。

使用原证书覆盖更新，沿用已有五个 LSPosed 作用域。更新后重启 SystemUI；首次安装或补选 System 作用域时整机重启。

正式 APK、SHA-256、签名和 R8 记录以 [GitHub Release](https://github.com/yudigaga/FusionStatusBar/releases/tag/v0.5.36) 及其验证附件为准。未做真机安装、LSPosed 注入、ROM 显示或性能验收。

[LSPosed 下载](https://github.com/Xposed-Modules-Repo/io.github.yudigaga.fusionstatusbar/releases/tag/250-0.5.36) · [构建与发布](building.md) · [文档导航](README.md)
