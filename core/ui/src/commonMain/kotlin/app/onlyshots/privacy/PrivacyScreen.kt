package app.onlyshots.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.HeaderBack
import app.onlyshots.core.model.AppVersion
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_version
import app.onlyshots.core.resources.privacy_changes
import app.onlyshots.core.resources.privacy_changes_title
import app.onlyshots.core.resources.privacy_children
import app.onlyshots.core.resources.privacy_children_title
import app.onlyshots.core.resources.privacy_lead
import app.onlyshots.core.resources.privacy_lead_detail
import app.onlyshots.core.resources.privacy_sharing
import app.onlyshots.core.resources.privacy_sharing_title
import app.onlyshots.core.resources.privacy_stored
import app.onlyshots.core.resources.privacy_stored_title
import app.onlyshots.core.resources.privacy_support
import app.onlyshots.core.resources.privacy_support_title
import app.onlyshots.core.resources.privacy_title
import app.onlyshots.core.resources.privacy_updated
import app.onlyshots.core.resources.privacy_use_bluetooth
import app.onlyshots.core.resources.privacy_use_bluetooth_why
import app.onlyshots.core.resources.privacy_use_camera
import app.onlyshots.core.resources.privacy_use_camera_why
import app.onlyshots.core.resources.privacy_use_network
import app.onlyshots.core.resources.privacy_use_network_why
import app.onlyshots.core.resources.privacy_use_photos
import app.onlyshots.core.resources.privacy_use_photos_why
import app.onlyshots.core.resources.privacy_uses_title
import org.jetbrains.compose.resources.stringResource

/** Where problems and ideas go: the project's issues. */
const val SUPPORT_ISSUES_URL = "https://github.com/parez-dev/OnlyShots/issues"

/**
 * The privacy policy inside the app, on every platform: the same words as the published page
 * (`app/webApp/src/webMain/resources/privacy.html`, which the stores link to). Change both together.
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit, modifier: Modifier = Modifier, version: String = AppVersion.NAME) {
    val uri = LocalUriHandler.current
    Box(modifier.fillMaxSize().background(CgColors.Surface.Base).safeDrawingPadding(), Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            HeaderBack(
                stringResource(Res.string.privacy_title),
                onBack = onBack,
                modifier = Modifier.padding(vertical = CgSpace.S8),
            )
            Column(
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = CgSpace.S16, end = CgSpace.S16, top = CgSpace.S8, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(CgSpace.S16),
            ) {
                Text(stringResource(Res.string.privacy_updated), style = data, color = CgColors.Text.Secondary)
                // The one thing to know, first and in bold, as the page has it.
                val lead = stringResource(Res.string.privacy_lead)
                val leadDetail = stringResource(Res.string.privacy_lead_detail)
                Column(
                    Modifier.fillMaxWidth().border(CgStroke.Hairline, CgColors.Brand.Signal).padding(CgSpace.S12),
                    verticalArrangement = Arrangement.spacedBy(CgSpace.S6),
                ) {
                    Text(lead, style = CgTheme.type.label.copy(letterSpacing = 0.em))
                    Text(leadDetail, style = body, color = CgColors.Text.Secondary)
                }
                Section(stringResource(Res.string.privacy_uses_title), framed = true) {
                    Use(
                        stringResource(Res.string.privacy_use_camera),
                        stringResource(Res.string.privacy_use_camera_why),
                    )
                    Use(
                        stringResource(Res.string.privacy_use_network),
                        stringResource(Res.string.privacy_use_network_why),
                    )
                    Use(
                        stringResource(Res.string.privacy_use_bluetooth),
                        stringResource(Res.string.privacy_use_bluetooth_why),
                    )
                    Use(
                        stringResource(Res.string.privacy_use_photos),
                        stringResource(Res.string.privacy_use_photos_why),
                        last = true,
                    )
                }
                Section(stringResource(Res.string.privacy_stored_title)) {
                    Paragraph(stringResource(Res.string.privacy_stored))
                }
                Section(stringResource(Res.string.privacy_sharing_title)) {
                    Paragraph(stringResource(Res.string.privacy_sharing))
                }
                Section(stringResource(Res.string.privacy_children_title)) {
                    Paragraph(stringResource(Res.string.privacy_children))
                }
                Section(stringResource(Res.string.privacy_changes_title)) {
                    Paragraph(stringResource(Res.string.privacy_changes))
                }
                Box(
                    Modifier.fillMaxWidth()
                        .padding(vertical = CgSpace.S8)
                        .height(CgStroke.Hairline)
                        .background(CgColors.Line.Hairline)
                )
                Section(stringResource(Res.string.privacy_support_title)) {
                    Paragraph(stringResource(Res.string.privacy_support))
                    Text(
                        "github.com/parez-dev/OnlyShots/issues ↗",
                        style = CgTheme.type.labelStrong.copy(letterSpacing = 0.em),
                        color = CgColors.Text.Primary,
                        modifier =
                            Modifier.heightIn(min = CgSize.Touch).wrapContentHeight().clickable(role = Role.Button) {
                                uri.openUri(SUPPORT_ISSUES_URL)
                            },
                    )
                }
                Text(stringResource(Res.string.core_version, version), style = data, color = CgColors.Text.Secondary)
            }
        }
    }
}

private val body
    @Composable get() = CgTheme.type.body.copy(lineHeight = CgTheme.type.body.fontSize * 1.5f)

private val data
    @Composable get() = CgTheme.type.data.copy(lineHeight = CgTheme.type.data.fontSize * 1.5f)

/** A heading in Mono capitals and what it says; [framed], the items sit in one hairline frame. */
@Composable
private fun Section(title: String, framed: Boolean = false, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = CgSpace.S8), verticalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
        Text(
            title.caps(),
            style = CgTheme.type.labelStrong,
            color = CgColors.Text.Secondary,
            modifier = Modifier.semantics { heading() },
        )
        if (framed) {
            Column(Modifier.fillMaxWidth().border(CgStroke.Hairline, CgColors.Line.Hairline)) { content() }
        } else {
            content()
        }
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(text, style = body, color = CgColors.Text.Secondary)
}

/** One permission: what it is, in Mono capitals, and why; a hairline under it unless it is the [last]. */
@Composable
private fun Use(what: String, why: String, last: Boolean = false) {
    Column {
        Text(
            buildAnnotatedString {
                withStyle(
                    CgTheme.type.labelStrong.copy(letterSpacing = 0.em, color = CgColors.Text.Primary).toSpanStyle()
                ) {
                    append(what.caps())
                }
                append(" $why")
            },
            style = body,
            color = CgColors.Text.Secondary,
            modifier = Modifier.padding(horizontal = CgSpace.S12, vertical = 10.dp),
        )
        if (!last) Box(Modifier.fillMaxWidth().height(CgStroke.Hairline).background(CgColors.Line.Hairline))
    }
}
