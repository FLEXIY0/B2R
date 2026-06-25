package com.example.compose.jetchat.b2r

import kotlinx.serialization.Serializable

/** b2r: a single message inside a private 1:1 conversation. */
@Serializable
data class B2rChatMessage(
    val id: String,
    /** Public key of the sender. */
    val sender: String,
    /** Sender's display mask at send time. */
    val senderName: String,
    val content: String,
    val createdAt: Long,
)
