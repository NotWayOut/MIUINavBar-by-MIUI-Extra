package io.github.NotWayOut.MIUINavBar.activity

import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import io.github.NotWayOut.MIUINavBar.R
import io.github.NotWayOut.MIUINavBar.config.localConfig
import io.github.NotWayOut.MIUINavBar.ui.PrefsState
import io.github.NotWayOut.MIUINavBar.ui.SettingsActions
import io.github.NotWayOut.MIUINavBar.ui.SettingsApp
import io.github.NotWayOut.MIUINavBar.utils.BackupUtils
import java.io.IOException

class SettingsActivity : ComponentActivity() {

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { BackupUtils.handleCreateDocument(this, localConfig(this), it) }
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // 恢复成功后主动通知界面刷新（远程首选项的监听不一定覆盖同实例写入）
            if (BackupUtils.handleReadDocument(this, localConfig(this), it)) {
                PrefsState.notifyChanged()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SettingsApp(
                actions = SettingsActions(
                    onCreateBackupDocument = { fileName ->
                        createDocumentLauncher.launch(fileName)
                    },
                    onPickDocument = {
                        // 仅显示 JSON 配置文件；具体后缀由 BackupUtils 校验
                        openDocumentLauncher.launch(arrayOf("application/json"))
                    },
                    onHideIconChange = { hidden -> setLauncherIconHidden(hidden) },
                    onRestartSystemUi = { restartSystemUi() },
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // 恢复界面时同步隐藏图标开关状态（其它入口也可能修改过）
        val hidden = localConfig(this).getBoolean("hide_launcher_icon", false)
        val componentName = ComponentName(this, "io.github.NotWayOut.MIUINavBar.launcher")
        val enabled = packageManager.getComponentEnabledSetting(componentName)
        val isEnabled = enabled == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        if ((enabled == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && hidden) ||
            (enabled != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && isEnabled == hidden)
        ) {
            setLauncherIconHidden(hidden)
        }
    }

    private fun setLauncherIconHidden(hidden: Boolean) {
        val componentName = ComponentName(this, "io.github.NotWayOut.MIUINavBar.launcher")
        val newState = if (hidden) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        packageManager.setComponentEnabledSetting(
            componentName,
            newState,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun restartSystemUi() {
        Thread {
            runCatching {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "killall com.android.systemui"))
                process.waitFor()
                if (process.exitValue() != 0) throw IOException("exit code ${process.exitValue()}")
            }.onFailure {
                runOnUiThread {
                    Toast.makeText(this, R.string.restart_failed, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
