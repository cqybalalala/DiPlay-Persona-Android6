package com.shilapi.xcertplay.orchestration

/**
 * Which wireless link a platform release will run for a stored or requested mode.
 *
 * The station-side links (the car's own hotspot, or an external network both devices join) use
 * nothing newer than API 23. A self-hosted link depends on the release: Android 10 lets the app
 * choose the Wi-Fi Direct group credentials and frequency, Android 8/9 use LocalOnlyHotspot, and
 * anything older only has the original `createGroup` call where the platform picks them.
 */
object WirelessModeSupport {
    private const val API_N = 24
    private const val API_O = 26
    private const val API_Q = 29

    /**
     * The legacy Wi-Fi Direct group was validated on Android 7. On Android 6 the same platform
     * call exists but nobody has run it on a head unit, so the UI marks it experimental there.
     */
    fun selfHostedIsExperimental(sdkInt: Int): Boolean = sdkInt < API_N

    /** Maps a stored or requested mode to the one this platform release will run. */
    fun effectiveMode(mode: WirelessHotspotMode, sdkInt: Int): WirelessHotspotMode = when {
        mode == WirelessHotspotMode.MANUAL || mode == WirelessHotspotMode.EXTERNAL_WIFI -> mode
        sdkInt >= API_Q -> mode
        sdkInt >= API_O -> WirelessHotspotMode.LOCAL_ONLY_HOTSPOT
        else -> WirelessHotspotMode.WIFI_P2P
    }

    /**
     * Whether wireless bring-up needs the location runtime permission below Android 12.
     *
     * Nothing Android 6 does on any wireless link is location-gated: reading the joined network
     * is open before API 27, the original `createGroup` call only needs the Wi-Fi state
     * permissions, and the scan lookups are optional. Skipping the request matters on head units
     * whose firmware ships without the runtime-permission dialog.
     */
    fun needsLegacyLocationPermission(sdkInt: Int): Boolean = sdkInt >= API_N
}
