package com.example.compose.jetchat.b2r

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * b2r: private 1:1 chat over the shared broker.
 *
 * Each conversation between [myKey] and a peer replicates through a topic derived
 * from the key pair; the payload is encrypted with a pair-derived secret. Reads are
 * served locally from [B2rChatStore]; sends append locally and re-advertise the
 * encrypted snapshot. Best-effort networking — chats keep working offline.
 */
class B2rChatRepository(
    private val store: B2rChatStore,
    private val broker: B2rMqttBroker,
    private val myKey: String,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(B2rChatMessage.serializer())

    val conversations: StateFlow<Map<String, List<B2rChatMessage>>> = store.conversations

    suspend fun send(peer: String, senderName: String, content: String): B2rChatMessage =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val message = B2rChatMessage(
                id = "$myKey:$now:${Random.nextInt(RANDOM_BOUND)}",
                sender = myKey,
                senderName = senderName,
                content = content.trim(),
                createdAt = now,
            )
            store.add(peer, message)
            publishSnapshot(peer)
            message
        }

    suspend fun refresh(peer: String) = withContext(Dispatchers.IO) {
        if (peer.isBlank()) return@withContext
        val topic = B2rCrypto.conversationTopic(myKey, peer)
        val cipher = broker.fetch(topic) ?: return@withContext
        val plain = B2rCrypto.decrypt(cipher, myKey, peer) ?: return@withContext
        val incoming = runCatching { json.decodeFromString(serializer, plain) }.getOrNull() ?: return@withContext
        store.upsert(peer, incoming)
        publishSnapshot(peer)
    }

    private suspend fun publishSnapshot(peer: String) {
        runCatching {
            val topic = B2rCrypto.conversationTopic(myKey, peer)
            val plain = json.encodeToString(serializer, store.messagesFor(peer))
            broker.publish(topic, B2rCrypto.encrypt(plain, myKey, peer))
        }
    }

    private companion object {
        const val RANDOM_BOUND = 1_000_000
    }
}
