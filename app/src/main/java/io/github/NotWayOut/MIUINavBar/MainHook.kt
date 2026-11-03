package io.github.NotWayOut.MIUINavBar

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.NotWayOut.MIUINavBar.config.KEY_HOME_HANDLE_ANIM
import io.github.NotWayOut.MIUINavBar.config.RemoteConfig
import io.github.NotWayOut.MIUINavBar.hook.HomeHandleHooker

/**
 * libxposed API 101 模块入口。
 *
 * 由 META-INF/xposed/java_init.list 注册，框架在 SystemUI 进程中实例化一次。
 */
class MainHook : XposedModule() {

    companion object {
        const val TAG = "MIUIExtra"

        const val SYSTEM_UI = "com.android.systemui"

        lateinit var instance: MainHook
            private set
    }

    init {
        instance = this
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != SYSTEM_UI) return
        log(Log.INFO, TAG, "SystemUI is ready, process: ${param.packageName}")

        if (!RemoteConfig.prefs.getBoolean(KEY_HOME_HANDLE_ANIM, false)) {
            log(Log.INFO, TAG, "Home handle animation is disabled")
            return
        }

        runCatching {
            HomeHandleHooker(this, param.classLoader).hook()
            log(Log.INFO, TAG, "HomeHandleHooker hooked")
        }.onFailure {
            log(Log.ERROR, TAG, "Failed to hook home handle", it)
        }
    }
}
