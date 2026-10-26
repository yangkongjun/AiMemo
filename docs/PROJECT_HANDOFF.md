# 后续开发交接

## 用户想解决的事

笔记很多且混乱，写了不回看，换一种说法搜索就找不到。用户希望输入原始内容后由AI梳理要点、分类和标签，能浏览这些结构并找到相关笔记；接受偶尔确认和纠正。原文必须保留，AI不能把个人感受变成确定事实，也不能编造来源。

界面与说明使用简洁、自然的中文。用户不希望为配置模型反复换安装包，不使用并行安装。不要擅自补全用户填写的接口地址。更新保留当前包名和签名，先备份再覆盖，不能引导用户卸载而不说明数据后果。

## 目前结构

Java原生Android，minSdk26，compile/target35，JDK17。没有第三方运行时依赖。Gradle8.9、AGP8.7.3。归档APK由android-build-fix/build_fix.py使用AAPT2、ECJ、D8、zipalign和apksigner构建。

- NoteRepository：SQLite本机数据、旧JSON迁移、原文版本、人工纠正与JSON备份。不要破坏schemaVersion及冲突保留逻辑。
- AiConfig：按输入原样校验HTTPS地址，Keystore加密API Key，启用开关、epoch及请求次数上限。
- AiClient / ApiResponse：直接向配置地址POST；Chat Completions格式；正式整理/检索3000 token上限，短诊断64；不自动跟随重定向。
- AiDocument：笔记结构及逐字原文引用校验，长笔记分段；无完整跨段综合总结。
- AiJobService / ResultGate：任务队列、有限重试、版本和锁定检查、过时结果丢弃。
- SearchEngine：文字及标签搜索、最多30个候选的AI重排、本地相似内容推荐；没有全库向量索引。
- DiagnosticRecord / DiagnosticStore / DiagnosticActivity：本机最近30次请求、阶段和HTTP分类、脱敏限长响应、测试、复制、导出。
- MainActivity / OverlayService / FloatingNoteWindow：主页、笔记编辑与悬浮输入。

## 当前事实与边界

用户已表示项目处于可用状态。最后一条服务日志显示模型返回chat.completion及finish_reason=length，completion_tokens=64，对应诊断短测试的上限。该日志不能作为正式整理结果完整的证明。

归档V0.2.6自动验证为52项JVM测试及APK签名、结构检查。未在该版运行真机/模拟器自动安装、触控或中文输入测试，未由开发环境持有真实Key进行联调，未跑本版Gradle lint。不要把用户反馈改写为所有机型或全部原需求已经完成验收。

主题是平面的，标签缺少同义词与合并管理；关联为本地相似性；AI检索只处理有限候选。没有云同步、自动笔记云备份或全库语义索引。完整范围见REQUIREMENT_STATUS.md；质量指标、规模、费用及长期后台稳定性尚无量化验收。

## 继续开发的起点

从本归档复制新工作目录，保留归档原件。先读README、DIAGNOSTICS和VALIDATION。新版本versionCode应高于8；使用原签名私钥，不能临时生成新签名来替代。修改后运行与变更相关的测试、检查签名后APK资源表的未压缩和4字节对齐，再用已备份数据验证覆盖更新。

不要把日志中的密钥、手机笔记或真实服务响应全文写入公开仓库。诊断响应仍可能包含服务商业务信息，分享前检查。当前API Key的Keystore材料不能靠复制签名文件或源码恢复。
