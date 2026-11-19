# 第三次修订：大时钟与设置按钮碰撞

02 独立复测 11 类 152 项，3 failures、0 errors/skipped，退出 1。原网速失败及该类 20 项已通过目标测试；三项时钟已越过文字高度检查，仍在碰撞检查失败。之后的 header 内部边界断言在这三个失败方法中尚未执行，不能宣称通过。

新 baseline/work 从 02 冻结的 717 项逐一核对复制；21 份完整独立 XML/命令/日志/最终哈希证据按字节保存。旧冻结、原失败证据和主线不改。

- [x] 读完整 XML 与三个失败栈，核对源码和执行副本哈希。
- [x] 两份独立只读诊断确认 CENTER_VERTICAL 的负 bottomMargin 同时影响 measured contentHeight 与 childTop；旧 padding 只补测量损失，仍有半边距位置外延。
- [x] 保留原 margin，horizontal LinearLayout 按有效 CENTER_VERTICAL 整数公式或 BOTTOM+baseline descent 上界补足行底外延；其他分支保留原合同，运行待验。
- [x] 正 translationY 向上取整，覆盖亚像素位移；源码首遍即可分配，无回调后测量重试。
- [x] 原三个失败方法、原碰撞条件及 19 个已有方法保持；只补失败诊断文本和新增 4 个边界方法源码。
- [x] 新边界源码覆盖奇数 margin、非零 topMargin、有效 child gravity、baseline、RTL、小数 translation、重复回调、宿主 padding 更新/关闭恢复。
- [ ] 独立静态复审，核对网速/背景/颜色等输入均未改变；补丁回放、输入及旧证据哈希校验后冻结。
- [ ] 回原会话交指定方按 DouAi 优先资源窗口复测。当前工作方不执行测试/编译/Gradle/lint/R8，不签名发送或操作设备。

现有 XML 没有坐标 stdout/stderr。负 6dp 推导的剩余半边距是源代码公式结论，不是现场实测像素。新增诊断应能在下轮失败 XML 给出实际矩形，不能以容差/隐藏按钮/减用户字号绕过要求。

独立只读复审未见剩余必须先修的问题。TOP 任意混合 baseline 兄弟的测量包络可能被原生 alternativeMaxHeight 覆盖，不泛化此类安全结论，也不为未知自定义布局扩大本轮实现。最终冻结/回放/旧证据核验以 delivery 元数据为准，复测未完成不勾选。
