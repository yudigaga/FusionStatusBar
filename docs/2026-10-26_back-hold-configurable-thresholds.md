# 返回停顿的距离与等待时间配置

日期：2026-10-26。范围：在 0.5.19 的等待取消与就绪提示规则上，允许用户自定义距离和等待时间，生成新的本地正式签名 APK。

## 配置与保存

`DesktopConfig.backHoldDistanceDp` 默认 `0`（跟随当前宿主原生门槛）；自定义值为 16～240 dp。`backHoldDelayMs` 默认 500，有效范围 200～3000 ms。新字段分别以 `back_hold_distance_dp`、`back_hold_delay_ms` 保存。旧数据缺失两键时保留原有默认行为。模型统一约束越界值，UI 对无效输入给出错误且不保存。

所有不可变配置变换、Bundle 往返、SharedPreferences、主机缓存、相等性和哈希均保留两值。UI 子编辑页只修改草稿，外层“完成”才落盘；取消、恢复默认、切换动作/目标均有独立用例。摘要显示实际阈值，说明不再写死 0.5 秒。

运行时沿 `DesktopConfigStore → RuntimeConfigExchange.publish → receiveHostConfig → 主机 Store → LauncherHooks.config` 传递配置；新增实际发布/接收 seam 用例验证桌面缓存写入在刷新回调之前，且设置包数据变化不会无故改写已接收缓存。

## 手势判定

每次原生接受手势时保存不可变配置快照，并读取当前 View 的密度。距离按 `dp × density` 换算；时间同时用于 MOVE 判定、绝对定时截止和 READY 的时间。捕获等待仍为 100 ms，就绪前的明显移动会重新计时；提前松手仍取消等待。

自定义距离使用包含边界的 `>=` 判定，默认跟随系统继续使用其原生规则。跟随系统时优先调用原生门槛，方法缺失或异常时保留既有回退。自定义距离下，等待/就绪退出带分别为 12/24 dp，并以自定义距离的一半为上限，确保最短 16 dp 设置下完整回撤仍能取消。

宿主的 `onSwipeProcess → GestureBackArrowView.onSwipeProgress → isShowBackArrow` 决定箭头和首段准备反馈。新增可选的精确静态 predicate Hook，仅在同一原生 MOVE 帧、自定义动作配置完整且距离为自定义值时使用该快照的距离规则；UP 和帧外调用继续执行原生方法。原生箭头进度调用点也请求反优化，降低已编译调用绕开拦截的风险；它仍属于真机待验部分。

模块内的自定义距离直接计算，避免经已 Hook 的反射 predicate 递归；自动模式仍调用原生。进入等待后，配置改变了任一阈值会使旧动作准备失效并取消；下一次手势使用新值。READY 提示、原生取消清理和动作执行保持前版顺序，执行时不补震。

## 验证与边界

新增实际 Hook seam 测试覆盖 200/500/1000/3000 ms、短/长距离、不同密度、左右边缘、退出迟滞、16 dp 完整回撤、配置更改、外观变更不干扰、原生 MOVE 反馈与 UP 规则分离。模型测试覆盖旧配置默认、往返、范围约束、所有变换保留；Compose 测试覆盖输入、校验、保存、取消、恢复默认与切换目标。

最终 Debug/Release 各 26 类、242 项定向测试通过，失败、错误、跳过均为 0；包含 22 项自定义阈值 Hook 用例、23 项模型、21 项 Compose 界面和 8 项运行配置交换用例，以及既有返回、反馈、颜色和主题回归。Python 构建工具回归 28 项通过。执行日志为 `analysis/back-hold-thresholds-final-tests-20261026.log` 与 `back-hold-thresholds-python-tests-20261026.log`。

Debug/Release APK 装配及构建元数据核验通过；Debug lint 为 0 错误、99 警告，Release lint 为 0 错误、98 警告，高风险规则通过。构建采用既有低内存 JVM 配置串行完成，未关闭 lint 错误检查。

宿主仍需先接受边缘手势；提供的 Home 代码以约 20 px 作为原生接受条件，因此 mdpi 下设置 16 dp 时仍受这一较小下界限制。本轮未修改宿主的原生接受区域。

最终定向测试、lint、APK 装配、签名、16 KiB 对齐、源码快照和最终 APK 哈希保存在 `artifacts/releases/v0.5.20/release.json` 及同目录证据。完整测试套件、手机安装、真实 Hook 执行及反馈手感未验证。仅生成本地正式签名版本，未发布远端。
