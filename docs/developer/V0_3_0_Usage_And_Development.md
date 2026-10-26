# V0.3.0使用与开发补充

先用原来的开发手册配置Android Studio、SDK及原签名；本补充说明新增功能在哪里设置、在哪里改。修改代码后需要重新构建APK，模型地址、预算、次数等设置直接在手机里改即可，不必重新下载或构建。

## 手机上怎么用

1. 旧版先导出JSON备份，然后直接覆盖安装V0.3.0，不卸载。包名和原证书延续，versionCode为9。
2. “AI设置”新增整理生成上限、搜索生成上限、读取等待秒数、候选篇数、正文摘录字符预算。比如先保持8192/4096/120秒/10篇/12000字符；如果服务拒绝参数，按实际服务能力调小。原地址不自动补路径，原Key留空保留。
3. 编辑页在光标处点“插图”。复制成功后切到预览，可点图缩放。编辑状态看到 `![图片](attachment://UUID)` 占位符；删除整个占位符即可删除该处引用，周围文字和换行不由软件重写。只删除一部分占位符会使其成为普通文字，应撤销或删掉完整标记；若AI消息仍含附件地址，会在发请求前拒绝发送并提示修复。
4. 图片不做识别。想让AI找回图片的事情，请在正文自己写描述。仅有图片时不会调用整理API。
5. 首页选择主题范围，输入线索；输入只进行本地搜索。点击AI查找可选当前范围或全部。运行中不允许再开第二次搜索；进程内有效缓存可以复用，服务错误后允许手动再点一次。
6. 含图数据用“更多 → 完整备份（含图片 ZIP）”。导出到应用以外的位置并保留原件。恢复用“导入 ZIP / JSON 备份”；先校验并显示数量，再由你点击导入。单独JSON缺原图时会拒绝不完整恢复。

## 常见改动在哪

| 想改什么 | 入口 | 修改要求 |
| --- | --- | --- |
| 默认生成/搜索上限、超时、候选、字符预算 | RequestOptions.defaults，AiConfig.Snapshot默认值 | 两处默认保持一致；已有用户保存值继续优先 |
| 设置允许范围 | RequestOptions构造校验、AiSettingsActivity文字 | 同时更新范围说明；不是服务商真实容量 |
| 命中附近的大小、3个窗口、每篇1200字符 | SearchPlanner.windows / Index.plan | 保留完整关键词优先、重叠合并、总预算、Unicode和原文位置 |
| 查询和标题/标签长度 | SearchPlanner.Index.plan / Plan.payload | 总输入还包括元数据、JSON和固定提示词，不将字符当精确token |
| 缓存时长、条数与内存预算 | SearchCoordinator的SearchSession构造参数 | 毫秒、条数、估算字节；保留单次请求准入及失效条件 |
| 图片大小/数量 | Attachment.MAX_BYTES / MAX_NOTE_BYTES / MAX_PER_NOTE，NoteBodyParser.validate | 联动界面说明、备份校验与预览上限，不只改一处数字 |
| 图片尺寸和格式 | Attachment构造校验、AttachmentStore.inspect / rejectAnimation | 同时维护选择器格式过滤；不直接解码全部原图 |
| 预览/查看清晰度 | NotePreviewRenderer的320、ImageViewerActivity的2048 | 增大会增加内存占用；查看页说明一起改，原图不重写 |
| ZIP/JSON备份上限 | BackupArchive.MAX_ARCHIVE / MAX_EXPANDED / MAX_JSON | 保留路径、CRC、hash、总展开量、暂存及事务恢复记录 |
| 整理提示词 | AiClient.RULES / analyze | originalNoteSegments是图片之间的连续文字，不可跨项引用；不传图片或路径 |

## 需要一起保留的保护

- Note.revision仍代表原内容版本；aiSourceHash代表标题及不含图片标记的文字。aiGeneration防止旧任务覆盖重新整理或人工操作。
- ResultGate与AiDocument.current一起判断：文字指纹、任务代次、锁定和原文引用均须有效。图片位置截断旧引用时不能把旧结果当成当前结果。
- 分类和标签变化不一定增加原revision，因此NoteRepository维护单独corpusVersion。SearchCoordinator用查询、范围、该版本、配置epoch及预算生成缓存键。
- 搜索结果的quote只能在一个实际发送的Window中定位，不能拼接两个片段；sourceStart传给编辑器后仍核对原文，过时位置不直接选中。
- 附件是不可变文件；备份恢复先校验，冲突ID确定性重映射，同时改正文、元数据、历史、纠正。文件先于DB提交，恢复记录用于处理中断；不能只复制路径。
- 只在启动时清理超过24小时且没有当前/历史/纠正引用的图片，导出期间不删源文件。历史保留的图片不会因当前正文删图而消失。

## 改完如何检查

正常环境运行 `gradlew.bat testDebugUnitTest lintDebug assembleDebug`（Linux/macOS用 `./gradlew`）。测试包含原文与指纹、片段预算和来源、搜索请求准入/缓存、ZIP校验及文件恢复记录。已有验证记录不能替代你修改后的检查。

用原签名构建覆盖包，递增versionCode；先在已另存备份的数据上验证。至少检查插图取消/多图、退出重进、删除相册原件、旋转、悬浮预览、旧JSON、完整ZIP恢复、重复导入、缺失原图修复、损坏包拒绝及服务返回截断。本环境没有进行这些Android交互验收，也没有持有真实模型Key。

本地完整备份含个人内容，私钥和密码继续放在仓库外。每次完成可交付修改后按Git同步约定提交源码、文档和验证记录；手机笔记不进入Git。
