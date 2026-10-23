package app.onlyshots.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import app.onlyshots.core.designsystem.CgMotion
import app.onlyshots.core.designsystem.component.Banner
import app.onlyshots.core.designsystem.component.BannerKind
import app.onlyshots.core.designsystem.component.ExportAsk
import app.onlyshots.core.designsystem.component.ExportOverlay
import app.onlyshots.core.designsystem.component.ExportSavedKeys
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.edit_export_notify_allow
import app.onlyshots.core.resources.edit_export_notify_later
import app.onlyshots.core.resources.edit_export_notify_title
import app.onlyshots.core.resources.edit_ok
import app.onlyshots.core.resources.text
import app.onlyshots.platform.rememberNotificationPermission
import org.jetbrains.compose.resources.stringResource

/**
 * The export over the editor while it runs and until its result is seen: a card over the dimmed window on a wide one,
 * the whole screen elsewhere ([fullScreen]). Keep editing puts it down, and the export sheet with it.
 */
@Composable
internal fun EditExportOverlay(
    visible: Boolean,
    ui: EditUiState,
    video: VideoItem,
    actions: EditActions,
    onExported: () -> Unit,
    fullScreen: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible, modifier, enter = fadeIn(CgMotion.medium()), exit = fadeOut(CgMotion.medium())) {
        val wording = LocalExportWording.current
        val detail =
            listOfNotNull(
                    (ui.exportingQuality ?: ui.galleryQuality).size(video.format).let { "${it.width} × ${it.height}" },
                    clock(video.trimmedDurationUs),
                    video.shot?.profileName,
                )
                .joinToString(" · ")
        val doneNow =
            when {
                ui.exporting != null -> null
                ui.savedToGallery -> stringResource(wording.saved)
                else -> wording.shared?.let { stringResource(it) } ?: ""
            }
        // While it fades out the export is over and acknowledged: it keeps saying what it said.
        var done by remember { mutableStateOf(doneNow) }
        if (visible) done = doneNow
        ExportOverlay(
            detail = detail,
            footer = stringResource(wording.footer),
            progress = ui.exporting,
            left = ui.exportLeftMs?.let { clock(it * 1000) },
            done = done,
            still = ui.thumbnails.getOrNull(ui.thumbnails.size / 2),
            stillRatio = video.format.ratio,
            onCancel = actions::cancelExport,
            onKeepEditing = {
                actions.acknowledgeExport()
                actions.closeSheet()
            },
            next = stringResource(wording.next),
            onNext = {
                actions.acknowledgeExport()
                onExported()
            },
            fullScreen = fullScreen,
            leave = wording.leave?.let { stringResource(it) },
            ask = exportAsk(ui, wording, actions),
            savedKeys =
                ui.savedVideo?.let {
                    wording.openSaved?.let { open ->
                        ExportSavedKeys(open = stringResource(open), onOpen = actions::openSavedVideo)
                    }
                },
        )
    }
}

/**
 * The first export's offer to say outside the app when it is done; Allow asks the system, whatever it answers the offer
 * goes for good.
 */
@Composable
private fun exportAsk(ui: EditUiState, wording: ExportWording, actions: EditActions): ExportAsk? {
    val text = wording.notifyText ?: return null
    val ask = rememberNotificationPermission { actions.answerExportNotifications(it) }
    if (!ui.askExportNotifications || ui.exporting == null) return null
    return ExportAsk(
        title = stringResource(Res.string.edit_export_notify_title),
        text = stringResource(text),
        allow = stringResource(Res.string.edit_export_notify_allow),
        later = stringResource(Res.string.edit_export_notify_later),
        onAllow = ask,
        onLater = { actions.answerExportNotifications(false) },
    )
}

/**
 * The editor's warning, over the top of the picture rather than above it, so the picture never changes size. It slides
 * down from under the bar and back up, still saying what it said while it goes.
 */
@Composable
internal fun MessageBanner(message: UiText?, onOk: () -> Unit, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf(message) }
    if (message != null) last = message
    AnimatedVisibility(
        message != null,
        modifier.clipToBounds(),
        enter = slideInVertically(CgMotion.offsetSpec) { -it },
        exit = slideOutVertically(CgMotion.offsetSpec) { -it },
    ) {
        last?.let {
            Banner(it.text(), BannerKind.Warning, action = stringResource(Res.string.edit_ok), onAction = onOk)
        }
    }
}
