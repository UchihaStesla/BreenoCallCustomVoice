package com.custom.breenocallvoice.config

import android.content.SharedPreferences
import io.github.libxposed.api.XposedInterface

/**
 * Safe read-only configuration accessor for the hooked process.
 * Reads live values from libxposed RemotePreferences; gracefully falls back to defaults
 * if the framework does not expose remote preferences.
 */
class ModulePrefsReader(private val xposed: XposedInterface) {

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun prefs(): SharedPreferences? {
        cachedPrefs?.let { return it }
        return runCatching { xposed.getRemotePreferences(ModuleConfig.PREF_GROUP) }
            .getOrNull()
            ?.also { cachedPrefs = it }
    }

    val isCustomTimbreEnabled: Boolean
        get() = prefs()?.getBoolean(
            ModuleConfig.KEY_ENABLE_CUSTOM_TIMBRE,
            ModuleConfig.DEFAULT_ENABLE_CUSTOM_TIMBRE,
        ) ?: ModuleConfig.DEFAULT_ENABLE_CUSTOM_TIMBRE

    val isAutoCloudRefreshEnabled: Boolean
        get() = prefs()?.getBoolean(
            ModuleConfig.KEY_ENABLE_AUTO_CLOUD_REFRESH,
            ModuleConfig.DEFAULT_ENABLE_AUTO_CLOUD_REFRESH,
        ) ?: ModuleConfig.DEFAULT_ENABLE_AUTO_CLOUD_REFRESH

    val isOfflineFallbackEnabled: Boolean
        get() = prefs()?.getBoolean(
            ModuleConfig.KEY_ENABLE_OFFLINE_FALLBACK,
            ModuleConfig.DEFAULT_ENABLE_OFFLINE_FALLBACK,
        ) ?: ModuleConfig.DEFAULT_ENABLE_OFFLINE_FALLBACK

    val bizSourceMode: BizSourceMode
        get() = BizSourceMode.fromValue(
            prefs()?.getInt(
                ModuleConfig.KEY_BIZ_SOURCE_MODE,
                ModuleConfig.DEFAULT_BIZ_SOURCE_MODE.value,
            ) ?: ModuleConfig.DEFAULT_BIZ_SOURCE_MODE.value,
        )

    val isBlockAppUpdateEnabled: Boolean
        get() = prefs()?.getBoolean(
            ModuleConfig.KEY_BLOCK_APP_UPDATE,
            ModuleConfig.DEFAULT_BLOCK_APP_UPDATE,
        ) ?: ModuleConfig.DEFAULT_BLOCK_APP_UPDATE

    val isVerboseLogEnabled: Boolean
        get() = prefs()?.getBoolean(
            ModuleConfig.KEY_VERBOSE_LOG,
            ModuleConfig.DEFAULT_VERBOSE_LOG,
        ) ?: ModuleConfig.DEFAULT_VERBOSE_LOG
}
