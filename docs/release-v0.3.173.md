# FusionStatusBar v0.3.173

控制中心外观统一与系统菜单扩展。本版汇总 0.3.152-0.3.173 的布局、材质、绘制修复和系统菜单更新。

公开版包名为 `io.github.yudigaga.fusionstatusbar`。从旧包 `com.xtjm.fusionstatusbar` 迁移时，先导出配置，再在新版导入，并在 LSPosed 中只启用一个版本；具体步骤见 [安装与迁移](../README.md#安装与迁移)。

## 控制中心布局

- 横屏继续使用已发布的自定义画布，支持跨面板分组、组合卡片和独立组件位置。
- 分组和组合保持成员顺序、标签、尺寸与外观；横屏仅压缩分面板后产生的全空行，不改写竖屏坐标。
- 横屏截图导入保留完整画布，恢复竖屏后继续使用原布局。
- 分组成员支持独立调整图标大小，范围 25%-200%，保留底板、文字空间和点击区域。

## 材质与描边

- 新增全局容器背景选择：原有、透明、磨砂、深色、水晶玻璃、浅色、石墨、冰蓝、薄荷和柔光玻璃。
- 全局材质统一应用于磁贴、网络卡片、音乐、融合中心、滑条轨道和分组，兼顾横竖屏。
- 全局描边提供独立开关和 1-4 dp 宽度，沿实际容器轮廓绘制。
- 背景、卡片与磁贴模糊强度保持独立。透明材质暂停容器模糊，保留设置值；柔光玻璃的底色不随模糊强度变淡。
- 开启的磁贴和二态卡片统一使用浅色底，未开启的使用当前容器底色；透明材质保持透明，原有背景保留原生行为。
- 音乐与融合中心使用统一的未开启状态色、卡片透明度及选定形状，保留内部图标、封面、按钮和设备列表的颜色。

## 绘制与恢复修复

- 修复圆形磁贴外圈与实际底板不同心、底部裁切，以及平移和按压缩放后的描边偏移。
- 非圆形磁贴由统一底板绘制形状与状态色，处理原生内层底板造成的双层外观。
- 软件底板接管时协调原生混色、背景模糊、高光和阴影，处理后续动画或主题更新再次覆盖自定义外观的问题。
- 透明装饰层显式使用透明轮廓，并调整绘制顺序，修复硬件轮廓与抬高的装饰层产生的磁贴遮罩。
- 音乐与融合中心复用单底板绘制路径，避免原生背景与配置形状叠加；背景重建后重新应用当前设置。
- 全局描边先展开为实际空心路径，再绘制边缘，避免依赖渲染器保留描边语义。
- 保留文字、图标和滑条进度的原生颜色；统一滑条轨道、描边和内层裁剪形状。
- 修复首次绑定、延迟图标更新、导入布局、主题刷新和 SystemUI 重启后的材质恢复。
- 关闭自定义或进入原生编辑时，恢复背景、轮廓及系统最后请求的原生材质参数。
- APK 内重启系统界面会等待已提交配置写入；保存失败时取消重启，未推送的控制中心草稿继续保持草稿状态。

## 系统菜单与通知

- 电源菜单、音量菜单、通知中心通知和悬浮通知可分别启用外观接管，复用控制中心的材质、描边、模糊和内容缩放设置。
- 音量条支持标准与短款；短款仅应用于未展开的音量弹窗。
- 三条电源菜单在中央原生关机/重启滑条旁加入两条操作滑条：左侧为线刷模式和卡刷模式，右侧为飞行模式和静音模式。
- 线刷模式重启至 bootloader，卡刷模式重启至 recovery；飞行模式和静音模式切换当前状态。
- 侧边操作支持拖动阈值、触感反馈、取消手势和无障碍动作；设备锁与关机密码限制继续生效。
- 侧边滑条跟随原菜单的布局、可见性和动画，关闭扩展时恢复原生布局。

## 配置与兼容性

- 布局格式 6 保存分组成员图标大小，旧格式 1-5 可读取，旧成员默认为 100%。
- 新增材质、全局描边和系统菜单设置随草稿、撤销、发布、配置备份及预设保存；旧完整备份采用兼容默认值。
- 旧分组背景和描边字段继续可读，显示效果由全局外观设置统一管理。
- 最低 Android API 33；具体系统菜单和原生材质能力取决于 ROM 的实现。

## 验证范围

正式 release 构建的 debug 与 release 变体各通过 863 项单元测试，0 失败、0 错误、0 跳过；两套 lint 均为 0 错误、65 条警告，高风险检查门禁通过。签名、版本一致性和 APK SHA-256 以本次正式发布记录为准。

本次不进行实体手机安装、LSPosed 注入检查或目标 HyperOS 设备验收。Robolectric 行为、生命周期、像素和 RenderNode 测试不能证明厂商 GPU 的最终合成结果。系统菜单操作、最终颜色与轮廓仍需在目标 ROM 上确认。

## 更新来源

下表记录汇总依据，版本范围表示对应阶段的更新记录。源码链接指向本次发布保留的最终实现。

| 版本范围 | 汇总内容 | 来源 |
| --- | --- | --- |
| 0.3.152 | 横屏自定义布局、跨面板绑定与完整画布导入 | [横屏记录](control-center-landscape-v0.3.152.md) |
| 0.3.153 | 分组成员图标大小、布局格式 6、初始全局容器背景 | [容器背景记录](control-center-surfaces-v0.3.153.md) |
| 0.3.154-0.3.155 | 新增材质、全局描边、柔光玻璃与内外层材质边界 | [全局材质](control-center-global-materials-v0.3.154.md)、[柔光玻璃](control-center-soft-glass-v0.3.155.md) |
| 0.3.156、0.3.158 | 柔光底色、滑条进度保色、原生背景染色与描边顺序 | [v156 回归记录](control-center-material-regression-v0.3.156.md)、[v158 回归记录](control-center-material-regression-v0.3.158.md) |
| 0.3.157 | 当前资料缺少该版本归档及独立说明，未补造更新条目 | 本地 `artifacts/releases/` 未发现 `v0.3.157` |
| 0.3.159 | 圆形几何、原生裁剪和开启状态填充 | [形状与开启底色](control-center-shape-fill-v0.3.159.md) |
| 0.3.160 | 无独立说明；相邻源码指纹记录材质、网格和形状测试变化，未进一步推断具体条目 | 本地 `v0.3.159/source-files.json` 与 `v0.3.160/source-files.json`；[材质源码](../app/src/main/java/com/xtjm/fusionstatusbar/ControlCenterMaterials.java)、[网格源码](../app/src/main/java/com/xtjm/fusionstatusbar/ControlCenterRuntimeGrid.java) |
| 0.3.161-0.3.162 | 前景保色、真实形状识别、材质刷新、磁贴底板与外圈对齐 | [渲染修复](control-center-rendering-v0.3.161.md)、[对齐修复](control-center-tile-alignment-v0.3.162.md) |
| 0.3.163-0.3.164 | 冷启动与重启恢复、等待配置写入、导入布局后的材质处理 | [重启恢复](control-center-native-restart-v0.3.163.md)、[导入材质修复](control-center-import-materials-v0.3.164.md) |
| 0.3.165-0.3.167 | 统一状态配色、原生效果接管、滑条与独立形状、背景模糊冲突处理 | [设置与时序检查](control-center-mask-audit-v0.3.165.md)、[形状分支](control-center-shape-branches-v0.3.166.md)、[原生背景层](control-center-tile-native-mask-v0.3.167.md) |
| 0.3.168 | 透明装饰层轮廓与绘制顺序修复 | [装饰层与硬件轮廓](control-center-decoration-outline-v0.3.168.md) |
| 0.3.169-0.3.170 | 音乐与融合中心的底色、形状及单底板绘制 | [组件底色](control-center-component-colors-v0.3.169.md)、[组件形状](control-center-component-shapes-v0.3.170.md) |
| 0.3.171 | 全局描边实际空心路径兼容处理 | [空心描边记录](control-center-hollow-border-v0.3.171.md) |
| 0.3.172 | 四类系统表面接管、三条电源菜单选项和短款音量条 | [设置入口](../app/src/main/java/com/xtjm/fusionstatusbar/MainActivity.java)、[菜单 Hook](../app/src/main/java/com/xtjm/fusionstatusbar/SystemMenuHooks.java)、[通知 Hook](../app/src/main/java/com/xtjm/fusionstatusbar/NotificationSurfaceHooks.java) |
| 0.3.173 | 独立电源菜单侧边滑条及明确操作语义 | [侧边滑条](../app/src/main/java/com/xtjm/fusionstatusbar/PowerMenuSideSlider.java)、[菜单扩展](../app/src/main/java/com/xtjm/fusionstatusbar/PowerMenuExtension.java)、[执行动作](../app/src/main/java/com/xtjm/fusionstatusbar/PowerMenuActions.java) |

本地历史归档位于主工作区的 `artifacts/releases/v<version>/`。0.3.152-0.3.173 范围内现有 21 份归档，均为 debug APK；其源码指纹与版本说明用于追溯更新。各归档记录共同基础提交 `d0cac3a` 和 `sourceDirty=true`，无法仅凭 Git 历史还原每个中间版本。v0.3.172 与 v0.3.173 的新增实现由相邻归档的 `source-files.json` 比较及当前源码核对确认。

