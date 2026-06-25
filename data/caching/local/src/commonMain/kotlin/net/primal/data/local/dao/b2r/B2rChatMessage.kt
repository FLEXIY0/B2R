package net.primal.data.local.dao.b2r

import androidx.room.Entity
import androidx.room.Index

/**
 * b2r fork: a single 1:1 chat message, stored locally as plaintext.
 *
 * Privacy is enforced on the wire, not in storage: messages travel through the
 * shared broker under an opaque per-conversation topic and are end-to-end
 * encrypted (NIP-04) so only the two participants can read them. Locally — on the
 * owner's own device — the decrypted [content] is kept for display.
 *
 * [conversationId] is derived from the two participants' public keys (sorted,
 * hashed) so both sides compute the same id without leaking who is talking.
 * Conflict resolution is last-write-wins by [modifiedAt], matching the feed.
 */
@Entity(
    tableName = "B2rChatMessage",
    primaryKeys = ["messageId"],
    indices = [Index(value = ["conversationId", "createdAt"])],
)
data class B2rChatMessage(
    val messageId: String,
    val conversationId: String,
    val senderPubkey: String,
    val recipientPubkey: String,
    /** Decrypted message text (local only). */
    val content: String,
    val createdAt: Long,
    val modifiedAt: Long,
    /** True if this device's user sent the message. */
    val mine: Boolean,
)
