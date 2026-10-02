# 优化实施记录

实施范围：执行 [优化计划](optimization-plan-20261001.md) 中的本地代码、测试、工程与发布工作。按用户要求排除设备连接、安装、实机注入与性能采样。

## 基线

- Git 基线提交：`42c81b6`，标签 `baseline-v0.3.129`。
- 强制重新执行 300 项原有单元测试，失败和错误均为 0。
- 原 APK、测试 XML 和 lint 报告保存于 `artifacts/baseline-v0.3.129/`。
- 已索引 `xtjm` 顶层 71 份 APK、日志包、截图及分析文件，原文件保留。

## 进度

| 工作 | 状态 |
| --- | --- |
| Git 基线、证据索引、README | 已完成 |
| Gradle 单一版本源与发布校验脚本 | 发布演练通过，版本与 v2 签名核验成功 |
| 工具链与 LibXposed 自定义 lint 兼容 | 已通过隔离检查 |
| 草稿会话、恢复、撤销与重做 | 已完成，本地回归通过 |
| 保存失败、原子配置与跨进程回执 | 已完成，本地回归通过 |
| 预览代次、不可变文件与后台解码 | 已完成，本地回归通过 |
| Hook 安装状态、故障清理与反射缓存 | 已完成，本地回归通过 |
| 页面和运行时职责拆分 | 分批进行；已有编辑、设置、预览及运行时模块，本轮提取设置启动协调器 |
| 熄屏遥测与不可见时钟调度 | 12 项 Android 13/15 定向测试已通过 |
| 最终全量测试、lint、签名与归档 | 369 项测试通过，lint 0 错误，签名通过 |
| 手机安装、OEM 效果与性能测量 | 按用户要求排除 |

## 实施约束

继续使用 Java 和原生 View，保留已有布局格式兼容、原生编辑、像素与行为回归。源码版本更新为 `0.3.130 / 144`。新配置文件使用 schema 4，旧 SharedPreferences 作为迁移来源；未修改旧 APK 与原始日志。

工具链升级为 AGP 8.9.3、Gradle 8.11.1、Robolectric 4.14.1，JDK 仍为 17。Gradle 分发带固定 SHA-256，图标导入固定上游提交。与业务实现分开的工作树用于验证工具链和节能调度。

性能相关本地证据包括队列有界、过期回调拒绝、减少重复反射、按需磁贴资源与停止不可见定时任务。没有硬件采样数据，因此不宣称帧率、CPU 或耗电降低了某个百分比。

## 最终验证

完整发布命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\release.ps1 -JavaHome $env:JAVA_HOME -AndroidSdk $env:ANDROID_HOME -GitPath git
```

- 55 个测试套件、369 项测试全部通过，失败、错误和跳过均为 0。
- lint 0 错误、56 项警告，LibXposed 自定义 lint 的旧 API 兼容提示已消除。新增高风险项按文件门禁检查，必要的 OEM 反射未被统一屏蔽。
- APK Manifest、Xposed 元信息、生成的 Hook BuildConfig 均为 `0.3.130 / 144`；APK v2 签名核验通过。
- APK SHA-256：`d684c02ecccc05bc74ad1392af79a06dfa3ed1057f5c648b1a4bade6fb312c2e`。
- 正式归档目录：`artifacts/releases/v0.3.130/`，包含 APK、测试报告、lint、签名结果、源码文件指纹和 `release.json`。
- 本地检查浅色/深色 360dp 控制中心截图，修复新增重做按钮后添加按钮换行不齐的问题；这些是 Robolectric 输出，不是手机截图。

初次集成的失败已处理：schema 断言更新、异步完成等待、测试实例隔离，以及 Windows JVM 与 Android 覆盖式原子重命名的差异。`AndroidAtomicFileShadow` 仅在测试中模拟 Android 的原子替换语义；生产实现保留原子写入和写后字节核验。预览不完整写入、迟到回调、并发请求和文件不可变性测试均通过。

## 改动结果

- `ControlCenterEditorSession` 管理草稿、选中项、模式和有界撤销/重做；独立草稿存储不发布配置。
- `FusionConfigRepository` 合并后台读写并隔离会话回调；`FusionConfigStore` 保存原子快照，旧配置迁移和失败重试有覆盖。
- 应用回执包含配置修订号、独立功能状态、模块版本及运行时实例身份；相同修订号的 SystemUI 重启也会清除旧回执。
- `ControlCenterPreviewStore/Loader/Capture` 分别负责原子发布、后台加载和窗口采集，使用代次拒绝过期结果。
- `RuntimeHookRegistry/ObserverRegistration/Reflection/ConfigCoordinator/WorkQueue` 隔离安装、注册、反射、配置应用与工作队列；缺失电池适配不会阻断独立时钟功能。
- `SettingsPageController` 提取七个设置对话框，配置接口只有读取和更新两个方法；`SettingsDialogController` 管理弹层尺寸和生命周期。MainActivity 从 4,941 行减少到 4,059 行，保留其余控制中心画布与导航的现有行为。
- 遥测熄屏后取消周期任务，亮屏重建流量基线；过期屏幕状态回调被拒绝。两个时钟共用可见性调度，隐藏/熄屏/分离时停止秒级刷新。
- 通知时钟的原父节点与临时堆栈改为弱引用，避免静态状态持有旧视图树；反复启用和恢复原生父节点/顺序的回归通过。

配置备份、布局预设、进一步按实测热点优化和新增 ROM 适配仍按原计划属于后续评估项。本轮没有迁移 UI 框架，也没有实施实机验收。

## 当前进度：0.3.134 / 148

- 从 `MainActivity` 提取 `SettingsStartupCoordinator`，集中负责配置异步读取、控制中心草稿恢复、Activity 就绪通知、单个延迟 Activity 结果回放及销毁后的迟到回调屏蔽。
- Activity 保留视图初始化、设置数据绑定和启动遮罩移除；协调器通过窄 `Pipeline` 与 `Host` 接口组织调用，不接管页面渲染或编辑策略。
- 新增协调器单测覆盖恢复顺序、重复读取防重入、就绪回调 exactly-once、延迟结果回放、Host 销毁和关闭后的迟到回调；冷启动页面回归继续覆盖最终进入概述页。
- 本地目标设备为 API 36 模拟器的既有冷启动检查仍对应 `0.3.133`，本次未重做模拟器检查；实机安装、注入及 HyperOS 验证仍未执行。
- 阶段 3 仍在进行。建议下一项拆分 SystemUI 主状态栏 Hook 安装组，将电池图标、状态栏容器、电话状态栏和锁屏状态栏的探测/注册从 `SystemUiHooks` 移出，同时保留回调、成功句柄状态、重试和应用状态判断在现有入口，并补充隔离安装测试。

本版本定向验证：`SettingsStartupCoordinatorTest`、冷启动和后台恢复流程、壁纸工作区清理及 `EditorPlatformMatrixTest` 通过。完整测试、lint、构建和发布清单以本版本发布归档为准。

## 当前进度：0.3.135 / 149

- 从 `SystemUiHooks.install()` 提取 `StatusBarHookInstaller`，负责状态栏电池视图、图标容器、电池容器、电话状态栏和锁屏状态栏的目标解析与 Hook 编排。
- installer 通过 `ClassResolver`、`Registrar` 和 `Hookers` 窄接口工作，不持有 Xposed 模块、全局句柄表、配置或视图状态；`SystemUiHooks` 继续负责实际业务回调、成功句柄、能力判断、失败重试和两个运行时类引用。
- 新增状态栏 installer 回归，覆盖四组独立安装、重复安装去重、目标类缺失、单方法缺失、失败项重试和不同 ClassLoader 的方法隔离。
- 本版本只完成源码、单元测试、lint、构建和签名归档；实机安装、LSPosed 注入、HyperOS 验证和设备性能采样仍未执行。

阶段 3 仍在进行。下一项候选是继续整理控制中心插件 Hook 安装组，但需先保持现有插件 ClassLoader 跟踪、延迟重试和全局能力回执行为不变。

## 当前进度：0.3.136 / 150

- 新增 `ControlCenterPluginHookInstaller`，以实际插件 ClassLoader 为输入，集中解析插件类、匹配重载方法并声明列表、布局、组件、形状和视觉 Hook。
- 通过窄接口注入类解析、真实 Hook 注册和业务 Hooker；全局材质 Hook 在原来的分组边界触发，仍由 `SystemUiHooks` 安装。
- 插件 ClassLoader 工厂探测、弱引用 loader 跟踪、延迟重试、全局能力状态及配置回执留在 `SystemUiHooks`。布局可用条件保持为 grid content、component bind、tile list、tile bind 四类 Hook 均成功。
- 新增测试覆盖必需 Hook、不同 ClassLoader 的独立方法注册、缺失类/方法后的独立安装、已注册句柄去重和失败项重试。
- 已知共享状态语义保持原状：插件就绪标志和 Hook 失败检查仍是全局状态，本次只迁移安装职责，没有调整多 loader 能力汇总或重试范围。
- 本版本只进行本地测试和打包，不执行实体手机安装、LSPosed 注入、HyperOS 验证或设备性能采样。

阶段 3 后续可评估 OEM 主系统 Hook 组或运行时生命周期边界；涉及设备行为的问题仍需留待实机验收。

## 当前进度：0.3.137 / 151

- 新增 `NotificationHookInstaller`，按 SystemUI ClassLoader 解析通知头部、展开控制器、展开回调、通知列表和原生网速视图的目标方法。
- installer 回传展开时钟字段、通知列表状态字段链和 `updateTopPadding` 方法，`SystemUiHooks` 继续持有这些运行时引用并执行时钟、列表偏移、控制中心判定和遥测回调。
- 保留通知 Header 同名声明方法的 Hook、原有 Hook ID、展开回调参数筛选和网速可见性精确签名；列表 `updateTopPadding` 与 `onLayout` 独立注册，单方法缺失不阻断同组其余能力。
- 新增测试覆盖完整安装和字段回传、缺少 Header 的独立安装、错误重载过滤、失败 Hook 重试及重复安装；字段探测不计入成功 Hook 数量。
- 本版本只进行本地测试、lint、构建和签名归档；实体手机安装、LSPosed 注入、HyperOS 验证和设备性能采样仍未执行。

阶段 3 的 Hook 安装职责拆分已覆盖设置启动、状态栏主组、控制中心插件组和通知中心组。后续优先处理运行时生命周期或回执状态的进一步边界，不在未测量前宣称动画帧率改善。

## 当前进度：0.3.138 / 152

- 从 `SystemUiHooks` 提取 `RuntimeConfigLifecycle`，集中管理首次启动、application Context、配置读取与应用失败退避、observer 注册调用、observer reload/preview 接线和应用回执入口。
- 现有 `RuntimeConfigCoordinator` 继续负责异步读取、请求代次合并、修订号单调性和确认回执；`RuntimeObserverRegistration` 继续独立管理订阅注册失败重试。
- `SystemUiHooks` 保留 `applyLoadedConfig` 及其状态栏、通知时钟、控制中心运行时副作用。首次 Context 选择与 coordinator/observer 初始化仍在宿主锁内完成；生命周期对象使用安全发布。
- 新增生命周期测试覆盖并发首用只创建一组运行时、observer reload/preview 接线、报告上下文、2/4/8/16/30 秒退避、应用失败重试及调度器拒绝后的状态恢复。
- 本版本仅进行本地验证与 APK 归档；实体手机安装、LSPosed 注入、HyperOS 验证及设备性能采样仍未执行。

## 当前进度：0.3.139 / 153

- 新增 `LatestRootRefreshScheduler`，集中管理状态栏刷新请求的弱引用、最新 root 合并、单个主线程 drain 和 drain 期间的续排；实际布局与回执仍由 `SystemUiHooks` 执行。
- 主线程 Handler 拒绝或抛错时恢复空闲状态并保留弱引用待处理 root，等待后续请求重试；单次刷新抛错也不会永久阻塞后续刷新，不立即循环重试。
- 新增纯 Java 单测覆盖多请求合并、执行期间重入请求、弱引用清空、post 拒绝/抛错以及刷新异常恢复。
- 本版本只进行本地验证、lint、构建与签名归档；未进行实体手机安装、LSPosed 注入、HyperOS 验证或设备性能采样。

## 当前进度：0.3.140 / 154

- 新增 `BoundedHookRetry` 统一承载 fixed-delay、有界次数、单 pending 任务与完成条件检查；SystemUI 全局 Hook 保留 4 次预算，控制中心插件安装保留 8 次预算，间隔均为 3 秒。
- 两类重试状态分别实例化，仍使用各自原有触发条件；全局 Hook 失败仍由 `RuntimeHookRegistry` 真实 handle/failure 状态判断，插件重试仍遍历已跟踪 ClassLoader 并用原全局插件 ready 状态判断。
- 调度器拒绝或抛错时释放 pending 状态和本次预算消耗；安装尝试完成后按条件决定是否继续。新增测试验证延迟、预算、停止条件、调度失败恢复和策略隔离。
- 本版本只进行本地验证、lint、构建与签名归档；没有实体手机、LSPosed 或 HyperOS 实机验证，也没有性能采样。

## 当前进度：0.3.141 / 155

- 将设置页的 `Spinner` 系统下拉改为面板内展开的选择列表；选择不会再创建独立下拉窗口或嵌套 `Dialog`，原有配置写回语义保持不变。
- 选择项展开后自动滚入设置面板视口；禁用的双排布局选项保持收起，选中后显示标记并仅在值变化时调用配置回调。
- 新增回归覆盖原设置窗口内展开、取消、当前值重选不写回、禁用状态和小屏大字显示，详见 [设置选择器记录](settings-choice-v0.3.141.md)。
- 本地完整测试通过：585 项通过，失败/错误/跳过均为 0；lint 0 错误、60 警告，4 项高风险 lint 规则与既有策略相符。Debug APK 已构建，签名、Manifest、BuildConfig 与 Xposed 元数据均核对为 `0.3.141 / 155`；SHA-256 为 `f657556acd38f6a66f213a723bdf8f46a17db9316b73f1fc9913197a9e081995`。
- APK 位于 `app/build/outputs/apk/debug/fusion-statusbar-v0.3.141-debug.apk`。PowerShell 执行策略阻止运行 `tools/release.ps1`，未覆盖该策略，因此本版未生成正式 `artifacts/releases/` 归档。
- 本地验证只能确认控件结构与布局行为，不能代表目标机帧耗时改善；本版本仍不做实体手机安装、LSPosed 注入、HyperOS 验证或实机性能采样。

## 当前进度：0.3.142 / 156

- 将控制中心编辑面板中的卡片样式、组合卡片排列/背景和磁贴形状 Spinner 改为面板内展开选择器，避免编辑窗口与系统下拉窗口叠加。
- 保留卡片样式“自定义”不可选、项目锁定禁用并收起、组合编辑按当前草稿提交、磁贴形状五项宽高映射和单次撤销语义；当前值重选不会写草稿。
- 更新控制中心编辑流与浅色/深色视觉回归，详见 [控制中心选择器记录](control-center-choice-v0.3.142.md)。
- 本地完整测试通过：585 项通过，失败/错误/跳过均为 0；lint 0 错误、60 警告，4 项高风险 lint 规则与既有策略相符。Debug APK 已构建，签名和 Manifest 核对为 `0.3.142 / 156`；SHA-256 为 `e2326e4e18dca6b62130754004f5a9408ac2d0cdcf65318137d1f1221180f201`。
- 已在 API 36 `IceBridge_API36` AVD 安装并启动 APK，UI 层级确认应用和控制中心页可显示；设置开合脚本因 PowerShell 执行策略无法运行，未绕过策略。该 AVD 观察不替代目标手机帧耗时或实机验收。
- APK 位于 `app/build/outputs/apk/debug/fusion-statusbar-v0.3.142-debug.apk`；实机安装、LSPosed 注入、HyperOS 验证和设备性能采样仍未执行。

## 当前进度：0.3.143 / 157

- 将主设置页“状态栏监测”中的三个位置 `Spinner` 改为面板内展开的 `SettingsChoiceSelector`，位置选项与遥测开关保持同一设置页窗口内交互。
- 保留原有配置写回、当前值同步和遥测项关闭时的位置控件禁用语义；重新选择当前值不会写回配置，选择新位置仍只触发一次配置更新。
- 新增遥测位置选择器回归，检查控件树中不含 `Spinner`，启用遥测项后可在原窗口内展开并更新对应位置配置。
- 本地完整测试通过：586 项通过，失败/错误/跳过均为 0；lint 0 错误、60 警告。Debug APK 已构建，Manifest、Xposed 元数据和 APK v2 签名核对为 `0.3.143 / 157`；SHA-256 为 `B3677EF85ADC75AC73CA9878A429658EB7B6A19A9D53574CFA00CF26102236BC`。
- APK 位于 `app/build/outputs/apk/debug/fusion-statusbar-v0.3.143-debug.apk`。PowerShell 执行策略仍未绕过，未生成正式 `artifacts/releases/` 归档；不执行实体手机安装、LSPosed 注入、HyperOS 验证或设备性能采样。

## 阶段 4 下一步：性能基线准备

- 增强 `tools/measure-settings-jank.ps1`：每轮可指定独立 `OutputName`，同时保存 trace 和机器可读 JSON 摘要，避免基线与修改版证据互相覆盖。
- 新增 [性能基线准备记录](performance-baseline-v0.3.143.md) 和 [静态指标记录](performance-baseline-preparation-20261001.md)，分别固定模拟器采样矩阵、30 次轮次、输出字段，以及当前源码热点候选。
- 本轮检查时 `adb devices` 没有在线设备，因此未运行采样脚本；没有新增帧耗时、CPU、内存或后台唤醒结论。实体设备验证继续排除。
- 统一两份基线记录的职责：静态热点记录保留源码指标，性能基线记录固定场景矩阵；采样命令改为每轮使用独立 `OutputName`，JSON 同时保存原始 `gfxinfo` 摘要。

## 0.3.143 设备验证补充

- 在 `b7015e9b`（Xiaomi Pad 5 Pro，Android 16 / API 36，1600×2560，360 dpi）安装并启动 `0.3.143 / 157`；应用概览显示“已激活 / 系统界面已连接”，LSPosed 管理器显示模块已启用，`com.android.systemui` 与 `miui.systemui.plugin` 作用域均已勾选。
- 重启设备后 `lspd`、`zygisk_lsposed` 和 `com.android.systemui` 均恢复运行；重启 SystemUI 后应用回执仍显示系统界面已连接，确认本版模块注入链路可用。
- 冷启动实测保存于 `build/perf/real-v0.3.143-cold-start.*`：201 帧，Janky 2（1.00%），50/90/95/99 分位 8/11/18/250 ms；最终页面为概述且显示“已激活”。
- “状态栏监测”连续开合 5 次保存于 `build/perf/real-v0.3.143-telemetry-open-close.*`：215 帧，Janky 3（1.40%），50/90/95/99 分位 8/11/21/250 ms；trace 仅有 5 次首次 `resize=true`，未发现显示后的 `first=false/resize=true` 二次窗口布局。
- 实机 UI hierarchy 确认遥测位置选择器在原设置窗口内展开，选中项显示“已选择”，没有额外下拉窗口；应用 PSS 约 70,947 KB，SystemUI PSS 约 223,602 KB。以上数据是该设备当前一次采样，不构成跨版本性能提升百分比。

## 用户指定跳过测试后的交付状态

- 已跳过完整单元测试、30 次实机性能矩阵和后续回归测试。
- 已生成 [设备验证记录](device-validation-v0.3.143.md)，并整理现有 APK、签名信息和实机 trace、gfxinfo、UI hierarchy、内存与 CPU 产物。
- 正式 `tools/release.ps1` 未运行，因为它会强制执行测试；本次仅生成单独的设备验证包，不声明测试门通过。
- 设备验证包位于 `artifacts/validation/v0.3.143/`，包含 APK、Manifest badging、签名输出、Xposed 元数据和已有实机采样文件；正式 `artifacts/releases/v0.3.143/` 不生成。

## 恢复完整验证后的实机基线

- 实体设备 `b7015e9b` 显式使用 `-AllowPhysicalDevice` 完成两组 30 次开合，脚本核对 serial 在线、`sys.boot_completed=1`，并保存独立 JSON/trace。
- “状态栏监测”：2508 帧，Janky 3.15%，50/90/95/99 分位 7/17/93/150 ms；“状态栏布局”：1588 帧，Janky 3.84%，50/90/95/99 分位 10/21/77/105 ms；两组显示后二次 resize 均为 0。
- 两组结果只作为该设备当前版本的基线，尚未据此修改性能代码；随后执行本地完整单测、lint、构建和签名核对。

## 阶段 4 慢帧诊断结论

- 对 0.3.143 两组 30 次 trace 做了标准库解析；每次新设置窗口的首个 `doFrame` 都包含首次 `relayoutWindow`、`traversal`、测量和初次绘制。
- 遥测设置首帧 wall-clock 平均约 91.3 ms、P95 99.9 ms；布局设置平均约 70.9 ms、P95 82.3 ms。普通后续帧 P95 分别约 7.0 ms 和 14.7 ms。
- 两组 trace 均有 30/30 首次窗口事件、无未配对结束或未完成开始事件，且没有显示后的二次 `resize=true`。这些是 wall-clock trace 时间，不是 CPU 时间或实际显示器 present 时间。
- 已核对设备重启前后模块版本：安装 `0.3.144 / 158` 时旧 SystemUI 仍回报 `0.3.143 / 157`；完整重启后 PID 从 3754 变为 3799，回执变为 `0.3.144 / 158`。后续实测必须以完整重启后的回执为准。
- 尝试过在显示前预量设置面板；5 次诊断首帧平均约 97.2 ms，但从触摸处理结束到首次 doFrame 结束的平均时间与原版基本相同（234.667 对 234.106 ms），Janky 仍为 3.80%，未保留为正式改动。trace 不足以证明成本转移的确切因果。
- 在已确认加载 `0.3.144 / 158` 的设备上复测 30 次；遥测设置 Janky 3.88%、首次 doFrame 平均 102.525 ms，布局设置 Janky 3.61%、首次 doFrame 平均 67.671 ms。两组显示后二次 resize 为 0，均完整覆盖 30 个开窗事件。
- 控制中心编辑器 30 次周期全部完成，配置哈希不变；首次窗口 doFrame 平均 63.056 ms、P95 79.412 ms，展开/收起内嵌选择器平均 38.229/28.807 ms。冷启动 30 次全部进入概述，启动 TotalTime 平均 1353.8 ms、P95 1464 ms；0/10/20/30 检查点的 ViewRootImpl/Activities 数量稳定。
- 以上新增场景仍包含观察工具开销；当前结果用于基线和资源趋势检查，不直接宣称优化收益或无泄漏。

## 当前进度：0.3.144 / 158

- 修复 `SettingsChoiceSelector` 初始无障碍状态：选择器父控件在首次绑定时同步“当前值”，展开后同步“已展开”，选项继续报告“已选择”。
- 实体设备 `b7015e9b` 完成“状态栏监测”和“状态栏布局”各 30 次采样；两组显示后二次 resize 均为 0，结果已记录到设备验证文档。
- 完整单元测试 592 项通过，失败/错误/跳过均为 0；lint 0 错误、60 警告，Debug 构建和正式归档已通过。APK 版本核对为 `0.3.144 / 158`，SHA-256 为 `53cf144c8f302ab08d16e20813d8f6b72270a08b7b764b7b2a48f42324729018`。
- 实体设备安装完成；历史 SystemUI 连接回执不足以验证加载版本。2026-10-02 完整重启后读取回执确认加载 `0.3.144 / 158`，正式归档位于 `artifacts/releases/v0.3.144/`。
- 已新增 0.3.144 遥测/布局两组各 30 次复测，详细指标和工具边界见 [设备验证记录](device-validation-v0.3.143.md)。原始 0.3.143 基线继续保留。

## 2026-10-02 阶段 4 诊断与完整场景补充

已完成实际加载版本核对及四个场景各 30 次：遥测设置、布局设置、控制中心卡片样式选择器、冷启动。稳定慢点是每次新建设置窗口的首次布局、窗口服务往返和初次绘制；预量实验未显示整体收益，已回退，当前应用仍使用正式 0.3.144。新增工具 13 项回归通过。

详细过程、测量边界和资源趋势见 [慢帧诊断记录](settings-performance-diagnosis-20261002.md)。本轮独立证据包位于 `artifacts/validation/v0.3.144/20261002-diagnosis/`；正式发布归档不覆盖。

阶段 4 仍未最终关闭：缺少同条件模块开关/优化前后对照、后台唤醒及完整 OEM 视觉/推送矩阵。下一项是独立验证 Activity 内设置面板是否能减少新窗口创建成本。
