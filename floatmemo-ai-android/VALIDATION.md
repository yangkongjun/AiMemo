# V0.4.0 Test3 验证记录

2026-11-13。生产 Java 与资源编译通过。170 项原有 JVM 测试及 4 项选区格式回归通过。覆盖混合选区加粗、混合字号逐段调整、替换旧相对字号和颜色/字号存取。

APK 包名 io.github.floatmemo.android，versionName 0.4.0-test3，versionCode 14，大小 152104 字节。ZIP CRC、资源表对齐、原证书和 v2/v3 签名检查通过。

APK SHA-256：`34a49dd02622a76e6da330565e9458d51822f100f0ec604dff6bca7cc093d3a9`。

2026-11-14 文档收尾复核了现有 Test3 APK 的包名、版本、SHA-256、签名和安装结构。174 项为 2026-11-13 的回归记录。手机选区和弹出菜单、SQLite 撤回及覆盖安装待测。见 [Test3 操作说明](../docs/releases/V0_4_0_Test3.md)。

---

# V0.4.0 Test2 验证记录

2026-11-13。生产 Java 与资源编译通过，164 项原有 JVM 测试及 6 项新增提醒回归通过。覆盖提前与逾期分别触发、同一截止时间去重、完成和回收站拦截、改期、旧数据和备份导入。

APK 包名 io.github.floatmemo.android，versionName 0.4.0-test2，versionCode 13，大小 148008 字节。ZIP CRC、资源表 4 字节对齐、原证书及 v2/v3 签名检查通过。

APK SHA-256：`75e024f46c6f2c65a27d44b5078c8b2b54eddfb2aee669ad226d0a5aa1d7a45f`。

真机覆盖安装、通知投递、锁屏及勿扰下声音和精确提醒权限仍待验证。见 [Test2 操作说明](../docs/releases/V0_4_0_Test2.md)。

---

# V0.4.0 Test1验证记录

日期2026-11-07。测试分支feature/note-tasks-format-reminders，包名io.github.floatmemo.android，versionName 0.4.0-test1，versionCode 12。

- 完整生产Java/资源经AAPT2、ECJ、D8编译，zipalign和原证书v2/v3签名通过。
- 164项JVM测试通过，失败0；既有133项重跑，新增31项覆盖格式/Unicode区间、清单状态、编号续写、任务与AI状态分离、提醒代次及schema4含回收站原图备份。
- APK资源表未压缩、4字节对齐（数据偏移3024）、ZIP CRC、必需文件、包名与版本、新Activity/Receiver及权限检查通过。证书与既有版一致，只核验升级身份条件。
- 源码检查：元数据变动不排AI任务；回收站排除搜索/AI/悬浮；多选限制当前范围，过期AI结果不进入当前多选；通知/精确权限分步处理，完成/回收取消并更新代次。
- 未运行：真机/模拟器、选字/横向滑动/软键盘/悬浮手势、实际覆盖安装/数据恢复、Android SQLite故障注入、系统通知/闹钟/Doze/重启/厂商省电、真实模型和Gradle lint。

APK SHA256：`052acb2bb5c8b01e7faeb12daeb38630bb914eba4044541739cf4c56bf75f360`，143912字节。证书SHA256：`48c18d24b9bbe983674249919f73fc31fd29dfa55de23529c1f17f94136b3cd1`。

[手机验收清单](../docs/releases/V0_4_0_Test1.md)、[机器记录](verification/v040_test1_checks.json)、[构建和测试日志](verification/build-v040-test1.log)。以下为历史版本验证，不能替代本次设备验收。

---

# V0.3.2验证记录

日期2026-10-29。包名io.github.floatmemo.android，版本0.3.2-ai-preview，versionCode 11。

- 本次完整生产Java/资源编译、AAPT2/ECJ/D8、zipalign和原证书签名通过；新NoteListActivity已进入DEX并注册到二进制清单。
- 原有133项JVM回归全部通过，失败0。此次布局改动没有新增镜像实现的测试；这133项不能验证新界面外观或交互。
- APK v2/v3签名、资源表未压缩与4字节对齐、ZIP CRC、包名/版本和清单检查通过；证书与V0.3.1一致。仅核验覆盖升级身份条件，没有实际执行手机安装。
- 源码检查卡片复用、整标签/主题匹配、图像标记不进文字预览、空态、订阅刷新及笔记ID锚点恢复。列表不发AI请求，不修改笔记存储或配置。
- 未运行：Android真机/模拟器、界面截图、触控/长列表/大字体/读屏、旋转/返回位置恢复、实际覆盖升级和数据保留、真实模型联调、Gradle lint。

APK SHA256：`90005cf566a7c7ce445c2f6ad9c16abcefe5aa3a8002bbdeeb3d37db080a2237`，字节数123432。

证书SHA256：`48c18d24b9bbe983674249919f73fc31fd29dfa55de23529c1f17f94136b3cd1`。资源表数据偏移2700，4字节对齐。

机器记录见verification/v032_checks.json、verification/build-v032.log；[本次交付说明](../docs/releases/V0_3_2.md)包含修改入口与手机验收步骤。以下为既有版本记录，不视为新增设备验证。

---

# V0.3.1验证记录

日期2026-10-27。包名io.github.floatmemo.android，版本0.3.1-ai-preview，versionCode 10。

- 完整生产Java/资源编译、AAPT2/ECJ/D8、zipalign与原证书签名通过。
- 133项JVM测试通过（原105项及28项接收/诊断测试），失败0。
- 回环HTTP合成分段测试：只发送1次POST，持续分段到达时总耗时可超过单次读取超时；不涉及真实API、TLS或Android。
- APK v2/v3、未压缩资源表、4字节对齐、ZIP CRC、必需文件和二进制清单检查通过。包名不变，版本代码提高，证书与V0.3.0一致；这只是覆盖升级的身份条件，未实际在设备安装。
- 未验证：Android真机/模拟器、真实模型长请求、实际网关断连复现及唯一根因、Gradle lint。
- 数据库和媒体存储代码未在本次修改；原笔记及旧设置沿用。短测试继续64 token非流式，不代表长回答或流式已通过服务验收。

APK SHA256：`7339b25a61d9c75899cf3a1235a2bfd0e9b3303424fe3d373af90e42079107b3`，字节数119336。

证书SHA256：`48c18d24b9bbe983674249919f73fc31fd29dfa55de23529c1f17f94136b3cd1`。

机器记录见verification/v031_checks.json、verification/build-v031.log；[交付与限制](../docs/releases/V0_3_1.md)包含使用步骤与依据。旧V0.3.0验证记录保留如下。

---

# V0.3.0验证记录

日期：2026-10-27。包名io.github.floatmemo.android，版本0.3.0-ai-preview，versionCode 9。

## 本次已运行

- 生产Java及Android资源完整编译，AAPT2、ECJ、D8、zipalign与apksigner流程完成；手工签名密码从本机环境变量读取，原私钥没有提交。
- **105项JVM测试通过，失败0**，含原52项回归及53项新测试。覆盖生成预算边界、3000字与分段边界、文字指纹、锁定和任务代次、图片标记/来源映射、附件复制及JSON、命中2200/5000处、候选及全局预算、完整词优先、Unicode、跨窗口伪引用、搜索准入/TTL/LRU/内存预算、媒体ZIP/hash/路径/CRC/缺失及损坏文件、旧JSON、ID重映射和文件恢复记录。
- APK v2/v3签名通过，证书与V0.2.6一致。资源表未压缩，本地数据偏移2672，4字节对齐；ZIP CRC、必需文件及二进制清单检查通过。
- 清单校验包名、minSdk26、版本代码9。具备覆盖V0.2.6的应用身份条件，不能把这一检查称为已完成设备升级。
- 对每条3014字符的10/100/1000条合成笔记运行本机搜索计划基准；默认配置均选10篇、正文6040字符、序列化用户消息7756字符，未发出HTTP。结果见verification/v030_search_benchmark.json。该数据不是手机速度或真实服务耗时。
- 原V0.2.6交付APK保持不变，旧归档没有改写。

## 仍未运行

- 当前环境没有Android设备、adb或模拟器：未测真实安装/覆盖升级、图片选择器、中文输入、旋转、悬浮窗、图片显示/缩放、低内存和存储不足行为。
- 文件暂存、校验、冲突映射及恢复记录有JVM测试；**未在Android实际SQLite事务与文件系统之间执行强杀、磁盘故障注入或完整往返恢复**。代码实现不等于这些设备验收已经通过。
- 没有持有用户真实API Key，未向魔搭或其他模型服务发送真实笔记；新预算与originalNoteSegments格式的服务兼容、长输出质量、语义召回及费用仍待实际验证。
- 未运行Gradle lint或Gradle构建；本次使用上述手工工具链。Gradle入口、版本和依赖保留。

## 安装包身份

- APK SHA256：`5565d979a1e1e403bba801149ada50602850629cc669c5cbcc18829b3dcf88d2`。
- 字节数：115240。
- 证书SHA256：`48c18d24b9bbe983674249919f73fc31fd29dfa55de23529c1f17f94136b3cd1`。

先从旧版另存笔记备份，然后直接覆盖安装，不卸载或清除数据。新版本含图片时使用完整ZIP备份；JSON不含原图片。不得把低版本APK强行降级读取新媒体数据当作已支持行为。

机器记录见verification/v030_checks.json，构建/测试输出见verification/build-v030.log。
