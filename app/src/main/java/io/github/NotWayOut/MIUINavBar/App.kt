package io.github.NotWayOut.MIUINavBar

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class App : Application(), XposedServiceHelper.OnServiceListener {

    companion object {
        /** 供 Compose 观察的服务绑定状态 */
        val serviceState = mutableStateOf<XposedService?>(null)

        @Volatile
        var mService: XposedService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        mService = service
        serviceState.value = service
    }

    override fun onServiceDied(service: XposedService) {
        mService = null
        serviceState.value = null
    }
}
