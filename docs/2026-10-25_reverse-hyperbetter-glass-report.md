# HyperBetter 玻璃实现与 HyperOS 3 兼容边界分析

分析日期：2026-10-25，时区 Asia/Shanghai。类型：普通 APK 静态逆向与既有日志核对，`flavor = null`。

## 1. 执行摘要

HyperBetter 的完整玻璃链调用宿主材质令牌、`setMiGlass(float[])`、双采样半径和背景采样容器；它与 FusionStatusBar 此前使用的混色、模糊及 21 项 bloom 描边并非同一实现。参考 APK 的高级参数编辑器处理 42 项玻璃数组，但这不能推导出每个 ROM 都接受相同数组布局。APK 入口没有数值意义上的“仅 HyperOS 4”判断，实际功能仍受宿主类、Bionics 材质模式和私有 API 能力约束。

用户已确认参考 HyperBetter **尚未在自己的设备测试**，当前设备为 **HyperOS 3**。附带 JSON 自称 HyperLight 配置，其 48 个键在 HyperBetter 反编译结果中均未找到，不能据此直接生成 HyperBetter 或目标 ROM 的原生参数。0.5.11 日志还显示不同组件采用不同材质分支，不能用某个组件的 `nativeGlass=true` 宣称所有卡片的玻璃效果已生效。

日期显示的降级提示有独立的跨 classloader 能力归属证据。用户已说明期间点击过手动重启；本报告不将 PID 更替归因为已确认的 SIGSEGV 或模块原生崩溃，也不据此排除未记录的异常。本轮没有连接目标 ADB 设备，也没有完成玻璃视觉或稳定性验收。

## 2. 范围、样本与方法

授权与边界见 [scope.md](../../analysis/hyperbetter-20261025/scope.md)。输入为用户提供的 APK、配置和 LSPosed ZIP；网络策略为 `offline_local_files`。只做本地解包、静态阅读、日志核对和只读设备枚举，没有执行、修改或安装参考 APK，没有向外发送输入文件。本文不包含第三方反编译类或着色器的整段复制。

| 项目 | 观察结果 | 证据 |
| --- | --- | --- |
| APK | [HyperBetter.apk](../../HyperBetter/HyperBetter.apk)，2,593,125 字节 | E-01 |
| SHA-256 | `65a5e4442ce4c6bc07514d5848506bd33c043d05c212b8282bcb667b6b9d5ac7` | E-01 |
| 包名、版本 | `btm.m.os4.hyperbetter`，`1.3.0 / 10300` | E-01 |
| SDK | min 33、target 35、compile 37 | E-01 |
| 应用与模块入口 | `btm.m.os4.systemuihook.HookApplication`；现代 Xposed 入口 `x.m60` | E-02 |
| Xposed 元数据 | min/target API 102；scope 含 SystemUI、plugin、AOD 和 HyperLight 包名 | E-02 |
| DEX 与本地库 | 一个 `classes.dex`；四个 ABI 的 AndroidX graphics-path 支持库 | E-03、E-04 |
| 工具 | JADX 1.5.6、Apktool 3.0.2、Microsoft OpenJDK 21.0.12.1 | 命令 A、B |
| 解包状态 | JADX 退出码 1，报告 75 项错误，产出 2,815 个 Java 文件；Apktool 退出码 0，smali 完整落盘 | [解包日志](../../analysis/hyperbetter-decode-20261025.log) |

JADX 中可见错误的类型推断和重复控制流，不能把伪 Java 当作可编译源码。关键属性门槛、42 项数组条件、令牌数组复制、完整调用失败后的后备分支以及采样器构造门槛已用 smali 交叉确认。

### 导入与依赖证据

APK 没有传统 PE 导入表，因此采用 DEX 外部方法引用作为托管代码的等价证据。[E-imports-dex.json](../../analysis/hyperbetter-20261025/E-imports-dex.json) 记录 2,821 个 DEX 声明类及 2,485 个外部方法引用；其中界面/图形分类含 `android.graphics`、`android.view` 和 `android.widget`，共 1,116 项，另有 hooking 14 项、反射 35 项、运行时 9 项、文件/偏好存储 68 项。所选 `java.net`、OkHttp 分类没有命中；这只是方法引用统计，不是完整的网络安全审计结论。

[E-native-libraries.json](../../analysis/hyperbetter-20261025/E-native-libraries.json) 保存四个 ELF 的哈希、依赖和导出。它们仅导出 `JNI_OnLoad`，包含 `androidx/graphics/path/PathIteratorPreApi34Impl`、`ConicConverter` 及对应 JNI 注册字符串；Java 加载点为 `androidx/graphics/path/PathIteratorPreApi34Impl.java:10`。当前包内未见独立承载玻璃着色器的专用 `.so`；已定位的原生玻璃能力来自 ROM 私有 API。

## 3. Evidence：可复查观察

下表各行的 `observed_at` 均为 2026-10-25；日志事件时间另行列出。`linked_workitem = n/a`，`supersedes = none`。JADX/smali 路径分别以 case 内 `reference/jadx/sources/`、`reference/apktool/smali/` 为根。完整输入及附件哈希见 [report-hashes.json](../../analysis/hyperbetter-20261025/report-hashes.json)，关键反编译文件哈希见 [static-findings.json](../../analysis/hyperbetter-20261025/static-findings.json)。

| E-id | source_type / source_ref | 固定观察与摘录 | content_hash / artifact_path | repro_command |
| --- | --- | --- | --- | --- |
| E-01 | file；原始 APK、`reference/apktool/apktool.yml:7–13`、Manifest | 版本 `1.3.0 / 10300`、包名及 SDK；APK 字节身份固定 | `65a5e4442ce4c6bc07514d5848506bd33c043d05c212b8282bcb667b6b9d5ac7`；原样本位于 case 外 | A、B |
| E-02 | file；Xposed 三个元数据文件、`x/m60.java:190–228` | 入口 `x.m60`；读取 `ro.mi.os.version.code` 后只判断去空白字符串长度；smali `x/m60.smali:1497,1585–1607` | `84925e95ca2b5b7ac318f0af1889c3d33b5f404b90faf136fb6a91bbe336412c`；`reference/jadx/sources/x/m60.java` | C |
| E-03 | file；DEX 外部引用清单 | 托管导入等价证据与分类计数 | `3093ab2738f71db10dabaf03e6c4046c03729ead830e0021742bfbef380ac175`；`E-imports-dex.json` | A、B、C |
| E-04 | file；四个 ELF 的依赖、导出、JNI 字符串 | AndroidX Path JNI，而非新发现的专用玻璃本地库 | `1d388c523d1fbc1b1b1fc533c2fb00a36e81a523c0a5d29a523533585a2a36c3`；`E-native-libraries.json` | A、B |
| E-05 | file；`x/kd1.java:27–63`、`x/yd1.java:837–884` | 查找 `setMiGlass`、双模糊半径及采样路由 API；必需查找失败时记录 native sampling 不可用 | `e1367b4f1a31a7fec46d8bab8e635acf727e63e8b7da8e1e4b053f4d25f7a531`；`reference/jadx/sources/x/kd1.java` | C |
| E-06 | file；`x/w1.java:295–430,887–1076`、`x/s1.java:17–25` | 宿主激活态令牌、Bionics 参数捕获、进度裁剪；完整、混色和纯色后备状态分开 | `a61184c6aabfdbace9753629c248f577f9eea765eef4f5a77405946697923a6c`；`reference/jadx/sources/x/w1.java` | C |
| E-07 | file；`x/e40.java:25–55,68–81`、`x/w30.java:13–91` | 只编辑长度 42 的数组；37–41 乘原值，42/43 单独修改两个半径；smali `x/e40.smali:43–58` | `edc56f7660b6418bc071d608bd4c8679943b409c445be6bc74c28cb4429866ee`；`reference/jadx/sources/x/e40.java` | C |
| E-08 | file；`x/k6.java:38–48,80–163,200–204`、`x/hh0.java:36–94`、`x/i60.java:27–113` | BitmapShader 输入、AGSL 折射/色散、坐标映射及有界缓存；失败不伪装成原生玻璃成功 | `cab12a0e3a9949d739883a08890095bef32b9ae92ebee4eabc166334f70f9ef5`；`reference/jadx/sources/x/k6.java` | C |
| E-09 | file；`x/yd1.java:633–635,760–767,780–795`、`x/gd1.java:2297–2305` | APK 的光学层安装与原生玻璃消费者/采样器关联；缺少 `kd1` 时不建立对应 `ud1` | `3cc0764ec923e06dc1dd870494face0844ac2be30ee2525bdee1f1208fc9194c`；`reference/jadx/sources/x/yd1.java` | C |
| E-10 | file；原始 JSON 和完整 Java/smali 键搜索 | `appName=HyperLight`、`module=soft_glass_global`；48 个键全部未命中 HyperBetter 消费代码 | `63d07418bb4581cfdfa97769ade40c39a2d8f327b4f9aa74e0ec4ab4b06d49cc`；原配置位于 case 外 | D |
| E-11 | log；归档中的 0.5.11 摘录，原日志行 3075、3151、3197–3205、3215 | 核心 loader `header=2`，plugin `header=0`；媒体/设备与普通磁贴分支不一致；精确日期降级 reason | `1ed1649a82f229fbe66aad8871f8d9599e41d226582390f0a606217d5419b399`；`crash-evidence/v0.5.11-runtime-evidence.txt` | E |
| E-12 | log；`header-split-red.log` | 跨 loader 日期、运营商两个回归用例失败；4 项测试中 2 项失败 | `72d63709a95e241c831a11e7877d91f9128a69073a47d1f72858bb6c2904ca95`；`header-split-red.log` | E |
| E-13 | log；归档分诊及 tombstone 索引 | 没有找到匹配本轮 PID 的 SystemUI Java/native fatal；历史 tombstone 不能用于认定本轮崩溃 | `a331d57dbf870a0f953daaef353dfbb850b2b74f89589b6ce93c92faaa5361cc`；`crash-evidence/triage-summary.json` | E |
| E-14 | command；本地 `adb devices -l` | 输出只有 `List of devices attached` 表头，无连接设备 | `bbfcc7239a6f1be3544814ebdc6bcc9b66bf5b1f0b03baa3340808f19265a630`；`adb-devices.txt` | F |
| E-15 | manual；当前会话用户补充 | 用户确认 HyperOS 3、HyperBetter 尚未实测，期间点击过手动重启 | `n/a`；会话输入，不伪造文件哈希 | 复查当前会话中的直接说明；离线文件不能替代该说明 |

## 4. 参考实现的调用契约

### 完整材质、Bionics 与后备分支

模块在 `onPackageLoaded` 中取得 `hyperbetter` 远程偏好。`x/da1.java:787–824` 监听运行时 `ClassLoader.loadClass(String)`，处理控制中心和其他材质目标的延迟加载。主滑块 Hook 为 `ToggleSliderViewHolder.updateProgressBlendColors`，采用 libxposed `PROTECTIVE`；`x/j60.java` 先执行宿主逻辑，再在 `control_center_active_large_tile_material` 启用时应用材质（E-02、E-06）。

`x/w1` 从宿主取得 `MiuiColorBlendToken.CC_TILE_ON_BLEND_COLORS` 与 `MiBackgroundStyle.ACTIVATED_GLASS_TOKEN`，优先调用完整的 `setMiBackgroundStyle`。完整入口不可用时才尝试 `applyBlendColor`；宿主 `MaterialMode$Disabled` 的相应路径使用纯色后备。状态枚举分别为 `APPLIED_FULL`、`APPLIED_BLEND_FALLBACK`、`APPLIED_SOLID_FALLBACK`，另有令牌/API 缺失状态。这里的 FULL 表示完整宿主调用成功，不是目标设备的视觉验收。

捕获滑块原生玻璃数组另有条件：完整状态且运行模式类名以 `MaterialMode$Bionics` 结束，才读取令牌 `getToBionicsParams()`；否则不假定存在可重放的玻璃参数。数组按真实长度复制，随后以滑块当前进度计算裁剪区域，调用 `setMiGlassClip`，再重放 `setMiGlass`。相关 smali 锚点为 `x/w1.smali:6023,6149–6152,6547,6680`。

### 42 项参数与 21 项 bloom 的区别

`x/e40.a(float[])` 的长度 42 检查及 `x/w30` 编辑器字段说明相互对应。编辑器中的 0–41 是目标数组内的参数，42/43 是单独传给 `setMiGlassBlurRadius(int,int)` 的两个半径。

| 参数组 | 参考 APK 的位置/行为 | 不能推导的结论 |
| --- | --- | --- |
| 明暗、颜色、整体材质透明度 | 例如 5 为主体饱和度、18 为整体不透明度 | 不代表与 JSON 同名字段同单位 |
| 曲面与折射 | 19 为边缘过渡宽度、21 为厚度、32 为折射率；名称来自 APK 自身编辑器说明 | 不代表所有 HyperOS 版本使用同一字段布局 |
| 背景染色 | 33/34 为背景染色饱和度/亮度偏移 | 不能把负向 UI 调节值当作原生系数 |
| 按压光 | 37–41 乘以正在动画的宿主原值 | 不能用固定值替换全部动态状态 |
| 两个模糊半径 | 编辑器索引 42/43，独立 API 参数 | 不是 42 项数组的额外两个元素 |

`e40` 还会保留无效亮度范围的原值，光照方向三项同时为零时恢复宿主方向。完整 smali 未发现 `setMiBloomStroke` 或 `setMiShadow` 字面量。故不能以“都接收 float[]”为由，把 21 项 bloom 轮廓数组传给 `setMiGlass`，也不能把描边近似效果认定为参考材质实现（E-05、E-07）。

### 背景采样与独立 AGSL 光学层

`x/kd1` 集中解析私有方法，包括选择背景模糊容器、pass-window-blur、原生材质类型、`setMiGlass` 及双半径接口。`x/yd1` 保存宿主请求的玻璃数组、半径和采样路由状态；`x/pd1.java:31–51` 可把背景采样指向专用图像消费者，并调整 pass-window-blur 和背景模式。它处理的是实际采样源，而非只为前景控件画一条轮廓（E-05、E-09）。

可选的 `x/k6` 光学渲染器接收 Bitmap，使用 BitmapShader 和 RuntimeShader，在圆角矩形的有符号距离与法线方向上计算折射，并对多个偏移位置采样得到色散。`x/hh0` 可取自定义面板图像或排除图层后的捕获结果，再把来源坐标映射到当前 View；`x/i60` 的变换后图像缓存上限为 4。无来源、图像已回收、尺寸或矩阵无效时不会凭空生成真实背景。AGSL 或采样失败会禁用对应光学层并记录日志，不能把失败后的普通显示当作光学成功（E-08）。

[提取的 AGSL](../../analysis/hyperbetter-20261025/hyperbetter-liquid-lens.agsl) 仅保存在分析目录作为证据，没有导入 FusionStatusBar。渲染器的数学部分不调用 42 项原生 API；但 APK 的实际安装链仍依赖原生采样能力：`yd1.h` 在 `kd1` 不存在时返回 null，共享通知交叉淡入路径也有相同门槛。因此该 APK 没有证明一个自动适配当前 HyperOS 3 的独立光学后备方案。

## 5. HyperOS 3 与 JSON 的适用边界

入口 `x/m60.java:201–213` 的检查是 HyperOS 系统属性非空，**不是 `版本 >= 4`**。另一方面，`kd1` 对 `setMiGlass`、`setMiGlassBlurRadius`、`setMiViewMaterialType` 等必需方法执行实际查找；缺失时 `yd1.b` 捕获失败并记录原生采样不可用。`getBackGroundBlurContainer`、`setMiCustomSurfaceColorType` 和 `setMiGlassClip` 是其中的可选查找。不能根据模块名称、compile SDK 或旧模糊 API 的 `5/5` 计数，推导当前设备满足这组原生玻璃接口。

用户尚未实测 HyperBetter，当前设备为 HyperOS 3（E-15）。[兼容性核对附件](../../analysis/hyperbetter-20261025/hyperos3-compatibility.json) 只证明参考 APK 的版本门槛和能力门槛如何写，不证明这些能力已在用户设备上工作。RuntimeShader 算法可独立接收 Bitmap 也不等于现有背景捕获、坐标变换和原生接线已兼容该设备。

JSON 的 `appName` 为 `HyperLight`、`module` 为 `soft_glass_global`。HyperBetter 使用自己的偏好和 `v1;索引=值` 参数表示，完整 Java/smali 搜索没有命中配置中的 48 个键。配置标注的 `int`、`float`、`boolean`、`long` 是序列化类型，不是向原生玻璃数组写值的转换规则。

| JSON 示例 | 与 HyperBetter 的可核对差别 | 处理结论 |
| --- | --- | --- |
| `soft_glass_global_alpha = 0`、`overall_alpha = 0` | HyperBetter 编辑器整体不透明度字段默认 1；直接写原生字段 18 的 0 会关闭材质显示 | 不能据名字机械映射；HyperLight 零值是否为“未覆盖”须找其真正消费者 |
| `shape_edge_px = 0`、方向参数多项为 0 | HyperBetter 有字段范围和特定零值保护；未提供覆盖键与明确覆盖 0 不等价 | 不能假定全部零值都表示继承，也不能全部写为 0 |
| `refraction = -50`、`bg_saturation = -2` | HyperBetter 对应编辑器字段采用其他范围和含义 | 没有消费者代码支持的单位换算不得编造 |
| `blur_radius = 100`、`burn = 50`、`reflection = 5` | JSON 中是 HyperLight 的调节值；不是已知的 42 项布局或两个采样半径 | 保留原始类型和值作为配置证据，不据此生成 ROM 数组 |

## 6. 0.5.11 日志与日期降级

本节分析的是用户归档内的 FusionStatusBar `0.5.11 / 225`，不是 HyperBetter 的运行结果。完整固定摘录见 [v0.5.11-runtime-evidence.txt](../../analysis/hyperbetter-20261025/crash-evidence/v0.5.11-runtime-evidence.txt)，分诊边界见 [triage-summary.json](../../analysis/hyperbetter-20261025/crash-evidence/triage-summary.json)。

| 时间与原日志行 | 实际观察 | 可支持的结论 |
| --- | --- | --- |
| 17:40:38.238 | 首次记录加载 0.5.11/225 | 本轮日志版本身份可核对 |
| 17:46:26.988，3075 | 主 SystemUI loader `header=2`、`layoutAvailable=false` | 日期/运营商头部 Hook 与布局能力可能归属不同 loader |
| 17:46:32.440，3151 | plugin `header=0`、`layoutAvailable=true`、hooks=39 | 不能仅用活动布局 loader 的 header 数量否定全局已安装能力 |
| 17:46:45–46，3197–3205 | 媒体、设备中心 `nativeGlass=true`、Canvas `fill=false`、`nativeBgBlur=1/275`；普通圆磁贴实际 ImageView 为 `software=true`、`nativeGlass=false`、`nativeBgBlur=0/0` | 组件之间分支不一致；日志标志及 getter 值不等于图形合成和视觉成功 |
| 17:47:13.058–059，3214–3215 | `border=false` 后仍报告 `control_center_date_visibility_hook_unavailable` | 日期降级与全局边框开关不是同一问题 |

普通磁贴记录的请求没有 Bloom 基线，是分流时可核对的输入差别；这不证明它是所有材质观感问题的唯一原因。主/插件 loader 的日期能力分离则已有本地 RED：`ControlCenterSplitHeaderCapabilityRegressionTest` 的日期与运营商用例失败，4 项中 2 项失败（E-12）。该 RED 证明本地能力判定缺口，不替代真机日期显隐验收。

归档记录了五次相关 SystemUI PID 退出；没有匹配的本轮 Java fatal、native fatal 或当前 tombstone。历史其他进程的 tombstone、其他应用的 Java 异常，以及启动期间被捕获后仍继续运行的 TurboSched 异常，都不能作为本轮模块原生崩溃的证据。用户进一步说明期间点击过手动重启，且退出前有 `su` 事件，因此这些 PID 更替不能直接归因为已定位的 SIGSEGV。该澄清也不等于排除所有潜在稳定性问题（E-13、E-15）。

## 7. Findings：结论与状态

此处 `validated` 只覆盖表内明确限定的静态或本地命题；不表示目标 ROM 渲染验收。原因推断、设备兼容性保留为 `candidate`。

| F-id | severity / category | evidence_ids | location | confidence / status | 结论与剩余限制 |
| --- | --- | --- | --- | --- | --- |
| F-01 | n/a_re / reverse_algo | E-05、E-07 | `x.kd1`、`x.e40.a` | high / validated | 参考使用原生玻璃及其 42 项编辑契约，不能用 21 项 bloom 等价替代；其他 ROM 布局未验证 |
| F-02 | n/a_re / other | E-02、E-05、E-06 | `x.m60.onPackageLoaded`、`x.w1.o` | high / validated | 没有数值版号 >=4 门槛，但完整功能依赖宿主能力/Bionics；用户 HyperOS 3 的完整兼容性未验证 |
| F-03 | n/a_re / reverse_algo | E-08、E-09 | `x.k6.c`、`x.yd1.h`、`x.gd1` | high / validated | 光学算法与原生数组在数学上分离，实际 APK 接线仍有原生能力门槛；不是已证明可用的 OS3 后备 |
| F-04 | n/a_re / other | E-07、E-10 | JSON 与 `x.e40`、`x.w30` | high / validated | 两种配置架构不同，不能直接移植 48 个键或统一解释零值 |
| F-05 | n/a_re / design | E-11、E-05 | 0.5.11 组件分支与参考 API | medium / candidate | 运行时分流和实现契约差异足以要求重新核对 OS3 合同，尚未证明材质视觉问题的唯一原因 |
| F-06 | n/a_re / design | E-11、E-12 | 日期/运营商跨 loader 能力归属 | high / validated | 本地复现日期能力误判；修复后的设备日期状态仍须另验 |
| F-07 | n/a_re / other | E-13、E-15 | 本轮 PID 更替归因 | high / validated | 该批事件有用户手动重启确认，不能称为已确认模块原生崩溃；不扩大为全局稳定性结论 |

## 8. Path：关键调用链

### P-01：宿主材质令牌到滑块玻璃

`path_type=callflow`；起点为包加载与滑块材质更新，目标为解释参考实现的完整/后备区别。

1. `x.m60.onPackageLoaded` 通过属性与包名门槛，读取远程偏好并注册加载/滑块回调。证据 E-02，结论 F-02。
2. 取得宿主激活态混色和玻璃令牌，优先完整入口；不可用时采用混色或纯色后备。证据 E-06，结论 F-01、F-02。
3. 只有相应完整 Bionics 路径才捕获原令牌的真实数组；按当前滑块进度设置裁剪，再重放原生玻璃。证据 E-06、E-07，结论 F-01。

剩余风险：API 存在、调用返回和状态名均不能替代目标设备的实际显示验证。

### P-02：背景来源到可选光学层

`path_type=callflow`；起点为可用的原生采样能力与背景来源，目标为解释折射为何不等于简单描边。

1. 解析原生采样接口，登记宿主玻璃请求与容器状态；不满足能力门槛时不创建对应图像材质消费者。证据 E-05、E-09，结论 F-03。
2. 获取自定义背景图像或排除图层后的捕获结果，并换算到目标 View 坐标。证据 E-08，结论 F-03。
3. BitmapShader 作为 RuntimeShader 输入执行边缘折射和色散；来源或着色器失败时退出对应层。证据 E-08、E-09，结论 F-03。

剩余风险：当前没有目标设备来源捕获、性能、边缘形状和渲染正确性的实测证据。

### P-03：跨 loader 日期能力到降级提示

`path_type=callflow`；起点为核心 loader 和 plugin 的分别安装结果，目标为定位日期降级的本地判定缺口。

1. 日志记录主 loader 有 header Hook，plugin 有布局 Hook 但 header 数量为零。证据 E-11，结论 F-06。
2. 活动布局判断未正确纳入对应已安装头部能力时，出现日期不可用 reason。证据 E-11、E-12，结论 F-06。
3. 本地分离安装结果的回归复现日期/运营商两个失败用例；设备修复效果仍单独验收。证据 E-12、E-14，结论 F-06。

剩余风险：本报告不以日期能力测试通过推导玻璃 API 正确，也不把日期降级提示推导成原生崩溃。

## 9. 可复现命令

以下命令用于 PowerShell。本轮工具实际位置由 ToolDiscovery 解析并运行版本命令验证；技能目录中的 `tool-index.md` 不存在，未使用目录内旧 `FixedVersion` 标签作为工具版本。

### A：确认输入与工具

```powershell
$glassApk = 'E:\vs\claude\xtjm\HyperBetter\HyperBetter.apk'
Get-Item -LiteralPath $glassApk
Get-FileHash -LiteralPath $glassApk -Algorithm SHA256
. 'C:\Users\95300\.codex\skills\skills\scripts\lib\ToolDiscovery.ps1'
Resolve-ReverseToolSpec -Name jadx
Resolve-ReverseToolSpec -Name apktool
& 'C:\Users\95300\Tools\jadx\bin\jadx.bat' --version
& 'C:\Users\95300\Tools\apktool\apktool.bat' --version
java -version
```

### B：在新目录重做解包，保留既有证据

```powershell
$glassCaseRoot = 'E:\vs\claude\xtjm\analysis\hyperbetter-20261025'
$glassReproRoot = Join-Path $glassCaseRoot ('repro-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
if (Test-Path -LiteralPath $glassReproRoot) { throw 'Reproduction output already exists' }
& 'C:\Users\95300\.codex\skills\skills\apk-reverse\scripts\decode.ps1' `
  -ApkPath 'E:\vs\claude\xtjm\HyperBetter\HyperBetter.apk' `
  -Name reference -OutRoot $glassReproRoot
```

原始解包位于 case 的 `reference/`；JADX 的非零退出码已记录，应结合 smali 查看，不应删除或掩盖该状态。

### C：复查属性门槛、原生接口与数组协议

```powershell
$glassCaseRoot = 'E:\vs\claude\xtjm\analysis\hyperbetter-20261025'
$glassJavaSources = Join-Path $glassCaseRoot 'reference\jadx\sources\x'
$glassSmaliSources = Join-Path $glassCaseRoot 'reference\apktool\smali\x'
rg -n 'ro.mi.os.version.code|length\(\) > 0' (Join-Path $glassJavaSources 'm60.java')
rg -n 'setMiGlass|setMiGlassBlurRadius|setMiViewMaterialType' (Join-Path $glassJavaSources 'kd1.java')
rg -n 'fArr.length != 42|37 <=|i < 44' (Join-Path $glassJavaSources 'e40.java')
rg -n 'getToBionicsParams|MaterialMode\$Bionics' (Join-Path $glassJavaSources 'w1.java')
rg -n '0x2a|array-length' (Join-Path $glassSmaliSources 'e40.smali')
rg -n 'getToBionicsParams|copyOf\(\[FI\)' (Join-Path $glassSmaliSources 'w1.smali')
rg -n 'setMiBloomStroke|setMiShadow' $glassSmaliSources
```

最后一次搜索无命中时 `rg` 退出码为 1，这是“未找到字面量”，不是 APK 无任何反射或等价效果的证明。DEX 与 ELF 原始清单可从 E-03、E-04 附件核对。

### D：复查 JSON 全部键的消费者

```powershell
$glassCaseRoot = 'E:\vs\claude\xtjm\analysis\hyperbetter-20261025'
$glassProfile = Get-Content -LiteralPath `
  'E:\vs\claude\xtjm\HyperBetter\HyperLight_soft_glass_global_20260911_115933.json' -Raw | ConvertFrom-Json
$glassProfile.appName
$glassProfile.module
$glassProfile.items.Count
$glassKeyFile = Join-Path $glassCaseRoot 'profile-keys.txt'
$glassProfile.items | ForEach-Object { $_.key } | Set-Content -LiteralPath $glassKeyFile -Encoding utf8
rg -F -n -f $glassKeyFile (Join-Path $glassCaseRoot 'reference\jadx\sources') (Join-Path $glassCaseRoot 'reference\apktool\smali')
```

预期为 48 个键、零命中；本次没有定位到这些键的直接消费者，而实际定位的配置采用另一种索引格式。不能通过猜测配置值补全其转换语义，也不能只凭字面量搜索排除所有动态构造的读取方式。

### E：复查固定日志与日期 RED

```powershell
$glassCaseRoot = 'E:\vs\claude\xtjm\analysis\hyperbetter-20261025'
rg -n 'header=2|header=0|nativeGlass=true|software=true|date_visibility_hook_unavailable' `
  (Join-Path $glassCaseRoot 'crash-evidence\v0.5.11-runtime-evidence.txt')
Get-Content -LiteralPath (Join-Path $glassCaseRoot 'crash-evidence\triage-summary.json')
Get-Content -LiteralPath (Join-Path $glassCaseRoot 'header-split-red.log') -Tail 40
```

原始 LSPosed ZIP 为 `F:\qq\MobileFile\LSPosed_20261025_174748.zip`，13,626,760 字节，SHA-256 为 `b45694d8e8e4fb38f8f3550b10d2b21f7ad77e63f4eff553805ff29b8a36b34f`。历史归档缺少的现场信息不能用重新运行本地测试补成真机证据。

### F：核对目标设备连接状态

```powershell
& 'C:\Users\95300\AppData\Local\Android\Sdk\platform-tools\adb.exe' devices -l
```

本轮结果只有表头，已保存为 E-14；没有进行目标设备安装、运行时 Hook 或渲染测试。

## 10. 时间线与验证边界

| 时间（Asia/Shanghai） | 已发生事项 | 证据 |
| --- | --- | --- |
| 2026-10-25 17:40:38.238 | 归档首次记录 FusionStatusBar 0.5.11/225 | E-11、E-13 |
| 17:46:26–32 | 主/插件 loader 分别记录头部与布局能力 | E-11 |
| 17:46:45–46 | 组件与普通磁贴的材质分支差别落盘 | E-11 |
| 17:47:13.059 | 关闭全局边框后仍报告日期能力降级 | E-11 |
| 2026-10-25，本次静态分析 | 核对 APK/JSON、解包、smali 交叉检查与日期 RED | E-01 至 E-12 |
| 2026-10-25，会话补充 | 用户澄清 HyperOS 3、参考未实测及手动重启 | E-15 |

已经完成参考 APK 的静态调用契约、配置边界、归档观察，以及日期能力与旧接口错误提升的本地 RED。后续兼容候选的本地验证见下一节；没有完成目标设备的视觉和稳定性验收。

后续应优先按 HyperOS 3 已确认的宿主合同隔离 0.5.11 中未经设备验证的强制材质实验，保留原始请求与恢复边界；日期能力按实际安装的功能及 loader 归属处理。参考 42 项参数和 AGSL 只能作为研究证据，不是可以直接导入的设备适配方案。目标设备的玻璃外观、真实采样来源、资源开销、LSPosed 回调及稳定性均未在本轮验收，本文不宣称这些问题已经修复。

## 11. 本地安全兼容候选 0.5.12 / 226

当前源码已隔离 0.5.11 新增的强制原生接管，恢复兼容底板路径，并移除把 21 项 bloom 合成为完整高光玻璃的逻辑。旧实现已保留在 case 的 `quarantined-v0.5.11/`，历史签名包及源码归档没有改写。界面选项明确标为“高光玻璃（兼容·随系统）”；该候选没有复刻或验收参考图中的背景折射。

新增 `ControlCenterGlassCapabilities` 只检查 MiGlass、双半径、裁剪、材质类型及采样路由的方法签名，不调用这些原生方法，也不因方法存在而报告完整玻璃可用。诊断日志明确输出 `backend=canvas_compat fullNativeGlass=false`，为目标 ROM 的下一轮核验提供事实。

日期与运营商能力现可由明确的当前核心 SystemUI loader 提供；布局和视觉能力仍只取当前活动插件，不借用历史或无关 loader。四项分离宿主回归验证：两个实际可用场景不再误报，同时保留真正缺失能力时的降级。

本地共 205 项 Debug 定向测试通过，失败、错误和跳过均为 0；Release Lint 为 0 错误、98 条警告，既有高风险策略通过，Release assembly 成功。完整构建日志为 [compatibility-final.log](../../analysis/hyperbetter-20261025/compatibility-final.log)，签名、对齐、最终 APK 哈希和源码身份以候选归档 [build-info.json](../artifacts/releases/v0.5.12/build-info.json) 为准。

没有运行全量 Debug/Release 单测及 Debug Lint，没有连接目标设备安装本候选，没有完成 LSPosed、玻璃视觉或稳定性复测，也没有远程发布。参考 APK、配置及原始日志均未修改，参考代码和提取着色器未导入生产代码。
