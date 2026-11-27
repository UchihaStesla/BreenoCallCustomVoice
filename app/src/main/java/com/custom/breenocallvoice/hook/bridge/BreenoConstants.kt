package com.custom.breenocallvoice.hook.bridge

/**
 * Target class names, MMKV keys, and Binder IPC commands inside com.heytap.speechassist (12.9.6).
 */
object BreenoConstants {
    // Main process cache & auth
    const val CLASS_PREFS_P6 = "com.heytap.speechassist.utils.p6"
    const val CLASS_LOGIN_HELPER = "zt.o"
    const val CLASS_APP_CTX_V2 = "com.heytap.speechassist.utils.v2"
    const val CLASS_TIMBRE_MODEL = "com.heytap.speechassist.homebasic.viewmodel.TimbreModel"
    const val CLASS_TIMBRE_CALLBACK = "bt.b"

    // Cross-process bridge & multi-process MMKV
    const val CLASS_BRIDGE_SERVICE_IMPL = "com.heytap.speechassist.aicall.bridge.local.AiCallBridgeServiceImpl"
    const val CLASS_BRIDGE_EXT = "com.heytap.speechassist.aicall.ext.l"
    const val CLASS_MMKV_UTIL = "d90.b"

    // :aiCall tone config, UI, TTS engine & offline fallback
    const val CLASS_AICALL_TIMBRE_CONFIG_BEAN = "com.heytap.speechassist.aicall.setting.config.AiCallTimbreConfigBean"
    const val CLASS_TONE_CONFIG_ITEM = "com.heytap.speechassist.config.switchtone.ToneConfigManager\$ToneConfigItem"
    const val CLASS_AICALL_FRAGMENT = "com.heytap.speechassist.home.settings.ui.fragment.XiaoBuAiCallFragment"
    const val CLASS_AICALL_SETTING_UTILS = "com.heytap.speechassist.aicall.setting.AiCallSettingUtils"
    const val CLASS_AICALL_CONFIG_MGR = "com.heytap.speechassist.aicall.setting.config.a"
    const val CLASS_TTS_ENGINE = "com.heytap.voiceassistant.sdk.tts.HeytapTtsEngine"
    const val CLASS_TTS_LISTENER = "com.heytap.voiceassistant.sdk.tts.callback.ITtsListener"
    const val CLASS_OFFLINE_DOWNLOAD_HELPER = "com.heytap.speechassist.aicall.engine.tts.offline.AiCallOfflineDownloadHelper"
    const val CLASS_OFFLINE_TTS_MANAGER = "com.heytap.speechassist.aicall.engine.tts.offline.AiCallOfflineTtsManager"

    // In-app update SDK & UI
    const val CLASS_UPGRADE_SDK = "com.heytap.upgrade.UpgradeSDK"
    const val CLASS_CHECK_PARAM = "com.heytap.upgrade.CheckParam"
    const val CLASS_DOWNLOAD_PARAM = "com.heytap.upgrade.DownloadParam"
    const val CLASS_INSTALL_PARAM = "com.heytap.upgrade.InstallParam"
    const val CLASS_UPGRADE_INFO = "com.heytap.upgrade.model.UpgradeInfo"
    const val CLASS_UPGRADE_ACTIVITY = "com.heytap.speechassist.home.settings.ui.UpgradeActivity"
    const val CLASS_UPGRADE_COMMAND_PROCESSOR = "or.a"

    // Keys & commands
    const val PREF_KEY_USER_TIMBRES = "key_cache_user_timbres"
    const val MMKV_KEY_USER_TIMBRES = "xposed_bcv_user_timbres_json"
    const val MMKV_KEY_USER_TOKEN = "xposed_bcv_user_token"
    const val MMKV_KEY_DEVICE_ID = "xposed_bcv_device_id"
    const val MMKV_KEY_STRIP_BIZ_SOURCE = "xposed_bcv_strip_biz_source"

    const val BRIDGE_CMD_GET_TIMBRES = "__xposed_bcv_get_timbres__"
    const val BRIDGE_CMD_REFRESH_TIMBRES = "__xposed_bcv_refresh_timbres__"
    const val BRIDGE_CMD_SYNC_AUTH = "__xposed_bcv_sync_auth__"

    const val FALLBACK_OFFLINE_TIMBRE = "xiaoou_female"

    val TTS_SNAPSHOT_KEYS = arrayOf(
        "role_name", "query", "is_play_sound", "stream_type", "clientContext",
        "speed", "volume", "sample_rate", "tts_format", "language",
        "tts_language", "emotion_tag", "ssml_text", "ttsSessionId",
    )
}
