# FusionStatusBar 0.5.0

版本号 `0.5.0`，构建号 `214`。包含原 `0.3.200` 开发构建的网络位置微调和锁屏状态栏高度修复，按新版本规则重新构建并使用原正式证书签名。

## 版本进位规则

版本号为 `主版本.中间位.末位`：末位满 `100` 向中间位进 `1`，中间位满 `10` 向主版本进 `1`。例如 `0.5.99 → 0.6.0`、`0.9.99 → 1.0.0`。原开发编号 `0.3.200` 规范为 `0.5.0`；Android `versionCode` 独立递增，本次为 `214`。

## 功能调整

- 在融合图标设置中新增 Wi-Fi 与 5G/移动网络文字独立上下微调，范围为融合图标高度的 `-20%～20%`，负值上移、正值下移，默认均为 `0`。
- 微调支持预览、保存、跨进程配置同步和备份恢复；旧备份缺少新字段时使用默认值，继续保留原有位置、大小和其他配置。
- 修复锁屏单排或跨行融合图标被固定高度容器压缩、裁剪的问题，使锁屏状态栏随总高度设置动态更新；覆盖挂载、测量、布局和配置刷新过程。
- 锁屏 Hook 校验接收对象类型，避免通过继承方法影响普通布局，并保留其他兄弟视图的布局。

## 安装与验证范围

使用原正式证书签名，可覆盖更新 `0.3.199`。更新后重启系统界面或设备，再打开设置同步配置。从更早版本升级时，仍按 [0.3.197 升级说明](release-notes-v0.3.197.md) 核对 System 作用域。

- 本次从已提交的干净源码运行完整本地测试：Debug `1169` 项、Release `1169` 项全部通过，均为 `0` 失败、错误和跳过。
- 工具回归测试 `28` 项通过；Debug/Release lint 均为 `0` 错误，分别为 `99` / `99` 条警告，高风险策略通过。
- 两种构建的 Manifest、BuildConfig、Xposed 版本及作用域校验通过。正式 APK 为非调试构建，正式签名及 `16 KiB` ZIP 对齐检查通过。
- 本版尚未进行设备安装、Hook 注入、目标 ROM 功能或实机性能验收。远端完整 CI 以发布提交对应的 GitHub Actions 为准。

## 产物与来源

- APK：`fusion-statusbar-v0.5.0.apk`，大小 `21,513,193` 字节。
- APK SHA-256：`fdca7110c8c86ebfc3fcd64ce6d91496240d2cd47a70220ffb87a707ec773279`。
- 签名证书 SHA-256：`ccfaf42f8f14bb36973aeb971572fcc956a256196f68ff088340dfa7eca96fe2`，沿用原正式证书。
- 实际构建源码提交：`a1e00e2a71d77a3976f2f6740e8e0cc80a474a89`；原始构建记录为 `official_signed`、`sourceDirty=false`、`validationInput=built_in_this_run`。
- 构建源码快照的 `540` 项文件已逐项核对。发布提交随后仅追加当前版本说明、链接和归档证据，应用代码与实际构建提交一致。
- `verified-build.json` 保存签名前的构建输出；最终正式签名 APK 的哈希、大小和证书以 `release.json` 为准。
- [原始构建记录](../artifacts/release-v0.5.0.json)、[文件索引](../artifacts/source-files-v0.5.0.json)、[构建补丁](../artifacts/tracked-v0.5.0.patch)和[验证摘要](../artifacts/validation-v0.5.0.md)随源码保存。
- 原始 `source.zip` 源码快照（SHA-256 `d920a38c299bdcdf45bc558f2ebfe9bfa24c6de4cee3709bb5704ecd8281ee19`）及完整验证记录作为 [本次 Release 附件](https://github.com/yudigaga/FusionStatusBar/releases/tag/v0.5.0)提供。
