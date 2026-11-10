# Native shim

`app/src/main/jniLibs/arm64-v8a/libhgshim.so` 由本目录的 `shim.c` 编译而来。
LSPosed 通过 `native_init.list` **对作用域内每个进程**加载它，所以它自己负责判断该不该继续：

- `is_desktop_process()` 读 `/proc/self/cmdline`，非 `com.miui.home` 直接返回，
  不建线程、不读掩码、不 `dlopen`；
- 桌面进程才会在 maps 里定位模块 APK、加载桌面玻璃载荷。

## 重新编译

需要 Android NDK 29（或兼容版本）与 aarch64 clang，API level 33：

```bash
NDK=/path/to/android-ndk-r29
$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android33-clang \
  -shared -fPIC -O2 -Wall \
  -o libhgshim.so shim.c -llog
cp libhgshim.so ../app/src/main/jniLibs/arm64-v8a/libhgshim.so
```

Windows 下把编译器路径里的 `linux-x86_64` 换成 `windows-x86_64`，可执行文件加 `.exe`。

产物只保留 `arm64-v8a`（`app/build.gradle.kts` 里 `abiFilters` 已限制）。

## 改包名时必做

`shim.c` 里用 `strstr(path, "duo18.plan")` 在 `/proc/self/maps` 中定位模块 APK。
改 `applicationId` 后必须同步修改这个字符串并**重新编译 `.so`**——
只改 Java 侧不会报错，但桌面玻璃会静默失效。详见 [TEMPLATE.md](../TEMPLATE.md)。

## 另一个二进制

`libhyperglass.so` 不在此目录，它是上游 HyperGlassFOLD 的桌面玻璃载荷，
本仓库仅携带预编译产物。分发前请阅读 [docs/PUBLISHING.md](../docs/PUBLISHING.md) 的合规说明。
