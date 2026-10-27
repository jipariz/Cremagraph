package app.onlyshots.connect

import app.onlyshots.core.model.SourceKind
import app.onlyshots.sources.machine.createMachineHttpClient
import app.onlyshots.storage.AppSettings
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SourcesTest {
    private val client = createMachineHttpClient()

    @Test
    fun aMachineBuildsFromItsAddress() {
        assertNotNull(factoryFor(SourceKind.GAGGIUINO, "gaggiuino.local", null, client))
        assertNotNull(factoryFor(SourceKind.GAGGIUINO, "192.168.1.9:8080", null, client))
        assertNotNull(factoryFor(SourceKind.GAGGIMATE, "gaggimate.local", null, client))
        assertNotNull(factoryFor(SourceKind.GAGGIMATE, "192.168.1.9:8080", null, client))
        assertNotNull(sourceFactory(AppSettings(source = SourceKind.GAGGIMATE, host = "gaggimate.local"), client))
    }

    @Test
    fun machinesThisVersionDropsBuildNothing() {
        // Settings saved by an older version still name them; the app starts disconnected instead.
        assertNull(factoryFor(SourceKind.DECENT, "192.168.1.20:8080", null, client))
        assertNull(sourceFactory(AppSettings(source = SourceKind.DECENT, host = "10.0.1.5"), client))
    }

    @Test
    fun anAddressThatIsNotAHostBuildsNothing() {
        assertNull(factoryFor(SourceKind.GAGGIUINO, "", null, client))
        assertNull(factoryFor(SourceKind.GAGGIUINO, "   ", null, client))
        assertNull(factoryFor(SourceKind.GAGGIUINO, ":80", null, client))
        assertNull(factoryFor(SourceKind.GAGGIUINO, "host:not-a-port", null, client))
    }

    @Test
    fun aScaleNeedsToHaveBeenPicked() {
        assertNull(factoryFor(SourceKind.SCALE, "", null, client))
        assertNotNull(factoryFor(SourceKind.SCALE, "", "AA:BB:CC", client))
    }

    @Test
    fun savedSettingsWithoutAMachineBuildNothing() {
        assertNull(sourceFactory(AppSettings(), client))
        assertNull(sourceFactory(AppSettings(source = SourceKind.GAGGIUINO, host = null), client))
        assertNotNull(sourceFactory(AppSettings(source = SourceKind.GAGGIUINO, host = "gaggiuino.local"), client))
    }
}
