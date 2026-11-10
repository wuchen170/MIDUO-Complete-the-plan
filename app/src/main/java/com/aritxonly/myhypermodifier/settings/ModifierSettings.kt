package com.aritxonly.myhypermodifier

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.service.XposedService

/**
 * Companion-app settings for the standalone soft-glass navigation module.
 *
 * <p>Only the keys that the four ported adapters and the gesture handle actually read are kept.
 * Key names are identical to upstream HyperModifier so an existing configuration can be migrated
 * by copying the values into this module's `modifier_settings` preferences.
 */
data class ModifierSettings(
    val moduleHooksEnabled: Boolean = true,
    // Gesture handle
    val gestureHandleEnabled: Boolean = false,
    val gestureHandlePreset: String = GestureHandlePresets.STOCK,
    val gestureHandleAppModes: Map<String, String> = emptyMap(),
    val gestureHandleTouchReveal: Boolean = false,
    val gestureHandleSwipeMotion: Boolean = false,
    val gestureHandleTouchAreaDp: Float = GestureHandleTouchArea.DEFAULT_DP,
    // Shared glass geometry
    val hyperGlassifyHiddenNavigationLift: Float =
        ModuleDefaultValues.HYPER_GLASSIFY_HIDDEN_NAVIGATION_LIFT,
    // Mi Home
    val miHomeFloatingNavigationEnabled: Boolean =
        ModuleDefaultValues.MI_HOME_FLOATING_NAVIGATION_ENABLED,
    val miHomeMiuixIconsEnabled: Boolean = false,
    val miHomeNavigationBadgesEnabled: Boolean = true,
    // Amap
    val amapFloatingNavigationEnabled: Boolean =
        ModuleDefaultValues.AMAP_FLOATING_NAVIGATION_ENABLED,
    val amapMiuixIconsEnabled: Boolean = false,
    val amapMonochromeIconsEnabled: Boolean = true,
    val amapHideLongPressVoiceTabEnabled: Boolean =
        ModuleDefaultValues.AMAP_HIDE_LONG_PRESS_VOICE_TAB_ENABLED,
    // Alipay
    val alipayFloatingNavigationEnabled: Boolean =
        ModuleDefaultValues.ALIPAY_FLOATING_NAVIGATION_ENABLED,
    // Bilibili
    val bilibiliFloatingNavigationEnabled: Boolean =
        ModuleDefaultValues.BILIBILI_FLOATING_NAVIGATION_ENABLED,
    val bilibiliNavigationBadgesEnabled: Boolean = true,
    val bilibiliHomeTabVisible: Boolean = true,
    val bilibiliFollowTabVisible: Boolean = true,
    val bilibiliDynamicTabVisible: Boolean = true,
    val bilibiliMallTabVisible: Boolean = true,
    val bilibiliMineTabVisible: Boolean = true,
    val bilibiliPublishButtonVisible: Boolean = true,
)

object ModifierSettingsStore {
    private const val TAG = "SoftGlassNav"
    const val PREFS = "modifier_settings"

    private const val KEY_MODULE_HOOKS_ENABLED = "module_hooks_enabled"
    private const val KEY_GESTURE_HANDLE_ENABLED = "gesture_handle_enabled"
    private const val KEY_GESTURE_HANDLE_PRESET = "gesture_handle_preset"
    private const val KEY_GESTURE_HANDLE_MODULE_PRESET = "gesture_handle_module_preset"
    private const val KEY_GESTURE_HANDLE_APP_MODES = "gesture_handle_app_modes"
    private const val KEY_GESTURE_HANDLE_TOUCH_REVEAL = "gesture_handle_touch_reveal"
    private const val KEY_GESTURE_HANDLE_SWIPE_MOTION = "gesture_handle_swipe_motion"
    private const val KEY_GESTURE_HANDLE_TOUCH_AREA_DP = "gesture_handle_touch_area_dp"
    private const val KEY_GESTURE_HANDLE_SCOPE_PACKAGES = "gesture_handle_scope_packages"
    private const val KEY_NAVIGATION_LIFT = "hyper_glassify_hidden_navigation_lift"

    private const val KEY_MI_HOME_ENABLED = "mi_home_floating_navigation_enabled"
    private const val KEY_MI_HOME_MIUIX_ICONS = "mi_home_miuix_icons_enabled"
    private const val KEY_MI_HOME_BADGES = "mi_home_navigation_badges_enabled"

    private const val KEY_AMAP_ENABLED = "amap_floating_navigation_enabled"
    private const val KEY_AMAP_MIUIX_ICONS = "amap_miuix_icons_enabled"
    private const val KEY_AMAP_MONOCHROME_ICONS = "amap_monochrome_icons_enabled"
    private const val KEY_AMAP_HIDE_VOICE_TAB = "amap_hide_long_press_voice_tab_enabled"

    private const val KEY_ALIPAY_ENABLED = "alipay_floating_navigation_enabled"

    private const val KEY_BILIBILI_ENABLED = "bilibili_floating_navigation_enabled"
    private const val KEY_BILIBILI_BADGES = "bilibili_navigation_badges_enabled"
    private const val KEY_BILIBILI_HOME = "bilibili_home_tab_visible"
    private const val KEY_BILIBILI_FOLLOW = "bilibili_follow_tab_visible"
    private const val KEY_BILIBILI_DYNAMIC = "bilibili_dynamic_tab_visible"
    private const val KEY_BILIBILI_MALL = "bilibili_mall_tab_visible"
    private const val KEY_BILIBILI_MINE = "bilibili_mine_tab_visible"
    private const val KEY_BILIBILI_PUBLISH = "bilibili_publish_button_visible"

    private val syncQueue = SettingsSyncQueue()

    @Volatile
    private var remoteService: XposedService? = null

    fun load(context: Context): ModifierSettings {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ModifierSettings(
            moduleHooksEnabled = preferences.getBoolean(KEY_MODULE_HOOKS_ENABLED, true),
            gestureHandleEnabled = preferences.getBoolean(KEY_GESTURE_HANDLE_ENABLED, false),
            gestureHandlePreset = GestureHandlePresets.fromStored(
                preferences.getString(KEY_GESTURE_HANDLE_PRESET, null),
                preferences.getBoolean(KEY_GESTURE_HANDLE_MODULE_PRESET, false),
            ),
            gestureHandleAppModes = GestureHandleRules.decode(
                preferences.getString(KEY_GESTURE_HANDLE_APP_MODES, ""),
            ),
            gestureHandleTouchReveal = preferences.getBoolean(KEY_GESTURE_HANDLE_TOUCH_REVEAL, false),
            gestureHandleSwipeMotion = preferences.getBoolean(KEY_GESTURE_HANDLE_SWIPE_MOTION, false),
            gestureHandleTouchAreaDp = GestureHandleTouchArea.normalize(
                preferences.getFloat(KEY_GESTURE_HANDLE_TOUCH_AREA_DP, GestureHandleTouchArea.DEFAULT_DP),
            ),
            hyperGlassifyHiddenNavigationLift = preferences.getFloat(
                KEY_NAVIGATION_LIFT,
                ModuleDefaultValues.HYPER_GLASSIFY_HIDDEN_NAVIGATION_LIFT,
            ),
            miHomeFloatingNavigationEnabled = preferences.getBoolean(
                KEY_MI_HOME_ENABLED,
                ModuleDefaultValues.MI_HOME_FLOATING_NAVIGATION_ENABLED,
            ),
            miHomeMiuixIconsEnabled = preferences.getBoolean(KEY_MI_HOME_MIUIX_ICONS, false),
            miHomeNavigationBadgesEnabled = preferences.getBoolean(KEY_MI_HOME_BADGES, true),
            amapFloatingNavigationEnabled = preferences.getBoolean(
                KEY_AMAP_ENABLED,
                ModuleDefaultValues.AMAP_FLOATING_NAVIGATION_ENABLED,
            ),
            amapMiuixIconsEnabled = preferences.getBoolean(KEY_AMAP_MIUIX_ICONS, false),
            amapMonochromeIconsEnabled = preferences.getBoolean(KEY_AMAP_MONOCHROME_ICONS, true),
            amapHideLongPressVoiceTabEnabled = preferences.getBoolean(
                KEY_AMAP_HIDE_VOICE_TAB,
                ModuleDefaultValues.AMAP_HIDE_LONG_PRESS_VOICE_TAB_ENABLED,
            ),
            alipayFloatingNavigationEnabled = preferences.getBoolean(
                KEY_ALIPAY_ENABLED,
                ModuleDefaultValues.ALIPAY_FLOATING_NAVIGATION_ENABLED,
            ),
            bilibiliFloatingNavigationEnabled = preferences.getBoolean(
                KEY_BILIBILI_ENABLED,
                ModuleDefaultValues.BILIBILI_FLOATING_NAVIGATION_ENABLED,
            ),
            bilibiliNavigationBadgesEnabled = preferences.getBoolean(KEY_BILIBILI_BADGES, true),
            bilibiliHomeTabVisible = preferences.getBoolean(KEY_BILIBILI_HOME, true),
            bilibiliFollowTabVisible = preferences.getBoolean(KEY_BILIBILI_FOLLOW, true),
            bilibiliDynamicTabVisible = preferences.getBoolean(KEY_BILIBILI_DYNAMIC, true),
            bilibiliMallTabVisible = preferences.getBoolean(KEY_BILIBILI_MALL, true),
            bilibiliMineTabVisible = preferences.getBoolean(KEY_BILIBILI_MINE, true),
            bilibiliPublishButtonVisible = preferences.getBoolean(KEY_BILIBILI_PUBLISH, true),
        )
    }

    fun save(context: Context, value: ModifierSettings) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = preferences.edit()
            .putBoolean(KEY_MODULE_HOOKS_ENABLED, value.moduleHooksEnabled)
            .putBoolean(KEY_GESTURE_HANDLE_ENABLED, value.gestureHandleEnabled)
            .putString(KEY_GESTURE_HANDLE_PRESET, value.gestureHandlePreset)
            .putBoolean(KEY_GESTURE_HANDLE_MODULE_PRESET,
                value.gestureHandlePreset == GestureHandlePresets.MODULE)
            .putString(KEY_GESTURE_HANDLE_APP_MODES,
                GestureHandleRules.encode(value.gestureHandleAppModes))
            .putBoolean(KEY_GESTURE_HANDLE_TOUCH_REVEAL, value.gestureHandleTouchReveal)
            .putBoolean(KEY_GESTURE_HANDLE_SWIPE_MOTION, value.gestureHandleSwipeMotion)
            .putFloat(KEY_GESTURE_HANDLE_TOUCH_AREA_DP,
                GestureHandleTouchArea.normalize(value.gestureHandleTouchAreaDp))
            .putFloat(KEY_NAVIGATION_LIFT, value.hyperGlassifyHiddenNavigationLift)
            .putBoolean(KEY_MI_HOME_ENABLED, value.miHomeFloatingNavigationEnabled)
            .putBoolean(KEY_MI_HOME_MIUIX_ICONS, value.miHomeMiuixIconsEnabled)
            .putBoolean(KEY_MI_HOME_BADGES, value.miHomeNavigationBadgesEnabled)
            .putBoolean(KEY_AMAP_ENABLED, value.amapFloatingNavigationEnabled)
            .putBoolean(KEY_AMAP_MIUIX_ICONS, value.amapMiuixIconsEnabled)
            .putBoolean(KEY_AMAP_MONOCHROME_ICONS, value.amapMonochromeIconsEnabled)
            .putBoolean(KEY_AMAP_HIDE_VOICE_TAB, value.amapHideLongPressVoiceTabEnabled)
            .putBoolean(KEY_ALIPAY_ENABLED, value.alipayFloatingNavigationEnabled)
            .putBoolean(KEY_BILIBILI_ENABLED, value.bilibiliFloatingNavigationEnabled)
            .putBoolean(KEY_BILIBILI_BADGES, value.bilibiliNavigationBadgesEnabled)
            .putBoolean(KEY_BILIBILI_HOME, value.bilibiliHomeTabVisible)
            .putBoolean(KEY_BILIBILI_FOLLOW, value.bilibiliFollowTabVisible)
            .putBoolean(KEY_BILIBILI_DYNAMIC, value.bilibiliDynamicTabVisible)
            .putBoolean(KEY_BILIBILI_MALL, value.bilibiliMallTabVisible)
            .putBoolean(KEY_BILIBILI_MINE, value.bilibiliMineTabVisible)
            .putBoolean(KEY_BILIBILI_PUBLISH, value.bilibiliPublishButtonVisible)
        check(editor.commit()) { "无法保存设置" }
        syncRemotePreferences(context)
    }

    /**
     * 单键读写：给 Java 设置页（fold.SettingsActivity）用。
     *
     * <p>Data class 的 `copy()` 在 Java 里调用要写满五十多个参数，实际不可用；而设置项本身
     * 就是一组互相独立的开关，按 key 读写反而更贴近宿主侧的读取方式（[ModuleSettings] 也是
     * 逐个 key 取的）。写完立刻镜像到 LSPosed 的远端偏好，目标进程下一次读到时就是新值。
     */
    @JvmStatic
    fun readBoolean(context: Context, key: String, defaultValue: Boolean): Boolean =
        preferences(context).getBoolean(key, defaultValue)

    @JvmStatic
    fun writeBoolean(context: Context, key: String, value: Boolean) {
        preferences(context).edit().putBoolean(key, value).commit()
        syncRemotePreferences(context)
    }

    @JvmStatic
    fun readFloat(context: Context, key: String, defaultValue: Float): Float =
        preferences(context).getFloat(key, defaultValue)

    @JvmStatic
    fun writeFloat(context: Context, key: String, value: Float) {
        preferences(context).edit().putFloat(key, value).commit()
        syncRemotePreferences(context)
    }

    @JvmStatic
    fun readString(context: Context, key: String, defaultValue: String): String =
        preferences(context).getString(key, defaultValue) ?: defaultValue

    @JvmStatic
    fun writeString(context: Context, key: String, value: String) {
        preferences(context).edit().putString(key, value).commit()
        syncRemotePreferences(context)
    }

    /** LSPosed 里勾选的作用域；未连接时为空集。 */
    @JvmStatic
    fun scopePackages(context: Context): Set<String> = gestureHandleScopePackages(context)

    /** 把本地偏好整体推一次远端，供刚绑定服务时补同步。 */
    @JvmStatic
    fun pushToRemote(context: Context) {
        syncRemotePreferences(context)
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun onXposedServiceBound(context: Context, service: XposedService) {
        remoteService = service
        syncRemotePreferences(context)
    }

    fun onXposedServiceDied() {
        remoteService = null
    }

    fun gestureHandleScopePackages(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_GESTURE_HANDLE_SCOPE_PACKAGES, "").orEmpty()
            .lineSequence().filter { it.isNotBlank() }.toSet()

    private fun syncRemotePreferences(context: Context) {
        val applicationContext = context.applicationContext
        syncQueue.submit { syncRemotePreferencesNow(applicationContext) }
    }

    private fun syncRemotePreferencesNow(context: Context): Boolean {
        val service = remoteService ?: return true
        return try {
            val source = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // Keep the real framework scope available to the gesture-handle preset rules.
            source.edit()
                .putString(KEY_GESTURE_HANDLE_SCOPE_PACKAGES, service.scope.joinToString("\n"))
                .apply()
            val snapshot = source.all
            val remote = service.getRemotePreferences(PREFS)
            val previous = remote.all
            val removed = previous.keys - snapshot.keys
            val changed = snapshot.filter { (key, value) ->
                !previous.containsKey(key) || previous[key] != value
            }
            if (removed.isEmpty() && changed.isEmpty()) return true
            val editor = remote.edit()
            removed.forEach(editor::remove)
            changed.forEach { (key, value) -> editor.putRemoteValue(key, value) }
            editor.commit()
        } catch (throwable: Throwable) {
            Log.w(TAG, "Could not mirror settings to LSPosed remote preferences", throwable)
            false
        }
    }

    private fun SharedPreferences.Editor.putRemoteValue(key: String, value: Any?) {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Float -> putFloat(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is String -> putString(key, value)
            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }
}
