package com.custom.breenocallvoice.ui.screen.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.custom.breenocallvoice.config.BizSourceMode
import com.custom.breenocallvoice.config.ModuleSettings
import com.custom.breenocallvoice.config.ServiceUiState
import com.custom.breenocallvoice.config.ThemeMode
import com.custom.breenocallvoice.config.UiStyle
import com.custom.breenocallvoice.config.XposedServiceManager
import com.custom.breenocallvoice.ui.screen.HomeScreenSupport
import com.custom.breenocallvoice.ui.screen.components.StatusCard
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun MiuixHomeScreen(uiState: ServiceUiState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    var showGuideDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showHideIconWarnDialog by remember { mutableStateOf(false) }

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

    val bizModeLabels = remember { BizSourceMode.entries.map { it.label } }
    val uiStyleLabels = remember { UiStyle.entries.map { it.label } }
    val themeModeLabels = remember { ThemeMode.entries.map { it.label } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "小布通话音色",
                largeTitle = "小布通话音色",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = {
                            XposedServiceManager.refresh()
                            showTip(if (uiState.isActivated) "已刷新模块与配置状态" else "尚未连接到 LSPosed 服务")
                        },
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = "刷新状态",
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { showGuideDialog = true }) {
                        Icon(
                            imageVector = MiuixIcons.Info,
                            contentDescription = "使用说明",
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "status") {
                StatusCard(
                    uiState = uiState,
                    breenoVersionText = breenoVersionText,
                    onRequestScope = {
                        XposedServiceManager.requestTargetScope { _, msg ->
                            showTip(msg)
                        }
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item(key = "timbre_section") {
                SmallTitle(text = "自定义音色")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    SwitchPreference(
                        title = "启用自定义音色",
                        summary = "跨进程同步小布助手自定义音色，并注入小布通话列表与云端 TTS 引擎",
                        checked = settings.enableCustomTimbre,
                        enabled = canEditRemote,
                        onCheckedChange = { checked ->
                            editRemote { it.copy(enableCustomTimbre = checked) }
                        },
                    )
                    SwitchPreference(
                        title = "自动云端刷新音色",
                        summary = "本地缓存为空时通过主进程自动拉取云端自定义音色（需登录欢太账号）",
                        checked = settings.enableAutoCloudRefresh,
                        enabled = canEditRemote && settings.enableCustomTimbre,
                        onCheckedChange = { checked ->
                            editRemote { it.copy(enableAutoCloudRefresh = checked) }
                        },
                    )
                    SwitchPreference(
                        title = "离线播报安全回退",
                        summary = "自定义音色缺少离线资源时自动回退至默认离线音色，防止无声或崩溃",
                        checked = settings.enableOfflineFallback,
                        enabled = canEditRemote && settings.enableCustomTimbre,
                        onCheckedChange = { checked ->
                            editRemote { it.copy(enableOfflineFallback = checked) }
                        },
                    )
                    OverlayDropdownPreference(
                        title = "TTS biz_source 策略",
                        summary = settings.bizSourceMode.description,
                        items = bizModeLabels,
                        selectedIndex = BizSourceMode.entries.indexOf(settings.bizSourceMode).coerceAtLeast(0),
                        enabled = canEditRemote && settings.enableCustomTimbre,
                        onSelectedIndexChange = { index ->
                            val chosen = BizSourceMode.entries.getOrElse(index) { BizSourceMode.AUTO }
                            editRemote { it.copy(bizSourceMode = chosen) }
                        },
                    )
                }
            }

            item(key = "update_section") {
                SmallTitle(text = "更新拦截")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    SwitchPreference(
                        title = "阻止小布助手应用内更新",
                        summary = "拦截 UpgradeSDK 自更新检测（显示已是最新版本）、更新弹窗、通知与 APK 下载安装",
                        checked = settings.blockAppUpdate,
                        enabled = canEditRemote,
                        onCheckedChange = { checked ->
                            editRemote { it.copy(blockAppUpdate = checked) }
                        },
                    )
                    BasicComponent(
                        title = "关于系统应用商店更新",
                        summary = "本开关仅拦截小布助手应用内自更新；若需防止被系统软件商店静默升级，请在软件商店中关闭自动更新。",
                    )
                }
            }

            item(key = "appearance_debug_section") {
                SmallTitle(text = "外观与调试")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    OverlayDropdownPreference(
                        title = "界面风格",
                        summary = uiState.uiStyle.description,
                        items = uiStyleLabels,
                        selectedIndex = UiStyle.entries.indexOf(uiState.uiStyle).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            val chosen = UiStyle.entries.getOrElse(index) { UiStyle.MIUIX }
                            XposedServiceManager.setUiStyle(chosen)
                        },
                    )
                    OverlayDropdownPreference(
                        title = "主题外观",
                        summary = "切换浅色、深色或 Android 动态取色（Monet）",
                        items = themeModeLabels,
                        selectedIndex = ThemeMode.entries.indexOf(uiState.themeMode).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            val chosen = ThemeMode.entries.getOrElse(index) { ThemeMode.SYSTEM }
                            XposedServiceManager.setThemeMode(chosen)
                        },
                    )
                    SwitchPreference(
                        title = "隐藏桌面图标",
                        summary = "隐藏启动器中的模块图标，之后可通过 LSPosed 管理器点击本模块打开设置",
                        checked = uiState.isLauncherIconHidden,
                        onCheckedChange = { checked ->
                            if (checked && !uiState.isActivated) {
                                showHideIconWarnDialog = true
                            } else {
                                applyLauncherIconHidden(checked)
                            }
                        },
                    )
                    SwitchPreference(
                        title = "详细调试日志",
                        summary = "向 LSPosed 日志输出更多 Hook 调试信息（Tag: BreenoCallCustomVoice）",
                        checked = settings.verboseLog,
                        enabled = canEditRemote,
                        onCheckedChange = { checked ->
                            editRemote { it.copy(verboseLog = checked) }
                        },
                    )
                    ArrowPreference(
                        title = "恢复默认配置",
                        summary = "将所有远程 Hook 开关重置为推荐默认值",
                        enabled = canEditRemote,
                        onClick = { showResetDialog = true },
                    )
                }
            }

            item(key = "actions_about_section") {
                SmallTitle(text = "快捷操作与关于")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    ArrowPreference(
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
                    ArrowPreference(
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
                    ArrowPreference(
                        title = "小布助手应用信息",
                        summary = "跳转到系统应用详情页，方便一键强行停止小布助手以重新加载 Hook",
                        onClick = {
                            if (!HomeScreenSupport.openBreenoAppDetails(context)) {
                                showTip("无法打开系统应用信息页")
                            }
                        },
                    )
                    ArrowPreference(
                        title = "使用指南与模块架构",
                        summary = "查看配置步骤、跨进程同步机制与当前已注册的 Hook 子模块",
                        onClick = { showGuideDialog = true },
                    )
                    BasicComponent(
                        title = "模块信息",
                        summary = HomeScreenSupport.moduleInfoSummary(uiState.uiStyle),
                    )
                }
            }
        }

        OverlayDialog(
            show = showGuideDialog,
            title = HomeScreenSupport.GUIDE_TITLE,
            summary = HomeScreenSupport.GUIDE_SUMMARY,
            onDismissRequest = { showGuideDialog = false },
        ) {
            TextButton(
                text = "我知道了",
                onClick = { showGuideDialog = false },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }

        OverlayDialog(
            show = showResetDialog,
            title = HomeScreenSupport.RESET_TITLE,
            summary = HomeScreenSupport.RESET_SUMMARY,
            onDismissRequest = { showResetDialog = false },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = { showResetDialog = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "确定重置",
                    onClick = {
                        showResetDialog = false
                        if (XposedServiceManager.resetSettings()) {
                            showTip("已恢复默认配置")
                        } else {
                            showTip("重置失败，请确认模块已激活")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }

        OverlayDialog(
            show = showHideIconWarnDialog,
            title = HomeScreenSupport.HIDE_ICON_WARN_TITLE,
            summary = HomeScreenSupport.HIDE_ICON_WARN_SUMMARY,
            onDismissRequest = { showHideIconWarnDialog = false },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = { showHideIconWarnDialog = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "仍然隐藏",
                    onClick = {
                        showHideIconWarnDialog = false
                        applyLauncherIconHidden(true)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
