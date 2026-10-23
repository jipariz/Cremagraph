package app.onlyshots.style

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.layout.WidePanes
import app.onlyshots.layout.WindowLayout

/**
 * The editor on a wide window: the whole frame beside the controls, the colour and save sheets inside the controls'
 * panel (on a fold, the header and frame on the left page, the tabs, controls and sheets on the right). [sheetOpen]
 * dims the controls; a tap on them ([onScrimTap]) closes a colour.
 */
@Composable
internal fun StyleEditorWideLayout(
    layout: WindowLayout,
    slots: StyleEditorSlots,
    sheetOpen: Boolean,
    onScrimTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrimTap by rememberUpdatedState(onScrimTap)
    // Measures the space inside the safe area, which is what the panel's width is decided from.
    BoxWithConstraints(modifier.fillMaxSize().background(CgColors.Surface.Base).safeDrawingPadding()) {
        WidePanes(
            layout,
            Modifier.fillMaxSize(),
            panelWidth = stylePanelWidthDp(maxWidth.value)?.dp,
            // The panel meets the frame at a hairline, as on the canvas.
            gap = false,
            main = {
                Column(Modifier.fillMaxSize()) {
                    slots.header(Modifier)
                    slots.preview(Modifier.weight(1f).padding(bottom = CgSpace.S16), true)
                }
            },
            panel = {
                Box(Modifier.fillMaxSize().background(CgColors.Surface.Base).drawBehind { drawStartHairline() }) {
                    Column(Modifier.fillMaxSize()) {
                        slots.tabs(true)
                        slots.controls(Modifier.fillMaxWidth().weight(1f))
                    }
                    // The controls dim under a sheet; the frame beside stays in full view.
                    AnimatedVisibility(
                        sheetOpen,
                        enter = fadeIn(CgMotion.short()),
                        exit = fadeOut(CgMotion.short()),
                    ) {
                        Box(
                            Modifier.fillMaxSize()
                                .background(CgColors.Surface.Scrim.copy(alpha = SCRIM_ALPHA))
                                .pointerInput(Unit) { detectTapGestures { scrimTap() } }
                        )
                    }
                    Box(Modifier.align(Alignment.BottomCenter)) { slots.sheets() }
                }
            },
        )
    }
}

/** How far the controls dim under a sheet. */
private const val SCRIM_ALPHA = 0.6f
