package app.onlyshots.screenshots

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.sample.SampleShots
import app.onlyshots.core.resources.raw
import app.onlyshots.edit.EditSheet
import app.onlyshots.home.HomeBody
import app.onlyshots.home.HomeUiState
import app.onlyshots.home.MachineShotsBody
import app.onlyshots.home.MachineShotsUiState
import app.onlyshots.home.NextTake
import app.onlyshots.home.SavedSource
import app.onlyshots.home.StartBody
import app.onlyshots.layout.LocalWindowLayout
import app.onlyshots.layout.bookLayout
import app.onlyshots.layout.stackedLayout
import app.onlyshots.layout.tinyLayout
import app.onlyshots.open.OpenBody
import app.onlyshots.open.OpenEntry
import app.onlyshots.open.OpenUi
import app.onlyshots.privacy.PrivacyScreen
import app.onlyshots.record.RecordPhase
import app.onlyshots.record.RecordUiState
import app.onlyshots.sources.ConnectionState
import app.onlyshots.sources.machine.FoundMachine
import app.onlyshots.sources.machine.NetworkMachines
import uk.co.fractalmotion.mugshot.annotations.Mugshot
import uk.co.fractalmotion.mugshot.annotations.MugshotDevice
import uk.co.fractalmotion.mugshot.annotations.MugshotDevices

/*
 * The screens as the "OnlyShots Foldables" design draws them. Mugshot renders at fixed device shapes, so each
 * preview hands the screen the posture itself: a fold down the middle (a book or an iPhone Duo held open), a fold across
 * (resting on its side), or a flip's cover screen.
 */

/** Two pages meeting at a fold down the middle. */
@Composable
private fun Book(content: @Composable () -> Unit) = CgTheme {
    BoxWithConstraints {
        val layout = bookLayout(maxWidth.value, maxHeight.value)
        CompositionLocalProvider(LocalWindowLayout provides layout) { content() }
    }
}

/** Half open with the fold across: a book on its side when the window is wider than tall, else a flip in flex mode. */
@Composable
private fun OnItsSide(content: @Composable () -> Unit) = CgTheme {
    BoxWithConstraints {
        val layout = stackedLayout(maxWidth.value, maxHeight.value)
        CompositionLocalProvider(LocalWindowLayout provides layout) { content() }
    }
}

/** A flip's cover screen, or a sliver of split screen. */
@Composable
private fun Cover(content: @Composable () -> Unit) = CgTheme {
    BoxWithConstraints {
        CompositionLocalProvider(LocalWindowLayout provides tinyLayout(maxWidth.value, maxHeight.value)) { content() }
    }
}

private val machine = FoundMachine(NetworkMachines.all.first(), "10.0.1.42", 80, "gaggiuino.local")

private val connected =
    HomeUiState(
        machines = listOf(machine),
        saved = SavedSource(SourceKind.GAGGIUINO, "10.0.1.42", null, null),
        connection = ConnectionState.Connected,
    )

private val idleReading = SampleShots.zer0.samples.first().copy(brewing = false)

private fun running(upToUs: Long = 15_000_000) =
    RecordUiState(
        phase = RecordPhase.RECORDING,
        status = "recording · 15.0 s".raw(),
        connection = ConnectionState.Connected,
        sourceKind = SourceKind.GAGGIUINO,
        shot = SampleShots.zer0.let { z -> z.copy(samples = z.samples.filter { it.tUs <= upToUs }) },
        videoTimeUs = 18_000_000,
        shotStartInVideoUs = 3_000_000,
        stopRuleText = "stops 5 s after the shot".raw(),
    )

private val armed =
    RecordUiState(
        phase = RecordPhase.ARMED,
        status = "armed · waiting for the shot".raw(),
        connection = ConnectionState.Connected,
        sourceKind = SourceKind.GAGGIUINO,
        shot = SampleShots.zer0.copy(samples = SampleShots.zer0.samples.take(1)),
        stopRuleText = "stops 5 s after the shot".raw(),
    )

// Home ------------------------------------------------------------------------------------

@Mugshot
@MugshotDevices(MugshotDevice.FOLDABLE)
@Preview
@Composable
internal fun HomeBookOpen() = Book { HomeBody(connected, {}, {}, {}, {}, {}, take = NextTake(live = idleReading)) }

/** The iPhone Duo open: the inner display is landscape. */
@Mugshot
@MugshotDevices(MugshotDevice.TABLET)
@Preview
@Composable
internal fun HomeDuoOpen() = Book { HomeBody(connected, {}, {}, {}, {}, {}, take = NextTake(live = idleReading)) }

// Record ----------------------------------------------------------------------------------

@Mugshot
@MugshotDevices(MugshotDevice.FOLDABLE)
@Preview
@Composable
internal fun RecordBookOpen() = Book { Record(running()) }

/** A book on its side: the camera in the upper half films 16:9, the controls on the half lying flat. */
@Mugshot
@MugshotDevices(MugshotDevice.TABLET)
@Preview
@Composable
internal fun RecordTabletop() = OnItsSide { Record(armed) }

/** A flip half open: the camera stands in the upper half, framed 9:16. */
@Mugshot
@MugshotDevices(MugshotDevice.PHONE)
@Preview
@Composable
internal fun RecordFlex() = OnItsSide { Record(running()) }

@Mugshot
@MugshotDevices(MugshotDevice.PHONE)
@Preview
@Composable
internal fun RecordCover() = Cover { Record(running()) }

// Edit ------------------------------------------------------------------------------------

@Mugshot
@MugshotDevices(MugshotDevice.FOLDABLE)
@Preview
@Composable
internal fun EditBookGraph() = Book { EditUi(sheet = EditSheet.STYLE) }

@Mugshot
@MugshotDevices(MugshotDevice.TABLET)
@Preview
@Composable
internal fun EditDuoOpen() = Book { EditUi(sheet = null, selected = false) }

@Mugshot
@MugshotDevices(MugshotDevice.TABLET)
@Preview
@Composable
internal fun EditTabletop() = OnItsSide { EditUi(sheet = null, selected = false) }

@Mugshot
@MugshotDevices(MugshotDevice.PHONE)
@Preview
@Composable
internal fun EditCover() = Cover { EditUi(sheet = null) }

// The screens the redesign gave a book layout: one side per page.

@Mugshot
@MugshotDevices(MugshotDevice.FOLDABLE)
@Preview
@Composable
internal fun StartBookOpen() = Book { StartBody(onRecord = {}, onEdit = {}) }

@Mugshot
@MugshotDevices(MugshotDevice.FOLDABLE)
@Preview
@Composable
internal fun PrivacyBookOpen() = Book { PrivacyScreen(onBack = {}, version = "1.0.0") }

@Mugshot
@MugshotDevices(MugshotDevice.FOLDABLE)
@Preview
@Composable
internal fun MachineShotsBookOpen() = Book {
    MachineShotsBody(MachineShotsUiState(machines = listOf(machine)), onMachine = {}, onShot = {}, onBack = {})
}

@Mugshot
@MugshotDevices(MugshotDevice.FOLDABLE)
@Preview
@Composable
internal fun OpenBookOpen() = Book {
    OpenBody(
        OpenUi(picked = pickedVideo, pickedFile = pickedFile, fromMachine = false),
        OpenEntry.PhoneApp(onBack = {}),
    )
}
