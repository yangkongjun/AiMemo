# FloatMemo AI（安卓）

当前源码基线：**V0.2.6，versionCode 8**。原生 Java 17，Android 8.0 及以上。

开发源码及完整文档位于 [floatmemo-ai 分支](https://github.com/yangkongjun/AiMemo/tree/floatmemo-ai)。首次同步保持应用代码不变；V0.3.0 新需求仍待用户审核，尚未实现。

## 从哪里开始

- [安卓工程和使用说明](floatmemo-ai-android/README.md)
- [开发手册](docs/developer/FloatMemo_Developer_Guide_Android_V0_2_6.md)：常见小修改、Android Studio、构建与签名
- [新需求审核稿](docs/planning/FloatMemo_Change_Proposal_V0_3_Review.md)：token 配置、插图、命中位置摘录及搜索预算
- [后续开发交接](docs/PROJECT_HANDOFF.md) 与 [每次交付的 Git 同步约定](docs/GIT_DELIVERY.md)
- [原始需求文档](docs/requirements/FloatMemo_AI_Requirements_V1.docx) 与 [实现状态](floatmemo-ai-android/REQUIREMENT_STATUS.md)
- [V0.2.6 验证记录](floatmemo-ai-android/VALIDATION.md)

## 构建

在 Android Studio 打开 `floatmemo-ai-android`；使用 JDK 17、SDK 35、Build Tools 35.0.0、Gradle 8.9、AGP 8.7.3。首次构建需下载依赖。

```bash
cd floatmemo-ai-android
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Windows 使用 `gradlew.bat`。本次同步没有重新运行 Gradle 或构建 APK；原 V0.2.6 的记录为52项 JVM 测试及 APK 签名、结构检查通过，真机、当前版模拟器、真实模型服务和 Gradle lint 未在开发环境验收。

默认 debug 签名不能保证覆盖手机上的旧版本。覆盖升级需使用原维护私钥，并递增 versionCode；先导出笔记备份，不通过卸载绕过安装错误。私钥及密码由用户私下保管，仓库不含这些文件。

仓库不包含 APK、个人笔记、API Key、签名私钥、SDK 或完整离线构建工具。手工构建脚本及依赖说明见 [android-build-fix](android-build-fix/README.md)。

本项目在此前 FloatMemo 安卓重写版本上继续开发；原 macOS 参考项目为 https://github.com/DevAutoFarm/floatmemo 。原组件仍遵循各自许可，本次同步不重新授予其许可。
