# Fusion 通知语义、原生回退和几何更新候选

日期：2026-11-16，Asia/Shanghai。版本保持 `0.5.27 / 241`。

本地已在独立源码副本完成候选修改、回归测试源码和静态独立审查。**测试构建未由本地执行**；不能把候选称为已修复、已通过或已解决实机卡顿。测试、lint、编译、构建及复测由小智指定的 GPT-6 Astra ultra 统一执行，冻结后本地等待反馈。

## 源码与隔离

| 项目 | 本轮实际状态 |
| --- | --- |
| 原活跃目录 | `E:\vs\claude\xtjm\fusion-statusbar-publish-v0.3.197` |
| 原 HEAD | `fe7bb9d230781985779e11e7151fc509acffd59f`，`main`，大量未提交及未跟踪源码 |
| 比较快照 | `fusion-statusbar-publish-v0.5.27`，`000ef04a0931c342f44fd275105cbbc9eed93f6d`，核对时干净；没有用它覆盖活跃源码 |
| 唯一实现副本 | `E:\vs\claude\xtjm\fusion-readability-20261116-01\work` |
| 修改前逐字节基线 | 同级 `baseline`，不含 `.git`，包含相关未提交和未跟踪源码 |
| 来源证据 | 同级 `evidence/source-manifest.json`，686 个文件、5,936,145 字节；逐文件 SHA-256；`source-status-before.bin` 与 `source-tracked-before.patch` |
| 排除内容 | 110 项历史 artifacts、临时补丁/日志；另不复制忽略的构建缓存、签名密钥、凭据、`.git`。`local.properties` 仅在副本生成 SDK 路径，不进入补丁或冻结输入清单 |
| 指令核对 | 已检查目录祖先及源码 `app/docs/tools/third_party` 的适用 `AGENTS.md`，未发现；原项目未发现 `.agents/skills` |

原目录未编辑，未执行 reset、clean、stash、提交、合并、推送、发布、上传、签名或真机操作；未启动云电脑或改变模型/推理设置。冻结证据见同级 `delivery/freeze.json`，其来源校验结果来自逐文件哈希与 Git 只读状态比较，属于完整性核对，不是测试执行。

八个指定核心文件在活跃目录和干净快照的 SHA-256 均相同。已完整阅读 `release-notes-v0.5.13.md` 和 `2026-10-25_glass-soft-edge-bugfix.md`；历史 221/32 等数字不作为本轮结果。

## 证据、发现与处理

| 本地静态证据 | 发现 | 本轮路径 |
| --- | --- | --- |
| baseline 的 `NotificationSurfaceHooks.applyRow → SystemSurfaceStyle.applyContent → content` | 所有被遍历 TextView 会统一着色、缩放，首次缓存会在重新绑定后恢复过时文字状态 | 通知退出通用内容着色，保留宿主文字颜色、ColorStateList、字号、图标与动作 |
| `ControlCenterSurface.HIGHLIGHT_GLASS.color` 与通用白色前景 | 亮背景可读性有静态风险，不能仅将全局前景改黑 | 仅通知自动高光在 OEM 模糊未知时原生回退，不更改全局前景 |
| `SystemUiHooks` 在私有 setter 调用前拦截请求 | setter 存在/安装、请求已观察不证明 compositor 有效 | 诊断分别记录通知适配器状态、OEM hook/request 是否观察到、availability unknown、native fallback |
| baseline 的 row height 与 background geometry setters 都完整 applyRow | 内容遍历、背景解包重挂、原生请求重播处于高频路径 | 编写真实注册 Hook 的计数测试；实现有所有权与配置保护的即时 bounds 路径，等待指定测试方先量化 |
| 独立静态审查的外部背景更新/内部 Drawable 清空分支 | 直接关闭可能覆盖新背景，自绘切换可能被旧材质遮挡 | restore 先接纳新外部背景；内部 null 时不画旧材质并放行宿主 onDraw；新增测试源码 |

上述是源码事实与候选处理路径，没有本轮目标 ROM 复现或设备成功证据。

## 已实现的范围

### A：通知内容与材质分离

`NotificationSurfaceHooks` 不再调用通用 `applyContent`。通知中的原生正文、强调色、禁用/按压态、应用自定义 RemoteViews、媒体文字、图片 tint、字号及点击动作全部交给宿主。关闭不会把旧的文本缓存写回。未修改控制中心活动态/普通态、自定义色或菜单的通用内容逻辑；悬浮通知和通知栏开关独立。

旧 `ControlCenterAdaptiveGlassConsumersTest` 中“蓝字变白”的断言保留为**菜单**契约，并明确命名；通知的新契约由 `NotificationContentSemanticsTest` 单独约束。包括真实 MessagingStyle/MediaStyle 模板、RemoteViews、stateful colors、重新绑定、同 uiMode 主题变化和离屏重用。测试源码尚未编译或运行。

### B：仅通知自动高光的可逆降级

新增 `SystemSurfaceStyle.applyNotification`，原始配置选择仍保持 HIGHLIGHT_GLASS。由于目前没有可靠 OEM compositor 可用性信号，通知自动高光的**所有正强度**先使用完整原生背景与原生效果请求；30 和 100 均处于同一原生回退状态，不再将原生背景乘成半透明。0 仍是既有显式透明端点；CLEAR 和手动 SOFT_GLASS 沿用原分支。正强度与 0 的外观可能有跳变，这是原生回退的兼容边界，必须由指定测试方和设备验收方确认。

有可读宿主 Drawable 时仍经现有 SurfaceDrawable/BoundedBackground 保存实际高度、RTL、展开边界和 alpha；原生 alpha 只应用一次。内部原始 Drawable 更换时跟随最新值；直接外部 setBackground 后关闭也保留新值。原始 View.background 为 null 时关闭仍恢复 null。没有可读 Drawable 时放弃材质所有权，让宿主自行绘制；内部 Drawable 后续变为 null 时也不绘制旧内容，并放行 onDraw。

原生回退不新造通用灰底，不证明宿主本身满足对比度，也不修改宿主自定义通知设计。用户单独选择的边框仍按原配置绘制。

通知 hook 是否已安装与 OEM 私有模糊 hook 不混用。`notificationBlurStatus` 只把拦截到的 OEM 调用记为 hook/request observed，getter 初始值不算请求；availability 始终 unknown。外层通知适配器缺失仍报 surface_hooks_unavailable。刷新汇总不会把回退 DEGRADED 覆盖为 surface_applied，通知栏与悬浮通知独立上报。

### C：静态热路径候选

row 的 `setActualHeight` 与 background 的 `setActualHeight/Width`、`setExpandAnimationRunning/Size`、`setClipTop/BottomAmount` 走 `safelyUpdateBounds`。只有当前 ControlCenterConfig 引用、heads-up 状态、启用状态、owner、background、bounded/content 与 SystemSurfaceStyle 所有权均匹配时，才直接 invalidate；其余回到完整 applyRow。tint/radius/customBackground、onLayout、injector、heads-up、pinned、配置 refresh 继续完整处理。

不做同帧延迟合并，不复制几何或 alpha，不重复建立 shader、轮廓、Grid dirty 缓存。BoundedBackground 每次 draw 仍实时读取实际高度、宽度、RTL、展开和裁剪/native alpha。

计数测试会分别打印完整 apply 的 120 次对照和 120 次真实 geometry Hook 回调，包含背景赋值、native replay、子树读取和宿主 setter 次数。**尚无实测计数改善**。基线测试可由指定测试方在另外的测试副本加入新增几何测试后，筛选 `fullApplyBaselineReportsTheCostOf120Refreshes` 运行；不得修改保存的 baseline 本身。预计完整 apply 为 240 次背景赋值、480 次四项 native replay；这些只是 fixture/源码推导，不是已取得证据。阶段 A 的内容遍历变化须与 C 的几何收益分别报告。

## 暂缓与限制

- 不增加真实 MiGlass、折射、私有后端能力猜测或大幅视觉重设计。Canvas 向内软边、共同裁剪、主题恢复与 shader 缓存仍在原通用渲染器，并由既有测试的明确 generic Canvas fixture 保留覆盖。
- 未将公共 `isCrossWindowBlurEnabled` 作为 OEM 私有能力；本轮未给菜单公共窗口新增 listener，也未改变控制中心、MaterialSession 或 OEM `275 * strength` 半径。阶段 B 的“公共窗口动态 listener + 控制中心整体降级”仍未完成；需要独立运行域设计和设备证据，不能称整个阶段 B 已验收。
- 本轮 OEM availability 没有 AVAILABLE 分支；自动通知玻璃正强度会持续原生回退，直到另有可靠能力来源。
- 不保证宿主自身/custom 内容的颜色达到 4.5:1，不因保留原生就声称可读性已解决。
- C 尚未经过“测量后确认优化”的验收；它是可独立审查、可回退的静态候选，是否保留由指定测试方的计数和回归决定。

## 测试与审批状态

2026-11-16 分工更新后，本地及子代理没有运行测试、lint、编译、Gradle、构建、打包或签名。早于更新的 `a-red` 命令退出 1，止于 **Could not connect to the Gradle daemon**，没有执行任何业务测试或编译，不能称为 RED。其原始日志和命令存于 `evidence/a-red.log`、`a-red-command.json`。

`a-red-unsandboxed` 是更新前发起的提权重试，仍待审批、尚未获准；本地未批准、未关闭审批卡、未换命令绕过。没有该命令的执行结果。不要把历史日志、缓存或 UP-TO-DATE 当作本轮执行。

| 验证 | 本轮状态 |
| --- | --- |
| 本地业务 RED/GREEN | 未执行；失败只在 Gradle 连接阶段 |
| 新增/修改测试编译与运行 | 未执行；待小智指定模型 |
| 全量 Release 单测、Python 工具回归 | 未执行 |
| Release lint / R8 assembleRelease | 未执行 |
| 合成视觉矩阵、对比度采样 | 未执行；交付 48 格待验收清单 |
| 真机视觉、动作、稳定性、Perfetto | 未执行，禁止本地操作真机 |
| 源码逐文件哈希与静态独立审查 | 已执行完整性核对与阅读；最终冻结记录见 delivery |

## 交给指定测试方的命令与依赖

以下只是建议，**本地未执行**。工作目录为唯一副本 work。JDK 21；Gradle wrapper 8.13；AGP 8.13.2；Kotlin/Compose plugin 2.3.20；Android SDK platform 36、build-tools 36.0.0；compileSdk 36、targetSdk 35、minSdk 33。依赖锁定文件、wrapper SHA-256、第三方许可证均来自副本原配置。没有新依赖、新权限、版本变更或签名需求。

指定执行方须先确认其他项目没有重构建，再串行运行，并把缓存/临时产物留在本项目 E:。可复用同级已准备的 `gradle-home`；它只复制既有 wrapper 和 modules-2，未复制全局 gradle.properties、init 脚本或凭据；依赖是否齐全尚未核验。Robolectric 运行时依赖也可能仍需执行方配置可写缓存。不要运行 release.ps1 的正式签名流程。

```powershell
Set-Location 'E:\vs\claude\xtjm\fusion-readability-20261116-01\work'
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot'
$env:GRADLE_USER_HOME = 'E:\vs\claude\xtjm\fusion-readability-20261116-01\gradle-home'
$env:TEMP = 'E:\vs\claude\xtjm\fusion-readability-20261116-01\tmp'
$env:TMP = $env:TEMP
$env:ANDROID_USER_HOME = 'E:\vs\claude\xtjm\fusion-readability-20261116-01\android-home'
.\gradlew.bat :app:testReleaseUnitTest --tests '*NotificationContentSemanticsTest' --tests '*NotificationBlurFallbackTest' --tests '*NotificationGeometryDispatchTest' --tests '*NotificationGlass*' --tests '*SystemSurfaceStyleTest' --tests '*ControlCenterGlass*' --tests '*ControlCenterAdaptiveGlassConsumersTest' --no-daemon --max-workers=2 --console=plain
.\gradlew.bat :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease --no-daemon --max-workers=2 --console=plain
& 'F:\python.exe' -m unittest discover -s tools/tests -v
```

记录实际命令、退出码、每个 Task 的实际执行/缓存状态、测试 XML 与日志。若出现不相关基线失败也按名称回报，不能静默略过。需要比较业务 RED 时，指定测试方应在另一测试副本叠加新增测试到原基线，保留 baseline 只读；通知契约修订后的候选不能拿旧白字断言充作有效 GREEN。

## 视觉、动作与性能验收

完整 48 格列于本目录 `2026-11-16-notification-visual-matrix.csv`：浅/深主题 × 纯亮/纯暗/高频照片/高饱和背景 × 模糊有效/失效 × 折叠/展开/悬浮。所有格均为未执行。合成绘制只代表本地 Canvas，无法验证 ROM 合成器真实模糊；当前 OEM availability unknown 候选应始终回退，不能靠测试注入 public-window=true 冒充私有模糊已有效。

设备追加清单：

- 普通、Messaging、Media、应用自定义 RemoteViews；正文/强调色/禁用按压态/图标与宿主一致，点击、媒体控制和 inline reply 都可用。
- 实际高度、RTL 宽度、展开裁剪、native alpha 255/128/0、完全裁剪、展开启动动画；反光不得越界或延迟一帧。
- 悬浮转通知栏、animating-away、pinned；分别关闭 notification/heads-up 再切换，配置必须即时生效。
- 切主题不重绑、同 uiMode 更换主题、离屏复用、新应用内容重新绑定；宿主更换内部/外部 Drawable，以及内部 Drawable 清空转自绘。
- 0/30/100 强度、CLEAR、手动柔光、再回自动、最后关闭；只清背景不隐藏前景，关闭恢复最新宿主背景/效果，不形成自引用。
- 正文实际对比度目标至少 4.5:1，大字至少 3:1；失败记录具体宿主、前景颜色和背景，不能静默全局改色。
- Release 在目标 ROM 的 60 Hz 与 120 Hz，分别冷/热下拉、连续展开、磁贴切换、编辑进出，采集 Perfetto FrameTimeline；分别看 SystemUI/UI、RenderThread、SurfaceFlinger 的 p50/p95/p99 与 jank，并保留输入场景、配置、原版和候选 trace。没有 trace 就没有“实机流畅性已改善”的结论。

## 审阅补丁与人工迁回

同级 `delivery/fusion-notification-candidate.patch` 只比较 baseline 与 work，不包含原活跃目录已有的其他改动；`file-changes.json` 给出逐文件旧/新 SHA-256，`frozen-inputs.json` 给出完整候选输入清单，`freeze.json` 记录原目录保持情况与交付哈希。`delivery/changes` 存放变更文件，均无签名材料。

由小智完成复测并取得整合授权后，迁回方先核对补丁旧 SHA-256 与当前活跃文件；任何文件已变化都须逐文件三方审阅，禁止直接覆盖。可先在另一临时审阅副本执行 `git apply --check`，再人工审阅/整合。该说明不授权本地自动合并；本地冻结后停止修改，等待反馈。

## 官方参考

已访问并用于界定平台规则，非本地/设备验证证据：

- Android Dark theme：<https://developer.android.com/develop/ui/views/theming/darktheme>
- Custom notification：<https://developer.android.com/develop/ui/views/notifications/custom-notification>
- AOSP Window blurs：<https://source.android.com/docs/core/display/window-blurs>
- Android accessibility：<https://developer.android.com/guide/topics/ui/accessibility/apps>
- Perfetto FrameTimeline：<https://perfetto.dev/docs/data-sources/frametimeline>
