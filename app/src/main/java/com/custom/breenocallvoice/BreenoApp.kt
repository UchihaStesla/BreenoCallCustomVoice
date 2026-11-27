package com.custom.breenocallvoice

import android.app.Application
import com.custom.breenocallvoice.config.XposedServiceManager

class BreenoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        XposedServiceManager.init(this)
    }
}
