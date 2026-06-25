package net.primal.android.b2r.ui

import net.primal.core.utils.b2rPubToNpub
import net.primal.core.utils.npubToB2rPub
import net.primal.domain.nostr.cryptography.utils.bech32ToHexOrThrow
import net.primal.domain.nostr.cryptography.utils.hexToNpubHrp

/**
 * b2r fork: render a hex public key as a short `b2r_pub…` label for the UI.
 * Falls back gracefully if the key is not valid hex.
 */
fun String.toB2rDisplayKey(): String {
    val full = runCatching { hexToNpubHrp().npubToB2rPub() }.getOrNull()
    return when {
        full != null && full.length > SHORT_KEY_THRESHOLD ->
            full.take(SHORT_KEY_PREFIX) + "…" + full.takeLast(SHORT_KEY_SUFFIX)
        full != null -> full
        length > SHORT_KEY_SUFFIX -> "b2r_pub…" + takeLast(SHORT_KEY_SUFFIX)
        else -> this
    }
}

/**
 * b2r fork: normalize a pasted peer key to the canonical hex form used for
 * conversation ids. Accepts `b2r_pub…`, `npub…` or raw hex.
 */
fun String.normalizePeerKeyToHex(): String {
    val trimmed = trim()
    return runCatching {
        when {
            trimmed.startsWith("b2r_pub") -> trimmed.b2rPubToNpub().bech32ToHexOrThrow()
            trimmed.startsWith("npub") -> trimmed.bech32ToHexOrThrow()
            else -> trimmed
        }
    }.getOrDefault(trimmed)
}

private const val SHORT_KEY_THRESHOLD = 20
private const val SHORT_KEY_PREFIX = 14
private const val SHORT_KEY_SUFFIX = 6
