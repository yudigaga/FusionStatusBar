# 手机管家 13.8.0 反编译与杂项功能实施报告

分析日期：2026-11-09。目标是工作区这一份 `手机管家_13.8.0-261020.0.1.apk`，不是对所有手机管家版本的通用适配。模块增加26项独立选项，默认全部关闭，保留原有充电图标、截图剪贴板及其他模块功能。**本报告确认静态入口、方法签名和实施边界；没有安装手机或验证真实 LSPosed 回调、驱动、游戏/视频效果、充电行为及紧急检测结果。**

其中“解锁”主要改变本地入口、支持判断或名单，不会增加设备没有的硬件。散热风扇、游戏音效和独显增强按UI功能标明；超分、插帧、HDR、影院和充电功能仍由原生服务/硬件决定效果。“三秒进游戏”不构成耗时承诺，填充展示也不保证扩大游戏视野。

## 样本与反编译结果

| 项目 | 当前样本 |
| --- | --- |
| 文件 | `E:\vs\claude\xtjm\手机管家_13.8.0-261020.0.1.apk` |
| 大小 | 80,524,009 字节 |
| SHA-256 | `e53a09e38772327e059f74cdaf02e8b3674f9f001e3eea29f10f77fda25448c6` |
| 包名 / 版本 | `com.miui.securitycenter` / `13.8.0-261020.0.1`，versionCode `40001380` |
| Android 条件 | minSdk 26、targetSdk 36；Manifest sharedUserId=`android.uid.system` |
| Application | `com.miui.securitycenter.Application` |
| DEX / smali | 4个DEX；33,048份smali |
| 全量 Java 输出 | 10,983份Java文件；JADX全量结束报告35处错误 |
| native 库 | 30个so，覆盖arm64-v8a与armeabi-v7a；未将native效果作为已验证结论 |
| 工具 | apktool 3.0.2、JADX 1.5.6；Java与具体命令以分析目录日志为准 |

包身份与SDK条件来自当前样本的 `apktool.yml` 和解码后的 `AndroidManifest.xml`，见 `apk-identity.json`；中文路径下aapt未能完成读取，不能将其列为成功验证。以上是宿主APK的SDK条件，不代表模块安装下限；当前FusionStatusBar源码为minSdk 33、targetSdk 35、compileSdk 36，版本为 `0.5.24 / 238`。正式APK路径、哈希、签名与对齐结果以本版构建归档中的 `release.json` 为准，本文不预填尚未完成的签名产物。

分析产物位于 `E:\vs\claude\xtjm\analysis\security-center-13.8.0-20261109`。全量JADX有错误，所以关键路径使用当前smali核对；缺失的 `q9.a`、`lb.v` 另用simple模式恢复，不能只靠Java别名或旧版本映射判断。`r6.b.a/i` 等短实现是当前样本事实；未经官方原包/证书对比，不据此判断APK来源。

## 范围、作用域与生效方式

授权来自用户本次明确要求的本地反编译和模块接入。范围为该APK的Manifest、资源、Java/smali及FusionStatusBar中的可选行为；没有修改原APK、自动同意隐私条款、伪造传感器、修改腾讯游戏进程或解除系统驱动温控。flavor=`null`，普通逆向，不作漏洞定性。详细范围：[隐私域](../../analysis/security-center-13.8.0-20261109/privacy-scope.md)、[电池域](../../analysis/security-center-13.8.0-20261109/battery-scope.md)、[游戏域](../../analysis/security-center-13.8.0-20261109/game-scope.md)。这些目录位于完整工作区，单独分发报告时应一并附带；下方核心表与样本哈希自包含。

1. 在LSPosed中确认模块已启用，并勾选“手机管家”作用域 `com.miui.securitycenter`。本批功能不靠新增腾讯游戏作用域工作。
2. 安装模块新版后重启手机管家相关进程或整机，使新Hook加载；单独重启SystemUI不等于重启手机管家。模块原有系统侧截图保护仍按其既有系统作用域与整机重启要求处理。
3. 在模块“杂项”的分类中按需开启。宿主会缓存菜单、能力字段及组件状态；改动后需重建相应页面/工具箱，必要时重启宿主进程或手机，不能承诺所有已打开界面即时更新。
4. 不支持版本不会套用混淆映射：运行时先核对手机管家versionCode=`40001380`，未知版本保留原行为并记录未适配。
5. 配置“已保存”和Hook“已安装”是不同状态；应核对模块日志的安装数量与实际页面行为，不以界面开关代替运行回调证据。

## 26项选项与精确接入点

配置key由 `SecurityCenterFeature.key()` 固定生成，均为独立布尔项。`Vision`=`com.miui.gamebooster.utils.GameBoxVisionEnhanceUtils`；`Display`=`com.miui.gamebooster.ui.performancetab.a`。表中签名 `Z/I/V` 分别是boolean/int/void；精确方法声明、物理行号和源文件哈希见对应证据组。

| 编号 / 功能 | 配置key | 宿主入口 | 精确门槛或消费点 | UI、硬件与恢复边界 | 证据 |
| --- | --- | --- | --- | --- | --- |
| F-01 智能防窥 | `security_center_smart_privacy` | 手机管家原生隐私/设置中的智能防窥 V2 | `t3.g.k()Z`；PrivacyFragment 两入口和 PeepProtectionMainActivity 的退出检查 | 只解除软件支持门槛；不绕原 Activity/Provider 权限、不自动同意隐私。屏幕像素防窥、AON 与渲染服务仍需设备提供。 | E-P |
| F-02 电池详细信息 | `security_center_battery_details` | 充电保护页 `preference_key_category_battery_info` | `ChargeProtectFragment.onCreatePreferences/onResume`；只保留6个原生信息 key，另添设计/满充容量参考行 | 读取真实温度、次数、周期、日期和容量；未知显示 `--`，不伪造健康度，不拦截安全警告行删除。 | E-B |
| F-03 锁定666分 | `security_center_score_666` | 手机管家扫描主页及扫描结果的 ScoreTextView | `ScoreTextView.setScore(I)V` 标记分数显示；`d(CharSequence)V` 只换文字；`setNumber(I)V` 清标记 | 独立分数字段/getTextScore、ScoreManager、扫描分差及统计保持原值；数字模式与动画不被当成分数修改，关闭恢复原文字。 | E-P |
| F-04 跳过十秒 | `security_center_skip_ten_seconds` | 敏感权限确认页 | `InterceptPermissionFragment.g0()I` 返回0；父 Handler 保留 | 只去除10秒初始等待；仍需原勾选、按钮点击及权限确认，不自动授权。 | E-P |
| F-05 王者荣耀启动加速（用户称“三秒”） | `security_center_game_three_second_launch` | 游戏加速对 `com.tencent.tmgp.sgame` 的冷启动 | `com.miui.gamebooster.service.n1.u0/v0(Z)`；`F()` 当前包名；宿主 `f2.c/e` 读写 `debug.game.video.boot` | 仅目标游戏原生启动窗口；保留原结束/切出清理。只撤销模块确实写入且仍拥有的值，不接管原来已true的状态；不保证3秒进入，不改腾讯进程。 | E-G |
| F-06 游戏工具箱填充展示 | `security_center_game_fill_display` | 游戏工具箱原生尺寸/填充菜单 `q9.a.h` | `t9.l.m(String)Z`、`t9.l.o()Z` | 原生 MiuiSizeCompat 与失败处理保留；宿主文案说明部分游戏横向视野可能变窄，不能宣称扩大视野。 | E-G |
| F-07 视频工具箱更多功能 | `security_center_video_more` | 视频工具箱菜单与更多效果 | `lb.v.f/g()Z`；`lb.x.A/H()Z`、`x/z(String)Z`；与画质提升共享 `I/w`、`lb.g.l/p` | 只开放对应本地门槛；setVpp/setFrc 等原生服务调用、效果冲突和失败路径保留，实际效果待设备验证。 | E-G |
| F-08 解除视频工具箱温度限制 | `security_center_video_temperature_limit` | 工具箱视频效果 hot 禁用层 | `hb.m.o()Z` 返回false；`hb.m.t()V` 仅在此选项开启时拦截 | 真实温度 `q()F` 不改；只绕工具箱该层停效果，不写thermal节点、不关闭驱动/硬件温控。关闭后若仍hot，重调原t恢复此层保护。按用户提供署名保留思路来源 @东边的何某。 | E-G |
| F-09 全机型晕车缓解与圆点提示时机 | `security_center_motion_sickness` | 晕车缓解设置页 `remind_time_setting` | `r6.b.h/i(Context)Z`；`g(Context)Z` 读真实启用键；`a(Context)I` 读真实提示时机 | 去机型/地区/MTK软件门槛，但保留真实线性加速度(10)与陀螺仪(4)。提示时机0=车辆运动/1=始终；驾驶时机另依赖厂商传感器33171091，不承诺全机型实际可用。 | E-P |
| F-10 影院模式 | `security_center_cinema_mode` | 视频工具箱影院入口 | `lb.q.g()Z`、`lb.q.f(String)Z`，并请求视频聚合支持门槛 | 默认关闭。需杜比全景声及原生音视频服务；缺支持的机型可能无效或导致手机管家FC，未实机验证。 | E-G |
| F-11 智能充电 | `security_center_smart_charging` | 手机管家充电功能页及游戏工具箱智能充电入口 | `xh.u.r()Z`、共享 `xh.u.E()Z`；游戏侧 `com.miui.gamebooster.utils.o0.X()Z` | 当前13.8充电页原生 key `pref_key_charge_protection_side_road_mode` 的标题已是智能充电；需要用户在宿主页开启，不伪造 IMiCharge 成功。 | E-B |
| F-12 旁路充电 Plus | `security_center_bypass_charging_plus` | 游戏工具箱的旁路/智能充电入口 | `com.miui.gamebooster.utils.o0.Y()Z`；与智能充电共享 `xh.u.r/E` | 显示对应入口/标题，仍走宿主设置和服务链；仅改状态键不能确认旁路成功，实际效果需设备自测。 | E-B |
| F-13 智能刷新率 | `security_center_smart_refresh_rate` | 视频工具箱显示效果 | `com.miui.gamebooster.utils.o0.B()Z`，并请求视频聚合支持门槛 | 开放 `support_video_dfps` 对应能力判断；实际刷新率由显示服务决定，保留原生弹幕等限制。 | E-G |
| F-14 画质提升 | `security_center_image_enhancement` | 视频工具箱VPP/显示效果 | `lb.x.I()Z`、`lb.x.w(String)Z`、`lb.g.l/p()Z`，与VIDEO_MORE取OR | 只改本地支持/应用资格，不伪造原生效果已开启状态或硬件结果。 | E-G |
| F-15 充电功能 | `security_center_charge_features` | 手机管家电池页的充电功能总入口/原生选项 | 共享 `xh.u.E()Z`；`ChargeFeatureFragment.L0()` 的实例 `p/q` 支持字段 | 用于显示原生充电设置，实际有线/无线能力仍依赖硬件。模块改过的实例能力字段需保存原值，关闭时恢复；旧菜单缓存需重建。 | E-B |
| F-16 准星辅助 | `security_center_aim_assist` | 游戏工具箱准星菜单 | `com.miui.gamebooster.utils.o0.h()Z`、`b9.a.l(String)Z` | 只解锁入口与名单；真实 gun_sight、折叠状态和非标准尺寸等后续限制保留。 | E-G |
| F-17 陀螺仪加速 | `security_center_gyroscope_acceleration` | 游戏工具箱/游戏设置的陀螺仪加速 | `com.miui.gamebooster.utils.b2.m()Z`、`b2.i(String)Z` | 只放开能力/名单；仍需游戏自身启用陀螺仪，实际传感器与底层接口不伪造。 | E-G |
| F-18 散热风扇（UI） | `security_center_cooling_fan_ui` | 游戏工具箱风扇入口 | `h9.e.n()Z`，由 `q9.a.h`、`ji.p.b0` 消费 | 仅显示界面，不增加风扇硬件，不强制转速或伪造运行状态。 | E-G |
| F-19 游戏音效（UI） | `security_center_game_sound_ui` | 游戏工具箱音效入口 | `s9.a.c()Z`：AudioGameEffectSupported 的支持判断 | 仅显示界面，用户点击仍走原音效调用；不全局强开音效，不保证设备效果。 | E-G |
| F-20 独显增强（UI） | `security_center_independent_display_ui` | 游戏工具箱/性能面板的独显增强 | `Vision.Y()Z`，并请求共享显示门槛 | 只解锁界面与支持判断；不捏造独显硬件、Joyose服务或效果状态。 | E-G |
| F-21 超级分辨率 | `security_center_super_resolution` | 游戏工具箱及新旧性能面板 | `Vision.e0/f0/S()Z`、`Display.g()Z`，并请求共享显示门槛 | 原生 setPictureEnhancement/setFrameInsertingOrSuperResolution 继续决定实际成功，效果需自测。 | E-G |
| F-22 智能插帧 | `security_center_frame_interpolation` | 游戏工具箱及新旧性能面板 | `Vision.Q/c0/T()Z`、`Display.f()Z`，并请求共享显示门槛 | 不强制 Vision.g0/Display.d 的同时生效能力；保留超分与插帧的宿主互斥，效果需自测。 | E-G |
| F-23 游戏HDR | `security_center_game_hdr` | 游戏工具箱/性能面板HDR | `com.miui.gamebooster.utils.o0.n()Z`、`utils.t.b/c()Z`、`Display.l()Z` | 只改本地资格，真实面板、SurfaceFlinger、Joyose能力与原生当前状态保持。 | E-G |
| F-24 车祸检测 | `security_center_car_crash_detection` | 手机管家安全页的车祸检测入口 | `CarAccidentUtils.isSupportCarAccidentDetect(Context)Z` | 可移除地区/云入口门槛，但保留真实专用传感器33171623；不自动开启服务、同意隐私、伪造事件或宣称已验证报警能力。 | E-P |
| F-25 去除智能/极速充电弹窗 | `security_center_skip_charge_dialogs` | 智能充电确认、极速无线提示与已开启后的信息页 | `e4.R(View)`→原 `S(View,DialogInterface,int)`；NightChargeDialogActivity 原onCreate后finish；SuperWireless 原negative按钮 | 继续原开启动作/通知清理。极速无线保持开启是 negative→vh.a.d(7)，positive反而关闭；不处理高温、潮湿、低温充电安全告警。 | E-B |
| F-26 恢复参考亮屏时间 | `security_center_screen_on_time` | 充电保护页电池信息区的新增参考行 | 当前宿主 BatteryStatsUtils.getBatteryStats → getScreenOnTime(elapsedRealtime×1000, STATS_SINCE_CHARGED) | 真实微秒统计转分钟；显示自上次充满的数据，缺失显示 --。精度参数只是1～60分钟显示粒度，不是提高统计准确率；不使用UsageStats粗合计。 | E-B |

亮屏时间的独立整数key为 `security_center_screen_on_time_precision_minutes`，缺省1、范围1～60，单位是分钟。该参数仅改变显示粒度，不改变BatteryStats数据。

## 共享门槛与配置生命周期

- 视频聚合门槛 `lb.v.f/g` 由“更多功能、影院、智能刷新率、画质提升”取OR；VPP/显示门槛 `lb.x.I/w`、`lb.g.l/p` 由“更多功能、画质提升”取OR。只关闭其中一个，不应误关另一项仍请求的共享门槛。
- 游戏显示公共门槛 `o0.K`、`Vision.l0/O/V`、`Display.m` 由“独显UI、超分、插帧、HDR”取OR；不伪造超分与插帧能同时运行。
- 充电入口 `xh.u.E` 由“充电功能、智能充电、旁路Plus”共享；`xh.u.r` 由“智能充电、旁路Plus”共享。二者不能当作彼此完全独立的物理充电模式。
- 模块使用既有Misc schema 1，旧快照缺少新key时默认为关闭，原有选项独立保留。新增只读宿主配置通道，不放宽模块配置的写入调用者权限；发布广播仍要求签名权限，非法类型/不完整必需字段不替换有效快照。
- `SecurityCenterConfigRuntime` 为手机管家单独保存设备保护存储快照，文件为 `files/fusion_statusbar_security_config_v1.json`；缓存读写需使用同一跨进程文件锁（包括AtomicFile恢复与验证读取），并在进程内避免重叠锁异常。宿主初始能力缓存运行前优先获取一次新配置；Provider不可用时保留已有快照，不静默改成默认值。
- 666分仅改渲染：`getTextScore()`读取独立字段并参与扫描分差/统计，所以不改其输入/返回值。弱视图记录保存每帧原生文字，关闭时在主线程恢复；`setNumber`回到原数字模式，不在onDraw反复setText。
- 王者加速只撤销模块写入且仍拥有的boot属性；原来已为true或宿主后续改写的值不应被模块清理覆盖。null包名不应导致旧状态泄漏。视频温度选项关闭后应重查真实hot并恢复工具箱原保护动作。
- 电池统计只在后台读真实数据，UI更新不应持有Activity强引用，旧请求应按代次、当前精度、页面是否仍有效进行校验。充电实例能力字段的恢复、异步过期结果和缓存锁是本次独立审查列出的回归项，不应仅凭辅助格式化测试宣称通过。

## Evidence → Finding → Path

所有F-01～F-26的severity=`n/a_re`、status=`validated`（静态门槛/路径）、confidence=`high`。这里的validated不代表硬件、系统服务或真机最终效果通过。

| Evidence ID | source_ref / content_hash | 可复核方式 | 关联Finding |
| --- | --- | --- | --- |
| E-APK | 原APK；SHA-256见样本表 | `Get-FileHash -Algorithm SHA256 -LiteralPath 'E:\vs\claude\xtjm\手机管家_13.8.0-261020.0.1.apk'` | 全部 |
| E-P | `privacy-evidence.json`，19份smali/XML路径及SHA-256；详见privacy-misc-findings.md | 读取索引中的原smali，按声明和下述调用链交叉检查 | F-01、03、04、09、24 |
| E-B | `battery-evidence.json`，12份源文件及SHA-256；详见battery-charge-findings.md | 对照原Preference key、Handler移除分支、真实getter和两个Dialog的原按钮动作 | F-02、11、12、15、25、26 |
| E-G | `game-video-contracts.json`，46条类/方法签名、声明行号及SHA-256；详见game-video-findings.md | 逐项核对当前smali `.method` 和消费点，不用旧版混淆类名代替证据 | F-05～08、10、13～14、16～23 |
| E-JADX | `jadx.log`；以及single-game-q9.log、single-game-lbv.log | 确认全量35处错误和关键单类恢复输出；保留apktool原smali作为兜底 | 涉及缺失Java类的条目 |
| E-MODULE | 主仓 `SecurityCenterFeature`、`MiscConfig/Store`、`SecurityCenterConfigRuntime/Cache`、三个域Hooks和MainActivity | 对照下方源码职责；最终签名构建源码哈希由版本归档保存 | 全部 |
| E-VERIFY | `all-targeted-green.log`、`all-targeted-test-counts.json`、`static-evidence-verification.json` | 82项定向测试的14组结果与静态契约/哈希核对均通过；完整验证另见归档结果 | 实施回归与证据完整性，不代表实机效果 |

Path：

- **P-01 / callflow**：模块Misc选项 → 原子快照/签名广播 → 手机管家Runtime校验版本、加载初始配置及缓存 → 精确方法Hook按当前选项决定原样proceed或覆盖特定门槛。Evidence=E-MODULE；Finding=全部。
- **P-02 / callflow**：PrivacyFragment V2入口 → `t3.g.k` → V2 MainActivity再次检查 → 原权限/用户交互/硬件服务。Evidence=E-P；Finding=F-01。
- **P-03 / callflow**：原扫描/ScoreManager真实计算 → ScoreTextView原生字段 → private d渲染 → 只替换被标记分数视图的文字；数字模式/真实getter不改变。Evidence=E-P；Finding=F-03。
- **P-04 / callflow**：原敏感权限确认g0 → 父Handler倒计时 → 原按钮/勾选 → 用户确认 → 原权限动作。Evidence=E-P；Finding=F-04，不自动授权。
- **P-05 / callflow**：晕车Dropdown → 原System键 → g/a读真实启用/时机 → 原服务动作和真实传感器。车祸另保留专用传感器及原隐私同意流程。Evidence=E-P；Finding=F-09、24。
- **P-06 / callflow**：原王者冷启动请求 → 限定游戏包名 → 写boot属性并读回 → 原结束/切出任务或模块关闭 → 有所有权时恢复。Evidence=E-G；Finding=F-05。
- **P-07 / callflow**：真实温度监测 → 工具箱hot/停效果层 → 选项开启仅绕该层；关闭后恢复原处理。没有修改thermal服务或温度数值。Evidence=E-G；Finding=F-08。
- **P-08 / callflow**：原充电信息分类 → 保留确切信息行/补参考行 → 后台读取真实BatteryStats/电池数据 → 校验有效请求后回写；无数据→`--`。Evidence=E-B；Finding=F-02、26。
- **P-09 / callflow**：智能充电原确认 → 原S回调；极速无线原negative按钮 → `vh.a.d(7)` → 原IMiCharge；原positive的关闭动作不被误选。Evidence=E-B；Finding=F-25。

## 源码职责与复核入口

| 文件 | 职责 |
| --- | --- |
| `app/src/main/java/com/xtjm/fusionstatusbar/SecurityCenterFeature.java` | 26项标题、分类、描述和稳定key |
| `MiscConfig.java`、`MiscConfigStore.java`、`FusionConfigProvider.java` | 布尔项/显示粒度持久化，类型校验，读取授权与既有写入边界 |
| `SecurityCenterConfigRuntime.java`、`SecurityCenterConfigCache.java` | 版本限制、首读、签名更新、配置观察、跨进程缓存与生命周期 |
| `SecurityCenterHookSupport.java` | 严格校验方法参数与返回类型、逐项记录实际安装数量、共享OR门槛 |
| `SecurityCenterPrivacyHooks.java` | V2防窥、真正渲染层666、十秒、晕车与车祸的真实传感器边界 |
| `SecurityCenterGameVideoHooks.java` | 游戏/视频入口、王者启动属性所有权和工具箱温度层恢复 |
| `SecurityCenterBatteryHooks.java` | 充电入口、真实电池/亮屏数据、原生确认动作和页面恢复 |
| `MainActivity.java` | 杂项分组开关与亮屏显示粒度控件 |

宿主原APK不重打包、不替换签名；模块依靠已授权的LSPosed作用域运行。已有 `com.miui.securitycenter` 作用域继续使用，不要求为了这些UI解锁新增任意第三方应用作用域。

## 验证状态与实机验收

截至报告更新，82项定向测试全部通过：14组、0失败、0错误、0跳过，见 `all-targeted-green.log` 与 `all-targeted-test-counts.json`。28项Python工具测试也已通过。静态证据校验通过：游戏域46条方法声明/物理行号/源文件哈希、电池域12份文件、隐私域19份文件，见 `static-evidence-verification.json`。

完整Debug和Release单元测试均已通过：各204组、1572项，0失败、0错误、0跳过，见 `full-debug-test-counts.json`、`full-release-test-counts.json`。两个变体lint均为0错误、101条警告。相对0.5.23新增的1条为 `SecurityCenterBatteryHooks.java` 读取真实BatteryStats的PrivateApi反射警告；已复核失败回退为未知数据 `--`，没有隐藏或忽略该警告，真实设备是否允许读取仍需验证。高风险lint政策未增加豁免。

模块源码版本为 `0.5.24 / 238`。最终签名APK、哈希、证书、对齐及构建来源以[本版归档清单](../artifacts/releases/v0.5.24/release.json)为准，原始构建日志为分析目录的 `full-verification.log`。`adb devices`返回空列表，未连接设备，因此没有手机安装、真实LSPosed回调或任何硬件/服务效果验收。构建和本地签名不会替代这些验收，也没有远端发布。

实机至少核对：

1. 确认手机管家精确版本、模块版本、LSPosed作用域、各域实际安装数量；未知版本保持原行为。
2. 分别测试打开/关闭以及宿主进程重启，确认独立开关与共享OR门槛不会互相清空，原有充电图标/截图剪贴板保持既有行为。
3. 666分的显示、动画、关闭恢复与数字模式；真实扫描风险和getTextScore数据不能被666替代。十秒选项只影响等待，仍需用户确认。
4. 王者启动前已有true、模块写true后原宿主改值、目标包切走/null包名及关闭选项，均不能错误覆盖他人的属性状态。
5. 电池参考值应与可用原生数据一致，缺失显示`--`；修改显示粒度、页面离开/重建与快速开关时，旧异步结果不能写进新页面。
6. 超分/插帧/HDR/音效/风扇/独显/影院/充电的真实结果逐台验证。没有硬件的设备只出现UI不能视作生效，影院缺杜比时注意原生FC风险；不把温度层解锁描述成硬件安全保护被解除。
7. 晕车车辆运动模式和车祸检测分别核对真实传感器/原生服务条件，不能用模拟事件或界面开关当作安全检测验收。

## 时间线

- 2026-11-09：固定APK身份，apktool解资源与4个DEX；全量JADX结束有35处错误。
- 2026-11-09：按隐私、电池、游戏/视频三域追踪当前smali；关键缺失类单独恢复；固定源文件/方法契约证据。
- 2026-11-09：先以无行为骨架验证新增用例能失败，再逐域实现。额外追查getTextScore的数据回流、MTK晕车状态消费、真实微秒统计与原充电按钮动作。
- 2026-11-09：独立审查列出缓存锁/首读、充电字段恢复、异步代次与启动属性所有权边界，修复后的82项定向测试、28项Python工具测试及三域静态证据校验通过；完整Debug/Release各1572项通过，lint均0错误/101警告，未进行设备验收。
