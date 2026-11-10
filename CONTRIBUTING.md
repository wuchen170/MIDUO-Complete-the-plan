# 参与开发

## 先读这两份

- [README.md](README.md)：项目能做什么、不能做什么、怎么装
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)：模块入口、设置存储双通道、底栏引擎的几个关键约定

## 环境

- JDK 17
- Android SDK Platform **37**（若稳定通道还没有，用 SDK Manager 的预览通道装 `platforms;android-37`）
- Android Build Tools 36.0.0
- 真机 + LSPosed（API 102）；纯改 UI 的改动不必挂真机，但任何 hook / 渲染改动必须真机验证

```bash
./gradlew :app:assembleRelease
```

产物在 `app/build/outputs/apk/release/app-release.apk`。当前 release 使用本机 debug key 签名，
**不要把这个 key 当作发布签名**：正式发布前请换成自己的 keystore，并确认升级覆盖安装的签名一致。

## 加一个新应用适配

请以 issue 模板「新应用适配请求」提供包名、版本、底栏截图，最好附 `uiautomator dump` 的 xml。
代码上通常要动这些地方：

| 位置 | 做什么 |
| --- | --- |
| `.../hyperglassify/extra/ExtraNavigationTargets.kt` 或对应分组 | 定义 `NativeViewBottomBarTarget`：底栏定位、Tab 提取、标签 / 图标 / 选中态 |
| `.../runtime/MyHyperModifier.java` | 把包名路由到对应 hooks |
| `.../runtime/ModuleSettings.java` | `load()` 里读取新开关的 key |
| `.../fold/DockSettingsPage.java` | 在界面上加对应开关 |
| `app/src/main/resources/META-INF/xposed/scope.list` | 追加包名 |
| `README.md` 适配清单 | 标注应用与测试过的版本 |

**最容易踩的坑**：新增开关的 key 必须同时写进 `DockSettingsPage.java` 和 `ModuleSettings.load()`，
只写界面会让开关变成摆设（1.1.0 修过一批这样的死代码）。

## 渲染 / R8 相关约定

- `app/proguard-rules.pro` 里的 `-repackageclasses 'duo18.plan.o'` 和 `-dontoptimize` **不要删**。
  前者避免混淆类名与宿主应用自身的混淆类重名（曾导致宿主启动即 `VerifyError` 闪退），
  后者避免 Compose / coroutines 的字节码被优化出验证器不接受的形式。
- 任何跑在宿主主线程消息队列、`onPreDraw`、绘制帧回调里的新代码，都要自己捕获异常，
  不要让模块异常把宿主进程带崩。
- 改包名时，`jni/shim.c` 里的 `strstr(path, "duo18.plan")` 要一起改，并重新编译 `.so`
  （见 [jni/README.md](jni/README.md)）。

## 提交前自检

- [ ] 构建通过，APK 里没有把 `build/`、mapping、keystore、本机 SDK 路径带进来
- [ ] 真机验证过，且没有和其它模块同时勾选同一进程
- [ ] 新增 / 修改的第三方代码更新了 `THIRD_PARTY_NOTICES.md` 与 `app/src/main/assets/licenses/`
- [ ] `CHANGELOG.md` 有对应条目
- [ ] Issue / PR 里不含设备序列号、账号等隐私信息

## 风格

Kotlin / Java 混用是历史原因，不要求统一；保持与相邻文件一致的缩进和命名即可。
注释写「为什么」，不要复述代码在做什么。提交信息用一句话说明动机，例如
`fix(weibo): 让胶囊左右两侧的触摸落到页面内容上`。
