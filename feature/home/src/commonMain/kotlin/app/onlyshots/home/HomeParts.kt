package app.onlyshots.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.home_try_sample
import org.jetbrains.compose.resources.stringResource

/*
 * Pieces Start, Home and the machine shot picker share across their layouts.
 */

/** The phones' design is one column this wide at most; on a tablet it stays one, centred. */
internal val PHONE_COLUMN_WIDTH = 480.dp

/** A label over a group, in caps. */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.caps(), style = CgTheme.type.labelStrong, color = CgColors.Text.Secondary, modifier = modifier)
}

/** A link in Mono, underlined, 44 dp tall to touch. */
@Composable
internal fun FooterLink(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        style = CgTheme.type.labelStrong.copy(letterSpacing = 0.em, textDecoration = TextDecoration.Underline),
        color = CgColors.Text.Primary,
        modifier =
            modifier.heightIn(min = CgSize.Touch).wrapContentHeight().clickable(role = Role.Button, onClick = onClick),
    )
}

/**
 * Under the record button: a recorded shot to try the app with when there is no machine at hand (and for App Review,
 * which has none).
 */
@Composable
internal fun SampleLink(onSample: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        FooterLink(
            stringResource(Res.string.home_try_sample),
            onClick = onSample,
            modifier = Modifier.testTag(TestTags.HomeSample),
        )
    }
}

/** A section note's colour. */
internal fun NoteTone.color(): Color =
    when (this) {
        NoteTone.Secondary -> CgColors.Text.Secondary
        NoteTone.Primary -> CgColors.Text.Primary
        NoteTone.Warning -> CgColors.Status.Warning
    }
