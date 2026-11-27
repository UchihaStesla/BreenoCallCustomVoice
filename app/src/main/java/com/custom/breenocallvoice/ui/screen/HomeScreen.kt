package com.custom.breenocallvoice.ui.screen

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.custom.breenocallvoice.BuildConfig
import com.custom.breenocallvoice.config.BizSourceMode
import com.custom.breenocallvoice.config.ModuleConfig
import com.custom.breenocallvoice.config.ModuleSettings
import com.custom.breenocallvoice.config.ServiceUiState
import com.custom.breenocallvoice.config.ThemeMode
import com.custom.breenocallvoice.config.XposedServiceManager
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
import top.yukonga.miuix.kmp.basic.Text
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
fun HomeScreen(uiState: ServiceUiState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    var showGuideDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    val breenoVersionText = remember(context) { queryTargetVersion(context) }
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

    val bizModeLabels = remember { BizSourceMode.entries.map { it.label } }
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
                        summary = "进入小布助手确认自定义音色已录制并缓存，再前往小布通话设置切换",
                        onClick = {
                            if (!launchTargetApp(context)) {
                                showTip("未找到小布助手（com.heytap.speechassist）")
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
                        summary = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · libxposed API 102 · Miuix 0.9.4",
                    )
                }
            }
        }

        OverlayDialog(
            show = showGuideDialog,
            title = "使用指南与架构说明",
            summary = "1. 在 LSPosed 中启用本模块并勾选「小布助手」（com.heytap.speechassist）。\n" +
                "2. 强行停止小布助手后重新打开，进入「声音设置」确保自定义音色已生成并缓存。\n" +
                "3. 进入「小布通话 → 音色设置」即可看到并选用你的自定义音色。\n\n" +
                "模块化 Hook 架构：\n" +
                "• HookEntry：仅负责进程路由与 API 102 detach()\n" +
                "• MainTimbreSyncHook：主进程缓存镜像与 Binder IPC 扩展\n" +
                "• AiCallToneInjectHook：:aiCall 音色列表合并与界面刷新\n" +
                "• AiCallTtsEngineHook：TTS 参数注入与 biz_source 自动回退\n" +
                "• AiCallOfflineFallbackHook：离线播报安全回退\n" +
                "• BlockUpdateHook：拦截 UpgradeSDK 与更新弹窗/通知",
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
            title = "恢复默认配置",
            summary = "确定要将所有功能开关与 TTS 策略重置为推荐默认值吗？修改将通过 libxposed RemotePreferences 实时同步。",
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
    }
}

private fun queryTargetVersion(context: Context): String = runCatching {
    val pm = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(ModuleConfig.TARGET_PACKAGE, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(ModuleConfig.TARGET_PACKAGE, 0)
    }
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
    "小布助手 v${info.versionName ?: "?"} ($code)"
}.getOrDefault("未检测到小布助手")

private fun launchTargetApp(context: Context): Boolean = runCatching {
    val intent = context.packageManager.getLaunchIntentForPackage(ModuleConfig.TARGET_PACKAGE)
        ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
}.getOrDefault(false)
