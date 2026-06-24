package net.primal.data.repository.b2r.p2p

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import net.primal.core.utils.serialization.CommonJsonEncodeDefaults
import net.primal.data.local.dao.b2r.B2rFeedEntry

/**
 * b2r fork (point 1): wire format for an author snapshot exchanged over the
 * discovery broker. A serializable mirror of [B2rFeedEntry] so the Room entity
 * stays free of serialization concerns.
 */
@Serializable
internal data class B2rPostDto(
    val eventId: String,
    val authorPubkey: String,
    val sequenceId: Long,
    val content: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val deleted: Boolean = false,
    val signature: String? = null,
)

internal fun B2rFeedEntry.toDto() =
    B2rPostDto(
        eventId = eventId,
        authorPubkey = authorPubkey,
        sequenceId = sequenceId,
        content = content,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        deleted = deleted,
        signature = signature,
    )

internal fun B2rPostDto.toEntry() =
    B2rFeedEntry(
        eventId = eventId,
        authorPubkey = authorPubkey,
        sequenceId = sequenceId,
        content = content,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        deleted = deleted,
        signature = signature,
    )

/** Encodes/decodes the JSON payload of a global feed snapshot exchanged via the broker. */
object P2pSnapshotCodec {

    private val serializer = ListSerializer(B2rPostDto.serializer())

    fun encode(entries: List<B2rFeedEntry>): String =
        CommonJsonEncodeDefaults.encodeToString(serializer, entries.map { it.toDto() })

    fun decode(payload: String): List<B2rFeedEntry> =
        CommonJsonEncodeDefaults.decodeFromString(serializer, payload).map { it.toEntry() }
}
