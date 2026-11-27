package com.custom.breenocallvoice.hook.timbre

import com.custom.breenocallvoice.hook.bridge.BreenoConstants
import com.custom.breenocallvoice.hook.bridge.TimbreStore
import com.custom.breenocallvoice.hook.core.FeatureHook
import com.custom.breenocallvoice.hook.core.HookContext
import com.custom.breenocallvoice.hook.core.ReflectExt

/**
 * `:aiCall` process hook providing safe offline TTS fallback when a custom timbre has no
 * bundled offline model or offline greeting text.
 */
class AiCallOfflineFallbackHook : FeatureHook {

    override val name: String = "AiCallOfflineFallbackHook"

    override fun install(ctx: HookContext) {
        hookOfflineDownloadPath(ctx)
        hookOfflineGreetingText(ctx)
    }

    private fun hookOfflineDownloadPath(ctx: HookContext) {
        runCatching {
            val helperCls = ReflectExt.findClass(
                ctx.classLoader,
                BreenoConstants.CLASS_OFFLINE_DOWNLOAD_HELPER,
            )
            val method = ReflectExt.findMethod(
                helperCls,
                "j",
                Boolean::class.javaPrimitiveType!!,
                String::class.java,
                String::class.java,
            )
            ctx.hookProtective(method) { chain ->
                if (!ctx.prefs.isCustomTimbreEnabled || !ctx.prefs.isOfflineFallbackEnabled) {
                    return@hookProtective chain.proceed()
                }
                val timbre = chain.getArg(1) as? String
                if (TimbreStore.isCustomTimbre(timbre)) {
                    ctx.logD("Offline path fallback: $timbre -> ${BreenoConstants.FALLBACK_OFFLINE_TIMBRE}")
                    chain.proceed(arrayOf(chain.getArg(0), BreenoConstants.FALLBACK_OFFLINE_TIMBRE, chain.getArg(2)))
                } else {
                    chain.proceed()
                }
            }
        }.onFailure { ctx.logE("Failed to hook AiCallOfflineDownloadHelper.j", it) }
    }

    private fun hookOfflineGreetingText(ctx: HookContext) {
        runCatching {
            val mgrCls = ReflectExt.findClass(
                ctx.classLoader,
                BreenoConstants.CLASS_OFFLINE_TTS_MANAGER,
            )
            val method = ReflectExt.findMethod(mgrCls, "h", Boolean::class.javaPrimitiveType!!)
            ctx.hookProtective(method) { chain ->
                val res = chain.proceed()
                if (res != null || !ctx.prefs.isCustomTimbreEnabled || !ctx.prefs.isOfflineFallbackEnabled) {
                    return@hookProtective res
                }
                val isContact = chain.getArg(0) == true
                val selected = ctx.bridge.getSelectedAiCallTimbre(isContact)
                if (TimbreStore.isCustomTimbre(selected)) {
                    ctx.bridge.lookupFallbackOfflineGreeting()
                } else {
                    null
                }
            }
        }.onFailure { ctx.logE("Failed to hook AiCallOfflineTtsManager.h", it) }
    }
}
