package com.aritxonly.myhypermodifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persisted app-wide defaults; per-app rules still take priority. */
public final class GestureHandlePresets {
    public static final String MODULE = "module";
    public static final String SHOW = "show";
    public static final String HIDE = "hide";
    public static final String IMMERSIVE = "immersive";
    /** Used by the app-wide reset; the page exposes the four explicit presets above. */
    public static final String STOCK = "stock";
    public static final Map<String, String> MODULE_APPS = moduleApps();

    private GestureHandlePresets() {}

    public static boolean selectable(String value) {
        return MODULE.equals(value) || SHOW.equals(value)
                || HIDE.equals(value) || IMMERSIVE.equals(value);
    }

    public static boolean valid(String value) {
        return selectable(value) || STOCK.equals(value);
    }

    public static String fromStored(String value, boolean legacyModulePreset) {
        if (valid(value)) return value;
        // Previous releases stored the all-system choice as a Boolean or JSON "system".
        if ("system".equals(value)) return SHOW;
        return legacyModulePreset ? MODULE : SHOW;
    }

    private static Map<String, String> moduleApps() {
        Map<String, String> apps = new LinkedHashMap<>();
        // Apps whose own bottom navigation is replaced by the soft-glass bar: the system pill is
        // hidden so it does not overlap the injected floating bar.
        apps.put("com.eg.android.AlipayGphone", HIDE);
        apps.put("com.xiaomi.smarthome", HIDE);
        apps.put("com.autonavi.minimap", HIDE);
        apps.put("tv.danmaku.bili", HIDE);
        return Collections.unmodifiableMap(apps);
    }
}
