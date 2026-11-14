# FloatMemo AI 当前交接

2026-11-14 核对。测试分支 `feature/note-tasks-format-reminders` 对应 **V0.4.0 Test3**（versionName 0.4.0-test3，versionCode 14）。版本以 [app/build.gradle](../floatmemo-ai-android/app/build.gradle) 为准。现有 APK 的源码提交是 `290c769`，本次为文档收尾。常规交付分支 `floatmemo-ai` 对应 V0.3.2（`bb64680`）。

## 从这里继续

用户要让杂乱笔记能重新看懂、凭模糊记忆找回，并浏览分类、标签及相关笔记；希望主要负责输入和使用，接受少量纠正。原文保留，感受、猜想、条件和否定不能被整理成确定事实。

继续工作先看 [需求实现状态与未完成想法提醒](../floatmemo-ai-android/REQUIREMENT_STATUS.md)。那里区分原有目标、已实现范围、未验证效果和仅供讨论的扩展。长期未做不等于取消；仅讨论的内容也不等于获得开发授权。

## 当前代码与入口

Java原生Android，minSdk26、compile/target35、JDK17、AGP8.7.3、Gradle8.9，无第三方运行时依赖。操作与完整边界以 [应用说明](../floatmemo-ai-android/README.md) 为准。

| 部分 | 当前行为与代码入口 |
| --- | --- |
| 原文与整理 | NoteRepository保存本机SQLite数据；AiDocument、ResultGate核对文字指纹、代次、锁定和原文摘录。AiClient只整理已有文字，长文按段处理，尚无全文综合与全部主题覆盖检查。 |
| 分类与关联 | 主题为平面文件夹，标签为笔记级标签；BrowseActivity与NoteListActivity提供主题/标签卡片浏览。SearchEngine.related按本地标签与措辞重合推荐，不调用模型，也不等于逻辑关系或全库语义索引。 |
| 搜索 | SearchPlanner本地筛选与命中附近截取，SearchCoordinator/SearchSession管理单次请求与有效缓存，AiClient只检查发送的有限片段。候选阶段漏掉的笔记不会由后续模型补回。 |
| 图文与恢复 | AttachmentStore、NoteBodyParser、NotePreviewRenderer、ImageViewerActivity负责私有原图副本与图文显示；BackupArchive负责含原图ZIP。JSON不包含原图片；卸载会删除应用私有笔记和图片。 |
| 接入与后台 | AiConfig保存配置并加密Key；AiClient按用户原样地址POST，禁止重定向。ResponseReader读取JSON/SSE；AiJobService管理后台整理及既有限次重试。 |
| 诊断 | DiagnosticRecord/Store/Activity保留本机最近30次记录，只记录限长脱敏错误、阶段与网络特征；不保存正常回答和推理正文。排查入口见 [诊断说明](../floatmemo-ai-android/DIAGNOSTICS.md)。 |

预算默认值、范围与stream开关以RequestOptions及AiConfig.Snapshot为准：整理8192、搜索4096 token，读取等待120秒，候选10篇，正文总摘录12000字符，流式默认开启；短连接测试仍为64 token非流式。读取等待不是总任务时长保证，应用允许值也不是服务商容量承诺。

## 测试分支入口

[安卓使用说明](../floatmemo-ai-android/README.md)汇总工具栏切换、标签撤回、待办提醒和回收站操作。[开发指南](developer/V0_4_0_Test1_Development.md)说明代码入口、存储和提醒机制。

## 开发与交付

- 工程入口、基础手册与当前补充见 [开发文档入口](developer/README.md)。常规使用Gradle；已有手工工具链入口为 [build_fix.py说明](../android-build-fix/README.md)。
- 修改应用时，新versionCode须高于当前已安装版本；保留包名和原签名，先备份后覆盖安装。私钥缺失时停止，不生成替代文件、不用卸载绕过。
- 本次按 [Git交付约定](GIT_DELIVERY.md) 同步到feature/note-tasks-format-reminders测试分支；不移动floatmemo-ai、不合并main。
- 本地工具链、原签名材料、旧交付APK与归档不是普通清理对象；先核实用途及唯一性。无新授权不删除个人数据或范围外文件。

## 验证边界

Test3 原交付通过生产编译和 174 项 JVM 回归。2026-11-14 复核现有 APK 的版本、哈希、签名和安装结构。选区、弹出菜单、SQLite 撤回、系统通知和覆盖安装的正式真机验证仍待记录。见 [验证记录](../floatmemo-ai-android/VALIDATION.md)。

仍待验证：真机覆盖升级与数据恢复、图文/卡片/旋转/返回/大字体等交互、Android实际SQLite与文件故障、真实模型兼容性与长请求、固定笔记集的召回/忠实度/纠正负担/费用。本环境未确认用户手机的现役版本，也未证实此前偶发断连的唯一原因。

## 收尾记录

[2026-11-14](../floatmemo-ai-android/verification/knowledge_closeout_20261114.json)记录本次核对结果。[2026-11-04](../floatmemo-ai-android/verification/knowledge_closeout_20261104.json)对应 V0.3.2 的历史收尾。
