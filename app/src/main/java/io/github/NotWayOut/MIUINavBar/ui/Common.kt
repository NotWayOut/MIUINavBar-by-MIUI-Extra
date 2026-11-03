package io.github.NotWayOut.MIUINavBar.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.NotWayOut.MIUINavBar.App
import io.github.NotWayOut.MIUINavBar.R
import io.github.NotWayOut.MIUINavBar.config.PREFS_GROUP
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * 界面使用的配置存储。
 *
 * 与 localConfig 一致：框架已绑定时使用 XposedService 远程首选项（Hook 侧可见），
 * 否则退化为应用私有文件；服务绑定状态变化时自动重新解析。
 *
 * 同时监听配置变化并自增 [PrefsState] 版本号，使恢复备份等外部写入后界面自动刷新。
 */
@Composable
fun rememberSharedPreferences(): SharedPreferences {
    val context = LocalContext.current
    val service = App.serviceState.value
    val prefs = remember(context, service) {
        service?.getRemotePreferences(PREFS_GROUP)
            ?: context.getSharedPreferences(PREFS_GROUP, Context.MODE_PRIVATE)
    }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            PrefsState.notifyChanged()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return prefs
}

/** 配置状态：版本号在配置变化时自增，供依赖配置的界面刷新 */
object PrefsState {
    val version = mutableIntStateOf(0)

    /** 配置发生变化（如恢复备份）后调用，通知界面刷新 */
    fun notifyChanged() {
        version.intValue += 1
    }
}

/** 记录当前打开的弹窗，使返回键优先关闭弹窗而非返回上一页 */
object DialogDismissRegistry {
    private val dismissers = mutableListOf<() -> Unit>()
    var openCount by mutableIntStateOf(0)
        private set

    val hasOpenDialog: Boolean get() = openCount > 0

    fun register(dismiss: () -> Unit): () -> Unit {
        dismissers.add(dismiss)
        openCount = dismissers.size
        return {
            dismissers.remove(dismiss)
            openCount = dismissers.size
        }
    }

    fun dismissTop() {
        dismissers.lastOrNull()?.invoke()
    }
}

/** 弹窗打开期间注册返回键关闭回调 */
@Composable
fun RegisterDialogDismiss(show: Boolean, onDismiss: () -> Unit) {
    if (show) {
        DisposableEffect(show) {
            val unregister = DialogDismissRegistry.register(onDismiss)
            onDispose { unregister() }
        }
    }
}

/** 绑定布尔配置的开关行，变更即时写入 SharedPreferences */
@Composable
fun SwitchRow(
    key: String,
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    defaultValue: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    val prefs = rememberSharedPreferences()
    val version = PrefsState.version.value
    var checked by remember(prefs, key, version) {
        mutableStateOf(prefs.getBoolean(key, defaultValue))
    }
    SwitchPreference(
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = {
            checked = it
            prefs.edit().putBoolean(key, it).apply()
            onCheckedChange?.invoke(it)
        },
        enabled = enabled,
    )
}

/** 绑定整型配置的滑块行，标题下方显示当前值与单位，点按可精确输入 */
@Composable
fun SliderRow(
    key: String,
    title: String,
    defaultValue: Int,
    min: Int,
    max: Int,
    unit: String,
) {
    val prefs = rememberSharedPreferences()
    val version = PrefsState.version.value
    var value by remember(prefs, key, version) {
        mutableIntStateOf(prefs.getInt(key, defaultValue))
    }
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = true) { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$value $unit",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { value = it.roundToInt() },
            onValueChangeFinished = { prefs.edit().putInt(key, value).apply() },
            modifier = Modifier.padding(top = 4.dp),
            valueRange = min.toFloat()..max.toFloat(),
        )
    }

    if (showDialog) {
        InputDialog(
            title = title,
            initialValue = value.toString(),
            onDismiss = { showDialog = false },
            onConfirm = { text ->
                val input = text.toIntOrNull()
                if (input != null && input in min..max) {
                    value = input
                    prefs.edit().putInt(key, input).apply()
                }
                showDialog = false
            },
        )
    }
}

/** 箭头跳转行 */
@Composable
fun ArrowRow(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        summary = summary,
        onClick = onClick,
        enabled = enabled,
    )
}

/** 文本输入对话框 */
@Composable
fun InputDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    OverlayDialog(
        title = title,
        show = true,
        onDismissRequest = onDismiss,
    ) {
        RegisterDialogDismiss(show = true, onDismiss = onDismiss)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = title,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        onConfirm(text)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(text = stringResource(R.string.ok))
                }
            }
        }
    }
}

/** libxposed API 101 为模块所需的最低框架 API 版本 */
private const val REQUIRED_API = 101

/** 框架名称未知时的回退值 */
private const val DEFAULT_FRAMEWORK_NAME = "LSPosed"

private enum class FrameworkStatus { Ok, Unsupported, Inactive, NoRoot }

private data class StatusColors(val background: Color, val content: Color)

/** 主页顶部框架状态卡片：展示模块激活状态、框架 API 版本与 Root 权限 */
@Composable
fun FrameworkStatusCard() {
    val service = App.serviceState.value
    val frameworkName = remember(service) {
        runCatching { service?.frameworkName }.getOrNull() ?: DEFAULT_FRAMEWORK_NAME
    }
    val apiVersion = remember(service) { runCatching { service?.apiVersion }.getOrNull() }
    var rootGranted by remember(service) { mutableStateOf<Boolean?>(null) }

    // 仅在模块已激活且框架版本满足要求时检测 Root，避免无谓的授权弹窗
    LaunchedEffect(service, apiVersion) {
        if (service != null && apiVersion != null && apiVersion >= REQUIRED_API) {
            rootGranted = withContext(Dispatchers.IO) { checkRoot() }
        }
    }

    val status = when {
        service == null -> FrameworkStatus.Inactive
        apiVersion == null || apiVersion < REQUIRED_API -> FrameworkStatus.Unsupported
        rootGranted == false -> FrameworkStatus.NoRoot
        else -> FrameworkStatus.Ok
    }
    val colors = statusColors(status)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .background(colors.background)
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        when (status) {
                            FrameworkStatus.Ok -> R.string.framework_ok
                            FrameworkStatus.Unsupported -> R.string.framework_unsupported
                            FrameworkStatus.Inactive -> R.string.framework_inactive
                            FrameworkStatus.NoRoot -> R.string.framework_no_root
                        }
                    ),
                    color = colors.content,
                    style = MiuixTheme.textStyles.title1.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = stringResource(
                        when (status) {
                            FrameworkStatus.Ok -> R.string.framework_ok_tips
                            FrameworkStatus.Unsupported -> R.string.framework_unsupported_tips
                            FrameworkStatus.Inactive -> R.string.framework_inactive_tips
                            FrameworkStatus.NoRoot -> R.string.framework_no_root_tips
                        },
                        frameworkName,
                        REQUIRED_API,
                    ),
                    color = colors.content,
                    style = MiuixTheme.textStyles.body2,
                )
            }
            Icon(
                imageVector = when (status) {
                    FrameworkStatus.Ok -> MiuixIcons.Ok
                    FrameworkStatus.NoRoot -> MiuixIcons.Lock
                    else -> MiuixIcons.Close
                },
                contentDescription = null,
                tint = colors.content,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

/** 通过 su 检测 Root 权限（在 IO 线程执行） */
private fun checkRoot(): Boolean = runCatching {
    val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor()
    process.exitValue() == 0 && output.contains("uid=0")
}.getOrDefault(false)

@Composable
private fun statusColors(status: FrameworkStatus): StatusColors {
    val isDark = MiuixTheme.colorScheme.background.luminanceIsDark()
    return when (status) {
        FrameworkStatus.Ok -> if (isDark) {
            StatusColors(Color(0xFF1B3B24), Color(0xFFA5D6A7))
        } else {
            StatusColors(Color(0xFFE1F2E5), Color(0xFF1B5E20))
        }

        FrameworkStatus.NoRoot -> if (isDark) {
            StatusColors(Color(0xFF3A2F14), Color(0xFFFFE082))
        } else {
            StatusColors(Color(0xFFFFF8E1), Color(0xFF8D6E00))
        }

        else -> if (isDark) {
            StatusColors(Color(0xFF3E1B1B), Color(0xFFEF9A9A))
        } else {
            StatusColors(Color(0xFFFFEBEE), Color(0xFFB71C1C))
        }
    }
}

private fun Color.luminanceIsDark(): Boolean = (red * 0.299f + green * 0.587f + blue * 0.114f) < 0.5f

/**
 * 通用页面骨架：顶部标题栏 + 可滚动内容。
 *
 * [showModuleName] 为 true 时左上角显示放大的模块名（HyperEars / InstallerX 风格，
 * 大小参考 Fuck Home），否则显示带返回键的页面标题。
 *
 * [overlay] 用于放置 OverlayDialog 等弹窗组件——miuix 的弹窗依赖 Scaffold 的 popupHost，
 * 必须声明在 Scaffold 内部（即本槽位中）才能正常弹出。
 */
@Composable
fun SettingsScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    showModuleName: Boolean = false,
    overlay: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            if (showModuleName) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MiuixTheme.textStyles.title1.copy(fontSize = 30.sp),
                    )
                }
            } else {
                SmallTopAppBar(
                    title = title,
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = stringResource(R.string.cancel),
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
            overlay()
        }
    }
}
