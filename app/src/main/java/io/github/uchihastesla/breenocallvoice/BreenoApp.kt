package io.github.uchihastesla.breenocallvoice

import android.app.Application
import io.github.uchihastesla.breenocallvoice.config.XposedServiceManager

class BreenoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        XposedServiceManager.init(this)
    }
}
