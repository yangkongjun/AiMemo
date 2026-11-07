# V0.4.0 Test1开发补充

本次工作分支为feature/note-tasks-format-reminders，起点bb64680c6a27da4749dc2501b63d913c5253cb24。floatmemo-ai保留V0.3.2，不合并。安装和手机测试见[交付说明](../releases/V0_4_0_Test1.md)。原包名、原签名及本地SQLite存储沿用，新增字段仍放在notes.data的JSON中。

## 常见修改入口

以下Java文件位于floatmemo-ai-android/app/src/main/java/io/github/floatmemo/android/。

| 想调整什么 | 文件与位置 |
| --- | --- |
| 工具栏按钮文案或顺序 | FormatToolbar构造器中的add；按钮独立于系统复制菜单，保留选区、横向滚动。 |
| 大号、特大倍率 | FormatToolbar中的1.25/1.5；TextFormat.validate与读取校验也须同步支持范围，测试覆盖不支持的值。 |
| 整张字号 | 原Note.fontSize及NoteEditorActivity.changeFont，不要误改局部倍率。 |
| 清单和编号插入 | BodyEditing.insertList、ListEditing.prefixLines/newline；TextFormat.items识别行，围栏代码块排除。 |
| 局部格式保存与文字变动映射 | TextFormat、TextStyleSpans、Note.formats/checks；区间使用原文UTF-16偏移，必须避开表情代理对中间。 |
| 预览复选框 | NotePreviewRenderer.textBlocks，使用完整原文位置及revision校验写入，保留Markdown到原文的映射。 |
| 状态颜色与文案 | TaskUi.color/summary、NoteTasks.label、MainActivity及NoteListActivity卡片。灰色是展示色，保留用户原配色。 |
| 提醒提前选项 | TaskUi.deadline的options和minutes保持一一对应；默认chosen索引3为1小时。 |
| 系统提醒 | ReminderScheduler、ReminderReceiver和AndroidManifest；修改后务必手机验收权限、Doze、重启、通知动作和取消。 |
| 批量管理范围/选择 | MainActivity.manage、NoteListActivity.manage、NoteManageActivity；全选只当前范围，使用稳定ID，刷新时删除不可见选择。 |
| 回收站与永久删除 | NoteRepository.trashMany/purgeMany；SQLite单事务先提交后更新内存，不把回收站排进AI或搜索。 |
| 备份/导入 | Note、BackupArchive、ImportPolicy、NoteRepository.importJsonInternal；新字段变动同时更新签名、校验与测试。 |

## 数据契约

Note新增task、completedAt、dueAt、reminderAt、reminderGeneration、reminderFiredAt、deletedAt、formats和checks。旧JSON默认普通便签、无提醒、无局部格式。formats为最多1000个起止范围，支持bold和size；checks按清单行起始原文偏移存布尔覆盖，因此可把原文`[x]`暂时取消勾选。内容改变时区间重映射；清单整行不再相同则清掉其覆盖状态。

纯格式/勾选/任务修改走editMetadata，不改变content、revision、AI指纹或AI任务代次；编辑正文继续走update/rebase。completedAt不能与AI reviewed混用。完成整张便签设置open=false；OverlayService和setAllOpen继续检查canFloat，不能被“显示全部”重新打开。

删除和恢复递增aiGeneration与reminderGeneration，旧请求与旧通知动作不得回写。回收站记录仍包含原图引用，exportJson必须包含回收站；AttachmentStore不能把这些原图当垃圾清除。永久删除也删除对应history/edits，未引用原图沿既有延迟清理流程回收。

schemaVersion4允许导入1–4。ZIP writer必须输出4，不可只修改JSON writer。新导入记录保留截止日期但重置reminderAt/firedAt并递增代次，避免通知轰炸；与本机完全相同而跳过的记录不改本机提醒。ImportPolicy.signature包含任务/样式/回收站状态，以免状态不同的备份被错误当成同一条。

## 提醒契约与限制

每篇便签有唯一PendingIntent URI和代次；通知动作另用COMPLETE URI。只有活动、待办、未完成、配置了提醒且未发送的记录可以提醒。提前或旧代次广播不发送；成功请求NotificationManager后记录firedAt。通知权限拒绝时不标记已发送。完成、移入回收站和更改提醒时取消旧闹钟/通知。

精确权限缺失时使用setAndAllowWhileIdle近似闹钟，不能承诺准点。BOOT_COMPLETED、MY_PACKAGE_REPLACED、TIME_SET/TIMEZONE_CHANGED及精确权限授权广播重排；Activity恢复也重排。强行停止后系统可能不投递广播，用户需重新打开应用。无联网或重复提醒，日期时间作为绝对时间存储。

当前未做大规模闹钟压测；Android系统和厂商可能限制调度数量。元数据事务与真实SQLite故障行为未做设备注入。测试阶段优先使用少量合成待办。

## 构建与验证

常规Android Studio/Gradle环境继续使用原构建入口。已有Linux工具链可运行android-build-fix/build_fix.py，需要已有SDK/工具与原签名材料；不生成替代密钥。脚本按版本主段默认输出V0_4_0.apk，交付副本命名V0_4_0_Test1.apk以表明测试版。

本次164项JVM测试通过，新增TaskPresentationTest与BackupArchiveTest案例。JVM只验证纯逻辑/备份，不能运行Android UI、SQLite和系统闹钟。构建日志及机器检查在verification/build-v040-test1.log和v040_test1_checks.json；没有运行Gradle lint。每次新增APK必须使用更高versionCode、同包名同签名，执行APK结构检查并先备份后覆盖安装。

此前未完成的分类层次、语义召回、关系证据、知识回顾与低纠正负担目标仍以[需求状态](../../floatmemo-ai-android/REQUIREMENT_STATUS.md)为准。
