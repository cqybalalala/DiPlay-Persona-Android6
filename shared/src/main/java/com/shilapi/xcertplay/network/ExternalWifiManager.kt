package com.shilapi.xcertplay.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Looper
import com.shilapi.xcertplay.transport.Iap2WirelessSecurity
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

/**
 * Wireless CarPlay over an EXTERNAL Wi-Fi network: the car and the iPhone both join the same
 * third-party access point (e.g. a pocket router), and the session runs as plain LAN traffic.
 *
 * This exists because every car-as-AP route on Android 7 dies the same way: the iPhone dials the
 * accessory's link-local IPv6 :7000 (phone-side pcap proves it), but the Android kernel silently
 * drops hotspot/P2P client-to-host IPv6. As a plain station client there is no tether firewall in
 * the path, so the link-local dial works exactly like it does on LIVI's Linux hostapd.
 *
 * The manager starts nothing: it waits for the car's own station connection and reports its live
 * state. The user connects the car to the external network in the car settings; the SSID the user
 * typed here is only an optional expectation check (the live SSID is what the phone is told).
 */
class ExternalWifiManager(
    context: Context,
    private val expectedSsid: String,
    private val passphrase: String,
    private val onDiagnostic: (String) -> Unit = {},
) : WirelessHotspotManager {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)
        ?: throw IllegalStateException("WifiManager is unavailable")

    @Volatile
    private var closed = false

    /** Why the last [readStationState] poll was not usable; reported when [start] times out. */
    private var lastReason = ""

    init {
        require('\u0000' !in expectedSsid) { "ssid must not contain U+0000" }
        require('\u0000' !in passphrase) { "passphrase must not contain U+0000" }
        require(passphrase.isEmpty() || passphrase.length in 8..63) {
            "passphrase must be empty or between 8 and 63 characters"
        }
    }

    @SuppressLint("MissingPermission")
    override fun start(timeoutMillis: Long): WirelessHotspotInfo {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "ExternalWifiManager.start must not run on the main thread"
        }
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        lastReason = "Wi-Fi station was not connected"
        while (true) {
            check(!closed) { "ExternalWifiManager is closed" }
            val live = readStationState()
            if (live != null) {
                return live
            }
            if (System.nanoTime() >= deadline) {
                onDiagnostic("External Wi-Fi not usable reason=$lastReason")
                throw IOException(
                    "外部 Wi-Fi 未连接：请先在车机设置中把车机连接到外部 Wi-Fi（$lastReason）",
                )
            }
            Thread.sleep(250)
        }
    }

    /** Reads the live station connection, or null with [lastReason] updated when not usable. */
    @SuppressLint("MissingPermission")
    private fun readStationState(): WirelessHotspotInfo? {
        if (runCatching { wifiManager.isWifiEnabled }.getOrNull() == false) {
            lastReason = "车机 Wi-Fi 已关闭"
            return null
        }
        val info = runCatching { wifiManager.connectionInfo }.getOrNull()
        val rawSsid = info?.ssid?.removePrefix("\"")?.removeSuffix("\"").orEmpty()
        if (info == null || rawSsid.isEmpty() || rawSsid == "<unknown ssid>") {
            lastReason = "车机尚未连接到任何 Wi-Fi 网络"
            return null
        }
        if (expectedSsid.isNotBlank() && rawSsid != expectedSsid) {
            throw IOException(
                "车机当前连接的 Wi-Fi 是 '$rawSsid'，与设置中的 '$expectedSsid' 不一致",
            )
        }
        val frequencyMHz = runCatching { info.frequency }.getOrNull()
            ?.takeIf { it in 2400..6000 }
        val channel = frequencyMHz?.let { wifiFrequencyMhzToChannel(it) } ?: 0
        val ipAddressInt = runCatching { info.ipAddress }.getOrNull() ?: 0
        val iface = findInterfaceByIp(ipAddressInt) ?: NetworkInterface.getByName("wlan0")
        if (iface == null) {
            lastReason = "找不到车机的 Wi-Fi 网络接口"
            return null
        }
        val addresses = Collections.list(iface.inetAddresses)
        val v4 = addresses.filterIsInstance<Inet4Address>().firstOrNull { !it.isLoopbackAddress }
        // Android 6 may list the link-local without a scope id; rebuild it from the interface.
        val linkLocal = scopedLinkLocal(addresses, iface.index)
        if (linkLocal == null) {
            lastReason = "接口 ${iface.name} 没有 IPv6 link-local 地址" +
                "（IPv4=${if (v4 != null) "有" else "无"}）；该车机系统可能关闭了 Wi-Fi 的 IPv6"
            return null
        }
        val security = if (passphrase.isEmpty()) {
            Iap2WirelessSecurity.NONE
        } else {
            Iap2WirelessSecurity.WPA_WPA2
        }
        onDiagnostic(
            "External Wi-Fi ssid='$rawSsid' channel=$channel " +
                "frequency=${frequencyMHz ?: -1}MHz iface=${iface.name} " +
                "v4=${v4?.hostAddress ?: "none"} v6=${linkLocal.hostAddress?.substringBefore('%')}",
        )
        return WirelessHotspotInfo(
            ssid = rawSsid,
            passphrase = passphrase,
            security = security,
            channel = channel,
            frequencyMHz = frequencyMHz,
            bssid = runCatching { info.bssid }.getOrNull()
                ?.takeUnless { it == "02:00:00:00:00:00" || it.isBlank() },
            interfaceName = iface.name,
            // The AirPlay listener and the 0x4301 address selection both key off the link-local:
            // the iPhone dials fe80::<car>:7000 on the network it joined, exactly like LIVI.
            hostAddress = linkLocal,
            bandLabel = if ((frequencyMHz ?: 0) > 5000) "5 GHz" else "2.4 GHz",
            backend = WirelessHotspotBackend.EXTERNAL_WIFI,
        )
    }

    /** Maps the int-form DHCP address from connectionInfo back to its interface. */
    private fun findInterfaceByIp(ipInt: Int): NetworkInterface? {
        if (ipInt == 0) return null
        val bytes = byteArrayOf(
            (ipInt and 0xff).toByte(),
            ((ipInt shr 8) and 0xff).toByte(),
            ((ipInt shr 16) and 0xff).toByte(),
            ((ipInt shr 24) and 0xff).toByte(),
        )
        val target = java.net.InetAddress.getByAddress(bytes)
        return runCatching {
            NetworkInterface.getByInetAddress(target) ?: NetworkInterface.getByName("wlan0")
        }.getOrNull()
    }

    override fun close() {
        closed = true
    }
}
