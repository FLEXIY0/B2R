package com.example.compose.jetchat.b2r

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.SecureRandom

/**
 * b2r: the device's identity.
 *
 * - [pubKey] is generated once on first use and persisted; it is the stable,
 *   machine-facing address used to route posts and private chats.
 * - [nickname] is the user-facing **mask** — a freely editable display name shown
 *   everywhere instead of the raw key. It defaults to a key-derived handle and can
 *   be changed in the profile screen.
 *
 * (A full secp256k1 key pair is introduced together with strong end-to-end chat
 * encryption; the current feed/chat only need a stable author id + a shared secret
 * derived from the key pair.)
 */
class B2rIdentity(context: Context) {

    private val prefs = context.getSharedPreferences("b2r_identity", Context.MODE_PRIVATE)

    val pubKey: String by lazy {
        prefs.getString(KEY_PUB, null) ?: generate().also {
            prefs.edit().putString(KEY_PUB, it).apply()
        }
    }

    private val _nickname = MutableStateFlow(
        prefs.getString(KEY_NICK, null) ?: defaultNickname(),
    )

    /** The user-facing mask. Observe this so the UI reacts to renames. */
    val nickname: StateFlow<String> = _nickname

    /** Short, technical label for the underlying key (e.g. `b2r_pubdeadbeef…`). */
    val keyLabel: String get() = "b2r_pub" + pubKey.take(DISPLAY_LEN)

    /** Update the mask and persist it. Blank input is ignored. */
    fun setNickname(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        prefs.edit().putString(KEY_NICK, trimmed).apply()
        _nickname.value = trimmed
    }

    private fun defaultNickname(): String = "b2r-" + pubKey.take(DEFAULT_NICK_LEN)

    private fun generate(): String {
        val bytes = ByteArray(KEY_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val KEY_PUB = "pub"
        const val KEY_NICK = "nickname"
        const val KEY_BYTES = 16
        const val DISPLAY_LEN = 10
        const val DEFAULT_NICK_LEN = 6
    }
}
