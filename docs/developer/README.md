# FloatMemo AI 安卓开发文档入口

当前测试源码是本仓库feature/note-tasks-format-reminders分支的 [floatmemo-ai-android工程](../../floatmemo-ai-android)，不必从旧归档重新建立开发基线。版本、功能与边界以 [应用说明](../../floatmemo-ai-android/README.md) 和 [当前交接](../PROJECT_HANDOFF.md) 为准。

- [V0.4.0 Test1开发补充](V0_4_0_Test1_Development.md)：局部格式、待办、提醒、批量与备份兼容性。
- [V0.3.x使用与开发补充](V0_3_0_Usage_And_Development.md)：预算、图文、备份、搜索、流式与卡片入口。
- [当前验证及未测项目](../../floatmemo-ai-android/VALIDATION.md)：区分本机检查与手机验收。
- [未完成想法提醒](../../floatmemo-ai-android/REQUIREMENT_STATUS.md#未完成想法提醒)：保留待做目标，不等于已排期。
- [V0.2.6基础手册HTML](FloatMemo_Developer_Guide_Android_V0_2_6.html) / [Markdown](FloatMemo_Developer_Guide_Android_V0_2_6.md)：保留Android Studio、JDK/SDK、原签名与简单修改练习，作为历史基础教程。
- [手工构建入口](../../android-build-fix/README.md)：已有工具链的Linux构建方式。

历史手册中的固定预算、候选数和版本号属于V0.2.6。生成上限、读取等待、候选数量现在可以在手机设置修改；提示词仍需改代码。示例里的versionCode 9不是当前升级值，新APK必须高于手机已安装版本。

examples/app.build.gradle.local-signing.example和examples/signing.properties.example继续保留作原签名配置参考。将签名配置按需合入当前工程，不要整份替换当前app/build.gradle或照抄旧版本号。私钥和密码使用私下保存的原材料，手机笔记另存完整备份。Windows电脑上的安卓构建教程不等于Windows桌面版工程。
