package app.onlyshots.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgShape
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_back
import app.onlyshots.core.resources.core_record
import app.onlyshots.core.resources.core_stop
import org.jetbrains.compose.resources.stringResource

enum class CgButtonKind {
    Primary,
    Secondary,
}

/** Figma "Button": a flat pill, lower-case label (callers pass lower-case copy). [Pill] is its compact sibling. */
@Composable
fun CgButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: CgButtonKind = CgButtonKind.Primary,
    enabled: Boolean = true,
) {
    val bg =
        when {
            kind == CgButtonKind.Secondary -> CgColors.Surface.Raised
            enabled -> CgColors.Text.Primary
            else -> CgColors.Surface.Raised
        }
    val fg =
        when {
            !enabled -> CgColors.Text.Dim
            kind == CgButtonKind.Primary -> CgColors.Text.OnLight
            else -> CgColors.Text.Primary
        }
    Box(
        modifier
            .defaultMinSize(minHeight = CgSize.Touch, minWidth = CgSize.Touch)
            .clip(CgShape.Pill)
            .background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = CgSpace.S16, vertical = CgSpace.S12),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = CgTheme.type.body, color = fg, textAlign = TextAlign.Center)
    }
}

/** Figma "Round button": 44 dp icon button on the camera rail and headers. */
@Composable
fun CgRoundButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(CgSize.Touch)
            .clip(CircleShape)
            .background(if (selected) CgColors.Text.Primary else CgColors.Surface.Scrim.copy(alpha = 0.55f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** The one back button: a 44 dp round key on the raised surface, on every screen that has a way back. */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val back = stringResource(Res.string.core_back)
    Box(
        modifier
            .size(CgSize.Touch)
            .clip(CircleShape)
            .background(CgColors.Surface.Raised)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = back },
        contentAlignment = Alignment.Center,
    ) {
        Text("‹", style = CgTheme.type.cardTitle, color = CgColors.Text.Primary)
    }
}

enum class RecordButtonState {
    Idle,
    Armed,
    Recording,
    Disabled,
}

/** Figma "Record button": 70 dp; armed pulses its ring 0.92 → 1.12 over 1.6 s. */
@Composable
fun RecordButton(
    state: RecordButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label =
        stringResource(if (state == RecordButtonState.Recording) Res.string.core_stop else Res.string.core_record)
    val ringScale =
        if (state == RecordButtonState.Armed) {
            val t = rememberInfiniteTransition(label = "armed")
            t.animateFloat(
                    initialValue = 0.92f,
                    targetValue = 1.12f,
                    animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
                    label = "ring",
                )
                .value
        } else 1f
    val enabled = state != RecordButtonState.Disabled
    Box(
        modifier.size(CgSize.Record).clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics {
            contentDescription = label
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(CgSize.Record)
                .scale(ringScale)
                .border(4.dp, if (enabled) CgColors.Text.Primary else CgColors.Text.Dim, CircleShape)
        )
        val inner =
            if (state == RecordButtonState.Recording) {
                Modifier.size(28.dp).background(CgColors.Status.Record, RoundedCornerShape(4.dp))
            } else {
                Modifier.size(54.dp)
                    .background(if (enabled) CgColors.Status.Record else CgColors.Surface.Raised, CircleShape)
            }
        Box(inner)
    }
}
