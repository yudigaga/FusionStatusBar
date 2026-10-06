# 2026-10-05 优化实施记录

本轮完成优化计划阶段 0-2 的本地实现与验证。源码工作区原有大量未提交内容，本轮没有覆盖或回退这些修改；另保存了可恢复快照用于基线核对。

## 已完成

- 公开版身份统一为 `io.github.yudigaga.fusionstatusbar`，Provider authority、配置 URI、激活状态 URI 和 Xposed `module.prop` 从 `BuildConfig.APPLICATION_ID` 派生；版本更新为 `0.3.174 / 188`。
- 新增 `app/gradle.lockfile`，发布检查记录 JDK、Gradle、AGP、SDK、Build Tools 和 lockfile 哈希。
- 新增 `tools/save-source-snapshot.ps1`，保存 `source.zip`、`tracked.patch`、源码文件哈希和快照清单；dirty 源码必须显式使用 `-AllowDirty`。
- 重写 `tools/release.ps1` 和 `tools/validate-build.py`，统一 Debug/Release 测试、lint、APK 元数据、Xposed 元数据和归档检查；增加 `-VerifyOnly`、脏树保护和既有目录保护。
- 增加 `tools/tests/test_validate_build.py` 和 `.github/workflows/validate.yml`。工具测试 17 项通过。
- `ControlCenterMaterials` 的材质配置、背景包装、颜色/tint/filter/alpha/文字更新改为幂等；区分“已接管”与“本次实际变化”，保留原生 Drawable 回调和动画调度。
- `ControlCenterRuntimeGrid` 增加 slot dirty 标记、绑定后有界晚到探测、延迟任务取消和轻量根背景/状态兜底；稳定帧不再重复扫描完整组件子树。
- `RuntimeConfigDiff` 将配置变化划分为状态栏、时钟、通知时钟、控制中心布局/材质、系统菜单和通知表面；无关功能不再执行全量刷新。
- 控制中心插件能力按实际 `ClassLoader` 记录，失败能力独立重试；系统菜单和通知表面回执改为以实际 Hook/窗口挂载状态表达 `applied`、`waiting`、`degraded` 或恢复原生。

## 验证结果

最终源码执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease --console=plain --no-daemon
```

结果：Debug 和 Release 各 875 项单元测试，失败/错误/跳过均为 0；两套 lint 均 0 错误、65 条警告；Debug/Release APK 构建成功。

定向回归覆盖材质幂等、静止帧 dirty、配置 diff、插件能力、系统菜单、通知表面及控制中心形状/状态切换，均通过。

统一发布入口使用 `-VerifyOnly -AllowDirty -SkipBuild` 生成了本地验证归档：
`artifacts/releases-validation2/v0.3.174/`。其中 Release APK 为未签名验证产物；正式签名需要运行不带 `-VerifyOnly` 的命令并提供本机证书。归档记录 `sourceDirty=true`、commit、source.zip、tracked.patch、依赖锁哈希以及安装/注入/ROM 验收仍为 `not_performed`。

## 未完成与限制

- 未安装 APK、未检查 LSPosed 注入、未重启 SystemUI，未在目标 HyperOS/GPU 上做最终视觉和性能对照。
- 控制中心页面、OEM `HostAdapter`、`MaterialSession` 尚未迁移；当前优先保留行为稳定性。
- 高频全局 Hook、反射缓存、遥测采样周期和 Activity 内设置面板仍需按设备 trace 测量后决定。
- 当前工作区仍有历史及本轮共同的 dirty 修改；正式公开发布前应在干净、可标记的源码提交上运行发布脚本并核对正式证书。

## 下一步

先在目标设备完成安装/注入/功能回执矩阵，再决定是否推进阶段 3 的职责迁移。任何性能收益需使用同设备、同布局、同刷新率的模块开关及前后对照数据证明。

## 2026-10-06 设备补充

已在目标 Xiaomi Pad 5 Pro 上确认 `0.3.174 / 188` 安装包哈希与本地签名设备归档一致；概览页显示本次开机已连接，SystemUI PID 启动时刻与 22:09 回执吻合。完整设备验收仍未完成，尤其是逐功能状态、HyperOS 最终渲染和性能对照，详见[设备验收补充记录](device-validation-v0.3.174.md)。

据此，安装和 SystemUI 连接验证已有当前设备证据；T14 继续保持未完成。SystemUI 连接不等同于功能级应用成功。

## 2026-10-06 T07 首条迁移

- `ControlCenterSettingsPage` 首先构建“布局与外观”面板并通过自身编辑会话应用控件变化；后续项目属性、画布与拖拽迁移见本记录后续小节。
- 草稿、dirty 和标准/紧凑模式均直接由页面编辑会话提供；Activity 保留保存、发布、备份恢复、修订回执协调和控件刷新。
- 页面级回归确认列数只更改所选画布、编辑进入页面撤销历史；控制中心设置 UI 流程回归覆盖外观选项、表面切换、模糊联动和发布行为。
- 当前 dirty 工作区的完整 Debug/Release 单测各 889 项通过，失败/错误/跳过均为 0；两套 lint 各 0 错误、65 警告；`assembleDebug` 与 `assembleRelease` 成功。该门禁没有更新正式归档或设备安装包。
- 第一阶段只迁移全局布局/外观面板及草稿/模式状态；后续页面属性与画布交互迁移见后续小节。

## 2026-10-06 T07 卡片样式迁移

- 当前画布属性面板中的卡片样式与独立圆角通过 `ControlCenterSettingsPage.editItemAppearance()` 写入页面会话；标准/紧凑画布选择由会话读取。
- 样式更新复用既有 `ControlCenterGridPlacement`，保留锁定拒绝、碰撞/越界拒绝和未推送草稿语义；Activity 仍持有项目属性弹窗其他控件及最终 UI 刷新。
- 页面测试覆盖只更改当前画布、一次撤销、锁定拒绝及旧布局未放置坐标解析；UI 测试覆盖当前紧凑画布、预设不提前发布、手动圆角和锁定联动。
- 最终 Debug/Release 全量单测各 893 项通过，失败/错误/跳过均为 0；两套 lint 各 0 错误、65 警告；`assembleDebug` 与 `assembleRelease` 成功。最终本地 APK SHA-256：Debug `6cb9fc859eede196d0446714ee7e2a768f510a4398317d6ac081ed7cf8f10949`，Release `31638bb66683489ad77ffe71d6046bbae5e85fbd8a920c9bd1fb82a15f238362`；只作为 dirty 工作区构建产物，未安装或归档为正式版本。
- 当时尚未迁移外围项目属性、画布和拖拽 UI；这些部分随后在 T07 属性与画布交互小节完成。

## 2026-10-06 T07 属性与画布交互迁移

- `ControlCenterSettingsPage` 现在创建 GridEditor listener、构建项目属性面板，并持有选择、当前画布、撤销/重做、属性更改、overlay action 和 release-time gesture transaction。
- `MainActivity` 保留 ViewFactory/主题、分组专用编辑器、配对选择器、持久化与摘要刷新；画布 gesture recognizer 仍由 `ControlCenterGridEditor` 处理。
- 回归通过 `ControlCenterSettingsPageTest`、`ControlCenterSettingsUiFlowTest` 和 `ControlCenterEditorInteractionTest`，覆盖当前画布隔离、锁定/碰撞拒绝、单次撤销、导入/分组/组合以及取消手势。

## 2026-10-06 T08 OEM 宿主边界

- `ControlCenterHostAdapter.Panel` 负责 content map 有序投影、捕获快照恢复、较新 OEM 分发保护、notify、QSRecord 创建/设置和 tile/component holder 创建绑定。
- `ControlCenterRuntimeGrid.GridState` 仍编排 holder attach/detach/recycle、View 生命周期和内嵌网格测量/绘制；这些顺序留在运行时状态机中。
- `ControlCenterHostAdapterTest`、`ControlCenterRuntimeAvailabilityTest`、`ControlCenterLandscapeRuntimeTest` 和 `ControlCenterRuntimeAnimationTest` 定向回归通过。

## 2026-10-06 T09 材质会话

- `ControlCenterMaterialSession` 接管组件背景、卡片背景和 outline clip 的应用/刷新/恢复，并在 OEM 替换 Drawable 时恢复旧 callback。
- `ControlCenterMaterials` 保留弱会话索引与 native hook 注册，绘制同步转为委托会话；重复释放保持幂等。
- 会话/形状/材质回归通过 `ControlCenterMaterialSessionTest`、`ControlCenterComponentShapeMaterialTest`、`ControlCenterTileSurfaceRegressionTest`、`ControlCenterPublishedMaterialTest` 和 `ControlCenterRuntimeShapeTest`。

## 2026-10-06 T07 页面与画布交互收口

- `ControlCenterSettingsPage` 创建 GridEditor listener、构建项目属性面板，并持有选择、当前画布、撤销/重做、属性更改、overlay action 和 release-time gesture transaction。
- 页面 toolbar 状态操作包含标准/紧凑切换、清空、撤销和重做；Activity 继续提供主题控件、分组专用子编辑器、配对选择器、持久化与摘要刷新。
- `ControlCenterGridEditor` 保留触摸识别和绘制；手势释放后页面以单次会话编辑提交移动/缩放，取消手势不修改草稿。
- 回归通过 `ControlCenterSettingsPageTest`、`ControlCenterSettingsUiFlowTest` 与 `ControlCenterEditorInteractionTest`，覆盖画布隔离、锁定/碰撞拒绝、分组/配对、撤销和取消。

## 2026-10-06 T08/T09/T12 复核

- `ControlCenterHostAdapter.Panel` 接管 content map 投影/恢复、过期代理保护、QSRecord 创建/加入状态和 tile/component holder 创建绑定；GridState 仍编排 holder 回收、View 生命周期及网格测量/绘制。
- `ControlCenterMaterialSession` 接管组件/卡片背景包装、outline clip、原生 Drawable callback 的转移和恢复；`ControlCenterMaterials` 保留弱会话注册及 native hook 协调。
- `DiagnosticLogExporter` 已提供运行时回执、来源范围、脱敏、条目/总容量/总时长限制、截断标记和取消清理；本轮补充总时间预算及总 ZIP 字节预算回归。
- Host、运行时可用性/横屏/动画、材料恢复和诊断归档定向回归通过。

## 2026-10-06 T10 高频 Hook 与反射缓存实验

- `RuntimeReflection` 在已有按实际 `Class` 的正/负缓存之上增加线程局部命中路径，覆盖 `field`、`exact`、`withCount` 和 `compatible` 四类查询；未命中仍回退到带弱引用的类级缓存，类卸载不会被线程局部结果永久保活。
- 快路径的键包含实际宿主 `Class`、成员名、参数类型/数量和 OEM-only 标志；正结果、负结果和重载方法均隔离，避免不同插件 `ClassLoader` 或重载之间串用。
- 材质层级识别继续保留父级动态遍历，避免 View 重挂载后得到过期层级；自身类可直接确定为磁贴/卡片的高频路径提前返回，并在释放时清除对应弱索引。
- 新增 `RuntimeReflectionTest.exactLookupReusesPositiveAndNegativeResultsWithoutCrossingOverloads`，与既有重载、负缓存及继承字段回归共同覆盖缓存语义。定向 Debug 测试：`RuntimeReflectionTest`、`ControlCenterMaterialsTest` 共 28 项通过。
- 本次没有在目标 HyperOS 上采集 Hook 调用计数、CPU 时间或帧 trace，因此不宣称实际帧率/耗电收益；T10 的设备级调用占比与耗时采样留给 T14，同设备、同布局、同刷新率对照后再决定是否继续收窄 Hook 范围。

## 2026-10-06 T13 CI、全量回归与发布核验

- `.github/workflows/validate.yml` 已配置工具回归、Debug/Release 单测、lint、无签名构建和证据归档；本地运行同一验证入口，本地工具回归 24 项通过。当前没有推送分支触发远程 GitHub Actions，因此不声称远程 CI run 已通过。
- 配对幸存项映射修复后，最终源码串行完成 Debug/Release 全量单测，各 907 项通过，失败/错误/跳过均为 0；两套 lint 各 0 错误、65 警告；Debug/Release APK 构建成功。
- 本地验证归档：`artifacts/releases-validation-final/v0.3.174/`；最终本地正式证书归档：`artifacts/releases-final-signed3/v0.3.174/`。后者为 `local_dirty_signed`，Release 证书 SHA-256 为 `ccfaf42f8f14bb36973aeb971572fcc956a256196f68ff088340dfa7eca96fe2`，zip alignment 通过。Debug/Release 各归档 111 个测试 XML，累计 907 项且无失败/错误/跳过。
- Release APK 已安装到 Xiaomi Pad 5 Pro（`b7015e9b`），版本 `0.3.174 / 188`，安装包 SHA-256 为 `cb8670d4721c0b9844420cb6f3c8af3b65431d87bce23e3b1ceb4b2cb1d9ac91`，与最终归档相同。应用概览显示的“系统界面已连接 · 22:09”早于本次安装，属于旧 SystemUI 会话；设备拒绝 shell 直接终止 SystemUI，因此本次新版的注入/功能回执未验证。
- T13 已完成 CI 工作流配置、本地门禁和设备安装核验；远程 GitHub Actions 尚未由 push/PR 触发。正式公开发布仍需在干净、可标记的源码提交上运行不带 `-AllowDirty` 的发布流程。T14 的目标 ROM/GPU/性能对照继续保持未完成。

## 2026-10-06 T11 遥测需求采样与设置面板实验收口

- `TelemetrySampler` 已按需求启停：无指标消费者时不建立轮询；熄屏取消任务并隐藏读数，亮屏广播后恢复；现行 2 秒周期只在屏幕交互期间执行。`TelemetryLifecycleTest` 覆盖亮灭屏和屏幕关闭时启用两条路径，Debug/Release 全量回归均通过。
- 既有同设备设置面板预量实验的首次帧与触摸结束到首次帧结束区间没有稳定改善，代码已保持原有呈现方式，不引入未经测量的 Activity 内面板改造。该实验完成了“保留/回退”的决策，未宣称性能收益。
- T11 已完成实现和本地实验决策；跨版本耗电、GPU 合成及新的设置面板对照仍属于 T14 的设备验收范围。

## 2026-10-06 T14 视觉问题跟踪补充

- 用户设备截图反馈 5G 开启态、右下纵向组合卡片里的三个成员尺寸/裁剪、控制中心滑条和音量弹窗填充描边。该项此前只笼统包含在 T14，后续改动没有逐项登记到拆分验收；现补为明确的 T14 子项。
- 5G 画布预览改为显示真实“5G”标签及开关填充颜色；运行时布局采集识别模块自绘 5G 并读取其状态，避免用原生系统组件猜测状态。
- 组合卡片的预览成员与原生成员保留各自正确的测量根尺寸，但按相同成员槽位比例绘制；不得缩小 OEM 根视图后裁切其固定尺寸子视图。组件槽位几何与其内部 OEM 内容尺寸分别核对。
- 音量弹窗进度填充按原生动态轮廓内缩全局描边宽度；拦截音量进度更新后重用新原生轮廓，描边关闭时恢复最新原生轮廓。控制中心亮度/音量继续使用 `ControlCenterSliderLayout` 内缩填充。
- 本地定向回归覆盖 5G 样式/采集、组合成员预览与运行时槽位缩放、控制中心双方向滑条、音量轮廓动态更新与恢复。v0.3.177 的历史发布只包含首次尝试的 72dp OEM 根测量，导致用户随后报告右下组合内容裁剪；当前跟踪项已撤回该根视图改动，验收要求将无裁剪作为硬条件。
- 用户提供的 `LSPosed_20261006_174035.zip` 记录目标 SystemUI 已加载 `0.3.178 / 192`，控制中心 `border=true`；截图仍显示 5G 深底蓝字及音量填充贴描边。新修正将启用态明确为白底蓝字，并给控制中心、音量弹窗进度遮罩增加 1dp 内侧余量；这属于新修订，不能据旧日志声称已在设备生效。
- 当前源码修改完成后的目标 ROM 安装与最终截图复验仍未执行；T14 保持打开。当前修改必须经新版本完整发布门禁后再交付，不能把旧 APK 视为含有这些修正。

## 2026-10-06 T14 v0.3.180 状态与完整轨道裁剪修正

- 用户最新截图仍显示 5G 深底蓝字、滑条填充侵入轨道圆弧与描边。此前仅增加内缩距离没有修正完整轨道和局部填充的几何差异，不能据此关闭验收。
- `ControlCenterMaterials` 显式识别模块 5G 的磁贴材质层和 `networkEnabled()` 状态，并保留组件自身管理的文字颜色。实际 `GridView.applyMaterials()`、全局刷新及关闭再开启回归在修正前失败，开启态期望 `0xfff8f9fb`，实际被覆盖为 `0xc03c4149`；修正后通过。
- `ControlCenterSliderLayout` 按完整轨道的胶囊、矩形或圆形轮廓内缩描边与缓冲，再与当前进度矩形相交；不再使用 OEM 的局部小圆角作为填充外缘。完整轨道转换到填充视图坐标，原生背景圆角跟随轨道，并保留手势、进度回调和释放恢复职责。
- `SystemMenuHooks` 通过同一 `SliderFillGeometry` 约束音量弹窗填充，保留原生进度分界线；跟随进度 setter 替换的新原生轮廓。只关闭描边时仍按已启用的轨道样式裁剪，关闭音量样式时恢复最新原生轮廓。轨道引用使用弱引用。
- 新增 API 33/34 原生图形像素回归，覆盖横竖胶囊/矩形、0/5/25/50/100% 进度、OEM 轮廓重写和填充视图偏移。修正前可稳定检测到内侧轮廓外的白色像素；修正后通过。音量弹窗另有曲线像素、原生进度分界线和满进度回归。
- 本次修改分别归入 T09 材质状态、既有滑条适配和 T06 系统菜单职责，交付版本 `0.3.180 / 194`；完整构建、签名和门禁结果以 `artifacts/releases-rebuild-20261006/v0.3.180/release.json` 为准。目标用户设备安装、此次注入和最终截图复验尚未执行，T14 保持打开。

## 2026-10-06 T14 v0.3.181 控制中心滑条内缘贴合

- 用户新截图中 5G 开启态已显示白底蓝字，亮度/音量填充的完整曲线已跟随轨道，但填充与描边之间仍有细微深色间隙。
- 控制中心滑条去掉 v0.3.179 引入的额外 1dp 内缩；填充现在只避让描边本身。宽度使用与 `ControlCenterGlobalBorder` 相同的浮点像素计算，避免整数取整在非整数密度下留下额外空隙。
- 完整轨道与进度区域相交的裁剪、原生状态和手势保持既有实现。该次调整归入现有滑条适配与 T14 视觉验收，交付 `0.3.181 / 195`；构建、签名与门禁结果以 `artifacts/releases-rebuild-20261006/v0.3.181/release.json` 为准。目标设备的新包注入和内缘贴合复验仍待完成。

## 2026-10-06 T14 v0.3.182 音量菜单内缘贴合

- 根据用户音量菜单截图，将控制中心滑条已使用的内缘贴合规则同步到音量菜单。
- `SystemMenuHooks.applyColumn()` 和原生进度 setter 后的 `refreshVolumeProgressOutline()` 均去掉额外 1dp 缓冲，只按实际浮点描边宽度裁剪填充；动态变化仍保留完整轨道曲线和原生进度分界线。
- 更新既有 API 33-35 内缘、动态更新/恢复回归和曲线像素回归，确认填充到达描边内缘且不覆盖描边。该修正归入 T06 系统菜单和 T14 视觉验收，交付 `0.3.182 / 196`；构建、签名与门禁结果以 `artifacts/releases-rebuild-20261006/v0.3.182/release.json` 为准。目标设备最终截图复验仍待完成。
