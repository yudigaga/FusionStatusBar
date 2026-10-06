# 设备验收补充记录（0.3.174）

日期：2026-10-06。此记录补充 `artifacts/releases-final/v0.3.174/release.json` 生成后的设备观察；它不改写该归档生成时记录的 `not_performed` 状态。

## 设备与安装

- 设备：Xiaomi Pad 5 Pro，型号 `M2105K81AC`，serial `b7015e9b`。
- 系统：Android 16 / API 36，HyperOS `OS3.0.303.0.WNXCNXM`，构建指纹 `Xiaomi/elish/elish:16/BP2A.250605.031.A3/OS3.0.303.0.WNXCNXM:user/release-keys`。
- 当前安装包：`io.github.yudigaga.fusionstatusbar`，版本 `0.3.174 / 188`；安装时间 `2026-10-05 19:34:22`。
- 设备上的 `base.apk` SHA-256 为 `bc2f2166ab69a29477e7ecbfe2de9e998edb119a872795357581c64a353d5440`，与本地签名设备归档清单一致；本次没有重复覆盖安装。
- 设备截图：[激活概览](../artifacts/validation/v0.3.174-device/activation-overview.png)，SHA-256：`715aea3a91f35925b7cecaf9fca5ea3188106b273433100b8e3c2d7e1fcd0d68`。

## 注入观察

- 2026-10-06 当前应用概览显示“已激活 / 系统界面已连接 · 22:09”，并显示版本 `0.3.174 (188)`。
- 同时读取到当前 SystemUI PID 为 `5330`，进程已运行约 `3:32`；设备时间约为 `01:41`，与该进程从 `22:09` 启动相符。应用连接回执和当前 PID 对应同一 SystemUI 进程。
- 该证据确认本次开机的 SystemUI 已连接模块 Provider，且进程自 22:09 起未被替换。精确 `module_version/module_code` 字段及逐功能回执没有通过公开设备接口读取，因此不据此声称每项 Hook 均已应用。
- 2026-10-05 归档的 LSPosed 截图显示总开关、`com.android.systemui` 和 `miui.systemui.plugin` 作用域已启用。这是历史截图，不作为 2026-10-06 作用域当前值的独立证明。

## 尚未验收

- 尚未逐项验证控制中心网格/材质、状态栏布局、通知表面、电源菜单和音量菜单的实际 ROM 行为。
- 尚未进行 GPU 最终合成检查、模块开关前后性能对照、长时间功耗或内存观察。
- 当前源码工作区为 dirty；本记录是设备补充证据，不构成干净 tag 的正式发布核验。
