# FusionStatusBar

面向 SystemUI 的 LibXposed 模块，提供融合图标、双排状态栏、时钟天气、遥测，以及控制中心布局与外观编辑。

## 开源项目

- 项目仓库：[yudigaga/FusionStatusBar](https://github.com/yudigaga/FusionStatusBar)
- UI 设计参考：[Miuix](https://github.com/compose-miuix-ui/miuix)，感谢开源项目提供设计灵感。

## 安装与迁移

- 从 0.3.150 起，模块包名为 `io.github.yudigaga.fusionstatusbar`，Android 会将其视为独立应用，旧包 `com.xtjm.fusionstatusbar` 的配置不会自动迁移。
- 如需保留配置，先在旧版设置中选择“导出配置备份”，安装新版后选择“导入配置备份”。确认新版配置后，再卸载旧版。
- 在 LSPosed 中关闭旧包的模块，只启用新版；作用域选择 `com.android.systemui`，设备存在 `miui.systemui.plugin` 时也将其勾选，然后重启系统界面。不要同时启用新旧两个模块。

## 构建

- JDK 17、Android SDK Platform 35 与 Build Tools 35 或更新版本。
- Gradle Wrapper 固定版本和分发 SHA-256；依赖首次构建需要网络。
- 设置 `JAVA_HOME`，在 `local.properties` 配置 `sdk.dir`，或设置 `ANDROID_HOME`。
- Android 最低 API 33。API 范围不代表所有厂商 ROM 均已验证兼容。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

本地 debug 构建检查与归档：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\release.ps1 -JavaHome $env:JAVA_HOME -AndroidSdk $env:ANDROID_HOME -GitPath git
```

脚本会核验测试、lint 检查兼容性、APK 签名、Manifest/Xposed/Hook 版本一致性，并把 APK、报告、源码指纹和 `release.json` 放在 `artifacts/releases/v<version>/`。该脚本产出 debug APK；对外发布需另行构建 release 变体并使用专用密钥签名。已有发布目录不会覆盖。脚本不安装 APK、不连接设备、不重启 SystemUI。

版本唯一来源为 `app/build.gradle` 的 `versionName/versionCode`。`BuildConfig` 和 Xposed `module.prop` 在构建时生成；模板位于 `app/src/main/xposed/module.properties`。

## 模块关系

设置页面负责输入与展示；编辑会话负责草稿、撤销与发布基线；配置存储负责版本迁移和完整快照；Provider 负责受限的跨进程传输；SystemUI 配置协调负责后台读取和修订号回执；Hook 注册与宿主适配负责能力探测；运行时网格负责挂载与释放。

依赖方向是页面/Hook 入口调用行为模块，行为模块复用布局、几何、材质和格式化策略。新增 OEM 差异应集中到宿主适配；新增编辑动作应通过会话事务执行。

## 配置与恢复

- 控制中心草稿独立于正式配置，保存草稿不会自动推送。
- 正式配置使用设备保护存储中的原子快照，旧 SharedPreferences 配置可以读取迁移。保存失败保留上一份正式配置与当前草稿。
- “已保存”、SystemUI 已读取、功能已应用分别记录。面板尚未显示时可以等待挂载；不能用读取成功替代最终应用成功。
- 预览截图按请求代次发布，旧回调不得覆盖新请求或恢复已清空的内容。
- 关闭自定义时恢复本模块实际修改的原生状态；不得覆盖宿主后续创建的新状态。
- `baseline-v0.3.129` 保存优化前源码。旧 APK 和验证报告存放在 `artifacts/baseline-v0.3.129/`。回退代码或 APK 前应保留应用数据；旧版本不认识新快照，不能把降级安装视为配置迁移。

## 验证范围

按用户要求不进行实体手机安装、LSPosed 注入检查或 HyperOS 实机验收。设置弹窗另有 API 36 模拟器窗口轨迹和帧统计，后者不能证明目标手机流畅度。Robolectric 的行为、生命周期和像素测试不能代替实机检查；以每版发布清单和验证记录为准。

测试覆盖编辑、布局、配置协议和异常路径。OEM 反射相关 lint 警告需逐项判断；新增错误和自定义 lint 兼容问题阻止发布。`tools/lint-policy.json` 按文件记录已审阅的高风险警告，新增项或数量增加会阻止发布。

## 证据与维护

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\index-evidence.ps1
```

该命令为 `xtjm` 顶层 APK、日志包、截图和分析文件生成大小、时间和 SHA-256 索引，不移动或删除原始资料。新日志建议按设备、日期与版本归档；导出完整系统日志前留意其中的个人数据。

- [优化计划](docs/optimization-plan-20261001.md)
- [本轮实施记录](docs/optimization-implementation-20261001.md)
- [设置弹窗修正与 Miuix 参考](docs/settings-sheet-v0.3.132.md)
- [冷启动旧设置页闪现修正](docs/cold-start-ui-v0.3.133.md)
- [启动协调器职责拆分](docs/startup-coordinator-v0.3.134.md)
- [状态栏 Hook 安装职责拆分](docs/status-bar-hook-installer-v0.3.135.md)
- [控制中心插件 Hook 安装职责拆分](docs/control-center-plugin-installer-v0.3.136.md)
- [通知中心 Hook 安装职责拆分](docs/notification-hook-installer-v0.3.137.md)
- [SystemUI 配置生命周期职责拆分](docs/runtime-config-lifecycle-v0.3.138.md)
- [状态栏刷新调度职责拆分](docs/status-bar-refresh-scheduler-v0.3.139.md)
- [Hook 安装重试协调](docs/bounded-hook-retry-v0.3.140.md)
- [图标资源来源与许可](third_party/material-icons/README.md)
