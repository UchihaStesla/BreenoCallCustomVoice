package io.github.uchihastesla.breenocallvoice.config

/**
 * Single source of truth for module preferences, keys, and default values.
 * Shared across the UI (via XposedService.getRemotePreferences)
 * and the hooked process (via XposedModule.getRemotePreferences).
 */
object ModuleConfig {
    const val PREF_GROUP = "breeno_call_voice_prefs"
    const val LOCAL_UI_PREFS = "breeno_call_voice_ui_local"

    const val TARGET_PACKAGE = "com.heytap.speechassist"
    const val MAIN_PROCESS = "com.heytap.speechassist"
    const val AICALL_PROCESS = "com.heytap.speechassist:aiCall"
    const val LAUNCHER_ALIAS_CLASS = "io.github.uchihastesla.breenocallvoice.ui.LauncherAlias"

    // Remote preference keys (synced from UI -> LSPosed -> target app)
    const val KEY_ENABLE_CUSTOM_TIMBRE = "enable_custom_timbre"
    const val KEY_ENABLE_AUTO_CLOUD_REFRESH = "enable_auto_cloud_refresh"
    const val KEY_ENABLE_OFFLINE_FALLBACK = "enable_offline_fallback"
    const val KEY_BIZ_SOURCE_MODE = "biz_source_mode"
    const val KEY_TTS_SPEED = "tts_speed"
    const val KEY_BLOCK_APP_UPDATE = "block_app_update"
    const val KEY_VERBOSE_LOG = "verbose_log"

    // Local UI-only preference keys
    const val KEY_THEME_MODE = "ui_theme_mode"
    const val KEY_UI_STYLE = "ui_style"

    // TTS speed range (matches Breeno Assistant HeytapTtsEngine 0-100 scale; d2 presets: 10/25/50/75/100)
    const val MIN_TTS_SPEED = 10
    const val MAX_TTS_SPEED = 100
    const val STEP_TTS_SPEED = 5
    const val DEFAULT_TTS_SPEED = 50

    // Defaults
    const val DEFAULT_ENABLE_CUSTOM_TIMBRE = true
    const val DEFAULT_ENABLE_AUTO_CLOUD_REFRESH = true
    const val DEFAULT_ENABLE_OFFLINE_FALLBACK = true
    const val DEFAULT_BLOCK_APP_UPDATE = true
    const val DEFAULT_VERBOSE_LOG = false
    val DEFAULT_BIZ_SOURCE_MODE = BizSourceMode.AUTO
    val DEFAULT_THEME_MODE = ThemeMode.SYSTEM
    val DEFAULT_UI_STYLE = UiStyle.MIUIX

    fun clampTtsSpeed(value: Int): Int = value.coerceIn(MIN_TTS_SPEED, MAX_TTS_SPEED)

    fun snapTtsSpeed(value: Float): Int {
        val snapped = Math.round(value / STEP_TTS_SPEED) * STEP_TTS_SPEED
        return clampTtsSpeed(snapped)
    }

    fun formatTtsSpeedLabel(speed: Int): String {
        val clamped = clampTtsSpeed(speed)
        val tier = when {
            clamped == DEFAULT_TTS_SPEED -> "标准（默认）"
            clamped <= 15 -> "较慢"
            clamped <= 35 -> "慢"
            clamped < DEFAULT_TTS_SPEED -> "偏慢"
            clamped <= 65 -> "偏快"
            clamped <= 85 -> "快"
            else -> "较快"
        }
        return "$clamped · $tier"
    }
}

enum class BizSourceMode(val value: Int, val label: String, val description: String) {
    AUTO(
        value = 0,
        label = "自动回退（推荐）",
        description = "优先保留 AI.Call 标记；若服务端拒绝自定义音色则自动清空并重试",
    ),
    FORCE_STRIP(
        value = 1,
        label = "强制清空 biz_source",
        description = "自定义音色合成时直接清空 AI.Call 标记，避免首句失败重试",
    ),
    KEEP_ORIGINAL(
        value = 2,
        label = "保持原始参数",
        description = "不修改 biz_source，仅注入 USER_TIMBRE 与账号鉴权信息",
    );

    companion object {
        fun fromValue(value: Int): BizSourceMode =
            entries.firstOrNull { it.value == value } ?: AUTO
    }
}

enum class UiStyle(val value: Int, val label: String, val description: String) {
    MIUIX(
        value = 0,
        label = "Miuix（澎湃风格）",
        description = "HyperOS / MIUI 卡片与弹性滚动视觉风格",
    ),
    MATERIAL3(
        value = 1,
        label = "Material Design 3",
        description = "Google Material You 现代设计语言，支持动态取色",
    );

    companion object {
        fun fromValue(value: Int): UiStyle =
            entries.firstOrNull { it.value == value } ?: MIUIX
    }
}

enum class ThemeMode(val value: Int, val label: String) {
    SYSTEM(0, "跟随系统"),
    LIGHT(1, "浅色模式"),
    DARK(2, "深色模式"),
    MONET_SYSTEM(3, "动态取色（跟随系统）");

    companion object {
        fun fromValue(value: Int): ThemeMode =
            entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

data class ModuleSettings(
    val enableCustomTimbre: Boolean = ModuleConfig.DEFAULT_ENABLE_CUSTOM_TIMBRE,
    val enableAutoCloudRefresh: Boolean = ModuleConfig.DEFAULT_ENABLE_AUTO_CLOUD_REFRESH,
    val enableOfflineFallback: Boolean = ModuleConfig.DEFAULT_ENABLE_OFFLINE_FALLBACK,
    val bizSourceMode: BizSourceMode = ModuleConfig.DEFAULT_BIZ_SOURCE_MODE,
    val ttsSpeed: Int = ModuleConfig.DEFAULT_TTS_SPEED,
    val blockAppUpdate: Boolean = ModuleConfig.DEFAULT_BLOCK_APP_UPDATE,
    val verboseLog: Boolean = ModuleConfig.DEFAULT_VERBOSE_LOG,
)
