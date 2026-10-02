# 控制中心插件 Hook 安装职责拆分（0.3.136）

本版本增加 `ControlCenterPluginHookInstaller`，负责在给定插件 ClassLoader 中按声明解析控制中心插件类、筛选目标方法并注册对应 Hook。方法筛选沿用 `ControlCenterHookPolicy`，保留原 Hook ID 和计数分组。

`SystemUiHooks` 继续负责插件工厂 ClassLoader 捕获、弱引用跟踪、全局句柄表、能力状态、延迟重试及 Hook 回调实现。全局 View/Drawable 材质 Hook 不归入某个插件 loader，并在原有方法分组边界由宿主执行。必需布局能力仍要求 grid content、component bind、tile list 和 tile bind 四类 Hook 都可用。

单测覆盖不同 ClassLoader 的独立解析和注册、目标缺失时其他 Hook 继续安装、重复安装不创建重复句柄、失败方法后续重试，以及全局材质 Hook 位于插件核心组和视觉组之间。窗口模糊 Hook 的 float 参数筛选和 Hook ID 也有直接断言。全局 ready 标志和 retry 条件沿用此前语义；本次不改变多 ClassLoader 的能力聚合策略。没有进行设备验证。
