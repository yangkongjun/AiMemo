# FloatMemo AI手工构建入口

`build_fix.py` 对应此前 Linux 构建流程。仓库仅保存脚本；SDK、构建器 JAR 和私钥需在本机另行准备，不包含在 Git 中。一般修改优先使用 Android Studio 和 Gradle，见开发手册。

Linux x86_64、Python 3、JDK 17。脚本期待本目录中的 `tools/android-15/`（AAPT2、zipalign、lib/d8.jar、lib/apksigner.jar、lib64/libc++.so）、`platform/android-35/android.jar`、`ecj.jar`（ECJ 3.37.0）、`junit.jar`（4.13.2）、`hamcrest.jar`（1.3）。JSON-java 测试依赖位于 Android 工程中。

覆盖升级还需用户原有 `preview-signing.keystore`。从用户私有维护包取用，不生成替代文件。签名密码通过本机环境变量 `FLOATMEMO_STORE_PASSWORD` 和 `FLOATMEMO_KEY_PASSWORD` 设置，不把值写入源码、Git 或终端日志。公开版本脚本没有内置密码，缺少所需材料会停止。

依赖及私钥就位后，在仓库根目录运行 `python3 android-build-fix/build_fix.py`。输出至 `deliverables/`；新版本先在 app/build.gradle 递增 versionCode。脚本运行编译、对齐、签名及 JVM 测试。V0.3.0已使用该环境变量签名路径构建；原私钥和密码仍在仓库外。

当前JVM测试、APK校验及未测项目见 `floatmemo-ai-android/VALIDATION.md`。源码同步和文件一致性检查不能替代真实设备覆盖升级验证。
