package io.github.uchihastesla.breenocallvoice.ui.screen

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.github.uchihastesla.breenocallvoice.BuildConfig
import io.github.uchihastesla.breenocallvoice.config.ModuleConfig
import io.github.uchihastesla.breenocallvoice.config.UiStyle
import io.github.uchihastesla.breenocallvoice.hook.bridge.BreenoConstants

internal object HomeScreenSupport {

    const val GUIDE_TITLE = "使用指南与架构说明"
    const val GUIDE_SUMMARY = "1. 在 LSPosed 中启用本模块并勾选「小布助手」（com.heytap.speechassist）。\n" +
        "2. 点下方「小布助手应用信息」强行停止并重启小布助手。\n" +
        "3. 在小布助手「音色设置」确认自定义音色已训练完成并缓存。\n" +
        "4. 打开「小布通话 → 与联系人/陌生人通话音色」，滑到底部即可选用你的自定义音色。\n\n" +
        "模块化 Hook 架构：\n" +
        "• HookEntry：进程路由与 libxposed API 102 detach()\n" +
        "• MainTimbreSyncHook：主进程缓存镜像与 Binder IPC 扩展\n" +
        "• LauncherAllowHook：放行模块拉起小布助手并支持直达小布通话设置\n" +
        "• AiCallToneInjectHook：:aiCall 音色列表合并与界面刷新\n" +
        "• AiCallTtsEngineHook：TTS 参数注入与 biz_source 自动回退\n" +
        "• AiCallOfflineFallbackHook：离线播报安全回退\n" +
        "• BlockUpdateHook：拦截 UpgradeSDK 与更新弹窗/通知"

    const val RESET_TITLE = "恢复默认配置"
    const val RESET_SUMMARY = "确定要将所有功能开关与 TTS 策略重置为推荐默认值吗？修改将通过 libxposed RemotePreferences 实时同步。"

    const val HIDE_ICON_WARN_TITLE = "确认隐藏桌面图标"
    const val HIDE_ICON_WARN_SUMMARY = "当前未检测到模块处于激活状态。隐藏桌面图标后，只能通过 LSPosed 管理器重新打开本界面，确定继续吗？"

    fun moduleInfoSummary(uiStyle: UiStyle): String {
        val styleTag = when (uiStyle) {
            UiStyle.MIUIX -> "Miuix 0.9.4"
            UiStyle.MATERIAL3 -> "Material Design 3"
        }
        return "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · libxposed API 102 · $styleTag"
    }

    fun queryTargetVersion(context: Context): String = runCatching {
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

    fun launchBreenoAssistant(context: Context): Boolean {
        val explicit = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(
                ModuleConfig.TARGET_PACKAGE,
                BreenoConstants.CLASS_SPEECH_ASSIST_MAIN_ACTIVITY,
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(explicit); true }.getOrDefault(false)) {
            return true
        }
        return runCatching {
            val fallback = context.packageManager.getLaunchIntentForPackage(ModuleConfig.TARGET_PACKAGE)
                ?: return false
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
            true
        }.getOrDefault(false)
    }

    fun launchAiCallSettings(context: Context): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(
                ModuleConfig.TARGET_PACKAGE,
                BreenoConstants.CLASS_SPEECH_ASSIST_MAIN_ACTIVITY,
            )
            putExtra(BreenoConstants.EXTRA_LAUNCH_ROUTE, BreenoConstants.ROUTE_AICALL_SETTINGS)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    fun openBreenoAppDetails(context: Context): Boolean = runCatching {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", ModuleConfig.TARGET_PACKAGE, null),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
