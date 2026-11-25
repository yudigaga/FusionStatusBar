# 模块日志候选

入口：设置 → 模块维护 → 模块日志。基线为正式 `v0.5.36`（250），本地候选为 0.5.37（251）。

- 默认开启。模块应用保存后立即使用新状态；注入进程收到配置观察通知、读取成功后切换，无需日常重启。漏通知时在后台定时补读，正常最多约一分钟（以 Provider 可用为前提）。
- 注入进程冷启动时，在确认设置前不写日志。首次确认开启时最多补发 48 条暂存的启动日志，确认关闭则丢弃。关闭期间不补发、不删除历史，不影响导出。临时读不到设置时保持原状态。
- 开启后保留原级别、限频和去重。开关只门控 Fusion 主动写入的 logcat 和 LSPosed 调用，不控制 LSPosed 框架、系统、其他模块和由这些组件独立捕获的异常（包括进程崩溃、注入失败和保护模式下的回调异常）。
- 配置复用应用直启 Provider。跨进程观察和直启启动依赖目标设备上的 Provider 可达性；多用户、锁屏启动和各 ROM Context 捕获有待真机核验。

## 本地复测

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:testReleaseUnitTest --tests '*ModuleLogTest' --tests '*MiscSettingsUiTest' --offline --max-workers=1
.\gradlew.bat :app:testReleaseUnitTest :app:assembleRelease --continue --offline --max-workers=1
python -m unittest discover -s tools/tests -p test_module_log_sinks.py -v
```

## 真机复测

1. 使用同证书候选覆盖安装。确认默认开启，在 SystemUI、桌面、手机管家和模块设置中各触发一轮已知诊断。检查 LSPosed 中归属 Fusion 的模块条目，以及 `adb logcat -v threadtime -s FusionStatusBar:V FusionControlGrid:V`。
2. 关闭开关，记下时间，等待各进程刷新。重复操作并重启一次设备，分别确认已有进程和冷启动不新增 Fusion 主动日志；保留旧日志，不运行清空命令。核对系统或其他模块记录依然存在。
3. 重新开启，重复同类操作，检查新输出恢复且不补发关闭期间的日志。单次诊断仍受原有去重影响，可重新进入对应场景或重启宿主验证启动输出。确认导出功能仍可读取历史。
