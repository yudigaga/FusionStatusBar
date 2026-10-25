# 高光玻璃软边与通知裁剪修复

日期：2026-10-25，Asia/Shanghai。本轮处理关闭全局边框后仍出现硬白线，以及通知背景底部反光断线的问题。当前仍为 Canvas 兼容玻璃，没有实现 HyperBetter 的真实折射；新代码尚未经过目标真机视觉与稳定性验收。

## 本地证据

输入为 `F:\qq\MobileFile\LSPosed_20261025_191023.zip`。证据提取目录为 [glass-white-rim-20261025-191023](../../analysis/glass-white-rim-20261025-191023/)，摘要见 [triage-summary.json](../../analysis/glass-white-rim-20261025-191023/triage-summary.json)。

- 最新 PID `8412` 于 `19:08:57.804` 加载 `0.5.12 / 226`；`19:09:01.011` 收到 `HIGHLIGHT_GLASS`、模糊 `100/100/100`、`border=false`，来源日志行 `2551`、`2630`，见 [v0.5.12-key-evidence.txt](../../analysis/glass-white-rim-20261025-191023/v0.5.12-key-evidence.txt)。
- 全局配置与同进程 SystemUI 配置均记录 `uiMode=17`，对应浅色模式；日志没有记录每个消费 View 的本地 `uiMode`，不能将其当作全部 View 的直接测量。
- 装载阶段曾出现 `control_center_items_unavailable`，随后于 `19:09:11.266` 恢复为 `applied / control_center_mounted`，修订号 `68`、来源日志行 `2748`。本次最新状态已应用，不能把前面的暂态错误当作持续降级。
- [device-properties.txt](../../analysis/glass-white-rim-20261025-191023/device-properties.txt) 记录 HyperOS 3、Android 16 / SDK 36。旧模糊接口 `5/5` 与 bloom/shadow 接口 `5/5` 只说明旧接口存在，不证明完整 MiGlass 材质已工作。

## 发现与修复路径

| 证据 | 发现 | 对应改动 |
| --- | --- | --- |
| `border=false` 配置及 `ControlCenterSoftGlass` 原实现 | 原有 `1.2dp` 外线与 `0.7dp` 内线属于材质自身，关闭全局边框后仍会绘制 | 自动高光的日夜模式统一改用渐隐软倒角和局部方向反光；手动柔光玻璃保持原有样式 |
| `SystemSurfaceStyle.SurfaceDrawable` 原有夜间白色细描边 | 自动夜间与 QS 使用不同边缘绘制路径 | QS 与系统表面复用 `ControlCenterSoftGlass.GlassDrawable` 的软边计算 |
| `NotificationSurfaceHooks.applyRow → BoundedBackground.draw` | 通知的实际高度、RTL 宽度、展开边界和原生淡出只包裹背景内容，独立 ViewOverlay 反光没有共同继承这些限制 | 通知反光放入 `SystemSurfaceStyle.SurfaceDrawable` 内部绘制，随 `BoundedBackground` 一起裁剪、淡出及恢复 |
| 主题预绘制回调绕过 `applyRow` 的解包路径 | 包住同一材质的 `BoundedBackground` 被误记为 OEM 原背景，会形成自引用；切回原生样式或关闭时有恢复风险 | `ownsBackground` 识别自有包装，保留原始背景及通知边界包装 |
| 折叠卡片可见高度与 View 测量高度不同 | 重复刷新时渐变缓存会在完整尺寸和实际尺寸间交替重建 | 背景拥有的反光保留上次实际尺寸，由绘制时的有效边界统一更新 |

生产改动见 [ControlCenterSoftGlass.java](../app/src/main/java/com/xtjm/fusionstatusbar/ControlCenterSoftGlass.java)、[SystemSurfaceStyle.java](../app/src/main/java/com/xtjm/fusionstatusbar/SystemSurfaceStyle.java) 和 [ControlCenterSurfaceShape.java](../app/src/main/java/com/xtjm/fusionstatusbar/ControlCenterSurfaceShape.java)。背景中心、文字、图标、形状裁角及 0 强度恢复仍由回归测试约束。

## 回归与验证状态

修改前已运行软边 RED：`4 tests / 3 failures / 0 errors / 0 skipped`。失败分别为自动夜间 QS/系统表面边缘不一致，以及日间、夜间反光仅集中于硬线、缺少多像素渐隐。证据为 [soft-edge-red.log](../../analysis/glass-white-rim-20261025-191023/soft-edge-red.log) 和 [soft-edge-red.xml](../../analysis/glass-white-rim-20261025-191023/soft-edge-red.xml)。

生命周期补充 RED：`6 tests / 4 failures / 0 errors / 0 skipped`，其中两项捕获主题刷新后的错误原背景身份，两项捕获折叠通知及切主题后反复重建渐变。证据为 [notification-lifecycle-red.log](../../analysis/glass-white-rim-20261025-191023/notification-lifecycle-red.log)、[notification-theme-red.xml](../../analysis/glass-white-rim-20261025-191023/notification-theme-red.xml) 与 [notification-cache-red.xml](../../analysis/glass-white-rim-20261025-191023/notification-cache-red.xml)。这些是本地复现的缺陷，不是本次手机退出的因果证明。

本轮新增 16 项测试：

- [ControlCenterGlassSoftEdgeRegressionTest.java](../app/src/test/java/com/xtjm/fusionstatusbar/ControlCenterGlassSoftEdgeRegressionTest.java)：4 项，覆盖日夜共用边缘、多深度渐隐、正文与裁角保持、关闭及 0 强度恢复。
- [NotificationGlassBoundsRegressionTest.java](../app/src/test/java/com/xtjm/fusionstatusbar/NotificationGlassBoundsRegressionTest.java)：6 项，走真实 `applyRow` 链，覆盖折叠裁剪、RTL 实际宽度、展开边界、原生淡出、完全裁剪及恢复。
- [NotificationGlassThemeRestoreRegressionTest.java](../app/src/test/java/com/xtjm/fusionstatusbar/NotificationGlassThemeRestoreRegressionTest.java)：2 项，检查原背景身份、包装层保持及切回原生样式、关闭后的恢复。
- [NotificationGlassShaderCacheRegressionTest.java](../app/src/test/java/com/xtjm/fusionstatusbar/NotificationGlassShaderCacheRegressionTest.java)：4 项，检查重复刷新、淡出、实际高度及主题变化后的渐变对象复用。

| 验证范围 | 状态 |
| --- | --- |
| 新增 16 项测试的最终 GREEN | 已通过，包含在最终相关回归中 |
| 相关 Debug 回归 | 221 项、40 个测试类；失败、错误、跳过均为 0；见 `soft-edge-targeted-final.log` |
| 完整 Debug/Release 单元测试与 Debug Lint | 本轮未运行 |
| Release Lint | 已通过，0 错误、98 警告；仓库高风险规则由打包校验再次检查 |
| Release 构建 | 已通过，`0.5.13 / 227` |
| 签名与最终产物核验 | 以本地归档 [build-info.json](../artifacts/releases/v0.5.13/build-info.json) 中的签名、对齐及 SHA-256 为最终记录 |
| 目标真机安装、LSPosed 绘制、视觉与稳定性验证 | 尚未执行 |

本地 Canvas 预览见 [after-day-night.png](../../analysis/glass-white-rim-20261025-191023/after-day-night.png)，使用合成背景，不模拟 ROM 合成器的真实模糊或折射。自动夜间保留既有暗色背景，使用独立软边；手动柔光玻璃的原始背景及细线样式保持不变。

以上本地证据和测试不能证明真实折射、目标 ROM 原生玻璃成功，亦不能将此前未定位的重启直接归因于本轮边缘绘制问题。
