package com.shilapi.xcertplay

import com.shilapi.xcertplay.transport.LockdownDeviceIdentity
import com.shilapi.xcertplay.transport.LockdownPairRecord
import org.bouncycastle.asn1.pkcs.RSAPublicKey
import java.math.BigInteger
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class LockdownDeviceStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun record(number: Long): LockdownPairRecord {
        val der = RSAPublicKey(BigInteger.valueOf(number), BigInteger.valueOf(65537)).encoded
        val pem = "-----BEGIN RSA PUBLIC KEY-----\n${Base64.getEncoder().encodeToString(der)}\n-----END RSA PUBLIC KEY-----"
        val dummy = byteArrayOf(1, 2, 3)
        return LockdownPairRecord.restore("host-$number", "system", "wifi", pem.toByteArray(), dummy, dummy, dummy, dummy, dummy)
    }
    @Test fun switchingPhonesPreservesBothAndResetOnlyRemovesCurrent() {
        val a = record(12345); val b = record(54321)
        val ka = LockdownDeviceIdentity.key(a.devicePublicKeyPem)
        val kb = LockdownDeviceIdentity.key(b.devicePublicKeyPem)
        AirPlayPersistence.saveLockdownRecord(context, a)
        AirPlayPersistence.saveLockdownRecord(context, b)
        assertEquals(a.hostId, AirPlayPersistence.loadLockdownRecord(context, ka)?.hostId)
        assertEquals(b.hostId, AirPlayPersistence.loadLockdownRecord(context, kb)?.hostId)
        AirPlayPersistence.clearLockdownRecord(context, kb)
        assertNull(AirPlayPersistence.loadLockdownRecord(context, kb))
        assertEquals(a.hostId, AirPlayPersistence.loadLockdownRecord(context, ka)?.hostId)
    }
    @Test fun legacyRecordIsArchivedToItsOwnPhoneWhenAnotherPhoneConnects() {
        val a = record(11111); val b = record(22223)
        val ka = LockdownDeviceIdentity.key(a.devicePublicKeyPem)
        val kb = LockdownDeviceIdentity.key(b.devicePublicKeyPem)
        AirPlayPersistence.saveLockdownRecord(context, a)
        val stored = context.getSharedPreferences("lockdown_$ka", 0)
        val legacy = context.getSharedPreferences("xcertplay_airplay", 0).edit()
        stored.all.forEach { (k,v) -> legacy.putString(k, v as String) }; legacy.commit()
        stored.edit().clear().commit()
        assertNull(AirPlayPersistence.loadLockdownRecord(context, kb))
        assertEquals(a.hostId, AirPlayPersistence.loadLockdownRecord(context, ka)?.hostId)
        assertNull(AirPlayPersistence.loadLockdownRecord(context))
    }
    @Test fun pemWhitespaceDoesNotChangeDeviceIdentity() {
        val pem = record(33331).devicePublicKeyPem
        assertEquals(LockdownDeviceIdentity.key(pem), LockdownDeviceIdentity.key(pem.toString(Charsets.UTF_8).replace("\n", "\r\n").toByteArray()))
    }
    @Test(expected = IllegalArgumentException::class) fun malformedIdentityIsRejected() {
        LockdownDeviceIdentity.key("not a device key".toByteArray())
    }
}
