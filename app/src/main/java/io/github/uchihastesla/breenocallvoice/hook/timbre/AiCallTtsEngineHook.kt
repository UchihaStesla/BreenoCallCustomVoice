package io.github.uchihastesla.breenocallvoice.hook.timbre

import io.github.uchihastesla.breenocallvoice.config.BizSourceMode
import io.github.uchihastesla.breenocallvoice.config.ModuleConfig
import io.github.uchihastesla.breenocallvoice.hook.bridge.BreenoConstants
import io.github.uchihastesla.breenocallvoice.hook.bridge.TimbreStore
import io.github.uchihastesla.breenocallvoice.hook.core.FeatureHook
import io.github.uchihastesla.breenocallvoice.hook.core.HookContext
import io.github.uchihastesla.breenocallvoice.hook.core.ReflectExt
import io.github.libxposed.api.XposedInterface
import org.json.JSONObject
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * `:aiCall` process hook for `HeytapTtsEngine.startSpeaking(String, ITtsListener)`.
 * Injects `ttsType=USER_TIMBRE`, `userToken`, `heytapAccountDeviceId`, and `clientContext`
 * for custom timbres, clears stale `ttsType` for built-in timbres, and handles `biz_source`
 * strategy (auto fallback / force strip / keep original).
 */
class AiCallTtsEngineHook : FeatureHook {

    override val name: String = "AiCallTtsEngineHook"

    override fun install(ctx: HookContext) {
        val ttsEngineCls = ReflectExt.findClass(ctx.classLoader, BreenoConstants.CLASS_TTS_ENGINE)
        val ttsListenerCls = ReflectExt.findClass(ctx.classLoader, BreenoConstants.CLASS_TTS_LISTENER)
        val startSpeakingMethod = ReflectExt.findMethod(
            ttsEngineCls,
            "startSpeaking",
            String::class.java,
            ttsListenerCls,
        )
        val getSdkParamsMethod = ReflectExt.findMethod(ttsEngineCls, "getSdkParams")
        val setParameterMethod = ReflectExt.findMethod(
            ttsEngineCls,
            "setParameter",
            String::class.java,
            String::class.java,
        )
        val originStartSpeaking = ctx.originInvoker(startSpeakingMethod)

        ctx.hookProtective(startSpeakingMethod) { chain ->
            val engine = chain.thisObject ?: return@hookProtective chain.proceed()
            val text = chain.getArg(0) as? String
            val origListener = chain.getArg(1)

            val configuredSpeed = ctx.prefs.ttsSpeed
            val speedOverride = if (configuredSpeed != ModuleConfig.DEFAULT_TTS_SPEED) {
                configuredSpeed.toString()
            } else {
                null
            }

            // Apply configured speed for ALL cloud TTS utterances (both built-in and custom timbres),
            // even when custom timbre injection is disabled.
            if (speedOverride != null) {
                setParameterMethod.invoke(engine, "speed", speedOverride)
            }

            if (!ctx.prefs.isCustomTimbreEnabled) {
                if (speedOverride != null) {
                    ctx.logD("startSpeaking speed=$speedOverride (custom timbre disabled)")
                }
                return@hookProtective chain.proceed()
            }

            val sdkParams = getSdkParamsMethod.invoke(engine)
            val roleName = getSdkParam(sdkParams, "role_name")

            if (!TimbreStore.isCustomTimbre(roleName)) {
                // HeytapTtsEngine.clearDynamicParams() does not clear ttsType; reset explicitly
                setParameterMethod.invoke(engine, "ttsType", "")
                if (speedOverride != null) {
                    ctx.logI("startSpeaking built-in timbre=$roleName speed=$speedOverride")
                }
                return@hookProtective chain.proceed()
            }

            val (userToken, deviceId) = ctx.bridge.fetchAuth()
            applyCustomTtsParams(engine, sdkParams, setParameterMethod, userToken, deviceId)
            val speedLogSuffix = speedOverride?.let { " speed=$it" }.orEmpty()

            when (ctx.prefs.bizSourceMode) {
                BizSourceMode.KEEP_ORIGINAL -> {
                    ctx.logI("startSpeaking custom timbre=$roleName$speedLogSuffix (keep biz_source)")
                    chain.proceed()
                }
                BizSourceMode.FORCE_STRIP -> {
                    setParameterMethod.invoke(engine, "biz_source", "")
                    ctx.logI("startSpeaking custom timbre=$roleName$speedLogSuffix (force stripped biz_source)")
                    chain.proceed()
                }
                BizSourceMode.AUTO -> {
                    val learnedStrip = ctx.bridge.readMmkvBoolean(
                        BreenoConstants.MMKV_KEY_STRIP_BIZ_SOURCE,
                        false,
                    )
                    if (learnedStrip) {
                        setParameterMethod.invoke(engine, "biz_source", "")
                        ctx.logI("startSpeaking custom timbre=$roleName$speedLogSuffix (biz_source stripped)")
                        return@hookProtective chain.proceed()
                    }
                    val snapshot = snapshotSdkParams(sdkParams)
                    val wrappedListener = wrapListenerForFallback(
                        ctx = ctx,
                        ttsListenerCls = ttsListenerCls,
                        origListener = origListener,
                        engine = engine,
                        text = text,
                        roleName = roleName!!,
                        userToken = userToken,
                        deviceId = deviceId,
                        speedOverride = speedOverride,
                        snapshot = snapshot,
                        setParameterMethod = setParameterMethod,
                        originStartSpeaking = originStartSpeaking,
                    )
                    ctx.logI("startSpeaking custom timbre=$roleName$speedLogSuffix (auto biz_source fallback)")
                    chain.proceed(arrayOf(text, wrappedListener))
                }
            }
        }
    }

    private fun applyCustomTtsParams(
        engine: Any,
        sdkParams: Any?,
        setParameterMethod: Method,
        userToken: String,
        deviceId: String,
    ) {
        setParameterMethod.invoke(engine, "ttsType", "USER_TIMBRE")
        if (userToken.isNotEmpty()) setParameterMethod.invoke(engine, "userToken", userToken)
        if (deviceId.isNotEmpty()) setParameterMethod.invoke(engine, "heytapAccountDeviceId", deviceId)

        val existingCc = getSdkParam(sdkParams, "clientContext")
        val ccObj = runCatching {
            if (!existingCc.isNullOrEmpty()) JSONObject(existingCc) else JSONObject()
        }.getOrElse { JSONObject() }

        if (userToken.isNotEmpty()) ccObj.put("userToken", userToken)
        if (deviceId.isNotEmpty()) ccObj.put("heytapAccountDeviceId", deviceId)
        setParameterMethod.invoke(engine, "clientContext", ccObj.toString())
    }

    private fun wrapListenerForFallback(
        ctx: HookContext,
        ttsListenerCls: Class<*>,
        origListener: Any?,
        engine: Any,
        text: String?,
        roleName: String,
        userToken: String,
        deviceId: String,
        speedOverride: String?,
        snapshot: Map<String, String>,
        setParameterMethod: Method,
        originStartSpeaking: XposedInterface.Invoker<*, Method>,
    ): Any {
        val receivedAudio = AtomicBoolean(false)
        val retried = AtomicBoolean(false)

        return Proxy.newProxyInstance(
            ctx.classLoader,
            arrayOf(ttsListenerCls),
        ) { proxy, method, args ->
            when (method.name) {
                "toString" -> return@newProxyInstance "BcvTtsListenerProxy($origListener)"
                "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
                "equals" -> return@newProxyInstance (args != null && args.size == 1 && proxy === args[0])
                "onSpeakBegin" -> receivedAudio.set(true)
                "onEvent" -> {
                    val eventType = args?.firstOrNull() as? Int
                    if (eventType == 10101 || eventType == 10102) {
                        receivedAudio.set(true)
                    }
                }
                "onCompleted" -> {
                    val errorObj = args?.firstOrNull()
                    if (errorObj != null && !receivedAudio.get() && retried.compareAndSet(false, true)) {
                        ctx.logW("Custom timbre TTS failed with biz_source=AI.Call ($errorObj); retrying once without biz_source")
                        ctx.bridge.writeMmkvBoolean(BreenoConstants.MMKV_KEY_STRIP_BIZ_SOURCE, true)
                        Thread({
                            runCatching {
                                for ((k, v) in snapshot) {
                                    if (v.isNotEmpty()) setParameterMethod.invoke(engine, k, v)
                                }
                                setParameterMethod.invoke(engine, "role_name", roleName)
                                setParameterMethod.invoke(engine, "ttsType", "USER_TIMBRE")
                                if (userToken.isNotEmpty()) setParameterMethod.invoke(engine, "userToken", userToken)
                                if (deviceId.isNotEmpty()) setParameterMethod.invoke(engine, "heytapAccountDeviceId", deviceId)
                                if (speedOverride != null) setParameterMethod.invoke(engine, "speed", speedOverride)
                                setParameterMethod.invoke(engine, "biz_source", "")
                                originStartSpeaking.invoke(engine, text, origListener)
                            }.onFailure { retryErr ->
                                ctx.logE("Fallback retry startSpeaking failed", retryErr)
                                if (origListener != null) {
                                    runCatching { method.invoke(origListener, *(args ?: emptyArray())) }
                                }
                            }
                        }, "bcv-tts-retry").apply {
                            isDaemon = true
                            start()
                        }
                        return@newProxyInstance null
                    }
                }
            }
            if (origListener != null) {
                method.invoke(origListener, *(args ?: emptyArray()))
            } else {
                null
            }
        }
    }

    private fun snapshotSdkParams(sdkParams: Any?): Map<String, String> {
        if (sdkParams == null) return emptyMap()
        val map = HashMap<String, String>()
        for (key in BreenoConstants.TTS_SNAPSHOT_KEYS) {
            val value = getSdkParam(sdkParams, key)
            if (!value.isNullOrEmpty()) map[key] = value
        }
        return map
    }

    private fun getSdkParam(sdkParams: Any?, key: String): String? {
        if (sdkParams == null) return null
        return runCatching {
            ReflectExt.findMethod(sdkParams.javaClass, "a", String::class.java)
                .invoke(sdkParams, key) as? String
        }.getOrNull()
    }
}
