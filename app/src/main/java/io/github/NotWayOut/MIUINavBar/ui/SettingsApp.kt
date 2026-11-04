package io.github.NotWayOut.MIUINavBar.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 设置界面需要由 Activity 提供的能力 */
data class SettingsActions(
    val onCreateBackupDocument: (String) -> Unit,
    val onPickDocument: () -> Unit,
    val onHideIconChange: (Boolean) -> Unit,
    val onRestartSystemUi: () -> Unit,
)

private enum class SettingsScreen { Main, CustomAnim, About }

@Composable
fun SettingsApp(actions: SettingsActions) {
    val isDark = isSystemInDarkTheme()
    val controller = remember(isDark) {
        ThemeController(colorSchemeMode = ColorSchemeMode.System, isDark = isDark)
    }

    // 状态栏图标颜色随明暗主题切换
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MiuixTheme(controller = controller) {
        // miuix 的 overlay 组件依赖 NavigationEventDispatcher；当前 navigationevent 版本
        // 不会自动从 Activity 解析，这里显式提供（无父级时创建根 dispatcher）
        val activityOwner = LocalNavigationEventDispatcherOwner.current
        val dispatcherOwner = rememberNavigationEventDispatcherOwner(parent = activityOwner)
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcherOwner) {
            AppContent(actions)
        }
    }
}

@Composable
private fun AppContent(actions: SettingsActions) {
    var screen by remember { mutableStateOf(SettingsScreen.Main) }
    val onBack: (() -> Unit)? = if (screen == SettingsScreen.Main) null else ({ screen = SettingsScreen.Main })
    BackHandler(enabled = screen != SettingsScreen.Main || DialogDismissRegistry.hasOpenDialog) {
        // 返回键优先关闭已打开的弹窗，其次才返回主页
        if (DialogDismissRegistry.hasOpenDialog) {
            DialogDismissRegistry.dismissTop()
        } else {
            screen = SettingsScreen.Main
        }
    }
    AnimatedContent(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
        targetState = screen,
        transitionSpec = {
            if (targetState.ordinal > initialState.ordinal) {
                (slideInHorizontally { width -> width / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { width -> -width / 4 } + fadeOut())
            } else {
                (slideInHorizontally { width -> -width / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { width -> width / 4 } + fadeOut())
            }
        },
        label = "settings-page-transition",
    ) { current ->
        // 动画期间退场页面仍处于组合中，需阻断其触摸，避免点到上一个页面的控件
        val interactive = current == screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (interactive) {
                        Modifier
                    } else {
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                        .changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                ),
        ) {
            when (current) {
                SettingsScreen.Main -> HomeHandleScreen(
                    onNavigateToCustomAnim = { screen = SettingsScreen.CustomAnim },
                    onNavigateToAbout = { screen = SettingsScreen.About },
                    onHideIconChange = actions.onHideIconChange,
                    onRestartSystemUi = actions.onRestartSystemUi,
                    onCreateBackupDocument = actions.onCreateBackupDocument,
                    onPickDocument = actions.onPickDocument,
                )

                SettingsScreen.CustomAnim -> CustomAnimScreen(onBack = { screen = SettingsScreen.Main })

                SettingsScreen.About -> AboutScreen(onBack = { screen = SettingsScreen.Main })
            }
        }
    }
}
