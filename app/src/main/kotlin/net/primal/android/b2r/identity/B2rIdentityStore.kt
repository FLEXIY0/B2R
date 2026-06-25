package net.primal.android.b2r.identity

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import net.primal.domain.nostr.cryptography.utils.CryptoUtils

/**
 * b2r fork: the device's own b2r identity.
 *
 * b2r does not require a Primal login — on first use it generates a secp256k1
 * keypair (shown in the UI as `b2r_pub…`) and persists it locally. The public key
 * is the author/identity for the feed and chat; the private key signs/encrypts.
 */
@Singleton
class B2rIdentityStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    @get:Synchronized
    val pubKey: String get() = ensure().second

    @get:Synchronized
    val privKey: String get() = ensure().first

    /** Returns (privateKeyHex, publicKeyHex), generating and persisting on first use. */
    @Synchronized
    private fun ensure(): Pair<String, String> {
        val existingPriv = prefs.getString(KEY_PRIV, null)
        val existingPub = prefs.getString(KEY_PUB, null)
        if (!existingPriv.isNullOrBlank() && !existingPub.isNullOrBlank()) {
            return existingPriv to existingPub
        }
        val keyPair = CryptoUtils.generateHexEncodedKeypair()
        prefs.edit()
            .putString(KEY_PRIV, keyPair.privateKey)
            .putString(KEY_PUB, keyPair.pubKey)
            .apply()
        return keyPair.privateKey to keyPair.pubKey
    }

    private companion object {
        const val PREFS_NAME = "b2r_identity"
        const val KEY_PRIV = "priv"
        const val KEY_PUB = "pub"
    }
}
