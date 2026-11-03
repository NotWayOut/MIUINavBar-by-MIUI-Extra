package io.github.NotWayOut.MIUINavBar.config

import android.content.Context
import android.content.SharedPreferences
import io.github.NotWayOut.MIUINavBar.App
import io.github.NotWayOut.MIUINavBar.MainHook

/** 模块与 SystemUI 进程共享的配置组名 */
const val PREFS_GROUP = "chen_main_settings"

/** 小白条动画总开关 */
const val KEY_HOME_HANDLE_ANIM = "chen_home_handle_anim"

/** 沉浸式手势栏（手势栏不再占位） */
const val KEY_HOME_HANDLE_NO_SPACE = "chen_home_handle_no_space"

/** 在小白条上应用模糊材质 */
const val KEY_HOME_HANDLE_BLUR_EFFECT = "chen_home_handle_blur_effect"

/** 禁用小白条跟手位移 */
const val KEY_HOME_HANDLE_DISABLE_MOVE = "home_handle_disable_move_event"

/** 在桌面时强制使用透明度调整 */
const val KEY_HOME_HANDLE_FORCE_ALPHA_AT_HOME = "home_handle_force_alpha_at_home"

/** 隐藏桌面图标 */
const val KEY_HIDE_LAUNCHER_ICON = "hide_launcher_icon"

/** 小白条动画状态 */
enum class HomeHandleState(val suffix: String) {
    NORMAL("normal"),
    PRESSED("pressed"),
    HOME("home"),
}

/** 小白条一组动画参数 */
data class HomeHandleSettings(
    val transDegree: Int,
    val scaleX: Float,
    val scaleY: Float,
    val offsetX: Float,
    val offsetY: Float,
    val alphaAnimDuration: Long,
    val scaleAnimDuration: Long,
    val xyAnimDuration: Long,
) {
    companion object {
        fun from(prefs: SharedPreferences, state: HomeHandleState): HomeHandleSettings {
            val s = state.suffix
            return HomeHandleSettings(
                transDegree = prefs.getInt("home_handle_setting_transdegree_$s", 30),
                scaleX = prefs.getInt("home_handle_setting_scalex_$s", 100) / 100f,
                scaleY = prefs.getInt("home_handle_setting_scaley_$s", 100) / 100f,
                offsetX = prefs.getInt("home_handle_setting_offsetx_$s", 0).toFloat(),
                offsetY = prefs.getInt("home_handle_setting_offsety_$s", 0).toFloat(),
                alphaAnimDuration = prefs.getInt("home_handle_setting_duration_alpha_$s", 600).toLong(),
                scaleAnimDuration = prefs.getInt("home_handle_setting_duration_scale_$s", 600).toLong(),
                xyAnimDuration = prefs.getInt("home_handle_setting_duration_xy_$s", 600).toLong(),
            )
        }
    }
}

/**
 * 被 Hook 进程中的只读配置（由框架提供的远程首选项）。
 *
 * 独立成对象而非文件顶层属性：入口类 MainHook 继承 compileOnly 的 XposedModule，
 * 若在 ConfigKt 的静态初始化中引用它，应用进程加载 ConfigKt 时即会因缺少超类崩溃。
 * 本对象仅在 Hook 进程中被加载。
 */
object RemoteConfig {
    val prefs: SharedPreferences by lazy { MainHook.instance.getRemotePreferences(PREFS_GROUP) }
}

/**
 * 模块自身进程中的可写配置。
 *
 * 必须通过 XposedService 的远程首选项写入：libxposed 的远程配置存储于框架数据库中，
 * 被 Hook 进程经 getRemotePreferences 读取的正是该存储；直接写模块私有 shared_prefs
 * 文件不会被 Hook 侧看到。框架未绑定时退化为应用私有文件。
 */
fun localConfig(context: Context): SharedPreferences =
    App.mService?.getRemotePreferences(PREFS_GROUP)
        ?: context.getSharedPreferences(PREFS_GROUP, Context.MODE_PRIVATE)
