---
title: "FloatMemo AI：自己修改与打包手册"
lang: zh-CN
---

> **这份手册的目标：把开发环境配置一次，以后自己修改小功能、生成 APK、覆盖更新，不必每次找人重新下载。**
>
> 对应本对话已归档的 **安卓 V0.2.6**，操作电脑以 Windows 为例。不是 Windows 桌面应用的开发手册。编写日期：2026-10-26。

## 1. 先分清：哪些改动不用重新安装

| 想调整什么 | 现在怎么做 | 是否需要改代码 |
| --- | --- | --- |
| 接口地址、模型、API Key、每日请求上限、暂停 AI | 应用里的“AI 设置” | 不需要 |
| 笔记的颜色、字号、透明度 | 打开笔记编辑页调整 | 不需要 |
| 笔记的主题、标签、AI 要点 | 在整理详情中修改；必要时解锁后重新整理 | 不需要 |
| 诊断当前配置、复制或导出错误记录 | 更多 → 诊断记录 | 不需要 |
| 首页文字、按钮名称、整体配色 | 修改源码，再打包 | 需要 |
| AI 整理偏好、单次输出上限、超时、候选数量 | 修改源码，再打包 | 需要 |
| 云同步、新增数据字段、全库语义检索 | 涉及多个模块，不属于简单改数值 | 需要设计和开发 |

**当前 APK 不支持修改手机外部文件就热更新 Java 代码。**文案和程序逻辑改动仍要重新生成 APK；区别是这一步以后由你在自己的电脑完成。若希望提示词、输出上限也能在应用设置中修改，需要另做一次功能开发，本手册没有假装这些设置已经存在。

## 2. 第一次准备好电脑和项目

### 2.1 找到正确的源码目录

解压项目归档后，打开里面的 `floatmemo-ai-android` 文件夹。这个文件夹中应能同时看到 `settings.gradle`、`build.gradle`、`gradlew.bat` 和 `app`。**在 Android Studio 选择 Open 时选这个目录，不是最外层归档目录。**

用副本做修改。例如把源码复制到 `C:\FloatMemo\work\floatmemo-ai-android`，保留原归档。后文说的“项目根目录”就是这个源码文件夹。

### 2.2 安装和同步

1. 从 [Android Studio 官网](https://developer.android.com/studio)安装 IDE。用 Open 打开上述项目，等待 Gradle 同步。
2. 在 SDK Manager 安装 **Android SDK Platform 35** 和 **Android SDK Build-Tools 35.0.0**。如果还要用 USB 调试，安装 Platform-Tools。
3. 给该项目的 Gradle 选择 **JDK 17**。常见入口为 Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK；不同 IDE 版本的菜单名称可能略有不同。
4. 先保留项目的 **AGP 8.7.3、Gradle Wrapper 8.9、SDK 35**。第一次同步时不要同时接受整套构建插件升级，先让原项目能构建。

项目已包含 Wrapper，不需要单独安装系统版 Gradle。第一次同步需要下载构建依赖；核心归档没有离线 SDK 二进制。SDK、依赖下载失败与 Java 源码编译失败是不同问题，不应靠乱改版本来尝试解决。

在 Android Studio 的 Terminal 中执行：

```powershell
.\gradlew.bat --version
```

检查显示的 Gradle 和 JVM。**IDE 里配置的 Gradle JDK 与终端使用的 JAVA_HOME 可能不同**；如果终端执行失败而 IDE 能构建，先检查两者用的 Java。此工程使用 JDK 17，既不是手机的 Android 版本，也不是 Windows 的 .NET 版本。

### 2.3 准备同一份签名，避免再次卸载丢笔记

取出单独保存的 `FloatMemo_AI_Signing_Maintenance_20261025.zip`。里面有 `preview-signing.keystore` 和 `SIGNING_README.md`，别名及密码见该文件。密钥不在公开源码包里。

同一个应用覆盖更新，需要维持：

| 项目 | 本项目值 |
| --- | --- |
| applicationId（包名） | `io.github.floatmemo.android` |
| 签名证书 SHA256 | `48c18d24b9bbe983674249919f73fc31fd29dfa55de23529c1f17f94136b3cd1` |
| 归档版 versionCode | `8` |
| 你自己的下一版 | 例如 `9`，之后再用 `10`、`11`…… |

不要在签名向导里点“Create new”去替代现有密钥。电脑默认生成的 debug 密钥也不是这份密钥。**如果安装器要求卸载旧应用，先停下来检查签名；不要用卸载绕过。**

## 3. 每次修改的固定流程

**复制备份 → 改一处 → 提高版本代码 → 编译检查 → 用原密钥签名 → 在手机上覆盖安装 → 验证这一个改动。**

先完成下面的“改首页文案”练习，就能走通全部流程。不必一开始理解所有 Java 类。

### 3.1 用“搜索代码”定位，而不是记行号

Android Studio 中把左侧目录视图切到 Project，可看到实际目录。用 `Ctrl+Shift+F` 搜索指定代码或界面文字，限定在当前项目。行号可能变化，**文件名 + 唯一代码片段**更可靠。

大多数 Java 文件位于：

```text
app/src/main/java/io/github/floatmemo/android/
```

下文只写 `MainActivity.java` 等短文件名时，均指这个目录。

### 3.2 修改版本代码

打开 **`app/build.gradle`**，找到：

```groovy
versionCode 8
versionName '0.2.6-ai-preview'
```

自己的首次修改可以改为：

```groovy
versionCode 9
versionName '0.2.7-local1'
```

以后继续升高 `versionCode`，不要反复固定为 9。`versionName` 是显示名称，`versionCode` 才用于版本顺序。保持 `applicationId` 原值。

### 3.3 生成可覆盖更新的 APK：先用签名向导

在 Android Studio 的 Build 菜单找到 **Generate Signed App Bundle or APK**（有的版本写成 Generate Signed Bundle/APK）：

1. 选择 **APK**，不是 AAB。
2. 选择已经保存的 `preview-signing.keystore`，填写维护说明中的别名与密码。
3. 选择 `app` 模块和 `release` 构建变体，完成构建。这个项目的 release 没有开启混淆。
4. 完成后用提示中的 Locate 找到 APK。具体输出目录以 IDE 提示为准，不要随手取另一个旧 APK。

**这条路线不用你先改签名相关代码。**第一次先使用它。以后若想一条命令生成，见第 6 节的一次性配置。

手机先通过“更多 → 导出全部便签（JSON）”保存一份备份，再用 USB 文件传输把新 APK 复制到手机并打开，选择更新。后续无需向聊天下载 APK，也不需要卸载重装。

签名、版本和包名正确是覆盖更新的必要条件，不等于任何机型上的安装都一定成功。若失败，保留原应用与数据，查看安装提示。

## 4. 常见修改：照着找到对应位置

### 4.1 改首页标题或按钮文字：适合第一次练习

文件：`MainActivity.java`。搜索原代码：

```java
root.addView(Ui.bold(this,"随手记下，\n以后找得到。",32,Ui.INK));
```

例如改为：

```java
root.addView(Ui.bold(this,"随手记录，\n慢慢想清楚。",32,Ui.INK));
```

只改引号中的文字。`\n` 表示换行，不要误删外面的引号、括号和分号。按钮如“主题标签”“AI 设置”也可按原文字搜索，只改显示文字。

检查方式：编译成功后看首页是否显示正确，有没有换行挤压。纯文案修改不必重跑网络接口测试。

一部分可复用文字位于 `app/src/main/res/values/strings.xml`。修改 XML 时保留 `%1$d`、`%2$d` 等占位符；`&` 要写成 `&amp;`，`<` 要写成 `&lt;`。

### 4.2 改整体配色

文件：`Ui.java`。搜索 `GREEN = Color.rgb(35,99,75)`。例如把主绿色换为蓝色：

```java
GREEN = Color.rgb(42,98,165)
```

只替换这个表达式的数字，保留所在行的其他变量。`BG` 是页面底色，`INK` 是主要文字色，`MUTED` 是次要文字色。

`COLORS` 是便签色板，和 `COLOR_NAMES`、已有笔记的颜色索引对应。**只更换现有 8 个颜色值比新增或删除颜色项简单**；改数量还涉及 `Note.fromJson` 的索引范围和编辑页。

检查主页、编辑页和整理详情的文字是否看得清。已有单张笔记的颜色仍可在应用内调整。

### 4.3 改 AI 的表达风格，不改变输出结构

文件：`AiClient.java`。搜索 `private static final String RULES`。可以在现有规则中补一句：

```text
要点先写一句直接的结论，再在原文有例子时用原例子帮助理解；用自然、简洁的中文。
```

它需要放进 Java 字符串的引号内；字符串本身包含双引号时须写成 `\"`。一次只加一条偏好，避免规则互相矛盾。

**保留 JSON 输出、quote 逐字原文依据、不补写事实等现有约束。**只改风格时，不要改 `summary`、`points`、`text`、`quote` 等字段名称；`AiDocument.java` 正按这些字段解析。提示词变了不会自动重写已经整理的笔记，要在目标笔记点“重新整理”；人工锁定的结果要按界面提示处理。

检查方式：用几条自己熟悉的笔记整理，看原文有没有被改、quote 能否定位、内容是否符合新增风格。编译和单元测试不能自动证明表达质量。

### 4.4 改正式请求的输出上限，例如 3000 → 6000

文件：`AiClient.java`。有两处正式请求：

```java
outputs.add(chat(c,RULES,instruction+data,epoch,"笔记整理 第"+(i+1)+"/"+chunks.size()+"段",3000,false,answer->AiDocument.parse(answer.text,chunk)));
```

以及：

```java
return chat(c,system,new JSONObject().put("query",query).put("notes",a).toString(),epoch,"AI 查找",3000,false,
    answer->searchResults(answer.text,map,sent));
```

只改这两处 `3000`。只想改变整理时，改第一处；检索也要增加时，再改第二处。**不要全项目替换 3000 或 64。**

这里控制请求中的 `max_tokens`，不是汉字数量，也不等于笔记长度。6000 是修改示例，不是对所用模型支持范围的保证。服务不支持时可能拒绝请求，生成更长也可能增加等待或费用。

诊断短测试目前单独用 `64`，它只测接口响应。若改变短测试数值，还要同步其返回提示里的“64 token”。正式整理达到上限时仍应拒绝不完整结果，不要删掉 `ApiResponse` 中的截断检查来让它看起来成功。

检查方式：打开诊断记录，看“本次输出上限”“completion_tokens”和“finish_reason”，再验证实际整理结果完整。

### 4.5 改网络等待时间

文件：`AiClient.java`。搜索：

```java
conn.setConnectTimeout(12000);conn.setReadTimeout(60000);
```

例如只把读取等待改为 120 秒：

```java
conn.setConnectTimeout(12000);conn.setReadTimeout(120000);
```

单位是毫秒。连接超时和读取超时是不同设置；更长等待不会解决错误地址、权限不足或响应格式不匹配，也不是整次任务总时长保证。

检查方式：看诊断的失败阶段与耗时，不要因为超时而重复点击产生很多请求。

### 4.6 改 AI 检索候选数量，例如 30 → 20

文件：`SearchEngine.java`。定位 `candidates` 方法末尾：

```java
return r.subList(0,Math.min(30,r.size()));
```

改为：

```java
return r.subList(0,Math.min(20,r.size()));
```

这改变送给 AI 的候选数量上限，不会删除笔记。减少候选可能减少发送量，也可能降低找回机会。搜索仍是“本地挑选候选 + AI 检查”，不是全库语义索引。

检查 `SearchEngineTest.java` 中候选上限的测试是否仍反映你的新需求，保留“数量不越界”的约束。再用一个已知相关笔记的查询验证它是否仍能入选，不能只改断言让测试变绿。

### 4.7 改相关笔记显示数量，例如 5 → 8

文件：`SearchEngine.java`，定位 `related` 方法，不是 `candidates` 方法：

```java
return r.subList(0,Math.min(5,r.size()));
```

改为 `Math.min(8,r.size())`。这里的相关推荐主要按本地内容与标签重合计算；增加条数不会自动让关联更准确。检查原笔记不被推荐给自己、没有候选时页面能正常显示。

### 4.8 改每条候选发送多少文字：要同时改界面说明

文件：`AiClient.java`，定位 `search` 方法：

```java
String text=n.content.substring(0,Math.min(1400,n.content.length()));
```

例如改为 2000。然后同步修改 `app/src/main/res/values/strings.xml` 中 `ai_search_running` 和 `ai_search_finished` 的“1400字符”，避免用户看到的发送范围与实际不一致。不要删除这些资源中的格式占位符。

检查长短笔记都能搜索，界面显示正确，且响应速度和发送量可接受。这里按 Java 字符串长度截取，不应在这一改动中随便移除原文引用校验。

### 4.9 改 AI 自动整理最多保留的标签数：有三处

以 8 → 6 为例：

1. `AiClient.java` 的整理提示词：“最多8个标签” → “最多6个标签”。
2. `AiDocument.java` 的 `parse` 方法：`tags.length()<8` → `tags.length()<6`。
3. 同文件 `combine` 方法：`if(a.length()==8)break;` → `if(a.length()==6)break;`。

不要全项目替换数字 8。单标签长度限制 30、问题数量上限 8、颜色数等是别的规则。这改变的是 AI 新整理结果的上限，不会自动清理已有标签，也不等于限制所有手工标签。

检查一篇短笔记和一篇会分段的长笔记，验证标签总数及原文引用。修改后运行 `AiDocumentTest` 相关测试。

### 4.10 改新建便签默认字号

文件：`Note.java`。搜索：

```java
public int fontSize = 16;
```

例如改为 18。它影响新建笔记，已有笔记已存了字号，不会因此全部变成 18。已有便签直接在编辑页调整即可。`fromJson` 中 `optInt("fontSize",16)` 是读取缺失字段时的默认值，只有你也想改变旧备份缺字段的回退行为时才一起改。

检查新建笔记的正文和悬浮窗是否变化，已有笔记是否仍保持原样。当前字号的读取和操作范围为 12–26，超出范围还需同步其他校验。

### 4.11 改自动整理开始前的等待

这不是只改一个 5000。当前有两层：

- `NoteRepository.java` 的 `claim` 方法要求 `System.currentTimeMillis()-n.updatedAt>=5000`，即内容至少静置约 5 秒才可领取。
- `AiJobService.java` 的 `schedule` 方法包含 `.setMinimumLatency(5500)`，这是系统调度的最小延迟。

若想约 10 秒后再尝试，可分别改为 10000 和 10500。不要改 `AiDocument` 分段算法里的另一个 5000，它是文字分段位置判断。

Android 后台调度还受网络、电量和系统策略影响，所以不是“第10秒保证开始”。检查持续输入时不会立刻整理、停止输入后会排队，以及原有编辑内容不会被在途结果覆盖。

### 4.12 改诊断保留数量或提示文案

- `DiagnosticStore.java`：`MAX_RECORDS=30` 控制最多保留多少条请求记录。
- `DiagnosticRecord.java`：`PREVIEW_LIMIT=4000` 控制单条响应片段长度。
- `ApiResponse.java`：固定的网络/协议错误提示与错误类型。
- `DiagnosticActivity.java`：诊断页面按钮和测试状态说明。

改显示文字时保持错误类型代码和脱敏处理。增加记录数会扩大页面、复制和导出内容，不必无限加大。不要把 API Key、Authorization 请求头或完整笔记正文写进日志。

## 5. 判断自己该不该动这个模块

| 模块 | 负责什么 | 普通小修改是否优先动它 |
| --- | --- | --- |
| `MainActivity`、`AiSettingsActivity`、`Ui` | 界面、设置页、外观 | 是，文案与外观从这里开始 |
| `AiClient` | 提示词、请求参数、响应处理衔接 | 可改提示词和明确参数；改前核对联动 |
| `SearchEngine` | 本地搜索、候选、相关笔记 | 可改数量；换算法需要验证找回效果 |
| `AiDocument`、`ApiResponse` | 输出结构、原文引用、协议及完整性检查 | 只改明确限制；不要为消除报错删校验 |
| `NoteRepository`、`ImportPolicy` | 数据库存取、备份导入、冲突处理 | 小问题通常不需要动 |
| `ResultGate`、revision/epoch、人工锁定 | 防止旧结果覆盖新内容及人工纠正 | 不要顺手删除 |
| `OverlayService`、`FloatingNoteWindow`、Manifest | 悬浮窗、后台服务与系统权限 | 改行为时要在手机上验证 |

比如“把主页标题换一句话”只需要动 `MainActivity`；“加一个可以跨设备同步的主题字段”要考虑存储、JSON备份、界面、同步冲突，就不能当成改几个字。

## 6. 想以后只执行一条构建命令：一次性配置

这一步是可选的，已经能用签名向导就不必急着做。辅助包提供完整配置示例，**不会自动改动你的归档源码，也不包含签名私钥**。

### 6.1 文件放置

在源码目录的同级建立 `private` 文件夹：

| 路径示例 | 放什么 |
| --- | --- |
| `C:\FloatMemo\work\floatmemo-ai-android\` | 工程源码 |
| `C:\FloatMemo\work\private\preview-signing.keystore` | 维护包里的原签名文件 |
| `C:\FloatMemo\work\private\signing.properties` | 你自己填写的签名参数 |

将辅助包中的 `examples/signing.properties.example` 复制到 `private/signing.properties`，把占位符替换为维护包 `SIGNING_README.md` 给出的值。字段是 `storePassword`、`keyAlias`、`keyPassword`。

先备份原 `app/build.gradle`，再把 `examples/app.build.gradle.local-signing.example` 的完整内容复制进去。示例沿用原项目依赖，新增本地签名配置，把示例版本代码设为 9；如果你已经安装过更高版本，请再提高它。

配置缺少私钥或密码时会明确停止，不会悄悄生成另一把钥匙。不要把 `private` 目录上传到公共仓库。

### 6.2 以后在项目根目录执行

```powershell
.\gradlew.bat assembleDebug
```

输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

上述命令只有在配置好本节的原签名后，才会使用同一签名生成更新包。**原始归档默认 Gradle debug 签名不等于原安装包签名。**不要跳过配置直接拿默认 APK 覆盖已有应用。

需要检查 AI 行为、检索或导入等逻辑时，运行：

```powershell
.\gradlew.bat testDebugUnitTest
```

需要静态检查时，单独运行：

```powershell
.\gradlew.bat lintDebug
```

这三个命令不等于已经在你的 Windows 上成功执行过。归档 APK 之前使用的是随包记录的 Linux 手工工具链；本手册提供 Windows/Android Studio 的维护路线，本次没有远程操作你的电脑完成环境配置。

## 7. 修改后怎样检查，失败时怎样退回

| 修改类型 | 最少做哪些检查 |
| --- | --- |
| 文案、颜色、默认字号 | 编译通过；打开对应页面；新旧笔记各看一条 |
| 提示词、输出参数、超时 | 编译；跑相关测试；实际整理熟悉的笔记；查看诊断 |
| 候选数、相似度、检索片段 | 跑检索相关测试；用已知能找到和应该找不到的查询检查 |
| 标签上限、输出结构或引用规则 | 跑结构相关测试；短文和长文各测；确认不接受无来源内容 |
| 存储、迁移或导入导出 | 用独立测试备份验证，不直接拿唯一正式数据试错 |

归档版记录的是 **52项 JVM 测试通过**，不意味着改过以后仍自动通过，也不意味着所有手机及所有模型都已验收。对纯文案改动不必反复调用收费模型。

修改前复制相关文件，或者用 Git 提交一个本地检查点。IDE 有 Undo/Local History，但不要把它当作手机笔记备份。

若新行为不好，恢复上一份源文件，使用更高的 `versionCode` 重新构建。不要为了安装旧版本而卸载。手机数据备份与源码回退是两件事。

## 8. 常见卡点

| 现象 | 先检查什么 |
| --- | --- |
| Gradle下载失败、无法解析依赖 | 网络和下载地址；不是先改业务代码 |
| 提示找不到SDK或Build Tools | SDK Manager是否装好35及35.0.0，IDE是否知道SDK路径 |
| Java/JVM版本错误 | Gradle JDK及终端JAVA_HOME；此工程按JDK17维护 |
| 修改一个文字就编译失败 | 双引号、Java转义、括号、分号；XML实体和格式占位符 |
| 签名私钥/属性文件找不到 | private是否位于项目同级，不是app目录里面；文件是否被保存成.properties.txt |
| 更新包提示签名冲突 | 是否用了电脑默认debug密钥，是否取错APK；保留旧应用，不卸载 |
| 提示版本过低 | 新versionCode是否高于当前安装版本；只改versionName没有作用 |
| 改了默认字号但旧笔记没变化 | 旧笔记保存自己的fontSize；改默认值影响新建笔记 |
| 改了提示词但结果没变化 | 是否重新整理，是否仍显示旧结果，是否人工锁定 |
| `MISSING_CHOICES` | 看诊断响应，是否请求到了欢迎入口或其他协议 |
| 连接测试显示64 token达上限 | 只说明短测试达到预算；继续看实际笔记整理，不必直接换模型 |

项目不需要 Node/npm，也没有给此安卓版配置浏览器热更新。不要把其他平台工程的启动方式套到这里。

## 9. 最适合第一次动手的任务

先只把首页“随手记下，以后找得到”改成你喜欢的一句话，保持其他逻辑不变。提高版本代码，用原密钥签名，把生成的 APK 传到手机覆盖更新，然后确认标题变化、旧笔记还在。

**这一次走通后，后续的小改动就是重复同一条流程。**应用内本来已有的设置，仍优先直接在应用里修改。

## 10. 对照文件与官方资料

本手册的文件位置与搜索片段已对照归档V0.2.6源码核对。行号不作为定位条件。提供的本地签名示例未在用户Windows环境实测；没有重新打包或改变当前手机应用。

- 项目原有说明：`README.md`、`DIAGNOSTICS.md`、`VALIDATION.md`、`REQUIREMENT_STATUS.md`。
- [Android Studio下载](https://developer.android.com/studio)。
- [SDK Manager说明](https://developer.android.com/studio/intro/update#sdk-manager)。
- [构建使用的Java/JDK](https://developer.android.com/build/jdks)。
- [AGP 8.7兼容信息：Gradle 8.9、JDK17](https://developer.android.com/build/releases/agp-8-7-0-release-notes)。
- [命令行构建](https://developer.android.com/build/building-cmdline)。
- [应用签名与签名向导](https://developer.android.com/studio/publish/app-signing)。
