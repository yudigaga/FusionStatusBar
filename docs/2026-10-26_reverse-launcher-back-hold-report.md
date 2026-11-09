# 系统桌面返回停顿适配分析

分析日期：2026-10-26。工具：本地 JADX 1.5.6、Android Build Tools 36.0.0 aapt、SHA-256。报告使用普通逆向结构（flavor = null）。

本机提供的 Home 7.00.00.2300 由桌面进程持有左右侧返回窗口和触摸识别器，SystemUI 通过状态标志及广播参与窗口可见性控制。模块已有的跨进程动作链也是桌面识别、SystemUI 执行。因此“返回停顿”应接入桌面的原生侧边返回流，继续复用 SystemUI 动作桥接。旧参考模块的“返回停顿切换最近应用”实现不能直接照搬：本次 Home 中已经没有它依赖的最近任务状态和方法。以下为静态证据；不代表真机 Hook 已触发。

## 范围和样本

范围见 [scope.md](../../analysis/launcher-back-hold-20261026/scope.md)，过程见 [timeline.md](../../analysis/launcher-back-hold-20261026/timeline.md)。用户授权的是本地功能开发及相关静态检查；未连接设备，未修改宿主 APK，未远程发布。

| 属性 | 值 |
|---|---|
| 文件 | `E:\vs\claude\xtjm\系统桌面_RELEASE-7.00.00.2300-06091632(7.0.0ENH).apk` |
| 大小 | 26,323,557 字节 |
| 包名 | `com.miui.home` |
| versionName / versionCode | `RELEASE-7.00.00.2300-06091632(7.0.0ENH)` / `999999700` |
| SHA-256 | `a2a19db0b6b6084ffee8e85cea142eec1cf37bfd52003898c76aef890e4b8837` |
| SDK | minSdk 34 / targetSdk 35 |
| 定点输出目录 | `E:\vs\claude\xtjm\analysis\launcher-back-hold-20261026` |

## Evidence

下列路径均指上述定点输出目录。每条均为 2026-10-26 的本地文件观察，关联用户请求，未被其他证据替代。

| ID | source_ref | content_hash (SHA-256) | 观察 |
|---|---|---|---|
| E-001 | 原始 Home APK | `a2a19db0b6b6084ffee8e85cea142eec1cf37bfd52003898c76aef890e4b8837` | aapt 验证包名、版本和 SDK |
| E-002 | `BaseRecentsImpl.java:789-859,975-1118,1501-1520` | `9a8ca10a0fd00260fbe20e21f71dd22bae25f561235ce18d57059bef1398e341` | 创建左右 GestureStubView；处理 SystemUI 广播和状态可见性 |
| E-003 | `GesturesBackTouchProcessor.java:48-143` | `61cf3fa468036130a1ce485f049dac9c604b2000df25f5277ea1e855b13be695` | 识别侧边流，开始/进度/完成回调 |
| E-004 | `GestureStubView.java:164-223,768-828,851-883` | `9fe6f40293af377fd422c03abc9f925bee1775001044394b721c36f2cc70db19` | 转发触摸，完成时执行返回，取消时走原生取消及动画清理 |
| E-005 | `GestureBackArrowView.java:56-58,227-268` | `ca14179efef54e3abe3cfb3c9788caff02fc35ebb596c969e40fa63a2f37e120` | TriggerType 只有 NONE/BACK，无 RECENT |
| E-006 | `OnBackInvokedCallbackController.java:49-80` | `ed729a71bc51424faef4aa0c1875c277e019af86d3e7362a0e12fbf56aa9add6` | 完成预测返回或注入 Back；取消时通知导航未完成 |
| E-007 | 模块 `LauncherHooks.java`、`DesktopActionQueue.java`、`DesktopSystemActionReceiver.java` | n/a（本轮开发中的源码） | 普通桌面动作有前台门控；签名广播限定目标为 SystemUI，并校验桌面 UID |
| E-008 | `TouchInteractionService.java:115-129` | `a3fd139197faa0a33b96708088cf749a5a845fb702d32b9823de5da7a463351b` | BACKGROUND_EXECUTOR 使用独立 FsGestureSecondaryThread；GestureStubView 并非保证在主线程处理输入 |
| E-009 | `BackGestureUtils.java:13-31` | `2e1d1ecfaeef0cdf0d1c375e9d3c8bc391c631e6165bb9c30e76657d4099b66d` | 返回位移先线性、后阻尼映射到背景缩放 |

可复现命令（从 `E:\vs\claude\xtjm` 执行）：

```powershell
$env:JAVA_OPTS = '-Xmx512m'
$apk = '.\系统桌面_RELEASE-7.00.00.2300-06091632(7.0.0ENH).apk'
$classes = @('BaseRecentsImpl', 'GestureStubView', 'GesturesBackTouchProcessor', 'GestureBackArrowView', 'OnBackInvokedCallbackController', 'TouchInteractionService')
foreach ($name in $classes) {
    & '.\.tools\jadx\bin\bin\jadx.bat' --single-class "com.miui.home.recents.$name" --single-class-output ".\analysis\launcher-back-hold-20261026\$name.java" --no-res $apk
}
Get-FileHash -Algorithm SHA256 -LiteralPath $apk
```

aapt 在本机不能直接读取含中文的 APK 路径，使用同目录 ASCII 副本 `analysis\launcher-back-hold-20261026\home.apk` 读取包元数据。定点并行解码曾因默认 JVM 内存申请失败，改为顺序执行并限制 `-Xmx512m` 后成功；保留 `hs_err_pid36108.log` 作为失败记录。

阻尼工具类可用相同命令将 `--single-class` 改为 `com.android.systemui.fsgesture.BackGestureUtils`，输出到上述目录的 `BackGestureUtils.java`。

## Findings

### F-001：侧边返回输入在 Home，SystemUI 协同窗口状态

- severity：n/a_re；status：validated（静态）；confidence：high；evidence_ids：E-002、E-003、E-004。
- location：`BaseRecentsImpl.initGestureStub`、`BroadcastUpdateGestureHandler.registerBroadcastReceiver`、`SysUIFlagsAndBroadcastUpdateGestureHandler`。
- 结论：Home 根据全面屏导航及默认桌面状态创建左右 `GestureStubView`。它接收 `com.android.systemui.fsgesture`，注册需要 `miui.permission.USE_INTERNAL_GENERAL_API`；通知栏、控制中心、锁屏状态也经 `mSystemUiFlags` 控制窗口显隐。模块应复用宿主已接受的返回流，以保留宿主已有场景限制。

### F-002：现代 Home 没有旧“最近应用停顿”状态

- severity：n/a_re；status：validated（静态）；confidence：high；evidence_ids：E-004、E-005。
- location：`GestureStubView$3.onSwipeStop(boolean,float,boolean)`、`GestureBackArrowView.TriggerType`。
- 结论：当前回调只有 BACK/NONE 分支。参考 Customiuizer 的 `SwipeAndStopActionHook` 依赖 `ReadyState.READY_STATE_RECENT`、`getNextTask`、`disableQuickSwitch`，这些方法/状态在本次 Home 对应类里不存在。不能以“成功安装旧 Hook”代替当前版本适配。

### F-003：自定义动作需要原生取消，并独立于桌面 Activity 前台状态

- severity：n/a_re；status：validated（静态）；confidence：high；evidence_ids：E-003、E-004、E-006、E-007。
- location：`GestureStubView$3.onSwipeStop`、`OnBackInvokedCallbackController.onBackCancelled`、`LauncherHooks.performGestureAction`。
- 结论：原生返回在松手时执行；取消路径会调用预测返回取消并清理动画。新停顿动作命中后应让原回调按取消完成清理，再仅发出一次自定义动作，避免同时返回。现有普通桌面手势以 `launcherResumed` 和正常桌面状态作门控；全局侧边返回在应用前台发生，需要专用入口，不能放宽所有桌面手势。

### F-004：返回外观绘制包含动画和振动状态更新

- severity：n/a_re；status：validated（静态）；confidence：high；evidence_ids：E-004、E-005、E-009。
- location：`GestureBackArrowView.onDraw`、`startArrowAnimating`、`onSwipeProgress`、`onSwipeStop`。
- 结论：原生 `onDraw` 不只是画位图。它在 BACK/NONE 切换时启动箭头透明度动画，并通过动画结束回调发出准备返回振动。完全跳过原方法会改变宿主反馈。自定义 Canvas 外观需要让原方法执行一次并暂时裁掉其绘制，再使用宿主当前几何和透明度绘制；原方法的调用和 Canvas 状态恢复必须有异常保护。字段/签名不匹配时保留原外观。

## Path

P-001，path_type=callflow，起点为原生侧边返回触摸，终点为完成/取消导航：

1. `BaseRecentsImpl.initGestureStub` 创建左右窗口；SystemUI 广播/flags 参与显隐（E-002，F-001）。
2. `GestureStubView.onTouchEvent` 完成宿主场景检查后转发至 `GesturesBackTouchProcessor.onPointerEvent`（E-003、E-004，F-001）。
3. 处理器在向内滑动至少 20 px 且方向条件成立时 `onSwipeStart`，随后 `onSwipeProcess(distance)`；松手生成 `onSwipeStop(isFinish,distance,feedback)`。原生成功阈值为位移 >180 px 或速度 >2 px/ms；ACTION_CANCEL 必为未完成（E-003，F-003）。
4. `$3.onSwipeStop` 按完成标志设置 BACK/NONE。BACK 分支执行 `injectBackKeyEvent`；NONE 分支 `onBackCancelled`，然后统一调用箭头 `onSwipeStop` 收尾（E-004，F-003）。
5. `OnBackInvokedCallbackController` 有有效导航回调时执行 `onBackInvoked` 并 `onBackNavigationFinished(true)`；否则注入 Back。取消时回调 `onBackCancelled`、`onBackNavigationFinished(false)` 并清空导航包装对象（E-006，F-003）。

P-002，path_type=callflow，模块自定义动作路径：停顿识别 → `DesktopActionQueue.dispatchIntent` 预检并签名 → 取消原生返回/动画清理 → 主线程 `DesktopActionQueue.sendPrepared` → 限定 SystemUI 的带签名广播 → `DesktopSystemActionReceiver` 验签和桌面 UID 校验 → 选定动作（E-007，F-003）。快捷方式继续使用桌面持有的 LauncherApps 权限。

P-003，path_type=callflow，侧边返回外观路径：宿主 `onSwipeStart` 定位 → `onSwipeProgress` 经阻尼函数更新 `mBackgroundScale` → `onDraw` 更新原生箭头动画/振动状态 → 原位图或模块 Canvas 预设绘制 → `onSwipeStop` 以 100 ms 缩回 → 宿主隐藏/重置窗口（E-004、E-005、E-009，F-004）。

## 侧边返回外观接入契约

`GestureBackArrowView` 继承 View。`onDraw(Canvas)` 没有额外平移/缩放；左右镜像由坐标计算完成。宿主在手势开始时把父级窗口扩为全屏，绘制时必须读取当前 `view.getWidth()`，不能缓存屏幕宽度。

| 字段 | 用途 |
|---|---|
| `mPosition` | 0 左侧、1 右侧 |
| `mBackgroundWidth * mBackgroundScale` | 当前向内凸起宽度 w，单位 px |
| `mStartX` | 距屏幕边缘的向内偏移，包含刘海适配 |
| `mStartY` | 形状和箭头的 Y 中心 |
| `mExpectBackHeight` | 背景高度，包含刘海适配 |
| `mArrowWidth / mArrowHeight` | 原生箭头位图的目标大小 |
| `mCurArrowAlpha / mArrowPaint.alpha` | 箭头当前透明度 |
| `mIconNeedDraw / mTriggerType` | 原生箭头是否允许绘制；BACK 以外不绘制 |

左侧背景区间为 `[mStartX, mStartX+w]`；右侧为 `[view.width-mStartX-w, view.width-mStartX]`。背景上下范围为 `mStartY ± mExpectBackHeight/2`。箭头在凸起区间中水平居中，Y 中心仍为 `mStartY`。原生箭头只在 `mIconNeedDraw && mBackgroundScale>0.1 && mTriggerType==BACK` 时出现。

`onSwipeStart(y,startX,expectHeight)` 在 `expectHeight>0` 时直接采用 y/高度；否则 Y 中心为 `y-20px`，高度为背景位图高度。不可把这个 20 当成 dp，或忽略 startX/expectHeight，否则会破坏刘海边定位。

`onSwipeProgress(distance)` 将缩放设为 `laterFriction(distance/360,0.8,2,0.5)`：288 px 之前线性，之后渐进阻尼，理论上限约 1.1333。`onSwipeStop` 继续使用同一初始值，在 100 ms 的 DecelerateInterpolator 动画中缩到零，并立即把 TriggerType 设为 NONE。外观预设应读取这个值，保留原生输入、完成/取消、100 ms 收尾和反馈时间。

安全替换顺序为：先读取并验证字段；Canvas.save；临时裁成空区域；原生 `onDraw` 执行一次；finally 恢复 Canvas；按当前字段绘制自定义形状。不要在每一帧改原生触发状态或重启它的 Animator。原生样式改色可使用画笔颜色过滤器，但需要保留原生透明度并在恢复默认时清理过滤器。

## 实现约束和验证边界

- 新配置追加稳定 gesture ID，不改变已有手势编号；默认使用系统原有行为。
- 只跟踪宿主已经接受的单指侧边流；需要向内达到返回范围后稳定停留，而不是单纯统计从按下到松手的总时长。取消、多指、位移退回和重复结束都需要复位；松手时使用当前有效配置。
- 只在“停顿命中且宿主完成标志为 true”时替换；正常返回、未配置和不完整动作配置继续原路径。
- 必须先完成正常取消/收尾，再调度动作。Home 的 BACKGROUND_EXECUTOR 使用独立 `FsGestureSecondaryThread`（E-008），在取消之前向主线程 post 并不能保证执行顺序。
- 调度前预检缺少签名令牌、不完整动作和权限缺失，失败时继续原生返回。已经取消导航之后的异步广播投递失败、SystemUI 未接收或下游动作执行失败，只能记录失败；不能把这种情况声称为已经恢复原生返回。
- 静态证据仅覆盖提供的 Home 文件及本地 SystemUI 源码；ROM 当前实际 APK、运行时 Hook、触摸线程、锁屏/多窗口/横屏体验仍需真机验证。
- 本报告不声称新增 Hook 已在设备执行；具体实现和本地测试结果由主任务补充。

## 已实现的设置和运行行为

桌面页的“桌面手势”新增“返回停顿”。左右侧边向内超过当前宿主的 180 px 返回阈值，且在触摸容差内保持 500 ms 后松手，才替换本次有效返回。计时同时检查横向、纵向和 MotionEvent 历史采样；持续拖动、回撤、取消、多指和重复结束不会重复触发。默认、无动作、目标不完整、签名令牌不可用、锁屏或息屏时继续原生路径。

`LauncherBackHoldGesture` 先预备动作，再调用原生 `onSwipeStop(false, …)`，最后执行预备动作。`LauncherHooks` 缓存 application Context 的弱引用，使配置刷新独立于桌面 Activity，同时避免静态字段持有 Activity；旧桌面手势继续要求桌面前台。普通系统动作通过 `DesktopActionQueue.sendPrepared` 发送已签名的 SystemUI 广播，快捷方式保留桌面的 LauncherApps 权限。

桌面页的“侧滑返回外观”用于普通返回和返回停顿：

| 预设 | 默认背景 / 箭头 ARGB | 绘制和运动 |
|---|---|---|
| 系统默认 | 保留原生绘制 | 直接执行宿主原方法 |
| 经典水滴 | `#CC202124` / `#FFFFFFFF` | 水滴轮廓，轻微弹性拉伸 |
| 灵动胶囊 | `#E63267D6` / `#FFFFFFFF` | 蓝色圆角胶囊，较快伸展 |
| 柔和涟漪 | `#B34AAFA5` / `#FFFFFFFF` | 青绿色圆形和两层随进度扩散的外圈 |
| 霓虹光环 | `#CC17112D` / `#FFBAA2FF` | 深紫底色、多层描边和弹性响应 |

背景和箭头可分别选择色板或输入 RGB/ARGB。颜色编辑有预览和输入校验，确认之前不写入配置。在系统默认下确认自定义颜色会启用经典水滴；重新选择系统默认会恢复原生绘制。设置内预览与 Hook 共用 `BackGestureRenderer`，仅在预览可见时运行示意动画；动画被系统关闭时显示静态预览。

`LauncherBackAppearance` 缓存字段契约及每个 View 的绘制器。自定义样式保留原生 onDraw 的动画和反馈副作用，读取最新几何、触发状态和箭头透明度后绘制。不同预设通过进度响应曲线和轮廓变化产生动画差异，仍由宿主的进度与 100 ms 收尾驱动。字段不匹配时保留原外观；自定义绘制异常不会重复执行原生 onDraw。

配置追加 `GESTURE_BACK_HOLD=10` 和三个可选外观字段，既有 0–9 手势编号保持不变；旧 Bundle/偏好设置缺少新字段时恢复系统默认。往返序列化、应用缓存、目标标签、切换功能及现有设置复制操作均保留颜色和手势映射。

## 本地自动化验证

Debug 定向覆盖共 104 个唯一用例，按类合并最终批次结果：104 通过，0 失败，0 错误，0 跳过。先执行 104 项定向批次，随后针对抗锯齿断言重跑绘制相关 15 项，针对上下文引用调整重跑运行时、缓存和动作链相关 41 项。汇总和各类最终 XML 保存在 `E:\vs\claude\xtjm\analysis\back-gesture-targeted-104-20261026`。

| 检查 | 用例数 | 范围 |
|---|---|---|
| Desktop 系列 | 58 | 配置兼容和持久化、Compose 选择保存、RGB/ARGB 校验、原有应用列表和签名动作通道 |
| 返回停顿 Hook | 15 | 真正的拦截链、500 ms 边界、连续拖动、双轴位移、多指、取消、回撤、右侧、重复结束、异常不重试 |
| 全局运行时 | 5 | Activity 不在前台、延迟动作、缺少令牌、锁屏/息屏、Activity 销毁后刷新、旧桌面门控 |
| 外观 Hook | 10 | 原生 onDraw 只执行一次、裁剪恢复、字段失败回退、原生透明度和触发门控、恢复默认 |
| 共享绘制器 | 5 | 四种独立响应与形状、左右镜像、自定义透明色、零进度、预设图 |
| 其余 Launcher 兼容 | 11 | 原有手势和桌面图标行为 |

镜像图形测试比较实际占用区域的预乘颜色平均误差与覆盖面积，允许 Skia 对镜像贝塞尔边缘的抗锯齿取整差异：平均误差不超过 3/255、覆盖面积差低于 3%。透明色检查也允许预乘存储带来的单通道 1 级取整。预设图已人工查看，文件为 `E:\vs\claude\xtjm\analysis\back-gesture-presets-20261026.png`，SHA-256 为 `8ecd044b52644e4d1f4f5495152e6a02362fa97fa33249591d3d1c58cd32f860`。

最终源代码的 Debug/Release 编译及 `:app:lintRelease` 均通过；Release lint 为 0 错误、97 警告、2 提示。报告位于 `app/build/reports/lint-results-release.html`，执行日志位于 `E:\vs\claude\xtjm\analysis\back-gesture-lint-20261026.log`。新增全局上下文引用使用弱引用，未留下本轮新增的静态 Context 持有警告。`git diff --check` 通过。

本轮未运行完整 Debug/Release 测试套件，未安装 APK，未连接手机或验证 LSPosed 实际回调、横竖屏/刘海设备手感；未创建正式版本或远程发布。
