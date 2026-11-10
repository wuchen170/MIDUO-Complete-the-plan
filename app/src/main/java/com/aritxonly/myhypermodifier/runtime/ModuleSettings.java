package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-local snapshot of the companion app's settings.
 *
 * <p>Trimmed port of the upstream HyperModifier runtime settings: only the keys required by the
 * soft-glass bottom bar adapters (Alipay / Mi Home / Amap / Bilibili) and the gesture handle are
 * loaded. The loading, retry and change-notification mechanics are kept identical to upstream so
 * the adapters can keep using the same call contract.
 */
final class ModuleSettings {
    private static final String TAG = "SoftGlassNav";
    private static final AtomicBoolean SETTINGS_LOADED = new AtomicBoolean();
    private static final AtomicBoolean LOAD_IN_FLIGHT = new AtomicBoolean();
    private static final AtomicBoolean RELOAD_REQUESTED = new AtomicBoolean();
    private static final AtomicBoolean LOAD_FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean REMOTE_LISTENER_REGISTERED = new AtomicBoolean();
    private static final AtomicInteger LOAD_FAILURES = new AtomicInteger();
    private static final AtomicLong NEXT_LOAD_UPTIME_MS = new AtomicLong();
    private static final CopyOnWriteArrayList<Runnable> LOAD_LISTENERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Runnable> CHANGE_LISTENERS = new CopyOnWriteArrayList<>();
    private static final ExecutorService SETTINGS_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SoftGlassNav-settings");
        thread.setDaemon(true);
        return thread;
    });

    static volatile boolean moduleHooksEnabled = true;
    /** Large-screen behaviour override: 0 auto, 1 phone, 2 foldable, 3 tablet. */
    static volatile int deviceModeOverride = LargeScreenDevicePolicy.AUTO;
    static volatile String diagnosticToken = "";
    private static volatile String lastLoadStatus = "not-requested";
    private static volatile SharedPreferences remotePreferences;

    /** Serialized navigation glass recipe consumed by NavigationGlassRuntime. */
    static volatile String navigationGlassParameters = "";

    /** Extra bottom inset (dp) applied when the native navigation bar is hidden. */
    static volatile float hyperGlassifyHiddenNavigationLift =
            ModuleDefaultValues.HYPER_GLASSIFY_HIDDEN_NAVIGATION_LIFT;

    // ---- Gesture handle ----------------------------------------------------
    static volatile boolean gestureHandleEnabled = false;
    static volatile Map<String, String> gestureHandleAppModes = Collections.emptyMap();
    static volatile String gestureHandlePreset = GestureHandlePresets.STOCK;
    static volatile boolean gestureHandleTouchReveal = false;
    static volatile boolean gestureHandleSwipeMotion = false;
    static volatile float gestureHandleTouchAreaDp = GestureHandleTouchArea.DEFAULT_DP;
    static volatile Set<String> gestureHandleScopePackages = Collections.emptySet();

    // ---- Mi Home -----------------------------------------------------------
    static volatile boolean miHomeFloatingNavigationEnabled =
            ModuleDefaultValues.MI_HOME_FLOATING_NAVIGATION_ENABLED;
    static volatile boolean miHomeMiuixIconsEnabled = false;
    static volatile boolean miHomeNavigationBadgesEnabled = true;

    // ---- Amap --------------------------------------------------------------
    static volatile boolean amapFloatingNavigationEnabled =
            ModuleDefaultValues.AMAP_FLOATING_NAVIGATION_ENABLED;
    static volatile boolean amapMiuixIconsEnabled = false;
    static volatile boolean amapMonochromeIconsEnabled = true;
    static volatile boolean amapHideLongPressVoiceTabEnabled =
            ModuleDefaultValues.AMAP_HIDE_LONG_PRESS_VOICE_TAB_ENABLED;

    // ---- Alipay ------------------------------------------------------------
    static volatile boolean alipayFloatingNavigationEnabled =
            ModuleDefaultValues.ALIPAY_FLOATING_NAVIGATION_ENABLED;

    // ---- Bilibili ----------------------------------------------------------
    static volatile boolean bilibiliFloatingNavigationEnabled =
            ModuleDefaultValues.BILIBILI_FLOATING_NAVIGATION_ENABLED;
    static volatile boolean bilibiliNavigationBadgesEnabled = true;
    static volatile boolean bilibiliHomeTabVisible = true;
    static volatile boolean bilibiliFollowTabVisible = true;
    static volatile boolean bilibiliDynamicTabVisible = true;
    static volatile boolean bilibiliMallTabVisible = true;
    static volatile boolean bilibiliMineTabVisible = true;
    static volatile boolean bilibiliPublishButtonVisible = true;

    // ---- Shopping / social ------------------------------------------------
    // Upstream adapters ship with per-app switches; only the three wired apps are reachable from
    // the entry point, the rest stay off so an edited preset can never dress up a foreign app.
    static volatile boolean taobaoFloatingNavigationEnabled = true;
    static volatile boolean jingdongFloatingNavigationEnabled = true;
    static volatile boolean qqFloatingNavigationEnabled = true;
    static volatile boolean qqNavigationBadgesEnabled = true;
    static volatile boolean pinduoduoFloatingNavigationEnabled = false;
    static volatile boolean xianyuFloatingNavigationEnabled = false;
    static volatile boolean douyinMallFloatingNavigationEnabled = false;
    static volatile boolean cainiaoFloatingNavigationEnabled = false;
    static volatile boolean weChatFloatingNavigationEnabled = false;
    static volatile boolean weChatNavigationBadgesEnabled = true;

    // ---- Douyin / Xiaohongshu / Weibo -------------------------------------
    static volatile boolean douyinFloatingNavigationEnabled = true;
    static volatile boolean douyinLiteFloatingNavigationEnabled = false;
    static volatile boolean xiaohongshuFloatingNavigationEnabled = true;
    static volatile boolean weiboFloatingNavigationEnabled = true;

    // ---- QQ Music ---------------------------------------------------------
    static volatile boolean qqMusicFloatingNavigationEnabled =
            ModuleDefaultValues.QQ_MUSIC_FLOATING_NAVIGATION_ENABLED;

    private ModuleSettings() {
    }

    /**
     * Connects this target process to the LSPosed-owned preferences bridge. Must be called from
     * the Xposed entry point: target apps cannot reliably resolve the module's provider.
     */
    static void setRemotePreferences(SharedPreferences preferences) {
        if (preferences == null) {
            moduleHooksEnabled = false;
            diagnosticToken = "";
            return;
        }
        remotePreferences = preferences;
        try {
            moduleHooksEnabled = preferences.getBoolean("module_hooks_enabled", true);
            String token = preferences.getString("diagnostic_token", "");
            diagnosticToken = token == null ? "" : token;
        } catch (Throwable throwable) {
            moduleHooksEnabled = false;
            diagnosticToken = "";
            Log.w(TAG, "Could not read early hook safety gates", throwable);
        }
        if (!REMOTE_LISTENER_REGISTERED.compareAndSet(false, true)) return;
        try {
            preferences.registerOnSharedPreferenceChangeListener(
                    (sharedPreferences, key) -> scheduleReload());
        } catch (Throwable throwable) {
            // Read-only bridges still deliver the initial snapshot; a scope restart refreshes.
            Log.w(TAG, "Remote settings changes are not observable", throwable);
        }
    }

    /** Never performs provider IPC on SystemUI's startup or resource-resolution thread. */
    static void markLoaded(Context context) {
        scheduleLoad(context);
    }

    static boolean loadImmediately(Context context) {
        if (SETTINGS_LOADED.get()) return true;
        if (!LOAD_IN_FLIGHT.compareAndSet(false, true)) return SETTINGS_LOADED.get();
        try {
            if (!load()) {
                scheduleRetry();
                return false;
            }
            publishLoadedSettings();
            return true;
        } finally {
            finishLoad();
        }
    }

    static boolean loadImmediately() {
        if (SETTINGS_LOADED.get()) return true;
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object application = activityThread.getMethod("currentApplication").invoke(null);
            return application instanceof Context && loadImmediately((Context) application);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void ensureLoaded() {
        if (SETTINGS_LOADED.get()) return;
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object application = activityThread.getMethod("currentApplication").invoke(null);
            if (application instanceof Context) scheduleLoad((Context) application);
        } catch (Throwable ignored) {
            // The application has not been attached yet; a later hooked call retries safely.
        }
    }

    static boolean isLoaded() {
        return SETTINGS_LOADED.get();
    }

    static String loadStatus() {
        return lastLoadStatus;
    }

    static void onLoaded(Runnable listener) {
        if (SETTINGS_LOADED.get()) {
            listener.run();
            return;
        }
        LOAD_LISTENERS.add(listener);
        if (SETTINGS_LOADED.get() && LOAD_LISTENERS.remove(listener)) listener.run();
    }

    static void onChanged(Runnable listener) {
        CHANGE_LISTENERS.addIfAbsent(listener);
        if (SETTINGS_LOADED.get()) listener.run();
    }

    private static void notifyLoaded() {
        for (Runnable listener : LOAD_LISTENERS) {
            if (LOAD_LISTENERS.remove(listener)) {
                try {
                    listener.run();
                } catch (Throwable throwable) {
                    Log.w(TAG, "Settings load listener failed", throwable);
                }
            }
        }
    }

    private static void scheduleLoad(Context context) {
        if (SETTINGS_LOADED.get() || remotePreferences == null) return;
        long now = SystemClock.uptimeMillis();
        if (now < NEXT_LOAD_UPTIME_MS.get() || !LOAD_IN_FLIGHT.compareAndSet(false, true)) return;
        try {
            lastLoadStatus = "scheduled";
            SETTINGS_EXECUTOR.execute(() -> {
                try {
                    if (load()) publishLoadedSettings();
                    else scheduleRetry();
                } finally {
                    finishLoad();
                }
            });
        } catch (RuntimeException exception) {
            LOAD_IN_FLIGHT.set(false);
            scheduleRetry();
        }
    }

    private static void scheduleReload() {
        if (remotePreferences == null) return;
        RELOAD_REQUESTED.set(true);
        if (!LOAD_IN_FLIGHT.compareAndSet(false, true)) return;
        try {
            SETTINGS_EXECUTOR.execute(() -> {
                try {
                    while (RELOAD_REQUESTED.getAndSet(false)) {
                        if (load()) publishLoadedSettings();
                        else scheduleRetry();
                    }
                } finally {
                    finishLoad();
                }
            });
        } catch (RuntimeException exception) {
            LOAD_IN_FLIGHT.set(false);
            scheduleRetry();
        }
    }

    private static void finishLoad() {
        LOAD_IN_FLIGHT.set(false);
        if (RELOAD_REQUESTED.get()) scheduleReload();
    }

    private static void publishLoadedSettings() {
        SETTINGS_LOADED.set(true);
        notifyLoaded();
        for (Runnable listener : CHANGE_LISTENERS) {
            try {
                listener.run();
            } catch (Throwable throwable) {
                Log.w(TAG, "Settings change listener failed", throwable);
            }
        }
        LOAD_FAILURES.set(0);
        NEXT_LOAD_UPTIME_MS.set(0L);
        LOAD_FAILURE_LOGGED.set(false);
    }

    private static void scheduleRetry() {
        int failures = Math.min(LOAD_FAILURES.incrementAndGet(), 5);
        long delayMs = Math.min(30_000L, 1_000L << failures);
        NEXT_LOAD_UPTIME_MS.set(SystemClock.uptimeMillis() + delayMs);
        if (LOAD_FAILURE_LOGGED.compareAndSet(false, true)) {
            Log.w(TAG, "Settings unavailable; retrying in background (" + lastLoadStatus + ")");
        }
    }

    private static boolean load() {
        try {
            SharedPreferences preferences = remotePreferences;
            if (preferences == null) {
                lastLoadStatus = "remote-unavailable";
                return false;
            }
            lastLoadStatus = "remote-preferences";
            Bundle values = preferencesToBundle(preferences);
            moduleHooksEnabled = values.getBoolean("module_hooks_enabled", true);
            gestureHandleEnabled = values.getBoolean("gesture_handle_enabled", false);
            gestureHandlePreset = GestureHandlePresets.fromStored(
                    values.getString("gesture_handle_preset"),
                    values.getBoolean("gesture_handle_module_preset", false));
            boolean newModuleDefaults = GestureHandlePresets.MODULE.equals(gestureHandlePreset)
                    && !values.containsKey("gesture_handle_app_modes");
            gestureHandleAppModes = GestureHandleRules.decode(
                    values.getString("gesture_handle_app_modes", ""));
            gestureHandleTouchReveal = values.getBoolean(
                    "gesture_handle_touch_reveal", newModuleDefaults);
            gestureHandleSwipeMotion = values.getBoolean(
                    "gesture_handle_swipe_motion", newModuleDefaults);
            gestureHandleTouchAreaDp = GestureHandleTouchArea.normalize(values.getFloat(
                    "gesture_handle_touch_area_dp", GestureHandleTouchArea.DEFAULT_DP));
            Set<String> scope = new HashSet<>();
            for (String name : values.getString("gesture_handle_scope_packages", "").split("\n")) {
                if (!name.isEmpty()) scope.add(name);
            }
            gestureHandleScopePackages = Collections.unmodifiableSet(scope);

            navigationGlassParameters = values.getString("navigation_glass_parameters", "");
            hyperGlassifyHiddenNavigationLift = Math.max(0f, Math.min(48f, values.getFloat(
                    "hyper_glassify_hidden_navigation_lift",
                    ModuleDefaultValues.HYPER_GLASSIFY_HIDDEN_NAVIGATION_LIFT)));

            miHomeFloatingNavigationEnabled = values.getBoolean(
                    "mi_home_floating_navigation_enabled",
                    ModuleDefaultValues.MI_HOME_FLOATING_NAVIGATION_ENABLED);
            miHomeMiuixIconsEnabled = values.getBoolean("mi_home_miuix_icons_enabled", false);
            miHomeNavigationBadgesEnabled = values.getBoolean(
                    "mi_home_navigation_badges_enabled", true);

            amapFloatingNavigationEnabled = values.getBoolean(
                    "amap_floating_navigation_enabled",
                    ModuleDefaultValues.AMAP_FLOATING_NAVIGATION_ENABLED);
            amapMiuixIconsEnabled = values.getBoolean("amap_miuix_icons_enabled", false);
            amapMonochromeIconsEnabled = values.getBoolean("amap_monochrome_icons_enabled", true);
            amapHideLongPressVoiceTabEnabled = values.getBoolean(
                    "amap_hide_long_press_voice_tab_enabled",
                    ModuleDefaultValues.AMAP_HIDE_LONG_PRESS_VOICE_TAB_ENABLED);

            alipayFloatingNavigationEnabled = values.getBoolean(
                    "alipay_floating_navigation_enabled",
                    ModuleDefaultValues.ALIPAY_FLOATING_NAVIGATION_ENABLED);

            bilibiliFloatingNavigationEnabled = values.getBoolean(
                    "bilibili_floating_navigation_enabled",
                    ModuleDefaultValues.BILIBILI_FLOATING_NAVIGATION_ENABLED);
            bilibiliNavigationBadgesEnabled = values.getBoolean(
                    "bilibili_navigation_badges_enabled", true);
            bilibiliHomeTabVisible = values.getBoolean("bilibili_home_tab_visible", true);
            bilibiliFollowTabVisible = values.getBoolean("bilibili_follow_tab_visible", true);
            bilibiliDynamicTabVisible = values.getBoolean("bilibili_dynamic_tab_visible", true);
            bilibiliMallTabVisible = values.getBoolean("bilibili_mall_tab_visible", true);
            bilibiliMineTabVisible = values.getBoolean("bilibili_mine_tab_visible", true);
            bilibiliPublishButtonVisible = values.getBoolean("bilibili_publish_button_visible", true);

            // ---- 购物 / 社交 / 短视频 ----
            // 这些开关以前只存在于上游预设里，本模块的读取路径没有接，等于永远走默认值。
            // 设置页（底栏标签页）现在会写这些 key，所以这里必须同步读出来。
            taobaoFloatingNavigationEnabled = values.getBoolean(
                    "taobao_floating_navigation_enabled",
                    ModuleDefaultValues.TAOBAO_FLOATING_NAVIGATION_ENABLED);
            jingdongFloatingNavigationEnabled = values.getBoolean(
                    "jingdong_floating_navigation_enabled",
                    ModuleDefaultValues.JINGDONG_FLOATING_NAVIGATION_ENABLED);
            qqFloatingNavigationEnabled = values.getBoolean(
                    "qq_floating_navigation_enabled",
                    ModuleDefaultValues.QQ_FLOATING_NAVIGATION_ENABLED);
            qqNavigationBadgesEnabled = values.getBoolean("qq_navigation_badges_enabled", true);
            pinduoduoFloatingNavigationEnabled = values.getBoolean(
                    "pinduoduo_floating_navigation_enabled", false);
            xianyuFloatingNavigationEnabled = values.getBoolean(
                    "xianyu_floating_navigation_enabled", false);
            douyinMallFloatingNavigationEnabled = values.getBoolean(
                    "douyin_mall_floating_navigation_enabled", false);
            cainiaoFloatingNavigationEnabled = values.getBoolean(
                    "cainiao_floating_navigation_enabled", false);
            weChatFloatingNavigationEnabled = values.getBoolean(
                    "wechat_floating_navigation_enabled", false);
            weChatNavigationBadgesEnabled = values.getBoolean(
                    "wechat_navigation_badges_enabled", true);
            douyinFloatingNavigationEnabled = values.getBoolean(
                    "douyin_floating_navigation_enabled",
                    ModuleDefaultValues.DOUYIN_FLOATING_NAVIGATION_ENABLED);
            douyinLiteFloatingNavigationEnabled = values.getBoolean(
                    "douyin_lite_floating_navigation_enabled", false);
            xiaohongshuFloatingNavigationEnabled = values.getBoolean(
                    "xiaohongshu_floating_navigation_enabled", true);
            weiboFloatingNavigationEnabled = values.getBoolean(
                    "weibo_floating_navigation_enabled", true);
            qqMusicFloatingNavigationEnabled = values.getBoolean(
                    "qq_music_floating_navigation_enabled",
                    ModuleDefaultValues.QQ_MUSIC_FLOATING_NAVIGATION_ENABLED);

            String token = values.getString("diagnostic_token", "");
            diagnosticToken = token == null ? "" : token;
            lastLoadStatus = "loaded";
            return true;
        } catch (Throwable throwable) {
            lastLoadStatus = "exception";
            Log.w(TAG, "Could not load module settings", throwable);
            return false;
        }
    }

    /** Normalizes remote preference types, which may not match the expected primitive. */
    private static Bundle preferencesToBundle(SharedPreferences preferences) {
        Bundle bundle = new Bundle();
        Map<String, ?> all = preferences.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Boolean) bundle.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) bundle.putInt(key, (Integer) value);
            else if (value instanceof Long) bundle.putLong(key, (Long) value);
            else if (value instanceof Float) bundle.putFloat(key, (Float) value);
            else if (value instanceof String) bundle.putString(key, (String) value);
        }
        return bundle;
    }
}
