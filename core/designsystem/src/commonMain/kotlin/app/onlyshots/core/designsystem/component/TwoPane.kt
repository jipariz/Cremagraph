package app.onlyshots.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Two panes with a dead band between them. On a foldable the band is the hinge, and because neither pane is measured
 * into it, "nothing across the hinge" is enforced by the layout rather than by eye.
 *
 * @param split size of the first pane, measured from the start or top edge.
 */
@Composable
fun TwoPane(
    vertical: Boolean,
    split: Dp,
    hinge: Dp = 0.dp,
    modifier: Modifier = Modifier,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    if (vertical) {
        Column(modifier.fillMaxSize()) {
            Box(Modifier.height(split).clipToBounds()) { first() }
            Spacer(Modifier.height(hinge))
            Box(Modifier.weight(1f).clipToBounds()) { second() }
        }
    } else {
        Row(modifier.fillMaxSize()) {
            Box(Modifier.width(split).clipToBounds()) { first() }
            Spacer(Modifier.width(hinge))
            Box(Modifier.weight(1f).clipToBounds()) { second() }
        }
    }
}
