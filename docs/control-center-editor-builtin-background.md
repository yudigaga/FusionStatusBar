# 控制中心内置背景

版本：0.3.120 / 134，日期：2026-09-30。

## 实现

- 壁纸画布改用随 APK 分发的黑色柔化纹理，不读取系统壁纸、不请求壁纸服务、不联网。
- 384 x 832 RGB PNG 放在 `drawable-nodpi`，约 117 KiB；ARGB 解码约 1.22 MiB，不随设备密度放大源图。
- 灰阶纹理由 `tools/generate_canvas_background.py` 使用 Pillow 离线生成并模糊，APK 构建和运行不依赖 Python。
- Android BitmapDrawable 使用过滤缩放铺满工作区。没有逐帧模糊、动画、壁纸位图读取或异步背景加载。
- 工作区背景完全不透明，窗口底色为黑色。不再调用 `setFormat`，工作区打开期间清除 `FLAG_SHOW_WALLPAPER`；若宿主原有此标志，退出时恢复。
- 暂时采用深色系统栏和白色图标，退出后恢复原有栏色及可见性标志。移除旧的系统壁纸请求说明。
- 同一个编辑器仍从设置页移入工作区，退出后还原父容器、索引、布局参数、设置页面和窗口背景。

草稿、长按、拖动、缩放、撤销、推送与 SystemUI 布局协议未改动。此内置背景只影响应用内画布，不替换手机壁纸或 SystemUI 背景；不是新增逐卡片实时背景模糊。

## 版本标识

Gradle 的 APK 版本、FusionModule 日志常量和 Xposed module.prop 同步为 0.3.120 / 134，修正 v0.3.119 只更新 APK 版本导致的诊断歧义。

## 验证

先更新新行为断言，旧实现的四项断言失败：仍请求系统壁纸、未暂停既有壁纸标志、切换透明窗口、没有内置不透明位图。实现更新后通过。

执行 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`。增加窗口栏色恢复、显式透明宿主兼容、资源体积与不透明度检查；保留设置页集成测试中的返回、暂停、状态保存、撤销与不自动保存草稿断言。

最终构建成功：208 项测试，失败、错误、跳过均为 0；Lint 为 0 errors、55 warnings。APK 为 `app/build/outputs/apk/debug/fusion-statusbar-v0.3.120-debug.apk`，1,378,886 字节。已核对打包的背景资源和 module.prop 版本与 APK 元数据一致。

Robolectric 原生图形测试生成 320 x 640、412 x 892、800 x 360 截图，并检查背景边缘像素完全不透明、存在低亮度纹理，标题、滚动区、操作栏互不重叠。截图位于 `app/build/reports/control-center-wallpaper/`。

这些是本地逻辑和渲染检查，不是真机帧率验证；尚未在用户的 HyperOS 3 / Android 16 手机上安装和复测。
