package com.example.compose.jetchat.b2r

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * b2r: addressing and light encryption for private 1:1 chat.
 *
 * Privacy model (matching the product intent — "ограничены видимостью"): a
 * conversation lives on a broker topic derived from the *pair* of participant
 * keys, so only the two sides know where to look. Payloads are additionally
 * AES-CBC encrypted with a secret derived from the same pair (NIP-04 wire format
 * `cipher?iv=`), so a passer-by who stumbles on the topic still can't read it.
 *
 * This is intentionally dependency-free (JDK `javax.crypto`). It is *not* yet
 * full end-to-end secp256k1 ECDH — that lands when the key pair upgrades — but it
 * already gives per-conversation isolation and at-rest opacity on the broker.
 */
object B2rCrypto {

    /** Deterministic, order-independent topic for the conversation between [a] and [b]. */
    fun conversationTopic(a: String, b: String): String {
        val digest = sha256(pairSeed(a, b).encodeToByteArray())
        return TOPIC_PREFIX + digest.toHex().take(TOPIC_LEN)
    }

    fun encrypt(plaintext: String, a: String, b: String): String {
        val iv = ByteArray(AES_BLOCK).also { Random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey(a, b), IvParameterSpec(iv))
        }
        val encrypted = cipher.doFinal(plaintext.encodeToByteArray())
        return base64(encrypted) + "?iv=" + base64(iv)
    }

    /** Returns null if the message wasn't meant for this pair (or is malformed). */
    fun decrypt(message: String, a: String, b: String): String? = runCatching {
        val parts = message.split("?iv=")
        require(parts.size == 2)
        val encrypted = unbase64(parts[0])
        val iv = unbase64(parts[1])
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(a, b), IvParameterSpec(iv))
        }
        cipher.doFinal(encrypted).decodeToString()
    }.getOrNull()

    private fun secretKey(a: String, b: String): SecretKeySpec =
        SecretKeySpec(sha256(pairSeed(a, b).encodeToByteArray()), "AES")

    private fun pairSeed(a: String, b: String): String =
        if (a <= b) "$a:$b" else "$b:$a"

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun base64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun unbase64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val AES_BLOCK = 16
    private const val TOPIC_PREFIX = "b2r/dm/v1/"
    private const val TOPIC_LEN = 24
}
