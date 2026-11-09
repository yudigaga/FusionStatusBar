# Release 与 R8 构建约定

2026-11-09，按用户要求，后续标准流程只构建和交付Release APK，统一使用R8代码压缩、优化和混淆。该要求落实在 `app/build.gradle`、`app/proguard-rules.pro`、`tools/release.ps1`、`tools/validate-build.py` 和CI工作流，作为后续构建的默认行为。

## 入口与保留规则

所有构建类型共享R8配置；标准入口只执行 `testReleaseUnitTest`、`lintRelease`、`assembleRelease`。LSPosed从 `META-INF/xposed/java_init.list` 按名称加载 `FusionModule`，所以保留该类原名、public无参构造和三个框架回调。配置、备份和跨进程交换使用明确的Bundle/JSON键，未按模块自身字段名反射，无需整包或整配置类保留。

宿主ROM方法和字段名是对外反射契约，不属于本模块的R8输入。Compose依赖的consumer规则交给AGP合并，Manifest组件和XML自定义视图沿用Android生成的入口规则；映射和实际DEX还会在成品阶段验证。

## 交付门槛

校验器默认只检查Release，必须同时满足：

- APK真实DEX含R8 marker，不能用D8产物或仅有源码开关代替。
- `java_init.list` 与源码一致，入口存在于DEX的class_defs。
- `mapping.txt` 的compiler、编译器版本和映射ID与APK marker一致，拒绝旧映射、缺失映射和不匹配产物。
- 单元测试、lint、APK/Manifest/Xposed版本身份以及原有签名和对齐检查保持有效。

`release.json` 记录 `minified=true`、engine、R8版本、mappingId及映射哈希；归档包含 `mapping/release/mapping.txt`，以及构建产生的configuration、seeds、usage等文件。混淆后的异常堆栈需使用同一APK对应的mapping还原。

## 本地验证

新增校验先以无行为骨架确认反例失败，再实现：DEX边界、MUTF-8、多DEX、R8标记、映射版本/ID、入口和Release-only合同。Python工具测试共45项通过；旧非R8的0.5.24 Release APK已被实际拒绝。0.5.25由R8 8.13.19生成，实际DEX入口、public无参构造、3个模块回调、Manifest组件及XML视图构造核验通过；Release 1598项单测通过，lint 0错误/145警告。最终签名、对齐和哈希以[本版归档清单](../artifacts/releases/v0.5.25/release.json)为准。

目前没有连接手机；本地静态DEX和映射检查不能代替R8后真实LSPosed注入、ROM回调及应用运行验收。该约定不自动发布仓库或Release。
