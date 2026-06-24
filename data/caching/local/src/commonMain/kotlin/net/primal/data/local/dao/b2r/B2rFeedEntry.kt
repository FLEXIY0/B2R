package net.primal.data.local.dao.b2r

import androidx.room.Entity
import androidx.room.Index

/**
 * b2r fork (Sprint 1.4): the local feed log.
 *
 * Replaces Primal's Nostr-event-shaped cache rows with b2r's own structure. Each
 * row is one post authored by [authorPubkey] (a b2r secp256k1 public key) and
 * carries a per-author [sequenceId] — the monotonic index of that author's posts
 * (the "log position" from the spec).
 *
 * Conflict resolution follows the owner's `todo` model: union + last-write-wins
 * by [modifiedAt], with [deleted] acting as a tombstone so a newer deletion is
 * not resurrected by an older copy received later from another peer. Because b2r
 * replicates directly phone-to-phone, the same [eventId] may arrive from several
 * peers; merging keeps whichever copy has the greatest [modifiedAt].
 */
@Entity(
    tableName = "B2rFeedEntry",
    primaryKeys = ["eventId"],
    indices = [
        Index(value = ["authorPubkey", "sequenceId"]),
        Index(value = ["createdAt"]),
    ],
)
data class B2rFeedEntry(
    /** Stable content id of the post (hash of the signed payload). */
    val eventId: String,
    /** b2r author public key (secp256k1), shown in the UI as `b2r_pub…`. */
    val authorPubkey: String,
    /** Monotonic per-author index of this post within the author's log. */
    val sequenceId: Long,
    /** Post body. */
    val content: String,
    /** Author-stated creation time (epoch millis); used for feed ordering. */
    val createdAt: Long,
    /** Logical clock for LWW merges (epoch millis); greatest wins. */
    val modifiedAt: Long,
    /** Tombstone flag — a deleted post is kept to stop resurrection on replication. */
    val deleted: Boolean = false,
    /** Signature over the payload by the author key, or null if not yet verified. */
    val signature: String? = null,
)
