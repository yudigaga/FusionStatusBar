# 性能基线准备（0.3.143）

阶段 4 的下一步是建立可重复的开合场景记录。模拟器为默认目标；实体设备只有在显式授权并传入 `-AllowPhysicalDevice` 时才运行。

## 当前可用工具

`tools/measure-settings-jank.ps1` 默认只接受 `emulator-<数字>` serial；实体设备必须显式传入 `-AllowPhysicalDevice`，脚本还会核对 serial 在线、状态为 `device` 且系统已完成启动。它会：

- 启动 `MainActivity`，通过 UI hierarchy 找到状态栏页和设置项；
- 预热两次开合后重置 `gfxinfo`；
- 执行指定次数的打开/返回关闭，采集 `gfx/view/wm` atrace；
- 检查首次显示后的 `relayoutWindow resize=true` 次数；
- 输出独立的 trace 和 JSON 摘要，避免不同轮次覆盖同一证据文件。

默认只报告目标设备帧统计，不启用未经测量的帧率门槛。`MaxPostShowResizes` 可用于只开合设置面板的窗口结构回归，默认值为 0。内嵌选择器展开会改变面板内容高度，不能套用同一零 resize 门槛。

## 采样矩阵

每个场景计划 30 次，使用相同设备、分辨率、字体缩放和刷新率。每轮使用不同 `OutputName`：

```powershell
powershell -NoProfile -File .\tools\measure-settings-jank.ps1 `
  -Serial emulator-5580 -PageTab '状态栏' -ItemText '状态栏监测' `
  -Iterations 30 -OutputName v0.3.143-telemetry-open-close
```

实体设备示例：

```powershell
powershell -NoProfile -File .\tools\measure-settings-jank.ps1 `
  -Serial b7015e9b -AllowPhysicalDevice -PageTab '状态栏' -ItemText '状态栏监测' `
  -Iterations 30 -OutputName real-v0.3.143-telemetry-30
```

需要保留的场景名称：

| 场景 | 页面动作 | 目的 |
| --- | --- | --- |
| cold-start | 冷启动后首次进入概述 | 识别启动遮罩和首屏布局成本 |
| warm-settings | 已启动后连续打开/关闭“状态栏监测” | 验证面板动画和二次 resize |
| warm-layout | 已启动后连续打开/关闭“状态栏布局” | 覆盖长面板和内嵌选择器 |
| editor-choice | 控制中心编辑器打开、展开选择器、关闭 | 覆盖编辑窗口内选择交互 |

每个 JSON 摘要至少记录 `serial`、页面/项目、次数、渲染帧数、janky 百分比、原始 `gfxinfo` 分位摘要、显示后 resize 次数、trace 文件名和采集时间。没有同时记录模块开关对照、刷新率和设备状态时，不得据此给出性能改善百分比。

## 当前状态与下一步

0.3.143 的两组历史 30 次基线继续保留。2026-10-02 完整重启后，回执确认 SystemUI 实际加载 0.3.144 / 158；当前版本的遥测/布局也各完成 30 次复测，前缀为 `build/perf/real-v0.3.144-b7015e9b-20261002-*`。

`tools/analyze-settings-trace.py` 解析主线程 B/E trace 并保留配对/完整性结果。`--max-opening-frame-ms` 仅是诊断 wall-clock 阈值，不能作为显示器 present 帧预算。默认不声明性能门通过。

控制中心选择器和冷启动使用 `tools/measure-settings-flows.py`：

```powershell
python tools/measure-settings-flows.py --serial b7015e9b --allow-physical-device --scenario editor-choice --iterations 30 --output-name real-v0.3.144-b7015e9b-20261002-editor-30
python tools/measure-settings-flows.py --serial b7015e9b --allow-physical-device --scenario cold-start --iterations 30 --output-name real-v0.3.144-b7015e9b-20261002-cold-30
```

每周期分别保存 trace，避免增大每 CPU 缓冲或覆盖长轮次的证据；0/10/20/30 次检查点记录内存和 View/Activity 等对象数量。冷启动每轮进程不同，应使用逐轮渲染和启动时间；最终 renderer 的 gfxinfo 不能代表 30 次总量。UI 检查和资源采集带来观察开销，选择器数据不能与纯开合脚本直接比较。最终 UI 快照也不能证明启动中没有瞬间闪现。

完成采样后先核对 JSON/trace 是否成对生成，再决定是否需要针对稳定可复现的布局或绘制热点做代码优化；单次或单设备结果不用于宣称跨版本提升。
