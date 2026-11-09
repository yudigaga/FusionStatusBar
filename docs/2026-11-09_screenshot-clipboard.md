# 截屏自动写入剪贴板

本地版本 `0.5.23`（`237`）。入口：“杂项 → 截屏 → 截屏自动写入剪贴板”。默认关闭；启用后保持 Secure 设置 `mi_screen_shots_write_clipboard_enable=1`，关闭时写入 `0` 并解除保护。开关与“移除链式启动弹窗”独立保存。

首次安装此功能后，需在 LSPosed 勾选“系统（system / system_server）”作用域并重启手机，让系统设置服务的 Hook 加载。此后修改开关通过已有签名广播和配置通知应用，无需每次重启。

## 防止被重新关闭

安全服务 `12.2.8-260204.0.1` 样本中，`com.miui.securitycenter.remote` 进程初始化会通过 `N6.C.b()` 读取此设置并将 `1` 改回 `0`。本功能在系统侧实际 SettingsProvider 的 `mutateSecureSetting` 中保护唯一目标键，不依赖安全服务混淆类名。

- 开启时：目标键的插入、更新、删除和 null 写入都规范为写 `1`；其他键和未知操作保持原样。
- 原始权限、跨用户和系统策略检查仍执行；失败的重置请求不会触发额外恢复。用户切换模块配置的应用独立于该请求。
- 重置操作保留完整原行为；原操作成功后恢复目标键。监听设置变化作为事件驱动的补充，无常驻轮询。
- 系统侧原子缓存保存开关，重启优先加载缓存，再读取模块配置；模块设置应用暂时不可用时保留已有系统快照。
- 应用配置时枚举已有用户，并监听用户切换。主动写入仅在模块自己的 worker 中清除/恢复 Binder 身份，先读后写，避免观察者循环。
- 默认关闭不拦截原生写入；从开启切到关闭会主动写 `0`，随后恢复原生控制。
- 写入后再次读取实际值，不能用 RPC 返回成功代替落值确认；原链执行期间若开关变化，会补施最新配置，避免旧请求覆盖新状态。

本版按 AOSP Android 13～16 的 8/9 参数布尔方法形状匹配，并从宿主反射读取操作常量。未匹配到实际 ROM 的方法时会记录安装数量为 `0`，不会把配置“已保存”当作 Hook 已生效。

安全服务仍可能打印 `Close screenshot write clipboard!`：样本的日志位于写入调用之后，没有核对最终值。启用保护后，这条日志不能证明功能已关闭，应检查实际设置值和截屏结果。

Lint 的 `SoonBlockedPrivateApi` 例外仅限定在 `install()`：它只从 Xposed 的 `onSystemServerStarting()` 执行。已核对 LSPosed/LSPosed 提交 `df74d83eb03a44cc6ad268841ac2ada28d077c77` 中 `OpenDexFileHooker.java`、`LSPosedContext.java` 和 `hook_bridge.cpp` 的模块加载器识别、`setTrusted`、`MakeDexFileTrusted` 调用；JingMatrix/Vector 提交 `91e77e439619bdee53c106ba9c82b9e08051c3e9` 有对应 `DexTrustHooker.kt` 路径。这里不是依靠普通应用反射权限，也不推断任意第三方框架都提供该机制。系统 Context 的静态保留经过独立调用链审查，在 lint 策略中仅放行本文件 1 项；没有 Activity Context 进入该字段。

## 实机验收

当前没有连接设备，以下是验收步骤，尚未执行：

1. 勾选系统作用域后重启，确认日志出现 provider bootstrap，以及 `screenshot clipboard mutation hooks installed=` 后的数量大于 `0`。
2. 在模块开启，读取当前用户的设置值应为 `1`；实际截屏后检查剪贴板。
3. 尝试将目标键写 `0` 或删除，重新读取仍应为 `1`；重启手机并让安全服务初始化后再检查。
4. 在模块关闭，读取应为 `0`；此后原生写入不受本功能拦截。
5. 使用多用户或工作资料时，分别检查相应用户的设置值与实际截屏行为。

```powershell
adb shell settings --user current get secure mi_screen_shots_write_clipboard_enable
```

本地单元测试与模拟器 Canvas/Compose 测试不能证明真实 LSPosed 回调、OEM 截屏组件或剪贴板最终行为。持久保护以模块启用、系统作用域已加载及实际 SettingsProvider 方法匹配为前提；最终构建与测试记录保存在版本归档。
