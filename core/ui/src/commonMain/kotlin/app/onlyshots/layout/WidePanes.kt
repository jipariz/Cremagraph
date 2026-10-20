package app.onlyshots.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.component.TwoPane

/**
 * The wide layout of a screen: [main] (the picture) beside a [panelWidth] [panel] (everything about it), through the
 * adaptive library's supporting-pane scaffold, so the window's pane directive decides the gap and a book foldable's
 * hinge stays clear between the two. On a fold ([WindowLayout.fold]) the two are a page each, meeting at it.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun WidePanes(
    layout: WindowLayout,
    modifier: Modifier = Modifier,
    panelWidth: Dp = PANEL_WIDTH,
    main: @Composable () -> Unit,
    panel: @Composable () -> Unit,
) {
    if (layout.fold) {
        // On a fold the panes meet at it, one page each. [layout] measures from the window's edge, the panes from
        // inside the safe area.
        val start = WindowInsets.safeDrawing.asPaddingValues().calculateStartPadding(LocalLayoutDirection.current)
        TwoPane(
            vertical = false,
            splitDp = (layout.splitDp.dp - start).coerceAtLeast(0.dp),
            hingeDp = layout.hingeDp.dp,
            modifier = modifier,
            first = main,
            second = { Box(Modifier.fillMaxSize().background(CgColors.Surface.Bar)) { panel() } },
        )
        return
    }
    val navigator = rememberSupportingPaneScaffoldNavigator(scaffoldDirective = layout.directive)
    SupportingPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        mainPane = { Box(Modifier.fillMaxSize()) { main() } },
        supportingPane = {
            Box(Modifier.preferredWidth(panelWidth).fillMaxSize().background(CgColors.Surface.Bar)) { panel() }
        },
        modifier = modifier,
    )
}

/** The side panel's width on a wide window, as the web editor's desk layout has it. */
val PANEL_WIDTH = 380.dp
