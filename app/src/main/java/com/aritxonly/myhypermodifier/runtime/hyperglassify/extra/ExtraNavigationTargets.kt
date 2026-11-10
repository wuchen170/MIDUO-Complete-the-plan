package com.aritxonly.myhypermodifier

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.SearchDevice

/**
 * Adapters for hosts that upstream does not cover. Both apps draw their tab strip with anonymous
 * containers, so the bar is located structurally (a full-width row of equal-height slots pinned to
 * the bottom) instead of by class name.
 */
internal enum class ExtraNavigationKind {
    Xiaohongshu,
    Weibo,
}

internal data class ExtraNavigationSpec(
    val packageName: String,
    val logName: String,
    val testedVersion: String,
    val kind: ExtraNavigationKind,
    val fallbackLabels: List<String>,
)

internal object ExtraNavigationTargets {
    private val specs = listOf(
        ExtraNavigationSpec(
            packageName = "com.xingin.xhs",
            logName = "小红书",
            testedVersion = "8.x",
            kind = ExtraNavigationKind.Xiaohongshu,
            // 首页 / 市集 / publish / 消息 / 我 — the publish slot has no label of its own.
            fallbackLabels = listOf("首页", "市集", "发布", "消息", "我"),
        ),
        ExtraNavigationSpec(
            packageName = "com.sina.weibo",
            logName = "微博",
            testedVersion = "15.x",
            kind = ExtraNavigationKind.Weibo,
            fallbackLabels = listOf("首页", "视频", "发现", "消息", "我"),
        ),
    )

    private val delegates = HashMap<String, ExtraNavigationDelegate>()

    fun specFor(packageName: String): ExtraNavigationSpec? =
        specs.firstOrNull { it.packageName == packageName }

    fun delegateFor(packageName: String): ExtraNavigationDelegate? {
        val spec = specFor(packageName) ?: return null
        return synchronized(delegates) {
            delegates.getOrPut(packageName) { ExtraNavigationDelegate(createTarget(spec)) }
        }
    }

    fun enabledFor(packageName: String): Boolean = when (packageName) {
        "com.xingin.xhs" -> ModuleSettings.xiaohongshuFloatingNavigationEnabled
        "com.sina.weibo" -> ModuleSettings.weiboFloatingNavigationEnabled
        else -> false
    }

    private fun createTarget(spec: ExtraNavigationSpec) = createTarget(spec, extendContentBehindBar = true)

    private fun createTarget(spec: ExtraNavigationSpec, extendContentBehindBar: Boolean) = NativeViewBottomBarTarget(
        logName = spec.logName,
        testedVersion = spec.testedVersion,
        bottomBarClassNames = emptySet(),
        tabClassNames = emptySet(),
        resolveBottomBar = { root -> root.findExtraBottomBar(spec) },
        resolveTabViews = { bar -> bar.extraTabViews(spec) },
        resolveTabLabel = { tab, index ->
            tab.extraTabLabel() ?: spec.fallbackLabels.getOrNull(index)
        },
        fallbackLabels = spec.fallbackLabels,
        enabled = {
            ModuleSettings.moduleHooksEnabled && enabledFor(spec.packageName)
        },
        useMiuixIcons = { true },
        useMonochromeIcons = { false },
        showBadges = { false },
        fallbackIcon = { label, index -> extraFallbackIcon(label, index) },
        resolveSelectedIndex = { _, tabs ->
            tabs.indexOfFirst(View::isExtraSelected).takeIf { it >= 0 }
        },
        onNativeTabClick = { tab -> tab.clickExtraTab() },
        resolveDarkTheme = { activity, _, _, _ -> HostThemeResolver.resolve(activity) },
        nativeBarVisible = { bar -> bar.isShown && bar.height > 0 },
        nativeRefreshIntervalMs = 180L,
        minimumCaptureIntervalMs = 100L,
        allowSoftwareBackdrop = true,
        sampleBackdropOnSourceFrame = true,
        useHardwareBackdrop = true,
        pixelCopyRetryDelayMs = 900L,
        lateAttachWindowMs = 30_000L,
        manageWindowImmersion = false,
        // Both hosts keep the strip in their own vertical layout, so the page stops at the strip
        // instead of running under it and the strip's colour shows as a band behind the dock.
        // Handing the strip height to the page (see collapseNativeBar) lifts the page to the
        // bottom edge; the strip stays attached and measured, it is only drawn transparent.
        collapseNativeBarLayout = extendContentBehindBar,
        keepNativeBarBackground = !extendContentBehindBar,
        hideNativeTabContentOnly = false,
        // The edge mask fades to MiuixTheme surface, which is near-black in dark mode and reads as
        // a black band under the capsule; the drop shadow adds a second dark halo on top of it.
        bottomEdgeMask = false,
        navigationShadowVisible = false,
        showNavigationShadowInLight = false,
        overlayAllowed = { activity -> !activity.isFinishing && !activity.isDestroyed },
        onAttachDiagnostic = { detail ->
            if (detail == "floating navigation attached") {
                HookDiagnostics.available(spec.packageName, spec.logName + "悬浮底栏")
            } else {
                HookDiagnostics.observed(spec.packageName, spec.logName + "悬浮底栏", detail)
            }
        },
    )
}

/**
 * XHS hosts its tab strip in a custom view group whose class name survives obfuscation, so the
 * class hint is tried first and the structural scan stays as the fallback.
 */
private val XHS_TAB_BAR_CLASSES = listOf(
    "com.xingin.xhs.homepage.tabbar.TabBarView",
    "com.xingin.commercial.shop.malltabbar.TabBarView",
)

private fun View.findExtraBottomBar(spec: ExtraNavigationSpec): View? {
    if (spec.kind == ExtraNavigationKind.Weibo) {
        val id = resources.getIdentifier("main_radio", "id", "com.sina.weibo")
        if (id != 0) findViewById<View>(id)?.let { return it }
    }
    val expected = if (spec.kind == ExtraNavigationKind.Xiaohongshu) 5 else 4
    if (spec.kind == ExtraNavigationKind.Xiaohongshu) {
        XHS_TAB_BAR_CLASSES.forEach { className ->
            findDescendantByClassName(className)
                ?.takeIf { it.extraVisibleChildren().size >= expected - 1 }
                ?.let { return it }
        }
    }
    return findBottomSlotBar(expected)
}

private fun View.findDescendantByClassName(className: String): View? {
    if (this::class.java.name == className) return this
    val group = this as? ViewGroup ?: return null
    for (index in 0 until group.childCount) {
        group.getChildAt(index).findDescendantByClassName(className)?.let { return it }
    }
    return null
}

/** Children that are actually drawn: hosts keep placeholder slots around that are GONE or 0dp. */
private fun View.extraVisibleChildren(): List<View> = (this as? ViewGroup)?.let { group ->
    (0 until group.childCount)
        .map { group.getChildAt(it) }
        .filter { it.visibility == View.VISIBLE && it.width > 0 && it.height > 0 }
} ?: emptyList()

private fun View.extraTabViews(spec: ExtraNavigationSpec): List<View> = when (spec.kind) {
    ExtraNavigationKind.Weibo ->
        // Each slot wraps a clickable child that owns the accessibility label.
        collectDescendants()
            .filter { it.isClickable && !it.extraAccessibilityLabel().isNullOrEmpty() }
            .distinctBy { it.extraScreenX() }
            .sortedBy { it.extraScreenX() }
    ExtraNavigationKind.Xiaohongshu -> extraVisibleChildren()
}

internal fun View.findBottomSlotBar(expectedChildren: Int): View? {
    val rootHeight = (rootView as? View)?.height?.takeIf { it > 0 } ?: return null
    val rootWidth = (rootView as? View)?.width?.takeIf { it > 0 } ?: return null
    // Tab strips sit above the gesture inset, so the window can be a lot taller than the bar.
    val minimumTop = rootHeight * 0.66f
    var best: View? = null
    var bestScore = Int.MIN_VALUE

    fun consider(candidate: ViewGroup) {
        val children = candidate.extraVisibleChildren()
        if (children.size < expectedChildren - 1 || children.size > expectedChildren + 2) return
        if (candidate.width < rootWidth * 0.8f) return
        val height = candidate.height
        if (height <= 0 || height !in (rootHeight / 24)..(rootHeight / 3)) return
        val location = IntArray(2)
        candidate.getLocationOnScreen(location)
        if (location[1] < minimumTop) return
        val tops = children.map { child -> IntArray(2).also(child::getLocationOnScreen)[1] }
        if ((tops.max() - tops.min()) > 8) return
        val interactive = children.count { it.isClickable || it.isExtraClickableDescendant() }
        if (interactive < children.size - 1) return
        val score = (if (children.size == expectedChildren) 100 else 0) +
            interactive * 10 + height
        if (score > bestScore) {
            bestScore = score
            best = candidate
        }
    }

    fun visit(view: View) {
        if (view is ViewGroup) {
            consider(view)
            repeat(view.childCount) { visit(view.getChildAt(it)) }
        }
    }

    visit(this)
    return best
}

private fun View.isExtraClickableDescendant(): Boolean = collectDescendants().any(View::isClickable)

private fun View.collectDescendants(): List<View> = buildList {
    fun visit(view: View) {
        if (view !== this@collectDescendants) add(view)
        (view as? ViewGroup)?.let { group ->
            repeat(group.childCount) { visit(group.getChildAt(it)) }
        }
    }
    visit(this@collectDescendants)
}

private fun View.extraAccessibilityLabel(): String? =
    contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        ?: (this as? android.widget.TextView)?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }

private fun View.extraTabLabel(): String? {
    // Prefer the visible caption: accessibility labels on these hosts append badge counts
    // ("消息，12条未读"), which would leak into the dock.
    collectDescendants()
        .filterIsInstance<android.widget.TextView>()
        .mapNotNull { it.text?.toString()?.trim()?.takeIf(String::isNotEmpty) }
        .firstOrNull()
        ?.let { return it }
    return extraAccessibilityLabel()
}

private fun View.extraScreenX(): Int = IntArray(2).also(::getLocationOnScreen)[0]

private fun View.isExtraSelected(): Boolean {
    if (isSelected || isActivated) return true
    val description = stateDescription?.toString()
    if (description != null && description.contains("选中")) return true
    return (this as? ViewGroup)?.let { group ->
        (0 until group.childCount).any { group.getChildAt(it).isExtraSelected() }
    } == true
}

private fun View.clickExtraTab() {
    if (isClickable) {
        performClick()
        return
    }
    collectDescendants().firstOrNull(View::isClickable)?.performClick()
}

private fun extraFallbackIcon(label: String, index: Int): ImageVector = when {
    label.contains("首页") -> MiuixIcons.Home
    label.contains("消息") -> MiuixIcons.Messages
    label.contains("我") -> MiuixIcons.ContactsCircle
    label.contains("发现") || label.contains("市集") -> MiuixIcons.SearchDevice
    else -> when (index) {
        0 -> MiuixIcons.Home
        else -> MiuixIcons.All
    }
}

internal class ExtraNavigationDelegate(target: NativeViewBottomBarTarget) {
    private val navigation = NativeViewBottomBarNavigation(target)

    fun prepare(activity: Activity) = navigation.prepare(activity)
    fun attach(activity: Activity) = navigation.attach(activity)
    fun dispose(activity: Activity) = navigation.dispose(activity)
    fun setForeground(activity: Activity, foreground: Boolean) =
        navigation.setForeground(activity, foreground)
    fun refresh(activity: Activity) = navigation.refresh(activity)
    fun onTouchEvent(activity: Activity, event: MotionEvent) =
        navigation.onTouchEvent(activity, event)
    fun hasHost(activity: Activity) = navigation.hasHost(activity)
    fun hasNavigationTarget(activity: Activity) = navigation.hasNavigationTarget(activity)
}
