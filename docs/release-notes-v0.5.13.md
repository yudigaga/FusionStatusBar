# FusionStatusBar 0.5.13

版本 `0.5.13`，Android 构建号 `227`。发布已有的正式证书签名 APK，汇总 0.5.9 之后的玻璃绘制、控制中心能力探测及通知背景修复。

## 更新内容

- 高光玻璃明确采用 Canvas 兼容绘制，修正强度、透明度及关闭后的恢复逻辑，移除强制套用旧 bloom/mix 接口的路径。此模式尚未实现或验证真实背景折射。
- 修复不同 ClassLoader 下的控制中心能力归属判断，避免后台探测失败覆盖已可用面板，并改善日期、运营商区域的可见性检查。
- 自动日间、夜间高光共用向内渐隐的软倒角与局部方向反光；手动选择的柔光玻璃保留既有样式。
- 通知反光纳入背景内部绘制，随实际高度、RTL 宽度、展开边界和原生透明度共同裁剪、淡出。
- 修复主题刷新时原背景身份、自引用与恢复问题，并减少折叠通知在重复刷新、主题变化时的渐变缓存重建。
- 继续保留控制中心融合图标独立于普通状态栏缩放的布局策略。

实现与本地复现说明见 [高光玻璃软边与通知裁剪修复](2026-10-25_glass-soft-edge-bugfix.md)。其中旧版设备日志用于定位问题，不代表本版已通过真机验收。

## 安装与验证范围

沿用原正式证书，可覆盖更新 `0.5.9`。更新后重启系统界面或设备，再打开设置同步配置。从更早版本升级时，请按 [0.3.197 升级说明](release-notes-v0.3.197.md)核对 System 作用域。

- 构建归档中的定向 Debug 回归为 `40` 个测试类、`221` 项测试，失败、错误、跳过均为 `0`；覆盖材质、软边、通知裁剪、主题恢复和缓存等相关路径。
- 完整 Release lint 通过：`0` 错误、`98` 警告，高风险规则通过；Release 装配和 APK 元数据检查通过。
- 本次发布前重新运行 Python 工具回归，`28` 项通过。此结果不替代完整 Android 单元测试。
- 完整 Debug 单元测试、Release 单元测试和 Debug lint 本轮未运行；远端完整 CI 以对应发布提交的 GitHub Actions 为准。
- APK 为非调试构建，原正式证书 v3 签名及 `16 KiB` ZIP 对齐通过。本版设备安装、LSPosed 绘制、目标 ROM 视觉与稳定性验收尚未执行，也未验证真正的 MiGlass 或背景折射。

## 产物与来源

- APK：`fusion-statusbar-v0.5.13.apk`，大小 `21,529,577` 字节。
- APK SHA-256：`edb7a04bb2245a2b8e5e6dd63efcf7688f93fcadafb8b3169d5d3e0a5b60a893`。
- 签名证书 SHA-256：`ccfaf42f8f14bb36973aeb971572fcc956a256196f68ff088340dfa7eca96fe2`，与此前正式版一致。
- 原始构建记录保留 `buildMode=local_dirty_signed_candidate`、`sourceDirty=true`。构建时基线为 `cf6ff50a11abe191eef9bf3eae14bf48e6dea0d7` 加归档的工作区改动；本次发布不重打包 APK，也不将其描述为从发布提交重新构建。
- 构建时工作区、文件索引及源码 ZIP 共 `596` 项，已逐项核对 SHA-256。发布文档、版本链接和归档索引在构建后补充。
- [原始构建记录](../artifacts/release-v0.5.13.json)、[文件索引](../artifacts/source-files-v0.5.13.json)、[构建补丁](../artifacts/tracked-v0.5.13.patch)和[验证摘要](../artifacts/validation-v0.5.13.md)随源码保存；构建记录中的发布状态为构建时的历史状态。
- 原始 `source.zip`（SHA-256 `2197e705111c2e16824d6221a35d1316ffe897d8e6af7b5b0750b78053fbe9ab`）及定向测试、lint、签名等验证附件随 [本次正式发布](https://github.com/yudigaga/FusionStatusBar/releases/tag/v0.5.13)提供。附件中的测试结果以最终定向 GREEN 记录为准。
