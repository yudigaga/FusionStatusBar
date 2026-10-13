# FusionStatusBar 0.3.197

版本号 `0.3.197`，构建号 `211`。本次涵盖上一个远端正式版 `0.3.193` 到当前版本的全部变化（`0.3.194-0.3.197`）。

## 新增与外观调整

- 新增“杂项”页面，底部导航包含桌面、状态栏、通知栏、控制中心、杂项和设置六个入口。
- 杂项提供“移除链式启动弹窗”开关，默认关闭。开启后放行 MIUI/HyperOS 的应用间启动确认入口，关闭后保留原生确认行为。
- 新增“毛玻璃悬浮底栏”，默认开启，使用当前页面背景模糊、圆角描边和蓝色选中图标；在“设置 → 界面”中关闭可恢复常规底栏，选择会保存。
- 页面按实际导航高度保留底部空间，调整安全区域与文字布局，适配六个入口、窄屏和大字体。背景模糊保持前景图标与文字清晰，背景捕获不可用时使用回退显示。

## 链式启动与作用域修正

- 0.3.194 引入的安全中心弹窗按钮方案已在 0.3.195 移除；最终方案在 system_server 中处理 MIUI 的应用间启动确认入口，不点击“一次允许”或“永久允许”按钮。
- 开启开关时直接放行 `SecurityManagerService$LocalService.checkAllowStartActivity` 的受支持布尔返回值入口；关闭开关时执行原方法并保留其结果。未知类或不兼容的方法保持原生行为。
- 杂项配置由设置应用保存，经认证 Provider、显式配置广播和系统端持久缓存同步；启动阶段在后台读取并有界重试，实际启动拦截只读内存配置，不在每次启动时发起 Binder 查询。
- 打开设置或修改开关会发布当前值；杂项配置独立于已有状态栏/控制中心备份格式，修改该设置时需要保持设置应用包可用。
- 0.3.196 修正遗漏的 LSPosed `system / system_server` 作用域；0.3.197 移除不需要的 `android` 作用域。配置广播仍以 Android 系统包名 `android` 为目标，该包名与 LSPosed 的 `system` 作用域是不同用途。
- 构建验证检查 APK 内作用域与源码完全一致，并要求包含 `system`。

## 升级步骤

1. 安装原签名正式 APK，可覆盖 0.3.193。
2. 在 LSPosed 的 FusionStatusBar 作用域中明确勾选 **System（system / system_server）**，保留 `com.android.systemui`、`miui.systemui.plugin`、`com.miui.home` 和 `com.miui.securitycenter`。旧模块可能不会自动添加新推荐作用域。
3. 若中间版本留下 `android` 选项，可取消该选项。
4. 重启手机，再打开 FusionStatusBar 一次同步配置；需要该功能时在“杂项”开启“移除链式启动弹窗”。已有开关值会保留。
5. 重新触发原来的应用间启动，确认目标应用能够打开。安装、Hook 注入与功能生效需要分别核对。

## 验证与产物

- 归档 Debug、Release 各 1116 项测试通过，0 失败、0 错误、0 跳过；两套 lint 均为 0 错误、97 条警告，高风险策略通过。
- 原签名 Release APK，`debuggable=false`，大小 `21,496,809` 字节。现场核对 Manifest/Xposed 版本、五个作用域、原证书及 ZIP 对齐通过。
- APK SHA-256：`119122b8e9d3c7db4a5d9354fabdada5170963777be51f2fb95efbcc33a5b7f9`
- 签名证书 SHA-256：`ccfaf42f8f14bb36973aeb971572fcc956a256196f68ff088340dfa7eca96fe2`
- 发布现有签名归档，保留 `local_dirty_signed`、`sourceDirty=true`、`validationInput=existing_build_outputs` 的原始来源记录，以及完整源码快照、文件哈希与补丁。
- 本版未进行设备安装、system_server/SystemUI 注入、目标 ROM 功能或实机功耗验收。
