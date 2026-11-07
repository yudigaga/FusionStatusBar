# 控制中心玻璃内圈与系统磁贴发现修复

基线为 `fa2d75e`（0.5.20 / 234），本地候选为 **0.5.21 / 235**。本轮处理截图中的玻璃内圈，以及实时字幕、翻译、模式等系统磁贴缺失。目标真机没有连接，宿主证据来自工作区已有的系统界面、系统界面组件反编译源码；这些证据不代表用户当前 ROM 已完成验收。

## 原因与修改

| 证据 | 结论 | 修复调用路径 |
| --- | --- | --- |
| [玻璃 RED](../../analysis/control-center-fixes-20261107/glass-red.xml)：3 倍密度、日间深灰底第 13 像素出现低于底色的暗边 | 高光玻璃的偏移内阴影形成第二圈轮廓 | `GlassDrawable.drawSoftEdge` 去掉内侧暗色描边，保留同一外轮廓上的方向反光；通知和控制中心继续共用绘制器 |
| [目录 RED](../../analysis/control-center-fixes-20261107/tiles-red.xml)：普通控制中心的编辑器候选列表为空时，只导出已有 Wi-Fi | `systemTiles`、`packageTiles` 要在原生编辑页查询后才填充，普通捕获不能依赖它们 | `nativeControlCenterTileCatalog` 合并已有记录、`getTilesStock` / `getStockTiles` / `staticStockTiles`，以及 SystemUI 工厂的 `mTileMap` 声明键；读取不调用 `startQuery`、`queryTiles` 或 `createTile` |
| [工厂目录 RED](../../analysis/control-center-fixes-20261107/factory-map-red.xml) | OEM stock 字符串仍可能漏掉标准 SystemUI 的模式磁贴 | 同时合并 `host.interactor.tileFactory.mTileMap`；已声明不等于目标设备一定可用 |
| [身份 RED](../../analysis/control-center-fixes-20261107/identity-red.xml) | 旧 `dnd → quietmode` 别名会合并独立入口，影响顺序、隐藏和工厂路由 | 保留 `dnd`（模式）、`quietmode`（MIUI 勿扰）、`modes_dnd`（模式体系中的勿扰）三个标识；既有已保存的 `quietmode` 原样保留，不猜测反向迁移 |
| [创建 RED](../../analysis/control-center-fixes-20261107/runtime-factory-red.xml) | MIUI 工厂未声明模式等标准 SystemUI 磁贴，读到标识也可能无法创建 | 优先保留原控制器创建；失败后仅对标准工厂确切声明的非应用磁贴调用后备工厂，依次 `setTileSpec → userSwitch(host.getUserId) → refreshState`，初始化失败销毁新实例，正常实例继续由现有 Lease 复用和释放 |
| [标题 RED](../../analysis/control-center-fixes-20261107/stable-labels-red.xml)：Wi-Fi 标题误变成快照 SSID | 快照中的状态文字不能替代已有稳定磁贴名称 | 已知磁贴保持固定标题；原生新增磁贴使用已有缓存的宿主标签，并为翻译、实时字幕、模式、勿扰提供名称回退 |

本机宿主的 `ModesTile` 使用 `dnd`，点击打开模式弹窗；`ModesDndTile` 使用 `modes_dnd`；MIUI `QuietModeTile` 使用 `quietmode`。工厂及接口字段按已有源码核对，运行时缺字段或创建失败时沿既有失败路径保留原生面板。

玻璃仍保留四段低透明度反光，不宣称实现真实折射或连续解析渐变。[修改前](../../analysis/control-center-fixes-20261107/glass-before.png)与[修改后](../../analysis/control-center-fixes-20261107/glass-after.png)为 Android Canvas 合成背景预览，不能模拟目标 ROM 合成器。

## 验证与本地归档

完整验证命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease --console=plain --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8'
python -m unittest discover -s tools/tests -v
```

新增回归涵盖：日夜、白色和深色底、圆形／胶囊／圆角矩形的内圈，目录的编辑前状态及后备，三种原生标识的布局往返与独立隐藏，新增磁贴创建、当前用户初始化、复用、异常释放，以及固定标题和宿主标签的优先级。首次完整验证因独立审查发现标题优先级问题而主动中止；修正后重新执行完整验证，不使用中止运行作为完成证据。

Debug、Release 各完成 1,500 项测试（194 个测试类），失败、错误、跳过均为 0；两套 Lint 均为 0 错误、99 条警告。工具回归 28 项通过。新增行为回归共 17 项，关键修复用例先观察到对应旧行为失败，再随完整套件通过；“已有编辑记录保留”“目录进入列表”“后备工厂拒绝未声明／应用磁贴”保护已有正确边界，首次运行即通过。

完整执行日志在 [full-build.log](../../analysis/control-center-fixes-20261107/full-build.log)。最终签名 APK 的身份、SHA-256、证书、对齐与测试、Lint 计数以本地 [release.json](../artifacts/releases/v0.5.21/release.json) 为准。归档使用当前工作区快照，保留 `sourceDirty=true` 和 `validationInput=existing_build_outputs`；构建与签名在本轮分别执行。本轮没有远端发布。

## 安装验收

安装新 APK 后重启一次 SystemUI，展开控制中心并重新拉取布局，更新旧版本缓存的磁贴目录；随后在模块中检查实时字幕、翻译、模式能否分别添加，并确认模式打开正确系统界面。旧配置若已把模式保存为 `quietmode`，应重新选择独立的“模式”入口；不自动重写用户原有勿扰设置。

尚未执行目标手机安装、LSPosed 实际回调、系统真实模糊合成、磁贴可用性或点击验收。宿主工厂的声明仅用于完整发现和精确路由，不保证硬件、地区、账号或系统服务条件已满足。
