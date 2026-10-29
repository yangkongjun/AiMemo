# FloatMemo AI（安卓）

当前版本：**V0.3.2，versionCode 11**。点击标签或主题后进入独立卡片列表，展示标题、原文预览、标签、主题和时间，便于区分笔记。保留V0.3.1的流式接收和断连诊断。

- [V0.3.2标签与主题卡片列表](docs/releases/V0_3_2.md)
- [V0.3.1流式接收与断连诊断](docs/releases/V0_3_1.md)
- [完整使用说明与构建入口](floatmemo-ai-android/README.md)
- [V0.3.0使用和开发补充](docs/developer/V0_3_0_Usage_And_Development.md)
- [原V0.2.6开发手册](docs/developer/FloatMemo_Developer_Guide_Android_V0_2_6.md)：Android Studio、本地修改和签名基础
- [变更与验证记录](docs/releases/V0_3_0.md)
- [已批准的需求方案](docs/planning/FloatMemo_Change_Proposal_V0_3_Review.md) 与 [需求实现状态](floatmemo-ai-android/REQUIREMENT_STATUS.md)
- [每次交付的Git同步约定](docs/GIT_DELIVERY.md)

源码位于 `floatmemo-ai` 分支。继续使用原包名和签名，先备份后覆盖安装，不卸载。应用数据仍在手机本机，Git同步不包含手机笔记。

验证状态与未测项目见[验证记录](floatmemo-ai-android/VALIDATION.md)。新卡片页仍需在手机检查显示、滚动和返回；既有合成HTTP测试不代表真实模型服务或断连根因验收。

仓库不包含API Key、签名私钥、密码、个人笔记、APK或SDK。安装包通过本次交付单独提供，不自动发布GitHub Release或合并main。

原macOS参考项目：https://github.com/DevAutoFarm/floatmemo 。原组件遵循各自许可，本次不重新授予其许可。
