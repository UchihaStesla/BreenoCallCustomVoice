package io.github.uchihastesla.breenocallvoice.hook.bridge

import android.content.Context
import io.github.uchihastesla.breenocallvoice.hook.core.ReflectExt

/**
 * Encapsulates cross-process data access inside com.heytap.speechassist:
 * - Multi-process MMKV (`d90.b`)
 * - Existing Binder IPC (`IAiCallBridge` via `com.heytap.speechassist.aicall.ext.l.f()`)
 * - Application Context & selected AiCall timbre lookup
 */
class BreenoBridgeClient(private val cl: ClassLoader) {

    fun getAppContext(): Context? = runCatching {
        val v2Cls = ReflectExt.findClass(cl, BreenoConstants.CLASS_APP_CTX_V2)
        ReflectExt.findMethod(v2Cls, "b").invoke(null) as? Context
    }.getOrNull()

    fun getAiCallBridge(): Any? = runCatching {
        val extL = ReflectExt.findClass(cl, BreenoConstants.CLASS_BRIDGE_EXT)
        ReflectExt.findMethod(extL, "f").invoke(null)
    }.getOrNull()

    fun sendBridgeCommand(command: String): String? = runCatching {
        val bridge = getAiCallBridge() ?: return null
        ReflectExt.findMethod(bridge.javaClass, "getGlobalModuleConfig", String::class.java)
            .invoke(bridge, command) as? String
    }.getOrNull()

    fun fetchCustomTimbresJson(): String {
        val fromBridge = sendBridgeCommand(BreenoConstants.BRIDGE_CMD_GET_TIMBRES)
        if (!fromBridge.isNullOrEmpty()) {
            writeMmkvString(BreenoConstants.MMKV_KEY_USER_TIMBRES, fromBridge)
            return fromBridge
        }
        return readMmkvString(BreenoConstants.MMKV_KEY_USER_TIMBRES, "")
    }

    fun fetchAuth(): Pair<String, String> {
        var token = ""
        var deviceId = ""
        runCatching {
            val bridge = getAiCallBridge() ?: return@runCatching
            token = (ReflectExt.findMethod(bridge.javaClass, "getToken").invoke(bridge) as? String).orEmpty()
            deviceId = (ReflectExt.findMethod(bridge.javaClass, "getDeviceId").invoke(bridge) as? String).orEmpty()
            if (token.isNotEmpty()) writeMmkvString(BreenoConstants.MMKV_KEY_USER_TOKEN, token)
            if (deviceId.isNotEmpty()) writeMmkvString(BreenoConstants.MMKV_KEY_DEVICE_ID, deviceId)
        }
        if (token.isEmpty()) token = readMmkvString(BreenoConstants.MMKV_KEY_USER_TOKEN, "")
        if (deviceId.isEmpty()) deviceId = readMmkvString(BreenoConstants.MMKV_KEY_DEVICE_ID, "")
        return token to deviceId
    }

    fun readMainProcessAuth(): Pair<String, String> {
        val ctx = getAppContext() ?: return "" to ""
        return runCatching {
            val loginHelper = ReflectExt.findClass(cl, BreenoConstants.CLASS_LOGIN_HELPER)
            val token = (ReflectExt.findMethod(loginHelper, "h", Context::class.java).invoke(null, ctx) as? String).orEmpty()
            val deviceId = (ReflectExt.findMethod(loginHelper, "c", Context::class.java).invoke(null, ctx) as? String).orEmpty()
            token to deviceId
        }.getOrDefault("" to "")
    }

    fun isMainProcessLoggedIn(): Boolean {
        val ctx = getAppContext() ?: return false
        return runCatching {
            val loginHelper = ReflectExt.findClass(cl, BreenoConstants.CLASS_LOGIN_HELPER)
            ReflectExt.findMethod(loginHelper, "k", Context::class.java).invoke(null, ctx) == true
        }.getOrDefault(false)
    }

    fun getSelectedAiCallTimbre(isContact: Boolean): String? = runCatching {
        val utilsCls = ReflectExt.findClass(cl, BreenoConstants.CLASS_AICALL_SETTING_UTILS)
        val instance = ReflectExt.getStaticField(utilsCls, "INSTANCE") ?: return null
        ReflectExt.findMethod(utilsCls, "s", Boolean::class.javaPrimitiveType!!)
            .invoke(instance, isContact) as? String
    }.getOrNull()

    fun lookupFallbackOfflineGreeting(): String? = runCatching {
        val cfgMgrCls = ReflectExt.findClass(cl, BreenoConstants.CLASS_AICALL_CONFIG_MGR)
        val companion = ReflectExt.getStaticField(cfgMgrCls, "c") ?: return null
        val mgr = ReflectExt.findMethod(companion.javaClass, "a").invoke(companion) ?: return null
        val ttsCfg = ReflectExt.findMethod(mgr.javaClass, "e").invoke(mgr) ?: return null
        val greetingsObj = ReflectExt.findMethod(ttsCfg.javaClass, "getGreetingTtsConfig").invoke(ttsCfg)
        if (greetingsObj !is List<*>) return null

        var firstNonEmpty: String? = null
        for (item in greetingsObj) {
            if (item == null) continue
            val timbre = ReflectExt.findMethod(item.javaClass, "getTimbre").invoke(item) as? String
            val greeting = ReflectExt.findMethod(item.javaClass, "getGreetings").invoke(item) as? String
            if (BreenoConstants.FALLBACK_OFFLINE_TIMBRE == timbre && !greeting.isNullOrEmpty()) {
                return greeting
            }
            if (firstNonEmpty == null && !greeting.isNullOrEmpty()) {
                firstNonEmpty = greeting
            }
        }
        firstNonEmpty
    }.getOrNull()

    fun readMmkvString(key: String, defVal: String): String = runCatching {
        val mmkvUtil = ReflectExt.findClass(cl, BreenoConstants.CLASS_MMKV_UTIL)
        val instance = ReflectExt.getStaticField(mmkvUtil, "INSTANCE") ?: return defVal
        (ReflectExt.findMethod(mmkvUtil, "g", String::class.java, String::class.java)
            .invoke(instance, key, defVal) as? String) ?: defVal
    }.getOrDefault(defVal)

    fun writeMmkvString(key: String, value: String) {
        runCatching {
            val mmkvUtil = ReflectExt.findClass(cl, BreenoConstants.CLASS_MMKV_UTIL)
            val instance = ReflectExt.getStaticField(mmkvUtil, "INSTANCE") ?: return
            ReflectExt.findMethod(mmkvUtil, "j", String::class.java, String::class.java)
                .invoke(instance, key, value)
        }
    }

    fun readMmkvBoolean(key: String, defVal: Boolean): Boolean = runCatching {
        val mmkvUtil = ReflectExt.findClass(cl, BreenoConstants.CLASS_MMKV_UTIL)
        val instance = ReflectExt.getStaticField(mmkvUtil, "INSTANCE") ?: return defVal
        (ReflectExt.findMethod(mmkvUtil, "a", String::class.java, Boolean::class.javaPrimitiveType!!)
            .invoke(instance, key, defVal) as? Boolean) ?: defVal
    }.getOrDefault(defVal)

    fun writeMmkvBoolean(key: String, value: Boolean) {
        runCatching {
            val mmkvUtil = ReflectExt.findClass(cl, BreenoConstants.CLASS_MMKV_UTIL)
            val instance = ReflectExt.getStaticField(mmkvUtil, "INSTANCE") ?: return
            ReflectExt.findMethod(mmkvUtil, "h", String::class.java, Boolean::class.javaPrimitiveType!!)
                .invoke(instance, key, value)
        }
    }
}
