package com.example.compose.jetchat.b2r

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * b2r: local persistence for the feed. A JSON file holds all known posts; reads
 * are served from memory via [posts]. Merge is union-by-id so replicated posts
 * de-duplicate.
 */
class B2rStore(context: Context) {

    private val file = File(context.filesDir, "b2r_feed.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(B2rPost.serializer())

    private val _posts = MutableStateFlow(load())
    val posts: StateFlow<List<B2rPost>> = _posts

    fun all(): List<B2rPost> = _posts.value

    @Synchronized
    fun upsertAll(incoming: List<B2rPost>) {
        if (incoming.isEmpty()) return
        val merged = (_posts.value + incoming)
            .associateBy { it.id }
            .values
            .sortedByDescending { it.createdAt }
        _posts.value = merged
        runCatching { file.writeText(json.encodeToString(serializer, merged)) }
    }

    fun add(post: B2rPost) = upsertAll(listOf(post))

    private fun load(): List<B2rPost> =
        runCatching { json.decodeFromString(serializer, file.readText()) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.createdAt }
}
