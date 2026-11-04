package io.github.NotWayOut.MIUINavBar.hook

import android.content.Context
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.InputEvent
import io.github.NotWayOut.MIUINavBar.MainHook
import java.lang.reflect.Proxy

/**
 * 输入事件监听器。
 *
 * 原实现基于 YukiHookAPI 的 toClass()，此处改为原生反射：
 * - Android U 及以上：通过 InputManagerGlobal.monitorGestureInput 注册 InputEventReceiver
 * - Android T：通过 MIUI 的 MiuiGestureMonitor 注册 Fod 触摸监听
 */
object ChenInputEventDispatcher {

    private val listeners = ArrayList<(InputEvent) -> Unit>()
    private var inited = false
    private var mInputEventReceiver: Any? = null
    private var mInputMonitor: Any? = null

    private val isAboveU = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    fun isInited(): Boolean = inited

    fun init(context: Context, classLoader: ClassLoader) {
        if (inited) return

        if (isAboveU) {
            // Android U 直接注册监听器，因为 fod 监听器丢弃了部分接口
            val inputEventReceiverCls =
                Class.forName(
                    "com.android.systemui.shared.system.InputChannelCompat\$InputEventReceiver",
                    false,
                    classLoader
                )
            val inputEventListenerCls =
                Class.forName(
                    "com.android.systemui.shared.system.InputChannelCompat\$InputEventListener",
                    false,
                    classLoader
                )
            val inputManagerGlobalCls =
                Class.forName("android.hardware.input.InputManagerGlobal", false, classLoader)
            val inputManager = inputManagerGlobalCls.getMethod("getInstance").invoke(null)
            mInputMonitor = inputManager.javaClass.getMethod("monitorGestureInput", String::class.java, Int::class.java)
                .invoke(inputManager, "Chen-HomeHandle-Touch", context.display?.displayId)
            val inputChannel = mInputMonitor!!.javaClass.getMethod("getInputChannel").invoke(mInputMonitor)
            val looper = context.mainLooper
            val choreographer = Choreographer.getInstance()
            val chenListener = Proxy.newProxyInstance(
                classLoader,
                arrayOf(inputEventListenerCls)
            ) { _, _, args ->
                notifyListeners(args[0] as InputEvent)
            }

            mInputEventReceiver = createReceiver(
                inputEventReceiverCls,
                inputChannel,
                looper,
                choreographer,
                chenListener,
            )
            Log.i(MainHook.TAG, "[ChenInputEventDispatcher] init done on Android U+")
            inited = true
            return
        }

        // Android T 使用 fod 触摸监听
        val miuiGestureListenerClass =
            Class.forName("com.android.keyguard.fod.MiuiGestureListener", false, classLoader)
        val miuiGestureMonitorClass =
            Class.forName("com.android.keyguard.fod.MiuiGestureMonitor", false, classLoader)
        val chenListener = Proxy.newProxyInstance(
            classLoader,
            arrayOf(miuiGestureListenerClass)
        ) { _, _, args ->
            notifyListeners(args[0] as InputEvent)
        }
        val miuiGestureMonitorInstance = miuiGestureMonitorClass.getMethod("getInstance", Context::class.java)
            .invoke(null, context)
        Log.i(MainHook.TAG, "[ChenInputEventDispatcher] init on Android T (Legacy Fod Listener)")
        miuiGestureMonitorInstance.javaClass
            .getMethod("registerPointerEventListener", miuiGestureListenerClass)
            .invoke(miuiGestureMonitorInstance, chenListener)

        inited = true
    }

    /**
     * 构造 InputChannelCompat.InputEventReceiver。
     *
     * Android 15（V）起该构造器首位新增了 name 参数，部分 ROM 还会回移或调整参数，
     * 因此按实际构造器签名匹配参数，避免写死 SDK 判断导致参数数量不匹配而崩溃。
     */
    private fun createReceiver(
        cls: Class<*>,
        inputChannel: Any,
        looper: Looper,
        choreographer: Choreographer,
        listener: Any,
    ): Any {
        val constructor = cls.declaredConstructors.firstOrNull { candidate ->
            candidate.parameterTypes.any { it.isInstance(listener) } &&
                candidate.parameterTypes.all { type ->
                    type == String::class.java ||
                        type.name == "android.view.InputChannel" ||
                        type == Looper::class.java ||
                        type == Choreographer::class.java ||
                        type.isInstance(listener)
                }
        } ?: throw NoSuchMethodException("${cls.name} <init>")

        val args = constructor.parameterTypes.map { type ->
            when {
                type == String::class.java -> "ChenHomeHandleTouchHandler"
                type.name == "android.view.InputChannel" -> inputChannel
                type == Looper::class.java -> looper
                type == Choreographer::class.java -> choreographer
                type.isInstance(listener) -> listener
                else -> throw IllegalArgumentException("Unsupported parameter: $type")
            }
        }.toTypedArray()

        return constructor.apply { isAccessible = true }.newInstance(*args)
    }

    fun unregister() {
        if (isAboveU) {
            if (mInputMonitor != null) {
                mInputMonitor!!.javaClass.getMethod("dispose").invoke(mInputMonitor)
            }
            if (mInputEventReceiver != null) {
                val mReceiver = mInputEventReceiver!!.javaClass.getDeclaredField("mReceiver")
                    .apply { isAccessible = true }
                    .get(mInputEventReceiver)
                mReceiver.javaClass.getMethod("dispose").invoke(mReceiver)
            }
        }
        inited = false
    }

    private fun notifyListeners(inputEvent: InputEvent) {
        for (listener in listeners) {
            listener.invoke(inputEvent)
        }
    }

    fun registerInputEventListener(listener: (InputEvent) -> Unit) {
        listeners.add(listener)
    }

    fun unregisterInputEventListener(listener: (InputEvent) -> Unit) {
        listeners.remove(listener)
    }
}
