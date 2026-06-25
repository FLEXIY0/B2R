package net.primal.data.repository.b2r.chat

/** b2r fork: a UI-facing chat message (plaintext; the Room entity stays internal). */
data class B2rChatMessageUi(
    val messageId: String,
    val senderPubkey: String,
    val content: String,
    val createdAt: Long,
    val mine: Boolean,
)

/** b2r fork: a row in the chat list — the latest message of one conversation. */
data class B2rConversationPreview(
    val conversationId: String,
    val peerPubkey: String,
    val lastMessage: String,
    val lastMessageAt: Long,
)
