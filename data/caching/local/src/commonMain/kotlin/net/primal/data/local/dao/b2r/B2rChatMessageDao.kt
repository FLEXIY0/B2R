package net.primal.data.local.dao.b2r

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * b2r fork: access to locally stored 1:1 chat messages ([B2rChatMessage]).
 */
@Dao
interface B2rChatMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: B2rChatMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<B2rChatMessage>)

    @Query("SELECT * FROM B2rChatMessage WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeConversation(conversationId: String): Flow<List<B2rChatMessage>>

    /** The full conversation — used to build the encrypted snapshot for the peer. */
    @Query("SELECT * FROM B2rChatMessage WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getConversation(conversationId: String): List<B2rChatMessage>

    @Query("SELECT * FROM B2rChatMessage WHERE messageId = :messageId")
    suspend fun findById(messageId: String): B2rChatMessage?

    /** Latest message per conversation, for the chat list. */
    @Query(
        "SELECT * FROM B2rChatMessage AS m WHERE m.createdAt = " +
            "(SELECT MAX(createdAt) FROM B2rChatMessage WHERE conversationId = m.conversationId) " +
            "GROUP BY m.conversationId ORDER BY m.createdAt DESC",
    )
    fun observeConversationsPreview(): Flow<List<B2rChatMessage>>
}
