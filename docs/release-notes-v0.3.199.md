# FusionStatusBar 0.3.199

版本号 `0.3.199`，构建号 `213`。基于 0.3.198 统一融合图标中 5G 文字与 Wi-Fi 的可见尺寸。

## 网络图标调整

- 按文字实际绘制轮廓计算 5G 的可见宽高，与 Wi-Fi 图形使用同一尺寸基准；顶部、中部和底部位置以及不同粗细、独立缩放下均使用该基准。
- 根据网络图形实际边界为布局预留空间，改善网络类型切换时的尺寸与间距一致性。
- `E` 等短标签保留自然宽度，`4G+` 等长标签限制在同一网络显示框内。
- 网络标签或字体粗细变化时刷新文字轮廓缓存；颜色和透明度随当前状态栏更新。保留已有位置、缩放、Wi-Fi 强度和双卡信号设置。

## 安装与验证范围

使用原正式证书签名，可覆盖更新 0.3.198。更新后重启系统界面或设备，再打开设置同步配置。从更早版本升级时，仍按 [0.3.197 升级说明](release-notes-v0.3.197.md) 核对 System 作用域。

- 本地 Debug、Release 各通过 **31 项相关测试**，涵盖内容配置、图标几何与位置、绘制状态及原生像素对比；其中新增 4 项网络尺寸与缓存刷新测试。这些记录并非本地完整测试集。
- 本次发布前重新运行构建校验器及 28 项工具回归测试，全部通过。两套 lint 记录均为 0 错误、99 条警告，高风险策略通过。
- Manifest、BuildConfig、Xposed 版本及作用域一致；正式 APK 为非调试构建，原正式证书签名和 16 KiB ZIP 对齐检查通过。
- 本版尚未进行设备安装、Hook 注入、目标 ROM 功能或实机性能验收。远端完整 CI 以对应发布提交的 GitHub Actions 为准。

## 产物与来源

- APK：`fusion-statusbar-v0.3.199.apk`，大小 `21,496,809` 字节。
- APK SHA-256：`59d81b0161c5322e7c7c14822e475e574bed20ce95d6ed2c634c7d6e56d46df0`。
- 签名证书 SHA-256：`ccfaf42f8f14bb36973aeb971572fcc956a256196f68ff088340dfa7eca96fe2`，与 0.3.198 一致。
- 发布已有的正式签名构建，保留原始 `local_dirty_signed`、`sourceDirty=true`、`validationInput=existing_build_outputs` 来源记录。构建时 527 项文件的索引、源码快照和发布前工作区已逐项核对，哈希全部一致。
- 构建后仅追加发布文档、Git 属性和归档记录；APK 保留原样，构建来源仍为 0.3.198 提交加归档补丁和新增测试。
- `verified-build.json` 记录签名前的构建输出；最终签名 APK 的哈希、大小及证书以 `release.json` 为准。
- [原始构建记录](../artifacts/release-v0.3.199.json)、[文件索引](../artifacts/source-files-v0.3.199.json)、[构建补丁](../artifacts/tracked-v0.3.199.patch)和[验证摘要](../artifacts/validation-v0.3.199.md)随源码保存。
- 原始 `source.zip` 源码快照（SHA-256 `eb574016cd07da45b0f4214f1873d87fba9bc49873f8f735850ee8b91523fe5e`）及完整验证记录作为 [本次 Release 附件](https://github.com/yudigaga/FusionStatusBar/releases/tag/v0.3.199)提供。
