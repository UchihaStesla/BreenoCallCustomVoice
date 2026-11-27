package io.github.uchihastesla.breenocallvoice.config

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

data class ServiceUiState(
    val isActivated: Boolean = false,
    val frameworkName: String = "",
    val frameworkVersion: String = "",
    val apiVersion: Int = 0,
    val isTargetInScope: Boolean = false,
    val supportsRemotePrefs: Boolean = false,
    val settings: ModuleSettings = ModuleSettings(),
    val themeMode: ThemeMode = ModuleConfig.DEFAULT_THEME_MODE,
    val uiStyle: UiStyle = ModuleConfig.DEFAULT_UI_STYLE,
    val isLauncherIconHidden: Boolean = false,
)

/**
 * Bridges libxposed-service (API 102) with the Compose UI (Miuix & Material Design 3).
 * Persists feature toggles via XposedService.getRemotePreferences() when activated,
 * manages local UI style / theme preferences, and toggles the launcher icon alias.
 */
object XposedServiceManager {

    private const val TAG = "BreenoServiceMgr"

    private val initialized = AtomicBoolean(false)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var xposedService: XposedService? = null

    private val _state = MutableStateFlow(ServiceUiState())
    val state: StateFlow<ServiceUiState> = _state.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        if (!initialized.compareAndSet(false, true)) return

        val localTheme = readLocalThemeMode()
        val localUiStyle = readLocalUiStyle()
        val iconHidden = readLauncherIconHidden()
        _state.update {
            it.copy(
                themeMode = localTheme,
                uiStyle = localUiStyle,
                isLauncherIconHidden = iconHidden,
            )
        }

        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                xposedService = service
                refreshFromService(service)
            }

            override fun onServiceDied(service: XposedService) {
                if (xposedService === service) {
                    xposedService = null
                    _state.update {
                        it.copy(
                            isActivated = false,
                            supportsRemotePrefs = false,
                            isTargetInScope = false,
                        )
                    }
                }
            }
        })
    }

    fun refresh() {
        _state.update { it.copy(isLauncherIconHidden = readLauncherIconHidden()) }
        val service = xposedService ?: return
        refreshFromService(service)
    }

    private fun refreshFromService(service: XposedService) {
        val scope = runCatching { service.scope }.getOrDefault(emptyList())
        val remotePrefs = runCatching { service.getRemotePreferences(ModuleConfig.PREF_GROUP) }.getOrNull()
        val settings = remotePrefs?.let(::readSettings) ?: ModuleSettings()

        _state.update { current ->
            current.copy(
                isActivated = true,
                frameworkName = runCatching { service.frameworkName }.getOrDefault("LSPosed"),
                frameworkVersion = runCatching { service.frameworkVersion }.getOrDefault(""),
                apiVersion = runCatching { service.apiVersion }.getOrDefault(0),
                isTargetInScope = scope.contains(ModuleConfig.TARGET_PACKAGE),
                supportsRemotePrefs = remotePrefs != null,
                settings = settings,
                isLauncherIconHidden = readLauncherIconHidden(),
            )
        }
    }

    fun updateSettings(transform: (ModuleSettings) -> ModuleSettings): Boolean {
        val service = xposedService ?: return false
        val prefs = runCatching { service.getRemotePreferences(ModuleConfig.PREF_GROUP) }.getOrNull()
            ?: return false

        val updated = transform(_state.value.settings)
        val ok = runCatching {
            prefs.edit()
                .putBoolean(ModuleConfig.KEY_ENABLE_CUSTOM_TIMBRE, updated.enableCustomTimbre)
                .putBoolean(ModuleConfig.KEY_ENABLE_AUTO_CLOUD_REFRESH, updated.enableAutoCloudRefresh)
                .putBoolean(ModuleConfig.KEY_ENABLE_OFFLINE_FALLBACK, updated.enableOfflineFallback)
                .putInt(ModuleConfig.KEY_BIZ_SOURCE_MODE, updated.bizSourceMode.value)
                .putBoolean(ModuleConfig.KEY_BLOCK_APP_UPDATE, updated.blockAppUpdate)
                .putBoolean(ModuleConfig.KEY_VERBOSE_LOG, updated.verboseLog)
                .apply()
            true
        }.onFailure {
            Log.e(TAG, "Failed to save remote preferences", it)
        }.getOrDefault(false)

        if (ok) {
            _state.update { it.copy(settings = updated) }
        }
        return ok
    }

    fun resetSettings(): Boolean = updateSettings { ModuleSettings() }

    fun setThemeMode(mode: ThemeMode) {
        localUiPrefs()?.edit()?.putInt(ModuleConfig.KEY_THEME_MODE, mode.value)?.apply()
        _state.update { it.copy(themeMode = mode) }
    }

    fun setUiStyle(style: UiStyle) {
        localUiPrefs()?.edit()?.putInt(ModuleConfig.KEY_UI_STYLE, style.value)?.apply()
        _state.update { it.copy(uiStyle = style) }
    }

    fun setLauncherIconHidden(hidden: Boolean): Boolean {
        val ctx = appContext ?: return false
        val ok = runCatching {
            val component = ComponentName(ctx.packageName, ModuleConfig.LAUNCHER_ALIAS_CLASS)
            val newState = if (hidden) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            ctx.packageManager.setComponentEnabledSetting(
                component,
                newState,
                PackageManager.DONT_KILL_APP,
            )
            true
        }.onFailure {
            Log.e(TAG, "Failed to toggle launcher icon alias", it)
        }.getOrDefault(false)

        if (ok) {
            _state.update { it.copy(isLauncherIconHidden = hidden) }
        }
        return ok
    }

    fun requestTargetScope(onResult: (approved: Boolean, message: String) -> Unit) {
        val service = xposedService
        if (service == null) {
            onResult(false, "模块尚未在 LSPosed 中激活")
            return
        }
        runCatching {
            service.requestScope(
                listOf(ModuleConfig.TARGET_PACKAGE),
                object : XposedService.OnScopeEventListener {
                    override fun onScopeRequestApproved(approved: List<String>) {
                        refreshFromService(service)
                        val granted = approved.contains(ModuleConfig.TARGET_PACKAGE)
                        onResult(
                            granted,
                            if (granted) "已将小布助手加入模块作用域" else "作用域请求未包含小布助手",
                        )
                    }

                    override fun onScopeRequestFailed(message: String) {
                        onResult(false, message.ifBlank { "作用域请求被取消或拒绝" })
                    }
                },
            )
        }.onFailure { err ->
            Log.e(TAG, "requestScope failed", err)
            onResult(false, err.message ?: "请求作用域失败")
        }
    }

    private fun readLauncherIconHidden(): Boolean {
        val ctx = appContext ?: return false
        return runCatching {
            val component = ComponentName(ctx.packageName, ModuleConfig.LAUNCHER_ALIAS_CLASS)
            ctx.packageManager.getComponentEnabledSetting(component) ==
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }.getOrDefault(false)
    }

    private fun readSettings(prefs: SharedPreferences): ModuleSettings = ModuleSettings(
        enableCustomTimbre = prefs.getBoolean(
            ModuleConfig.KEY_ENABLE_CUSTOM_TIMBRE,
            ModuleConfig.DEFAULT_ENABLE_CUSTOM_TIMBRE,
        ),
        enableAutoCloudRefresh = prefs.getBoolean(
            ModuleConfig.KEY_ENABLE_AUTO_CLOUD_REFRESH,
            ModuleConfig.DEFAULT_ENABLE_AUTO_CLOUD_REFRESH,
        ),
        enableOfflineFallback = prefs.getBoolean(
            ModuleConfig.KEY_ENABLE_OFFLINE_FALLBACK,
            ModuleConfig.DEFAULT_ENABLE_OFFLINE_FALLBACK,
        ),
        bizSourceMode = BizSourceMode.fromValue(
            prefs.getInt(
                ModuleConfig.KEY_BIZ_SOURCE_MODE,
                ModuleConfig.DEFAULT_BIZ_SOURCE_MODE.value,
            ),
        ),
        blockAppUpdate = prefs.getBoolean(
            ModuleConfig.KEY_BLOCK_APP_UPDATE,
            ModuleConfig.DEFAULT_BLOCK_APP_UPDATE,
        ),
        verboseLog = prefs.getBoolean(
            ModuleConfig.KEY_VERBOSE_LOG,
            ModuleConfig.DEFAULT_VERBOSE_LOG,
        ),
    )

    private fun localUiPrefs(): SharedPreferences? =
        appContext?.getSharedPreferences(ModuleConfig.LOCAL_UI_PREFS, Context.MODE_PRIVATE)

    private fun readLocalThemeMode(): ThemeMode {
        val raw = localUiPrefs()?.getInt(
            ModuleConfig.KEY_THEME_MODE,
            ModuleConfig.DEFAULT_THEME_MODE.value,
        ) ?: ModuleConfig.DEFAULT_THEME_MODE.value
        return ThemeMode.fromValue(raw)
    }

    private fun readLocalUiStyle(): UiStyle {
        val raw = localUiPrefs()?.getInt(
            ModuleConfig.KEY_UI_STYLE,
            ModuleConfig.DEFAULT_UI_STYLE.value,
        ) ?: ModuleConfig.DEFAULT_UI_STYLE.value
        return UiStyle.fromValue(raw)
    }
}
