<div align="center">

# 18duo 补全计划

**一个 LSPosed 模块：把系统界面和常用 App 的底部导航，换成浮在内容之上的柔光玻璃胶囊。**

[![Build APK](https://github.com/OWNER/REPO/actions/workflows/build.yml/badge.svg)](https://github.com/OWNER/REPO/actions/workflows/build.yml)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/Android-13%2B%20%7C%20arm64--v8a-green)
![LSPosed](https://img.shields.io/badge/LSPosed-API%20102-orange)

</div>

---

## 这是什么

把两块能力合并进了一个 APK：

1. **桌面 · 相机 · 壁纸**（移植自 [HyperGlassFOLD](https://github.com/wuchen710/HyperGlassFOLD)）：
   桌面图标柔光玻璃化、动态图标 / MAML 小组件、后摄拍照时的外屏视频、外屏壁纸右半张对齐。
2. **柔光玻璃悬浮底栏**（移植自 [HyperModifier](https://github.com/AritxOnly/HyperModifier)）：
   原生底栏被画成透明，页面内容铺到屏幕最底部，一枚玻璃胶囊浮在内容之上；支持背景采样、
   下滑 / 闲置自动收起、触摸穿透，以及 SystemUI 的手势提示线。

包名 `duo18.plan`，与旧模块 `dev.hg.glass`、`com.aritxonly.softglassnav` 相互独立，可并存安装。
设置界面以 HyperGlassFOLD 为主体，柔光底栏是其中一个子页。

> **本项目是二次整合与适配**，不是上游项目的官方版本。适配依赖应用内部类名 / 资源名，
> 目标应用升级后可能失效。

## 适配清单

设置页里 11 个应用各有独立开关，还有一个总开关：

| 分类 | 应用 | 包名 |
| --- | --- | --- |
| 购物 | 淘宝 / 京东 | `com.taobao.taobao` / `com.jingdong.app.mall` |
| 社交 | QQ / 小红书 / 微博 | `com.tencent.mobileqq` / `com.xingin.xhs` / `com.sina.weibo` |
| 内容 | 哔哩哔哩 / 抖音 / QQ 音乐 | `tv.danmaku.bili` / `com.ss.android.ugc.aweme` / `com.tencent.qqmusic` |
| 工具 | 支付宝 / 米家 / 高德地图 | `com.eg.android.AlipayGphone` / `com.xiaomi.smarthome` / `com.autonavi.minimap` |

另外 `scope.list` 还包含 `com.android.systemui`（手势提示线）、
`com.miui.home` / `com.android.camera` / `com.miui.miwallpaper`（桌面 / 相机 / 壁纸）。

已适配但**没有设置页开关 / 未接路由**的包名（拼多多、闲鱼、抖音商城、菜鸟、微信、抖音极速版）
默认不启用，引擎本身具备扩展能力，见 [CONTRIBUTING.md](CONTRIBUTING.md#加一个新应用适配)。

### 设置界面

Miuix 风格：大标题 + 分组圆角卡片 + 行内开关，底部一条液态玻璃标签栏，共 5 页：
**桌面 / 底栏 / 相机 / 壁纸 / 关于**。「底栏」页里有总开关、11 个应用开关、
手势提示线 / 触摸保持 / 滑动动效以及两个滑块。

旧版 Compose 细节页仍留在源码里，但自 1.1.1 起已下线：不导出、界面上也没有入口。

## 运行要求

- Root + **LSPosed（API 102）**
- Android 13（API 33）以上，**arm64-v8a**
- 桌面 / 相机 / 壁纸与手势提示线依赖 HyperOS 的内部实现，非 HyperOS 或大改版系统上可能不生效
- 仅针对代码里标注的应用版本验证过

## 安装与启用

1. 从 [Releases](https://github.com/OWNER/REPO/releases) 下载 APK 并安装。
2. 在 LSPosed 里启用模块，确认作用域勾了你要用的应用（`scope.list` 已内置，首次仍建议手动核对）。
3. **重启目标应用**（或重启手机）。
4. 从 LSPosed 的模块齿轮，或控制中心「18duo 设置」磁贴进入设置；桌面图标可在「桌面」页里显隐。

> ⚠️ 不要和旧 HyperGlassFOLD / SoftGlassNav / `dev.hg.glass` **同时勾选同一个应用**。
> 两个模块往同一个进程注入浮层必然冲突，闪退时也请先排除这一点。

## 构建

需要 JDK 17、Android SDK Platform **37**、Build Tools 36.0.0（AGP 9.1.1 / Gradle 9.3.1 由 wrapper 提供）。

```bash
# macOS / Linux
./gradlew :app:assembleRelease
# Windows
gradlew.bat :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`。

release 目前使用**本机 debug key** 签名，方便覆盖安装调试；正式分发请换成自己的 keystore。
`app/proguard-rules.pro` 里的 `-repackageclasses` 与 `-dontoptimize` 是踩坑后的必要配置，
不要随手删除，原因见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#5-r8-的两条硬规则)。

Tag（`v*`）会触发 GitHub Actions 自动构建并把 APK 附到 Release。

## 当前状态

版本 **1.1.5**（versionCode 28）。发布前请留意这几项：

- 抖音的「5 秒无触摸自动隐藏、触摸即显示」**已通过编译，尚未在真机上验证**。
- 微博还有两个**尚未修复**的问题：胶囊后方残留一条浅灰色细条；胶囊左右两侧的区域点不到页面内容，
  会命中原生底栏的「首页 / 我」。根因已定位（原生 Tab 只被置为透明、仍保留点击；
  条带来自底栏容器的残留绘制），见 [CHANGELOG](CHANGELOG.md)。
- 1.1.4 为规避类名冲突，APK 从约 4.14 MB 增至约 5.7 MB。

其余已知限制：无 root / 未装 LSPosed 不生效；目标应用升级改了内部类名会失效；
高德底栏用 `PixelCopy` 采样地图 `SurfaceView`，属实验性适配；
手势提示线依赖 HyperOS SystemUI 的内部类。

## 目录

```
app/src/main/
├── java/com/aritxonly/
│   ├── deadliner/                 柔光玻璃材质与悬浮 Tab（平台无关）
│   └── myhypermodifier/
│       ├── fold/                  桌面 / 相机 / 壁纸 + 5 页设置主界面
│       │   ├── SettingsActivity.java
│       │   └── DockSettingsPage.java   「底栏」子页、11 个开关
│       ├── runtime/               Xposed 入口、设置快照、各应用 hook 分组
│       │   └── hyperglassify/    common / extra / douyin / ecommerce / social ...
│       ├── settings/ ui/ app/    设置存储、原 Compose 细节页（已下线）
│       └── systemui/              手势提示线
├── jniLibs/arm64-v8a/            预编译 native 库（源码见 jni/）
├── assets/licenses/              第三方许可文本
└── resources/META-INF/xposed/    模块声明与作用域
third_party/miuix-glass/           上游玻璃材质源码（作为 Kotlin sourceSet 编译）
jni/shim.c                         libhgshim.so 的源码
```

### 设置存储

两块功能共用一份 `modifier_settings` 偏好，但走两条通道：

- 底栏 / 手势提示线：`DockSettingsPage.java` 通过 `ModifierSettingsStore` 的 Java 桥读写，
  再镜像到 LSPosed remote preferences，目标进程由 `ModuleSettings.load()` 取快照。
- 桌面 / 相机 / 壁纸：`fold.subscreen.core.Prefs`，经 `/data/local/tmp` 掩码文件中转
  （桌面进程不初始化 LSPosed 的 Java 入口）。

新增开关时 key 必须同时改 `DockSettingsPage.java` 和 `ModuleSettings.load()`，
否则界面上的开关是摆设——1.1.0 修过一批这样的死代码。

## 把它当成模板

这份仓库整理成了可直接发布、也可用 `Use this template` 派生的形状：
改包名 / 名字的最小步骤、裁剪功能、签名与合规义务都写在 [TEMPLATE.md](TEMPLATE.md)。

更多文档：

| 文档 | 内容 |
| --- | --- |
| [TEMPLATE.md](TEMPLATE.md) | 发布成模板仓库、改名、裁剪、签名 |
| [docs/PUBLISHING.md](docs/PUBLISHING.md) | 发布前检查清单（隐私 / 许可 / 对应源码） |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 入口、设置双通道、底栏引擎、R8 约束 |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 环境、新增应用适配、提交自检 |
| [CHANGELOG.md](CHANGELOG.md) | 版本历史 |
| [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) | 第三方代码与许可 |

## 隐私

模块不联网、不上传数据。崩溃诊断日志只会写到**目标应用自己的私有目录**
（`/sdcard/Android/data/<包名>/files/duo18-diag.txt`），仅用于定位闪退，可随时删除。

## 许可与致谢

本整合项目遵循 **GPL-3.0**，见 [LICENSE](LICENSE)。第三方代码与许可见
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

上游项目：
[HyperModifier](https://github.com/AritxOnly/HyperModifier) ·
[HyperGlassFOLD](https://github.com/wuchen710/HyperGlassFOLD) ·
[Deadliner](https://github.com/AritxOnly/Deadliner) ·
[MIUIX](https://github.com/miuix-kotlin-multiplatform/miuix) ·
[AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)

> `libhyperglass.so` 是上游预编译载荷，本仓库不含其源码。
> 若你以 GPL-3.0 再分发，请先核实上游许可与源码获取方式，或在发布版中移除该库。
> 详见 [docs/PUBLISHING.md](docs/PUBLISHING.md)。
