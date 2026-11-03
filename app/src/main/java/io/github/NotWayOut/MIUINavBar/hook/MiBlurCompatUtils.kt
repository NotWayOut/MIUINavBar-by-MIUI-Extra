package io.github.NotWayOut.MIUINavBar.hook

import android.content.Context
import android.view.View

/**
 * MIUI 高级材质（模糊）兼容调用。
 *
 * 小白条的 setMiBackgroundBlurMode / setMiBackgroundBlurRadius / setMiViewBlurMode 等
 * 方法由 MIUI 框架动态添加到 View 上（非公开 API），需通过反射调用。
 */
object MiBlurCompatUtils {

    var classLoader: ClassLoader? = null

    private val miBlurCompatClass: Class<*>?
        get() = runCatching {
            Class.forName("com.miui.systemui.util.MiBlurCompat", false, classLoader)
        }.getOrNull()

    /** 按名字与参数类型查找方法（包含父类），并绕过访问检查 */
    private fun findMethod(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): java.lang.reflect.Method {
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                c = c.superclass
            }
        }
        throw NoSuchMethodException("${clazz.name}#$name")
    }

    fun View.setPassWindowBlurEnabledCompat(enable: Boolean) {
        miBlurCompatClass?.let {
            findMethod(it, "setPassWindowBlurEnabledCompat", View::class.java, java.lang.Boolean.TYPE)
                .invoke(null, this, enable)
        }
    }

    fun View.getPassWindowBlurEnabled(): Boolean =
        findMethod(javaClass, "getPassWindowBlurEnabled").invoke(this) as Boolean

    fun View.setMiBackgroundBlurModeCompat(mode: Int) {
        findMethod(javaClass, "setMiBackgroundBlurMode", java.lang.Integer.TYPE).invoke(this, mode)
    }

    fun View.getMiBackgroundBlurModeCompat(): Int =
        findMethod(javaClass, "getMiBackgroundBlurMode").invoke(this) as Int

    fun View.setMiBackgroundBlurRadius(radius: Int) {
        findMethod(javaClass, "setMiBackgroundBlurRadius", java.lang.Integer.TYPE).invoke(this, radius)
    }

    fun View.setMiViewBlurMode(mode: Int) {
        findMethod(javaClass, "setMiViewBlurMode", java.lang.Integer.TYPE).invoke(this, mode)
    }

    fun View.isSupportMiBlur(): Boolean =
        miBlurCompatClass?.let {
            findMethod(it, "getBackgroundBlurOpened", Context::class.java).invoke(null, context) as? Boolean
        } ?: false
}
