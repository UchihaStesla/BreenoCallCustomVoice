package io.github.uchihastesla.breenocallvoice.hook

import android.util.Log
import io.github.uchihastesla.breenocallvoice.config.ModuleConfig
import io.github.uchihastesla.breenocallvoice.config.ModulePrefsReader
import io.github.uchihastesla.breenocallvoice.hook.bridge.LauncherAllowHook
import io.github.uchihastesla.breenocallvoice.hook.core.FeatureHook
import io.github.uchihastesla.breenocallvoice.hook.core.HookContext
import io.github.uchihastesla.breenocallvoice.hook.timbre.AiCallOfflineFallbackHook
import io.github.uchihastesla.breenocallvoice.hook.timbre.AiCallToneInjectHook
import io.github.uchihastesla.breenocallvoice.hook.timbre.AiCallTtsEngineHook
import io.github.uchihastesla.breenocallvoice.hook.timbre.MainTimbreSyncHook
import io.github.uchihastesla.breenocallvoice.hook.update.BlockUpdateHook
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin LSPosed (libxposed API 102) entry point declared in META-INF/xposed/java_init.list.
 * Responsible solely for process filtering, detach() on irrelevant processes, and dispatching
 * to modular [FeatureHook] implementations.
 */
class HookEntry : XposedModule() {

    private val hooksInstalled = AtomicBoolean(false)

    @Volatile
    private var currentProcessName: String = ""

    private val mainProcessHooks: List<FeatureHook> = listOf(
        MainTimbreSyncHook(),
        BlockUpdateHook(),
        LauncherAllowHook(),
    )

    private val aiCallProcessHooks: List<FeatureHook> = listOf(
        AiCallToneInjectHook(),
        AiCallTtsEngineHook(),
        AiCallOfflineFallbackHook(),
    )

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        currentProcessName = param.processName
        if (!isMainProcess(currentProcessName) && !isAiCallProcess(currentProcessName)) {
            if (apiVersion >= XposedInterface.API_102) {
                detach()
            }
            return
        }
        log(
            Log.INFO,
            HookContext.TAG,
            "Loaded in process=$currentProcessName, api=$apiVersion, framework=$frameworkName $frameworkVersion",
        )
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.packageName != ModuleConfig.TARGET_PACKAGE) return
        if (!hooksInstalled.compareAndSet(false, true)) return

        val ctx = HookContext(
            module = this,
            classLoader = param.classLoader,
            processName = currentProcessName,
            prefs = ModulePrefsReader(this),
        )

        when {
            isMainProcess(currentProcessName) -> {
                ctx.logI("Dispatching main process feature hooks")
                ctx.installAll(mainProcessHooks)
            }
            isAiCallProcess(currentProcessName) -> {
                ctx.logI("Dispatching :aiCall process feature hooks")
                ctx.installAll(aiCallProcessHooks)
            }
        }
    }

    private fun isMainProcess(process: String): Boolean =
        process == ModuleConfig.MAIN_PROCESS

    private fun isAiCallProcess(process: String): Boolean =
        process == ModuleConfig.AICALL_PROCESS || process.endsWith(":aiCall")
}
