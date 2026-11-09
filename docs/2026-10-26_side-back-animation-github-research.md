# 侧滑返回动画与明暗配色参考

2026-10-26，针对 FusionStatusBar 的返回提示外观进行 GitHub 一手源码调研。结论是采用紧凑轮廓、分阶段进度和同色系明暗色阶；本项目继续原创 Canvas 绘制，不引入整套导航控制器，也不复制其他模块的输入判断。以下参数建议不代表真机视觉或触发稳定性已经验收。

## 可用参考与取舍

| 参考 | 已核实内容 | 适合本项目的部分 |
|---|---|---|
| [R1：AOSP BackPanelController](https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/packages/SystemUI/src/com/android/systemui/navigationbar/gestural/BackPanelController.kt#L159) | 明确区分进入、激活、回撤、快速抬手、完成、取消；激活与退出有独立振动入口 | 阶段变化才反馈，避免每个 MOVE 事件或每帧振动；保留本项目宿主自己的输入与取消链 |
| [R2：AOSP EdgePanelParams](https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/packages/SystemUI/src/com/android/systemui/navigationbar/gestural/EdgePanelParams.kt#L157)、[BackPanel](https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/packages/SystemUI/src/com/android/systemui/navigationbar/gestural/BackPanel.kt#L151) | 宽高、近边/远边圆角、透明度使用独立参数；颜色按当前 Resources 的明暗模式选择配对的容器/箭头角色 | 轮廓紧凑、圆角有层次，避免现在大面积黑色水滴；每次绘制读取当前宿主明暗状态 |
| [R3：Google Material Color Utilities HCT](https://github.com/material-foundation/material-color-utilities/blob/bd811e4ca2ab94c4ce3cadc89c19b1820b9704b3/java/hct/Hct.java#L20)、[TonalPalette](https://github.com/material-foundation/material-color-utilities/blob/bd811e4ca2ab94c4ce3cadc89c19b1820b9704b3/java/palettes/TonalPalette.java) | 色相、彩度和感知明度分开处理，色域不足时可降低彩度 | 保存用户选择的种子颜色，按明暗模式派生不同明度；避免直接 RGB 取反或黑白蒙层 |
| [R4：MaterialKolor 4.1.1 HCT 移植](https://github.com/jordond/MaterialKolor/blob/473e6c7a5c603e51944fcf53402cc30bd4ab0586/material-color-utilities/src/commonMain/kotlin/com/materialkolor/hct/Hct.kt) | 本地已锁定并在 APK 运行时使用 `material-color-utilities-android:4.1.1`；缓存 AAR 的 JVM API 已用 javap 核实 | 显式暴露同版本编译依赖即可复用现有二进制，避免再带一套颜色实现 |
| [R5：Flutter Cupertino route](https://github.com/flutter/flutter/blob/66b3ad90177ccbd2963836a5556076b370622fe4/packages/flutter/lib/src/cupertino/route.dart#L843) | 拖动阶段直接跟随进度，松手后才使用曲线完成或回退，并在动画结束后清理手势状态 | 借鉴“手指跟随与收尾分开”的节奏；不把整页路由动画移植成侧边返回提示 |
| [R6：BackGestureHook 的小米双段振动](https://github.com/Kyuofox/BackGestureHook/blob/91b8936fb6c6ee098c60eea06d161fb02cc0ada1/app/src/main/java/dev/codex/backgesturehook/hooks/systemui/MiuiHapticFeedbackHelper.java)、[返回提示绘制](https://github.com/Kyuofox/BackGestureHook/blob/91b8936fb6c6ee098c60eea06d161fb02cc0ada1/app/src/main/java/dev/codex/backgesturehook/hooks/systemui/MiuiStyleBackArrowOverlay.java) | 该参考把准备阶段和抬手提交阶段分开；提示资源缓存同时记录密度和夜间模式 | 借鉴反馈分工和资源失效条件；本项目不复制其恢复宿主位图的外观方案 |

调研快照分别固定为：AOSP `1cdfff555f4a21f71ccc978290e2e212e2f8b168`，Google MCU `bd811e4ca2ab94c4ce3cadc89c19b1820b9704b3`，MaterialKolor tag `4.1.1` / `473e6c7a5c603e51944fcf53402cc30bd4ab0586`，Flutter `66b3ad90177ccbd2963836a5556076b370622fe4`。GitHub REST 接口遇到匿名限流后，改用只读 `git ls-remote` 核实引用，再按提交下载原始文件；未使用账号、访问令牌或写入 GitHub。

R6 的只读 Git 请求遇到网络超时，随后通过 GitHub codeload 下载 main 的源码 ZIP；ZIP 注释确认提交为 `91b8936fb6c6ee098c60eea06d161fb02cc0ada1`。只解出振动、返回提示和输入桥接的指定 Java 文件及许可证，未读取或执行仓库的代理指令或构建脚本。检索结果中的旧 `dev.codex.miuibackgesturehook` 路径在该提交已不成立，不能按旧搜索片段定位新源码。

## 拉起与触发的两段反馈参考

R6 的实际代码将准备反馈放在箭头渐入动画正常完成后，将抬手反馈放在已提交的释放路径；取消路径不提交。其线性马达效果为准备 `162`、抬手 `163`，V2 默认二者使用 `0`，增强模式抬手使用 `1`；线性路径还保留准备后 `140 ms` 的抬手抑制。它的绘制缓存按 `densityDpi` 和 `UI_MODE_NIGHT_MASK` 共同失效。

这些是该参考的实现事实，不是可直接套用到所有手机的保证。本项目应在已有小米宿主反馈之上明确一次“拉起”和一次“触发”，并通过回调测试确认没有重复、取消后第二段振动或因线程调度打乱顺序；不能仅复制效果编号来宣布双段反馈已生效。

## 四款预设的原创改进方案

以下尺寸是适合当前返回窗口的设计起点，并非上游原值。实际位置继续使用宿主的侧别、刘海偏移和中心坐标；松手收尾继续由现有原生进度驱动，不重新控制宿主 Animator。

| 现有预设 | 建议轮廓与运动 | 颜色处理 |
|---|---|---|
| 经典水滴 | 改为窄叶片，整体高度约 48–64 dp；通过进度拉伸中部，避免背景从整个原生高度直接铺开 | 细微同色轮廓，取消厚重全黑填充 |
| 灵动胶囊 | 主体高度约 32–44 dp，边缘留 4–9 dp 间距；靠屏幕边与远侧采用不同圆角，箭头轻微向内移动 | 明暗容器色配高对比箭头，适合作默认自定义样式 |
| 柔和涟漪 | 一个实心主体加一条随进度扩展的细环/弧，约 1.2–1.5 dp；避免两层始终完整显示的固定大圆 | 外环透明度低于主体，保持同色系 |
| 霓虹光环 | 紧凑胶囊和约 1.2 dp 描边，只保留一层低透明度柔边；取消多层粗描边造成的发糊感 | 同色高明度边缘，夜间亮度受控，不使用白色大光晕 |

可借鉴 R2 的阶段化响应，但不直接套用会越界的过冲参数：进入宽度曲线为 `(0.19,1.27,0.71,0.86)`，激活宽度为 `(0.7,-0.24,0.48,1.21)`。本项目可使用自己实现并限幅的响应曲线，始终保持零进度不画、回撤不残留、左右对称。R5 支持拖动时直接跟手、结束时才缓动的取舍。

## 同一颜色跟随白天和夜间

用户 ARGB 继续作为种子保存，派生后的颜色不写回设置。当前实现采用以下设计值；它借鉴 R2 的配对角色和 R3 的色阶方法，不声称是 Material 系统角色的原始数值。

| 输出 | 白天 Tone | 夜间 Tone | 色相与透明度 |
|---|---:|---:|---|
| 背景 | 94 | 22 | 使用背景种子色相，限制过高彩度，保留原 Alpha |
| 箭头 | 22 | 90 | 彩色箭头保留自己的色相；近中性箭头沿背景色系派生，保留箭头 Alpha |
| 轮廓 | 46 | 70 | 跟随背景色相，透明度低于主体 |

本地 JVM API 为 `Hct.Companion.fromInt(argb)`、`getHue()/getChroma()`、`Hct.Companion.from(hue,chroma,tone).toInt()`；也可用 `TonalPalette.Companion.fromHueAndChroma(hue,chroma).tone(int)`。HCT 转换得到不透明 RGB 后再恢复用户 Alpha。颜色解析应按 `(背景种子,箭头种子,nightMask)` 缓存；每帧只核对当前宿主 `Resources.configuration.uiMode`，模式变化才重算，并覆盖同一个 View 的日→夜→日切换。

不透明默认色应做箭头/容器对比测试，任意透明种子还要测试与场景底色的合成结果。用户主动把颜色设为全透明时，应保留其透明意图，不能声称任意透明色在任意应用背景上都能保证固定对比度。

## 许可证与落地边界

- AOSP Controller 有 Apache-2.0 文件头；SystemUI 包的 [Android.bp](https://github.com/aosp-mirror/platform_frameworks_base/blob/1cdfff555f4a21f71ccc978290e2e212e2f8b168/packages/SystemUI/Android.bp#L19) 也声明 Apache-2.0。这里只参考阶段和视觉取舍，不打包 AOSP 控制器源码。
- Google MCU 的 [LICENSE](https://github.com/material-foundation/material-color-utilities/blob/bd811e4ca2ab94c4ce3cadc89c19b1820b9704b3/LICENSE) 为 Apache-2.0。MaterialKolor 4.1.1 仓库 [LICENSE](https://github.com/jordond/MaterialKolor/blob/473e6c7a5c603e51944fcf53402cc30bd4ab0586/LICENSE) 为 MIT，但其 HCT 移植文件仍保留 Google 的 Apache-2.0 头，不能把全部算法部分只标为 MIT。
- Flutter [LICENSE](https://github.com/flutter/flutter/blob/66b3ad90177ccbd2963836a5556076b370622fe4/LICENSE) 为 BSD 三条款。本项目只参考交互节奏，不复制 Flutter 路由实现或引入 Flutter 运行时。
- R6 的 [LICENSE](https://github.com/Kyuofox/BackGestureHook/blob/91b8936fb6c6ee098c60eea06d161fb02cc0ada1/LICENSE) 为 Apache-2.0。本次只读参考双段反馈和模式缓存，不复制其输入桥接或宿主位图实现。
- 为实际复用的 MaterialKolor/MCU 保留 `third_party/materialkolor/NOTICE`、`LICENSE-MIT`、`LICENSE-APACHE-2.0`，由构建打包到许可目录。

原始文件、提交引用和 AAR/JVM 检查证据保存在 `E:\vs\claude\xtjm\analysis\back-gesture-feedback-20261026\research`。这份调研不证明触发稳定性修复或双段振动已通过，二者需要对应的状态机和实际回调测试。
