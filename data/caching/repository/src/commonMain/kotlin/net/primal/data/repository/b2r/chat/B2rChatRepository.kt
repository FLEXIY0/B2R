@file:OptIn(ExperimentalStdlibApi::class, kotlin.io.encoding.ExperimentalEncodingApi::class)

package net.primal.data.repository.b2r.chat

import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.primal.core.utils.asSha256Hash
import net.primal.core.utils.coroutines.DispatcherProvider
import net.primal.data.local.dao.b2r.B2rChatMessage
import net.primal.data.local.db.PrimalDatabase
import net.primal.data.repository.b2r.p2p.DiscoveryBroker
import net.primal.domain.nostr.cryptography.utils.CryptoUtils

/**
 * b2r fork: private 1:1 chat over the shared broker.
 *
 * "Find a person, write to them" — the conversation between two keys lives at an
 * opaque topic derived from both keys ([conversationIdOf]) and every message is
 * end-to-end encrypted (secp256k1 ECDH + AES, via [CryptoUtils]) before it leaves
 * the device. Other peers on the same public broker only see ciphertext on a
 * topic they cannot attribute. Locally we keep the decrypted text for display.
 *
 * Uses the device's b2r identity key directly, so it works without any Primal
 * login. A conversation is replicated as a retained snapshot merged with
 * union + last-write-wins.
 */
class B2rChatRepository(
    private val database: PrimalDatabase,
    private val dispatcherProvider: DispatcherProvider,
    private val discoveryBroker: DiscoveryBroker,
) {

    /** Observe one conversation (oldest first) as UI messages. */
    fun observeConversation(myPubkey: String, peerPubkey: String): Flow<List<B2rChatMessageUi>> =
        database.b2rChat()
            .observeConversation(conversationIdOf(myPubkey, peerPubkey))
            .map { messages -> messages.map { it.toUi() } }
            .flowOn(dispatcherProvider.io())

    /** Observe the chat list: the latest message of each conversation. */
    fun observeConversations(myPubkey: String): Flow<List<B2rConversationPreview>> =
        database.b2rChat()
            .observeConversationsPreview()
            .map { previews -> previews.map { it.toPreview(myPubkey) } }
            .flowOn(dispatcherProvider.io())

    /** Send a message to [peerPubkey]: store locally, then publish the encrypted conversation. */
    suspend fun sendMessage(
        myPrivkeyHex: String,
        myPubkey: String,
        peerPubkey: String,
        text: String,
    ): B2rChatMessageUi =
        withContext(dispatcherProvider.io()) {
            val now = Clock.System.now().toEpochMilliseconds()
            val message = B2rChatMessage(
                messageId = "$myPubkey:$now:${Random.nextInt(RANDOM_ID_BOUND)}",
                conversationId = conversationIdOf(myPubkey, peerPubkey),
                senderPubkey = myPubkey,
                recipientPubkey = peerPubkey,
                content = text,
                createdAt = now,
                modifiedAt = now,
                mine = true,
            )
            database.b2rChat().upsert(message)
            publishConversation(myPrivkeyHex = myPrivkeyHex, myPubkey = myPubkey, peerPubkey = peerPubkey)
            message.toUi()
        }

    /** Pull the latest conversation snapshot from the broker, decrypt and merge it. */
    suspend fun syncConversation(myPrivkeyHex: String, myPubkey: String, peerPubkey: String) =
        withContext(dispatcherProvider.io()) {
            val conversationId = conversationIdOf(myPubkey, peerPubkey)
            val payload = try {
                discoveryBroker.fetchSnapshot(dmTopic(conversationId))
            } catch (error: Exception) {
                null
            } ?: return@withContext

            val incoming = try {
                B2rChatSnapshotCodec.decode(payload)
            } catch (error: Exception) {
                return@withContext
            }

            val privBytes = myPrivkeyHex.hexToByteArray()
            val peerBytes = peerPubkey.hexToByteArray()
            val dao = database.b2rChat()
            val toWrite = incoming.mapNotNull { dto ->
                val existing = dao.findById(dto.messageId)
                if (existing != null && existing.modifiedAt >= dto.modifiedAt) return@mapNotNull null
                val plaintext = try {
                    CryptoUtils.decrypt(dto.ciphertext, privBytes, peerBytes)
                } catch (error: Exception) {
                    return@mapNotNull null
                }
                B2rChatMessage(
                    messageId = dto.messageId,
                    conversationId = dto.conversationId,
                    senderPubkey = dto.senderPubkey,
                    recipientPubkey = dto.recipientPubkey,
                    content = plaintext,
                    createdAt = dto.createdAt,
                    modifiedAt = dto.modifiedAt,
                    mine = dto.senderPubkey == myPubkey,
                )
            }
            if (toWrite.isNotEmpty()) dao.upsertAll(toWrite)
        }

    private suspend fun publishConversation(myPrivkeyHex: String, myPubkey: String, peerPubkey: String) {
        val conversationId = conversationIdOf(myPubkey, peerPubkey)
        val messages = database.b2rChat().getConversation(conversationId)
        if (messages.isEmpty()) return

        val privBytes = myPrivkeyHex.hexToByteArray()
        val peerBytes = peerPubkey.hexToByteArray()
        val dtos = messages.mapNotNull { message ->
            val ciphertext = try {
                CryptoUtils.encrypt(message.content, privBytes, peerBytes)
            } catch (error: Exception) {
                return@mapNotNull null
            }
            B2rChatMessageDto(
                messageId = message.messageId,
                conversationId = message.conversationId,
                senderPubkey = message.senderPubkey,
                recipientPubkey = message.recipientPubkey,
                ciphertext = ciphertext,
                createdAt = message.createdAt,
                modifiedAt = message.modifiedAt,
            )
        }
        if (dtos.isEmpty()) return
        try {
            discoveryBroker.publishSnapshot(dmTopic(conversationId), B2rChatSnapshotCodec.encode(dtos))
        } catch (error: Exception) {
            // best-effort advertise; the local copy is already saved
        }
    }

    private fun B2rChatMessage.toUi() =
        B2rChatMessageUi(
            messageId = messageId,
            senderPubkey = senderPubkey,
            content = content,
            createdAt = createdAt,
            mine = mine,
        )

    private fun B2rChatMessage.toPreview(myPubkey: String) =
        B2rConversationPreview(
            conversationId = conversationId,
            peerPubkey = if (senderPubkey == myPubkey) recipientPubkey else senderPubkey,
            lastMessage = content,
            lastMessageAt = createdAt,
        )

    companion object {
        private const val RANDOM_ID_BOUND = 1_000_000

        /** Deterministic, order-independent conversation id, hashed so it leaks no keys. */
        fun conversationIdOf(a: String, b: String): String {
            val ordered = if (a <= b) "$a|$b" else "$b|$a"
            return ordered.asSha256Hash()
        }

        private fun dmTopic(conversationId: String) = "b2r/dm/$conversationId"
    }
}
