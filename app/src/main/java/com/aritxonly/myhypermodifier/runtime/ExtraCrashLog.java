package com.aritxonly.myhypermodifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 目标进程内的异常落盘。
 *
 * <p>宿主应用的崩溃栈只在 logcat 里，没接数据线时完全看不到。这里把两类信息写到该应用
 * 自己的外部私有目录（{@code /sdcard/Android/data/&lt;pkg&gt;/files/duo18-diag.txt}）：
 * <ul>
 *   <li>{@code CRASH} —— 未捕获异常（进程真的要死了）；</li>
 *   <li>其它 tag —— 被模块防护网吞掉的异常（没崩，但说明某条路径有问题）。</li>
 * </ul>
 *
 * <p>文件由宿主应用自己写自己的目录，不需要任何权限；单文件上限 256 KB，条目也做了节流，
 * 避免刷盘拖慢主线程。
 */
final class ExtraCrashLog {

    private static final Object LOCK = new Object();
    private static final int MAX_NOTES = 40;
    private static final long MAX_BYTES = 256L * 1024L;

    private static String packageName;
    private static boolean installed;
    private static int notes;

    private ExtraCrashLog() {
    }

    /** 只安装一次；pkg 用于定位该应用的 external files 目录。 */
    static void install(String pkg) {
        synchronized (LOCK) {
            packageName = pkg;
            if (installed) return;
            installed = true;
        }
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            write(thread.getName(), "CRASH", error);
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    static void note(String tag, Throwable error) {
        if (error == null) return;
        synchronized (LOCK) {
            if (notes >= MAX_NOTES) return;
            notes++;
        }
        write(Thread.currentThread().getName(), tag, error);
    }

    private static void write(String thread, String tag, Throwable error) {
        final String pkg;
        synchronized (LOCK) {
            pkg = packageName;
        }
        if (pkg == null) return;
        try {
            File dir = new File("/sdcard/Android/data/" + pkg + "/files");
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            File file = new File(dir, "duo18-diag.txt");
            if (file.length() > MAX_BYTES) return;
            StringWriter buffer = new StringWriter();
            PrintWriter writer = new PrintWriter(buffer);
            writer.println("---- "
                    + new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date())
                    + " [" + tag + "] thread=" + thread);
            error.printStackTrace(writer);
            writer.flush();
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write(buffer.toString().getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {
            // 落盘失败绝不能影响宿主
        }
    }
}
