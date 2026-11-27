package io.github.uchihastesla.breenocallvoice.hook.bridge

import android.app.Activity
import android.content.Intent
import io.github.uchihastesla.breenocallvoice.config.ModuleConfig
import io.github.uchihastesla.breenocallvoice.hook.core.FeatureHook
import io.github.uchihastesla.breenocallvoice.hook.core.HookContext
import io.github.uchihastesla.breenocallvoice.hook.core.ReflectExt

/**
 * Main-process hook allowing our own module app (`io.github.uchihastesla.breenocallvoice`) to launch
 * `SpeechAssistMainActivity` without being rejected by Breeno's `DeeplinkRemoteConfig` caller
 * whitelist (`SpeechAssistMainActivity.I5()`), and optionally forwarding straight to the
 * non-exported `XiaoBuAiCallActivity` (`heytap.intent.action.AI_CALL_SETTING`).
 */
class LauncherAllowHook : FeatureHook {

    override val name: String = "LauncherAllowHook"

    override fun install(ctx: HookContext) {
        runCatching {
            val mainActCls = ReflectExt.findClass(
                ctx.classLoader,
                BreenoConstants.CLASS_SPEECH_ASSIST_MAIN_ACTIVITY,
            )
            val checkCallerMethod = ReflectExt.findMethod(mainActCls, "I5")
            ctx.hookProtective(checkCallerMethod) { chain ->
                val activity = chain.thisObject as? Activity
                if (activity != null && isFromModule(activity)) {
                    val route = activity.intent?.getStringExtra(BreenoConstants.EXTRA_LAUNCH_ROUTE)
                    if (route == BreenoConstants.ROUTE_AICALL_SETTINGS) {
                        val forwarded = runCatching {
                            val targetIntent = Intent(BreenoConstants.ACTION_AI_CALL_SETTING).apply {
                                setPackage(ModuleConfig.TARGET_PACKAGE)
                            }
                            activity.startActivity(targetIntent)
                            true
                        }.onFailure {
                            ctx.logE("Failed to forward to XiaoBuAiCallActivity", it)
                        }.getOrDefault(false)
                        if (forwarded) {
                            ctx.logI("Forwarded module launch to XiaoBuAiCallActivity")
                            return@hookProtective false
                        }
                    }
                    ctx.logI("Allowed SpeechAssistMainActivity launch from module")
                    return@hookProtective true
                }
                chain.proceed()
            }
        }.onFailure { ctx.logE("Failed to hook SpeechAssistMainActivity.I5", it) }
    }

    private fun isFromModule(activity: Activity): Boolean {
        val referrerHost = runCatching { activity.referrer?.host }.getOrNull()
        if (referrerHost == BreenoConstants.MODULE_PACKAGE) return true
        val callingPkg = runCatching { activity.callingPackage }.getOrNull()
        return callingPkg == BreenoConstants.MODULE_PACKAGE
    }
}
