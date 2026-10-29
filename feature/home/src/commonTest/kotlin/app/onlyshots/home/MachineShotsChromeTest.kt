package app.onlyshots.home

import kotlin.test.Test
import kotlin.test.assertEquals

class MachineShotsChromeTest {
    @Test
    fun theMachinesTakeAPhoneColumnOrAMediumWindowsShare() {
        assertEquals(MACHINES_WIDTH_DP, machinesWidthDp(1200f))
        assertEquals(600f * MACHINES_SHARE, machinesWidthDp(600f))
    }
}
