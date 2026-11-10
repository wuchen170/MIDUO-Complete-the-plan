package com.aritxonly.myhypermodifier;

import java.util.Set;

/** Declared module targets shared by preset classification and the scope dashboard. */
public final class ModuleScopePackage {
    public static final String VOICE_ASSIST = "com.miui.voiceassist";
    public static final String PERSONAL_ASSISTANT = "com.miui.personalassistant";
    public static final String LAUNCHER = "com.miui.home";
    public static final String SETTINGS = "com.android.settings";
    public static final String SYSTEM_FRAMEWORK = "system";
    public static final String SYSTEM_UI = "com.android.systemui";
    public static final String PLUGIN = "miui.systemui.plugin";
    public static final String XMSF = "com.xiaomi.xmsf";
    public static final String INCALL_UI = "com.android.incallui";
    public static final String MILINK = "com.milink.service";
    public static final String XIAOMI_HEALTH = "com.mi.health";
    public static final String MI_MUSIC = "com.miui.player";
    public static final String MARKET = "com.xiaomi.market";
    public static final String MI_HOME = "com.xiaomi.smarthome";
    public static final String AMAP = "com.autonavi.minimap";
    public static final String XIAOMI_COMMUNITY = "com.xiaomi.vipaccount";
    public static final String SPOTIFY = "com.spotify.music";
    public static final String NIO = "cn.com.weilaihui3";
    public static final String ALIPAY = "com.eg.android.AlipayGphone";
    public static final String DOUYIN = "com.ss.android.ugc.aweme";
    public static final String DOUYIN_LITE = "com.ss.android.ugc.aweme.lite";
    public static final String KUAISHOU = "com.smile.gifmaker";
    public static final String KUAISHOU_LITE = "com.kuaishou.nebula";
    public static final String WECHAT = "com.tencent.mm";
    public static final String QQ = "com.tencent.mobileqq";
    public static final String QQ_MUSIC = "com.tencent.qqmusic";
    public static final String XIAOHONGSHU = "com.xingin.xhs";
    public static final String WEIBO = "com.sina.weibo";
    public static final String PINDUODUO = "com.xunmeng.pinduoduo";
    public static final String TAOBAO = "com.taobao.taobao";
    public static final String JINGDONG = "com.jingdong.app.mall";
    public static final String XIANYU = "com.taobao.idlefish";
    public static final String DOUYIN_MALL = "com.ss.android.ugc.livelite";
    public static final String CAINIAO = "com.cainiao.wireless";
    public static final String PILIPLUS = "com.example.piliplus";
    public static final String BILIBILI = "tv.danmaku.bili";
    /**
     * Targets owned by this standalone module. The gesture-handle "module" preset hides the
     * system pill only for these packages (plus system apps), so unrelated apps stay untouched.
     */
    public static final Set<String> PACKAGES = Set.of(
            SYSTEM_UI, PLUGIN, ALIPAY, MI_HOME, AMAP, BILIBILI,
            TAOBAO, JINGDONG, QQ, QQ_MUSIC, XIAOHONGSHU, WEIBO, DOUYIN);

    private ModuleScopePackage() {}
}
