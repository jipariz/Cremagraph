package app.onlyshots.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.onlyshots.core.designsystem.CgColors
import app.onlyshots.core.designsystem.CgIcons
import app.onlyshots.core.designsystem.CgSpace
import app.onlyshots.core.designsystem.CgTheme
import app.onlyshots.core.designsystem.component.AddRow
import app.onlyshots.core.designsystem.component.BackButton
import app.onlyshots.core.designsystem.component.Banner
import app.onlyshots.core.designsystem.component.BannerKind
import app.onlyshots.core.designsystem.component.CardBar
import app.onlyshots.core.designsystem.component.CardBarButton
import app.onlyshots.core.designsystem.component.CgButton
import app.onlyshots.core.designsystem.component.CgButtonKind
import app.onlyshots.core.designsystem.component.CgDialogCard
import app.onlyshots.core.designsystem.component.CgRoundButton
import app.onlyshots.core.designsystem.component.CgSlider
import app.onlyshots.core.designsystem.component.CgStepper
import app.onlyshots.core.designsystem.component.CgTextField
import app.onlyshots.core.designsystem.component.CgToggle
import app.onlyshots.core.designsystem.component.ColorChip
import app.onlyshots.core.designsystem.component.ConnectScaleCard
import app.onlyshots.core.designsystem.component.EditorSheet
import app.onlyshots.core.designsystem.component.HeaderBack
import app.onlyshots.core.designsystem.component.HeaderPivot
import app.onlyshots.core.designsystem.component.IconCircleButton
import app.onlyshots.core.designsystem.component.LibraryChip
import app.onlyshots.core.designsystem.component.ListRow
import app.onlyshots.core.designsystem.component.MachineRow
import app.onlyshots.core.designsystem.component.OnlyShotsMark
import app.onlyshots.core.designsystem.component.Pill
import app.onlyshots.core.designsystem.component.ProgressLine
import app.onlyshots.core.designsystem.component.RecordAction
import app.onlyshots.core.designsystem.component.RecordButton
import app.onlyshots.core.designsystem.component.RecordButtonState
import app.onlyshots.core.designsystem.component.RulesStrip
import app.onlyshots.core.designsystem.component.RulesStripHint
import app.onlyshots.core.designsystem.component.ScaleAction
import app.onlyshots.core.designsystem.component.ScaleDeviceRow
import app.onlyshots.core.designsystem.component.SectionHeader
import app.onlyshots.core.designsystem.component.SegmentedControl
import app.onlyshots.core.designsystem.component.SeriesChip
import app.onlyshots.core.designsystem.component.SettingRow
import app.onlyshots.core.designsystem.component.SheetHeader
import app.onlyshots.core.designsystem.component.ShotRow
import app.onlyshots.core.designsystem.component.SkeletonRow
import app.onlyshots.core.designsystem.component.StatusChip
import app.onlyshots.core.designsystem.component.Timeline
import app.onlyshots.core.designsystem.component.TopChip
import app.onlyshots.edit.ShotSheetContent
import app.onlyshots.home.HelpContent
import app.onlyshots.open.ShotSourceChoices
import uk.co.fractalmotion.mugshot.annotations.Mugshot
import uk.co.fractalmotion.mugshot.annotations.MugshotShrink

/**
 * The design system, one golden per group. Nothing here takes a ViewModel or platform code, so every component renders
 * exactly as the app draws it.
 *
 * `RecordButton(Armed)` is left out on purpose: its ring pulses through an infinite transition, which would make a
 * different image every run.
 */
/** The phone width the design system is drawn for, and enough height for the tallest sheet. */
private const val SHEET_W = 390
private const val SHEET_H = 620

@Composable
private fun Sheet(content: @Composable () -> Unit) {
    CgTheme {
        Column(
            Modifier.width(SHEET_W.dp).background(CgColors.Surface.Base).padding(CgSpace.S16),
            verticalArrangement = Arrangement.spacedBy(CgSpace.S12),
        ) {
            content()
        }
    }
}

@Mugshot
@MugshotShrink
@Preview
@Composable
internal fun Buttons() = Sheet {
    CgButton("create video", onClick = {})
    CgButton("create video", onClick = {}, enabled = false)
    CgButton("record another", onClick = {}, kind = CgButtonKind.Secondary)
    CgButton("record another", onClick = {}, kind = CgButtonKind.Secondary, enabled = false)
    Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
        RecordButton(RecordButtonState.Idle, onClick = {})
        RecordButton(RecordButtonState.Recording, onClick = {})
        RecordButton(RecordButtonState.Disabled, onClick = {})
    }
    Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
        CgRoundButton("zoom 1 times", onClick = {}) { Text("1×", style = CgTheme.type.label) }
        CgRoundButton("sound on", onClick = {}, selected = true) {
            Text("mic", style = CgTheme.type.label, color = CgColors.Text.OnLight)
        }
    }
}

@Mugshot
@MugshotShrink
@Preview
@Composable
internal fun Controls() = Sheet {
    CgToggle(checked = true, onCheckedChange = {})
    CgToggle(checked = false, onCheckedChange = {})
    CgToggle(checked = false, onCheckedChange = {}, enabled = false)
    SeriesChip("pressure", CgColors.Series.Pressure, selected = true, onClick = {})
    SeriesChip("weight flow", CgColors.Series.WeightFlow, selected = false, onClick = {})
    SeriesChip("temp", CgColors.Series.Temperature, selected = false, onClick = {}, enabled = false)
    SegmentedControl(listOf("graph + readings", "graph", "readings"), selectedIndex = 0, onSelect = {})
    SegmentedControl(
        listOf("shot end", "weight settles", "fixed", "manual"),
        selectedIndex = 1,
        onSelect = {},
        fill = true,
    )
    CgStepper(value = "3 s", onMinus = {}, onPlus = {})
    CgSlider(value = 0.8f, onValueChange = {}, range = 0.2f..1f, steps = 15, label = "transparency")
}

@Mugshot
@MugshotShrink
@Preview
@Composable
internal fun StatusAndHeaders() = Sheet {
    StatusChip("armed · starts when the brew switch flips on", recording = false)
    StatusChip("recording", recording = true, dot = CgColors.Text.Primary)
    Banner("saved to library", BannerKind.Success, action = "record another", onAction = {})
    Banner("the machine is busy: close another web interface", BannerKind.Warning)
    Banner("rendering keeps going in the background", BannerKind.Info)
    HeaderPivot(listOf("record", "library", "machine"), selectedIndex = 0, onSelect = {})
    HeaderBack("edit", onBack = {})
}

@Mugshot
@MugshotShrink
@Preview
@Composable
internal fun ShotRowsAndTimeline() = Sheet {
    ShotRow("Zer0", "0:40 · 36.0 g", CgColors.Machine.Gaggiuino, selected = true, onClick = {})
    ShotRow("Gentle Bloom", "0:32 · 34.2 g", CgColors.Machine.Gaggimate, onClick = {})
    ShotRow("Turbo", "0:25 · 40.0 g", CgColors.Machine.Decent, onClick = {})
    Timeline(
        durationUs = 48_000_000,
        trimInUs = 2_000_000,
        trimOutUs = 44_000_000,
        playheadUs = 20_000_000,
        shotStartUs = 5_000_000,
        shotStartLocked = false,
    )
    Timeline(
        durationUs = 48_000_000,
        trimInUs = 0,
        trimOutUs = 48_000_000,
        playheadUs = 5_000_000,
        shotStartUs = 5_000_000,
        shotStartLocked = true,
    )
}

/** Figma "Home": the rows and cards the start screen is built from, in the states it shows them. */
@Mugshot
@MugshotShrink
@Preview
@Composable
internal fun HomeRows() = Sheet {
    Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S12)) {
        OnlyShotsMark()
        LibraryChip(12, onClick = {})
    }
    SectionHeader("machines on this Wi-Fi", trailing = "searching…", trailingColor = CgColors.Status.Highlight)
    MachineRow(
        "Gaggiuino",
        "192.168.1.42",
        CgColors.Machine.Gaggiuino,
        "connected",
        CgColors.Status.Live,
        selected = true,
        onClick = {},
    )
    MachineRow("Gaggiuino", "192.168.1.77", CgColors.Machine.Gaggiuino, "ready", CgColors.Status.Live, false, {})
    SkeletonRow()
    AddRow("add by address", onClick = {}, subtitle = "IP or name, for machines the search can’t see")
    SectionHeader("Bluetooth scales", trailing = "2 found")
    ScaleDeviceRow("Acaia Lunar", "weight · flow", bars = 3, action = ScaleAction.Connect, onAction = {})
    ScaleDeviceRow("Bookoo Themis Mini", "weight · flow", bars = 4, action = ScaleAction.Connecting, onAction = {})
    ConnectScaleCard("connect a scale", "Adds weight and flow; works with any machine, or alone", onClick = {})
}

@Mugshot
@MugshotShrink
@Preview
@Composable
internal fun RecordRules() = Sheet {
    RulesStrip("with the shot", "3 s before", "5 s after the shot")
    RulesStrip("by hand", null, "when the weight settles")
    RulesStripHint("no machine · record by hand, add the shot in the editor")
    IconCircleButton(CgIcons.Settings, "recording settings", onClick = {})
}

@Mugshot
@MugshotShrink
@Preview
@Composable
internal fun HomeRecordAction() = Sheet {
    RecordAction("record without data", "Pick a machine or a scale above for the live graph", true, {})
    RecordAction("open camera · Gaggiuino", "Shot starts recording when the brew switch flips on", true, {})
}

/** Home's ? sheet: what the app does, the machines and scales it knows, and the privacy and support links. */
@Mugshot @MugshotShrink @Preview @Composable internal fun HomeHelp() = Sheet { HelpContent(version = "1.0.0") }

/** The editor's shot sheet with a recorded shot attached: the shot leads, the other ways in are small pills. */
@Mugshot
@MugshotShrink
@Preview
@Composable
internal fun ShotSheet() = Sheet {
    ShotSheetContent(
        sampleVideo.copy(syncLocked = true, shotStartInVideoUs = 3_000_000),
        shots = emptyList(),
        onPick = {},
        onFile = {},
        onFromMachine = {},
    )
}

/** The editor's shot sheet before any shot: the ways to add one are the sheet. */
@Mugshot
@MugshotShrink
@Preview
@Composable
internal fun ShotSheetEmpty() = Sheet {
    ShotSheetContent(sampleVideo.copy(shot = null), shots = emptyList(), onPick = {}, onFile = {}, onFromMachine = {})
}

/** "Compose a video"'s shot sheet: from a file, or from the machine. */
@Mugshot
@MugshotShrink
@Preview
@Composable
internal fun ShotSources() = Sheet { ShotSourceChoices(onFile = {}, onMachine = {}) }

/** The web editor's phone controls, as the mobile editor draws them. */
@Mugshot
@MugshotShrink
@Preview
@Composable
internal fun EditorControls() = Sheet {
    Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S6)) {
        BackButton(onClick = {})
        TopChip("Londinium declining", onClick = {}, dot = CgColors.Status.Success)
        TopChip("export", onClick = {}, primary = true)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(CgSpace.S6)) {
        Pill("⚑ start here", onClick = {}, color = CgColors.Brand.Orange)
        Pill("find start", onClick = {}, color = CgColors.Status.Live, enabled = false)
        Pill("fit", onClick = {})
        Pill("graph start", onClick = {}, icon = CgIcons.Lock)
    }
    CardBar {
        CardBarButton("style ▾", onClick = {})
        CardBarButton("data ▾", onClick = {})
        CardBarButton("◐ 80 %", onClick = {})
        CardBarButton("↺", onClick = {})
    }
    EditorSheet(title = "Making your video", onDone = null) {
        ProgressLine(0.42f)
        ListRow("another shot file", ".shot or .json, from SproFiler or the machine", onClick = {})
        Pill("cancel", onClick = {}, modifier = Modifier.fillMaxWidth(), fill = true)
    }
}

/** The style editor's controls: the material choice and a colour chip. */
@Mugshot
@MugshotShrink
@Preview
@Composable
internal fun StyleEditorControls() = Sheet {
    SegmentedControl(listOf("solid", "blur", "Liquid Glass", "none"), selectedIndex = 2, onSelect = {})
    ColorChip(androidx.compose.ui.graphics.Color(0xFF4A1216), "oxblood", onClick = {})
    ColorChip(androidx.compose.ui.graphics.Color(0x24FFFFFF), "clear · 14 %", onClick = {})
}

/** Settings rows, the text field, a sheet's header and a dialog: everything a form is made of. */
@Mugshot
@MugshotShrink
@Preview
@Composable
internal fun FormsAndDialogs() = Sheet {
    SheetHeader("recording", onDone = {})
    SettingRow("start with the shot", hint = "starts when the brew switch flips on") {
        CgToggle(checked = true, onCheckedChange = {})
    }
    SettingRow("pre-roll", hint = "seconds of picture kept from before the shot started") {
        CgStepper(value = "3 s", onMinus = {}, onPlus = {})
    }
    SectionHeader("stops")
    SegmentedControl(
        listOf("shot end", "weight settles", "fixed", "manual"),
        selectedIndex = 0,
        onSelect = {},
        fill = true,
    )
    CgTextField("Morning glass", onValueChange = {})
    CgTextField("", onValueChange = {}, placeholder = "192.168.1.20")
    CgDialogCard(
        title = "leave this video?",
        text = "It is not in your gallery yet. Once you leave, you cannot come back to it here.",
        confirm = "keep editing" to {},
        dismiss = "leave" to {},
    )
}
