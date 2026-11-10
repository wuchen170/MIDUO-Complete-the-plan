# 发布前检查清单

在把仓库 `push` 到 GitHub 之前，按这份单子过一遍。

## 必须处理

- [ ] **占位符**：全文搜索 `OWNER/REPO`，替换成真实仓库地址（`README.md`、`.github/ISSUE_TEMPLATE/config.yml`）。
- [ ] **签名**：当前 release 用本机 debug key。正式分发请换成自己的 keystore，凭据只放 `local.properties` / 环境变量；确认 `*.jks`、`*.keystore`、`keystore.properties` 已被 `.gitignore` 排除。
- [ ] **隐私**：全文搜索本机用户名、SDK 绝对路径、设备序列号、`/sdcard/` 之外的调试路径。Issue / PR 模板里也已提示提交者不要贴设备标识。
- [ ] **构建产物**：仓库里不应有 `app/build/`、`build/`、`.gradle/`、`.kotlin/`、`*.apk`、`local.properties`。
- [ ] **版本号**：`app/build.gradle.kts` 的 `versionCode` / `versionName` 与 `CHANGELOG.md` 最新条目一致。
- [ ] **截图**：`screenshots/` 里放真实截图，或保留说明占位，但不要留失效链接。

## 合规（重要）

- [ ] `LICENSE` 为 GPL-3.0 全文；`THIRD_PARTY_NOTICES.md` 列出了全部第三方来源。
- [ ] `app/src/main/assets/licenses/` 里 4 份 MIT / Apache-2.0 文本齐全，并且确实打进了 APK。
- [ ] **`libhgshim.so` 的源码在 `jni/shim.c`**，编译命令在 `jni/README.md`。GPL-3.0 要求分发的二进制有对应源码，这一项不能只留 `.so`。
- [ ] **`libhyperglass.so` 是上游 HyperGlassFOLD 的桌面玻璃载荷**，本仓库只带了预编译产物，没有它的源码。
      如果你以 GPL-3.0 发布整个仓库，请先确认上游的许可与源码获取方式，或在发布版中移除该库与
      `native_init.list`，只保留柔光底栏部分。**这一项请你自己核实后决定**，不要默认已经合规。
- [ ] 若新增 / 替换了任何第三方代码或资源，同步更新 `THIRD_PARTY_NOTICES.md` 与许可文本。

## 可选但建议

- [ ] 在 GitHub 仓库设置里勾选 `Template repository`（见 [TEMPLATE.md](../TEMPLATE.md)）。
- [ ] 打一个 `v1.1.5` tag，确认 `.github/workflows/build.yml` 跑通并自动附上 APK。
      注意 CI 需要 SDK Platform 37；若稳定通道没有，workflow 会尝试预览通道，
      本地则需要用 SDK Manager 手动安装。
- [ ] 在 Release 说明里写清楚：需要 root + LSPosed API 102、作用域列表、不要与其它同类模块同时勾同一进程。
- [ ] 给仓库加 `lsposed`、`xposed`、`hyperos`、`compose`、`android` 等 topic，方便检索。

## 已知会带到发布版里的状态

见 [README 的「当前状态」](../README.md#当前状态)：抖音闲置隐藏改动尚未实机验证，
微博的灰条与左右侧触摸问题尚未修复。发布前请决定是修完再发，还是在 README 里如实标注。
