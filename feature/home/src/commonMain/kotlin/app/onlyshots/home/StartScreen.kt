package app.onlyshots.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.onlyshots.SharedKeys
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.CgSheet
import app.onlyshots.core.designsystem.component.ChoiceCard
import app.onlyshots.core.designsystem.component.IconCircleButton
import app.onlyshots.core.designsystem.component.OnlyShotsMark
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.app_name
import app.onlyshots.core.resources.home_about
import app.onlyshots.core.resources.home_start_edit_detail
import app.onlyshots.core.resources.home_start_edit_title
import app.onlyshots.core.resources.home_start_question
import app.onlyshots.core.resources.home_start_record_detail
import app.onlyshots.core.resources.home_start_record_title
import app.onlyshots.core.resources.start_title
import app.onlyshots.shareBounds
import org.jetbrains.compose.resources.stringResource

/**
 * The phones' first screen: record a shot live (Home, then the camera) or edit a video already filmed (pick it and its
 * shot file, then the editor). The ? opens the same sheet as Home's.
 */
@Composable
fun StartScreen(onRecord: () -> Unit, onEdit: () -> Unit, onPrivacy: (() -> Unit)? = null) {
    var help by remember { mutableStateOf(false) }
    StartBody(onRecord = onRecord, onEdit = onEdit, onHelp = { help = true })
    if (help) {
        CgSheet(title = stringResource(Res.string.home_about), onDismiss = { help = false }) {
            HelpContent(
                onPrivacy =
                    onPrivacy?.let { open ->
                        {
                            help = false
                            open()
                        }
                    }
            )
        }
    }
}

/** The start screen from plain callbacks, so the screenshot tests can draw it. */
@Composable
fun StartBody(onRecord: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier, onHelp: () -> Unit = {}) {
    Box(modifier.fillMaxSize().background(CgColors.Surface.Base).safeDrawingPadding(), Alignment.TopCenter) {
        // A phone column; on a tablet it stays one, centred.
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(60.dp).padding(start = CgSpace.S16, end = CgSpace.S12),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OnlyShotsMark()
                Text(
                    stringResource(Res.string.app_name),
                    style = CgTheme.type.cardTitle,
                    modifier = Modifier.weight(1f),
                )
                IconCircleButton(CgIcons.Help, stringResource(Res.string.home_about), onClick = onHelp)
            }
            Column(
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CgSpace.S16),
                verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
            ) {
                Spacer(Modifier.height(CgSpace.S24))
                Text(stringResource(Res.string.start_title), style = CgTheme.type.sheetTitle)
                Text(
                    stringResource(Res.string.home_start_question),
                    style = CgTheme.type.body,
                    color = CgColors.Text.Secondary,
                )
                Spacer(Modifier.height(CgSpace.S8))
                ChoiceCard(
                    icon = CgIcons.Record,
                    tileColor = CgColors.Status.Record,
                    title = stringResource(Res.string.home_start_record_title),
                    detail = stringResource(Res.string.home_start_record_detail),
                    onClick = onRecord,
                    // The card opens into the screen it leads to: it becomes that screen's header.
                    modifier = Modifier.shareBounds(SharedKeys.START_RECORD, clip = StartCardShape),
                )
                ChoiceCard(
                    icon = CgIcons.Film,
                    tileColor = CgColors.Surface.Raised,
                    title = stringResource(Res.string.home_start_edit_title),
                    detail = stringResource(Res.string.home_start_edit_detail),
                    onClick = onEdit,
                    modifier = Modifier.shareBounds(SharedKeys.START_EDIT, clip = StartCardShape),
                )
            }
        }
    }
}

/** The start cards' corners, kept while they fly into the next screen's header. */
private val StartCardShape = RoundedCornerShape(12.dp)
