# Home scanning text Implementation Plan

**Goal:** 用户补充要求在原“隐藏首页风险提醒”开关开启时，同时隐藏“正在体检…”。

**Architecture:** 复用已有可逆文字可见性处理。顶部过滤值增加宿主 hints_scanning_text；底部只匹配这一资源，补充文字设置、可见性设置与显入动画三个入口。原方法、体检调用和结果文案保留。

**Tech Stack:** Java / libxposed 102 / Android Views / Robolectric / Gradle Release R8。

**Constraints:** 仅 40001380 / 普通 MainContentFrame；沿用 security_center_hide_home_warnings；保留已有工作区修改；无 Debug 构建、设备操作或远程发布。更新七语言说明。此窄范围变更执行相关 Release 回归，不重复此前 1608 项全量测试。

## Scope and evidence

用户于 2026-11-13 在 0.5.26 交付后补充“正在体检也要屏蔽”，授权对应本地功能扩展及安装包。宿主同前一报告，hints_scanning_text=0x7f120e06；MainFragment.K2/f3 分别写顶部和顶部+底部。MainContentFrame.f 对应 status_bar_bottom。底部 setStatusBottomVisible 和 t() 会重新显出，均需在原生执行后应用过滤。底部仅隐藏体检文字，避免隐藏实际结果。

## Steps

- [x] 编写并观察体检文案回归 RED；9 项中顶部过滤缺失导致 1 项预期断言失败，覆盖两种宿主语言、原调用执行、底部动画/显隐、关闭恢复及结果可见。
- [x] 增加一个顶部过滤值、三个底部精确 Hook，更新七语言说明。
- [x] 相关 32 项 Release 测试 GREEN，0 失败/错误/跳过；lint 0 错误/142 警告，高风险规则通过；R8 8.13.19 构建成功；独立审查及静态宿主声明核对通过。未重复全量或实机验证。
- [ ] 0.5.27 / 241 使用正式证书签名、归档 mapping，验证 APK/对齐/签名/源代码快照并交付。

## Review focus

原生体检继续执行；关闭开关可恢复；原生 GONE 保留；底部实际结果文案不隐藏；动画重显后仍保持过滤；宿主语言变化仍按资源匹配。
