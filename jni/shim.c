// libhgshim.so —— 功能开关装载器
// 职责：在桌面进程里读取 Java 侧写下的功能位掩码，按掩码把 libhyperglass.so
// 程序表里的 groups[] 清零，再调用真正的 native_init。
// 这样"关掉某组"就等于该组永远不会生成补丁。
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <unistd.h>

typedef int (*HookFn)(void *, void *, void **);
typedef int (*UnhookFn)(void *);
typedef struct {
  uint32_t version;
  HookFn hook;
  UnhookFn unhook;
} NativeAPI;
typedef void (*LoadedFn)(const char *, void *);
typedef LoadedFn (*InitFn)(const NativeAPI *);

#define LOG(...) __android_log_print(4, "HyperGlassShim", __VA_ARGS__)
#include <android/log.h>

// logcat 缓冲在桌面启动阶段会被刷掉，关键结论另写一份到文件
static void trace(const char *fmt, ...) {
  char buf[512];
  va_list ap;
  va_start(ap, fmt);
  int n = vsnprintf(buf, sizeof buf, fmt, ap);
  va_end(ap);
  if (n <= 0) return;
  if (n > (int)sizeof buf - 2) n = (int)sizeof buf - 2;
  buf[n++] = '\n';
  const char *paths[] = {"/data/local/tmp/hgshim.txt", "/data/user/0/com.miui.home/files/hgshim.txt"};
  for (int i = 0; i < 2; ++i) {
    int fd = (int)syscall(SYS_openat, AT_FDCWD, paths[i], O_WRONLY | O_CREAT | O_APPEND, 0644);
    if (fd < 0) continue;
    syscall(SYS_write, fd, buf, (size_t)n);
    syscall(SYS_close, fd);
    return;
  }
}

// 本机 HyperOS 开了「应用数据隔离」：模块 App 的进程跑在独立 mount namespace
// 里，/data/data 是 tmpfs，只 bind 了它自己和少数几个应用，com.miui.home 不可见。
// 所以开关不能写在桌面自己的 files 目录里（App 那边根本看不到那个路径），
// 必须用一个双方共享、不受隔离影响的中转文件：/data/local/tmp 属于真实 /data。
static const char *kMaskPaths[] = {
    "/data/local/tmp/hg6325.mask",          // 首选：跨 namespace 可见
    "/sdcard/.hg6325.mask",                 // 备选：外置存储
    "/data/user/0/com.miui.home/files/hg6325.mask",
    "/data/data/com.miui.home/files/hg6325.mask",
    "/data/user_de/0/com.miui.home/files/hg6325.mask",
};
#define MASK_PATH_COUNT ((int)(sizeof(kMaskPaths) / sizeof(kMaskPaths[0])))
// Program 结构在 payload 里的固定 VA（1.0.2 / 三个镜像）
static const uint64_t kProgramVa[] = {0x14a2c0, 0x14a320, 0x14a380};

static int read_mask(void) {
  char buf[64];
  for (int i = 0; i < MASK_PATH_COUNT; ++i) {
    int fd = (int)syscall(SYS_openat, AT_FDCWD, kMaskPaths[i], O_RDONLY, 0);
    if (fd < 0) continue;
    ssize_t n = syscall(SYS_read, fd, buf, sizeof(buf) - 1);
    syscall(SYS_close, fd);
    if (n <= 0) continue;
    buf[n] = 0;
    int mask = 0;
    if (sscanf(buf, "%d", &mask) == 1) return mask;
  }
  return -2;  // 文件还没有
}

static int wait_mask(void) {
  int mask = read_mask();
  if (mask != -2) return mask;
  for (int i = 0; i < 15; ++i) {  // 最多等 1.5 秒，等配置文件落盘
    struct timespec d = {0, 100 * 1000 * 1000};
    syscall(SYS_nanosleep, &d, 0);
    mask = read_mask();
    if (mask != -2) return mask;
  }
  return -1;  // 拿不到就不过滤，保持默认行为
}

static int apk_path(char *out, size_t cap) {
  FILE *f = fopen("/proc/self/maps", "re");
  if (!f) return 0;
  char line[2048];
  int found = 0;
  while (fgets(line, sizeof line, f)) {
    char perm[8], path[1600];
    unsigned long lo, hi, off;
    if (sscanf(line, "%lx-%lx %7s %lx %*s %*s %1599[^\n]", &lo, &hi, perm, &off, path) != 5) continue;
    char *apk = strstr(path, ".apk");
    if (!apk) continue;
    if (!strstr(path, "duo18.plan") && !strstr(path, "HyperGlass")) continue;
    size_t len = (size_t)(apk - path) + 4;
    if (len >= cap) continue;
    memcpy(out, path, len);
    out[len] = 0;
    found = 1;
    break;
  }
  fclose(f);
  return found;
}

static void apply_mask(uintptr_t base, int mask) {
  if (mask < 0) return;
  size_t page = (size_t)sysconf(_SC_PAGESIZE);
  for (unsigned i = 0; i < sizeof(kProgramVa) / sizeof(kProgramVa[0]); ++i) {
    uintptr_t prog = base + kProgramVa[i];
    uint64_t groups = *(volatile uint64_t *)(prog + 64);
    uint64_t count = *(volatile uint64_t *)(prog + 72);
    if (!groups || !count || count > 8) continue;
    uintptr_t begin = groups & ~(page - 1);
    uintptr_t end = (groups + count * 4 + page - 1) & ~(page - 1);
    if (syscall(SYS_mprotect, (void *)begin, end - begin, PROT_READ | PROT_WRITE)) continue;
    uint32_t *slots = (uint32_t *)groups;
    for (uint64_t n = 0; n < count; ++n) {
      uint32_t g = slots[n];
      if (g && !(mask & g)) slots[n] = 0;
    }
    syscall(SYS_mprotect, (void *)begin, end - begin, PROT_READ);
  }
}

// ---- 让 BlurUtilities.isGlassMaterialSupported 恒返回 true --------------
// 6325 的桌面把第三方 AppWidget 判成「不支持玻璃材质」，于是米家这类小组件
// 只能画成不透明白卡片。该函数是 Dart 层唯一的判定入口，返回值是 Dart bool
// 对象：x22+0x30 为 true、x22+0x20 为 false。把它改成
//     mov x0, x22 ; add x0, x0, #0x30 ; ret
// 即可让所有「是否支持玻璃」的分支都走玻璃路径。
#define MAPS_MAX 128
typedef struct {
  uintptr_t lo, hi;
} Range;
static Range g_range[MAPS_MAX];
static int g_ranges;

static int collect_apk_ranges(void) {
  FILE *f = fopen("/proc/self/maps", "re");
  if (!f) return 0;
  char line[2048];
  g_ranges = 0;
  while (fgets(line, sizeof line, f) && g_ranges < MAPS_MAX) {
    unsigned long lo, hi, off;
    char perm[8], path[1500];
    int n = sscanf(line, "%lx-%lx %7s %lx %*s %*s %1499[^\n]", &lo, &hi, perm, &off, path);
    if (n != 5) continue;
    if (perm[0] != 'r') continue;
    if (!strstr(path, "com.miui.home") || !strstr(path, "base.apk")) continue;
    g_range[g_ranges].lo = (uintptr_t)lo;
    g_range[g_ranges].hi = (uintptr_t)hi;
    g_ranges++;
  }
  fclose(f);
  return g_ranges;
}

static int readable(uintptr_t at, size_t len) {
  for (int i = 0; i < g_ranges; ++i)
    if (at >= g_range[i].lo && at + len <= g_range[i].hi) return 1;
  return 0;
}

// 目标：libapp.so 的 BlurUtilities.isGlassMaterialSupported 入口
#define PATCH_VA 0x106bf70u
#define PROBE_ORIG 0xa9bf79fdu  // stp x29,x30,[sp,#-0x10]!

// 已验证：本机 mprotect 加上 PROT_WRITE|PROT_EXEC 是允许的，直接改写即可。
// （更保守的「先降 RW 再升 RX + /proc/self/mem 兜底」写法备份在
//   shim_2.1_twostep.c.pending，本项目未采用，避免多线程执行中掉 EXEC。）
static int patch_glass_supported(void) {
  int nr = collect_apk_ranges();
  trace("collect ranges=%d", nr);
  if (nr <= 0) {
    LOG("libapp ranges not found");
    return 0;
  }
  size_t page = (size_t)sysconf(_SC_PAGESIZE);
  for (int i = 0; i < g_ranges; ++i) {
    uintptr_t base = g_range[i].lo;
    if (*(volatile uint32_t *)base != 0x464c457fu) continue;  // ELF magic
    uintptr_t entry = base + PATCH_VA;
    trace("try base=%p elf, entry=%p", (void *)base, (void *)entry);
    if (!readable(entry, 12) || !readable(base, 4)) { trace("  not readable"); continue; }
    trace("  word=%08x", *(volatile uint32_t *)entry);
    if (*(volatile uint32_t *)entry != PROBE_ORIG) continue;
    uintptr_t begin = entry & ~(page - 1);
    uintptr_t end = (entry + 12 + page - 1) & ~(page - 1);
    if (syscall(SYS_mprotect, (void *)begin, end - begin, PROT_READ | PROT_WRITE | PROT_EXEC)) {
      LOG("mprotect failed for libapp");
      trace("mprotect failed errno page=%p", (void *)begin);
      return 0;
    }
    volatile uint32_t *p = (volatile uint32_t *)entry;
    p[0] = 0xaa1603e0u;  // mov x0, x22
    p[1] = 0x9100c2c0u;  // add x0, x22, #0x30   (Dart true)
    p[2] = 0xd65f03c0u;  // ret
    syscall(SYS_mprotect, (void *)begin, end - begin, PROT_READ | PROT_EXEC);
    __builtin___clear_cache((char *)entry, (char *)(entry + 12));
    LOG("glass supported forced true, base=%p", (void *)base);
    trace("PATCHED base=%p entry=%p now=%08x", (void *)base, (void *)entry,
          *(volatile uint32_t *)entry);
    return 1;
  }
  LOG("libapp pattern not matched");
  trace("no candidate matched");
  return 0;
}

// native_init.list 是全局的：LSPosed 会对 scope 里每一个包都加载本 .so，包括
// 支付宝、淘宝、小红书这些跟桌面毫无关系的应用。以前 scope 只有桌面+相机+壁纸，
// 现在有十几个包，如果每个进程都去 dlopen 1.3MB 的 libhyperglass.so 并执行它的
// native_init，轻则白白多等 1.5 秒、白占一份内存，重则把桌面专用的补丁打到
// 别人的进程里直接闪退（小红书就是这样挂的）。所以这里只认桌面主进程，
// 其他进程立刻返回，连线程都不建。
static int is_desktop_process(void) {
  char buf[256];
  int fd = (int)syscall(SYS_openat, AT_FDCWD, "/proc/self/cmdline", O_RDONLY, 0);
  if (fd < 0) return 0;
  ssize_t n = syscall(SYS_read, fd, buf, sizeof buf - 1);
  syscall(SYS_close, fd);
  if (n <= 0) return 0;
  buf[n] = 0;  // cmdline 是 '\0' 分隔的 argv，第一个参数即进程名
  return strcmp(buf, "com.miui.home") == 0;
}

static void *worker(void *arg) {
  const NativeAPI *api = (const NativeAPI *)arg;
  int mask = wait_mask();
  LOG("mask=%d pid=%d", mask, getpid());
  trace("== worker start mask=%d pid=%d", mask, getpid());

  char apk[1700];
  if (!apk_path(apk, sizeof apk)) {
    LOG("apk path not found");
    return 0;
  }
  char lib[1800];
  snprintf(lib, sizeof lib, "%s!/lib/arm64-v8a/libhyperglass.so", apk);
  void *handle = dlopen(lib, RTLD_NOW);
  if (!handle) {
    LOG("dlopen failed: %s", dlerror());
    return 0;
  }
  InitFn init = (InitFn)dlsym(handle, "native_init");
  if (!init) {
    LOG("native_init missing");
    return 0;
  }
  Dl_info info;
  if (dladdr((void *)init, &info) && info.dli_fbase) {
    apply_mask((uintptr_t)info.dli_fbase, mask);
    LOG("groups filtered, base=%p", info.dli_fbase);
  }
  // 第 4 位 = Android 小组件玻璃化：让 Dart 层的「是否支持玻璃材质」恒为真
  if (mask > 0 && (mask & 4)) patch_glass_supported();
  LoadedFn loaded = init(api);
  (void)loaded;
  LOG("forwarded to payload");
  return 0;
}

__attribute__((visibility("default"), used)) LoadedFn
native_init(const NativeAPI *api) {
  if (!api || !api->hook) return 0;
  // 非桌面进程直接放行：不建线程、不读 mask、不 dlopen payload。
  if (!is_desktop_process()) return 0;
  pthread_t t;
  if (pthread_create(&t, 0, worker, (void *)api)) return 0;
  pthread_setname_np(t, "hgshim");
  pthread_detach(t);
  return 0;
}
