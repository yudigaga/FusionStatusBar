# HyperLight 高光玻璃分析与 FusionStatusBar 日间适配

分析日期：2026-10-24（Asia/Shanghai）。分析方式：读取工作区已有 JADX Java 反编译结果；报告类型为普通逆向分析，`flavor = null`。

## 结论与当前验证状态

HyperLight 普通高光玻璃依赖宿主模糊、多层背景混色和原生双光源高光描边。日间使用三层混色提亮背景，并通过白色高光呈现玻璃边缘；单层浅色背景不能等价还原这条链路。本次 FusionStatusBar 适配增加“高光玻璃（随系统）”选项：系统浅色使用日间高光玻璃，系统深色解析为已有 `SOFT_GLASS` 柔光玻璃，保留独立选择原柔光玻璃的行为。

本报告已完成本地静态证据核对。0.5.4 / 218 的 Debug、Release 各 1,220 项测试全部通过，失败、错误和跳过均为 0；两种变体编译、打包与 lint 均通过。Debug / Release lint 分别为 0 错误、99 / 98 条警告。另有 28 项 Python 工具回归通过。未连接 HyperOS 真机，未完成实际 LSPosed 回调、ROM 原生绘制和视觉效果验收。

## 范围与授权

用户要求查看工作区 HyperLight 源码，参考其适合白天的高光玻璃，为当前模块适配日间效果；日夜行为采用跟随系统浅色、深色自动切换。

| 字段 | 范围 |
| --- | --- |
| auth | 用户在当前会话授权工作区静态分析与 FusionStatusBar 适配 |
| in_scope | 普通高光混色、高光描边、日夜选择、FusionStatusBar 材质与预览的一致性 |
| source_root | `E:\vs\claude\xtjm\analysis\hyperlight-1.1.7-3\jadx\sources` |
| target_project | `E:\vs\claude\xtjm\fusion-statusbar-publish-v0.3.197` |
| network_profile | 本次分析仅使用本地文件，无网络请求 |
| scope.md | 未创建独立 case 文件；本节记录本次文档工作范围 |
| 不涉及 | 截图捕获、液态折射、陀螺仪动态高光、全局通知文字 Hook、外部发布 |

实现以行为和平台接口为依据独立编写，不把外部反编译 Java 整段搬入项目，也不导入其 DEX、图片、着色器或其他代码资源。下列数值用于解释已观察到的材质参数，不代表对外部实现源码的复制。

## 静态证据

以下 `source_ref` 均相对于上述 `source_root`，行号对应本次工作区现成 JADX 结果。这里的 SHA-256 固定的是反编译 Java 文件，**不是原始 APK 的哈希**。

| E-id | source_ref | 观察内容 | repro_command |
| --- | --- | --- | --- |
| E-001 | `com/kiminonawa/HyperLight/utils/hookutils/blur/HyperLightMaterialUtils.java:130-140,182-192,257-264` | 创建日夜 token，选择混色和 bloom，按布尔值取得材质后应用 | 复现命令 A |
| E-002 | `miuix/theme/token/ColorBlendToken.java:77,82`；`miuix/theme/token/BlendModeToken.java:8,20,23-24` | 默认日夜混色栈及混合模式数值 | 复现命令 B |
| E-003 | `com/kiminonawa/HyperLight/utils/hookutils/blur/HyperLightStrokeUtils.java:16-21,62-93`；`miuix/theme/token/MaterialToken.java:202-225` | 默认 small light/dark 高光参数及 21 项数组字段映射 | 复现命令 C |
| E-004 | `miuix/core/util/HyperMaterialUtils.java:78-103` | 无 blurConfig 时启用混色和 view blur；随后应用阴影和 bloom | 复现命令 D |
| E-005 | `miuix/core/util/HyperBloomStrokeUtils.java:13-17,24-61` | 直接反射 `View.setMiBloomStroke(float[])`；宽度换算、清除与不支持分支 | 复现命令 E |
| E-006 | `miuix/core/util/MiuiBlurUtils.java:555-568,652-681,738-758,809-821` | mix、view blur、现代混色列表与旧版追加接口 | 复现命令 F |
| E-007 | `defpackage/sb0.java:1134-1165`；`defpackage/d21.java:20-36`；`defpackage/rv0.java:216-254` | 日夜选择及独立文字颜色模式 | 复现命令 G |

| 证据 | content_hash（SHA-256） |
| --- | --- |
| E-001 | `BDAC641B30B981127871124BBCFF5E6908FEE3DFF75E822C061A86142DD81E13` |
| E-002（ColorBlendToken.java） | `3E62B677BA256E8D4B5F695F38F033334B0531322EC31AB6B4E0CEA7854EA914` |
| E-003（HyperLightStrokeUtils.java） | `061AB6E83BD9E7E6E4DDBDF9878F1FE274AC84941A3C2A071DB700C13A95CD8B` |
| E-004 | `7C0F5BC5E53739D25988681C3FE2F3D0895BBC058109DAD6B45C68BE4B6F0A45` |
| E-005 | `25D6CA559A8421912728F9A220956B384A14B3A072FBA7E1062619B307F59138` |
| E-006 | `5B93D01480285E5C15A3E8A1753033F5CC8F0B626E4002EC136976E0C7918167` |
| E-007 | n/a；以现有文件路径、行号和复现命令定位，未独立计算该组文件哈希 |

### 日间与夜间混色

默认日间选择 `Colored_Thick_Light`，默认夜间选择 `Colored_Extra_Thin_Dark`。颜色为 ARGB，必须保留层次与顺序。

| 模式 | 顺序 | ARGB | 混合模式 | 原生数值 |
| --- | --- | --- | --- | --- |
| 日间 | 1 | `#E6747474` | OVERLAY | 15 |
| 日间 | 2 | `#99525252` | COLOR_DODGE | 18 |
| 日间 | 3 | `#33D1D1D1` | SRC_OVER | 3 |
| 夜间参考 | 1 | `#C7CCCCCC` | COLOR_BURN | 19 |
| 夜间参考 | 2 | `#2E525252` | SRC_OVER | 3 |

原生现代接口将这些层编码为 `ArrayList<Point>`：`Point.x` 为颜色，`Point.y` 为混合模式，调用 `setMiBackgroundBlendColors`。旧版清除后依次调用 `addMiBackgroundBlendColor`。这只是混色接口；真实模糊仍需宿主或 FusionStatusBar 已有模糊链提供。

HyperLight 普通 token 本身没有调用 `setBlur`。`HyperMaterialUtils.applyViewMaterial` 对只有混色配置的 token 调用 `setMixEffectEnabled(true)`、`setMiViewBlurMode(1)`，再应用背景混色。不能据此给它虚构一个普通高光专属的固定模糊半径。

### 高光数组布局与默认参数

`setMiBloomStroke(float[21])` 不接收圆角或 shape 参数。下表按 `MaterialToken.Builder.setBloomStroke` 字段映射解释。颜色通道取 0–1 范围；两个宽度传入原生 API 前转换为像素，XYZ 不乘 density。

| 数组位置 | 含义 | 日间 small light | 夜间 small dark 参考 |
| --- | --- | --- | --- |
| 0 | bloom 宽度 | 0.8 dp | 0.8 dp |
| 1 | bloom 渐变角度 | 180° | 180° |
| 2–5 | bloom RGBA | (1,1,1,0.05) | (1,1,1,0.08) |
| 6 | normal 宽度 | 2.6 dp | 2.3 dp |
| 7–9 | 光源 1 XYZ | (0.5,0.5,-0.5) | (0.5,0.5,-0.5) |
| 10–13 | 光源 1 RGBA | (1,1,1,0.6) | (1,1,1,0.6) |
| 14–16 | 光源 2 XYZ | (0.5,0.95,-0.5) | (0.5,0.95,-0.36) |
| 17–20 | 光源 2 RGBA | (1,1,1,0.35) | (1,1,1,0.25) |

Miuix helper 对 HyperMaterial version≥4 尝试反射 `View.setMiBloomStroke(float[])`；方法缺失或调用抛异常返回失败。清除使用 21 项全零数组。数组版本的 dp helper 将位置 0、6 按 `int(dp*density+0.5)` 换算，标量版本传 `dp*density+0.5` 浮点值；这些是 helper 的差异，不能把其余参数也做 dp 换算。

原实现先设置混色/模糊，再设置可选阴影，最后设置 bloom；`HyperLightMaterialUtils` 在应用后才按调用方传入圆角设置 Outline。保留 FusionStatusBar 当前形状、Outline 和裁剪边界是适配方向；原生高光在具体 ROM 上是否严格沿每种自定义形状绘制，当前只有接口层静态证据，仍需设备验证。

### 日夜选择与前景色

普通入口按消费 View 的 `Configuration.uiMode` 判断浅色或深色。HyperLight 控制中心还允许单独配置跟随策略，因此不能把其所有默认设置都概括为自动跟随；FusionStatusBar 此次明确采用用户选择的系统自动模式。

文字颜色不是 material token 的一部分。HyperLight 默认通知文字模式为 `system`；强制 `light`、`dark` 或 `anti_system` 时才安装独立文字 Hook。本次不引入这些通知文字 Hook。FusionStatusBar 日间高光的中性文字和图标使用深色，深色下沿用柔光玻璃前景策略；启用态图标、卡片和开关仍需保持自身状态可辨识。

## Findings 与适配决策

| F-id | severity | evidence_ids | confidence | location | status | 结论与影响 |
| --- | --- | --- | --- | --- | --- | --- |
| F-001 | n/a_re | E-001,E-002,E-004,E-006 | high | `HyperLightMaterialUtils.f`、`HyperMaterialUtils.applyViewMaterial` | 静态确认；运行时未验证 | 普通日间玻璃应使用三层原生混色，且保留真实模糊来源 |
| F-002 | n/a_re | E-001,E-003,E-005 | high | `HyperLightStrokeUtils.g`、`HyperBloomStrokeUtils.setBloomStroke` | 静态确认；运行时未验证 | 可以独立组装静态 21 项数组调用原生 bloom，无须引入 gyro 或外部 token 类 |
| F-003 | n/a_re | E-001,E-005 | medium | 高光 API 与 View Outline | 需要设备验证 | 接口无 shape 参数；不得仅凭反射调用成功宣称所有形状视觉正确 |
| F-004 | n/a_re | E-007 | high | `sb0.y`、`d21.a`、`rv0` 文字管理 | 静态确认；本地适配回归通过，设备未验收 | 日夜材质选择与文字颜色需分别处理，自动模式保存值应独立于当时 uiMode |

本次计划在既有材质枚举末尾追加 `HIGHLIGHT_GLASS`，名称为“高光玻璃（随系统）”，避免改变已有选项的序号语义。保存该选择后，在实际消费 View 的 Context 上解析：浅色保留高光玻璃，深色解析为 `SOFT_GLASS`。原 `SOFT_GLASS` 选项保持已有视觉含义。

日间原生路径优先使用上述三层混色与静态 bloom；不支持原生接口时使用 FusionStatusBar 自有 Canvas 装饰和浅色底色降级。Canvas 只能近似反光边缘与玻璃色调，不能等价提供 ROM 原生背景混色、真实模糊或像素折射。为亮背景增加的暗轮廓属于 FusionStatusBar 的可见性适配；HyperLight 默认高光数组中的光源都是白色，没有暗色轮廓。

在“全局外观 → 全局容器背景”选择“高光玻璃（随系统）”即可启用。保存值保持自动选项，各消费 View 使用自身 Context 解析浅色、深色；原柔光玻璃保持独立。已存在的原生布局从其他材质切入时会补建按 root 去重的主题监听，稳定帧不重建材质，退出自动选项时清理监听。只有从构造期即可直接识别、且确认支持高光方法的原生视图，才允许在没有 OEM 高光请求时建立零基线；后续 OEM 请求仍覆盖该基线，退出时恢复最新请求。父容器推导的视图不假定其早期高光为零。

本次深色模式采用既有柔光玻璃，因此上面的夜间 HyperLight 参数仅用于解释原实现，不代表本次要替换柔光玻璃的夜间效果。截图液态折射、动态图层和陀螺仪不在本次范围。

## 调用路径

`P-001`：`path_type=callflow`，起点为材质应用入口，目标为普通日间高光玻璃。

1. 通知入口或控制中心消费 View 读取日夜状态，选择 light/dark。证据：E-007；结论：F-004。
2. `HyperLightMaterialUtils.f` 构建混色与高光 token，`i` 取得所选日夜配置。证据：E-001,E-002,E-003；结论：F-001,F-002。
3. `HyperMaterialUtils.applyViewMaterial` 应用混色/模糊、可选阴影与 bloom。证据：E-004；结论：F-001。
4. `MiuiBlurUtils` 写入多层背景混色，`HyperBloomStrokeUtils` 调用 `View.setMiBloomStroke`。证据：E-005,E-006；结论：F-002。
5. 调用方 Outline/裁剪限定几何边界。证据：E-001；结论：F-003，ROM 视觉结果未验收。

`P-002`：`path_type=callflow`，FusionStatusBar 适配范围。保存 `HIGHLIGHT_GLASS` → 消费 View 按当前系统模式解析 → 浅色使用日间原生混色/高光或 Canvas 后备 → 深色使用已有 `SOFT_GLASS` → 预览和真实组件采用同一解析规则。此路径的源码行为、单元测试和设备效果须分别验证，静态分析不替代这些检查。

## 复现本地分析

以下命令在 PowerShell 中只读执行，不需要启动 IDA、Frida 或重新解包 APK。运行环境需要工作区原始分析文件和 `rg`。

```powershell
$sourceRoot = 'E:\vs\claude\xtjm\analysis\hyperlight-1.1.7-3\jadx\sources'

# A：构建、选择和应用材质
rg -n 'setColorBlend|setBloomStroke|applyViewMaterial|public static void i' "$sourceRoot\com\kiminonawa\HyperLight\utils\hookutils\blur\HyperLightMaterialUtils.java"

# B：默认混色与模式数值
rg -n 'Colored_Thick_Light =|Colored_Extra_Thin_Dark =' "$sourceRoot\miuix\theme\token\ColorBlendToken.java"
rg -n 'SRC_OVER|OVERLAY|COLOR_DODGE|COLOR_BURN' "$sourceRoot\miuix\theme\token\BlendModeToken.java"

# C：默认描边和数组字段映射
rg -n 'public static final float\[\]|Glass_Stroke_Small' "$sourceRoot\com\kiminonawa\HyperLight\utils\hookutils\blur\HyperLightStrokeUtils.java"
rg -n -A24 'Builder setBloomStroke\(float\[\]' "$sourceRoot\miuix\theme\token\MaterialToken.java"

# D：应用顺序
rg -n -A26 'void applyViewMaterial' "$sourceRoot\miuix\core\util\HyperMaterialUtils.java"

# E：原生 bloom 接口、换算和清除
rg -n 'EMPTY|setBloomStrokeWithDp|setMiBloomStroke|getVersion' "$sourceRoot\miuix\core\util\HyperBloomStrokeUtils.java"

# F：背景混色桥接
rg -n 'setMixEffectEnabled|setMiViewBlurMode|wrapBlendConfig|setMiBackgroundBlendColors|addMiBackgroundBlendColor' "$sourceRoot\miuix\core\util\MiuiBlurUtils.java"

# G：日夜选择与文字模式
rg -n -A32 'boolean y\(View view, String str\)' "$sourceRoot\defpackage\sb0.java"
rg -n -A18 'void a\(View view, int i, String str\)' "$sourceRoot\defpackage\d21.java"
rg -n -A38 'notification_text_color_mode' "$sourceRoot\defpackage\rv0.java"

# 校验证据文件固定性；结果应与上表一致
Get-FileHash -Algorithm SHA256 -LiteralPath "$sourceRoot\miuix\theme\token\ColorBlendToken.java"
```

## 验证记录与遗留项

| 检查 | 当前状态 | 能说明什么 |
| --- | --- | --- |
| 已有 JADX 源码与行号核对 | 已执行 | 本文参数、接口和调用顺序具有静态依据 |
| 关键 Java 文件 SHA-256 | 已执行 | 可确认本报告对应的分析输入 |
| FusionStatusBar 定向单元测试 | 71 项通过；后续补充用例已并入全量验证 | 像素、预览、原生 API 替身、恢复及缓存的本地回归 |
| FusionStatusBar 全量单元测试 | Debug / Release 各 1,220 项通过，0 失败、0 错误、0 跳过 | 包括新增 16 项自动玻璃回归，覆盖原生布局首次/再次启用、日夜刷新、混色/高光/mix 恢复、激活态、滑条、配置备份、预览及缓存 |
| Python 工具回归 | 28 项通过 | 新选项同步进设置流程检查工具，工具和构建校验器本地回归通过 |
| 编译 / APK 构建 | Debug / Release 均通过，0.5.4 / 218 | 最后一轮日志为 `analysis/highlight-glass-full-final-0.5.4-20261024.log`，`BUILD SUCCESSFUL in 19m 26s` |
| lint | Debug / Release 均 0 错误，99 / 98 条警告 | 原生绘制和性能不属于 lint 验证范围 |
| 真机和 LSPosed | 未执行 | 原生 API 可用性、回调执行和 ROM 实际效果均未验收 |
| 日夜切换、亮暗壁纸、自定义形状 | 未执行设备验收 | 不能根据本地 Canvas 绘制或源码推断宣称最终视觉一致 |

设备验收应覆盖白天亮壁纸/暗壁纸、原生 bloom 成功与后备路径、日夜切换后已有 View 的刷新、启用态与未启用态文字图标对比、自定义形状裁剪、退出高光选项后的原生残留清理。这里列出的是验收范围，不是已通过的结果。

## 时间线

1. 2026-10-24：按用户要求只读分析工作区现成 HyperLight 1.1.7-3 JADX Java 输出。
2. 2026-10-24：交叉核对默认日间三层混色、21 项 bloom 字段、原生调用与日夜选择；计算关键源文件 SHA-256。
3. 2026-10-24：确认本次自动模式采用浅色高光玻璃、深色既有柔光玻璃；排除液态截图折射与 gyro。
4. 2026-10-24：生成本报告初稿；实现检查仍为待验证，设备验收未执行。
5. 2026-10-24：修复首次/再次启用高光玻璃的观察器注册遗漏，补充原生高光零基线和最新请求恢复回归。首轮测试的 qualifier 顺序和独立 blur 恢复期望已校正；后续 71 项定向回归通过。
6. 2026-10-24：Windows 提交内存不足导致构建启动和原生 JVM 分配失败，相关日志保留在工作区 `analysis/highlight-glass-memory-failures`。采用单 worker、768 MiB 测试堆和更频繁分叉，在内存恢复后完整重跑，两种变体各 1,220 项、lint 与 APK 构建全部通过。

最终签名 APK 的哈希、证书、对齐和源码索引以本地归档 `artifacts/releases/v0.5.4/release.json` 为准。该归档由 `tools/release.ps1 -SkipBuild -AllowDirty` 验证本轮完整构建输出并生成，不代表远端发布、CI 或设备验收。
