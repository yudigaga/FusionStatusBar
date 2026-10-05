# 控制中心渲染修复 v0.3.161

本版针对 2026-10-04 提交的三张截图和 LSPosed 日志修复材质、描边及布局刷新问题。构建号为 175。本地测试不等于目标 HyperOS 设备验收；本次没有安装到实体手机，也没有重启其 SystemUI。

## 日志核对

输入：`LSPosed_20261004_172943.zip`，SHA-256：
`d3c3192c1816bf555e46ec9abe316c1f3b2de384b1dc5c0f2928affd9b7cf372`。

ZIP 内 `log/modules_2026-10-04T17:24:42.380315.log` 提供以下证据：

- 第 63 行及之后四次 SystemUI 启动均为 `0.3.160 / 174`。
- 第 67、135 行确认原生材质 API `5/5`，插件加载后安装 38 个 Hook，`layoutAvailable=true`。
- 第 611 行记录 17:26:34 启用 15 项布局；第 885–893 行记录 17:26:57 成功挂载，包含 17 个绑定。
- 第 941 行记录 17:27:57 清空布局。异常覆盖自定义网格和原生布局两条路径。
- 没有发现与本次画面对应的本模块 Java fatal。归档内 ANR、watchdog 和 tombstone 有历史记录，不能直接归因于这些截图。

日志未记录完整材质、圆角、描边配置，因此不把截图与某一组未取证的参数强行对应。

## 修复与回归

| 证据 / 用例 | 原因 | 修改路径 |
| --- | --- | --- |
| `ControlCenterNativeMaterialBoundaryTest`：文字和卡片图标颜色被转成背景颜色 | 原生 blur API 同时用于前景混色，原先仅按父容器判断材质归属 | `ControlCenterMaterials` 保留 TextView、QSCardItemIconView 的原生混色，不为它们创建独立模糊底板 |
| 同组测试：系统请求关闭模糊或清空混色后仍被重新着色 | 全局材质覆盖了原生关闭动作 | 原生 mode 0 和空混色列表保持关闭，恢复本模块创建的底板 |
| `ControlCenterGlobalBorderTest`、`ControlCenterSoftGlassTest` 的像素回归 | 未知形状、外层容器边界被当成实际表面边界 | 共用 `ControlCenterSurfaceShape` 解析真实圆角、椭圆、包装 Drawable 和原生路径；无法确认形状时不画猜测边框 |
| 原生小按钮内部圆形描边用例 | 描边装到带 padding 的 tile 容器上 | 原生小按钮使用内部 ImageView，滑条使用 `progress_bg` 轨道，保持外层布局格透明 |
| `ControlCenterTileSurfaceRegressionTest`：原生背景尺寸和动画回调 | 替换 Drawable 丢失 intrinsic size，安装新包装时还会断开底层回调 | 保留原生 Drawable 尺寸、边界和动画转发 |
| 同组测试：只有 glyph 图层的原生模糊按钮 | 被误判成未处理，回退到给整个按钮容器补白底 | 活跃底色归内部 ImageView 管理，图标原始 LayerDrawable 保持完整，关闭后恢复原背景 |
| `ControlCenterRuntimeGridRefreshTest`：切换材质后再次绘制 | 网格仍使用挂载时缓存的旧设置 | 刷新装饰时同步网格的材质设置 |
| 同组测试：双按钮共用底板像素 | 共用底板存在裁剪对象，但布局时没有填入路径 | 为共用底板生成完整的组合形状裁剪路径 |

以上新增失败用例均在相应修复前运行过。图层尺寸、原生关闭动作、描边边界及共享底板均有明确失败断言，不以“无异常”替代画面验证。

## 复验与交付

项目根目录执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

使用 JDK 17 和本机 Android SDK。发布脚本另行核验 APK 签名、Manifest、BuildConfig 和 Xposed 元数据版本一致性。准确测试数量、lint 结果及 APK SHA-256 以 `artifacts/releases/v0.3.161/release.json` 为准。

目标设备仍需验收三组画面：原生空布局、自定义普通网格、自定义含分组网格。重点检查 WLAN/移动网络文字底色、圆形开关外缘、亮度/音量滑条边界，以及切换材质后再次下拉控制中心是否保留新设置。
