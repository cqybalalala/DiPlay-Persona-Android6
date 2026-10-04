package com.shilapi.xcertplay.transport

import java.security.MessageDigest
import java.util.Base64
import org.bouncycastle.asn1.pkcs.RSAPublicKey

/** Private storage key only; never log the public key or its fingerprint. */
object LockdownDeviceIdentity {
    fun key(pem: ByteArray): String {
        require(pem.size in 1..16384) { "Invalid device public key size" }
        val text = pem.toString(Charsets.US_ASCII).trim()
        val begin = "-----BEGIN RSA PUBLIC KEY-----"
        val end = "-----END RSA PUBLIC KEY-----"
        require(text.startsWith(begin) && text.endsWith(end)) { "Invalid device public key PEM" }
        val encoded = text.removePrefix(begin).removeSuffix(end).filterNot { it.isWhitespace() }
        val key = RSAPublicKey.getInstance(Base64.getDecoder().decode(encoded))
        require(key.modulus.signum() > 0 && key.publicExponent.signum() > 0) { "Invalid RSA public key" }
        return MessageDigest.getInstance("SHA-256").digest(key.getEncoded("DER"))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
