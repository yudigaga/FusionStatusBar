# FusionStatusBar 0.5.9

版本 `0.5.9`，Android 构建号 `223`。继续改进控制中心状态栏副本的融合图标布局，同时保留普通状态栏自身的尺寸设置。

## 更新内容

- 普通状态栏继续使用现有融合图标大小设置；控制中心真实和模拟状态栏副本根据各自宿主范围独立缩放，不再继承普通状态栏的图标缩放值。
- 控制中心副本按照自身电池图标容器测量并居中，避免状态栏双排高度或跨槽位偏移套用到较小的控制中心区域。
- 保留普通状态栏双排高度和偏移行为。

本地 `0.5.4` 至 `0.5.8` 的开发更新分别涉及系统日夜高光玻璃、控制中心转场时序、模拟状态栏网络图标、原生图标间距和控制中心电池容器布局；当前 `0.5.9` APK 以 [构建源码快照](../artifacts/releases/v0.5.9/source.zip)为准。

## 安装与验证范围

使用原正式证书签名，可覆盖更新 `0.5.3`。更新后重启系统界面或设备，再打开设置同步配置。从更早版本升级时，请按 [0.3.197 升级说明](release-notes-v0.3.197.md)核对 System 作用域。

- 本版使用 `:app:assembleRelease` 生成正式包；按当前构建记录，Debug/Release 单元测试和完整 lint 未运行。`lintVitalRelease` 随 Release 装配运行。
- 本次发布前重新运行 Python 工具回归测试，`28` 项通过。此结果不替代本版 Android 单测或完整 lint。
- APK 包名、版本 `0.5.9` / `223`、v3 签名、正式证书指纹和 `16 KiB` ZIP 对齐检查通过。
- 本版尚未进行设备安装、LSPosed Hook 注入、目标 ROM 渲染或运行性能验收。

## 产物与来源

- APK：`fusion-statusbar-v0.5.9.apk`，大小 `21,513,193` 字节。
- APK SHA-256：`301f02e81b1e6518a61d29c3ba097bf3753290b3a74df0461fb1e0961bb8b902`。
- 签名证书 SHA-256：`ccfaf42f8f14bb36973aeb971572fcc956a256196f68ff088340dfa7eca96fe2`，与此前正式版一致。
- 构建记录标记 `local_dirty_signed`、`sourceDirty=true` 和 `validationInput=existing_build_outputs`。构建时源码提交为 `c5246472fec49c514b6dea53d62340c3d1f3813e`，并应用归档的工作区补丁。
- 构建时 `570` 项文件已与 `source-files.json`、`source.zip` 逐项 SHA-256 核对。发布说明与链接在 APK 构建后补充；正式 APK 不作重新打包。
- `build-info.json` 为该构建的最终 APK 哈希、大小和签名记录；`manifest-badging-release.txt` 和 `signature-release.txt` 保存 APK 元数据和签名验证输出。
- 完整构建源码快照（SHA-256 `071cdd3ad6a0c0c9957fc82cc3c66a5871d687bcc6180a47a9a03d9abdaa011b`）及验证附件随 [正式版发布页](https://github.com/yudigaga/FusionStatusBar/releases/tag/v0.5.9)提供。
