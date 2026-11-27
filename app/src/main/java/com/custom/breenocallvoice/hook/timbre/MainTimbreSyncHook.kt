package com.custom.breenocallvoice.hook.timbre

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.custom.breenocallvoice.hook.bridge.BreenoConstants
import com.custom.breenocallvoice.hook.core.FeatureHook
import com.custom.breenocallvoice.hook.core.HookContext
import com.custom.breenocallvoice.hook.core.ReflectExt
import org.json.JSONObject
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Main-process (`com.heytap.speechassist`) hook that mirrors Breeno Assistant's custom timbre
 * cache (`key_cache_user_timbres`) and account auth into multi-process MMKV (`d90.b`), and
 * extends `AiCallBridgeServiceImpl.getGlobalModuleConfig` as a zero-overhead IPC channel for `:aiCall`.
 */
class MainTimbreSyncHook : FeatureHook {

    override val name: String = "MainTimbreSyncHook"

    private val mainAppSynced = AtomicBoolean(false)

    @Volatile
    private var lastRefreshMs: Long = 0L

    override fun install(ctx: HookContext) {
        hookTimbreCacheWrites(ctx)
        hookAiCallBridgeService(ctx)
        hookApplicationDelayedSync(ctx)
    }

    private fun hookTimbreCacheWrites(ctx: HookContext) {
        runCatching {
            val p6Class = ReflectExt.findClass(ctx.classLoader, BreenoConstants.CLASS_PREFS_P6)
            val putString = ReflectExt.findMethod(
                p6Class,
                "l",
                Context::class.java,
                String::class.java,
                String::class.java,
            )
            ctx.hookProtective(putString) { chain ->
                val res = chain.proceed()
                val key = chain.getArg(1) as? String
                if (key == BreenoConstants.PREF_KEY_USER_TIMBRES && ctx.prefs.isCustomTimbreEnabled) {
                    val value = chain.getArg(2) as? String
                    if (!value.isNullOrEmpty()) {
                        ctx.bridge.writeMmkvString(BreenoConstants.MMKV_KEY_USER_TIMBRES, value)
                        ctx.logI("Mirrored key_cache_user_timbres to MMKV (${value.length} chars)")
                    }
                    syncMainAuthToMmkv(ctx)
                }
                res
            }
        }.onFailure { ctx.logE("Failed to hook p6.l", it) }
    }

    private fun hookAiCallBridgeService(ctx: HookContext) {
        runCatching {
            val bridgeImplClass = ReflectExt.findClass(
                ctx.classLoader,
                BreenoConstants.CLASS_BRIDGE_SERVICE_IMPL,
            )
            val onCreateMethod = ReflectExt.findMethod(bridgeImplClass, "m")
            ctx.hookProtective(onCreateMethod) { chain ->
                val res = chain.proceed()
                if (ctx.prefs.isCustomTimbreEnabled) {
                    syncMainDataToMmkv(ctx)
                }
                res
            }

            val getGlobalConfigMethod = ReflectExt.findMethod(
                bridgeImplClass,
                "getGlobalModuleConfig",
                String::class.java,
            )
            ctx.hookProtective(getGlobalConfigMethod) { chain ->
                val module = chain.getArg(0) as? String
                when (module) {
                    BreenoConstants.BRIDGE_CMD_GET_TIMBRES -> {
                        if (!ctx.prefs.isCustomTimbreEnabled) return@hookProtective ""
                        syncMainDataToMmkv(ctx).orEmpty()
                    }
                    BreenoConstants.BRIDGE_CMD_REFRESH_TIMBRES -> {
                        if (ctx.prefs.isCustomTimbreEnabled && ctx.prefs.isAutoCloudRefreshEnabled) {
                            triggerRemoteTimbreRefresh(ctx)
                        }
                        "ok"
                    }
                    BreenoConstants.BRIDGE_CMD_SYNC_AUTH -> syncMainAuthAndBuildJson(ctx)
                    else -> chain.proceed()
                }
            }
        }.onFailure { ctx.logE("Failed to hook AiCallBridgeServiceImpl", it) }
    }

    private fun hookApplicationDelayedSync(ctx: HookContext) {
        runCatching {
            val appOnCreate = ReflectExt.findMethod(Application::class.java, "onCreate")
            ctx.hookProtective(appOnCreate) { chain ->
                val res = chain.proceed()
                if (mainAppSynced.compareAndSet(false, true)) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (ctx.prefs.isCustomTimbreEnabled) {
                            runCatching { syncMainDataToMmkv(ctx) }
                        }
                    }, 3_000L)
                }
                res
            }
        }.onFailure { ctx.logE("Failed to hook Application.onCreate", it) }
    }

    private fun syncMainDataToMmkv(ctx: HookContext): String? {
        var timbresJson: String? = null
        runCatching {
            val appCtx = ctx.bridge.getAppContext() ?: return@runCatching
            val p6Class = ReflectExt.findClass(ctx.classLoader, BreenoConstants.CLASS_PREFS_P6)
            val getString = ReflectExt.findMethod(
                p6Class,
                "f",
                Context::class.java,
                String::class.java,
                String::class.java,
            )
            timbresJson = getString.invoke(
                null,
                appCtx,
                BreenoConstants.PREF_KEY_USER_TIMBRES,
                "",
            ) as? String
            if (!timbresJson.isNullOrEmpty()) {
                ctx.bridge.writeMmkvString(BreenoConstants.MMKV_KEY_USER_TIMBRES, timbresJson!!)
            }
        }.onFailure { ctx.logE("syncMainDataToMmkv timbres error", it) }

        syncMainAuthToMmkv(ctx)
        return timbresJson
    }

    private fun syncMainAuthToMmkv(ctx: HookContext) {
        val (token, deviceId) = ctx.bridge.readMainProcessAuth()
        if (token.isNotEmpty()) ctx.bridge.writeMmkvString(BreenoConstants.MMKV_KEY_USER_TOKEN, token)
        if (deviceId.isNotEmpty()) ctx.bridge.writeMmkvString(BreenoConstants.MMKV_KEY_DEVICE_ID, deviceId)
    }

    private fun syncMainAuthAndBuildJson(ctx: HookContext): String {
        val (token, deviceId) = ctx.bridge.readMainProcessAuth()
        val obj = JSONObject()
        if (token.isNotEmpty()) {
            ctx.bridge.writeMmkvString(BreenoConstants.MMKV_KEY_USER_TOKEN, token)
            obj.put("token", token)
        }
        if (deviceId.isNotEmpty()) {
            ctx.bridge.writeMmkvString(BreenoConstants.MMKV_KEY_DEVICE_ID, deviceId)
            obj.put("deviceId", deviceId)
        }
        return obj.toString()
    }

    private fun triggerRemoteTimbreRefresh(ctx: HookContext) {
        val now = System.currentTimeMillis()
        if (now - lastRefreshMs < 10_000L) return
        lastRefreshMs = now

        runCatching {
            val appCtx = ctx.bridge.getAppContext() ?: return
            if (!ctx.bridge.isMainProcessLoggedIn()) {
                ctx.logD("User not logged in; skipping remote custom timbre refresh")
                return
            }
            val timbreModelCls = ReflectExt.findClass(ctx.classLoader, BreenoConstants.CLASS_TIMBRE_MODEL)
            val companion = ReflectExt.getStaticField(timbreModelCls, "h") ?: return
            val timbreModel = ReflectExt.findMethod(companion.javaClass, "a").invoke(companion) ?: return
            val callbackInterface = ReflectExt.findClass(ctx.classLoader, BreenoConstants.CLASS_TIMBRE_CALLBACK)

            val callbackProxy = Proxy.newProxyInstance(
                ctx.classLoader,
                arrayOf(callbackInterface),
            ) { _, method, args ->
                when (method.name) {
                    "onSuccess" -> {
                        val resp = args?.firstOrNull() as? String
                        if (!resp.isNullOrEmpty()) {
                            handleRemoteTimbreResponse(ctx, appCtx, resp)
                        }
                    }
                    "onFail" -> {
                        ctx.logW("Remote queryUserTimbre failed: ${args?.firstOrNull() ?: ""}")
                    }
                }
                null
            }
            ReflectExt.findMethod(timbreModelCls, "i0", callbackInterface)
                .invoke(timbreModel, callbackProxy)
            ctx.logI("Triggered remote TimbreModel.queryUserTimbre()")
        }.onFailure { ctx.logE("triggerRemoteTimbreRefresh error", it) }
    }

    private fun handleRemoteTimbreResponse(ctx: HookContext, appCtx: Context, resp: String) {
        runCatching {
            var dataJson = resp
            val root = JSONObject(resp)
            if (root.has("data")) {
                val extracted = root.optString("data")
                if (extracted.isNotEmpty() && extracted != "null") {
                    dataJson = extracted
                }
            }
            if (dataJson.contains("timbreList")) {
                val p6Class = ReflectExt.findClass(ctx.classLoader, BreenoConstants.CLASS_PREFS_P6)
                ReflectExt.findMethod(
                    p6Class,
                    "l",
                    Context::class.java,
                    String::class.java,
                    String::class.java,
                ).invoke(null, appCtx, BreenoConstants.PREF_KEY_USER_TIMBRES, dataJson)
                ctx.bridge.writeMmkvString(BreenoConstants.MMKV_KEY_USER_TIMBRES, dataJson)
                ctx.logI("Refreshed custom timbres from cloud (${dataJson.length} chars)")
            }
        }.onFailure { ctx.logE("handleRemoteTimbreResponse error", it) }
    }
}
