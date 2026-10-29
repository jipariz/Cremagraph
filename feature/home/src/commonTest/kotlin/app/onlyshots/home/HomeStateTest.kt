package app.onlyshots.home

import app.onlyshots.StopRuleChoice
import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.StopRule
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.home_action_open_camera
import app.onlyshots.core.resources.home_action_scale_caption
import app.onlyshots.core.resources.home_action_without_data
import app.onlyshots.core.resources.home_auto_start_brew_switch
import app.onlyshots.core.resources.home_auto_start_process_start
import app.onlyshots.core.resources.home_bluetooth_off
import app.onlyshots.core.resources.home_found
import app.onlyshots.core.resources.home_record
import app.onlyshots.core.resources.home_record_armed_auto
import app.onlyshots.core.resources.home_record_armed_manual
import app.onlyshots.core.resources.home_rule_start_now_scale
import app.onlyshots.core.resources.home_rule_start_pre_roll
import app.onlyshots.core.resources.home_rule_start_pre_roll_scale
import app.onlyshots.core.resources.home_searching
import app.onlyshots.core.resources.home_status_brewing
import app.onlyshots.core.resources.home_status_idle
import app.onlyshots.core.resources.home_status_no_source
import app.onlyshots.core.resources.home_status_offline
import app.onlyshots.core.resources.home_status_waiting
import app.onlyshots.core.resources.home_take_camera_only
import app.onlyshots.core.resources.home_take_machine
import app.onlyshots.core.resources.raw
import app.onlyshots.core.resources.reading_flow
import app.onlyshots.core.resources.reading_pressure
import app.onlyshots.core.resources.reading_temperature
import app.onlyshots.core.resources.reading_weight
import app.onlyshots.core.resources.source_scale
import app.onlyshots.core.resources.text
import app.onlyshots.sources.ConnectionState
import app.onlyshots.sources.machine.FoundMachine
import app.onlyshots.sources.machine.GaggiMate
import app.onlyshots.sources.machine.Gaggiuino
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeStateTest {
    private val found = FoundMachine(Gaggiuino, "192.168.1.42", 80)
    private val savedGaggiuino = SavedSource(SourceKind.GAGGIUINO, "192.168.1.42", null, null)

    @Test
    fun nothingChosenRecordsWithoutData() {
        val action = recordAction(HomeUiState())
        assertEquals(Res.string.home_action_without_data.text(), action.text)
        assertTrue(action.enabled)
        assertFalse(action.withData)
    }

    @Test
    fun anOfflineSourceRecordsWithoutDataUntilItAnswers() {
        val offline = HomeUiState(saved = savedGaggiuino, connection = ConnectionState.Unreachable("x"))
        assertEquals(Res.string.home_action_without_data.text(), recordAction(offline).text)
        assertTrue(recordAction(offline).enabled)
        assertFalse(recordAction(offline).withData)
        val back = offline.copy(connection = ConnectionState.Connected)
        assertEquals(Res.string.home_action_open_camera.text("Gaggiuino".raw()), recordAction(back).text)
        assertTrue(recordAction(back).withData)
    }

    @Test
    fun theNoteSaysSearchingUntilSomethingIsFound() {
        assertEquals(Res.string.home_searching.text(), foundNote(0))
        assertEquals(Res.string.home_found.text("2"), foundNote(2))
    }

    @Test
    fun aFoundMachineIsReadyUntilTapped() {
        val row = machineRows(HomeUiState(machines = listOf(found))).single()
        assertEquals(RowStatus.Ready, row.status)
        assertFalse(row.selected)
    }

    @Test
    fun theChosenMachineIsSelectedFirstAndNamedOnTheAction() {
        val other = FoundMachine(Gaggiuino, "192.168.1.77", 80)
        val s =
            HomeUiState(machines = listOf(other, found), saved = savedGaggiuino, connection = ConnectionState.Connected)
        val rows = machineRows(s)
        assertEquals(listOf("192.168.1.42", "192.168.1.77"), rows.map { it.detail })
        assertTrue(rows.first().selected)
        assertEquals(RowStatus.Connected, rows.first().status)
        val action = recordAction(s)
        assertEquals(Res.string.home_action_open_camera.text("Gaggiuino".raw()), action.text)
        assertEquals(Res.string.home_auto_start_brew_switch.text(), action.caption)
        assertTrue(action.enabled)
    }

    @Test
    fun aSavedMachineTheSearchMissedIsStillListed() {
        val s = HomeUiState(saved = savedGaggiuino, connection = ConnectionState.Unreachable("x"))
        val row = machineRows(s).single()
        assertTrue(row.selected)
        assertEquals(RowStatus.Offline, row.status)
        assertNull(row.found)
    }

    @Test
    fun aScaleAloneIsNamedOnTheAction() {
        val s = HomeUiState(saved = SavedSource(SourceKind.SCALE, null, "AA", "BOOKOO_SC 123"))
        assertTrue(machineRows(s).isEmpty())
        assertEquals(Res.string.home_action_open_camera.text("BOOKOO_SC 123".raw()), recordAction(s).text)
        assertEquals(Res.string.home_action_scale_caption.text(), recordAction(s).caption)
    }

    @Test
    fun signalStrengthBecomesFourBars() {
        assertEquals(listOf(4, 3, 2, 1), listOf(-50, -65, -75, -95).map(::signalBars))
    }

    @Test
    fun twoKindsOfMachineOnTheWifiAreTwoRows() {
        val gaggimate = FoundMachine(GaggiMate, "192.168.1.9", 80, hostname = "gaggimate.local")
        val s =
            HomeUiState(
                machines = listOf(found, gaggimate),
                saved = SavedSource(SourceKind.GAGGIMATE, "192.168.1.9", null, null),
                connection = ConnectionState.Connected,
            )
        val rows = machineRows(s)
        assertEquals(listOf(SourceKind.GAGGIMATE, SourceKind.GAGGIUINO), rows.map { it.kind })
        // The saved one leads and is the selected row, whichever machine the registry lists first.
        assertTrue(rows.first().selected)
        assertFalse(rows.last().selected)
    }

    @Test
    fun aGaggiMateSaysTheMachineStartsTheShot() {
        val s =
            HomeUiState(
                saved = SavedSource(SourceKind.GAGGIMATE, "gaggimate.local", null, null),
                connection = ConnectionState.Connected,
            )
        assertEquals(Res.string.home_action_open_camera.text("GaggiMate".raw()), recordAction(s).text)
        assertEquals(Res.string.home_auto_start_process_start.text(), recordAction(s).caption)
    }

    @Test
    fun aMachineSavedByNameAndFoundByIpIsOneRow() {
        val byIp = FoundMachine(Gaggiuino, "10.0.1.42", 80, hostname = "gaggiuino.local")
        val s =
            HomeUiState(
                machines = listOf(byIp),
                saved = SavedSource(SourceKind.GAGGIUINO, "gaggiuino.local", null, null),
                connection = ConnectionState.Connected,
            )
        val row = machineRows(s).single()
        assertTrue(row.selected)
        assertEquals("gaggiuino.local · 10.0.1.42", row.detail)
    }

    private val gaggiuino = SavedSource(SourceKind.GAGGIUINO, "10.0.1.42", null, null)

    @Test
    fun theNextTakeSaysWhetherReadingsComeIn() {
        val waiting = HomeUiState(saved = gaggiuino, connection = ConnectionState.Connected)
        assertEquals(Res.string.home_status_waiting.text(), takeStatus(waiting, null))
        assertEquals(Res.string.home_status_idle.text(), takeStatus(waiting, Sample(0, brewing = false)))
        assertEquals(Res.string.home_status_brewing.text(), takeStatus(waiting, Sample(0, brewing = true)))
        assertEquals(
            Res.string.home_status_offline.text("Gaggiuino".raw()),
            takeStatus(waiting.copy(connection = ConnectionState.Unreachable("x")), null),
        )
        assertEquals(Res.string.home_status_no_source.text(), takeStatus(HomeUiState(), null))
    }

    @Test
    fun aMachineShowsTempPressureAndWeightOrItsFlow() {
        val s = HomeUiState(saved = gaggiuino)
        assertEquals(
            listOf(Res.string.reading_temperature, Res.string.reading_pressure, Res.string.reading_weight),
            takeReadings(s, null).map { it.label },
        )
        assertEquals("–", takeReadings(s, null).first().value)
        val noScale = Sample(0, pressure = 9f, flow = 2f, temperature = 93f)
        assertEquals(
            listOf(Res.string.reading_temperature, Res.string.reading_pressure, Res.string.reading_flow),
            takeReadings(s, noScale).map { it.label },
        )
        assertTrue(takeReadings(HomeUiState(), null).isEmpty(), "no source, no tiles")
    }

    @Test
    fun theStopSwitchTogglesBetweenTheShotAndByHand() {
        val off = RecordingSettings().withStopAfterShot(false)
        assertEquals(StopRule.Manual, off.stopRule)
        assertEquals(StopRule.ShotEndPlusTail(), off.withStopAfterShot(true).stopRule)
    }

    @Test
    fun aScaleStartsWithTheFirstDropsNotThePump() {
        val s = RecordingSettings(preRollSeconds = 3)
        assertEquals(Res.string.home_rule_start_pre_roll.text("3"), startRuleDetail(s, SourceKind.GAGGIUINO))
        assertEquals(Res.string.home_rule_start_pre_roll_scale.text("3"), startRuleDetail(s, SourceKind.SCALE))
        assertEquals(
            Res.string.home_rule_start_now_scale.text(),
            startRuleDetail(s.copy(preRollSeconds = 0), SourceKind.SCALE),
        )
    }

    @Test
    fun aWideHomeSaysRecordAndWhatTheCameraWillDoWithTheRules() {
        val s = HomeUiState(saved = savedGaggiuino, connection = ConnectionState.Connected)
        val auto = recordAction(s, nextTake = RecordingSettings(autoStart = true))
        assertEquals(Res.string.home_record.text(), auto.text)
        assertEquals(Res.string.home_record_armed_auto.text(), auto.caption)
        assertTrue(auto.withData)
        val manual = recordAction(s, nextTake = RecordingSettings(autoStart = false))
        assertEquals(Res.string.home_record_armed_manual.text(), manual.caption)
        // Without a source, or with it offline, the rules change nothing: the wide pane says what the phone says.
        assertEquals(recordAction(HomeUiState()), recordAction(HomeUiState(), nextTake = RecordingSettings()))
        val offline = s.copy(connection = ConnectionState.Unreachable("x"))
        assertEquals(recordAction(offline), recordAction(offline, nextTake = RecordingSettings()))
        assertFalse(recordAction(offline, nextTake = RecordingSettings()).withData)
    }

    @Test
    fun theNextTakeIsTitledBySourceAndKind() {
        assertEquals(TakeTitle(Res.string.home_take_camera_only.text(), null), takeTitle(null))
        assertEquals(TakeTitle("Gaggiuino".raw(), Res.string.home_take_machine.text()), takeTitle(savedGaggiuino))
        val scale = SavedSource(SourceKind.SCALE, null, "AA", "BOOKOO_SC 123")
        assertEquals(TakeTitle("BOOKOO_SC 123".raw(), Res.string.source_scale.text()), takeTitle(scale))
    }

    @Test
    fun theTakesLightIsOnOnlyWhileReadingsComeIn() {
        val connected = HomeUiState(saved = savedGaggiuino, connection = ConnectionState.Connected)
        assertTrue(takeIsLive(connected, Sample(0)))
        assertFalse(takeIsLive(connected, null))
        assertFalse(takeIsLive(connected.copy(connection = ConnectionState.Busy), Sample(0)))
    }

    @Test
    fun thePreRollStepsWithinItsRange() {
        val range = RecordingSettings.PRE_ROLL_RANGE
        val low = RecordingSettings(preRollSeconds = range.first)
        assertFalse(low.canStepPreRoll(-1))
        assertTrue(low.canStepPreRoll(1))
        assertEquals(range.first, low.withPreRollStep(-1).preRollSeconds)
        assertEquals(range.first + 1, low.withPreRollStep(1).preRollSeconds)
        val high = RecordingSettings(preRollSeconds = range.last)
        assertFalse(high.canStepPreRoll(1))
        assertEquals(range.last, high.withPreRollStep(1).preRollSeconds)
    }

    @Test
    fun theStopPickerOffersTheRulesTheSourceCanRunButNotByHand() {
        assertEquals(
            listOf(StopRuleChoice.SHOT_END, StopRuleChoice.WEIGHT, StopRuleChoice.FIXED),
            stopRuleChoices(null),
        )
        assertFalse(StopRuleChoice.SHOT_END in stopRuleChoices(SourceKind.SCALE), "a scale knows no shot end")
        assertFalse(StopRuleChoice.MANUAL in stopRuleChoices(SourceKind.GAGGIUINO))
    }

    @Test
    fun theScalesNoteSaysBluetoothIsOffInAWarning() {
        assertEquals(SectionNote(foundNote(0), NoteTone.Secondary, searching = true), scalesNote(HomeUiState()))
        assertEquals(
            SectionNote(Res.string.home_bluetooth_off.text(), NoteTone.Warning, searching = false),
            scalesNote(HomeUiState(bluetoothOff = true)),
        )
        assertEquals(
            SectionNote(null, NoteTone.Secondary, searching = false),
            scalesNote(HomeUiState(bluetoothDenied = true)),
        )
        assertEquals(SectionNote(foundNote(2), NoteTone.Primary, searching = true), machinesNote(2))
        assertEquals(NoteTone.Secondary, machinesNote(0).tone)
    }

    @Test
    fun machinesAndScalesStandSideBySideOnlyWithRoomAndNeverOnAFold() {
        assertTrue(sourcesSideBySide(fold = false, pageWidthDp = TWO_COLUMNS_DP))
        assertFalse(sourcesSideBySide(fold = false, pageWidthDp = TWO_COLUMNS_DP - 1))
        assertFalse(sourcesSideBySide(fold = true, pageWidthDp = 900f))
    }
}
