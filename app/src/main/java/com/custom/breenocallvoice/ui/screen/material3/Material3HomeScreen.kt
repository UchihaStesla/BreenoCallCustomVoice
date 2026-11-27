package com.custom.breenocallvoice.ui.screen.material3

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.custom.breenocallvoice.config.BizSourceMode
import com.custom.breenocallvoice.config.ModuleSettings
import com.custom.breenocallvoice.config.ServiceUiState
import com.custom.breenocallvoice.config.ThemeMode
import com.custom.breenocallvoice.config.UiStyle
import com.custom.breenocallvoice.config.XposedServiceManager
import com.custom.breenocallvoice.ui.screen.HomeScreenSupport
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Material3HomeScreen(uiState: ServiceUiState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showGuideDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showHideIconWarnDialog by remember { mutableStateOf(false) }
    var showBizModeDialog by remember { mutableStateOf(false) }
    var showUiStyleDialog by remember { mutableStateOf(false) }
    var showThemeModeDialog by remember { mutableStateOf(false) }

    val breenoVersionText = remember(context) { HomeScreenSupport.queryTargetVersion(context) }
    val canEditRemote = uiState.isActivated && uiState.supportsRemotePrefs
    val settings = uiState.settings

    fun showTip(msg: String) {
        scope.launch {
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun editRemote(transform: (ModuleSettings) -> ModuleSettings) {
        if (!canEditRemote) {
            showTip("请先在 LSPosed 中激活本模块以保存远程配置")
            return
        }
        if (!XposedServiceManager.updateSettings(transform)) {
            showTip("保存配置失败，请检查 LSPosed 服务状态")
        }
    }

    fun applyLauncherIconHidden(hidden: Boolean) {
        if (XposedServiceManager.setLauncherIconHidden(hidden)) {
            showTip(
                if (hidden) "已隐藏桌面图标，可从 LSPosed 管理器重新进入"
                else "已恢复桌面图标显示",
            )
        } else {
            showTip("修改桌面图标状态失败")
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(text = "小布通话音色") },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = {
                            XposedServiceManager.refresh()
                            showTip(if (uiState.isActivated) "已刷新模块与配置状态" else "尚未连接到 LSPosed 服务")
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新状态",
                        )
                    }
                    IconButton(onClick = { showGuideDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = "使用说明",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "status") {
                Md3StatusCard(
                    uiState = uiState,
                    breenoVersionText = breenoVersionText,
                    onRequestScope = {
                        XposedServiceManager.requestTargetScope { _, msg -> showTip(msg) }
                    },
                )
            }

            item(key = "timbre_section") {
                Md3Section(title = "自定义音色") {
                    Md3SwitchItem(
                        title = "启用自定义音色",
                        summary = "跨进程同步小布助手自定义音色，并注入小布通话列表与云端 TTS 引擎",
                        checked = settings.enableCustomTimbre,
                        enabled = canEditRemote,
                        onCheckedChange = { checked ->
                            editRemote { it.copy(enableCustomTimbre = checked) }
                        },
                    )
                    Md3Divider()
                    Md3SwitchItem(
                        title = "自动云端刷新音色",
                        summary = "本地缓存为空时通过主进程自动拉取云端自定义音色（需登录欢太账号）",
                        checked = settings.enableAutoCloudRefresh,
                        enabled = canEditRemote && settings.enableCustomTimbre,
                        onCheckedChange = { checked ->
                            editRemote { it.copy(enableAutoCloudRefresh = checked) }
                        },
                    )
                    Md3Divider()
                    Md3SwitchItem(
                        title = "离线播报安全回退",
                        summary = "自定义音色缺少离线资源时自动回退至默认离线音色，防止无声或崩溃",
                        checked = settings.enableOfflineFallback,
                        enabled = canEditRemote && settings.enableCustomTimbre,
                        onCheckedChange = { checked ->
                            editRemote { it.copy(enableOfflineFallback = checked) }
                        },
                    )
                    Md3Divider()
                    Md3ClickableItem(
                        title = "TTS biz_source 策略",
                        summary = "${settings.bizSourceMode.label} · ${settings.bizSourceMode.description}",
                        enabled = canEditRemote && settings.enableCustomTimbre,
                        onClick = { showBizModeDialog = true },
                    )
                }
            }

            item(key = "update_section") {
                Md3Section(title = "更新拦截") {
                    Md3SwitchItem(
                        title = "阻止小布助手应用内更新",
                        summary = "拦截 UpgradeSDK 自更新检测（显示已是最新版本）、更新弹窗、通知与 APK 下载安装",
                        checked = settings.blockAppUpdate,
                        enabled = canEditRemote,
                        onCheckedChange = { checked ->
                            editRemote { it.copy(blockAppUpdate = checked) }
                        },
                    )
                    Md3Divider()
                    Md3InfoItem(
                        title = "关于系统应用商店更新",
                        summary = "本开关仅拦截小布助手应用内自更新；若需防止被系统软件商店静默升级，请在软件商店中关闭自动更新。",
                    )
                }
            }

            item(key = "appearance_debug_section") {
                Md3Section(title = "外观与调试") {
                    Md3ClickableItem(
                        title = "界面风格",
                        summary = "${uiState.uiStyle.label} · ${uiState.uiStyle.description}",
                        onClick = { showUiStyleDialog = true },
                    )
                    Md3Divider()
                    Md3ClickableItem(
                        title = "主题外观",
                        summary = uiState.themeMode.label,
                        onClick = { showThemeModeDialog = true },
                    )
                    Md3Divider()
                    Md3SwitchItem(
                        title = "隐藏桌面图标",
                        summary = "隐藏启动器中的模块图标，之后可通过 LSPosed 管理器点击本模块打开设置",
                        checked = uiState.isLauncherIconHidden,
                        enabled = true,
                        onCheckedChange = { checked ->
                            if (checked && !uiState.isActivated) {
                                showHideIconWarnDialog = true
                            } else {
                                applyLauncherIconHidden(checked)
                            }
                        },
                    )
                    Md3Divider()
                    Md3SwitchItem(
                        title = "详细调试日志",
                        summary = "向 LSPosed 日志输出更多 Hook 调试信息（Tag: BreenoCallCustomVoice）",
                        checked = settings.verboseLog,
                        enabled = canEditRemote,
                        onCheckedChange = { checked ->
                            editRemote { it.copy(verboseLog = checked) }
                        },
                    )
                    Md3Divider()
                    Md3ClickableItem(
                        title = "恢复默认配置",
                        summary = "将所有远程 Hook 开关重置为推荐默认值",
                        enabled = canEditRemote,
                        onClick = { showResetDialog = true },
                    )
                }
            }

            item(key = "actions_about_section") {
                Md3Section(title = "快捷操作与关于") {
                    Md3ClickableItem(
                        title = "打开小布助手",
                        summary = "由模块放行白名单校验后拉起小布助手（更新后需重启一次小布助手生效）",
                        onClick = {
                            if (!HomeScreenSupport.launchBreenoAssistant(context)) {
                                showTip("未找到小布助手（com.heytap.speechassist）")
                            } else if (!uiState.isActivated) {
                                showTip("模块未激活时小布助手可能会拦截第三方拉起")
                            }
                        },
                    )
                    Md3Divider()
                    Md3ClickableItem(
                        title = "直达小布通话设置",
                        summary = "通过模块桥接直接打开小布通话音色设置页（需模块已激活并重启过小布助手）",
                        onClick = {
                            if (!uiState.isActivated) {
                                showTip("请先在 LSPosed 中激活模块并重启小布助手")
                            } else if (!HomeScreenSupport.launchAiCallSettings(context)) {
                                showTip("无法拉起小布通话设置")
                            }
                        },
                    )
                    Md3Divider()
                    Md3ClickableItem(
                        title = "小布助手应用信息",
                        summary = "跳转到系统应用详情页，方便一键强行停止小布助手以重新加载 Hook",
                        onClick = {
                            if (!HomeScreenSupport.openBreenoAppDetails(context)) {
                                showTip("无法打开系统应用信息页")
                            }
                        },
                    )
                    Md3Divider()
                    Md3ClickableItem(
                        title = "使用指南与模块架构",
                        summary = "查看配置步骤、跨进程同步机制与当前已注册的 Hook 子模块",
                        onClick = { showGuideDialog = true },
                    )
                    Md3Divider()
                    Md3InfoItem(
                        title = "模块信息",
                        summary = HomeScreenSupport.moduleInfoSummary(uiState.uiStyle),
                    )
                }
            }
        }
    }

    if (showBizModeDialog) {
        Md3SingleChoiceDialog(
            title = "TTS biz_source 策略",
            options = BizSourceMode.entries.map { it.label to it.description },
            selectedIndex = BizSourceMode.entries.indexOf(settings.bizSourceMode).coerceAtLeast(0),
            onDismiss = { showBizModeDialog = false },
            onSelect = { idx ->
                showBizModeDialog = false
                val chosen = BizSourceMode.entries.getOrElse(idx) { BizSourceMode.AUTO }
                editRemote { it.copy(bizSourceMode = chosen) }
            },
        )
    }

    if (showUiStyleDialog) {
        Md3SingleChoiceDialog(
            title = "界面风格",
            options = UiStyle.entries.map { it.label to it.description },
            selectedIndex = UiStyle.entries.indexOf(uiState.uiStyle).coerceAtLeast(0),
            onDismiss = { showUiStyleDialog = false },
            onSelect = { idx ->
                showUiStyleDialog = false
                val chosen = UiStyle.entries.getOrElse(idx) { UiStyle.MIUIX }
                XposedServiceManager.setUiStyle(chosen)
            },
        )
    }

    if (showThemeModeDialog) {
        Md3SingleChoiceDialog(
            title = "主题外观",
            options = ThemeMode.entries.map { it.label to null },
            selectedIndex = ThemeMode.entries.indexOf(uiState.themeMode).coerceAtLeast(0),
            onDismiss = { showThemeModeDialog = false },
            onSelect = { idx ->
                showThemeModeDialog = false
                val chosen = ThemeMode.entries.getOrElse(idx) { ThemeMode.SYSTEM }
                XposedServiceManager.setThemeMode(chosen)
            },
        )
    }

    if (showGuideDialog) {
        AlertDialog(
            onDismissRequest = { showGuideDialog = false },
            title = { Text(HomeScreenSupport.GUIDE_TITLE) },
            text = { Text(HomeScreenSupport.GUIDE_SUMMARY) },
            confirmButton = {
                TextButton(onClick = { showGuideDialog = false }) {
                    Text("我知道了")
                }
            },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(HomeScreenSupport.RESET_TITLE) },
            text = { Text(HomeScreenSupport.RESET_SUMMARY) },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("取消")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        if (XposedServiceManager.resetSettings()) {
                            showTip("已恢复默认配置")
                        } else {
                            showTip("重置失败，请确认模块已激活")
                        }
                    },
                ) {
                    Text("确定重置")
                }
            },
        )
    }

    if (showHideIconWarnDialog) {
        AlertDialog(
            onDismissRequest = { showHideIconWarnDialog = false },
            title = { Text(HomeScreenSupport.HIDE_ICON_WARN_TITLE) },
            text = { Text(HomeScreenSupport.HIDE_ICON_WARN_SUMMARY) },
            dismissButton = {
                TextButton(onClick = { showHideIconWarnDialog = false }) {
                    Text("取消")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showHideIconWarnDialog = false
                        applyLauncherIconHidden(true)
                    },
                ) {
                    Text("仍然隐藏")
                }
            },
        )
    }
}

@Composable
private fun Md3StatusCard(
    uiState: ServiceUiState,
    breenoVersionText: String,
    onRequestScope: () -> Unit,
) {
    val containerColor = when {
        uiState.isActivated && uiState.isTargetInScope -> MaterialTheme.colorScheme.primaryContainer
        uiState.isActivated -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when {
        uiState.isActivated && uiState.isTargetInScope -> MaterialTheme.colorScheme.onPrimaryContainer
        uiState.isActivated -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }
    val icon = when {
        uiState.isActivated && uiState.isTargetInScope -> Icons.Rounded.CheckCircle
        uiState.isActivated -> Icons.Rounded.Settings
        else -> Icons.Rounded.Warning
    }
    val title = when {
        uiState.isActivated && uiState.isTargetInScope -> "模块已激活"
        uiState.isActivated -> "模块已激活 · 待授权作用域"
        else -> "模块未激活"
    }
    val subtitle = when {
        uiState.isActivated && uiState.isTargetInScope -> {
            val fw = buildString {
                append(uiState.frameworkName.ifBlank { "LSPosed" })
                if (uiState.frameworkVersion.isNotBlank()) {
                    append(" ")
                    append(uiState.frameworkVersion)
                }
                if (uiState.apiVersion > 0) {
                    append(" · API ")
                    append(uiState.apiVersion)
                }
            }
            "$fw\n目标应用：$breenoVersionText"
        }
        uiState.isActivated -> {
            "尚未将小布助手（com.heytap.speechassist）加入作用域\n目标应用：$breenoVersionText"
        }
        else -> {
            "请在 LSPosed 管理器中启用本模块并勾选「小布助手」，然后强行停止小布助手后重试\n目标应用：$breenoVersionText"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.86f),
                    )
                }
            }

            if (uiState.isActivated && !uiState.isTargetInScope) {
                Spacer(modifier = Modifier.height(14.dp))
                FilledTonalButton(
                    onClick = onRequestScope,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("一键请求小布助手作用域")
                }
            }
        }
    }
}

@Composable
private fun Md3Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun Md3SwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.48f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun Md3ClickableItem(
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.48f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Md3InfoItem(
    title: String,
    summary: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Md3Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 18.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun Md3SingleChoiceDialog(
    title: String,
    options: List<Pair<String, String?>>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEachIndexed { index, (label, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (!desc.isNullOrEmpty()) {
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}
