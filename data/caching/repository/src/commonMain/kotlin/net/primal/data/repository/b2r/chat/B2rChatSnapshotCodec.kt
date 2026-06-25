package net.primal.data.repository.b2r.chat

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import net.primal.core.utils.serialization.CommonJsonEncodeDefaults

/**
 * b2r fork: wire format for a 1:1 conversation snapshot exchanged over the broker.
 *
 * [ciphertext] is the NIP-04 encrypted message body; the broker (and any other
 * peer) only ever sees this, never the plaintext.
 */
@Serializable
internal data class B2rChatMessageDto(
    val messageId: String,
    val conversationId: String,
    val senderPubkey: String,
    val recipientPubkey: String,
    val ciphertext: String,
    val createdAt: Long,
    val modifiedAt: Long,
)

internal object B2rChatSnapshotCodec {

    private val serializer = ListSerializer(B2rChatMessageDto.serializer())

    fun encode(messages: List<B2rChatMessageDto>): String =
        CommonJsonEncodeDefaults.encodeToString(serializer, messages)

    fun decode(payload: String): List<B2rChatMessageDto> =
        CommonJsonEncodeDefaults.decodeFromString(serializer, payload)
}
