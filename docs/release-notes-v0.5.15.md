# FusionStatusBar 0.5.15

版本 `0.5.15`，Android 构建号 `229`。发布已有的正式证书签名 APK，汇总 0.5.13 之后的控制中心重复计算优化与竖向开合动画调整。

## 更新内容

- 复用几何不变的玻璃描边路径，实际尺寸、圆角、描边或原生 Path 内容变化时更新，保留现有玻璃软边与绘制层次。
- 稳定磁贴跳过重复的编辑模式查询；有临时背景或待恢复状态时继续读取实时模式，并保留编辑结束、禁用后重新启用等恢复路径。
- 对启用且布局有效的自定义网格承载视图，调整竖向展开、回收和松手后的弹簧收尾，移除适用配置中的分排延迟。
- 展开、收起的 spring response 上限分别为 `0.30` / `0.20`，需要调整时 damping 不低于 `0.9`；更快的有效原生配置保持原样。response 是弹簧参数，不等于固定 `300ms` / `200ms` 动画总时长。
- 保留原生动画目标、完成回调及配置元数据；直接拖动、中间跟随进度、镜像和普通原生布局继续走对应的原生路径。共享配置 setter 的 Hook 按当前承载视图上下文处理，避免不同安装和嵌套配置相互误改。

开发过程见 [控制中心下拉开销回归修复](2026-10-25_control-center-performance-regression.md)及[控制中心快速展开与回收分析记录](2026-10-25_reverse-control-center-fast-pull-report.md)。本地绘制计数与配置契约测试不能直接证明目标手机帧率或手势耗时改善。

## 安装与验证范围

沿用原正式证书，可覆盖更新 `0.5.13`。更新后重启系统界面或设备，再打开设置同步配置。从更早版本升级时，请按 [0.3.197 升级说明](release-notes-v0.3.197.md)核对 System 作用域。

- 构建归档中的定向 Debug 回归为 `52` 个测试类、`339` 项测试，失败、错误、跳过均为 `0`；覆盖动画、共享配置、渲染预算、玻璃材质、通知时钟等相关路径。
- 完整 Release lint 通过：`0` 错误、`98` 警告，高风险规则通过；Release 装配和 APK 元数据检查通过。
- 本次发布前重新运行 Python 工具回归，`28` 项通过。完整 Debug 单元测试、Release 单元测试和 Debug lint 本轮未运行。
- APK 为非调试构建，原正式证书 v3 签名及 `16 KiB` ZIP 对齐通过。本版设备安装、LSPosed 稳定性、目标 ROM 视觉、实际手势耗时和帧率验收尚未执行；远端完整 CI 以对应发布提交的 GitHub Actions 为准。

## 产物与来源

- APK：`fusion-statusbar-v0.5.15.apk`，大小 `21,545,961` 字节。
- APK SHA-256：`03f1f84cdc75ce7ecff8599d2af246b6196f8e2d9c5633017e4989a15e1172da`。
- 签名证书 SHA-256：`ccfaf42f8f14bb36973aeb971572fcc956a256196f68ff088340dfa7eca96fe2`，与此前正式版一致。
- 原始构建记录保留 `buildMode=local_dirty_signed_candidate`、`sourceDirty=true`。构建时基线为 `71b1e0e1f6a14bc15d000b0925be01a01e53fb14` 加归档工作区改动；本次发布不重打包 APK，也不将其描述为从发布提交重新构建。
- 构建时工作区、文件索引与源码 ZIP 共 `611` 项，已逐项核对 SHA-256。发布文档、版本链接和归档索引在构建后补充。
- [原始构建记录](../artifacts/release-v0.5.15.json)、[文件索引](../artifacts/source-files-v0.5.15.json)、[构建补丁](../artifacts/tracked-v0.5.15.patch)和[验证摘要](../artifacts/validation-v0.5.15.md)随源码保存；构建记录中的发布状态为构建时的历史状态。
- 原始 `source.zip`（SHA-256 `53df6c0756a8c3ed9b15014c4cc6c593129fc28fd53180c1c51068f39514f741`）及最终定向测试、lint、签名等验证附件随 [本次正式发布](https://github.com/yudigaga/FusionStatusBar/releases/tag/v0.5.15)提供。
