package app.onlyshots.core.designsystem.component

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgSize
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme

/** Figma "Shot row": one shot from the machine's history, pickable in the editor. */
@Composable
fun ShotRow(
    title: String,
    subtitle: String,
    machineColor: Color,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = CgSize.Touch)
            .background(if (selected) CgColors.Surface.Raised else Color.Transparent)
            .selectable(selected, onClick = onClick)
            .padding(horizontal = CgSpace.S12, vertical = CgSpace.S8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
    ) {
        androidx.compose.foundation.layout.Box(Modifier.size(width = 4.dp, height = 28.dp).background(machineColor))
        Column(Modifier.weight(1f)) {
            Text(title, style = CgTheme.type.body)
            Text(subtitle, style = CgTheme.type.caption, color = CgColors.Text.Secondary)
        }
    }
}
