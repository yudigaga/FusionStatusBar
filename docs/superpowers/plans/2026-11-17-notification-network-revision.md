# 通知绘制、网速与通知时钟修订计划

> 执行方式：分工静态诊断与回归源码，集中协调生产修改；测试与构建交指定独立验证方。禁止提交、合并、签名、发送或发布。

目标：优先消除通知背景包装的可达绘制循环，分别处理桌面网速视觉占位、锁屏原生网速管理缺口，以及大时间字号下日期不可见的问题。

架构：保持现有配置与宿主 Hook 边界。背景只接纳可安全绘制及恢复的宿主原图；网速管理区分 phone/keyguard 两个根；布局使用真实测量/可见边界。通知时间日期可继续合并显示，以可见且不遮挡邻近内容为验收目标。

技术：Java、Android View/Drawable、现有 LSPosed Hook、Robolectric Release 单测。当前候选由 20261117-01 冻结的 705 个输入逐文件复制。baseline 与旧冻结只读。

## 证据和边界

- ZIP 与已发 APK 哈希已核对，10 份报告均为 SystemUI StackOverflowError。已发 APK DEX 与既有 R8 mapping 对应构建逐项一致；日志 pg_map_id 一致。
- 用户触发：屏幕左上向下拉通知中心，有通知时反复展开/收起，间歇重启。这与绘制路径相符，但没有证明具体宿主重绑入口。
- 桌面空白与锁屏额外 8.17/KB/s 的截图未附到本地；不能声称已见截图或已确认该读数归属。
- 不能以旧版通过 1694 测试推断本轮问题已修复。不能捕获 StackOverflowError 来替代结构修正。
- 保留通知内容语义色、原图 alpha、边框独立 alpha、模糊 UNKNOWN 回退与窗口监听行为。
- 保留用户的时钟格式、开关、尺寸、偏移、对齐、关闭恢复和状态栏时钟。不得擅自选择固定字号/偏移。

## 1. 通知背景循环（优先）

- [x] 隔离复制与 ZIP/APK/mapping 哈希验证。
- [x] 用对应 R8 Retrace 保存全部现有栈帧，核对重复方法/行号。
- [x] 新增 NotificationBackgroundCycleRegressionTest：交叉行背景重绑、标准 DrawableWrapper 包住旧包装、反复 apply/关闭恢复、普通宿主换图与原图绘制。
- [ ] 独立验证方在未改生产代码的副本记录 RED，区分 fixture 编译问题与预期断言失败。
- [x] SystemSurfaceStyle 分开处理“当前 material 的所有权”和“能否接纳为原图”，阻断模块包装回流，保留最新有效宿主原图。
- [ ] 独立验证 GREEN，并回归 alpha/bounds/几何快路径/UNKNOWN 回退。

## 2. 锁屏网速

- [x] KeyguardNativeSpeedVisibilityRegressionTest：phone/keyguard 两根、host visibility 更新、启用关闭、替换/重挂及根外 View。
- [x] SystemUiHooks 对两个有效根统一同步原生网速，仅对模块确实绘制网速的根隐藏；关闭恢复宿主可见性。
- [ ] 保留读数归属未确认的限制，不把风险修订描述为截图根因已证实。

## 3. 桌面网速视觉占位

- [x] StatusSlotVisualScaleRegressionTest：真实 View 测量，0.6/1/1.5 缩放、LTR/RTL、足宽/窄宽。
- [ ] 如 RED 支持，DualRowStatusBarLayout 按原生图标实际视觉边界分配读数空间，保留 FusionIcon 跨行尺寸语义。
- [x] 不凭描述调 padding 或压缩宿主父容器；记录无法从本地层级确认的截图空白来源。

## 4. 大字号日期可见

- [x] NotificationCenterClockController 与调用方：检查两行限制、合并行高/宽度、父容器高度、列表起点与设置按钮空间，优先保留合并。
- [x] 新增真实 TextView 测量回归：大时间/较小日期、宽度与 fontScale/density、长/多行格式、横竖布局、展开/收起、重绑、关闭恢复。
- [ ] 依据测量结果分配空间；无法在已知宿主合同内安全扩大时应说明限制，不能承诺全部配置绝对无遮挡。

## 交付与验收

- [x] 每项分别列明已证明缺陷、修改、独立验证和真机待验范围。
- [ ] 复核完整输入 manifest、增量及累计补丁可重放、所有旧冻结和输入哈希不变。
- [ ] 候选冻结交小智串行验证，DouAi 重负载优先；本工作方不运行 Gradle/lint/构建。
- [ ] 独立验证方确认目标回归后再做完整 Release 单测、Python、lint/R8（按现有授权安排），不签名发送。

## 当前状态

源码修订及独立静态复审完成，51 个专属 Test 入口和安装器 3 个新增入口均未执行。基线与候选保留双快照，由指定方执行 RED/GREEN；本地不跑 Gradle。时钟实例高度及容器/时间尺寸加入 lastApplied 所有权和宿主新值保留；列表净空向上取整。通知可读性补自动 HIGHLIGHT 0 原背景保留。交接工具两次报路径解析错误，没有成功派发测试。最终输入数量、补丁回放和哈希以 delivery/freeze.json 为准，未完成项继续保持未勾选。
