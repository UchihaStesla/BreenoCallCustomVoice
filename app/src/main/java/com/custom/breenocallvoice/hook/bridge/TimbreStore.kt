package com.custom.breenocallvoice.hook.bridge

import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

data class CustomTimbre(
    val id: String,
    val name: String,
    val avatar: String,
    val bigPic: String,
    val firBgColor: String,
    val secBgColor: String,
)

/**
 * Thread-safe custom timbre registry and JSON parser.
 */
object TimbreStore {
    private val knownTimbreIds: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap())

    fun hasKnownTimbres(): Boolean = knownTimbreIds.isNotEmpty()

    fun register(timbreId: String) {
        if (timbreId.isNotEmpty()) {
            knownTimbreIds.add(timbreId)
        }
    }

    fun isCustomTimbre(roleName: String?): Boolean {
        if (roleName.isNullOrEmpty()) return false
        return roleName.startsWith("breeno") || knownTimbreIds.contains(roleName)
    }

    fun parseReadyTimbres(json: String): List<CustomTimbre> {
        val arr = extractTimbreArray(json) ?: return emptyList()
        val result = ArrayList<CustomTimbre>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val timbreId = obj.optString("timbreId", "")
            val status = obj.optInt("status", -1)
            if (timbreId.isEmpty() || status != 3) continue

            register(timbreId)
            result.add(
                CustomTimbre(
                    id = timbreId,
                    name = obj.optString("timbreName", "").ifEmpty { "自定义音色" },
                    avatar = obj.optString("avatar", ""),
                    bigPic = obj.optString("bigPic", ""),
                    firBgColor = obj.optString("firBgColor", "").ifEmpty { "#EEF3FF" },
                    secBgColor = obj.optString("secBgColor", "").ifEmpty { "#D8E5FF" },
                ),
            )
        }
        return result
    }

    private fun extractTimbreArray(json: String): JSONArray? = runCatching {
        val trimmed = json.trim()
        if (trimmed.startsWith("[")) return JSONArray(trimmed)
        val root = JSONObject(trimmed)
        root.optJSONArray("timbreList")?.let { return it }
        root.optJSONObject("data")?.optJSONArray("timbreList")?.let { return it }
        val dataStr = root.optString("data", "").trim()
        if (dataStr.startsWith("{")) {
            return JSONObject(dataStr).optJSONArray("timbreList")
        }
        null
    }.getOrNull()
}
