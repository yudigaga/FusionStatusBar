# 控制中心编辑器第三步：交互与浮层

日期：2026-09-30

## 实现边界

基于原生 Java View / ViewGroup，沿用第二步的透明 EditorItemHost 与 ControlCenterCardView 分离。此次不修改布局序列化、配置存储格式和 SystemUI 运行时。

- 点按仍进入属性面板；长按选择卡片，未锁定卡片缩放至 1.05，表面阴影提高。
- 长按前超过触摸阈值取消选择，保留父容器滚动机会。选中后可直接拖动。
- 拖动使用原抓取点的坐标增量；尺寸手柄仅在选中且未锁定时出现，不再常驻展开箭头。
- 新增独立 ControlCenterEditOverlay：移除、锁定/解锁、隐藏/显示、属性、完成；组合卡片另有排列切换和拆分。触控目标为 48dp，窄画布自动换行。
- 工具浮层优先停靠卡片上方，首行卡片改停靠下方；可覆盖相邻卡片，不参与布局占位。隐藏卡片的透明度不影响浮层。
- 位置变化使用 180ms 平移/缩放过渡，选中缩放使用 140ms 动画。稳定布局不创建无意义的过渡。

## 手势事务与数据安全

移动/缩放期间只有临时 View 状态；松手且位置合法、数值实际变化时，才提交一次草稿操作和一次撤销记录。CANCEL、多指触摸、外部布局刷新、模式切换、页面离开均取消临时手势。

编辑器预检边界与占位，Activity 再校验当前模式后走原有 applyControlGridPlan / updateControlCenterDraft。浮层操作不会保存配置或通知 SystemUI，仍须用户显式推送。

拒绝重叠落点并动画回退，不做自动挤开/重排，避免改变用户布局或产生无法推送的原始坐标。隐藏幽灵卡片在编辑器仍占位，和编辑器 resolver 保持一致。锁定项仍可长按选择以解锁，但不能拖动、缩放或删除。

## 文件

- `app/src/main/java/com/xtjm/fusionstatusbar/ControlCenterGridEditor.java`：手势状态、命中测试、过渡与壳层。
- `app/src/main/java/com/xtjm/fusionstatusbar/ControlCenterEditOverlay.java`：独立编辑工具浮层。
- `app/src/main/java/com/xtjm/fusionstatusbar/ControlCenterCardView.java`：选中态视觉动画。
- `app/src/main/java/com/xtjm/fusionstatusbar/MainActivity.java`：已验证草稿事务、浮层动作及生命周期清理。

## 验证

执行 `:app:testDebugUnitTest :app:lintDebug`。

本轮新增 22 项测试：16 项手势测试、5 项设置页草稿集成测试、1 项原生选中态渲染样图测试。覆盖取消、多指、抓取偏移、单次提交、尺寸预览、重叠回退、锁定、隐藏浮层、撤销、不保存配置及模式切换。

全量结果：191 项测试，失败/错误/跳过均为 0；其中控制中心相关 106 项。lint：0 errors、55 warnings。

原生图形测试生成 `app/build/reports/control-center-editor/step3-selected-pair.png`，已检查首行组合卡片和工具浮层。它是 Robolectric 原生绘制样图，不是真机截图，不能证明硬件阴影与动画帧率。

## 尚未完成的真实 WYSIWYG 能力

尚未接入真实壁纸、背景采样模糊、系统实时开关状态或 OEM 图标。当前背景与状态仍为示意；运行时渲染器未共享本次 View。未实现拖拽边缘自动滚动、自动挤开其他组件，也未进行 APK 打包发布、安装或真机触摸/性能验证。收到参考图后再做比例、浮层密度及材质精调。
