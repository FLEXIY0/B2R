package com.example.compose.jetchat.b2r

import android.content.Context
import java.security.SecureRandom

/**
 * b2r: the device's identity. Generated once on first use and persisted. Shown
 * in the UI as `b2r_pub…`. (A full secp256k1 key is introduced together with the
 * encrypted chat; the public feed only needs a stable author id.)
 */
class B2rIdentity(context: Context) {

    private val prefs = context.getSharedPreferences("b2r_identity", Context.MODE_PRIVATE)

    val pubKey: String by lazy {
        prefs.getString(KEY_PUB, null) ?: generate().also {
            prefs.edit().putString(KEY_PUB, it).apply()
        }
    }

    /** Short, human-facing label. */
    val displayKey: String get() = "b2r_pub" + pubKey.take(DISPLAY_LEN)

    private fun generate(): String {
        val bytes = ByteArray(KEY_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val KEY_PUB = "pub"
        const val KEY_BYTES = 16
        const val DISPLAY_LEN = 10
    }
}
