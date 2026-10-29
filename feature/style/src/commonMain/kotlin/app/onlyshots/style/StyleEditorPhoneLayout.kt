package app.onlyshots.style

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.onlyshots.core.designsystem.CgColors

/**
 * The editor in one column: header, the strip of the frame the graph sits on, tabs, controls, and a sheet sliding up
 * over them. Serves every window that is not wide — a phone, a tabletop ([app.onlyshots.layout.LayoutKind.Stacked]) and
 * a cover screen ([app.onlyshots.layout.LayoutKind.Tiny]) — none of which has room for the frame beside the controls.
 */
@Composable
internal fun StyleEditorPhoneLayout(slots: StyleEditorSlots, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(CgColors.Surface.Base).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            slots.header(Modifier)
            slots.preview(Modifier, false)
            slots.tabs(false)
            slots.controls(Modifier.fillMaxWidth().weight(1f))
        }
        Box(Modifier.align(Alignment.BottomCenter)) { slots.sheets() }
    }
}
