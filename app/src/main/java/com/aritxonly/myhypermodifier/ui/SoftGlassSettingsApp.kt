package com.aritxonly.myhypermodifier.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aritxonly.myhypermodifier.GestureHandlePresets
import com.aritxonly.myhypermodifier.GestureHandleTouchArea
import com.aritxonly.myhypermodifier.ModifierSettings
import com.aritxonly.myhypermodifier.ModifierSettingsStore
import com.aritxonly.myhypermodifier.ModuleFrameworkState
import kotlin.math.roundToInt

private enum class Page(val title: String) {
    HOME("柔光底栏"),
    ALIPAY("支付宝"),
    MIHOME("米家"),
    AMAP("高德地图"),
    BILIBILI("哔哩哔哩"),
    GESTURE("手势提示线"),
}

@Composable
fun SoftGlassSettingsApp() {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
    ) {
        SoftGlassSettingsContent()
    }
}

@Composable
private fun SoftGlassSettingsContent() {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(ModifierSettingsStore.load(context)) }
    var pageName by rememberSaveable { mutableStateOf(Page.HOME.name) }
    val page = Page.entries.firstOrNull { it.name == pageName } ?: Page.HOME
    val framework by ModuleFrameworkState.snapshot

    fun update(next: ModifierSettings) {
        settings = next
        ModifierSettingsStore.save(context, next)
    }

    BackHandler(enabled = page != Page.HOME) { pageName = Page.HOME.name }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            Header(
                title = page.title,
                onBack = if (page == Page.HOME) null else ({ pageName = Page.HOME.name }),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (page) {
                    Page.HOME -> HomePage(
                        settings = settings,
                        connected = framework.connected && framework.apiVersion >= ModuleFrameworkState.MIN_SUPPORTED_API,
                        scope = framework.scope,
                        onNavigate = { target: Page -> pageName = target.name },
                        onUpdate = { update(it) },
                    )
                    Page.ALIPAY -> AlipayPage(settings) { update(it) }
                    Page.MIHOME -> MiHomePage(settings) { update(it) }
                    Page.AMAP -> AmapPage(settings) { update(it) }
                    Page.BILIBILI -> BilibiliPage(settings) { update(it) }
                    Page.GESTURE -> GestureHandlePage(settings) { update(it) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Header(title: String, onBack: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF7A5AF8), Color(0xFF4B9BFF)),
                ),
            )
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        Column {
            if (onBack != null) {
                Text(
                    text = "← 返回",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onBack)
                        .padding(vertical = 2.dp, horizontal = 2.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "18duo 补全计划 · 底栏细节",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun HomePage(
    settings: ModifierSettings,
    connected: Boolean,
    scope: Set<String>,
    onNavigate: (Page) -> Unit,
    onUpdate: (ModifierSettings) -> Unit,
) {
    val context = LocalContext.current
    if (!connected) {
        Card {
            Text(
                text = "未连接到 LSPosed。设置可以保存，但只有连接后才会同步到目标应用；" +
                    "请在 LSPosed 中启用本模块并勾选作用域。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    Card {
        SwitchRow(
            title = "启用模块功能",
            subtitle = "总开关，关闭后所有 Hook 停止生效",
            checked = settings.moduleHooksEnabled,
            onCheckedChange = { onUpdate(settings.copy(moduleHooksEnabled = it)) },
        )
    }

    SectionLabel("导航")
    Card {
        NavigationRow(
            title = "返回主界面",
            subtitle = "桌面 · 底栏 · 相机 · 壁纸",
            active = false,
            inScope = true,
            onClick = { (context as? android.app.Activity)?.finish() },
        )
    }

    SectionLabel("目标应用")
    Card {
        NavigationRow(
            title = "支付宝",
            subtitle = if (settings.alipayFloatingNavigationEnabled) "柔光底栏已启用" else "已关闭",
            active = settings.alipayFloatingNavigationEnabled,
            inScope = "com.eg.android.AlipayGphone" in scope,
            onClick = { onNavigate(Page.ALIPAY) },
        )
        HorizontalDivider()
        NavigationRow(
            title = "米家",
            subtitle = if (settings.miHomeFloatingNavigationEnabled) "柔光底栏已启用" else "已关闭",
            active = settings.miHomeFloatingNavigationEnabled,
            inScope = "com.xiaomi.smarthome" in scope,
            onClick = { onNavigate(Page.MIHOME) },
        )
        HorizontalDivider()
        NavigationRow(
            title = "高德地图",
            subtitle = if (settings.amapFloatingNavigationEnabled) "柔光底栏已启用" else "已关闭",
            active = settings.amapFloatingNavigationEnabled,
            inScope = "com.autonavi.minimap" in scope,
            onClick = { onNavigate(Page.AMAP) },
        )
        HorizontalDivider()
        NavigationRow(
            title = "哔哩哔哩",
            subtitle = if (settings.bilibiliFloatingNavigationEnabled) "柔光底栏已启用" else "已关闭",
            active = settings.bilibiliFloatingNavigationEnabled,
            inScope = "tv.danmaku.bili" in scope,
            onClick = { onNavigate(Page.BILIBILI) },
        )
    }

    SectionLabel("系统")
    Card {
        NavigationRow(
            title = "手势提示线",
            subtitle = if (settings.gestureHandleEnabled) "已接管底部小横条" else "已关闭",
            active = settings.gestureHandleEnabled,
            inScope = "com.android.systemui" in scope,
            onClick = { onNavigate(Page.GESTURE) },
        )
    }

    Hint("淘宝、京东、QQ、QQ 音乐、小红书、微博、抖音的开关在主界面「底栏」标签页。")

    Card {
        Text(
            text = "修改设置后需要重启对应应用（或在 LSPosed 中重启作用域）才会重新注入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AlipayPage(settings: ModifierSettings, onUpdate: (ModifierSettings) -> Unit) {
    Card {
        SwitchRow(
            title = "柔光玻璃底栏",
            subtitle = "替换支付宝经典 TabBar 为悬浮胶囊底栏，保留原生点击与路由",
            checked = settings.alipayFloatingNavigationEnabled,
            onCheckedChange = {
                onUpdate(settings.copy(alipayFloatingNavigationEnabled = it))
            },
        )
    }
    Hint("适配基于 com.alipay.android.launcher.TabLauncherFragment；" +
        "支付宝版本更新后若类名变化，需要同步调整适配代码。")
}

@Composable
private fun MiHomePage(settings: ModifierSettings, onUpdate: (ModifierSettings) -> Unit) {
    Card {
        SwitchRow(
            title = "柔光玻璃底栏",
            subtitle = "接管原生 TabPageIndicatorNew，保留角标与 Lottie 图标",
            checked = settings.miHomeFloatingNavigationEnabled,
            onCheckedChange = {
                onUpdate(settings.copy(miHomeFloatingNavigationEnabled = it))
            },
        )
        HorizontalDivider()
        SwitchRow(
            title = "显示消息角标",
            subtitle = "读取原生 Tab 的未读角标",
            checked = settings.miHomeNavigationBadgesEnabled,
            enabled = settings.miHomeFloatingNavigationEnabled,
            onCheckedChange = {
                onUpdate(settings.copy(miHomeNavigationBadgesEnabled = it))
            },
        )
        HorizontalDivider()
        SwitchRow(
            title = "使用 MIUIX 图标",
            subtitle = "用 MIUIX 图标替代原生图标快照",
            checked = settings.miHomeMiuixIconsEnabled,
            enabled = settings.miHomeFloatingNavigationEnabled,
            onCheckedChange = {
                onUpdate(settings.copy(miHomeMiuixIconsEnabled = it))
            },
        )
    }
}

@Composable
private fun AmapPage(settings: ModifierSettings, onUpdate: (ModifierSettings) -> Unit) {
    Card {
        SwitchRow(
            title = "柔光玻璃底栏",
            subtitle = "接管原生 LiteTabBar，保留高德自有埋点",
            checked = settings.amapFloatingNavigationEnabled,
            onCheckedChange = {
                onUpdate(settings.copy(amapFloatingNavigationEnabled = it))
            },
        )
        HorizontalDivider()
        SwitchRow(
            title = "隐藏“长按说话”标签",
            subtitle = "只隐藏语音 Tab，普通消息 Tab 保持不变",
            checked = settings.amapHideLongPressVoiceTabEnabled,
            enabled = settings.amapFloatingNavigationEnabled,
            onCheckedChange = {
                onUpdate(settings.copy(amapHideLongPressVoiceTabEnabled = it))
            },
        )
        HorizontalDivider()
        SwitchRow(
            title = "单色图标",
            subtitle = "图标按选中状态着色",
            checked = settings.amapMonochromeIconsEnabled,
            enabled = settings.amapFloatingNavigationEnabled,
            onCheckedChange = {
                onUpdate(settings.copy(amapMonochromeIconsEnabled = it))
            },
        )
        HorizontalDivider()
        SwitchRow(
            title = "使用 MIUIX 图标",
            subtitle = "用 MIUIX 图标替代高德原生图标",
            checked = settings.amapMiuixIconsEnabled,
            enabled = settings.amapFloatingNavigationEnabled,
            onCheckedChange = {
                onUpdate(settings.copy(amapMiuixIconsEnabled = it))
            },
        )
    }
    Hint("高德地图适配为实验性：底栏材质使用 PixelCopy 采样地图 SurfaceView。")
}

@Composable
private fun BilibiliPage(settings: ModifierSettings, onUpdate: (ModifierSettings) -> Unit) {
    val visibleTabs = listOf(
        settings.bilibiliHomeTabVisible,
        settings.bilibiliFollowTabVisible,
        settings.bilibiliDynamicTabVisible,
        settings.bilibiliMallTabVisible,
        settings.bilibiliMineTabVisible,
    ).count { it }

    Card {
        SwitchRow(
            title = "柔光玻璃底栏",
            subtitle = "Deadliner 柔光玻璃胶囊，保留发布按钮与原生路由",
            checked = settings.bilibiliFloatingNavigationEnabled,
            onCheckedChange = {
                onUpdate(settings.copy(bilibiliFloatingNavigationEnabled = it))
            },
        )
        HorizontalDivider()
        SwitchRow(
            title = "显示消息角标",
            checked = settings.bilibiliNavigationBadgesEnabled,
            enabled = settings.bilibiliFloatingNavigationEnabled,
            onCheckedChange = {
                onUpdate(settings.copy(bilibiliNavigationBadgesEnabled = it))
            },
        )
        HorizontalDivider()
        SwitchRow(
            title = "显示发布按钮",
            subtitle = "胶囊右侧的独立“+”按钮",
            checked = settings.bilibiliPublishButtonVisible,
            enabled = settings.bilibiliFloatingNavigationEnabled,
            onCheckedChange = {
                onUpdate(settings.copy(bilibiliPublishButtonVisible = it))
            },
        )
    }

    SectionLabel("导航目标（至少保留一个）")
    Card {
        TabSwitch(
            title = "首页",
            checked = settings.bilibiliHomeTabVisible,
            enabled = settings.bilibiliFloatingNavigationEnabled,
            canDisable = visibleTabs > 1,
        ) { onUpdate(settings.copy(bilibiliHomeTabVisible = it)) }
        HorizontalDivider()
        TabSwitch(
            title = "动态",
            checked = settings.bilibiliFollowTabVisible,
            enabled = settings.bilibiliFloatingNavigationEnabled,
            canDisable = visibleTabs > 1,
        ) { onUpdate(settings.copy(bilibiliFollowTabVisible = it)) }
        HorizontalDivider()
        TabSwitch(
            title = "关注",
            checked = settings.bilibiliDynamicTabVisible,
            enabled = settings.bilibiliFloatingNavigationEnabled,
            canDisable = visibleTabs > 1,
        ) { onUpdate(settings.copy(bilibiliDynamicTabVisible = it)) }
        HorizontalDivider()
        TabSwitch(
            title = "会员购",
            checked = settings.bilibiliMallTabVisible,
            enabled = settings.bilibiliFloatingNavigationEnabled,
            canDisable = visibleTabs > 1,
        ) { onUpdate(settings.copy(bilibiliMallTabVisible = it)) }
        HorizontalDivider()
        TabSwitch(
            title = "我的",
            checked = settings.bilibiliMineTabVisible,
            enabled = settings.bilibiliFloatingNavigationEnabled,
            canDisable = visibleTabs > 1,
        ) { onUpdate(settings.copy(bilibiliMineTabVisible = it)) }
    }
}

@Composable
private fun TabSwitch(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    canDisable: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SwitchRow(
        title = title,
        checked = checked,
        enabled = enabled && (checked || canDisable),
        onCheckedChange = onCheckedChange,
    )
}

@Composable
private fun GestureHandlePage(settings: ModifierSettings, onUpdate: (ModifierSettings) -> Unit) {
    Card {
        SwitchRow(
            title = "接管手势提示线",
            subtitle = "控制底部小横条的显示、动画与触摸唤醒",
            checked = settings.gestureHandleEnabled,
            onCheckedChange = { onUpdate(settings.copy(gestureHandleEnabled = it)) },
        )
    }

    SectionLabel("应用预设")
    Card {
        PresetRow(
            label = "跟随模块推荐",
            detail = "仅对已勾选作用域与系统应用隐藏，其余沉浸",
            selected = settings.gestureHandlePreset == GestureHandlePresets.MODULE,
            enabled = settings.gestureHandleEnabled,
        ) { onUpdate(settings.copy(gestureHandlePreset = GestureHandlePresets.MODULE)) }
        HorizontalDivider()
        PresetRow(
            label = "全部显示",
            detail = "所有应用都显示系统手势条",
            selected = settings.gestureHandlePreset == GestureHandlePresets.SHOW,
            enabled = settings.gestureHandleEnabled,
        ) { onUpdate(settings.copy(gestureHandlePreset = GestureHandlePresets.SHOW)) }
        HorizontalDivider()
        PresetRow(
            label = "全部隐藏",
            detail = "所有应用都不显示手势条",
            selected = settings.gestureHandlePreset == GestureHandlePresets.HIDE,
            enabled = settings.gestureHandleEnabled,
        ) { onUpdate(settings.copy(gestureHandlePreset = GestureHandlePresets.HIDE)) }
        HorizontalDivider()
        PresetRow(
            label = "全部沉浸",
            detail = "切换应用/任务后显示 3 秒，随后淡出",
            selected = settings.gestureHandlePreset == GestureHandlePresets.IMMERSIVE,
            enabled = settings.gestureHandleEnabled,
        ) { onUpdate(settings.copy(gestureHandlePreset = GestureHandlePresets.IMMERSIVE)) }
    }

    SectionLabel("交互")
    Card {
        SwitchRow(
            title = "触摸时保持可见",
            subtitle = "触摸底部手势区域时显示，松开 3 秒后淡出",
            checked = settings.gestureHandleTouchReveal,
            enabled = settings.gestureHandleEnabled,
            onCheckedChange = { onUpdate(settings.copy(gestureHandleTouchReveal = it)) },
        )
        HorizontalDivider()
        SwitchRow(
            title = "滑动动效",
            subtitle = "横条跟随四方向滑动并带阻尼回弹",
            checked = settings.gestureHandleSwipeMotion,
            enabled = settings.gestureHandleEnabled,
            onCheckedChange = { onUpdate(settings.copy(gestureHandleSwipeMotion = it)) },
        )
    }

    SectionLabel("底部触控带")
    Card {
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("感应高度", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${settings.gestureHandleTouchAreaDp.roundToInt()} dp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = settings.gestureHandleTouchAreaDp,
                onValueChange = {
                    onUpdate(settings.copy(gestureHandleTouchAreaDp = it))
                },
                valueRange = 0f..GestureHandleTouchArea.MAX_DP,
                enabled = settings.gestureHandleEnabled,
            )
            Text(
                text = "屏幕最底部用于唤醒横条的高度，0 dp 表示关闭触摸唤醒。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    SectionLabel("布局")
    Card {
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("隐藏系统导航栏时的悬浮底栏抬升", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${settings.hyperGlassifyHiddenNavigationLift.roundToInt()} dp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = settings.hyperGlassifyHiddenNavigationLift,
                onValueChange = {
                    onUpdate(settings.copy(hyperGlassifyHiddenNavigationLift = it))
                },
                valueRange = 0f..48f,
            )
            Text(
                text = "手势条处于沉浸态时，避免悬浮底栏与手势条重叠。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PresetRow(
    label: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
        ) {
            if (selected) {
                Text(
                    text = "✓",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
private fun NavigationRow(
    title: String,
    subtitle: String,
    active: Boolean,
    inScope: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!inScope) {
            StatusChip("未授权作用域")
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = "›",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
