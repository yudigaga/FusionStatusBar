# 融合图标充电显示开关

本地版本 `0.5.22`（`236`）。在“融合图标 → 显示 → 充电时显示充电图标”中选择充电期间的电量内容。

| 开关 | 普通充电 | 快充 | 未充电 |
| --- | --- | --- | --- |
| 开启（默认） | 闪电 | 快充闪电 | 电量数字 |
| 关闭 | 电量数字 | 电量数字 | 电量数字 |

开关只控制数字与闪电的替换，圆环仍沿用原有充电配色与电量进度。电量内容的位置、缩放和无障碍充电状态说明沿用现有配置。

配置键为 `fusion_charging_icon`，由 `FusionContentConfig` 统一读写。缺少该键的旧配置及旧 schema 6 完整备份默认开启；无需改写其他配置。`sameAs` 包含该字段，保存后由既有配置通知刷新图标。

## 验证方式

`FusionChargingIconTest` 覆盖普通充电、快充的原生 Canvas 像素比较，配置存储、其他编辑保留开关状态、刷新差异及新旧备份兼容。`SettingsPageControllerTest.chargingIconSwitchUpdatesConfigAndReopensWithSavedChoice` 覆盖点击开关与重新打开设置。

先运行新增测试确认旧实现出现四项预期失败，再实现开关并运行相关测试。完整构建与测试结果以本地归档 `artifacts/releases/v0.5.22/release.json` 和测试、lint 报告为准。

本地归档不代表真机验收。未进行设备安装、真实 LSPosed 注入和 ROM 显示验证；此任务不包含远端发布。
