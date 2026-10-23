package app.onlyshots.open

import app.onlyshots.clockLength
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.VideoItem
import app.onlyshots.core.model.machineName
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.UiText
import app.onlyshots.core.resources.open_change
import app.onlyshots.core.resources.open_choose
import app.onlyshots.core.resources.open_drop_to_use
import app.onlyshots.core.resources.open_hint_add_shot
import app.onlyshots.core.resources.open_hint_add_video
import app.onlyshots.core.resources.open_hint_both
import app.onlyshots.core.resources.open_hint_ready
import app.onlyshots.core.resources.open_mobile_shot_detail
import app.onlyshots.core.resources.open_mobile_video_detail
import app.onlyshots.core.resources.open_phone_shot_machine
import app.onlyshots.core.resources.open_phone_video_gallery
import app.onlyshots.core.resources.open_recorded
import app.onlyshots.core.resources.open_shot_file_detail
import app.onlyshots.core.resources.open_shot_from
import app.onlyshots.core.resources.open_shot_weight_in
import app.onlyshots.core.resources.open_video
import app.onlyshots.core.resources.open_wide_shot_detail
import app.onlyshots.core.resources.open_wide_shot_detail_machine
import app.onlyshots.core.resources.open_wide_video_drop
import app.onlyshots.core.resources.raw
import app.onlyshots.core.resources.source_scale
import app.onlyshots.core.resources.text
import app.onlyshots.core.resources.unit_grams
import app.onlyshots.core.resources.unit_seconds
import app.onlyshots.minuteStamp
import kotlin.math.roundToInt

/*
 * What the two steps say on every Open page, as UiText: one rule per line, each page choosing its words ([StepWords]).
 */

/** Which page says the steps: the phone app's, the web's on a phone, or the desktop's and the web's desk page. */
internal enum class StepWords {
    PhoneApp,
    MobileWeb,
    Desk,
}

/** The video step's line: "opening…" while one is copied in, what was picked, or what to pick. */
internal fun OpenUi.videoDetail(words: StepWords): UiText =
    opening ?: picked?.let { pickedVideoLine(words, it, pickedFile) } ?: videoPrompt(words)

/**
 * The video step before one is picked, as the desk's empty cell says it (also while it ticks over to the picked one):
 * "opening…", or what to pick.
 */
internal fun OpenUi.emptyVideoDetail(words: StepWords): UiText = opening ?: videoPrompt(words)

/** What to pick for the video; "drop it to use it" while a file is held over the window. */
private fun OpenUi.videoPrompt(words: StepWords): UiText =
    when (words) {
        StepWords.PhoneApp -> if (dragging) Res.string.open_drop_to_use else Res.string.open_phone_video_gallery
        StepWords.MobileWeb -> Res.string.open_mobile_video_detail
        StepWords.Desk -> if (dragging) Res.string.open_drop_to_use else Res.string.open_wide_video_drop
    }.text()

/** The shot step's line: what was picked, or what to pick. */
internal fun OpenUi.shotDetail(words: StepWords): UiText = shot?.let { pickedShotLine(words, it) } ?: shotPrompt(words)

/** What to pick for the shot: a file, or the machine too where the host can reach one. */
internal fun OpenUi.shotPrompt(words: StepWords): UiText =
    when (words) {
        StepWords.PhoneApp -> if (fromMachine) Res.string.open_phone_shot_machine else Res.string.open_shot_file_detail
        StepWords.MobileWeb -> Res.string.open_mobile_shot_detail
        StepWords.Desk ->
            if (fromMachine) Res.string.open_wide_shot_detail_machine else Res.string.open_wide_shot_detail
    }.text()

private fun pickedVideoLine(words: StepWords, item: VideoItem, file: PickedFile?): UiText =
    when (words) {
        StepWords.PhoneApp -> filmedLine(item)
        StepWords.MobileWeb,
        StepWords.Desk -> videoLine(item, file)
    }

private fun pickedShotLine(words: StepWords, chosen: ChosenShot): UiText =
    when (words) {
        StepWords.PhoneApp ->
            when (chosen.from) {
                ShotOrigin.File -> chosen.label.raw()
                ShotOrigin.Machine -> Res.string.open_shot_from.text(chosen.label, machineText(chosen.shot.source))
                ShotOrigin.Visualizer ->
                    Res.string.open_shot_from.text(chosen.label, machineText(SourceKind.VISUALIZER))
            }
        StepWords.MobileWeb,
        StepWords.Desk -> shotLine(chosen)
    }

/** A step's key: "choose" until it is picked, "change" after. */
internal fun pickAction(picked: Boolean): UiText =
    (if (picked) Res.string.open_change else Res.string.open_choose).text()

/** "2026-10-19 08:11 · 0:30": when the video was filmed and how long it is, as kept. */
internal fun filmedLine(item: VideoItem): UiText =
    UiText.Joined(listOf(item.createdAt.minuteStamp().raw(), clockLength(item.trimmedDurationUs).raw()))

/** "0:42 · 1080 × 1920 · recorded 2026-10-20 08:14". */
internal fun videoLine(item: VideoItem, file: PickedFile?): UiText =
    UiText.Joined(
        listOfNotNull(
            clockLength(item.durationUs).raw(),
            file?.sizeText()?.raw(),
            item.recordedAt?.let { Res.string.open_recorded.text(it.minuteStamp()) },
        )
    )

/** "Gaggiuino · 36.4 g in 28 s · 2026-10-20 08:14". */
internal fun shotLine(chosen: ChosenShot): UiText {
    val shot = chosen.shot
    val time = UiText.Joined(listOf("${shot.durationUs / 1_000_000}".raw(), Res.string.unit_seconds.text()), " ")
    val amount =
        shot.finalWeight?.let {
            val grams =
                UiText.Joined(listOf("${(it * 10).roundToInt() / 10f}".raw(), Res.string.unit_grams.text()), " ")
            Res.string.open_shot_weight_in.text(grams, time)
        } ?: time
    return UiText.Joined(listOf(machineText(shot.source), amount, shot.startedAt.minuteStamp().raw()))
}

/** The picked video's name: its file's, or "video" while that is unknown. */
internal fun videoName(file: PickedFile?): UiText = file?.name?.raw() ?: Res.string.open_video.text()

/** "1080 × 1920", or null while the size is unknown. */
internal fun PickedFile.sizeText(): String? = takeIf { widthPx > 0 && heightPx > 0 }?.let { "$widthPx × $heightPx" }

/** The machine by its own name; a scale is a word, said in the user's language. */
internal fun machineText(kind: SourceKind): UiText =
    if (kind == SourceKind.SCALE) Res.string.source_scale.text() else machineName(kind, scale = "").raw()

/** What "open editor" is waiting for, said beside it. */
internal fun OpenUi.ctaHint(): UiText =
    when {
        picked != null && shot != null -> Res.string.open_hint_ready
        picked != null -> Res.string.open_hint_add_shot
        shot != null -> Res.string.open_hint_add_video
        else -> Res.string.open_hint_both
    }.text()

/** "01", "02": a step's number as the canvas sets it. */
internal fun stepNumber(number: Int): String = number.toString().padStart(2, '0')
