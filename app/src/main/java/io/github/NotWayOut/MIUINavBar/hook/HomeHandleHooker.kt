package io.github.NotWayOut.MIUINavBar.hook

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Insets
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import io.github.libxposed.api.XposedModule
import io.github.NotWayOut.MIUINavBar.MainHook
import io.github.NotWayOut.MIUINavBar.config.HomeHandleSettings
import io.github.NotWayOut.MIUINavBar.config.HomeHandleState
import io.github.NotWayOut.MIUINavBar.config.KEY_HOME_HANDLE_BLUR_EFFECT
import io.github.NotWayOut.MIUINavBar.config.KEY_HOME_HANDLE_DISABLE_MOVE
import io.github.NotWayOut.MIUINavBar.config.KEY_HOME_HANDLE_FORCE_ALPHA_AT_HOME
import io.github.NotWayOut.MIUINavBar.config.KEY_HOME_HANDLE_NO_SPACE
import io.github.NotWayOut.MIUINavBar.config.RemoteConfig
import io.github.NotWayOut.MIUINavBar.hook.MiBlurCompatUtils.getMiBackgroundBlurModeCompat
import io.github.NotWayOut.MIUINavBar.hook.MiBlurCompatUtils.getPassWindowBlurEnabled
import io.github.NotWayOut.MIUINavBar.hook.MiBlurCompatUtils.isSupportMiBlur
import io.github.NotWayOut.MIUINavBar.hook.MiBlurCompatUtils.setMiBackgroundBlurModeCompat
import io.github.NotWayOut.MIUINavBar.hook.MiBlurCompatUtils.setMiBackgroundBlurRadius
import io.github.NotWayOut.MIUINavBar.hook.MiBlurCompatUtils.setMiViewBlurMode
import io.github.NotWayOut.MIUINavBar.hook.MiBlurCompatUtils.setPassWindowBlurEnabledCompat
import java.lang.reflect.Field
import kotlin.math.abs

/**
 * 小白条（手势线）动画 Hook。
 *
 * 迁移自 MIUI-Extra-YukiAPI 的 HomeHandleAnimatorHooker，改为 libxposed API 101 原生写法：
 * hook(executable).intercept { chain -> ... }，不再使用 YukiHookAPI 与 XposedHelpers。
 *
 * 未迁移：小白条动画增强模式（turbo）、手势栏隐藏时隐藏小白条（workaround）。
 */
class HomeHandleHooker(
    private val module: XposedModule,
    private val classLoader: ClassLoader,
) {

    private companion object {
        const val NAVIGATION_BAR_INFLATER_VIEW = "com.android.systemui.navigationbar.NavigationBarInflaterView"
        const val NAVIGATION_BAR = "com.android.systemui.navigationbar.NavigationBar"
        const val NAVIGATION_HANDLE = "com.android.systemui.navigationbar.gestural.NavigationHandle"
        const val MIUI_ACTIVITY_UTIL = "com.miui.systemui.util.MiuiActivityUtil"
        const val MIUI_TOP_ACTIVITY_OBSERVER = "com.miui.systemui.functions.MiuiTopActivityObserver"
        const val BAR_TRANSITIONS = "com.android.systemui.statusbar.phone.BarTransitions"
        const val PHONE_STATUS_BAR_TRANSITIONS = "com.android.systemui.statusbar.phone.PhoneStatusBarTransitions"
    }

    private val isAboveU = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    private val prefs = RemoteConfig.prefs

    private var settingsDirty = true

    private var mContext: Context? = null
    private var mHandler: Handler? = null

    private lateinit var mHomeHandle: View

    private var mHomeHandleId = -1
    private var barHeight = 0
    private var origBarHeight = 0
    private var yOffset = 0f
    private var useMiBlur = false
    private var mLightColor = -1
    private var mDarkColor = -1
    private var orientation = 0
    private var disableHomeHandleMovement = false
    private var forceAlphaAtHome = false
    private var noSpace = false

    private var topActivityObserver: Any? = null

    private var mIsInHome = false
    private var motionTriggered = false

    private lateinit var normalSettings: HomeHandleSettings
    private lateinit var pressedSettings: HomeHandleSettings
    private lateinit var onHomeSettings: HomeHandleSettings

    private lateinit var zoomXAnimator: ValueAnimator
    private lateinit var zoomYAnimator: ValueAnimator
    private lateinit var xAnimator: ValueAnimator
    private lateinit var yAnimator: ValueAnimator
    private lateinit var alphaAnimator: ValueAnimator

    private var currBlurRadius = 0
    private var lastBlurRadius = 0

    private var baseX = -1f
    private var baseY = -1f

    private val opacityHomeHandleRunnable = Runnable {
        if (alphaAnimator.isRunning) {
            alphaAnimator.cancel()
        }
        alphaAnimator.start()
    }

    private val doOnOpacityUpdate = ValueAnimator.AnimatorUpdateListener {
        if (!useMiBlur || !mHomeHandle.isSupportMiBlur()) {
            // 非模糊模式：动画过程中调整透明度
            mHomeHandle.alpha = it.animatedValue as Float
        } else {
            // 模糊模式：调整模糊半径
            currBlurRadius = (it.animatedValue as Float).toInt() * 10
            // setRadius 开销较大，仅在变化时调用
            if (currBlurRadius != lastBlurRadius) {
                mHomeHandle.setMiBackgroundBlurRadius(currBlurRadius)
            }
            lastBlurRadius = currBlurRadius
        }
    }

    fun hook() {
        MiBlurCompatUtils.classLoader = classLoader

        prefs.registerOnSharedPreferenceChangeListener { _, _ -> settingsDirty = true }

        hookNavigationBarInflaterView()
        hookNavigationBar()
        hookTopActivityChanged()
        hookNavigationHandle()
        hookBarTransitions()
    }

    /* ---------------------------------- 配置读取 ---------------------------------- */

    private fun updateSettings(force: Boolean = false) {
        if (force || settingsDirty) {
            settingsDirty = false
            normalSettings = HomeHandleSettings.from(prefs, HomeHandleState.NORMAL)
            pressedSettings = HomeHandleSettings.from(prefs, HomeHandleState.PRESSED)
            onHomeSettings = HomeHandleSettings.from(prefs, HomeHandleState.HOME)

            disableHomeHandleMovement = prefs.getBoolean(KEY_HOME_HANDLE_DISABLE_MOVE, false)
            useMiBlur = prefs.getBoolean(KEY_HOME_HANDLE_BLUR_EFFECT, false)
            forceAlphaAtHome = prefs.getBoolean(KEY_HOME_HANDLE_FORCE_ALPHA_AT_HOME, false)
            noSpace = prefs.getBoolean(KEY_HOME_HANDLE_NO_SPACE, false)
        }
    }

    /* ----------------------------------- 动画 ----------------------------------- */

    private fun animateZoomTo(scaleX: Float, scaleY: Float, duration: Long) {
        if (!this::zoomXAnimator.isInitialized) {
            zoomXAnimator = ValueAnimator()
            zoomYAnimator = ValueAnimator()
            zoomXAnimator.interpolator = PathInterpolator(0.39f, 1.58f, 0.44f, 1.07f)
            zoomYAnimator.interpolator = PathInterpolator(0.39f, 1.58f, 0.44f, 1.07f)

            zoomXAnimator.addUpdateListener {
                mHomeHandle.scaleX = it.animatedValue as Float
            }
            zoomYAnimator.addUpdateListener {
                mHomeHandle.scaleY = it.animatedValue as Float
            }
        }

        mHomeHandle.pivotX = (mHomeHandle.width / 2).toFloat()
        if (zoomXAnimator.isRunning) zoomXAnimator.cancel()
        if (zoomYAnimator.isRunning) zoomYAnimator.cancel()

        zoomXAnimator.setFloatValues(mHomeHandle.scaleX, scaleX)
        zoomYAnimator.setFloatValues(mHomeHandle.scaleY, scaleY)
        zoomXAnimator.duration = duration
        zoomYAnimator.duration = duration

        zoomXAnimator.start()
        zoomYAnimator.start()
    }

    private fun animateHomeHandleX(duration: Long, offset: Float) {
        if (!this::xAnimator.isInitialized) {
            xAnimator = ValueAnimator()
            xAnimator.interpolator = PathInterpolator(0.39f, 1.58f, 0.44f, 1.07f)
        }
        xAnimator.duration = duration
        xAnimator.setFloatValues(mHomeHandle.translationX, offset)

        xAnimator.addUpdateListener {
            mHomeHandle.translationX = it.animatedValue as Float
        }
        xAnimator.start()
    }

    private fun animateHomeHandleY(duration: Long, offset: Float) {
        if (!this::yAnimator.isInitialized) {
            yAnimator = ValueAnimator()
            yAnimator.interpolator = PathInterpolator(0.39f, 1.58f, 0.44f, 1.07f)

            yAnimator.addUpdateListener {
                mHomeHandle.translationY = it.animatedValue as Float
            }
        }

        yAnimator.duration = duration
        yAnimator.setFloatValues(mHomeHandle.translationY, offset)

        yAnimator.start()
    }

    private fun cancelHomeHandleXYAnim() {
        xAnimator.cancel()
        yAnimator.cancel()
    }

    private fun animateHomeHandleXYToNormal(type: HomeHandleState) {
        val setting = when (type) {
            HomeHandleState.NORMAL -> normalSettings
            HomeHandleState.HOME -> onHomeSettings
            HomeHandleState.PRESSED -> pressedSettings
        }
        yOffset = setting.offsetY
        animateHomeHandleX(setting.xyAnimDuration, setting.offsetX)
        animateHomeHandleY(setting.xyAnimDuration, setting.offsetY)
    }

    private fun opacityTo(to: Float, duration: Long) {
        if (!this::alphaAnimator.isInitialized) {
            alphaAnimator = ValueAnimator()
        }
        if (alphaAnimator.isRunning) alphaAnimator.end()

        alphaAnimator.duration = duration
        alphaAnimator.setFloatValues(mHomeHandle.alpha, to)

        if (currBlurRadius != 0) {
            currBlurRadius = 0
            mHomeHandle.setMiBackgroundBlurRadius(currBlurRadius)
            mHomeHandle.setMiBackgroundBlurModeCompat(0)
        }
        alphaAnimator.removeAllListeners()
        alphaAnimator.addUpdateListener {
            mHomeHandle.alpha = it.animatedValue as Float
        }
        alphaAnimator.start()
    }

    private fun opacityHomeHandle(type: HomeHandleState) {
        var notStart = false
        updateSettings()

        if (!this::alphaAnimator.isInitialized) {
            alphaAnimator = ValueAnimator()
            alphaAnimator.addUpdateListener(doOnOpacityUpdate)
        }

        if (forceAlphaAtHome && useMiBlur && type == HomeHandleState.HOME) {
            // 桌面下强制使用透明度动画
            opacityTo(1 - (onHomeSettings.transDegree.toFloat() / 100), onHomeSettings.alphaAnimDuration)
            animateZoomTo(onHomeSettings.scaleX, onHomeSettings.scaleY, onHomeSettings.scaleAnimDuration)
            return
        }

        if (alphaAnimator.isRunning) {
            if (type == HomeHandleState.HOME) {
                alphaAnimator.cancel()
            } else {
                alphaAnimator.doOnEnd {
                    it.removeAllListeners()
                    alphaAnimator.addUpdateListener(doOnOpacityUpdate)
                    opacityHomeHandle(type)
                }
                return
            }
        }

        val duration = when {
            type == HomeHandleState.HOME
                // 普通或按下到桌面
            -> onHomeSettings.alphaAnimDuration

            type == HomeHandleState.NORMAL && mHomeHandle.alpha != 0f
                // 按下到普通
            -> normalSettings.alphaAnimDuration * 2

            type == HomeHandleState.NORMAL
                // 桌面到普通
            -> normalSettings.alphaAnimDuration

            else
                // 普通到按下
            -> pressedSettings.alphaAnimDuration
        }

        if (useMiBlur) {
            if (mHomeHandle.isSupportMiBlur() && mHomeHandle.getMiBackgroundBlurModeCompat() == 0 && orientation == 0) {
                mHomeHandle.alpha = 1f
                mHomeHandle.setMiBackgroundBlurModeCompat(1)
                mHomeHandle.setMiViewBlurMode(2)
                mHomeHandle.setMiBackgroundBlurRadius(normalSettings.transDegree)
                mHomeHandle.setPassWindowBlurEnabledCompat(true)

                alphaAnimator.doOnEnd {
                    if (currBlurRadius == 0) {
                        // 半径为 0 时清除模糊
                        mHomeHandle.setMiBackgroundBlurModeCompat(0)
                    }
                }
            }

            if (mDarkColor != -1 && mLightColor != -1 && mDarkColor != 0x55) {
                mDarkColor = Color.argb(0x55, Color.red(mDarkColor), Color.green(mDarkColor), Color.blue(mDarkColor))
                mLightColor = Color.argb(0x77, Color.red(mLightColor), Color.green(mLightColor), Color.blue(mLightColor))
            }
        } else {
            if (mDarkColor != -1 && mLightColor != -1 && mDarkColor != 0xee) {
                // 按原始颜色重置透明度
                mDarkColor = Color.argb(0xee, Color.red(mDarkColor), Color.green(mDarkColor), Color.blue(mDarkColor))
                mLightColor = Color.argb(0xdd, Color.red(mLightColor), Color.green(mLightColor), Color.blue(mLightColor))
            }
            if (mHomeHandle.isSupportMiBlur() && mHomeHandle.getPassWindowBlurEnabled()) {
                mHomeHandle.setMiBackgroundBlurRadius(0)
                mHomeHandle.setPassWindowBlurEnabledCompat(false)
            }
        }

        if (useMiBlur) {
            val to = when (type) {
                HomeHandleState.HOME -> onHomeSettings.transDegree.toFloat()
                HomeHandleState.NORMAL -> normalSettings.transDegree.toFloat()
                else -> pressedSettings.transDegree.toFloat()
            }
            if (currBlurRadius.toFloat() == to) notStart = true

            alphaAnimator.setFloatValues(
                currBlurRadius.toFloat() / 10,
                to / 10
            )
        } else {
            // 普通透明度动画
            val to = when (type) {
                HomeHandleState.HOME -> 1 - (onHomeSettings.transDegree.toFloat() / 100)
                HomeHandleState.NORMAL -> 1 - (normalSettings.transDegree.toFloat() / 100)
                else -> 1 - (pressedSettings.transDegree.toFloat() / 100)
            }
            if (mHomeHandle.alpha == to) notStart = true
            alphaAnimator.setFloatValues(
                mHomeHandle.alpha,
                to
            )
        }
        alphaAnimator.duration = duration

        if (!notStart) {
            mHandler?.removeCallbacks(opacityHomeHandleRunnable)
            mHandler?.post(opacityHomeHandleRunnable)
        }

        when (type) {
            HomeHandleState.NORMAL -> animateZoomTo(
                normalSettings.scaleX,
                normalSettings.scaleY,
                normalSettings.scaleAnimDuration
            )
            HomeHandleState.HOME -> animateZoomTo(
                onHomeSettings.scaleX,
                onHomeSettings.scaleY,
                onHomeSettings.scaleAnimDuration
            )
            else -> animateZoomTo(
                pressedSettings.scaleX,
                pressedSettings.scaleY,
                pressedSettings.scaleAnimDuration
            )
        }
    }

    private fun leaveHome() {
        opacityHomeHandle(HomeHandleState.NORMAL)
        animateHomeHandleXYToNormal(HomeHandleState.NORMAL)
    }

    private fun enterHome() {
        // 透明度动画在当前手势释放后再执行
        if (!motionTriggered) {
            opacityHomeHandle(HomeHandleState.HOME)
            animateHomeHandleXYToNormal(HomeHandleState.HOME)
        }
    }

    private fun onHomeBlurChanged(active: Boolean) {
        if (!useMiBlur) return

        val mode = if (active) 0 else 1
        if (mHomeHandle.getMiBackgroundBlurModeCompat() == mode) return

        mHomeHandle.setMiBackgroundBlurModeCompat(mode)
    }

    /* ---------------------------------- 输入事件 ---------------------------------- */

    private fun onInputEvent(inputEvent: InputEvent) {
        if (inputEvent !is MotionEvent) return
        // 拷贝事件到本地 runnable 执行以避免竞态
        val motionEvent = MotionEvent.obtain(inputEvent)
        mHandler?.post {
            if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN
                && (mHomeHandle.alpha == 0f || mHomeHandle.scaleX <= 0f || mHomeHandle.scaleY <= 0f
                        || (mHomeHandle.windowVisibility != View.VISIBLE))
            ) {
                // 小白条透明时丢弃事件
                return@post
            }

            if (disableHomeHandleMovement && motionEvent.actionMasked == MotionEvent.ACTION_MOVE) {
                // 禁用跟手位移时丢弃移动事件
                return@post
            }

            val screenRealHeight = getScreenRealHeight(mContext!!)
            val screenHeight = getScreenHeight(mContext!!)
            // onInputEvent 在 mHandler post 中执行，原始 motionEvent 已被回收，
            // 因此使用拷贝的事件并由我们自行回收
            val isNavigationBarArea: Boolean =
                if (!isAboveU && screenRealHeight - screenHeight > 1) {
                    motionEvent.y > screenHeight
                } else {
                    // U 以上：真实高度 - 原始导航栏高度更准确
                    motionEvent.y > screenRealHeight - origBarHeight
                }

            if (isNavigationBarArea) {
                if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN) {
                    if (!mIsInHome || mHomeHandle.alpha != 0f) {
                        opacityHomeHandle(HomeHandleState.PRESSED)
                        animateHomeHandleXYToNormal(HomeHandleState.PRESSED)
                    }

                    baseX = motionEvent.x
                    baseY = motionEvent.y
                    motionTriggered = true
                }
                if (motionEvent.actionMasked == MotionEvent.ACTION_MOVE && motionTriggered && orientation == 0) {
                    val offsetNeeded = -(baseY - motionEvent.y) * 0.15f + yOffset
                    if (abs(offsetNeeded) < barHeight / 2 - 6) {
                        mHomeHandle.translationY = offsetNeeded
                    }
                }
            }

            if (motionEvent.actionMasked == MotionEvent.ACTION_MOVE && motionTriggered) {
                cancelHomeHandleXYAnim()
                if (orientation != 0) {
                    // 横屏保留一点位移效果而不是完全固定
                    val offsetNeeded = -(baseY - motionEvent.y) * 0.02f + yOffset
                    if (abs(offsetNeeded) < barHeight / 2 + 6) {
                        mHomeHandle.translationY = offsetNeeded
                    }
                }
                mHomeHandle.translationX = -(baseX - motionEvent.x) * 0.2f
            }

            if (motionEvent.actionMasked == MotionEvent.ACTION_UP && motionTriggered) {
                val eventType = if (!mIsInHome) HomeHandleState.NORMAL else HomeHandleState.HOME
                opacityHomeHandle(eventType)
                animateHomeHandleXYToNormal(eventType)
                motionTriggered = false
            }
            motionEvent.recycle()
        } ?: motionEvent.recycle()
    }

    private fun onUserPresent() {
        if (topActivityObserver == null) return

        val currentTopActivity = topActivityObserver!!.getObjectFieldCompat("mTopActivity") as ComponentName

        if (currentTopActivity.packageName == "com.miui.home") enterHome()
    }

    /* ----------------------------------- Hook ----------------------------------- */

    @SuppressLint("DiscouragedApi")
    private fun hookNavigationBarInflaterView() {
        val clazz = Class.forName(NAVIGATION_BAR_INFLATER_VIEW, false, classLoader)

        if (!isAboveU) {
            module.hook(
                clazz.getDeclaredMethod(
                    "createView",
                    String::class.java,
                    ViewGroup::class.java,
                    android.view.LayoutInflater::class.java
                )
            ).intercept { chain ->
                val result = chain.proceed()
                val str = chain.getArg(0) as String
                if ("home_handle" == extractButton(str)) {
                    mHomeHandleId = (result as View).id
                }
                result
            }
        }

        module.hook(clazz.getDeclaredConstructor(Context::class.java, android.util.AttributeSet::class.java))
            .intercept { chain ->
                chain.proceed()
                mContext = chain.getArg(0) as Context
                mHandler = Handler(Looper.getMainLooper())

                ChenInputEventDispatcher.init(mContext!!, classLoader)
                updateSettings(true)

                if (ChenInputEventDispatcher.isInited()) {
                    ChenInputEventDispatcher.registerInputEventListener { onInputEvent(it) }
                    module.log(Log.INFO, MainHook.TAG, "ChenInputEventDispatcher registered!")
                }

                // 注册桌面模糊状态变化监听
                val eventListener = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        when (intent?.action) {
                            Intent.ACTION_USER_PRESENT -> onUserPresent()
                            "chen.action.home.blur.state.switched" ->
                                onHomeBlurChanged(intent.getBooleanExtra("active", false))
                        }
                    }
                }

                val eventFilter = IntentFilter("chen.action.home.blur.state.switched")
                eventFilter.addAction(Intent.ACTION_USER_PRESENT)
                mContext!!.registerReceiver(eventListener, eventFilter)
                null
            }

        module.hook(clazz.getDeclaredMethod("inflateLayout", String::class.java)).intercept { chain ->
            val result = chain.proceed()
            // 初始化小白条状态
            val mHorizontal = chain.thisObject.getObjectFieldCompat("mHorizontal") as FrameLayout
            if (isAboveU && mHomeHandleId == -1) {
                mHomeHandleId = mContext!!.resources.getIdentifier("home_handle", "id", mContext!!.packageName)
            }

            mHomeHandle = mHorizontal.findViewById(mHomeHandleId)

            if (chain.thisObject.getIntFieldCompat("mNavBarMode") != 2) {
                // 不支持，使用 stub view 防止崩溃
                mHomeHandle = TextView(mContext)
                Toast.makeText(
                    mContext,
                    "MIUI Extra: Please disable Home Handle Anim on Virtual Key Mode !! * 3",
                    Toast.LENGTH_LONG
                ).show()
            }

            // 动画到预设值
            if (useMiBlur && mHomeHandle.isSupportMiBlur()) {
                // 模糊模式
                mHomeHandle.alpha = if (mIsInHome) 0f else 1f
            }

            updateSettings(true)

            // 异步执行
            mHandler?.post {
                opacityHomeHandle(HomeHandleState.NORMAL)
                animateHomeHandleXYToNormal(HomeHandleState.NORMAL)
                animateZoomTo(
                    normalSettings.scaleX,
                    normalSettings.scaleY,
                    normalSettings.scaleAnimDuration
                )
            }
            result
        }
    }

    private fun hookNavigationBar() {
        val clazz = Class.forName(NAVIGATION_BAR, false, classLoader)

        module.hook(clazz.getDeclaredMethod("getBarLayoutParamsForRotation", java.lang.Integer.TYPE))
            .intercept { chain ->
                val result = chain.proceed() as WindowManager.LayoutParams
                val orientationFor = chain.getArg(0) as Int

                if (chain.thisObject.getIntFieldCompat("mNavBarMode") != 2) {
                    if (origBarHeight > 0) {
                        result.height = origBarHeight
                    }
                    return@intercept result
                }

                if (orientationFor == 0) {
                    origBarHeight = result.height
                }

                lateinit var inset: Insets
                lateinit var insets: Array<Insets>
                lateinit var insetsProviderArr: Array<*>
                lateinit var insetsProviderOverride: Any
                if (!isAboveU) {
                    @Suppress("UNCHECKED_CAST")
                    insets = getFieldValue(result, "providedInternalInsets") as Array<Insets>
                    inset = insets[1]
                } else {
                    // U 以上
                    insetsProviderArr = getFieldValue(result, "providedInsets") as Array<*>
                    insetsProviderOverride = insetsProviderArr[0]!!
                    inset = findMethodCompat(insetsProviderOverride.javaClass, "getInsetsSize")
                        .invoke(insetsProviderOverride) as Insets
                }

                updateSettings()
                if (noSpace && orientationFor == 0) {
                    inset = if (isAboveU) {
                        Insets.of(inset.left, 0, inset.right, 0)
                    } else {
                        Insets.of(inset.left, result.height, inset.right, 0)
                    }
                }
                if (orientationFor == 0) {
                    barHeight = result.height
                }

                // 应用 Inset
                if (!isAboveU) {
                    insets[1] = inset
                    setFieldValue(result, "providedInternalInsets", insets)
                } else {
                    findMethodCompat(insetsProviderOverride.javaClass, "setInsetsSize", Insets::class.java)
                        .invoke(insetsProviderOverride, inset)
                    setFieldValue(result, "providedInsets", insetsProviderArr)
                }
                module.log(
                    Log.INFO,
                    MainHook.TAG,
                    "screenHeight is ${getScreenHeight(mContext!!)}, realScreenHeight is ${
                        getScreenRealHeight(mContext!!)
                    }, origBarHeight $origBarHeight, currentBarHeight $barHeight"
                )
                result
            }

        module.hook(clazz.getDeclaredMethod("repositionNavigationBar", java.lang.Integer.TYPE))
            .intercept { chain ->
                orientation = chain.getArg(0) as Int
                val mode = if (orientation == 0) 1 else 0

                if (useMiBlur) {
                    mHomeHandle.setMiBackgroundBlurModeCompat(mode)
                }
                chain.proceed()
            }
    }

    private fun hookTopActivityChanged() {
        val activityChangedNotify = if (!isAboveU) {
            Class.forName(MIUI_ACTIVITY_UTIL, false, classLoader).getDeclaredMethod("notifyListeners")
        } else {
            // U 以上
            val clazz = Class.forName(MIUI_TOP_ACTIVITY_OBSERVER, false, classLoader)
            clazz.declaredMethods.first { it.name == "onTaskMovedToFront" && it.parameterCount == 1 }
        }

        var mLastTopActivity: ComponentName? = null
        var mLastInSmallWindow = false

        module.hook(activityChangedNotify).intercept { chain ->
            val result = chain.proceed()
            if (topActivityObserver == null) topActivityObserver = chain.thisObject

            val currentTopActivity =
                chain.thisObject.getObjectFieldCompat("mTopActivity") as ComponentName

            val mInSmallWindow = chain.thisObject.getObjectFieldCompat("mInSmallWindow") as? Boolean == true

            if (mLastInSmallWindow == mInSmallWindow && mLastTopActivity == currentTopActivity) {
                return@intercept result
            }
            mLastInSmallWindow = mInSmallWindow
            mLastTopActivity = currentTopActivity

            val intent = Intent("chen.action.top_activity.switched")
            intent.`package` = "com.miui.home"
            val mUtilsContext = chain.thisObject.getObjectFieldCompat("mContext") as Context

            if (currentTopActivity.packageName == "com.miui.home") {
                intent.putExtra("isEnteredHome", true)
                mIsInHome = true
                enterHome()
            } else {
                intent.putExtra("isEnteredHome", false)
                mIsInHome = false
                leaveHome()
            }
            mUtilsContext.sendBroadcast(intent)
            result
        }
    }

    private fun hookNavigationHandle() {
        val clazz = Class.forName(NAVIGATION_HANDLE, false, classLoader)
        module.hook(clazz.getDeclaredMethod("setDarkIntensity", java.lang.Float.TYPE))
            .intercept { chain ->
                if (mDarkColor == -1) {
                    mDarkColor = chain.thisObject.getIntFieldCompat("mDarkColor")
                    mLightColor = chain.thisObject.getIntFieldCompat("mLightColor")
                } else {
                    chain.thisObject.setIntFieldCompat("mDarkColor", mDarkColor)
                    chain.thisObject.setIntFieldCompat("mLightColor", mLightColor)
                }
                chain.proceed()
            }
    }

    private fun hookBarTransitions() {
        updateSettings(true)
        if (!noSpace) return

        val statusBarCls = Class.forName(PHONE_STATUS_BAR_TRANSITIONS, false, classLoader)
        val barTransitionsCls = Class.forName(BAR_TRANSITIONS, false, classLoader)
        val applyModeBackground = barTransitionsCls.declaredMethods.first {
            it.name == "applyModeBackground" && it.parameterCount == 2
        }

        module.hook(applyModeBackground).intercept { chain ->
            // 忽略状态栏过渡
            if (statusBarCls.isInstance(chain.thisObject)) {
                return@intercept chain.proceed()
            }

            val mode = chain.getArg(0) as Int
            if (mode in 1..4) {
                chain.proceed(arrayOf(0, chain.getArg(1)))
            } else {
                chain.proceed()
            }
        }
    }

    /* ---------------------------------- 反射辅助 ---------------------------------- */

    private fun findFieldCompat(clazz: Class<*>, name: String): Field {
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                return c.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        throw NoSuchFieldException("${clazz.name}#$name")
    }

    private fun findMethodCompat(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): java.lang.reflect.Method {
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

    private fun getFieldValue(obj: Any, name: String): Any? =
        findFieldCompat(obj.javaClass, name).get(obj)

    private fun setFieldValue(obj: Any, name: String, value: Any?) {
        findFieldCompat(obj.javaClass, name).set(obj, value)
    }

    private fun Any.getObjectFieldCompat(name: String): Any? = getFieldValue(this, name)

    private fun Any.getIntFieldCompat(name: String): Int =
        findFieldCompat(javaClass, name).getInt(this)

    private fun Any.setIntFieldCompat(name: String, value: Int) {
        findFieldCompat(javaClass, name).setInt(this, value)
    }

    /* ---------------------------------- 屏幕信息 ---------------------------------- */

    private fun getScreenHeight(context: Context): Int {
        return context.resources?.displayMetrics?.heightPixels ?: 0
    }

    private var sRealSizes = arrayOfNulls<Point>(2)

    private fun getScreenRealHeight(context: Context): Int {
        var orientation = context.resources?.configuration?.orientation
        orientation = if (orientation == 1) 0 else 1
        if (sRealSizes[orientation] == null) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val point = Point()
            @Suppress("DEPRECATION")
            display.getRealSize(point)
            sRealSizes[orientation] = point
        }
        return sRealSizes[orientation]?.y ?: getScreenRealHeight(context)
    }

    private fun extractButton(str: String): String {
        return if (!str.contains("[")) str else str.substring(0, str.indexOf("["))
    }
}

/** ValueAnimator 结束回调（替代 androidx.core.animation.doOnEnd） */
private inline fun ValueAnimator.doOnEnd(crossinline block: (Animator) -> Unit) {
    addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            block(animation)
        }
    })
}
