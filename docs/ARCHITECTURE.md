# 架构速览

给要改代码的人看的地图。更细的适配说明在 [CONTRIBUTING.md](../CONTRIBUTING.md)。

## 1. 入口：两条 Java 链 + 一条 native 链

`app/src/main/resources/META-INF/xposed/`：

| 文件 | 内容 | 作用 |
| --- | --- | --- |
| `module.prop` | `minApiVersion=102` / `staticScope=true` / `exceptionMode=protective` | LSPosed API 102 模块声明 |
| `java_init.list` | `MyHyperModifier`、`fold.CameraModule` | 每个作用域进程都会加载的 Java 入口 |
| `native_init.list` / `assets/native_init` | `libhgshim.so` | **全局** native 入口，LSPosed 无法按包区分 |
| `scope.list` | 15 个包名 | 静态作用域 |

`MyHyperModifier` 按包名把 hook 路由到各组；`CameraModule` 负责外屏相机 / 壁纸。
`libhgshim.so` 因为是全局加载，内部 `is_desktop_process()` 读 `/proc/self/cmdline`，
只有 `com.miui.home` 才继续 `dlopen` 桌面玻璃载荷——否则每个作用域应用都会白等一次超时并多占一份内存，
某些宿主还会因此崩溃。

## 2. 设置存储：两条通道

- **底栏 / 手势提示线**：`fold/DockSettingsPage.java` 用 `ModifierSettingsStore` 的
  `readBoolean/writeBoolean/readFloat` 桥读写偏好，并镜像到 LSPosed remote preferences，
  目标进程通过 `runtime/ModuleSettings.java` 的 `load()` 拿快照。
- **桌面 / 相机 / 壁纸**：`fold.subscreen.core.Prefs`，桌面进程不初始化 LSPosed 的 Java 入口，
  所以经 `/data/local/tmp` 的掩码文件中转。

新增开关必须同时改「写入的 key」和 `ModuleSettings.load()` 里「读的 key」。

## 3. 底栏引擎 `runtime/hyperglassify/common/`

- `NativeViewBottomBarNavigation` 是总控：`attach → 找原生栏 → 建宿主 → 逐帧同步`。
- 原生栏不 `GONE`、不 `remove`，只把 `alpha` 置 0，保留引用与测量结果，
  否则宿主重建布局时实例会失效。
- **内容铺底**：`captureStretchTargets()` 全树找「底边贴住原生栏顶、横向覆盖」的页面视图，
  把原生栏高度交给页面（固定高度加 strip / 加权视图减 bottomMargin / 回收父容器 padding），
  `reassertContentStretch()` 每帧重新断言并限制扫描深度。
- **触摸穿透**：`ShadowPassThroughFrameLayout` 只按胶囊真实矩形命中，其余区域交还宿主；
  胶囊滑出屏幕时 `dockHidden` 让整条带子重新可点。
- **隐藏策略**：默认按滚动方向（`ScrollDrivenNavigationVisibility`）；
  `NativeViewBottomBarTarget.idleAutoHideMs > 0` 时改为闲置计时（抖音 5 秒），
  任意触摸通过 `onTouchEvent` 重置计时并立即展开。
- **背景采样**：`PixelCopy` / `WindowGpuBackdropSampler` / `DouyinSurfaceBackdrop` 按目标切换。

## 4. 主线程上的异常约束

模块的很多回调跑在宿主的 `onPreDraw`、帧回调、`decorView.post` 里。
这些地方抛异常会**直接杀死宿主进程**，`exceptionMode=protective` 也拦不住。
因此 preDraw、scrollTicker、attach 重试、touch、生命周期回调都做了 `try/catch`，
并通过 `ExtraCrashLog` 把栈写到目标应用私有目录的 `duo18-diag.txt`。

## 5. R8 的两条硬规则

`app/proguard-rules.pro`：

- `-repackageclasses 'duo18.plan.o'`：混淆类**不能**留在根包。模块 dex 注入宿主后按
  「父优先」解析类名，`a` / `k` 这类名字会命中宿主自己的混淆类，
  导致 `catch (AbortFlowException)` 的 handler 不是 Throwable，ART 17 抛 `VerifyError`。
- `-dontoptimize`：避免 R8 优化阶段在 Compose / coroutines 字节码上产生验证器不接受的形式。

改动这两条之前，请先真机跑一遍小红书（对类名冲突最敏感）和一个桌面应用。

## 6. 目录

```
app/src/main/
├── java/com/aritxonly/
│   ├── deadliner/              柔光玻璃材质与悬浮 Tab（平台无关）
│   ├── myhypermodifier/
│   │   ├── fold/               桌面 / 相机 / 壁纸 + Miuix 风格设置主界面
│   │   ├── runtime/            Xposed 入口、设置快照、各应用 hook 分组
│   │   ├── settings/ ui/ app/ 设置存储、原 Compose 细节页（已下线但保留）
│   │   └── systemui/           手势提示线
├── jniLibs/arm64-v8a/         预编译 native 库（源码见仓库根目录 jni/）
├── assets/native_init         LSPosed native 入口
└── resources/META-INF/xposed/ 模块声明
third_party/miuix-glass/       上游玻璃材质源码（作为 Kotlin sourceSet 编译）
```
