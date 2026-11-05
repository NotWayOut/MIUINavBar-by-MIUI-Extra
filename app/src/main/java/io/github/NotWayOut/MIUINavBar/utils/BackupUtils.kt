package io.github.NotWayOut.MIUINavBar.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import io.github.NotWayOut.MIUINavBar.R
import io.github.NotWayOut.MIUINavBar.config.HomeHandleState
import io.github.NotWayOut.MIUINavBar.config.PREFS_GROUP
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader

/**
 * 配置备份与恢复。
 *
 * 备份格式与原模块一致（JSON 数组，元素为 {"name": 配置组名, "content": 键值对}），
 * 但仅识别本次迁移的小白条相关键，其余键在备份与恢复时都会被忽略。
 */
object BackupUtils {

    /** 迁移功能涉及的全部配置键 */
    val backupKeys: Set<String> = buildSet {
        add(io.github.NotWayOut.MIUINavBar.config.KEY_HOME_HANDLE_ANIM)
        add(io.github.NotWayOut.MIUINavBar.config.KEY_HOME_HANDLE_NO_SPACE)
        add(io.github.NotWayOut.MIUINavBar.config.KEY_HOME_HANDLE_BLUR_EFFECT)
        add(io.github.NotWayOut.MIUINavBar.config.KEY_HOME_HANDLE_FORCE_ALPHA_AT_HOME)
        for (state in HomeHandleState.entries) {
            add("home_handle_setting_transdegree_${state.suffix}")
            add("home_handle_setting_scalex_${state.suffix}")
            add("home_handle_setting_scaley_${state.suffix}")
            add("home_handle_setting_offsetx_${state.suffix}")
            add("home_handle_setting_offsety_${state.suffix}")
            add("home_handle_setting_duration_alpha_${state.suffix}")
            add("home_handle_setting_duration_scale_${state.suffix}")
            add("home_handle_setting_duration_xy_${state.suffix}")
        }
    }

    fun backupFileName(): String = "MIUINavBar_configs_${System.currentTimeMillis()}.json"

    /** 备份文件后缀：导出与导入均只接受该后缀 */
    private const val BACKUP_FILE_SUFFIX = ".json"

    /** 读取文档显示名，用于后缀校验 */
    private fun getDisplayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()

    /** @return true 表示备份成功 */
    fun handleCreateDocument(context: Context, prefs: SharedPreferences, uri: Uri): Boolean {
        return runCatching {
            val content = JSONObject()
            for (key in backupKeys) {
                if (!prefs.contains(key)) continue
                when (val value = prefs.all[key]) {
                    is Boolean -> content.put(key, value)
                    is Int -> content.put(key, value)
                    is Long -> content.put(key, value)
                    is Float -> content.put(key, value)
                    is String -> content.put(key, value)
                    is Set<*> -> content.put(key, JSONArray(value.toList()))
                }
            }
            val rootArray = JSONArray().put(JSONObject().put("name", PREFS_GROUP).put("content", content))
            context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { it.write(rootArray.toString().toByteArray()) }
            }
            Toast.makeText(context, R.string.backup_success, Toast.LENGTH_SHORT).show()
            true
        }.getOrElse {
            Toast.makeText(context, R.string.backup_failed, Toast.LENGTH_LONG).show()
            false
        }
    }

    /** @return true 表示恢复成功 */
    fun handleReadDocument(context: Context, prefs: SharedPreferences, uri: Uri): Boolean {
        // 按文件后缀过滤，只接受 .json 配置文件
        val displayName = getDisplayName(context, uri)
        if (displayName != null && !displayName.endsWith(BACKUP_FILE_SUFFIX, ignoreCase = true)) {
            Toast.makeText(context, R.string.restore_invalid_file, Toast.LENGTH_LONG).show()
            return false
        }

        return runCatching {
            val stringBuilder = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stringBuilder.append(line)
                        line = reader.readLine()
                    }
                }
            }

            val jsonArray = JSONArray(stringBuilder.toString())
            var restored = 0
            val editor = prefs.edit()
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                if (jsonObj.optString("name") != PREFS_GROUP) continue
                val content = jsonObj.optJSONObject("content") ?: continue
                val keys = content.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key !in backupKeys) continue
                    when (val value = content.get(key)) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Double -> editor.putFloat(key, value.toFloat())
                        is Float -> editor.putFloat(key, value)
                        is String -> editor.putString(key, value)
                        is JSONArray -> {
                            val set = mutableSetOf<String>()
                            for (j in 0 until value.length()) set.add(value.getString(j))
                            editor.putStringSet(key, set)
                        }
                    }
                    restored++
                }
            }
            editor.apply()
            if (restored == 0) throw IllegalStateException("no recognizable key found")
            Toast.makeText(context, R.string.restore_success, Toast.LENGTH_SHORT).show()
            true
        }.getOrElse {
            Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_LONG).show()
            false
        }
    }
}
