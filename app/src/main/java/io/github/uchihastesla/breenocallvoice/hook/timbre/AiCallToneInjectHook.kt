package io.github.uchihastesla.breenocallvoice.hook.timbre

import android.os.Handler
import android.os.Looper
import io.github.uchihastesla.breenocallvoice.hook.bridge.BreenoConstants
import io.github.uchihastesla.breenocallvoice.hook.bridge.TimbreStore
import io.github.uchihastesla.breenocallvoice.hook.core.FeatureHook
import io.github.uchihastesla.breenocallvoice.hook.core.HookContext
import io.github.uchihastesla.breenocallvoice.hook.core.ReflectExt
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * `:aiCall` process hook that injects ready custom timbres (`status == 3`) into
 * `AiCallTimbreConfigBean.getToneList()` and refreshes `XiaoBuAiCallFragment` UI on resume/open.
 */
class AiCallToneInjectHook : FeatureHook {

    override val name: String = "AiCallToneInjectHook"

    @Volatile
    private var lastUiRefreshMs: Long = 0L

    override fun install(ctx: HookContext) {
        hookToneList(ctx)
        hookAiCallSettingsFragment(ctx)
    }

    private fun hookToneList(ctx: HookContext) {
        runCatching {
            val configBeanCls = ReflectExt.findClass(
                ctx.classLoader,
                BreenoConstants.CLASS_AICALL_TIMBRE_CONFIG_BEAN,
            )
            val getToneListMethod = ReflectExt.findMethod(configBeanCls, "getToneList")
            ctx.hookProtective(getToneListMethod) { chain ->
                val original = chain.proceed()
                if (!ctx.prefs.isCustomTimbreEnabled) return@hookProtective original
                mergeCustomTimbres(ctx, original)
            }
        }.onFailure { ctx.logE("Failed to hook AiCallTimbreConfigBean.getToneList", it) }
    }

    private fun hookAiCallSettingsFragment(ctx: HookContext) {
        runCatching {
            val fragmentCls = ReflectExt.findClass(
                ctx.classLoader,
                BreenoConstants.CLASS_AICALL_FRAGMENT,
            )
            val onResumeMethod = ReflectExt.findMethod(fragmentCls, "onResume")
            val loadTimbresMethod = ReflectExt.findMethod(fragmentCls, "b3")
            val updateAssignmentMethod = ReflectExt.findMethod(
                fragmentCls,
                "v3",
                Boolean::class.javaPrimitiveType!!,
            )
            val openDialogMethod = ReflectExt.findMethod(
                fragmentCls,
                "X2",
                String::class.java,
                Boolean::class.javaPrimitiveType!!,
            )
            val isAddedMethod = ReflectExt.findMethod(fragmentCls, "isAdded")
            val assignmentInitField = ReflectExt.findField(fragmentCls, "M")

            ctx.hookProtective(onResumeMethod) { chain ->
                if (ctx.prefs.isCustomTimbreEnabled) {
                    val fragment = chain.thisObject
                    runCatching {
                        loadTimbresMethod.invoke(fragment)
                        assignmentInitField.setBoolean(fragment, false)
                        if (ctx.prefs.isAutoCloudRefreshEnabled) {
                            maybeRequestAsyncRefresh(
                                ctx = ctx,
                                fragment = fragment,
                                loadTimbresMethod = loadTimbresMethod,
                                updateAssignmentMethod = updateAssignmentMethod,
                                isAddedMethod = isAddedMethod,
                                assignmentInitField = assignmentInitField,
                            )
                        }
                    }.onFailure { ctx.logE("XiaoBuAiCallFragment.onResume pre-hook error", it) }
                }
                chain.proceed()
            }

            ctx.hookProtective(openDialogMethod) { chain ->
                if (ctx.prefs.isCustomTimbreEnabled) {
                    runCatching {
                        loadTimbresMethod.invoke(chain.thisObject)
                    }.onFailure { ctx.logE("XiaoBuAiCallFragment.X2 pre-hook error", it) }
                }
                chain.proceed()
            }
        }.onFailure { ctx.logE("Failed to hook XiaoBuAiCallFragment", it) }
    }

    private fun mergeCustomTimbres(ctx: HookContext, originalObj: Any?): Any? {
        val merged = ArrayList<Any>()
        val existingTones = HashSet<String>()

        if (originalObj is List<*>) {
            for (item in originalObj) {
                if (item == null) continue
                merged.add(item)
                runCatching {
                    val tone = ReflectExt.findField(item.javaClass, "tone").get(item) as? String
                    if (!tone.isNullOrEmpty()) existingTones.add(tone)
                }
            }
        }

        val json = ctx.bridge.fetchCustomTimbresJson()
        if (json.isEmpty()) {
            return if (originalObj != null) merged else null
        }

        val customTimbres = TimbreStore.parseReadyTimbres(json)
        if (customTimbres.isEmpty()) return merged

        runCatching {
            val toneItemCls = ReflectExt.findClass(
                ctx.classLoader,
                BreenoConstants.CLASS_TONE_CONFIG_ITEM,
            )
            val ctor = toneItemCls.getDeclaredConstructor().apply { isAccessible = true }
            val fTone = ReflectExt.findField(toneItemCls, "tone")
            val fTitle = ReflectExt.findField(toneItemCls, "title")
            val fDesc = ReflectExt.findField(toneItemCls, "desc")
            val fPic = ReflectExt.findField(toneItemCls, "pic")
            val fBigPic = ReflectExt.findField(toneItemCls, "bigPic")
            val fFirBgColor = ReflectExt.findField(toneItemCls, "firBgColor")
            val fSecBgColor = ReflectExt.findField(toneItemCls, "secBgColor")
            val fText = ReflectExt.findField(toneItemCls, "text")
            val fType = ReflectExt.findField(toneItemCls, "type")

            var added = 0
            for (timbre in customTimbres) {
                if (!existingTones.add(timbre.id)) continue
                val item = ctor.newInstance()
                fTone.set(item, timbre.id)
                fTitle.set(item, timbre.name)
                fDesc.set(item, "小布助手自定义音色")
                if (timbre.avatar.isNotEmpty()) fPic.set(item, timbre.avatar)
                if (timbre.bigPic.isNotEmpty()) fBigPic.set(item, timbre.bigPic)
                fFirBgColor.set(item, timbre.firBgColor)
                fSecBgColor.set(item, timbre.secBgColor)
                fText.set(item, "")
                fType.set(item, "USER_TIMBRE")
                merged.add(item)
                added++
            }
            if (added > 0) {
                ctx.logI("Injected $added custom timbre(s) into AiCall tone list")
            }
        }.onFailure { ctx.logE("mergeCustomTimbres error", it) }

        return merged
    }

    private fun maybeRequestAsyncRefresh(
        ctx: HookContext,
        fragment: Any?,
        loadTimbresMethod: Method,
        updateAssignmentMethod: Method,
        isAddedMethod: Method,
        assignmentInitField: Field,
    ) {
        if (fragment == null || TimbreStore.hasKnownTimbres()) return
        val now = System.currentTimeMillis()
        if (now - lastUiRefreshMs < 15_000L) return
        lastUiRefreshMs = now

        Thread({
            runCatching {
                ctx.bridge.sendBridgeCommand(BreenoConstants.BRIDGE_CMD_REFRESH_TIMBRES) ?: return@runCatching
                Thread.sleep(1_500L)
                Handler(Looper.getMainLooper()).post {
                    runCatching {
                        if (isAddedMethod.invoke(fragment) == true) {
                            loadTimbresMethod.invoke(fragment)
                            assignmentInitField.setBoolean(fragment, false)
                            updateAssignmentMethod.invoke(fragment, false)
                        }
                    }
                }
            }.onFailure { ctx.logE("maybeRequestAsyncRefresh error", it) }
        }, "bcv-timbre-refresh").apply {
            isDaemon = true
            start()
        }
    }
}
