package app.onlyshots.connect

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.model.Series
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.core_series_targets
import app.onlyshots.core.resources.reading_flow
import app.onlyshots.core.resources.reading_pressure
import app.onlyshots.core.resources.reading_temperature
import app.onlyshots.core.resources.reading_weight
import app.onlyshots.core.resources.reading_weight_flow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The app's names for the data series, shared by the connect screen and the editor. */
val Series.labelRes: StringResource
    get() =
        when (this) {
            Series.PRESSURE -> Res.string.reading_pressure
            Series.FLOW -> Res.string.reading_flow
            Series.WEIGHT -> Res.string.reading_weight
            Series.WEIGHT_FLOW -> Res.string.reading_weight_flow
            Series.TEMPERATURE -> Res.string.reading_temperature
            Series.TARGETS -> Res.string.core_series_targets
        }

/** [labelRes] in words, in composition. */
val Series.label: String
    @Composable get() = stringResource(labelRes)

/** The series' colours, shared by the connect screen and the editor. */
val Series.color: Color
    get() =
        when (this) {
            Series.PRESSURE -> CgColors.Series.Pressure
            Series.FLOW -> CgColors.Series.Flow
            Series.WEIGHT -> CgColors.Series.Weight
            Series.WEIGHT_FLOW -> CgColors.Series.WeightFlow
            Series.TEMPERATURE -> CgColors.Series.Temperature
            Series.TARGETS -> CgColors.Text.Secondary
        }
