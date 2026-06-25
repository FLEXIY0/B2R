package com.example.compose.jetchat.b2r

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * b2r: local persistence for private chats. One JSON file keeps every conversation
 * keyed by peer public key. Merge is union-by-id (LWW) so replicated messages
 * de-duplicate, mirroring the feed store.
 */
class B2rChatStore(context: Context) {

    private val file = File(context.filesDir, "b2r_chats.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), ListSerializer(B2rChatMessage.serializer()))

    private val _conversations = MutableStateFlow(load())
    val conversations: StateFlow<Map<String, List<B2rChatMessage>>> = _conversations

    fun messagesFor(peer: String): List<B2rChatMessage> = _conversations.value[peer].orEmpty()

    @Synchronized
    fun upsert(peer: String, incoming: List<B2rChatMessage>) {
        if (incoming.isEmpty()) return
        val merged = (_conversations.value[peer].orEmpty() + incoming)
            .associateBy { it.id }
            .values
            .sortedBy { it.createdAt }
        _conversations.value = _conversations.value + (peer to merged)
        persist()
    }

    fun add(peer: String, message: B2rChatMessage) = upsert(peer, listOf(message))

    private fun persist() {
        runCatching { file.writeText(json.encodeToString(serializer, _conversations.value)) }
    }

    private fun load(): Map<String, List<B2rChatMessage>> =
        runCatching { json.decodeFromString(serializer, file.readText()) }
            .getOrDefault(emptyMap())
}
