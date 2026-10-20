package app.onlyshots.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
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
                Text(stringResource(Res.string.privacy_updated), style = small, color = CgColors.Text.Dim)
                // The one thing to know, first and in bold, as the page has it.
                val lead = stringResource(Res.string.privacy_lead)
                val leadDetail = stringResource(Res.string.privacy_lead_detail)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = CgColors.Text.Primary)) {
                            append(lead)
                        }
                        append(" ")
                        append(leadDetail)
                    },
                    style = body,
                    color = CgColors.Text.Secondary,
                )
                Section(stringResource(Res.string.privacy_uses_title)) {
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
                        .height(1.dp)
                        .background(CgColors.Line.Divider)
                )
                Section(stringResource(Res.string.privacy_support_title)) {
                    Paragraph(stringResource(Res.string.privacy_support))
                    Text(
                        "github.com/parez-dev/OnlyShots/issues",
                        style = body.copy(textDecoration = TextDecoration.Underline),
                        color = CgColors.Text.Primary,
                        modifier =
                            Modifier.heightIn(min = CgSize.Touch).wrapContentHeight().clickable(role = Role.Button) {
                                uri.openUri(SUPPORT_ISSUES_URL)
                            },
                    )
                }
                Text(stringResource(Res.string.core_version, version), style = small, color = CgColors.Text.Dim)
            }
        }
    }
}

private val body
    @Composable get() = CgTheme.type.body.copy(fontSize = 16.sp, lineHeight = 25.sp)

private val small
    @Composable get() = CgTheme.type.body.copy(fontSize = 14.sp, lineHeight = 21.sp)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = CgSpace.S8), verticalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
        Text(
            title.uppercase(),
            style = CgTheme.type.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.em),
            color = CgColors.Text.Secondary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(text, style = body, color = CgColors.Text.Secondary)
}

/** One permission: what it is, in bold, and why. */
@Composable
private fun Use(what: String, why: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S8)) {
        Text("·", style = body, color = CgColors.Text.Dim)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = CgColors.Text.Primary)) { append(what) }
                append(": $why")
            },
            style = body,
            color = CgColors.Text.Secondary,
        )
    }
}
