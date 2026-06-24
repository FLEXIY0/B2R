package net.primal.core.utils

/**
 * b2r fork (Sprint 2.2): public-key presentation.
 *
 * b2r keeps Primal's key plumbing (secp256k1, bech32) but rebrands how a public
 * key is shown to the user: an `npub1…` is displayed as `b2r_pub1…`.
 *
 * This is a DISPLAY-ONLY transform. The underlying bech32 string (and the hex
 * pubkey it decodes to) is unchanged, so signing, lookups and decoding keep
 * working. We deliberately do NOT re-encode with a `b2r_pub` bech32 HRP —
 * underscores are not valid in a bech32 human-readable part and it would break
 * round-tripping. We only swap the human-facing prefix, which is fully
 * reversible via [b2rPubToNpub].
 */
object B2rKeyFormatter {

    const val B2R_PUB_PREFIX = "b2r_pub"
    private const val NPUB_PREFIX = "npub"

    /** `npub1abc…` -> `b2r_pub1abc…` (returns input unchanged if it is not an npub). */
    fun npubToB2rPub(npub: String): String =
        if (npub.startsWith(NPUB_PREFIX)) B2R_PUB_PREFIX + npub.removePrefix(NPUB_PREFIX) else npub

    /** `b2r_pub1abc…` -> `npub1abc…`, for any code path that needs the underlying npub. */
    fun b2rPubToNpub(b2rPub: String): String =
        if (b2rPub.startsWith(B2R_PUB_PREFIX)) NPUB_PREFIX + b2rPub.removePrefix(B2R_PUB_PREFIX) else b2rPub
}

/** Convenience extension: display an npub as a b2r public key. */
fun String.npubToB2rPub(): String = B2rKeyFormatter.npubToB2rPub(this)

/** Convenience extension: recover the npub from a b2r public key display string. */
fun String.b2rPubToNpub(): String = B2rKeyFormatter.b2rPubToNpub(this)
