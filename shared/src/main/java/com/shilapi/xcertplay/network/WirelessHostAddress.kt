package com.shilapi.xcertplay.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * The interface's link-local IPv6 carrying that interface's scope, or null when it has none.
 *
 * Older platform releases can report the address without a scope id; an unscoped link-local
 * cannot be bound or dialled, so the scope is always rebuilt from [interfaceIndex].
 */
internal fun scopedLinkLocal(addresses: List<InetAddress>, interfaceIndex: Int): Inet6Address? {
    if (interfaceIndex <= 0) return null
    val linkLocal = addresses.filterIsInstance<Inet6Address>()
        .firstOrNull { it.isLinkLocalAddress } ?: return null
    return Inet6Address.getByAddress(null, linkLocal.address, interfaceIndex)
}

/** Use the same scoped, link-local path for manual APs as for Wi-Fi Direct. */
internal fun wirelessHostAddress(addresses: List<InetAddress>, interfaceIndex: Int): InetAddress? {
    if (interfaceIndex > 0) {
        addresses.filterIsInstance<Inet6Address>().firstOrNull { it.isLinkLocalAddress }?.let {
            return Inet6Address.getByAddress(null, it.address, interfaceIndex)
        }
    }
    return addresses.firstOrNull {
        it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress &&
            !it.isAnyLocalAddress && !it.isMulticastAddress
    }
}
