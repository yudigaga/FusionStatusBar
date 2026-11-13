# 模块多语言 Implementation Plan

> **For agentic workers:** Use subagent-driven-development. Track completion with checkboxes.

**Goal:** 七种界面语言及跟随系统，可在设置中持久切换。

**Architecture:** 标准Android资源 + API33原生LocaleManager + 现有Miuix选择器；按页面分工资源化，按语言文件分工翻译。

**Tech Stack:** Java/Kotlin、Android SDK36/min33、Miuix/Compose、JUnit/Robolectric。

**Spec:** ../specs/2026-11-09_app-languages-design.md

## Constraints and review focus

- 只新增语言与本地化，不改变协议、配置键或已保存用户内容。
- 原生LocaleManager只针对模块包，不修改宿主进程全局Locale。
- 共享组件必须正确解析模块资源，动态插值保持参数完整。
- 语言重建等待写入完成，页签/草稿保留，失败回滚。
- 保留旧工作区；无并行Gradle，无远端发布。
- 用户追加要求：以后构建采用R8，只交付Release；buildTypes启用minify与优化规则，标准构建/归档/CI仅执行Release，按外部入口精确保留并匹配R8 marker与mapping。

## Tasks

- [x] 原生语言API、失败/保存门闩、旋转事务恢复均先红后绿，声明七语言；连续加载期重建和原始草稿/发布目标分别保留。
- [x] 模块UI硬编码资源化；共享组件按包解析资源；中文历法、协议、用户内容及旧模型兼容API分类保留。
- [x] 六种译文各850条，XML、完整键集合、参数、百分号/换行和数组引用静态检查通过。
- [x] 97项定向测试通过，含英文选择/配置与草稿重建、俄文320dp/1.3倍字号无裁字、日文界面、保存失败与连续重建；28项Python通过。
- [x] Release1598项、lint 0错误/145警告、R8 8.13.19构建通过；0.5.25/239已由原证书v3签名归档，16KiB对齐通过，根目录交付APK同哈希。
- [x] 报告与release.json记录测试、哈希/证书/映射及未实机边界；本完成状态在签名归档后更新，不改变构建源码。

## Validation ledger

- Release 208组/1598项，0失败/错误/跳过；lint 0错误/145警告，高风险政策通过；Python45项通过。
- R8 8.13.19实物marker/mapping及5类8方法契约核验通过，版本0.5.25/239；标准入口、validator及CI只处理Release。
- 97项语言定向通过后，另验证原有普通后台重建回总览的行为；语言/配置重建才保留当前页，不扩大原有恢复语义。
- 850条/语言，6套译文完整；未实机、未远端发布。最终签名归档以release.json为准。
