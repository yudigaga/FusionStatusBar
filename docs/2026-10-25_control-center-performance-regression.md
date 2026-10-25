# 控制中心下拉开销回归修复

日期：2026-10-25。用户反馈 0.5.13 的控制中心下拉较 0.5.9 卡顿。本轮比较实际归档源码并使用真实 Native Canvas 绘制计数；未将本地操作次数等同于手机帧率。

## 对照来源

- 0.5.9：提交 `cf6ff50`、构建号 223；归档 `artifacts/releases/v0.5.9/source.zip` SHA-256 为 `071cdd3ad6a0c0c9957fc82cc3c66a5871d687bcc6180a47a9a03d9abdaa011b`。关键动画、网格和材质源码与该提交一致。
- 当前问题基线：提交 `71b1e0e`、0.5.13 / 227。
- 本轮候选：0.5.14 / 228；只调整几何缓存及稳定磁贴的同步路径，保留现有高光软边的颜色和绘制层次。

0.5.9 的动画 Hook、半径/比例动画不触发材质子树扫描的保护、稳定组件跳过材质同步的逻辑均保留。未发现此前转场修复被覆盖。

## 实际复现

证据目录为 [control-center-performance-20261025](../../analysis/control-center-performance-20261025/)。计数来自 `View.draw(Canvas)` 的实际执行，不能直接解释为每个显示刷新周期的次数；HWUI 可以复用显示列表。

| 信号 | 优化前实测 | 修复后针对性实测 |
| --- | --- | --- |
| 两个稳定圆形磁贴，连续绘制 32 次 | `getMode` 调用 64 次 | `getMode` 与 provider 均为 0 次；fallback 继续检查实时模式 |
| 静态圆角玻璃，预热后连续绘制 30 次 | `getFillPath` 调用 180 次 | 新增转换 0 次，复用已生成的描边轮廓 |
| 静态 OEM SmoothRoundDrawable，同样绘制 30 次 | `getFillPath` 调用 180 次 | 新增转换 0 次，原生 Path 原地修改仍可更新 |
| 仅颜色、透明度、主题、位移变化的 4 次绘制 | `getFillPath` 调用 24 次 | 新增转换 0 次，保留相同局部几何 |

初始 RED：磁贴预算 2 项、2 失败；玻璃预算 8 项、3 失败。详见 `tile-mode-red.xml`、`glass-render-red.xml` 及对应构建日志。

针对性 GREEN：共 12 项，失败、错误和跳过均为 0；磁贴实测输出为 `[mode-budget] frames=32 tiles=2 providerGets=0 modeReads=0`。见 `performance-focused-green.log`、`glass-render-focused-green.xml` 和 `tile-mode-focused-green.xml`。

另发现 EDIT 释放通用 active fallback 后，返回 NORMAL 时背景未重建。稳定分支现在保留暂停标记，支持 EDIT→NORMAL 和禁用→启用恢复，同时约束 0% 透明及图标保持。

## 处理与失效规则

- 网格先判断原生图标的稳定状态，再决定是否查询编辑模式；有通用 fallback 或待恢复状态时仍读取实时模式。
- 填充描边路径采用有界缓存，几何相同则复用；颜色、Shader、透明度和位置不属于几何键。
- 实际尺寸、密度引起的描边宽度、圆角、椭圆及原生 Path 内容变化须刷新几何。API 33 的未知原生路径保守重算，避免错误复用。
- 不减少现有软反光的绘制层数，不用截图、位图或强制图层替换；绘制成本仍须由目标手机复验。

## 验证记录

| 验证 | 状态 |
| --- | --- |
| 新增渲染预算、模式预算与恢复回归 | 12 项通过，包含 Outline 路径复用及原生逆向填充路径切换的校验 |
| 0.5.9 动画、材质 dirty、形状与现有玻璃/透明度回归 | 325 项、50 个测试类，失败、错误、跳过均为 0；见 `performance-targeted-final.log` |
| 日夜 Canvas 预览与优化前逐像素一致性 | PNG 字节完全一致；SHA-256 均为 `c508ead25f95fc156a16185b2c0a41b050c95d04fca6c263faad3db69656b670` |
| Release Lint 与装配 | 已通过，0.5.14 / 228；Lint 0 错误、98 警告，仓库高风险规则由打包验证再次检查 |
| 签名、对齐与最终 APK 哈希 | 以 [build-info.json](../artifacts/releases/v0.5.14/build-info.json) 的最终记录为准 |
| 完整 Debug/Release 单元测试、Debug Lint | 本轮未运行 |
| 目标手机的下拉帧耗时、GPU 合成开销及 LSPosed 稳定性 | 尚未验证 |

本地计数通过只能证明重复工作减少和回归契约成立，不能证明目标 ROM 的卡顿已完全消失。最终产物与校验范围以 [build-info.json](../artifacts/releases/v0.5.14/build-info.json) 为准；本轮不发布远端版本。
