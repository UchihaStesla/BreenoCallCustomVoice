package com.custom.breenocallvoice.hook.update

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.custom.breenocallvoice.hook.bridge.BreenoConstants
import com.custom.breenocallvoice.hook.core.FeatureHook
import com.custom.breenocallvoice.hook.core.HookContext
import com.custom.breenocallvoice.hook.core.ReflectExt
import java.io.File

/**
 * Main-process hook blocking Breeno Assistant's in-app self-update checks, popups,
 * notifications, APK downloads, and installs so the hooked version stays stable.
 *
 * Targets verified in com.heytap.speechassist 12.9.6:
 * - `com.heytap.upgrade.UpgradeSDK` (checkUpgrade / startDownload / install / hasDownloadComplete)
 * - `com.heytap.upgrade.model.UpgradeInfo#isUpgradeAvailable()`
 * - `com.heytap.speechassist.home.settings.ui.UpgradeActivity#x3(int)`
 * - `or.a#o(String)` (UpgradeCommandProcessor notification)
 */
class BlockUpdateHook : FeatureHook {

    override val name: String = "BlockUpdateHook"

    override fun install(ctx: HookContext) {
        hookUpgradeSdk(ctx)
        hookUpgradeInfo(ctx)
        hookUpgradeActivity(ctx)
        hookUpgradeNotification(ctx)
    }

    private fun hookUpgradeSdk(ctx: HookContext) {
        runCatching {
            val sdkCls = ReflectExt.findClass(ctx.classLoader, BreenoConstants.CLASS_UPGRADE_SDK)
            val checkParamCls = ReflectExt.findClass(ctx.classLoader, BreenoConstants.CLASS_CHECK_PARAM)
            val downloadParamCls = ReflectExt.findClass(ctx.classLoader, BreenoConstants.CLASS_DOWNLOAD_PARAM)
            val installParamCls = ReflectExt.findClass(ctx.classLoader, BreenoConstants.CLASS_INSTALL_PARAM)
            val upgradeInfoCls = ReflectExt.findClass(ctx.classLoader, BreenoConstants.CLASS_UPGRADE_INFO)

            val checkUpgrade = ReflectExt.findMethod(sdkCls, "checkUpgrade", checkParamCls)
            ctx.hookProtective(checkUpgrade) { chain ->
                if (!ctx.prefs.isBlockAppUpdateEnabled) return@hookProtective chain.proceed()
                val param = chain.getArg(0)
                ctx.logI("Blocked UpgradeSDK.checkUpgrade()")
                notifyNoUpgradeAvailable(ctx, param, upgradeInfoCls)
                null
            }

            val startDownload = ReflectExt.findMethod(sdkCls, "startDownload", downloadParamCls)
            ctx.hookProtective(startDownload) { chain ->
                if (!ctx.prefs.isBlockAppUpdateEnabled) return@hookProtective chain.proceed()
                ctx.logI("Blocked UpgradeSDK.startDownload()")
                false
            }

            val install = ReflectExt.findMethod(sdkCls, "install", installParamCls)
            ctx.hookProtective(install) { chain ->
                if (!ctx.prefs.isBlockAppUpdateEnabled) return@hookProtective chain.proceed()
                ctx.logI("Blocked UpgradeSDK.install()")
                null
            }

            val hasDownloadComplete = ReflectExt.findMethod(
                sdkCls,
                "hasDownloadComplete",
                String::class.java,
                File::class.java,
                upgradeInfoCls,
            )
            ctx.hookProtective(hasDownloadComplete) { chain ->
                if (!ctx.prefs.isBlockAppUpdateEnabled) return@hookProtective chain.proceed()
                ctx.logD("Blocked UpgradeSDK.hasDownloadComplete() -> false")
                false
            }
        }.onFailure { ctx.logE("Failed to hook UpgradeSDK", it) }
    }

    private fun hookUpgradeInfo(ctx: HookContext) {
        runCatching {
            val upgradeInfoCls = ReflectExt.findClass(ctx.classLoader, BreenoConstants.CLASS_UPGRADE_INFO)
            val isAvailable = ReflectExt.findMethod(upgradeInfoCls, "isUpgradeAvailable")
            ctx.hookProtective(isAvailable) { chain ->
                if (!ctx.prefs.isBlockAppUpdateEnabled) return@hookProtective chain.proceed()
                false
            }
        }.onFailure { ctx.logE("Failed to hook UpgradeInfo.isUpgradeAvailable", it) }
    }

    private fun hookUpgradeActivity(ctx: HookContext) {
        runCatching {
            val activityCls = ReflectExt.findClass(ctx.classLoader, BreenoConstants.CLASS_UPGRADE_ACTIVITY)
            val dealCmd = ReflectExt.findMethod(activityCls, "x3", Int::class.javaPrimitiveType!!)
            ctx.hookProtective(dealCmd) { chain ->
                if (!ctx.prefs.isBlockAppUpdateEnabled) return@hookProtective chain.proceed()
                ctx.logI("Blocked UpgradeActivity dialog command -> finishing activity")
                (chain.thisObject as? Activity)?.finish()
                null
            }
        }.onFailure { ctx.logE("Failed to hook UpgradeActivity.x3", it) }
    }

    private fun hookUpgradeNotification(ctx: HookContext) {
        runCatching {
            val processorCls = ReflectExt.findClass(
                ctx.classLoader,
                BreenoConstants.CLASS_UPGRADE_COMMAND_PROCESSOR,
            )
            val showNotify = ReflectExt.findMethod(processorCls, "o", String::class.java)
            ctx.hookProtective(showNotify) { chain ->
                if (!ctx.prefs.isBlockAppUpdateEnabled) return@hookProtective chain.proceed()
                ctx.logI("Blocked upgrade notification: ${chain.getArg(0)}")
                null
            }
        }.onFailure { ctx.logE("Failed to hook UpgradeCommandProcessor.o", it) }
    }

    /**
     * Synthesizes a clean "no upgrade available" callback (`upgradeFlag = FLAG_NO_UPGRADE (1)`)
     * so manual checks in Settings -> Check for updates immediately display "已是最新版本"
     * instead of spinning indefinitely.
     */
    private fun notifyNoUpgradeAvailable(ctx: HookContext, checkParam: Any?, upgradeInfoCls: Class<*>) {
        if (checkParam == null) return
        runCatching {
            val callback = ReflectExt.findMethod(checkParam.javaClass, "getCallback").invoke(checkParam)
                ?: return
            val info = upgradeInfoCls.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            ReflectExt.findMethod(upgradeInfoCls, "setUpgradeFlag", Int::class.javaPrimitiveType!!)
                .invoke(info, 1) // UpgradeInfo.FLAG_NO_UPGRADE
            val onResult = ReflectExt.findMethod(callback.javaClass, "onResult", upgradeInfoCls)
            Handler(Looper.getMainLooper()).post {
                runCatching { onResult.invoke(callback, info) }
            }
        }.onFailure { ctx.logE("notifyNoUpgradeAvailable error", it) }
    }
}
