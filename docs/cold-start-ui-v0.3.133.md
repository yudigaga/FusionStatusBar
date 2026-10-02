# 冷启动旧设置页闪现修正（0.3.133）

用户提供的截图显示冷启动时旧的 XML 设置页先出现，配置读取后该页被 `installMiuixSettingsUi()` 替换，所以视觉上像完整页面弹出后又消失。

## 原因

`MainActivity.onCreate()` 先同步显示 `activity_main.xml`，之后才异步读正式配置与控制中心草稿。读完后，页面初始化逻辑才把原控件隐藏并创建新的五页导航界面。旧布局在异步等待期间可被系统绘制。

## 修改

- 冷启动同步加入覆盖旧内容的应用启动遮罩，显示应用名和加载进度。
- 数据加载后先完成页面搭建、摘要和概览刷新，再移除遮罩；初始页为“概述”。
- 保存所选页签仅用于 Activity 因配置变化重建。应用从后台/任务栈重新创建时回到“概述”；编辑器草稿和撤销状态仍照常恢复。

## 回归验证

- 新增 `coldStartKeepsLegacyLayoutCoveredUntilOverviewIsReady`，确认旧 XML 可见期间遮罩显示，初始化结束后才显示概览。
- 新增 `activityRestoredFromBackgroundStartsOnOverview`，覆盖从后台恢复到新 Activity 的默认页行为。
- 原有 `activityRecreationKeepsDraftModeAndRedoWithoutPublishing` 通过，确认编辑器恢复没有退化。
- API 36 虚拟设备上对 v0.3.133 做 force-stop 后重新启动；启动后的窗口层级显示“概述”，截图不再出现旧 XML 设置页。
- 完整测试、lint、构建与虚拟设备冷启动截图以本版本归档记录为准。实体设备安装、LSPosed 注入及 HyperOS 行为仍未执行。
