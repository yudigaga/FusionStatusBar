# 通知可读性照片 fixture

此资源供 `NotificationReadabilityVisualTest` 的本地 Canvas 矩阵使用。测试源码已编写；本文不表示测试已执行，也不表示目标 ROM 已验收。

## 照片来源

| 项目 | 固定值 |
| --- | --- |
| 文件 | `high-frequency-photo.jpg` |
| 标题 | Managed forest landscape |
| 来源页 | <https://www.usgs.gov/media/images/managed-forest-landscape> |
| 原始文件 | <https://d9-wret.s3.us-west-2.amazonaws.com/assets/palladium/production/s3fs-public/thumbnails/image/managed%20landscape%201.jpeg> |
| 署名 | Ed Arnett, Cooperative Forest Ecosystem Research Program |
| 许可 | USGS 媒体详情标记 Public Domain |
| 取得日期 | 2026-11-17 |
| SHA-256 | `47462f6e61177317dccfa9561280fe179544a1a3d0c3434cc517a822db547700` |
| 大小 | 768091 bytes |
| 处理 | 原始下载字节，无转换；绘制时缩放到场景范围 |

结构化来源记录见 [photo-source.json](photo-source.json)。照片包含真实森林纹理，不以合成棋盘替代。测试在资源缺失、哈希不符或解码失败时直接失败。

## 运行与阅读报告

由统一验证执行者在项目 `work` 根目录或 `work/app` 中运行 Release 单元测试；当前编写代理未运行以下命令：

```powershell
.\gradlew.bat :app:testReleaseUnitTest --tests com.xtjm.fusionstatusbar.NotificationReadabilityVisualTest --rerun-tasks
```

命令示例以 `work` 根目录为工作目录。测试读取 `docs/2026-11-16-notification-visual-matrix.csv` 的 48 个唯一组合，使用 SDK 34、mdpi、NATIVE Canvas，调用既有 SystemUI host stub 和真实 `NotificationSurfaceHooks.applyRow`。

执行后输出到 `app/build/reports/notification-readability/`：

- `contact-sheet.png`：6 列 × 8 行，按 CSV 顺序；每格注明场景标签、Canvas/OEM 边界及正文/大字对比度。
- `contrast-report.json`：48 格实测值、正文/大字文字区域、输入哈希，以及应用低对比负例和未知宿主背景的附加记录。正文 14sp 和动作文字要求至少 4.5:1，大字 24sp 要求至少 3:1。

对比度从隐藏全部内容后的真实背景绘制结果计算，再单独绘制文字供 contact sheet 阅读，避免字缘抗锯齿使对比度虚低。已知宿主 fixture 为浅色 `#EEF2F6`/`#202124`、深色 `#353840`/`#F1F3F4`；不透明宿主卡片在四种 wallpaper 上均保持原生基线。这证明已知 fixture 的回退行为，不代表任意半透明或应用自定义通知。

应用自带低对比 `ColorStateList` 仅记录为 `expected_low_contrast`，保留原值。无已知背景契约的宿主自绘记录 `not_evaluated`，不计算或硬判对比度。测试同时断言文字颜色对象、字号、图标/色调、动作回调和原生 drawable alpha 保持，并在每个 fixture 结束时同时关闭通知与 heads-up 样式恢复原值。

CSV 的 `effective`/`ineffective` 只作场景输入标签，不设置公共 blur availability，也不伪造 OEM 能力。所有 JSON 格均保留 `oemAvailability=unknown`、`oemBlurVerified=false`、`targetRomStatus=pending`；这些标签下均绘制真实 native fallback。真机 48 格、RemoteViews 膨胀、LSPosed 回调、OEM compositor、ROM/ART 与 60/120Hz Perfetto 验收仍需独立执行。
