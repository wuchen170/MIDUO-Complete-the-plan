package com.aritxonly.myhypermodifier;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Choreographer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * LSPosed entry point of the standalone soft-glass navigation module.
 *
 * <p>Scope is deliberately limited to four target apps plus SystemUI:
 * <ul>
 *   <li>Alipay — classic launcher tab bar</li>
 *   <li>Mi Home — {@code TabPageIndicatorNew} floating bar</li>
 *   <li>Amap — native {@code LiteTabBar} floating bar</li>
 *   <li>Bilibili — Deadliner soft-glass tab capsule</li>
 *   <li>SystemUI — gesture handle (bottom pill) appearance and reveal behaviour</li>
 * </ul>
 */
public final class MyHyperModifier extends XposedModule {
    private static final String TAG = "SoftGlassNav";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String ALIPAY = "com.eg.android.AlipayGphone";
    private static final String MI_HOME = "com.xiaomi.smarthome";
    private static final String AMAP = "com.autonavi.minimap";
    private static final String BILIBILI = "tv.danmaku.bili";
    private static final String TAOBAO = "com.taobao.taobao";
    private static final String JD = "com.jingdong.app.mall";
    private static final String QQ = "com.tencent.mobileqq";
    private static final String QQ_MUSIC = "com.tencent.qqmusic";
    private static final String XIAOHONGSHU = "com.xingin.xhs";
    private static final String WEIBO = "com.sina.weibo";
    private static final String DOUYIN = "com.ss.android.ugc.aweme";

    private static final AtomicBoolean HANDLE_HOOKS_SCHEDULED = new AtomicBoolean();
    private static final AtomicBoolean HANDLE_HOOKS_RUNNING = new AtomicBoolean();
    private static final ScheduledExecutorService DEFERRED_WORKER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "SoftGlassNav-systemui-deferred");
                thread.setDaemon(true);
                return thread;
            });
    private static final long FALLBACK_DELAY_MS = 5_000L;
    private static final long FRAME_SETTLE_DELAY_MS = 300L;

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        String packageName = param.getPackageName();
        String processName = Application.getProcessName();
        if (!packageName.equals(processName)) return;

        if (!ALIPAY.equals(packageName) && !MI_HOME.equals(packageName)
                && !AMAP.equals(packageName) && !BILIBILI.equals(packageName)
                && !TAOBAO.equals(packageName) && !JD.equals(packageName)
                && !QQ.equals(packageName) && !QQ_MUSIC.equals(packageName)
                && !XIAOHONGSHU.equals(packageName) && !WEIBO.equals(packageName)
                && !DOUYIN.equals(packageName)
                && !SYSTEM_UI.equals(packageName)) {
            return;
        }

        SharedPreferences preferences;
        try {
            preferences = getRemotePreferences("modifier_settings");
        } catch (Throwable error) {
            Log.w(TAG, "Remote preferences unavailable for " + packageName, error);
            return;
        }
        ModuleSettings.setRemotePreferences(preferences);

        if (SYSTEM_UI.equals(packageName)) {
            // SystemUI needs the saved gesture-handle mode before its first draw callback.
            ModuleSettings.loadImmediately();
            scheduleDeferredHandleHooks(param.getClassLoader());
            return;
        }

        ModuleSettings.ensureLoaded();
        ClassLoader classLoader = param.getClassLoader();
        if (ALIPAY.equals(packageName)) {
            installSafely(packageName, "支付宝经典底栏",
                    () -> AlipayHooks.install(this, classLoader));
        } else if (MI_HOME.equals(packageName)) {
            installSafely(packageName, "米家悬浮底栏",
                    () -> MiHomeHooks.install(this, classLoader));
        } else if (AMAP.equals(packageName)) {
            installSafely(packageName, "高德地图悬浮底栏",
                    () -> AmapHooks.install(this, classLoader));
        } else if (BILIBILI.equals(packageName)) {
            installSafely(packageName, "哔哩哔哩悬浮底栏",
                    () -> BilibiliHooks.install(this, classLoader));
        } else if (TAOBAO.equals(packageName) || JD.equals(packageName)) {
            installSafely(packageName, "电商悬浮底栏",
                    () -> EcommerceHooks.install(this, classLoader, packageName));
        } else if (QQ_MUSIC.equals(packageName)) {
            installSafely(packageName, "QQ音乐悬浮底栏",
                    () -> QQMusicHooks.install(this, classLoader));
        } else if (DOUYIN.equals(packageName)) {
            installSafely(packageName, "抖音悬浮底栏",
                    () -> DouyinHooks.install(this, classLoader, packageName));
        } else if (XIAOHONGSHU.equals(packageName) || WEIBO.equals(packageName)) {
            installSafely(packageName, "悬浮底栏",
                    () -> ExtraAppsHooks.install(this, classLoader, packageName));
        } else {
            installSafely(packageName, "QQ 悬浮底栏",
                    () -> SocialHooks.install(this, classLoader, packageName));
        }
    }

    /**
     * Other LSPosed modules also hook {@code ClassLoader.loadClass}. Installing SystemUI hooks
     * inside that nested callback can exceed the process-startup ANR deadline, so the handle
     * hooks are installed after the first frame on a worker thread, with a timed fallback.
     */
    private void scheduleDeferredHandleHooks(ClassLoader classLoader) {
        if (classLoader == null || !HANDLE_HOOKS_SCHEDULED.compareAndSet(false, true)) return;
        Runnable install = () -> {
            if (!HANDLE_HOOKS_RUNNING.compareAndSet(false, true)) return;
            if (!ModuleSettings.moduleHooksEnabled) return;
            installSafely(SYSTEM_UI, "手势提示线",
                    () -> GestureHandleHooks.install(this, classLoader));
        };
        try {
            DEFERRED_WORKER.schedule(install, FALLBACK_DELAY_MS, TimeUnit.MILLISECONDS);
            Choreographer.getInstance().postFrameCallback(frame -> DEFERRED_WORKER.schedule(
                    install, FRAME_SETTLE_DELAY_MS, TimeUnit.MILLISECONDS));
        } catch (Throwable error) {
            HANDLE_HOOKS_SCHEDULED.set(false);
            HookDiagnostics.failure(SYSTEM_UI, "手势提示线调度", error);
        }
    }

    @FunctionalInterface
    private interface HookInstall {
        void run() throws Throwable;
    }

    private void installSafely(String packageName, String feature, HookInstall install) {
        try {
            install.run();
            HookDiagnostics.ready(packageName);
        } catch (Throwable error) {
            if (error instanceof VirtualMachineError || error instanceof ThreadDeath) throw (Error) error;
            HookDiagnostics.failure(packageName, feature, error);
        }
    }
}
