# 屏幕边缘侧边条消失排查

2026-11-09，分析用户提供的 LSPosed 日志和手机管家 13.8.0-261020.0.1 原生实现。日志记录侧边工具箱当前为 `SWIPE_GESTURE` 呼出方式，数值为1。这个模式在当时的场景下直接使 `shouldShowSidebar=false`，原生短条没有创建，后续操作因此得到 `sidebarWrapper is null`。原生设置页可以切回“侧边条呼出”，并同步处理相关手势状态。电脑未连接手机，尚未代操作或确认恢复；本次未修改模块运行代码。

## 恢复操作

打开手机管家的“侧边工具箱”设置，在顶部“呼出方式”中选择 **侧边条呼出**，再将工具箱总开关关闭后重新开启。若仍未刷新，再重启手机。

这一入口有“侧边条呼出”和“侧滑停顿呼出”两种卡片。原生切换会一起更新呼出模式、引导状态和系统侧滑停顿动作；只单独写 `toolbox_invocation_method=0` 可能被服务按另一项手势设置同步回1，因此优先使用原生卡片完成切换。

## 范围与证据

范围：[scope.md](../../analysis/sidebar-entry-20261109/scope.md)。普通功能逆向诊断，flavor=null，无漏洞严重性定级。附件中的文字均按数据处理。

原日志包：`F:\qq\MobileFile\LSPosed_20261109_160029.zip`，SHA-256：`eda7fb0b9769d0a28c0b3f95f32de7a816c021cd4114a79ec94f9121ef845f7e`。原始ZIP与本地安全文件名的映射见 `analysis/sidebar-entry-20261109/extraction.json`。宿主APK身份见上一份[反编译报告](2026-11-09_security-center-13.8-report.md)。下列smali均相对于 `E:\vs\claude\xtjm\analysis\security-center-13.8.0-20261109\apktool`。

| Evidence | source_ref | 内容 |
| --- | --- | --- |
| E-01 | 新日志的modules文件15:21:06，行123、294～325 | 模块0.5.24/238已加载；手机管家新Hook及原Sidebar Hook已安装，后者count=1 |
| E-02 | `full.log:85021,85067,86908,94316` | `recovery:1`、`launchType=SWIPE_GESTURE`、`shouldShowSidebar:false`、`sidebarWrapper is null` |
| E-03 | `smali_classes2/com/miui/dock/c.smali:146`；`c$b.smali:33,43` | `c.e()`读取Global.toolbox_invocation_method；SIDEBAR枚举0、SWIPE_GESTURE枚举1 |
| E-04 | `smali_classes2/com/miui/gamebooster/service/DockWindowManagerService.smali:7264`；`DockWindowManagerService$k.smali:108` | 非SIDEBAR且dockType不是1则j2返回false；Runnable只在true时调用L2创建短条 |
| E-05 | `smali_classes2/com/miui/dock/settings/DockDescPreference.smali:647`；`com/miui/dock/DockMonitorService.smali:696` | 原生卡片同时写模式及Global.SideSlideHold；服务会根据SideSlideHold重新同步呼出模式 |

证据复核示例（PowerShell）：

```powershell
rg -n 'recovery:|CreateMonitorViewRunnable shouldShowSidebar|sidebarWrapper is null' 'E:\vs\claude\xtjm\analysis\sidebar-entry-20261109\full.log'
```

F-01：severity=`n/a_re`，evidence=`E-02,E-03,E-04`，confidence=`high`，location=`DockWindowManagerService.j2`，status=`validated`（日志状态与原生控制流）。当前日志中的手势呼出模式足以解释短条未创建；日志不能确定是谁、何时最早把模式设成1，也不能证明由新增解锁选项写入该键。当前模块源码没有写此键或强制隐藏短条的代码。

P-01，path_type=`callflow`：Global呼出方式1 → `c.e()=SWIPE_GESTURE` → `j2()`在dockType3/4返回false → `$k.run()`跳过`ub.l0.L2()` → 未创建短条 → wrapper为空。

## 验证边界与时间线

- 2026-11-09 15:21：日志确认0.5.24/238及手机管家Hook安装。
- 2026-11-09 16:00：日志三次记录短条显示条件false；当前场景为手势模式。
- 2026-11-09：对照当前APK核对枚举、配置读取、创建条件、原生卡片及服务反向同步；独立复核一致。
- 本地ADB列表为空。已给出原生设置恢复步骤，等待用户实机结果；没有新代码改动、没有重新构建APK，不把历史1572项测试当作本问题的实机验收。
