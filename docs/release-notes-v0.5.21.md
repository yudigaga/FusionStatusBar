# FusionStatusBar 0.5.21

版本 `0.5.21`，Android 构建号 `235`。发布已有的正式证书签名 APK，修复高光玻璃内圈与控制中心系统磁贴的发现、创建和名称。

## 更新内容

- 移除高光玻璃偏移内阴影产生的第二圈暗色轮廓，保留外轮廓上的方向反光；通知与控制中心继续共用绘制器。
- 控制中心磁贴目录合并已有记录、宿主 stock 列表和标准 SystemUI 工厂声明，无需先进入原生编辑页才发现实时字幕、翻译、模式等候选。目录读取不启动查询任务，也不为枚举而创建磁贴。
- 保留原控制器创建路径；创建失败时，仅为标准工厂确切声明的非应用磁贴使用后备工厂，按当前用户初始化，异常时释放新实例。
- 分别保留 `dnd`（模式）、`quietmode`（MIUI 勿扰）和 `modes_dnd` 标识，避免独立入口在布局、隐藏及工厂路由中被合并。
- 已知磁贴保留固定名称，新增原生磁贴使用宿主缓存标签及名称回退，避免 Wi-Fi 等标题被快照状态文字替换。

实现与本地复现见 [控制中心玻璃内圈与系统磁贴发现修复](2026-11-07_control-center-glass-and-native-tiles.md)。工厂声明仅用于发现与精确路由，不保证当前设备的硬件、地区、账号和系统服务条件满足。

## 安装与验证范围

沿用原正式证书，可覆盖更新 `0.5.20`。安装后重启 SystemUI 或设备，展开控制中心并重新拉取布局，更新缓存的磁贴目录。从更早版本升级时，按 [0.3.197 升级说明](release-notes-v0.3.197.md)核对 System 作用域。

旧配置已保存的 `quietmode` 保持原样；需要系统“模式”入口时，请重新选择独立的“模式”磁贴，不会自动改写原有勿扰设置。

- 构建归档记录 Debug、Release 各 `194` 个测试类、`1500` 项测试，失败、错误、跳过均为 `0`；本版为完整单元测试记录。
- 两套完整 lint 均为 `0` 错误、`99` 警告，高风险规则通过；两种变体装配及 Manifest、BuildConfig、Xposed 版本和作用域检查通过。
- 本次发布前重新运行 `28` 项 Python 工具测试，全部通过，并校验当前构建报告与元数据。
- 正式 APK 为非调试构建，原正式证书 v3 签名及 `16 KiB` ZIP 对齐检查通过。
- 本版设备安装、真实 LSPosed 回调、ROM 玻璃合成、磁贴可用性与点击验收尚未执行。远端完整 CI 以对应发布提交的 GitHub Actions 为准。

## 产物与来源

- APK：`fusion-statusbar-v0.5.21.apk`，大小 `21,652,726` 字节。
- APK SHA-256：`67c558d53a88fd0f76d2e56195c25fe96c760e4ebdce95a0b5d7b2c47819de64`。
- 签名证书 SHA-256：`ccfaf42f8f14bb36973aeb971572fcc956a256196f68ff088340dfa7eca96fe2`，与此前正式版一致。
- 原始构建记录保留 `buildMode=local_dirty_signed`、`sourceDirty=true`、`validationInput=existing_build_outputs`。构建基线为 `fa2d75e06979b76ee0ae43614769eafda9773289` 加工作区改动；本次不重打包 APK，也不声称从最终发布提交重新构建。
- 构建时工作区、文件索引及源码 ZIP 的 `665` 项文件已逐项核对 SHA-256。发布文档、链接和归档摘要在构建后补充。
- [原始构建记录](../artifacts/release-v0.5.21.json)、[文件索引](../artifacts/source-files-v0.5.21.json)、[构建补丁](../artifacts/tracked-v0.5.21.patch)和[验证摘要](../artifacts/validation-v0.5.21.md)随源码保存。`verified-build.json` 记录签名前产物，最终签名 APK 以原始 `release.json` 为准。
- 原始 `source.zip`（SHA-256 `b474317f83ce0388b324f0525b6a3e3dfa0f848d66e26657f5905c007efdf191`）及两套测试、lint、签名等验证附件随 [本次正式发布](https://github.com/yudigaga/FusionStatusBar/releases/tag/v0.5.21)提供。
