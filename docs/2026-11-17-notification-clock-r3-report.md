# 第三次修订：时钟文字与原生设置按钮碰撞

本轮是待独立验证的源码候选。没有本轮测试、编译、lint、R8、设备或性能通过结论，无新 APK，不签名发送、集成或发布。版本仍为 0.5.27 / 241。

## 上一候选的真实结果

02 独立执行证据位于 `E:\vs\claude\xtjm\fusion-network-independent-20261117-02`。完整 XML 汇总为 11 类、152 项、3 failures、0 errors/skipped，Gradle 退出 1。717 个输入最终哈希未变，manifest SHA-256 为 `458a7f76c4cec5b42126ace0323b5f50af8fe5c3bdf768f3f002f8d36b6fe90a`。

网速类 20 项目标测试为 0 failures/errors/skipped，包括此前离根失败。但这不是完整套件或设备验收。本轮不改网速、通知背景、语义颜色、玻璃或其他反馈的源码。

三项仍失败的时钟方法是：

- `firstEnabledMeasureFitsBothNativeRowsWithoutASecondTraversal`：line 3 覆盖第二行原生设置按钮。
- `expandedRebindingAndDisableRestoreTheFixedNativeRows`：line 3 覆盖。
- `legacyVerticalHeaderFitsLargeTextAcrossDensityAndFontScale`：line 2 覆盖。

三项都先通过原 `assertAllTextVisible`，再在 `assertShortcutClear` 失败。后面的 `assertTextInsideNativeHeader` 及行高检查在这三项中没有执行，不能宣称通过。旧 XML 的 stdout/stderr 未记录实际坐标，因此不能把静态推导的余量写成现场实测像素。

新 03 的 baseline/work 从 717 项冻结输入逐文件复制；完整命令、日志、失败栈、最终校验及全部结果文件共 21 项另存 `evidence/independent-02`，原件/副本均核对 SHA-256。旧冻结、失败证据和主线不改写。

## 确认的布局缺口

本地参考 XML `jadx-controlcenter/resources/res/layout/miui_ns_notification_header_view.xml` 的首行是横向 LinearLayout、CENTER_VERTICAL，big_time 的 bottomMargin 为负；第二行放原生 shortcut。NativeHeader 夹具保留了该关键垂直合同。参考宿主版本字符串相同不等于当前设备二进制身份，也不是设备验收。

令时钟实测高度为 H，marginTop 为 a，marginBottom 为 b，首行去掉 padding 的高度为 C。Android 横向 LinearLayout 的居中定位为：

```text
childTop = paddingTop + trunc((C-H)/2) + a - b
```

测量包含垂直 margins；在时钟主导高度、a=0、b=-m 时，C=H-m，因此位置还会向下移动 ceil(m/2)。02 只加了 m 的底部 padding 来补测量损失，并没有为这段定位外延留出空间。重复布局沿用相同公式，不能消除差额。

例如 m=6px、整数 Y 且文字/按钮没有额外空隙时，静态公式推导仍有 3px 外延；这不是当前 XML 的坐标实测值。换行和密度会改变发生碰撞的行号，不能据此认为字体或原断言有错。

## 最小源码修订

保持原生 margins、用户字号/格式/偏移和设置按钮可见性。仅在时钟直接属于 horizontal LinearLayout 时读取有效垂直 gravity，child 的 gravity 优先于父行：

- CENTER_VERTICAL：按原生整数公式保留至少 `(a+b)/2 - 2*b` 的行底外延，并与原 `max(0,-b)` 取较大值。保留 Java 向零整数除法，不能换成负数向下取整。
- BOTTOM 且 baselineAligned：原生 descent 同样含 margins，保守上界为 `-a-2*b`，仍与旧预留取较大值。
- 其余分支保持原预留合同。正 translationY 改为向上取整，避免 0.25/0.75px 等位移被 round 少算。

新计算不读取上一帧的 measuredHeight/baseline，不依赖额外布局遍数；仍通过 `original.bottom + below` 写入，继续使用原有宿主 padding 更新和关闭恢复机制，不增加 margin 所有权状态。

独立静态复审核对了公式、奇数截断、gravity 覆盖、首次测量、重复回调及恢复。没有由静态复审推断新候选已通过测试。

## 测试与诊断源码

原 19 个测试方法主体、NativeFixture/NativeHeader/Header/Fixture、nativeLayout、measureNative、坐标映射和原 `RectF.intersects(lineBounds(clock,line), target)` 条件均保持。没有增加 idle、forceLayout、测量重试、容差、隐藏按钮或减小用户字号。

新增 4 个入口，覆盖奇偶负边距和非零 topMargin、有效 gravity、baseline、LTR/RTL、小数 Y、重复回调不累计和宿主 padding 更新后的关闭恢复。新案例还要求文字行不超出首行底边，避免仅因按钮横向错开而漏掉垂直外溢。

碰撞失败消息增加文字/按钮 root 矩形、实际和测量尺寸、Layout 行边界、padding/scroll、translation/scale/pivot、父行 gravity/baseline/margins、header/list floor。若独立复测仍失败，完整 XML 可以直接给出几何值。原成功条件没有改动。

## 证明范围与交接

任意自定义父布局、scaleY 大于 1 的新 footer 布局、TOP+baselineAligned 且同排不同 baseline 兄弟的通用保障不在本轮证明范围。AOSP 非 EXACT 测量可能用 alternativeMaxHeight 覆盖 baseline 包络，不能泛称所有 TOP 混排安全。已核对的原生 CENTER_VERTICAL 合同与本轮新增 BOTTOM 边界有明确公式；不扩大为全局布局 Hook。

冻结权威为 delivery/freeze.json 和 frozen-inputs.json；regression-preservation.json 保存逐块/逐方法保持核验及未变源文件范围。执行前必须复制到指定验证方的新目录并核对 manifest，不能在冻结副本原地构建。

提供对照快照：02 生产代码加本轮最新诊断/测试源码。指定方可在资源窗口先只跑这三个原失败方法，取得原始几何，再跑 03 同一 11 类目标及新增方法。对照尚未执行，不能代替现有 02 失败证据。DouAi 重负载优先；目标门禁通过后才按授权安排后续全量/Python/lint/无签名 R8。本工作方不自行启动这些任务。

具体设备展开/收起、真实 ROM 父级/方向、时钟/日期/设置/列表联合边界仍需验收；不承诺全部配置或任意背景的可读性。
