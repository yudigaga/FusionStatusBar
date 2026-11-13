# 构建与发布

[返回项目首页](../README.md) · [文档导航](README.md)

以下命令均在项目根目录执行。


- JDK 21、Android SDK Platform 36 与 Build Tools 36.0.0。项目源码仍输出 Java 17 字节码；Miuix 依赖与 Compose 测试需要 Java 21。
- Gradle Wrapper 固定版本和分发 SHA-256；依赖首次构建需要网络。
- 标准交付流程只构建 Release APK，统一启用 R8 和优化规则。LSPosed 外部入口在 `app/proguard-rules.pro` 精确保留；每次交付需验证 APK 的 R8 标记并归档对应 mapping，供混淆日志还原。
- 设置 `JAVA_HOME`，在 `local.properties` 配置 `sdk.dir`，或设置 `ANDROID_HOME`。
- Android 最低 API 33。API 范围不代表所有厂商 ROM 均已验证兼容。

```powershell
.\gradlew.bat :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease --console=plain
```

完整本地验证与归档（Release、单测、lint、R8映射、版本元数据和源码快照）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\release.ps1 -JavaHome $env:JAVA_HOME -AndroidSdk $env:ANDROID_HOME -GitPath git
```

正式归档要求工作区干净、`HEAD` 与 `-SourceRef` 一致，并使用本机签名凭据。开发过程可以显式加 `-AllowDirty`，脚本会在 `release.json` 中标记 `sourceDirty=true`，同时保存 `source.zip`、`tracked.patch`、文件哈希和构建输入；默认绝不覆盖已存在的版本目录。只验证而不签名时使用 `-VerifyOnly`，CI 使用同一入口，不需要签名凭据。脚本不安装 APK、不连接设备、不重启 SystemUI。

脚本会核验Release的测试、lint、Manifest/Xposed/Hook版本一致性、R8标记和对应映射，并对归档 APK 执行独立的入口、继承关系、API 打包边界及实际改名检查；本地签名归档还会执行zip alignment和证书指纹核验。APK、mapping、报告、源码快照和 `release.json` 放在 `artifacts/releases/v<version>/`。`tools/validate-build.py` 是无Android工具依赖的结构化校验器，Python回归测试位于 `tools/tests/`。

GitHub Actions 的 `Validate Android Release` 工作流运行工具回归、Release测试、lint和无签名构建验证；它只上传验证证据，不发布APK。详见[R8交付约定](2026-11-09_r8-release-builds.md)。

版本唯一来源为 `app/build.gradle` 的 `versionName/versionCode`。`BuildConfig` 和 Xposed `module.prop` 在构建时生成；模板位于 `app/src/main/xposed/module.properties`。

版本号使用 `主版本.中间位.末位`：末位范围为 `0–99`，满 `100` 向中间位进 `1`；中间位范围为 `0–9`，满 `10` 向主版本进 `1`。例如 `0.5.99 → 0.6.0`、`0.9.99 → 1.0.0`。此前的 `0.3.200` 按此规则规范为 `0.5.0`。Android `versionCode` 独立递增，当前值以 `app/build.gradle` 为准。


## 解释验证结果

`release.json` 是最终签名包的大小、哈希和来源记录；`verified-build.json` 对应签名前构建输出，两者的 APK 哈希可以不同。原始源码 ZIP、文件索引和补丁记录实际构建输入，发布文档不应改写这些来源字段。

`-SkipBuild` 复用现有测试、lint 和 APK 输出，不等于本次执行了全量测试。过滤测试也不等于全量测试；每个版本的实际范围见对应更新说明及归档 XML。CI 运行完成后再以该提交的 Actions 结论判断是否通过。

Windows 上如默认 `python` 不可用，可用 `-PythonPath` 指定有效的 Python 3.12 解释器。签名凭据留在本机，不进入源码或发布附件。
