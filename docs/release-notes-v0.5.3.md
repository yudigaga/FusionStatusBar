# FusionStatusBar 0.5.3

版本号 `0.5.3`，构建号 `217`。汇总 0.5.0 之后的通知中心时钟与控制中心材质修复，发布已有的正式签名构建。

## 功能调整

- 修复通知中心时钟在下拉展开和文字刷新时的位置跳动，保留系统当前动画位置，避免重复叠加偏移。
- 自定义通知时钟在下拉后半段平滑渐显，避免未展开时的大时钟经过状态栏区域；横屏、关闭自定义、完全展开或更换时钟视图时恢复原有过渡透明度。
- 控制中心模糊半径、缩放动画更新时继续记录原生状态以便恢复，同时减少不必要的材质布局重建。
- 毛玻璃背景复用未变化的渐变；尺寸、强度或屏幕密度变化时更新，覆盖零尺寸与重新显示等边界情况。
- 继续保留 0.5.0 的网络文字独立上下微调、锁屏状态栏高度修复及既有配置。

## 安装与验证范围

沿用原正式证书，可覆盖更新 `0.5.0`。更新后重启系统界面或设备，再打开设置同步配置。从更早版本升级时，仍按 [0.3.197 升级说明](release-notes-v0.3.197.md) 核对 System 作用域。

- 最新构建归档记录 Debug、Release 各 `1204` 项测试通过，均为 `0` 失败、错误和跳过。覆盖通知时钟生命周期、展开与刷新、透明度恢复及控制中心材质缓存等回归。
- 本次发布前重新运行构建校验器及 `28` 项工具回归测试，全部通过。Debug/Release lint 均为 `0` 错误，分别为 `99` / `98` 条警告，高风险策略通过。
- Manifest、BuildConfig、Xposed 版本及作用域一致；正式 APK 为非调试构建，原正式证书签名和 `16 KiB` ZIP 对齐检查通过。
- 本版尚未进行设备安装、Hook 注入、目标 ROM 功能或实机性能验收。远端完整 CI 以发布提交对应的 GitHub Actions 为准。

## 产物与来源

- APK：`fusion-statusbar-v0.5.3.apk`，大小 `21,513,193` 字节。
- APK SHA-256：`fc49b1a8600a44e331905a2f93a2a4c212f5301a354aa6c3d9cdf558a85c77e5`。
- 签名证书 SHA-256：`ccfaf42f8f14bb36973aeb971572fcc956a256196f68ff088340dfa7eca96fe2`，沿用原正式证书。
- 保留原始 `local_dirty_signed`、`sourceDirty=true`、`validationInput=existing_build_outputs` 构建来源记录。构建源码为 `d5e597654e7a0cf465c7cb9b385baf6f5eb6dcef` 加归档补丁。
- 构建时工作区、源码快照与文件索引共 `548` 项，已逐项核对哈希，全部一致。发布前仅追加文档、Git 属性和归档证据，APK 保留原样。
- `verified-build.json` 保存签名前的构建输出；最终正式签名 APK 的哈希、大小和证书以 `release.json` 为准。
- [原始构建记录](../artifacts/release-v0.5.3.json)、[文件索引](../artifacts/source-files-v0.5.3.json)、[构建补丁](../artifacts/tracked-v0.5.3.patch)和[验证摘要](../artifacts/validation-v0.5.3.md)随源码保存。
- 原始 `source.zip` 源码快照（SHA-256 `f7a5286f3160312c5c437102831ae66f00cd4f0b7ec7465150b781c78ec53623`）及完整验证记录作为 [本次 Release 附件](https://github.com/yudigaga/FusionStatusBar/releases/tag/v0.5.3)提供。
