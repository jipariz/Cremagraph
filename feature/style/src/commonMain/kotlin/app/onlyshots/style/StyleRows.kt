package app.onlyshots.style

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps
import app.onlyshots.core.designsystem.component.CgSlider
import app.onlyshots.core.model.style.GraphStyle
import app.onlyshots.core.model.style.Material
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.style_fix_it
import app.onlyshots.core.resources.style_readability_text_panel
import app.onlyshots.core.resources.style_readability_text_tint
import app.onlyshots.core.resources.style_readability_title
import app.onlyshots.core.resources.text
import app.onlyshots.overlay.compose.label
import app.onlyshots.overlay.fixReadability
import app.onlyshots.overlay.readabilityIssue
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/* Rows the tabs' controls (StyleControls.kt) and the colour sheet are built from. */

@Composable
internal fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    text: String,
    onChange: (Float) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
    ) {
        StyleLabel(label, Modifier.width(SLIDER_LABEL))
        CgSlider(value.coerceIn(range), onChange, range, Modifier.weight(1f), label = label)
        Text(
            text.caps(),
            style = CgTheme.type.labelStrong,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(SLIDER_VALUE),
        )
    }
}

@Composable
internal fun Note(text: String) {
    Text(text, style = CgTheme.type.caption, color = CgColors.Text.Secondary)
}

/** What is hard to read over this frame, and a fix for it. */
@Composable
internal fun ReadabilityWarning(s: GraphStyle, luma: Float, change: ((GraphStyle) -> GraphStyle) -> Unit) {
    s.readabilityIssue(luma)?.let { issue ->
        Column(
            Modifier.fillMaxWidth()
                .border(CgStroke.Hairline, CgColors.Status.Warning)
                .padding(horizontal = CgSpace.S12, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(CgSpace.S6),
        ) {
            Text(
                stringResource(
                        Res.string.style_readability_title,
                        issue.reading.label().replaceFirstChar { it.uppercase() },
                    )
                    .caps(),
                style = CgTheme.type.labelStrong,
                color = CgColors.Status.Warning,
            )
            Text(
                stringResource(
                    if (s.panel.material == Material.SOLID) Res.string.style_readability_text_panel
                    else Res.string.style_readability_text_tint,
                    colorName(issue.value).text().replaceFirstChar { it.uppercase() },
                    "${(issue.ratio * 10).roundToInt() / 10f}",
                ),
                style = CgTheme.type.caption,
                color = CgColors.Text.Secondary,
            )
            // The whole 44 dp cell takes the tap; the amber key drawn in it is smaller.
            Box(
                Modifier.heightIn(min = CgSize.Touch)
                    .clickable(role = Role.Button) { change { it.fixReadability(luma) } }
                    .padding(top = CgSpace.S4),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    stringResource(Res.string.style_fix_it).caps(),
                    style = CgTheme.type.labelStrong,
                    color = CgColors.Text.OnLight,
                    modifier =
                        Modifier.heightIn(min = FIX_KEY_HEIGHT)
                            .background(CgColors.Status.Warning)
                            .padding(horizontal = CgSpace.S12, vertical = 10.dp),
                )
            }
        }
    }
}

/** A slider row's name and value columns, as the canvas sets them. */
internal val SLIDER_LABEL = 88.dp
private val SLIDER_VALUE = 44.dp

/** The amber "fix it" key, drawn inside its 44 dp tap cell. */
private val FIX_KEY_HEIGHT = 36.dp
