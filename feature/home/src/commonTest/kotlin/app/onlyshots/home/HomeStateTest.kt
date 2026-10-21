package app.onlyshots.home

import app.onlyshots.core.model.RecordingSettings
import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.model.StopRule
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.home_action_open_camera
import app.onlyshots.core.resources.home_action_sample
import app.onlyshots.core.resources.home_action_scale_caption
import app.onlyshots.core.resources.home_action_without_data
import app.onlyshots.core.resources.home_auto_start_brew_switch
import app.onlyshots.core.resources.home_found
import app.onlyshots.core.resources.home_rule_start_now_scale
import app.onlyshots.core.resources.home_rule_start_pre_roll
import app.onlyshots.core.resources.home_rule_start_pre_roll_scale
import app.onlyshots.core.resources.home_searching
import app.onlyshots.core.resources.home_status_brewing
import app.onlyshots.core.resources.home_status_idle
import app.onlyshots.core.resources.home_status_no_source
import app.onlyshots.core.resources.home_status_offline
import app.onlyshots.core.resources.home_status_sample
import app.onlyshots.core.resources.home_status_waiting
import app.onlyshots.core.resources.raw
import app.onlyshots.core.resources.reading_flow
import app.onlyshots.core.resources.reading_pressure
import app.onlyshots.core.resources.reading_temperature
import app.onlyshots.core.resources.reading_weight
import app.onlyshots.core.resources.text
import app.onlyshots.sources.ConnectionState
import app.onlyshots.sources.machine.FoundMachine
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
    }

    @Test
    fun anOfflineSourceRecordsWithoutDataUntilItAnswers() {
        val offline = HomeUiState(saved = savedGaggiuino, connection = ConnectionState.Unreachable("x"))
        assertEquals(Res.string.home_action_without_data.text(), recordAction(offline).text)
        assertTrue(recordAction(offline).enabled)
        val back = offline.copy(connection = ConnectionState.Connected)
        assertEquals(Res.string.home_action_open_camera.text("Gaggiuino".raw()), recordAction(back).text)
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
    fun theSampleShotStandsInForAMachine() {
        val s = HomeUiState(sample = true)
        assertEquals(Res.string.home_action_sample.text(), recordAction(s).text)
        assertEquals(Res.string.home_status_sample.text(), takeStatus(s, null))
        assertEquals(
            listOf(Res.string.reading_temperature, Res.string.reading_pressure, Res.string.reading_weight),
            takeReadings(s, null).map { it.label },
        )
    }
}
