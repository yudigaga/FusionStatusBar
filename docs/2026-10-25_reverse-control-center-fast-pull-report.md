# 控制中心快速展开与回收：原生动画链分析及模块调整报告

分析日期：2026-10-25，时区 Asia/Shanghai。类型：已授权的离线 APK 静态分析与模块动画调整，`flavor = null`。状态：**本地测试与 Release 装配通过，设备手势验收未执行**。

## 1. 执行摘要

用户希望控制中心能像通知中心一样快速下拉、快速回收。0.5.14 的帧内绘制优化与本轮的动画时序调整属于不同问题：减少描边计算或模式查询，不会自动改变原生弹簧、分排延迟及缩放动画的收尾。本轮追踪到承载整个自定义网格的原生 `MainPanelItemViewHolder`；此前仅限制 Header expansion 和 Blur 的响应参数，没有覆盖该 carrier 的完整可见性与位移动画。

基线的 `MAX_RESPONSE_SECONDS = 0.35` 仅缩短更慢的弹簧。控制中心默认 spring response 已为 `0.35`，因此这条基线保护对默认配置返回原对象。通知中心和控制中心在手势中都使用弹簧追随，而且默认都立即启动；不能把“通知直接跟手、控制中心才有弹簧”或“控制中心未显式调用立即启动 API”作为本轮根因。

本轮实现对**明确标记为自定义网格 carrier** 的原生配置构造链去除 stagger delay，限制展开与向展开态释放的 response 为 `0.30`，收起为 `0.20`。需要重定时的 spring damping 下限为 `0.9`，原本更快的有效原生配置保持原样。该策略参考通知内容的追随和快速隐藏参数，是用户所需的快速偏好，不是对通知中心全部属性的逐项复刻，也不承诺固定 `300ms/200ms` 总时长。339 项相关 Debug 回归及 Release 装配、Lint 已通过；设备验收未执行。

## 2. 范围与样本身份

授权和 case 边界见 [scope.md](../../analysis/control-center-fast-pull-20261025/scope.md)，过程记录见 [timeline.md](../../analysis/control-center-fast-pull-20261025/timeline.md)。本报告仅覆盖工作区内 SystemUI APK、既有反编译结果、FusionStatusBar 源码及本地回归测试；网络策略为 `offline_local_files`。

本轮分析和调整的目标是正常通知/控制中心的**竖向**展开与回收。横向 ShadeSwitch 的 ALPHA easing 不作为竖向动画参数。未在本报告工作中修改宿主 APK、安装参考 APK、操作目标手机或向外发送样本；也未将 HyperOS 4 的 MiGlass 材质实现引入此动画调整。

| 对象 | 身份与用途 | 证据 |
| --- | --- | --- |
| `systemui.apk` 与 `系统界面_16.03.251211.r.apk` | 两份文件字节相同；包 `com.android.systemui`，版本 `16.03.251211.r / 202501210`，compile SDK 36 | E-002 |
| SystemUI SHA-256 | `1b8a6cf73c3bb3e96fd795e648c4edd1e5e790b6f449465710296fde861c3ce5` | E-002 |
| 控制中心组件 APK SHA-256 | `1d94245dbb196eed49861335ca6593f675096b63b5f3113dcfaab670c7977044`；版本 `17.1.4.26.0` | [host-evidence-hashes.json](../../analysis/control-center-fast-pull-20261025/host-evidence-hashes.json) |
| `classes2.dex` | 通知竖向动画类所在 DEX；本地 `_sysui_classes/classes2.dex` 与 APK 内对应条目哈希相同 | E-002 |
| `classes2.dex` SHA-256 | `f2c6150bd21d4e6ed69f03b2e7f66797b6d4f5bbbe94a81f223c8a1572c3ffe9` | E-002 |
| 控制中心宿主实现 | `jadx-systemui-plugin/sources/miui/systemui/controlcenter/` 下的 ExpandController、SpreadRowsAnimator 与 MainPanelItemViewHolder | E-004 |
| 模块基线 | `artifacts/releases/v0.5.14/source.zip` 中的动画 Hook 源码 | E-005 |
| 本轮模块入口 | `ControlCenterAnimationHooks`、`ControlCenterCarrierMotionHooks`，以及 Grid carrier 标记 | E-007、E-008 |
| 工具与方法 | JADX 1.5.6、PowerShell、Python 标准库；Java/DEX 静态阅读与原生图形/队列契约测试 | E-002、E-006 |

### 依赖与托管导入等价证据

所分析路径在 Java/DEX 层。通知侧使用 `miuix.animation.Folme`、`IStateStyle`、`AnimConfig`、`EaseManager`、`TransitionListener` 及生命周期对象；控制中心侧还使用 `FloatProperty` 和宿主 holder。模块通过 libxposed `XposedInterface.Chain`、反射访问和真实方法注册保留接口接入。上述依赖可从 E-003、E-004、E-007 的 import 与调用处复查；本轮没有需要转交 JNI/ELF 才能解释的动画逻辑，传统 PE/ELF 导入表不适用。该结论仅限此调用路径，不构成整个 APK 的本地库审计。

## 3. Evidence：观察与复查入口

各行 `observed_at = 2026-10-25`，`linked_workitem = n/a`，`supersedes = none`。表中 `content_hash = n/a` 表示未在本 case 内额外固定一份源码副本，`artifact_path = n/a`；样本哈希和已有摘录身份以 [timing-summary.json](../../analysis/notification-pull-timing-20261025/timing-summary.json) 为准，不为变化中的源码伪造固定哈希。

| E-id | source_type / source_ref | raw_excerpt / 固定观察 | content_hash / artifact_path | repro_command |
| --- | --- | --- | --- | --- |
| E-001 | manual；当前会话用户请求 | 希望控制中心像通知中心一样快速下拉、快速回收；授权工作区离线分析与模块动画调整 | `n/a / n/a` | 复查当前会话；文件不能替代用户直接授权 |
| E-002 | file；`notification-pull-timing-20261025/timing-summary.json`、SystemUI Manifest 与 APK 内 `classes2.dex` | 两份 APK 哈希一致；本地 DEX 与 APK 条目匹配；版本 `16.03.251211.r / 202501210` | `n/a / n/a`；摘要位于相邻分析目录 | A、B |
| E-003 | file；`NotificationPanelExpansionAnimator.java:34,65–74`、`NotificationPanelBlurAnimator.java:34,65–74`、`NotificationPanelViewController.java:2343–2369` | 通知 Header/Blur 为 `(-2,.9,.35)`，MOVE 更新明确传 `animated=true`；列表更新才按 sample rate 采样 | `n/a / n/a`；见 `notification-expansion.txt`、`notification-blur.txt`、`notification-touch.txt` | B |
| E-004 | file；通知 `MiuiPanelAnimValueObjectImpl.java:802–821,910–951,1259–1271`；控制中心 `MainPanelItemViewHolder.java:468–553,574–599,629–678`、`AnimConfig.java:253–266` | 通知内容 follow 为 `(-2,.9,.3)`，隐藏 response 按 index 插值 `.25→.2`；CC 可见性展开显式 delay；默认 `startImmediately=true` | `n/a / n/a`；见 `notification-content.txt`、`cc-carrier.txt`、`cc-default-config.txt` | B |
| E-005 | file；0.5.14 归档 `ControlCenterAnimationHooks.java:19,144–145` | `MAX_RESPONSE_SECONDS=.35`；原生 response `<=.35` 返回原对象；未覆盖 MainPanelItemViewHolder 的局部配置构造 | `n/a / n/a`；归档在模块项目内 | C |
| E-006 | log；case `carrier-motion-red.xml` 与 `carrier-motion-red.log` | 初始 RED 为 `tests=9, failures=6, errors=0, skipped=0`，捕获 native holder→配置 setter→延迟队列消费后的慢参数 | `n/a / n/a`；case 内已有固定 RED 记录 | D |
| E-007 | file；本轮 `ControlCenterAnimationHooks.java:19–21,54–78,109–117,157–195` | 展开 `.30`、收起 `.20`；中间 Header progress 保留；按 encoded plan 缓存有效性；克隆合法 spring factors | `n/a / n/a` | E |
| E-008 | file；本轮 `ControlCenterCarrierMotionHooks.java:17–26,67–96,123–211`；`ControlCenterMaterials.java:1210`、`ControlCenterRuntimeGrid.java:800` | 已标记 carrier 才进入；局部构造配置的 delay/main ease/property special 在宿主调用期间调整；线程上下文按配置与 ease 类型校验 | `n/a / n/a` | E |
| E-009 | file；`ControlCenterCarrierMotionRegressionTest`、`ControlCenterCarrierSharedConfigRegressionTest` | 队列在边界退出后消费；覆盖 target、元数据、完成回调、异常、普通/镜像 holder、重复安装共享 setter、异配置嵌套 | `n/a / n/a`；最终运行结论见第 8 节 | F；本地通过 |

## 4. 原生竖向时序的边界

### 手势追随与松手收尾

通知触摸路径在 `NotificationPanelViewController.onTouchEvent` 的实际展开分支中累加 Y 差值，按 `expandHeight >= expandThresh` 更新 appearance；阈值为 `200px`。Header expansion 目标为 `clamp(expandHeight,0,200)/200`，MOVE 期间传 `animated=true`，Blur 同样通过 spring 追随。`panel_tracking_sample_rate` 默认值为 `2`，只约束通知列表的 `startPanelExpandAnimation` 更新，不把 Header/Blur 变为隔帧更新或直接 `setTo`（E-003）。

松手或取消触摸后，`NotificationPanelExpandController.onExpandFinish()` 按 appearance 选择 `200/0` 目标，重新触发 Header/Blur 与内容释放动画。这个方法没有速度参数。所读触摸分支中的 `VelocityTracker.computeCurrentVelocity(1000)` 位于 `nssCoveringQs` 的顶部 padding fling 分支，不能转述为通知主竖向 spring 的释放速度设置。按键、锁屏、悬浮通知等其他路径不据此概括。

控制中心 `onExpandChange` 同样使用 spring 更新 expansion；`setExpandProgress$default` 的默认参数掩码包含 animated 位，不能仅看到调用点上的 `false` 字面量就判断为直接 `setTo`。`onExpandFinish(float)` 记录速度后，还会触发 SpreadRows、Blur 和 holder 位移，不能只盯 Header 的 normalized progress。

### 内容属性并不由一条弹簧统一控制

下表 `d/r` 为 spring damping/response；不是固定动画总时长。通知参数中的 `p=index/10`，CC 的 `deltaY/delayPer` 来自宿主几何位置。

| 运动域 | 通知中心 | 控制中心 | 本轮处理 |
| --- | --- | --- | --- |
| Header expansion、Blur | `d=.9,r=.35` | Header 默认 `.95/.35`，Blur `.9/.35` | 只对适用的末端目标采用快速偏好 |
| 内容拖动位移 | `d=.9,r=.3` | SpreadRows/holder follow 同为 `.9/.3` | 保留非 release 的原生追随 |
| 内容释放位移 | `d=lerp(.7,1,p), r=lerp(.4,.45,p)` | Row `.7/.4`，holder 按 deltaY 插值 `.7→1 / .4→.45` | 限制已标记 carrier 的 release 收尾 |
| 可见性展开 | alpha/scale 约 `.8→.9 / .4→.6`，位移约 `.8→.95 / .35→.45` | show、SHRINK_X、SHRINK_Y 各有独立 profile | 同时约束 main ease 与 property special |
| 可见性隐藏 | 约 `.8→.9 / .25→.2` | hide 类似，另有 shrink special | 以 `.20` 作为快速回收 response 上限 |
| 显式竖向 show delay | 观察到的 visible config 未设置 delay | `(long)(delayPer*100)` 毫秒 | 仅对 carrier 置零 |
| 立即启动 | animator 显式设置为 true | `AnimConfig` 构造器默认已为 true | 不作为差异根因 |

0.5.14 保留的 `.35` 上限是较慢 profile 的保护，不是“所有动画变成 350ms”。本轮也没有把通知中心的横向 ALPHA easing 套用到上述竖向内容属性。

## 5. 本轮配置策略与保留契约

### 精确进入 carrier 的配置构造范围

Grid 挂载时用 `ControlCenterMaterials.setGridCarrier(view,true)` 标记 RecyclerView 所有的外层 carrier；释放时清除标记。新 Hook 检查 holder 的真实 `itemView`、有效且启用的自定义 layout plan，以及对应 animated/release/mirror 参数后才建立本线程上下文。普通原生 holder、未启用或无效布局不会因此获得快速策略（E-007、E-008）。

原生 `changeVisible` 会在方法体内新建 `AnimConfig`，随后调用 `setDelay`、主 `setEase` 和多个 `setSpecial`。只修改外部 `animConfig` 字段无法完整覆盖这些局部对象；只修改主 ease 也会被 property special 再次覆盖。本轮在限定上下文内替换 setter 的对应参数，仍执行原方法一次，使已构造的配置被原生 `.to(...)` 和延迟队列继续持有。Header/Blur 的字段配置则使用稳定副本，在宿主方法返回后恢复字段，避免排队消费时读到已经撤销的参数。

### 快速偏好与例外

| 场景 | 策略 |
| --- | --- |
| Header expansion 的 animated `0/1` 末端 | 展开 response 上限 `.30`；收起 `.20`；镜像或无法确认镜像状态时保留原生 |
| Header expansion 中间值 | 保留原生追随 profile 和目标 |
| 适用的 Blur 展开/回收 | 按 shown 选择 `.30/.20`；不改无 blur、直接更新或 mirror 路径 |
| carrier 可见性 animated 展开/隐藏 | 去除构造期间的 stagger；main ease 与 property special 采用 `.30/.20` 上限 |
| carrier animated release | 向展开态释放采用 `.30`；关闭目标采用 `.20`；需明确非镜像 |
| carrier 非 release 的 animated follow | 保留原生跟随 profile |
| direct drag / 非 animated 更新 | 不转换为新动画，不改变原生 `.setTo` 目标 |
| 普通 holder、禁用或无效 plan、镜像 | 保留原生时序；准备或类型校验失败时调用原始路径 |
| 更快的有效原生 spring | response 已小于上限时不重建，保留 damping 和额外因素 |
| 需要重定时的有效 spring | style 必须为 `-2`，因素有限且为正；damping 至少 `.9`，response 不超过上限；复制并保留额外因素 |

这些更改不重写 target、几何位置、alpha/scale 目标、velocity/fromSpeed、tag、flags 或完成监听器。瞬态上下文用 `finally` 恢复；原生异常按原路径传播，不借重试重复调用宿主方法。多个安装共享同一原生 setter Method 时，首次保留的 Hook 读取当前 holder 的静态线程上下文；上下文携带实际 `configType/easeType/factory`，避免第二个 holder 安装失效，也防止其他配置类型在嵌套调用中被误改。普通 holder 的边界会暂时清除外层策略，再恢复它（E-008、E-009）。

## 6. Findings

`validated` 指已有静态或 RED 证据支持的分析结论；`locally_validated` 指本轮相关测试通过，不表示实体手机已经验收。

| F-id | title / category | severity / status / confidence | evidence_ids / location | impact / remediation |
| --- | --- | --- | --- | --- |
| F-001 | 基线 `.35` 保护对默认 `.35` spring 无效果，且没有覆盖 carrier 局部配置；`design` | `n/a_re / validated / high` | `[E-004,E-005,E-006]`；基线 `ControlCenterAnimationHooks.fastConfig`、原生 holder `changeVisible/changeExpand` | Header/Blur 的保护不足以落实整块网格快速展开/回收；本轮将限定范围延伸到真实 carrier 配置构造链 |
| F-002 | “通知直接跟手”及“CC 未立即启动”的解释与目标 APK 不符；`reverse_algo` | `n/a_re / validated / high` | `[E-002,E-003,E-004]`；通知 MOVE、双方 AnimConfig 构造 | 两侧都使用 spring 且立即启动；应区分跟随、visible 与 release，不能用横切 alpha 或 `.35` 常数推导总时长 |
| F-003 | 快速策略必须覆盖 main ease、property special 与排队消费；`design` | `n/a_re / locally_validated / high` | `[E-006,E-007,E-008,E-009]`；本轮 `Scope.holder → ease/delay → chain.proceed` | 已验证边界退出后的队列继续消费稳定的新配置，并保留 targets、元数据及完成监听器 |
| F-004 | 安装范围与运行上下文必须分离，且不能跨配置类型误作用；`design` | `n/a_re / locally_validated / high` | `[E-008,E-009]`；`ACTIVE`、`Context`、共享 Method/id 注册回归 | 重复注册保留首个 setter Hook 的两个独立 holder，以及异配置嵌套回归均通过 |

复现步骤见第 8 节 A–F。建议验收时分别记录手势期间的目标追随和松手后的 settling，不以“无异常”或 Hook 安装数量代替动画体验结论。

## 7. Path：入口到消费与完成

### P-001：通知中心竖向参照路径

- `path_type`: `callflow`
- `start`: 通知区域 `onTouchEvent` 的竖向 MOVE，以及 UP/CANCEL。
- `goal`: 确认实际跟随、内容可见性和释放参数。
- `steps`:
  1. MOVE 累加 expandHeight、更新 appearance，Header/Blur 以 animated=true 跟随；列表按默认采样 2 更新。`evidence: E-003`；`finding: F-002`。
  2. 通知内容 `changeExpand` 选择 follow 或 release profile；visible 配置独立控制 alpha/scale/位移，无观察到的显式 show delay。`evidence: E-004`；`finding: F-001,F-002`。
  3. UP/CANCEL 调用 `onExpandFinish()`，按 appearance 选择端点，再触发 Header/Blur 与内容 release。`evidence: E-003,E-004`；`finding: F-002`。
- `residual_risks`: 这是目标 APK 的静态 Java/DEX 路径，未在用户设备采集逐帧回调或完整 settling 时间；其他锁屏、镜像和 HUN 分支不能据此统称。

### P-002：自定义网格 carrier 的限定时序路径

- `path_type`: `callflow`
- `start`: 明确带 `setGridCarrier(true)` 标记的 RecyclerView 外层 View。
- `goal`: 让宿主最终排队消费的 delay/ease/special 符合快速偏好，并保留 native targets 与 completion。
- `steps`:
  1. Grid 标记真实 carrier，原生 `MainPanelItemViewHolder.changeVisible/changeExpand` 仍承担其运动。`evidence: E-008`；`finding: F-001`。
  2. 边界 Hook 校验 itemView、有效 plan、animated/release 和 mirror，建立带实际配置/工厂类型的线程上下文。`evidence: E-007,E-008`；`finding: F-004`。
  3. 宿主新建配置；原生 `setDelay/setEase/setSpecial` 的合格参数分别去 stagger、复制并限制 spring factors，其他参数继续原样传递。`evidence: E-004,E-008`；`finding: F-003`。
  4. 原生 `.to(...)` 保留已构造配置；测试队列在边界退出后消费 targets、主/special profile 和元数据。`evidence: E-006,E-009`；`finding: F-003`。
  5. `finally` 恢复外层上下文，队列中的原生完成监听器继续执行；异常不导致二次 proceed。共享 setter 读取当前合格 Context，异配置和普通嵌套不继承策略。`evidence: E-008,E-009`；`finding: F-004`。
- `residual_risks`: 实体 ROM 的真实调用命中、Folme 消费、完成时序和用户触感仍需安装后验证；本地队列测试不等同于设备画面验收。

## 8. 复查命令与验证状态

以下命令在模块项目根目录执行。A–E 用于只读复查；F 会运行本地测试，应按实际运行结果判断，不能因命令存在就视为通过。

### A：核对原 APK 身份

```powershell
Get-FileHash -LiteralPath '..\systemui.apk','..\系统界面_16.03.251211.r.apk' -Algorithm SHA256
```

### B：核对通知参照与控制中心原生配置

```powershell
Get-Content -LiteralPath '..\analysis\notification-pull-timing-20261025\timing-summary.json'
Get-Content -LiteralPath '..\analysis\notification-pull-timing-20261025\notification-touch.txt'
Get-Content -LiteralPath '..\analysis\notification-pull-timing-20261025\notification-content.txt'
Get-Content -LiteralPath '..\analysis\notification-pull-timing-20261025\cc-carrier.txt'
Get-Content -LiteralPath '..\analysis\notification-pull-timing-20261025\cc-default-config.txt'
```

### C：核对 0.5.14 动画 Hook 基线

```powershell
python -c "from zipfile import ZipFile; z=ZipFile('artifacts/releases/v0.5.14/source.zip'); p=next(p for p in z.namelist() if p.endswith('/ControlCenterAnimationHooks.java')); print(z.read(p).decode('utf-8'))"
```

### D：复核已保存的 RED

```powershell
python -c "import xml.etree.ElementTree as E; r=E.parse('../analysis/control-center-fast-pull-20261025/carrier-motion-red.xml').getroot(); print({k:r.get(k) for k in ['tests','failures','errors','skipped']})"
```

保存的结果为 `tests=9, failures=6, errors=0, skipped=0`。测试沿真实 holder 调用模式构造配置，并在边界退出后消费队列；它证明基线没有满足该契约，不是实体手机上的帧时间测量。

### E：复查本轮生产入口

```powershell
Get-Content -LiteralPath 'app\src\main\java\com\xtjm\fusionstatusbar\ControlCenterAnimationHooks.java'
Get-Content -LiteralPath 'app\src\main\java\com\xtjm\fusionstatusbar\ControlCenterCarrierMotionHooks.java'
```

### F：定向回归

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.xtjm.fusionstatusbar.ControlCenterAnimationHooksTest' --tests 'com.xtjm.fusionstatusbar.ControlCenterCarrierMotionRegressionTest' --tests 'com.xtjm.fusionstatusbar.ControlCenterCarrierSharedConfigRegressionTest'
```

**本地验证结果：**

| 范围 | 结果 |
| --- | --- |
| 聚焦动画回归 | 23 项通过：carrier 10、共享配置 2、scalar/排队配置 11 |
| 最终相关 Debug 回归 | 339 项、52 个测试类；失败、错误、跳过均为 0；见 `fast-pull-targeted-final.log` |
| 既有玻璃 Canvas 预览 | 与 0.5.14 字节一致，SHA-256 `c508ead25f95fc156a16185b2c0a41b050c95d04fca6c263faad3db69656b670` |
| Release Lint 与装配 | 通过；0 错误、98 警告；版本 0.5.15 / 229 |
| 签名、对齐及最终 APK 身份 | 以 [build-info.json](../artifacts/releases/v0.5.15/build-info.json) 的最终记录为准 |
| 完整 Debug/Release 单元测试、Debug Lint | 本轮未运行 |
| 目标设备 Hook 命中、快速往返手势、镜像/次级面板及真实收尾体验 | 未执行；ADB 枚举无连接设备 |

回归覆盖既有转场、时钟生命周期、材质/形状、透明端点、玻璃几何缓存、普通与镜像 holder 的排除路径。一次性成功 setter 诊断记录 `control-center carrier spring applied` 和 `control-center carrier stagger removed`，供设备日志确认实际作用；Hook 安装数量不代替命中证据。测试回调队列和静态 Canvas 图不是目标 ROM 的逐帧验收。

## 9. Timeline 与附件

详细过程由 case [timeline.md](../../analysis/control-center-fast-pull-20261025/timeline.md) 记录。已固定的先后顺序是：用户提出快速展开/回收目标；比对 0.5.14 `.35` 基线；离线追踪通知与 CC 竖向内容运动；保存 9 项测试中 6 项失败的 RED；实现 carrier 配置作用域、端点策略和共享 setter 类型边界；完成 23 项聚焦回归、339 项相关回归及 Release 装配和 Lint。

- [通知时序摘要及身份信息](../../analysis/notification-pull-timing-20261025/timing-summary.json)
- [通知内容 profile 摘录](../../analysis/notification-pull-timing-20261025/notification-content.txt)
- [通知触摸参数摘录](../../analysis/notification-pull-timing-20261025/notification-touch.txt)
- [CC carrier 原生构造链摘录](../../analysis/notification-pull-timing-20261025/cc-carrier.txt)
- [初始 RED XML](../../analysis/control-center-fast-pull-20261025/carrier-motion-red.xml)
- [初始 RED 构建日志](../../analysis/control-center-fast-pull-20261025/carrier-motion-red.log)

本轮是交互行为适配与普通逆向分析，不涉及攻击权限或数据获取；IOC、CVE 与 ATT&CK 映射不适用。
