package app.onlyshots.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgSpace

/** The phone's start: one column, the headline scrolling if the window is short, the choices at the bottom. */
@Composable
internal fun StartPhoneLayout(
    onRecord: () -> Unit,
    onEdit: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().safeDrawingPadding(), Alignment.TopCenter) {
        Column(Modifier.widthIn(max = PHONE_COLUMN_WIDTH).fillMaxSize().padding(horizontal = CgSpace.S16)) {
            StartHeader(onHelp = onHelp, big = false)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CgSpace.S24),
            ) {
                Spacer(Modifier.height(0.dp))
                GraphCard(Modifier.height(GRAPH_CARD_PHONE), big = false)
                Headline(big = false)
            }
            Box(Modifier.padding(top = CgSpace.S16, bottom = CgSpace.S24)) {
                Choices(onRecord, onEdit, sideBySide = false)
            }
        }
    }
}

/** The phone card: its header and a 112 dp graph, as the canvas draws it. */
private val GRAPH_CARD_PHONE = 152.dp
