package com.example.compose.jetchat.b2r

import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * b2r: the global shared feed. Reads come from the local [B2rStore]; writes append
 * locally and advertise the merged snapshot to peers over the [B2rMqttBroker].
 * Everyone who installs the app shares this one feed.
 */
class B2rFeedRepository(
    private val store: B2rStore,
    private val broker: B2rMqttBroker,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(B2rPost.serializer())

    val posts: StateFlow<List<B2rPost>> = store.posts

    suspend fun createPost(author: String, authorName: String, content: String): B2rPost = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val post = B2rPost(
            id = "$author:$now:${Random.nextInt(RANDOM_BOUND)}",
            author = author,
            content = content.trim(),
            createdAt = now,
            authorName = authorName,
        )
        store.add(post)
        publishSnapshot()
        post
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val payload = broker.fetch(GLOBAL_TOPIC) ?: return@withContext
        val incoming = runCatching { json.decodeFromString(serializer, payload) }.getOrNull() ?: return@withContext
        store.upsertAll(incoming)
        // Re-advertise the merged view so peers converge.
        publishSnapshot()
    }

    private suspend fun publishSnapshot() {
        runCatching { broker.publish(GLOBAL_TOPIC, json.encodeToString(serializer, store.all())) }
    }

    private companion object {
        const val GLOBAL_TOPIC = "b2r/global/v1"
        const val RANDOM_BOUND = 1_000_000
    }
}
