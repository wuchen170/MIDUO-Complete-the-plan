# 更新日志

版本历史从 1.1.0（界面重构）开始留档；更早的 1.0.x 为内部集成构建，未单独发布记录。
版本号对应 `app/build.gradle.kts` 里的 `versionName` / `versionCode`。

## 1.1.5 (28)

- 抖音：底栏改为「5 秒无触摸自动收起、任意触摸立即回来」，替换原来的滚动方向隐藏（视频流持续滚动时不再每划一下闪一次）。**该改动已通过编译，尚未在真机上验证。**
- 工程整理为可发布到 GitHub 的仓库：补齐 CI、Issue / PR 模板、第三方许可文本、native 源码与文档。

## 1.1.4 (27)

- 修复小红书启动即闪退（`VerifyError`）。
  - 根因：R8 把 5595 个混淆类放进了根包，类名如 `a` / `a0` / `k`；模块 dex 注入宿主后由宿主的 ClassLoader 链「父优先」解析，命中的是小红书自己的同名类，于是 `catch (AbortFlowException)` 的 handler 类型不再可赋值给 Throwable，ART 17 直接拒绝整个类。
  - 修法：加 `-repackageclasses 'duo18.plan.o'`，把所有混淆类搬进宿主不可能存在的独立包；同时保留 `-dontoptimize`。构建后 dex 中根包类数量为 0。
- 副作用：APK 从 4.14 MB 增至约 5.7 MB（换取类名不冲突）。

## 1.1.3 (26)

- 尝试通过关闭 R8 优化阶段（`-dontoptimize`）修复 `VerifyError`；验证了「非法 catch 不是优化产生」，为 1.1.4 定位到真正的类名冲突铺路。

## 1.1.2 (25)

- 修复「隐藏桌面图标」开关失效：主 Activity 之前同时带 `LAUNCHER` 和 `MODULE_SETTINGS`，绕过了别名组件；删除 `LAUNCHER` 后由 `.fold.Launcher` 独占控制。
- 给主线程绘制回调、滚动采样、attach 重试、触摸、生命周期回调加异常兜底，并限制内容铺底的全树扫描深度（12 层）。
- 新增 `ExtraCrashLog`：把未捕获异常与被兜住的异常写到目标应用私有目录的 `duo18-diag.txt`。

## 1.1.1 (24)

- 移除「底栏」页里通往原版 Compose 细节页的入口；该 Activity 仍保留但不再导出、无法从界面进入。
- `libhgshim.so` 增加 `is_desktop_process()`：只有 `com.miui.home` 才继续加载桌面玻璃载荷，避免 scope 扩到 15 个包后每个应用都 dlopen 一遍。

## 1.1.0 (23)

- 设置界面改为以 HyperGlassFOLD 为主体：5 个标签（桌面 / 底栏 / 相机 / 壁纸 / 关于），柔光底栏变成其中的「底栏」子页。
- 补齐 11 个应用的独立开关。
- 修复 `ModuleSettings.load()` 从未读取这 15 个新 key 的问题——此前界面上的开关是死代码。
