package dev.ota.blocker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaEndpointTest {
    @Test fun downloadToggleBlocksStreamingWithoutBlockingLocalInstallation() {
        val endpoint = UpdaterProfiles.verified.single().endpoints.single { it.name == "updatePackage" }
        val download = OtaPolicy().set(OtaPolicy.MASTER, true).set(OtaAction.DOWNLOAD.bit, true)
        val local = listOf("local.zip", null, false, null, false)
        val stream = listOf("synthetic", null, false, Any(), false)
        assertFalse(endpoint.shouldBlock(download, local))
        assertTrue(endpoint.shouldBlock(download, stream))
        assertFalse(endpoint.shouldBlock(OtaPolicy(), stream))
        assertTrue(endpoint.shouldBlock(download.set(OtaAction.INSTALL.bit, true), local))
        assertTrue(endpoint.result == false)
    }

    @Test fun unmatchedArgumentsContinueAndCheckUsesNullableResult() {
        val endpoint = UpdaterProfiles.verified.single().endpoints.single { it.owner == "q0.q" && it.name == "x" }
        val policy = OtaPolicy().set(OtaPolicy.MASTER, true).set(OtaAction.REMINDER.bit, true)
        assertTrue(endpoint.shouldBlock(policy, listOf(null, 3)))
        assertTrue(endpoint.shouldBlock(policy, listOf(null, 4)))
        assertFalse(endpoint.shouldBlock(policy, listOf(null, 15)))
        assertFalse(endpoint.shouldBlock(OtaPolicy(), listOf(null, 3)))
        assertNull(UpdaterProfiles.verified.single().endpoints.single { it.action == OtaAction.CHECK }.result)
    }
}
