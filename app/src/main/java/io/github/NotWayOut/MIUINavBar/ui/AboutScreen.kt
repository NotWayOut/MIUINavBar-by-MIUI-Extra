package io.github.NotWayOut.MIUINavBar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.NotWayOut.MIUINavBar.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val REPO_URL = "https://github.com/NotWayOut/MIUINavBar-by-MIUI-Extra"

/**
 * 关于模块页：布局参考 Hyper Helper，内容取自项目 README。
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    SettingsScaffold(title = stringResource(R.string.about_title), onBack = onBack) {
        item {
            Card {
                AboutIconRow(
                    title = stringResource(R.string.about_use_miuix),
                    summary = stringResource(R.string.about_use_miuix_summary),
                    icon = MiuixIcons.Info,
                    uriHandler = uriHandler,
                    url = "https://github.com/compose-miuix-ui/miuix",
                )
                AboutIconRow(
                    title = stringResource(R.string.about_use_libxposed),
                    summary = stringResource(R.string.about_use_libxposed_summary),
                    icon = MiuixIcons.Info,
                    uriHandler = uriHandler,
                    url = "https://github.com/libxposed",
                )
                AboutIconRow(
                    title = stringResource(R.string.about_use_upstream),
                    summary = stringResource(R.string.about_use_upstream_summary),
                    icon = MiuixIcons.Info,
                    uriHandler = uriHandler,
                    url = "https://github.com/Art-Chen/MIUI-Extra-YukiAPI",
                )
            }
        }
        item { AboutSectionTitle(stringResource(R.string.about_section_developers)) }
        item {
            Card {
                BasicComponent(
                    title = "Art-Chen",
                    summary = stringResource(R.string.about_dev_original_summary),
                    startAction = { AvatarImage(R.drawable.avatar_artchen) },
                    onClick = { openUrl(uriHandler, "https://github.com/Art-Chen") },
                )
                BasicComponent(
                    title = "NotWayOut",
                    summary = stringResource(R.string.about_dev_self_summary),
                    startAction = { AvatarImage(R.drawable.avatar_notwayout) },
                    onClick = { openUrl(uriHandler, "https://github.com/NotWayOut") },
                )
            }
        }
        item { AboutSectionTitle(stringResource(R.string.about_section_others)) }
        item {
            Card {
                AboutIconRow(
                    title = stringResource(R.string.about_source),
                    summary = stringResource(R.string.about_source_detail),
                    icon = MiuixIcons.File,
                    uriHandler = uriHandler,
                    url = REPO_URL,
                )
                AboutIconRow(
                    title = stringResource(R.string.about_issues),
                    summary = stringResource(R.string.about_issues_detail),
                    icon = MiuixIcons.Messages,
                    uriHandler = uriHandler,
                    url = "$REPO_URL/issues",
                )
                AboutIconRow(
                    title = stringResource(R.string.about_donate),
                    summary = stringResource(R.string.about_donate_detail),
                    icon = MiuixIcons.Favorites,
                    uriHandler = uriHandler,
                    url = "https://afdian.com/a/art_chen",
                )
                AboutIconRow(
                    title = stringResource(R.string.about_license),
                    summary = stringResource(R.string.about_license_detail),
                    icon = MiuixIcons.Info,
                    uriHandler = uriHandler,
                    url = "$REPO_URL/blob/main/LICENSE",
                )
            }
        }
        item { AboutSectionTitle(stringResource(R.string.about_section_references)) }
        item {
            Card {
                AboutIconRow(
                    title = "MIUI-Extra-YukiAPI",
                    summary = "Art_Chen",
                    icon = MiuixIcons.Link,
                    uriHandler = uriHandler,
                    url = "https://github.com/Art-Chen/MIUI-Extra-YukiAPI",
                )
                AboutIconRow(
                    title = "libXposed API 101",
                    summary = "libxposed",
                    icon = MiuixIcons.Link,
                    uriHandler = uriHandler,
                    url = "https://github.com/libxposed",
                )
                AboutIconRow(
                    title = "miuix",
                    summary = "Apache-2.0",
                    icon = MiuixIcons.Link,
                    uriHandler = uriHandler,
                    url = "https://github.com/compose-miuix-ui/miuix",
                )
                AboutIconRow(
                    title = "YukiHookAPI",
                    summary = "HighCapable",
                    icon = MiuixIcons.Link,
                    uriHandler = uriHandler,
                    url = "https://github.com/HighCapable/YukiHookAPI",
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.about_copyright),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun AboutSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 2.dp),
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onBackgroundVariant,
    )
}

@Composable
private fun AboutIconRow(
    title: String,
    summary: String,
    icon: ImageVector,
    uriHandler: UriHandler,
    url: String,
) {
    BasicComponent(
        title = title,
        summary = summary,
        startAction = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
        },
        onClick = { openUrl(uriHandler, url) },
    )
}

@Composable
private fun AvatarImage(resId: Int) {
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape),
        contentScale = ContentScale.Crop,
    )
}

private fun openUrl(uriHandler: UriHandler, url: String) {
    runCatching { uriHandler.openUri(url) }
}
