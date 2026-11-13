package dev.ota.blocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaPolicyTest {
    @Test fun defaultAllowsEveryAction() {
        assertFalse(OtaPolicy().enabled)
        OtaAction.entries.forEach { assertFalse(OtaPolicy().blocks(it)) }
    }

    @Test fun eachActionRequiresMasterAndItsOwnBit() {
        for (bits in 0..31) {
            for (action in OtaAction.entries) {
                assertEquals(bits and 1 != 0 && bits and action.bit != 0, OtaPolicy(bits).blocks(action))
            }
        }
    }

    @Test fun disablePreservesSelectionAndRestoreClearsIt() {
        val enabled = OtaPolicy().set(OtaPolicy.MASTER, true).set(OtaAction.INSTALL.bit, true)
        assertTrue(enabled.blocks(OtaAction.INSTALL))
        val disabled = enabled.set(OtaPolicy.MASTER, false)
        assertFalse(disabled.blocks(OtaAction.INSTALL))
        assertTrue(disabled.selected(OtaAction.INSTALL))
        assertFalse(OtaPolicy().selected(OtaAction.INSTALL))
    }
}
