# 透明装饰层与硬件轮廓 v0.3.168

## 最新证据

用户提供 `LSPosed_20261004_224356.zip` 和自定义/原生布局对照图。自定义布局的 5G 有覆盖整格子的方形层，圆形磁贴有多边形内层；原生布局没有。22:34、22:38 的 SystemUI 均为 0.3.167 (181)。22:42:40 的 5G、自动亮度、护眼和夜间磁贴均为 `nativeBgBlur=0/0`。因此上一版关闭背景模糊已生效，但没有解决遮罩，不能继续将其作为单一根因。

## 新发现与修改

此前 `tile surface layers` 只检查 OEM 磁贴子树，漏掉了网格单独创建的 `Decoration.view` 兄弟节点。该节点的大小为全槽位（5G 为 275×313）或圆形图标表面（249×249），位于磁贴内容上方。背景透明，但自定义 Outline 没有设置透明度，`dispatchDraw()` 又将其 Z 设置为最高磁贴 Z 加一个密度单位。

Android `View.rebuildOutline()` 在调用 provider 前将 Outline alpha 初始化为 1；`Outline.setAlpha()` 的契约说明，alpha=1 表示轮廓内完全不透明，渲染器可以省略下方内容。正 Z 还启用硬件阴影。透明描边节点违反这一契约。工作区中 OEM `QSTileItemIconView` 的轮廓明确设置 alpha=0。

本版的形状 Outline 显式设置 alpha=0，仅描述裁剪形状。装饰节点保持 Z=0，先由正常 ViewGroup 绘制磁贴，再单独绘制可见的装饰节点，仍覆盖原生内容但不再生成抬高的阴影平面。保留原生磁贴开关颜色、图标动画和既有形状尺寸。

日志增加 `tile decoration layers`，包括此前遗漏的装饰/外层底板、Z、Outline alpha 和填色。内部图层诊断等到图标 Drawable 非空才记录，避免只捕获重启后的空图标。

## 验证与边界

两条回归先在旧代码失败：装饰轮廓 alpha 为 1，磁贴 Z=20 时装饰 Z=21。修复后通过，描边位置、缩放跟随和图标颜色测试也保持通过。

另通过 `HardwareRenderer.createHardwareBitmap(RenderNode, ...)` 检查实际记录的像素，覆盖圆形、矩形、胶囊的装饰开关差分。Robolectric 的硬件位图实现把光照强度设为 0，且不是手机 HyperOS/Adreno 渲染器，因此它能验证基础 RenderNode 合成和图标不被覆盖，不能证明设备阴影路径已消除截图中的全部遮罩。未安装或操作实体手机。

完整测试、lint、签名与版本检查见 `artifacts/releases/v0.3.168/release.json`。目标 ROM 上的最终效果仍需实机确认；若仍复现，应对照新增装饰层日志，不再只排查原生背景模糊。
