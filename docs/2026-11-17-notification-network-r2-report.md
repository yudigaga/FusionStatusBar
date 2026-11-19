# 通知与网速候选第二次修订

本轮是独立目标测试失败后的源码修订，**尚未由指定方执行新测试或编译**。不能宣称三项失败已通过，更不能宣称整体或真机已修好。版本保持 0.5.27 / 241，无新 APK，不签名、发送、集成或发布。

`fusion-notification-network-20261117-01` 的 715 项冻结输入及独立执行副本、712 项 RED 输入及执行副本均重新逐项校验一致。新 `fusion-notification-network-20261117-02/baseline` 和 `work` 从旧冻结复制。独立证据 40 个文件另存新 `evidence/independent-01` 并记录原件/副本哈希；旧冻结、旧失败 XML/命令/日志不改写。

## 已执行的上一候选门禁

独立方证据根：`E:\vs\claude\xtjm\fusion-network-independent-20261117-01`。以其中 REPORT.json、完整 command.json、XML、日志及复制输入为证据：

| 输入 | 目标范围 | 失败 | errors / skipped | 退出码 |
|---|---:|---:|---:|---:|
| 最初 RED | 11 类 / 139 项 | 46 | 0 / 0 | 1 |
| 第一候选 | 相同 11 类 / 139 项 | 3 | 0 / 0 | 1 |

这是实际编译、执行后的结果，不是环境失败。第一候选 manifest SHA-256 为 `185f66c1abd8a39a082a2e31b378aeda3ca22c2b6b661f3f467d53f7319a67f3`。原 REPORT.json 的 red.manifestSha256 复用了候选标识；本轮另以真正 RED manifest `e4dfe920c481c03a783ab8880ef7e766742464227028a7e4ec6ce660634add93` 核对 712 项源码与其执行副本，不改原报告。

候选失败后独立方立即停止，未跑全量 Release、Python、lint、R8、设备、签名或发送。本轮也未启动这些任务。继承的第一候选说明文件是冻结当时的历史记录；其后的执行状态以上表和本轮交接为准。

## 三项失败与修改

### 离根网速 View 被重新接回

失败：`KeyguardNativeSpeedVisibilityRegressionTest.refreshingRestoresEveryNativeSpeedViewThatHasLeftTheRoot`，期望 VISIBLE(0)，实际 GONE(8)。主线程待执行任务的 suppressed 提示仅是线索，不能据此增加 idle 掩盖失败。

同步调用链是：原 speed 在 system_icon_area，双行安装捕获到 systemChildren；测试把两个 speed 移到外部 window；keyguardLayout 先调用 DualRow.apply；isPlaced 发现旧 speed 父级改变，clearManagedViews/placeSystemChildren 将其重新接回根；随后 syncNativeSpeed 仍将它视作有效根内的原生网速并隐藏。原测试在配置更新入口已有主 Looper drain，最后一次 layout Hook 本身同步执行。

修订仅在 State 已 applied 后，剔除离根的精确 NetworkSpeedView 托管记录。首次安装本来会临时拆子，不能误剔除；整根 detach 时子仍属于根，也不能误释放。其他原生 child 的布局修复行为保留。已有缓存恢复逻辑现在能看到实际离根状态。

相邻边界也补入回归：speed 从 A 根交给仍显示模块网速的 B 根后，A 的清理不能删除全局宿主可见性快照并揭开 B 的隐藏。SystemUiHooks 清理时排除正在释放的旧根、查找新的有效根，将视图登记到新根缓存；全局 NET_SPEED 已关闭时不让旧布局状态续藏。

### 时钟首行超出实测高度

失败分别是 `NotificationClockBoundsRegressionTest.expandedRebindingAndDisableRestoreTheFixedNativeRows` 与 `legacyVerticalHeaderFitsLargeTextAcrossDensityAndFontScale`。两者均在原 assertAllTextVisible 条件报 `Clock line 0 exceeds its measured content height`。

静态追踪发现原方案的测量反馈缺口：先在原固定 header 高度中测量，再在布局监听中按旧 Layout 和已受约束的 sibling.measuredHeight 拼新的固定高度。布局完成后发起的请求可能不能让未挂载夹具下一轮相同规格的 measure 重新测量；第二行自身也可能已被约束到 0，继续使用该结果会遗漏其内部固定大小设置按钮的空间。旧失败证据没有具体每步尺寸，报告不把推导的尺寸写成实测值。

修订在首次测量前，将当前通知 header 的原正固定高度改为 WRAP_CONTENT，并把原固定高度与原 minimumHeight 的较大值作为下限，让 LinearLayout 自然测两行。删除按旧 Layout/受限 sibling 手算固定目标的循环。原生非正高度策略保留。

HeightState 同时记录 native/applied height 和 minimumHeight；宿主写入新值时更新原生记录，关闭、横屏或替换只恢复仍由模块持有的值。仅操作当前通知 header 实例，不改共享 dimen、控制中心 header、用户字号/格式/偏移、状态栏时钟或配置/备份/迁移字段。

已查旧本地宿主的两处 header 高度 owner 均写入正固定 dimension，未发现将该 header 写为 WRAP_CONTENT 的路径。完全同值的外部写入无法仅凭 lastApplied 数值区分，属于当前所有权记录的边界；本轮不扩大为全局 View.setLayoutParams Hook。

## 回归保持与静态检查

原两个时钟失败方法的动作、循环次数和断言保持原样；网速原失败方法只增加父级断言，旧动作和可见性条件保留。没有增加 idle、forceLayout、等待或放宽高度条件。高度失败消息只增加实际/测量高度、Layout 行底、padding/scroll、父级高度/最小高度等诊断值。

网速新增离根、parent=null、替换不复活、根内移动、整根重挂和跨根交接回归；时钟新增首次测量即完整两行、原生高度下限、宿主高度/最小高度更新、关闭/横屏/替换恢复。数量及方法保持核验以 delivery/regression-preservation.json 为准，均为源码检查，不是执行通过数。

源码、原失败证据、对照输入和补丁均按哈希冻结。独立静态复审不替代新的 RED/GREEN 或真实设备验收。继承的背景循环、玻璃 UNKNOWN/0、语义颜色、桌面缩放等改动本轮不扩大；上轮相关目标局部通过也不等于最终验收。

## 交接

请使用本轮 delivery/frozen-inputs.json 和 freeze.json 绑定执行输入，复制到指定方的新目录；不要在 work、baseline、旧冻结或对照快照原地构建。完整命令参数参考已保存的上一轮 command.json；保持同一 11 类目标范围并纳入新增方法，仍使用 Release、--rerun-tasks、串行资源窗口。

本轮对照快照使用“第一候选生产代码 + 本轮最新测试”，用于需要重验旧行为/新增边界时执行；已有原三失败证据永久保留。先取得新候选目标结果，再按既有授权决定全量/Python/lint/无签名 R8；不把编译或夹具错误当行为 RED。DouAi 重负载优先。

当前设备重启入口、8.17/KB/s 的归属、截图空白、极限字体/视口及真实 OEM 父级测量/动画仍需设备验收。没有任意背景对比度、全部配置无遮挡或运行性能保证。
