# FusionStatusBar 0.5.20

版本 `0.5.20`，构建号 `234`。发布已有的正式签名 Release，沿用长期证书，可覆盖更新 `0.5.15`；本页汇总其后的返回停顿、外观及自动主题更新。

## 累计更新

- 桌面手势新增“返回停顿”动作配置；侧滑返回提供系统默认及自定义外观，包括渐变彩虹，支持背景色、箭头色、透明度和同色系随系统日夜切换，并提供示意预览。
- 修正等待取消和就绪提示：第一段沿用系统拉起反馈，第二段在等待达标时提示可以松手执行，执行动作时不再补震；连续快速侧滑保留原生返回规则。
- 修复玻璃自动日夜切换时的资源同步，按实际消费 View 的资源组处理窗口、插件配置变化和挂载迁移，避免同一实例继续使用旧主题着色器。

## 返回停顿阈值

桌面 → 桌面手势 → 返回停顿，新增两项设置：

- **距离阈值**：默认跟随系统，可改为 `16～240 dp`。使用 dp 换算，适配屏幕密度。
- **等待时间**：可设 `200～3000 毫秒`，默认 `500 毫秒`。

输入数字后，子页“完成”返回手势编辑页，最后点手势编辑页“完成”保存；“取消”放弃相应修改。支持恢复默认，空值、非数字及越界输入会提示，不会保存。

自定义距离同时用于进入等待和原生箭头的拉起反馈；第二段震动在等待达标时提示已就绪，随后松手执行。提前松手取消等待，明显回撤取消手势；连续快速侧滑保留原生返回规则。保持渐变彩虹及同色系自动日夜切换。

## 验证和使用

Debug/Release 各 26 类、242 项定向测试通过，失败、错误、跳过均为 0；Python 构建工具回归 28 项通过。范围包括阈值判定、配置传递、编辑保存、两段反馈、误返回及颜色/主题回归。

两个变体的 APK 装配和 lint 通过；Debug 0 错误、99 警告，Release 0 错误、98 警告，高风险规则通过。

详细范围见 [阈值配置说明](2026-10-26_back-hold-configurable-thresholds.md)和[自动主题修复记录](2026-10-26_glass-theme-switch-bugfix.md)。这些开发记录中的本地发布状态是生成当时的历史记录，本次正式发布及最终产物以本页和[原始构建记录](../artifacts/release-v0.5.20.json)为准。

安装后重启系统桌面和系统界面，或重启设备；确认 `com.miui.home`、`com.android.systemui` 等相应 LSPosed 作用域已启用。从更早版本升级时，仍按 [0.3.197 升级说明](release-notes-v0.3.197.md)核对 System 作用域。完整测试套件及手机安装、真实 LSPosed 回调、马达手感本轮未验证；远端完整 CI 以发布提交对应的 GitHub Actions 为准。

## 产物与来源

- APK：`fusion-statusbar-v0.5.20.apk`，大小 `21,652,726` 字节。
- APK SHA-256：`f58333f44372685aabc65bb92f2eb39df1b50581cde069a8d29d4dde0c182e89`。
- 签名证书 SHA-256：`ccfaf42f8f14bb36973aeb971572fcc956a256196f68ff088340dfa7eca96fe2`。正式 v3 签名和 `16 KiB` ZIP 对齐检查通过，APK 为非调试构建。
- 原始记录保留 `buildMode=local_dirty_signed`、`sourceDirty=true`、`validationInput=existing_build_outputs` 及当时的 `distribution=local_only`。构建基线为 `c2ad317ac55af574d938e39a6fa53dc416e920c8` 加工作区改动；本次不重打包 APK，也不声称从最终发布提交重新构建。
- 构建时工作区、文件索引和源码 ZIP 的 `655` 项文件已逐项核对 SHA-256。发布文档、链接和归档摘要在构建后补充。
- 新增 MaterialKolor 4.1.1 依赖、锁文件以及 [NOTICE](../third_party/materialkolor/NOTICE)、MIT 和 Apache 2.0 许可文本一并保存；APK 内三份许可文件与源码一致。
- [构建记录](../artifacts/release-v0.5.20.json)、[文件索引](../artifacts/source-files-v0.5.20.json)、[构建补丁](../artifacts/tracked-v0.5.20.patch)和[验证摘要](../artifacts/validation-v0.5.20.md)随源码提供。`verified-build.json` 记录签名前产物，最终签名 APK 以原始 `release.json` 为准。
- 原始 `source.zip`（SHA-256 `74368f61b281ae896d98a9a3304702ebbc6be00fdda25a66bb410b70d719560e`）及两套定向测试、lint 和独立签名核验附件随 [正式发布页](https://github.com/yudigaga/FusionStatusBar/releases/tag/v0.5.20)提供。
