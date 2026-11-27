package io.github.uchihastesla.breenocallvoice.hook.core

import android.util.Log
import io.github.uchihastesla.breenocallvoice.config.ModulePrefsReader
import io.github.uchihastesla.breenocallvoice.hook.bridge.BreenoBridgeClient
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/**
 * Shared execution context passed to every [FeatureHook].
 * Provides structured logging, live remote preferences, cross-process bridge helpers,
 * and protective libxposed API 102 hooking helpers.
 */
class HookContext(
    val module: XposedModule,
    val classLoader: ClassLoader,
    val processName: String,
    val prefs: ModulePrefsReader,
) {
    companion object {
        const val TAG = "BreenoCallCustomVoice"
    }

    val bridge: BreenoBridgeClient = BreenoBridgeClient(classLoader)

    fun hookProtective(
        method: Method,
        handler: (XposedInterface.Chain) -> Any?,
    ): XposedInterface.HookHandle = module.hook(method)
        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
        .intercept { chain -> handler(chain) }

    fun originInvoker(method: Method): XposedInterface.Invoker<*, Method> =
        module.getInvoker(method).setType(XposedInterface.Invoker.Type.ORIGIN)

    fun installAll(hooks: List<FeatureHook>) {
        for (feature in hooks) {
            runCatching {
                feature.install(this)
                logI("Installed feature hook: ${feature.name}")
            }.onFailure { err ->
                logE("Failed to install feature hook: ${feature.name}", err)
            }
        }
    }

    fun logD(msg: String) {
        if (prefs.isVerboseLogEnabled) {
            module.log(Log.DEBUG, TAG, msg)
        }
    }

    fun logI(msg: String) {
        module.log(Log.INFO, TAG, msg)
    }

    fun logW(msg: String) {
        module.log(Log.WARN, TAG, msg)
    }

    fun logE(msg: String, tr: Throwable) {
        module.log(Log.ERROR, TAG, msg, tr)
    }
}
