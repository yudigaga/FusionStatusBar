# 控制中心编辑器第四步：系统壁纸画布入口

日期：2026-09-30

后续变更：v0.3.120 已按用户要求改为内置黑色预模糊背景，不再请求系统壁纸。
以下保留第四步初版记录；当前行为见 `control-center-editor-builtin-background.md`。

## 范围

新增“壁纸画布”按钮与 ControlCenterWallpaperWorkspace。在同一 Activity 内临时移走编辑器，隐藏设置页根视图、清除不透明窗口背景，以 FLAG_SHOW_WALLPAPER 请求系统提供壁纸背景。画布叠加约 14% 黑色遮罩，卡片仍使用此前的半透明材质。

这是壁纸显示请求，不是壁纸图像读取器。未新增存储/壁纸读取权限，没有壁纸位图缓存、截图或文件写入；未接入背景采样模糊，也没有用前景 blur 冒充 backdrop blur。设备是否兑现壁纸请求尚未真机确认。

## View 层生命周期

- 复用同一个 ControlCenterGridEditor，不复制配置，不建立第二份草稿。
- 标准/紧凑模式沿用进入时的当前模式；全屏画布不新增模式切换和推送入口。
- 全屏保留属性、浮层编辑、尺寸与拖动交互、撤销；返回设置后再执行原有推送。
- 进入前记录父容器、子索引、LayoutParams、页面可见性、窗口背景与壁纸标志。
- 返回按钮、系统返回、onPause、onSaveInstanceState 均关闭工作区，取消临时选中/拖动并恢复原层级。
- 显式注册 Android 返回回调；重复打开/关闭无副作用。已启用该 Activity 的 OnBackInvokedCallback。
- 根据系统栏与刘海 insets 保护顶部标题和底部按钮，重复分发不叠加 padding。

## 改动文件

- `app/src/main/java/com/xtjm/fusionstatusbar/ControlCenterWallpaperWorkspace.java`
- `app/src/main/java/com/xtjm/fusionstatusbar/MainActivity.java`
- `app/src/main/AndroidManifest.xml`
- `app/src/test/java/com/xtjm/fusionstatusbar/ControlCenterWallpaperWorkspaceTest.java`
- `app/src/test/java/com/xtjm/fusionstatusbar/ControlCenterSettingsUiFlowTest.java`

布局序列化、草稿模型、配置存储、推送链路及 SystemUI 运行时未改动。

## 验证

执行 `:app:testDebugUnitTest :app:lintDebug`，BUILD SUCCESSFUL。

全量 200 项测试，失败/错误/跳过均为 0；控制中心相关 115 项。lint 0 errors、55 warnings。

新增 9 项测试：5 项工作区测试覆盖同一编辑器复用、窗口标志、父容器/背景恢复、幂等和 insets；4 项设置页集成测试覆盖返回、浮层删除与撤销、暂停及保存页面状态。进入/退出不产生草稿撤销记录，也不保存控制中心配置。

## 未验证与后续

未构建发布 APK、未安装、未操作设备。Robolectric 不具备真实系统壁纸合成环境；测试通过不能证明壁纸实际透出、动态壁纸表现、真机返回手势或帧率。若 OEM 不支持当前窗口请求，可能显示系统提供的默认背景；目前没有可靠的显示成功检测，不能宣称已完成自适应降级。

逐卡片毛玻璃、实时系统状态、OEM 图标、运行时 Renderer 对齐仍未实现。下一步应结合用户的当前/目标参考图和设备实测，确认壁纸路径后再选定背景模糊方案。
