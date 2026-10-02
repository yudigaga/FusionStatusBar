# 性能基线准备记录

日期：2026-10-01。对应源码版本：`0.3.143 / 157`。

本记录只整理可重复的测量入口和当前静态指标。按项目范围约束，本轮不连接实体手机、不执行 LSPosed 注入、不进行 HyperOS 验收，也不把本地静态检查或 Robolectric 结果当作设备帧耗时。

## 当前静态指标

在当前工作树（包含 `0.3.143` 遥测位置选择器改动）读取 `app/src/main/java/com/xtjm/fusionstatusbar` 得到：

| 指标 | 当前值 | 说明 |
| --- | ---: | --- |
| Java 源文件 | 96 | 主源码目录，包含运行时、页面和适配器 |
| Java 源代码行数 | 27,158 | 按 UTF-8 行计数，包含空行和注释 |
| `MainActivity.java` | 4,173 行 | 页面导航、设置绑定与控制中心编辑宿主 |
| `SystemUiHooks.java` | 4,256 行 | SystemUI Hook 与运行时副作用入口 |
| `ControlCenterRuntimeGrid.java` | 1,572 行 | 控制中心运行时布局与资源生命周期 |
| `requestLayout` 调用 | 28 | 静态热点候选，不等于每帧调用次数 |
| `invalidate` 调用 | 36 | 静态热点候选，不等于实际重绘次数 |
| `getDeclaredMethod` 调用 | 20 | 反射查找候选；需按 ClassLoader 和调用频率取样 |
| `Class.forName` 调用 | 4 | 反射类解析候选 |
| `BitmapFactory.decode*` 调用 | 4 | 截图/图标解码候选，需确认线程和输入大小 |

这些数值用于后续复测时确认改动范围，不构成 CPU、内存、帧率或耗电结论。

## 已有测量入口

`tools/measure-settings-jank.ps1` 提供设置项弹窗的模拟器结构和帧统计脚本。脚本会：

1. 校验模拟器已启动并进入 `MainActivity`；
2. 在同一设置页打开/关闭“状态栏监测”项目；
3. 重置 `gfxinfo`，重复指定次数；
4. 采集 `gfxinfo` 的总帧数、卡顿帧百分比和 50/90/95/99 分位信息；
5. 通过 `atrace` 保存 `gfx/view/wm` 跟踪，并统计首次显示后的窗口重布局。

建议基线命令（仅在获准的 Android 模拟器上执行）：

```powershell
powershell -NoProfile -File .\tools\measure-settings-jank.ps1 `
  -Serial emulator-5580 -Iterations 30 -OutputName v0.3.143-settings-open-close `
  -MinRenderedFrames 1
```

默认只报告指标，不设置未经采样确定的卡顿阈值。对照时应固定模拟器镜像、分辨率、刷新率、动画倍率和后台进程；优化前后使用同样的串号、页面、迭代次数，并保留 `build/perf/<OutputName>.trace` 与 `build/perf/<OutputName>.json`。

## 当前采样状态

后续已连接设备 `b7015e9b` 并完成有限实测：冷启动 201 帧、Janky 1.00%；“状态栏监测”开合 5 次 215 帧、Janky 1.40%，均未发现显示后的二次窗口 resize。另有应用/SystemUI PSS 和 CPU 快照。用户要求跳过 30 次矩阵和测试执行，因此这些数据只作为单次设备证据，不能据此宣称跨版本性能改善。

进入允许的设备/模拟器测量阶段后，最小采样矩阵应覆盖：冷启动、暖启动、设置项开合、控制中心连续开合、编辑/推送、SystemUI 重启；每个场景至少 30 次，再依据实际刷新率计算 16.7 ms（60 Hz）或 8.3 ms（120 Hz）帧预算。实体设备项目仍保持未验证标记。
