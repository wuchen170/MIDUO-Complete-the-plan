# 把本仓库当作模板使用

这份仓库保留了完整可构建的功能代码，同时整理成了 GitHub **Template repository** 的形状。
你可以直接发布它，也可以点 `Use this template` 派生出自己的模块。

## 一、发布成模板仓库

1. 把本目录整体推到一个新仓库（**不要** `git add .` 之前先检查 [发布前清单](docs/PUBLISHING.md)）。
2. 在 GitHub 仓库页 → `Settings` → `General` → 勾选 **Template repository**。
3. 把下面这些占位替换成你的信息：
   - `OWNER/REPO`：出现在 `README.md` 的构建徽章、`.github/ISSUE_TEMPLATE/config.yml` 的链接里；
   - `LICENSE` 的版权归属说明（GPL-3.0 正文不要改）；
   - `screenshots/` 里的占位说明，换成真实截图。
4. 开一个 tag（如 `v1.1.5`），CI 会自动构建并把 APK 附到 Release 上。

## 二、派生自己的模块：最小改动路径

只想换个名字 / 包名、不重写代码的话，改下面 **5 处** 就够了。

| # | 文件 | 改成什么 |
| --- | --- | --- |
| 1 | `app/build.gradle.kts` → `applicationId` | 你自己的包名，例如 `com.example.mydock` |
| 2 | `app/src/main/res/values/strings.xml` → `app_name` | 模块显示名 |
| 3 | `jni/shim.c` → `strstr(path, "duo18.plan")` | 与第 1 步一致的包名 |
| 4 | `app/proguard-rules.pro` → `-repackageclasses 'duo18.plan.o'` | 你独占的混淆包，例如 `com.example.mydock.o` |
| 5 | `README.md` / `CHANGELOG.md` | 项目名、链接、版本历史 |

**第 3 步不能只改源码**：`libhgshim.so` 是预编译的，改完 `shim.c` 必须用 NDK 重新编译并替换
`app/src/main/jniLibs/arm64-v8a/libhgshim.so`，否则桌面玻璃会静默失效。步骤见
[jni/README.md](jni/README.md)。

> `namespace = "com.aritxonly.myhypermodifier"` 决定 R 类和源码包路径，**建议保持不动**。
> 改它要同步移动几十个 Java/Kotlin 文件、`java_init.list` 里的入口类名、以及所有 `import ...R`，
> 收益很小、出错概率很高。`applicationId` 才是安装时区分模块的身份。
>
> 同理，`-repackageclasses` 的包名一定要选一个**宿主应用绝不会用到**的名字。
> 混淆类落在根包曾导致模块 dex 与小红书自己的 `a` / `k` 类重名，宿主启动即 `VerifyError` 闪退
> （详见 [CHANGELOG 1.1.4](CHANGELOG.md#114-27)）。

## 三、按需要裁剪功能

- **只想要柔光底栏**：删掉 `fold/` 下桌面 / 相机 / 壁纸相关页面与 `CameraModule` 入口，
  从 `java_init.list` 去掉对应行，移除 `libhyperglass.so` 与 `native_init.list`，
  再把 `fold/SettingsActivity.java` 简化成单页。此时 `scope.list` 也只留底栏目标应用。
- **只想要桌面 / 相机 / 壁纸**：反向操作，去掉底栏的 hooks 与 `DockSettingsPage`。
- **增删某个 App 的适配**：见 [CONTRIBUTING.md](CONTRIBUTING.md#加一个新应用适配)。

## 四、签名

当前 release 用本机 debug keystore 签名，方便覆盖安装调试。正式分发前请：

1. 生成自己的 keystore（自行保管，不要提交）；
2. 在 `app/build.gradle.kts` 里用 `signingConfigs` 指向它，凭据从 `local.properties`
   或环境变量读取；
3. 确认新签名与你要升级的旧包一致，否则用户只能卸载重装。

## 五、派生后的合规义务

本仓库是 GPL-3.0。派生发布时请保留 `LICENSE`、`THIRD_PARTY_NOTICES.md` 和
`app/src/main/assets/licenses/`，并提供你所分发二进制的对应源码——包括
`jni/` 下的 native 源码。详见 [docs/PUBLISHING.md](docs/PUBLISHING.md)。
