package io.github.NotWayOut.MIUINavBar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.NotWayOut.MIUINavBar.R
import io.github.NotWayOut.MIUINavBar.config.KEY_HIDE_LAUNCHER_ICON
import io.github.NotWayOut.MIUINavBar.config.KEY_HOME_HANDLE_ANIM
import io.github.NotWayOut.MIUINavBar.config.KEY_HOME_HANDLE_BLUR_EFFECT
import io.github.NotWayOut.MIUINavBar.config.KEY_HOME_HANDLE_DISABLE_MOVE
import io.github.NotWayOut.MIUINavBar.config.KEY_HOME_HANDLE_NO_SPACE
import io.github.NotWayOut.MIUINavBar.utils.BackupUtils
import top.yukonga.miuix.kmp.basic.Card

/**
 * 小白条与手势栏主设置页。
 */
@Composable
fun HomeHandleScreen(
    onNavigateToCustomAnim: () -> Unit,
    onHideIconChange: (Boolean) -> Unit,
    onRestartSystemUi: () -> Unit,
    onCreateBackupDocument: (String) -> Unit,
    onPickDocument: () -> Unit,
) {
    val prefs = rememberSharedPreferences()
    val version = PrefsState.version.value
    var masterEnabled by remember(prefs, version) {
        mutableStateOf(prefs.getBoolean(KEY_HOME_HANDLE_ANIM, false))
    }

    SettingsScaffold(
        title = stringResource(R.string.home_handle_header),
        showModuleName = true,
    ) {
        item {
            FrameworkStatusCard()
        }
        // 总开关
        item {
            Card {
                SwitchRow(
                    key = KEY_HOME_HANDLE_ANIM,
                    title = stringResource(R.string.chen_home_handle_anim_title),
                    defaultValue = false,
                    onCheckedChange = { masterEnabled = it },
                )
            }
        }
        // 小白条功能
        item {
            Card {
                SwitchRow(
                    key = KEY_HOME_HANDLE_NO_SPACE,
                    title = stringResource(R.string.chen_home_handle_no_space_title),
                    summary = stringResource(R.string.chen_home_handle_no_space_summary),
                    enabled = masterEnabled,
                )
                ArrowRow(
                    title = stringResource(R.string.home_handle_anim_custom_title),
                    enabled = masterEnabled,
                    onClick = onNavigateToCustomAnim,
                )
                SwitchRow(
                    key = KEY_HOME_HANDLE_BLUR_EFFECT,
                    title = stringResource(R.string.chen_home_handle_blur_effect_title),
                    enabled = masterEnabled,
                )
                SwitchRow(
                    key = KEY_HOME_HANDLE_DISABLE_MOVE,
                    title = stringResource(R.string.home_handle_disable_move_event_title),
                    enabled = masterEnabled,
                )
            }
        }
        // 模块操作
        item {
            Card {
                SwitchRow(
                    key = KEY_HIDE_LAUNCHER_ICON,
                    title = stringResource(R.string.hide_launcher_icon),
                    onCheckedChange = onHideIconChange,
                )
                ArrowRow(
                    title = stringResource(R.string.restart_systemui),
                    summary = stringResource(R.string.need_restart_systemui),
                    onClick = onRestartSystemUi,
                )
                ArrowRow(
                    title = stringResource(R.string.backup),
                    onClick = {
                        onCreateBackupDocument(BackupUtils.backupFileName())
                    },
                )
                ArrowRow(
                    title = stringResource(R.string.restore),
                    onClick = onPickDocument,
                )
            }
        }
    }
}
