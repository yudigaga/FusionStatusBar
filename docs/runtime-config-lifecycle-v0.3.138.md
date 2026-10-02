# SystemUI 配置生命周期职责拆分（0.3.138）

本版本增加 `RuntimeConfigLifecycle`，统一管理配置运行时首次启动、application Context、observer reload/preview 回调接线，以及配置读取不可用或配置应用异常后的指数退避重试。

它组合已有 `RuntimeConfigCoordinator` 和 `RuntimeObserverRegistration`：前者继续负责后台串行读取、最新请求合并、修订号检查和应用回执，后者继续负责观察者/解锁广播的独立注册重试。`SystemUiHooks` 保留 `applyLoadedConfig` 的 UI 与 SystemUI 运行时副作用。

测试覆盖并发首次使用、observer 回调接线、报告上下文、退避重置、配置应用失败，以及调度器拒绝后恢复重试状态。没有进行实体设备验证。
