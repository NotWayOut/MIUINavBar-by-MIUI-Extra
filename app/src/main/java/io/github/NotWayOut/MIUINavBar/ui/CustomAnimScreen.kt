package io.github.NotWayOut.MIUINavBar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.NotWayOut.MIUINavBar.R
import io.github.NotWayOut.MIUINavBar.config.HomeHandleState
import io.github.NotWayOut.MIUINavBar.config.KEY_HOME_HANDLE_FORCE_ALPHA_AT_HOME
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle

/**
 * 自定义小白条动画页：默认时 / 按下时 / 在桌面时 三组参数。
 */
@Composable
fun CustomAnimScreen(onBack: () -> Unit) {
    SettingsScaffold(title = stringResource(R.string.home_handle_anim_custom_title), onBack = onBack) {
        for (state in HomeHandleState.entries) {
            item(key = "title_${state.suffix}") {
                SmallTitle(
                    text = stringResource(
                        when (state) {
                            HomeHandleState.NORMAL -> R.string.normal_state_title
                            HomeHandleState.PRESSED -> R.string.pressed_state_title
                            HomeHandleState.HOME -> R.string.home_state_title
                        }
                    )
                )
            }
            item(key = "card_${state.suffix}") {
                Card {
                    SliderRow(
                        key = "home_handle_setting_transdegree_${state.suffix}",
                        title = stringResource(R.string.home_handle_auto_trans_alphaval_title),
                        defaultValue = 30,
                        min = 0,
                        max = 100,
                        unit = "%",
                    )
                    SliderRow(
                        key = "home_handle_setting_scalex_${state.suffix}",
                        title = stringResource(R.string.home_handle_scale_x_title),
                        defaultValue = 100,
                        min = 0,
                        max = 200,
                        unit = "%",
                    )
                    SliderRow(
                        key = "home_handle_setting_scaley_${state.suffix}",
                        title = stringResource(R.string.home_handle_scale_y_title),
                        defaultValue = 100,
                        min = 0,
                        max = 200,
                        unit = "%",
                    )
                    SliderRow(
                        key = "home_handle_setting_offsetx_${state.suffix}",
                        title = stringResource(R.string.home_handle_offset_x_title),
                        defaultValue = 0,
                        min = -1000,
                        max = 1000,
                        unit = "px",
                    )
                    SliderRow(
                        key = "home_handle_setting_offsety_${state.suffix}",
                        title = stringResource(R.string.home_handle_offset_y_title),
                        defaultValue = 0,
                        min = -15,
                        max = 50,
                        unit = "px",
                    )
                    SliderRow(
                        key = "home_handle_setting_duration_alpha_${state.suffix}",
                        title = stringResource(R.string.home_handle_anim_duration_alpha),
                        defaultValue = 600,
                        min = 0,
                        max = 3000,
                        unit = "ms",
                    )
                    SliderRow(
                        key = "home_handle_setting_duration_scale_${state.suffix}",
                        title = stringResource(R.string.home_handle_anim_duration_scale),
                        defaultValue = 600,
                        min = 0,
                        max = 3000,
                        unit = "ms",
                    )
                    SliderRow(
                        key = "home_handle_setting_duration_xy_${state.suffix}",
                        title = stringResource(R.string.home_handle_anim_duration_xy),
                        defaultValue = 600,
                        min = 0,
                        max = 3000,
                        unit = "ms",
                    )
                    if (state == HomeHandleState.HOME) {
                        SwitchRow(
                            key = KEY_HOME_HANDLE_FORCE_ALPHA_AT_HOME,
                            title = stringResource(R.string.home_handle_force_alpha_at_home_title),
                            summary = stringResource(R.string.home_handle_force_alpha_at_home_summary),
                        )
                    }
                }
            }
        }
    }
}
