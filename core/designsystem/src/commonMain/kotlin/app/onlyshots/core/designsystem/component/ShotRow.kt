package app.onlyshots.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgStroke
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.caps

/**
 * Figma "Shot row": one shot from the machine's history, pickable in the editor. A cell on the ink with the machine's
 * bar, the shot's name in Mono capitals and its time in Mono; chosen, the cell is raised and framed in the signal.
 * Inside a [HairlineStack] ([framed] off) an unchosen row draws no edge of its own: the stack's lines are its edges.
 */
@Composable
fun ShotRow(
    title: String,
    subtitle: String,
    machineColor: Color,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = CgSize.Touch)
            .background(if (selected) CgColors.Surface.Raised else CgColors.Surface.Base)
            .then(
                when {
                    selected -> Modifier.border(CgStroke.Hairline, CgColors.Brand.Bone)
                    framed -> Modifier.border(CgStroke.Hairline, CgColors.Line.Hairline)
                    else -> Modifier
                }
            )
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = CgSpace.S16, vertical = CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
    ) {
        androidx.compose.foundation.layout.Box(Modifier.size(width = 4.dp, height = 32.dp).background(machineColor))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title.caps(), style = CgTheme.type.label.copy(letterSpacing = CgTheme.type.data.letterSpacing))
            Text(subtitle, style = CgTheme.type.data, color = CgColors.Text.Secondary)
        }
    }
}
