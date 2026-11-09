# 手机管家 13.8 杂项扩展设计

目标是将用户列出的 26 项功能作为独立开关接入“杂项”，默认关闭，并适配工作区 APK `手机管家_13.8.0-261020.0.1.apk`。样本 SHA-256 为 `e53a09e38772327e059f74cdaf02e8b3674f9f001e3eea29f10f77fda25448c6`，包名 `com.miui.securitycenter`，构建号 `40001380`。既有充电图标、截图剪贴板和链式启动配置保持独立。

## 用户可见项目

| 枚举 | 项目 | 限定 |
| --- | --- | --- |
| SMART_PRIVACY | 智能防窥 | V2 设置入口，保留原权限与硬件服务 |
| BATTERY_DETAILS | 电池详细信息 | 真实可读值，未知显示 -- |
| SCORE_666 | 锁定 666 分 | 只改分数显示，不改真实扫描结果 |
| SKIP_TEN_SECONDS | 跳过十秒 | 仅跳等待，保留确认与权限动作 |
| GAME_THREE_SECOND_LAUNCH | 王者荣耀启动加速 | 原生路径，不承诺实测耗时 |
| GAME_FILL_DISPLAY | 游戏工具箱填充展示 | 原文注明部分游戏可能变窄 |
| VIDEO_MORE | 视频工具箱更多功能 | 效果取决于设备 |
| VIDEO_TEMPERATURE_LIMIT | 视频工具箱温度限制 | 只改工具箱效果禁用，不改全局温控 |
| MOTION_SICKNESS | 晕车缓解与圆点时机 | 保留真实传感器要求 |
| CINEMA_MODE | 影院模式 | 缺少杜比支持可能导致宿主崩溃，默认关闭 |
| SMART_CHARGING | 智能充电 | 在手机管家充电功能中设置 |
| BYPASS_CHARGING_PLUS | 旁路充电 Plus | 入口解锁与硬件实效分开 |
| SMART_REFRESH_RATE | 智能刷新率 | 宿主支持判断 |
| IMAGE_ENHANCEMENT | 画质提升 | 宿主支持判断 |
| CHARGE_FEATURES | 充电功能 | 总入口，不替代底层硬件 |
| AIM_ASSIST | 准星辅助 | 手机管家工具箱入口 |
| GYROSCOPE_ACCELERATION | 陀螺仪加速 | 仍需游戏自身开启陀螺仪 |
| COOLING_FAN_UI | 散热风扇 | 仅 UI |
| GAME_SOUND_UI | 游戏音效 | 仅 UI |
| INDEPENDENT_DISPLAY_UI | 独显增强 | 仅 UI |
| SUPER_RESOLUTION | 超级分辨率 | 效果自行验证 |
| FRAME_INTERPOLATION | 智能插帧 | 保留宿主与超分的互斥逻辑 |
| GAME_HDR | 游戏 HDR | 不伪造面板能力 |
| CAR_CRASH_DETECTION | 车祸检测 | 保留真实专用传感器，不自动同意或启用服务 |
| SKIP_CHARGE_DIALOGS | 去除智能/极速充电弹窗 | 沿原用户开启动作，不处理高温安全警告 |
| SCREEN_ON_TIME | 参考亮屏时间 | 真实 BatteryStats；分钟显示粒度 1～60，默认 1 |

## 接入方式

沿用 `MiscConfig` 和原子 DP 存储。新增 `SecurityCenterFeature` 枚举、`isEnabled(feature)` / `withSecurityFeature(feature, boolean)`；`screenOnTimePrecisionMinutes` 仅控制向下取整显示粒度，不能叫测量准确率。原 schema 1 缺少新字段时全部关闭，保留同 schema 未知字段。

手机管家进程在 `onPackageLoaded` 按 ApplicationInfo 的实际构建号校验适配并读宿主 DP 缓存；在 `onPackageReady` 使用实际宿主 ClassLoader 安装精确类/方法/返回类型匹配的 Hook。Application attach 后、原 onCreate 前先同步读取一次受限 Provider，再建立签名广播与后台刷新；失败或畸形响应保留缓存。宿主多进程的缓存读写均持相同独立文件锁。读取授权只增加手机管家，写授权不扩大。未知版本不安装这批 Hook。

公共 `SecurityCenterHookSupport` 负责反射签名校验、去优化、保护模式和开关判断；隐私、电池、游戏视频分别独立实现。共享门槛用明确的开关 OR，禁止全局替换 FeatureParser 或所有机型/温度属性。宿主支持门槛和实际参数/服务/驱动能力分开验证。

## 验证范围

先记录当前 APK 资源→调用→门槛证据，再做红绿测试；包括开关默认值/独立保存/格式与类型边界、版本匹配、关闭走原链、多个开关共享门槛、真实传感器、时间单位与未知数据、倒计时确认、用户动作保留以及 UI 可访问性。最后运行完整单测、lint、装配及签名归档。未连接设备时不声称真实 LSPosed 回调或硬件效果已验收；不远端发布。
