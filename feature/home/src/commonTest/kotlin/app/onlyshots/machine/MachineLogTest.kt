package app.onlyshots.record

import app.onlyshots.core.model.Sample
import app.onlyshots.core.model.SourceKind
import app.onlyshots.core.resources.Res
import app.onlyshots.core.resources.raw
import app.onlyshots.core.resources.source_scale
import app.onlyshots.core.resources.text
import app.onlyshots.machine.MachineLog
import app.onlyshots.sources.BrewEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MachineLogTest {
    @Test
    fun formatsSamplesInAppTermsWithUnits() {
        val s =
            Sample(
                tUs = 12_300_000,
                pressure = 8.94f,
                flow = 2.1f,
                weight = 30.06f,
                temperature = 93.4f,
                targetFlow = 2f,
                targetPressure = 0f,
                targetTemperature = 93f,
            )
        assertEquals(
            "12.3 s · 8.9 bar · 2.1 ml/s · 30.1 g · 93.4 °C · target 2.0 ml/s, 93.0 °C",
            MachineLog.line(s, shotStartUs = 0),
        )
        assertEquals(
            "idle · 0.2 bar",
            MachineLog.line(Sample(tUs = 5, pressure = 0.2f, brewing = false), shotStartUs = null),
        )
        assertEquals("brew ended at 34.2 s", MachineLog.line(BrewEvent.Ended(35_200_000), shotStartUs = 1_000_000))
    }

    @Test
    fun formatsBluetoothReadings() {
        assertEquals(
            "Lunar · 36.5 g · 1.8 g/s · battery 80 %",
            MachineLog.line(
                app.onlyshots.sources.ble.BleReading(weightG = 36.5f, weightFlowGps = 1.8f, batteryPercent = 80),
                "Lunar",
            ),
        )
    }

    @Test
    fun namesMachinesByTheirOwnNamesAndAScaleInWords() {
        assertEquals("Gaggiuino".raw(), MachineLog.label(SourceKind.GAGGIUINO))
        assertEquals(Res.string.source_scale.text(), MachineLog.label(SourceKind.SCALE))
    }

    @Test
    fun parsesAddresses() {
        assertEquals("gaggiuino.local" to 80, MachineLog.parseAddress(" gaggiuino.local ", 80))
        assertEquals("192.168.1.40" to 8080, MachineLog.parseAddress("ws://192.168.1.40:8080/ws", 80))
        assertEquals("192.168.1.41" to 8080, MachineLog.parseAddress("192.168.1.41", 8080))
        assertNull(MachineLog.parseAddress("", 80))
        assertNull(MachineLog.parseAddress(":8080", 80))
        assertNull(MachineLog.parseAddress("host:99999", 80))
    }
}
