# V0.4.0 测试分支开发指南

适用于 `feature/note-tasks-format-reminders` 的 Test3。沿用 Test1 文档的原链接。安装步骤见 [安卓使用说明](../../floatmemo-ai-android/README.md)，测试范围见 [验证记录](../../floatmemo-ai-android/VALIDATION.md)。

## 常见修改入口

Java 文件在 `floatmemo-ai-android/app/src/main/java/io/github/floatmemo/android/`。

| 修改内容 | 代码入口 |
| --- | --- |
| 选字工具栏、三级字号和调色板 | FormatToolbar 切换同一容器内的普通按钮与格式按钮。字号预设为 16、20、24 sp，±1 由 TextFormat.stepSize 逐段调整。整张字号仍由 Note.fontSize 和 NoteEditorActivity.changeFont 管理。 |
| 格式渲染与列表 | TextFormat、TextStyleSpans、Markdown、BodyEditing、ListEditing。区间使用原文 UTF-16 偏移，保留表情字符边界。NotePreviewRenderer 以原文位置和 revision 校验清单写入。 |
| 标签撤回 | AiDetailActivity 调用 NoteRepository.editTags/undoTags。标签记录通过 edits.beforeData 中的 editScope 标识，撤回恢复标签和人工锁定状态。标签保存和撤回与历史记录在同一事务内提交。 |
| 待办、提醒和回收站 | TaskUi、NoteTasks、ReminderScheduler、ReminderReceiver、NoteManageActivity、NoteRepository.trashMany/purgeMany。提前时长由 TaskUi 的 LEAD_MINUTES 和 LEAD_LABELS 定义。 |
| 备份和导入 | BackupArchive.SCHEMA_VERSION、NoteRepository.exportJson/importJsonInternal、Note、ImportPolicy。 |

## 数据与提醒

局部格式、清单和任务字段保存在 SQLite 的笔记 JSON 中。格式支持加粗、绝对字号、颜色和旧版相对字号。纯格式、勾选和任务状态由 editMetadata 保存，正文及 AI 原文指纹保持原值。删除和恢复更新 AI 和提醒代次，旧请求失效。回收站原图仍按引用保留。

当前备份写出 schemaVersion 5，支持读取 1–5。JSON 和完整 ZIP 使用同一版本常量。新导入记录保留截止时间，提前和逾期提醒都需重新开启。标签撤回的分类标识随纠正记录进入备份。

提前和逾期各有独立调度和发送记录，共用便签的提醒代次。到期仍未完成且开启逾期提醒时发一次。接收器先保存发送时间，再提交通知，遇到通知权限异常时清回发送时间。完成、移入回收站及修改提醒配置会取消对应旧闹钟和通知。精确权限、通知权限与铃声设置按系统处理。手机操作见 [提醒说明](../releases/V0_4_0_Test2.md)。

## 构建

常规 Android Studio 和 Gradle 命令见 [使用与构建](../../floatmemo-ai-android/README.md#构建与验证)，Linux 手工构建见 [构建入口](../../android-build-fix/README.md)。安装覆盖需要原包名、原签名和高于已安装版本的 versionCode。签名材料保存在 Git 仓库外。
