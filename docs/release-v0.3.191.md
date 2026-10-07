# FusionStatusBar v0.3.191

首阶段 Miuix 迁移：桌面页与桌面相关弹窗现在直接使用 Compose 和 Miuix 0.8.8。

## 更新内容

- 桌面页使用 Miuix Card、SuperSwitch、SuperArrow 和分组标题，替换原生 View 仿制控件。
- 手势属性、动作、功能切换、应用、快捷方式和活动选择使用窗口级 WindowBottomSheet。
- 应用搜索使用 Miuix TextField，列表保留彩色应用图标、包名与冻结/停用/暂停状态。
- 配置存储、Provider、桌面 Hook 与动作 ID 不变，保留已有设置；属性在“完成”后保存，取消丢弃草稿。
- 应用目录、活动目录和图标在后台读取，列表按项复用并使用有界图标缓存；页面销毁时取消任务并释放 Compose 内容。
- Java Activity 使用 AndroidX ComponentActivity 承载 Compose，保留现有导航；状态栏、通知栏、控制中心和设置页暂保持原生 View。

## 构建条件

- 固定 Miuix 0.8.8、Kotlin/Compose Compiler 2.3.20、AndroidX Compose 1.10.5、Activity Compose 1.12.4。
- Miuix Android 依赖包含 Java 21 字节码；构建与单元测试使用 JDK 21。
- AGP 8.13.2 与 Gradle 8.13 支持 Kotlin 2.3 元数据，编译 SDK 36；目标 SDK 35、最低 API 33 不变。
- 上游使用 Apache-2.0 许可，依赖来源为 Maven Central，依赖版本保存在 Gradle lockfile。

## 验证与归档

- Debug、Release 各 1060 项测试通过，0 失败、0 错误、0 跳过；工具回归测试 25 项通过。两套 lint 均 0 错误、79 条警告，高风险策略通过。
- Compose 行为测试覆盖动作草稿、搜索目标、隐藏勾选、取消、冻结包、活动与功能切换。
- Native Graphics 渲染检查覆盖浅色/深色、320dp 小屏与 1.5 倍字体，导出实际页面和弹窗位图；检查滚动、图标、空状态、焦点与文字边界。
- 首轮全量验证暴露测试 JVM 内存耗尽；测试堆调整为 2GB，并每 40 个测试类重启进程。重新完整运行后两套测试均通过。
- CI 与归档脚本同步使用 JDK 21/SDK 36；Miuix 许可包含在源码与 APK 的 `META-INF/miuix-license.txt` 中。
- 完整测试、lint、签名、版本元数据与源码快照见归档中的 `release.json` 和 `validation.md`。
- 本次未进行实体设备安装与 Hook 注入验证。
- 签名 APK 归档至 `fusion-statusbar-feedback-v0.3.183/artifacts/releases/v0.3.191/`。
