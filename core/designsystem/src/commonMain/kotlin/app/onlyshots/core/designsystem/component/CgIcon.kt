package app.onlyshots.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors

/**
 * An icon from [app.onlyshots.core.designsystem.CgIcons], tinted [tint], [size] square. Every icon in the app is drawn
 * through this, never as a character. [contentDescription] only where the icon is all there is to read.
 */
@Composable
fun CgIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = CgColors.Text.Primary,
    size: Dp = 16.dp,
    contentDescription: String? = null,
) = Icon(icon, contentDescription, modifier.size(size), tint)
