# 手机管家 13.8 杂项扩展 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Track completion with checkboxes.

**Goal:** 在杂项提供 26 项独立配置并将可验证的当前宿主调用接入运行时。

**Architecture:** 复用 Misc DP 配置，新增枚举和版本限定的宿主配置运行时；按隐私、电池、游戏视频分域安装精确 Hook。默认关闭，关闭时原链不变，硬件状态不伪造。

**Tech Stack:** Java 17、Android SDK 36、libxposed 102、Miuix、JUnit/Robolectric、JADX/apktool。

**Spec:** ../specs/2026-11-09_security-center-13.8-design.md

## Global Constraints

- 只适配 com.miui.securitycenter / versionCode 40001380，所有功能默认关闭。
- 保留现有 0.5.23 行为与工作区改动，不提交、回滚或发布无关内容。
- 只解锁明确的应用层入口/判断，不修改全局温控、伪造传感器或修改腾讯游戏进程。
- 所有 UI-only/硬件风险/参考数据须在开关说明与验证报告中写明。
- 亮屏显示粒度 1～60 分钟、默认 1；真实数据未知显示 --。

## Review Focus

- 开关关闭、旧快照和其他开关编辑不能丢失既有状态。
- 不匹配的宿主版本/签名要停用对应 Hook，不盲猜混淆名。
- 共享门槛要 OR，不能被第二个关闭开关覆盖。
- 倒计时/弹窗跳过仍保留原权限检查和用户明确开启动作。
- 参考亮屏时间的 us/min 单位、未知值、粒度上下界和用户切换需有测试。

## Task 1: 当前 APK 证据

- [x] 固定样本身份；apktool 完整资源/smali；JADX 输出与错误日志保留。
- [x] 收齐 `analysis/security-center-13.8.0-20261109/{privacy-misc,battery-charge,game-video}-findings.md`；46 个游戏视频方法声明、12 份电池与 19 份隐私证据文件的行号/哈希核对通过。

## Task 2: 配置、宿主快照与界面

**Files:** SecurityCenterFeature.java、MiscConfig.java、MiscConfigStore.java、MiscSystemConfigExchange.java、FusionConfigProvider.java、MainActivity.java；新增 SecurityCenterConfigRuntime.java 和对应测试。

**Interfaces:** MiscConfig.isEnabled(SecurityCenterFeature)、withSecurityFeature(feature,boolean)、withScreenOnTimePrecisionMinutes(int)；SecurityCenterConfigRuntime.current() 返回经过宿主版本校验的 MiscConfig；prepare(ApplicationInfo) 先读 DP 缓存，install(module,loader) 捕获宿主 Application attach。

- [x] 字面配置键、独立复制、合法/非法接收等红绿测试完成。
- [x] 枚举定义全部标题/分组/说明；按枚举统一序列化，保留旧两字段。Provider 仅增加手机管家读授权；签名广播分别定向系统和手机管家。
- [x] 构建号 40001380 准入，宿主 DP 缓存先行；Application attach 后、原 onCreate 前同步首读，异步后续刷新失败保留缓存；缓存读写均持独立文件锁。界面保存失败回滚。
- [x] 配置、广播、版本门槛、UI 重建/滚动与原功能共 82 项定向测试通过。

## Task 3: 精确运行时 Hook

**Files:** SecurityCenterHookSupport.java、SecurityCenterPrivacyHooks.java、SecurityCenterBatteryHooks.java、SecurityCenterGameVideoHooks.java、SecurityCenterHooks.java、FusionModule.java；各文件对应测试。

**Interfaces:** `forceBoolean(module,loader,feature,owner,name,Class<?>...params)`；`forceBooleanAny(module,loader,SecurityCenterFeature[] features,owner,name,Class<?>...params)`；`forceValue(module,loader,feature,owner,name,returnType,value,Class<?>...params)`；`hook(module,Method,id,XposedInterface.Hooker)`。

- [x] 各域先红后绿，统一串行执行 Gradle；复审新增问题也先复现再修复。
- [x] 隐私域：V2防窥、666渲染、g0倒计时、真实晕车/车祸传感器及圆点时机设置读取。
- [x] 电池域：独占 o0.X/Y、xh.u.r/E；真实数据与参考亮屏行；关闭恢复同实例原值/可见性/索引，异步按树/代次/粒度丢弃旧结果。
- [x] 游戏视频域：其余 15 项；工具箱层温度；王者启动属性只清理自身持有的改动；包名资格保留 null/blank 的原链。
- [x] onPackageLoaded 准备早期配置，onPackageReady 以实际宿主 ClassLoader 安装精确 Hook；旧 Sidebar 不变。三域完成证据核对及交叉审查。

## Task 4: 完整验证与交付

- [x] 完整 Debug/Release 各 1572 项、0失败/错误/跳过；lint 各 0错误/101警告；两变体装配成功。28 项 Python 通过，设备列表为空，实机边界已记录。
- [ ] 更新版本为 0.5.24 / 238，原正式证书签名、16 KiB 对齐、APK 元数据、哈希及源码快照校验。
- [x] 汇总 26 项接入/仅UI/硬件限制与未实机项：`docs/2026-11-09_security-center-13.8-report.md`。APK 交付以本版签名归档结果为准；不远端发布。

## Ledger

- Ruling: 沿用当前工作区而不重置或创建丢失未提交改动的分支 — 0.5.22/0.5.23 是本次会话的既有本地成果 — 代价是归档需准确标记 sourceDirty。
- Ruling: 亮屏时间恢复到已确认电池信息页并明确参考口径 — 13.8 原文案仍在但旧 UI 无消费 — 不伪称恢复了已删除的主页面控件。
- Ruling: 亮屏时间只采用真实 BatteryStats.getScreenOnTime 的微秒口径；不采用单位未确认的 BatteryHistoryHelper 包装或 UsageStats 推算。
- Ruling: 666 只替换 ScoreTextView 的渲染字符，保留 setScore 参数、getTextScore 数据与普通数字模式；关闭恢复最新原生渲染。
- Ruling: 多进程共享宿主缓存的 AtomicFile 读也可能恢复暂存文件，因此读写均持相同独立 .lck 文件锁；没有扩大其他存储模块的变更范围。
- Ruling: 已有工作区不创建分支、不提交或远端发布；本地正式证书签名归档明确 sourceDirty=true。
