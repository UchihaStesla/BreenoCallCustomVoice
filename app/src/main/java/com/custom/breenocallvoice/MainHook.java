package com.custom.breenocallvoice;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * LSPosed (libxposed API 102) module enabling Breeno Assistant custom voice timbres in Breeno Call.
 * Target: com.heytap.speechassist (verified on v12.9.6 / versionCode 120906).
 */
public final class MainHook extends XposedModule {

    private static final String TAG = "BreenoCallCustomVoice";

    private static final String TARGET_PACKAGE = "com.heytap.speechassist";
    private static final String MAIN_PROCESS = "com.heytap.speechassist";
    private static final String AICALL_PROCESS = "com.heytap.speechassist:aiCall";

    private static final String PREF_KEY_USER_TIMBRES = "key_cache_user_timbres";

    private static final String MMKV_KEY_USER_TIMBRES = "xposed_bcv_user_timbres_json";
    private static final String MMKV_KEY_USER_TOKEN = "xposed_bcv_user_token";
    private static final String MMKV_KEY_DEVICE_ID = "xposed_bcv_device_id";
    private static final String MMKV_KEY_STRIP_BIZ_SOURCE = "xposed_bcv_strip_biz_source";

    private static final String BRIDGE_CMD_GET_TIMBRES = "__xposed_bcv_get_timbres__";
    private static final String BRIDGE_CMD_REFRESH_TIMBRES = "__xposed_bcv_refresh_timbres__";
    private static final String BRIDGE_CMD_SYNC_AUTH = "__xposed_bcv_sync_auth__";

    private static final String FALLBACK_OFFLINE_TIMBRE = "xiaoou_female";

    private static final String[] TTS_SNAPSHOT_KEYS = {
            "role_name", "query", "is_play_sound", "stream_type", "clientContext",
            "speed", "volume", "sample_rate", "tts_format", "language",
            "tts_language", "emotion_tag", "ssml_text", "ttsSessionId"
    };

    private final Set<String> knownCustomTimbres =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final AtomicBoolean mainAppSynced = new AtomicBoolean(false);
    private final AtomicBoolean hooksInstalled = new AtomicBoolean(false);

    private volatile String currentProcessName = "";
    private volatile long lastMainRefreshMs = 0L;
    private volatile long lastUiRefreshMs = 0L;

    public MainHook() {
    }

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        currentProcessName = param.getProcessName();
        boolean isMain = MAIN_PROCESS.equals(currentProcessName);
        boolean isAiCall = AICALL_PROCESS.equals(currentProcessName)
                || currentProcessName.endsWith(":aiCall");
        if (!isMain && !isAiCall) {
            if (getApiVersion() >= API_102) {
                detach();
            }
            return;
        }
        logI("Loaded in process=" + currentProcessName
                + ", api=" + getApiVersion()
                + ", framework=" + getFrameworkName() + " " + getFrameworkVersion());
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        if (!hooksInstalled.compareAndSet(false, true)) {
            return;
        }
        ClassLoader cl = param.getClassLoader();
        if (MAIN_PROCESS.equals(currentProcessName)) {
            hookMainProcess(cl);
        } else if (AICALL_PROCESS.equals(currentProcessName)
                || currentProcessName.endsWith(":aiCall")) {
            hookAiCallProcess(cl);
        }
    }

    // =========================================================================
    // Main process hooks (com.heytap.speechassist)
    // =========================================================================

    private void hookMainProcess(ClassLoader cl) {
        logI("Installing main process hooks");

        // 1. Mirror user custom timbres whenever Breeno Assistant updates key_cache_user_timbres
        try {
            Class<?> p6Class = findClass(cl, "com.heytap.speechassist.utils.p6");
            Method putStringMethod = findMethod(p6Class, "l", Context.class, String.class, String.class);
            hook(putStringMethod)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object res = chain.proceed();
                        String key = (String) chain.getArg(1);
                        if (PREF_KEY_USER_TIMBRES.equals(key)) {
                            String value = (String) chain.getArg(2);
                            if (!TextUtils.isEmpty(value)) {
                                writeMmkvString(cl, MMKV_KEY_USER_TIMBRES, value);
                                logI("Mirrored key_cache_user_timbres to multi-process MMKV (" + value.length() + " chars)");
                            }
                            syncMainAuthToMmkv(cl);
                        }
                        return res;
                    });
        } catch (Throwable t) {
            logE("Failed to hook p6.l", t);
        }

        // 2. Proactively sync cache + auth when AiCallBridgeServiceImpl initializes
        try {
            Class<?> bridgeImplClass = findClass(cl, "com.heytap.speechassist.aicall.bridge.local.AiCallBridgeServiceImpl");
            Method onCreateMethod = findMethod(bridgeImplClass, "m");
            hook(onCreateMethod)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object res = chain.proceed();
                        syncMainProcessDataToMmkv(cl);
                        return res;
                    });

            // 3. Reuse AiCallBridgeServiceImpl.getGlobalModuleConfig(String) as IPC channel for :aiCall
            Method getGlobalConfigMethod = findMethod(bridgeImplClass, "getGlobalModuleConfig", String.class);
            hook(getGlobalConfigMethod)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        String module = (String) chain.getArg(0);
                        if (BRIDGE_CMD_GET_TIMBRES.equals(module)) {
                            String json = syncMainProcessDataToMmkv(cl);
                            return json != null ? json : "";
                        }
                        if (BRIDGE_CMD_REFRESH_TIMBRES.equals(module)) {
                            triggerMainProcessTimbreRefresh(cl);
                            return "ok";
                        }
                        if (BRIDGE_CMD_SYNC_AUTH.equals(module)) {
                            return syncMainAuthAndBuildJson(cl);
                        }
                        return chain.proceed();
                    });
        } catch (Throwable t) {
            logE("Failed to hook AiCallBridgeServiceImpl", t);
        }

        // 4. One-shot delayed mirror after main Application.onCreate so existing caches are synced early
        try {
            Method appOnCreate = findMethod(Application.class, "onCreate");
            hook(appOnCreate)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object res = chain.proceed();
                        if (mainAppSynced.compareAndSet(false, true)) {
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                try {
                                    syncMainProcessDataToMmkv(cl);
                                } catch (Throwable ignored) {
                                }
                            }, 3000L);
                        }
                        return res;
                    });
        } catch (Throwable t) {
            logE("Failed to hook Application.onCreate", t);
        }
    }

    @Nullable
    private String syncMainProcessDataToMmkv(ClassLoader cl) {
        String timbresJson = null;
        try {
            Context ctx = getAppContext(cl);
            if (ctx != null) {
                Class<?> p6Class = findClass(cl, "com.heytap.speechassist.utils.p6");
                Method getStringMethod = findMethod(p6Class, "f", Context.class, String.class, String.class);
                timbresJson = (String) getStringMethod.invoke(null, ctx, PREF_KEY_USER_TIMBRES, "");
                if (!TextUtils.isEmpty(timbresJson)) {
                    writeMmkvString(cl, MMKV_KEY_USER_TIMBRES, timbresJson);
                }
            }
        } catch (Throwable t) {
            logE("syncMainProcessDataToMmkv timbres error", t);
        }
        syncMainAuthToMmkv(cl);
        return timbresJson;
    }

    private void syncMainAuthToMmkv(ClassLoader cl) {
        try {
            Context ctx = getAppContext(cl);
            if (ctx == null) {
                return;
            }
            Class<?> loginHelper = findClass(cl, "zt.o");
            String token = (String) findMethod(loginHelper, "h", Context.class).invoke(null, ctx);
            String deviceId = (String) findMethod(loginHelper, "c", Context.class).invoke(null, ctx);
            if (!TextUtils.isEmpty(token)) {
                writeMmkvString(cl, MMKV_KEY_USER_TOKEN, token);
            }
            if (!TextUtils.isEmpty(deviceId)) {
                writeMmkvString(cl, MMKV_KEY_DEVICE_ID, deviceId);
            }
        } catch (Throwable t) {
            logE("syncMainAuthToMmkv error", t);
        }
    }

    private String syncMainAuthAndBuildJson(ClassLoader cl) {
        JSONObject obj = new JSONObject();
        try {
            Context ctx = getAppContext(cl);
            if (ctx != null) {
                Class<?> loginHelper = findClass(cl, "zt.o");
                String token = (String) findMethod(loginHelper, "h", Context.class).invoke(null, ctx);
                String deviceId = (String) findMethod(loginHelper, "c", Context.class).invoke(null, ctx);
                if (!TextUtils.isEmpty(token)) {
                    writeMmkvString(cl, MMKV_KEY_USER_TOKEN, token);
                    obj.put("token", token);
                }
                if (!TextUtils.isEmpty(deviceId)) {
                    writeMmkvString(cl, MMKV_KEY_DEVICE_ID, deviceId);
                    obj.put("deviceId", deviceId);
                }
            }
        } catch (Throwable t) {
            logE("syncMainAuthAndBuildJson error", t);
        }
        return obj.toString();
    }

    private void triggerMainProcessTimbreRefresh(ClassLoader cl) {
        long now = System.currentTimeMillis();
        if (now - lastMainRefreshMs < 10_000L) {
            return;
        }
        lastMainRefreshMs = now;
        try {
            Context ctx = getAppContext(cl);
            if (ctx == null) {
                return;
            }
            Class<?> loginHelper = findClass(cl, "zt.o");
            Boolean isLogin = (Boolean) findMethod(loginHelper, "k", Context.class).invoke(null, ctx);
            if (!Boolean.TRUE.equals(isLogin)) {
                logI("User not logged in; skipping remote custom timbre refresh");
                return;
            }
            Class<?> timbreModelCls = findClass(cl, "com.heytap.speechassist.homebasic.viewmodel.TimbreModel");
            Object companion = getStaticField(timbreModelCls, "h");
            Object timbreModel = findMethod(companion.getClass(), "a").invoke(companion);
            Class<?> callbackInterface = findClass(cl, "bt.b");

            Object callbackProxy = Proxy.newProxyInstance(
                    cl,
                    new Class<?>[]{callbackInterface},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("onSuccess".equals(name) && args != null && args.length == 1 && args[0] instanceof String) {
                            String resp = (String) args[0];
                            handleRemoteTimbreResponse(cl, ctx, resp);
                        } else if ("onFail".equals(name)) {
                            logW("Remote queryUserTimbre failed: " + (args != null && args.length > 0 ? args[0] : ""));
                        }
                        return null;
                    }
            );
            findMethod(timbreModelCls, "i0", callbackInterface).invoke(timbreModel, callbackProxy);
            logI("Triggered remote TimbreModel.queryUserTimbre()");
        } catch (Throwable t) {
            logE("triggerMainProcessTimbreRefresh error", t);
        }
    }

    private void handleRemoteTimbreResponse(ClassLoader cl, Context ctx, String resp) {
        try {
            if (TextUtils.isEmpty(resp)) {
                return;
            }
            String dataJson = resp;
            JSONObject root = new JSONObject(resp);
            if (root.has("data")) {
                String extracted = root.optString("data");
                if (!TextUtils.isEmpty(extracted) && !"null".equals(extracted)) {
                    dataJson = extracted;
                }
            }
            if (dataJson.contains("timbreList")) {
                Class<?> p6Class = findClass(cl, "com.heytap.speechassist.utils.p6");
                findMethod(p6Class, "l", Context.class, String.class, String.class)
                        .invoke(null, ctx, PREF_KEY_USER_TIMBRES, dataJson);
                writeMmkvString(cl, MMKV_KEY_USER_TIMBRES, dataJson);
                logI("Refreshed user custom timbres from cloud (" + dataJson.length() + " chars)");
            }
        } catch (Throwable t) {
            logE("handleRemoteTimbreResponse error", t);
        }
    }

    // =========================================================================
    // :aiCall process hooks (com.heytap.speechassist:aiCall)
    // =========================================================================

    private void hookAiCallProcess(ClassLoader cl) {
        logI("Installing :aiCall process hooks");

        // 1. Inject custom timbres into AiCallTimbreConfigBean.getToneList()
        try {
            Class<?> configBeanCls = findClass(cl, "com.heytap.speechassist.aicall.setting.config.AiCallTimbreConfigBean");
            Method getToneListMethod = findMethod(configBeanCls, "getToneList");
            hook(getToneListMethod)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object original = chain.proceed();
                        return mergeCustomTimbres(cl, original);
                    });
        } catch (Throwable t) {
            logE("Failed to hook AiCallTimbreConfigBean.getToneList", t);
        }

        // 2. Refresh XiaoBuAiCallFragment timbre list onResume and before opening picker dialog
        try {
            Class<?> fragmentCls = findClass(cl, "com.heytap.speechassist.home.settings.ui.fragment.XiaoBuAiCallFragment");
            Method onResumeMethod = findMethod(fragmentCls, "onResume");
            Method loadTimbresMethod = findMethod(fragmentCls, "b3");
            Method updateAssignmentMethod = findMethod(fragmentCls, "v3", boolean.class);
            Method openDialogMethod = findMethod(fragmentCls, "X2", String.class, boolean.class);
            Method isAddedMethod = findMethod(fragmentCls, "isAdded");
            Field assignmentInitField = findField(fragmentCls, "M");

            hook(onResumeMethod)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object fragment = chain.getThisObject();
                        try {
                            loadTimbresMethod.invoke(fragment);
                            assignmentInitField.setBoolean(fragment, false);
                            maybeRequestAsyncRefresh(cl, fragment, loadTimbresMethod, updateAssignmentMethod, isAddedMethod, assignmentInitField);
                        } catch (Throwable t) {
                            logE("XiaoBuAiCallFragment.onResume pre-hook error", t);
                        }
                        return chain.proceed();
                    });

            hook(openDialogMethod)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            loadTimbresMethod.invoke(chain.getThisObject());
                        } catch (Throwable t) {
                            logE("XiaoBuAiCallFragment.X2 pre-hook error", t);
                        }
                        return chain.proceed();
                    });
        } catch (Throwable t) {
            logE("Failed to hook XiaoBuAiCallFragment", t);
        }

        // 3. Inject USER_TIMBRE + userToken + heytapAccountDeviceId + clientContext into HeytapTtsEngine.startSpeaking
        try {
            Class<?> ttsEngineCls = findClass(cl, "com.heytap.voiceassistant.sdk.tts.HeytapTtsEngine");
            Class<?> ttsListenerCls = findClass(cl, "com.heytap.voiceassistant.sdk.tts.callback.ITtsListener");
            Method startSpeakingMethod = findMethod(ttsEngineCls, "startSpeaking", String.class, ttsListenerCls);
            Method getSdkParamsMethod = findMethod(ttsEngineCls, "getSdkParams");
            Method setParameterMethod = findMethod(ttsEngineCls, "setParameter", String.class, String.class);
            XposedInterface.Invoker<?, Method> originStartSpeaking =
                    getInvoker(startSpeakingMethod).setType(Invoker.Type.ORIGIN);

            hook(startSpeakingMethod)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object engine = chain.getThisObject();
                        String text = (String) chain.getArg(0);
                        Object origListener = chain.getArg(1);

                        Object sdkParams = getSdkParamsMethod.invoke(engine);
                        String roleName = getSdkParam(sdkParams, "role_name");

                        if (!isCustomTimbre(roleName)) {
                            // HeytapTtsEngine.clearDynamicParams() does NOT clear ttsType; clear it explicitly!
                            setParameterMethod.invoke(engine, "ttsType", "");
                            return chain.proceed();
                        }

                        String[] auth = fetchAuth(cl);
                        String userToken = auth[0];
                        String deviceId = auth[1];

                        applyCustomTtsParams(engine, sdkParams, setParameterMethod, userToken, deviceId);

                        boolean stripBiz = readMmkvBoolean(cl, MMKV_KEY_STRIP_BIZ_SOURCE, false);
                        if (stripBiz) {
                            setParameterMethod.invoke(engine, "biz_source", "");
                            logI("startSpeaking custom timbre=" + roleName + " (biz_source stripped)");
                            return chain.proceed();
                        }

                        // Snapshot current dynamic params so we can cleanly retry once without biz_source if server rejects "AI.Call"
                        Map<String, String> snapshot = snapshotSdkParams(sdkParams);
                        Object wrappedListener = wrapTtsListenerForFallback(
                                cl,
                                ttsListenerCls,
                                origListener,
                                engine,
                                text,
                                roleName,
                                userToken,
                                deviceId,
                                snapshot,
                                setParameterMethod,
                                originStartSpeaking
                        );
                        logI("startSpeaking custom timbre=" + roleName + " (with auto biz_source fallback)");
                        return chain.proceed(new Object[]{text, wrappedListener});
                    });
        } catch (Throwable t) {
            logE("Failed to hook HeytapTtsEngine.startSpeaking", t);
        }

        // 4. Offline TTS fallback safety so offline playback never crashes or fails silently on custom timbre
        try {
            Class<?> offlineHelperCls = findClass(cl, "com.heytap.speechassist.aicall.engine.tts.offline.AiCallOfflineDownloadHelper");
            Method getOfflinePathMethod = findMethod(offlineHelperCls, "j", boolean.class, String.class, String.class);
            hook(getOfflinePathMethod)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        String timbre = (String) chain.getArg(1);
                        if (isCustomTimbre(timbre)) {
                            return chain.proceed(new Object[]{chain.getArg(0), FALLBACK_OFFLINE_TIMBRE, chain.getArg(2)});
                        }
                        return chain.proceed();
                    });
        } catch (Throwable t) {
            logE("Failed to hook AiCallOfflineDownloadHelper.j", t);
        }

        try {
            Class<?> offlineMgrCls = findClass(cl, "com.heytap.speechassist.aicall.engine.tts.offline.AiCallOfflineTtsManager");
            Method getGreetingTextMethod = findMethod(offlineMgrCls, "h", boolean.class);
            hook(getGreetingTextMethod)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object res = chain.proceed();
                        if (res != null) {
                            return res;
                        }
                        boolean isContact = Boolean.TRUE.equals(chain.getArg(0));
                        String selected = getSelectedAiCallTimbre(cl, isContact);
                        if (isCustomTimbre(selected)) {
                            String fallbackGreeting = lookupFallbackOfflineGreeting(cl);
                            if (fallbackGreeting != null) {
                                return fallbackGreeting;
                            }
                        }
                        return null;
                    });
        } catch (Throwable t) {
            logE("Failed to hook AiCallOfflineTtsManager.h", t);
        }
    }

    private Object mergeCustomTimbres(ClassLoader cl, @Nullable Object originalObj) {
        List<Object> merged = new ArrayList<>();
        Set<String> existingTones = new HashSet<>();

        if (originalObj instanceof List<?>) {
            for (Object item : (List<?>) originalObj) {
                if (item != null) {
                    merged.add(item);
                    try {
                        String tone = (String) findField(item.getClass(), "tone").get(item);
                        if (!TextUtils.isEmpty(tone)) {
                            existingTones.add(tone);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        String json = fetchCustomTimbresJson(cl);
        if (TextUtils.isEmpty(json)) {
            return originalObj != null ? merged : null;
        }

        try {
            JSONArray arr = extractTimbreArray(json);
            if (arr == null || arr.length() == 0) {
                return merged;
            }
            Class<?> toneItemCls = findClass(cl, "com.heytap.speechassist.config.switchtone.ToneConfigManager$ToneConfigItem");
            Constructor<?> ctor = toneItemCls.getDeclaredConstructor();
            ctor.setAccessible(true);

            Field fTone = findField(toneItemCls, "tone");
            Field fTitle = findField(toneItemCls, "title");
            Field fDesc = findField(toneItemCls, "desc");
            Field fPic = findField(toneItemCls, "pic");
            Field fBigPic = findField(toneItemCls, "bigPic");
            Field fFirBgColor = findField(toneItemCls, "firBgColor");
            Field fSecBgColor = findField(toneItemCls, "secBgColor");
            Field fText = findField(toneItemCls, "text");
            Field fType = findField(toneItemCls, "type");

            int added = 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                String timbreId = obj.optString("timbreId", "");
                int status = obj.optInt("status", -1);
                if (TextUtils.isEmpty(timbreId) || status != 3) {
                    continue;
                }
                knownCustomTimbres.add(timbreId);
                if (existingTones.contains(timbreId)) {
                    continue;
                }

                String name = obj.optString("timbreName", "");
                if (TextUtils.isEmpty(name)) {
                    name = "自定义音色";
                }
                String avatar = obj.optString("avatar", "");
                String bigPic = obj.optString("bigPic", "");
                String firBg = obj.optString("firBgColor", "");
                String secBg = obj.optString("secBgColor", "");

                Object item = ctor.newInstance();
                fTone.set(item, timbreId);
                fTitle.set(item, name);
                fDesc.set(item, "小布助手自定义音色");
                if (!TextUtils.isEmpty(avatar)) {
                    fPic.set(item, avatar);
                }
                if (!TextUtils.isEmpty(bigPic)) {
                    fBigPic.set(item, bigPic);
                }
                fFirBgColor.set(item, !TextUtils.isEmpty(firBg) ? firBg : "#EEF3FF");
                fSecBgColor.set(item, !TextUtils.isEmpty(secBg) ? secBg : "#D8E5FF");
                fText.set(item, "");
                fType.set(item, "USER_TIMBRE");

                merged.add(item);
                existingTones.add(timbreId);
                added++;
            }
            if (added > 0) {
                logI("Injected " + added + " custom timbre(s) into AiCall tone list");
            }
        } catch (Throwable t) {
            logE("mergeCustomTimbres parse error", t);
        }

        return merged;
    }

    @Nullable
    private JSONArray extractTimbreArray(String json) {
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("[")) {
                return new JSONArray(trimmed);
            }
            JSONObject root = new JSONObject(trimmed);
            JSONArray list = root.optJSONArray("timbreList");
            if (list != null) {
                return list;
            }
            JSONObject dataObj = root.optJSONObject("data");
            if (dataObj != null) {
                return dataObj.optJSONArray("timbreList");
            }
            String dataStr = root.optString("data", "");
            if (!TextUtils.isEmpty(dataStr) && dataStr.trim().startsWith("{")) {
                return new JSONObject(dataStr).optJSONArray("timbreList");
            }
        } catch (Throwable t) {
            logE("extractTimbreArray error", t);
        }
        return null;
    }

    private void maybeRequestAsyncRefresh(
            ClassLoader cl,
            Object fragment,
            Method loadTimbresMethod,
            Method updateAssignmentMethod,
            Method isAddedMethod,
            Field assignmentInitField
    ) {
        if (!knownCustomTimbres.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastUiRefreshMs < 15_000L) {
            return;
        }
        lastUiRefreshMs = now;
        Thread worker = new Thread(() -> {
            try {
                Object bridge = getAiCallBridge(cl);
                if (bridge == null) {
                    return;
                }
                Method getConfig = findMethod(bridge.getClass(), "getGlobalModuleConfig", String.class);
                getConfig.invoke(bridge, BRIDGE_CMD_REFRESH_TIMBRES);
                Thread.sleep(1500L);
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        if (Boolean.TRUE.equals(isAddedMethod.invoke(fragment))) {
                            loadTimbresMethod.invoke(fragment);
                            assignmentInitField.setBoolean(fragment, false);
                            updateAssignmentMethod.invoke(fragment, false);
                        }
                    } catch (Throwable ignored) {
                    }
                });
            } catch (Throwable t) {
                logE("maybeRequestAsyncRefresh error", t);
            }
        }, "bcv-timbre-refresh");
        worker.setDaemon(true);
        worker.start();
    }

    private void applyCustomTtsParams(
            Object engine,
            Object sdkParams,
            Method setParameterMethod,
            @Nullable String userToken,
            @Nullable String deviceId
    ) throws Throwable {
        setParameterMethod.invoke(engine, "ttsType", "USER_TIMBRE");
        if (!TextUtils.isEmpty(userToken)) {
            setParameterMethod.invoke(engine, "userToken", userToken);
        }
        if (!TextUtils.isEmpty(deviceId)) {
            setParameterMethod.invoke(engine, "heytapAccountDeviceId", deviceId);
        }

        String existingCc = getSdkParam(sdkParams, "clientContext");
        JSONObject ccObj;
        try {
            ccObj = !TextUtils.isEmpty(existingCc) ? new JSONObject(existingCc) : new JSONObject();
        } catch (Throwable ignored) {
            ccObj = new JSONObject();
        }
        if (!TextUtils.isEmpty(userToken)) {
            ccObj.put("userToken", userToken);
        }
        if (!TextUtils.isEmpty(deviceId)) {
            ccObj.put("heytapAccountDeviceId", deviceId);
        }
        setParameterMethod.invoke(engine, "clientContext", ccObj.toString());
    }

    private Object wrapTtsListenerForFallback(
            ClassLoader cl,
            Class<?> ttsListenerCls,
            @Nullable Object origListener,
            Object engine,
            String text,
            String roleName,
            @Nullable String userToken,
            @Nullable String deviceId,
            Map<String, String> snapshot,
            Method setParameterMethod,
            XposedInterface.Invoker<?, Method> originStartSpeaking
    ) {
        AtomicBoolean receivedAudio = new AtomicBoolean(false);
        AtomicBoolean retried = new AtomicBoolean(false);

        return Proxy.newProxyInstance(
                cl,
                new Class<?>[]{ttsListenerCls},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("toString".equals(name)) {
                        return "BcvTtsListenerProxy(" + origListener + ")";
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(name)) {
                        return args != null && args.length == 1 && proxy == args[0];
                    }

                    if ("onSpeakBegin".equals(name)) {
                        receivedAudio.set(true);
                    } else if ("onEvent".equals(name) && args != null && args.length >= 1 && args[0] instanceof Integer) {
                        int eventType = (Integer) args[0];
                        if (eventType == 10102 || eventType == 10101) {
                            receivedAudio.set(true);
                        }
                    } else if ("onCompleted".equals(name) && args != null && args.length == 1 && args[0] != null) {
                        if (!receivedAudio.get() && retried.compareAndSet(false, true)) {
                            logW("Custom timbre TTS failed with biz_source=AI.Call (" + args[0]
                                    + "), persisting strip flag and retrying once without biz_source");
                            writeMmkvBoolean(cl, MMKV_KEY_STRIP_BIZ_SOURCE, true);
                            Thread retryThread = new Thread(() -> {
                                try {
                                    for (Map.Entry<String, String> entry : snapshot.entrySet()) {
                                        if (!TextUtils.isEmpty(entry.getValue())) {
                                            setParameterMethod.invoke(engine, entry.getKey(), entry.getValue());
                                        }
                                    }
                                    setParameterMethod.invoke(engine, "role_name", roleName);
                                    setParameterMethod.invoke(engine, "ttsType", "USER_TIMBRE");
                                    if (!TextUtils.isEmpty(userToken)) {
                                        setParameterMethod.invoke(engine, "userToken", userToken);
                                    }
                                    if (!TextUtils.isEmpty(deviceId)) {
                                        setParameterMethod.invoke(engine, "heytapAccountDeviceId", deviceId);
                                    }
                                    setParameterMethod.invoke(engine, "biz_source", "");
                                    originStartSpeaking.invoke(engine, text, origListener);
                                } catch (Throwable retryErr) {
                                    logE("Fallback retry startSpeaking failed", retryErr);
                                    if (origListener != null) {
                                        try {
                                            method.invoke(origListener, args);
                                        } catch (Throwable ignored) {
                                        }
                                    }
                                }
                            }, "bcv-tts-retry");
                            retryThread.setDaemon(true);
                            retryThread.start();
                            return null;
                        }
                    }

                    if (origListener != null) {
                        return method.invoke(origListener, args);
                    }
                    return null;
                }
        );
    }

    private Map<String, String> snapshotSdkParams(@Nullable Object sdkParams) {
        Map<String, String> map = new HashMap<>();
        if (sdkParams == null) {
            return map;
        }
        for (String key : TTS_SNAPSHOT_KEYS) {
            String val = getSdkParam(sdkParams, key);
            if (!TextUtils.isEmpty(val)) {
                map.put(key, val);
            }
        }
        return map;
    }

    @Nullable
    private String getSdkParam(@Nullable Object sdkParams, String key) {
        if (sdkParams == null) {
            return null;
        }
        try {
            return (String) findMethod(sdkParams.getClass(), "a", String.class).invoke(sdkParams, key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isCustomTimbre(@Nullable String roleName) {
        if (TextUtils.isEmpty(roleName)) {
            return false;
        }
        return roleName.startsWith("breeno") || knownCustomTimbres.contains(roleName);
    }

    // =========================================================================
    // Cross-process data helpers (IAiCallBridge + multi-process MMKV d90.b)
    // =========================================================================

    @Nullable
    private String fetchCustomTimbresJson(ClassLoader cl) {
        try {
            Object bridge = getAiCallBridge(cl);
            if (bridge != null) {
                String fromBridge = (String) findMethod(bridge.getClass(), "getGlobalModuleConfig", String.class)
                        .invoke(bridge, BRIDGE_CMD_GET_TIMBRES);
                if (!TextUtils.isEmpty(fromBridge)) {
                    writeMmkvString(cl, MMKV_KEY_USER_TIMBRES, fromBridge);
                    return fromBridge;
                }
            }
        } catch (Throwable ignored) {
        }
        return readMmkvString(cl, MMKV_KEY_USER_TIMBRES, "");
    }

    private String[] fetchAuth(ClassLoader cl) {
        String token = "";
        String deviceId = "";
        try {
            Object bridge = getAiCallBridge(cl);
            if (bridge != null) {
                token = (String) findMethod(bridge.getClass(), "getToken").invoke(bridge);
                deviceId = (String) findMethod(bridge.getClass(), "getDeviceId").invoke(bridge);
                if (!TextUtils.isEmpty(token)) {
                    writeMmkvString(cl, MMKV_KEY_USER_TOKEN, token);
                }
                if (!TextUtils.isEmpty(deviceId)) {
                    writeMmkvString(cl, MMKV_KEY_DEVICE_ID, deviceId);
                }
            }
        } catch (Throwable ignored) {
        }
        if (TextUtils.isEmpty(token)) {
            token = readMmkvString(cl, MMKV_KEY_USER_TOKEN, "");
        }
        if (TextUtils.isEmpty(deviceId)) {
            deviceId = readMmkvString(cl, MMKV_KEY_DEVICE_ID, "");
        }
        return new String[]{token != null ? token : "", deviceId != null ? deviceId : ""};
    }

    @Nullable
    private Object getAiCallBridge(ClassLoader cl) {
        try {
            Class<?> extL = findClass(cl, "com.heytap.speechassist.aicall.ext.l");
            return findMethod(extL, "f").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private String getSelectedAiCallTimbre(ClassLoader cl, boolean isContact) {
        try {
            Class<?> utilsCls = findClass(cl, "com.heytap.speechassist.aicall.setting.AiCallSettingUtils");
            Object instance = getStaticField(utilsCls, "INSTANCE");
            return (String) findMethod(utilsCls, "s", boolean.class).invoke(instance, isContact);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private String lookupFallbackOfflineGreeting(ClassLoader cl) {
        try {
            Class<?> cfgMgrCls = findClass(cl, "com.heytap.speechassist.aicall.setting.config.a");
            Object companion = getStaticField(cfgMgrCls, "c");
            Object mgr = findMethod(companion.getClass(), "a").invoke(companion);
            Object ttsCfg = findMethod(mgr.getClass(), "e").invoke(mgr);
            Object greetingsObj = findMethod(ttsCfg.getClass(), "getGreetingTtsConfig").invoke(ttsCfg);
            if (!(greetingsObj instanceof List<?>)) {
                return null;
            }
            String firstNonEmpty = null;
            for (Object item : (List<?>) greetingsObj) {
                if (item == null) {
                    continue;
                }
                String timbre = (String) findMethod(item.getClass(), "getTimbre").invoke(item);
                String greeting = (String) findMethod(item.getClass(), "getGreetings").invoke(item);
                if (FALLBACK_OFFLINE_TIMBRE.equals(timbre) && !TextUtils.isEmpty(greeting)) {
                    return greeting;
                }
                if (firstNonEmpty == null && !TextUtils.isEmpty(greeting)) {
                    firstNonEmpty = greeting;
                }
            }
            return firstNonEmpty;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private Context getAppContext(ClassLoader cl) {
        try {
            Class<?> v2Cls = findClass(cl, "com.heytap.speechassist.utils.v2");
            return (Context) findMethod(v2Cls, "b").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String readMmkvString(ClassLoader cl, String key, String defVal) {
        try {
            Class<?> mmkvUtil = findClass(cl, "d90.b");
            Object instance = getStaticField(mmkvUtil, "INSTANCE");
            String val = (String) findMethod(mmkvUtil, "g", String.class, String.class)
                    .invoke(instance, key, defVal);
            return val != null ? val : defVal;
        } catch (Throwable ignored) {
            return defVal;
        }
    }

    private void writeMmkvString(ClassLoader cl, String key, String value) {
        try {
            Class<?> mmkvUtil = findClass(cl, "d90.b");
            Object instance = getStaticField(mmkvUtil, "INSTANCE");
            findMethod(mmkvUtil, "j", String.class, String.class).invoke(instance, key, value);
        } catch (Throwable ignored) {
        }
    }

    private boolean readMmkvBoolean(ClassLoader cl, String key, boolean defVal) {
        try {
            Class<?> mmkvUtil = findClass(cl, "d90.b");
            Object instance = getStaticField(mmkvUtil, "INSTANCE");
            Boolean val = (Boolean) findMethod(mmkvUtil, "a", String.class, boolean.class)
                    .invoke(instance, key, defVal);
            return val != null ? val : defVal;
        } catch (Throwable ignored) {
            return defVal;
        }
    }

    private void writeMmkvBoolean(ClassLoader cl, String key, boolean value) {
        try {
            Class<?> mmkvUtil = findClass(cl, "d90.b");
            Object instance = getStaticField(mmkvUtil, "INSTANCE");
            findMethod(mmkvUtil, "h", String.class, boolean.class).invoke(instance, key, value);
        } catch (Throwable ignored) {
        }
    }

    // =========================================================================
    // Reflection & logging utilities
    // =========================================================================

    private static Class<?> findClass(ClassLoader cl, String name) throws ClassNotFoundException {
        return Class.forName(name, false, cl);
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>... paramTypes) throws NoSuchMethodException {
        Class<?> cur = cls;
        while (cur != null) {
            try {
                Method m = cur.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                cur = cur.getSuperclass();
            }
        }
        throw new NoSuchMethodException(cls.getName() + "#" + name);
    }

    private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        Class<?> cur = cls;
        while (cur != null) {
            try {
                Field f = cur.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        throw new NoSuchFieldException(cls.getName() + "#" + name);
    }

    private static Object getStaticField(Class<?> cls, String name) throws ReflectiveOperationException {
        return findField(cls, name).get(null);
    }

    private void logI(String msg) {
        log(Log.INFO, TAG, msg);
    }

    private void logW(String msg) {
        log(Log.WARN, TAG, msg);
    }

    private void logE(String msg, Throwable tr) {
        log(Log.ERROR, TAG, msg, tr);
    }
}
