package app.onlyshots.edit

import androidx.compose.ui.graphics.Color
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.model.SourceKind

/** The machine's own colour, kept out of the design system so it stays domain-free. */
fun machineColor(kind: SourceKind): Color =
    when (kind) {
        SourceKind.GAGGIUINO -> CgColors.Machine.Gaggiuino
        SourceKind.GAGGIMATE -> CgColors.Machine.Gaggimate
        SourceKind.DECENT -> CgColors.Machine.Decent
        SourceKind.SCALE -> CgColors.Machine.Scale
        SourceKind.VISUALIZER -> CgColors.Machine.Visualizer
    }
