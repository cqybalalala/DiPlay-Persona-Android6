package com.shilapi.xcertplay.orchestration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WirelessModeSupportTest {
    @Test fun android6SelfHostsThroughTheLegacyWifiDirectGroup() {
        assertEquals(WirelessHotspotMode.WIFI_P2P, effective(WirelessHotspotMode.WIFI_P2P, 23))
        assertEquals(
            WirelessHotspotMode.WIFI_P2P,
            effective(WirelessHotspotMode.LOCAL_ONLY_HOTSPOT, 23),
        )
        assertEquals(WirelessHotspotMode.MANUAL, effective(WirelessHotspotMode.MANUAL, 23))
        assertEquals(
            WirelessHotspotMode.EXTERNAL_WIFI,
            effective(WirelessHotspotMode.EXTERNAL_WIFI, 23),
        )
    }

    @Test fun newerReleasesKeepTheirExistingMapping() {
        assertEquals(WirelessHotspotMode.WIFI_P2P, effective(WirelessHotspotMode.WIFI_P2P, 24))
        assertEquals(
            WirelessHotspotMode.WIFI_P2P,
            effective(WirelessHotspotMode.LOCAL_ONLY_HOTSPOT, 25),
        )
        assertEquals(
            WirelessHotspotMode.LOCAL_ONLY_HOTSPOT,
            effective(WirelessHotspotMode.WIFI_P2P, 28),
        )
        assertEquals(WirelessHotspotMode.WIFI_P2P, effective(WirelessHotspotMode.WIFI_P2P, 29))
        assertEquals(
            WirelessHotspotMode.LOCAL_ONLY_HOTSPOT,
            effective(WirelessHotspotMode.LOCAL_ONLY_HOTSPOT, 34),
        )
        for (sdk in listOf(24, 26, 29, 34)) {
            assertEquals(WirelessHotspotMode.MANUAL, effective(WirelessHotspotMode.MANUAL, sdk))
            assertEquals(
                WirelessHotspotMode.EXTERNAL_WIFI,
                effective(WirelessHotspotMode.EXTERNAL_WIFI, sdk),
            )
        }
    }

    @Test fun onlyAndroid6IsExperimentalAndSkipsTheLegacyLocationPermission() {
        assertTrue(WirelessModeSupport.selfHostedIsExperimental(23))
        assertFalse(WirelessModeSupport.needsLegacyLocationPermission(23))
        assertFalse(WirelessModeSupport.selfHostedIsExperimental(24))
        assertTrue(WirelessModeSupport.needsLegacyLocationPermission(24))
        assertTrue(WirelessModeSupport.needsLegacyLocationPermission(30))
    }

    private fun effective(mode: WirelessHotspotMode, sdk: Int) =
        WirelessModeSupport.effectiveMode(mode, sdk)
}
