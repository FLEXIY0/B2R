package com.example.compose.jetchat.b2r

import kotlinx.serialization.Serializable

/** b2r: a public post in the global shared feed. */
@Serializable
data class B2rPost(
    val id: String,
    val author: String,
    val content: String,
    val createdAt: Long,
)
