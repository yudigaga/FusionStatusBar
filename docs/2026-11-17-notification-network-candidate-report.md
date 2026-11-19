# FusionStatusBar 通知与网速反馈修订候选

本轮交付是**待独立验证的源码候选**。没有执行本轮单测、编译、lint、Gradle、R8、设备测试或签名，没有新 APK。版本字段仍为 0.5.27 / 241。旧版本 1694 项通过记录不能作为本轮结论。

工作目录：`E:\vs\claude\xtjm\fusion-notification-network-20261117-01`。`baseline` 来自上轮冻结的 705 个输入，逐文件核对 SHA-256。修改只在新 `work`。既有主线、旧冻结、ZIP、已发签名 APK 不作集成或覆盖。

## 证据

用户提供 ZIP 的 SHA-256 为 `be3b5d3a6a8e91a68408cd0b760fe8d6a8253764b6f84d08cba2a6191fbdf2aa`。选取的 10 份 gzip 崩溃报告均为 `com.android.systemui` 的 `StackOverflowError`；解压同时核验所读 ZIP 成员 CRC。保留了报告中现有的全部栈帧，但不能补出原报告截断之外的帧。

准确 R8 Retrace 8.13.19、mapping SHA-256 `b1f5c70ef374effea2edf1d1266b075c07751d3d7a75d441d716e00ec2558446`，日志与 mapping 的 pg_map_id 均为 `541a0e55b1c5673c2fd529abb282069aa74d6e70dcc6c267c36c1abd26c3f86d`。10 份还原均无 stderr。重复帧为 `BoundedBackground.draw:342`、`SurfaceDrawable.draw:586`，每个 Surface 约有两个 Bounded 帧。已发签名 APK SHA-256 `98a057f9eba2fcb4c1ead345b6fcc88631e0d993952c554471111be766ebb725` 的 DEX 与对应既有无签名构建逐项一致。

原始完整报告仅存新目录 `evidence/private`，不加入源码交付。不上传敏感日志。

旧本地 SystemUI 样本 `systemui.apk` / `系统界面_16.03.251211.r.apk` 的 SHA-256 同为 `1b8a6cf73c3bb3e96fd795e648c4edd1e5e790b6f449465710296fde861c3ce5`。已有 `jadx-controlcenter` 的 manifest 自报 16.03.251211.r / 202501210，与日志版本字符串相同，但缺少该解包目录完整提取溯源，也不能证明与用户 thor ROM 是同一二进制。以下只将其用作宿主合同反例。

## 分项修改与证明范围

| 反馈 | 源码候选改动 | 尚未证明的部分 |
|---|---|---|
| 左上下拉、有通知时反复展开收起偶发重启 | 不再把模块包装图接纳为宿主原图；标准容器有界 identity 图检查；两层 revision 防止同步关闭/重开后的旧调用覆盖新状态；动态原图再次检查 | 可构造与日志相同的两个 Bounded / 一个 Surface 拓扑，仍未证明 thor 的具体重入入口，未运行 RED/GREEN 或真机复现 |
| 桌面上下行网速右侧空白 | 原生图标按当前矩阵的可见包络预留，读数按剩余空间重测/布局；覆盖左右、RTL、缩小和放大 | 未见本地截图/现场层级；宿主父框内部空白没有凭猜测压缩，不能宣称截图间距已修好 |
| 锁屏额外读数交叠 | phone/keyguard 分根管理；只有模块网速实际挂载才隐藏原生；各根缓存、重挂发现、离根/关闭恢复；优先精确 updateVisibility，缺失才回退 controller，避免双 Hook | 额外 8.17/KB/s 不符合当前模块上下行 formatter，归属仍未知；现场配置、动画/回调和实际层级未验证 |
| 时间过大，第二排日期看不到 | 合并文本保留日期字号，取消固定两行及省略限制；时间/容器按文本测量；仅通知 header 实例按所需高度分配；补正正 Y 位移和原负下边距占用；列表按实测文字底边留净空；关闭及新宿主尺寸条件恢复 | 不改共享高度资源及独立控制中心 header；旧样本存在重新设置实例高度的 owner，实际展开动画/控制中心切换收敛、小屏极限配置及隐私/运营商等全部邻居仍需设备验收 |
| 日间黑界面黑字不清 | 当前通知路径没有统一改黑文字；修订自动 HIGHLIGHT 的 0 端点，所有强度均保留可确认的宿主原图/语义色，未知图退还宿主绘制 | 未取得用户实际模式/强度，不能认定此次黑底一定来自 0；原生本已透明或自定义内容本身低对比度，不承诺任意背景对比度 |

配置字段、备份和迁移格式未改。显式 CLEAR 和 DEFAULT 的既有强度语义保留。自动 HIGHLIGHT 的新取舍是“强度 0 仍保留宿主通知背景”，不会把配置数字自动改成别的值。通知文本、强调色、状态色、媒体及 RemoteViews 内容保持宿主所有权；菜单/音量对 UNKNOWN 容器的原有行为保留。

## 回归源码和静态复审

新增 5 个专属类共 **51 个 `@Test` 入口源码**：背景循环 12、网速生命周期 14、缩放占位 6、日间背景 7、时钟边界 12。另在 Hook 安装器原测试加入 3 项，三份已有通知测试只调整自动 HIGHLIGHT 0 的旧透明预期。以上数字是静态源码计数，绝不是执行通过数。

背景循环夹具走真实 `View.setBackground`，不声明 OEM `mBackground` shadow；覆盖交叉重绑、标准/可变容器、同步关闭重开和恢复期间重开。Identity 图断言在绘制前失败，防止基线 RED 耗尽执行器栈。

网速夹具使用实际可安装双列和模块读数、宿主 controller 重复同值早退、两原生 View 同时离根。时钟夹具包含柔性父布局和参考 XML 的 vertical/bottom、125dp header、64dp time、-6dp margin、不同父级日期与设置入口；测量真实 TextView/Spannable，检查行尾、边界、字体比例、密度、重绑恢复和坐标映射。列表净空向上取整，兼容旧样本的 int 截断。

独立代理分别静态复审背景图、网速生命周期/安装器、时钟恢复/矩阵/整数化。发现的重复 controller、批量离根恢复、关闭后的高度所有权、宿主新尺寸、隐藏祖先净空问题均加入源码修订和回归。没有静态复审替代运行验证的结论。

图检查上限为 128 个节点、32 层。公开 API 无法穷举任意不透明自定义 Drawable 的内部委托；UNKNOWN 通知图退出样式并保留外部宿主对象。没有捕获 StackOverflowError 来掩盖绘制问题。没有性能/60Hz/120Hz、OEM compositor 模糊或真机可读性结论。

## 独立验证交接

`delivery/freeze.json` 和 `frozen-inputs.json` 是候选权威输入；`red-02/inputs.json` 对应原生产代码加最新回归源码。只读快照必须复制到执行方新目录后运行，不能直接在 work、baseline、red-02 或旧冻结内构建。

DouAi 重负载优先。先串行运行上述 5 类和 `NotificationHookInstallerTest` 的 Release 单测，对比 RED 与候选；基线失败须区分预期断言、fixture/编译错误和环境问题，不得把编译失败当行为 RED。再按已有授权安排完整 Release 单测、Python、lint、无签名 R8/产物检查。`--tests` 只用于 testReleaseUnitTest，不用于 lintRelease。所有结果需绑输入 manifest SHA-256。

本轮没有启动这些命令。跨聊天交接工具两次返回 `Invalid request: AbsolutePathBuf deserialized without a base path`，当时未成功发送验证任务；不擅自改模型、代理或权限来绕过。交接状态另记入 delivery。

设备验收至少包括：有通知时左上反复展开/收起、普通及 heads-up；桌面/锁屏原生与模块网速及启闭恢复；大时间小日期、长/多行格式、宽度/density/fontScale、横竖和重绑；日间黑/浅/复杂背景、普通/Messaging/媒体/自定义通知、主题切换及关闭恢复。通过前不得签名、发送、集成或发布。
