package com.shilapi.xcertplay.transport
import org.junit.Assert.*
import org.junit.Test
class LockdownRemoteErrorTest {
    @Test fun onlySessionPairingErrorsPermitRePairing() {
        assertTrue(LockdownRemoteError("StartSession", "InvalidHostID").invalidPairing)
        assertTrue(LockdownRemoteError("StartSession", "InvalidPairRecord").invalidPairing)
        for (code in listOf("PasswordProtected", "UserDeniedPairing", "PairingDialogResponsePending", "Timeout", "SSLFailure")) {
            assertFalse(LockdownRemoteError("StartSession", code).invalidPairing)
        }
        assertFalse(LockdownRemoteError("StartService", "InvalidHostID").invalidPairing)
    }
}
