package app.onlyshots.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.onlyshots.PRIVACY_URL
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.CgIcon
import app.onlyshots.core.designsystem.component.HeaderBack
import app.onlyshots.core.designsystem.component.LocalNativeScreen
import app.onlyshots.core.model.AppVersion
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_version
import app.onlyshots.core.resources.home_about_text
import app.onlyshots.core.resources.home_help_machines
import app.onlyshots.core.resources.home_help_machines_note
import app.onlyshots.core.resources.home_help_scales_note
import app.onlyshots.core.resources.home_privacy
import app.onlyshots.core.resources.home_scales_title
import app.onlyshots.edit.machineColor
import app.onlyshots.sources.machine.NetworkMachines
import org.jetbrains.compose.resources.stringResource

/**
 * Behind Start's ? button: the machines and scales OnlyShots knows, how they are found, and the privacy policy and
 * where to get help (guidelines 5.1.1 and 1.5). Stateless, so the screenshot tests can draw it outside its sheet.
 */
@Composable
fun HelpContent(modifier: Modifier = Modifier, onPrivacy: (() -> Unit)? = null, version: String = AppVersion.NAME) {
    val uri = LocalUriHandler.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CgSpace.S24)) {
        Text(
            stringResource(Res.string.home_about_text),
            style = CgTheme.type.body.copy(lineHeight = CgTheme.type.body.fontSize * 1.55f),
        )
        HelpSection(stringResource(Res.string.home_help_machines), stringResource(Res.string.home_help_machines_note)) {
            for (machine in NetworkMachines.all) {
                Row(
                    Modifier.fillMaxWidth().border(CgStroke.Hairline, CgColors.Line.Hairline).padding(CgSpace.S12),
                    horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(width = CgSpace.S4, height = CgSpace.S24).background(machineColor(machine.kind)))
                    Text(machine.name.caps(), style = CgTheme.type.label)
                }
            }
        }
        HelpSection(stringResource(Res.string.home_scales_title), stringResource(Res.string.home_help_scales_note)) {
            Text(
                SCALE_BRANDS.caps(),
                style = CgTheme.type.data.copy(lineHeight = CgTheme.type.data.fontSize * 1.6f),
                modifier =
                    Modifier.fillMaxWidth().border(CgStroke.Hairline, CgColors.Line.Hairline).padding(CgSpace.S12),
            )
        }
        Column {
            Box(Modifier.fillMaxWidth().height(CgStroke.Hairline).background(CgColors.Line.Hairline))
            Row(Modifier.fillMaxWidth().padding(top = CgSpace.S8), verticalAlignment = Alignment.CenterVertically) {
                // The app's own privacy screen where the host has one; the published page otherwise. Either ends with
                // where to get help, so one link serves both.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    FooterLink(
                        stringResource(Res.string.home_privacy),
                        modifier = Modifier.testTag(TestTags.HelpPrivacy),
                    ) {
                        onPrivacy?.invoke() ?: uri.openUri(PRIVACY_URL)
                    }
                    CgIcon(
                        CgIcons.ArrowRight,
                        tint = CgColors.Text.Primary,
                        size = 14.dp,
                        modifier = Modifier.padding(start = CgSpace.S6),
                    )
                }
                Text(
                    stringResource(Res.string.core_version, version),
                    style = CgTheme.type.data,
                    color = CgColors.Text.Secondary,
                )
            }
        }
    }
}

@Composable
private fun HelpSection(title: String, note: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
        SectionLabel(title)
        content()
        Text(note, style = CgTheme.type.bodySmall, color = CgColors.Text.Secondary)
    }
}

/** The help as a page of its own, under the screen's back key; it hands the native bar a back key of its own. */
@Composable
internal fun HelpPage(title: String, onClose: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None)) { onClose() }
    CompositionLocalProvider(LocalNativeScreen provides remember { Any() }) {
        Column(modifier) {
            HeaderBack(title, onBack = onClose, modifier = Modifier.padding(vertical = CgSpace.S8))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(CgSpace.S16)) { content() }
        }
    }
}
