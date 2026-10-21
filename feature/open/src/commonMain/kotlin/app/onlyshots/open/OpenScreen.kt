package app.onlyshots.open

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.onlyshots.TestTags
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.CgButton
import app.onlyshots.core.designsystem.component.CgSheet
import app.onlyshots.core.designsystem.component.ChoiceCard
import app.onlyshots.core.designsystem.component.HeaderBack
import app.onlyshots.core.designsystem.component.OnlyShotsMark
import app.onlyshots.core.model.Shot
import app.onlyshots.core.model.SidecarCodec
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.model.machineName
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.app_name
import app.onlyshots.core.resources.open_change
import app.onlyshots.core.resources.open_choose
import app.onlyshots.core.resources.open_drop_to_use
import app.onlyshots.core.resources.open_editor
import app.onlyshots.core.resources.open_from_file
import app.onlyshots.core.resources.open_from_machine
import app.onlyshots.core.resources.open_from_machine_detail
import app.onlyshots.core.resources.open_header_edit
import app.onlyshots.core.resources.open_phone_intro
import app.onlyshots.core.resources.open_phone_shot_machine
import app.onlyshots.core.resources.open_phone_shot_title
import app.onlyshots.core.resources.open_phone_video_drop
import app.onlyshots.core.resources.open_phone_video_gallery
import app.onlyshots.core.resources.open_phone_video_title
import app.onlyshots.core.resources.open_sheet_title
import app.onlyshots.core.resources.open_shot_file_detail
import app.onlyshots.core.resources.open_shot_from
import app.onlyshots.core.resources.source_scale
import app.onlyshots.core.resources.start_title
import app.onlyshots.core.resources.text
import app.onlyshots.platform.rememberShotFilePicker
import app.onlyshots.platform.rememberVideoPicker
import app.onlyshots.platform.videoDrop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The shot picked for the video: the shot itself, what to call it, and where it came from ("file" or a machine). */
data class ChosenShot(val shot: Shot, val label: String, val from: String)

/**
 * Every editor's start: pick the video and its shot, then open the editor, which lines the shot up. Both are needed:
 * the editor is for putting a shot's graph on a video. The shot comes from a `.shot` file, or from the machine itself
 * where the host can reach one ([onFromMachine]: the phones and the desktop, not the web), whose picker hands the shot
 * back through [pickedShot].
 *
 * The phones come here from their start screen ([onBack]); the desktop and the web start here and take a dropped video.
 */
@Composable
fun OpenScreen(
    onOpen: (id: String) -> Unit,
    onBack: (() -> Unit)? = null,
    onFromMachine: (() -> Unit)? = null,
    pickedShot: StateFlow<String?> = MutableStateFlow(null),
    onPickedShotUsed: () -> Unit = {},
    /** What the desktop or the web says about itself (where it runs, what its videos come out as). */
    host: OpenHost? = null,
    /** The phone app's store links, which the web offers. */
    stores: StoreLinks? = null,
    /** The app's own privacy screen, for the header's "privacy" (desktop, web). */
    onPrivacy: (() -> Unit)? = null,
    viewModel: OpenViewModel = koinViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val picked by viewModel.picked.collectAsStateWithLifecycle()
    val shot by viewModel.shot.collectAsStateWithLifecycle()
    val pickedFile by viewModel.pickedFile.collectAsStateWithLifecycle()
    val pickedThumb by viewModel.pickedThumb.collectAsStateWithLifecycle()
    val fromMachine by pickedShot.collectAsStateWithLifecycle()
    val scale = stringResource(Res.string.source_scale)
    LaunchedEffect(fromMachine) {
        val json = fromMachine ?: return@LaunchedEffect
        runCatching { SidecarCodec.json.decodeFromString(Shot.serializer(), json) }
            .getOrNull()
            ?.let { viewModel.chooseShot(it, it.profileName, machineName(it.source, scale)) }
        onPickedShotUsed()
    }
    val pickVideo = rememberVideoPicker(viewModel::pickVideo)
    val pickFile = rememberShotFilePicker(viewModel::chooseFile)
    var choosingSource by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    OpenBody(
        picked = picked,
        pickedFile = pickedFile?.copy(thumb = pickedThumb),
        shot = shot,
        status = status,
        onPickVideo = pickVideo,
        // With the file the only way (the web), the file picker opens at once rather than a sheet with one choice.
        onPickShot = if (onFromMachine == null) pickFile else ({ choosingSource = true }),
        fromMachine = onFromMachine != null,
        host = host,
        stores = stores,
        onPrivacy = onPrivacy,
        onOpenEditor = { viewModel.openWith(onOpen) },
        onBack = onBack,
        dragging = dragging,
        modifier = Modifier.videoDrop(onFile = viewModel::pickVideo, onHover = { dragging = it }),
    )
    if (choosingSource) {
        CgSheet(title = stringResource(Res.string.open_sheet_title), onDismiss = { choosingSource = false }) {
            ShotSourceChoices(
                onFile = {
                    choosingSource = false
                    pickFile()
                },
                onMachine =
                    onFromMachine?.let { open ->
                        {
                            choosingSource = false
                            open()
                        }
                    },
            )
        }
    }
}

/**
 * The start from plain state: the two steps, and the button that opens the editor once both are picked. [dragging]
 * while a file is held over the window.
 */
@Composable
fun OpenBody(
    picked: VideoItem?,
    shot: ChosenShot?,
    status: UiText?,
    onPickVideo: () -> Unit,
    onPickShot: () -> Unit,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    dragging: Boolean = false,
    /** The machine is one way to the shot (phones, desktop), not only a file (web). */
    fromMachine: Boolean = true,
    /** What the desktop or the web says about itself. */
    host: OpenHost? = null,
    stores: StoreLinks? = null,
    /** The picked video's own name and size, for the desktop's and the web's picked step. */
    pickedFile: PickedFile? = null,
    onPrivacy: (() -> Unit)? = null,
) {
    // The desktop and the web start here: the "OnlyShots Open Screen" design's page. The phones come from their start
    // screen to the column below.
    if (onBack == null) {
        WideOpenBody(
            picked = picked,
            pickedFile = pickedFile,
            shot = shot,
            status = status,
            onPickVideo = onPickVideo,
            onPickShot = onPickShot,
            onOpenEditor = onOpenEditor,
            modifier = modifier,
            dragging = dragging,
            fromMachine = fromMachine,
            host = host,
            stores = stores,
            onPrivacy = onPrivacy,
        )
        return
    }
    Page(onBack = onBack, modifier = modifier) {
        val opening = status?.takeIf { it == Opening }?.text()
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = CgSpace.S16),
            verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
        ) {
            if (onBack == null) {
                Text(stringResource(Res.string.start_title), style = CgTheme.type.sheetTitle)
                Text(
                    stringResource(Res.string.open_phone_intro),
                    style = CgTheme.type.body,
                    color = CgColors.Text.Secondary,
                )
            }
            Step(
                number = 1,
                title = stringResource(Res.string.open_phone_video_title),
                detail =
                    opening
                        ?: picked?.let(::videoDetail)
                        ?: if (dragging) stringResource(Res.string.open_drop_to_use)
                        else if (onBack == null) stringResource(Res.string.open_phone_video_drop)
                        else stringResource(Res.string.open_phone_video_gallery),
                action = stringResource(if (picked == null) Res.string.open_choose else Res.string.open_change),
                done = picked != null,
                highlighted = dragging,
                onClick = onPickVideo,
                modifier = Modifier.testTag(TestTags.OpenPickVideo),
            )
            Step(
                number = 2,
                title = stringResource(Res.string.open_phone_shot_title),
                detail =
                    shot?.let {
                        if (it.from == "file") it.label
                        else stringResource(Res.string.open_shot_from, it.label, it.from)
                    }
                        ?: if (fromMachine) stringResource(Res.string.open_phone_shot_machine)
                        else stringResource(Res.string.open_shot_file_detail),
                action = stringResource(if (shot == null) Res.string.open_choose else Res.string.open_change),
                done = shot != null,
                onClick = onPickShot,
                modifier = Modifier.testTag(TestTags.OpenPickShot),
            )
            status
                ?.takeIf { it != Opening }
                ?.let { Text(it.text(), style = CgTheme.type.body, color = CgColors.Status.Highlight) }
        }
        CgButton(
            stringResource(Res.string.open_editor),
            onClick = onOpenEditor,
            enabled = picked != null && shot != null,
            modifier = Modifier.testTag(TestTags.OpenEditor).fillMaxWidth().padding(CgSpace.S16),
        )
    }
}

/** What the shot sheet offers: a file, and the machine where the host can reach one. */
@Composable
fun ShotSourceChoices(onFile: () -> Unit, onMachine: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
        ChoiceCard(
            icon = CgIcons.Film,
            tileColor = CgColors.Surface.Raised,
            title = stringResource(Res.string.open_from_file),
            detail = stringResource(Res.string.open_shot_file_detail),
            onClick = onFile,
            modifier = Modifier.testTag(TestTags.OpenFromFile),
        )
        if (onMachine != null) {
            ChoiceCard(
                icon = CgIcons.Machine,
                tileColor = CgColors.Machine.Gaggiuino,
                title = stringResource(Res.string.open_from_machine),
                detail = stringResource(Res.string.open_from_machine_detail),
                onClick = onMachine,
                modifier = Modifier.testTag(TestTags.OpenFromMachine),
            )
        }
    }
}

/** A back header (phones) or the OnlyShots name (desktop, web) over a column, centred on a wide window. */
@Composable
private fun Page(onBack: (() -> Unit)?, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(CgColors.Surface.Base).safeDrawingPadding(), Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize()) {
            if (onBack != null) {
                HeaderBack(
                    stringResource(Res.string.open_header_edit),
                    onBack = onBack,
                    modifier = Modifier.padding(vertical = CgSpace.S8),
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().height(72.dp).padding(horizontal = CgSpace.S16),
                    horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OnlyShotsMark(splashTarget = true)
                    Text(stringResource(Res.string.app_name), style = CgTheme.type.screenTitle)
                }
            }
            content()
        }
    }
}

/** A numbered step: what it is, what was picked (or what to pick), and the button to pick it. */
@Composable
private fun Step(
    number: Int,
    title: String,
    detail: String,
    action: String,
    done: Boolean,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val card = RoundedCornerShape(16.dp)
    // A step lights up as it is done or hovered with a file, rather than switching at once.
    val ground by
        animateColorAsState(if (highlighted) CgColors.Surface.Raised else CgColors.Surface.Bar, CgMotion.short())
    val edge by
        animateColorAsState(
            when {
                highlighted -> CgColors.Text.Primary
                done -> CgColors.Text.Secondary
                else -> CgColors.Line.Hairline
            },
            CgMotion.short(),
        )
    val edgeWidth by animateDpAsState(if (highlighted) 2.dp else 1.dp, CgMotion.short())
    val disc by animateColorAsState(if (done) CgColors.Text.Primary else CgColors.Surface.Raised, CgMotion.medium())
    val digit by animateColorAsState(if (done) CgColors.Text.OnLight else CgColors.Text.Primary, CgMotion.medium())
    Row(
        modifier
            .fillMaxWidth()
            .clip(card)
            .background(ground)
            .border(edgeWidth, edge, card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(CgSpace.S16),
        horizontalArrangement = Arrangement.spacedBy(CgSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(disc),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                style = CgTheme.type.label,
                color = digit,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = CgTheme.type.body)
            // "choose a video" → the picked file's name and length, with a fade.
            AnimatedContent(
                detail,
                transitionSpec = { fadeIn(CgMotion.medium()) togetherWith fadeOut(CgMotion.short()) },
                label = "detail",
            ) {
                Text(it, style = CgTheme.type.caption, color = CgColors.Text.Dim)
            }
        }
        Text(action, style = CgTheme.type.label, color = CgColors.Text.Secondary)
    }
}

/** "2026-10-19 08:11 · 0:30": when the video was filmed and how long it is. */
private fun videoDetail(item: VideoItem): String {
    val seconds = item.trimmedDurationUs / 1_000_000
    val length = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    return item.createdAt.toString().take(16).replace('T', ' ') + " · " + length
}
